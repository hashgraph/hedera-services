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

package com.swirlds.merkledb.files;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.hedera.pbj.runtime.io.WritableSequentialData;
import com.hedera.pbj.runtime.io.buffer.BufferedData;
import com.swirlds.common.crypto.DigestType;
import com.swirlds.common.crypto.Hash;
import com.swirlds.virtualmap.datasource.VirtualHashRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class VirtualHashRecordSerializerTest {

    private VirtualHashRecordSerializer subject;

    @BeforeEach
    void setUp() {
        subject = new VirtualHashRecordSerializer();
    }

    @Test
    void serializedSizeTest() {
        final Hash nonDefaultHash = new Hash(DigestType.SHA_512);
        assertEquals(
                VirtualHashRecordSerializer.VARIABLE_DATA_SIZE,
                subject.getSerializedSize(),
                "Serialized size should be variable");
        final VirtualHashRecord data0 = new VirtualHashRecord(0L, nonDefaultHash);
        assertEquals(
                1 + 1 + nonDefaultHash.getBytes().length(), // tag + len + hash
                subject.getSerializedSize(data0),
                "Serialized size should be 0 bytes for path and 66 bytes for hash");
        final VirtualHashRecord data1 = new VirtualHashRecord(1L, nonDefaultHash);
        assertEquals(
                1 + 8 + 1 + 1 + nonDefaultHash.getBytes().length(), // tag + path + tag + len + hash
                subject.getSerializedSize(data1),
                "Serialized size should be 9 bytes for path and 75 bytes for hash");
        assertEquals(1L, subject.getCurrentDataVersion(), "Current version should be 1");
    }

    @Test
    void serializeEnforcesDefaultDigest() {
        final WritableSequentialData out = BufferedData.allocate(128);
        final Hash nonDefaultHash = new Hash(DigestType.SHA_512);
        final VirtualHashRecord data = new VirtualHashRecord(1L, nonDefaultHash);
        assertThrows(IllegalArgumentException.class, () -> subject.serialize(data, out));
    }

    @Test
    void serializedSizeEqualsToEstimation() {
        final BufferedData out = BufferedData.allocate(128);
        final Hash hash = new Hash(DigestType.SHA_384);
        final VirtualHashRecord data0 = new VirtualHashRecord(0L, hash);
        subject.serialize(data0, out);
        assertEquals(subject.getSerializedSize(data0), out.position());
        out.reset();
        final VirtualHashRecord data1 = new VirtualHashRecord(1L, hash);
        subject.serialize(data1, out);
        assertEquals(subject.getSerializedSize(data1), out.position());
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
