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

package com.swirlds.platform.builder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hedera.hapi.node.state.roster.Roster;
import com.swirlds.common.platform.NodeId;
import com.swirlds.platform.system.BasicSoftwareVersion;
import com.swirlds.platform.system.address.Address;
import com.swirlds.platform.system.address.AddressBook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PlatformBuilderTest {

    private final AddressBook addressBook = new AddressBook();

    private PlatformBuilder platformBuilder;

    @BeforeEach
    void setUp() {
        platformBuilder = PlatformBuilder.create(
                "name",
                "swirldName",
                new BasicSoftwareVersion(1),
                () -> null,
                (inputStream, path) -> null,
                new NodeId(0));
    }

    @Test
    void testCreateRosterFromNonEmptyAddressBook() {
        final Address address1 = new Address(new NodeId(1), "", "", 10, null, 77, null, 88, null, null, "");
        final Address address2 = new Address(new NodeId(2), "", "", 10, null, 77, null, 88, null, null, "");
        addressBook.add(address1);
        addressBook.add(address2);
        platformBuilder.withBootstrapAddressBook(addressBook);
        final Roster roster = platformBuilder.createRoster();

        assertNotNull(roster);
        assertEquals(2, roster.rosters().size());
        assertEquals(1L, roster.rosters().getFirst().nodeId());
        assertEquals(2L, roster.rosters().getLast().nodeId());
    }

    @Test
    void testCreateRosterFromNullAddressBook() {
        assertThrows(
                IllegalStateException.class,
                platformBuilder::createRoster,
                "Illegal attempt to create a Roster from a null AddressBook");
    }

    @Test
    void testCreateRosterFromEmptyAddressBook() {
        platformBuilder.withBootstrapAddressBook(addressBook);
        final Roster roster = platformBuilder.createRoster();

        assertNotNull(roster);
        assertTrue(roster.rosters().isEmpty());
    }
}
