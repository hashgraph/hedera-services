package com.hedera.test.mocks;

import com.hedera.services.fees.FeeMultiplierSource;

public enum TestFeeMultiplierSource implements FeeMultiplierSource {
	MULTIPLIER_SOURCE;

	@Override
	public void updateMultiplier() {
		/* No-op */
	}

	@Override
	public long currentMultiplier() {
		return 1L;
	}

	@Override
	public void resetExpectations() {
		/* No-op */
	}
}
