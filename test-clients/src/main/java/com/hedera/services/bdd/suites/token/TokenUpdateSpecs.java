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
import com.hedera.services.bdd.suites.HapiApiSuite;
import com.hederahashgraph.api.proto.java.TokenFreezeStatus;
import com.hederahashgraph.api.proto.java.TokenKycStatus;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTokenInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.burnToken;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoDelete;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.grantTokenKyc;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.mintToken;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenAssociate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenDelete;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenUnfreeze;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.wipeTokenAccount;
import static com.hedera.services.bdd.spec.transactions.token.CustomFeeSpecs.fixedHbarFee;
import static com.hedera.services.bdd.spec.transactions.token.CustomFeeSpecs.fixedHtsFee;
import static com.hedera.services.bdd.spec.transactions.token.CustomFeeSpecs.fractionalFee;
import static com.hedera.services.bdd.spec.transactions.token.CustomFeeSpecs.incompleteCustomFee;
import static com.hedera.services.bdd.spec.transactions.token.CustomFeeTests.fixedHbarFeeInSchedule;
import static com.hedera.services.bdd.spec.transactions.token.CustomFeeTests.fixedHtsFeeInSchedule;
import static com.hedera.services.bdd.spec.transactions.token.CustomFeeTests.fractionalFeeInSchedule;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.moving;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CUSTOM_FEES_ARE_MARKED_IMMUTABLE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CUSTOM_FEES_LIST_TOO_LONG;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CUSTOM_FEE_MUST_BE_POSITIVE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CUSTOM_FEE_NOT_FULLY_SPECIFIED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FRACTION_DIVIDES_BY_ZERO;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ADMIN_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_AUTORENEW_ACCOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_CUSTOM_FEE_COLLECTOR;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_EXPIRATION_TIME;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_RENEWAL_PERIOD;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SIGNATURE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_ID_IN_CUSTOM_FEES;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TREASURY_ACCOUNT_FOR_TOKEN;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ZERO_BYTE_IN_STRING;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_IS_IMMUTABLE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_NAME_TOO_LONG;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_NOT_ASSOCIATED_TO_FEE_COLLECTOR;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_SYMBOL_TOO_LONG;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_WAS_DELETED;

public class TokenUpdateSpecs extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(TokenUpdateSpecs.class);
	private static final int MAX_NAME_LENGTH = 100;
	private static final int MAX_SYMBOL_LENGTH = 100;
	private static final long A_HUNDRED_SECONDS = 100;

	private static String TOKEN_TREASURY = "treasury";
	private static final long defaultMaxLifetime =
			Long.parseLong(HapiSpecSetup.getDefaultNodeProps().get("entities.maxLifetime"));

	public static void main(String... args) {
		new TokenUpdateSpecs().runSuiteSync();
	}

	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		return List.of(new HapiApiSpec[] {
				symbolChanges(),
				standardImmutabilitySemanticsHold(),
				validAutoRenewWorks(),
				tooLongNameCheckHolds(),
				tooLongSymbolCheckHolds(),
				nameChanges(),
				keysChange(),
				validatesAlreadyDeletedToken(),
				treasuryEvolves(),
				deletedAutoRenewAccountCheckHolds(),
				renewalPeriodCheckHolds(),
				invalidTreasuryCheckHolds(),
				newTreasuryMustSign(),
				newTreasuryMustBeAssociated(),
				tokensCanBeMadeImmutableWithEmptyKeyList(),
				updateHappyPath(),
				validatesMissingAdminKey(),
				validatesMissingRef(),
				validatesNewExpiry(),
				/* HIP-18 */
				onlyValidCustomFeeScheduleCanBeUpdated(),
				customFeesOnceImmutableStayImmutable(),
				}
		);
	}

	private HapiApiSpec validatesNewExpiry() {
		final var smallBuffer = 12_345L;
		final var okExpiry = defaultMaxLifetime + Instant.now().getEpochSecond() - smallBuffer;
		final var excessiveExpiry = defaultMaxLifetime + Instant.now().getEpochSecond() + smallBuffer;
		return defaultHapiSpec("ValidatesNewExpiry")
				.given(
						tokenCreate("tbu")
				).when().then(
						tokenUpdate("tbu")
								.expiry(excessiveExpiry)
								.hasKnownStatus(INVALID_EXPIRATION_TIME),
						tokenUpdate("tbu")
								.expiry(okExpiry)
				);
	}

	private HapiApiSpec validatesAlreadyDeletedToken() {
		return defaultHapiSpec("ValidatesAlreadyDeletedToken")
				.given(
						newKeyNamed("adminKey"),
						cryptoCreate(TOKEN_TREASURY).balance(0L),
						tokenCreate("tbd")
								.adminKey("adminKey")
								.treasury(TOKEN_TREASURY),
						tokenDelete("tbd")
				).when().then(
						tokenUpdate("tbd")
								.hasKnownStatus(TOKEN_WAS_DELETED)
				);
	}

	private HapiApiSpec tokensCanBeMadeImmutableWithEmptyKeyList() {
		return defaultHapiSpec("TokensCanBeMadeImmutableWithEmptyKeyList")
				.given(
						newKeyNamed("initialAdmin"),
						cryptoCreate("neverToBe").balance(0L),
						tokenCreate("mutableForNow").adminKey("initialAdmin")
				).when(
						tokenUpdate("mutableForNow")
								.improperlyEmptyingAdminKey()
								.hasPrecheck(INVALID_ADMIN_KEY),
						tokenUpdate("mutableForNow").properlyEmptyingAdminKey()
				).then(
						getTokenInfo("mutableForNow"),
						tokenUpdate("mutableForNow")
								.treasury("neverToBe")
								.signedBy(GENESIS, "neverToBe")
								.hasKnownStatus(TOKEN_IS_IMMUTABLE)
				);
	}

	private HapiApiSpec standardImmutabilitySemanticsHold() {
		long then = Instant.now().getEpochSecond() + 1_234_567L;
		return defaultHapiSpec("StandardImmutabilitySemanticsHold")
				.given(
						tokenCreate("immutable").expiry(then)
				).when(
						tokenUpdate("immutable")
								.treasury(ADDRESS_BOOK_CONTROL)
								.hasKnownStatus(TOKEN_IS_IMMUTABLE),
						tokenUpdate("immutable")
								.expiry(then - 1)
								.hasKnownStatus(INVALID_EXPIRATION_TIME),
						tokenUpdate("immutable")
								.expiry(then + 1)
				).then(
						getTokenInfo("immutable").logged()
				);
	}

	private HapiApiSpec validatesMissingRef() {
		return defaultHapiSpec("UpdateValidatesRef")
				.given(
						cryptoCreate("payer")
				).when().then(
						tokenUpdate("0.0.0")
								.payingWith("payer")
								.signedBy("payer")
								.hasKnownStatus(INVALID_TOKEN_ID),
						tokenUpdate("1.2.3")
								.payingWith("payer")
								.signedBy("payer")
								.hasKnownStatus(INVALID_TOKEN_ID)
				);
	}

	private HapiApiSpec validatesMissingAdminKey() {
		return defaultHapiSpec("ValidatesMissingAdminKey")
				.given(
						cryptoCreate(TOKEN_TREASURY).balance(0L),
						cryptoCreate("payer"),
						tokenCreate("tbd").treasury(TOKEN_TREASURY)
				).when().then(
						tokenUpdate("tbd")
								.autoRenewAccount(GENESIS)
								.payingWith("payer")
								.signedBy("payer", GENESIS)
								.hasKnownStatus(TOKEN_IS_IMMUTABLE)
				);
	}

	public HapiApiSpec keysChange() {
		return defaultHapiSpec("KeysChange")
				.given(
						newKeyNamed("adminKey"),
						newKeyNamed("newAdminKey"),
						newKeyNamed("kycThenFreezeKey"),
						newKeyNamed("freezeThenKycKey"),
						newKeyNamed("wipeThenSupplyKey"),
						newKeyNamed("supplyThenWipeKey"),
						cryptoCreate("misc").balance(0L),
						cryptoCreate(TOKEN_TREASURY).balance(0L),
						tokenCreate("tbu")
								.treasury(TOKEN_TREASURY)
								.freezeDefault(true)
								.initialSupply(10)
								.adminKey("adminKey")
								.kycKey("kycThenFreezeKey")
								.freezeKey("freezeThenKycKey")
								.supplyKey("supplyThenWipeKey")
								.wipeKey("wipeThenSupplyKey")
				).when(
						getTokenInfo("tbu").logged(),
						tokenUpdate("tbu")
								.adminKey("newAdminKey")
								.kycKey("freezeThenKycKey")
								.freezeKey("kycThenFreezeKey")
								.wipeKey("supplyThenWipeKey")
								.supplyKey("wipeThenSupplyKey"),
						tokenAssociate("misc", "tbu")
				).then(
						getTokenInfo("tbu").logged(),
						grantTokenKyc("tbu", "misc")
								.signedBy(GENESIS, "freezeThenKycKey"),
						tokenUnfreeze("tbu", "misc")
								.signedBy(GENESIS, "kycThenFreezeKey"),
						getAccountInfo("misc").logged(),
						cryptoTransfer(moving(5, "tbu")
								.between(TOKEN_TREASURY, "misc")),
						mintToken("tbu", 10)
								.signedBy(GENESIS, "wipeThenSupplyKey"),
						burnToken("tbu", 10)
								.signedBy(GENESIS, "wipeThenSupplyKey"),
						wipeTokenAccount("tbu", "misc", 5)
								.signedBy(GENESIS, "supplyThenWipeKey"),
						getAccountInfo(TOKEN_TREASURY).logged()
				);
	}

	public HapiApiSpec newTreasuryMustBeAssociated() {
		return defaultHapiSpec("NewTreasuryMustBeAssociated")
				.given(
						newKeyNamed("adminKey"),
						cryptoCreate("oldTreasury").balance(0L),
						tokenCreate("tbu")
								.adminKey("adminKey")
								.treasury("oldTreasury")
				).when(
						cryptoCreate("newTreasury").balance(0L)
				).then(
						tokenUpdate("tbu")
								.treasury("newTreasury").hasKnownStatus(INVALID_TREASURY_ACCOUNT_FOR_TOKEN)
				);
	}

	public HapiApiSpec newTreasuryMustSign() {
		return defaultHapiSpec("NewTreasuryMustSign")
				.given(
						newKeyNamed("adminKey"),
						cryptoCreate("oldTreasury").balance(0L),
						cryptoCreate("newTreasury").balance(0L),
						tokenCreate("tbu")
								.adminKey("adminKey")
								.treasury("oldTreasury")
				).when(
						tokenAssociate("newTreasury", "tbu"),
						cryptoTransfer(moving(1, "tbu")
								.between("oldTreasury", "newTreasury"))
				).then(
						tokenUpdate("tbu")
								.treasury("newTreasury")
								.signedBy(GENESIS, "adminKey")
								.hasKnownStatus(INVALID_SIGNATURE),
						tokenUpdate("tbu")
								.treasury("newTreasury")
				);
	}

	public HapiApiSpec treasuryEvolves() {
		return defaultHapiSpec("TreasuryEvolves")
				.given(
						newKeyNamed("adminKey"),
						newKeyNamed("kycKey"),
						newKeyNamed("freezeKey"),
						cryptoCreate("oldTreasury").balance(0L),
						cryptoCreate("newTreasury").balance(0L),
						tokenCreate("tbu")
								.adminKey("adminKey")
								.freezeDefault(true)
								.kycKey("kycKey")
								.freezeKey("freezeKey")
								.treasury("oldTreasury")
				).when(
						getAccountInfo("oldTreasury").logged(),
						getAccountInfo("newTreasury").logged(),
						tokenAssociate("newTreasury", "tbu"),
						tokenUpdate("tbu")
								.treasury("newTreasury")
								.via("treasuryUpdateTxn")
				).then(
						getAccountInfo("oldTreasury").logged(),
						getAccountInfo("newTreasury").logged(),
						getTxnRecord("treasuryUpdateTxn").logged()
				);
	}

	public HapiApiSpec validAutoRenewWorks() {
		long firstPeriod = 500_000, secondPeriod = 600_000;
		return defaultHapiSpec("AutoRenewInfoChanges")
				.given(
						cryptoCreate("autoRenew").balance(0L),
						cryptoCreate("newAutoRenew").balance(0L),
						newKeyNamed("adminKey")
				).when(
						tokenCreate("tbu")
								.adminKey("adminKey")
								.autoRenewAccount("autoRenew")
								.autoRenewPeriod(firstPeriod),
						tokenUpdate("tbu")
								.signedBy(GENESIS)
								.autoRenewAccount("newAutoRenew")
								.autoRenewPeriod(secondPeriod)
								.hasKnownStatus(INVALID_SIGNATURE),
						tokenUpdate("tbu")
								.autoRenewAccount("newAutoRenew")
								.autoRenewPeriod(secondPeriod)
				).then(
						getTokenInfo("tbu").logged()
				);
	}

	public HapiApiSpec symbolChanges() {
		var hopefullyUnique = "ORIGINAL" + TxnUtils.randomUppercase(5);

		return defaultHapiSpec("SymbolChanges")
				.given(
						newKeyNamed("adminKey"),
						cryptoCreate(TOKEN_TREASURY).balance(0L)
				).when(
						tokenCreate("tbu")
								.adminKey("adminKey")
								.treasury(TOKEN_TREASURY),
						tokenUpdate("tbu")
								.symbol(hopefullyUnique)
				).then(
						getTokenInfo("tbu").hasSymbol(hopefullyUnique),
						tokenAssociate(GENESIS, "tbu"),
						cryptoTransfer(
								moving(1, "tbu").between(TOKEN_TREASURY, GENESIS))
				);
	}

	public HapiApiSpec nameChanges() {
		var hopefullyUnique = "ORIGINAL" + TxnUtils.randomUppercase(5);

		return defaultHapiSpec("NameChanges")
				.given(
						newKeyNamed("adminKey"),
						cryptoCreate(TOKEN_TREASURY).balance(0L)
				).when(
						tokenCreate("tbu")
								.adminKey("adminKey")
								.treasury(TOKEN_TREASURY),
						tokenUpdate("tbu")
								.name(hopefullyUnique)
				).then(
						getTokenInfo("tbu").hasName(hopefullyUnique)
				);
	}

	public HapiApiSpec tooLongNameCheckHolds() {
		var tooLongName = "ORIGINAL" + TxnUtils.randomUppercase(MAX_NAME_LENGTH + 1);

		return defaultHapiSpec("TooLongNameCheckHolds")
				.given(
						newKeyNamed("adminKey"),
						cryptoCreate(TOKEN_TREASURY).balance(0L)
				).when(
						tokenCreate("tbu")
								.adminKey("adminKey")
								.treasury(TOKEN_TREASURY)
				).then(
						tokenUpdate("tbu")
								.name(tooLongName)
								.hasPrecheck(TOKEN_NAME_TOO_LONG)
				);
	}

	public HapiApiSpec tooLongSymbolCheckHolds() {
		var tooLongSymbol = TxnUtils.randomUppercase(MAX_SYMBOL_LENGTH + 1);

		return defaultHapiSpec("TooLongSymbolCheckHolds")
				.given(
						newKeyNamed("adminKey"),
						cryptoCreate(TOKEN_TREASURY).balance(0L)
				).when(
						tokenCreate("tbu")
								.adminKey("adminKey")
								.treasury(TOKEN_TREASURY)
				).then(
						tokenUpdate("tbu")
								.symbol(tooLongSymbol)
								.hasPrecheck(TOKEN_SYMBOL_TOO_LONG)
				);
	}

	public HapiApiSpec deletedAutoRenewAccountCheckHolds() {
		return defaultHapiSpec("DeletedAutoRenewAccountCheckHolds")
				.given(
						newKeyNamed("adminKey"),
						cryptoCreate("autoRenewAccount").balance(0L),
						cryptoCreate(TOKEN_TREASURY).balance(0L)
				).when(
						cryptoDelete("autoRenewAccount"),
						tokenCreate("tbu")
								.adminKey("adminKey")
								.treasury(TOKEN_TREASURY)
				).then(
						tokenUpdate("tbu")
								.autoRenewAccount("autoRenewAccount")
								.hasKnownStatus(INVALID_AUTORENEW_ACCOUNT)
				);
	}

	public HapiApiSpec renewalPeriodCheckHolds() {
		return defaultHapiSpec("RenewalPeriodCheckHolds")
				.given(
						newKeyNamed("adminKey"),
						cryptoCreate(TOKEN_TREASURY).balance(0L),
						cryptoCreate("autoRenewAccount").balance(0L)
				).when(
						tokenCreate("tbu")
								.adminKey("adminKey")
								.treasury(TOKEN_TREASURY),
						tokenCreate("withAutoRenewAcc")
								.adminKey("adminKey")
								.autoRenewAccount("autoRenewAccount")
								.treasury(TOKEN_TREASURY)
				).then(
						tokenUpdate("tbu")
								.autoRenewAccount("autoRenewAccount")
								.autoRenewPeriod(-1123)
								.hasKnownStatus(INVALID_RENEWAL_PERIOD),
						tokenUpdate("tbu")
								.autoRenewAccount("autoRenewAccount")
								.autoRenewPeriod(0)
								.hasKnownStatus(INVALID_RENEWAL_PERIOD),
						tokenUpdate("withAutoRenewAcc")
								.autoRenewPeriod(-1)
								.hasKnownStatus(INVALID_RENEWAL_PERIOD),
						tokenUpdate("withAutoRenewAcc")
								.autoRenewPeriod(100000000000L)
								.hasKnownStatus(INVALID_RENEWAL_PERIOD)
				);
	}

	public HapiApiSpec invalidTreasuryCheckHolds() {
		return defaultHapiSpec("InvalidTreasuryCheckHolds")
				.given(
						newKeyNamed("adminKey"),
						cryptoCreate(TOKEN_TREASURY).balance(0L),
						cryptoCreate("invalidTreasury").balance(0L)
				).when(
						cryptoDelete("invalidTreasury"),
						tokenCreate("tbu")
								.adminKey("adminKey")
								.treasury(TOKEN_TREASURY)
				).then(
						tokenUpdate("tbu")
								.treasury("invalidTreasury")
								.hasKnownStatus(INVALID_TREASURY_ACCOUNT_FOR_TOKEN)
				);
	}

	public HapiApiSpec updateHappyPath() {
		String originalMemo = "First things first";
		String updatedMemo = "Nothing left to do";
		String saltedName = salted("primary");
		String newSaltedName = salted("primary");
		return defaultHapiSpec("UpdateHappyPath")
				.given(
						cryptoCreate(TOKEN_TREASURY).balance(0L),
						cryptoCreate("newTokenTreasury").balance(0L),
						cryptoCreate("autoRenewAccount").balance(0L),
						cryptoCreate("newAutoRenewAccount").balance(0L),
						newKeyNamed("adminKey"),
						newKeyNamed("freezeKey"),
						newKeyNamed("newFreezeKey"),
						newKeyNamed("kycKey"),
						newKeyNamed("newKycKey"),
						newKeyNamed("supplyKey"),
						newKeyNamed("newSupplyKey"),
						newKeyNamed("wipeKey"),
						newKeyNamed("newWipeKey"),
						tokenCreate("primary")
								.name(saltedName)
								.entityMemo(originalMemo)
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
				).when(
						tokenAssociate("newTokenTreasury", "primary"),
						tokenUpdate("primary")
								.entityMemo(ZERO_BYTE_MEMO)
								.hasPrecheck(INVALID_ZERO_BYTE_IN_STRING),
						tokenUpdate("primary")
								.name(newSaltedName)
								.entityMemo(updatedMemo)
								.treasury("newTokenTreasury")
								.autoRenewAccount("newAutoRenewAccount")
								.autoRenewPeriod(101)
								.freezeKey("newFreezeKey")
								.kycKey("newKycKey")
								.supplyKey("newSupplyKey")
								.wipeKey("newWipeKey")
				).then(
						getAccountBalance(TOKEN_TREASURY)
								.hasTokenBalance("primary", 0),
						getAccountBalance("newTokenTreasury")
								.hasTokenBalance("primary", 500),
						getAccountInfo(TOKEN_TREASURY)
								.hasToken(
										ExpectedTokenRel.relationshipWith("primary")
												.balance(0)
								),
						getAccountInfo("newTokenTreasury")
								.hasToken(
										ExpectedTokenRel.relationshipWith("primary")
												.freeze(TokenFreezeStatus.Unfrozen)
												.kyc(TokenKycStatus.Granted)
												.balance(500)
								),
						getTokenInfo("primary")
								.logged()
								.hasEntityMemo(updatedMemo)
								.hasRegisteredId("primary")
								.hasName(newSaltedName)
								.hasTreasury("newTokenTreasury")
								.hasFreezeKey("primary")
								.hasKycKey("primary")
								.hasSupplyKey("primary")
								.hasWipeKey("primary")
								.hasTotalSupply(500)
								.hasAutoRenewAccount("newAutoRenewAccount")
								.hasAutoRenewPeriod(101L)
				);
	}

	private HapiApiSpec onlyValidCustomFeeScheduleCanBeUpdated() {
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

		final var adminKey = "admin";

		final var newHbarAmount = 17_234L;
		final var newHtsAmount = 27_345L;
		final var newNumerator = 17;
		final var newDenominator = 107;
		final var newMinimumToCollect = 57;
		final var newMaximumToCollect = 507;

		final var newFeeDenom = "newDenom";
		final var newHbarCollector = "newHbarFee";
		final var newHtsCollector = "newDenomFee";
		final var newTokenCollector = "newFractionalFee";

		return defaultHapiSpec("OnlyValidCustomFeeScheduleCanBeUpdated")
				.given(
						fileUpdate(APP_PROPERTIES)
								.payingWith(GENESIS)
								.overridingProps(Map.of("tokens.maxCustomFeesAllowed", "10")),
						newKeyNamed(adminKey),
						cryptoCreate(htsCollector),
						cryptoCreate(newHtsCollector),
						cryptoCreate(hbarCollector),
						cryptoCreate(newHbarCollector),
						cryptoCreate(tokenCollector),
						cryptoCreate(newTokenCollector),
						tokenCreate(feeDenom).treasury(htsCollector),
						tokenCreate(newFeeDenom).treasury(newHtsCollector),
						tokenCreate(token)
								.adminKey(adminKey)
								.treasury(tokenCollector)
								.customFeesMutable(true)
								.withCustom(fixedHbarFee(hbarAmount, hbarCollector))
								.withCustom(fixedHtsFee(htsAmount, feeDenom, htsCollector))
								.withCustom(fractionalFee(
										numerator, denominator,
										minimumToCollect, OptionalLong.of(maximumToCollect),
										tokenCollector)),
						fileUpdate(APP_PROPERTIES)
								.payingWith(GENESIS)
								.overridingProps(Map.of("tokens.maxCustomFeesAllowed", "1"))
						)
				.when(
						tokenUpdate(token)
								.treasury(tokenCollector)
								.withCustom(fractionalFee(
										numerator, 0,
										minimumToCollect, OptionalLong.of(maximumToCollect),
										tokenCollector))
								.hasKnownStatus(FRACTION_DIVIDES_BY_ZERO),
						tokenUpdate(token)
								.treasury(tokenCollector)
								.withCustom(fractionalFee(
										-numerator, denominator,
										minimumToCollect, OptionalLong.of(maximumToCollect),
										tokenCollector))
								.hasKnownStatus(CUSTOM_FEE_MUST_BE_POSITIVE),
						tokenUpdate(token)
								.treasury(tokenCollector)
								.withCustom(fractionalFee(
										numerator, denominator,
										-minimumToCollect, OptionalLong.of(maximumToCollect),
										tokenCollector))
								.hasKnownStatus(CUSTOM_FEE_MUST_BE_POSITIVE),
						tokenUpdate(token)
								.treasury(tokenCollector)
								.withCustom(fractionalFee(
										numerator, denominator,
										minimumToCollect, OptionalLong.of(-maximumToCollect),
										tokenCollector))
								.hasKnownStatus(CUSTOM_FEE_MUST_BE_POSITIVE),
						tokenUpdate(token)
								.treasury(tokenCollector)
								.withCustom(fixedHbarFee(hbarAmount, hbarCollector))
								.withCustom(fixedHtsFee(htsAmount, feeDenom, htsCollector))
								.hasKnownStatus(CUSTOM_FEES_LIST_TOO_LONG),
						tokenUpdate(token)
								.treasury(tokenCollector)
								.withCustom(fixedHbarFee(hbarAmount, invalidEntityId))
								.hasKnownStatus(INVALID_CUSTOM_FEE_COLLECTOR),
						tokenUpdate(token)
								.treasury(tokenCollector)
								.withCustom(fixedHtsFee(htsAmount, invalidEntityId, htsCollector))
								.hasKnownStatus(INVALID_TOKEN_ID_IN_CUSTOM_FEES),
						tokenUpdate(token)
								.treasury(tokenCollector)
								.withCustom(fixedHtsFee(htsAmount, feeDenom, hbarCollector))
								.hasKnownStatus(TOKEN_NOT_ASSOCIATED_TO_FEE_COLLECTOR),
						tokenUpdate(token)
								.treasury(tokenCollector)
								.withCustom(fixedHtsFee(-htsAmount, feeDenom, htsCollector))
								.hasKnownStatus(CUSTOM_FEE_MUST_BE_POSITIVE),
						tokenUpdate(token)
								.treasury(tokenCollector)
								.withCustom(incompleteCustomFee(hbarCollector))
								.hasKnownStatus(CUSTOM_FEE_NOT_FULLY_SPECIFIED),
						fileUpdate(APP_PROPERTIES)
								.payingWith(GENESIS)
								.overridingProps(Map.of("tokens.maxCustomFeesAllowed", "10")),
						tokenAssociate(newTokenCollector, token),
						tokenUpdate(token)
								.treasury(newTokenCollector)
								.customFeesMutable(true)
								.withCustom(fixedHbarFee(newHbarAmount, newHbarCollector))
								.withCustom(fixedHtsFee(newHtsAmount, newFeeDenom, newHtsCollector))
								.withCustom(fractionalFee(
										newNumerator, newDenominator,
										newMinimumToCollect, OptionalLong.of(newMaximumToCollect),
										newTokenCollector))
						)
				.then(
						getTokenInfo(token)
								.hasCustomFeesMutable(true)
								.hasCustom(fixedHbarFeeInSchedule(newHbarAmount, newHbarCollector))
								.hasCustom(fixedHtsFeeInSchedule(newHtsAmount, newFeeDenom, newHtsCollector))
								.hasCustom(fractionalFeeInSchedule(
										newNumerator, newDenominator,
										newMinimumToCollect, OptionalLong.of(newMaximumToCollect),
										newTokenCollector))
				);
	}

	private HapiApiSpec customFeesOnceImmutableStayImmutable() {
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

		final var adminKey = "admin";

		return defaultHapiSpec("CustomFeesOnceImmutableStayImmutable")
				.given(
						newKeyNamed(adminKey),
						cryptoCreate(htsCollector),
						cryptoCreate(hbarCollector),
						cryptoCreate(tokenCollector),
						tokenCreate(feeDenom).treasury(htsCollector),
						tokenCreate(token)
								.adminKey(adminKey)
								.treasury(tokenCollector)
								.customFeesMutable(true)
								.withCustom(fixedHbarFee(hbarAmount, hbarCollector))
								.withCustom(fixedHtsFee(htsAmount, feeDenom, htsCollector))
								.withCustom(fractionalFee(
										numerator, denominator,
										minimumToCollect, OptionalLong.of(maximumToCollect),
										tokenCollector))

						)
				.when(
						tokenUpdate(token)
								.customFeesMutable(false)
								.withCustom(fixedHbarFee(hbarAmount, hbarCollector))
								.withCustom(fixedHtsFee(htsAmount, feeDenom, htsCollector))
								.withCustom(fractionalFee(
										numerator, denominator,
										minimumToCollect, OptionalLong.of(maximumToCollect),
										tokenCollector))
				)
				.then(
						getTokenInfo(token)
								.hasCustomFeesMutable(false)
								.hasCustom(fixedHbarFeeInSchedule(hbarAmount, hbarCollector))
								.hasCustom(fixedHtsFeeInSchedule(htsAmount, feeDenom, htsCollector))
								.hasCustom(fractionalFeeInSchedule(
										numerator, denominator,
										minimumToCollect, OptionalLong.of(maximumToCollect),
										tokenCollector)),
						tokenUpdate(token)
								.withCustom(fixedHbarFee(hbarAmount, hbarCollector))
								.withCustom(fixedHtsFee(htsAmount, feeDenom, htsCollector))
								.withCustom(fractionalFee(
										numerator, denominator,
										minimumToCollect, OptionalLong.of(maximumToCollect),
										tokenCollector))
								.hasKnownStatus(CUSTOM_FEES_ARE_MARKED_IMMUTABLE)
				);
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}
