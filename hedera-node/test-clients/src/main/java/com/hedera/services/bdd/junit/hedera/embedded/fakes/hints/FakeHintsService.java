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

package com.hedera.services.bdd.junit.hedera.embedded.fakes.hints;

import com.hedera.node.app.hints.HintsService;
import com.hedera.node.app.hints.WritableHintsStore;
import com.hedera.node.app.hints.handlers.HintsHandlers;
import com.hedera.node.app.hints.impl.HintsServiceImpl;
import com.hedera.node.app.roster.ActiveRosters;
import com.hedera.node.app.spi.AppContext;
import com.hedera.node.config.data.TssConfig;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.common.metrics.noop.NoOpMetrics;
import com.swirlds.state.lifecycle.SchemaRegistry;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;

public class FakeHintsService implements HintsService {
    private final HintsService delegate;
    private final FakeHintsLibrary operations = new FakeHintsLibrary();
    private final Queue<Runnable> pendingHintsSubmissions = new ArrayDeque<>();

    public FakeHintsService(@NonNull final AppContext appContext) {
        delegate = new HintsServiceImpl(new NoOpMetrics(), pendingHintsSubmissions::offer, appContext, operations);
    }

    @Override
    public @NonNull Bytes activeVerificationKeyOrThrow() {
        return delegate.activeVerificationKeyOrThrow();
    }

    @Override
    public boolean isReady() {
        return delegate.isReady();
    }

    @Override
    public CompletableFuture<Bytes> signFuture(@NonNull final Bytes blockHash) {
        return delegate.signFuture(blockHash);
    }

    @Override
    public HintsHandlers handlers() {
        return delegate.handlers();
    }

    @Override
    public void reconcile(
            @NonNull final ActiveRosters activeRosters,
            @NonNull final WritableHintsStore hintsStore,
            @NonNull final Instant now,
            @NonNull final TssConfig tssConfig) {
        delegate.reconcile(activeRosters, hintsStore, now, tssConfig);
    }

    @Override
    public void stop() {
        delegate.stop();
    }

    @Override
    public void registerSchemas(@NonNull final SchemaRegistry registry) {
        delegate.registerSchemas(registry);
    }
}
