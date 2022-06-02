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

import org.junit.jupiter.api.Assertions;

import java.time.Instant;
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

		Instant startTime = Instant.now();
		final long startNanos = System.nanoTime();
		final long[] addNanos = new long[] { 0 };


		for (int i = 0; i < threads; i++) {
			exec.execute(() -> {
				ready.countDown();
				try {
					start.await();
					while (!stopped.get()) {
						synchronized (subject) {

							// We need to handle time going backwards here, which was causing tests using
							// this to be flaky. It is possible for time to go backwards with ntp running on
							// your system.
							long toAdd = System.nanoTime() - startNanos;
							if (addNanos[0] >= toAdd) {
								continue;
							}

							addNanos[0] = toAdd;

							if (subject.allow(opsToRequest, startTime.plusNanos(addNanos[0]))) {
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
