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
import org.junit.jupiter.api.Test;

public class RosterValidatorTests {
    @Test
    void nullTest() {
        final Exception ex = assertThrows(InvalidRosterException.class, () -> RosterValidator.validate(null));
        assertEquals("roster is null", ex.getMessage());
    }

    @Test
    void emptyTest() {
        final Exception ex = assertThrows(InvalidRosterException.class, () -> RosterValidator.validate(Roster.DEFAULT));
        assertEquals("roster is empty", ex.getMessage());
    }

    @Test
    void zeroWeightTest() {
        final Exception ex = assertThrows(
                InvalidRosterException.class,
                () -> RosterValidator.validate(Roster.newBuilder()
                        .rosterEntries(
                                RosterEntry.newBuilder()
                                        .nodeId(1)
                                        .weight(0)
                                        .gossipCaCertificate(Bytes.wrap("test"))
                                        .tssEncryptionKey(Bytes.wrap("test"))
                                        .gossipEndpoint(ServiceEndpoint.newBuilder()
                                                .domainName("domain.com")
                                                .port(666)
                                                .build())
                                        .build(),
                                RosterEntry.newBuilder()
                                        .nodeId(2)
                                        .weight(0)
                                        .gossipCaCertificate(Bytes.wrap("test"))
                                        .tssEncryptionKey(Bytes.wrap("test"))
                                        .gossipEndpoint(ServiceEndpoint.newBuilder()
                                                .domainName("domain.com")
                                                .port(666)
                                                .build())
                                        .build(),
                                RosterEntry.newBuilder()
                                        .nodeId(3)
                                        .weight(0)
                                        .gossipCaCertificate(Bytes.wrap("test"))
                                        .tssEncryptionKey(Bytes.wrap("test"))
                                        .gossipEndpoint(ServiceEndpoint.newBuilder()
                                                .domainName("domain.com")
                                                .port(666)
                                                .build())
                                        .build())
                        .build()));
        assertEquals("roster weight is zero or negative", ex.getMessage());
    }

    @Test
    void negativeWeightTest() {
        final Exception ex = assertThrows(
                InvalidRosterException.class,
                () -> RosterValidator.validate(Roster.newBuilder()
                        .rosterEntries(
                                RosterEntry.newBuilder()
                                        .nodeId(1)
                                        .weight(0)
                                        .gossipCaCertificate(Bytes.wrap("test"))
                                        .tssEncryptionKey(Bytes.wrap("test"))
                                        .gossipEndpoint(ServiceEndpoint.newBuilder()
                                                .domainName("domain.com")
                                                .port(666)
                                                .build())
                                        .build(),
                                RosterEntry.newBuilder()
                                        .nodeId(2)
                                        .weight(-1)
                                        .gossipCaCertificate(Bytes.wrap("test"))
                                        .tssEncryptionKey(Bytes.wrap("test"))
                                        .gossipEndpoint(ServiceEndpoint.newBuilder()
                                                .domainName("domain.com")
                                                .port(666)
                                                .build())
                                        .build(),
                                RosterEntry.newBuilder()
                                        .nodeId(3)
                                        .weight(2)
                                        .gossipCaCertificate(Bytes.wrap("test"))
                                        .tssEncryptionKey(Bytes.wrap("test"))
                                        .gossipEndpoint(ServiceEndpoint.newBuilder()
                                                .domainName("domain.com")
                                                .port(666)
                                                .build())
                                        .build())
                        .build()));
        assertEquals("weight is negative for node id: 2", ex.getMessage());
    }

    @Test
    void duplicateNodeIdTest() {
        final Exception ex = assertThrows(
                InvalidRosterException.class,
                () -> RosterValidator.validate(Roster.newBuilder()
                        .rosterEntries(
                                RosterEntry.newBuilder()
                                        .nodeId(1)
                                        .weight(1)
                                        .gossipCaCertificate(Bytes.wrap("test"))
                                        .tssEncryptionKey(Bytes.wrap("test"))
                                        .gossipEndpoint(ServiceEndpoint.newBuilder()
                                                .domainName("domain.com")
                                                .port(666)
                                                .build())
                                        .build(),
                                RosterEntry.newBuilder()
                                        .nodeId(1)
                                        .weight(1)
                                        .gossipCaCertificate(Bytes.wrap("test"))
                                        .tssEncryptionKey(Bytes.wrap("test"))
                                        .gossipEndpoint(ServiceEndpoint.newBuilder()
                                                .domainName("domain.com")
                                                .port(666)
                                                .build())
                                        .build(),
                                RosterEntry.newBuilder()
                                        .nodeId(2)
                                        .weight(1)
                                        .gossipCaCertificate(Bytes.wrap("test"))
                                        .tssEncryptionKey(Bytes.wrap("test"))
                                        .gossipEndpoint(ServiceEndpoint.newBuilder()
                                                .domainName("domain.com")
                                                .port(666)
                                                .build())
                                        .build())
                        .build()));
        assertEquals("duplicate node id: 1", ex.getMessage());
    }

    @Test
    void invalidGossipCertTest() {
        final Exception ex = assertThrows(
                InvalidRosterException.class,
                () -> RosterValidator.validate(Roster.newBuilder()
                        .rosterEntries(
                                RosterEntry.newBuilder()
                                        .nodeId(1)
                                        .weight(1)
                                        .gossipCaCertificate(Bytes.wrap("test"))
                                        .tssEncryptionKey(Bytes.wrap("test"))
                                        .gossipEndpoint(ServiceEndpoint.newBuilder()
                                                .domainName("domain.com")
                                                .port(666)
                                                .build())
                                        .build(),
                                RosterEntry.newBuilder()
                                        .nodeId(2)
                                        .weight(1)
                                        .gossipCaCertificate(Bytes.EMPTY)
                                        .tssEncryptionKey(Bytes.wrap("test"))
                                        .gossipEndpoint(ServiceEndpoint.newBuilder()
                                                .domainName("domain.com")
                                                .port(666)
                                                .build())
                                        .build(),
                                RosterEntry.newBuilder()
                                        .nodeId(3)
                                        .weight(1)
                                        .gossipCaCertificate(Bytes.wrap("test"))
                                        .tssEncryptionKey(Bytes.wrap("test"))
                                        .gossipEndpoint(ServiceEndpoint.newBuilder()
                                                .domainName("domain.com")
                                                .port(666)
                                                .build())
                                        .build())
                        .build()));
        assertEquals("gossipCaCertificate is empty for NodeId 2", ex.getMessage());
    }

    @Test
    void emptyGossipEndpointsTest() {
        final Exception ex = assertThrows(
                InvalidRosterException.class,
                () -> RosterValidator.validate(Roster.newBuilder()
                        .rosterEntries(
                                RosterEntry.newBuilder()
                                        .nodeId(1)
                                        .weight(1)
                                        .gossipCaCertificate(Bytes.wrap("test"))
                                        .tssEncryptionKey(Bytes.wrap("test"))
                                        .gossipEndpoint(ServiceEndpoint.newBuilder()
                                                .domainName("domain.com")
                                                .port(666)
                                                .build())
                                        .build(),
                                RosterEntry.newBuilder()
                                        .nodeId(2)
                                        .weight(1)
                                        .gossipCaCertificate(Bytes.wrap("test"))
                                        .tssEncryptionKey(Bytes.wrap("test"))
                                        .gossipEndpoint(ServiceEndpoint.newBuilder()
                                                .domainName("domain.com")
                                                .port(666)
                                                .build())
                                        .build(),
                                RosterEntry.newBuilder()
                                        .nodeId(3)
                                        .weight(1)
                                        .gossipCaCertificate(Bytes.wrap("test"))
                                        .tssEncryptionKey(Bytes.wrap("test"))
                                        .build())
                        .build()));
        assertEquals("gossipEndpoint is empty for NodeId 3", ex.getMessage());
    }

    @Test
    void zeroPortTest() {
        final Exception ex = assertThrows(
                InvalidRosterException.class,
                () -> RosterValidator.validate(Roster.newBuilder()
                        .rosterEntries(
                                RosterEntry.newBuilder()
                                        .nodeId(1)
                                        .weight(1)
                                        .gossipCaCertificate(Bytes.wrap("test"))
                                        .tssEncryptionKey(Bytes.wrap("test"))
                                        .gossipEndpoint(ServiceEndpoint.newBuilder()
                                                .domainName("domain.com")
                                                .port(0)
                                                .build())
                                        .build(),
                                RosterEntry.newBuilder()
                                        .nodeId(2)
                                        .weight(1)
                                        .gossipCaCertificate(Bytes.wrap("test"))
                                        .tssEncryptionKey(Bytes.wrap("test"))
                                        .gossipEndpoint(ServiceEndpoint.newBuilder()
                                                .domainName("domain.com")
                                                .port(666)
                                                .build())
                                        .build(),
                                RosterEntry.newBuilder()
                                        .nodeId(3)
                                        .weight(2)
                                        .gossipCaCertificate(Bytes.wrap("test"))
                                        .tssEncryptionKey(Bytes.wrap("test"))
                                        .gossipEndpoint(ServiceEndpoint.newBuilder()
                                                .domainName("domain.com")
                                                .port(666)
                                                .build())
                                        .build())
                        .build()));
        assertEquals(
                "gossipPort is zero for NodeId 1 and ServiceEndpoint ServiceEndpoint[ipAddressV4=, port=0, domainName=domain.com]",
                ex.getMessage());
    }

    @Test
    void domainAndIpTest() {
        final Exception ex = assertThrows(
                InvalidRosterException.class,
                () -> RosterValidator.validate(Roster.newBuilder()
                        .rosterEntries(
                                RosterEntry.newBuilder()
                                        .nodeId(1)
                                        .weight(1)
                                        .gossipCaCertificate(Bytes.wrap("test"))
                                        .tssEncryptionKey(Bytes.wrap("test"))
                                        .gossipEndpoint(ServiceEndpoint.newBuilder()
                                                .domainName("domain.com")
                                                .port(666)
                                                .build())
                                        .build(),
                                RosterEntry.newBuilder()
                                        .nodeId(2)
                                        .weight(2)
                                        .gossipCaCertificate(Bytes.wrap("test"))
                                        .tssEncryptionKey(Bytes.wrap("test"))
                                        .gossipEndpoint(ServiceEndpoint.newBuilder()
                                                .domainName("domain.com")
                                                .ipAddressV4(Bytes.wrap("test"))
                                                .port(666)
                                                .build())
                                        .build(),
                                RosterEntry.newBuilder()
                                        .nodeId(3)
                                        .weight(3)
                                        .gossipCaCertificate(Bytes.wrap("test"))
                                        .tssEncryptionKey(Bytes.wrap("test"))
                                        .gossipEndpoint(ServiceEndpoint.newBuilder()
                                                .domainName("domain.com")
                                                .port(666)
                                                .build())
                                        .build())
                        .build()));
        assertEquals(
                "ServiceEndpoint must specify either a domainName or an ipAddressV4, but not both. For NodeId 2 found ServiceEndpoint ServiceEndpoint[ipAddressV4=74657374, port=666, domainName=domain.com]",
                ex.getMessage());
    }

    @Test
    void wrongNodeIdOrderTest() {
        final Exception ex = assertThrows(
                InvalidRosterException.class,
                () -> RosterValidator.validate(Roster.newBuilder()
                        .rosterEntries(
                                RosterEntry.newBuilder()
                                        .nodeId(1)
                                        .weight(1)
                                        .gossipCaCertificate(Bytes.wrap("test"))
                                        .tssEncryptionKey(Bytes.wrap("test"))
                                        .gossipEndpoint(ServiceEndpoint.newBuilder()
                                                .domainName("domain.com")
                                                .port(666)
                                                .build())
                                        .build(),
                                RosterEntry.newBuilder()
                                        .nodeId(3)
                                        .weight(2)
                                        .gossipCaCertificate(Bytes.wrap("test"))
                                        .tssEncryptionKey(Bytes.wrap("test"))
                                        .gossipEndpoint(ServiceEndpoint.newBuilder()
                                                .domainName("domain.com")
                                                .port(666)
                                                .build())
                                        .build(),
                                RosterEntry.newBuilder()
                                        .nodeId(2)
                                        .weight(3)
                                        .gossipCaCertificate(Bytes.wrap("test"))
                                        .tssEncryptionKey(Bytes.wrap("test"))
                                        .gossipEndpoint(ServiceEndpoint.newBuilder()
                                                .domainName("domain.com")
                                                .port(666)
                                                .build())
                                        .build())
                        .build()));
        assertEquals("RosterEntries sort order is invalid. Found node id: 2 following 3", ex.getMessage());
    }

    @Test
    void invalidIPv4Test() {
        final Exception ex = assertThrows(
                InvalidRosterException.class,
                () -> RosterValidator.validate(Roster.newBuilder()
                        .rosterEntries(
                                RosterEntry.newBuilder()
                                        .nodeId(1)
                                        .weight(1)
                                        .gossipCaCertificate(Bytes.wrap("test"))
                                        .tssEncryptionKey(Bytes.wrap("test"))
                                        .gossipEndpoint(ServiceEndpoint.newBuilder()
                                                .domainName("domain.com")
                                                .port(666)
                                                .build())
                                        .build(),
                                RosterEntry.newBuilder()
                                        .nodeId(2)
                                        .weight(2)
                                        .gossipCaCertificate(Bytes.wrap("test"))
                                        .tssEncryptionKey(Bytes.wrap("test"))
                                        .gossipEndpoint(ServiceEndpoint.newBuilder()
                                                .domainName("domain.com")
                                                .port(666)
                                                .build())
                                        .build(),
                                RosterEntry.newBuilder()
                                        .nodeId(3)
                                        .weight(3)
                                        .gossipCaCertificate(Bytes.wrap("test"))
                                        .tssEncryptionKey(Bytes.wrap("test"))
                                        .gossipEndpoint(ServiceEndpoint.newBuilder()
                                                .ipAddressV4(Bytes.wrap("test too long"))
                                                .port(666)
                                                .build())
                                        .build())
                        .build()));
        assertEquals(
                "ServiceEndpoint ipAddressV4 must have a length of 4 bytes, found 13 bytes for nodeId 3",
                ex.getMessage());
    }

    static Roster buildValidRoster() {
        return Roster.newBuilder()
                .rosterEntries(
                        RosterEntry.newBuilder()
                                .nodeId(1)
                                .weight(1)
                                .gossipCaCertificate(Bytes.wrap("test"))
                                .tssEncryptionKey(Bytes.wrap("test"))
                                .gossipEndpoint(ServiceEndpoint.newBuilder()
                                        .domainName("domain.com")
                                        .port(666)
                                        .build())
                                .build(),
                        RosterEntry.newBuilder()
                                .nodeId(2)
                                .weight(2)
                                .gossipCaCertificate(Bytes.wrap("test"))
                                .tssEncryptionKey(Bytes.wrap("test"))
                                .gossipEndpoint(ServiceEndpoint.newBuilder()
                                        .domainName("domain.com")
                                        .port(666)
                                        .build())
                                .build(),
                        RosterEntry.newBuilder()
                                .nodeId(3)
                                .weight(3)
                                .gossipCaCertificate(Bytes.wrap("test"))
                                .tssEncryptionKey(Bytes.wrap("test"))
                                .gossipEndpoint(ServiceEndpoint.newBuilder()
                                        .domainName("domain.com")
                                        .port(666)
                                        .build())
                                .build())
                .build();
    }

    @Test
    void validTest() {
        RosterValidator.validate(buildValidRoster());
    }
}
