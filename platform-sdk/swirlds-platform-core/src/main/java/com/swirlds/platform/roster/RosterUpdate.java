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
 * @param round               the current round
 * @param effectiveRoster     the roster that was effective in the specified round (i.e. the roster that was used to
 *                            compute consensus for the round)
 * @param effectiveRosterDiff describes the difference between this round's effective roster and the previous round's
 *                            effective roster
 * @param createdRoster       the roster that was created in the specified round
 * @param createdRosterDiff   describes the difference between this round's created roster and the previous round's
 *                            created roster
 * @param roundsNonAncient    the number of non-ancient rounds for the current round
 * @param rosterOffset        the roster offset for the current round, describes the number of rounds ago when the
 *                            effective roster was created. For example, a roster offset of 3 means that the current
 *                            effective roster was created 3 rounds ago.
 */
public record RosterUpdate(
        long round,
        @NonNull AddressBook effectiveRoster,
        @NonNull RosterDiff effectiveRosterDiff,
        @NonNull AddressBook createdRoster,
        @NonNull RosterDiff createdRosterDiff,
        int roundsNonAncient,
        int rosterOffset) {
}
