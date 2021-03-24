package com.hedera.services.throttling.real;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static com.hedera.services.throttling.real.BucketThrottle.CAPACITY_UNITS_PER_NT;
import static com.hedera.services.throttling.real.BucketThrottle.CAPACITY_UNITS_PER_TXN;
import static com.hedera.services.throttling.real.BucketThrottle.MTPS_PER_TPS;
import static com.hedera.services.throttling.real.BucketThrottle.NTPS_PER_MTPS;
import static com.hedera.services.throttling.real.BucketThrottleTest.NTPS_PER_TPS;
import static org.junit.jupiter.api.Assertions.*;

class DeterministicThrottleTest {
	@Test
	void factoriesWork() {
		// setup:
		int tps = 1_000;
		long expectedCapacity = tps * NTPS_PER_TPS * CAPACITY_UNITS_PER_NT;

		// given:
		var fromTps = DeterministicThrottle.withTps(tps);
		var fromMtps = DeterministicThrottle.withMtps(tps * MTPS_PER_TPS);
		var fromTpsAndBurstPeriod = DeterministicThrottle.withTpsAndBurstPeriod(tps / 2, 2);
		var fromMtpsAndBurstPeriod = DeterministicThrottle.withMtpsAndBurstPeriod(tps / 2 * MTPS_PER_TPS, 2);

		// expect:
		assertEquals(expectedCapacity, fromTps.delegate().bucket().totalCapacity());
		assertEquals(expectedCapacity, fromMtps.delegate().bucket().totalCapacity());
		assertEquals(expectedCapacity, fromTpsAndBurstPeriod.delegate().bucket().totalCapacity());
		assertEquals(expectedCapacity, fromMtpsAndBurstPeriod.delegate().bucket().totalCapacity());
		// and:
		assertEquals(tps * MTPS_PER_TPS, fromTps.delegate().mtps());
		assertEquals(tps * MTPS_PER_TPS, fromMtps.delegate().mtps());
		assertEquals(tps * MTPS_PER_TPS / 2, fromTpsAndBurstPeriod.delegate().mtps());
		assertEquals(tps * MTPS_PER_TPS / 2, fromMtpsAndBurstPeriod.delegate().mtps());
		// and:
		assertNull(fromTps.lastDecisionTime());
		assertNull(fromMtps.lastDecisionTime());
		assertNull(fromTpsAndBurstPeriod.lastDecisionTime());
		assertNull(fromMtpsAndBurstPeriod.lastDecisionTime());
	}

	@Test
	void usesZeroElapsedNanosOnFirstDecision() {
		// setup:
		int tps = 1;
		int burstPeriod = 5;
		long internalCapacity = tps * NTPS_PER_TPS * CAPACITY_UNITS_PER_NT * burstPeriod;
		Instant now = Instant.ofEpochSecond(1_234_567L);

		// given:
		var subject = DeterministicThrottle.withTpsAndBurstPeriod(tps, burstPeriod);

		// when:
		var result = subject.allow(1, now);

		// then:
		assertTrue(result);
		assertSame(now, subject.lastDecisionTime());
		assertEquals(internalCapacity - CAPACITY_UNITS_PER_TXN, subject.delegate().bucket().capacityFree());
	}

	@Test
	void canBeUsedWithoutExplicitTime() throws InterruptedException {
		// setup:
		int tps = 42;
		int threads = 3;
		int secsToTry = 5;
		int txnsPerAttempt = 2;
		// and:
		AtomicInteger allowed = new AtomicInteger(0);
		AtomicBoolean stopped = new AtomicBoolean(false);

		// given:
		var subject = DeterministicThrottle.withTps(tps);

		// when:
		var ready = new CountDownLatch(threads);
		var start = new CountDownLatch(1);
		var done = new CountDownLatch(threads);
		ExecutorService exec = Executors.newCachedThreadPool();
		// and:
		for (int i = 0; i < threads; i++) {
			exec.execute(() -> {
				ready.countDown();
				try {
					start.await();
					while (!stopped.get()) {
						synchronized (subject) {
							if (subject.allow(txnsPerAttempt)) {
								allowed.getAndAdd(txnsPerAttempt);
							}
						}
					}
					System.out.println("Thread " + Thread.currentThread().getName() + " sees stopped=true");
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				} finally {
					done.countDown();
				}
			});
		}

		// and:
		ready.await();
		start.countDown();
		System.out.println("Now sleeping " + secsToTry + " seconds...");
		TimeUnit.SECONDS.sleep(secsToTry);
		System.out.println("...awake!");
		stopped.set(true);
		done.await();

		exec.shutdown();

		// then:
		System.out.println("Allowed " + allowed.get() + " txns in " + secsToTry + "s w/ " + threads + " threads.");
	}

	@Test
	void requiresMonotonicIncreasingTimeline() {
		// setup:
		int tps = 1;
		int burstPeriod = 5;
		Instant now = Instant.ofEpochSecond(1_234_567L);

		// given:
		var subject = DeterministicThrottle.withTpsAndBurstPeriod(tps, burstPeriod);

		// when:
		subject.allow(1, now);

		// then:
		assertThrows(IllegalArgumentException.class, () -> subject.allow(1, now.minusNanos(1)));
		assertDoesNotThrow(() -> subject.allow(1, now));
	}

	@Test
	void usesCorrectElapsedNanosOnSubsequentDecision() {
		// setup:
		int tps = 1;
		int burstPeriod = 5;
		long internalCapacity = tps * NTPS_PER_TPS * CAPACITY_UNITS_PER_NT * burstPeriod;
		long elapsedNanos = 1_234;
		Instant originalDecision = Instant.ofEpochSecond(1_234_567L, 0);
		Instant now = Instant.ofEpochSecond(1_234_567L, elapsedNanos);

		// given:
		var subject = DeterministicThrottle.withTpsAndBurstPeriod(tps, burstPeriod);

		// when:
		subject.allow(1, originalDecision);
		// and:
		var result = subject.allow(1, now);

		// then:
		assertTrue(result);
		assertSame(now, subject.lastDecisionTime());
		assertEquals(
				internalCapacity - 2 * CAPACITY_UNITS_PER_TXN + 1_000 * elapsedNanos,
				subject.delegate().bucket().capacityFree());
	}

	@Test
	void returnsExpectedState() {
		// setup:
		int mtps = 333;
		int burstPeriod = 6;
		long internalCapacity = mtps * NTPS_PER_MTPS * CAPACITY_UNITS_PER_NT * burstPeriod;
		Instant originalDecision = Instant.ofEpochSecond(1_234_567L, 0);

		// given:
		var subject = DeterministicThrottle.withMtpsAndBurstPeriod(mtps, burstPeriod);

		// when:
		subject.allow(1, originalDecision);
		// and:
		var state = subject.usageSnapshot();

		// then:
		assertEquals(internalCapacity, state.capacity());
		assertEquals(CAPACITY_UNITS_PER_TXN, state.used());
		assertEquals(originalDecision, state.lastDecisionTime());
	}

	@Test
	void resetsAsExpected() {
		// setup:
		int mtps = 333;
		int burstPeriod = 6;
		long internalCapacity = mtps * NTPS_PER_MTPS * CAPACITY_UNITS_PER_NT * burstPeriod;
		long used = internalCapacity / 2;
		Instant originalDecision = Instant.ofEpochSecond(1_234_567L, 0);

		// given:
		var subject = DeterministicThrottle.withMtpsAndBurstPeriod(mtps, burstPeriod);
		// and:
		var snapshot = new DeterministicThrottle.UsageSnapshot(used, internalCapacity, originalDecision);

		// when:
		subject.resetUsageTo(snapshot);

		// then:
		assertEquals(used, subject.delegate().bucket().capacityUsed());
		assertEquals(originalDecision, subject.lastDecisionTime());
	}

	@Test
	void refusesToResetToSnapshotOfThrottleWithDifferentCapacity() {
		// setup:
		int mtps = 333;
		int burstPeriod = 6;
		long internalCapacity = mtps * NTPS_PER_MTPS * CAPACITY_UNITS_PER_NT * burstPeriod;
		long used = internalCapacity / 2;
		Instant originalDecision = Instant.ofEpochSecond(1_234_567L, 0);

		// given:
		var subject = DeterministicThrottle.withMtpsAndBurstPeriod(mtps, burstPeriod);
		// and:
		var snapshot = new DeterministicThrottle.UsageSnapshot(used, internalCapacity - 1, originalDecision);

		// expect:
		assertThrows(IllegalArgumentException.class, () -> subject.resetUsageTo(snapshot));
	}
}
