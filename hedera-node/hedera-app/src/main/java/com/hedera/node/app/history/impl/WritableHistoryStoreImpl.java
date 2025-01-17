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

package com.hedera.node.app.history.impl;

import static com.hedera.node.app.history.schemas.V059HistorySchema.ACTIVE_PROOF_CONSTRUCTION_KEY;
import static com.hedera.node.app.history.schemas.V059HistorySchema.HISTORY_SIGNATURES_KEY;
import static com.hedera.node.app.history.schemas.V059HistorySchema.LEDGER_ID_KEY;
import static com.hedera.node.app.history.schemas.V059HistorySchema.NEXT_PROOF_CONSTRUCTION_KEY;
import static com.hedera.node.app.history.schemas.V059HistorySchema.PROOF_KEY_SETS_KEY;
import static com.hedera.node.app.history.schemas.V059HistorySchema.PROOF_VOTES_KEY;
import static com.hedera.node.app.roster.ActiveRosters.Phase.HANDOFF;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.state.history.ConstructionNodeId;
import com.hedera.hapi.node.state.history.HistoryProof;
import com.hedera.hapi.node.state.history.HistoryProofConstruction;
import com.hedera.hapi.node.state.history.HistoryProofVote;
import com.hedera.hapi.node.state.history.ProofKeySet;
import com.hedera.hapi.node.state.history.RecordedHistorySignature;
import com.hedera.hapi.node.state.primitives.ProtoBytes;
import com.hedera.hapi.platform.state.NodeId;
import com.hedera.node.app.history.WritableHistoryStore;
import com.hedera.node.app.roster.ActiveRosters;
import com.hedera.node.config.data.TssConfig;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.state.spi.WritableKVState;
import com.swirlds.state.spi.WritableSingletonState;
import com.swirlds.state.spi.WritableStates;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Instant;

/**
 * Default implementation of {@link WritableHistoryStore}.
 */
public class WritableHistoryStoreImpl extends ReadableHistoryStoreImpl implements WritableHistoryStore {
    private final WritableSingletonState<ProtoBytes> ledgerId;
    private final WritableSingletonState<HistoryProofConstruction> nextConstruction;
    private final WritableSingletonState<HistoryProofConstruction> activeConstruction;
    private final WritableKVState<NodeId, ProofKeySet> proofKeySets;
    private final WritableKVState<ConstructionNodeId, RecordedHistorySignature> signatures;
    private final WritableKVState<ConstructionNodeId, HistoryProofVote> votes;

    public WritableHistoryStoreImpl(@NonNull final WritableStates states) {
        super(states);
        this.ledgerId = states.getSingleton(LEDGER_ID_KEY);
        this.nextConstruction = states.getSingleton(NEXT_PROOF_CONSTRUCTION_KEY);
        this.activeConstruction = states.getSingleton(ACTIVE_PROOF_CONSTRUCTION_KEY);
        this.proofKeySets = states.get(PROOF_KEY_SETS_KEY);
        this.signatures = states.get(HISTORY_SIGNATURES_KEY);
        this.votes = states.get(PROOF_VOTES_KEY);
    }

    @Override
    public @NonNull HistoryProofConstruction getOrCreateConstruction(
            @NonNull final ActiveRosters activeRosters,
            @NonNull final Instant now,
            @NonNull final TssConfig tssConfig) {
        requireNonNull(activeRosters);
        requireNonNull(now);
        requireNonNull(tssConfig);
        final var phase = activeRosters.phase();
        if (phase == HANDOFF) {
            throw new IllegalArgumentException("Handoff phase has no construction");
        }

        throw new AssertionError("Not implemented");
    }

    @Override
    public void setLedgerId(@NonNull final Bytes bytes) {
        requireNonNull(bytes);
        ledgerId.put(new ProtoBytes(bytes));
    }

    @Override
    public HistoryProofConstruction setAssemblyTime(final long constructionId, @NonNull final Instant now) {
        requireNonNull(now);
        throw new AssertionError("Not implemented");
    }

    @Override
    public HistoryProofConstruction completeProof(final long constructionId, @NonNull final HistoryProof proof) {
        requireNonNull(proof);
        throw new AssertionError("Not implemented");
    }

    @Override
    public boolean purgeStateAfterHandoff(@NonNull final ActiveRosters activeRosters) {
        throw new AssertionError("Not implemented");
    }
}
