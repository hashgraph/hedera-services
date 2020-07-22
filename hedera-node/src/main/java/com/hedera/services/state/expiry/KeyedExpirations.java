package com.hedera.services.state.expiry;

public interface KeyedExpirations<K> {
	void track(K id, long expiry);
	boolean hasExpiringAt(long now);
	K expireNextAt(long now);
}
