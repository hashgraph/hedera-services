// SPDX-License-Identifier: Apache-2.0
package com.swirlds.platform.state.service;

import com.hedera.hapi.node.state.roster.Roster;
import com.hedera.hapi.node.state.roster.RoundRosterPair;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.List;

/**
 * Read-only implementation for accessing rosters states.
 */
public interface ReadableRosterStore {
    /**
     * Gets the candidate roster if found in state or null otherwise.
     * Note that state commits are buffered,
     * so it is possible that a recently stored candidate roster is still in the batched changes and not yet committed.
     * Therefore, callers of this API must bear in mind that an immediate call after storing a candidate roster may return null.
     *
     * @return the candidate roster
     */
    @Nullable
    Roster getCandidateRoster();

    /**
     * Gets the active roster.
     * Returns the active roster iff:
     *      the roster state singleton is not null
     *      the list of round roster pairs is not empty
     *      the first round roster pair exists
     *      the active roster hash is present in the roster map
     * otherwise returns null.
     * @return the active roster
     */
    @Nullable
    Roster getActiveRoster();

    /**
     * Get the roster based on roster hash
     *
     * @param rosterHash The roster hash
     * @return The roster.
     */
    @Nullable
    Roster get(@NonNull Bytes rosterHash);

    /**
     * Gets the roster history.
     * Returns the active roster history iff:
     *      the roster state singleton is not null
     *      the list of round roster pairs is not empty
     *      the active roster hashes are present in the roster map
     * otherwise returns null.
     * @return the active rosters
     */
    @NonNull
    List<RoundRosterPair> getRosterHistory();

    /**
     * Get the current roster hash.
     * @return The current roster hash.
     */
    @Nullable
    Bytes getCurrentRosterHash();

    /**
     * Get the previous roster hash, if present. If the current roster is the genesis
     * roster, returns null.
     */
    @Nullable
    Bytes getPreviousRosterHash();

    /**
     * Gets the candidate roster hash, if present. If none is set, returns null;
     */
    @Nullable
    Bytes getCandidateRosterHash();
}
