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
import com.hedera.services.bdd.spec.transactions.TxnUtils;
import com.hedera.services.bdd.suites.HapiApiSuite;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.Map;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTokenInfo;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.*;

public class TokenCreateSpecs extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(TokenCreateSpecs.class);

	private static String TOKEN_TREASURY = "treasury";
	private static final int MAX_NAME_LENGTH = 100;

	public static void main(String... args) {
		new TokenCreateSpecs().runSuiteSync();
	}

	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		return List.of(new HapiApiSpec[] {
						creationValidatesSymbol(),
						treasuryHasCorrectBalance(),
						initialFloatMustBeSane(),
						numAccountsAllowedIsDynamic(),
						autoRenewValidationWorks(),
						creationYieldsExpectedToken(),
						creationSetsExpectedName(),
						creationValidatesName(),
						creationRequiresAppropriateSigs(),
				}
		);
	}

	public HapiApiSpec autoRenewValidationWorks() {
		return defaultHapiSpec("AutoRenewValidationWorks")
				.given(
						cryptoCreate("autoRenew")
				).when(
						tokenCreate("primary")
								.signedBy(GENESIS)
								.autoRenewAccount("1.2.3")
								.hasKnownStatus(INVALID_AUTORENEW_ACCOUNT),
						tokenCreate("primary")
								.autoRenewAccount("autoRenew")
								.autoRenewPeriod(Long.MAX_VALUE)
								.hasKnownStatus(INVALID_RENEWAL_PERIOD),
						tokenCreate("primary")
								.signedBy(GENESIS)
								.autoRenewAccount("autoRenew")
								.hasKnownStatus(INVALID_SIGNATURE),
						tokenCreate("primary")
								.autoRenewAccount("autoRenew")
				).then(
						getTokenInfo("primary")
								.logged()
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

	public HapiApiSpec creationSetsExpectedName() {
		String saltedName = salted("primary");
		return defaultHapiSpec("CreationSetsExpectedName")
				.given(
						cryptoCreate("payer").balance(A_HUNDRED_HBARS),
						cryptoCreate(TOKEN_TREASURY)
				).when(
						tokenCreate("primary")
								.name(saltedName)
								.treasury(TOKEN_TREASURY)
				).then(
						getTokenInfo("primary")
								.logged()
								.hasRegisteredId("primary")
								.hasName(saltedName)
				);
	}


	public HapiApiSpec creationValidatesName() {
		String longName = "a".repeat(MAX_NAME_LENGTH + 1);
		return defaultHapiSpec("CreationValidatesName")
				.given(
						cryptoCreate("payer").balance(A_HUNDRED_HBARS),
						cryptoCreate(TOKEN_TREASURY)
				).when(
				).then(
						tokenCreate("primary")
								.name("")
								.logged()
								.hasKnownStatus(MISSING_TOKEN_NAME),
						tokenCreate("primary")
								.name(longName)
								.logged()
								.hasKnownStatus(TOKEN_NAME_TOO_LONG)
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
		String hopefullyUnique = "FIRSTMOVER" + TxnUtils.randomUppercase(5);

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
						tokenCreate("whiteSpaces")
								.payingWith("payer")
								.symbol(" ")
								.hasKnownStatus(INVALID_TOKEN_SYMBOL),
						tokenCreate("tooLong")
								.payingWith("payer")
								.symbol("ABCDEZABCDEZABCDEZABCDEZABCDEZABCDEZ")
								.hasKnownStatus(TOKEN_SYMBOL_TOO_LONG),
						tokenCreate("firstMoverAdvantage")
								.symbol(hopefullyUnique)
								.payingWith("payer")
				).then(
						tokenCreate("tooLate")
								.payingWith("payer")
								.symbol(hopefullyUnique)
								.hasKnownStatus(TOKEN_SYMBOL_ALREADY_IN_USE)
				);
	}

	public HapiApiSpec creationRequiresAppropriateSigs() {
		return defaultHapiSpec("CreationRequiresAppropriateSigs")
				.given(
						cryptoCreate("payer").balance(A_HUNDRED_HBARS),
						cryptoCreate(TOKEN_TREASURY),
						newKeyNamed("adminKey")
				).when().then(
						tokenCreate("shouldntWork")
								.treasury(TOKEN_TREASURY)
								.payingWith("payer")
								.adminKey("adminKey")
								.signedBy("payer")
								.hasKnownStatus(INVALID_SIGNATURE),
						/* treasury must sign */
						tokenCreate("shouldntWorkEither")
								.treasury(TOKEN_TREASURY)
								.payingWith("payer")
								.adminKey("adminKey")
								.signedBy("payer", "adminKey")
								.hasKnownStatus(INVALID_SIGNATURE)
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
								.hasKnownStatus(INVALID_INITIAL_SUPPLY),
						tokenCreate("indivisible")
								.payingWith("payer")
								.divisibility(-1)
								.hasKnownStatus(INVALID_TOKEN_DECIMALS),
						tokenCreate("indivisible")
								.payingWith("payer")
								.divisibility(1)
								.initialFloat(1L << 62)
								.hasKnownStatus(INVALID_TOKEN_DECIMALS),
						tokenCreate("toobigdivisibility")
								.payingWith("payer")
								.initialFloat(0)
								.divisibility(19)
								.hasKnownStatus(INVALID_TOKEN_DECIMALS),
						tokenCreate("toobigdivisibility")
								.payingWith("payer")
								.initialFloat(10)
								.divisibility(18)
								.hasKnownStatus(INVALID_TOKEN_DECIMALS)
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
