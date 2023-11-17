/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform.roster;

import com.swirlds.common.system.address.AddressBook;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Describes a (potential) update to the effective roster. Note that a roster update is sent once per round even if
 * there is not change in the roster.
 *
 * @param pendingConsensusRound the current round that the hashgraph is working on (i.e. the round that will reach
 *                              consensus next). This is equivalent to the round in which {@link #effectiveRoster}
 *                              becomes effective.
 * @param effectiveRoster       the roster that is being used to compute consensus for {@link #pendingConsensusRound}
 * @param effectiveRosterDiff   describes the difference between the new effective roster and the previous effective
 *                              roster
 */
public record RosterUpdate(
        long pendingConsensusRound,
        long minimumRoundNonAncient,
        @NonNull AddressBook effectiveRoster,
        @NonNull RosterDiff effectiveRosterDiff) {

}
