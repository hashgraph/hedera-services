package com.hedera.services.bdd.suites.perf.contract.opcodes;

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
import com.hedera.services.bdd.spec.utilops.LoadTest;
import com.hedera.services.bdd.suites.perf.PerfTestLoadSettings;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getContractInfo;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.uploadSingleInitCode;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.logIt;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.BUSY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.DUPLICATE_TRANSACTION;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.PLATFORM_TRANSACTION_NOT_CREATED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.UNKNOWN;

public class SStoreOperationLoadTest extends LoadTest {
	private static final Logger log = LogManager.getLogger(SStoreOperationLoadTest.class);
	private static int size = 4;

	public static void main(String... args) {
		int usedArgs = parseArgs(args);

		// parsing local argument specific to this test
		if (args.length > usedArgs) {
			size = Integer.parseInt(args[usedArgs]);
			log.info("Set sizeInKb as " + size);
		}

		/* Has a static initializer whose behavior seems influenced by initialization of ForkJoinPool#commonPool. */
		new org.ethereum.crypto.HashUtil();

		SStoreOperationLoadTest suite = new SStoreOperationLoadTest();
		suite.runSuiteSync();
	}

	@Override
	public List<HapiApiSpec> getSpecsInSuite() {
		return List.of(runContractCalls());
	}

	private HapiApiSpec runContractCalls() {
		final var contract = "BigArray";
		PerfTestLoadSettings settings = new PerfTestLoadSettings();
		final AtomicInteger submittedSoFar = new AtomicInteger(0);
		long setValue = 0x1234abdeL;
		Supplier<HapiSpecOperation[]> callBurst = () -> new HapiSpecOperation[] {
				contractCall(contract, "changeArray", setValue)
						.noLogging()
						.payingWith("sender")
						.suppressStats(true)
						.hasKnownStatusFrom(UNKNOWN, SUCCESS)
						.hasRetryPrecheckFrom(BUSY, DUPLICATE_TRANSACTION, PLATFORM_TRANSACTION_NOT_CREATED)
						.deferStatusResolution()
		};

		return defaultHapiSpec("runContractCalls")
				.given(
						withOpContext((spec, ignore) -> settings.setFrom(spec.setup().ciPropertiesMap())),
						logIt(ignore -> settings.toString())
				).when(
						cryptoCreate("sender").balance(initialBalance.getAsLong())
								.withRecharging()
								.rechargeWindow(3)
								.hasRetryPrecheckFrom(BUSY, DUPLICATE_TRANSACTION, PLATFORM_TRANSACTION_NOT_CREATED),
						uploadSingleInitCode(contract, BUSY, DUPLICATE_TRANSACTION, PLATFORM_TRANSACTION_NOT_CREATED),
						contractCreate(contract)
								.hasRetryPrecheckFrom(BUSY, DUPLICATE_TRANSACTION, PLATFORM_TRANSACTION_NOT_CREATED),
						getContractInfo(contract).hasExpectedInfo().logged(),

						// Initialize storage size
						contractCall(contract, "setSize", size)
								.hasRetryPrecheckFrom(BUSY, DUPLICATE_TRANSACTION, PLATFORM_TRANSACTION_NOT_CREATED)
								.gas(300_000)
				).then(
						defaultLoadTest(callBurst, settings)
				);
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}
