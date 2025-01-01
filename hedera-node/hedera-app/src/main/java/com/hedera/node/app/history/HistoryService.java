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

import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.platform.state.service.ReadableRosterStore;
import com.swirlds.state.lifecycle.Service;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.time.Instant;

/**
 * Proves inclusion of metadata in a history of rosters.
 */
public interface HistoryService extends Service {
    String NAME = "HistoryService";

    /**
     * A source of metadata to be incorporated into the roster history.
     */
    @FunctionalInterface
    interface MetadataSource {
        /**
         * Returns the metadata to be incorporated for the given roster hash, if known.
         * @param rosterHash the roster hash
         * @return the metadata, or {@code null} if not yet known for this roster hash
         */
        @Nullable
        Bytes metadataFor(@NonNull Bytes rosterHash);
    }

    /**
     * Whether this service is ready to provide metadata-enriched proofs.
     */
    boolean isReady();

    /**
     * Reconciles the history of rosters with the current roster.
     * @param now the current time
     * @param rosterStore the roster store
     * @param metadataSource the metadata source
     * @param historyStore the history store
     */
    void reconcile(
            @NonNull Instant now,
            @NonNull ReadableRosterStore rosterStore,
            @NonNull MetadataSource metadataSource,
            @NonNull WritableHistoryStore historyStore);

    /**
     * Returns a proof of inclusion of the given metadata for the current roster.
     * @param metadata the metadata that must be included in the proof
     * @return the proof
     * @throws IllegalStateException if the service is not ready
     * @throws IllegalArgumentException if the metadata for the current roster does not match the given metadata
     */
    @NonNull
    Bytes getCurrentProof(@NonNull Bytes metadata);

    @Override
    default @NonNull String getServiceName() {
        return NAME;
    }
}
