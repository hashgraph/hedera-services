package com.hedera.services.throttling.real;

import java.time.Duration;
import java.time.Instant;

/**
 * A throttle with milli-TPS resolution that exists in a deterministic timeline.
 */
public class DeterministicThrottle {
	private static final Instant NEVER = null;

	private final BucketThrottle delegate;
	private Instant lastDecisionTime;

	public static DeterministicThrottle withTps(int tps) {
		return new DeterministicThrottle(BucketThrottle.withTps(tps));
	}

	public static DeterministicThrottle withMtps(long mtps) {
		return new DeterministicThrottle(BucketThrottle.withMtps(mtps));
	}

	public static DeterministicThrottle withTpsAndBurstPeriod(int tps, int burstPeriod) {
		return new DeterministicThrottle(BucketThrottle.withTpsAndBurstPeriod(tps, burstPeriod));
	}

	public static DeterministicThrottle withMtpsAndBurstPeriod(long mtps, int burstPeriod) {
		return new DeterministicThrottle(BucketThrottle.withMtpsAndBurstPeriod(mtps, burstPeriod));
	}

	private DeterministicThrottle(BucketThrottle delegate) {
		this.delegate = delegate;
		lastDecisionTime = NEVER;
	}

	public boolean allow(int n) {
		return allow(n, Instant.now());
	}

	public boolean allow(int n, Instant now) {
		long elapsedNanos = 0L;
		if (lastDecisionTime != NEVER) {
			elapsedNanos = Duration.between(lastDecisionTime, now).toNanos();
			if (elapsedNanos < 0L) {
				throw new IllegalArgumentException(
						"Throttle timeline must advance, but " + now + " is not after " + lastDecisionTime + "!");
			}
		}

		var decision = delegate.allow(n, elapsedNanos);
		lastDecisionTime = now;
		return decision;
	}

	public UsageSnapshot usageSnapshot() {
		var bucket = delegate.bucket();
		return new UsageSnapshot(bucket.capacityUsed(), bucket.totalCapacity(), lastDecisionTime);
	}

	public void resetUsageTo(UsageSnapshot usageSnapshot) {
		var bucket = delegate.bucket();
		if (bucket.totalCapacity() != usageSnapshot.capacity()) {
			throw new IllegalArgumentException(
					"Throttle capacity " + bucket.totalCapacity()
							+ "differs from snapshot " + usageSnapshot.capacity() + "!");
		}
		lastDecisionTime = usageSnapshot.lastDecisionTime();
		bucket.resetUsed(usageSnapshot.used());
	}

	public static class UsageSnapshot {
		private final long used;
		private final long capacity;
		private final Instant lastDecisionTime;

		public UsageSnapshot(long used, long capacity, Instant lastDecisionTime) {
			this.used = used;
			this.capacity = capacity;
			this.lastDecisionTime = lastDecisionTime;
		}

		public long used() {
			return used;
		}

		public long capacity() {
			return capacity;
		}

		public Instant lastDecisionTime() {
			return lastDecisionTime;
		}
	}

	BucketThrottle delegate() {
		return delegate;
	}

	Instant lastDecisionTime() {
		return lastDecisionTime;
	}
}
