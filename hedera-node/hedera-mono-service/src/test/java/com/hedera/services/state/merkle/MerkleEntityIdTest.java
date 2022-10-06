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
package com.hedera.services.state.merkle;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.inOrder;
import static org.mockito.BDDMockito.mock;

import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import java.io.IOException;
import org.junit.jupiter.api.Test;

class MerkleEntityIdTest {
    private static final long shard = 13;
    private static final long realm = 25;
    private static final long num = 7;

    private static final MerkleEntityId subject = new MerkleEntityId(shard, realm, num);

    @Test
    void objectContractMet() {
        final var one = new MerkleEntityId();
        final var two = new MerkleEntityId(1, 2, 3);
        final var three = new MerkleEntityId();
        final var twoRef = two;

        three.setShard(1);
        three.setRealm(2);
        three.setNum(3);

        final var equalsForcedCallResult = one.equals(null);
        assertFalse(equalsForcedCallResult);
        assertNotEquals(one, new Object());
        assertEquals(two, twoRef);
        assertNotEquals(two, one);
        assertEquals(two, three);

        assertNotEquals(one.hashCode(), two.hashCode());
        assertEquals(two.hashCode(), three.hashCode());
    }

    @Test
    void gettersTest() {
        final var subject = new MerkleEntityId();

        subject.setShard(1);
        subject.setRealm(2);
        subject.setNum(3);

        assertEquals(1, subject.getShard());
        assertEquals(2, subject.getRealm());
        assertEquals(3, subject.getNum());
    }

    @Test
    void merkleMethodsWork() {
        assertEquals(MerkleEntityId.MERKLE_VERSION, subject.getVersion());
        assertEquals(MerkleEntityId.RUNTIME_CONSTRUCTABLE_ID, subject.getClassId());
        assertTrue(subject.isLeaf());
    }

    @Test
    void serializeWorks() throws IOException {
        final var out = mock(SerializableDataOutputStream.class);
        final var inOrder = inOrder(out);

        subject.serialize(out);

        inOrder.verify(out).writeLong(shard);
        inOrder.verify(out).writeLong(realm);
        inOrder.verify(out).writeLong(num);
    }

    @Test
    void deserializeWorks() throws IOException {
        final var in = mock(SerializableDataInputStream.class);
        final var defaultSubject = new MerkleEntityId();
        given(in.readLong()).willReturn(shard, realm, num);

        defaultSubject.deserialize(in, MerkleEntityId.MERKLE_VERSION);

        assertEquals(subject, defaultSubject);
    }

    @Test
    void toStringWorks() {
        assertEquals(
                "MerkleEntityId{shard=" + shard + ", realm=" + realm + ", entity=" + num + "}",
                subject.toString());
    }

    @Test
    void copyWorks() {
        final var subjectCopy = subject.copy();

        assertNotSame(subject, subjectCopy);
        assertEquals(subject, subjectCopy);
        assertTrue(subject.isImmutable());
    }

    @Test
    void deleteIsNoop() {
        assertDoesNotThrow(subject::release);
    }
}
