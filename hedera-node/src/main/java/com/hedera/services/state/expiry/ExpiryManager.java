package com.hedera.services.state.expiry;

import com.hedera.services.ledger.HederaLedger;
import com.hedera.services.records.TxnIdRecentHistory;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleEntityId;
import com.hedera.services.state.submerkle.ExpirableTxnRecord;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.swirlds.common.AddressBook;
import com.swirlds.fcmap.FCMap;
import com.swirlds.fcqueue.FCQueue;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.LongStream;

import static com.hedera.services.records.TxnIdRecentHistory.UNKNOWN_SUBMITTING_MEMBER;
import static com.hedera.services.utils.EntityIdUtils.accountParsedFromString;
import static java.util.stream.Collectors.toMap;

public class ExpiryManager {
	private final Map<TransactionID, TxnIdRecentHistory> txnHistories;

	final Map<AccountID, Long> lookup;

	long sharedNow;
	MonotonicFullQueueExpiries<Long> payerExpiries = new MonotonicFullQueueExpiries<>();
	MonotonicFullQueueExpiries<Long> historicalExpiries = new MonotonicFullQueueExpiries<>();

	public ExpiryManager(
			AddressBook book,
			Map<TransactionID, TxnIdRecentHistory> txnHistories
	) {
		lookup = toMemberLookup(book);
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
	}

	Map<AccountID, Long> toMemberLookup(AddressBook book) {
		return LongStream.range(0, book.getSize())
				.boxed()
				.collect(toMap(
						l -> accountParsedFromString(book.getAddress(l).getMemo()),
						Function.identity()));
	}

	private void addUniqueExpiries(
			Long num,
			FCQueue<ExpirableTxnRecord> records,
			List<Map.Entry<Long, Long>> expiries
	) {
		long lastAdded = -1;
		for (ExpirableTxnRecord record : records) {
			var expiry = record.getExpiry();
			if (expiry != lastAdded) {
				expiries.add(new AbstractMap.SimpleImmutableEntry<>(num, expiry));
				lastAdded = expiry;
			}
		}
	}

	void stage(ExpirableTxnRecord record, Long owningAccountNum) {
		var txnId = record.getTxnId().toGrpc();
		long bestGuessMember = (record.getTxnId().getPayerAccount().num() == owningAccountNum)
				? UNKNOWN_SUBMITTING_MEMBER : lookup.get(accountWith(owningAccountNum));
		txnHistories.computeIfAbsent(txnId, ignore -> new TxnIdRecentHistory()).stage(record, bestGuessMember);
	}

	public void purgeExpiredRecordsAt(long now, HederaLedger ledger) {
		sharedNow = now;
		while (historicalExpiries.hasExpiringAt(now)) {
			ledger.purgeExpiredRecords( accountWith(historicalExpiries.expireNextAt(now)), now);
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
