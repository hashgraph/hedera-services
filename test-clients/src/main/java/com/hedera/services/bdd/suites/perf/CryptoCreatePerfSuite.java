package com.hedera.services.bdd.suites.perf;

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

import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.spec.utilops.LoadTest;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.freeze;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.inParallel;

public class CryptoCreatePerfSuite extends LoadTest {
	private static final Logger log = LogManager.getLogger(CryptoCreatePerfSuite.class);

	public static void main(String... args) {
		CryptoCreatePerfSuite suite = new CryptoCreatePerfSuite();
		suite.setReportStats(true);
		suite.runSuiteSync();
	}

	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		return List.of(runCryptoCreates());
	}

	@Override
	public boolean hasInterestingStats() {
		return true;
	}

	private HapiApiSpec runCryptoCreates() {
		final int NUM_CREATES = 1000000;

		return defaultHapiSpec("cryptoCreatePerf")
				.given(
				).when(
						inParallel(
								asOpArray(NUM_CREATES, i ->
										(i == (NUM_CREATES - 1)) ? cryptoCreate("testAccount" + i)
												.balance(100_000_000_000L)
												.key(GENESIS)
												.withRecharging()
												.rechargeWindow(30)
												.payingWith(GENESIS) :
												cryptoCreate("testAccount" + i)
														.balance(100_000_000_000L)
														.key(GENESIS)
														.withRecharging()
														.rechargeWindow(30)
														.payingWith(GENESIS)
														.deferStatusResolution()
								)
						)
				).then(
						freeze().payingWith(GENESIS).startingIn(60).seconds().andLasting(1).minutes()
				);
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}


