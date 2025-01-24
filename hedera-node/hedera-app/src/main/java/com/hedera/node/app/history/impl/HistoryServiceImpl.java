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

import com.hedera.hapi.node.state.history.HistoryProof;
import com.hedera.node.app.history.HistoryLibrary;
import com.hedera.node.app.history.HistoryService;
import com.hedera.node.app.history.WritableHistoryStore;
import com.hedera.node.app.history.schemas.V059HistorySchema;
import com.hedera.node.app.roster.ActiveRosters;
import com.hedera.node.app.spi.AppContext;
import com.hedera.node.config.data.TssConfig;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.metrics.api.Metrics;
import com.swirlds.state.lifecycle.SchemaRegistry;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.time.Instant;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/**
 * Default implementation of the {@link HistoryService}.
 */
public class HistoryServiceImpl implements HistoryService, Consumer<HistoryProof> {
    private final HistoryServiceComponent component;

    /**
     * If not null, the proof of the history ending at the current roster.
     */
    @Nullable
    private HistoryProof historyProof;

    public HistoryServiceImpl(
            @NonNull final Metrics metrics,
            @NonNull final Executor executor,
            @NonNull final AppContext appContext,
            @NonNull final HistoryLibrary library,
            @NonNull final HistoryLibraryCodec codec) {
        component = DaggerHistoryServiceComponent.factory().create(library, codec, appContext, executor, metrics, this);
    }

    @Override
    public void reconcile(
            @NonNull final ActiveRosters activeRosters,
            @Nullable final Bytes metadata,
            @NonNull final WritableHistoryStore historyStore,
            @NonNull final Instant now,
            @NonNull final TssConfig tssConfig) {
        requireNonNull(activeRosters);
        requireNonNull(historyStore);
        requireNonNull(now);
        requireNonNull(tssConfig);
        throw new UnsupportedOperationException();
    }

    @Override
    public void accept(@NonNull final HistoryProof historyProof) {
        this.historyProof = historyProof;
    }

    @Override
    public boolean isReady() {
        return historyProof != null;
    }

    @Override
    public @NonNull Bytes getCurrentProof(@NonNull final Bytes metadata) {
        requireNonNull(metadata);
        requireNonNull(historyProof);
        final var targetMetadata = historyProof.targetHistoryOrThrow().metadata();
        if (!targetMetadata.equals(metadata)) {
            throw new IllegalArgumentException(
                    "Metadata '" + metadata + "' does not match proof (for '" + targetMetadata + "')");
        }
        return historyProof.proof();
    }

    @Override
    public void registerSchemas(@NonNull final SchemaRegistry registry) {
        requireNonNull(registry);
        registry.register(new V059HistorySchema(this));
    }
}
