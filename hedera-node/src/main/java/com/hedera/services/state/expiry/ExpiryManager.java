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
import com.hedera.services.state.submerkle.ExpirableTxnRecord;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.swirlds.fcmap.FCMap;
import com.swirlds.fcqueue.FCQueue;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public class ExpiryManager {
	private final RecordCache recordCache;
	private final Map<TransactionID, TxnIdRecentHistory> txnHistories;

	long sharedNow;
	MonotonicFullQueueExpiries<Long> payerExpiries = new MonotonicFullQueueExpiries<>();

	public ExpiryManager(
			RecordCache recordCache,
			Map<TransactionID, TxnIdRecentHistory> txnHistories
	) {
		this.recordCache = recordCache;
		this.txnHistories = txnHistories;
	}

	public void trackPayerRecord(AccountID effectivePayer, long expiry) {
		payerExpiries.track(effectivePayer.getAccountNum(), expiry);
	}

	public void resumeTrackingFrom(FCMap<MerkleEntityId, MerkleAccount> accounts) {
		var _payerExpiries = new ArrayList<Map.Entry<Long, Long>>();
		accounts.forEach((id, account) -> {
			addUniqueExpiries(id.getNum(), account.payerRecords(), _payerExpiries);
		});

		var cmp = Comparator.comparing(Map.Entry<Long, Long>::getValue).thenComparing(Map.Entry::getKey);
		_payerExpiries.sort(cmp);
		_payerExpiries.forEach(entry -> payerExpiries.track(entry.getKey(), entry.getValue()));

		txnHistories.values().forEach(TxnIdRecentHistory::observeStaged);
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
			ledger.purgeExpiredPayerRecords(accountWith(payerExpiries.expireNextAt(now)), now, this::updateHistory);
		}
		recordCache.forgetAnyOtherExpiredHistory(now);
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
}
