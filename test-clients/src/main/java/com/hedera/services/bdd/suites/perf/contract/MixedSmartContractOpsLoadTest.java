package com.hedera.services.bdd.suites.perf.contract;

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
import static com.hedera.services.bdd.spec.transactions.TxnUtils.randomUtf8Bytes;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractUpdate;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.logIt;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;

public class MixedSmartContractOpsLoadTest extends LoadTest {
	private static final Logger log = LogManager.getLogger(MixedSmartContractOpsLoadTest.class);
	private final String CONTRACT_NAME_PREFIX = "testContract";

	public static void main(String... args) {
		parseArgs(args);

		MixedSmartContractOpsLoadTest suite = new MixedSmartContractOpsLoadTest();
		suite.setReportStats(true);
		suite.runSuiteSync();
	}

	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		return	List.of(
				RunMixedSmartContractOps()
		);
	}

	@Override
	public boolean hasInterestingStats() {
		return true;
	}

	protected HapiApiSpec RunMixedSmartContractOps() {
		PerfTestLoadSettings settings = new PerfTestLoadSettings();
		final AtomicInteger createdSoFar = new AtomicInteger(0);
		final byte[] memo = randomUtf8Bytes(memoLength.getAsInt());

		Supplier<HapiSpecOperation[]> mixedOpsBurst = () -> new HapiSpecOperation[] {
				contractCreate(CONTRACT_NAME_PREFIX + createdSoFar.getAndIncrement())
						.payingWith(GENESIS)
						.bytecode("byteCode")
						.entityMemo(new String(memo))
						.noLogging()
						.hasAnyPrecheck()
						.deferStatusResolution(),

				getContractInfo(CONTRACT_NAME_PREFIX + createdSoFar.get())
						.hasExpectedInfo(),

				contractUpdate(CONTRACT_NAME_PREFIX + createdSoFar.get())
						.payingWith(GENESIS)
						.noLogging()
						.hasAnyPrecheck()
						.deferStatusResolution()
		};
		return defaultHapiSpec("RunMixedFileOps")
				.given(
						withOpContext((spec, ignore) -> settings.setFrom(spec.setup().ciPropertiesMap())),
						logIt(ignore -> settings.toString())
				)
				.when(
						contractCreate(CONTRACT_NAME_PREFIX + createdSoFar.getAndIncrement())
								.payingWith(GENESIS)
								.bytecode("byteCode")
								.entityMemo(new String(memo))
								.logged()
				)
				.then(
						defaultLoadTest(mixedOpsBurst, settings)
				);
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}
