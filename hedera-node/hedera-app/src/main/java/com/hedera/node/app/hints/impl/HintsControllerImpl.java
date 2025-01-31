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

import static com.hedera.hapi.util.HapiUtils.asInstant;
import static com.hedera.node.app.hints.HintsService.partySizeForRosterNodeCount;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toMap;

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
import java.util.stream.IntStream;

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
        // Ensure we are up-to-date on any published hinTS keys we might need for this construction
        if (!construction.hasHintsScheme()) {
            final var cutoffTime = construction.hasPreprocessingStartTime()
                    ? asInstant(construction.preprocessingStartTimeOrThrow())
                    : Instant.MAX;
            publications.forEach(publication -> {
                if (!publication.adoptionTime().isAfter(cutoffTime)) {
                    maybeUpdateForHintsKey(publication);
                }
            });
        }
    }

    @Override
    public long constructionId() {
        return construction.constructionId();
    }

    @Override
    public boolean isStillInProgress() {
        return !construction.hasHintsScheme();
    }

    @Override
    public boolean hasNumParties(final int numParties) {
        return this.numParties == numParties;
    }

    @Override
    public void advanceConstruction(@NonNull final Instant now, @NonNull final WritableHintsStore hintsStore) {
        requireNonNull(now);
        requireNonNull(hintsStore);
        if (construction.hasHintsScheme()) {
            return;
        }
        if (construction.hasPreprocessingStartTime()) {
            if (!votes.containsKey(selfId) && preprocessingVoteFuture == null) {
                preprocessingVoteFuture =
                        startPreprocessingVoteFuture(asInstant(construction.preprocessingStartTimeOrThrow()));
            }
        } else {
            throw new AssertionError("Not implemented");
        }
    }

    @Override
    public @NonNull OptionalInt partyIdOf(final long nodeId) {
        if (!weights.targetIncludes(nodeId)) {
            return OptionalInt.empty();
        }
        return nodePartyIds.containsKey(nodeId)
                ? OptionalInt.of(nodePartyIds.get(nodeId))
                : OptionalInt.of(expectedPartyId(nodeId));
    }

    @Override
    public void addHintsKeyPublication(@NonNull final HintsKeyPublication publication) {
        requireNonNull(publication);
        // If grace period is over, we have either finished construction or already set the
        // preprocessing time to something earlier than consensus now; so we will not use
        // this key and can return immediately
        if (!construction.hasGracePeriodEndTime()) {
            return;
        }
        maybeUpdateForHintsKey(publication);
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

    /**
     * If the publication is for the expected party id, update the node and party id mappings and
     * start a validation future for the hinTS key.
     * @param publication the publication
     */
    private void maybeUpdateForHintsKey(@NonNull final HintsKeyPublication publication) {
        final int partyId = publication.partyId();
        final long nodeId = publication.nodeId();
        if (partyId == expectedPartyId(nodeId)) {
            nodePartyIds.put(nodeId, partyId);
            partyNodeIds.put(partyId, nodeId);
            validationFutures.put(publication.adoptionTime(), validationFuture(partyId, publication.hintsKey()));
        }
    }

    /**
     * Returns the party ID that this node should use in the target roster. These ids are assigned
     * by sorting the unassigned node ids and unused party ids in ascending order, and matching
     * node ids and party ids by their indexes in these lists.
     * <p>
     * For example, suppose there are three nodes with ids {@code 7}, {@code 9}, and {@code 12};
     * and the party size is four (hence party ids are {@code 0}, {@code 1}, {@code 2}, and {@code 3}).
     * Then we can think of two lists,
     * <ul>
     *     <Li>{@code (7, 9, 12)}</Li>
     *     <Li>{@code (0, 1, 2, 3)}</Li>
     * </ul>
     * And do three assignments: {@code 7 -> 0}, {@code 9 -> 1}, and {@code 12 -> 2}.
     * <p>
     * The important thing about this strategy is that it doesn't matter the <b>order</b> in
     * which we do the assignments. For example, if the nodes publish their keys in the order
     * {@code 9}, {@code 7}, {@code 12}, then after assigning {@code 9 -> 1}, the remaining
     * lists will be,
     * <ul>
     *     <Li>{@code (7, 12)}</Li>
     *     <Li>{@code (0, 2, 3)}</Li>
     * </ul>
     * And no matter which node publishes their key next, they still the same id as expected.
     * @throws IndexOutOfBoundsException if the node id has already been assigned a party id
     */
    private int expectedPartyId(final long nodeId) {
        final var unassignedNodeIds = weights.targetNodeWeights().keySet().stream()
                .filter(id -> !nodePartyIds.containsKey(id))
                .sorted()
                .toList();
        final var unusedPartyIds = IntStream.range(0, numParties)
                .filter(id -> !partyNodeIds.containsKey(id))
                .boxed()
                .toList();
        return unusedPartyIds.get(unassignedNodeIds.indexOf(nodeId));
    }

    /**
     * Returns a future that completes to a validation of the given hints key.
     *
     * @param partyId the party ID
     * @param hintsKey the hints key
     * @return the future
     */
    private CompletableFuture<Validation> validationFuture(final int partyId, @NonNull final Bytes hintsKey) {
        return CompletableFuture.supplyAsync(
                () -> {
                    final var isValid = library.validateHintsKey(hintsKey, partyId, numParties);
                    return new Validation(partyId, hintsKey, isValid);
                },
                executor);
    }

    /**
     * Returns a future that completes to the aggregated hinTS keys for this construction for
     * all valid published hinTS keys.
     *
     * @return the future
     */
    private CompletableFuture<Void> startPreprocessingVoteFuture(@NonNull final Instant cutoff) {
        return CompletableFuture.runAsync(
                () -> {
                    final var hintKeys = validationFutures.headMap(cutoff, true).values().stream()
                            .map(CompletableFuture::join)
                            .filter(Validation::isValid)
                            .collect(toMap(Validation::partyId, Validation::hintsKey));
                    final var aggregatedWeights = nodePartyIds.entrySet().stream()
                            .filter(entry -> hintKeys.containsKey(entry.getValue()))
                            .collect(toMap(Map.Entry::getValue, entry -> weights.targetWeightOf(entry.getKey())));
                    final var output = library.preprocess(hintKeys, aggregatedWeights, numParties);
                    final var preprocessedKeys = codec.decodePreprocessedKeys(output);
                    submissions
                            .submitHintsVote(construction.constructionId(), preprocessedKeys)
                            .join();
                },
                executor);
    }
}
