// SPDX-License-Identifier: Apache-2.0
package com.hedera.node.app.hints;

import com.hedera.hapi.node.state.hints.HintsConstruction;
import com.hedera.hapi.node.state.hints.PreprocessedKeys;
import com.hedera.hapi.node.state.hints.PreprocessingVote;
import com.hedera.node.app.roster.ActiveRosters;
import com.hedera.node.config.data.TssConfig;
import com.hedera.pbj.runtime.io.buffer.Bytes;
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
    HintsConstruction getOrCreateConstruction(
            @NonNull ActiveRosters activeRosters, @NonNull Instant now, @NonNull TssConfig tssConfig);

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
    boolean setHintsKey(long nodeId, int partyId, int numParties, @NonNull Bytes hintsKey, @NonNull final Instant now);

    /**
     * Adds a preprocessing vote for the given node and construction.
     */
    void addPreprocessingVote(long nodeId, long constructionId, @NonNull PreprocessingVote vote);

    /**
     * Sets the consensus preprocessing output for the construction with the given ID and returns the
     * updated construction.
     * @return the updated construction
     */
    HintsConstruction setHintsScheme(
            long constructionId, @NonNull PreprocessedKeys keys, @NonNull Map<Long, Integer> nodePartyIds);

    /**
     * Sets the preprocessing start time for the construction with the given ID and returns the updated construction.
     * @param constructionId the construction ID
     * @param now the aggregation time
     * @return the updated construction
     */
    HintsConstruction setPreprocessingStartTime(long constructionId, @NonNull Instant now);

    /**
     * Purges any state no longer needed after a given handoff.
     */
    void updateForHandoff(@NonNull ActiveRosters activeRosters);
}
