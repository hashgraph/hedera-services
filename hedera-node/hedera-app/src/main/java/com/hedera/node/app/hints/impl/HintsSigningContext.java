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

import static java.util.Collections.unmodifiableMap;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toMap;

import com.hedera.cryptography.bls.BlsSignature;
import com.hedera.hapi.node.state.hints.HintsConstruction;
import com.hedera.hapi.node.state.hints.PartyAssignment;
import com.hedera.node.app.hints.HintsOperations;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Future;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class HintsSigningContext {
    @Nullable
    private HintsConstruction activeConstruction;

    @Nullable
    private Map<Long, Long> activeNodeWeights;

    @Nullable
    private Map<Long, Long> activeNodePartyIds;

    private final HintsOperations operations;

    @Inject
    public HintsSigningContext(@NonNull final HintsOperations operations) {
        this.operations = requireNonNull(operations);
    }

    public class Signing {
        private final long constructionId;
        private final Bytes message;
        private final Bytes aggregationKey;
        private final Map<Long, Long> weights;
        private final Map<Long, Long> partyIds;
        private final CompletableFuture<Bytes> future = new CompletableFuture<>();
        private final ConcurrentMap<Long, Bytes> signatures = new ConcurrentHashMap<>();

        public Signing(
                final long constructionId,
                @NonNull final Bytes message,
                @NonNull final Bytes aggregationKey,
                @NonNull final Map<Long, Long> weights,
                @NonNull final Map<Long, Long> partyIds) {
            this.constructionId = constructionId;
            this.message = requireNonNull(message);
            this.aggregationKey = requireNonNull(aggregationKey);
            this.weights = requireNonNull(weights);
            this.partyIds = requireNonNull(partyIds);
        }

        public Future<Bytes> future() {
            return future;
        }

        public void incorporate(final long constructionId, final long nodeId, @NonNull final BlsSignature signature) {
            requireNonNull(signature);
            if (this.constructionId == constructionId) {
                final var publicKey = operations.extractPublicKey(aggregationKey, partyIds.get(nodeId));
                if (operations.verifyPartial(message, signature, publicKey)) {}
            }
        }
    }

    public void setActiveConstruction(@NonNull final HintsConstruction construction) {
        requireNonNull(construction);
    }

    public void setActiveNodeWeights(@NonNull final Map<Long, Long> activeNodeWeights) {
        this.activeNodeWeights = requireNonNull(unmodifiableMap(activeNodeWeights));
    }

    public boolean needsActiveNodeWeights() {
        return activeConstruction != null && activeNodeWeights == null;
    }

    public boolean isReady() {
        return activeConstruction != null && activeNodeWeights != null;
    }

    public @NonNull Future<Bytes> signFuture(@NonNull final Bytes blockHash) {
        requireNonNull(blockHash);
        if (!isReady()) {
            throw new IllegalStateException("Signing context not ready with activeConstructionId="
                    + Optional.ofNullable(activeConstruction)
                            .map(HintsConstruction::constructionId)
                            .map(Object::toString)
                            .orElse("<N/A>")
                    + " and activeNodeWeights=" + (activeNodeWeights == null ? "<N/A>" : "<SET>"));
        }
        requireNonNull(activeConstruction);
        requireNonNull(activeNodeWeights);
        final var signing = new Signing(
                activeConstruction.constructionId(),
                blockHash,
                activeConstruction.preprocessedKeysOrThrow().aggregationKey(),
                activeNodeWeights,
                asNodePartyIds(activeConstruction.partyAssignments()));
        return signing.future();
    }

    private Map<Long, Long> asNodePartyIds(@NonNull final List<PartyAssignment> partyAssignments) {
        return partyAssignments.stream().collect(toMap(PartyAssignment::nodeId, PartyAssignment::partyId));
    }
}
