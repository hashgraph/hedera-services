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

package com.hedera.node.app.history.impl;

import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.state.history.HistoryAssemblySignature;
import com.hedera.hapi.node.state.history.MetadataProof;
import com.hedera.hapi.node.state.history.MetadataProofConstruction;
import com.hedera.node.app.tss.RosterTransitionWeights;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Manages the process objects and work needed to advance toward completion of a metadata proof.
 */
public class ProofConstructionController {
    private final long selfId;
    private final Duration proofKeysWaitTime;
    private final SchnorrKeyPair schnorrKeyPair;
    private final Map<Long, Bytes> proofKeys;
    private final RosterTransitionWeights weights;
    private final Consumer<MetadataProof> proofConsumer;

    /**
     * The ongoing construction, updated each time the controller advances the construction in state.
     */
    private MetadataProofConstruction construction;

    /**
     * If not null, a future that resolves when this node finishes assembling the SNARK proof for this construction.
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
     * @param signature its assembly signature
     * @param isValid whether the signature is valid
     */
    private record Validation(long nodeId, @NonNull HistoryAssemblySignature signature, boolean isValid) {}

    public ProofConstructionController(
            final long selfId,
            @NonNull final MetadataProofConstruction construction,
            @NonNull final Duration proofKeysWaitTime,
            @NonNull final SchnorrKeyPair schnorrKeyPair,
            @NonNull final Map<Long, Bytes> proofKeys,
            @NonNull final RosterTransitionWeights weights,
            @NonNull final Consumer<MetadataProof> proofConsumer) {
        this.selfId = selfId;
        this.proofKeys = proofKeys;
        this.weights = requireNonNull(weights);
        this.construction = requireNonNull(construction);
        this.proofConsumer = requireNonNull(proofConsumer);
        this.schnorrKeyPair = requireNonNull(schnorrKeyPair);
        this.proofKeysWaitTime = requireNonNull(proofKeysWaitTime);
    }
}
