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

import com.hedera.hapi.node.state.history.MetadataProofConstruction;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Instant;

public interface WritableHistoryStore extends ReadableHistoryStore {
    /**
     * Reschedules the next assembly checkpoint for the construction with the given ID and returns the
     * updated construction.
     * @param constructionId the construction ID
     * @param then the next assembly checkpoint
     * @return the updated construction
     */
    MetadataProofConstruction rescheduleAssemblyCheckpoint(long constructionId, @NonNull Instant then);

    /**
     * Sets the assembly time for the construction with the given ID and returns the
     * updated construction.
     * @param constructionId the construction ID
     * @param now the aggregation time
     * @return the updated construction
     */
    MetadataProofConstruction setAssemblyTime(long constructionId, @NonNull Instant now);
}
