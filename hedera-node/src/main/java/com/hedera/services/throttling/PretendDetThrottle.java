package com.hedera.services.throttling;

import com.google.common.base.MoreObjects;
import java.time.Instant;

public class PretendDetThrottle {
	private final StateSnapshot pretendState;

	public PretendDetThrottle(StateSnapshot pretendState) {
		this.pretendState = pretendState;
	}

	public StateSnapshot snapshot() {
		return pretendState;
	}

	public void setStateFrom(StateSnapshot snapshot) {
		throw new AssertionError("Not implemented!");
	}

	public static class StateSnapshot {
		private final long used;
		private final long capacity;
		private final Instant lastUsed;

		public StateSnapshot(long used, long capacity, Instant lastUsed) {
			this.used = used;
			this.capacity = capacity;
			this.lastUsed = lastUsed;
		}

		public long getUsed() {
			return used;
		}

		public long getCapacity() {
			return capacity;
		}

		public Instant getLastUsed() {
			return lastUsed;
		}

		@Override
		public String toString() {
			return MoreObjects.toStringHelper(StateSnapshot.class)
					.add("used", used)
					.add("capacity", capacity)
					.add("last used", lastUsed)
					.toString();
		}
	}
}
