/*
 * Copyright (C) 2021-2022 Hedera Hashgraph, LLC
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
import static com.hedera.services.state.virtual.VirtualBlobKey.Type.CONTRACT_BYTECODE;
import static com.hedera.services.state.virtual.VirtualBlobKey.Type.FILE_DATA;
import static com.hedera.services.state.virtual.VirtualBlobKey.Type.FILE_METADATA;
import static com.hedera.services.state.virtual.VirtualBlobKey.Type.SYSTEM_DELETED_ENTITY_EXPIRY;
import static com.hedera.services.state.virtual.VirtualBlobKey.fromPath;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class VirtualBlobKeyTest {
    private final int entityNum = 2;

    private VirtualBlobKey subject;

    @BeforeEach
    void setup() {
        subject = new VirtualBlobKey(FILE_DATA, entityNum);
    }

    @Test
    void ordersSameAsExpected() {
        final var sameButDifferent = subject;
        assertEquals(0, subject.compareTo(sameButDifferent));
    }

    @Test
    void orderPrioritizesEntityNum() {
        final var smallerEntityNum =
                new VirtualBlobKey(SYSTEM_DELETED_ENTITY_EXPIRY, entityNum - 1);
        assertEquals(+1, subject.compareTo(smallerEntityNum));
    }

    @Test
    void orderBreaksEntityNumTiesByType() {
        final var expectedCmp = FILE_DATA.ordinal() - SYSTEM_DELETED_ENTITY_EXPIRY.ordinal();
        final var largerType = new VirtualBlobKey(SYSTEM_DELETED_ENTITY_EXPIRY, entityNum);
        assertEquals(expectedCmp, subject.compareTo(largerType));
    }

    @Test
    void objectContractMet() {
        final var one = new VirtualBlobKey(VirtualBlobKey.Type.FILE_METADATA, entityNum);
        final var two = new VirtualBlobKey(FILE_DATA, entityNum);
        final var twoRef = two;
        final var three = new VirtualBlobKey(FILE_DATA, entityNum);
        final var otherEntityNum = 3;
        final var four = new VirtualBlobKey(VirtualBlobKey.Type.FILE_METADATA, otherEntityNum);

        assertNotEquals(two, one);
        assertEquals(two, twoRef);
        assertEquals(two, three);

        assertNotEquals(one.hashCode(), two.hashCode());
        assertEquals(two.hashCode(), three.hashCode());
        assertNotEquals(one, four);
        assertNotNull(one);

        final var forcedEqualsCheck = one.equals(entityNum);
        assertFalse(forcedEqualsCheck, "forcing equals on two different class types.");
    }

    @Test
    void fromPathWorks() {
        final String dataPath = "/0/f112";
        final String metadataPath = "/0/k3";
        final String bytecodePath = "/0/s4";
        final String expiryTimePath = "/0/e5";

        final var dataBlobKey = fromPath(dataPath);
        final var metadataBlobKey = fromPath(metadataPath);
        final var bytecodeBlobKey = fromPath(bytecodePath);
        final var expiryBlobKey = fromPath(expiryTimePath);

        assertEquals(112, dataBlobKey.getEntityNumCode());
        assertEquals(FILE_DATA, dataBlobKey.getType());
        assertEquals(3, metadataBlobKey.getEntityNumCode());
        assertEquals(FILE_METADATA, metadataBlobKey.getType());
        assertEquals(4, bytecodeBlobKey.getEntityNumCode());
        assertEquals(CONTRACT_BYTECODE, bytecodeBlobKey.getType());
        assertEquals(5, expiryBlobKey.getEntityNumCode());
        assertEquals(SYSTEM_DELETED_ENTITY_EXPIRY, expiryBlobKey.getType());
    }

    @Test
    void fromPathThrowsOnInvalidEntityNum() {
        final String dataPath = "/0/ffff";
        assertThrows(IllegalArgumentException.class, () -> fromPath(dataPath));
    }

    @Test
    void fromPathThrowsOnInvalidType() {
        final String dataPath = "/0/x2";
        var ex = assertThrows(IllegalArgumentException.class, () -> fromPath(dataPath));
        assertEquals("Invalid code in blob path '/0/x2'", ex.getMessage());
    }

    @Test
    void serializeWorks() throws IOException {
        final var buffer = mock(ByteBuffer.class);

        subject.serialize(buffer);

        verify(buffer).put((byte) FILE_DATA.ordinal());
        verify(buffer).putInt(entityNum);
    }

    @Test
    void deserializeWorks() throws IOException {
        final var buffer = mock(ByteBuffer.class);

        given(buffer.get()).willReturn((byte) FILE_DATA.ordinal());
        given(buffer.getInt()).willReturn(entityNum);

        VirtualBlobKey blobKey = new VirtualBlobKey();

        blobKey.deserialize(buffer, VirtualBlobKey.CURRENT_VERSION);

        assertEquals(subject.getEntityNumCode(), blobKey.getEntityNumCode());
        assertEquals(subject.getType(), blobKey.getType());
    }

    @Test
    void merkleMethodsWork() {
        assertEquals(VirtualBlobKey.CURRENT_VERSION, subject.getVersion());
        assertEquals(VirtualBlobKey.CLASS_ID, subject.getClassId());
    }

    @Test
    void gettersWork() {
        assertEquals(2, subject.getEntityNumCode());
        assertEquals(FILE_DATA, subject.getType());
        assertEquals(BYTES_IN_SERIALIZED_FORM, VirtualBlobKey.sizeInBytes());
    }

    @Test
    void deserializeUsingSerializableDataInputStreamWorks() throws IOException {
        final var fin = mock(SerializableDataInputStream.class);

        given(fin.readByte()).willReturn((byte) FILE_DATA.ordinal());
        given(fin.readInt()).willReturn(entityNum);

        VirtualBlobKey blobKey = new VirtualBlobKey();

        blobKey.deserialize(fin, VirtualBlobKey.CURRENT_VERSION);

        assertEquals(subject.getEntityNumCode(), blobKey.getEntityNumCode());
        assertEquals(subject.getType(), blobKey.getType());
    }

    @Test
    void serializeUsingSerializableDataOutputStreamWorks() throws IOException {
        final var fOut = mock(SerializableDataOutputStream.class);

        subject.serialize(fOut);

        verify(fOut).writeByte((byte) FILE_DATA.ordinal());
        verify(fOut).writeInt(entityNum);
    }
}
