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

package com.hedera.node.app.history;

import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.state.history.HistorySignature;
import com.hedera.hapi.node.state.history.MetadataProofConstruction;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Instant;

/**
 * Provides write access to the history of rosters.
 */
public interface ReadableHistoryStore {
    /**
     * The full record of a assembly signature publication, include the time the signature was published.
     *
     * @param nodeId the node ID submitting the signature
     * @param signature the assembly signature
     * @param at the time at which the signature was published
     */
    record AssemblySignaturePublication(long nodeId, @NonNull HistorySignature signature, @NonNull Instant at) {
        public AssemblySignaturePublication {
            requireNonNull(signature);
            requireNonNull(at);
        }
    }

    /**
     * Returns the active construction.
     */
    MetadataProofConstruction getActiveConstruction();
}
