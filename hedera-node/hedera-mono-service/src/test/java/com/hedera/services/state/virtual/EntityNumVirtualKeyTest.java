/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.Callable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class EntityNumVirtualKeyTest {
    private final long longKey = 2;

    private EntityNumVirtualKey subject;

    @BeforeEach
    void setup() {
        subject = new EntityNumVirtualKey(longKey);
    }

    @Test
    void ordersSameAsExpected() {
        final var sameButDifferent = subject;
        assertEquals(0, subject.compareTo(sameButDifferent));
    }

    @Test
    void orderPrioritizesEntityNum() {
        final var smallerEntityNum = new EntityNumVirtualKey(longKey - 1);
        assertEquals(+1, subject.compareTo(smallerEntityNum));
    }

    @Test
    void objectContractMet() {
        final var one = new EntityNumVirtualKey(longKey);
        final var two = new EntityNumVirtualKey(longKey);
        final var twoRef = two;
        final var three = new EntityNumVirtualKey(longKey);
        final var otherEntityNum = 3;
        final var four = new EntityNumVirtualKey(otherEntityNum);

        assertEquals(two, one);
        assertEquals(two, twoRef);
        assertEquals(two, three);

        assertEquals(one.hashCode(), two.hashCode());
        assertEquals(two.hashCode(), three.hashCode());
        assertNotEquals(two.hashCode(), four.hashCode());
        assertNotEquals(one, four);
        assertNotNull(one);

        Object nil = null;
        assertNotEquals(one, nil);
        assertNotEquals(one, new Object());

        final var forcedEqualsCheck = one.equals(longKey);
        assertFalse(forcedEqualsCheck, "forcing equals on two different class types.");
    }

    @Test
    void serializeWorks() throws IOException {
        final var buffer = mock(ByteBuffer.class);

        subject.serialize(buffer);

        verify(buffer).putLong(longKey);
    }

    @Test
    void deserializeWorks() throws IOException {
        final var buffer = mock(ByteBuffer.class);

        given(buffer.getLong()).willReturn(longKey);

        EntityNumVirtualKey key = new EntityNumVirtualKey();

        key.deserialize(buffer, EntityNumVirtualKey.CURRENT_VERSION);

        assertEquals(subject.getKeyAsLong(), key.getKeyAsLong());
    }

    @Test
    void serializeActuallyWorks() throws Exception {
        checkSerialize(
                () -> {
                    final var byteArr = new ByteArrayOutputStream();
                    final var out = new SerializableDataOutputStream(byteArr);
                    subject.serialize(out);

                    var copy = new EntityNumVirtualKey();
                    copy.deserialize(
                            new SerializableDataInputStream(
                                    new ByteArrayInputStream(byteArr.toByteArray())),
                            EntityNumVirtualKey.CURRENT_VERSION);

                    assertEquals(subject, copy);

                    return copy;
                });
    }

    @Test
    void serializeActuallyWithByteBufferWorks() throws Exception {
        checkSerialize(
                () -> {
                    final var buffer = ByteBuffer.allocate(100000);
                    subject.serialize(buffer);
                    buffer.rewind();
                    var copy = new EntityNumVirtualKey();
                    copy.deserialize(buffer, EntityNumVirtualKey.CURRENT_VERSION);

                    assertEquals(subject, copy);

                    return copy;
                });
    }

    @Test
    void serializeActuallyWithMixedWorksBytesFirst() throws Exception {
        checkSerialize(
                () -> {
                    final var buffer = ByteBuffer.allocate(100000);
                    subject.serialize(buffer);

                    var copy = new EntityNumVirtualKey();
                    copy.deserialize(
                            new SerializableDataInputStream(
                                    new ByteArrayInputStream(buffer.array())),
                            EntityNumVirtualKey.CURRENT_VERSION);

                    assertEquals(subject, copy);

                    return copy;
                });
    }

    @Test
    void serializeActuallyWithMixedWorksBytesSecond() throws Exception {
        checkSerialize(
                () -> {
                    final var byteArr = new ByteArrayOutputStream();
                    final var out = new SerializableDataOutputStream(byteArr);
                    subject.serialize(out);

                    final var buffer = ByteBuffer.wrap(byteArr.toByteArray());
                    var copy = new EntityNumVirtualKey();
                    copy.deserialize(buffer, EntityNumVirtualKey.CURRENT_VERSION);

                    assertEquals(subject, copy);

                    return copy;
                });
    }

    private void checkSerialize(Callable<EntityNumVirtualKey> check) throws Exception {
        check.call();
    }

    @Test
    void merkleMethodsWork() {
        assertEquals(EntityNumVirtualKey.CURRENT_VERSION, subject.getVersion());
        assertEquals(EntityNumVirtualKey.CLASS_ID, subject.getClassId());
    }

    @Test
    void gettersWork() {
        assertEquals(longKey, subject.getKeyAsLong());
    }

    @Test
    void asEntityNumWorks() {
        assertEquals(subject.asEntityNum().longValue(), subject.getKeyAsLong());
    }

    @Test
    void toStringWorks() {
        assertEquals("EntityNumVirtualKey{value=2}", subject.toString());
    }

    @Test
    void deserializeUsingSerializableDataInputStreamWorks() throws IOException {
        final var fin = mock(SerializableDataInputStream.class);

        given(fin.readLong()).willReturn(longKey);

        EntityNumVirtualKey key = new EntityNumVirtualKey();

        key.deserialize(fin, EntityNumVirtualKey.CURRENT_VERSION);

        assertEquals(subject.getKeyAsLong(), key.getKeyAsLong());
    }

    @Test
    void serializeUsingSerializableDataOutputStreamWorks() throws IOException {
        final var fOut = mock(SerializableDataOutputStream.class);

        subject.serialize(fOut);

        verify(fOut).writeLong(longKey);
    }
}
