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
import com.swirlds.platform.crypto.KeysAndCerts;
import com.swirlds.platform.roster.Roster;
import com.swirlds.platform.roster.RosterEntry;
import com.swirlds.platform.system.address.Address;
import com.swirlds.platform.system.address.AddressBook;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

public class AddressBookRosterTests {

    @DisplayName("Serialize and deserialize AddressBook derived Roster")
    @ParameterizedTest
    @MethodSource({"com.swirlds.platform.crypto.CryptoArgsProvider#basicTestArgs"})
    void serializeDeserializeTest(
            @NonNull final AddressBook addressBook, @NonNull final Map<NodeId, KeysAndCerts> keysAndCerts)
            throws IOException, ConstructableRegistryException {
        ConstructableRegistry.getInstance().registerConstructables("com.swirlds");
        final Roster roster = new AddressBookRoster(addressBook, keysAndCerts);

        final ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
        final SerializableDataOutputStream out = new SerializableDataOutputStream(byteOut);

        out.writeSerializable(roster, true);

        final SerializableDataInputStream in =
                new SerializableDataInputStream(new ByteArrayInputStream(byteOut.toByteArray()));

        Roster roster2 = in.readSerializable();
        assertEquals(roster, roster2);
    }

    @DisplayName("Roster derived from AddressBook")
    @ParameterizedTest
    @MethodSource({"com.swirlds.platform.crypto.CryptoArgsProvider#basicTestArgs"})
    void addressBookRosterTest(
            @NonNull final AddressBook addressBook, @NonNull final Map<NodeId, KeysAndCerts> keysAndCerts) {
        final Roster roster = new AddressBookRoster(addressBook, keysAndCerts);
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
}
