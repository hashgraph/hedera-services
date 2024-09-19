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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.hedera.hapi.node.state.roster.Roster;
import com.hedera.hapi.node.state.roster.RosterEntry;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.platform.NodeId;
import com.swirlds.common.test.fixtures.Randotron;
import com.swirlds.platform.builder.PlatformBuilder;
import com.swirlds.platform.state.signed.ReservedSignedState;
import com.swirlds.platform.system.BasicSoftwareVersion;
import com.swirlds.platform.system.address.Address;
import com.swirlds.platform.system.address.AddressBook;
import com.swirlds.platform.test.fixtures.addressbook.RandomAddressBookBuilder;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.List;
import org.junit.jupiter.api.Test;

class RosterUtilsTest {

    @Test
    void testHash() {
        final Hash hash = RosterUtils.hashOf(Roster.DEFAULT);
        assertEquals(
                "38b060a751ac96384cd9327eb1b1e36a21fdb71114be07434c0cc7bf63f6e1da274edebfe76f65fbd51ad2f14898b95b",
                hash.toString());

        final Hash anotherHash = RosterUtils.hashOf(
                Roster.DEFAULT.copyBuilder().rosterEntries(RosterEntry.DEFAULT).build());
        assertEquals(
                "5d693ce2c5d445194faee6054b4d8fe4a4adc1225cf0afc2ecd7866ea895a0093ea3037951b75ab7340b75699aa1db1d",
                anotherHash.toString());

        final Hash validRosterHash = RosterUtils.hashOf(RosterValidatorTests.buildValidRoster());
        assertEquals(
                "1b8414aa690d96ce79e972abfc58c7ca04052996f89c5e6789b25b9051ee85fccb7c8ed3fc6ebacef177adfdcbbb5709",
                validRosterHash.toString());
    }

    @Test
    void testEndpointForValidIpV4Address() {
        final PlatformBuilder platformBuilder = PlatformBuilder.create(
                "name",
                "swirldName",
                new BasicSoftwareVersion(1),
                ReservedSignedState.createNullReservation(),
                new NodeId(0));

        // can't use the RandomAddressBookBuilder here because we need to set/test the ip addresses explicitly
        final Address address1 = new Address(new NodeId(1), "", "", 10, "192.168.1.1", 77, null, 88, null, null, "");
        final Address address2 = new Address(new NodeId(2), "", "", 10, "testDomainName", 77, null, 88, null, null, "");
        final AddressBook addressBook = new AddressBook();
        addressBook.add(address1);
        addressBook.add(address2);
        platformBuilder.withAddressBook(addressBook);
        final Roster roster = RosterUtils.createRoster(addressBook);

        assertNotNull(roster);
        assertEquals(2, roster.rosterEntries().size());

        assertEquals(
                roster.rosterEntries().getFirst().gossipEndpoint().getFirst().ipAddressV4(),
                Bytes.wrap(new byte[] {(byte) 192, (byte) 168, 1, 1}));
        assertEquals(
                "", roster.rosterEntries().getFirst().gossipEndpoint().getLast().domainName());

        assertEquals(
                "testDomainName",
                roster.rosterEntries().getLast().gossipEndpoint().getFirst().domainName());
        assertEquals(roster.rosterEntries().getLast().gossipEndpoint().getLast().ipAddressV4(), Bytes.wrap(""));
    }

    @Test
    void testCreateRosterFromNonEmptyAddressBook() {
        final PlatformBuilder platformBuilder = PlatformBuilder.create(
                "name",
                "swirldName",
                new BasicSoftwareVersion(1),
                ReservedSignedState.createNullReservation(),
                new NodeId(0));

        //can't use the RandomAddressBookBuilder here because we need to set/test the nodeIds explicitly
        final Address address1 = new Address(new NodeId(1), "", "", 10, null, 77, null, 88, null, null, "");
        final Address address2 = new Address(new NodeId(2), "", "", 10, null, 77, null, 88, null, null, "");
        final AddressBook addressBook = new AddressBook();
        addressBook.add(address1);
        addressBook.add(address2);
        platformBuilder.withAddressBook(addressBook);
        final Roster roster = RosterUtils.createRoster(addressBook);

        assertNotNull(roster);
        assertEquals(2, roster.rosterEntries().size());
        assertEquals(1L, roster.rosterEntries().getFirst().nodeId());
        assertEquals(2L, roster.rosterEntries().getLast().nodeId());
    }

    @Test
    void testCreateRosterFromNullAddressBook() {
        assertThrows(
                NullPointerException.class,
                () -> RosterUtils.createRoster(null),
                "Illegal attempt to create a Roster from a null AddressBook");
    }

    @Test
    void testCreateRosterFromEmptyAddressBook() {
        final PlatformBuilder platformBuilder = PlatformBuilder.create(
                "name",
                "swirldName",
                new BasicSoftwareVersion(1),
                ReservedSignedState.createNullReservation(),
                new NodeId(0));
        final AddressBook addressBook = new AddressBook();
        platformBuilder.withAddressBook(addressBook);
        final Roster roster = RosterUtils.createRoster(addressBook);

        assertNotNull(roster);
        assertTrue(roster.rosterEntries().isEmpty());
    }

    @Test
    void testToRosterEntryWithExternalHostname() {
        final Address address = new Address().copySetHostnameExternal("hostnameExternal");
        final AddressBook addressBook = new AddressBook(List.of(address));
        final Roster roster = RosterUtils.createRoster(addressBook);

        assertEquals(1, roster.rosterEntries().size());
        assertEquals(
                "hostnameExternal",
                roster.rosterEntries().getFirst().gossipEndpoint().getFirst().domainName());
    }

    @Test
    void testToRosterEntryWithInternalHostname() {
        final Address address = new Address().copySetHostnameInternal("hostnameInternal");
        final AddressBook addressBook = new AddressBook(List.of(address));
        final Roster roster = RosterUtils.createRoster(addressBook);

        assertEquals(1, roster.rosterEntries().size());
        assertEquals(
                "hostnameInternal",
                roster.rosterEntries().getFirst().gossipEndpoint().getFirst().domainName());
    }

    @Test
    void testCreateRoster() {
        final AddressBook addressBook = RandomAddressBookBuilder.create(Randotron.create()).withSize(1).build();
        final Roster roster = RosterUtils.createRoster(addressBook);
        assertNotNull(roster);
        assertNotNull(roster.rosterEntries());
        assertEquals(1, roster.rosterEntries().size());
    }

    @Test
    void testCreateRosterWithNullOrEmptyAddressBook() {
        assertThrows(NullPointerException.class, () -> RosterUtils.createRoster(null));
        assertEquals(0, RosterUtils.createRoster(new AddressBook()).rosterEntries().size());
    }

    @Test
    void testToRosterEntryWithCertificateEncodingExceptionThrows() throws CertificateEncodingException {
        //have to use mocks here to test the exception
        final Address address = mock(Address.class);
        final X509Certificate certificate = mock(X509Certificate.class);
        when(address.getSigCert()).thenReturn(certificate);
        when(certificate.getEncoded()).thenThrow(new CertificateEncodingException());
        final AddressBook addressBook = mock(AddressBook.class);
        when(addressBook.getSize()).thenReturn(1);
        final NodeId nodeId = new NodeId(1);
        when(address.getNodeId()).thenReturn(nodeId);
        when(addressBook.getNodeId(0)).thenReturn(nodeId);
        when(addressBook.getAddress(nodeId)).thenReturn(address);

       assertThrows(
                InvalidAddressBookException.class, () -> RosterUtils.createRoster(addressBook).rosterEntries().getFirst().gossipCaCertificate());
    }
}
