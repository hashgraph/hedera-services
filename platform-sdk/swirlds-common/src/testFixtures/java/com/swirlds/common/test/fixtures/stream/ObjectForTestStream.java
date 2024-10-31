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

package com.swirlds.common.test.fixtures.stream;

import static com.swirlds.common.utility.ByteUtils.intToByteArray;

import com.swirlds.common.AbstractHashable;
import com.swirlds.common.crypto.RunningHash;
import com.swirlds.common.crypto.RunningHashable;
import com.swirlds.common.crypto.SerializableHashable;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.common.stream.StreamAligned;
import com.swirlds.common.stream.Timestamped;
import java.io.IOException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;

/**
 * Defines a RunningHashable &amp; SerializableHashable class for testing LinkedObjectStream
 */
public class ObjectForTestStream extends AbstractHashable
        implements RunningHashable, SerializableHashable, StreamAligned, Timestamped {

    private static final long CLASS_ID = 0xeecf8388d5496ba4L;

    /**
     * each ObjectForTestStream has a byte array and an Instant
     */
    private static final int CLASS_VERSION_PAYLOAD = 2;

    private static final int CLASS_VERSION = CLASS_VERSION_PAYLOAD;

    private byte[] payload;
    private Instant consensusTimestamp;

    private RunningHash runningHash;

    private long streamAlignment;

    // For RuntimeConstructable
    public ObjectForTestStream() {}

    public ObjectForTestStream(final int number, final Instant consensusTimestamp) {
        this(number, consensusTimestamp, NO_ALIGNMENT);
    }

    public ObjectForTestStream(final int number, final Instant consensusTimestamp, final long streamAlignment) {
        this(intToByteArray(number), consensusTimestamp, streamAlignment);
    }

    public ObjectForTestStream(final byte[] payload, final Instant consensusTimestamp, final long streamAlignment) {
        this.payload = payload;
        this.consensusTimestamp = consensusTimestamp;
        runningHash = new RunningHash();
        this.streamAlignment = streamAlignment;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getStreamAlignment() {
        return streamAlignment;
    }

    @Override
    public Instant getTimestamp() {
        return consensusTimestamp;
    }

    @Override
    public long getClassId() {
        return CLASS_ID;
    }

    @Override
    public int getClassVersion() {
        return CLASS_VERSION;
    }

    @Override
    public RunningHash getRunningHash() {
        return runningHash;
    }

    @Override
    public void serialize(SerializableDataOutputStream out) throws IOException {
        out.writeByteArray(payload);
        out.writeInstant(consensusTimestamp);
    }

    @Override
    public void deserialize(SerializableDataInputStream in, int version) throws IOException {
        if (version == CLASS_VERSION_PAYLOAD) {
            payload = in.readByteArray(Integer.MAX_VALUE);
        } else {
            // read a int number
            int number = in.readInt();
            payload = intToByteArray(number);
        }
        consensusTimestamp = in.readInstant();
    }

    @Override
    public String toString() {
        return String.format("ObjectForTestStream[payload size: %d, time: %s]", payload.length, consensusTimestamp);
    }

    @Override
    public boolean equals(final Object other) {
        if (this == other) {
            return true;
        }
        if (other == null || getClass() != other.getClass()) {
            return false;
        }
        final ObjectForTestStream that = (ObjectForTestStream) other;
        return Arrays.equals(payload, that.payload) && Objects.equals(consensusTimestamp, that.consensusTimestamp);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(consensusTimestamp);
        result = 31 * result + Arrays.hashCode(payload);
        return result;
    }

    /**
     * get a random ObjectForTestStream
     *
     * @return a random ObjectForTestStream
     */
    public static ObjectForTestStream getRandomObjectForTestStream(final int sizeOfPayload) {
        final SecureRandom random = new SecureRandom();
        final byte[] payload = new byte[sizeOfPayload];
        random.nextBytes(payload);
        return new ObjectForTestStream(payload, Instant.now(), NO_ALIGNMENT);
    }
}
