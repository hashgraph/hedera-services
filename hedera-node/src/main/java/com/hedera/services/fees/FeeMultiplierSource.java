package com.hedera.services.fees;

@FunctionalInterface
public interface FeeMultiplierSource {
	long currentMultiplier();
}
