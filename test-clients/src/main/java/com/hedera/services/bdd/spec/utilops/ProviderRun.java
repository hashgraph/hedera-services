package com.hedera.services.bdd.spec.utilops;

/*-
 * ‌
 * Hedera Services Test Clients
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

import com.google.common.base.Stopwatch;
import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.spec.infrastructure.OpProvider;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.function.IntSupplier;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.inParallel;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

public class ProviderRun extends UtilOp {
	private static final Logger log = LogManager.getLogger(ProviderRun.class);

	private static final int DEFAULT_MAX_OPS_PER_SEC = Integer.MAX_VALUE;
	private static final int DEFAULT_MAX_PENDING_OPS = Integer.MAX_VALUE;
	private static final int DEFAULT_BACKLOG_SLEEPOFF_SECS = 1;
	private static final long DEFAULT_DURATION = 30;
	private static final TimeUnit DEFAULT_UNIT = TimeUnit.SECONDS;
	private static final int DEFAULT_TOTAL_OPS_TO_SUBMIT = -1;

	private final Function<HapiApiSpec, OpProvider> providerFn;
	private IntSupplier maxOpsPerSecSupplier = () -> DEFAULT_MAX_OPS_PER_SEC;
	private IntSupplier maxPendingOpsSupplier = () -> DEFAULT_MAX_PENDING_OPS;
	private IntSupplier backoffSleepSecsSupplier = () -> DEFAULT_BACKLOG_SLEEPOFF_SECS;
	private LongSupplier durationSupplier = () -> DEFAULT_DURATION;
	private Supplier<TimeUnit> unitSupplier = () -> DEFAULT_UNIT;
	private IntSupplier totalOpsToSubmit = () -> DEFAULT_TOTAL_OPS_TO_SUBMIT;

	private Map<HederaFunctionality, AtomicInteger> counts = new HashMap<>();

	public ProviderRun(Function<HapiApiSpec, OpProvider> providerFn) {
		this.providerFn = providerFn;
		Stream.of(HederaFunctionality.class.getEnumConstants()).forEach(type -> counts.put(type, new AtomicInteger()));
	}

	public ProviderRun lasting(LongSupplier durationSupplier, Supplier<TimeUnit> unitSupplier) {
		this.unitSupplier = unitSupplier;
		this.durationSupplier = durationSupplier;
		return this;
	}

	public ProviderRun totalOpsToSumbit(IntSupplier totalOpsSupplier) {
		this.totalOpsToSubmit = totalOpsSupplier;
		return this;
	}

	public ProviderRun maxOpsPerSec(IntSupplier maxOpsPerSecSupplier) {
		this.maxOpsPerSecSupplier = maxOpsPerSecSupplier;
		return this;
	}

	public ProviderRun maxPendingOps(IntSupplier maxPendingOpsSupplier) {
		this.maxPendingOpsSupplier = maxPendingOpsSupplier;
		return this;
	}

	public ProviderRun backoffSleepSecs(IntSupplier backoffSleepSecsSupplier) {
		this.backoffSleepSecsSupplier = backoffSleepSecsSupplier;
		return this;
	}

	@Override
	protected boolean submitOp(HapiApiSpec spec) {
		int MAX_N = Runtime.getRuntime().availableProcessors();
		int MAX_OPS_PER_SEC = maxOpsPerSecSupplier.getAsInt();
		int MAX_PENDING_OPS = maxPendingOpsSupplier.getAsInt();
		int BACKOFF_SLEEP_SECS = backoffSleepSecsSupplier.getAsInt();
		long duration = durationSupplier.getAsLong();
		OpProvider provider = providerFn.apply(spec);

		allRunFor(spec, provider.suggestedInitializers().toArray(new HapiSpecOperation[0]));
		log.info("Finished initialization for provider run...");

		TimeUnit unit = unitSupplier.get();
		Stopwatch stopwatch = Stopwatch.createStarted();

		final var remainingOpsToSubmit = new AtomicInteger(totalOpsToSubmit.getAsInt());
		final boolean fixedOpSubmission = (remainingOpsToSubmit.get() < 0) ? false : true;
		int submittedSoFar = 0;
		long durationMs = unit.toMillis(duration);
		long logIncrementMs = durationMs / 100;
		long nextLogTargetMs = logIncrementMs;
		long lastDeltaLogged = -1;
		final var opsThisSecond = new AtomicInteger(0);
		final var submissionBoundaryMs = new AtomicLong(stopwatch.elapsed(MILLISECONDS) + 1_000);
		while (stopwatch.elapsed(unit) < duration) {
			long elapsedMs = stopwatch.elapsed(MILLISECONDS);
			if (elapsedMs > submissionBoundaryMs.get()) {
				submissionBoundaryMs.getAndAdd(1_000);
				opsThisSecond.set(0);
			}
			int numPending = spec.numPendingOps();
			if (elapsedMs > nextLogTargetMs) {
				nextLogTargetMs += logIncrementMs;
				long delta = duration - stopwatch.elapsed(unit);
				if (delta != lastDeltaLogged) {
					log.info(delta + " "
							+ unit.toString().toLowerCase()
							+ (fixedOpSubmission ? (" or " + remainingOpsToSubmit + " ops ") : "")
							+ " left in test - "
							+ submittedSoFar
							+ " ops submitted so far ("
							+ numPending
							+ " pending).");
					log.info("Precheck txn status counts :: " + spec.precheckStatusCounts());
					log.info("Resolved txn status counts :: " + spec.finalizedStatusCounts());
					log.info("\n------------------------------\n");
					lastDeltaLogged = delta;
				}
			}

			if (fixedOpSubmission && remainingOpsToSubmit.get() <= 0) {
				if (numPending > 0) {
					continue;
				}
				log.info("Finished submission of total {} operations", totalOpsToSubmit.getAsInt());
				break;
			}
			if (numPending < MAX_PENDING_OPS) {
				HapiSpecOperation[] burst = IntStream
						.range(0, Math.min(MAX_N,
								fixedOpSubmission ? Math.min(remainingOpsToSubmit.get(),
										MAX_OPS_PER_SEC - opsThisSecond.get())
										: MAX_OPS_PER_SEC - opsThisSecond.get()))
						.mapToObj(ignore -> provider.get())
						.flatMap(Optional::stream)
						.peek(op -> counts.get(op.type()).getAndIncrement())
						.toArray(HapiSpecOperation[]::new);
				if (burst.length > 0) {
					allRunFor(spec, inParallel(burst));
					submittedSoFar += burst.length;
					if (fixedOpSubmission) {
						remainingOpsToSubmit.getAndAdd(-burst.length);
					}
					opsThisSecond.getAndAdd(burst.length);
				}
			} else {
				log.warn("Now " + numPending + " ops pending; backing off for " + BACKOFF_SLEEP_SECS + "s!");
				try {
					Thread.sleep(BACKOFF_SLEEP_SECS * 1_000L);
				} catch (InterruptedException ignore) {
				}
			}
		}

		Map<HederaFunctionality, Integer> finalCounts = counts.entrySet()
				.stream()
				.filter(entry -> entry.getValue().get() > 0)
				.collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().get()));
		log.info("Final breakdown of *provided* ops: " + finalCounts);
		log.info("Final breakdown of *resolved* statuses: " + spec.finalizedStatusCounts());


		return false;
	}
}
