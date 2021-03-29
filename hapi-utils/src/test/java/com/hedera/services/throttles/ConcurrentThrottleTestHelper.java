package com.hedera.services.throttles;

import org.junit.jupiter.api.Assertions;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class ConcurrentThrottleTestHelper {
	private final int threads;
	private final int lifetimeSecs;
	private final int opsToRequest;

	private int lastNumAllowed = 0;

	public ConcurrentThrottleTestHelper(int threads, int lifetimeSecs, int opsToRequest) {
		this.threads = threads;
		this.lifetimeSecs = lifetimeSecs;
		this.opsToRequest = opsToRequest;
	}

	public void assertTolerableTps(double expectedTps, double maxPerDeviation, int logicalToActualTxnRatio) {
		var actualTps = (1.0 * lastNumAllowed) / logicalToActualTxnRatio / lifetimeSecs;
		var percentDeviation = Math.abs(1.0 - actualTps / expectedTps) * 100.0;
		Assertions.assertEquals(0.0, percentDeviation, maxPerDeviation);
	}

	public void assertTolerableTps(double expectedTps, double maxPerDeviation) {
		var actualTps = (1.0 * lastNumAllowed) / lifetimeSecs;
		var percentDeviation = Math.abs(1.0 - actualTps / expectedTps) * 100.0;
		Assertions.assertEquals(0.0, percentDeviation, maxPerDeviation);
	}

	public int runWith(DeterministicThrottle subject) throws InterruptedException {
		AtomicInteger allowed = new AtomicInteger(0);
		AtomicBoolean stopped = new AtomicBoolean(false);

		var ready = new CountDownLatch(threads);
		var start = new CountDownLatch(1);
		var done = new CountDownLatch(threads);
		ExecutorService exec = Executors.newCachedThreadPool();

		for (int i = 0; i < threads; i++) {
			exec.execute(() -> {
				ready.countDown();
				try {
					start.await();
					while (!stopped.get()) {
						synchronized (subject) {
							if (subject.allow(opsToRequest)) {
								allowed.getAndAdd(opsToRequest);
							}
						}
					}
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
		TimeUnit.SECONDS.sleep(lifetimeSecs);
		stopped.set(true);
		done.await();

		exec.shutdown();

		return (lastNumAllowed = allowed.get());
	}
}
