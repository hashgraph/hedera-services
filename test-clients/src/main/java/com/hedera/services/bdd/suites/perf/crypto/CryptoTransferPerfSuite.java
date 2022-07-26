package com.hedera.services.bdd.suites.perf.crypto;

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
import com.hedera.services.bdd.suites.HapiApiSuite;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Arrays;
import java.util.List;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountRecords;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromTo;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.finishThroughputObs;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.inParallel;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.startThroughputObs;

public class CryptoTransferPerfSuite extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(CryptoTransferPerfSuite.class);

	public static void main(String... args) {
		CryptoTransferPerfSuite suite = new CryptoTransferPerfSuite();
		suite.runSuiteSync();
	}

	@Override
	public List<HapiApiSpec> getSpecsInSuite() {
		return Arrays.asList(cryptoTransferPerf());
	}

	@Override
	public boolean canRunConcurrent() {
		return false;
	}

	private HapiApiSpec cryptoTransferPerf() {
		final int NUM_ACCOUNTS = 10;
		final int NUM_TRANSFERS = 10_000;
		final long INIT_BALANCE = 100_000_000_000L;
		final long ENDING_ZERO_ACCOUNT_BALANCE = (2L * NUM_TRANSFERS);

		return defaultHapiSpec("CryptoTransferPerf")
				.given(
						inParallel(
								asOpArray(NUM_ACCOUNTS + 1, i -> (i == 0)
										? cryptoCreate("account0")
												.balance(0L)
										: cryptoCreate("account" + i)
												.sendThreshold(1L)
												.balance(INIT_BALANCE))
						)
				).when(
						startThroughputObs("transferThroughput")
								.msToSaturateQueues(50L),
						inParallel(
								asOpArray(NUM_TRANSFERS, i ->
										cryptoTransfer(
												tinyBarsFromTo(
														"account" + (i % NUM_ACCOUNTS + 1),
														"account0",
														2L)
										).deferStatusResolution().hasAnyStatusAtAll()
								)
						)
				).then(
						finishThroughputObs("transferThroughput").gatedByQuery(() ->
								getAccountBalance("account0")
										.hasTinyBars(ENDING_ZERO_ACCOUNT_BALANCE)
										.noLogging()
						).sleepMs(1_000L).expiryMs(300_000L),
						getAccountRecords("account1")
								.withLogging((log, records) -> log.info(String.format("%d records!", records.size())))
								.savingTo("record-snapshots")
				);
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}

