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

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import com.hedera.services.ServicesState;
import com.hedera.services.state.merkle.MerkleSchedule;
import com.hedera.services.state.merkle.MerkleScheduledTransactions;
import com.hedera.services.state.virtual.EntityNumVirtualKey;
import com.hedera.services.state.virtual.schedule.ScheduleEqualityVirtualKey;
import com.hedera.services.state.virtual.schedule.ScheduleEqualityVirtualValue;
import com.hedera.services.state.virtual.schedule.ScheduleSecondVirtualValue;
import com.hedera.services.state.virtual.schedule.ScheduleVirtualValue;
import com.hedera.services.state.virtual.temporal.SecondSinceEpocVirtualKey;
import com.hedera.services.utils.EntityNum;
import com.swirlds.merkle.map.MerkleMap;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.collections.impl.list.mutable.primitive.LongArrayList;

import static com.hedera.services.utils.MiscUtils.forEach;

public class LongTermScheduledTransactionsMigration {
	private static final Logger log = LogManager.getLogger(LongTermScheduledTransactionsMigration.class);

	/**
	 * @param initializingState
	 * 		the saved state being migrated during initialization
	 */

	public static void migrateScheduledTransactions(
			final ServicesState initializingState
	) {
		log.info("Migrating long term scheduled transactions");

		final MerkleMap<EntityNum, MerkleSchedule> legacySchedules = initializingState.getChild(StateChildIndices.SCHEDULE_TXS);

		final var newScheduledTxns = new MerkleScheduledTransactions();

		final Map<Character, AtomicInteger> counts = new HashMap<>();
		forEach(legacySchedules, (key, schedule) -> {

			var newScheduleKey = new EntityNumVirtualKey(key.longValue());
			var newSchedule = new ScheduleVirtualValue(schedule);

			newScheduledTxns.byId().put(newScheduleKey, newSchedule);

			var secondKey = new SecondSinceEpocVirtualKey(newSchedule.calculatedExpirationTime().getSeconds());

			var bySecond = newScheduledTxns.byExpirationSecond().getForModify(secondKey);

			if (bySecond == null) {
				bySecond = new ScheduleSecondVirtualValue();
				newScheduledTxns.byExpirationSecond().put(secondKey, bySecond);
				counts.computeIfAbsent('s', ignore -> new AtomicInteger()).getAndIncrement();
			}

			bySecond.add(newSchedule.calculatedExpirationTime(), new LongArrayList(newScheduleKey.getKeyAsLong()));

			var equalityKey = new ScheduleEqualityVirtualKey(newSchedule.equalityCheckKey());

			var byEquality = newScheduledTxns.byEquality().getForModify(equalityKey);

			if (byEquality == null) {
				byEquality = new ScheduleEqualityVirtualValue();
				newScheduledTxns.byEquality().put(equalityKey, byEquality);
				counts.computeIfAbsent('e', ignore -> new AtomicInteger()).getAndIncrement();
			}

			byEquality.add(newSchedule.equalityCheckValue(), newScheduleKey.getKeyAsLong());

			if (newScheduledTxns.getCurrentMinSecond() > secondKey.getKeyAsLong()) {
				newScheduledTxns.setCurrentMinSecond(secondKey.getKeyAsLong());
			}

			counts.computeIfAbsent('t', ignore -> new AtomicInteger()).getAndIncrement();
		});

		initializingState.setChild(StateChildIndices.SCHEDULE_TXS, newScheduledTxns);

		final var defaultZero = new AtomicInteger();
		final var summaryTpl = """
				Migration complete for:
					\u21AA {} transactions
					\u21AA {} second buckets
					\u21AA {} equality buckets""";
		log.info(summaryTpl,
				counts.getOrDefault('t', defaultZero).get(),
				counts.getOrDefault('s', defaultZero).get(),
				counts.getOrDefault('e', defaultZero).get());
	}


	private LongTermScheduledTransactionsMigration() {
		throw new UnsupportedOperationException("Utility class");
	}
}
