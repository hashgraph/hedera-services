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

package com.swirlds.platform.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.hedera.hapi.node.state.roster.Roster;
import com.hedera.hapi.node.state.roster.RosterEntry;
import com.swirlds.common.crypto.Hash;
import org.junit.jupiter.api.Test;

public class PbjRecordHasherTest {
    @Test
    void testHash() {
        final PbjRecordHasher hasher = new PbjRecordHasher();

        final Hash hash = hasher.hash(Roster.DEFAULT, Roster.PROTOBUF);
        assertEquals(
                "38b060a751ac96384cd9327eb1b1e36a21fdb71114be07434c0cc7bf63f6e1da274edebfe76f65fbd51ad2f14898b95b",
                hash.toString());

        final Hash anotherHash = hasher.hash(
                Roster.DEFAULT.copyBuilder().rosterEntries(RosterEntry.DEFAULT).build(), Roster.PROTOBUF);
        assertEquals(
                "5d693ce2c5d445194faee6054b4d8fe4a4adc1225cf0afc2ecd7866ea895a0093ea3037951b75ab7340b75699aa1db1d",
                anotherHash.toString());
    }
}
