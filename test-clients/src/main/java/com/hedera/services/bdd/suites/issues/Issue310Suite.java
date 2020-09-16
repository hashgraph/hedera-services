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

import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.DUPLICATE_TRANSACTION;
import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.keys.KeyShape.SIMPLE;
import static com.hedera.services.bdd.spec.keys.KeyShape.listOf;
import static com.hedera.services.bdd.spec.keys.KeyShape.threshOf;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromTo;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.*;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.*;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;

public class Issue310Suite extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(CryptoCreateSuite.class);

	public static void main(String... args) {
		new Issue310Suite().runSuiteSync();
	}

	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		return List.of(
				duplicatedTxnsSameTypeDetected(),
				duplicatedTxnsDifferentTypesDetected(),
				duplicatedTxnsSameTypeDifferntNodesDetected(),
				duplicatedTxnsDiffrentTypesDifferentNodesDetected()
		);
	}

	private HapiApiSpec duplicatedTxnsSameTypeDetected() {
		long initialBalance = 10_000L;

		return defaultHapiSpec("duplicatedTxnsSameTypeDetected")
				.given(
						cryptoCreate("acct1")
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

	private HapiApiSpec duplicatedTxnsDifferentTypesDetected() {
		return defaultHapiSpec("duplicatedTxnsDifferentTypesDetected")
				.given(
						cryptoCreate("acct2")
								.via("txnId2"),
						newKeyNamed("key1"),
						createTopic("topic2")
								.submitKeyName("key1")
				)
				.when(
						submitMessageTo("topic2")
								.message("Hello world")
								.payingWith("acct2")
								.txnId("txnId2")
								.hasPrecheck(DUPLICATE_TRANSACTION)

				)
				.then(
						getTxnRecord("txnId2").logged()

				);
	}


	private HapiApiSpec duplicatedTxnsSameTypeDifferntNodesDetected() {

		return defaultHapiSpec("duplicatedTxnsSameTypeDifferntNodesDetected")
				.given(
						cryptoCreate("acct3")
								.setNode("0.0.3")
								.via("txnId1"),
						UtilVerbs.sleepFor(1000),
						cryptoCreate("acctWithDuplicateTxnId")
								.setNode("0.0.5")
								.txnId("txnId1")
								.hasPrecheck(DUPLICATE_TRANSACTION)

				).when(
				).then(
						getTxnRecord("txnId1").logged()

				);
	}


	private HapiApiSpec duplicatedTxnsDiffrentTypesDifferentNodesDetected() {
		return defaultHapiSpec("duplicatedTxnsDiffrentTypesDifferentNodesDetected")
				.given(
						cryptoCreate("acct4")
								.via("txnId4")
								.setNode("0.0.3"),
						newKeyNamed("key2"),
						createTopic("topic2")
								.setNode("0.0.5")
								.submitKeyName("key2")
				)
				.when(
						submitMessageTo("topic2")
								.message("Hello world")
								.payingWith("acct4")
								.txnId("txnId4")
								.hasPrecheck(DUPLICATE_TRANSACTION)

				)
				.then(
						getTxnRecord("txnId4").logged()

				);
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}

}