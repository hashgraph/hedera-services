package com.hedera.services.throttles;

/*-
 * ‌
 * Hedera Services Node
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

public class GasLimitDeterministicThrottle {

    private static final Instant NEVER = null;

    private final GasLimitBucketThrottle delegate;
    private Instant lastDecisionTime;
    private long capacity;

    public GasLimitDeterministicThrottle(long capacity) {
        this.capacity = capacity;
        this.delegate = new GasLimitBucketThrottle(capacity);
    }

    public boolean allow(Instant now, long txGasLimit) {
        long elapsedNanos = 0L;
        if (lastDecisionTime != NEVER) {
            elapsedNanos = Duration.between(lastDecisionTime, now).toNanos();
            if (elapsedNanos < 0L) {
                throw new IllegalArgumentException(
                        "Throttle timeline must advance, but " + now + " is not after " + lastDecisionTime + "!");
            }
        }

        var decision = delegate.allow(txGasLimit, elapsedNanos);
        lastDecisionTime = now;
        return decision;
    }

    public String name() {
        return null;
    }

    public long getCapacity() {
        return capacity;
    }

    Instant lastDecisionTime() {
        return lastDecisionTime;
    }

    GasLimitBucketThrottle delegate() {
        return delegate;
    }

    public void leakUnusedGasPreviouslyReserved(long value) {
        delegate().bucket().leak(value);
    }

    public DeterministicThrottle.UsageSnapshot usageSnapshot() {
        var bucket = delegate.bucket();
        return new DeterministicThrottle.UsageSnapshot(bucket.capacityUsed(), lastDecisionTime);
    }

    public void resetUsageTo(DeterministicThrottle.UsageSnapshot usageSnapshot) {
        var bucket = delegate.bucket();
        lastDecisionTime = usageSnapshot.lastDecisionTime();
        bucket.resetUsed(usageSnapshot.used());
    }
}
