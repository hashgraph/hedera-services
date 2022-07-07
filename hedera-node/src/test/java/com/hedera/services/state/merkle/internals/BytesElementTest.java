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
package com.hedera.services.state.merkle.internals;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hedera.services.legacy.proto.utils.CommonUtils;
import com.swirlds.common.crypto.DigestType;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class BytesElementTest {
    private static final byte[] SOME_DATA = "abcdefgh".getBytes(StandardCharsets.UTF_8);

    private BytesElement subject;

    @Test
    void metaAsExpected() {
        subject = new BytesElement();
        assertEquals(1, subject.getVersion());
        assertEquals(0xd1b1fc6b87447a02L, subject.getClassId());
    }

    @Test
    void copyReturnsSelf() {
        subject = new BytesElement(SOME_DATA);
        assertSame(subject, subject.copy());
    }

    @Test
    void canManageHash() {
        subject = new BytesElement(SOME_DATA);

        final var literal = CommonUtils.noThrowSha384HashOf(SOME_DATA);
        final var hash = new Hash(literal, DigestType.SHA_384);

        subject.setHash(hash);

        assertSame(hash, subject.getHash());
    }

    @Test
    void liveFireSerdeWorksWithNonEmpty() throws IOException {
        final var baos = new ByteArrayOutputStream();
        final var dos = new SerializableDataOutputStream(baos);

        subject = new BytesElement(SOME_DATA);

        subject.serialize(dos);
        dos.flush();
        final var bytes = baos.toByteArray();
        final var bais = new ByteArrayInputStream(bytes);
        final var din = new SerializableDataInputStream(bais);

        final var newSubject = new BytesElement();
        newSubject.deserialize(din, 1);

        assertArrayEquals(subject.getData(), newSubject.getData());
    }

    @Test
    @SuppressWarnings("java:S3415")
    void checkEqualityComparisonWorks() {
        subject = new BytesElement(SOME_DATA);
        assertEquals(subject, subject);
        // Note: suppressed warning here because check needs to cover null code-path of equals
        // method.
        assertNotEquals(subject, null);
        assertNotEquals(subject, new Object());
        assertEquals(subject, new BytesElement(SOME_DATA));
        assertNotEquals(subject, new BytesElement("DIFFERENT DATA".getBytes()));
    }

    @Test
    void checkHashCodeDiverse() {
        Set<Integer> hashCodes = new HashSet<>();
        hashCodes.add(new BytesElement("DATA1".getBytes()).hashCode());
        hashCodes.add(new BytesElement("DATA2".getBytes()).hashCode());
        hashCodes.add(new BytesElement("dATA1".getBytes()).hashCode());
        hashCodes.add(new BytesElement(new byte[] {}).hashCode());
        assertTrue(hashCodes.size() >= 3);
    }

    @Test
    void stringContainsBytes() {
        assertTrue(new BytesElement(new byte[] {10, 20, 30}).toString().contains("10"));
        assertTrue(new BytesElement(new byte[] {10, 20, 30}).toString().contains("20"));
        assertTrue(new BytesElement(new byte[] {10, 20, 30}).toString().contains("30"));
    }
}
