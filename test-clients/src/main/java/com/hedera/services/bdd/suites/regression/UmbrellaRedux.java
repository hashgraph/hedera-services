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
import com.hedera.services.bdd.spec.HapiPropertySource;
import com.hedera.services.bdd.suites.HapiApiSuite;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.infrastructure.OpProvider.UNIQUE_PAYER_ACCOUNT;
import static com.hedera.services.bdd.spec.infrastructure.OpProvider.UNIQUE_PAYER_ACCOUNT_INITIAL_BALANCE;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.runWithProvider;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sleepFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sourcing;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.suites.regression.RegressionProviderFactory.factoryFrom;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

public class UmbrellaRedux extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(UmbrellaRedux.class);

	public static final String DEFAULT_PROPERTIES = "regression-mixed_ops.properties";

	private AtomicLong duration = new AtomicLong(1);
	private AtomicInteger maxOpsPerSec = new AtomicInteger(Integer.MAX_VALUE);
	private AtomicInteger maxPendingOps = new AtomicInteger(Integer.MAX_VALUE);
	private AtomicInteger backoffSleepSecs = new AtomicInteger(1);
	private AtomicInteger statusTimeoutSecs = new AtomicInteger(5);
	private AtomicReference<String> props = new AtomicReference<>(DEFAULT_PROPERTIES);
	private AtomicReference<TimeUnit> unit = new AtomicReference<>(MILLISECONDS);

	public static void main(String... args) {
		UmbrellaRedux umbrellaRedux = new UmbrellaRedux();
		umbrellaRedux.runSuiteSync();
	}

	@Override
	public List<HapiApiSpec> getSpecsInSuite() {
		return List.of(
				new HapiApiSpec[]{
					umbrellaRedux(),
				}
		);
	}

	private HapiApiSpec umbrellaRedux() {
		return defaultHapiSpec("UmbrellaRedux")
				.given(
						withOpContext((spec, opLog) -> {
							configureFromCi(spec);
							// use ci property statusTimeoutSecs to overwrite default value of status.wait.timeout.ms
							spec.addOverrideProperties(
									Map.of("status.wait.timeout.ms",
											Integer.toString(1_000 * statusTimeoutSecs.get())));
						}),
						cryptoCreate(UNIQUE_PAYER_ACCOUNT)
								.balance(UNIQUE_PAYER_ACCOUNT_INITIAL_BALANCE)
								.withRecharging()
								.via("createUniquePayer"),
						sleepFor(10000)
				).when(
						getTxnRecord("createUniquePayer").logged()
				).then(
						sourcing( () -> runWithProvider(factoryFrom(props::get))
								.lasting(duration::get, unit::get)
								.maxOpsPerSec(maxOpsPerSec::get)
								.maxPendingOps(maxPendingOps::get)
								.backoffSleepSecs(backoffSleepSecs::get)

						)
		);
	}

	private void configureFromCi(HapiApiSpec spec) {
		HapiPropertySource ciProps = spec.setup().ciPropertiesMap();
		if (ciProps.has("duration")) {
			duration.set(ciProps.getLong("duration"));
		}
		if (ciProps.has("unit")) {
			unit.set(ciProps.getTimeUnit("unit"));
		}
		if (ciProps.has("maxOpsPerSec")) {
			maxOpsPerSec.set(ciProps.getInteger("maxOpsPerSec"));
		}
		if (ciProps.has("props")) {
			props.set(ciProps.get("props"));
		}
		if (ciProps.has("maxPendingOps")) {
			maxPendingOps.set(ciProps.getInteger("maxPendingOps"));
		}
		if (ciProps.has("backoffSleepSecs")) {
			backoffSleepSecs.set(ciProps.getInteger("backoffSleepSecs"));
		}
		if (ciProps.has("statusTimeoutSecs")) {
			statusTimeoutSecs.set(ciProps.getInteger("statusTimeoutSecs"));
		}
		if (ciProps.has("secondsWaitingServerUp")) {
			statusTimeoutSecs.set(ciProps.getInteger("secondsWaitingServerUp"));
		}
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}
