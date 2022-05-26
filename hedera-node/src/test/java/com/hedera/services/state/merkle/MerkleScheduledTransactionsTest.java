package com.hedera.services.state.merkle;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2022 Hedera Hashgraph, LLC
 * ​
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
 * ‍
 */

import com.hedera.services.state.virtual.EntityNumVirtualKey;
import com.hedera.services.state.virtual.schedule.ScheduleEqualityVirtualKey;
import com.hedera.services.state.virtual.schedule.ScheduleEqualityVirtualValue;
import com.hedera.services.state.virtual.schedule.ScheduleSecondVirtualValue;
import com.hedera.services.state.virtual.schedule.ScheduleVirtualValue;
import com.hedera.services.state.virtual.temporal.SecondSinceEpocVirtualKey;
import com.swirlds.virtualmap.VirtualMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.mock;
import static org.mockito.BDDMockito.verify;

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

		subject = new MerkleScheduledTransactions(List.of(state, byId, byExpirationSecond, byEquality));
	}

	@Test
	void equalsIncorporatesRecords() {
		final var otherByExpirationSecond = mock(VirtualMap.class);

		final var otherSubject = new MerkleScheduledTransactions(List.of(state, byId,
				otherByExpirationSecond, byEquality));

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
		MerkleScheduledTransactions.stackDump = () -> {
		};
		final var original = new MerkleScheduledTransactions();

		original.copy();

		assertThrows(IllegalStateException.class, () -> original.copy());

		MerkleScheduledTransactions.stackDump = Thread::dumpStack;
	}

	@Test
	void merkleMethodsWork() {
		assertEquals(
				MerkleScheduledTransactions.ChildIndices.NUM_0270_CHILDREN,
				subject.getMinimumChildCount(MerkleScheduledTransactions.CURRENT_VERSION));
		assertEquals(MerkleScheduledTransactions.CURRENT_VERSION, subject.getVersion());
		assertEquals(MerkleScheduledTransactions.RUNTIME_CONSTRUCTABLE_ID, subject.getClassId());
		assertFalse(subject.isLeaf());
	}

	@Test
	void toStringWorks() {
		assertEquals(
				"MerkleScheduledTransactions{state=MerkleScheduledTransactionsState, " +
						"# schedules=0, # seconds=0, # equalities=0}",
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
		assertThrows(UnsupportedOperationException.class, () -> new MerkleScheduledTransactions.ChildIndices());
	}

	@Test
	void delegatesDelete() {
		subject.release();

		verify(byId).decrementReferenceCount();
		verify(byExpirationSecond).decrementReferenceCount();
		verify(byEquality).decrementReferenceCount();
	}

	@Test
	void pendingMigrationSizeWorks() {
		subject = new MerkleScheduledTransactions(5);

		subject.setChild(MerkleScheduledTransactions.ChildIndices.BY_ID, byId);

		assertEquals(5L, subject.getNumSchedules());

		given(byId.size()).willReturn(4L);

		assertEquals(4L, subject.getNumSchedules());
	}
}
