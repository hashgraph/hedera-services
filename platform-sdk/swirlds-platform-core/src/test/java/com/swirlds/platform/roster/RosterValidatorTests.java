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

import static org.junit.jupiter.api.Assertions.assertThrows;

import com.hedera.hapi.node.base.ServiceEndpoint;
import com.hedera.hapi.node.state.roster.Roster;
import com.hedera.hapi.node.state.roster.RosterEntry;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import org.junit.jupiter.api.Test;

public class RosterValidatorTests {
    @Test
    void nullTest() {
        assertThrows(InvalidRosterException.class, () -> RosterValidator.validate(null));
    }

    @Test
    void emptyTest() {
        assertThrows(InvalidRosterException.class, () -> RosterValidator.validate(Roster.DEFAULT));
    }

    @Test
    void zeroWeightTest() {
        assertThrows(
                InvalidRosterException.class,
                () -> RosterValidator.validate(Roster.newBuilder()
                        .rosters(
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
    }

    @Test
    void duplicateNodeIdTest() {
        assertThrows(
                InvalidRosterException.class,
                () -> RosterValidator.validate(Roster.newBuilder()
                        .rosters(
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
    }

    @Test
    void invalidGossipCertTest() {
        assertThrows(
                InvalidRosterException.class,
                () -> RosterValidator.validate(Roster.newBuilder()
                        .rosters(
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
    }

    @Test
    void emptyGossipEndpointsTest() {
        assertThrows(
                InvalidRosterException.class,
                () -> RosterValidator.validate(Roster.newBuilder()
                        .rosters(
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
    }

    @Test
    void zeroPortTest() {
        assertThrows(
                InvalidRosterException.class,
                () -> RosterValidator.validate(Roster.newBuilder()
                        .rosters(
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
    }

    @Test
    void domainAndIpTest() {
        assertThrows(
                InvalidRosterException.class,
                () -> RosterValidator.validate(Roster.newBuilder()
                        .rosters(
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
    }

    @Test
    void validTest() {
        RosterValidator.validate(Roster.newBuilder()
                .rosters(
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
                .build());
    }
}
