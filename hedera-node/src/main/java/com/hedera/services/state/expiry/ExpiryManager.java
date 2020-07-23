package com.hedera.services.state.expiry;

import com.hedera.services.context.ServicesContext;
import com.hedera.services.ledger.HederaLedger;
import com.hedera.services.state.submerkle.ExpirableTxnRecord;
import com.hederahashgraph.api.proto.java.AccountID;
import com.swirlds.fcqueue.FCQueue;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public class ExpiryManager {
	MonotonicFullQueueExpiries<Long> payerExpiries = new MonotonicFullQueueExpiries<>();
	MonotonicFullQueueExpiries<Long> historicalExpiries = new MonotonicFullQueueExpiries<>();

	public void trackHistoricalRecord(AccountID payer, long expiry) {
		historicalExpiries.track(payer.getAccountNum(), expiry);
	}

	public void trackPayerRecord(AccountID effectivePayer, long expiry) {
		payerExpiries.track(effectivePayer.getAccountNum(), expiry);
	}

	public void resumeTrackingFrom(ServicesContext ctx) {
		var accounts = ctx.accounts();

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

	public void purgeExpiriesAt(long now, ServicesContext ctx) {
		purgeRecords(now, ctx.ledger());
	}

	private void purgeRecords(long now, HederaLedger ledger) {
		while (historicalExpiries.hasExpiringAt(now)) {
			ledger.purgeExpiredRecords(accountWith(historicalExpiries.expireNextAt(now)), now);
		}
		while (payerExpiries.hasExpiringAt(now)) {
			ledger.purgeExpiredPayerRecords(accountWith(payerExpiries.expireNextAt(now)), now);
		}
	}

	private AccountID accountWith(long num) {
		return AccountID.newBuilder()
				.setShardNum(0)
				.setRealmNum(0)
				.setAccountNum(num)
				.build();
	}
}
