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

package com.hedera.node.app.spi.state.serdes;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verify;

import java.nio.ByteBuffer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ByteBufferDataInputTest {
    @Mock
    private ByteBuffer buffer;

    private ByteBufferDataInput subject;

    @BeforeEach
    void setUp() {
        subject = new ByteBufferDataInput(buffer);
    }

    @Test
    void readFullyDelegates() {
        final var b = new byte[5];
        subject.readFully(b);
        verify(buffer).get(b);
        subject.readFully(b, 1, 3);
        verify(buffer).get(b, 1, 3);
    }

    @Test
    void skipBytes() {
        final var skipped = subject.skipBytes(32);
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
        subject = new ByteBufferDataInput(buffer);

        assertTrue(subject.readBoolean());
        assertEquals(2, subject.readByte());
        assertEquals(3, subject.readUnsignedByte());
        assertEquals(1029, subject.readShort());
        assertEquals(1543, subject.readUnsignedShort());
        assertEquals(2057, subject.readChar());
        assertEquals(66051, subject.readInt());
        assertEquals(289644378304610305l, subject.readLong());
        assertEquals(9.625514E-38f, subject.readFloat());
        assertEquals(1.2688028221216506E-279, subject.readDouble());
    }

    @Test
    void canReadFalse() {
        buffer = ByteBuffer.wrap(new byte[] {0});
        subject = new ByteBufferDataInput(buffer);

        assertFalse(subject.readBoolean());
    }

    @Test
    void cannotReadStrings() {
        assertThrows(UnsupportedOperationException.class, () -> subject.readLine());
        assertThrows(UnsupportedOperationException.class, () -> subject.readUTF());
    }

    @Test
    void returnsDelegate() {
        assertSame(buffer, subject.getBuffer());
    }
}
