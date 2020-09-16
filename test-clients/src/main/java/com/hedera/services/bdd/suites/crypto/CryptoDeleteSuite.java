package com.hedera.services.bdd.suites.crypto;

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

import java.util.Arrays;
import java.util.List;

import com.hedera.services.bdd.spec.HapiSpecSetup;
import com.hedera.services.bdd.suites.HapiApiSuite;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.assertions.AccountInfoAsserts.accountWith;
import static com.hedera.services.bdd.spec.assertions.TransactionRecordAsserts.recordWith;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromTo;
import static com.hedera.services.bdd.spec.assertions.TransferListAsserts.including;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoDelete;

public class CryptoDeleteSuite extends HapiApiSuite {
	static final Logger log = LogManager.getLogger(CryptoDeleteSuite.class);

	public static void main(String... args) {
		new CryptoDeleteSuite().runSuiteSync();
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}

	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		return allOf(
				positiveTests(),
				negativeTests()
		);
	}

	private List<HapiApiSpec> positiveTests() {
		return Arrays.asList(
				fundsTransferOnDelete()
		);
	}

	private List<HapiApiSpec> negativeTests() {
		return Arrays.asList(
		);
	}

	private HapiApiSpec fundsTransferOnDelete() {
		long B = HapiSpecSetup.getDefaultInstance().defaultBalance();

		return defaultHapiSpec("FundsTransferOnDelete")
				.given(
						cryptoCreate("toBeDeleted"),
						cryptoCreate("transferAccount").balance(0L)
				).when(
						cryptoDelete("toBeDeleted")
								.transfer("transferAccount").via("deleteTxn")
				).then(
						getAccountInfo("transferAccount")
								.has(accountWith().balance(B)),
						getTxnRecord("deleteTxn")
								.hasPriority(recordWith().transfers(including(
										tinyBarsFromTo("toBeDeleted", "transferAccount", B)))));
	}
}
