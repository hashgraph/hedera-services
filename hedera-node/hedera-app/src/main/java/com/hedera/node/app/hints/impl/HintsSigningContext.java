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

import static com.hedera.node.app.roster.RosterTransitionWeights.moreThanTwoThirdsOfTotal;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toMap;

import com.hedera.hapi.node.state.hints.HintsConstruction;
import com.hedera.hapi.node.state.hints.NodePartyId;
import com.hedera.node.app.hints.HintsLibrary;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * The current hinTS signing context.
 */
@Singleton
public class HintsSigningContext {
    private final HintsLibrary operations;

    @Nullable
    private HintsConstruction construction;

    @Nullable
    private Map<Long, Integer> nodePartyIds;

    @Inject
    public HintsSigningContext(@NonNull final HintsLibrary operations) {
        this.operations = requireNonNull(operations);
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
    public Bytes activeVerificationKeyOrThrow() {
        throwIfNotReady();
        return requireNonNull(construction).preprocessedKeysOrThrow().verificationKey();
    }

    public long activeConstructionIdOrThrow() {
        throwIfNotReady();
        return requireNonNull(construction).constructionId();
    }

    /**
     * Creates a new asynchronous signing process for the given block hash.
     * @param blockHash the block hash
     * @return the signing process
     */
    public @NonNull HintsSigning newSigning(@NonNull final Bytes blockHash) {
        requireNonNull(blockHash);
        throwIfNotReady();
        final var aggregationKey =
                requireNonNull(construction).preprocessedKeysOrThrow().aggregationKey();
        return new HintsSigning(
                moreThanTwoThirdsOfTotal(operations.extractTotalWeight(aggregationKey)),
                construction.constructionId(),
                blockHash,
                aggregationKey,
                requireNonNull(nodePartyIds),
                operations);
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
}
