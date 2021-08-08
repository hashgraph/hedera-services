package com.hedera.services.state.submerkle;

public class RoyaltyFeeSpec {
	private final long numerator;
	private final long denominator;
	private final FixedFeeSpec fallbackFee;

	public RoyaltyFeeSpec(long numerator, long denominator, FixedFeeSpec fallbackFee) {
		this.numerator = numerator;
		this.denominator = denominator;
		this.fallbackFee = fallbackFee;
	}
}
