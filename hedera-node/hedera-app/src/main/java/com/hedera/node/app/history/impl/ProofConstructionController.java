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

import static java.util.Objects.requireNonNull;
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
import com.hedera.node.app.history.WritableHistoryStore;
import com.hedera.node.app.tss.RosterTransitionWeights;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/**
 * Manages the process objects and work needed to advance toward completion of a metadata proof.
 */
public class ProofConstructionController {
    private final long selfId;
    private final Duration proofKeysWaitTime;
    private final Executor executor;
    private final SchnorrKeyPair schnorrKeyPair;
    private final HistoryOperations operations;
    private final RosterTransitionWeights weights;
    private final Consumer<MetadataProof> proofConsumer;
    private final Map<Long, MetadataProofVote> votes = new HashMap<>();
    private final Map<Long, Bytes> targetProofKeys = new HashMap<>();
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
     * If not null, a future that resolves when this node finishes assembling the SNARK proof for this construction
     * and voting for its proof as the consensus metadata proof.
     */
    @Nullable
    private CompletableFuture<Void> proofFuture;

    /**
     * If not null, the future that resolves when this node publishes its Schnorr key for this construction.
     */
    @Nullable
    private CompletableFuture<Void> publicationFuture;

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
            @NonNull final Executor executor,
            @Nullable final Bytes metadata,
            @NonNull final MetadataProofConstruction construction,
            @NonNull final Duration proofKeysWaitTime,
            @NonNull final SchnorrKeyPair schnorrKeyPair,
            @NonNull final HistoryOperations operations,
            @NonNull final RosterTransitionWeights weights,
            @NonNull final Consumer<MetadataProof> proofConsumer) {
        this.selfId = selfId;
        this.metadata = metadata;
        this.executor = requireNonNull(executor);
        this.operations = requireNonNull(operations);
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
        if (construction.hasAssemblyTime()) {
            if (!votes.containsKey(selfId) && proofFuture == null && hasSufficientSignatures()) {}
        }
    }

    /**
     * Returns a future that completes when the node has completed its metadata proof and submitted
     * the corresponding vote.
     */
    private CompletableFuture<Void> proofFuture() {
        final var choice = requireNonNull(firstSufficientSignatures());
        final var signatures = verificationFutures.headMap(choice.cutoff(), true).values().stream()
                .map(CompletableFuture::join)
                .filter(v -> choice.assembly().equals(v.assembly()) && v.isValid())
                .collect(toMap(Verification::nodeId, v -> v.assemblySignature().signature()));
        final Bytes sourceProof;
        final Map<Long, Bytes> sourceProofKeys;
        if (construction.hasSourceProof()) {
            sourceProof = construction.sourceProofOrThrow().proof();
            sourceProofKeys = proofKeysFrom(construction.sourceProofOrThrow());
        } else {
            sourceProof = null;
            sourceProofKeys = Map.copyOf(targetProofKeys);
        }
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
                            sourceProof,
                            sourceRoster,
                            operations.hashProofRoster(targetRoster),
                            requireNonNull(metadata),
                            signatures);
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
     * Returns the proof keys used for the given proof.
     * @param proof the proof
     * @return the proof keys
     */
    private static Map<Long, Bytes> proofKeysFrom(@NonNull final MetadataProof proof) {
        return proof.proofKeys().stream().collect(toMap(ProofKey::nodeId, ProofKey::key));
    }
}
