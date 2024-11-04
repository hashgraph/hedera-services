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

import static com.hedera.node.app.service.schedule.impl.handlers.HandlerUtility.childAsOrdinary;

import com.hedera.hapi.node.state.schedule.Schedule;
import com.hedera.node.app.service.schedule.ScheduleService;
import com.hedera.node.app.service.schedule.impl.schemas.V0490ScheduleSchema;
import com.hedera.node.app.service.schedule.impl.schemas.V0570ScheduleSchema;
import com.hedera.node.app.spi.RpcService;
import com.hedera.node.app.spi.signatures.VerificationAssistant;
import com.hedera.node.app.spi.store.StoreFactory;
import com.swirlds.state.spi.SchemaRegistry;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Instant;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.function.Supplier;

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
    public Iterator<ExecutableTxn> iterTxnsForInterval(
            Instant start, Instant end, Supplier<StoreFactory> cleanupStoreFactory) {
        var store = cleanupStoreFactory.get().writableStore(WritableScheduleStoreImpl.class);
        // Filter transactions that are not executed/deleted and have all the required keys
        var executableTxns = store.getByExpirationBetween(start.getEpochSecond(), end.getEpochSecond()).stream()
                .filter(schedule -> !schedule.executed() && !schedule.deleted())
                .toList();

        // Return a custom iterator that supports the remove() method
        return new Iterator<>() {
            private int currentIndex = -1; // To keep track of the current element
            private final List<Schedule> transactions = executableTxns;
            private ExecutableTxn lastReturned = null;

            @Override
            public boolean hasNext() {
                return currentIndex + 1 < transactions.size();
            }

            @Override
            public ExecutableTxn next() {
                if (!hasNext()) {
                    throw new NoSuchElementException();
                }
                lastReturned = toExecutableTxn(transactions.get(++currentIndex));
                return lastReturned;
            }

            @Override
            public void remove() {
                if (lastReturned == null) {
                    throw new IllegalStateException("No transaction to remove");
                }

                // Use the StoreFactory to clean up the transaction
                cleanupStoreFactory
                        .get()
                        .writableStore(WritableScheduleStoreImpl.class)
                        .delete(transactions.get(currentIndex).scheduleId(), Instant.now());

                transactions.remove(currentIndex);
                currentIndex--; // Adjust index after removal

                // Reset lastReturned to prevent consecutive remove calls
                lastReturned = null;
            }

            private ExecutableTxn toExecutableTxn(Schedule schedule) {
                final var signatories = schedule.signatories();
                final VerificationAssistant callback = (k, ignore) -> signatories.contains(k);
                return new ExecutableTxn(
                        childAsOrdinary(schedule),
                        callback,
                        schedule.payerAccountId(),
                        Instant.ofEpochSecond(schedule.calculatedExpirationSecond()));
            }
        };
    }
}
