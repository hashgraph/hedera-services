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

import com.hedera.hapi.node.state.hints.HintsConstruction;
import com.hedera.hapi.node.state.hints.HintsKey;
import com.hedera.hapi.node.state.hints.PreprocessedKeysVote;
import com.hedera.node.app.roster.ActiveRosters;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Encapsulates information about the hints in state that are not directly scoped to
 * a particular maximum universe size.
 */
public interface ReadableHintsStore {
    /**
     * The full record of a hinTS key publication, including the key, the submitting node id, and (importantly)
     * the party id for that node id in this construction.
     *
     * @param nodeId the node ID submitting the key
     * @param hintsKey the hinTS key itself
     * @param partyId the party ID for the node in this construction
     * @param adoptionTime the time at which the key was adopted
     */
    record HintsKeyPublication(long nodeId, @NonNull HintsKey hintsKey, int partyId, @NonNull Instant adoptionTime) {
        public HintsKeyPublication {
            requireNonNull(hintsKey);
            requireNonNull(adoptionTime);
        }
    }

    /**
     * Returns the id of the active construction.
     */
    HintsConstruction getActiveConstruction();

    /**
     * Returns the verification key for the given target roster hash, if it exists.
     * @param targetRosterHash the target roster hash
     */
    @Nullable
    Bytes getVerificationKeyFor(@NonNull Bytes targetRosterHash);

    /**
     * If there is a known construction matching the active rosters, returns it; otherwise, null.
     */
    @Nullable
    HintsConstruction getConstructionFor(@NonNull ActiveRosters activeRosters);

    /**
     * If there is a known construction with the given source and target roster hashes,
     * returns the corresponding {@link HintsConstruction}; otherwise, returns null.
     * @param sourceRosterHash the source roster hash
     * @param targetRosterHash the target roster hash
     * @return the corresponding {@link HintsConstruction}, or null
     */
    @Nullable
    HintsConstruction getConstructionFor(@Nullable Bytes sourceRosterHash, @NonNull Bytes targetRosterHash);

    /**
     * Returns the preprocessed keys and votes for the given construction id, if they exist.
     * @param constructionId the construction id
     * @param nodeIds the node ids
     * @return the preprocessed keys and votes, or null
     */
    @NonNull
    Map<Long, PreprocessedKeysVote> votesFor(long constructionId, @NonNull Set<Long> nodeIds);

    /**
     * Returns the hinTS keys published by the given set of nodes for the given party size.
     * @param nodeIds the node ids
     * @param partySize the party size
     * @return the {@link HintsKeyPublication}s
     */
    @NonNull
    List<HintsKeyPublication> getNodeHintsKeyPublications(@NonNull Set<Long> nodeIds, int partySize);
}
