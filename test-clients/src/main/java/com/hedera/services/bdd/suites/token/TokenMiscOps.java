package com.hedera.services.bdd.suites.token;

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
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTokenInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.burnToken;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.mintToken;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenAssociate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;

public class TokenMiscOps extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(TokenMiscOps.class);

	public static void main(String... args) {
		new TokenMiscOps().runSuiteSync();
	}

	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		return allOf(
				List.of(new HapiApiSpec[] {
//								wellKnownAccountsHaveTokens(),
								someInfoQueries(),
						}
				)
		);
	}

	public HapiApiSpec someInfoQueries() {
		return defaultHapiSpec("SomeInfoQueries")
				.given().when().then(
						getAccountInfo("0.0.1001").logged(),
						getAccountInfo(SYSTEM_ADMIN).logged(),
						getTokenInfo("0.0.1002").logged(),
						getTokenInfo("0.0.1003").logged()
				);
	}

	public HapiApiSpec wellKnownAccountsHaveTokens() {
		return defaultHapiSpec("WellKnownAccountsHaveTokens")
				.given(
						cryptoCreate(TOKEN_TREASURY).balance(0L),
						newKeyNamed("supplyKey"),
						newKeyNamed("freezeKey"),
						newKeyNamed("kycKey"),
						tokenCreate("supple")
								.kycKey("kycKey")
								.freezeKey("freezeKey")
								.supplyKey("supplyKey")
								.decimals(1)
								.treasury(TOKEN_TREASURY),
						tokenCreate("another")
								.kycKey("kycKey")
								.freezeDefault(true)
								.freezeKey("freezeKey")
								.supplyKey("supplyKey")
								.treasury(SYSTEM_ADMIN)
				).when(
						tokenAssociate(TOKEN_TREASURY, "another")
				).then(
						getAccountInfo(TOKEN_TREASURY).logged(),
						getAccountInfo(SYSTEM_ADMIN).logged(),
						getTokenInfo("supple").logged(),
						getTokenInfo("another").logged()
				);
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}
