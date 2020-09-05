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

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTokenInfo;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SIGNATURE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_DIVISIBILITY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_FLOAT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_SYMBOL;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.MISSING_TOKEN_SYMBOL;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKENS_PER_ACCOUNT_LIMIT_EXCEEDED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_SYMBOL_ALREADY_IN_USE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_SYMBOL_TOO_LONG;

public class TokenCreateSpecs extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(TokenCreateSpecs.class);

	private static String TOKEN_TREASURY = "treasury";

	public static void main(String... args) {
		new TokenCreateSpecs().runSuiteSync();
	}

	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		return List.of(new HapiApiSpec[] {
						creationValidatesSymbol(),
						treasuryHasCorrectBalance(),
						creationRequiresAppropriateSigs(),
						initialFloatMustBeSane(),
						numAccountsAllowedIsDynamic(),
						creationYieldsExpectedToken(),
				}
		);
	}

	public HapiApiSpec creationYieldsExpectedToken() {
		return defaultHapiSpec("CreationYieldsExpectedToken")
				.given(
						cryptoCreate("payer").balance(A_HUNDRED_HBARS),
						cryptoCreate(TOKEN_TREASURY),
						newKeyNamed("freeze")
				).when(
						tokenCreate("primary")
								.symbol(salted("Primary"))
								.initialFloat(123)
								.divisibility(4)
								.freezeDefault(true)
								.freezeKey("freeze")
								.treasury(TOKEN_TREASURY)
				).then(
						getTokenInfo("primary")
								.logged()
								.hasRegisteredId("primary")
				);
	}

	public HapiApiSpec numAccountsAllowedIsDynamic() {
		final int MONOGAMOUS_NETWORK = 1;
		final int ADVENTUROUS_NETWORK = 1_000;

		return defaultHapiSpec("NumAccountsAllowedIsDynamic")
				.given(
						cryptoCreate("payer").balance(A_HUNDRED_HBARS),
						cryptoCreate(TOKEN_TREASURY)
				).when(
						fileUpdate(APP_PROPERTIES).overridingProps(Map.of(
								"tokens.maxPerAccount", "" + MONOGAMOUS_NETWORK
						)),
						tokenCreate(salted("primary"))
								.treasury(TOKEN_TREASURY),
						tokenCreate(salted("secondary"))
								.treasury(TOKEN_TREASURY)
								.hasKnownStatus(TOKENS_PER_ACCOUNT_LIMIT_EXCEEDED)
				).then(
						fileUpdate(APP_PROPERTIES).overridingProps(Map.of(
								"tokens.maxPerAccount", "" + ADVENTUROUS_NETWORK
						)),
						tokenCreate(salted("secondary"))
								.treasury(TOKEN_TREASURY)
				);
	}

	public HapiApiSpec creationValidatesSymbol() {
		int salt = Instant.now().getNano();

		return defaultHapiSpec("CreationValidatesSymbol")
				.given(
						cryptoCreate("payer").balance(A_HUNDRED_HBARS),
						cryptoCreate(TOKEN_TREASURY)
				).when(
						tokenCreate("nonAlphanumeric")
								.payingWith("payer")
								.symbol("!")
								.hasKnownStatus(INVALID_TOKEN_SYMBOL),
						tokenCreate("missingSymbol")
								.payingWith("payer")
								.symbol("")
								.hasKnownStatus(MISSING_TOKEN_SYMBOL),
						tokenCreate("tooLong")
								.payingWith("payer")
								.symbol("abcde0abcde1abcde2abcde3abcde4abcde5")
								.hasKnownStatus(TOKEN_SYMBOL_TOO_LONG),
						tokenCreate("firstMoverAdvantage")
								.payingWith("payer")
								.symbol(salted("POPULAR"))
				).then(
						tokenCreate("tooLate")
								.payingWith("payer")
								.symbol(spec -> spec.registry().getSymbol("firstMoverAdvantage"))
								.hasKnownStatus(TOKEN_SYMBOL_ALREADY_IN_USE)
				);
	}

	public HapiApiSpec creationRequiresAppropriateSigs() {
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
						tokenCreate(salted("frozenToken"))
								.treasury(TOKEN_TREASURY)
								.freezeKey("treasuryKey")
								.freezeDefault(true)
								.payingWith("payer")
				);
	}

	public HapiApiSpec initialFloatMustBeSane() {
		return defaultHapiSpec("InitialFloatMustBeSane")
				.given(
						cryptoCreate("payer").balance(A_HUNDRED_HBARS)
				).when(
				).then(
						tokenCreate("sinking")
								.payingWith("payer")
								.initialFloat(-1L)
								.hasKnownStatus(INVALID_TOKEN_FLOAT),
						tokenCreate("indivisible")
								.payingWith("payer")
								.divisibility(-1)
								.hasKnownStatus(INVALID_TOKEN_DIVISIBILITY),
						tokenCreate("indivisible")
								.payingWith("payer")
								.divisibility(1)
								.initialFloat(1L << 62)
								.hasKnownStatus(INVALID_TOKEN_DIVISIBILITY)
				);
	}

	public HapiApiSpec treasuryHasCorrectBalance() {
		String token = salted("myToken");

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
