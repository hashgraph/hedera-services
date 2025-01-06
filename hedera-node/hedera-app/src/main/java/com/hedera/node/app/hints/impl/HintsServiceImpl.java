/*
 * Copyright (C) 2025 Hedera Hashgraph, LLC
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

import com.hedera.node.app.hints.HintsService;
import com.hedera.node.app.hints.WritableHintsStore;
import com.hedera.node.app.roster.ActiveRosters;
import com.hedera.node.app.spi.AppContext;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.state.lifecycle.SchemaRegistry;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;

/**
 * Placeholder implementation of the {@link HintsService}.
 */
public class HintsServiceImpl implements HintsService {
    public HintsServiceImpl(@NonNull final AppContext appContext) {
        requireNonNull(appContext);
    }

    @Override
    public void reconcile(
            @NonNull final ActiveRosters activeRosters,
            @NonNull final WritableHintsStore hintsStore,
            @NonNull final Instant now) {
        requireNonNull(activeRosters);
        requireNonNull(hintsStore);
        requireNonNull(now);
        throw new UnsupportedOperationException();
    }

    @Override
    public @NonNull Bytes currentVerificationKeyOrThrow() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void registerSchemas(@NonNull final SchemaRegistry registry) {
        requireNonNull(registry);
    }

    @Override
    public boolean isReady() {
        throw new UnsupportedOperationException();
    }

    @Override
    public CompletableFuture<Bytes> signFuture(@NonNull final Bytes blockHash) {
        requireNonNull(blockHash);
        throw new UnsupportedOperationException();
    }
}
