/*
 * Copyright (C) 2018-2023 Hedera Hashgraph, LLC
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

package com.swirlds.merkledb;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.swirlds.common.crypto.DigestType;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.merkledb.files.VirtualInternalRecordSerializer;
import com.swirlds.merkledb.serialize.DataItemHeader;
import com.swirlds.virtualmap.datasource.VirtualInternalRecord;
import java.io.IOException;
import java.nio.ByteBuffer;
import org.junit.jupiter.api.Test;

class VirtualInternalRecordSerializerTest {

    @Test
    void deserializeEnforcesCurrentVersion() {
        final ByteBuffer someBuffer = ByteBuffer.allocate(1);
        final VirtualInternalRecordSerializer subject = new VirtualInternalRecordSerializer();

        assertThrows(
                IllegalArgumentException.class,
                () -> subject.deserialize(someBuffer, 123),
                "Should have rejected attempt to deserialize data not using the current version");
    }

    @Test
    void serializeEnforcesDefaultDigest() {
        final SerializableDataOutputStream fout = mock(SerializableDataOutputStream.class);
        final Hash nonDefaultHash = new Hash(DigestType.SHA_512);
        final VirtualInternalRecordSerializer subject = new VirtualInternalRecordSerializer();
        final VirtualInternalRecord data = new VirtualInternalRecord(1L, nonDefaultHash);
        assertEquals(Long.BYTES, subject.getHeaderSize(), "Header size should be 8 bytes");
        assertEquals(
                56, subject.getSerializedSize(), "Serialized size should be 8 bytes for header + 48 bytes for digest");
        assertEquals(1L, subject.getCurrentDataVersion(), "Current version should be 1");

        assertThrows(
                IllegalArgumentException.class,
                () -> subject.serialize(data, fout),
                "Should have rejected attempt to serialize data with non-default hash digest");
    }

    @Test
    void serializeUnhappyPath() {
        final SerializableDataOutputStream fout = mock(SerializableDataOutputStream.class);
        final VirtualInternalRecord data = new VirtualInternalRecord(42L, new Hash(DigestType.SHA_384));
        final VirtualInternalRecordSerializer subject = new VirtualInternalRecordSerializer();
        final NullPointerException e = assertThrows(
                NullPointerException.class, () -> subject.serialize(data, fout), "Unchecked exceptions not handled");
    }

    @Test
    void deserializeHappyPath() throws IOException {
        final ByteBuffer bb = mock(ByteBuffer.class);
        final Hash validHash = new Hash(DigestType.SHA_384);
        final VirtualInternalRecord expectedData = new VirtualInternalRecord(42L, validHash);
        final VirtualInternalRecordSerializer subject = new VirtualInternalRecordSerializer();
        when(bb.getLong()).thenReturn(42L);
        when(bb.get(any())).thenReturn(bb);
        final DataItemHeader expectedHeader = new DataItemHeader(56, 42L);
        assertEquals(expectedHeader, subject.deserializeHeader(bb), "Deserialized header should match serialized");
        assertEquals(expectedData, subject.deserialize(bb, 1L), "Deserialized data should match serialized");
    }
}
