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
import com.hedera.services.bdd.spec.infrastructure.meta.ContractResources;
import com.hedera.services.bdd.spec.utilops.LoadTest;
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
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileCreate;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.logIt;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.BUSY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.DUPLICATE_TRANSACTION;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.PLATFORM_TRANSACTION_NOT_CREATED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.UNKNOWN;

public class ContractBigArrayLoadTest extends LoadTest {
	private static final Logger log = LogManager.getLogger(ContractBigArrayLoadTest.class);
	private static int sizeInKb = 4;

	public static void main(String... args) {
		int usedArgs = parseArgs(args);

		// parsing local argument specific to this test
		if (args.length > usedArgs) {
			sizeInKb = Integer.parseInt(args[usedArgs]);
			log.info("Set sizeInKb as " + sizeInKb);
		}

		/* Has a static initializer whose behavior seems influenced by initialization of ForkJoinPool#commonPool. */
		new org.ethereum.crypto.HashUtil();

		ContractBigArrayLoadTest suite = new ContractBigArrayLoadTest();
		suite.setReportStats(true);
		suite.runSuiteSync();
	}

	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		return List.of(runContractCalls());
	}

	@Override
	public boolean hasInterestingStats() {
		return true;
	}

	private HapiApiSpec runContractCalls() {
		PerfTestLoadSettings settings = new PerfTestLoadSettings();
		final AtomicInteger submittedSoFar = new AtomicInteger(0);
		long setValue = 0x1234abdeL;
		Supplier<HapiSpecOperation[]> callBurst = () -> new HapiSpecOperation[] {
				contractCall("perf", ContractResources.BIG_ARRAY_CHANGE_ARRAY_ABI, setValue)
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
						fileCreate("contractBytecode").path(ContractResources.BIG_ARRAY_BYTECODE_PATH)
								.hasRetryPrecheckFrom(BUSY, DUPLICATE_TRANSACTION, PLATFORM_TRANSACTION_NOT_CREATED),
						contractCreate("perf").bytecode("contractBytecode")
								.hasRetryPrecheckFrom(BUSY, DUPLICATE_TRANSACTION, PLATFORM_TRANSACTION_NOT_CREATED),
						getContractInfo("perf").hasExpectedInfo().logged(),

						// Initialize storage size
						contractCall("perf", ContractResources.BIG_ARRAY_SET_SIZE_IN_KB_ABI, sizeInKb)
								.hasRetryPrecheckFrom(BUSY, DUPLICATE_TRANSACTION, PLATFORM_TRANSACTION_NOT_CREATED)
								.gas(600000)

				).then(
						defaultLoadTest(callBurst, settings)
				);
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}
