package com.hedera.services.bdd.suites.perf;

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

import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.spec.infrastructure.OpProvider;
import com.hedera.services.bdd.spec.transactions.HapiTxnOp;
import com.hedera.services.bdd.suites.HapiApiSuite;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.SplittableRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.transactions.TxnUtils.NOISY_ALLOWED_STATUSES;
import static com.hedera.services.bdd.spec.transactions.TxnUtils.NOISY_RETRY_PRECHECKS;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.scheduleCreate;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromTo;
import static com.hedera.services.bdd.spec.utilops.LoadTest.initialBalance;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.runWithProvider;
import static com.hedera.services.bdd.suites.perf.PerfUtilOps.stdMgmtOf;
import static java.util.concurrent.TimeUnit.MINUTES;

public class ReadyToRunScheduledXfersLoad extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(ReadyToRunScheduledXfersLoad.class);

	private AtomicLong duration = new AtomicLong(Long.MAX_VALUE);
	private AtomicReference<TimeUnit> unit = new AtomicReference<>(MINUTES);
	private AtomicInteger maxOpsPerSec = new AtomicInteger(500);
	private SplittableRandom r = new SplittableRandom();

	public static void main(String... args) {
		new ReadyToRunScheduledXfersLoad().runSuiteSync();
	}

	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		return List.of(
				new HapiApiSpec[] {
						runReadyToRunXfers(),
				}
		);
	}

	private HapiApiSpec runReadyToRunXfers() {
		return defaultHapiSpec("RunReadyToRunXfers")
				.given(
						stdMgmtOf(duration, unit, maxOpsPerSec)
				).when(
						runWithProvider(readyToRunXfersFactory())
								.lasting(duration::get, unit::get)
								.maxOpsPerSec(maxOpsPerSec::get)
				).then(
				);
	}

	static String payingSender(int id) {
		return "payingSender" + id;
	}

	static String inertReceiver(int id) {
		return "inertReceiver" + id;
	}

	private Function<HapiApiSpec, OpProvider> readyToRunXfersFactory() {
		var numNonDefaultSenders = new AtomicInteger(0);
		var numInertReceivers = new AtomicInteger(0);

		return spec -> new OpProvider() {
			@Override
			public List<HapiSpecOperation> suggestedInitializers() {
				var ciProps = spec.setup().ciPropertiesMap();
				numNonDefaultSenders.set(ciProps.getInteger("numNonDefaultSenders"));
				numInertReceivers.set(ciProps.getInteger("numInertReceivers"));

				List<HapiSpecOperation> initializers = new ArrayList<>();
				for (int i = 0, n = numNonDefaultSenders.get(); i < n; i++) {
					initializers.add(
							cryptoCreate(payingSender(i))
									.balance(initialBalance.orElse(A_HUNDRED_HBARS) / numNonDefaultSenders.get())
									.withRecharging()
									.hasRetryPrecheckFrom(NOISY_RETRY_PRECHECKS)
					);
				}
				for (int i = 0, n = numInertReceivers.get(); i < n; i++) {
					initializers.add(
							cryptoCreate(inertReceiver(i))
									.balance(0L)
									.hasRetryPrecheckFrom(NOISY_RETRY_PRECHECKS)
					);
				}

				for (HapiSpecOperation op : initializers) {
					if (op instanceof HapiTxnOp) {
						((HapiTxnOp) op).hasRetryPrecheckFrom(NOISY_RETRY_PRECHECKS);
					}
				}

				return initializers;
			}

			@Override
			public Optional<HapiSpecOperation> get() {
				var sendingPayer = DEFAULT_PAYER;
				if (numNonDefaultSenders.get() > 0) {
					sendingPayer = payingSender(r.nextInt(numNonDefaultSenders.get()));
				}
				var receiver = FUNDING;
				if (numInertReceivers.get() > 0) {
					receiver = inertReceiver(r.nextInt(numInertReceivers.get()));
				}
				var innerOp = cryptoTransfer(tinyBarsFromTo(sendingPayer, receiver, 1L))
						.payingWith(sendingPayer)
						.signedBy(sendingPayer)
						.noLogging();
				var op = scheduleCreate("wrapper", innerOp)
						.rememberingNothing()
						.inheritingScheduledSigs()
						.hasKnownStatusFrom(NOISY_ALLOWED_STATUSES)
						.hasRetryPrecheckFrom(NOISY_RETRY_PRECHECKS)
						.noLogging()
						.deferStatusResolution();
				return Optional.of(op);
			}
		};
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}
