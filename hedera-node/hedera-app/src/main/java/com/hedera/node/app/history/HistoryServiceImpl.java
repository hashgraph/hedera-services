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

package com.hedera.node.app.history;

import static java.util.Objects.requireNonNull;

import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.platform.state.service.ReadableRosterStore;
import com.swirlds.state.lifecycle.SchemaRegistry;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Instant;

public class HistoryServiceImpl implements HistoryService {
    @Override
    public boolean isReady() {
        return true;
    }

    @Override
    public void reconcile(
            @NonNull final Instant now,
            @NonNull final ReadableRosterStore rosterStore,
            @NonNull final RosterMetadataSource metadataSource,
            @NonNull final WritableHistoryStore historyStore) {
        requireNonNull(now);
        requireNonNull(rosterStore);
        requireNonNull(metadataSource);
        requireNonNull(historyStore);
        // No-op
    }

    @Override
    public @NonNull Bytes getCurrentProof(@NonNull Bytes metadata) {
        requireNonNull(metadata);
        return Bytes.EMPTY;
    }

    @Override
    public void registerSchemas(@NonNull final SchemaRegistry registry) {
        requireNonNull(registry);
    }
}
