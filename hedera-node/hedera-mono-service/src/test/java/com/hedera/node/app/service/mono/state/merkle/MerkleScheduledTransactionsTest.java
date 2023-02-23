/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.mono.state.merkle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.mock;
import static org.mockito.BDDMockito.verify;

import com.hedera.node.app.service.mono.state.adapters.MerkleMapLike;
import com.hedera.node.app.service.mono.state.virtual.EntityNumVirtualKey;
import com.hedera.node.app.service.mono.state.virtual.schedule.ScheduleEqualityVirtualKey;
import com.hedera.node.app.service.mono.state.virtual.schedule.ScheduleEqualityVirtualValue;
import com.hedera.node.app.service.mono.state.virtual.schedule.ScheduleSecondVirtualValue;
import com.hedera.node.app.service.mono.state.virtual.schedule.ScheduleVirtualValue;
import com.hedera.node.app.service.mono.state.virtual.temporal.SecondSinceEpocVirtualKey;
import com.swirlds.merkle.map.MerkleMap;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MerkleScheduledTransactionsTest {
    private static final long currentMinSecond = 23;

    private MerkleScheduledTransactionsState state;

    private MerkleMap<ScheduleEqualityVirtualKey, ScheduleEqualityVirtualValue> byEquality;
    private MerkleMap<SecondSinceEpocVirtualKey, ScheduleSecondVirtualValue> byExpirationSecond;
    private MerkleMap<EntityNumVirtualKey, ScheduleVirtualValue> byId;

    private MerkleScheduledTransactions subject;

    @BeforeEach
    void setup() {
        byEquality = mock(MerkleMap.class);
        given(byEquality.copy()).willReturn(byEquality);

        byExpirationSecond = mock(MerkleMap.class);
        given(byExpirationSecond.copy()).willReturn(byExpirationSecond);

        byId = mock(MerkleMap.class);
        given(byId.copy()).willReturn(byId);

        state = mock(MerkleScheduledTransactionsState.class);
        given(state.currentMinSecond()).willReturn(currentMinSecond);
        given(state.copy()).willReturn(state);
        given(state.toString()).willReturn("MerkleScheduledTransactionsState");

        subject = new MerkleScheduledTransactions(List.of(state, byId, byExpirationSecond, byEquality));
    }

    @Test
    void equalsIncorporatesRecords() {
        final var otherByExpirationSecond = mock(MerkleMap.class);

        final var otherSubject =
                new MerkleScheduledTransactions(List.of(state, byId, otherByExpirationSecond, byEquality));

        assertNotEquals(otherSubject, subject);
    }

    @Test
    void returnsExpectedChildren() {
        assertEquals(byId, subject.byIdInternal());
        assertEquals(byExpirationSecond, subject.byExpirationSecondInternal());
        assertEquals(byEquality, subject.byEqualityInternal());
        assertInstanceOf(MerkleMapLike.class, subject.byId());
        assertInstanceOf(MerkleMapLike.class, subject.byExpirationSecond());
        assertInstanceOf(MerkleMapLike.class, subject.byEquality());
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
        assertEquals(MerkleScheduledTransactions.ChildIndices.NUM_0270_CHILDREN, subject.getMinimumChildCount());
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
        assertEquals(byId, copy.byIdInternal());
        verify(byExpirationSecond).copy();
        assertEquals(byExpirationSecond, copy.byExpirationSecondInternal());
        verify(byEquality).copy();
        assertEquals(byEquality, copy.byEqualityInternal());
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
        assertThrows(UnsupportedOperationException.class, () -> new MerkleScheduledTransactions.ChildIndices());
    }

    @Test
    void delegatesRelease() {
        subject.release();

        verify(byId).release();
        verify(byExpirationSecond).release();
        verify(byEquality).release();
    }

    @Test
    void pendingMigrationSizeWorks() {
        subject = new MerkleScheduledTransactions(5);

        subject.setChild(MerkleScheduledTransactions.ChildIndices.BY_ID, byId);

        assertEquals(5L, subject.getNumSchedules());

        given(byId.size()).willReturn(4);

        assertEquals(4L, subject.getNumSchedules());
    }
}
