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

package com.hedera.node.app.hints;

import static java.util.Objects.requireNonNull;

import com.hedera.cryptography.bls.BlsPublicKey;
import com.hedera.hapi.node.state.hints.HintsConstruction;
import com.hedera.hapi.node.state.hints.HintsKey;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Function;

/**
 * Manages the process objects and work needed to advance toward completion of a hinTS construction.
 */
public class HintsConstructionController {
    private final int M;
    private final long sourceWeightThreshold;
    private final long targetWeightThreshold;
    private final Executor executor;
    private final Duration hintKeysWaitTime;
    private final HintsOperations operations;
    private final Map<Long, Long> sourceNodeWeights;
    private final Map<Long, Long> targetNodeWeights;
    private final HintsConstruction construction;
    private final Map<Long, Long> nodePartyIds = new HashMap<>();
    private final Function<Bytes, BlsPublicKey> keyParser;
    private final Map<Long, CompletableFuture<HintsKeyValidation>> hintsKeyValidations = new HashMap<>();

    private record HintsKeyValidation(@NonNull HintsKey hintsKey, boolean isValid) {}

    public HintsConstructionController(
            @NonNull final Executor executor,
            @NonNull final Duration hintKeysWaitTime,
            @NonNull final HintsOperations operations,
            @NonNull final Map<Long, Long> sourceNodeWeights,
            @NonNull final Map<Long, Long> targetNodeWeights,
            @NonNull final HintsConstruction construction,
            @NonNull final Map<Long, Long> nodePartyIds,
            @NonNull final Map<Long, HintsKey> hintsKeys,
            @NonNull final Function<Bytes, BlsPublicKey> keyParser) {
        this.executor = requireNonNull(executor);
        this.keyParser = requireNonNull(keyParser);
        this.operations = requireNonNull(operations);
        this.construction = requireNonNull(construction);
        this.hintKeysWaitTime = requireNonNull(hintKeysWaitTime);
        this.sourceNodeWeights = requireNonNull(sourceNodeWeights);
        this.sourceWeightThreshold = strongMinorityWeightFor(sourceNodeWeights);
        this.targetNodeWeights = requireNonNull(targetNodeWeights);
        this.targetWeightThreshold = strongMinorityWeightFor(targetNodeWeights);
        this.nodePartyIds.putAll(requireNonNull(nodePartyIds));
        this.M = partySizeForRosterNodeCount(targetNodeWeights.size());
        hintsKeys.forEach((partyId, hintsKey) -> hintsKeyValidations.put(partyId, validateFuture(hintsKey)));
    }

    /**
     * Acts deterministically relative to the given state to advance its hinTS construction toward completion.
     * @param now the current consensus time
     * @param hintsStore the hints store, in case the controller is able to complete the construction
     */
    public void advanceConstruction(@NonNull final Instant now, @NonNull final WritableHintsStore hintsStore) {
        throw new AssertionError("Not implemented");
    }

    /**
     * Incorporates a new hint key into the controller's state.
     * @param nodeId the node ID
     * @param partyId the party ID
     * @param hintsKey the hint key
     */
    public void incorporateNewHint(final long nodeId, final long partyId, @NonNull final HintsKey hintsKey) {
        throw new AssertionError("Not implemented");
    }

    /**
     * Cancels any pending work that this controller has scheduled.
     */
    public void cancelPendingWork() {
        throw new AssertionError("Not implemented");
    }

    /**
     * Returns the ID of the hinTS construction that this controller is managing.
     */
    public long constructionId() {
        throw new AssertionError("Not implemented");
    }

    /**
     * Returns the source roster hash of the hinTS construction that this controller is managing.
     */
    @Nullable
    public Bytes sourceRosterHash() {
        throw new AssertionError("Not implemented");
    }

    /**
     * Returns the target roster hash of the hinTS construction that this controller is managing.
     */
    @NonNull
    public Bytes targetRosterHash() {
        throw new AssertionError("Not implemented");
    }

    /**
     * Returns a future that completes to a validation of the given hints key.
     * @param hintsKey the hints key
     * @return the future
     */
    private CompletableFuture<HintsKeyValidation> validateFuture(@NonNull final HintsKey hintsKey) {
        return CompletableFuture.supplyAsync(
                () -> {
                    final var isValid =
                            operations.validateHints(keyParser.apply(hintsKey.publicKey()), hintsKey.hint(), M);
                    return new HintsKeyValidation(hintsKey, isValid);
                },
                executor);
    }

    /**
     * Returns the weight that would constitute a strong minority of the network weight for a roster.
     * @param weights the weights of the nodes in the roster
     * @return the weight required for a strong minority
     */
    private static long strongMinorityWeightFor(@NonNull final Map<Long, Long> weights) {
        final var weight = weights.values().stream().mapToLong(Long::longValue).sum();
        // Since aBFT is unachievable with n/3 malicious weight, using the conclusion of n/3 weight
        // ensures it the conclusion overlaps with the weight held by at least one honest node
        return (weight + 2) / 3;
    }

    /**
     * Returns the party size {@code M=2^k} such that the given roster node count will fall inside the
     * range {@code [2*(k-1), 2^k)}.
     * @param n the roster node count
     * @return the party size
     */
    private static int partySizeForRosterNodeCount(int n) {
        n++;
        if ((n & (n - 1)) == 0) {
            return n;
        }
        return Integer.highestOneBit(n) << 1;
    }
}
