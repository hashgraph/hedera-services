package com.hedera.services.state.expiry;

import com.hedera.services.context.ServicesContext;
import com.hedera.services.context.properties.PropertySource;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TransactionRecord;

public class ExpiryManager {
	private final PropertySource properties;

	MonotonicFullQueueExpiries<Long> payerExpiries = new MonotonicFullQueueExpiries<>();
	MonotonicFullQueueExpiries<Long> thresholdExpiries = new MonotonicFullQueueExpiries<>();

	public ExpiryManager(PropertySource properties) {
		this.properties = properties;
	}

	public void trackThresholdRecord(TransactionRecord record, AccountID payer, long now) {
		throw new AssertionError("Not implemented");
	}

	public void trackPayerRecord(TransactionRecord record, AccountID effectivePayer, long now) {
		long expiry = now + properties.getIntProperty("cache.records.ttl");
		payerExpiries.track(effectivePayer.getAccountNum(), expiry);
	}

	public void resumeTrackingFrom(ServicesContext ctx, long now) {
		throw new AssertionError("Not implemented");
	}

	public void purgeExpiriesAt(long now) {
		throw new AssertionError("Not implemented");
	}
}
