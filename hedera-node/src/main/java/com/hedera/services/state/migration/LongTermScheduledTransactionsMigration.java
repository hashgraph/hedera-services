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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;

import com.hedera.services.ServicesState;
import com.hedera.services.state.merkle.MerkleSchedule;
import com.hedera.services.state.virtual.EntityNumVirtualKey;
import com.hedera.services.state.virtual.schedule.ScheduleEqualityVirtualKey;
import com.hedera.services.state.virtual.schedule.ScheduleEqualityVirtualValue;
import com.hedera.services.state.virtual.schedule.ScheduleSecondVirtualValue;
import com.hedera.services.state.virtual.schedule.ScheduleVirtualValue;
import com.hedera.services.state.virtual.temporal.SecondSinceEpocVirtualKey;
import com.hedera.services.utils.EntityNum;
import com.swirlds.merkle.map.MerkleMap;

import org.apache.commons.lang3.tuple.Pair;
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
		migrateScheduledTransactions(initializingState, (e, s) -> s.equalityCheckKey());
	}

	static void migrateScheduledTransactions(
			final ServicesState initializingState,
			final BiFunction<EntityNum, ScheduleVirtualValue, Long> getEqualityCheckKey
	) {
		log.info("Migrating long term scheduled transactions");

		if (!(initializingState.getChild(StateChildIndices.SCHEDULE_TXS) instanceof MerkleMap)) {
			log.warn("Scheduled Transactions appear to already be migrated!");
			return;
		}

		final MerkleMap<EntityNum, MerkleSchedule> legacySchedules =
				initializingState.getChild(StateChildIndices.SCHEDULE_TXS);

		final var newScheduledTxns = initializingState.scheduleTxs();

		// all legacy schedules have their expiration date generated from the consensus time so if we order
		// by expiry, it will order by consensus time (to the second, we can't get the nanos)
		TreeMap<Long, List<Pair<EntityNum, MerkleSchedule>>> legacyOrdered = new TreeMap<>();
		forEach(legacySchedules, (key, schedule) -> {
			var list = legacyOrdered.computeIfAbsent(
					schedule.expiry(), k -> new ArrayList<>());
			list.add(Pair.of(key, schedule));
		});

		// sort by entity id secondarily so we have deterministic ordering
		legacyOrdered.values().forEach(v -> v.sort(Comparator.comparingLong(a -> a.getKey().longValue())));

		final Map<Character, AtomicInteger> counts = new HashMap<>();
		legacyOrdered.values().forEach(list -> list.forEach(value -> {
			var key = value.getKey();
			var schedule = value.getValue();

			var newScheduleKey = new EntityNumVirtualKey(key.longValue());
			var newSchedule = new ScheduleVirtualValue(schedule);

			newScheduledTxns.byId().put(newScheduleKey, newSchedule);

			var secondKey = new SecondSinceEpocVirtualKey(newSchedule.calculatedExpirationTime().getSeconds());

			var bySecond = newScheduledTxns.byExpirationSecond().get(secondKey);

			if (bySecond == null) {
				bySecond = new ScheduleSecondVirtualValue();
				counts.computeIfAbsent('s', ignore -> new AtomicInteger()).getAndIncrement();
			} else {
				bySecond = bySecond.asWritable();
			}

			bySecond.add(newSchedule.calculatedExpirationTime(), new LongArrayList(newScheduleKey.getKeyAsLong()));

			newScheduledTxns.byExpirationSecond().put(secondKey, bySecond);

			var equalityKey = new ScheduleEqualityVirtualKey(getEqualityCheckKey.apply(key, newSchedule));

			var byEquality = newScheduledTxns.byEquality().get(equalityKey);

			if (byEquality == null) {
				byEquality = new ScheduleEqualityVirtualValue();
				counts.computeIfAbsent('e', ignore -> new AtomicInteger()).getAndIncrement();
			} else {
				byEquality = byEquality.asWritable();
			}

			byEquality.add(newSchedule.equalityCheckValue(), newScheduleKey.getKeyAsLong());

			newScheduledTxns.byEquality().put(equalityKey, byEquality);

			if (newScheduledTxns.getCurrentMinSecond() > secondKey.getKeyAsLong()) {
				newScheduledTxns.setCurrentMinSecond(secondKey.getKeyAsLong());
			}

			counts.computeIfAbsent('t', ignore -> new AtomicInteger()).getAndIncrement();
		}));

		initializingState.setChild(StateChildIndices.SCHEDULE_TXS, newScheduledTxns);

		final var defaultZero = new AtomicInteger();
		final var summaryTpl = "Migration complete for: {} transactions {} second buckets{} equality buckets";
		log.info(summaryTpl,
				counts.getOrDefault('t', defaultZero).get(),
				counts.getOrDefault('s', defaultZero).get(),
				counts.getOrDefault('e', defaultZero).get());
	}


	LongTermScheduledTransactionsMigration() {
		throw new UnsupportedOperationException("Utility class");
	}
}
