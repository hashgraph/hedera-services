package com.hedera.services.bdd.suites.records;

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
import com.hedera.services.bdd.suites.HapiApiSuite;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import java.util.List;
import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileAppend;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileDelete;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileUpdate;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.*;

public class FileRecordsSanityCheckSuite extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(FileRecordsSanityCheckSuite.class);

	public static void main(String... args) {
		new FileRecordsSanityCheckSuite().runSuiteSync();
	}

	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		return List.of(
				new HapiApiSpec[] {
						fileCreateRecordSanityChecks(),
						fileDeleteRecordSanityChecks(),
						fileAppendRecordSanityChecks(),
						fileUpdateRecordSanityChecks(),
						fileCreateRecordSanityChecksWithTls()
				}
		);
	}

	private HapiApiSpec fileAppendRecordSanityChecks() {
		return defaultHapiSpec("FileAppendRecordSanityChecks")
				.given(flattened(
						fileCreate("test"),
						takeBalanceSnapshots(FUNDING, NODE, GENESIS)
				)).when(
						fileAppend("test").via("txn").fee(95_000_000L)
				).then(
						validateTransferListForBalances("txn", List.of(FUNDING, NODE, GENESIS)),
						validateRecordTransactionFees("txn")
				);
	}

	private HapiApiSpec fileCreateRecordSanityChecks() {
		return defaultHapiSpec("FileCreateRecordSanityChecks")
				.given(
						takeBalanceSnapshots(FUNDING, NODE, GENESIS)
				).when(
						fileCreate("test").via("txn")
				).then(
						validateTransferListForBalances("txn", List.of(FUNDING, NODE, GENESIS)),
						validateRecordTransactionFees("txn")
				);
	}

	private HapiApiSpec fileCreateRecordSanityChecksWithTls() {
		return defaultHapiSpec("FileCreateRecordSanityChecksWithTls")
				.given(
						takeBalanceSnapshots(FUNDING, NODE, GENESIS)
				).when(
						fileCreate("test").via("txn")
				).then(
						validateTransferListForBalances("txn", List.of(FUNDING, NODE, GENESIS)),
						validateRecordTransactionFees("txn")
				);
	}

	private HapiApiSpec fileDeleteRecordSanityChecks() {
		return defaultHapiSpec("FileDeleteRecordSanityChecks")
				.given(flattened(
						fileCreate("test"),
						takeBalanceSnapshots(FUNDING, NODE, GENESIS)
				)).when(
						fileDelete("test").via("txn")
				).then(
						validateTransferListForBalances("txn", List.of(FUNDING, NODE, GENESIS)),
						validateRecordTransactionFees("txn")
				);
	}

	private HapiApiSpec fileUpdateRecordSanityChecks() {
		return defaultHapiSpec("FileUpdateRecordSanityChecks")
				.given(flattened(
						fileCreate("test"),
						takeBalanceSnapshots(FUNDING, NODE, GENESIS)
				)).when(
						fileUpdate("test")
								.contents("Here are some new contents!")
								.via("txn")
								.fee(95_000_000L)
				).then(
						validateTransferListForBalances("txn", List.of(FUNDING, NODE, GENESIS)),
						validateRecordTransactionFees("txn")
				);
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}

