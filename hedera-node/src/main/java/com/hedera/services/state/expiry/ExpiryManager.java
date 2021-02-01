package com.hedera.services.state.expiry;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2020 Hedera Hashgraph, LLC
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

import com.hedera.services.ledger.HederaLedger;
import com.hedera.services.records.RecordCache;
import com.hedera.services.records.TxnIdRecentHistory;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleEntityId;
import com.hedera.services.state.merkle.MerkleSchedule;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.state.submerkle.ExpirableTxnRecord;
import com.hedera.services.store.schedule.ScheduleStore;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.swirlds.fcmap.FCMap;
import com.swirlds.fcqueue.FCQueue;
import javafx.util.Pair;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class ExpiryManager {
	private final RecordCache recordCache;
	private final Map<TransactionID, TxnIdRecentHistory> txnHistories;
	private final FCMap<MerkleEntityId, MerkleSchedule> schedules;

	private final ScheduleStore scheduleStore;

	long sharedNow;
	MonotonicFullQueueExpiries<Long> payerExpiries = new MonotonicFullQueueExpiries<>();
	MonotonicFullQueueExpiries<Pair<Long, Consumer<EntityId>>> entityExpiries = new MonotonicFullQueueExpiries<>();

	public ExpiryManager(
			RecordCache recordCache,
			Map<TransactionID, TxnIdRecentHistory> txnHistories,
			ScheduleStore scheduleStore,
			FCMap<MerkleEntityId, MerkleSchedule> schedules
	) {
		this.recordCache = recordCache;
		this.txnHistories = txnHistories;
		this.scheduleStore = scheduleStore;

		this.schedules = schedules;
	}

	public void trackRecord(AccountID owner, long expiry) {
		payerExpiries.track(owner.getAccountNum(), expiry);
	}

	public void restartTrackingFrom(FCMap<MerkleEntityId, MerkleAccount> accounts) {
		recordCache.reset();
		txnHistories.clear();
		payerExpiries.reset();

		var _payerExpiries = new ArrayList<Map.Entry<Long, Long>>();
		accounts.forEach((id, account) -> {
			addUniqueExpiries(id.getNum(), account.records(), _payerExpiries);
		});

		var cmp = Comparator.comparing(Map.Entry<Long, Long>::getValue).thenComparing(Map.Entry::getKey);
		_payerExpiries.sort(cmp);
		_payerExpiries.forEach(entry -> payerExpiries.track(entry.getKey(), entry.getValue()));

		txnHistories.values().forEach(TxnIdRecentHistory::observeStaged);
	}

	/**
	 * Invites the expiry manager to build any auxiliary data structures
	 * later needed to purge expired entities
	 */
	public void restartEntitiesTrackingFrom() {
		entityExpiries.reset();

		var expiries = new ArrayList<Map.Entry<Pair<Long, Consumer<EntityId>>, Long>>();
		schedules.forEach((id, schedule) -> {
			var pair = new Pair<Long, Consumer<EntityId>>(id.getNum(), scheduleStore::expire);
			expiries.add(new AbstractMap.SimpleImmutableEntry<>(pair, schedule.expiry()));
		});
		// todo: add accounts, files, tokens, topics

		var cmp = Comparator
				.comparing(Map.Entry<Pair<Long, Consumer<EntityId>>, Long>::getValue)
				.thenComparing(entry -> entry.getKey().getKey());
		expiries.sort(cmp);
		expiries.forEach(entry -> entityExpiries.track(entry.getKey(), entry.getValue()));
	}

	private void addUniqueExpiries(
			Long num,
			FCQueue<ExpirableTxnRecord> records,
			List<Map.Entry<Long, Long>> expiries
	) {
		long lastAdded = -1;
		for (ExpirableTxnRecord record : records) {
			stage(record);
			var expiry = record.getExpiry();
			if (expiry != lastAdded) {
				expiries.add(new AbstractMap.SimpleImmutableEntry<>(num, expiry));
				lastAdded = expiry;
			}
		}
	}

	void stage(ExpirableTxnRecord record) {
		var txnId = record.getTxnId().toGrpc();
		txnHistories.computeIfAbsent(txnId, ignore -> new TxnIdRecentHistory()).stage(record);
	}

	public void purgeExpiredRecordsAt(long now, HederaLedger ledger) {
		sharedNow = now;
		while (payerExpiries.hasExpiringAt(now)) {
			ledger.purgeExpiredRecords(accountWith(payerExpiries.expireNextAt(now)), now, this::updateHistory);
		}
		recordCache.forgetAnyOtherExpiredHistory(now);
	}

	/**
	 * Marks expired entities as deleted before given timestamp in seconds. Not that for
	 * this to be done efficiently, the expiry manager will need the opportunity to scan
	 * the ledger and build an auxiliary data structure of expiration times
	 * @param now the time in seconds used to expire entities
	 */
	public void purgeExpiredEntitiesAt(long now) {
		while (entityExpiries.hasExpiringAt(now)) {
			var current = entityExpiries.expireNextAt(now);
			current.getValue().accept(entityWith(current.getKey()));
		}
	}

	public void trackEntity(Pair<Long, Consumer<EntityId>> entity, long expiry) {
		entityExpiries.track(entity, expiry);
	}

	void updateHistory(ExpirableTxnRecord record) {
		var txnId = record.getTxnId().toGrpc();
		var history = txnHistories.get(txnId);
		if (history != null) {
			history.forgetExpiredAt(sharedNow);
			if (history.isForgotten()) {
				txnHistories.remove(txnId);
			}
		}
	}

	AccountID accountWith(long num) {
		return AccountID.newBuilder()
				.setShardNum(0)
				.setRealmNum(0)
				.setAccountNum(num)
				.build();
	}

	EntityId entityWith(long num) {
		return new EntityId(0, 0, num);
	}
}
