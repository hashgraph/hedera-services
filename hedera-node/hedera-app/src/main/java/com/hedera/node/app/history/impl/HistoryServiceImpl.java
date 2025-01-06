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

package com.hedera.node.app.history.impl;

import static java.util.Objects.requireNonNull;

import com.hedera.node.app.history.HistoryService;
import com.hedera.node.app.history.WritableHistoryStore;
import com.hedera.node.app.roster.ActiveRosters;
import com.hedera.node.app.spi.AppContext;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.state.lifecycle.SchemaRegistry;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.time.Instant;

/**
 * Placeholder implementation of the {@link HistoryService}.
 */
public class HistoryServiceImpl implements HistoryService {
    public HistoryServiceImpl(@NonNull final AppContext appContext) {
        requireNonNull(appContext);
    }

    @Override
    public boolean isReady() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void reconcile(
            @NonNull ActiveRosters activeRosters,
            @Nullable Bytes currentMetadata,
            @NonNull WritableHistoryStore historyStore,
            @NonNull Instant now) {
        requireNonNull(activeRosters);
        requireNonNull(historyStore);
        requireNonNull(now);
        throw new UnsupportedOperationException();
    }

    @NonNull
    @Override
    public Bytes getCurrentProof(@NonNull final Bytes metadata) {
        requireNonNull(metadata);
        throw new UnsupportedOperationException();
    }

    @Override
    public void registerSchemas(@NonNull final SchemaRegistry registry) {
        requireNonNull(registry);
        throw new UnsupportedOperationException();
    }
}
