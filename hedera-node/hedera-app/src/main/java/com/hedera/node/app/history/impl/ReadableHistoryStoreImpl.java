/*
 * Copyright (C) 2025 Hedera Hashgraph, LLC
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
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.state.history.ConstructionNodeId;
import com.hedera.hapi.node.state.history.HistoryProofConstruction;
import com.hedera.hapi.node.state.history.HistoryProofVote;
import com.hedera.hapi.node.state.history.ProofKeySet;
import com.hedera.hapi.node.state.history.RecordedHistorySignature;
import com.hedera.hapi.node.state.primitives.ProtoBytes;
import com.hedera.hapi.platform.state.NodeId;
import com.hedera.node.app.history.ReadableHistoryStore;
import com.hedera.node.app.roster.ActiveRosters;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.state.spi.ReadableKVState;
import com.swirlds.state.spi.ReadableSingletonState;
import com.swirlds.state.spi.ReadableStates;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Default implementation of {@link ReadableHistoryStore}.
 */
public class ReadableHistoryStoreImpl implements ReadableHistoryStore {
    private final ReadableSingletonState<ProtoBytes> ledgerId;
    private final ReadableSingletonState<HistoryProofConstruction> nextConstruction;
    private final ReadableSingletonState<HistoryProofConstruction> activeConstruction;
    private final ReadableKVState<NodeId, ProofKeySet> proofKeySets;
    private final ReadableKVState<ConstructionNodeId, RecordedHistorySignature> signatures;
    private final ReadableKVState<ConstructionNodeId, HistoryProofVote> votes;

    public ReadableHistoryStoreImpl(@NonNull final ReadableStates states) {
        requireNonNull(states);
        this.ledgerId = states.getSingleton(LEDGER_ID_KEY);
        this.nextConstruction = states.getSingleton(NEXT_PROOF_CONSTRUCTION_KEY);
        this.activeConstruction = states.getSingleton(ACTIVE_PROOF_CONSTRUCTION_KEY);
        this.proofKeySets = states.get(PROOF_KEY_SETS_KEY);
        this.signatures = states.get(HISTORY_SIGNATURES_KEY);
        this.votes = states.get(PROOF_VOTES_KEY);
    }

    @Override
    public @Nullable Bytes getLedgerId() {
        final var maybeLedgerId = requireNonNull(ledgerId.get()).value();
        return Bytes.EMPTY.equals(maybeLedgerId) ? null : maybeLedgerId;
    }

    @Override
    public @NonNull HistoryProofConstruction getActiveConstruction() {
        throw new AssertionError("Not implemented");
    }

    @Override
    public @Nullable HistoryProofConstruction getConstructionFor(@NonNull final ActiveRosters activeRosters) {
        requireNonNull(activeRosters);
        throw new AssertionError("Not implemented");
    }

    @Override
    public @NonNull Map<Long, HistoryProofVote> getVotes(final long constructionId, @NonNull final Set<Long> nodeIds) {
        requireNonNull(nodeIds);
        return Map.of();
    }

    @Override
    public @NonNull List<ProofKeyPublication> getProofKeyPublications(@NonNull final Set<Long> nodeIds) {
        requireNonNull(nodeIds);
        return List.of();
    }

    @Override
    public @NonNull List<HistorySignaturePublication> getSignaturePublications(
            final long constructionId, @NonNull final Set<Long> nodeIds) {
        requireNonNull(nodeIds);
        return List.of();
    }
}
