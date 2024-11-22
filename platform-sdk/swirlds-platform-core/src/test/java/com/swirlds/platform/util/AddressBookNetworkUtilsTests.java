/*
 * Copyright (C) 2018-2024 Hedera Hashgraph, LLC
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

import static com.swirlds.platform.roster.RosterRetriever.buildRoster;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.hedera.hapi.node.base.ServiceEndpoint;
import com.hedera.hapi.node.state.roster.Roster;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.common.platform.NodeId;
import com.swirlds.common.test.fixtures.Randotron;
import com.swirlds.platform.network.Network;
import com.swirlds.platform.roster.InvalidAddressBookException;
import com.swirlds.platform.state.address.AddressBookNetworkUtils;
import com.swirlds.platform.system.address.Address;
import com.swirlds.platform.system.address.AddressBook;
import com.swirlds.platform.system.address.AddressBookUtils;
import com.swirlds.platform.test.fixtures.addressbook.RandomAddressBookBuilder;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

/**
 * Tests for {@link AddressBookNetworkUtils}
 */
class AddressBookNetworkUtilsTests {

    @Test
    @DisplayName("Determine If Local Node")
    void determineLocalNodeAddress() throws UnknownHostException {
        final Randotron randotron = Randotron.create();
        final AddressBook addressBook =
                RandomAddressBookBuilder.create(randotron).withSize(2).build();
        final Address address = addressBook.getAddress(addressBook.getNodeId(0));

        final Address loopBackAddress = address.copySetHostnameInternal(
                Inet4Address.getLoopbackAddress().getHostAddress());
        assertTrue(AddressBookNetworkUtils.isLocal(loopBackAddress));

        final Address localIpAddress = address.copySetHostnameInternal(
                Inet4Address.getByName(Network.getInternalIPAddress()).getHostAddress());
        assertTrue(AddressBookNetworkUtils.isLocal(localIpAddress));

        final InetAddress inetAddress = Inet4Address.getByName(Network.getInternalIPAddress());
        assertTrue(Network.isOwn(inetAddress));

        final Address notLocalAddress =
                address.copySetHostnameInternal(Inet4Address.getByAddress(new byte[] {(byte) 192, (byte) 168, 0, 1})
                        .getHostAddress());
        assertFalse(AddressBookNetworkUtils.isLocal(notLocalAddress));
    }

    @Test
    @DisplayName("Error On Invalid Local Address")
    @DisabledOnOs({OS.WINDOWS, OS.MAC})
    void ErrorOnInvalidLocalAddress() {
        final Randotron randotron = Randotron.create();
        final AddressBook addressBook =
                RandomAddressBookBuilder.create(randotron).withSize(2).build();
        final Address address = addressBook.getAddress(addressBook.getNodeId(0));

        final Address badLocalAddress = address.copySetHostnameInternal("500.8.8");
        assertThrows(IllegalStateException.class, () -> AddressBookNetworkUtils.isLocal(badLocalAddress));
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
    void testToRosterEntryWithCertificateEncodingException() throws CertificateEncodingException {
        final X509Certificate certificate = mock(X509Certificate.class);
        final Address address = mock(Address.class);
        when(address.getSigCert()).thenReturn(certificate);
        when(address.getNodeId()).thenReturn(NodeId.FIRST_NODE_ID);
        when(certificate.getEncoded()).thenThrow(new CertificateEncodingException());

        final AddressBook addressBook = new AddressBook(List.of(address));
        assertThrows(
                InvalidAddressBookException.class,
                () -> buildRoster(addressBook),
                "Invalid certificates are not allowed in Rosters.");
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
