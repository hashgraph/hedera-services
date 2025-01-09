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

package com.hedera.node.app.hints;

import com.hedera.hapi.node.state.hints.HintsConstruction;
import com.hedera.hapi.node.state.hints.HintsKey;
import com.hedera.hapi.node.state.hints.PreprocessedKeys;
import com.hedera.node.app.roster.ActiveRosters;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Instant;
import java.util.Map;

/**
 * Provides write access to the {@link HintsConstruction} instances in state.
 */
public interface WritableHintsStore extends ReadableHintsStore {
    /**
     * If there is a known construction matching the active rosters, returns it; otherwise, null.
     */
    @NonNull
    HintsConstruction getOrCreateConstruction(@NonNull ActiveRosters activeRosters, @NonNull Instant now);

    /**
     * Includes the given hints key for the given node and party IDs relative to a party size, assigning
     * the given adoption time if the key is immediately in use.
     *
     * @param nodeId the node ID
     * @param partyId the party ID
     * @param numParties the number of parties
     * @param hintsKey the hints key to include
     * @param now the adoption time
     * @return whether the key was immediately in use
     */
    boolean setHintsKey(
            long nodeId, int partyId, int numParties, @NonNull HintsKey hintsKey, @NonNull final Instant now);

    /**
     * Sets the consensus preprocessing output for the construction with the given ID and returns the
     * updated construction.
     * @return the updated construction
     */
    HintsConstruction setPreprocessingOutput(
            long constructionId, @NonNull PreprocessedKeys keys, @NonNull Map<Long, Integer> nodePartyIds);

    /**
     * Sets the preprocessing start time for the construction with the given ID and returns the updated construction.
     * @param constructionId the construction ID
     * @param now the aggregation time
     * @return the updated construction
     */
    HintsConstruction setPreprocessingStartTime(long constructionId, @NonNull Instant now);

    /**
     * Reschedules the next preprocessing checkpoint for the construction with the given ID and returns the
     * updated construction.
     * @param constructionId the construction ID
     * @param then the next preprocessing checkpoint
     * @return the updated construction
     */
    HintsConstruction reschedulePreprocessingCheckpoint(long constructionId, @NonNull Instant then);

    /**
     * Purges any state no longer needed after a given handoff.
     * @return whether any state was purged
     */
    boolean purgeStateAfterHandoff(@NonNull ActiveRosters activeRosters);
}
