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
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.summingLong;
import static java.util.stream.Collectors.toMap;

import com.hedera.cryptography.bls.BlsKeyPair;
import com.hedera.hapi.node.state.hints.HintsConstruction;
import com.hedera.hapi.node.state.hints.HintsKey;
import com.hedera.hapi.node.state.hints.PreprocessingVote;
import com.hedera.hapi.services.auxiliary.hints.HintsKeyPublicationTransactionBody;
import com.hedera.hapi.services.auxiliary.hints.HintsPreprocessingVoteTransactionBody;
import com.hedera.node.app.hints.HintsLibrary;
import com.hedera.node.app.hints.ReadableHintsStore.HintsKeyPublication;
import com.hedera.node.app.hints.WritableHintsStore;
import com.hedera.node.app.roster.RosterTransitionWeights;
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
public class ActiveHintsController implements HintsController {
    private final int numParties;
    private final long selfId;
    private final Executor executor;
    private final BlsKeyPair blsKeyPair;
    private final HintsLibrary library;
    private final HintsSubmissions submissions;
    private final HintsContext signingContext;
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
    private record Validation(int partyId, @NonNull HintsKey hintsKey, boolean isValid) {}

    public ActiveHintsController(
            final long selfId,
            @NonNull final HintsConstruction construction,
            @NonNull final RosterTransitionWeights weights,
            @NonNull final Executor executor,
            @NonNull final BlsKeyPair blsKeyPair,
            @NonNull final HintsLibrary library,
            @NonNull final List<HintsKeyPublication> publications,
            @NonNull final Map<Long, PreprocessingVote> votes,
            @NonNull final HintsSubmissions submissions,
            @NonNull final HintsContext signingContext) {
        this.selfId = selfId;
        this.blsKeyPair = requireNonNull(blsKeyPair);
        this.weights = requireNonNull(weights);
        this.numParties = partySizeForRosterNodeCount(weights.targetRosterSize());
        this.executor = requireNonNull(executor);
        this.signingContext = requireNonNull(signingContext);
        this.submissions = requireNonNull(submissions);
        this.library = requireNonNull(library);
        this.construction = requireNonNull(construction);
        this.votes.putAll(votes);
        publications.forEach(this::addHintsKeyPublication);
    }

    @Override
    public long constructionId() {
        return construction.constructionId();
    }

    @Override
    public boolean isStillInProgress() {
        return !construction.hasPreprocessedKeys();
    }

    @Override
    public boolean hasNumParties(final int numParties) {
        return this.numParties == numParties;
    }

    @Override
    public void advanceConstruction(@NonNull final Instant now, @NonNull final WritableHintsStore hintsStore) {
        requireNonNull(now);
        requireNonNull(hintsStore);
        if (construction.hasPreprocessedKeys()) {
            return;
        }
        if (construction.hasPreprocessingStartTime()) {
            if (!votes.containsKey(selfId) && preprocessingVoteFuture == null) {
                preprocessingVoteFuture =
                        startPreprocessingVoteFuture(asInstant(construction.preprocessingStartTimeOrThrow()));
            }
        } else {
            if (shouldStartPreprocessing(now)) {
                construction = hintsStore.setPreprocessingStartTime(construction.constructionId(), now);
                preprocessingVoteFuture = startPreprocessingVoteFuture(now);
            } else {
                ensureHintsKeyPublished();
            }
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
        // Ignore hinTS keys published after the construction
        if (construction.hasPreprocessingStartTime()) {
            return;
        }
        final long nodeId = publication.nodeId();
        // Ignore hinTS keys from nodes not in the target roster
        if (!weights.targetIncludes(nodeId)) {
            return;
        }
        final int partyId = publication.partyId();
        final int expectedPartyId = expectedPartyId(nodeId);
        if (partyId != expectedPartyId) {
            throw new IllegalArgumentException(
                    "Expected party #" + expectedPartyId + " for node" + nodeId + "; but was party #" + partyId);
        }
        nodePartyIds.put(nodeId, partyId);
        partyNodeIds.put(partyId, nodeId);
        validationFutures.put(publication.adoptionTime(), validationFuture(partyId, publication.hintsKey()));
    }

    @Override
    public void addPreprocessingVote(
            final long nodeId, @NonNull final PreprocessingVote vote, @NonNull final WritableHintsStore hintsStore) {
        requireNonNull(vote);
        requireNonNull(hintsStore);
        if (!construction.hasPreprocessedKeys()) {
            votes.put(nodeId, vote);
            final var outputWeights = votes.entrySet().stream()
                    .collect(groupingBy(
                            entry -> entry.getValue().preprocessedKeysOrThrow(),
                            summingLong(entry -> weights.sourceWeightOf(entry.getKey()))));
            final var maybeWinningOutputs = outputWeights.entrySet().stream()
                    .filter(entry -> entry.getValue() >= weights.sourceWeightThreshold())
                    .map(Map.Entry::getKey)
                    .findFirst();
            maybeWinningOutputs.ifPresent(keys -> {
                construction = hintsStore.setPreprocessedKeys(construction.constructionId(), keys, nodePartyIds);
                if (hintsStore.getActiveConstruction().constructionId() == construction.constructionId()) {
                    signingContext.setConstruction(construction);
                }
            });
        }
    }

    @Override
    public void cancelPendingWork() {
        if (publicationFuture != null) {
            publicationFuture.cancel(true);
        }
        if (preprocessingVoteFuture != null) {
            preprocessingVoteFuture.cancel(true);
        }
        validationFutures.values().forEach(future -> future.cancel(true));
    }

    /**
     * Applies a deterministic policy to choose a preprocessing behavior at the given time.
     *
     * @param now the current consensus time
     * @return the choice of preprocessing behavior
     */
    private boolean shouldStartPreprocessing(@NonNull final Instant now) {
        // If every active node in the target roster has published a hinTS key,
        // start preprocessing now; there is nothing else to wait for
        if (validationFutures.size() == weights.numTargetNodesInSource()) {
            return true;
        }
        if (now.isBefore(asInstant(construction.gracePeriodEndTimeOrThrow()))) {
            return false;
        } else {
            return weightOfValidHintsKeysAt(now) >= weights.targetWeightThreshold();
        }
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
                    final var keys = library.preprocess(hintKeys, aggregatedWeights, numParties);
                    final var body = HintsPreprocessingVoteTransactionBody.newBuilder()
                            .constructionId(construction.constructionId())
                            .vote(PreprocessingVote.newBuilder()
                                    .preprocessedKeys(keys)
                                    .build())
                            .build();
                    submissions.submitHintsVote(body).join();
                },
                executor);
    }

    /**
     * Returns the weight of the nodes in the target roster that have published valid hinTS keys up to the given time.
     * This is blocking because if we are reduced to checking this, we have already exhausted the grace period waiting
     * for hinTS key publications, and all the futures in this map are essentially guaranteed to be complete, meaning
     * the once-per-round check is very cheap to do.
     *
     * @param now the time up to which to consider hinTS keys
     * @return the weight of the nodes with valid hinTS keys
     */
    private long weightOfValidHintsKeysAt(@NonNull final Instant now) {
        return validationFutures.headMap(now, true).values().stream()
                .map(CompletableFuture::join)
                .filter(Validation::isValid)
                .mapToLong(validation -> weights.targetWeightOf(partyNodeIds.get(validation.partyId())))
                .sum();
    }

    /**
     * Returns a future that completes to a validation of the given hints key.
     *
     * @param partyId the party ID
     * @param hintsKey the hints key
     * @return the future
     */
    private CompletableFuture<Validation> validationFuture(final int partyId, @NonNull final HintsKey hintsKey) {
        return CompletableFuture.supplyAsync(
                () -> {
                    final var isValid = library.validateHintsKey(hintsKey, numParties);
                    return new Validation(partyId, hintsKey, isValid);
                },
                executor);
    }

    /**
     * If this node is part of the target construction and has not yet published (and is not currently publishing) its
     * hinTS key, then starts publishing it.
     */
    private void ensureHintsKeyPublished() {
        if (publicationFuture != null && weights.targetIncludes(selfId) && !nodePartyIds.containsKey(selfId)) {
            final int selfPartyId = expectedPartyId(selfId);
            publicationFuture = CompletableFuture.runAsync(
                    () -> {
                        final var hints = library.computeHints(blsKeyPair.privateKey(), selfPartyId, numParties);
                        final var hintsKey =
                                new HintsKey(Bytes.wrap(blsKeyPair.publicKey().toBytes()), hints);
                        final var body = new HintsKeyPublicationTransactionBody(selfPartyId, numParties, hintsKey);
                        submissions.submitHintsKey(body).join();
                    },
                    executor);
        }
    }

    /**
     * Returns the party ID that this node should use in the target roster.
     */
    private int expectedPartyId(final long nodeId) {
        // List both unassigned node ids and unused party ids in ascending order, and assign
        // the node to the party id at the same position in the list. Note that no matter how
        // many assignments have been made from the time the construction started, a remaining
        // unassigned node id will always be assigned the same party id; to see this, imagine
        // the starting lists as being zipped together---clearly, removing an assigned pair
        // from some index in the zipped list doesn't change any other index
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
}
