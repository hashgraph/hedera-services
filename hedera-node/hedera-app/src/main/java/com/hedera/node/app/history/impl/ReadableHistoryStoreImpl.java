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

import com.hedera.hapi.node.state.history.HistoryProofConstruction;
import com.hedera.hapi.node.state.history.HistoryProofVote;
import com.hedera.node.app.history.ReadableHistoryStore;
import com.hedera.node.app.roster.ActiveRosters;
import com.hedera.node.app.spi.workflows.FreeQueryHandler;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static java.util.Objects.requireNonNull;

public class ReadableHistoryStoreImpl implements ReadableHistoryStore {
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
    public @NonNull List<HistorySignaturePublication> getSignaturePublications(final long constructionId, @NonNull final Set<Long> nodeIds) {
        requireNonNull(nodeIds);
        return List.of();
    }
}
