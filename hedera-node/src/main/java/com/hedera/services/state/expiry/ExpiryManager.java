package com.hedera.services.state.expiry;

import com.hedera.services.ledger.HederaLedger;
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
	private final Map<TransactionID, TxnIdRecentHistory> txnHistories;

	long sharedNow;
	MonotonicFullQueueExpiries<Long> payerExpiries = new MonotonicFullQueueExpiries<>();
	MonotonicFullQueueExpiries<Long> historicalExpiries = new MonotonicFullQueueExpiries<>();

	public ExpiryManager(Map<TransactionID, TxnIdRecentHistory> txnHistories) {
		this.txnHistories = txnHistories;
	}

	public void trackHistoricalRecord(AccountID payer, long expiry) {
		historicalExpiries.track(payer.getAccountNum(), expiry);
	}

	public void trackPayerRecord(AccountID effectivePayer, long expiry) {
		payerExpiries.track(effectivePayer.getAccountNum(), expiry);
	}

	public void resumeTrackingFrom(FCMap<MerkleEntityId, MerkleAccount> accounts) {
		var _payerExpiries = new ArrayList<Map.Entry<Long, Long>>();
		var _historicalExpiries = new ArrayList<Map.Entry<Long, Long>>();
		accounts.forEach((id, account) -> {
			addUniqueExpiries(id.getNum(), account.payerRecords(), _payerExpiries);
			addUniqueExpiries(id.getNum(), account.records(), _historicalExpiries);
		});

		var cmp = Comparator.comparing(Map.Entry<Long, Long>::getValue).thenComparing(Map.Entry::getKey);
		_payerExpiries.sort(cmp);
		_historicalExpiries.sort(cmp);
		_payerExpiries.forEach(entry -> payerExpiries.track(entry.getKey(), entry.getValue()));
		_historicalExpiries.forEach(entry -> historicalExpiries.track(entry.getKey(), entry.getValue()));

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
		while (historicalExpiries.hasExpiringAt(now)) {
			ledger.purgeExpiredRecords(accountWith(historicalExpiries.expireNextAt(now)), now);
		}
		while (payerExpiries.hasExpiringAt(now)) {
			ledger.purgeExpiredPayerRecords(accountWith(payerExpiries.expireNextAt(now)), now, this::updateHistory);
		}
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
