/*
 * Copyright (C) 2018-2024 Hedera Hashgraph, LLC
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.swirlds.common.crypto.DigestType;
import com.swirlds.common.crypto.Hash;
import com.swirlds.merkledb.files.VirtualHashRecordSerializer;
import com.swirlds.merkledb.serialize.DataItemHeader;
import com.swirlds.virtualmap.datasource.VirtualHashRecord;
import java.io.IOException;
import java.nio.ByteBuffer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class VirtualHashRecordSerializerTest {

    private VirtualHashRecordSerializer subject;

    @BeforeEach
    void setup() {
        subject = new VirtualHashRecordSerializer();
    }

    @Test
    void deserializeEnforcesCurrentVersion() {
        final ByteBuffer someBuffer = ByteBuffer.allocate(1);
        assertThrows(
                IllegalArgumentException.class,
                () -> subject.deserialize(someBuffer, 123),
                "Should have rejected attempt to deserialize data not using the current version");
    }

    @Test
    void serializeEnforcesDefaultDigest() {
        final ByteBuffer bbuf = ByteBuffer.allocate(subject.getSerializedSize());
        final Hash nonDefaultHash = new Hash(DigestType.SHA_512);
        final VirtualHashRecord data = new VirtualHashRecord(1L, nonDefaultHash);
        assertEquals(Long.BYTES, subject.getHeaderSize(), "Header size should be 8 bytes");
        assertEquals(
                56, subject.getSerializedSize(), "Serialized size should be 8 bytes for header + 48 bytes for digest");
        assertEquals(1L, subject.getCurrentDataVersion(), "Current version should be 1");

        assertThrows(
                IllegalArgumentException.class,
                () -> subject.serialize(data, bbuf),
                "Should have rejected attempt to serialize data with non-default hash digest");
    }

    @Test
    void deserializeHappyPath() throws IOException {
        final ByteBuffer bb = ByteBuffer.allocate(subject.getSerializedSize());
        final Hash validHash = new Hash(DigestType.SHA_384);
        final VirtualHashRecord expectedData = new VirtualHashRecord(42L, validHash);
        bb.putLong(42L);
        bb.rewind();

        final DataItemHeader expectedHeader = new DataItemHeader(56, 42L);
        assertEquals(expectedHeader, subject.deserializeHeader(bb), "Deserialized header should match serialized");
        bb.rewind();
        assertEquals(expectedData, subject.deserialize(bb, 1L), "Deserialized data should match serialized");
    }

    @Test
    void testEquals() {
        final VirtualHashRecordSerializer other = new VirtualHashRecordSerializer();
        assertEquals(subject, other, "Should be equal");
        assertEquals(subject, subject, "Should be equal");
        assertFalse(subject.equals(null), "Should not be equal to null");
        assertFalse(other.equals(new Object()), "Should not be equal to Object");
    }

    @Test
    void testHashCode() {
        final VirtualHashRecordSerializer other = new VirtualHashRecordSerializer();
        assertEquals(subject.hashCode(), other.hashCode(), "Should have same hash code");
    }
}
