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
import com.hedera.services.bdd.spec.queries.crypto.ExpectedTokenRel;
import com.hedera.services.bdd.spec.transactions.TxnUtils;
import com.hedera.services.bdd.spec.utilops.UtilVerbs;
import com.hedera.services.bdd.suites.HapiApiSuite;
import com.hederahashgraph.api.proto.java.TokenFreezeStatus;
import com.hederahashgraph.api.proto.java.TokenKycStatus;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.Map;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getFileContents;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTokenInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoDelete;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.*;

public class TokenCreateSpecs extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(TokenCreateSpecs.class);

	private static String TOKEN_TREASURY = "treasury";
	private static final int MAX_NAME_LENGTH = 100;
	private static final long A_HUNDRED_SECONDS = 100;

	public static void main(String... args) {
		new TokenCreateSpecs().runSuiteSync();
	}

	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		return List.of(
						wtf(),
						creationValidatesSymbol(),
						treasuryHasCorrectBalance(),
						creationRequiresAppropriateSigs(),
						initialSupplyMustBeSane(),
						numAccountsAllowedIsDynamic(),
						creationYieldsExpectedToken(),
						creationSetsExpectedName(),
						creationValidatesName(),
						creationRequiresAppropriateSigs(),
						creationValidatesTreasuryAccount(),
						autoRenewValidationWorks(),
						creationSetsCorrectExpiry(),
						creationHappyPath(),
						creationWithoutKYCSetsCorrectStatus(),
						creationValidatesExpiry(),
						creationValidatesFreezeDefaultWithNoFreezeKey()
		);
	}

	public HapiApiSpec wtf() {
		return defaultHapiSpec("WhatAreTheApiPermissions??")
				.given( ).when( ).then(
						getFileContents(API_PERMISSIONS).logged()
				);
	}

	public HapiApiSpec autoRenewValidationWorks() {
		return defaultHapiSpec("AutoRenewValidationWorks")
				.given(
						cryptoCreate("autoRenew"),
						cryptoCreate("deletingAccount")
				).when(
						cryptoDelete("deletingAccount"),
						tokenCreate("primary")
								.autoRenewAccount("deletingAccount")
								.hasKnownStatus(INVALID_AUTORENEW_ACCOUNT),
						tokenCreate("primary")
								.signedBy(GENESIS)
								.autoRenewAccount("1.2.3")
								.hasKnownStatus(INVALID_AUTORENEW_ACCOUNT),
						tokenCreate("primary")
								.autoRenewAccount("autoRenew")
								.autoRenewPeriod(Long.MAX_VALUE)
								.hasPrecheck(INVALID_RENEWAL_PERIOD),
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
								.initialSupply(123)
								.decimals(4)
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

	public HapiApiSpec creationWithoutKYCSetsCorrectStatus() {
		String saltedName = salted("primary");
		return defaultHapiSpec("CreationWithoutKYCSetsCorrectStatus")
				.given(
						cryptoCreate(TOKEN_TREASURY)
				).when(
						tokenCreate("primary")
								.name(saltedName)
								.treasury(TOKEN_TREASURY)
				).then(
						getAccountInfo(TOKEN_TREASURY)
								.hasToken(
										ExpectedTokenRel.relationshipWith("primary")
												.kyc(TokenKycStatus.KycNotApplicable)
								)
				);
	}

	public HapiApiSpec creationHappyPath() {
		String saltedName = salted("primary");
		return defaultHapiSpec("CreationHappyPath")
				.given(
						cryptoCreate(TOKEN_TREASURY),
						cryptoCreate("autoRenewAccount"),
						newKeyNamed("adminKey"),
						newKeyNamed("freezeKey"),
						newKeyNamed("kycKey"),
						newKeyNamed("supplyKey"),
						newKeyNamed("wipeKey")
				).when(
						tokenCreate("primary")
								.name(saltedName)
								.treasury(TOKEN_TREASURY)
								.autoRenewAccount("autoRenewAccount")
								.autoRenewPeriod(A_HUNDRED_SECONDS)
								.initialSupply(500)
								.decimals(1)
								.adminKey("adminKey")
								.freezeKey("freezeKey")
								.kycKey("kycKey")
								.supplyKey("supplyKey")
								.wipeKey("wipeKey")
								.via("createTxn")
				).then(
						UtilVerbs.withOpContext((spec, opLog) -> {
							var createTxn = getTxnRecord("createTxn");
							allRunFor(spec, createTxn);
							var timestamp = createTxn.getResponseRecord().getConsensusTimestamp().getSeconds();
							spec.registry().saveExpiry("primary", timestamp + A_HUNDRED_SECONDS);
						}),
						getTokenInfo("primary")
								.logged()
								.hasRegisteredId("primary")
								.hasName(saltedName)
								.hasTreasury(TOKEN_TREASURY)
								.hasAutoRenewPeriod(A_HUNDRED_SECONDS)
								.hasValidExpiry()
								.hasDecimals(1)
								.hasAdminKey("adminKey")
								.hasFreezeKey("freezeKey")
								.hasKycKey("kycKey")
								.hasSupplyKey("supplyKey")
								.hasWipeKey("wipeKey")
								.hasTotalSupply(500)
								.hasAutoRenewAccount("autoRenewAccount"),
						getAccountInfo(TOKEN_TREASURY)
								.hasToken(
										ExpectedTokenRel.relationshipWith("primary")
												.balance(500)
												.kyc(TokenKycStatus.Granted)
												.freeze(TokenFreezeStatus.Unfrozen)
								)
				);
	}

	public HapiApiSpec creationSetsCorrectExpiry() {
		return defaultHapiSpec("CreationSetsCorrectExpiry")
				.given(
						cryptoCreate(TOKEN_TREASURY),
						cryptoCreate("autoRenew")
				).when(
						tokenCreate("primary")
								.autoRenewAccount("autoRenew")
								.autoRenewPeriod(A_HUNDRED_SECONDS)
								.treasury(TOKEN_TREASURY)
								.via("createTxn")
				).then(
						UtilVerbs.withOpContext((spec, opLog) -> {
							var createTxn = getTxnRecord("createTxn");
							allRunFor(spec, createTxn);
							var timestamp = createTxn.getResponseRecord().getConsensusTimestamp().getSeconds();
							spec.registry().saveExpiry("primary", timestamp + A_HUNDRED_SECONDS);
						}),
						getTokenInfo("primary")
								.logged()
								.hasRegisteredId("primary")
								.hasValidExpiry()
				);
	}

	public HapiApiSpec creationValidatesExpiry() {
		return defaultHapiSpec("CreationValidatesExpiry")
				.given().when().then(
						tokenCreate("primary")
								.expiry(1000)
								.hasPrecheck(INVALID_EXPIRATION_TIME)
				);
	}

	public HapiApiSpec creationValidatesFreezeDefaultWithNoFreezeKey() {
		return defaultHapiSpec("CreationValidatesFreezeDefaultWithNoFreezeKey")
				.given()
				.when()
				.then(
						tokenCreate("primary")
								.freezeDefault(true)
								.hasPrecheck(TOKEN_HAS_NO_FREEZE_KEY)
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
								.hasPrecheck(MISSING_TOKEN_NAME),
						tokenCreate("primary")
								.name(longName)
								.logged()
								.hasPrecheck(TOKEN_NAME_TOO_LONG)
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
						fileUpdate(APP_PROPERTIES)
								.payingWith(ADDRESS_BOOK_CONTROL)
								.overridingProps(Map.of( "tokens.maxPerAccount", "" + MONOGAMOUS_NETWORK)),
						tokenCreate(salted("primary"))
								.treasury(TOKEN_TREASURY),
						tokenCreate(salted("secondary"))
								.treasury(TOKEN_TREASURY)
								.hasKnownStatus(TOKENS_PER_ACCOUNT_LIMIT_EXCEEDED)
				).then(
						fileUpdate(APP_PROPERTIES)
								.payingWith(ADDRESS_BOOK_CONTROL)
								.overridingProps(Map.of(
								"tokens.maxPerAccount", "" + ADVENTUROUS_NETWORK
						)),
						tokenCreate(salted("secondary"))
								.treasury(TOKEN_TREASURY)
				);
	}

	public HapiApiSpec creationValidatesSymbol() {
		String firstToken = "FIRSTMOVER" + TxnUtils.randomUppercase(5);

		return defaultHapiSpec("CreationValidatesSymbol")
				.given(
						cryptoCreate("payer").balance(A_HUNDRED_HBARS),
						cryptoCreate(TOKEN_TREASURY)
				).when(
						tokenCreate("nonAlphanumeric")
								.payingWith("payer")
								.symbol("!")
								.hasPrecheck(INVALID_TOKEN_SYMBOL),
						tokenCreate("missingSymbol")
								.payingWith("payer")
								.symbol("")
								.hasPrecheck(MISSING_TOKEN_SYMBOL),
						tokenCreate("whiteSpaces")
								.payingWith("payer")
								.symbol(" ")
								.hasPrecheck(INVALID_TOKEN_SYMBOL),
						tokenCreate("tooLong")
								.payingWith("payer")
								.symbol("ABCDEZABCDEZABCDEZABCDEZABCDEZABCDEZ")
								.hasPrecheck(TOKEN_SYMBOL_TOO_LONG),
						tokenCreate("firstMoverAdvantage")
								.symbol(firstToken)
								.payingWith("payer")
				).then();
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

	public HapiApiSpec creationValidatesTreasuryAccount() {
		return defaultHapiSpec("CreationValidatesTreasuryAccount")
				.given(
						cryptoCreate(TOKEN_TREASURY)
				).when(
						cryptoDelete(TOKEN_TREASURY)
				).then(
						tokenCreate("shouldntWork")
								.treasury(TOKEN_TREASURY)
								.hasKnownStatus(INVALID_TREASURY_ACCOUNT_FOR_TOKEN)
				);
	}

	public HapiApiSpec initialSupplyMustBeSane() {
		return defaultHapiSpec("InitialSupplyMustBeSane")
				.given(
						cryptoCreate("payer").balance(A_HUNDRED_HBARS)
				).when(
				).then(
						tokenCreate("sinking")
								.payingWith("payer")
								.initialSupply(-1L)
								.hasPrecheck(INVALID_TOKEN_INITIAL_SUPPLY),
						tokenCreate("bad decimals")
								.payingWith("payer")
								.decimals(-1)
								.hasPrecheck(INVALID_TOKEN_DECIMALS),
						tokenCreate("bad decimals")
								.payingWith("payer")
								.decimals(1 << 31)
								.hasPrecheck(INVALID_TOKEN_DECIMALS),
						tokenCreate("bad initial supply")
								.payingWith("payer")
								.initialSupply(1L << 63)
								.hasPrecheck(INVALID_TOKEN_INITIAL_SUPPLY)
				);
	}

	public HapiApiSpec treasuryHasCorrectBalance() {
		String token = salted("myToken");

		int decimals = 1;
		long initialSupply = 100_000;

		return defaultHapiSpec("TreasuryHasCorrectBalance")
				.given(
						cryptoCreate("payer").balance(A_HUNDRED_HBARS),
						cryptoCreate(TOKEN_TREASURY).balance(A_HUNDRED_HBARS)
				).when(
						tokenCreate(token)
								.treasury(TOKEN_TREASURY)
								.decimals(decimals)
								.initialSupply(initialSupply)
								.payingWith("payer")
				).then(
						getAccountBalance(TOKEN_TREASURY)
								.hasTinyBars(A_HUNDRED_HBARS)
								.hasTokenBalance(token, initialSupply)
				);
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}
