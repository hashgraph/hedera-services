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

import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.state.schedule.Schedule;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.schedule.ScheduleService;
import com.hedera.node.app.service.schedule.impl.schemas.V0490ScheduleSchema;
import com.hedera.node.app.spi.RpcService;
import com.hedera.node.app.spi.signatures.VerificationAssistant;
import com.hedera.node.app.spi.store.StoreFactory;
import com.swirlds.state.spi.SchemaRegistry;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Instant;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Standard implementation of the {@link ScheduleService} {@link RpcService}.
 */
public final class ScheduleServiceImpl implements ScheduleService {
    @Override
    public void registerSchemas(@NonNull final SchemaRegistry registry) {
        registry.register(new V0490ScheduleSchema());
    }

    @Override
    public Iterator<ExecutableTxn> iterTxnsForInterval(
            Instant start,
            Instant end,
            Supplier<StoreFactory> cleanupStoreFactory,
            Function<TransactionBody, Set<Key>> txnToRequiredKeysFn) {
        var store = cleanupStoreFactory.get().writableStore(WritableScheduleStoreImpl.class);
        // Filter transactions that are not executed/deleted and have all the required keys
        var executableTxns = store.getByExpirationBetween(start.getEpochSecond(), end.getEpochSecond()).stream()
                .filter(schedule -> new HashSet<>(schedule.signatories())
                        .containsAll(txnToRequiredKeysFn.apply(childAsOrdinary(schedule))))
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
                store.delete(transactions.get(currentIndex).scheduleId(), Instant.now());

                transactions.remove(currentIndex);
                currentIndex--; // Adjust index after removal

                // Reset lastReturned to prevent consecutive remove calls
                lastReturned = null;
            }

            private ExecutableTxn toExecutableTxn(Schedule schedule) {
                final var signatories = new HashSet<>(schedule.signatories());
                final VerificationAssistant callback = (k, ignore) -> signatories.contains(k);
                //                var keyVerifier = new KeyVerifier(){
                //
                //                    @Override
                //                    public @NonNull SignatureVerification verificationFor(@NonNull Key key) {
                //                        return verificationFor(key, callback);
                //                    }
                //
                //                    @Override
                //                    public @NonNull SignatureVerification verificationFor(@NonNull Key key, @NonNull
                // VerificationAssistant callback) {
                //                        requireNonNull(key, "key must not be null");
                //                        requireNonNull(callback, "callback must not be null");
                //
                //                        return switch (key.key().kind()) {
                //                            case ED25519, ECDSA_SECP256K1 -> {
                //                                final var result = resolveFuture(keyVerifications.get(key), () ->
                // failedVerification(key));
                //                                yield callback.test(key, result) ? passedVerification(key) :
                // failedVerification(key);
                //                            }
                //                            case KEY_LIST -> {
                //                                final var keys = key.keyListOrThrow().keys();
                //                                var failed = keys.isEmpty();
                //                                for (final var childKey : keys) {
                //                                    failed |= verificationFor(childKey, callback).failed();
                //                                }
                //                                yield failed ? failedVerification(key) : passedVerification(key);
                //                            }
                //                            case THRESHOLD_KEY -> {
                //                                final var thresholdKey = key.thresholdKeyOrThrow();
                //                                final var keyList = thresholdKey.keysOrElse(KeyList.DEFAULT);
                //                                final var keys = keyList.keys();
                //                                final var threshold = thresholdKey.threshold();
                //                                final var clampedThreshold = Math.max(1, Math.min(threshold,
                // keys.size()));
                //                                var passed = 0;
                //                                for (final var childKey : keys) {
                //                                    if (verificationFor(childKey, callback).passed()) {
                //                                        passed++;
                //                                    }
                //                                }
                //                                yield passed >= clampedThreshold ? passedVerification(key) :
                // failedVerification(key);
                //                            }
                //                            case CONTRACT_ID, DELEGATABLE_CONTRACT_ID, ECDSA_384, RSA_3072, UNSET -> {
                //                                final var failure = failedVerification(key);
                //                                yield callback.test(key, failure) ? passedVerification(key) : failure;
                //                            }
                //                        };
                //                    }
                //                };
                //                return new ExecutableTxn(
                //                        childAsOrdinary(schedule),
                //                        keyVerifier,
                //                        Instant.ofEpochSecond(schedule.calculatedExpirationSecond()));
                return null;
            }
        };
    }
}
