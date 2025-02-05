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

import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.state.hints.CRSState;
import com.hedera.hapi.node.state.hints.HintsConstruction;
import com.hedera.hapi.node.state.hints.PreprocessingVote;
import com.hedera.node.app.roster.ActiveRosters;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Gives read access to the primary and secondary hinTS service states.
 */
public interface ReadableHintsStore {

    /**
     * The full record of a hinTS key publication, including the key, the time it was adopted, the submitting node id,
     * and the party id the node claimed to have for the construction. (For validating its submission.)
     *
     * @param nodeId the id of the node submitting the key
     * @param hintsKey the hinTS key itself
     * @param partyId the party id the node claimed to have for the construction
     * @param adoptionTime the time at which the key was adopted
     */
    record HintsKeyPublication(long nodeId, @NonNull Bytes hintsKey, int partyId, @NonNull Instant adoptionTime) {
        public HintsKeyPublication {
            requireNonNull(hintsKey);
            requireNonNull(adoptionTime);
        }
    }

    /**
     * Returns the active construction.
     */
    @NonNull
    HintsConstruction getActiveConstruction();

    /**
     * Returns the next construction.
     */
    @NonNull
    HintsConstruction getNextConstruction();

    /**
     * Returns whether the give roster hash is ready to be adopted.
     * @param rosterHash the roster hash
     * @return whether the give roster hash is ready to be adopted
     */
    default boolean isReadyToAdopt(@NonNull final Bytes rosterHash) {
        final var construction = getNextConstruction();
        return construction.hasHintsScheme() && construction.targetRosterHash().equals(rosterHash);
    }

    /**
     * Returns the verification key for the current roster, if known.
     *
     * @return the verification key, or null if not yet known
     */
    @Nullable
    Bytes getActiveVerificationKey();

    /**
     * If there is a known construction matching the active rosters, returns it; otherwise, null.
     * @param activeRosters the active rosters
     */
    @Nullable
    HintsConstruction getConstructionFor(@NonNull ActiveRosters activeRosters);

    /**
     * Returns all known preprocessing output votes from the given nodes for the given construction id.
     * @param constructionId the construction id
     * @param nodeIds the node ids
     * @return the preprocessed keys and votes, or null
     */
    @NonNull
    Map<Long, PreprocessingVote> getVotes(long constructionId, @NonNull Set<Long> nodeIds);

    /**
     * Returns the hinTS keys published by the given set of nodes for the given party size.
     * @param nodeIds the node ids
     * @param numParties the number of parties in the scheme
     * @return the {@link HintsKeyPublication}s
     */
    @NonNull
    List<HintsKeyPublication> getHintsKeyPublications(@NonNull Set<Long> nodeIds, int numParties);

    CRSState getCrsState();
}
