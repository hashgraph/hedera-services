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

package com.hedera.node.app.roster;

import static java.util.Objects.requireNonNull;

import com.swirlds.platform.roster.RosterHistory;
import com.swirlds.platform.state.service.ReadableRosterStore;
import edu.umd.cs.findbugs.annotations.NonNull;

public class RosterStartupLogic {
    private final ReadableRosterStore rosterStore;

    public RosterStartupLogic(@NonNull final ReadableRosterStore rosterStore) {
        requireNonNull(rosterStore);
        this.rosterStore = rosterStore;
    }

    /**
     * Determining the Roster History.
     * There are three non-genesis modes that a node can start in:
     * <ul>
     *   <li> Genesis Network - The node is started with a genesis roster and no pre-existing state on disk. </li>
     *   <li> Network Transplant - The node is started with a state on disk and an overriding roster for a different network. </li>
     *   <li> Software Upgrade - The node is restarted with the same state on disk and a software upgrade is happening. </li>
     *   <li> Normal Restart - The node is restarted with the same state on disk and no software upgrade is happening. </li>
     * </ul>
     *
     * @return the roster history if able to determine it, otherwise IllegalStateException is thrown
     */
    public RosterHistory determineRosterHistory() {
        final var roundRosterPairs = rosterStore.getRosterHistory();
        // If there exists active rosters in the roster state.
        if (roundRosterPairs != null) {
            // Read the active rosters and construct the existing rosterHistory from roster state
            final var current = roundRosterPairs.get(0);
            final var previous = roundRosterPairs.get(1);
            return new RosterHistory(
                    rosterStore.get(current.activeRosterHash()),
                    current.roundNumber(),
                    rosterStore.get(previous.activeRosterHash()),
                    previous.roundNumber());
        } else {
            // If there is no roster state content, this is a fatal error: The migration did not happen on software
            // upgrade.
            throw new IllegalStateException("No active rosters found in the roster state");
        }
    }
}
