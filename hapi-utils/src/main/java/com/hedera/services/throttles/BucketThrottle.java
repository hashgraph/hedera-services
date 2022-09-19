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

import static com.hedera.services.legacy.proto.utils.CommonUtils.productWouldOverflow;

/**
 * A throttle that enforces a transaction rate with resolution of 1/1000th of a transaction.
 * Throttling decisions are made based on the capacity remaining in a {@link DiscreteLeakyBucket}
 * that leaks a fixed number of units per nanosecond. (One unit of capacity in the bucket is
 * one-billionth of the capacity needed to perform one-thousandth of a transaction.)
 *
 * <p>This class is <b>not</b> thread-safe.
 *
 * <p>The {@link BucketThrottle#allow(int, long)} method answers the question of whether some
 * positive integer number of transactions can be accepted a given number of nanoseconds after the
 * last call to this method.
 *
 * <p>The throttle's behavior is controlled by two parameters,
 *
 * <ol>
 *   <li>The <i>allowed transaction rate</i>, in units of either transactions-per-second (tps) or
 *       milli-transactions-per-second (mtps).
 *   <li>The <i>burst period</i>; that is, the maximum period for which the allowed transaction rate
 *       can be sustained in a sudden burst; units are seconds or milliseconds.
 * </ol>
 *
 * The purpose of the mtps unit is to allow the user to create a {@code BucketThrottle} with allowed
 * transaction rate below 1 tps. However, if the user tries to construct a {@code BucketThrottle}
 * for which the allowed transaction rate multiplied by the burst period still does not amount to a
 * full transaction, the constructor throws an {@code IllegalArgumentException}---the resulting
 * instance could only ever return {@code false} from {@link BucketThrottle#allow(int, long)}.
 */
public class BucketThrottle {
    private static final int DEFAULT_BURST_PERIOD = 1;

    static final long MS_PER_SEC = 1_000L;
    static final long MTPS_PER_TPS = 1_000L;
    static final long NTPS_PER_MTPS = 1_000_000L;
    static final long CAPACITY_UNITS_PER_TXN = 1_000_000_000_000L;
    static final long CAPACITY_UNITS_PER_NANO_TXN = 1_000L;

    public static long capacityUnitsPerTxn() {
        return CAPACITY_UNITS_PER_TXN;
    }

    public static long capacityUnitsPerMs(final long mtps) {
        return mtps * NTPS_PER_MTPS * CAPACITY_UNITS_PER_NANO_TXN / MS_PER_SEC;
    }

    private final long mtps;
    private final DiscreteLeakyBucket bucket;

    private long lastAllowedUnits = 0L;

    static BucketThrottle withTps(int tps) {
        return new BucketThrottle(tps * MTPS_PER_TPS, DEFAULT_BURST_PERIOD * MS_PER_SEC);
    }

    static BucketThrottle withMtps(long mtps) {
        return new BucketThrottle(mtps, DEFAULT_BURST_PERIOD * MS_PER_SEC);
    }

    static BucketThrottle withTpsAndBurstPeriod(int tps, int burstPeriod) {
        return new BucketThrottle(tps * MTPS_PER_TPS, burstPeriod * MS_PER_SEC);
    }

    static BucketThrottle withTpsAndBurstPeriodMs(int tps, long burstPeriodMs) {
        return new BucketThrottle(tps * MTPS_PER_TPS, burstPeriodMs);
    }

    static BucketThrottle withMtpsAndBurstPeriod(long mtps, int burstPeriod) {
        return new BucketThrottle(mtps, burstPeriod * MS_PER_SEC);
    }

    static BucketThrottle withMtpsAndBurstPeriodMs(long mtps, long burstPeriodMs) {
        return new BucketThrottle(mtps, burstPeriodMs);
    }

    private BucketThrottle(long mtps, long burstPeriodMs) {
        this.mtps = mtps;
        validateCapacityForRequested(mtps, burstPeriodMs);
        long capacity =
                (mtps * NTPS_PER_MTPS * CAPACITY_UNITS_PER_NANO_TXN) / 1_000 * burstPeriodMs;
        bucket = new DiscreteLeakyBucket(capacity);
        if (bucket.totalCapacity() < CAPACITY_UNITS_PER_TXN) {
            throw new IllegalArgumentException(
                    "A throttle with "
                            + mtps
                            + " MTPS and "
                            + burstPeriodMs
                            + "ms burst period can never allow a transaction");
        }
    }

    private void validateCapacityForRequested(long mtps, long burstPeriodMs) {
        if (productWouldOverflow(mtps, NTPS_PER_MTPS * CAPACITY_UNITS_PER_NANO_TXN)) {
            throw new IllegalArgumentException(
                    "Base bucket capacity calculation outside numeric range");
        }
        final var unscaledCapacity = mtps * NTPS_PER_MTPS * CAPACITY_UNITS_PER_NANO_TXN / 1_000;
        if (productWouldOverflow(unscaledCapacity, burstPeriodMs)) {
            throw new IllegalArgumentException(
                    "Scaled bucket capacity calculation outside numeric range");
        }
    }

    boolean allow(int n, long elapsedNanos) {
        leakFor(elapsedNanos);
        return allowInstantaneous(n);
    }

    void leakFor(long elapsedNanos) {
        final var leakedUnits = effectiveLeak(elapsedNanos);
        bucket.leak(leakedUnits);
    }

    boolean allowInstantaneous(int n) {
        if (productWouldOverflow(n, CAPACITY_UNITS_PER_TXN)) {
            return false;
        }
        long requiredUnits = n * CAPACITY_UNITS_PER_TXN;
        if (requiredUnits > bucket.capacityFree()) {
            return false;
        }

        bucket.useCapacity(requiredUnits);
        lastAllowedUnits += requiredUnits;
        return true;
    }

    /**
     * Returns the percent of the throttle bucket's capacity that is used, given some number of
     * nanoseconds have elapsed since the last capacity test.
     *
     * @param givenElapsedNanos time since last test
     * @return the percent of the bucket that is used
     */
    double percentUsed(final long givenElapsedNanos) {
        final var used = bucket.capacityUsed();
        return 100.0
                * (used - Math.min(used, effectiveLeak(givenElapsedNanos)))
                / bucket.totalCapacity();
    }

    private long effectiveLeak(final long elapsedNanos) {
        return productWouldOverflow(elapsedNanos, mtps)
                ? bucket.totalCapacity()
                : elapsedNanos * mtps;
    }

    void resetLastAllowedUse() {
        lastAllowedUnits = 0;
    }

    void reclaimLastAllowedUse() {
        bucket.leak(lastAllowedUnits);
        lastAllowedUnits = 0;
    }

    DiscreteLeakyBucket bucket() {
        return bucket;
    }

    long mtps() {
        return mtps;
    }
}
