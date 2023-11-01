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

package com.swirlds.common.system.events;

import static com.swirlds.common.utility.CommonUtils.hex;

import com.swirlds.common.crypto.Hash;
import com.swirlds.common.io.SelfSerializable;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.common.system.NodeId;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.util.Objects;

/**
 * A stripped down description an event. Stores hash, generation, and creator ID.
 */
public class EventDescriptor implements SelfSerializable {

    public static final long CLASS_ID = 0x825e17f25c6e2566L;

    private static final class ClassVersion {
        public static final int ORIGINAL = 1;
        /**
         * The creator field is serialized as a self serializable node id.
         * @since 0.40.0
         */
        public static final int SELF_SERIALIZABLE_NODE_ID = 2;
    }

    private Hash hash;
    private NodeId creator;
    private long generation;

    /**
     * Zero arg constructor, required for deserialization. Do not use manually.
     */
    public EventDescriptor() {}

    /**
     * Create a new event descriptor.
     *
     * @param hash       the hash of the event
     * @param creator    the creator of the event
     * @param generation the age of an event, smaller is older
     */
    public EventDescriptor(@NonNull final Hash hash, @NonNull final NodeId creator, final long generation) {
        this.hash = Objects.requireNonNull(hash, "hash must not be null");
        this.creator = Objects.requireNonNull(creator, "creator must not be null");
        this.generation = generation;
    }

    /**
     * Get the hash of the event.
     *
     * @return the event's hash
     */
    @NonNull
    public Hash getHash() {
        if (hash == null) {
            throw new IllegalStateException("EventDescriptor improperly initialized: the hash is null");
        }
        return hash;
    }

    /**
     * Get the node ID of the event's creator.
     *
     * @return a node ID
     */
    @NonNull
    public NodeId getCreator() {
        if (hash == null) {
            throw new IllegalStateException("EventDescriptor improperly initialized: the hash is null");
        }
        return creator;
    }

    /**
     * Get the generation of the event.
     *
     * @return the generation of the event
     */
    public long getGeneration() {
        return generation;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getClassId() {
        return CLASS_ID;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getVersion() {
        return ClassVersion.SELF_SERIALIZABLE_NODE_ID;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void serialize(@NonNull final SerializableDataOutputStream out) throws IOException {
        out.writeSerializable(hash, false);
        out.writeSerializable(creator, false);
        out.writeLong(generation);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void deserialize(@NonNull final SerializableDataInputStream in, final int version) throws IOException {
        hash = in.readSerializable(false, Hash::new);
        if (hash == null) {
            throw new IOException("hash cannot be null");
        }
        if (version < ClassVersion.SELF_SERIALIZABLE_NODE_ID) {
            creator = new NodeId(in.readLong());
        } else {
            creator = in.readSerializable(false, NodeId::new);
            if (creator == null) {
                throw new IOException("creator cannot be null");
            }
        }
        generation = in.readLong();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        final EventDescriptor that = (EventDescriptor) o;

        return Objects.equals(creator, that.creator) && generation == that.generation && hash.equals(that.hash);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        if (hash == null) {
            throw new IllegalStateException("EventDescriptor improperly initialized: the hash is null");
        }
        return hash.hashCode();
    }

    @Override
    public String toString() {
        return "(creator: " + creator + ", generation: "
                + generation + ", hash: "
                + hex(hash.getValue()).substring(0, 12) + ")";
    }
}
