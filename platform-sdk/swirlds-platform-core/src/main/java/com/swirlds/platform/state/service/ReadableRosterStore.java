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

package com.swirlds.platform.state.service;

import com.hedera.hapi.node.state.roster.Roster;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;

/**
 * Provides read-only methods for interacting with Rosters.
 */
public interface ReadableRosterStore {

    /**
     * Set the candidate roster.
     *
     * @param candidateRoster a candidate roster to set
     */
    void setCandidateRoster(@NonNull final Roster candidateRoster);

    /**
     * Get the candidate roster.
     *
     * @return the candidate roster
     */
    @Nullable
    Roster getCandidateRoster();

    /**
     * Set the active roster.
     *
     * @param activeRoster an active roster to set
     * @param round        the round in which this roster became active
     */
    void setActiveRoster(@NonNull final Roster activeRoster, final long round);

    /**
     * Get the active roster.
     *
     * @return the active roster
     */
    @Nullable
    Roster getActiveRoster();

    /**
     * adopts the candidate roster.
     * @param roundNumber the next round number when the adopted roster will be active
     */
    void adoptCandidateRoster(long roundNumber);

    /**
     * Get the round number when the active roster was set.
     *
     * @return the round number when the active roster was set
     */
    @SuppressWarnings("unused")
    long getActiveRosterRoundNumber();
}
