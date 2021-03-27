package com.hedera.services.throttles;

import org.junit.jupiter.api.Test;

import static com.hedera.services.throttles.BucketThrottle.CAPACITY_UNITS_PER_NANO_TXN;
import static com.hedera.services.throttles.BucketThrottle.CAPACITY_UNITS_PER_TXN;
import static com.hedera.services.throttles.BucketThrottle.MTPS_PER_TPS;
import static com.hedera.services.throttles.BucketThrottle.NTPS_PER_MTPS;
import static org.junit.jupiter.api.Assertions.*;

class BucketThrottleTest {
	static final long NTPS_PER_TPS = 1_000_000_000L;

	@Test
	void factoryRejectsNeverPassableThrottle() {
		// expect:
		assertThrows(IllegalArgumentException.class,
				() -> BucketThrottle.withMtpsAndBurstPeriod(499, 2));
	}

	@Test
	void factoriesResultInExpectedThrottles() {
		// setup:
		int tps = 1_000;
		long expectedCapacity = tps * NTPS_PER_TPS * CAPACITY_UNITS_PER_NANO_TXN;

		// given:
		var fromTps = BucketThrottle.withTps(tps);
		var fromMtps = BucketThrottle.withMtps(tps * MTPS_PER_TPS);
		var fromTpsAndBurstPeriod = BucketThrottle.withTpsAndBurstPeriod(tps / 2, 2);
		var fromMtpsAndBurstPeriod = BucketThrottle.withMtpsAndBurstPeriod(tps / 2 * MTPS_PER_TPS, 2);

		// expect:
		assertEquals(expectedCapacity, fromTps.bucket().totalCapacity());
		assertEquals(expectedCapacity, fromMtps.bucket().totalCapacity());
		assertEquals(expectedCapacity, fromTpsAndBurstPeriod.bucket().totalCapacity());
		assertEquals(expectedCapacity, fromMtpsAndBurstPeriod.bucket().totalCapacity());
		// and:
		assertEquals(tps * MTPS_PER_TPS, fromTps.mtps());
		assertEquals(tps * MTPS_PER_TPS, fromMtps.mtps());
		assertEquals(tps * MTPS_PER_TPS / 2, fromTpsAndBurstPeriod.mtps());
		assertEquals(tps * MTPS_PER_TPS / 2, fromMtpsAndBurstPeriod.mtps());
	}

	@Test
	void rejectsUnsupportedNumOfTransactions() {
		// setup:
		int maxTps = (int)(Long.MAX_VALUE / CAPACITY_UNITS_PER_TXN);

		// given:
		var subject = BucketThrottle.withTps(maxTps);

		// when:
		var shouldAllowUnsupportedTxns = subject.allow(maxTps + 123, 0);

		// then:
		assertFalse(shouldAllowUnsupportedTxns);
	}

	@Test
	void withExcessElapsedNanosCompletelyEmptiesBucket() {
		// setup:
		int tps = 1_000;

		// given:
		var subject = BucketThrottle.withTps(tps);

		// when:
		var shouldAllowAll = subject.allow(1000, Long.MAX_VALUE / (tps * 1_000) + 123);

		// then:
		assertTrue(shouldAllowAll);
		assertEquals(0, subject.bucket().capacityFree());
	}

	@Test
	void withZeroElapsedNanosSimplyAdjustsCapacityFree() {
		// setup:
		int tps = 1_000;
		long internalCapacity = tps * NTPS_PER_TPS * CAPACITY_UNITS_PER_NANO_TXN;

		// given:
		var subject = BucketThrottle.withTps(tps);

		// when:
		var shouldAllowHalf = subject.allow(tps / 2, 0L);

		// then:
		assertTrue(shouldAllowHalf);
		assertEquals(internalCapacity / 2, subject.bucket().capacityFree());
	}

	@Test
	void withZeroElapsedNanosRejectsUnavailableCapacity() {
		// setup:
		int tps = 1_000;
		long internalCapacity = tps * NTPS_PER_TPS * CAPACITY_UNITS_PER_NANO_TXN;

		// given:
		var subject = BucketThrottle.withTps(tps);

		// when:
		var shouldntAllowOverRate = subject.allow(tps + 1, 0L);

		// then:
		assertFalse(shouldntAllowOverRate);
		assertEquals(internalCapacity, subject.bucket().capacityFree());
	}

	@Test
	void scalesLeakRateByDesiredTps() {
		// setup:
		int tps = 1_000;
		long internalCapacity = tps * NTPS_PER_TPS * CAPACITY_UNITS_PER_NANO_TXN;

		// given:
		var subject = BucketThrottle.withTps(tps);
		// and:
		subject.bucket().resetUsed(internalCapacity);

		// when:
		var shouldAllowWithJustEnoughCapacity = subject.allow(1, NTPS_PER_TPS / tps);

		// then:
		assertTrue(shouldAllowWithJustEnoughCapacity);
		assertEquals(0, subject.bucket().capacityFree());
	}

	@Test
	void scalesLeakRateByDesiredMtps() {
		// setup:
		int mtps = 100;
		int burstPeriod = 10;
		long internalCapacity = mtps * NTPS_PER_MTPS * CAPACITY_UNITS_PER_NANO_TXN * burstPeriod;

		// given:
		var subject = BucketThrottle.withMtpsAndBurstPeriod(mtps, burstPeriod);
		// and:
		subject.bucket().resetUsed(internalCapacity);

		// when:
		var shouldAllowWithJustEnoughCapacity = subject.allow(1, 1000 * NTPS_PER_TPS / mtps);

		// then:
		assertTrue(shouldAllowWithJustEnoughCapacity);
		assertEquals(0, subject.bucket().capacityFree());
	}

	@Test
	void scalesLeakRateByDesiredTpsUnderOne() {
		// setup:
		int mtps = 500;
		int burstPeriod = 2;

		// given:
		var subject = BucketThrottle.withMtpsAndBurstPeriod(mtps, burstPeriod);
		// and:
		subject.bucket().resetUsed(mtps);

		// when:
		var shouldAllowWithJustEnoughCapacity = subject.allow(1, 1);

		// then:
		assertTrue(shouldAllowWithJustEnoughCapacity);
		assertEquals(0, subject.bucket().capacityFree());
	}

	@Test
	void canReclaimCapacity() {
		// setup:
		int mtps = 500;
		int burstPeriod = 4;
		long internalCapacity = mtps * NTPS_PER_MTPS * burstPeriod * CAPACITY_UNITS_PER_NANO_TXN;

		// given:
		var subject = BucketThrottle.withMtpsAndBurstPeriod(mtps, burstPeriod);

		// when:
		var firstDecision = subject.allow(1, 0L);
		var secondDecision = subject.allow(1, 0L);
		var thirdDecision = subject.allow(1, 0L);

		// then:
		assertTrue(firstDecision);
		assertTrue(secondDecision);
		assertFalse(thirdDecision);
		// and when:
		subject.reclaimLastAllowedUse();
		// then:
		assertEquals(internalCapacity / 2, subject.bucket().capacityFree());
	}

	@Test
	void hasExpectedMtps() {
		// setup:
		int mtps = 250;
		int burstPeriod = 4;
		// and:
		int numAllowed = 0;
		int numSeconds = 100;
		long numNanoseconds = numSeconds * NTPS_PER_TPS;

		// given:
		var subject = BucketThrottle.withMtpsAndBurstPeriod(mtps, burstPeriod);

		for (long i = 0; i < numNanoseconds / 1000; i++) {
			if (subject.allow(1, 1_000L)) {
				numAllowed++;
			}
		}

		// then:
		assertEquals(numAllowed, (numSeconds * mtps) / 1000);
	}

	@Test
	void hasExpectedTps() {
		// setup:
		int tps = 250;
		// and:
		int numAllowed = 0;
		int numSeconds = 100;
		int perDecision = 10;
		long numNanoseconds = numSeconds * NTPS_PER_TPS;

		// given:
		var subject = BucketThrottle.withTps(tps);

		long decisionPeriod = 1000;
		for (long i = 0; i < numNanoseconds / decisionPeriod; i++) {
			if (subject.allow(perDecision, decisionPeriod)) {
				numAllowed += perDecision;
			}
		}

		// then:
		double actual = 1.0 * numAllowed;
		double epsilon = 0.01;
		double expected = 1.0 * numSeconds * tps;
		assertEquals(1.0, actual / expected, epsilon);
	}
}
