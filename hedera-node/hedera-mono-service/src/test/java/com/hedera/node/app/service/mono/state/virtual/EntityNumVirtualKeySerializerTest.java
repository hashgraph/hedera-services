/*
 * Copyright (C) 2020-2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.mono.state.virtual;

import static com.hedera.node.app.service.mono.state.virtual.EntityNumVirtualKey.BYTES_IN_SERIALIZED_FORM;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hedera.pbj.runtime.io.buffer.BufferedData;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import org.junit.jupiter.api.Test;

class EntityNumVirtualKeySerializerTest {
    private final long longKey = 2;
    private final long otherLongKey = 3;

    private final EntityNumVirtualKeySerializer subject = new EntityNumVirtualKeySerializer();

    @Test
    void gettersWork() {
        final ByteBuffer bin = ByteBuffer.allocate(subject.getSerializedSize());

        assertEquals(BYTES_IN_SERIALIZED_FORM, subject.getSerializedSize());
        assertEquals(EntityNumVirtualKeySerializer.DATA_VERSION, subject.getCurrentDataVersion());
        assertEquals(EntityNumVirtualKeySerializer.CLASS_ID, subject.getClassId());
        assertEquals(EntityNumVirtualKeySerializer.CURRENT_VERSION, subject.getVersion());
    }

    @Test
    void deserializeUsingBufferedDataWorks() {
        final var in = BufferedData.allocate(subject.getSerializedSize());
        in.writeLong(longKey);
        in.resetPosition();

        final var expectedKey = new EntityNumVirtualKey(longKey);
        assertEquals(expectedKey, subject.deserialize(in));
    }

    @Test
    void deserializeUsingByteBufferWorks() throws IOException {
        final ByteBuffer bin = ByteBuffer.allocate(subject.getSerializedSize());
        bin.putLong(longKey);
        bin.rewind();

        final var expectedKey = new EntityNumVirtualKey(longKey);
        assertEquals(expectedKey, subject.deserialize(bin, 1));
    }

    @Test
    void serializeUsingBufferedDataWorks() {
        final BufferedData out = BufferedData.allocate(subject.getSerializedSize());
        final BufferedData verify = BufferedData.allocate(subject.getSerializedSize());
        verify.writeLong(longKey);
        verify.resetPosition();

        final var virtualKey = new EntityNumVirtualKey(longKey);

        subject.serialize(virtualKey, out);
        assertEquals(BYTES_IN_SERIALIZED_FORM, out.position());
        out.resetPosition();

        assertEquals(verify, out);
    }

    void serializeUsingByteBufferWorks() throws IOException {
        final ByteBuffer out = ByteBuffer.allocate(subject.getSerializedSize());
        final ByteBuffer verify = ByteBuffer.allocate(subject.getSerializedSize());
        verify.putLong(longKey);
        verify.rewind();

        final var virtualKey = new EntityNumVirtualKey(longKey);

        subject.serialize(virtualKey, out);
        assertEquals(BYTES_IN_SERIALIZED_FORM, out.position());
        out.rewind();

        assertEquals(verify, out);
    }

    @Test
    void equalsUsingBufferedDataWorks() throws IOException {
        final var someKey = new EntityNumVirtualKey(longKey);
        final var diffNum = new EntityNumVirtualKey(otherLongKey);

        final BufferedData buf = BufferedData.allocate(subject.getSerializedSize());
        buf.writeLong(someKey.getKeyAsLong());
        buf.resetPosition();

        assertTrue(subject.equals(buf, someKey));
        buf.resetPosition();
        assertFalse(subject.equals(buf, diffNum));
    }

    @Test
    void equalsUsingByteBufferWorks() throws IOException {
        final var someKey = new EntityNumVirtualKey(longKey);
        final var diffNum = new EntityNumVirtualKey(otherLongKey);

        final ByteBuffer bin = ByteBuffer.allocate(subject.getSerializedSize());
        bin.putLong(someKey.getKeyAsLong());
        bin.rewind();

        assertTrue(subject.equals(bin, 1, someKey));
        bin.rewind();
        assertFalse(subject.equals(bin, 1, diffNum));
    }

    @Test
    void serdesAreNoop() {
        assertDoesNotThrow(() -> subject.deserialize((SerializableDataInputStream) null, 1));
        assertDoesNotThrow(() -> subject.serialize(null));
    }
}
