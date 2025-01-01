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
import static com.hedera.hapi.util.HapiUtils.asNullableInstant;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.summingLong;
import static java.util.stream.Collectors.toMap;

import com.hedera.cryptography.bls.BlsKeyPair;
import com.hedera.hapi.node.state.hints.HintsConstruction;
import com.hedera.hapi.node.state.hints.HintsKey;
import com.hedera.hapi.node.state.hints.PreprocessedKeysVote;
import com.hedera.hapi.services.auxiliary.hints.HintsAggregationVoteTransactionBody;
import com.hedera.hapi.services.auxiliary.hints.HintsKeyPublicationTransactionBody;
import com.hedera.node.app.hints.HintsOperations;
import com.hedera.node.app.hints.HintsService;
import com.hedera.node.app.hints.ReadableHintsStore.HintsKeyPublication;
import com.hedera.node.app.hints.WritableHintsStore;
import com.hedera.node.app.tss.RosterTransitionWeights;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.OptionalLong;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.stream.LongStream;

/**
 * Manages the process objects and work needed to advance toward completion of a hinTS construction.
 */
public class HintsConstructionController {
    private final int k;
    private final int M;
    private final long selfId;
    private final Urgency urgency;
    private final Executor executor;
    private final BlsKeyPair blsKeyPair;
    private final Duration hintKeysWaitTime;
    private final HintsOperations operations;
    private final HintsSubmissions submissions;
    private final HintsSigningContext signingContext;
    private final Map<Long, Long> nodePartyIds = new HashMap<>();
    private final Map<Long, Long> partyNodeIds = new HashMap<>();
    private final RosterTransitionWeights weights;
    private final Map<Long, PreprocessedKeysVote> votes = new HashMap<>();
    private final NavigableMap<Instant, CompletableFuture<Validation>> validationFutures = new TreeMap<>();

    /**
     * The ongoing construction, updated each time the controller advances the construction in state.
     */
    private HintsConstruction construction;

    /**
     * If not null, a future that resolves when this node completes the aggregation work for this construction.
     */
    @Nullable
    private CompletableFuture<Void> aggregationFuture;

    /**
     * If not null, the future performing the hinTS key publication for this node.
     */
    @Nullable
    private CompletableFuture<Void> publicationFuture;

    /**
     * Whether this construction must succeed as quickly as possible, even at the cost of some overhead on
     * the {@code handleTransaction} thread when validating hints; or even at the cost of omitting some
     * nodes' hinTS keys from the construction if they are too slow to publish them, excluding these nodes
     * from contributing partial signatures after the roster transition.
     */
    public enum Urgency {
        /**
         * The construction is urgent; some overhead is acceptable to ensure the construction completes
         * as soon as possible if nodes are slow to publish their hints.
         */
        HIGH,
        /**
         * The construction is not urgent; the construction can take as long as necessary to complete,
         * and should minimize overhead on the handle thread.
         */
        LOW,
    }

    /**
     * A party's validated hinTS key, including the key itself and whether it is valid.
     *
     * @param partyId the party ID
     * @param hintsKey the hinTS key
     * @param isValid whether the key is valid
     */
    private record Validation(long partyId, @NonNull HintsKey hintsKey, boolean isValid) {}

    public HintsConstructionController(
            final long selfId,
            @NonNull final HintsConstruction construction,
            @NonNull final RosterTransitionWeights weights,
            @NonNull final Urgency urgency,
            @NonNull final Executor executor,
            @NonNull final BlsKeyPair blsKeyPair,
            @NonNull final Duration hintKeysWaitTime,
            @NonNull final HintsOperations operations,
            @NonNull final List<HintsKeyPublication> publications,
            @NonNull final Map<Long, PreprocessedKeysVote> votes,
            @NonNull final HintsSubmissions submissions,
            @NonNull final HintsSigningContext signingContext) {
        this.selfId = selfId;
        this.blsKeyPair = requireNonNull(blsKeyPair);
        this.urgency = requireNonNull(urgency);
        this.hintKeysWaitTime = requireNonNull(hintKeysWaitTime);
        this.weights = requireNonNull(weights);
        this.M = HintsService.partySizeForRosterNodeCount(weights.targetRosterSize());
        this.k = Integer.numberOfTrailingZeros(M);
        this.executor = requireNonNull(executor);
        this.signingContext = requireNonNull(signingContext);
        this.submissions = requireNonNull(submissions);
        this.operations = requireNonNull(operations);
        this.construction = requireNonNull(construction);
        this.votes.putAll(votes);
        publications.forEach(this::incorporateHintsKey);
    }

    /**
     * Returns whether the construction is still in progress.
     */
    public boolean isStillInProgress() {
        return !construction.hasPreprocessedKeys();
    }

    /**
     * Returns whether the construction has the given maximum universe size log base-2.
     * @param k a log base-2 maximum universe size
     * @return whether the construction has the given maximum universe size
     */
    public boolean hasLog2UniverseSize(final int k) {
        return this.k == k;
    }

    /**
     * Acts relative to the given state to let this node help advance the ongoing hinTS construction toward a
     * deterministic completion.
     *
     * @param now the current consensus time
     * @param hintsStore the hints store, in case the controller is able to complete the construction
     */
    public void advanceConstruction(@NonNull final Instant now, @NonNull final WritableHintsStore hintsStore) {
        if (construction.hasPreprocessedKeys()) {
            return;
        }
        final var cutoff = aggregationTime();
        if (cutoff != null) {
            if (!votes.containsKey(selfId) && aggregationFuture == null) {
                aggregationFuture = aggregateFuture(cutoff);
            }
        } else {
            switch (recommendAggregationBehavior(now)) {
                case RESCHEDULE_CHECKPOINT -> construction =
                        hintsStore.rescheduleAggregationCheckpoint(constructionId(), now.plus(hintKeysWaitTime));
                case AGGREGATE_NOW -> {
                    construction = hintsStore.setAggregationTime(constructionId(), now);
                    aggregationFuture = aggregateFuture(now);
                }
                case COME_BACK_LATER -> {
                    // Nothing to do now
                }
            }
            if (aggregationTime() != null
                    && publicationFuture != null
                    && weights.hasTargetWeightOf(selfId)
                    && !nodePartyIds.containsKey(selfId)) {
                publicationFuture = CompletableFuture.runAsync(
                        () -> {
                            final var hints = operations.computeHints(blsKeyPair.privateKey(), M);
                            final var hintsKey = new HintsKey(
                                    Bytes.wrap(blsKeyPair.publicKey().toBytes()), hints);
                            final var body = new HintsKeyPublicationTransactionBody(k, hintsKey);
                            submissions.submitHintsKey(body).join();
                        },
                        executor);
            }
        }
    }

    /**
     * Incorporates a new hint key publication into the controller's state.
     *
     * @param publication the hint key publication
     */
    public void incorporateHintsKey(@NonNull final HintsKeyPublication publication) {
        requireNonNull(publication);
        if (aggregationTime() == null) {
            nodePartyIds.put(publication.nodeId(), publication.partyId());
            partyNodeIds.put(publication.partyId(), publication.nodeId());
            validationFutures.put(
                    publication.adoptionTime(), validationFuture(publication.partyId(), publication.hintsKey()));
        }
    }

    /**
     * If the construction is not already complete, incorporates an aggregation vote into the controller's state,
     * updating network state with the winning aggregation if one is found.
     * @param nodeId the node ID
     * @param vote the aggregation vote
     * @param hintsStore the hints store
     */
    public void incorporateAggregationVote(
            final long nodeId, @NonNull final PreprocessedKeysVote vote, @NonNull final WritableHintsStore hintsStore) {
        requireNonNull(vote);
        requireNonNull(hintsStore);
        if (!construction.hasPreprocessedKeys()) {
            votes.put(nodeId, vote);
            final var aggregationWeights = votes.entrySet().stream()
                    .collect(groupingBy(
                            entry -> entry.getValue().preprocessedKeysOrThrow(),
                            summingLong(entry -> weights.sourceWeightOf(entry.getKey()))));
            final var maybeWinningAggregation = aggregationWeights.entrySet().stream()
                    .filter(entry -> entry.getValue() >= weights.sourceWeightThreshold())
                    .map(Map.Entry::getKey)
                    .findFirst();
            maybeWinningAggregation.ifPresent(keys -> {
                final var id = constructionId();
                construction = hintsStore.completeAggregation(id, keys, nodePartyIds);
                if (hintsStore.getActiveConstruction().constructionId() == id) {
                    signingContext.setConstruction(construction);
                }
            });
        }
    }

    /**
     * Cancels any pending work that this controller has scheduled.
     */
    public void cancelPendingWork() {
        if (publicationFuture != null) {
            publicationFuture.cancel(true);
        }
        if (aggregationFuture != null) {
            aggregationFuture.cancel(true);
        }
        validationFutures.values().forEach(future -> future.cancel(true));
    }

    /**
     * Returns the ID of the hinTS construction that this controller is managing.
     */
    public long constructionId() {
        return construction.constructionId();
    }

    /**
     * Returns the party id for the given node id, if available.
     * @param nodeId the node ID
     * @return the party ID, if available
     */
    public OptionalLong partyIdOf(final long nodeId) {
        return nodePartyIds.containsKey(nodeId) ? OptionalLong.of(nodePartyIds.get(nodeId)) : OptionalLong.empty();
    }

    /**
     * Returns the next available party id.
     */
    public long nextPartyId() {
        return LongStream.range(0, M)
                .filter(partyId -> !partyNodeIds.containsKey(partyId))
                .findFirst()
                .orElseThrow();
    }

    /**
     * Returns the source roster hash of the hinTS construction that this controller is managing.
     */
    @Nullable
    public Bytes sourceRosterHash() {
        return construction.sourceRosterHash();
    }

    /**
     * Returns the target roster hash of the hinTS construction that this controller is managing.
     */
    @NonNull
    public Bytes targetRosterHash() {
        return construction.targetRosterHash();
    }

    /**
     * Returns the aggregation time of the hinTS construction this controller is managing, if available.
     */
    private @Nullable Instant aggregationTime() {
        return asNullableInstant(construction.aggregationTime());
    }

    private @NonNull Instant nextAggregationCheckpoint() {
        return asInstant(construction.nextAggregationCheckpointOrThrow());
    }

    /**
     * The possible recommendations the controller's aggregation policy may make.
     */
    private enum Recommendation {
        /**
         * Schedule the construction's key aggregation using valid hinTS published up to now.
         */
        AGGREGATE_NOW,
        /**
         * Revisit the question again at the next opportunity.
         */
        COME_BACK_LATER,
        /**
         * Reschedule the next aggregation checkpoint to reduce overhead.
         */
        RESCHEDULE_CHECKPOINT,
    }

    /**
     * Applies a deterministic policy to recommend an aggregation behavior at the given time.
     *
     * @param now the current consensus time
     * @return the recommendation
     */
    private Recommendation recommendAggregationBehavior(@NonNull final Instant now) {
        // If every node in the target roster has published a hinTS key, schedule the final aggregation at this time.
        // Note that if even here, a strong minority of weight _still_ has not published valid hinTS, the signatures
        // produced by this construction will never reach the weight threshold---but such a condition would imply the
        // network was already in an unusable state
        if (validationFutures.size() == weights.targetRosterSize()) {
            return Recommendation.AGGREGATE_NOW;
        }
        if (now.isBefore(nextAggregationCheckpoint())) {
            return Recommendation.COME_BACK_LATER;
        } else {
            return switch (urgency) {
                case HIGH -> validWeightAt(now) >= weights.targetWeightThreshold()
                        ? Recommendation.AGGREGATE_NOW
                        : Recommendation.COME_BACK_LATER;
                case LOW -> Recommendation.RESCHEDULE_CHECKPOINT;
            };
        }
    }

    /**
     * Returns a future that completes to the aggregated hinTS keys for this construction for
     * all valid published hinTS keys.
     *
     * @return the future
     */
    private CompletableFuture<Void> aggregateFuture(@NonNull final Instant cutoff) {
        return CompletableFuture.runAsync(
                () -> {
                    final var hintKeys = validationFutures.headMap(cutoff, true).values().stream()
                            .map(CompletableFuture::join)
                            .filter(Validation::isValid)
                            .collect(toMap(Validation::partyId, Validation::hintsKey));
                    final var aggregatedWeights = nodePartyIds.entrySet().stream()
                            .filter(entry -> hintKeys.containsKey(entry.getValue()))
                            .collect(toMap(Map.Entry::getValue, entry -> weights.targetWeightOf(entry.getKey())));
                    final var keys = operations.preprocess(hintKeys, aggregatedWeights, M);
                    final var body = HintsAggregationVoteTransactionBody.newBuilder()
                            .constructionId(constructionId())
                            .vote(PreprocessedKeysVote.newBuilder()
                                    .preprocessedKeys(keys)
                                    .build())
                            .build();
                    submissions.submitHintsVote(body).join();
                },
                executor);
    }

    /**
     * Returns the weight of the nodes in the target roster that have published valid hinTS keys up to the given time.
     * This is blocking because if we are reduced to checking this, we are trying to finish an {@link Urgency#HIGH}
     * construction after the initially scheduled aggregation checkpoint; and then it is worth doing validation even
     * on the {@code handleTransaction} thread.
     *
     * @param now the time up to which to consider hinTS keys
     * @return the weight of the nodes with valid hinTS keys
     */
    private long validWeightAt(@NonNull final Instant now) {
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
    private CompletableFuture<Validation> validationFuture(final long partyId, @NonNull final HintsKey hintsKey) {
        return CompletableFuture.supplyAsync(
                () -> {
                    final var isValid = operations.validate(hintsKey, M);
                    return new Validation(partyId, hintsKey, isValid);
                },
                executor);
    }
}
