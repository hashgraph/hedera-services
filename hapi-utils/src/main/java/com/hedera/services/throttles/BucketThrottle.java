package com.hedera.services.throttles;

/**
 * A throttle that enforces a milli-TPS-resolution transaction rate based
 * on the capacity remaining in a {@link DiscreteLeakyBucket} instance.
 * Throttling decisions cannot be made more frequently than once per
 * nanosecond. (One unit of capacity in the bucket is one-billionth of the
 * capacity needed to perform one-thousandth of a transaction.)
 *
 * This class is <b>not</b> thread-safe.
 *
 * The {@link BucketThrottle#allow(int, long)} method answers the question of
 * whether some positive integer number of transactions can be accepted a
 * given number of nanoseconds after the last call to this method.
 *
 * The throttle's behavior is controlled by two parameters,
 * <ol>
 *     <li>The <i>allowed transaction rate</i>, in units of either
 *     transactions-per-second (TPS) or millitransactions-per-second (MTPS).
 *     </li>
 *     <li>The <i>burst period</i>; that is, the maximum number of seconds of
 *     the allowed transaction rate which can be accepted in a sudden burst.
 *     </li>
 * </ol>
 *
 * The purpose of the MTPS unit is to allow the user to create a {@code BucketThrottle}
 * with allowed transaction rate below 1 TPS. However, if the user tries to construct
 * a {@code BucketThrottle} for which the allowed transaction rate multiplied
 * by the burst period still does not amount to a full transaction, the constructor
 * throws an {@code IllegalArgumentException}---the resulting instance could only
 * ever return {@code false} from {@link BucketThrottle#allow(int, long)}.
 */
public class BucketThrottle {
	private static final int DEFAULT_BURST_PERIOD = 1;

	public static long capacityUnitsPerTxn() {
		return CAPACITY_UNITS_PER_TXN;
	}

	static final long MTPS_PER_TPS = 1_000L;
	static final long NTPS_PER_MTPS = 1_000_000L;
	static final long CAPACITY_UNITS_PER_TXN = 1_000_000_000_000L;
	static final long CAPACITY_UNITS_PER_NANO_TXN = 1_000L;

	private final long mtps;
	private final DiscreteLeakyBucket bucket;

	private long lastAllowedUnits = 0L;

	static BucketThrottle withTps(int tps) {
		return new BucketThrottle(tps * MTPS_PER_TPS, DEFAULT_BURST_PERIOD);
	}

	static BucketThrottle withMtps(long mtps) {
		return new BucketThrottle(mtps, DEFAULT_BURST_PERIOD);
	}

	static BucketThrottle withTpsAndBurstPeriod(int tps, int burstPeriod) {
		return new BucketThrottle(tps * MTPS_PER_TPS, burstPeriod);
	}

	static BucketThrottle withMtpsAndBurstPeriod(long mtps, int burstPeriod) {
		return new BucketThrottle(mtps, burstPeriod);
	}

	private BucketThrottle(long mtps, int burstPeriod) {
		this.mtps = mtps;
		bucket = new DiscreteLeakyBucket(mtps * NTPS_PER_MTPS * burstPeriod * CAPACITY_UNITS_PER_NANO_TXN);
		if (bucket.totalCapacity() < CAPACITY_UNITS_PER_TXN) {
			throw new IllegalArgumentException("A throttle with " + mtps + " MTPS and "
					+ burstPeriod + "s burst period can never allow a transaction!");
		}
	}

	boolean allow(int n, long elapsedNanos) {
		long leakedUnits = elapsedNanos * mtps;
		if (leakedUnits < 0) {
			leakedUnits = bucket.totalCapacity();
		}
		bucket.leak(leakedUnits);

		long requiredUnits = n * CAPACITY_UNITS_PER_TXN;
		if (requiredUnits < 0 || requiredUnits > bucket.capacityFree()) {
			return false;
		}

		bucket.useCapacity(requiredUnits);
		lastAllowedUnits = requiredUnits;
		return true;
	}

	void reclaimLastAllowedUse() {
		bucket.leak(lastAllowedUnits);
	}

	DiscreteLeakyBucket bucket() {
		return bucket;
	}

	long mtps() {
		return mtps;
	}
}
