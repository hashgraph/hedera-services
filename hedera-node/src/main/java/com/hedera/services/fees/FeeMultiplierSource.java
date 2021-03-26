package com.hedera.services.fees;

public interface FeeMultiplierSource {
	void updateMultiplier();
	long currentMultiplier();
	void resetExpectations();
}
