/*
 * Copyright (C) 2016-2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform.event;

import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.common.system.NodeId;
import com.swirlds.common.system.events.BaseEvent;
import com.swirlds.common.system.events.BaseEventHashedData;
import com.swirlds.common.system.events.BaseEventImpl;
import com.swirlds.common.system.events.BaseEventUnhashedData;
import com.swirlds.common.system.events.EventDescriptor;
import com.swirlds.platform.EventStrings;
import com.swirlds.platform.gossip.chatter.protocol.messages.ChatterEvent;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.io.IOException;
import java.time.Instant;
import java.util.Objects;

/**
 * A class used to hold information about an event transferred through gossip
 */
public class GossipEvent implements BaseEvent, ChatterEvent {
    private static final long CLASS_ID = 0xfe16b46795bfb8dcL;

    private static final class ClassVersion {
        /**
         * Serializes BaseEventHashedData and BaseEventUnhashedData.
         */
        public static final int ORIGINAL = 1;

        public static final int REMOVED_ROUND = 2;
        /**
         * Refactored event serialization to support adding address book round.
         */
        public static final int ADDRESS_BOOK_ROUND = 3;
    }

    private BaseEventImpl baseEvent;
    private EventDescriptor descriptor;
    private Instant timeReceived;

    /**
     * The id of the node which sent us this event
     * <p>
     * The sender ID of an event should not be serialized when an event is serialized, and it should not affect the
     * hash of the event in any way.
     */
    private NodeId senderId;

    @SuppressWarnings("unused") // needed for RuntimeConstructable
    public GossipEvent() {}

    /**
     * @param hashedData   the hashed data for the event
     * @param unhashedData the unhashed data for the event
     */
    public GossipEvent(
            @NonNull final BaseEventHashedData hashedData, @NonNull final BaseEventUnhashedData unhashedData) {
        // The BaseEventImpl constructor checks for null.
        this.baseEvent = new BaseEventImpl(hashedData, unhashedData);
        this.timeReceived = Instant.now();
        this.senderId = null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void serialize(final SerializableDataOutputStream out) throws IOException {
        out.writeSerializable(baseEvent, false);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void deserialize(final SerializableDataInputStream in, final int version) throws IOException {
        if (version < ClassVersion.ADDRESS_BOOK_ROUND) {
            final BaseEventHashedData hashedData = in.readSerializable(false, BaseEventHashedData::new);
            final BaseEventUnhashedData unhashedData = in.readSerializable(false, BaseEventUnhashedData::new);
            this.baseEvent = new BaseEventImpl(hashedData, unhashedData);
        } else {
            this.baseEvent = in.readSerializable(false, BaseEventImpl::new);
        }
        if (version == ClassVersion.ORIGINAL) {
            in.readLong(); // roundCreated
        }
        timeReceived = Instant.now();
    }

    /**
     * Get the base event
     *
     * @return the base event
     */
    public BaseEventImpl getBaseEvent() {
        return baseEvent;
    }

    /**
     * Get the hashed data for the event.
     */
    @Override
    public BaseEventHashedData getHashedData() {
        return baseEvent.getHashedData();
    }

    /**
     * Get the unhashed data for the event.
     */
    @Override
    public BaseEventUnhashedData getUnhashedData() {
        return baseEvent.getUnhashedData();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public EventDescriptor getDescriptor() {
        if (descriptor == null) {
            throw new IllegalStateException("Can not get descriptor until event has been hashed");
        }
        return descriptor;
    }

    /**
     * Build the descriptor of this event. This cannot be done when the event is first instantiated, it needs to be
     * hashed before the descriptor can be built.
     */
    public void buildDescriptor() {
        this.descriptor = baseEvent.getHashedData().getEventDescriptor();
    }

    @Override
    public long getGeneration() {
        return baseEvent.getHashedData().getGeneration();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public @NonNull Instant getTimeReceived() {
        return timeReceived;
    }

    /**
     * Get the id of the node which sent us this event
     *
     * @return the id of the node which sent us this event
     */
    @Nullable
    public NodeId getSenderId() {
        return senderId;
    }

    /**
     * Set the id of the node which sent us this event
     *
     * @param senderId the id of the node which sent us this event
     */
    public void setSenderId(@NonNull final NodeId senderId) {
        this.senderId = senderId;
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
        return ClassVersion.ADDRESS_BOOK_ROUND;
    }

    @Override
    public int getMinimumSupportedVersion() {
        return ClassVersion.REMOVED_ROUND;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return EventStrings.toMediumString(this);
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

        final GossipEvent that = (GossipEvent) o;
        return Objects.equals(getHashedData(), that.getHashedData());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return baseEvent.getHashedData().getHash().hashCode();
    }
}
