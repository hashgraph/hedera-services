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

package com.swirlds.platform.system.events;

import static com.swirlds.common.io.streams.SerializableStreamConstants.NULL_LIST_ARRAY_LENGTH;

import com.hedera.hapi.platform.event.EventDescriptor;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.common.platform.NodeId;
import com.swirlds.platform.event.AncientMode;
import com.swirlds.platform.system.address.AddressBook;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * A wrapper class for {@link EventDescriptor} that includes the hash of the event descriptor.
 */
public record EventDescriptorWrapper(@NonNull EventDescriptor eventDescriptor, @NonNull Hash hash, @NonNull NodeId creator) {
    public static final long CLASS_ID = 0x825e17f25c6e2566L;

    private static final class ClassVersion {
        public static final int ORIGINAL = 1;
        /**
         * The creator field is serialized as a self serializable node id.
         *
         * @since 0.40.0
         */
        public static final int SELF_SERIALIZABLE_NODE_ID = 2;
        /**
         * The birthRound field is added.
         *
         * @since 0.46.0
         */
        public static final int BIRTH_ROUND = 3;
    }


    public EventDescriptorWrapper(@NonNull EventDescriptor eventDescriptor) {
        this(eventDescriptor, new Hash(eventDescriptor.hash().toByteArray()), new NodeId(eventDescriptor.creatorNodeId()));
    }

    /**
     * Get the value used to determine if this event is ancient or not. Will be the event's generation prior to
     * migration, and the event's birth round after migration.
     *
     * @return the value used to determine if this event is ancient or not
     */
    public long getAncientIndicator(@NonNull final AncientMode ancientMode) {
        return switch (ancientMode) {
            case GENERATION_THRESHOLD -> eventDescriptor.generation();
            case BIRTH_ROUND_THRESHOLD -> eventDescriptor.birthRound();
        };
    }

    public void serialize(@NonNull final SerializableDataOutputStream out) throws IOException {
        out.writeInt(ClassVersion.BIRTH_ROUND);
        serializeOne(out);
    }

    private void serializeOne(@NonNull final SerializableDataOutputStream out) throws IOException {
        out.writeSerializable(hash, false);
        out.writeSerializable(creator, false);
        out.writeLong(eventDescriptor.generation());
        out.writeLong(eventDescriptor.birthRound());
    }

    public static void serializeList(@Nullable final List<EventDescriptorWrapper> descriptorWrapperList, @NonNull final SerializableDataOutputStream out) throws IOException {
        if (descriptorWrapperList == null) {
            out.writeInt(NULL_LIST_ARRAY_LENGTH);
            return;
        }
        out.writeInt(descriptorWrapperList.size());
        out.writeBoolean(true); // allSameClass

        boolean classIdVersionWritten = false;
        for (final EventDescriptorWrapper descriptorWrapper : descriptorWrapperList) {
            if (descriptorWrapper == null) {
                out.writeBoolean(true);
                continue;
            }
            out.writeBoolean(false);
            if (!classIdVersionWritten) {
                // this is the first non-null member, so we write the ID and version
                out.writeInt(ClassVersion.BIRTH_ROUND);
                classIdVersionWritten = true;
            }
            descriptorWrapper.serializeOne(out);
        }
    }

    @NonNull
    public static EventDescriptorWrapper deserialize(@NonNull final SerializableDataInputStream in) throws IOException {
        final int version = in.readInt();
        return deserializeOne(in, version);
    }

    @NonNull
    private static EventDescriptorWrapper deserializeOne(@NonNull final SerializableDataInputStream in, final int version) throws IOException {
        final Hash hash = in.readSerializable(false, Hash::new);
        if (hash == null) {
            throw new IOException("hash cannot be null");
        }

        final NodeId creator = in.readSerializable(false, NodeId::new);
        if (creator == null) {
            throw new IOException("creator cannot be null");
        }
        final long generation = in.readLong();
        final long birthRound;
        if (version < ClassVersion.BIRTH_ROUND) {
            birthRound = EventConstants.BIRTH_ROUND_UNDEFINED;
        } else {
            birthRound = in.readLong();
        }

        return new EventDescriptorWrapper(new EventDescriptor(hash.getBytes(), creator.id(), generation, birthRound));
    }

    @Nullable
    public static List<EventDescriptorWrapper> deserializeList(@NonNull final SerializableDataInputStream in) throws IOException {
        final int length = in.readInt();
        if (length == NULL_LIST_ARRAY_LENGTH) {
            return null;
        }
        if (length > AddressBook.MAX_ADDRESSES) {
            throw new IOException(String.format(
                    "The input stream provided a length of %d for the list/array "
                            + "which exceeds the maxLength of %d",
                    length, AddressBook.MAX_ADDRESSES));
        }
        in.readBoolean(); // allSameClass
        final List<EventDescriptorWrapper> list = new ArrayList<>(length);
        if (length > 0) {
            int version = -1;
            for (int i = 0; i < length; i++) {
                final boolean isNull = in.readBoolean();
                if (isNull) {
                    list.add(null);
                } else {
                    if (version == -1) {
                        version = in.readInt();
                    }
                    list.add(deserializeOne(in, version));
                }
            }
        }
        return list;
    }

}
