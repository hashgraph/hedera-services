/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.common.platform.NodeId;
import com.swirlds.platform.crypto.KeysAndCerts;
import com.swirlds.platform.roster.Roster;
import com.swirlds.platform.roster.RosterEntry;
import com.swirlds.platform.system.address.Address;
import com.swirlds.platform.system.address.AddressBook;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;

/**
 * A {@link Roster} implementation that uses an {@link AddressBook} as its backing data structure.
 */
public class AddressBookRoster implements Roster {
    private static final long CLASS_ID = 0x7104f97d4e298619L;

    private static final class ClassVersion {
        public static final int ORIGINAL = 1;
    }

    private final Map<NodeId, RosterEntry> entries = new HashMap<>();

    /**
     * Constructs a new {@link AddressBookRoster} from the given {@link AddressBook} and {@link KeysAndCerts} map.
     *
     * @param addressBook     the address book
     * @param keysAndCertsMap the keys and certs map
     */
    public AddressBookRoster(
            @NonNull final AddressBook addressBook, @NonNull final Map<NodeId, KeysAndCerts> keysAndCertsMap) {
        Objects.requireNonNull(addressBook);
        Objects.requireNonNull(keysAndCertsMap);

        for (final Address address : addressBook) {
            entries.put(address.getNodeId(), new AddressRosterEntry(address, keysAndCertsMap.get(address.getNodeId())));
        }
    }

    /**
     * Empty constructor for deserialization.
     */
    public AddressBookRoster() {}

    @Override
    public long getClassId() {
        return CLASS_ID;
    }

    @Override
    public int getVersion() {
        return ClassVersion.ORIGINAL;
    }

    @Override
    public void serialize(@NonNull SerializableDataOutputStream out) throws IOException {
        out.writeInt(entries.size());
        for (final RosterEntry entry : entries.values()) {
            out.writeSerializable(entry, true);
        }
    }

    @Override
    public void deserialize(@NonNull SerializableDataInputStream in, int version) throws IOException {
        final int size = in.readInt();
        for (int i = 0; i < size; i++) {
            final RosterEntry entry = in.readSerializable();
            entries.put(entry.getNodeId(), entry);
        }
    }

    @Override
    @NonNull
    public Collection<NodeId> getNodeIds() {
        return entries.keySet();
    }

    @Override
    @NonNull
    public RosterEntry getEntry(@NonNull final NodeId nodeId) {
        Objects.requireNonNull(nodeId);
        return entries.get(nodeId);
    }

    @Override
    @NonNull
    public Iterator<RosterEntry> iterator() {
        return new Iterator<RosterEntry>() {
            private final Iterator<NodeId> nodeIds =
                    entries.keySet().stream().sorted().iterator();

            @Override
            public boolean hasNext() {
                return nodeIds.hasNext();
            }

            @Override
            public RosterEntry next() {
                return entries.get(nodeIds.next());
            }
        };
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        AddressBookRoster that = (AddressBookRoster) o;
        return Objects.equals(entries, that.entries);
    }

    @Override
    public int hashCode() {
        return Objects.hash(entries);
    }

    @Override
    public String toString() {
        return "AddressBookRoster{" + "entries=" + entries + '}';
    }
}
