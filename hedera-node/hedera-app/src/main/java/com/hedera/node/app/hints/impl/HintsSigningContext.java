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

import com.hedera.hapi.node.state.hints.HintsConstruction;
import com.hedera.node.app.hints.HintsOperations;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Future;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class HintsSigningContext {
    private long activeConstructionId = 0L;

    @Nullable
    private Bytes activeAggregationKey = null;

    @Nullable
    private Map<Long, Long> activeNodeWeights;

    private final HintsOperations operations;

    @Inject
    public HintsSigningContext(@NonNull final HintsOperations operations) {
        this.operations = requireNonNull(operations);
    }

    public class Signing {
        private final long constructionId;
        private final Bytes message;
        private final Map<Long, Long> weights;
        private final CompletableFuture<Bytes> future = new CompletableFuture<>();
        private final ConcurrentMap<Long, Bytes> signatures = new ConcurrentHashMap<>();

        public Signing(
                final long constructionId, @NonNull final Bytes message, @NonNull final Map<Long, Long> weights) {
            this.constructionId = constructionId;
            this.message = requireNonNull(message);
            this.weights = requireNonNull(weights);
        }

        public Future<Bytes> future() {
            return future;
        }

        public void incorporate(final long constructionId, final long nodeId, @NonNull final Bytes signature) {
            requireNonNull(signature);
            if (this.constructionId == constructionId) {}
        }
    }

    public void setActiveConstruction(@NonNull final HintsConstruction construction) {
        requireNonNull(construction);
    }

    public void setActiveConstructionId(final long activeConstructionId) {
        this.activeConstructionId = activeConstructionId;
    }

    public void setActiveNodeWeights(@NonNull final Map<Long, Long> activeNodeWeights) {
        this.activeNodeWeights = requireNonNull(unmodifiableMap(activeNodeWeights));
    }

    public boolean needsActiveNodeWeights() {
        return activeConstructionId > 0 && activeNodeWeights == null;
    }

    public boolean isReady() {
        return activeConstructionId > 0 && activeNodeWeights != null;
    }

    public @NonNull Future<Bytes> signFuture(@NonNull final Bytes blockHash) {
        if (!isReady()) {
            throw new IllegalStateException("Signing context not ready with activeConstructionId="
                    + activeConstructionId + " and activeNodeWeights=" + activeNodeWeights);
        }
        requireNonNull(activeNodeWeights);
        return new Signing(activeConstructionId, blockHash, activeNodeWeights).future();
    }
}
