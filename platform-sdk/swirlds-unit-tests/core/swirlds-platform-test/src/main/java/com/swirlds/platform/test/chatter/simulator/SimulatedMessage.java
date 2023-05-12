/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

import com.swirlds.common.io.SelfSerializable;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;
import org.apache.commons.lang3.tuple.Pair;

/**
 * Represents a message sent from one node to another.
 */
public class SimulatedMessage {

    private final long source;
    private final long destination;

    private final SelfSerializable payload;

    private final int size;

    private Instant transmissionTime;
    private long bytesToSend;
    private long bytesToReceive;

    public SimulatedMessage(final long source, final long destination, final SelfSerializable payload) {
        this.source = source;
        this.destination = destination;

        final Pair<Integer, SelfSerializable> pair = copyBySerialization(payload);
        this.size = pair.getKey();
        this.payload = pair.getValue();

        bytesToSend = size;
        bytesToReceive = size;
    }

    /**
     * In order to make sure there is no funny business where nodes pass objects directly between each other, copy
     * the payload via serialization. Measure the size of a message at the same time.
     *
     * @param payload
     * 		the payload to copy
     * @return a pair with the size and the copy
     */
    private static Pair<Integer /* size */, SelfSerializable> copyBySerialization(final SelfSerializable payload) {
        final ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
        final SerializableDataOutputStream out = new SerializableDataOutputStream(byteOut);

        try {
            out.writeSerializable(payload, true);
            out.flush();
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }

        final byte[] bytes = byteOut.toByteArray();

        final int length = bytes.length;

        final SerializableDataInputStream in = new SerializableDataInputStream(new ByteArrayInputStream(bytes));

        final SelfSerializable copy;
        try {
            copy = in.readSerializable();
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }

        return Pair.of(length, copy);
    }

    /**
     * Get the node ID that sent this message.
     */
    public long getSource() {
        return source;
    }

    /**
     * Get the node ID where this message is heading.
     */
    public long getDestination() {
        return destination;
    }

    /**
     * Get the payload of the message.
     */
    public SelfSerializable getPayload() {
        return payload;
    }

    /**
     * Get the size, in bytes, of the payload.
     */
    public int getSize() {
        return size;
    }

    /**
     * Get the time when this message was sent.
     */
    public Instant getTransmissionTime() {
        return transmissionTime;
    }

    /**
     * Set the time when this message was sent.
     */
    public void setTransmissionTime(final Instant transmissionTime) {
        this.transmissionTime = transmissionTime;
    }

    /**
     * Get the number of bytes that still need to be sent.
     */
    public long getBytesToSend() {
        return bytesToSend;
    }

    /**
     * Set the number of bytes that still need to be sent.
     */
    public void setBytesToSend(final long bytesToSend) {
        this.bytesToSend = bytesToSend;
    }

    /**
     * Get the number of bytes that still need to be received.
     */
    public long getBytesToReceive() {
        return bytesToReceive;
    }

    /**
     * Set the number of bytes that still need to be received.
     */
    public void setBytesToReceive(final long bytesToReceive) {
        this.bytesToReceive = bytesToReceive;
    }
}
