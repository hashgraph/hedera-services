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

import com.swirlds.base.utility.ToStringBuilder;
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
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
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
    private List<NodeId> nodeOrder;

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

        nodeOrder = entries.keySet().stream().sorted().toList();
    }

    /**
     * Empty constructor for deserialization.
     */
    public AddressBookRoster() {
        nodeOrder = new ArrayList<>();
    }

    @Override
    public long getClassId() {
        return CLASS_ID;
    }

    @Override
    public int getVersion() {
        return ClassVersion.ORIGINAL;
    }

    @Override
    public void serialize(@NonNull final SerializableDataOutputStream out) throws IOException {
        out.writeInt(entries.size());
        for (final RosterEntry entry : this) {
            out.writeSerializable(entry, true);
        }
    }

    @Override
    public void deserialize(@NonNull final SerializableDataInputStream in, final int version) throws IOException {
        final int size = in.readInt();
        for (int i = 0; i < size; i++) {
            final RosterEntry entry = in.readSerializable();
            entries.put(entry.getNodeId(), entry);
        }
        nodeOrder = entries.keySet().stream().sorted().toList();
    }

    @Override
    @NonNull
    public Collection<NodeId> getNodeIds() {
        return nodeOrder;
    }

    @Override
    @NonNull
    public RosterEntry getEntry(@NonNull final NodeId nodeId) {
        Objects.requireNonNull(nodeId);
        final RosterEntry entry = entries.get(nodeId);
        if (entry == null) {
            throw new NoSuchElementException("No entry found for nodeId " + nodeId);
        }
        return entry;
    }

    @Override
    @NonNull
    public Iterator<RosterEntry> iterator() {
        return new Iterator<>() {
            private int index = 0;

            @Override
            public boolean hasNext() {
                return index < nodeOrder.size();
            }

            @Override
            public RosterEntry next() {
                return entries.get(nodeOrder.get(index++));
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
        final AddressBookRoster that = (AddressBookRoster) o;
        return Objects.equals(entries, that.entries);
    }

    @Override
    public int hashCode() {
        return Objects.hash(entries);
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this).append("entries", entries).toString();
    }
}
