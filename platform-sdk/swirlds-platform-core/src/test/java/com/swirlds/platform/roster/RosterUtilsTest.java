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

import static com.swirlds.platform.roster.RosterRetriever.buildRoster;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.hedera.hapi.node.base.ServiceEndpoint;
import com.hedera.hapi.node.state.roster.Roster;
import com.hedera.hapi.node.state.roster.RosterEntry;
import com.hedera.hapi.node.state.roster.RoundRosterPair;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.platform.NodeId;
import com.swirlds.platform.state.service.ReadableRosterStore;
import com.swirlds.platform.system.address.Address;
import com.swirlds.platform.system.address.AddressBook;
import com.swirlds.platform.system.address.AddressBookUtils;
import com.swirlds.platform.test.fixtures.crypto.PreGeneratedX509Certs;
import java.security.cert.CertificateEncodingException;
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

    @Test
    void testCreateRosterHistory() {
        // Mock the ReadableRosterStore
        ReadableRosterStore rosterStore = mock(ReadableRosterStore.class);

        // Create mock data
        RoundRosterPair pair1 = new RoundRosterPair(1, Bytes.wrap(new byte[] {1}));
        RoundRosterPair pair2 = new RoundRosterPair(2, Bytes.wrap(new byte[] {2}));
        List<RoundRosterPair> roundRosterPairs = List.of(pair2, pair1);

        Roster roster1 = mock(Roster.class);
        Roster roster2 = mock(Roster.class);

        // Define behavior for the mock
        when(rosterStore.getRosterHistory()).thenReturn(roundRosterPairs);
        when(rosterStore.get(Bytes.wrap(new byte[] {1}))).thenReturn(roster1);
        when(rosterStore.get(Bytes.wrap(new byte[] {2}))).thenReturn(roster2);

        // Call the method under test
        RosterHistory rosterHistory = RosterUtils.createRosterHistory(rosterStore);

        // Verify the results
        assertEquals(roster2, rosterHistory.getCurrentRoster());
        assertEquals(roster1, rosterHistory.getPreviousRoster());
        assertEquals(roster1, rosterHistory.getRosterForRound(1));
        assertEquals(roster2, rosterHistory.getRosterForRound(2));
    }

    @Test
    void testCreateRosterHistoryNoActiveRosters() {
        // Mock the ReadableRosterStore
        ReadableRosterStore rosterStore = mock(ReadableRosterStore.class);

        // Define behavior for the mock
        when(rosterStore.getRosterHistory()).thenReturn(null);

        assertThrows(NullPointerException.class, () -> RosterUtils.createRosterHistory(rosterStore));
    }

    @Test
    void testFetchingCertificates() throws CertificateEncodingException {
        // Positive Case
        assertEquals(
                PreGeneratedX509Certs.getSigCert(0).getCertificate(),
                RosterUtils.fetchGossipCaCertificate(RosterEntry.newBuilder()
                        .gossipCaCertificate(Bytes.wrap(PreGeneratedX509Certs.getSigCert(0)
                                .getCertificate()
                                .getEncoded()))
                        .build()));
        // Negative Cases
        assertNull(RosterUtils.fetchGossipCaCertificate(
                RosterEntry.newBuilder().gossipCaCertificate(null).build()));
        assertNull(RosterUtils.fetchGossipCaCertificate(
                RosterEntry.newBuilder().gossipCaCertificate(Bytes.EMPTY).build()));
        assertNull(RosterUtils.fetchGossipCaCertificate(RosterEntry.newBuilder()
                .gossipCaCertificate(
                        Bytes.wrap(PreGeneratedX509Certs.createBadCertificate().getEncoded()))
                .build()));
    }

    @Test
    void testCreateRosterFromNonEmptyAddressBook() {
        final Address address1 = new Address(NodeId.of(1), "", "", 10, null, 77, null, 88, null, null, "");
        final Address address2 = new Address(NodeId.of(2), "", "", 10, null, 77, null, 88, null, null, "");
        final AddressBook addressBook = new AddressBook();
        addressBook.add(address1);
        addressBook.add(address2);
        final Roster roster = buildRoster(addressBook);

        assertNotNull(roster);
        assertEquals(2, roster.rosterEntries().size());
        assertEquals(1L, roster.rosterEntries().getFirst().nodeId());
        assertEquals(2L, roster.rosterEntries().getLast().nodeId());
    }

    @Test
    void testCreateRosterFromNullAddressBook() {
        assertNull(buildRoster(null), "A null address book should produce a null roster.");
    }

    @Test
    void testCreateRosterFromEmptyAddressBook() {
        final AddressBook addressBook = new AddressBook();
        final Roster roster = buildRoster(addressBook);

        assertNotNull(roster);
        assertTrue(roster.rosterEntries().isEmpty());
    }

    @Test
    void testToRosterEntryWithExternalHostname() {
        final Address address = new Address().copySetHostnameExternal("hostnameExternal");
        final AddressBook addressBook = new AddressBook(List.of(address));
        final Roster roster = buildRoster(addressBook);

        assertEquals(1, roster.rosterEntries().size());
        assertEquals(
                "hostnameExternal",
                roster.rosterEntries().getFirst().gossipEndpoint().getFirst().domainName());
    }

    @Test
    void testToRosterEntryWithInternalHostname() {
        final Address address = new Address().copySetHostnameInternal("hostnameInternal");
        final AddressBook addressBook = new AddressBook(List.of(address));
        final Roster roster = buildRoster(addressBook);

        assertEquals(1, roster.rosterEntries().size());
        assertEquals(
                "hostnameInternal",
                roster.rosterEntries().getFirst().gossipEndpoint().getFirst().domainName());
    }

    @Test
    void testEndpointForValidIpV4Address() {
        final ServiceEndpoint endpoint = AddressBookUtils.endpointFor("192.168.1.1", 2);
        assertEquals(endpoint.ipAddressV4(), Bytes.wrap(new byte[] {(byte) 192, (byte) 168, 1, 1}));
    }

    @Test
    void testEndpointForInvalidIpAddressConvertsToDomainName() {
        final String invalidIpAddress = "192.168.is.bad";
        assertEquals(
                Bytes.EMPTY, AddressBookUtils.endpointFor(invalidIpAddress, 2).ipAddressV4());
        assertEquals(AddressBookUtils.endpointFor(invalidIpAddress, 2).domainName(), invalidIpAddress);
    }
}
