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

import com.hedera.hapi.node.state.history.HistoryProof;
import com.hedera.hapi.node.state.history.HistoryProofConstruction;
import com.hedera.hapi.node.state.history.HistoryProofVote;
import com.hedera.hapi.node.state.history.HistorySignature;
import com.hedera.node.app.roster.ActiveRosters;
import com.hedera.node.config.data.TssConfig;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Instant;

/**
 * Provides write access to the {@link HistoryService} state.
 */
public interface WritableHistoryStore extends ReadableHistoryStore {
    /**
     * If there is a known construction matching the active rosters, returns it; otherwise, null.
     * @param activeRosters the active rosters
     * @param now the current time
     * @param tssConfig the TSS configuration
     */
    @NonNull
    HistoryProofConstruction getOrCreateConstruction(
            @NonNull ActiveRosters activeRosters, @NonNull Instant now, @NonNull TssConfig tssConfig);

    /**
     * Includes the given proof key for the given node, assigning the given adoption time if the key
     * is immediately in use.
     *
     * @param nodeId the node ID
     * @param proofKey the hints key to include
     * @param now the adoption time
     * @return whether the key was immediately in use
     */
    boolean setProofKey(long nodeId, @NonNull Bytes proofKey, @NonNull Instant now);

    /**
     * Sets the assembly time for the construction with the given ID and returns the
     * updated construction.
     * @param constructionId the construction ID
     * @param now the aggregation time
     * @return the updated construction
     */
    HistoryProofConstruction setAssemblyTime(long constructionId, @NonNull Instant now);

    /**
     * Adds a node's signature on a particular assembled history proof for the given construction.
     */
    void addSignature(long nodeId, long constructionId, @NonNull HistorySignature signature, @NonNull Instant now);

    /**
     * Adds a history proof vote for the given node and construction.
     */
    void addProofVote(long nodeId, long constructionId, @NonNull HistoryProofVote vote);

    /**
     * Completes the proof for the construction with the given ID and returns the updated construction.
     * @param constructionId the construction ID
     * @param proof the proof
     * @return the updated construction
     */
    HistoryProofConstruction completeProof(long constructionId, @NonNull HistoryProof proof);

    /**
     * Sets the ledger ID to the given bytes.
     * @param bytes the bytes
     */
    void setLedgerId(@NonNull Bytes bytes);

    /**
     * Purges any state no longer needed after a given handoff.
     * @return whether any state was purged
     */
    boolean purgeStateAfterHandoff(@NonNull ActiveRosters activeRosters);
}
