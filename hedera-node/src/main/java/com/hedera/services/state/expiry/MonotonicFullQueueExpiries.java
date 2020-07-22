package com.hedera.services.state.expiry;

import java.util.ArrayDeque;
import java.util.Deque;

public class MonotonicFullQueueExpiries<K> implements KeyedExpirations<K> {
	long now = 0L;
	Deque<ExpiryEvent> allExpiries = new ArrayDeque<>();

	@Override
	public void track(K id, long expiry) {
		if (expiry < now) {
			throw new IllegalArgumentException(String.format("Track time %d for %s not later than %d", expiry, id, now));
		}
		now = expiry;
		allExpiries.add(new ExpiryEvent(id, expiry));
	}

	@Override
	public boolean hasExpiringAt(long now) {
		return !allExpiries.isEmpty() && allExpiries.peekFirst().isExpiredAt(now);
	}

	@Override
	public K expireNextAt(long now) {
		if (allExpiries.isEmpty()) {
			throw new IllegalStateException("No ids are queued for expiration!");
		}
		if (!allExpiries.peek().isExpiredAt(now)) {
			throw new IllegalArgumentException("Next id is not expired!");
		}
		return allExpiries.removeFirst().getId();
	}

	private final class ExpiryEvent {
		private final K id;
		private final long expiry;

		public ExpiryEvent(K id, long expiry) {
			this.id = id;
			this.expiry = expiry;
		}

		public boolean isExpiredAt(long now) {
			return expiry <= now;
		}

		public K getId() {
			return id;
		}
	}
}
