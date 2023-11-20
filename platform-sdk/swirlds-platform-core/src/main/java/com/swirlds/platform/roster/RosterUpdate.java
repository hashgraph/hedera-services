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
 * @param effectiveRound the round when this roster becomes effective
 * @param roster         the roster that will be used to compute consensus for the effective round
 * @param rosterDiff     describes the difference between the new roster and the roster that is effective in round
 *                       ({@link #effectiveRound} - 1).
 */
public record RosterUpdate(
        long effectiveRound, @NonNull AddressBook roster, @NonNull RosterDiff rosterDiff) {
}
