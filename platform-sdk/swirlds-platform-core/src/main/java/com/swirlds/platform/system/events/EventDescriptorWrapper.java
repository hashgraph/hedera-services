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
import static com.swirlds.common.io.streams.SerializableStreamConstants.NULL_VERSION;

import com.hedera.hapi.platform.event.EventDescriptor;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.io.exceptions.InvalidVersionException;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.common.platform.NodeId;
import com.swirlds.platform.event.AncientMode;
import com.swirlds.platform.system.address.AddressBook;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * A wrapper class for {@link EventDescriptor} that includes the hash of the event descriptor.
 */
public record EventDescriptorWrapper(
        @NonNull EventDescriptor eventDescriptor, @NonNull Hash hash, @NonNull NodeId creator) {
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

        public static boolean valid(final int version) {
            return version >= SELF_SERIALIZABLE_NODE_ID && version <= BIRTH_ROUND;
        }
    }

    public EventDescriptorWrapper(@NonNull EventDescriptor eventDescriptor) {
        this(eventDescriptor, new Hash(eventDescriptor.hash()), NodeId.of(eventDescriptor.creatorNodeId()));
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

    /**
     * Get the version of the class.
     * @return the version of the class
     */
    public int getClassVersion() {
        return ClassVersion.BIRTH_ROUND;
    }

    /**
     * Deserialize an {@link EventDescriptorWrapper} from a {@link SerializableDataInputStream}.
     *
     * @param out the {@link SerializableDataInputStream} to write to
     * @param obj the {@link EventDescriptorWrapper} to serialize may be null
     *
     * @throws IOException if an IO error occurs
     */
    public static void serialize(
            @NonNull final SerializableDataOutputStream out, @Nullable final EventDescriptorWrapper obj)
            throws IOException {
        if (obj == null) {
            out.writeInt(NULL_VERSION);
        } else {
            out.writeInt(ClassVersion.BIRTH_ROUND);
            serializeObject(out, obj);
        }
    }

    private static void serializeObject(
            @NonNull final SerializableDataOutputStream out, @NonNull final EventDescriptorWrapper obj)
            throws IOException {
        out.writeSerializable(obj.hash(), false);
        out.writeSerializable(obj.creator(), false);
        out.writeLong(obj.eventDescriptor().generation());
        out.writeLong(obj.eventDescriptor().birthRound());
    }

    /**
     * Serialize a list of {@link EventDescriptorWrapper} to a {@link SerializableDataOutputStream}.
     *
     * @param out the {@link SerializableDataOutputStream} to write to
     * @param list the list of {@link EventDescriptorWrapper} to serialize may be null
     *
     * @throws IOException if an IO error occurs
     */
    public static void serializeList(
            @NonNull final SerializableDataOutputStream out, @Nullable final List<EventDescriptorWrapper> list)
            throws IOException {
        if (list == null) {
            out.writeInt(NULL_LIST_ARRAY_LENGTH);
        } else {
            final Iterator<EventDescriptorWrapper> iterator = list.iterator();
            int size = list.size();
            out.writeInt(size);
            if (size != 0) {
                out.writeBoolean(
                        true); // if the class ID and version is written only once, we need to write it when we come
                // across
                // the first non-null member, this variable will keep track of whether its written or not
                boolean classIdVersionWritten = false;
                while (iterator.hasNext()) {
                    final EventDescriptorWrapper serializable = iterator.next();
                    if (serializable == null) {
                        out.writeBoolean(true);
                        continue;
                    }
                    out.writeBoolean(false);
                    if (!classIdVersionWritten) {
                        // this is the first non-null member, so we write the ID and version

                        out.writeInt(serializable.getVersion());
                        classIdVersionWritten = true;
                    }
                    serializeObject(out, serializable);
                }
            }
        }
    }

    /**
     * Deserialize an {@link EventDescriptorWrapper} from a {@link SerializableDataInputStream}.
     *
     * @param in the {@link SerializableDataInputStream} to read from
     * @return the deserialized {@link EventDescriptorWrapper} may be null
     *
     * @throws IOException if an IO error occurs
     */
    @Nullable
    public static EventDescriptorWrapper deserialize(@NonNull final SerializableDataInputStream in) throws IOException {
        final int version = in.readInt();
        if (version == NULL_VERSION) {
            return null;
        }

        return deserializeObject(in, version);
    }

    @NonNull
    private static EventDescriptorWrapper deserializeObject(@NonNull final SerializableDataInputStream in, int version)
            throws IOException {
        if (!ClassVersion.valid(version)) {
            throw new InvalidVersionException(
                    ClassVersion.SELF_SERIALIZABLE_NODE_ID, ClassVersion.BIRTH_ROUND, version);
        }
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

        return new EventDescriptorWrapper(new EventDescriptor(hash.getBytes(), creator.id(), birthRound, generation));
    }

    /**
     * Deserialize a list of {@link EventDescriptorWrapper} from a {@link SerializableDataInputStream}.
     *
     * @param in the {@link SerializableDataInputStream} to read from
     * @return the deserialized list of {@link EventDescriptorWrapper} may be null
     *
     * @throws IOException if an IO error occurs
     */
    @Nullable
    public static List<EventDescriptorWrapper> deserializeList(@NonNull final SerializableDataInputStream in)
            throws IOException {
        final int length = in.readInt();
        if (length == NULL_LIST_ARRAY_LENGTH) {
            return null;
        } else {
            final List<EventDescriptorWrapper> list = new ArrayList<>(length);
            if (length > AddressBook.MAX_ADDRESSES) {
                throw new IOException(String.format(
                        "The input stream provided a length of %d for the list/array "
                                + "which exceeds the maxLength of %d",
                        length, AddressBook.MAX_ADDRESSES));
            }
            if (length == 0) {
                return list;
            } else {
                final boolean allSameClass = in.readBoolean();
                int version = -1;
                for (int i = 0; i < length; i++) {

                    if (allSameClass) {
                        final boolean isNull = in.readBoolean();
                        if (isNull) {
                            list.add(null);
                        } else {
                            if (version == -1) {
                                // this is the first non-null member, so we read the ID and version
                                version = in.readInt();
                            }
                            list.add(deserializeObject(in, version));
                        }
                    }
                }
            }
            return list;
        }
    }
}
