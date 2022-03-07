package com.hedera.services.bdd.suites.regression;

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
import com.hedera.services.bdd.spec.transactions.TxnUtils;
import com.hedera.services.bdd.suites.HapiApiSuite;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.scheduleCreate;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromTo;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.runWithProvider;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.suites.utils.sysfiles.serdes.ThrottleDefsLoader.protoDefsFromResource;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.BUSY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static java.util.concurrent.TimeUnit.SECONDS;

public class SplittingThrottlesWorks extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(SplittingThrottlesWorks.class);

	private static final int scheduleCreatesPerCryptoCreate = 12;
	private AtomicLong duration = new AtomicLong(120);
	private AtomicReference<TimeUnit> unit = new AtomicReference<>(SECONDS);
	private AtomicInteger maxOpsPerSec = new AtomicInteger(4 * scheduleCreatesPerCryptoCreate + 2);

	public static void main(String... args) {
		new SplittingThrottlesWorks().runSuiteSync();
	}

	@Override
	public List<HapiApiSpec> getSpecsInSuite() {
		return List.of(
				new HapiApiSpec[] {
						setNewLimits(),
						tryCreations(),
				}
		);
	}

	private HapiApiSpec setNewLimits() {
		var artificialLimits = protoDefsFromResource("testSystemFiles/split-throttles.json");

		return defaultHapiSpec("SetNewLimits")
				.given().when().then(
						fileUpdate(THROTTLE_DEFS)
								.payingWith(EXCHANGE_RATE_CONTROL)
								.contents(artificialLimits.toByteArray())
				);
	}

	private HapiApiSpec tryCreations() {
		return defaultHapiSpec("TryCreations")
				.given().when(
						runWithProvider(cryptoCreateOps())
								.lasting(duration::get, unit::get)
								.maxOpsPerSec(maxOpsPerSec::get)
				).then(
						withOpContext((spec, opLog) -> {
							var actualTps = 1.0 * spec.finalAdhoc() / duration.get();
							opLog.info("Total ops accepted in {} {} = {} ==> {}tps",
									duration.get(),
									unit.get(),
									spec.finalAdhoc(),
									actualTps);
						})
				);
	}

	private Function<HapiApiSpec, OpProvider> cryptoCreateOps() {
		var i = new AtomicInteger(0);

		return spec -> new OpProvider() {
			@Override
			public List<HapiSpecOperation> suggestedInitializers() {
				return List.of(
						cryptoCreate("civilian")
								.payingWith(GENESIS)
								.balance(ONE_MILLION_HBARS)
								.withRecharging()
				);
			}

			@Override
			public Optional<HapiSpecOperation> get() {
				HapiSpecOperation op;
				final var nextI = i.getAndIncrement();
				if (nextI % (scheduleCreatesPerCryptoCreate + 1) == 0) {
					op = cryptoCreate("w/e" + nextI)
							.noLogging()
							.deferStatusResolution()
							.payingWith("civilian")
							.hasPrecheckFrom(OK, BUSY);
				} else {
					op = scheduleCreate("scheduleW/e" + nextI,
							cryptoTransfer(tinyBarsFromTo("civilian", FUNDING, 1))
									.memo(TxnUtils.randomAlphaNumeric(32))
									.hasPrecheckFrom(STANDARD_PERMISSIBLE_PRECHECKS)
					)
							.noLogging()
							.deferStatusResolution()
							.payingWith("civilian")
							.hasPrecheckFrom(OK, BUSY);
				}
				return Optional.of(op);
			}
		};
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}
