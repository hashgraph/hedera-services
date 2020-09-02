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
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SIGNATURE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_DIVISIBILITY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_FLOAT;

public class TokenCreateSpecs extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(TokenCreateSpecs.class);

	private static String TOKEN_TREASURY = "treasury";

	public static void main(String... args) {
		new TokenCreateSpecs().runSuiteSync();
	}

	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		return List.of(new HapiApiSpec[] {
						treasuryHasCorrectBalance(),
						creationRequiresAppropriateSigs(),
						initialFloatMustBeSane(),
				}
		);
	}

	public HapiApiSpec creationRequiresAppropriateSigs() {
		String token = "frozenToken";

		return defaultHapiSpec("CreationRequiresAppropriateSigs")
				.given(
						cryptoCreate("payer").balance(A_HUNDRED_HBARS),
						cryptoCreate(TOKEN_TREASURY),
						newKeyNamed("adminKey"),
						newKeyNamed("treasuryKey"),
						newKeyNamed("randomWrongKey")
				).when(
						tokenCreate("shouldntWork")
								.payingWith("payer")
								.adminKey("adminKey")
								.signedBy("payer")
								.hasKnownStatus(INVALID_SIGNATURE),
						tokenCreate("shouldntWork")
								.payingWith("payer")
								.adminKey("adminKey")
								.freezeKey("treasuryKey")
								.signedBy("payer", "adminKey")
								.hasKnownStatus(INVALID_SIGNATURE),
						tokenCreate("shouldntWork")
								.payingWith("payer")
								.adminKey("adminKey")
								.freezeKey("treasuryKey")
								.signedBy("payer", "adminKey", "randomWrongKey")
								.hasKnownStatus(INVALID_SIGNATURE)
				).then(
						tokenCreate(token)
								.treasury(TOKEN_TREASURY)
								.freezeKey("treasuryKey")
								.freezeDefault(true)
								.payingWith("payer")
				);
	}

	public HapiApiSpec initialFloatMustBeSane() {
		String token = "myToken";

		return defaultHapiSpec("InitialFloatMustBeSane")
				.given(
						cryptoCreate("payer").balance(A_HUNDRED_HBARS)
				).when(
				).then(
						tokenCreate(token)
								.payingWith("payer")
								.initialFloat(-1L)
								.hasKnownStatus(INVALID_TOKEN_FLOAT),
						tokenCreate(token)
								.payingWith("payer")
								.divisibility(-1)
								.hasKnownStatus(INVALID_TOKEN_DIVISIBILITY),
						tokenCreate(token)
								.payingWith("payer")
								.divisibility(1)
								.initialFloat(1L << 62)
								.hasKnownStatus(INVALID_TOKEN_DIVISIBILITY)
				);
	}

	public HapiApiSpec treasuryHasCorrectBalance() {
		String token = "myToken";

		int divisibility = 1;
		long tokenFloat = 100_000;

		return defaultHapiSpec("TreasuryHasCorrectBalance")
				.given(
						cryptoCreate("payer").balance(A_HUNDRED_HBARS),
						cryptoCreate(TOKEN_TREASURY).balance(A_HUNDRED_HBARS)
				).when(
						tokenCreate(token)
								.treasury(TOKEN_TREASURY)
								.divisibility(divisibility)
								.initialFloat(tokenFloat)
								.payingWith("payer")
				).then(
						getAccountBalance(TOKEN_TREASURY)
								.hasTinyBars(A_HUNDRED_HBARS)
								.hasTokenBalance(token, tokenFloat * 10)
				);
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}
