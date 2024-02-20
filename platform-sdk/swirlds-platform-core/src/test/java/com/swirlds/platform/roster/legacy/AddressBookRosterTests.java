/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.swirlds.platform.roster.legacy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.swirlds.common.constructable.ConstructableRegistry;
import com.swirlds.common.constructable.ConstructableRegistryException;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.common.platform.NodeId;
import com.swirlds.platform.roster.Roster;
import com.swirlds.platform.roster.RosterEntry;
import com.swirlds.platform.system.address.Address;
import com.swirlds.platform.system.address.AddressBook;
import com.swirlds.platform.test.fixtures.addressbook.RandomAddressBookGenerator;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class AddressBookRosterTests {

    @Test
    @DisplayName("Serialize and deserialize AddressBook derived Roster")
    void serializeDeserializeTest() throws IOException, ConstructableRegistryException {
        final AddressBook addressBook =
                new RandomAddressBookGenerator().setSize(100).build();
        ConstructableRegistry.getInstance().registerConstructables("com.swirlds");
        final AddressBookRoster roster = new AddressBookRoster(addressBook);

        final ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
        final SerializableDataOutputStream out = new SerializableDataOutputStream(byteOut);

        out.writeSerializable(roster, true);

        final SerializableDataInputStream in =
                new SerializableDataInputStream(new ByteArrayInputStream(byteOut.toByteArray()));

        final AddressBookRoster roster2 = in.readSerializable();
        assertEquals(roster, roster2);
        assertEquals(addressBook, roster2.getAddressBook());
    }

    @Test
    @DisplayName("Roster derived from AddressBook")
    void addressBookRosterTest() {
        final AddressBook addressBook =
                new RandomAddressBookGenerator().setSize(100).build();
        final Roster roster = new AddressBookRoster(addressBook);
        final Iterator<RosterEntry> entries = roster.iterator();
        for (int i = 0; i < addressBook.getSize(); i++) {
            final NodeId nodeId = addressBook.getNodeId(i);
            final Address address = addressBook.getAddress(nodeId);
            final RosterEntry rosterEntry = entries.next();
            assertEquals(address.getHostnameExternal(), rosterEntry.getHostname());
            assertEquals(address.getPortExternal(), rosterEntry.getPort());
            assertEquals(address.getNodeId(), rosterEntry.getNodeId());
            assertEquals(address.getWeight(), rosterEntry.getWeight());
            assertEquals(address.getSigPublicKey(), rosterEntry.getSigningPublicKey());
        }
        assertFalse(entries.hasNext());
    }

    @Test
    @DisplayName("Serialize and deserialize AddressBook derived Roster")
    void serializeDeserializeEntryTest() throws IOException, ConstructableRegistryException {
        final AddressBook addressBook =
                new RandomAddressBookGenerator().setSize(100).build();
        ConstructableRegistry.getInstance().registerConstructables("com.swirlds");
        final Roster roster = new AddressBookRoster(addressBook);

        for (final RosterEntry entry : roster) {

            final ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
            final SerializableDataOutputStream out = new SerializableDataOutputStream(byteOut);

            out.writeSerializable(entry, true);

            final SerializableDataInputStream in =
                    new SerializableDataInputStream(new ByteArrayInputStream(byteOut.toByteArray()));

            RosterEntry entry2 = in.readSerializable();
            assertEquals(entry, entry2);
            assertEquals(addressBook.getAddress(entry.getNodeId()), ((AddressRosterEntry) entry2).getAddress());
        }
    }
}
