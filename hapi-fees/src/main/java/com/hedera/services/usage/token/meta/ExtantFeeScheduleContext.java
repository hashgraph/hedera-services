package com.hedera.services.usage.token.meta;

public class ExtantFeeScheduleContext {
	private final long expiry;
	private final int numBytesInFeeScheduleRepr;

	public ExtantFeeScheduleContext(long expiry, int numBytesInFeeScheduleRepr) {
		this.expiry = expiry;
		this.numBytesInFeeScheduleRepr = numBytesInFeeScheduleRepr;
	}

	public long getExpiry() {
		return expiry;
	}

	public int numBytesInFeeScheduleRepr() {
		return numBytesInFeeScheduleRepr;
	}
}
