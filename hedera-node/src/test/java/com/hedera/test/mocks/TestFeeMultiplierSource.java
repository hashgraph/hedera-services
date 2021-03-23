package com.hedera.test.mocks;

import com.hedera.services.fees.FeeMultiplierSource;

public enum TestFeeMultiplierSource implements FeeMultiplierSource {
	MULTIPLIER_SOURCE;

	@Override
	public long currentMultiplier() {
		return 1L;
	}
}
