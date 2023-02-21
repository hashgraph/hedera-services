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

import java.nio.ByteBuffer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ByteBufferDataOutputTest {
    @Mock
    private ByteBuffer buffer;

    private ByteBufferDataOutput subject;

    @BeforeEach
    void setUp() {
        subject = new ByteBufferDataOutput(buffer);
    }

    @Test
    void delegatesWrites() {
        final var bytes = new byte[] {1, 2, 3, 4, 5};

        InOrder inOrder = Mockito.inOrder(buffer);

        subject.write(12);
        subject.write(bytes);
        subject.write(bytes, 1, 3);
        subject.writeBoolean(true);
        subject.writeByte(42);
        subject.writeShort(13);
        subject.writeChar(14);
        subject.writeInt(15);
        subject.writeFloat(16.0f);
        subject.writeDouble(17.0);

        inOrder.verify(buffer).put((byte) 12);
        inOrder.verify(buffer).put(bytes);
        inOrder.verify(buffer).put(bytes, 1, 3);
        inOrder.verify(buffer).put((byte) 1);
        inOrder.verify(buffer).put((byte) 42);
        inOrder.verify(buffer).putShort((short) 13);
        inOrder.verify(buffer).putChar((char) 14);
        inOrder.verify(buffer).putInt(15);
    }

    @Test
    void cannotWriteStrings() {
        assertThrows(UnsupportedOperationException.class, () -> subject.writeBytes("foo"));
        assertThrows(UnsupportedOperationException.class, () -> subject.writeChars("foo"));
        assertThrows(UnsupportedOperationException.class, () -> subject.writeUTF("foo"));
    }
}
