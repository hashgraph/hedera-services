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

import static java.util.Objects.requireNonNull;

import com.hedera.cryptography.bls.BlsSignature;
import com.hedera.node.app.hints.HintsLibrary;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A particular signing process spawned from this context.
 */
public class HintsSigning {
    private final long constructionId;
    private final long thresholdWeight;
    private final Bytes message;
    private final Bytes aggregationKey;
    private final Map<Long, Long> partyIds;
    private final CompletableFuture<Bytes> future = new CompletableFuture<>();
    private final ConcurrentMap<Long, BlsSignature> signatures = new ConcurrentHashMap<>();
    private final AtomicLong weightOfSignatures = new AtomicLong();
    private final HintsLibrary operations;

    public HintsSigning(
            final long constructionId,
            final long thresholdWeight,
            @NonNull final Bytes message,
            @NonNull final Bytes aggregationKey,
            @NonNull final Map<Long, Long> partyIds,
            @NonNull final HintsLibrary operations) {
        this.constructionId = constructionId;
        this.thresholdWeight = thresholdWeight;
        this.message = requireNonNull(message);
        this.aggregationKey = requireNonNull(aggregationKey);
        this.partyIds = requireNonNull(partyIds);
        this.operations = requireNonNull(operations);
    }

    /**
     * The future that will complete when sufficient partial signatures have been aggregated.
     */
    public CompletableFuture<Bytes> future() {
        return future;
    }

    /**
     * Incorporate a partial signature into the aggregation.
     * @param constructionId the construction ID
     * @param nodeId the node ID
     * @param signature the partial signature
     */
    public void incorporate(final long constructionId, final long nodeId, @NonNull final BlsSignature signature) {
        requireNonNull(signature);
        if (this.constructionId == constructionId) {
            final var publicKey = operations.extractPublicKey(aggregationKey, partyIds.get(nodeId));
            if (operations.verifyPartial(message, signature, publicKey)) {
                signatures.put(nodeId, signature);
                final var weight = operations.extractWeight(aggregationKey, partyIds.get(nodeId));
                if (weightOfSignatures.addAndGet(weight) >= thresholdWeight) {
                    future.complete(operations.aggregateSignatures(aggregationKey, signatures));
                }
            }
        }
    }
}
