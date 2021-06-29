package com.hedera.services.bdd.suites.token;

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
import com.hedera.services.bdd.spec.HapiSpecSetup;
import com.hedera.services.bdd.spec.queries.crypto.ExpectedTokenRel;
import com.hedera.services.bdd.spec.transactions.TxnUtils;
import com.hedera.services.bdd.spec.utilops.UtilVerbs;
import com.hedera.services.bdd.suites.HapiApiSuite;
import com.hederahashgraph.api.proto.java.TokenFreezeStatus;
import com.hederahashgraph.api.proto.java.TokenKycStatus;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
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
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenDelete;
import static com.hedera.services.bdd.spec.transactions.token.CustomFeeSpecs.fixedHbarFee;
import static com.hedera.services.bdd.spec.transactions.token.CustomFeeSpecs.fixedHtsFee;
import static com.hedera.services.bdd.spec.transactions.token.CustomFeeSpecs.fractionalFee;
import static com.hedera.services.bdd.spec.transactions.token.CustomFeeSpecs.incompleteCustomFee;
import static com.hedera.services.bdd.spec.transactions.token.CustomFeeTests.fixedHbarFeeInSchedule;
import static com.hedera.services.bdd.spec.transactions.token.CustomFeeTests.fixedHtsFeeInSchedule;
import static com.hedera.services.bdd.spec.transactions.token.CustomFeeTests.fractionalFeeInSchedule;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.recordSystemProperty;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sourcing;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CUSTOM_FEES_LIST_TOO_LONG;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CUSTOM_FEE_MUST_BE_POSITIVE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CUSTOM_FEE_NOT_FULLY_SPECIFIED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FRACTION_DIVIDES_BY_ZERO;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_AUTORENEW_ACCOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_CUSTOM_FEE_COLLECTOR;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_EXPIRATION_TIME;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_RENEWAL_PERIOD;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SIGNATURE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_DECIMALS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_ID_IN_CUSTOM_FEES;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_INITIAL_SUPPLY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TREASURY_ACCOUNT_FOR_TOKEN;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ZERO_BYTE_IN_STRING;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.MISSING_TOKEN_NAME;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.MISSING_TOKEN_SYMBOL;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKENS_PER_ACCOUNT_LIMIT_EXCEEDED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_HAS_NO_FREEZE_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_NAME_TOO_LONG;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_NOT_ASSOCIATED_TO_FEE_COLLECTOR;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_SYMBOL_TOO_LONG;

public class TokenCreateSpecs extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(TokenCreateSpecs.class);

	private static String TOKEN_TREASURY = "treasury";
	private static final long A_HUNDRED_SECONDS = 100;

	private static final long defaultMaxLifetime =
			Long.parseLong(HapiSpecSetup.getDefaultNodeProps().get("entities.maxLifetime"));

	public static void main(String... args) {
		new TokenCreateSpecs().runSuiteSync();
	}

	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		return List.of(new HapiApiSpec[] {
						creationValidatesMemo(),
						creationValidatesName(),
						creationValidatesSymbol(),
						treasuryHasCorrectBalance(),
						creationRequiresAppropriateSigs(),
						creationRequiresAppropriateSigsHappyPath(),
						initialSupplyMustBeSane(),
						creationYieldsExpectedToken(),
						creationSetsExpectedName(),
						creationValidatesTreasuryAccount(),
						autoRenewValidationWorks(),
						creationWithoutKYCSetsCorrectStatus(),
						creationValidatesExpiry(),
						creationValidatesFreezeDefaultWithNoFreezeKey(),
						creationSetsCorrectExpiry(),
						creationHappyPath(),
						numAccountsAllowedIsDynamic(),
						worksAsExpectedWithDefaultTokenId(),
						cannotCreateWithExcessiveLifetime(),
						/* HIP-18 */
						onlyValidCustomFeeScheduleCanBeCreated(),
				}
		);
	}


	private HapiApiSpec worksAsExpectedWithDefaultTokenId() {
		return defaultHapiSpec("WorksAsExpectedWithDefaultTokenId")
				.given().when().then(
						getTokenInfo("0.0.0").hasCostAnswerPrecheck(INVALID_TOKEN_ID)
				);
	}

	public HapiApiSpec cannotCreateWithExcessiveLifetime() {
		final var smallBuffer = 12_345L;
		final var okExpiry = defaultMaxLifetime + Instant.now().getEpochSecond() - smallBuffer;
		final var excessiveExpiry = defaultMaxLifetime + Instant.now().getEpochSecond() + smallBuffer;
		return defaultHapiSpec("CannotCreateWithExcessiveLifetime")
				.given().when().then(
						tokenCreate("neverToBe")
								.expiry(excessiveExpiry)
								.hasKnownStatus(INVALID_EXPIRATION_TIME),
						tokenCreate("neverToBe")
								.expiry(okExpiry)
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
		String memo = "JUMP";
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
								.entityMemo(memo)
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
								.hasCustomFeesMutable(false)
								.hasRegisteredId("primary")
								.hasEntityMemo(memo)
								.hasName(saltedName)
								.hasTreasury(TOKEN_TREASURY)
								.hasAutoRenewPeriod(A_HUNDRED_SECONDS)
								.hasValidExpiry()
								.hasDecimals(1)
								.hasAdminKey("primary")
								.hasFreezeKey("primary")
								.hasKycKey("primary")
								.hasSupplyKey("primary")
								.hasWipeKey("primary")
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

	public HapiApiSpec creationValidatesMemo() {
		return defaultHapiSpec("CreationValidatesMemo")
				.given().when().then(
						tokenCreate("primary")
								.entityMemo("N\u0000!!!")
								.hasPrecheck(INVALID_ZERO_BYTE_IN_STRING)
				);
	}

	public HapiApiSpec onlyValidCustomFeeScheduleCanBeCreated() {
		final var hbarAmount = 1_234L;
		final var htsAmount = 2_345L;
		final var numerator = 1;
		final var denominator = 10;
		final var minimumToCollect = 5;
		final var maximumToCollect = 50;

		final var token = "withCustomSchedules";
		final var feeDenom = "denom";
		final var hbarCollector = "hbarFee";
		final var htsCollector = "denomFee";
		final var tokenCollector = "fractionalFee";
		final var invalidEntityId = "1.2.786";
		final var negativeHtsFee = -100L;

		final var customFeesKey = "antique";

		return defaultHapiSpec("OnlyValidCustomFeeScheduleCanBeCreated")
				.given(
						newKeyNamed(customFeesKey),
						cryptoCreate(htsCollector),
						cryptoCreate(hbarCollector),
						cryptoCreate(tokenCollector),
						tokenCreate(feeDenom).treasury(htsCollector),
						fileUpdate(APP_PROPERTIES)
								.payingWith(GENESIS)
								.overridingProps(Map.of("tokens.maxCustomFeesAllowed", "1"))
				).when(
						tokenCreate(token)
								.treasury(tokenCollector)
								.withCustom(fractionalFee(
										numerator, 0,
										minimumToCollect, OptionalLong.of(maximumToCollect),
										tokenCollector))
								.hasKnownStatus(FRACTION_DIVIDES_BY_ZERO),
						tokenCreate(token)
								.treasury(tokenCollector)
								.withCustom(fixedHbarFee(hbarAmount, hbarCollector))
								.withCustom(fixedHtsFee(htsAmount, feeDenom, htsCollector))
								.hasKnownStatus(CUSTOM_FEES_LIST_TOO_LONG),
						tokenCreate(token)
								.treasury(tokenCollector)
								.withCustom(fixedHbarFee(hbarAmount, invalidEntityId))
								.hasKnownStatus(INVALID_CUSTOM_FEE_COLLECTOR),
						tokenCreate(token)
								.treasury(tokenCollector)
								.withCustom(fixedHtsFee(htsAmount, invalidEntityId, htsCollector))
								.hasKnownStatus(INVALID_TOKEN_ID_IN_CUSTOM_FEES),
						tokenCreate(token)
								.treasury(tokenCollector)
								.withCustom(fixedHtsFee(htsAmount, feeDenom, hbarCollector))
								.hasKnownStatus(TOKEN_NOT_ASSOCIATED_TO_FEE_COLLECTOR),
						tokenCreate(token)
								.treasury(tokenCollector)
								.withCustom(incompleteCustomFee(hbarCollector))
								.hasKnownStatus(CUSTOM_FEE_NOT_FULLY_SPECIFIED),
						tokenCreate(token)
								.treasury(tokenCollector)
								.withCustom(fixedHtsFee(negativeHtsFee, feeDenom, hbarCollector))
								.hasKnownStatus(CUSTOM_FEE_MUST_BE_POSITIVE),
						tokenCreate(token)
								.treasury(tokenCollector)
								.withCustom(fractionalFee(
										numerator, -denominator,
										minimumToCollect, OptionalLong.of(maximumToCollect),
										tokenCollector))
								.hasKnownStatus(CUSTOM_FEE_MUST_BE_POSITIVE),
						tokenCreate(token)
								.treasury(tokenCollector)
								.withCustom(fractionalFee(
										numerator, denominator,
										-minimumToCollect, OptionalLong.of(maximumToCollect),
										tokenCollector))
								.hasKnownStatus(CUSTOM_FEE_MUST_BE_POSITIVE),
						tokenCreate(token)
								.treasury(tokenCollector)
								.withCustom(fractionalFee(
										numerator, denominator,
										minimumToCollect, OptionalLong.of(-maximumToCollect),
										tokenCollector))
								.hasKnownStatus(CUSTOM_FEE_MUST_BE_POSITIVE),
						fileUpdate(APP_PROPERTIES)
								.payingWith(GENESIS)
								.overridingProps(Map.of("tokens.maxCustomFeesAllowed", "10")),
						tokenCreate(token)
								.treasury(tokenCollector)
								.withCustom(fixedHbarFee(hbarAmount, hbarCollector))
								.withCustom(fixedHtsFee(htsAmount, feeDenom, htsCollector))
								.withCustom(fractionalFee(
										numerator, denominator,
										minimumToCollect, OptionalLong.of(maximumToCollect),
										tokenCollector))
				).then(
						getTokenInfo(token)
								.hasCustomFeesMutable(false)
								.hasCustom(fixedHbarFeeInSchedule(hbarAmount, hbarCollector))
								.hasCustom(fixedHtsFeeInSchedule(htsAmount, feeDenom, htsCollector))
								.hasCustom(fractionalFeeInSchedule(
										numerator, denominator,
										minimumToCollect, OptionalLong.of(maximumToCollect),
										tokenCollector))
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
						tokenCreate("primary")
								.name("T\u0000ken")
								.logged()
								.hasPrecheck(INVALID_ZERO_BYTE_IN_STRING),
						sourcing(() -> tokenCreate("tooLong")
								.name(TxnUtils.nAscii(maxUtf8Bytes.get() + 1))
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
						newKeyNamed("admin"),
						cryptoCreate(TOKEN_TREASURY).balance(0L)
				).when(
						fileUpdate(APP_PROPERTIES)
								.payingWith(ADDRESS_BOOK_CONTROL)
								.overridingProps(Map.of("tokens.maxPerAccount", "" + MONOGAMOUS_NETWORK)),
						tokenCreate("primary")
								.adminKey("admin")
								.treasury(TOKEN_TREASURY),
						tokenCreate("secondaryFails")
								.treasury(TOKEN_TREASURY)
								.hasKnownStatus(TOKENS_PER_ACCOUNT_LIMIT_EXCEEDED),
						tokenDelete("primary"),
						/* Deleted tokens still count against your max allowed associations. */
						tokenCreate("secondaryFailsAgain")
								.treasury(TOKEN_TREASURY)
								.hasKnownStatus(TOKENS_PER_ACCOUNT_LIMIT_EXCEEDED)
				).then(
						fileUpdate(APP_PROPERTIES)
								.payingWith(ADDRESS_BOOK_CONTROL)
								.overridingProps(Map.of(
										"tokens.maxPerAccount", "" + ADVENTUROUS_NETWORK
								)),
						tokenCreate("secondary").treasury(TOKEN_TREASURY)
				);
	}

	public HapiApiSpec creationValidatesSymbol() {
		AtomicInteger maxUtf8Bytes = new AtomicInteger();

		return defaultHapiSpec("CreationValidatesSymbol")
				.given(
						cryptoCreate(TOKEN_TREASURY).balance(0L),
						recordSystemProperty("tokens.maxSymbolUtf8Bytes", Integer::parseInt, maxUtf8Bytes::set)
				).when().then(
						tokenCreate("missingSymbol")
								.symbol("")
								.hasPrecheck(MISSING_TOKEN_SYMBOL),
						tokenCreate("primary")
								.name("T\u0000ken")
								.logged()
								.hasPrecheck(INVALID_ZERO_BYTE_IN_STRING),
						sourcing(() -> tokenCreate("tooLong")
								.symbol(TxnUtils.nAscii(maxUtf8Bytes.get() + 1))
								.hasPrecheck(TOKEN_SYMBOL_TOO_LONG)),
						sourcing(() -> tokenCreate("tooLongAgain")
								.symbol(nCurrencySymbols(maxUtf8Bytes.get() / 3 + 1))
								.hasPrecheck(TOKEN_SYMBOL_TOO_LONG))
				);
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

	public HapiApiSpec creationRequiresAppropriateSigsHappyPath() {
		return defaultHapiSpec("CreationRequiresAppropriateSigsHappyPath")
				.given(
						cryptoCreate("payer"),
						cryptoCreate(TOKEN_TREASURY).balance(0L),
						newKeyNamed("adminKey")
				).when().then(
						tokenCreate("shouldWork")
								.treasury(TOKEN_TREASURY)
								.payingWith("payer")
								.adminKey("adminKey")
								.signedBy(TOKEN_TREASURY, "payer", "adminKey")
								.hasKnownStatus(SUCCESS)
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
