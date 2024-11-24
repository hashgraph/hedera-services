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
import java.util.Iterator;
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
    public Iterator<ExecutableTxn<?>> executableTxns(
            @NonNull final Instant start, @NonNull final Instant end, @NonNull final StoreFactory storeFactory) {
        requireNonNull(start);
        requireNonNull(end);
        requireNonNull(storeFactory);
        return new ExecutableTxnsIterator(start.getEpochSecond(), end.getEpochSecond(), storeFactory);
    }

    private static class ExecutableTxnsIterator implements Iterator<ExecutableTxn<?>> {
        private static final Comparator<ScheduledOrder> ORDER_COMPARATOR =
                Comparator.comparingLong(ScheduledOrder::expirySecond).thenComparingInt(ScheduledOrder::orderNumber);

        private final long startSecond;
        private final long endSecond;
        private final StoreFactory storeFactory;
        private final WritableScheduleStore scheduleStore;

        private boolean nextKnown = false;

        @Nullable
        private Schedule nextSchedule;

        @Nullable
        private ScheduledOrder prevOrder;

        @Nullable
        private ScheduledOrder nextOrder;

        public ExecutableTxnsIterator(
                final long startSecond, final long endSecond, @NonNull final StoreFactory storeFactory) {
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
            ScheduledOrder order;
            if (prevOrder == null) {
                order = nextOrder;
            } else {
                if (ORDER_COMPARATOR.compare(prevOrder, nextOrder) > 0) {
                    throw new IllegalStateException("remove() called twice");
                }
                order = prevOrder;
            }
            while (ORDER_COMPARATOR.compare(order, nextOrder) <= 0) {
                final var lastOfSecond = scheduleStore.purgeByOrder(order);
                order = next(order, lastOfSecond);
            }
            prevOrder = order;
        }

        private @Nullable ScheduledOrder prepNext() {
            if (nextKnown) {
                return nextOrder;
            }
            nextOrder = null;
            nextSchedule = null;
            ScheduledOrder order;
            if (prevOrder != null) {
                order = prevOrder;
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
