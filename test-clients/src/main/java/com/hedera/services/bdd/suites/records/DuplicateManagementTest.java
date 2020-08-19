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
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.assertions.TransactionRecordAsserts.recordWith;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getReceipt;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.uncheckedSubmit;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromTo;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sleepFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.usableTxnIdNamed;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_PAYER_SIGNATURE;

public class DuplicateManagementTest extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(DuplicateManagementTest.class);

	public static void main(String... args) {
		new DuplicateManagementTest().runSuiteSync();
	}

	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		return List.of(
				new HapiApiSpec[] {
						usesUnclassifiableIfNoClassifiableAvailable(),
				}
		);
	}

	private HapiApiSpec usesUnclassifiableIfNoClassifiableAvailable() {
		return defaultHapiSpec("UsesUnclassifiableIfNoClassifiableAvailable")
				.given(
						newKeyNamed("wrongKey"),
						cryptoCreate("civilian"),
						usableTxnIdNamed("txnId").payerId("civilian"),
						cryptoTransfer(tinyBarsFromTo(GENESIS, "0.0.3", 100 * 100_000_000L))
				).when(
						uncheckedSubmit(
								cryptoCreate("nope")
										.txnId("txnId")
										.setNode("0.0.3")
										.signedBy("wrongKey"))
								.hasAnyStatusAtAll(),
						sleepFor(1_000L)
				).then(
						getReceipt("txnId").hasReceiptStatus(INVALID_PAYER_SIGNATURE),
						getTxnRecord("txnId").has(recordWith().status(INVALID_PAYER_SIGNATURE))
				);
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}
