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

package com.hedera.node.app.hints;

import static java.util.Objects.requireNonNull;

import com.google.common.annotations.VisibleForTesting;
import com.hedera.node.app.hints.handlers.HintsHandlers;
import com.hedera.node.app.hints.schemas.V059HintsSchema;
import com.hedera.node.app.spi.AppContext;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.metrics.api.Metrics;
import com.swirlds.platform.state.service.ReadableRosterStore;
import com.swirlds.state.lifecycle.SchemaRegistry;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Instant;
import java.util.concurrent.Executor;

/**
 * Default implementation of the {@link HintsService}.
 */
public class HintsServiceImpl implements HintsService {
    private final HintsServiceComponent component;

    public HintsServiceImpl(
            @NonNull final Metrics metrics,
            @NonNull final Executor executor,
            @NonNull final AppContext appContext,
            @NonNull final HintsOperations operations) {
        this.component = DaggerHintsServiceComponent.factory().create(operations, appContext, executor, metrics);
    }

    @VisibleForTesting
    public HintsServiceImpl(@NonNull final HintsServiceComponent component) {
        this.component = requireNonNull(component);
    }

    @Override
    public HintsHandlers handlers() {
        return component.handlers();
    }

    @Override
    public void start() {
        // No-op
    }

    @Override
    public void stop() {
        // No-op
    }

    @Override
    public void registerSchemas(@NonNull final SchemaRegistry registry) {
        requireNonNull(registry);
        registry.register(new V059HintsSchema());
    }

    @Override
    public void reconcile(
            @NonNull final Instant now,
            @NonNull final ReadableRosterStore rosterStore,
            @NonNull final WritableHintsStore hintsStore) {
        final var currentRosterHash = requireNonNull(rosterStore.getCurrentRosterHash());
        final var candidateRosterHash = rosterStore.getCandidateRosterHash();
        final Bytes sourceRosterHash;
        final Bytes targetRosterHash;
        if (candidateRosterHash == null) {
            sourceRosterHash = rosterStore.getPreviousRosterHash();
            targetRosterHash = currentRosterHash;
        } else {
            sourceRosterHash = currentRosterHash;
            targetRosterHash = candidateRosterHash;
        }
        var construction = hintsStore.getConstructionFor(sourceRosterHash, targetRosterHash);
        if (construction == null) {
            construction = hintsStore.newConstructionFor(sourceRosterHash, targetRosterHash, rosterStore);
        }
        if (!construction.hasPreprocessedKeys()) {
            final var controller =
                    component.controllers().getOrCreateControllerFor(construction, hintsStore, rosterStore);
            controller.advanceConstruction(now, hintsStore);
        } else if (candidateRosterHash == null) {
            hintsStore.purgeConstructionsNotFor(currentRosterHash);
        }
    }
}
