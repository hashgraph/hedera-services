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
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;

import com.google.common.collect.ImmutableMap;
import com.hedera.services.state.submerkle.RichInstant;
import com.hedera.services.state.virtual.temporal.SecondSinceEpocVirtualKey;
import com.swirlds.common.exceptions.MutabilityException;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.concurrent.Callable;
import org.eclipse.collections.api.list.primitive.LongList;
import org.eclipse.collections.impl.factory.primitive.LongLists;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ScheduleSecondVirtualValueTest {
    private ScheduleSecondVirtualValue subject;
    private Map<RichInstant, ? extends LongList> ids =
            ImmutableMap.of(
                    new RichInstant(30L, 40), LongLists.immutable.of(80L),
                    new RichInstant(10L, 20), LongLists.immutable.of(50L, 60L, 70L));
    private Map<RichInstant, ? extends LongList> otherIds =
            ImmutableMap.of(
                    new RichInstant(100L, 200), LongLists.immutable.of(500L, 600L, 700L),
                    new RichInstant(300L, 400), LongLists.immutable.of(800L));

    @BeforeEach
    void setup() {
        subject = new ScheduleSecondVirtualValue(ids, new SecondSinceEpocVirtualKey(3L));
    }

    @Test
    void objectContractMet() {
        final var one = new ScheduleSecondVirtualValue(ids);
        final var two = new ScheduleSecondVirtualValue(otherIds);
        final var three = new ScheduleSecondVirtualValue(ids);
        final var four = one.copy();
        final var five = one.asWritable();
        final var six = one.asReadOnly();
        final var twoRef = two;

        assertNotEquals(two, one);
        assertSubjectEquals(two, twoRef);
        assertSubjectEquals(one, three);
        assertSubjectEquals(three, four);
        assertSubjectEquals(three, five);
        assertSubjectEquals(three, six);

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
        inOrder.verify(out).writeLong(50L);
        inOrder.verify(out).writeLong(60L);
        inOrder.verify(out).writeLong(70L);
        inOrder.verify(out).writeLong(10L);
        inOrder.verify(out).writeInt(20);

        inOrder.verify(out).writeInt(1);
        inOrder.verify(out).writeLong(80L);
        inOrder.verify(out).writeLong(30L);
        inOrder.verify(out).writeInt(40);
        inOrder.verify(out).writeLong(3L);

        inOrder.verifyNoMoreInteractions();
    }

    @Test
    void deserializeWorks() throws IOException {
        final var in = mock(SerializableDataInputStream.class);
        final var defaultSubject = new ScheduleSecondVirtualValue();

        given(in.readInt()).willReturn(2, 3, 20, 1, 40);
        given(in.readLong()).willReturn(50L, 60L, 70L, 10L, 80L, 30L, 3L);
        given(in.readByte()).willReturn((byte) 1);

        defaultSubject.deserialize(in, ScheduleEqualityVirtualValue.CURRENT_VERSION);

        assertSubjectEquals(subject, defaultSubject);
    }

    @Test
    void serializeWithByteBufferWorks() throws IOException {
        final var buffer = mock(ByteBuffer.class);
        final var inOrder = inOrder(buffer);
        subject.serialize(buffer);

        inOrder.verify(buffer).putInt(2);

        inOrder.verify(buffer).putInt(3);
        inOrder.verify(buffer).putLong(50L);
        inOrder.verify(buffer).putLong(60L);
        inOrder.verify(buffer).putLong(70L);
        inOrder.verify(buffer).putLong(10L);
        inOrder.verify(buffer).putInt(20);

        inOrder.verify(buffer).putInt(1);
        inOrder.verify(buffer).putLong(80L);
        inOrder.verify(buffer).putLong(30L);
        inOrder.verify(buffer).putInt(40);
        inOrder.verify(buffer).putLong(3L);

        inOrder.verifyNoMoreInteractions();
    }

    @Test
    void deserializeWithByteBufferWorks() throws IOException {
        final var buffer = mock(ByteBuffer.class);
        final var defaultSubject = new ScheduleSecondVirtualValue();

        given(buffer.getInt()).willReturn(2, 3, 20, 1, 40);
        given(buffer.get()).willReturn((byte) 1);
        given(buffer.getLong()).willReturn(50L, 60L, 70L, 10L, 80L, 30L, 3L);

        defaultSubject.deserialize(buffer, ScheduleSecondVirtualValue.CURRENT_VERSION);

        assertSubjectEquals(subject, defaultSubject);
    }

    @Test
    void serializeActuallyWorks() throws Exception {
        checkSerialize(
                () -> {
                    final var byteArr = new ByteArrayOutputStream();
                    final var out = new SerializableDataOutputStream(byteArr);
                    subject.serialize(out);

                    var copy = new ScheduleSecondVirtualValue();
                    copy.deserialize(
                            new SerializableDataInputStream(
                                    new ByteArrayInputStream(byteArr.toByteArray())),
                            ScheduleSecondVirtualValue.CURRENT_VERSION);

                    assertSubjectEquals(subject, copy);

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
                    var copy = new ScheduleSecondVirtualValue();
                    copy.deserialize(buffer, ScheduleSecondVirtualValue.CURRENT_VERSION);

                    assertSubjectEquals(subject, copy);

                    return copy;
                });
    }

    @Test
    void serializeActuallyWithMixedWorksBytesFirst() throws Exception {
        checkSerialize(
                () -> {
                    final var buffer = ByteBuffer.allocate(100000);
                    subject.serialize(buffer);

                    var copy = new ScheduleSecondVirtualValue();
                    copy.deserialize(
                            new SerializableDataInputStream(
                                    new ByteArrayInputStream(buffer.array())),
                            ScheduleSecondVirtualValue.CURRENT_VERSION);

                    assertSubjectEquals(subject, copy);

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
                    var copy = new ScheduleSecondVirtualValue();
                    copy.deserialize(buffer, ScheduleSecondVirtualValue.CURRENT_VERSION);

                    assertSubjectEquals(subject, copy);

                    return copy;
                });
    }

    private void checkSerialize(Callable<ScheduleSecondVirtualValue> check) throws Exception {
        check.call();
        subject = new ScheduleSecondVirtualValue();
        check.call();
        subject = new ScheduleSecondVirtualValue(otherIds);
        check.call();
        subject = new ScheduleSecondVirtualValue(otherIds, new SecondSinceEpocVirtualKey(3L));
        check.call();
    }

    @Test
    void merkleMethodsWork() {
        assertEquals(ScheduleSecondVirtualValue.CURRENT_VERSION, subject.getVersion());
        assertEquals(ScheduleSecondVirtualValue.RUNTIME_CONSTRUCTABLE_ID, subject.getClassId());
    }

    @Test
    void getIdsWork() {
        assertEquals(subject.getIds(), ids);

        final var ids = subject.getIds();
        final var instant = new RichInstant(10000, 100);
        final var longs = LongLists.immutable.of(2000L);
        assertThrows(UnsupportedOperationException.class, () -> ids.put(instant, longs));
    }

    @Test
    void addWorks() {
        assertEquals(subject.getIds(), ids);

        subject.add(new RichInstant(10L, 20), LongLists.immutable.of(20L, 6000L));
        subject.add(new RichInstant(10L, 21), LongLists.immutable.of(9020L));
        subject.add(new RichInstant(10L, 21), LongLists.immutable.of(9120L));
        subject.add(new RichInstant(0L, 20), LongLists.immutable.of(9030L));
        subject.add(new RichInstant(200L, 20), LongLists.immutable.of(9030L));

        assertEquals(5, subject.getIds().size());

        assertEquals(
                "ScheduleSecondVirtualValue{ids={"
                        + "RichInstant{seconds=0, nanos=20}=[9030], "
                        + "RichInstant{seconds=10, nanos=20}=[50, 60, 70, 20, 6000], "
                        + "RichInstant{seconds=10, nanos=21}=[9020, 9120], "
                        + "RichInstant{seconds=30, nanos=40}=[80], "
                        + "RichInstant{seconds=200, nanos=20}=[9030]}, number=3}",
                subject.toString());

        subject.copy();
        final var instant = new RichInstant(210L, 20);
        final var longs = LongLists.immutable.of(9180L);
        assertThrows(MutabilityException.class, () -> subject.add(instant, longs));
    }

    @Test
    void removeIdWorks() {
        assertEquals(subject.getIds(), ids);

        subject.add(new RichInstant(10L, 20), LongLists.immutable.of(20L, 6000L));
        subject.add(new RichInstant(10L, 21), LongLists.immutable.of(9020L));
        subject.add(new RichInstant(10L, 21), LongLists.immutable.of(9120L));
        subject.add(new RichInstant(0L, 20), LongLists.immutable.of(9030L));
        subject.add(new RichInstant(200L, 20), LongLists.immutable.of(9030L));

        assertEquals(5, subject.getIds().size());

        subject.removeId(new RichInstant(10L, 15), 9020L);
        subject.removeId(new RichInstant(10L, 21), 9020L);
        subject.removeId(new RichInstant(10L, 20), 9120L);
        subject.removeId(new RichInstant(10L, 20), 50L);
        subject.removeId(new RichInstant(10L, 21), 9120L);

        assertEquals(
                "ScheduleSecondVirtualValue{ids={"
                        + "RichInstant{seconds=0, nanos=20}=[9030], "
                        + "RichInstant{seconds=10, nanos=20}=[60, 70, 20, 6000], "
                        + "RichInstant{seconds=30, nanos=40}=[80], "
                        + "RichInstant{seconds=200, nanos=20}=[9030]}, number=3}",
                subject.toString());

        subject.copy();

        final var instant = new RichInstant(10L, 20);
        assertThrows(MutabilityException.class, () -> subject.removeId(instant, 6000L));
    }

    @Test
    void toStringWorks() {
        assertEquals(
                "ScheduleSecondVirtualValue{ids={RichInstant{seconds=10, nanos=20}=[50, 60, 70], "
                        + "RichInstant{seconds=30, nanos=40}=[80]}, number=3}",
                subject.toString());
    }

    private static void assertSubjectEquals(
            ScheduleSecondVirtualValue subject, ScheduleSecondVirtualValue value) {
        assertEquals(subject, value);
    }
}
