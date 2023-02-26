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

package com.swirlds.platform.test.chatter.simulator;

import com.swirlds.common.crypto.DigestType;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.platform.chatter.protocol.messages.ChatterEvent;
import com.swirlds.platform.chatter.protocol.messages.ChatterEventDescriptor;
import java.io.IOException;
import java.time.Instant;
import java.util.Random;

/**
 * A simulated event.
 */
public class SimulatedEvent implements ChatterEvent {

    private static final long CLASS_ID = 0x8872f27408ab54b2L;

    private static final class ClassVersion {
        public static final int ORIGINAL = 1;
    }

    private ChatterEventDescriptor descriptor;
    private byte[] data;
    private Instant timeReceived;

    /**
     * Zero arg constructor for constructable registry.
     */
    @SuppressWarnings("unused")
    public SimulatedEvent() {}

    /**
     * Create a new simulated event.
     *
     * @param random
     * 		a source of randomness
     * @param creator
     * 		the creator of the event
     * @param round
     * 		the round associated with the event
     * @param size
     * 		the size of the event
     */
    public SimulatedEvent(final Random random, final long creator, final long round, final int size) {
        this.data = new byte[size];

        final byte[] hashBytes = new byte[DigestType.SHA_384.digestLength()];
        random.nextBytes(hashBytes);
        final Hash hash = new Hash(hashBytes, DigestType.SHA_384);

        this.descriptor = new ChatterEventDescriptor(hash, creator, round);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ChatterEventDescriptor getDescriptor() {
        return descriptor;
    }

    @Override
    public Instant getTimeReceived() {
        return timeReceived;
    }

    public void setTimeReceived(final Instant timeReceived) {
        this.timeReceived = timeReceived;
    }

    /**
     * Get the data associated with the event.
     *
     * @return the event's data
     */
    public byte[] getData() {
        return data;
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
    public void serialize(final SerializableDataOutputStream out) throws IOException {
        out.writeSerializable(descriptor, false);
        out.writeByteArray(data);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void deserialize(final SerializableDataInputStream in, final int version) throws IOException {
        descriptor = in.readSerializable(false, ChatterEventDescriptor::new);
        data = in.readByteArray(Integer.MAX_VALUE);
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
    public boolean equals(final Object o) {
        if (o == null || o.getClass() != this.getClass()) {
            return false;
        }

        if (o == this) {
            return true;
        }

        return descriptor.equals(((SimulatedEvent) o).descriptor);
    }

    /**
     * {@inheritDoc}
     */
    public int hashCode() {
        return descriptor.hashCode();
    }
}
