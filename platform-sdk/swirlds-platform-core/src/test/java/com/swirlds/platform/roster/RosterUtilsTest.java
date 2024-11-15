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
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.hedera.hapi.node.base.ServiceEndpoint;
import com.hedera.hapi.node.state.roster.Roster;
import com.hedera.hapi.node.state.roster.RosterEntry;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.common.crypto.Hash;
import java.util.List;
import org.junit.jupiter.api.Test;

public class RosterUtilsTest {
    @Test
    void testHash() {
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
                "b58744d9cfbceda7b1b3c50f501c3ab30dc4ea7e59e96c8071a7bb2198e7071bde40535605c7f37db47e7a1efe5ef280",
                validRosterHash.toString());
    }

    @Test
    void testFetchHostname() {
        assertEquals(
                "domain.name",
                RosterUtils.fetchHostname(
                        RosterEntry.newBuilder()
                                .gossipEndpoint(List.of(ServiceEndpoint.newBuilder()
                                        .port(666)
                                        .domainName("domain.name")
                                        .build()))
                                .build(),
                        0));

        assertEquals(
                "domain.name.2",
                RosterUtils.fetchHostname(
                        RosterEntry.newBuilder()
                                .gossipEndpoint(List.of(
                                        ServiceEndpoint.newBuilder()
                                                .port(666)
                                                .domainName("domain.name")
                                                .build(),
                                        ServiceEndpoint.newBuilder()
                                                .port(666)
                                                .domainName("domain.name.2")
                                                .build()))
                                .build(),
                        1));

        assertEquals(
                "10.0.0.1",
                RosterUtils.fetchHostname(
                        RosterEntry.newBuilder()
                                .gossipEndpoint(List.of(ServiceEndpoint.newBuilder()
                                        .port(666)
                                        .ipAddressV4(Bytes.wrap(new byte[] {10, 0, 0, 1}))
                                        // While the below violates the ServiceEndpoint specification,
                                        // there's no any hard validations present, and we want to ensure
                                        // the logic in the RosterUtils.fetchHostname() picks up the IP
                                        // instead of the domainName in this case, so we provide both in this test:
                                        .domainName("domain.name")
                                        .build()))
                                .build(),
                        0));

        assertThrows(
                IllegalArgumentException.class,
                () -> RosterUtils.fetchHostname(
                        RosterEntry.newBuilder()
                                .gossipEndpoint(List.of(ServiceEndpoint.newBuilder()
                                        .port(666)
                                        .ipAddressV4(Bytes.wrap(new byte[] {10, 0, 0, 1, 2}))
                                        // While the below violates the ServiceEndpoint specification,
                                        // there's no any hard validations present, and we want to ensure
                                        // the logic in the RosterUtils.fetchHostname() picks up the IP
                                        // instead of the domainName in this case, so we provide both in this test:
                                        .domainName("domain.name")
                                        .build()))
                                .build(),
                        0));
    }

    @Test
    void testFetchPort() {
        assertEquals(
                666,
                RosterUtils.fetchPort(
                        RosterEntry.newBuilder()
                                .gossipEndpoint(List.of(ServiceEndpoint.newBuilder()
                                        .port(666)
                                        .domainName("domain.name")
                                        .build()))
                                .build(),
                        0));

        assertEquals(
                777,
                RosterUtils.fetchPort(
                        RosterEntry.newBuilder()
                                .gossipEndpoint(List.of(
                                        ServiceEndpoint.newBuilder()
                                                .port(666)
                                                .domainName("domain.name")
                                                .build(),
                                        ServiceEndpoint.newBuilder()
                                                .port(777)
                                                .domainName("domain.name.2")
                                                .build()))
                                .build(),
                        1));

        assertEquals(
                888,
                RosterUtils.fetchPort(
                        RosterEntry.newBuilder()
                                .gossipEndpoint(List.of(ServiceEndpoint.newBuilder()
                                        .port(888)
                                        .ipAddressV4(Bytes.wrap(new byte[] {10, 0, 0, 1}))
                                        .build()))
                                .build(),
                        0));
    }
}
