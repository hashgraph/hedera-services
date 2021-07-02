package com.hedera.services.usage.token;

public class ExtantFeeScheduleContext {
	private final long expiry;
	private final long numBytesInFeeScheduleRepr;

	public ExtantFeeScheduleContext(long expiry, long numBytesInFeeScheduleRepr) {
		this.expiry = expiry;
		this.numBytesInFeeScheduleRepr = numBytesInFeeScheduleRepr;
	}

	public long getExpiry() {
		return expiry;
	}

	public long getNumBytesInFeeScheduleRepr() {
		return numBytesInFeeScheduleRepr;
	}
}
