package com.hedera.services.fees;

public interface FeeMultiplierSource {
	long currentMultiplier();
	void resetExpectations();
}
