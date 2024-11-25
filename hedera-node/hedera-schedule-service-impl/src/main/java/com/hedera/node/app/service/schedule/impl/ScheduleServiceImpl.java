/*
 * Copyright (C) 2020-2024 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hedera.node.app.service.schedule.impl;

import static com.hedera.node.app.service.schedule.impl.handlers.AbstractScheduleHandler.simpleKeyVerifierFrom;
import static com.hedera.node.app.service.schedule.impl.handlers.HandlerUtility.childAsOrdinary;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.state.schedule.Schedule;
import com.hedera.hapi.node.state.schedule.ScheduledOrder;
import com.hedera.node.app.service.schedule.ExecutableTxn;
import com.hedera.node.app.service.schedule.ExecutableTxnIterator;
import com.hedera.node.app.service.schedule.ScheduleService;
import com.hedera.node.app.service.schedule.ScheduleStreamBuilder;
import com.hedera.node.app.service.schedule.WritableScheduleStore;
import com.hedera.node.app.service.schedule.impl.schemas.V0490ScheduleSchema;
import com.hedera.node.app.service.schedule.impl.schemas.V0570ScheduleSchema;
import com.hedera.node.app.service.token.ReadableAccountStore;
import com.hedera.node.app.spi.RpcService;
import com.hedera.node.app.spi.store.StoreFactory;
import com.swirlds.state.lifecycle.SchemaRegistry;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.time.Instant;
import java.util.Comparator;
import java.util.NoSuchElementException;

/**
 * Standard implementation of the {@link ScheduleService} {@link RpcService}.
 */
public final class ScheduleServiceImpl implements ScheduleService {
    @Override
    public void registerSchemas(@NonNull final SchemaRegistry registry) {
        registry.register(new V0490ScheduleSchema());
        registry.register(new V0570ScheduleSchema());
    }

    @Override
    public ExecutableTxnIterator executableTxns(
            @NonNull final Instant start, @NonNull final Instant end, @NonNull final StoreFactory storeFactory) {
        requireNonNull(start);
        requireNonNull(end);
        requireNonNull(storeFactory);
        return new PurgingIterator(start.getEpochSecond(), end.getEpochSecond(), storeFactory);
    }

    /**
     * An {@link ExecutableTxnIterator} that traverses the executable transactions in the specified
     * interval and purges <i>all</i> traversed scheduling metadata (not just for executable transactions)
     * in response to calls to {@link ExecutableTxnIterator#remove()} and
     * {@link ExecutableTxnIterator#purgeUntilNext()}.
     */
    private static class PurgingIterator implements ExecutableTxnIterator {
        private static final Comparator<ScheduledOrder> ORDER_COMPARATOR =
                Comparator.comparingLong(ScheduledOrder::expirySecond).thenComparingInt(ScheduledOrder::orderNumber);

        private final long startSecond;
        private final long endSecond;
        private final StoreFactory storeFactory;
        private final WritableScheduleStore scheduleStore;

        /**
         * True if the next executable transaction to be processed is known; false otherwise.
         */
        private boolean nextKnown = false;

        /**
         * The order of the next executable transaction to be processed. Null in exactly two cases:
         * <ol>
         *     <li>When neither {@link #hasNext()} nor {@link #next()} has ever been called (so that
         *     {@link #nextKnown} is false).</li>
         *     <li>When the last call to {@link #hasNext()} or {@link #next()} discovered that
         *     there are no more executable transactions in the scoped {@code [start, end]} interval (so
         *     that {@link #nextKnown} is true).</li>
         * </ol>
         * If not null, then is the order of the last executable transaction to have been discovered.
         * When {@link #nextKnown} is true, the executable transaction with this order will be returned
         * from then next call to {@link #next()}; if {@link #nextKnown} is false, the executable
         * transaction with this order was already returned from a call to {@link #next()}.
         */
        @Nullable
        private ScheduledOrder nextOrder;

        /**
         * If not null, the schedule representing the next executable transaction to be processed.
         */
        @Nullable
        private Schedule nextSchedule;

        /**
         * If not null, the earliest order before {@link #nextOrder} that is known to contain scheduled
         * transaction metadata.
         */
        @Nullable
        private ScheduledOrder previousOrder;

        /**
         * If not null, the earliest order after {@link #nextOrder} that may contain scheduled transaction metadata.
         */
        @Nullable
        private ScheduledOrder candidateOrder;

        public PurgingIterator(final long startSecond, final long endSecond, @NonNull final StoreFactory storeFactory) {
            this.startSecond = startSecond;
            this.endSecond = endSecond;
            this.storeFactory = requireNonNull(storeFactory);
            this.scheduleStore = storeFactory.writableStore(WritableScheduleStore.class);
        }

        @Override
        public boolean hasNext() {
            return prepNext() != null;
        }

        @Override
        public ExecutableTxn<ScheduleStreamBuilder> next() {
            if (!nextKnown) {
                prepNext();
            }
            nextKnown = false;
            if (nextSchedule == null) {
                throw new NoSuchElementException();
            }
            return executableTxnFrom(storeFactory.readableStore(ReadableAccountStore.class), nextSchedule);
        }

        @Override
        public void remove() {
            if (nextOrder == null) {
                throw new IllegalStateException("remove() called before next()");
            }
            if (candidateOrder != null && ORDER_COMPARATOR.compare(candidateOrder, nextOrder) > 0) {
                throw new IllegalStateException("remove() called twice");
            }
            // Pointer to the order whose executable transaction metadata should be purged
            var order = requireNonNull(previousOrder);
            while (ORDER_COMPARATOR.compare(order, nextOrder) <= 0) {
                final var lastOfSecond = scheduleStore.purgeByOrder(order);
                order = next(order, lastOfSecond);
            }
            candidateOrder = order;
            previousOrder = null;
        }

        @Override
        public boolean purgeUntilNext() {
            if (!nextKnown) {
                throw new IllegalStateException("purgeUntilNext() called before next()");
            }
            if (previousOrder != null) {
                var order = previousOrder;
                final var boundaryOrder = nextOrder != null ? nextOrder : new ScheduledOrder(endSecond + 1, 0);
                while (ORDER_COMPARATOR.compare(order, boundaryOrder) < 0) {
                    final var lastOfSecond = scheduleStore.purgeByOrder(order);
                    order = next(order, lastOfSecond);
                }
                return true;
            }
            return false;
        }

        /**
         * When {@link #nextKnown} is not already true, resets the iterator to be agnostic about the next
         * and previous orders, and then traverses orders starting from either {@link #candidateOrder} (if
         * not null), or the first candidate order in the interval if {@link #candidateOrder} is null.
         * <p>
         * It sets {@link #previousOrder} to the first encountered order with scheduled transaction metadata;
         * and sets {@link #nextOrder} and {@link #nextSchedule} to the first encountered order with an
         * executable schedule.
         * @return the next executable transaction to be processed, or null if there are no more
         */
        private @Nullable ScheduledOrder prepNext() {
            if (nextKnown) {
                return nextOrder;
            }
            nextOrder = null;
            nextSchedule = null;
            previousOrder = null;
            // Pointer to the order of the next schedule that should possibly be executed
            ScheduledOrder order;
            if (candidateOrder != null) {
                order = candidateOrder;
            } else {
                final var startCounts = scheduleStore.scheduledCountsAt(startSecond);
                if (startCounts == null) {
                    order = new ScheduledOrder(startSecond + 1, 0);
                } else {
                    order = new ScheduledOrder(startSecond, startCounts.numberProcessed());
                }
            }
            while (order.expirySecond() <= endSecond) {
                final var nextId = scheduleStore.getByOrder(order);
                if (nextId != null) {
                    if (previousOrder == null) {
                        previousOrder = order;
                    }
                    final var schedule = requireNonNull(scheduleStore.get(nextId));
                    if (!schedule.waitForExpiry() || schedule.deleted()) {
                        order = next(order, false);
                    } else {
                        nextOrder = order;
                        nextSchedule = schedule;
                        break;
                    }
                } else {
                    order = next(order, true);
                }
            }
            nextKnown = true;
            return nextOrder;
        }

        private ScheduledOrder next(@NonNull final ScheduledOrder order, final boolean lastInSecond) {
            return lastInSecond
                    ? new ScheduledOrder(order.expirySecond() + 1, 0)
                    : order.copyBuilder().orderNumber(order.orderNumber() + 1).build();
        }
    }

    private static ExecutableTxn<ScheduleStreamBuilder> executableTxnFrom(
            @NonNull final ReadableAccountStore accountStore, @NonNull final Schedule schedule) {
        return new ExecutableTxn<>(
                childAsOrdinary(schedule),
                schedule.payerAccountIdOrThrow(),
                simpleKeyVerifierFrom(accountStore, schedule.signatories()),
                Instant.ofEpochSecond(schedule.calculatedExpirationSecond()),
                ScheduleStreamBuilder.class,
                builder -> builder.scheduleRef(schedule.scheduleIdOrThrow()));
    }
}
