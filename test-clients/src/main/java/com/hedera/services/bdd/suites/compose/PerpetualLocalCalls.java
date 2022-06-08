package com.hedera.services.bdd.suites.compose;

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
import com.hedera.services.bdd.suites.HapiApiSuite;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.math.BigInteger;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.assertions.ContractFnResultAsserts.isLiteralResult;
import static com.hedera.services.bdd.spec.assertions.ContractFnResultAsserts.resultWith;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.contractCallLocal;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.uploadInitCode;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.runWithProvider;
import static com.hedera.services.bdd.suites.contract.Utils.FunctionType.FUNCTION;
import static com.hedera.services.bdd.suites.contract.Utils.getABIFor;
import static java.util.concurrent.TimeUnit.MINUTES;

public class PerpetualLocalCalls extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(PerpetualLocalCalls.class);

	private AtomicLong duration = new AtomicLong(Long.MAX_VALUE);
	private AtomicReference<TimeUnit> unit = new AtomicReference<>(MINUTES);
	private AtomicInteger maxOpsPerSec = new AtomicInteger(100);
	private AtomicInteger totalBeforeFailure = new AtomicInteger(0);

	public static void main(String... args) {
		new PerpetualLocalCalls().runSuiteSync();
	}

	@Override
	public List<HapiApiSpec> getSpecsInSuite() {
		return List.of(
				new HapiApiSpec[] {
						localCallsForever(),
				}
		);
	}

	private HapiApiSpec localCallsForever() {
		return defaultHapiSpec("LocalCallsForever").
				given().when().then(
						runWithProvider(localCallsFactory())
								.lasting(duration::get, unit::get)
								.maxOpsPerSec(maxOpsPerSec::get)
				);
	}

	private Function<HapiApiSpec, OpProvider> localCallsFactory() {
		return spec -> new OpProvider() {
			@Override
			public List<HapiSpecOperation> suggestedInitializers() {
				return List.of(
						uploadInitCode("ChildStorage"),
						contractCreate("ChildStorage")
				);
			}

			@Override
			public Optional<HapiSpecOperation> get() {
				var op = contractCallLocal("ChildStorage", "getMyValue")
								.noLogging()
								.has(resultWith().resultThruAbi(
										getABIFor(FUNCTION, "getMyValue", "ChildStorage"),
										isLiteralResult(new Object[] { BigInteger.valueOf(73) })));
				var soFar = totalBeforeFailure.getAndIncrement();
				if (soFar % 1000 == 0) {
					log.info("--- " + soFar);
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
