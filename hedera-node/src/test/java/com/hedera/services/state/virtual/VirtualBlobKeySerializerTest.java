/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.state.virtual;

import static com.hedera.services.state.virtual.VirtualBlobKey.BYTES_IN_SERIALIZED_FORM;
import static com.hedera.services.state.virtual.VirtualBlobKey.Type.FILE_DATA;
import static com.hedera.services.state.virtual.VirtualBlobKeySerializer.CLASS_ID;
import static com.hedera.services.state.virtual.VirtualBlobKeySerializer.CURRENT_VERSION;
import static com.hedera.services.state.virtual.VirtualBlobKeySerializer.DATA_VERSION;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import org.junit.jupiter.api.Test;

class VirtualBlobKeySerializerTest {
    private final int entityNum = 2;
    private final int otherEntityNum = 3;

    private final VirtualBlobKeySerializer subject = new VirtualBlobKeySerializer();

    @Test
    void gettersWork() {
        final var bin = mock(ByteBuffer.class);

        assertEquals(BYTES_IN_SERIALIZED_FORM, subject.deserializeKeySize(bin));
        assertEquals(BYTES_IN_SERIALIZED_FORM, subject.getSerializedSize());
        assertEquals(DATA_VERSION, subject.getCurrentDataVersion());
        assertEquals(CLASS_ID, subject.getClassId());
        assertEquals(CURRENT_VERSION, subject.getVersion());
    }

    @Test
    void deserializeWorks() throws IOException {
        final var bin = mock(ByteBuffer.class);
        final var expectedKey = new VirtualBlobKey(FILE_DATA, entityNum);
        given(bin.get()).willReturn((byte) FILE_DATA.ordinal());
        given(bin.getInt()).willReturn(entityNum);

        assertEquals(expectedKey, subject.deserialize(bin, 1));
    }

    @Test
    void serializeWorks() throws IOException {
        final var out = mock(SerializableDataOutputStream.class);
        final var virtualBlobKey = new VirtualBlobKey(FILE_DATA, entityNum);

        assertEquals(BYTES_IN_SERIALIZED_FORM, subject.serialize(virtualBlobKey, out));

        verify(out).writeByte((byte) FILE_DATA.ordinal());
        verify(out).writeInt(entityNum);
    }

    @Test
    void equalsUsingByteBufferWorks() throws IOException {
        final var someKey = new VirtualBlobKey(FILE_DATA, entityNum);
        final var sameTypeDiffNum = new VirtualBlobKey(FILE_DATA, otherEntityNum);
        final var diffTypeSameNum =
                new VirtualBlobKey(VirtualBlobKey.Type.FILE_METADATA, entityNum);

        final var bin = mock(ByteBuffer.class);
        given(bin.get()).willReturn((byte) someKey.getType().ordinal());
        given(bin.getInt()).willReturn(someKey.getEntityNumCode());

        assertTrue(subject.equals(bin, 1, someKey));
        assertFalse(subject.equals(bin, 1, sameTypeDiffNum));
        assertFalse(subject.equals(bin, 1, diffTypeSameNum));
    }

    @Test
    void serdesAreNoop() {
        final var in = mock(SerializableDataInputStream.class);
        assertDoesNotThrow(() -> subject.deserialize(in, 1));
        assertDoesNotThrow(() -> subject.serialize(null));
    }
}
