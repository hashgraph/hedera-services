package com.hedera.services.bdd.spec.utilops;

/*-
 * ‌
 * Hedera Services Test Clients
 * ​
 * Copyright (C) 2018 - 2020 Hedera Hashgraph, LLC
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

import com.google.common.base.Stopwatch;
import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.spec.HapiSpecOperation;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.Assert;

import java.util.concurrent.TimeUnit;
import java.util.function.DoubleSupplier;
import java.util.function.IntSupplier;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

import static com.google.common.base.Stopwatch.createStarted;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

public class RunLoadTest extends UtilOp {
	private static final Logger log = LogManager.getLogger(RunLoadTest.class);
	private static final int DEFAULT_SECS_ALLOWED_BELOW_TOLERANCE = 0;
	private static final int DEFAULT_TPS_TARGET = 500;
	private static final int DEFAULT_TPS_TOLERANCE_PERCENTAGE = 5;
	private static final long DEFAULT_DURATION = 30;
	private static final TimeUnit DEFAULT_DURATION_UNIT = TimeUnit.SECONDS;

	private DoubleSupplier targetTps = () -> DEFAULT_TPS_TARGET;
	private IntSupplier tpsTolerancePercentage = () -> DEFAULT_TPS_TOLERANCE_PERCENTAGE;
	private IntSupplier secsAllowedBelowTolerance = () -> DEFAULT_SECS_ALLOWED_BELOW_TOLERANCE;
	private LongSupplier testDuration = () -> DEFAULT_DURATION;
	private Supplier<TimeUnit> ofUnit = () -> DEFAULT_DURATION_UNIT;

	private final Supplier<HapiSpecOperation[]> opSource;

	private int numberOfThreads = 1;

	public RunLoadTest tps(DoubleSupplier targetTps) {
		this.targetTps = targetTps;
		log.info("targetTps: {}", this.targetTps);
		return this;
	}

	public RunLoadTest tolerance(IntSupplier tpsTolerance) {
		this.tpsTolerancePercentage = tpsTolerance;
		log.info("tpsTolerancePercentage: {}", this.tpsTolerancePercentage);
		return this;
	}

	public RunLoadTest allowedSecsBelow(IntSupplier allowedSecsBelow) {
		this.secsAllowedBelowTolerance = allowedSecsBelow;
		log.info("secsAllowedBelowTolerance: {}", this.secsAllowedBelowTolerance);
		return this;
	}

	public RunLoadTest setNumberOfThreads(int numberOfThreads) {
		this.numberOfThreads = numberOfThreads;
		log.info("numberOfThreads: {}", this.numberOfThreads);
		return this;
	}

	public RunLoadTest lasting(LongSupplier duration, Supplier<TimeUnit> ofUnit) {
		this.testDuration = duration;
		this.ofUnit = ofUnit;
		log.info("testDuration: {} {}", this.testDuration, this.ofUnit.get());
		return this;
	}

	public RunLoadTest(Supplier<HapiSpecOperation[]> opSource) {
		this.opSource = opSource;
	}

	@Override
	protected boolean submitOp(HapiApiSpec spec) {
		return threadMode(spec);
	}

	protected boolean threadMode(HapiApiSpec spec) {
		Thread[] threadClients = new Thread[numberOfThreads];

		// Dynamically instantiate test case thread and pass arguments to it
		for (int k = 0; k < numberOfThreads; k++) {
			threadClients[k] = new Thread(() -> testRun(spec));
			threadClients[k].setName("thread" + k);
		}

		for (int k = 0; k < numberOfThreads; k++) {
			threadClients[k].start();
		}
		for (int k = 0; k < numberOfThreads; k++) {
			try {
				threadClients[k].join();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
		return false;
	}

	void testRun(HapiApiSpec spec) {
		double _targetTps = targetTps.getAsDouble();
		long _testDuration = testDuration.getAsLong();
		TimeUnit _ofUnit = ofUnit.get();
		int totalOps = 0;
		float currentTPS = 0;
		Stopwatch duration = createStarted();
		long defaultSleepMS = (long) (opSource.get().length * 1000 / _targetTps);

		boolean reported = false;
		while (duration.elapsed(_ofUnit) < _testDuration) {
			int numOpsThen = spec.numLedgerOps();
			HapiSpecOperation[] ops = opSource.get();
			allRunFor(spec, ops);
			int numOpsNow = spec.numLedgerOps();
			//should not use spec.numLedgerOps() since spec shared by all load test threads
			//log.info("size {} Added {}", ops.length, (numOpsNow - numOpsThen));
			totalOps += ops.length;

			long elapsedMS = duration.elapsed(MILLISECONDS);
			currentTPS = totalOps / (elapsedMS * 0.001f);
			//log.info("Thread {} elapsedMS {} totalOps {} currentTPS {} ", Thread.currentThread().getName(), elapsedMS, totalOps, currentTPS);

			if (duration.elapsed(SECONDS) % 10 == 0) { //report periodically
				if (!reported) {
					log.info("Thread {} ops {} current TPS {}", Thread.currentThread().getName(),
							totalOps, currentTPS);
					reported = true;
					totalOps = 0;
					duration = createStarted();
				}
			} else {
				reported = false;
			}
			try {
				if (currentTPS > _targetTps) {
					long pauseMillieSeconds = (long) ((totalOps / (float) _targetTps) * 1000 - elapsedMS);
					//log.info("Thread {} pauseMillieSeconds {}", Thread.currentThread().getName(), pauseMillieSeconds);
					Thread.sleep(Math.max(5, pauseMillieSeconds));
				}
			} catch (InterruptedException irrelevant) {
			}
		}
		log.info("Thread {} final ops {} in {} seconds, TPS {} ", Thread.currentThread().getName(),
				totalOps, duration.elapsed(SECONDS), currentTPS);
	}
}
