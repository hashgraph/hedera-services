package com.hedera.services.throttles;

/*-
 * ‌
 * Hedera Services API Utilities
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
 * ​
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ‍
 */

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * A throttle with milli-TPS resolution that exists in a deterministic timeline.
 */
public class DeterministicThrottle {
	private static final Instant NEVER = null;
	private static final String NO_NAME = null;

	private final String name;
	private final BucketThrottle delegate;
	private Instant lastDecisionTime;

	public static DeterministicThrottle withTps(int tps) {
		return new DeterministicThrottle(BucketThrottle.withTps(tps), NO_NAME);
	}

	public static DeterministicThrottle withTpsNamed(int tps, String name) {
		return new DeterministicThrottle(BucketThrottle.withTps(tps), name);
	}

	public static DeterministicThrottle withMtps(long mtps) {
		return new DeterministicThrottle(BucketThrottle.withMtps(mtps), NO_NAME);
	}

	public static DeterministicThrottle withMtpsNamed(long mtps, String name) {
		return new DeterministicThrottle(BucketThrottle.withMtps(mtps), name);
	}

	public static DeterministicThrottle withTpsAndBurstPeriod(int tps, int burstPeriod) {
		return new DeterministicThrottle(BucketThrottle.withTpsAndBurstPeriod(tps, burstPeriod), NO_NAME);
	}

	public static DeterministicThrottle withTpsAndBurstPeriodNamed(int tps, int burstPeriod, String name) {
		return new DeterministicThrottle(BucketThrottle.withTpsAndBurstPeriod(tps, burstPeriod), name);
	}

	public static DeterministicThrottle withMtpsAndBurstPeriod(long mtps, int burstPeriod) {
		return new DeterministicThrottle(BucketThrottle.withMtpsAndBurstPeriod(mtps, burstPeriod), NO_NAME);
	}

	public static DeterministicThrottle withMtpsAndBurstPeriodNamed(long mtps, int burstPeriod, String name) {
		return new DeterministicThrottle(BucketThrottle.withMtpsAndBurstPeriod(mtps, burstPeriod), name);
	}

	private DeterministicThrottle(BucketThrottle delegate, String name) {
		this.name = name;
		this.delegate = delegate;
		lastDecisionTime = NEVER;
	}

	public static long capacityRequiredFor(int nTransactions) {
		return nTransactions * BucketThrottle.capacityUnitsPerTxn();
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

	public void reclaimLastAllowedUse() {
		delegate.reclaimLastAllowedUse();
	}

	public String name() {
		return name;
	}

	public long mtps() {
		return delegate.mtps();
	}

	public long used() {
		return delegate.bucket().capacityUsed();
	}

	public long capacity() {
		return delegate.bucket().totalCapacity();
	}

	public UsageSnapshot usageSnapshot() {
		var bucket = delegate.bucket();
		return new UsageSnapshot(bucket.capacityUsed(), lastDecisionTime);
	}

	public void resetUsageTo(UsageSnapshot usageSnapshot) {
		var bucket = delegate.bucket();
		lastDecisionTime = usageSnapshot.lastDecisionTime();
		bucket.resetUsed(usageSnapshot.used());
	}

	@Override
	public boolean equals(Object obj) {
		var that = (DeterministicThrottle)obj;

		return this.delegate.bucket().totalCapacity() == that.delegate.bucket().totalCapacity()
				&& this.delegate.mtps() == that.delegate.mtps();
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder("DeterministicThrottle{");
		if (name != NO_NAME) {
			sb.append("name='").append(name).append("', ");
		}
		return sb
				.append("mtps=").append(delegate.mtps()).append(", ")
				.append("capacity=").append(capacity()).append(" (used=").append(used()).append(")")
				.append(lastDecisionTime == NEVER ? "" : (", last decision @ " + lastDecisionTime))
				.append("}")
				.toString();
	}

	public static class UsageSnapshot {
		private final long used;
		private final Instant lastDecisionTime;

		public UsageSnapshot(long used, Instant lastDecisionTime) {
			this.used = used;
			this.lastDecisionTime = lastDecisionTime;
		}

		public long used() {
			return used;
		}

		public Instant lastDecisionTime() {
			return lastDecisionTime;
		}

		@Override
		public String toString() {
			var sb = new StringBuilder("DeterministicThrottle.UsageSnapshot{");
			return sb
					.append("used=").append(used)
					.append(", last decision @ ")
					.append(lastDecisionTime == NEVER ? "<N/A>" : lastDecisionTime)
					.append("}")
					.toString();
		}

		@Override
		public boolean equals(Object o) {
			if (o == this) {
				return true;
			}
			if (o == null || !o.getClass().equals(UsageSnapshot.class)) {
				return false;
			}
			UsageSnapshot that = (UsageSnapshot) o;
			return this.used == that.used && Objects.equals(this.lastDecisionTime, that.lastDecisionTime);
		}

		@Override
		public int hashCode() {
			return Objects.hash(used, lastDecisionTime);
		}
	}

	BucketThrottle delegate() {
		return delegate;
	}

	Instant lastDecisionTime() {
		return lastDecisionTime;
	}
}
