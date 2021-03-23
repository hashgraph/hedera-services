package com.hedera.services.throttling;

import com.google.common.base.MoreObjects;

public class DeterministicThrottle {
	public StateSnapshot snapshot() {
		throw new AssertionError("Not implemented!");
	}

	public static class StateSnapshot {
		private final long used;
		private final long capacity;

		public StateSnapshot(long used, long capacity) {
			this.used = used;
			this.capacity = capacity;
		}

		public long getUsed() {
			return used;
		}

		public long getCapacity() {
			return capacity;
		}

		@Override
		public String toString() {
			return MoreObjects.toStringHelper(StateSnapshot.class)
					.add("used", used)
					.add("capacity", capacity)
					.toString();
		}
	}
}
