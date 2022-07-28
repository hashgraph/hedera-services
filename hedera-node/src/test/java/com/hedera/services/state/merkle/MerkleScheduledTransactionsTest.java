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
package com.hedera.services.state.merkle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.mock;
import static org.mockito.BDDMockito.verify;

import com.hedera.services.state.merkle.MerkleScheduledTransactions.ChildIndices;
import com.hedera.services.state.virtual.EntityNumVirtualKey;
import com.hedera.services.state.virtual.schedule.ScheduleEqualityVirtualKey;
import com.hedera.services.state.virtual.schedule.ScheduleEqualityVirtualValue;
import com.hedera.services.state.virtual.schedule.ScheduleSecondVirtualValue;
import com.hedera.services.state.virtual.schedule.ScheduleVirtualValue;
import com.hedera.services.state.virtual.temporal.SecondSinceEpocVirtualKey;
import com.swirlds.merkle.map.MerkleMap;
import com.swirlds.virtualmap.VirtualMap;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MerkleScheduledTransactionsTest {
    private static final long currentMinSecond = 23;

    private MerkleScheduledTransactionsState state;

    private VirtualMap<ScheduleEqualityVirtualKey, ScheduleEqualityVirtualValue> byEquality;
    private VirtualMap<SecondSinceEpocVirtualKey, ScheduleSecondVirtualValue> byExpirationSecond;
    private VirtualMap<EntityNumVirtualKey, ScheduleVirtualValue> byId;

    private MerkleScheduledTransactions subject;

    @BeforeEach
    void setup() {
        byEquality = mock(VirtualMap.class);
        given(byEquality.copy()).willReturn(byEquality);

        byExpirationSecond = mock(VirtualMap.class);
        given(byExpirationSecond.copy()).willReturn(byExpirationSecond);

        byId = mock(VirtualMap.class);
        given(byId.copy()).willReturn(byId);

        state = mock(MerkleScheduledTransactionsState.class);
        given(state.currentMinSecond()).willReturn(currentMinSecond);
        given(state.copy()).willReturn(state);
        given(state.toString()).willReturn("MerkleScheduledTransactionsState");

        subject =
                new MerkleScheduledTransactions(
                        List.of(state, byId, byExpirationSecond, byEquality));
    }

    @Test
    void equalsIncorporatesRecords() {
        final var otherByExpirationSecond = mock(VirtualMap.class);

        final var otherSubject =
                new MerkleScheduledTransactions(
                        List.of(state, byId, otherByExpirationSecond, byEquality));

        assertNotEquals(otherSubject, subject);
    }

    @Test
    void returnsExpectedChildren() {
        assertEquals(byId, subject.byId());
        assertEquals(byExpirationSecond, subject.byExpirationSecond());
        assertEquals(byEquality, subject.byEquality());
        assertEquals(state, subject.state());
    }

    @Test
    @SuppressWarnings("unchecked")
    void returnsExpectedCurrentMinSecond() {
        assertSame(currentMinSecond, subject.getCurrentMinSecond());
    }

    @Test
    void immutableMerkleScheduledTransactionsThrowsIse() {
        MerkleScheduledTransactions.stackDump = () -> {};
        final var original = new MerkleScheduledTransactions();

        original.copy();

        assertThrows(IllegalStateException.class, () -> original.copy());

        MerkleScheduledTransactions.stackDump = Thread::dumpStack;
    }

    @Test
    void merkleMethodsWork() {
        assertEquals(
                MerkleScheduledTransactions.ChildIndices.NUM_0270_CHILDREN,
                subject.getMinimumChildCount());
        assertEquals(MerkleScheduledTransactions.CURRENT_VERSION, subject.getVersion());
        assertEquals(MerkleScheduledTransactions.RUNTIME_CONSTRUCTABLE_ID, subject.getClassId());
        assertFalse(subject.isLeaf());
    }

    @Test
    void toStringWorks() {
        assertEquals(
                "MerkleScheduledTransactions{state=MerkleScheduledTransactionsState, "
                        + "# schedules=0, # seconds=0, # equalities=0}",
                subject.toString());

        subject.setChild(ChildIndices.BY_ID, new MerkleMap<>());
        subject.setChild(ChildIndices.BY_EQUALITY, new MerkleMap<>());
        subject.setChild(ChildIndices.BY_EXPIRATION_SECOND, new MerkleMap<>());

        assertEquals(
                "MerkleScheduledTransactions{state=MerkleScheduledTransactionsState, "
                        + "# schedules=0, # seconds=0, # equalities=0}",
                subject.toString());

        subject.setChild(ChildIndices.BY_ID, new MerkleScheduledTransactionsState());
        subject.setChild(ChildIndices.BY_EQUALITY, new MerkleScheduledTransactionsState());
        subject.setChild(ChildIndices.BY_EXPIRATION_SECOND, new MerkleScheduledTransactionsState());

        assertEquals(
                "MerkleScheduledTransactions{state=MerkleScheduledTransactionsState, "
                        + "# schedules=0, # seconds=0, # equalities=0}",
                subject.toString());
    }

    @Test
    void gettersDelegate() {
        // expect:
        assertEquals(state.currentMinSecond(), subject.getCurrentMinSecond());
    }

    @Test
    void settersDelegate() {
        subject.setCurrentMinSecond(2);
        verify(state).setCurrentMinSecond(2);
    }

    @Test
    void copyConstructorFastCopiesMutableFcqs() {
        given(byId.isImmutable()).willReturn(false);

        final var copy = subject.copy();

        verify(state).copy();
        assertEquals(state, copy.state());
        verify(byId).copy();
        assertEquals(byId, copy.byId());
        verify(byExpirationSecond).copy();
        assertEquals(byExpirationSecond, copy.byExpirationSecond());
        verify(byEquality).copy();
        assertEquals(byEquality, copy.byEquality());
    }

    @Test
    void isMutableAfterCopy() {
        subject.copy();

        assertTrue(subject.isImmutable());
    }

    @Test
    void equalsWorksWithExtremes() {
        final var sameButDifferent = subject;
        assertEquals(subject, sameButDifferent);
        assertNotEquals(null, subject);
        assertNotEquals(subject, new Object());
    }

    @Test
    void originalIsMutable() {
        assertFalse(subject.isImmutable());
    }

    @Test
    void childIndicesConstructorThrows() {
        assertThrows(
                UnsupportedOperationException.class,
                () -> new MerkleScheduledTransactions.ChildIndices());
    }

    @Test
    void delegatesRelease() {
        subject.release();

        verify(byId).release();
        verify(byExpirationSecond).release();
        verify(byEquality).release();
    }

    @Test
    void byIdMigratesCorrectly() {

        var map = new MerkleMap<EntityNumVirtualKey, ScheduleVirtualValue>();

        var key1 = new EntityNumVirtualKey(1);
        var value1 = new ScheduleVirtualValue();
        value1.setKey(key1);
        map.put(key1, value1);

        var key2 = new EntityNumVirtualKey(2);
        var value2 = new ScheduleVirtualValue();
        value2.setKey(key2);
        map.put(key2, value2);

        subject.setChild(MerkleScheduledTransactions.ChildIndices.BY_ID, map);

        subject.setImmutable(true);
        assertThrows(IllegalStateException.class, () -> subject.do0230MigrationIfNeeded());
        assertThrows(IllegalStateException.class, () -> subject.byId());
        subject.setImmutable(false);

        assertEquals(map.get(key1).getKey(), key1);
        assertSame(map.get(key1), value1);
        assertEquals(map.get(key2).getKey(), key2);
        assertSame(map.get(key2), value2);

        subject.do0230MigrationIfNeeded();
        subject.setImmutable(true);

        assertEquals(subject.byId().get(key1).getKey(), key1);
        assertNotSame(subject.byId().get(key1), value1);
        assertEquals(subject.byId().get(key2).getKey(), key2);
        assertNotSame(subject.byId().get(key2), value2);

        subject.setImmutable(false);
        var map2 = new MerkleMap<EntityNumVirtualKey, ScheduleVirtualValue>();
        value1 = value1.asWritable();
        value2 = value2.asWritable();
        map2.put(key1, value1);
        map2.put(key2, value2);
        subject.setChild(MerkleScheduledTransactions.ChildIndices.BY_ID, map2);

        assertEquals(map2.get(key1).getKey(), key1);
        assertSame(map2.get(key1), value1);
        assertEquals(map2.get(key2).getKey(), key2);
        assertSame(map2.get(key2), value2);

        assertEquals(subject.byId().get(key1).getKey(), key1);
        assertNotSame(subject.byId().get(key1), value1);
        assertEquals(subject.byId().get(key2).getKey(), key2);
        assertNotSame(subject.byId().get(key2), value2);
    }

    @Test
    void byExpirationSecondMigratesCorrectly() {

        var map = new MerkleMap<SecondSinceEpocVirtualKey, ScheduleSecondVirtualValue>();

        var key1 = new SecondSinceEpocVirtualKey(1);
        var value1 = new ScheduleSecondVirtualValue();
        value1.setKey(key1);
        map.put(key1, value1);

        var key2 = new SecondSinceEpocVirtualKey(2);
        var value2 = new ScheduleSecondVirtualValue();
        value2.setKey(key2);
        map.put(key2, value2);

        subject.setChild(MerkleScheduledTransactions.ChildIndices.BY_EXPIRATION_SECOND, map);

        subject.setImmutable(true);
        assertThrows(IllegalStateException.class, () -> subject.do0230MigrationIfNeeded());
        assertThrows(IllegalStateException.class, () -> subject.byExpirationSecond());
        subject.setImmutable(false);

        assertEquals(map.get(key1).getKey(), key1);
        assertSame(map.get(key1), value1);
        assertEquals(map.get(key2).getKey(), key2);
        assertSame(map.get(key2), value2);

        subject.do0230MigrationIfNeeded();
        subject.setImmutable(true);

        assertEquals(subject.byExpirationSecond().get(key1).getKey(), key1);
        assertNotSame(subject.byExpirationSecond().get(key1), value1);
        assertEquals(subject.byExpirationSecond().get(key2).getKey(), key2);
        assertNotSame(subject.byExpirationSecond().get(key2), value2);

        subject.setImmutable(false);
        var map2 = new MerkleMap<SecondSinceEpocVirtualKey, ScheduleSecondVirtualValue>();
        value1 = value1.asWritable();
        value2 = value2.asWritable();
        map2.put(key1, value1);
        map2.put(key2, value2);
        subject.setChild(MerkleScheduledTransactions.ChildIndices.BY_EXPIRATION_SECOND, map2);

        assertEquals(map2.get(key1).getKey(), key1);
        assertSame(map2.get(key1), value1);
        assertEquals(map2.get(key2).getKey(), key2);
        assertSame(map2.get(key2), value2);

        assertEquals(subject.byExpirationSecond().get(key1).getKey(), key1);
        assertNotSame(subject.byExpirationSecond().get(key1), value1);
        assertEquals(subject.byExpirationSecond().get(key2).getKey(), key2);
        assertNotSame(subject.byExpirationSecond().get(key2), value2);
    }

    @Test
    void byEqualityMigratesCorrectly() {

        var map = new MerkleMap<ScheduleEqualityVirtualKey, ScheduleEqualityVirtualValue>();

        var key1 = new ScheduleEqualityVirtualKey(1);
        var value1 = new ScheduleEqualityVirtualValue();
        value1.setKey(key1);
        map.put(key1, value1);

        var key2 = new ScheduleEqualityVirtualKey(2);
        var value2 = new ScheduleEqualityVirtualValue();
        value2.setKey(key2);
        map.put(key2, value2);

        subject.setChild(MerkleScheduledTransactions.ChildIndices.BY_EQUALITY, map);

        subject.setImmutable(true);
        assertThrows(IllegalStateException.class, () -> subject.do0230MigrationIfNeeded());
        assertThrows(IllegalStateException.class, () -> subject.byEquality());
        subject.setImmutable(false);

        assertEquals(map.get(key1).getKey(), key1);
        assertSame(map.get(key1), value1);
        assertEquals(map.get(key2).getKey(), key2);
        assertSame(map.get(key2), value2);

        subject.do0230MigrationIfNeeded();
        subject.setImmutable(true);

        assertEquals(subject.byEquality().get(key1).getKey(), key1);
        assertNotSame(subject.byEquality().get(key1), value1);
        assertEquals(subject.byEquality().get(key2).getKey(), key2);
        assertNotSame(subject.byEquality().get(key2), value2);

        subject.setImmutable(false);
        var map2 = new MerkleMap<ScheduleEqualityVirtualKey, ScheduleEqualityVirtualValue>();
        value1 = value1.asWritable();
        value2 = value2.asWritable();
        map2.put(key1, value1);
        map2.put(key2, value2);
        subject.setChild(MerkleScheduledTransactions.ChildIndices.BY_EQUALITY, map2);

        assertEquals(map2.get(key1).getKey(), key1);
        assertSame(map2.get(key1), value1);
        assertEquals(map2.get(key2).getKey(), key2);
        assertSame(map2.get(key2), value2);

        assertEquals(subject.byEquality().get(key1).getKey(), key1);
        assertNotSame(subject.byEquality().get(key1), value1);
        assertEquals(subject.byEquality().get(key2).getKey(), key2);
        assertNotSame(subject.byEquality().get(key2), value2);
    }
}
