package com.hedera.services.bdd.suites.regression;

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
import com.hedera.services.bdd.spec.queries.QueryVerbs;
import com.hedera.services.bdd.suites.HapiApiSuite;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountInfo;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromTo;

public class SavedStateCheck extends HapiApiSuite {
	static final Logger log = LogManager.getLogger(SavedStateCheck.class);

	public static void main(String... args) {
		new SavedStateCheck().runSuiteSync();
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}

	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		return List.of(new HapiApiSpec[] {
				getAccoutInfoByAccountID(),
				getFileInfo(),
				getContractInfo(),
				getScheduleInfo(),
				}
		);
	}

	private HapiApiSpec getAccoutInfoByAccountID() {
		return defaultHapiSpec("getAccoutInfoByAccountID")
				.given(
						getAccountInfo("0.0.2000100")
								.logged(),

						getAccountInfo("0.0.2000101")
								.logged()
				).when().then(
						cryptoTransfer(tinyBarsFromTo("0.0.1100", "0.0.1101", 1))
								.fee(ONE_HBAR)
								.signedBy(GENESIS)
								.logged()
								.payingWith("0.0.1100")
				);
	}


	private HapiApiSpec getScheduleInfo() {
		return defaultHapiSpec("getScheduleInfo")
				.given(
				).when().then(
						QueryVerbs.getScheduleInfo("0.0.2351")
								.payingWith(GENESIS)
								.fee(ONE_HBAR)
								.nodePayment(ONE_HBAR)
								.logged()
				);
	}

	private HapiApiSpec getContractInfo() {
		return defaultHapiSpec("getContractInfo")
				.given(
				).when().then(
						QueryVerbs.getContractInfo("0.0.2350")
								.payingWith(GENESIS)
								.fee(ONE_HBAR)
								.nodePayment(ONE_HBAR)
								.logged()
				);
	}

	private HapiApiSpec getFileInfo() {
		return defaultHapiSpec("getFileInfo")
				.given().when().then(
						QueryVerbs.getFileInfo("0.0.2249").logged()
				);
	}
}
