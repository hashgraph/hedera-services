package com.hedera.services.throttling.bootstrap;

import com.hedera.services.throttling.real.DeterministicThrottle;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class ConcurrentThrottleTestHelper {
	private final int threads;
	private final int lifetime;
	private final int opsToRequest;

	public ConcurrentThrottleTestHelper(int threads, int lifetime, int opsToRequest) {
		this.threads = threads;
		this.lifetime = lifetime;
		this.opsToRequest = opsToRequest;
	}

	public int successfulAllowsWhenRunWith(DeterministicThrottle subject) throws InterruptedException {
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
								allowed.getAndIncrement();
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
		TimeUnit.SECONDS.sleep(lifetime);
		stopped.set(true);
		done.await();

		exec.shutdown();

		return allowed.get();
	}
}
