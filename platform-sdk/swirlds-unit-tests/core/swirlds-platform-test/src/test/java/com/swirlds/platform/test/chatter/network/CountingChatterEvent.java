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
import com.swirlds.platform.chatter.protocol.messages.EventDescriptor;
import java.io.IOException;
import java.time.Instant;

/**
 * A very simple, fake event that is easy to track and reason about. Each event should have a number which is one more
 * that the previously created event.
 */
public class CountingChatterEvent implements SimulatedChatterEvent {
    private static final long CLASS_ID = 0xe92ae5ef4444248cL;

    private static final class ClassVersion {
        public static final int ORIGINAL = 1;
    }

    private final EventDescriptor descriptor;

    private long creator;
    private long order;
    private Instant timeReceived;

    public CountingChatterEvent(final long creator, final long order) {
        this.descriptor = new FakeEventDescriptor(creator, order);
        this.creator = creator;
        this.order = order;
    }

    public long getOrder() {
        return order;
    }

    public long getCreator() {
        return creator;
    }

    @Override
    public long getGeneration() {
        return order;
    }

    @Override
    public void serialize(final SerializableDataOutputStream out) throws IOException {
        out.writeLong(creator);
        out.writeLong(order);
    }

    @Override
    public void deserialize(final SerializableDataInputStream in, final int version) throws IOException {
        creator = in.readLong();
        order = in.readLong();
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
