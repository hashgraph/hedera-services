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

package com.swirlds.platform.test.chatter.network;

import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.common.system.NodeId;
import com.swirlds.common.system.events.EventDescriptor;
import com.swirlds.platform.test.chatter.network.framework.SimulatedChatterEvent;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.time.Instant;
import java.util.Objects;

/**
 * A very simple, fake event that is easy to track and reason about. Each event should have a number which is one more
 * that the previously created event.
 */
public class CountingChatterEvent implements SimulatedChatterEvent {
    private static final long CLASS_ID = 0xe92ae5ef4444248cL;

    private static final class ClassVersion {
        public static final int ORIGINAL = 1;
    }

    /** The order number to assign to the next event */
    private static long orderCounter = 0;

    /** A unique description of this event */
    private EventDescriptor descriptor;
    /** The creator of this event */
    private NodeId creator;
    /** The unique, monotonically increasing number assigned to each event across all nodes */
    private long order;
    /** The time this event was received by a node. */
    private Instant timeReceived;

    /** Default constructor for constructable registry */
    public CountingChatterEvent() {}

    public CountingChatterEvent(@NonNull final NodeId creator) {
        this(creator, orderCounter++);
    }

    public CountingChatterEvent(final CountingChatterEvent countingChatterEvent) {
        this(countingChatterEvent.creator, countingChatterEvent.order);
    }

    private CountingChatterEvent(@NonNull final NodeId creator, final long order) {
        this.creator = Objects.requireNonNull(creator, "creator must not be null");
        this.order = order;
        this.descriptor = new CountingEventDescriptor(creator, order);
    }

    public long getOrder() {
        return order;
    }

    @NonNull
    public NodeId getCreator() {
        return creator;
    }

    @Override
    public long getGeneration() {
        return order;
    }

    @Override
    public void serialize(final SerializableDataOutputStream out) throws IOException {
        // FUTURE WORK: The creator should be a selfSerializable NodeId at some point.
        // Changing the event format may require a HIP.  The old format is preserved for now.
        out.writeLong(creator.id());
        out.writeLong(order);
    }

    @Override
    public void deserialize(final SerializableDataInputStream in, final int version) throws IOException {
        // FUTURE WORK: The creator should be a selfSerializable NodeId at some point.
        // Changing the event format may require a HIP.  The old format is preserved for now.
        creator = NodeId.deserializeLong(in, false);
        order = in.readLong();
        this.descriptor = new CountingEventDescriptor(creator, order);
    }

    @Override
    public EventDescriptor getDescriptor() {
        return descriptor;
    }

    @Override
    public void setTimeReceived(final Instant timeReceived) {
        this.timeReceived = timeReceived;
    }

    @Override
    public SimulatedChatterEvent copy() {
        return new CountingChatterEvent(this);
    }

    @Override
    public Instant getTimeReceived() {
        return timeReceived;
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

    @Override
    public String toString() {
        return "Event(" + order + ", c:" + creator + ")";
    }
}
