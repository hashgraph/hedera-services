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

import static com.swirlds.common.utility.CommonUtils.hex;

import com.swirlds.base.utility.ToStringBuilder;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.io.SelfSerializable;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.common.platform.NodeId;
import com.swirlds.platform.event.AncientMode;
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

    private Hash hash;
    private NodeId creator;
    private long generation;
    private long birthRound;

    /**
     * Zero arg constructor, required for deserialization. Do not use manually.
     */
    public EventDescriptor() {}

    /**
     * Create a new event descriptor.
     *
     * @param hash        the hash of the event
     * @param creator     the creator of the event
     * @param generation  the age of an event, smaller is older
     * @param birthRound  the round when the event was created
     */
    public EventDescriptor(
            @NonNull final Hash hash, @NonNull final NodeId creator, final long generation, final long birthRound) {
        this.hash = Objects.requireNonNull(hash, "hash must not be null");
        this.creator = Objects.requireNonNull(creator, "creator must not be null");
        this.generation = generation;
        this.birthRound = birthRound;
    }

    /**
     * Create a new event descriptor. This is package protected to only allow related classes to use it.  The creator
     * must be set before retrieval.
     *
     * @param hash        the hash of the event
     * @param generation  the age of an event, smaller is older
     * @param birthRound  the round when the event was created
     */
    @Deprecated(since = "0.46.0", forRemoval = true)
    protected EventDescriptor(@NonNull final Hash hash, final long generation, final long birthRound) {
        this.hash = Objects.requireNonNull(hash, "hash must not be null");
        this.generation = generation;
        this.birthRound = birthRound;
        this.creator = null;
    }

    /**
     * Set the creator node of the event. This is package protected to only allow related classes to use it.
     *
     * @param creator the creator node id
     */
    @Deprecated(since = "0.46.0", forRemoval = true)
    protected void setCreator(@NonNull final NodeId creator) {
        this.creator = Objects.requireNonNull(creator, "creator must not be null");
    }

    private void checkInitialization() {
        if (hash == null) {
            throw new IllegalStateException("EventDescriptor improperly initialized: the hash is null");
        }
        if (creator == null) {
            throw new IllegalStateException("EventDescriptor improperly initialized: the creator node id is null");
        }
    }

    /**
     * Get the hash of the event.
     *
     * @return the event's hash
     */
    @NonNull
    public Hash getHash() {
        checkInitialization();
        return hash;
    }

    /**
     * Get the node ID of the event's creator.
     *
     * @return a node ID
     */
    @NonNull
    public NodeId getCreator() {
        checkInitialization();
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
     * Get the round when the event was created.
     *
     * @return the round when the event was created
     */
    public long getBirthRound() {
        return birthRound;
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
        return ClassVersion.BIRTH_ROUND;
    }

    @Override
    public int getMinimumSupportedVersion() {
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
        out.writeLong(birthRound);
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
        creator = in.readSerializable(false, NodeId::new);
        if (creator == null) {
            throw new IOException("creator cannot be null");
        }
        generation = in.readLong();
        if (version < ClassVersion.BIRTH_ROUND) {
            birthRound = EventConstants.BIRTH_ROUND_UNDEFINED;
        } else {
            birthRound = in.readLong();
        }
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

        return Objects.equals(creator, that.creator)
                && generation == that.generation
                && birthRound == that.birthRound
                && hash.equals(that.hash);
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
        return new ToStringBuilder(this)
                .append("creator", creator)
                .append("generation", generation)
                .append("birthRound", birthRound)
                .append("hash", hex(hash.getValue()).substring(0, 12))
                .toString();
    }

    /**
     * Get the value used to determine if this event is ancient or not. Will be the event's generation prior to
     * migration, and the event's birth round after migration.
     *
     * @return the value used to determine if this event is ancient or not
     */
    public long getAncientIndicator(@NonNull final AncientMode ancientMode) {
        return switch (ancientMode) {
            case GENERATION_THRESHOLD -> getGeneration();
            case BIRTH_ROUND_THRESHOLD -> getBirthRound();
        };
    }
}
