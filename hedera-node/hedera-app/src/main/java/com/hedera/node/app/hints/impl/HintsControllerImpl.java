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

import static com.hedera.node.app.hints.HintsService.partySizeForRosterNodeCount;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.state.hints.HintsConstruction;
import com.hedera.hapi.node.state.hints.PreprocessingVote;
import com.hedera.node.app.hints.HintsLibrary;
import com.hedera.node.app.hints.ReadableHintsStore.HintsKeyPublication;
import com.hedera.node.app.hints.WritableHintsStore;
import com.hedera.node.app.roster.RosterTransitionWeights;
import com.hedera.node.app.tss.TssKeyPair;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.OptionalInt;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Manages the process objects and work needed to advance toward completion of a hinTS construction.
 */
public class HintsControllerImpl implements HintsController {
    private final int numParties;
    private final long selfId;
    private final Executor executor;
    private final TssKeyPair blsKeyPair;
    private final HintsLibrary library;
    private final HintsLibraryCodec codec;
    private final HintsSubmissions submissions;
    private final HintsContext context;
    private final Map<Long, Integer> nodePartyIds = new HashMap<>();
    private final Map<Integer, Long> partyNodeIds = new HashMap<>();
    private final RosterTransitionWeights weights;
    private final Map<Long, PreprocessingVote> votes = new HashMap<>();
    private final NavigableMap<Instant, CompletableFuture<Validation>> validationFutures = new TreeMap<>();

    /**
     * The ongoing construction, updated each time the controller advances the construction in state.
     */
    private HintsConstruction construction;

    /**
     * If not null, a future that resolves when this node completes the preprocessing stage of this construction.
     */
    @Nullable
    private CompletableFuture<Void> preprocessingVoteFuture;

    /**
     * If not null, the future performing the hinTS key publication for this node.
     */
    @Nullable
    private CompletableFuture<Void> publicationFuture;

    /**
     * A party's validated hinTS key, including the key itself and whether it is valid.
     *
     * @param partyId the party ID
     * @param hintsKey the hinTS key
     * @param isValid whether the key is valid
     */
    private record Validation(int partyId, @NonNull Bytes hintsKey, boolean isValid) {}

    public HintsControllerImpl(
            final long selfId,
            @NonNull final TssKeyPair blsKeyPair,
            @NonNull final HintsConstruction construction,
            @NonNull final RosterTransitionWeights weights,
            @NonNull final Executor executor,
            @NonNull final HintsLibrary library,
            @NonNull final HintsLibraryCodec codec,
            @NonNull final Map<Long, PreprocessingVote> votes,
            @NonNull final List<HintsKeyPublication> publications,
            @NonNull final HintsSubmissions submissions,
            @NonNull final HintsContext context) {
        this.selfId = selfId;
        this.blsKeyPair = requireNonNull(blsKeyPair);
        this.weights = requireNonNull(weights);
        this.numParties = partySizeForRosterNodeCount(weights.targetRosterSize());
        this.executor = requireNonNull(executor);
        this.context = requireNonNull(context);
        this.submissions = requireNonNull(submissions);
        this.library = requireNonNull(library);
        this.codec = requireNonNull(codec);
        this.construction = requireNonNull(construction);
        this.votes.putAll(votes);
        publications.forEach(this::addHintsKeyPublication);
    }

    @Override
    public long constructionId() {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public boolean isStillInProgress() {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public boolean hasNumParties(final int numParties) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public void advanceConstruction(@NonNull final Instant now, @NonNull final WritableHintsStore hintsStore) {
        requireNonNull(now);
        requireNonNull(hintsStore);
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public @NonNull OptionalInt partyIdOf(final long nodeId) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public void addHintsKeyPublication(@NonNull final HintsKeyPublication publication) {
        requireNonNull(publication);
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public boolean addPreprocessingVote(
            final long nodeId, @NonNull final PreprocessingVote vote, @NonNull final WritableHintsStore hintsStore) {
        requireNonNull(vote);
        requireNonNull(hintsStore);
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public void cancelPendingWork() {
        throw new UnsupportedOperationException("Not yet implemented");
    }
}
