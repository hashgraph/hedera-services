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

package com.swirlds.platform.roster;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.hedera.hapi.node.state.roster.Roster;
import com.hedera.hapi.node.state.roster.RosterEntry;
import com.swirlds.common.crypto.Hash;
import org.junit.jupiter.api.Test;

public class RosterUtilsTest {
    @Test
    void tesetHash() {
        final Hash hash = RosterUtils.hash(Roster.DEFAULT);
        assertEquals(
                "38b060a751ac96384cd9327eb1b1e36a21fdb71114be07434c0cc7bf63f6e1da274edebfe76f65fbd51ad2f14898b95b",
                hash.toString());

        final Hash anotherHash = RosterUtils.hash(
                Roster.DEFAULT.copyBuilder().rosterEntries(RosterEntry.DEFAULT).build());
        assertEquals(
                "5d693ce2c5d445194faee6054b4d8fe4a4adc1225cf0afc2ecd7866ea895a0093ea3037951b75ab7340b75699aa1db1d",
                anotherHash.toString());

        final Hash validRosterHash = RosterUtils.hash(RosterValidatorTests.buildValidRoster());
        assertEquals(
                "1b8414aa690d96ce79e972abfc58c7ca04052996f89c5e6789b25b9051ee85fccb7c8ed3fc6ebacef177adfdcbbb5709",
                validRosterHash.toString());
    }
}
