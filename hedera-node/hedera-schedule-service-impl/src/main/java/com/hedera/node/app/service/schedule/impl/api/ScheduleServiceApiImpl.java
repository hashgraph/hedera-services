/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.schedule.impl.api;

import static com.hedera.node.app.service.schedule.impl.handlers.HandlerUtility.childAsOrdinary;

import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.state.schedule.Schedule;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.schedule.WritableScheduleStore;
import com.hedera.node.app.service.schedule.api.ScheduleServiceApi;
import com.hedera.node.app.service.schedule.impl.WritableScheduleStoreImpl;
import com.hedera.node.app.spi.key.KeyVerifier;
import com.hedera.node.app.spi.metrics.StoreMetricsService;
import com.hedera.node.app.spi.signatures.VerificationAssistant;
import com.swirlds.config.api.Configuration;
import com.swirlds.state.spi.WritableStates;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Instant;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.function.Function;

public class ScheduleServiceApiImpl implements ScheduleServiceApi {

    private final WritableScheduleStore scheduleStore;

    ScheduleServiceApiImpl(
            @NonNull Configuration configuration,
            @NonNull StoreMetricsService storeMetricsService,
            @NonNull WritableStates writableStates) {
        this.scheduleStore = new WritableScheduleStoreImpl(writableStates, configuration, storeMetricsService);
    }

    @Override
    public Iterator<ExecutableTxn> iterTxnsForInterval(
            Instant start, Instant end, Function<TransactionBody, Set<Key>> requiredKeysFn, KeyVerifier keyVerifier) {
        // Filter transactions that are not executed/deleted and have all the required keys
        var executableTxns = scheduleStore.getByExpirationBetween(start.getEpochSecond(), end.getEpochSecond()).stream()
                .filter(schedule -> !schedule.executed() && !schedule.deleted())
                .filter(schedule -> hasAllRequiredKeys(schedule, requiredKeysFn, keyVerifier))
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
                scheduleStore.delete(transactions.get(currentIndex).scheduleId(), Instant.now());

                transactions.remove(currentIndex);
                currentIndex--; // Adjust index after removal

                // Reset lastReturned to prevent consecutive remove calls
                lastReturned = null;
            }

            private ExecutableTxn toExecutableTxn(Schedule schedule) {
                return new ExecutableTxn(
                        childAsOrdinary(schedule), Instant.ofEpochSecond(schedule.calculatedExpirationSecond()));
            }
        };
    }

    private boolean hasAllRequiredKeys(
            Schedule schedule, Function<TransactionBody, Set<Key>> requiredKeysFn, KeyVerifier keyVerifier) {
        final var signatories = new HashSet<>(schedule.signatories());
        final var transactionBody = childAsOrdinary(schedule);
        final var requiredKeys = requiredKeysFn.apply(transactionBody);
        final VerificationAssistant callback = (k, ignore) -> signatories.contains(k);
        final var remainingKeys = new HashSet<>(requiredKeys);
        remainingKeys.removeIf(
                k -> keyVerifier.verificationFor(k, callback).passed());
        return remainingKeys.isEmpty();
    }
}
