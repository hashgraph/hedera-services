package com.hedera.services.throttling.real;

import org.junit.jupiter.api.Test;

import static com.hedera.services.throttling.real.BucketThrottle.CAPACITY_UNITS_PER_NT;
import static com.hedera.services.throttling.real.BucketThrottle.MTPS_PER_TPS;
import static com.hedera.services.throttling.real.BucketThrottle.NTPS_PER_MTPS;
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
		long expectedCapacity = tps * NTPS_PER_TPS * CAPACITY_UNITS_PER_NT;

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
	void withZeroElapsedNanosSimplyAdjustsCapacityFree() {
		// setup:
		int tps = 1_000;
		long internalCapacity = tps * NTPS_PER_TPS * CAPACITY_UNITS_PER_NT;

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
		long internalCapacity = tps * NTPS_PER_TPS * CAPACITY_UNITS_PER_NT;

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
		long internalCapacity = tps * NTPS_PER_TPS * CAPACITY_UNITS_PER_NT;

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
		long internalCapacity = mtps * NTPS_PER_MTPS * CAPACITY_UNITS_PER_NT * burstPeriod;

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
		long internalCapacity = mtps * NTPS_PER_MTPS * burstPeriod;

		// given:
		var subject = BucketThrottle.withMtpsAndBurstPeriod(mtps, burstPeriod);
		// and:
		subject.bucket().resetUsed(internalCapacity);

		// when:
		var shouldAllowWithJustEnoughCapacity = subject.allow(1, 2 * NTPS_PER_TPS);

		// then:
		assertTrue(shouldAllowWithJustEnoughCapacity);
		assertEquals(0, subject.bucket().capacityFree());
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
