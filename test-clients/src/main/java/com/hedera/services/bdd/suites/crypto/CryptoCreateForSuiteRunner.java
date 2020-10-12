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
import com.hedera.services.bdd.spec.utilops.CustomSpecAssert;
import com.hedera.services.bdd.suites.HapiApiSuite;
import com.hedera.services.bdd.suites.SuiteRunner;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountInfo;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.BUSY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.DUPLICATE_TRANSACTION;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.PLATFORM_TRANSACTION_NOT_CREATED;

public class CryptoCreateForSuiteRunner extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(CryptoCreateForSuiteRunner.class);

	public static void main(String... args) {
		new CryptoCreateForSuiteRunner().runSuiteSync();
	}

	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		return allOf(
				positiveTests()
		);
	}

	private List<HapiApiSpec> positiveTests() {
		return List.of(
				vanillaCreateSucceeds()
		);
	}


	private HapiApiSpec vanillaCreateSucceeds() {
		long initialBalance = 50_000_000_000L;
		return defaultHapiSpec("CryptoCreate")
				.given().when().then(
						withOpContext((spec, log)-> {
							var cryptoCreateOp = cryptoCreate("payerAccount").balance(initialBalance)
									.withRecharging()
									.rechargeWindow(3)
									.hasRetryPrecheckFrom(BUSY, DUPLICATE_TRANSACTION, PLATFORM_TRANSACTION_NOT_CREATED).
									via("txn");
							var payerAccountInfo = getAccountInfo("payerAccount")
									.saveToRegistry("payerAccountInfo").logged();
							CustomSpecAssert.allRunFor(spec, cryptoCreateOp, payerAccountInfo);
							SuiteRunner.setPayerId(spec.registry()
									.getAccountInfo("payerAccountInfo").getAccountID().toString());
						}
				));
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}
