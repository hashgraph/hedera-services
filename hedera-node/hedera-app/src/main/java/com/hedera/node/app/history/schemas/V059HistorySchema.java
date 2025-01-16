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

package com.hedera.node.app.history.schemas;

import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.SemanticVersion;
import com.hedera.hapi.node.state.history.ConstructionNodeId;
import com.hedera.hapi.node.state.history.HistoryProof;
import com.hedera.hapi.node.state.history.HistoryProofConstruction;
import com.hedera.hapi.node.state.history.HistoryProofVote;
import com.hedera.hapi.node.state.history.ProofKeySet;
import com.hedera.hapi.node.state.history.RecordedHistorySignature;
import com.hedera.hapi.node.state.primitives.ProtoBytes;
import com.hedera.hapi.platform.state.NodeId;
import com.hedera.node.app.history.HistoryService;
import com.swirlds.state.lifecycle.MigrationContext;
import com.swirlds.state.lifecycle.Schema;
import com.swirlds.state.lifecycle.StateDefinition;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Registers the states needed for the {@link HistoryService}; these are,
 * <ul>
 *     <li>A singleton with the ledger id (that is, the hash of the genesis
 *     proof roster).</li>
 *     <li>A singleton with the active {@link HistoryProofConstruction}; must
 *     be at least ongoing once the network is active (and until complete, the
 *     history service will not be able to prove the metadata scoped to the
 *     current roster is derived from the ledger id).</li>
 *     <li>A singleton with the next {@link HistoryProofConstruction}; may or
 *     may not be ongoing, as there may not be a candidate roster set.</li>
 *     <li>A map from node id to the node's timestamped proof key; and,
 *     if applicable, the key it wants to start using for all constructions
 *     that begin after the current one ends.</li>
 *     <li>A map from pair of node id and construction id to the node's
 *     signature on its assembled history of proof roster hash and metadata
 *     for that construction.</li>
 *     <li>A map from pair of node id and construction id to the node's
 *     vote for the metadata proof for that construction.</li>
 * </ul>
 */
public class V059HistorySchema extends Schema {
    private static final SemanticVersion VERSION =
            SemanticVersion.newBuilder().minor(59).build();

    private static final long MAX_PROOF_KEYS = 1L << 21;
    private static final long MAX_ASSEMBLY_SIGNATURES = MAX_PROOF_KEYS;
    private static final long MAX_PROOF_VOTES = MAX_ASSEMBLY_SIGNATURES;

    public static final String LEDGER_ID_KEY = "LEDGER_ID";
    public static final String PROOF_KEY_SETS_KEY = "PROOF_KEY_SETS";
    public static final String ACTIVE_CONSTRUCTION_KEY = "ACTIVE_CONSTRUCTION";
    public static final String NEXT_CONSTRUCTION_KEY = "NEXT_CONSTRUCTION";
    public static final String HISTORY_SIGNATURES_KEY = "HISTORY_SIGNATURES";
    public static final String PROOF_VOTES_KEY = "PROOF_VOTES";

    private final Consumer<HistoryProof> proofConsumer;

    public V059HistorySchema(@NonNull final Consumer<HistoryProof> proofConsumer) {
        super(VERSION);
        this.proofConsumer = requireNonNull(proofConsumer);
    }

    @Override
    public @NonNull Set<StateDefinition> statesToCreate() {
        return Set.of(
                StateDefinition.singleton(LEDGER_ID_KEY, ProtoBytes.PROTOBUF),
                StateDefinition.singleton(ACTIVE_CONSTRUCTION_KEY, HistoryProofConstruction.PROTOBUF),
                StateDefinition.singleton(NEXT_CONSTRUCTION_KEY, HistoryProofConstruction.PROTOBUF),
                StateDefinition.onDisk(PROOF_KEY_SETS_KEY, NodeId.PROTOBUF, ProofKeySet.PROTOBUF, MAX_PROOF_KEYS),
                StateDefinition.onDisk(
                        HISTORY_SIGNATURES_KEY,
                        ConstructionNodeId.PROTOBUF,
                        RecordedHistorySignature.PROTOBUF,
                        MAX_ASSEMBLY_SIGNATURES),
                StateDefinition.onDisk(
                        PROOF_VOTES_KEY, ConstructionNodeId.PROTOBUF, HistoryProofVote.PROTOBUF, MAX_PROOF_VOTES));
    }

    @Override
    public void migrate(@NonNull final MigrationContext ctx) {
        final var states = ctx.newStates();
        states.<ProtoBytes>getSingleton(LEDGER_ID_KEY).put(ProtoBytes.DEFAULT);
        states.<HistoryProofConstruction>getSingleton(ACTIVE_CONSTRUCTION_KEY).put(HistoryProofConstruction.DEFAULT);
        states.<HistoryProofConstruction>getSingleton(NEXT_CONSTRUCTION_KEY).put(HistoryProofConstruction.DEFAULT);
    }

    @Override
    public void restart(@NonNull final MigrationContext ctx) {
        final var states = ctx.previousStates();
        final var activeConstruction =
                requireNonNull(states.<HistoryProofConstruction>getSingleton(ACTIVE_CONSTRUCTION_KEY)
                        .get());
        if (activeConstruction.hasTargetProof()) {
            proofConsumer.accept(activeConstruction.targetProofOrThrow());
        }
    }
}
