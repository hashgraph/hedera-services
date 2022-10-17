/*
 * Copyright (C) 2021-2022 Hedera Hashgraph, LLC
 *
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
 */
package com.hedera.services.throttles;

import static com.hedera.services.throttles.DeterministicThrottle.elapsedNanosBetween;

import com.google.common.annotations.VisibleForTesting;
import java.time.Duration;
import java.time.Instant;

/**
 * Main class responsible for throttling transactions by gasLimit. Keeps track of the instance the
 * last decision was made and calculates the time elapsed since then. Uses a {@link
 * GasLimitBucketThrottle} under the hood.
 */
public class GasLimitDeterministicThrottle {
    private final GasLimitBucketThrottle delegate;
    private Instant lastDecisionTime;
    private final long capacity;

    /**
     * Creates a new instance of the throttle with capacity - the total amount of gas allowed per
     * sec.
     *
     * @param capacity - the total amount of gas allowed per sec.
     */
    public GasLimitDeterministicThrottle(long capacity) {
        this.capacity = capacity;
        this.delegate = new GasLimitBucketThrottle(capacity);
    }

    /**
     * Calculates the amount of nanoseconds that elapsed since the last time the method was called.
     * Verifies whether there is enough capacity to handle a transaction with some gasLimit.
     *
     * @param now - the instant against which the {@link GasLimitBucketThrottle} is tested.
     * @param txGasLimit - the gasLimit extracted from the transaction payload.
     * @return true if there is enough capacity to handle this transaction; false if it should be
     *     throttled.
     */
    public boolean allow(Instant now, long txGasLimit) {
        final var elapsedNanos = elapsedNanosBetween(lastDecisionTime, now);
        var decision = delegate.allow(txGasLimit, elapsedNanos);
        lastDecisionTime = now;
        return decision;
    }

    /**
     * Given a time which must not be before the {@code lastDecisionTime} of this throttle, leaks
     * until that time and returns the resulting free-to-used ratio.
     *
     * @param now the time at which the free-to-used ratio must be computed
     * @return the free-to-used ratio at that time
     */
    public long freeToUsedRatio(final Instant now) {
        delegate.leakFor(elapsedNanosBetween(lastDecisionTime, now));
        lastDecisionTime = now;
        return delegate.freeToUsedRatio();
    }

    /**
     * Returns the percent usage of this throttle, at a time which may be later than the last
     * throttling decision (which would imply some capacity has been freed).
     *
     * @param now a time which will be ignored if before the last throttling decision
     * @return the capacity available at this time
     */
    public double percentUsed(final Instant now) {
        if (lastDecisionTime == null) {
            return 0.0;
        }
        final var elapsedNanos = Math.max(0, Duration.between(lastDecisionTime, now).toNanos());
        return delegate.percentUsed(elapsedNanos);
    }

    /**
     * Returns the capacity of the throttle.
     *
     * @return the capacity of the throttle.
     */
    public long getCapacity() {
        return capacity;
    }

    /**
     * Returns the used capacity of the throttle.
     *
     * @return the used capacity of the throttle.
     */
    public long getUsed() {
        return delegate.bucket().capacityUsed();
    }

    /**
     * Used to release some capacity previously reserved by calling {@link
     * GasLimitDeterministicThrottle#allow(Instant, long)} without having to wait for the natural
     * leakage.
     *
     * @param value - the amount to release
     */
    public void leakUnusedGasPreviouslyReserved(long value) {
        delegate().bucket().leak(value);
    }

    /**
     * returns an instance of the {@link GasLimitBucketThrottle} used under the hood.
     *
     * @return - an instance of the {@link GasLimitBucketThrottle} used under the hood.
     */
    GasLimitBucketThrottle delegate() {
        return delegate;
    }

    public DeterministicThrottle.UsageSnapshot usageSnapshot() {
        final var bucket = delegate.bucket();
        return new DeterministicThrottle.UsageSnapshot(bucket.capacityUsed(), lastDecisionTime);
    }

    public void resetUsageTo(final DeterministicThrottle.UsageSnapshot usageSnapshot) {
        final var bucket = delegate.bucket();
        lastDecisionTime = usageSnapshot.lastDecisionTime();
        bucket.resetUsed(usageSnapshot.used());
    }

    public void resetUsage() {
        resetLastAllowedUse();
        final var bucket = delegate.bucket();
        bucket.resetUsed(0);
    }

    public void reclaimLastAllowedUse() {
        delegate.reclaimLastAllowedUse();
    }

    public void resetLastAllowedUse() {
        delegate.resetLastAllowedUse();
    }

    @VisibleForTesting
    Instant getLastDecisionTime() {
        return lastDecisionTime;
    }
}
