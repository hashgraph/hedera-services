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
package com.hedera.services.state.virtual.schedule;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;

import com.google.common.collect.ImmutableMap;
import com.swirlds.common.exceptions.MutabilityException;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.Callable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;

class ScheduleEqualityVirtualValueTest {
    private ScheduleEqualityVirtualValue subject;
    private Map<String, Long> ids =
            ImmutableMap.of(
                    "foo", 1L,
                    "truck", 2L);
    private Map<String, Long> otherIds =
            ImmutableMap.of(
                    "cat", 3L,
                    "dog", 4L);

    @BeforeEach
    void setup() {
        subject = new ScheduleEqualityVirtualValue(ids, new ScheduleEqualityVirtualKey(3L));
    }

    @Test
    void objectContractMet() {
        final var one = new ScheduleEqualityVirtualValue(ids);
        final var two = new ScheduleEqualityVirtualValue(otherIds);
        final var three = new ScheduleEqualityVirtualValue(ids);
        final var four = one.copy();
        final var five = one.asWritable();
        final var six = one.asReadOnly();
        final var twoRef = two;

        assertNotEquals(two, one);
        assertEquals(two, twoRef);
        assertEquals(one, three);
        assertEquals(three, four);
        assertEquals(three, five);
        assertEquals(three, six);

        assertNotEquals(one.hashCode(), two.hashCode());
        assertEquals(two.hashCode(), twoRef.hashCode());
        assertEquals(one.hashCode(), three.hashCode());
        assertEquals(one.hashCode(), four.hashCode());
        assertEquals(one.hashCode(), five.hashCode());
        assertEquals(one.hashCode(), six.hashCode());
        assertDoesNotThrow(subject::release);
        Object nil = null;
        assertNotEquals(one, nil);
        assertNotEquals(one, new Object());

        final var forcedEqualsCheck = one.equals(ids);
        assertFalse(forcedEqualsCheck, "forcing equals on two different class types.");
    }

    @Test
    void serializeWorks() throws IOException {
        final var out = mock(SerializableDataOutputStream.class);
        final var inOrder = inOrder(out);
        subject.serialize(out);

        inOrder.verify(out).writeInt(2);

        inOrder.verify(out).writeInt(3);
        inOrder.verify(out).write("foo".getBytes(StandardCharsets.UTF_8));
        inOrder.verify(out).writeLong(1L);

        inOrder.verify(out).writeInt(5);
        inOrder.verify(out).write("truck".getBytes(StandardCharsets.UTF_8));
        inOrder.verify(out).writeLong(2L);
    }

    @Test
    void deserializeWorks() throws IOException {
        final var in = mock(SerializableDataInputStream.class);
        final var defaultSubject = new ScheduleEqualityVirtualValue();

        given(in.readInt()).willReturn(2, 3, 5);

        doAnswer(invocationOnMock -> null)
                .when(in)
                .readFully(
                        ArgumentMatchers.argThat(
                                b -> {
                                    if (b.length == 3) {
                                        System.arraycopy(
                                                "foo".getBytes(StandardCharsets.UTF_8), 0, b, 0, 3);
                                    } else {
                                        System.arraycopy(
                                                "truck".getBytes(StandardCharsets.UTF_8),
                                                0,
                                                b,
                                                0,
                                                5);
                                    }
                                    return true;
                                }));

        given(in.readLong()).willReturn(1L, 2L, 3L);
        given(in.readByte()).willReturn((byte) 1);

        defaultSubject.deserialize(in, ScheduleEqualityVirtualValue.CURRENT_VERSION);

        assertEquals(subject, defaultSubject);
    }

    @Test
    void serializeWithByteBufferWorks() throws IOException {
        final var buffer = mock(ByteBuffer.class);
        final var inOrder = inOrder(buffer);
        subject.serialize(buffer);

        inOrder.verify(buffer).putInt(2);

        inOrder.verify(buffer).putInt(3);
        inOrder.verify(buffer).put("foo".getBytes(StandardCharsets.UTF_8));
        inOrder.verify(buffer).putLong(1L);

        inOrder.verify(buffer).putInt(5);
        inOrder.verify(buffer).put("truck".getBytes(StandardCharsets.UTF_8));
        inOrder.verify(buffer).putLong(2L);
    }

    @Test
    void deserializeWithByteBufferWorks() throws IOException {
        final var buffer = mock(ByteBuffer.class);
        final var defaultSubject = new ScheduleEqualityVirtualValue();

        given(buffer.getInt()).willReturn(2, 3, 5);
        given(buffer.getLong()).willReturn(1L, 2L);

        doAnswer(invocationOnMock -> null)
                .when(buffer)
                .get(
                        ArgumentMatchers.argThat(
                                b -> {
                                    if (b.length == 3) {
                                        System.arraycopy(
                                                "foo".getBytes(StandardCharsets.UTF_8), 0, b, 0, 3);
                                    } else {
                                        System.arraycopy(
                                                "truck".getBytes(StandardCharsets.UTF_8),
                                                0,
                                                b,
                                                0,
                                                5);
                                    }
                                    return true;
                                }));

        defaultSubject.deserialize(buffer, ScheduleEqualityVirtualValue.CURRENT_VERSION);

        assertEquals(subject, defaultSubject);
    }

    @Test
    void serializeActuallyWorks() throws Exception {
        checkSerialize(
                () -> {
                    final var byteArr = new ByteArrayOutputStream();
                    final var out = new SerializableDataOutputStream(byteArr);
                    subject.serialize(out);

                    var copy = new ScheduleEqualityVirtualValue();
                    copy.deserialize(
                            new SerializableDataInputStream(
                                    new ByteArrayInputStream(byteArr.toByteArray())),
                            ScheduleEqualityVirtualValue.CURRENT_VERSION);

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
                    var copy = new ScheduleEqualityVirtualValue();
                    copy.deserialize(buffer, ScheduleEqualityVirtualValue.CURRENT_VERSION);

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

                    var copy = new ScheduleEqualityVirtualValue();
                    copy.deserialize(
                            new SerializableDataInputStream(
                                    new ByteArrayInputStream(buffer.array())),
                            ScheduleEqualityVirtualValue.CURRENT_VERSION);

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
                    var copy = new ScheduleEqualityVirtualValue();
                    copy.deserialize(buffer, ScheduleEqualityVirtualValue.CURRENT_VERSION);

                    assertEquals(subject, copy);

                    return copy;
                });
    }

    private void checkSerialize(Callable<ScheduleEqualityVirtualValue> check) throws Exception {
        check.call();
        subject = new ScheduleEqualityVirtualValue();
        check.call();
        subject = new ScheduleEqualityVirtualValue(otherIds);
        check.call();
    }

    @Test
    void merkleMethodsWork() {
        assertEquals(ScheduleEqualityVirtualValue.CURRENT_VERSION, subject.getVersion());
        assertEquals(ScheduleEqualityVirtualValue.RUNTIME_CONSTRUCTABLE_ID, subject.getClassId());
    }

    @Test
    void getIdsWork() {
        assertEquals(subject.getIds(), ids);

        final var ids = subject.getIds();
        assertThrows(UnsupportedOperationException.class, () -> ids.put("foo", 2L));
    }

    @Test
    void addWorks() {
        assertEquals(subject.getIds(), ids);

        subject.add("rain", 5L);
        subject.add("rain", 5L);
        subject.add("cloud", 7L);

        assertThrows(IllegalStateException.class, () -> subject.add("rain", 6L));

        assertEquals(4, subject.getIds().size());

        assertEquals(1L, subject.getIds().get("foo"));
        assertEquals(2L, subject.getIds().get("truck"));
        assertEquals(5L, subject.getIds().get("rain"));
        assertEquals(7L, subject.getIds().get("cloud"));

        subject.copy();
        assertThrows(MutabilityException.class, () -> subject.add("ice", 22L));
    }

    @Test
    void removeWorks() {
        assertEquals(subject.getIds(), ids);

        subject.add("rain", 5L);
        subject.add("rain", 5L);
        subject.add("cloud", 7L);

        assertThrows(IllegalStateException.class, () -> subject.remove("rain", 6L));
        assertThrows(IllegalStateException.class, () -> subject.remove("foo", 6L));

        subject.remove("lolz", 5L);
        subject.remove("rain", 5L);
        subject.remove("foo", 1L);

        assertEquals(2, subject.getIds().size());

        assertEquals(2L, subject.getIds().get("truck"));
        assertEquals(7L, subject.getIds().get("cloud"));

        subject.copy();
        assertThrows(MutabilityException.class, () -> subject.remove("cloud", 7L));
    }

    @Test
    void toStringWorks() {
        assertEquals(
                "ScheduleEqualityVirtualValue{ids={foo=1, truck=2}, number=3}", subject.toString());
    }
}
