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

package com.swirlds.platform.state;

import com.hedera.hapi.node.state.roster.Roster;
import com.swirlds.platform.state.signed.ReservedSignedState;
import com.swirlds.platform.system.SoftwareVersion;
import edu.umd.cs.findbugs.annotations.NonNull;

public interface RosterStateModifier extends RosterStateAccessor {

    /**
     * Sets the candidate roster.
     * This will be called to inform the platform of a new candidate roster.
     *
     * @param candidateRoster a candidate roster to set
     */
    void setCandidateRoster(@NonNull final Roster candidateRoster);

    /**
     * Determines the initial active roster based on the network start-up state.
     *
     * @param version the software version of the current node
     * @param initialState the initial state of the platform
     * @return the active roster which will be used by the platform
     */
    Roster determineActiveRoster(
            @NonNull final SoftwareVersion version, @NonNull final ReservedSignedState initialState);
}
