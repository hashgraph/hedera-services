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
import com.swirlds.common.system.events.BaseEvent;
import com.swirlds.common.system.events.BaseEventHashedData;
import com.swirlds.common.system.events.BaseEventUnhashedData;
import com.swirlds.platform.EventStrings;
import com.swirlds.platform.chatter.protocol.messages.ChatterEvent;
import com.swirlds.platform.chatter.protocol.messages.ChatterEventDescriptor;
import com.swirlds.platform.chatter.protocol.messages.EventDescriptor;
import java.io.IOException;
import java.time.Instant;
import java.util.Objects;
import org.apache.commons.lang3.builder.HashCodeBuilder;

/**
 * A class used to hold information about an event transferred through gossip
 */
public class GossipEvent implements EventIntakeTask, BaseEvent, ChatterEvent {
    private static final long CLASS_ID = 0xfe16b46795bfb8dcL;
    private static final long ROUND_CREATED_UNDEFINED = -1;
    private BaseEventHashedData hashedData;
    private BaseEventUnhashedData unhashedData;
    private ChatterEventDescriptor descriptor;
    private Instant timeReceived;
    private long roundCreated = ROUND_CREATED_UNDEFINED;

    @SuppressWarnings("unused") // needed for RuntimeConstructable
    public GossipEvent() {}

    /**
     * @param hashedData
     * 		the hashed data for the event
     * @param unhashedData
     * 		the unhashed data for the event
     */
    public GossipEvent(final BaseEventHashedData hashedData, final BaseEventUnhashedData unhashedData) {
        this.hashedData = hashedData;
        this.unhashedData = unhashedData;
        this.timeReceived = Instant.now();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void serialize(final SerializableDataOutputStream out) throws IOException {
        out.writeSerializable(hashedData, false);
        out.writeSerializable(unhashedData, false);
        out.writeLong(roundCreated);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void deserialize(final SerializableDataInputStream in, final int version) throws IOException {
        hashedData = in.readSerializable(false, BaseEventHashedData::new);
        unhashedData = in.readSerializable(false, BaseEventUnhashedData::new);
        roundCreated = in.readLong();
        timeReceived = Instant.now();
    }

    /**
     * Get the hashed data for the event.
     */
    @Override
    public BaseEventHashedData getHashedData() {
        return hashedData;
    }

    /**
     * Get the unhashed data for the event.
     */
    @Override
    public BaseEventUnhashedData getUnhashedData() {
        return unhashedData;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public EventDescriptor getDescriptor() {
        return descriptor;
    }

    /**
     * Build the descriptor of this event. This cannot be done when the event is first instantiated, it needs to be
     * hashed before the descriptor can be built.
     */
    public void buildDescriptor() {
        this.descriptor =
                new ChatterEventDescriptor(hashedData.getHash(), hashedData.getCreatorId(), hashedData.getGeneration());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Instant getTimeReceived() {
        return timeReceived;
    }

    /**
     * @return true if roundCreated has been set
     */
    public boolean isRoundCreatedSet() {
        return roundCreated != ROUND_CREATED_UNDEFINED;
    }

    public long getRoundCreated() {
        return roundCreated;
    }

    public void setRoundCreated(final long roundCreated) {
        this.roundCreated = roundCreated;
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
        return ClassVersion.ORIGINAL;
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
        if (this == o) return true;

        if (o == null || getClass() != o.getClass()) return false;

        final GossipEvent that = (GossipEvent) o;

        return Objects.equals(hashedData, that.hashedData)
                && Objects.equals(unhashedData, that.unhashedData)
                && Objects.equals(descriptor, that.descriptor);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return new HashCodeBuilder(17, 37)
                .append(hashedData)
                .append(unhashedData)
                .append(descriptor)
                .toHashCode();
    }

    private static final class ClassVersion {
        public static final int ORIGINAL = 1;
    }
}
