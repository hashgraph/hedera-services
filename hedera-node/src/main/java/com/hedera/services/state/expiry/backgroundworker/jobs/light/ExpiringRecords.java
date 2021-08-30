/*
 * -
 * ‌
 * Hedera Services Node
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *       http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hedera.services.state.expiry.backgroundworker.jobs.light;

import com.hedera.services.config.HederaNumbers;
import com.hedera.services.records.RecordCache;
import com.hedera.services.records.TxnIdRecentHistory;
import com.hedera.services.state.expiry.MonotonicFullQueueExpiries;
import com.hedera.services.state.expiry.backgroundworker.jobs.Job;
import com.hedera.services.state.expiry.backgroundworker.jobs.JobEntityClassification;
import com.hedera.services.state.expiry.backgroundworker.jobs.JobStatus;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleEntityId;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.state.submerkle.ExpirableTxnRecord;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.swirlds.fcmap.FCMap;
import com.swirlds.fcqueue.FCQueue;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static com.hedera.services.utils.MiscUtils.forEach;
import static java.util.Comparator.comparing;

/**
 * Responsible for cleaning up old transaction records.
 * 
 */
public class ExpiringRecords implements Job {
	
	private JobStatus status;
	private final JobEntityClassification classification = JobEntityClassification.LIGHTWEIGHT;
	
	private long shard;
	private long realm;
	private final RecordCache recordCache;
	private final MonotonicFullQueueExpiries<Long> payerRecordExpiries = new MonotonicFullQueueExpiries<>();
	private final Supplier<FCMap<MerkleEntityId, MerkleAccount>> accounts;
	private final Map<TransactionID, TxnIdRecentHistory> txnHistories;
	
	public void trackRecordInState(AccountID owner, long expiry) {
		payerRecordExpiries.track(owner.getAccountNum(), expiry);
	}
	
	public ExpiringRecords(
			final HederaNumbers numbers,
			final RecordCache recordCache,
			final Supplier<FCMap<MerkleEntityId, MerkleAccount>> accounts,
			final Map<TransactionID, TxnIdRecentHistory> txnHistories) {
		this.recordCache = recordCache;
		this.accounts = accounts;
		
		this.txnHistories = txnHistories;
		this.shard = numbers.shard();
		this.realm = numbers.realm();
	}
	
	@Override
	public boolean execute(long now) {
		final var currentAccounts = accounts.get();
		while (payerRecordExpiries.hasExpiringAt(now)) {
			final var key = new MerkleEntityId(shard, realm, payerRecordExpiries.expireNextAt(now));

			final var mutableAccount = currentAccounts.getForModify(key);
			final var mutableRecords = mutableAccount.records();
			purgeExpiredFrom(mutableRecords, now);
		}
		recordCache.forgetAnyOtherExpiredHistory(now);
		return true;
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

	/**
	 * When payer records are in state (true by default), upon restart or reconnect the
	 * expiry manager needs to rebuild its expiration queue so it can correctly purge
	 * these records as their lifetimes (default 180s) expire.
	 *
	 * <b>IMPORTANT:</b> As a side-effect, this method re-stages the injected
	 * {@code txnHistories} map with the recent histories of the {@link TransactionID}s
	 * from records in state.
	 */
	@Override
	public void reviewExistingEntities() {
		recordCache.reset();
		txnHistories.clear();
		payerRecordExpiries.reset();

		final var _payerExpiries = new ArrayList<Map.Entry<Long, Long>>();
		final var currentAccounts = accounts.get();
		forEach(currentAccounts, (id, account) -> stageExpiringRecords(id.getNum(), account.records(), _payerExpiries));
		_payerExpiries.sort(comparing(Map.Entry<Long, Long>::getValue).thenComparing(Map.Entry::getKey));
		_payerExpiries.forEach(entry -> payerRecordExpiries.track(entry.getKey(), entry.getValue()));

		txnHistories.values().forEach(TxnIdRecentHistory::observeStaged);
	}

	@Override
	public long getAffectedId() {
		// no-op for this job
		return 0L;
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


	@Override
	public JobStatus getStatus() {
		return status;
	}

	@Override
	public JobEntityClassification getClassification() {
		return classification;
	}

	public void setStatus(final JobStatus status) {
		this.status = status;
	}
}
