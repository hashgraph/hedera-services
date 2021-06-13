package com.hedera.services.state.expiry;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
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

import com.hedera.services.config.HederaNumbers;
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
import org.apache.commons.lang3.tuple.Pair;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static java.util.Comparator.comparing;

/**
 * Manager of two queues of expiration events---one for payer records, one for schedule entities.
 *
 * There are two management responsibilities:
 * <ol>
 *    <li>On restart or reconnect, rebuild the expiration queues from state.</li>
 *    <li>At the first consensus second an entity is expired, remove it from its parent collection.</li>
 * </ol>
 */
public class ExpiryManager {
	private final long shard, realm;

	private final RecordCache recordCache;
	private final ScheduleStore scheduleStore;
	private final Map<TransactionID, TxnIdRecentHistory> txnHistories;
	private final Supplier<FCMap<MerkleEntityId, MerkleAccount>> accounts;
	private final Supplier<FCMap<MerkleEntityId, MerkleSchedule>> schedules;

	private final MonotonicFullQueueExpiries<Long> payerRecordExpiries =
			new MonotonicFullQueueExpiries<>();
	private final PriorityQueueExpiries<Pair<Long, Consumer<EntityId>>> shortLivedEntityExpiries =
			new PriorityQueueExpiries<>();

	public ExpiryManager(
			RecordCache recordCache,
			ScheduleStore scheduleStore,
			HederaNumbers hederaNums,
			Map<TransactionID, TxnIdRecentHistory> txnHistories,
			Supplier<FCMap<MerkleEntityId, MerkleAccount>> accounts,
			Supplier<FCMap<MerkleEntityId, MerkleSchedule>> schedules
	) {
		this.accounts = accounts;
		this.schedules = schedules;
		this.recordCache = recordCache;
		this.txnHistories = txnHistories;
		this.scheduleStore = scheduleStore;

		this.shard = hederaNums.shard();
		this.realm = hederaNums.realm();
	}

	/**
	 * Purges any references to expired entities (at this time, records or schedules).
	 *
	 * @param now the consensus second
	 */
	public void purge(long now) {
		purgeExpiredRecordsAt(now);
		purgeExpiredShortLivedEntities(now);
	}

	/**
	 * Begins tracking an expiration event.
	 *
	 * @param event the expiration event to track
	 * @param expiry the earliest consensus second at which it should fire
	 */
	public void trackExpirationEvent(Pair<Long, Consumer<EntityId>> event, long expiry) {
		shortLivedEntityExpiries.track(event, expiry);
	}

	/**
	 * When payer records are in state (true by default), upon restart or reconnect the
	 * expiry manager needs to rebuild its expiration queue so it can correctly purge
	 * these records as their lifetimes (default 180s) expire.
	 *
	 * <b>IMPORTANT:</b> As a side-effect, this method re-stages the injected
	 * {@code txnHistories} map with the recent histories of the {@link TransactionID}s
	 * from records in state.
	 */
	public void reviewExistingPayerRecords() {
		recordCache.reset();
		txnHistories.clear();
		payerRecordExpiries.reset();

		final var _payerExpiries = new ArrayList<Map.Entry<Long, Long>>();
		final var currentAccounts = accounts.get();
		currentAccounts.forEach((id, account) -> stageExpiringRecords(id.getNum(), account.records(), _payerExpiries));
		_payerExpiries.sort(comparing(Map.Entry<Long, Long>::getValue).thenComparing(Map.Entry::getKey));
		_payerExpiries.forEach(entry -> payerRecordExpiries.track(entry.getKey(), entry.getValue()));

		txnHistories.values().forEach(TxnIdRecentHistory::observeStaged);
	}

	/**
	 * Entities that typically expire on the order of days or months (topics, accounts, tokens, etc.)
	 * are monitored and automatically renewed or removed by the {@link EntityAutoRenewal} process.
	 *
	 * The only entities that currently qualify as "short-lived" are schedule entities, which have
	 * a default expiration time of 30 minutes. So this method's only function is to scan the
	 * current {@code schedules} FCM and enqueue their expiration events.
	 */
	public void reviewExistingShortLivedEntities() {
		shortLivedEntityExpiries.reset();

		final var _shortLivedEntityExpiries = new ArrayList<Map.Entry<Pair<Long, Consumer<EntityId>>, Long>>();
		schedules.get().forEach((id, schedule) -> {
			Consumer<EntityId> consumer = scheduleStore::expire;
			var pair = Pair.of(id.getNum(), consumer);
			_shortLivedEntityExpiries.add(new AbstractMap.SimpleImmutableEntry<>(pair, schedule.expiry()));
		});

		_shortLivedEntityExpiries.sort(comparing(Map.Entry<Pair<Long, Consumer<EntityId>>, Long>::getValue).
				thenComparing(entry -> entry.getKey().getKey()));
		_shortLivedEntityExpiries.forEach(entry -> shortLivedEntityExpiries.track(entry.getKey(), entry.getValue()));
	}

	void trackRecordInState(AccountID owner, long expiry) {
		payerRecordExpiries.track(owner.getAccountNum(), expiry);
	}

	private void purgeExpiredRecordsAt(long now) {
		final var currentAccounts = accounts.get();
		while (payerRecordExpiries.hasExpiringAt(now)) {
			final var key = new MerkleEntityId(shard, realm, payerRecordExpiries.expireNextAt(now));

			final var mutableAccount = currentAccounts.getForModify(key);
			final var mutableRecords = mutableAccount.records();
			purgeExpiredFrom(mutableRecords, now);
		}
		recordCache.forgetAnyOtherExpiredHistory(now);
	}

	private void purgeExpiredFrom(FCQueue<ExpirableTxnRecord> records, long now) {
		ExpirableTxnRecord nextRecord;
		while ((nextRecord = records.peek()) != null && nextRecord.getExpiry() <= now) {
			nextRecord = records.poll();
			final var txnId = nextRecord.getTxnId().toGrpc();
			final var history = txnHistories.get(txnId);
			if (history != null) {
				history.forgetExpiredAt(now);
				if (history.isForgotten()) {
					txnHistories.remove(txnId);
				}
			}
		}
	}

	private void purgeExpiredShortLivedEntities(long now) {
		while (shortLivedEntityExpiries.hasExpiringAt(now)) {
			var current = shortLivedEntityExpiries.expireNextAt(now);
			current.getValue().accept(entityWith(current.getKey()));
		}
	}

	private void stageExpiringRecords(
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

	private void stage(ExpirableTxnRecord record) {
		final var txnId = record.getTxnId().toGrpc();
		txnHistories.computeIfAbsent(txnId, ignore -> new TxnIdRecentHistory()).stage(record);
	}

	private EntityId entityWith(long num) {
		return new EntityId(shard, realm, num);
	}

	PriorityQueueExpiries<Pair<Long, Consumer<EntityId>>> getShortLivedEntityExpiries() {
		return shortLivedEntityExpiries;
	}
}
