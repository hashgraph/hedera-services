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

import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.spec.keys.KeyShape;
import com.hedera.services.bdd.spec.utilops.UtilVerbs;
import com.hedera.services.bdd.suites.HapiApiSuite;
import com.hedera.services.bdd.suites.crypto.CryptoCreateSuite;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Arrays;
import java.util.List;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.DUPLICATE_TRANSACTION;
import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.keys.KeyShape.SIMPLE;
import static com.hedera.services.bdd.spec.keys.KeyShape.listOf;
import static com.hedera.services.bdd.spec.keys.KeyShape.threshOf;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromTo;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.*;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.*;

public class Issue310Suite extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(CryptoCreateSuite.class);

	public static void main(String... args) {
		new Issue310Suite().runSuiteSync();
	}

	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		return allOf(
				negativeTests()
		);
	}

	private List<HapiApiSpec> positiveTests() {
		return Arrays.asList(
		);
	}

	private List<HapiApiSpec> negativeTests() {
		return List.of(
				createDuplcateTransaction()
		);
	}

	private HapiApiSpec createDuplcateTransaction() {
		KeyShape thresholdShape0 = threshOf(0, SIMPLE, SIMPLE, SIMPLE);
		KeyShape thresholdShape4 = threshOf(4, SIMPLE, SIMPLE, SIMPLE);

		long initialBalance = 10_000L;

		return defaultHapiSpec("testDuplcateTransaction")
				.given(
						cryptoCreate("acctWithTxnId")
								.balance(initialBalance)
								.logged()
								.via("txnId1"),
						UtilVerbs.sleepFor(1000),
						cryptoCreate("acctWithDuplicateTxnId")
								.balance(initialBalance)
								.logged()
								.txnId("txnId1")
								.hasPrecheck(DUPLICATE_TRANSACTION)

				).when(
				).then(
						getTxnRecord("txnId1").logged()

				);
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}

}
