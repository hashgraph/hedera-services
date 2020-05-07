package com.hedera.services.bdd.suites.issues;

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

import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hedera.services.bdd.spec.HapiApiSpec;
import static com.hedera.services.bdd.spec.HapiApiSpec.*;

import com.hedera.services.bdd.spec.keys.KeyFactory;
import com.hedera.services.bdd.suites.HapiApiSuite;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import static com.hedera.services.bdd.spec.HapiPropertySource.asAccount;
import static com.hedera.services.bdd.spec.HapiPropertySource.asContract;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.*;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.*;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.*;
import static com.hedera.services.bdd.spec.HapiPropertySource.asFile;
import static com.hedera.services.bdd.spec.assertions.TransactionRecordAsserts.*;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.*;

import java.util.List;

public class Issue1765Suite extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(Issue1765Suite.class);

	public static void main(String... args) {
		new Issue1765Suite().runSuiteSync();
	}

	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		return List.of(
//				recordOfInvalidFileAppendSanityChecks(),
//				recordOfInvalidAccountUpdateSanityChecks(),
//				recordOfInvalidAccountTransferSanityChecks()
//				recordOfInvalidFileUpdateSanityChecks()
//				recordOfInvalidContractUpdateSanityChecks()
				get950Balance()
		);
	}

	public static HapiApiSpec get950Balance() {
		return defaultHapiSpec("Get950Balance")
				.given().when().then(
					getAccountBalance("0.0.950").logged()
				);
	}

	public static HapiApiSpec recordOfInvalidAccountTransferSanityChecks() {
		final String INVALID_ACCOUNT = "imaginary";

		return defaultHapiSpec("RecordOfInvalidAccountTransferSanityChecks")
				.given(flattened(
						withOpContext((spec, ctxLog) -> {
							spec.registry().saveAccountId(INVALID_ACCOUNT, asAccount("1.1.1"));
						}),
						takeBalanceSnapshots(FUNDING, GENESIS, NODE)
				)).when(
						cryptoTransfer(
								tinyBarsFromTo(GENESIS, INVALID_ACCOUNT, 1L)
						)
				).then();
	}

	public static HapiApiSpec recordOfInvalidAccountUpdateSanityChecks() {
		final String INVALID_ACCOUNT = "imaginary";

		return defaultHapiSpec("RecordOfInvalidAccountSanityChecks")
				.given(flattened(
						withOpContext((spec, ctxLog) -> {
							spec.registry().saveAccountId(INVALID_ACCOUNT, asAccount("1.1.1"));
						}),
						newKeyNamed(INVALID_ACCOUNT),
						newKeyNamed("irrelevant"),
						takeBalanceSnapshots(FUNDING, GENESIS, NODE)
				)).when(
						cryptoUpdate(INVALID_ACCOUNT).key("irrelevant")
				).then();
	}

	public static HapiApiSpec recordOfInvalidContractUpdateSanityChecks() {
		final long ADEQUATE_FEE = 100_000_000L;
		final String INVALID_CONTRACT = "imaginary";
		final String THE_MEMO_IS = "Turning and turning in the widening gyre";

		return defaultHapiSpec("RecordOfInvalidContractUpdateSanityChecks")
				.given(flattened(
						withOpContext((spec, ctxLog) -> {
							spec.registry().saveContractId(INVALID_CONTRACT, asContract("1.1.1"));
						}),
						newKeyNamed(INVALID_CONTRACT),
						takeBalanceSnapshots(FUNDING, GENESIS, NODE)
				)).when(
						contractUpdate(INVALID_CONTRACT)
								.memo(THE_MEMO_IS)
								.fee(ADEQUATE_FEE)
								.via("invalidUpdateTxn")
								.hasKnownStatus(ResponseCodeEnum.INVALID_CONTRACT_ID)
				).then(
						validateTransferListForBalances("invalidUpdateTxn", List.of(FUNDING, GENESIS, NODE)),
						getTxnRecord("invalidUpdateTxn").has(recordWith().memo(THE_MEMO_IS))
				);
	}

	public static HapiApiSpec recordOfInvalidFileUpdateSanityChecks() {
		final long ADEQUATE_FEE = 100_000_000L;
		final String INVALID_FILE = "imaginary";
		final String THE_MEMO_IS = "Turning and turning in the widening gyre";

		return defaultHapiSpec("RecordOfInvalidFileUpdateSanityChecks")
				.given(flattened(
						withOpContext((spec, ctxLog) -> {
							spec.registry().saveFileId(INVALID_FILE, asFile("0.0.0"));
						}),
						newKeyNamed(INVALID_FILE).type(KeyFactory.KeyType.LIST),
						takeBalanceSnapshots(FUNDING, GENESIS, NODE)
				)).when(
						fileUpdate(INVALID_FILE)
								.memo(THE_MEMO_IS)
								.fee(ADEQUATE_FEE)
								.via("invalidUpdateTxn")
								.hasKnownStatus(ResponseCodeEnum.INVALID_FILE_ID)
				).then(
						validateTransferListForBalances("invalidUpdateTxn", List.of(FUNDING, GENESIS, NODE)),
						getTxnRecord("invalidUpdateTxn").has(recordWith().memo(THE_MEMO_IS))
				);
	}

	public static HapiApiSpec recordOfInvalidFileAppendSanityChecks() {
		final long ADEQUATE_FEE = 100_000_000L;
		final String INVALID_FILE = "imaginary";
		final String THE_MEMO_IS = "Turning and turning in the widening gyre";

		return defaultHapiSpec("RecordOfInvalidFileAppendSanityChecks")
				.given(flattened(
						withOpContext((spec, ctxLog) -> {
							spec.registry().saveFileId(INVALID_FILE, asFile("0.0.0"));
						}),
						newKeyNamed(INVALID_FILE).type(KeyFactory.KeyType.LIST),
						takeBalanceSnapshots(FUNDING, GENESIS, NODE)
				)).when(
						fileAppend(INVALID_FILE)
								.memo(THE_MEMO_IS)
								.content("Some more content.")
								.fee(ADEQUATE_FEE)
								.via("invalidAppendTxn")
								.hasKnownStatus(ResponseCodeEnum.INVALID_FILE_ID)
				).then(
						validateTransferListForBalances("invalidAppendTxn", List.of(FUNDING, GENESIS, NODE)),
						getTxnRecord("invalidAppendTxn").has(recordWith().memo(THE_MEMO_IS))
				);
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}

