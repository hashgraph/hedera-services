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

import com.hedera.hapi.node.state.history.History;
import com.hedera.hapi.node.state.history.HistoryAddressBook;
import com.hedera.hapi.node.state.history.HistoryAddressBookEntry;
import com.hedera.hapi.node.state.history.HistoryProof;
import com.hedera.hapi.node.state.history.HistoryProofConstruction;
import com.hedera.hapi.node.state.history.HistoryProofVote;
import com.hedera.hapi.node.state.history.HistorySignature;
import com.hedera.hapi.node.state.history.ProofKey;
import com.hedera.node.app.history.HistoryLibrary;
import com.hedera.node.app.history.ReadableHistoryStore.AssemblySignaturePublication;
import com.hedera.node.app.history.WritableHistoryStore;
import com.hedera.node.app.roster.RosterTransitionWeights;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.nio.ByteBuffer;
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
public class ProofController {
    private static final Comparator<ProofKey> PROOF_KEY_COMPARATOR = Comparator.comparingLong(ProofKey::nodeId);

    private final long selfId;

    @Nullable
    private final Bytes ledgerId;

    private final Executor executor;
    private final SchnorrKeyPair schnorrKeyPair;
    private final HistoryLibrary library;
    private final HistorySubmissions submissions;
    private final RosterTransitionWeights weights;
    private final Consumer<HistoryProof> proofConsumer;
    private final Map<Long, HistoryProofVote> votes = new HashMap<>();
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
    private HistoryProofConstruction construction;

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
     * A party's verified signature on some new history.
     *
     * @param nodeId the node's id
     * @param historySignature its history signature
     * @param isValid whether the signature is valid
     */
    private record Verification(long nodeId, @NonNull HistorySignature historySignature, boolean isValid) {
        public @NonNull History history() {
            return historySignature.historyOrThrow();
        }
    }

    /**
     * A summary of the signatures to be used in a proof.
     *
     * @param history the assembly with the signatures
     * @param cutoff the time at which the signatures were sufficient
     */
    private record Signatures(@NonNull History history, @NonNull Instant cutoff) {}

    public ProofController(
            final long selfId,
            @NonNull final HistoryProofConstruction construction,
            @Nullable final Bytes ledgerId,
            @NonNull final RosterTransitionWeights weights,
            @NonNull final Executor executor,
            @Nullable final Bytes metadata,
            @NonNull final SchnorrKeyPair schnorrKeyPair,
            @NonNull final HistoryLibrary library,
            @NonNull final HistorySubmissions submissions,
            @NonNull final Consumer<HistoryProof> proofConsumer) {
        this.selfId = selfId;
        this.ledgerId = ledgerId;
        this.metadata = metadata;
        this.executor = requireNonNull(executor);
        this.library = requireNonNull(library);
        this.submissions = requireNonNull(submissions);
        this.weights = requireNonNull(weights);
        this.construction = requireNonNull(construction);
        this.proofConsumer = requireNonNull(proofConsumer);
        this.schnorrKeyPair = requireNonNull(schnorrKeyPair);
    }

    /**
     * Acts relative to the given state to let this node help advance the ongoing metadata proof
     * construction toward a deterministic completion.
     *
     * @param now the current consensus time
     * @param historyStore the history store, in case the controller is able to complete the construction
     */
    public void advanceConstruction(@NonNull final Instant now, @NonNull final WritableHistoryStore historyStore) {
        if (construction.hasTargetProof()) {
            return;
        }
        if (metadata == null) {
            ensureProofKeyPublished();
        } else if (construction.hasAssemblyStartTime()) {
            if (!votes.containsKey(selfId) && proofFuture == null) {
                if (hasSufficientSignatures()) {
                    proofFuture = startProofFuture();
                } else if (!signingNodeIds.contains(selfId) && signingFuture == null) {
                    signingFuture = startSigningFuture();
                }
            }
        } else {
            if (shouldAssemble(now)) {
                construction = historyStore.setAssemblyTime(construction.constructionId(), now);
                signingFuture = startSigningFuture();
            } else {
                ensureProofKeyPublished();
            }
        }
    }

    /**
     * Incorporates the proof key published by the given node, if this construction has not already "locked in"
     * its assembled target roster.
     *
     * @param nodeId the node ID
     * @param proofKey the proof key
     */
    public void incorporateProofKey(final long nodeId, @NonNull final Bytes proofKey) {
        requireNonNull(proofKey);
        if (!construction.hasAssemblyStartTime()) {
            targetProofKeys.put(nodeId, proofKey);
        }
    }

    /**
     * Incorporates the assembly signature published by the given node, if this construction still needs a
     * proof and the
     *
     * @param publication the proof key publication
     */
    public void addProofSignature(@NonNull final AssemblySignaturePublication publication) {
        requireNonNull(publication);
        if (!construction.hasTargetProof() && targetProofKeys.containsKey(publication.nodeId())) {
            verificationFutures.put(
                    publication.at(), verificationFuture(publication.nodeId(), publication.signature()));
        }
    }

    /**
     * Incorporates the metadata proof vote published by the given node, if this construction still needs a proof.
     *
     * @param nodeId the node ID
     * @param vote the vote
     * @param historyStore the history store
     */
    public void incorporateProofVote(
            final long nodeId, @NonNull final HistoryProofVote vote, @NonNull final WritableHistoryStore historyStore) {
        requireNonNull(vote);
        if (!construction.hasTargetProof() && !votes.containsKey(nodeId)) {
            votes.put(nodeId, vote);
            final var proofWeights = votes.entrySet().stream()
                    .collect(groupingBy(
                            entry -> entry.getValue().proofOrThrow(),
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
                        historyStore.setLedgerId(proof.sourceAddressBookHash());
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
    private boolean shouldAssemble(@NonNull final Instant now) {
        // If every active node in the target roster has published a proof key,
        // assemble the new history now; there is nothing else to wait for
        if (targetProofKeys.size() == weights.numTargetNodesInSource()) {
            return true;
        }
        if (now.isBefore(asInstant(construction.gracePeriodEndTimeOrThrow()))) {
            return false;
        } else {
            return publishedWeight() >= weights.targetWeightThreshold();
        }
    }

    /**
     * Ensures this node has published its proof key.
     */
    private void ensureProofKeyPublished() {
        if (publicationFuture == null && weights.targetIncludes(selfId) && !targetProofKeys.containsKey(selfId)) {
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
        final var targetRoster = new HistoryAddressBook(weights.orderedTargetWeights()
                .map(node ->
                        new HistoryAddressBookEntry(node.nodeId(), node.weight(), targetProofKeys.get(node.nodeId())))
                .toList());
        return CompletableFuture.runAsync(
                () -> {
                    final var targetRosterHash = library.hashAddressBook(targetRoster);
                    final var buffer = ByteBuffer.allocate((int) (targetRosterHash.length() + metadata.length()));
                    targetRosterHash.writeTo(buffer);
                    metadata.writeTo(buffer);
                    final var message = noThrowSha384HashOf(Bytes.wrap(buffer.array()));
                    final var signature = new HistorySignature(
                            new History(targetRosterHash, metadata),
                            library.signHistory(message, schnorrKeyPair.privateKey()));
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
                .filter(v -> choice.history().equals(v.history()) && v.isValid())
                .collect(toMap(Verification::nodeId, v -> v.historySignature().signature()));
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
                    final var sourceRoster = new HistoryAddressBook(weights.orderedSourceWeights()
                            .map(node -> new HistoryAddressBookEntry(
                                    node.nodeId(), node.weight(), sourceProofKeys.get(node.nodeId())))
                            .toList());
                    final var targetRoster = new HistoryAddressBook(weights.orderedTargetWeights()
                            .map(node -> new HistoryAddressBookEntry(
                                    node.nodeId(), node.weight(), targetProofKeys.get(node.nodeId())))
                            .toList());
                    final var proof = library.proveChainOfTrust(
                            Optional.ofNullable(ledgerId).orElseGet(() -> library.hashAddressBook(sourceRoster)),
                            sourceProof,
                            sourceRoster,
                            signatures,
                            library.hashAddressBook(targetRoster),
                            proofMetadata);
                    final var metadataProof = HistoryProof.newBuilder()
                            .sourceAddressBookHash(library.hashAddressBook(sourceRoster))
                            .targetAddressBookHash(library.hashAddressBook(targetRoster))
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
        final Map<History, Long> historyWeights = new HashMap<>();
        for (final var entry : verificationFutures.entrySet()) {
            final var verification = entry.getValue().join();
            if (verification.isValid()) {
                final long weight = historyWeights.merge(
                        verification.history(), weights.sourceWeightOf(verification.nodeId()), Long::sum);
                if (weight >= weights.sourceWeightThreshold()) {
                    return new Signatures(verification.history(), entry.getKey());
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
     *
     * @param proof the proof
     * @return the proof keys
     */
    private static Map<Long, Bytes> proofKeyMapFrom(@NonNull final HistoryProof proof) {
        return proof.proofKeys().stream().collect(toMap(ProofKey::nodeId, ProofKey::key));
    }

    /**
     * Returns a list of proof keys from the given map.
     *
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
     * @return the future
     */
    private CompletableFuture<Verification> verificationFuture(
            final long nodeId, @NonNull final HistorySignature historySignature) {
        return CompletableFuture.supplyAsync(
                () -> {
                    final var message = messageFor(historySignature.historyOrThrow());
                    final var proofKey = requireNonNull(targetProofKeys.get(nodeId));
                    final var isValid = library.verifyHistorySignature(proofKey, message, historySignature.signature());
                    return new Verification(nodeId, historySignature, isValid);
                },
                executor);
    }

    private Bytes messageFor(@NonNull final History history) {
        return messageFor(history.addressBookHash(), history.metadata());
    }

    /**
     * Returns the message to be signed for the given address book hash and metadata.
     *
     * @param addressBookHash the address book hash
     * @param metadata the metadata
     * @return the message
     */
    private Bytes messageFor(@NonNull final Bytes addressBookHash, @NonNull final Bytes metadata) {
        final var buffer = ByteBuffer.allocate((int) (addressBookHash.length() + metadata.length()));
        addressBookHash.writeTo(buffer);
        metadata.writeTo(buffer);
        return noThrowSha384HashOf(Bytes.wrap(buffer.array()));
    }
}
