package com.hedera.services.sigs.sourcing;

@FunctionalInterface
public interface SigObserver {
	void accept(KeyType type, byte[] pubKey, byte[] sig);
}
