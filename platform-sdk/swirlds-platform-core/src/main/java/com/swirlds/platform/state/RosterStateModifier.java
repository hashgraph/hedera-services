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
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Read-write interface for modifying rosters states.
 */
public interface RosterStateModifier extends RosterStateAccessor {

    /**
     * Sets the candidate roster. This will be called to inform the platform of a new candidate roster.
     *
     * @param candidateRoster a candidate roster to set
     */
    void setCandidateRoster(@NonNull final Roster candidateRoster);

    /**
     * Sets the Active roster. This will be called to store a new Active Roster in the state
     *
     * @param roster an active roster to set
     */
    void setActiveRoster(@NonNull final Roster roster, final long round);
}
