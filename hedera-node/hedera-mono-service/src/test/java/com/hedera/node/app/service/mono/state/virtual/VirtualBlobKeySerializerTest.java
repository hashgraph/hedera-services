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

import static com.hedera.node.app.service.mono.state.virtual.VirtualBlobKey.BYTES_IN_SERIALIZED_FORM;
import static com.hedera.node.app.service.mono.state.virtual.VirtualBlobKey.Type.FILE_DATA;
import static com.hedera.node.app.service.mono.state.virtual.VirtualBlobKeySerializer.CLASS_ID;
import static com.hedera.node.app.service.mono.state.virtual.VirtualBlobKeySerializer.CURRENT_VERSION;
import static com.hedera.node.app.service.mono.state.virtual.VirtualBlobKeySerializer.DATA_VERSION;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import com.hedera.pbj.runtime.io.buffer.BufferedData;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import java.io.IOException;
import org.junit.jupiter.api.Test;

class VirtualBlobKeySerializerTest {
    private final int entityNum = 2;
    private final int otherEntityNum = 3;

    private final VirtualBlobKeySerializer subject = new VirtualBlobKeySerializer();

    @Test
    void gettersWork() {
        assertEquals(BYTES_IN_SERIALIZED_FORM, subject.getSerializedSize());
        assertEquals(DATA_VERSION, subject.getCurrentDataVersion());
        assertEquals(CLASS_ID, subject.getClassId());
        assertEquals(CURRENT_VERSION, subject.getVersion());
    }

    @Test
    void getSerializedSizeWorks() {
        final var virtualBlobKey = new VirtualBlobKey(FILE_DATA, entityNum);

        assertEquals(BYTES_IN_SERIALIZED_FORM, subject.getSerializedSize(virtualBlobKey));
    }

    @Test
    void deserializeWorks() throws IOException {
        final BufferedData bin = BufferedData.allocate(subject.getSerializedSize());
        final var expectedKey = new VirtualBlobKey(FILE_DATA, entityNum);
        bin.writeByte((byte) FILE_DATA.ordinal());
        bin.writeInt(entityNum);
        bin.reset();

        assertEquals(expectedKey, subject.deserialize(bin));
    }

    @Test
    void serializeWorks() {
        final BufferedData out = BufferedData.allocate(subject.getSerializedSize());
        final var virtualBlobKey = new VirtualBlobKey(FILE_DATA, entityNum);

        subject.serialize(virtualBlobKey, out);
        assertEquals(BYTES_IN_SERIALIZED_FORM, out.position());
    }

    @Test
    void equalsUsingByteBufferWorks() throws IOException {
        final var someKey = new VirtualBlobKey(FILE_DATA, entityNum);
        final var sameTypeDiffNum = new VirtualBlobKey(FILE_DATA, otherEntityNum);
        final var diffTypeSameNum = new VirtualBlobKey(VirtualBlobKey.Type.FILE_METADATA, entityNum);

        final BufferedData bin = BufferedData.allocate(subject.getSerializedSize());
        bin.writeByte((byte) someKey.getType().ordinal());
        bin.writeInt(someKey.getEntityNumCode());
        bin.reset();

        assertTrue(subject.equals(bin, someKey));
        bin.reset();
        assertFalse(subject.equals(bin, sameTypeDiffNum));
        bin.reset();
        assertFalse(subject.equals(bin, diffTypeSameNum));
    }

    @Test
    void serdesAreNoop() {
        final var in = mock(SerializableDataInputStream.class);
        assertDoesNotThrow(() -> subject.deserialize(in, 1));
        assertDoesNotThrow(() -> subject.serialize(null));
    }
}
