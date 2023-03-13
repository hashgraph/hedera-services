/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;

import com.hedera.pbj.runtime.io.buffer.BufferedData;
import java.nio.ByteBuffer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DataBufferTest {
    @Mock
    private ByteBuffer buffer;

    private BufferedData subject;

    @BeforeEach
    void setUp() {
        subject = BufferedData.wrap(buffer);
    }

    @Test
    void readFullyDelegates() {
        final var b = new byte[5];
        subject.readBytes(b);
        verify(buffer).get(b);
        subject.readBytes(b, 1, 3);
        verify(buffer).get(b, 1, 3);
    }

    @Test
    void skipBytes() {
        final var skipped = subject.skip(32L);
        assertEquals(0, skipped);
    }

    @Test
    void canReadThings() {
        buffer = ByteBuffer.wrap(new byte[] {
            1, 2, 3, 4, 5, 6, 7, 8, 9, 0,
            1, 2, 3, 4, 5, 6, 7, 8, 9, 0,
            1, 2, 3, 4, 5, 6, 7, 8, 9, 0,
            1, 2, 3, 4, 5, 6, 7, 8, 9, 0,
        });
        subject = BufferedData.wrap(buffer);

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
        buffer = ByteBuffer.wrap(new byte[] {0});
        subject = BufferedData.wrap(buffer);

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

        InOrder inOrder = Mockito.inOrder(buffer);

        subject.writeByte((byte) 12);
        subject.writeBytes(bytes);
        subject.writeBytes(bytes, 1, 3);
        //        subject.writeBoolean(true);
        //        subject.writeBoolean(false);
        subject.writeByte((byte) 42);
        //        subject.writeShort(13);
        //        subject.writeChar(14);
        subject.writeInt(15);
        subject.writeLong(16L);
        subject.writeFloat(17.0f);
        subject.writeDouble(18.0);

        inOrder.verify(buffer).put((byte) 12);
        inOrder.verify(buffer).put(bytes);
        inOrder.verify(buffer).put(bytes, 1, 3);
        //        inOrder.verify(buffer).put((byte) 1);
        //        inOrder.verify(buffer).put((byte) 0);
        inOrder.verify(buffer).put((byte) 42);
        //        inOrder.verify(buffer).putShort((short) 13);
        //        inOrder.verify(buffer).putChar((char) 14);
        inOrder.verify(buffer).putInt(15);
        inOrder.verify(buffer).putLong(16L);
        inOrder.verify(buffer).putFloat(17.0f);
        inOrder.verify(buffer).putDouble(18.0);
    }

    @Test
    @Disabled("Enable once writing Strings is supported")
    void cannotWriteStrings() {
        //        assertThrows(UnsupportedOperationException.class, () -> subject.writeBytes("foo"));
        //        assertThrows(UnsupportedOperationException.class, () -> subject.writeChars("foo"));
        //        assertThrows(UnsupportedOperationException.class, () -> subject.writeUTF("foo"));
    }
}
