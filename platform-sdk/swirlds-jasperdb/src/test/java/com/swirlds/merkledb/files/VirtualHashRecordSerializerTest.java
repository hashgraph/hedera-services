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

package com.swirlds.merkledb.files;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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
    void setUp() {
        subject = new VirtualHashRecordSerializer();
    }

    @Test
    void serializeEnforcesDefaultDigest() {
        final ByteBuffer bbuf = mock(ByteBuffer.class);
        final Hash nonDefaultHash = new Hash(DigestType.SHA_512);
        final VirtualHashRecord data = new VirtualHashRecord(1L, nonDefaultHash);
        assertEquals(
                56, subject.getSerializedSize(), "Serialized size should be 8 bytes for header + 48 bytes for digest");
        assertEquals(1L, subject.getCurrentDataVersion(), "Current version should be 1");
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
