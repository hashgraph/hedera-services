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

package com.hedera.node.app.hints.handlers;

import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toMap;

import com.hedera.cryptography.bls.BlsPublicKey;
import com.hedera.hapi.node.state.hints.HintsKey;
import com.hedera.hapi.node.state.hints.PreprocessedKeys;
import com.hedera.node.app.hints.HintsOperations;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Function;

/**
 * Orchestrates the work to produce a deterministic {@link PreprocessedKeys} given an accumulated
 * set of {@link HintsKey} instances.
 * <p>
 * <b>IMPORTANT:</b> This class is not thread-safe; that is, its correct usage is to,
 * <ol>
 *     <li>Sequentially call {@link HintAggregator#incorporate(long, HintsKey)} for each
 *     party's received, yet-to-be-validated hint.</li>
 *     <li>Call {@link HintAggregator#aggregateFuture(Map)} to return a future that resolves to the
 *     final {@link PreprocessedKeys}.</li>
 * </ol>
 */
public class HintAggregator {
    private final int n;
    private final Executor executor;
    private final HintsOperations hintsOperations;
    private final Function<Bytes, BlsPublicKey> keyParser;
    private final Map<Long, CompletableFuture<Validation>> validations = new HashMap<>();

    private boolean locked = false;

    public HintAggregator(
            final int n,
            @NonNull final Executor executor,
            @NonNull final HintsOperations hintsOperations,
            @NonNull final Function<Bytes, BlsPublicKey> keyParser) {
        this.n = n;
        this.executor = requireNonNull(executor);
        this.hintsOperations = requireNonNull(hintsOperations);
        this.keyParser = requireNonNull(keyParser);
    }

    private record Validation(HintsKey hintsKey, boolean isValid) {}

    /**
     *
     * @param partyId
     * @param hintsKey
     */
    public void incorporate(final long partyId, @NonNull final HintsKey hintsKey) {
        requireNonNull(hintsKey);
        throwIfLocked();
        validations.put(
                partyId,
                CompletableFuture.supplyAsync(
                        () -> {
                            final var publicKey = keyParser.apply(hintsKey.publicKey());
                            final var isValid = hintsOperations.validateHints(hintsKey.hint(), publicKey, n);
                            return new Validation(hintsKey, isValid);
                        },
                        executor));
    }

    public CompletableFuture<PreprocessedKeys> aggregateFuture(@NonNull final Map<Long, Long> weights) {
        throwIfLocked();
        locked = true;
        return CompletableFuture.allOf(validations.values().toArray(CompletableFuture[]::new))
                .thenApplyAsync(
                        ignore -> {
                            final var hintKeys = validations.entrySet().stream()
                                    .filter(entry -> entry.getValue().join().isValid())
                                    .collect(toMap(
                                            Map.Entry::getKey,
                                            entry -> entry.getValue().join().hintsKey()));
                            return hintsOperations.aggregate(hintKeys, weights, n);
                        },
                        executor);
    }

    private void throwIfLocked() {
        if (locked) {
            throw new IllegalStateException("Aggregation already started");
        }
    }
}
