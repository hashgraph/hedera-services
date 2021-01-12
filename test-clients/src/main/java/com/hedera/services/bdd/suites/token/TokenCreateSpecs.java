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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTokenInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoDelete;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.recordSystemProperty;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sourcing;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.*;

public class TokenCreateSpecs extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(TokenCreateSpecs.class);

	private static String TOKEN_TREASURY = "treasury";
	private static final long A_HUNDRED_SECONDS = 100;

	public static void main(String... args) {
		new TokenCreateSpecs().runSuiteSync();
	}

	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		return List.of(new HapiApiSpec[] {
						creationValidatesName(),
						creationValidatesSymbol(),
						treasuryHasCorrectBalance(),
						creationRequiresAppropriateSigs(),
						initialSupplyMustBeSane(),
						numAccountsAllowedIsDynamic(),
						creationYieldsExpectedToken(),
						creationSetsExpectedName(),
						creationValidatesTreasuryAccount(),
						autoRenewValidationWorks(),
						creationWithoutKYCSetsCorrectStatus(),
						creationValidatesExpiry(),
						creationValidatesFreezeDefaultWithNoFreezeKey(),
						creationSetsCorrectExpiry(),
						creationHappyPath(),
				}
		);
	}

	public HapiApiSpec autoRenewValidationWorks() {
		return defaultHapiSpec("AutoRenewValidationWorks")
				.given(
						cryptoCreate("autoRenew").balance(0L),
						cryptoCreate("deletingAccount").balance(0L)
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
						cryptoCreate(TOKEN_TREASURY).balance(0L),
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
						cryptoCreate(TOKEN_TREASURY).balance(0L)
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
						cryptoCreate(TOKEN_TREASURY).balance(0L)
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
						cryptoCreate(TOKEN_TREASURY).balance(0L),
						cryptoCreate("autoRenewAccount").balance(0L),
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
						cryptoCreate(TOKEN_TREASURY).balance(0L),
						cryptoCreate("autoRenew").balance(0L)
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
		AtomicInteger maxUtf8Bytes = new AtomicInteger();

		return defaultHapiSpec("CreationValidatesName")
				.given(
						cryptoCreate(TOKEN_TREASURY).balance(0L),
						recordSystemProperty("tokens.maxTokenNameUtf8Bytes", Integer::parseInt, maxUtf8Bytes::set)
				).when().then(
						tokenCreate("primary")
								.name("")
								.logged()
								.hasPrecheck(MISSING_TOKEN_NAME),
						sourcing(() -> tokenCreate("tooLong")
								.name(nAscii(maxUtf8Bytes.get() + 1))
								.hasPrecheck(TOKEN_NAME_TOO_LONG)),
						sourcing(() -> tokenCreate("tooLongAgain")
								.name(nCurrencySymbols(maxUtf8Bytes.get() / 3 + 1))
								.hasPrecheck(TOKEN_NAME_TOO_LONG))
				);
	}

	public HapiApiSpec numAccountsAllowedIsDynamic() {
		final int MONOGAMOUS_NETWORK = 1;
		final int ADVENTUROUS_NETWORK = 1_000;

		return defaultHapiSpec("NumAccountsAllowedIsDynamic")
				.given(
						cryptoCreate(TOKEN_TREASURY).balance(0L)
				).when(
						fileUpdate(APP_PROPERTIES)
								.payingWith(ADDRESS_BOOK_CONTROL)
								.overridingProps(Map.of("tokens.maxPerAccount", "" + MONOGAMOUS_NETWORK)),
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
						tokenCreate(salted("secondary")).treasury(TOKEN_TREASURY)
				);
	}

	public HapiApiSpec creationValidatesSymbol() {
		AtomicInteger maxUtf8Bytes = new AtomicInteger();

		return defaultHapiSpec("CreationValidatesSymbol")
				.given(
						cryptoCreate(TOKEN_TREASURY).balance(0L),
						recordSystemProperty("tokens.maxSymbolUtf8Bytes", Integer::parseInt, maxUtf8Bytes::set)
				).when( ).then(
						tokenCreate("missingSymbol")
								.symbol("")
								.hasPrecheck(MISSING_TOKEN_SYMBOL),
						sourcing(() -> tokenCreate("tooLong")
								.symbol(nAscii(maxUtf8Bytes.get() + 1))
								.hasPrecheck(TOKEN_SYMBOL_TOO_LONG)),
						sourcing(() -> tokenCreate("tooLongAgain")
								.symbol(nCurrencySymbols(maxUtf8Bytes.get() / 3 + 1))
								.hasPrecheck(TOKEN_SYMBOL_TOO_LONG))
				);
	}

	private String nAscii(int n) {
		return IntStream.range(0, n).mapToObj(ignore -> "A").collect(Collectors.joining());
	}

	private String nCurrencySymbols(int n) {
		return IntStream.range(0, n).mapToObj(ignore -> "€").collect(Collectors.joining());
	}

	public HapiApiSpec creationRequiresAppropriateSigs() {
		return defaultHapiSpec("CreationRequiresAppropriateSigs")
				.given(
						cryptoCreate("payer"),
						cryptoCreate(TOKEN_TREASURY).balance(0L),
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
						cryptoCreate(TOKEN_TREASURY).balance(0L)
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
				.given().when().then(
						tokenCreate("sinking")
								.initialSupply(-1L)
								.hasPrecheck(INVALID_TOKEN_INITIAL_SUPPLY),
						tokenCreate("bad decimals")
								.decimals(-1)
								.hasPrecheck(INVALID_TOKEN_DECIMALS),
						tokenCreate("bad decimals")
								.decimals(1 << 31)
								.hasPrecheck(INVALID_TOKEN_DECIMALS),
						tokenCreate("bad initial supply")
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
						cryptoCreate(TOKEN_TREASURY).balance(1L)
				).when(
						tokenCreate(token)
								.treasury(TOKEN_TREASURY)
								.decimals(decimals)
								.initialSupply(initialSupply)
				).then(
						getAccountBalance(TOKEN_TREASURY)
								.hasTinyBars(1L)
								.hasTokenBalance(token, initialSupply)
				);
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}
