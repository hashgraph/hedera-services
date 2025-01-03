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

import static com.hedera.hapi.util.HapiUtils.asInstant;
import static com.hedera.node.app.hapi.utils.CommonUtils.noThrowSha384HashOf;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.summingLong;
import static java.util.stream.Collectors.toMap;

import com.hedera.hapi.node.state.history.HistoryAssembly;
import com.hedera.hapi.node.state.history.HistoryAssemblySignature;
import com.hedera.hapi.node.state.history.MetadataProof;
import com.hedera.hapi.node.state.history.MetadataProofConstruction;
import com.hedera.hapi.node.state.history.MetadataProofVote;
import com.hedera.hapi.node.state.history.ProofKey;
import com.hedera.hapi.node.state.history.ProofRoster;
import com.hedera.hapi.node.state.history.ProofRosterEntry;
import com.hedera.node.app.history.HistoryOperations;
import com.hedera.node.app.history.ReadableHistoryStore.AssemblySignaturePublication;
import com.hedera.node.app.history.WritableHistoryStore;
import com.hedera.node.app.tss.RosterTransitionWeights;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/**
 * Manages the process objects and work needed to advance toward completion of a metadata proof.
 */
public class ProofConstructionController {
    private static final Comparator<ProofKey> PROOF_KEY_COMPARATOR = Comparator.comparingLong(ProofKey::nodeId);

    private final long selfId;

    @Nullable
    private final Bytes ledgerId;

    private final Urgency urgency;
    private final Duration proofKeysWaitTime;
    private final Executor executor;
    private final SchnorrKeyPair schnorrKeyPair;
    private final HistoryOperations operations;
    private final HistorySubmissions submissions;
    private final RosterTransitionWeights weights;
    private final Consumer<MetadataProof> proofConsumer;
    private final Map<Long, MetadataProofVote> votes = new HashMap<>();
    private final Map<Long, Bytes> targetProofKeys = new HashMap<>();
    private final Set<Long> signingNodeIds = new HashSet<>();
    private final NavigableMap<Instant, CompletableFuture<Verification>> verificationFutures = new TreeMap<>();

    /**
     * If not null, the metadata this controller will include in its historical assembly.
     */
    @Nullable
    private Bytes metadata;

    /**
     * The ongoing construction, updated each time the controller advances the construction in state.
     */
    private MetadataProofConstruction construction;

    /**
     * If not null, the future that resolves when this node publishes its Schnorr key for this construction.
     */
    @Nullable
    private CompletableFuture<Void> publicationFuture;

    /**
     * If not null, the future that resolves when this node signs its assembly for this construction.
     */
    @Nullable
    private CompletableFuture<Void> signingFuture;

    /**
     * If not null, a future that resolves when this node finishes assembling the SNARK proof for this construction
     * and voting for its proof as the consensus metadata proof.
     */
    @Nullable
    private CompletableFuture<Void> proofFuture;

    /**
     * Whether this construction must succeed as quickly as possible, even at the cost of some overhead on
     * the {@code handleTransaction} thread when validating assembly signatures; or even at the cost of
     * omitting some nodes' Schnorr keys from the target proof roster, preventing them from contributing
     * signatures in the next roster transition.
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
     * A party's validated signature on its assembly.
     *
     * @param nodeId the node's id
     * @param assemblySignature its assembly signature
     * @param isValid whether the signature is valid
     */
    private record Verification(long nodeId, @NonNull HistoryAssemblySignature assemblySignature, boolean isValid) {
        public @NonNull HistoryAssembly assembly() {
            return assemblySignature.assemblyOrThrow();
        }
    }

    /**
     * A summary of the signatures to be used in a proof.
     * @param assembly the assembly with the signatures
     * @param cutoff the time at which the signatures were sufficient
     */
    private record Signatures(@NonNull HistoryAssembly assembly, @NonNull Instant cutoff) {}

    public ProofConstructionController(
            final long selfId,
            @Nullable final Bytes ledgerId,
            @NonNull final Urgency urgency,
            @NonNull final Executor executor,
            @Nullable final Bytes metadata,
            @NonNull final MetadataProofConstruction construction,
            @NonNull final Duration proofKeysWaitTime,
            @NonNull final SchnorrKeyPair schnorrKeyPair,
            @NonNull final HistoryOperations operations,
            @NonNull final HistorySubmissions submissions,
            @NonNull final RosterTransitionWeights weights,
            @NonNull final Consumer<MetadataProof> proofConsumer) {
        this.selfId = selfId;
        this.ledgerId = ledgerId;
        this.metadata = metadata;
        this.urgency = requireNonNull(urgency);
        this.executor = requireNonNull(executor);
        this.operations = requireNonNull(operations);
        this.submissions = requireNonNull(submissions);
        this.weights = requireNonNull(weights);
        this.construction = requireNonNull(construction);
        this.proofConsumer = requireNonNull(proofConsumer);
        this.schnorrKeyPair = requireNonNull(schnorrKeyPair);
        this.proofKeysWaitTime = requireNonNull(proofKeysWaitTime);
    }

    /**
     * Acts relative to the given state to let this node help advance the ongoing metadata proof
     * construction toward a deterministic completion.
     *
     * @param now the current consensus time
     * @param historyStore the history store, in case the controller is able to complete the construction
     */
    public void advanceConstruction(@NonNull final Instant now, @NonNull final WritableHistoryStore historyStore) {
        if (construction.hasMetadataProof()) {
            return;
        }
        if (metadata == null) {
            ensureProofKeyPublished();
        } else if (construction.hasAssemblyTime()) {
            if (!votes.containsKey(selfId) && proofFuture == null) {
                if (hasSufficientSignatures()) {
                    proofFuture = startProofFuture();
                } else if (!signingNodeIds.contains(selfId) && signingFuture == null) {
                    signingFuture = startSigningFuture();
                }
            }
        } else {
            switch (recommendAssemblyBehavior(now)) {
                case RESCHEDULE_CHECKPOINT -> {
                    construction = historyStore.rescheduleAssemblyCheckpoint(
                            construction.constructionId(), now.plus(proofKeysWaitTime));
                    ensureProofKeyPublished();
                }
                case ASSEMBLE_NOW -> {
                    construction = historyStore.setAssemblyTime(construction.constructionId(), now);
                    signingFuture = startSigningFuture();
                }
                case COME_BACK_LATER -> ensureProofKeyPublished();
            }
        }
    }

    /**
     * Incorporates the proof key published by the given node, if this construction has not already "locked in"
     * its assembled target roster.
     * @param nodeId the node ID
     * @param proofKey the proof key
     */
    public void incorporateProofKey(final long nodeId, @NonNull final Bytes proofKey) {
        requireNonNull(proofKey);
        if (!construction.hasAssemblyTime()) {
            targetProofKeys.put(nodeId, proofKey);
        }
    }

    /**
     * Incorporates the assembly signature published by the given node, if this construction still needs a
     * proof and the
     * @param publication the proof key publication
     */
    public void incorporateAssemblySignature(@NonNull final AssemblySignaturePublication publication) {
        requireNonNull(publication);
        if (!construction.hasMetadataProof() && targetProofKeys.containsKey(publication.nodeId())) {
            verificationFutures.put(
                    publication.at(), verificationFuture(publication.nodeId(), publication.signature()));
        }
    }

    /**
     * Incorporates the metadata proof vote published by the given node, if this construction still needs a proof.
     * @param nodeId the node ID
     * @param vote the vote
     * @param historyStore the history store
     */
    public void incorporateProofVote(
            final long nodeId,
            @NonNull final MetadataProofVote vote,
            @NonNull final WritableHistoryStore historyStore) {
        requireNonNull(vote);
        if (!construction.hasMetadataProof() && !votes.containsKey(nodeId)) {
            votes.put(nodeId, vote);
            final var proofWeights = votes.entrySet().stream()
                    .collect(groupingBy(
                            entry -> entry.getValue().metadataProofOrThrow(),
                            summingLong(entry -> weights.sourceWeightOf(entry.getKey()))));
            final var maybeWinningProof = proofWeights.entrySet().stream()
                    .filter(entry -> entry.getValue() >= weights.sourceWeightThreshold())
                    .map(Map.Entry::getKey)
                    .findFirst();
            maybeWinningProof.ifPresent(proof -> {
                construction = historyStore.completeProof(construction.constructionId(), proof);
                if (historyStore.getActiveConstruction().constructionId() == construction.constructionId()) {
                    proofConsumer.accept(proof);
                    if (ledgerId == null) {
                        historyStore.setLedgerId(proof.sourceProofRosterHash());
                    }
                }
            });
        }
    }

    /**
     * The possible recommendations the controller's assembly policy may make.
     */
    private enum Recommendation {
        /**
         * Schedule the construction's assembly using proof keys published til now.
         */
        ASSEMBLE_NOW,
        /**
         * Revisit the question again at the next opportunity.
         */
        COME_BACK_LATER,
        /**
         * Reschedule the next assembly checkpoint to reduce overhead.
         */
        RESCHEDULE_CHECKPOINT,
    }

    /**
     * Applies a deterministic policy to recommend an assembly behavior at the given time.
     *
     * @param now the current consensus time
     * @return the recommendation
     */
    private Recommendation recommendAssemblyBehavior(@NonNull final Instant now) {
        // If every node in the target roster has published a proof key, schedule the final assembly at this time.
        // Note that if even here, a strong minority of weight _still_ has not published valid proof keys, the
        // signatures produced by this construction will never reach the weight threshold---but such a condition
        // would imply the network was already in an unusable state
        if (targetProofKeys.size() == weights.targetRosterSize()) {
            return Recommendation.ASSEMBLE_NOW;
        }
        if (now.isBefore(asInstant(construction.nextAssemblyCheckpointOrThrow()))) {
            return Recommendation.COME_BACK_LATER;
        } else {
            return switch (urgency) {
                case HIGH -> publishedWeight() >= weights.targetWeightThreshold()
                        ? Recommendation.ASSEMBLE_NOW
                        : Recommendation.COME_BACK_LATER;
                case LOW -> Recommendation.RESCHEDULE_CHECKPOINT;
            };
        }
    }

    /**
     * Ensures this node has published its proof key.
     */
    private void ensureProofKeyPublished() {
        if (publicationFuture == null && weights.hasTargetWeightOf(selfId) && !targetProofKeys.containsKey(selfId)) {
            publicationFuture = CompletableFuture.runAsync(
                    () -> submissions
                            .submitProofKeyPublication(schnorrKeyPair.publicKey())
                            .join(),
                    executor);
        }
    }

    /**
     * Returns a future that completes when the node has signed its assembly and submitted
     * the signature.
     */
    private CompletableFuture<Void> startSigningFuture() {
        requireNonNull(metadata);
        final var targetRoster = new ProofRoster(weights.orderedTargetWeights()
                .map(node -> new ProofRosterEntry(node.nodeId(), node.weight(), targetProofKeys.get(node.nodeId())))
                .toList());
        return CompletableFuture.runAsync(
                () -> {
                    final var targetRosterHash = operations.hashProofRoster(targetRoster);
                    final var buffer = ByteBuffer.allocate((int) (targetRosterHash.length() + metadata.length()));
                    targetRosterHash.writeTo(buffer);
                    metadata.writeTo(buffer);
                    final var message = noThrowSha384HashOf(Bytes.wrap(buffer.array()));
                    final var signature = new HistoryAssemblySignature(
                            new HistoryAssembly(targetRosterHash, metadata),
                            operations.signSchnorr(message, schnorrKeyPair.privateKey()));
                    submissions
                            .submitAssemblySignature(construction.constructionId(), signature)
                            .join();
                },
                executor);
    }

    /**
     * Returns a future that completes when the node has completed its metadata proof and submitted
     * the corresponding vote.
     */
    private CompletableFuture<Void> startProofFuture() {
        final var choice = requireNonNull(firstSufficientSignatures());
        final var signatures = verificationFutures.headMap(choice.cutoff(), true).values().stream()
                .map(CompletableFuture::join)
                .filter(v -> choice.assembly().equals(v.assembly()) && v.isValid())
                .collect(toMap(Verification::nodeId, v -> v.assemblySignature().signature()));
        final Bytes sourceProof;
        final Map<Long, Bytes> sourceProofKeys;
        if (construction.hasSourceProof()) {
            sourceProof = construction.sourceProofOrThrow().proof();
            sourceProofKeys = proofKeyMapFrom(construction.sourceProofOrThrow());
        } else {
            sourceProof = null;
            sourceProofKeys = Map.copyOf(targetProofKeys);
        }
        final var proofMetadata = requireNonNull(metadata);
        return CompletableFuture.runAsync(
                () -> {
                    final var sourceRoster = new ProofRoster(weights.orderedSourceWeights()
                            .map(node -> new ProofRosterEntry(
                                    node.nodeId(), node.weight(), sourceProofKeys.get(node.nodeId())))
                            .toList());
                    final var targetRoster = new ProofRoster(weights.orderedTargetWeights()
                            .map(node -> new ProofRosterEntry(
                                    node.nodeId(), node.weight(), targetProofKeys.get(node.nodeId())))
                            .toList());
                    final var proof = operations.proveTransition(
                            Optional.ofNullable(ledgerId).orElseGet(() -> operations.hashProofRoster(sourceRoster)),
                            sourceProof,
                            sourceRoster,
                            operations.hashProofRoster(targetRoster),
                            proofMetadata,
                            signatures);
                    final var metadataProof = MetadataProof.newBuilder()
                            .sourceProofRosterHash(operations.hashProofRoster(sourceRoster))
                            .targetProofRosterHash(operations.hashProofRoster(targetRoster))
                            .proof(proof)
                            .proofKeys(proofKeyListFrom(targetProofKeys))
                            .metadata(proofMetadata)
                            .build();
                    submissions
                            .submitProofVote(construction.constructionId(), metadataProof)
                            .join();
                },
                executor);
    }

    /**
     * Whether the construction has sufficient verified signatures to initiate a proof.
     */
    private boolean hasSufficientSignatures() {
        return firstSufficientSignatures() != null;
    }

    /**
     * Returns the first time at which this construction had sufficient verified signatures to
     * initiate a proof. Blocks until verifications are ready; this is acceptable because these
     * verification future will generally have already been completed async in the interval
     * between the time the signature reaches consensus and the time the next round starts.
     */
    @Nullable
    private Signatures firstSufficientSignatures() {
        final Map<HistoryAssembly, Long> assemblyWeights = new HashMap<>();
        for (final var entry : verificationFutures.entrySet()) {
            final var verification = entry.getValue().join();
            if (verification.isValid()) {
                final long weight = assemblyWeights.merge(
                        verification.assembly(), weights.sourceWeightOf(verification.nodeId()), Long::sum);
                if (weight >= weights.sourceWeightThreshold()) {
                    return new Signatures(verification.assembly(), entry.getKey());
                }
            }
        }
        return null;
    }

    /**
     * Returns the weight of the nodes in the target roster that have published their proof keys.
     */
    private long publishedWeight() {
        return targetProofKeys.keySet().stream()
                .mapToLong(weights::targetWeightOf)
                .sum();
    }

    /**
     * Returns the proof keys used for the given proof.
     * @param proof the proof
     * @return the proof keys
     */
    private static Map<Long, Bytes> proofKeyMapFrom(@NonNull final MetadataProof proof) {
        return proof.proofKeys().stream().collect(toMap(ProofKey::nodeId, ProofKey::key));
    }

    /**
     * Returns a list of proof keys from the given map.
     * @param proofKeys the proof keys in a map
     * @return the list of proof keys
     */
    private static List<ProofKey> proofKeyListFrom(@NonNull final Map<Long, Bytes> proofKeys) {
        return proofKeys.entrySet().stream()
                .map(entry -> new ProofKey(entry.getKey(), entry.getValue()))
                .sorted(PROOF_KEY_COMPARATOR)
                .toList();
    }

    /**
     * Returns a future that completes to a verification of the given assembly signature.
     *
     * @param nodeId the node ID
     *
     * @return the future
     */
    private CompletableFuture<Verification> verificationFuture(
            final long nodeId, @NonNull final HistoryAssemblySignature signature) {
        return CompletableFuture.supplyAsync(
                () -> {
                    final var message = messageFor(signature.assemblyOrThrow());
                    final var proofKey = requireNonNull(targetProofKeys.get(nodeId));
                    final var isValid = operations.verifySchnorr(proofKey, message);
                    return new Verification(nodeId, signature, isValid);
                },
                executor);
    }

    private Bytes messageFor(@NonNull final HistoryAssembly assembly) {
        return messageFor(assembly.proofRosterHash(), assembly.metadata());
    }

    /**
     * Returns the message to be signed for the given proof roster hash and metadata.
     * @param proofRosterHash the proof roster hash
     * @param metadata the metadata
     * @return the message
     */
    private Bytes messageFor(@NonNull final Bytes proofRosterHash, @NonNull final Bytes metadata) {
        final var buffer = ByteBuffer.allocate((int) (proofRosterHash.length() + metadata.length()));
        proofRosterHash.writeTo(buffer);
        metadata.writeTo(buffer);
        return noThrowSha384HashOf(Bytes.wrap(buffer.array()));
    }
}
