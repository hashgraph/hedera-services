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

package com.hedera.node.app.hints.impl;

import static com.hedera.hapi.util.HapiUtils.asInstant;
import static com.hedera.node.app.hints.schemas.V059HintsSchema.ACTIVE_CONSTRUCTION_KEY;
import static com.hedera.node.app.hints.schemas.V059HintsSchema.HINTS_KEY;
import static com.hedera.node.app.hints.schemas.V059HintsSchema.NEXT_CONSTRUCTION_KEY;
import static com.hedera.node.app.hints.schemas.V059HintsSchema.PREPROCESSING_VOTES_KEY;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.state.hints.HintsConstruction;
import com.hedera.hapi.node.state.hints.HintsId;
import com.hedera.hapi.node.state.hints.HintsKeySet;
import com.hedera.hapi.node.state.hints.PreprocessVoteId;
import com.hedera.hapi.node.state.hints.PreprocessedKeysVote;
import com.hedera.node.app.hints.ReadableHintsStore;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.state.spi.ReadableKVState;
import com.swirlds.state.spi.ReadableSingletonState;
import com.swirlds.state.spi.ReadableStates;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Provides read access to the {@link HintsConstruction} and {@link PreprocessedKeysVote} instances in state.
 */
public class ReadableHintsStoreImpl implements ReadableHintsStore {
    private final ReadableKVState<HintsId, HintsKeySet> hintsKeys;
    private final ReadableSingletonState<HintsConstruction> nextConstruction;
    private final ReadableSingletonState<HintsConstruction> activeConstruction;
    private final ReadableKVState<PreprocessVoteId, PreprocessedKeysVote> votes;

    public ReadableHintsStoreImpl(@NonNull final ReadableStates states) {
        requireNonNull(states);
        this.hintsKeys = states.get(HINTS_KEY);
        this.nextConstruction = states.getSingleton(NEXT_CONSTRUCTION_KEY);
        this.activeConstruction = states.getSingleton(ACTIVE_CONSTRUCTION_KEY);
        this.votes = states.get(PREPROCESSING_VOTES_KEY);
    }

    @Override
    public @Nullable Bytes getVerificationKeyFor(@NonNull final Bytes targetRosterHash) {
        requireNonNull(targetRosterHash);
        HintsConstruction construction;
        if ((construction = requireNonNull(activeConstruction.get())).hasPreprocessedKeys()) {
            return construction.preprocessedKeysOrThrow().verificationKey();
        } else if ((construction = requireNonNull(nextConstruction.get())).hasPreprocessedKeys()) {
            return construction.preprocessedKeysOrThrow().verificationKey();
        }
        return null;
    }

    @Override
    public HintsConstruction getActiveConstruction() {
        return requireNonNull(activeConstruction.get());
    }

    @Override
    public @Nullable HintsConstruction getConstructionFor(
            @Nullable final Bytes sourceRosterHash, @NonNull final Bytes targetRosterHash) {
        requireNonNull(targetRosterHash);
        HintsConstruction construction;
        if (constructionIsFor(
                construction = requireNonNull(activeConstruction.get()), sourceRosterHash, targetRosterHash)) {
            return construction;
        } else if (constructionIsFor(
                construction = requireNonNull(nextConstruction.get()), sourceRosterHash, targetRosterHash)) {
            return construction;
        }
        return null;
    }

    @Override
    public @NonNull Map<Long, PreprocessedKeysVote> votesFor(
            final long constructionId, @NonNull final Set<Long> nodeIds) {
        requireNonNull(nodeIds);
        final Map<Long, PreprocessedKeysVote> scopedVotes = new HashMap<>();
        for (final var nodeId : nodeIds) {
            final var vote = votes.get(new PreprocessVoteId(constructionId, nodeId));
            if (vote != null) {
                scopedVotes.put(nodeId, vote);
            }
        }
        return scopedVotes;
    }

    @Override
    public @NonNull List<HintsKeyPublication> publicationsForMaxSizeLog2(
            final int k, @NonNull final Set<Long> nodeIds) {
        requireNonNull(nodeIds);
        final int M = 1 << k;
        final List<HintsKeyPublication> publications = new ArrayList<>();
        for (long partyId = 0; partyId < M; partyId++) {
            final var keySet = hintsKeys.get(new HintsId(partyId, k));
            if (keySet != null) {
                if (nodeIds.contains(keySet.nodeId())) {
                    publications.add(new HintsKeyPublication(
                            keySet.keyOrThrow(), keySet.nodeId(), partyId, asInstant(keySet.adoptionTimeOrThrow())));
                }
            }
        }
        return publications;
    }

    private boolean constructionIsFor(
            @NonNull final HintsConstruction construction,
            @Nullable final Bytes sourceRosterHash,
            @NonNull final Bytes targetRosterHash) {
        return Optional.ofNullable(sourceRosterHash).orElse(Bytes.EMPTY).equals(construction.sourceRosterHash())
                && targetRosterHash.equals(construction.targetRosterHash());
    }
}
