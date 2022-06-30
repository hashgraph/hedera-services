package com.hedera.services.state.migration;

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

import com.hedera.services.ServicesState;
import com.hedera.services.state.merkle.MerkleSchedule;
import com.hedera.services.state.merkle.MerkleScheduleSerdeTest;
import com.hedera.services.state.merkle.MerkleScheduledTransactions;
import com.hedera.services.state.submerkle.RichInstant;
import com.hedera.services.state.virtual.EntityNumVirtualKey;
import com.hedera.services.state.virtual.schedule.ScheduleEqualityVirtualKey;
import com.hedera.services.state.virtual.schedule.ScheduleVirtualValue;
import com.hedera.services.state.virtual.temporal.SecondSinceEpocVirtualKey;
import com.hedera.services.utils.EntityNum;
import com.hedera.test.utils.SeededPropertySource;
import com.swirlds.merkle.map.MerkleMap;
import org.eclipse.collections.impl.factory.primitive.LongLists;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.SplittableRandom;

import static com.hedera.services.state.migration.LongTermScheduledTransactionsMigration.migrateScheduledTransactions;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class LongTermScheduledTransactionsMigrationTest {
	@Mock
	private ServicesState state;

	@Test
	void migratesAsExpected() {
		MerkleMap<EntityNum, MerkleSchedule> oldSchedules = new MerkleMap<>();

		final SeededPropertySource propertySource = new SeededPropertySource(new SplittableRandom(10));

		var schedule1 = MerkleScheduleSerdeTest.nextSchedule(propertySource, null,
				propertySource.nextSerializedLegacyScheduleCreateTransactionBody());
		var schedule2 = MerkleScheduleSerdeTest.nextSchedule(propertySource, null,
				propertySource.nextSerializedLegacyScheduleCreateTransactionBody());
		var schedule3 = MerkleScheduleSerdeTest.nextSchedule(propertySource, schedule2.expiry(),
				propertySource.nextSerializedLegacyScheduleCreateTransactionBody());
		var schedule4 = MerkleScheduleSerdeTest.nextSchedule(propertySource, null,
				propertySource.nextSerializedLegacyScheduleCreateTransactionBody());
		var schedule5 = MerkleScheduleSerdeTest.nextSchedule(propertySource, null,
				propertySource.nextSerializedLegacyScheduleCreateTransactionBody());

		oldSchedules.put(schedule1.getKey(), schedule1);
		oldSchedules.put(schedule2.getKey(), schedule2);
		oldSchedules.put(schedule3.getKey(), schedule3);
		oldSchedules.put(schedule4.getKey(), schedule4);
		oldSchedules.put(schedule5.getKey(), schedule5);

		var minSecond = Math.min(schedule1.expiry(), Math.min(schedule2.expiry(),
				Math.min(schedule3.expiry(), Math.min(schedule4.expiry(), schedule5.expiry()))));

		given(state.getChild(StateChildIndices.SCHEDULE_TXS)).willReturn(oldSchedules);
		given(state.scheduleTxs()).willReturn(new MerkleScheduledTransactions());

		migrateScheduledTransactions(state, (e, s) -> e.longValue() == schedule5.getKey().longValue() ?
				new ScheduleVirtualValue(schedule4).equalityCheckKey() : s.equalityCheckKey());

		verify(state).setChild(eq(StateChildIndices.SCHEDULE_TXS),
				argThat((MerkleScheduledTransactions newSchedules) -> {

			assertEquals(minSecond, newSchedules.getCurrentMinSecond());

			var newSchedule1 = newSchedules.byId().get(new EntityNumVirtualKey(schedule1.getKey()));
			var newSchedule2 = newSchedules.byId().get(new EntityNumVirtualKey(schedule2.getKey()));
			var newSchedule3 = newSchedules.byId().get(new EntityNumVirtualKey(schedule3.getKey()));
			var newSchedule4 = newSchedules.byId().get(new EntityNumVirtualKey(schedule4.getKey()));
			var newSchedule5 = newSchedules.byId().get(new EntityNumVirtualKey(schedule5.getKey()));

			assertNotNull(newSchedule1);
			assertNotNull(newSchedule2);
			assertNotNull(newSchedule3);
			assertNotNull(newSchedule4);
			assertNotNull(newSchedule5);


			assertEquals(schedule1.bodyBytes(), newSchedule1.bodyBytes());
			assertEquals(schedule2.bodyBytes(), newSchedule2.bodyBytes());
			assertEquals(schedule3.bodyBytes(), newSchedule3.bodyBytes());
			assertEquals(schedule4.bodyBytes(), newSchedule4.bodyBytes());
			assertEquals(schedule5.bodyBytes(), newSchedule5.bodyBytes());


			var newSchedule1ByExpiration = newSchedules.byExpirationSecond().get(
					new SecondSinceEpocVirtualKey(schedule1.expiry()));
			var newSchedule2ByExpiration = newSchedules.byExpirationSecond().get(
					new SecondSinceEpocVirtualKey(schedule2.expiry()));
			var newSchedule3ByExpiration = newSchedules.byExpirationSecond().get(
					new SecondSinceEpocVirtualKey(schedule3.expiry()));
			var newSchedule4ByExpiration = newSchedules.byExpirationSecond().get(
					new SecondSinceEpocVirtualKey(schedule4.expiry()));
			var newSchedule5ByExpiration = newSchedules.byExpirationSecond().get(
					new SecondSinceEpocVirtualKey(schedule5.expiry()));

			assertNotNull(newSchedule1ByExpiration);
			assertNotNull(newSchedule2ByExpiration);
			assertNotNull(newSchedule3ByExpiration);
			assertNotNull(newSchedule4ByExpiration);
			assertNotNull(newSchedule5ByExpiration);

			assertEquals(1, newSchedule1ByExpiration.getIds().size());
			assertEquals(1, newSchedule2ByExpiration.getIds().size());
			assertEquals(1, newSchedule3ByExpiration.getIds().size());
			assertEquals(1, newSchedule4ByExpiration.getIds().size());
			assertEquals(1, newSchedule5ByExpiration.getIds().size());

			assertEquals(newSchedule3ByExpiration.getIds(), newSchedule2ByExpiration.getIds());


			assertEquals(newSchedule1ByExpiration.getIds().keySet().stream().findFirst().get(),
					new RichInstant(schedule1.expiry(), 0));
			assertEquals(newSchedule2ByExpiration.getIds().keySet().stream().findFirst().get(),
					new RichInstant(schedule2.expiry(), 0));
			assertEquals(newSchedule3ByExpiration.getIds().keySet().stream().findFirst().get(),
					new RichInstant(schedule3.expiry(), 0));
			assertEquals(newSchedule4ByExpiration.getIds().keySet().stream().findFirst().get(),
					new RichInstant(schedule4.expiry(), 0));
			assertEquals(newSchedule5ByExpiration.getIds().keySet().stream().findFirst().get(),
					new RichInstant(schedule5.expiry(), 0));

			assertEquals(newSchedule1ByExpiration.getIds().values().stream().findFirst().get(),
					LongLists.immutable.with(schedule1.getKey().longValue()));

			assertEquals(2,
					newSchedule2ByExpiration.getIds().values().stream().findFirst().get().size());
			assertTrue(newSchedule2ByExpiration.getIds().values().stream().findFirst().get()
							.containsAll(schedule2.getKey().longValue(), schedule3.getKey().longValue()));

			assertTrue(newSchedule2ByExpiration.getIds().values().stream().findFirst().get().get(0)
					< newSchedule2ByExpiration.getIds().values().stream().findFirst().get().get(1));

			assertEquals(2,
					newSchedule3ByExpiration.getIds().values().stream().findFirst().get().size());
			assertTrue(newSchedule3ByExpiration.getIds().values().stream().findFirst().get()
							.containsAll(schedule2.getKey().longValue(), schedule3.getKey().longValue()));

			assertEquals(newSchedule4ByExpiration.getIds().values().stream().findFirst().get(),
					LongLists.immutable.with(schedule4.getKey().longValue()));
			assertEquals(newSchedule5ByExpiration.getIds().values().stream().findFirst().get(),
					LongLists.immutable.with(schedule5.getKey().longValue()));


			var newSchedule1ByEquality = newSchedules.byEquality().get(
					new ScheduleEqualityVirtualKey(newSchedule1.equalityCheckKey()));
			var newSchedule2ByEquality = newSchedules.byEquality().get(
					new ScheduleEqualityVirtualKey(newSchedule2.equalityCheckKey()));
			var newSchedule3ByEquality = newSchedules.byEquality().get(
					new ScheduleEqualityVirtualKey(newSchedule3.equalityCheckKey()));
			var newSchedule4ByEquality = newSchedules.byEquality().get(
					new ScheduleEqualityVirtualKey(newSchedule4.equalityCheckKey()));
			var newSchedule5ByEquality = newSchedules.byEquality().get(
					new ScheduleEqualityVirtualKey(newSchedule5.equalityCheckKey()));

			assertNotNull(newSchedule1ByEquality);
			assertNotNull(newSchedule2ByEquality);
			assertNotNull(newSchedule3ByEquality);
			assertNotNull(newSchedule4ByEquality);
			assertNull(newSchedule5ByEquality);

			assertEquals(1, newSchedule1ByEquality.getIds().size());
			assertEquals(1, newSchedule2ByEquality.getIds().size());
			assertEquals(1, newSchedule3ByEquality.getIds().size());
			assertEquals(2, newSchedule4ByEquality.getIds().size());

			assertEquals(newSchedule1ByEquality.getIds().keySet().stream().findFirst().get(),
					newSchedule1.equalityCheckValue());
			assertEquals(newSchedule2ByEquality.getIds().keySet().stream().findFirst().get(),
					newSchedule2.equalityCheckValue());
			assertEquals(newSchedule3ByEquality.getIds().keySet().stream().findFirst().get(),
					newSchedule3.equalityCheckValue());

			assertEquals(newSchedule1ByEquality.getIds().values().stream().findFirst().get(),
					schedule1.getKey().longValue());
			assertEquals(newSchedule2ByEquality.getIds().values().stream().findFirst().get(),
					schedule2.getKey().longValue());
			assertEquals(newSchedule3ByEquality.getIds().values().stream().findFirst().get(),
					schedule3.getKey().longValue());

			assertEquals(newSchedule4ByEquality.getIds().get(newSchedule4.equalityCheckValue()),
					schedule4.getKey().longValue());
			assertEquals(newSchedule4ByEquality.getIds().get(newSchedule5.equalityCheckValue()),
					schedule5.getKey().longValue());

			return true;
		}));
	}


	@Test
	void migrateUsesEqualityCheckKey() {
		MerkleMap<EntityNum, MerkleSchedule> oldSchedules = new MerkleMap<>();

		final SeededPropertySource propertySource = new SeededPropertySource(new SplittableRandom(10));

		var schedule1 = MerkleScheduleSerdeTest.nextSchedule(propertySource, null,
				propertySource.nextSerializedLegacyScheduleCreateTransactionBody());

		oldSchedules.put(schedule1.getKey(), schedule1);

		given(state.getChild(StateChildIndices.SCHEDULE_TXS)).willReturn(oldSchedules);
		given(state.scheduleTxs()).willReturn(new MerkleScheduledTransactions());

		migrateScheduledTransactions(state);

		verify(state).setChild(eq(StateChildIndices.SCHEDULE_TXS),
				argThat((MerkleScheduledTransactions newSchedules) -> {

			var newSchedule1 = newSchedules.byId().get(new EntityNumVirtualKey(schedule1.getKey()));

			var newSchedule1ByEquality = newSchedules.byEquality().get(
					new ScheduleEqualityVirtualKey(newSchedule1.equalityCheckKey()));

			assertNotNull(newSchedule1ByEquality);
			assertEquals(1, newSchedule1ByEquality.getIds().size());
			assertEquals(newSchedule1ByEquality.getIds().keySet().stream().findFirst().get(),
					newSchedule1.equalityCheckValue());
			assertEquals(newSchedule1ByEquality.getIds().values().stream().findFirst().get(),
					schedule1.getKey().longValue());

			return true;
		}));

	}
	@Test
	void migrateSkippedIfAlreadyDone() {
		given(state.getChild(StateChildIndices.SCHEDULE_TXS)).willReturn(new MerkleScheduledTransactions());

		migrateScheduledTransactions(state);

		verify(state, never()).setChild(anyInt(), any());
	}

	@Test
	void constructorThrows() {
		assertThrows(UnsupportedOperationException.class, () -> new LongTermScheduledTransactionsMigration());
	}
}
