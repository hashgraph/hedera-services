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

package com.hedera.node.app.service.mono.state.codec;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.hedera.pbj.runtime.io.buffer.BufferedData;
import java.nio.ByteBuffer;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

class DataBufferTest {

    @Test
    void readFullyDelegates() {
        final var b = new byte[] {(byte) 0x01, (byte) 0x02, (byte) 0x03, (byte) 0x04, (byte) 0x05};
        final var subject = BufferedData.wrap(ByteBuffer.wrap(b));
        final var bRead = new byte[b.length];
        subject.readBytes(bRead);
        assertArrayEquals(b, bRead);
    }

    @Test
    void skipBytes() {
        final var subject = BufferedData.wrap(ByteBuffer.wrap(new byte[] {}));
        final var skipped = subject.skip(32L);
        assertEquals(0, skipped);
    }

    @Test
    void canReadThings() {
        final var buffer = ByteBuffer.wrap(new byte[] {
            1, 2, 3, 4, 5, 6, 7, 8, 9, 0,
            1, 2, 3, 4, 5, 6, 7, 8, 9, 0,
            1, 2, 3, 4, 5, 6, 7, 8, 9, 0,
            1, 2, 3, 4, 5, 6, 7, 8, 9, 0,
        });
        final var subject = BufferedData.wrap(buffer);

        //        assertTrue(subject.readBoolean());
        subject.readByte();
        assertEquals(2, subject.readByte());
        assertEquals(3, subject.readUnsignedByte());
        //        assertEquals(1029, subject.readShort());
        //        assertEquals(1543, subject.readUnsignedShort());
        //        assertEquals(2057, subject.readChar());
        subject.readBytes(6);
        assertEquals(66051, subject.readInt());
        assertEquals(289644378304610305L, subject.readLong());
        assertEquals(9.625514E-38f, subject.readFloat());
        assertEquals(1.2688028221216506E-279, subject.readDouble());
    }

    @Test
    @Disabled("Enable once reading boolean is supported")
    void canReadFalse() {
        final var buffer = ByteBuffer.wrap(new byte[] {0});
        final var subject = BufferedData.wrap(buffer);

        //        assertFalse(subject.readBoolean());
    }

    @Test
    @Disabled("Enable once reading Strings is supported")
    void cannotReadStrings() {
        //        assertThrows(UnsupportedOperationException.class, () -> subject.readLine());
        //        assertThrows(UnsupportedOperationException.class, () -> subject.readUTF());
    }

    @Test
    @Disabled("Enable once direct access to buffer is supported")
    void returnsDelegate() {
        //        assertSame(buffer, subject.getBuffer());
    }

    @Test
    void delegatesWrites() {
        final var bytes = new byte[] {1, 2, 3, 4, 5};
        final var buffer = ByteBuffer.allocate(40);
        final var subject = BufferedData.wrap(buffer);

        subject.writeByte((byte) 12);
        subject.writeBytes(bytes);
        subject.writeBytes(bytes, 1, 3);
        subject.writeByte((byte) 42);
        subject.writeInt(15);
        subject.writeLong(16L);
        subject.writeFloat(17.0f);
        subject.writeDouble(18.0);

        assertArrayEquals(
                new byte[] {
                    12, 1, 2, 3, 4, 5, 2, 3, 4, 42, 0, 0, 0, 15, 0, 0, 0, 0, 0, 0, 0, 16, 65, -120, 0, 0, 64, 50, 0, 0,
                    0, 0, 0, 0, 0, 0, 0, 0, 0, 0
                },
                buffer.array());

        buffer.rewind();
        assertEquals(12, buffer.get());
        final var bytes5Read = new byte[5];
        buffer.get(bytes5Read);
        assertArrayEquals(bytes, bytes5Read);
        final var bytes3Read = new byte[3];
        buffer.get(bytes3Read);
        assertArrayEquals(new byte[] {2, 3, 4}, bytes3Read);
        assertEquals(42, buffer.get());
        assertEquals(15, buffer.getInt());
        assertEquals(16L, buffer.getLong());
        assertEquals(17.0f, buffer.getFloat());
        assertEquals(18.0, buffer.getDouble());
    }

    @Test
    @Disabled("Enable once writing Strings is supported")
    void cannotWriteStrings() {
        //        assertThrows(UnsupportedOperationException.class, () -> subject.writeBytes("foo"));
        //        assertThrows(UnsupportedOperationException.class, () -> subject.writeChars("foo"));
        //        assertThrows(UnsupportedOperationException.class, () -> subject.writeUTF("foo"));
    }
}
