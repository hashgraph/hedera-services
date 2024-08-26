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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hedera.hapi.node.state.roster.Roster;
import com.swirlds.common.platform.NodeId;
import com.swirlds.common.test.fixtures.Randotron;
import com.swirlds.platform.builder.PlatformBuilder;
import com.swirlds.platform.network.Network;
import com.swirlds.platform.state.address.AddressBookNetworkUtils;
import com.swirlds.platform.system.BasicSoftwareVersion;
import com.swirlds.platform.system.address.Address;
import com.swirlds.platform.system.address.AddressBook;
import com.swirlds.platform.system.address.AddressBookUtils;
import com.swirlds.platform.test.fixtures.addressbook.RandomAddressBookBuilder;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
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
        final PlatformBuilder platformBuilder = PlatformBuilder.create(
                "name",
                "swirldName",
                new BasicSoftwareVersion(1),
                () -> null,
                (inputStream, path) -> null,
                new NodeId(0));

        final Address address1 = new Address(new NodeId(1), "", "", 10, null, 77, null, 88, null, null, "");
        final Address address2 = new Address(new NodeId(2), "", "", 10, null, 77, null, 88, null, null, "");
        final AddressBook addressBook = new AddressBook();
        addressBook.add(address1);
        addressBook.add(address2);
        platformBuilder.withBootstrapAddressBook(addressBook);
        final Roster roster = AddressBookUtils.createRoster(addressBook);

        assertNotNull(roster);
        assertEquals(2, roster.rosters().size());
        assertEquals(1L, roster.rosters().getFirst().nodeId());
        assertEquals(2L, roster.rosters().getLast().nodeId());
    }

    @Test
    void testCreateRosterFromNullAddressBook() {
        assertThrows(
                NullPointerException.class,
                () -> AddressBookUtils.createRoster(null),
                "Illegal attempt to create a Roster from a null AddressBook");
    }

    @Test
    void testCreateRosterFromEmptyAddressBook() {
        final PlatformBuilder platformBuilder = PlatformBuilder.create(
                "name",
                "swirldName",
                new BasicSoftwareVersion(1),
                () -> null,
                (inputStream, path) -> null,
                new NodeId(0));
        final AddressBook addressBook = new AddressBook();
        platformBuilder.withBootstrapAddressBook(addressBook);
        final Roster roster = AddressBookUtils.createRoster(addressBook);

        assertNotNull(roster);
        assertTrue(roster.rosters().isEmpty());
    }
}
