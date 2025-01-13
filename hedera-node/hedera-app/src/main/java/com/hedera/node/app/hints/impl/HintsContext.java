/*
 * Copyright (C) 2024-2025 Hedera Hashgraph, LLC
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

package com.hedera.node.app.hints.impl;

import static com.hedera.node.app.roster.RosterTransitionWeights.atLeastOneThirdOfTotal;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toMap;

import com.hedera.cryptography.bls.BlsSignature;
import com.hedera.hapi.node.state.hints.HintsConstruction;
import com.hedera.hapi.node.state.hints.NodePartyId;
import com.hedera.node.app.hints.HintsLibrary;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * The current hinTS context.
 */
@Singleton
public class HintsContext {
    private final HintsLibrary library;

    @Nullable
    private HintsConstruction construction;

    @Nullable
    private Map<Long, Integer> nodePartyIds;

    @Inject
    public HintsContext(@NonNull final HintsLibrary library) {
        this.library = requireNonNull(library);
    }

    /**
     * Sets the active hinTS construction as the signing context. Called in three places,
     * <ol>
     *     <li>In the startup phase, when initializing from a state whose active hinTS
     *     construction had already finished its preprocessing work.</li>
     *     <li>In the bootstrap runtime phase, on finishing the preprocessing work for
     *     the genesis hinTS construction.</li>
     *     <li>In the normal runtime phase, in the first round after an upgrade, when
     *     swapping in a newly adopted roster's hinTS construction and purging votes for
     *     the previous construction.</li>
     * </ol>
     * @param construction the construction
     */
    public void setConstruction(@NonNull final HintsConstruction construction) {
        this.construction = requireNonNull(construction);
        this.nodePartyIds = asNodePartyIds(construction.nodePartyIds());
    }

    /**
     * Returns true if the signing context is ready.
     */
    public boolean isReady() {
        return construction != null;
    }

    /**
     * Returns the active verification key, or throws if the context is not ready.
     */
    public Bytes verificationKeyOrThrow() {
        throwIfNotReady();
        return requireNonNull(construction).preprocessedKeysOrThrow().verificationKey();
    }

    /**
     * Returns the active construction ID, or throws if the context is not ready.
     */
    public long constructionIdOrThrow() {
        throwIfNotReady();
        return requireNonNull(construction).constructionId();
    }

    /**
     * Creates a new asynchronous signing process for the given block hash.
     * @param blockHash the block hash
     * @return the signing process
     */
    public @NonNull Signing newSigning(@NonNull final Bytes blockHash) {
        requireNonNull(blockHash);
        throwIfNotReady();
        final var aggregationKey =
                requireNonNull(construction).preprocessedKeysOrThrow().aggregationKey();
        return new Signing(
                construction.constructionId(),
                atLeastOneThirdOfTotal(library.extractTotalWeight(aggregationKey)),
                blockHash,
                aggregationKey,
                requireNonNull(nodePartyIds),
                library);
    }

    /**
     * Returns the party assignments as a map of node IDs to party IDs.
     * @param nodePartyIds the party assignments
     * @return the map of node IDs to party IDs
     */
    private static Map<Long, Integer> asNodePartyIds(@NonNull final List<NodePartyId> nodePartyIds) {
        return nodePartyIds.stream().collect(toMap(NodePartyId::nodeId, NodePartyId::partyId));
    }

    /**
     * Throws an exception if the context is not ready.
     */
    private void throwIfNotReady() {
        if (!isReady()) {
            throw new IllegalStateException("Signing context not ready");
        }
    }

    /**
     * A particular hinTS signing happening in this context.
     */
    public static class Signing {
        private final long constructionId;
        private final long thresholdWeight;
        private final Bytes message;
        private final Bytes aggregationKey;
        private final Map<Long, Integer> partyIds;
        private final CompletableFuture<Bytes> future = new CompletableFuture<>();
        private final ConcurrentMap<Long, BlsSignature> signatures = new ConcurrentHashMap<>();
        private final AtomicLong weightOfSignatures = new AtomicLong();
        private final HintsLibrary library;

        public Signing(
                final long constructionId,
                final long thresholdWeight,
                @NonNull final Bytes message,
                @NonNull final Bytes aggregationKey,
                @NonNull final Map<Long, Integer> partyIds,
                @NonNull final HintsLibrary library) {
            this.constructionId = constructionId;
            this.thresholdWeight = thresholdWeight;
            this.message = requireNonNull(message);
            this.aggregationKey = requireNonNull(aggregationKey);
            this.partyIds = requireNonNull(partyIds);
            this.library = requireNonNull(library);
        }

        /**
         * The future that will complete when sufficient partial signatures have been aggregated.
         */
        public CompletableFuture<Bytes> future() {
            return future;
        }

        /**
         * Incorporate a partial signature into the aggregation.
         * @param constructionId the construction ID
         * @param nodeId the node ID
         * @param signature the partial signature
         */
        public void incorporate(final long constructionId, final long nodeId, @NonNull final BlsSignature signature) {
            requireNonNull(signature);
            if (this.constructionId == constructionId) {
                final var publicKey = library.extractPublicKey(aggregationKey, partyIds.get(nodeId));
                if (publicKey != null && library.verifyPartial(message, signature, publicKey)) {
                    signatures.put(nodeId, signature);
                    final var weight = library.extractWeight(aggregationKey, partyIds.get(nodeId));
                    if (weightOfSignatures.addAndGet(weight) >= thresholdWeight) {
                        future.complete(library.signAggregate(aggregationKey, signatures));
                    }
                }
            }
        }
    }
}
