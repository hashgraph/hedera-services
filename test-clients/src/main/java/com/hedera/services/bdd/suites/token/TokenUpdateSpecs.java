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

import com.google.protobuf.ByteString;
import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.spec.HapiSpecSetup;
import com.hedera.services.bdd.spec.queries.crypto.ExpectedTokenRel;
import com.hedera.services.bdd.spec.transactions.TxnUtils;
import com.hedera.services.bdd.suites.HapiApiSuite;
import com.hederahashgraph.api.proto.java.TokenFreezeStatus;
import com.hederahashgraph.api.proto.java.TokenKycStatus;
import com.hederahashgraph.api.proto.java.TokenPauseStatus;
import com.hederahashgraph.api.proto.java.TokenType;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.Instant;
import java.util.List;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTokenInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTokenNftInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.burnToken;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoDelete;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.grantTokenKyc;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.mintToken;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenAssociate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenDelete;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenFeeScheduleUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenUnfreeze;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.wipeTokenAccount;
import static com.hedera.services.bdd.spec.transactions.token.CustomFeeSpecs.fixedHbarFee;
import static com.hedera.services.bdd.spec.transactions.token.CustomFeeSpecs.fixedHbarFeeInheritingRoyaltyCollector;
import static com.hedera.services.bdd.spec.transactions.token.CustomFeeSpecs.royaltyFeeWithFallback;
import static com.hedera.services.bdd.spec.transactions.token.CustomFeeTests.fixedHbarFeeInSchedule;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.moving;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.movingUnique;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ADMIN_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_AUTORENEW_ACCOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_CUSTOM_FEE_SCHEDULE_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_EXPIRATION_TIME;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_RENEWAL_PERIOD;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SIGNATURE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TREASURY_ACCOUNT_FOR_TOKEN;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ZERO_BYTE_IN_STRING;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_HAS_NO_FEE_SCHEDULE_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_IS_IMMUTABLE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_NAME_TOO_LONG;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_SYMBOL_TOO_LONG;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_WAS_DELETED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TRANSACTION_REQUIRES_ZERO_TOKEN_BALANCES;
import static com.hederahashgraph.api.proto.java.TokenType.NON_FUNGIBLE_UNIQUE;

public class TokenUpdateSpecs extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(TokenUpdateSpecs.class);
	private static final int MAX_NAME_LENGTH = 100;
	private static final int MAX_SYMBOL_LENGTH = 100;

	private static String TOKEN_TREASURY = "treasury";
	private static final long defaultMaxLifetime =
			Long.parseLong(HapiSpecSetup.getDefaultNodeProps().get("entities.maxLifetime"));

	public static void main(String... args) {
		new TokenUpdateSpecs().runSuiteAsync();
	}

	@Override
	public boolean canRunConcurrent() {
		return true;
	}

	@Override
	public List<HapiApiSpec> getSpecsInSuite() {
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
						updateNftTreasuryHappyPath(),
						updateTokenTreasuryRequiresZeroTokenBalance(),
						validatesMissingAdminKey(),
						validatesMissingRef(),
						validatesNewExpiry(),
						/* HIP-18 */
						customFeesOnlyUpdatableWithKey(),
						updateUniqueTreasuryWithNfts(),
						updateHappyPath(),
						safeToUpdateCustomFeesWithNewFallbackWhileTransferring(),

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
		return defaultHapiSpec("ValidatesMissingRef")
				.given(
						cryptoCreate("payer")
				).when().then(
						tokenUpdate("0.0.0")
								.fee(ONE_HBAR)
								.payingWith("payer")
								.signedBy("payer")
								.hasKnownStatus(INVALID_TOKEN_ID),
						tokenUpdate("1.2.3")
								.fee(ONE_HBAR)
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
						newKeyNamed("oldFeeScheduleKey"),
						newKeyNamed("newFeeScheduleKey"),
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
								.feeScheduleKey("oldFeeScheduleKey")
				).when(
						getTokenInfo("tbu").logged(),
						tokenUpdate("tbu")
								.adminKey("newAdminKey")
								.kycKey("freezeThenKycKey")
								.freezeKey("kycThenFreezeKey")
								.wipeKey("supplyThenWipeKey")
								.supplyKey("wipeThenSupplyKey")
								.feeScheduleKey("newFeeScheduleKey"),
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
		final var firstPeriod = THREE_MONTHS_IN_SECONDS;
		final var secondPeriod = THREE_MONTHS_IN_SECONDS + 1234;
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
		final var civilian = "civilian";
		return defaultHapiSpec("UpdateHappyPath")
				.given(
						cryptoCreate(civilian).balance(ONE_HUNDRED_HBARS),
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
						newKeyNamed("pauseKey"),
						newKeyNamed("newPauseKey"),
						tokenCreate("primary")
								.name(saltedName)
								.entityMemo(originalMemo)
								.treasury(TOKEN_TREASURY)
								.autoRenewAccount("autoRenewAccount")
								.autoRenewPeriod(THREE_MONTHS_IN_SECONDS)
								.initialSupply(500)
								.decimals(1)
								.adminKey("adminKey")
								.freezeKey("freezeKey")
								.kycKey("kycKey")
								.supplyKey("supplyKey")
								.wipeKey("wipeKey")
								.pauseKey("pauseKey")
								.payingWith(civilian)
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
								.autoRenewPeriod(THREE_MONTHS_IN_SECONDS + 1)
								.freezeKey("newFreezeKey")
								.kycKey("newKycKey")
								.supplyKey("newSupplyKey")
								.wipeKey("newWipeKey")
								.pauseKey("newPauseKey")
								.payingWith(civilian)
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
								.hasPauseKey("primary")
								.hasTotalSupply(500)
								.hasAutoRenewAccount("newAutoRenewAccount")
								.hasPauseStatus(TokenPauseStatus.Unpaused)
								.hasAutoRenewPeriod(THREE_MONTHS_IN_SECONDS + 1)
				);
	}

	public HapiApiSpec updateTokenTreasuryRequiresZeroTokenBalance() {
		return defaultHapiSpec("updateTokenTreasuryRequiresZeroTokenBalance")
				.given(
						cryptoCreate("oldTreasury"),
						cryptoCreate("newTreasury"),
						newKeyNamed("adminKey"),
						newKeyNamed("supplyKey"),
						tokenCreate("non-fungible")
								.tokenType(NON_FUNGIBLE_UNIQUE)
								.initialSupply(0)
								.adminKey("adminKey")
								.supplyKey("supplyKey")
								.treasury("oldTreasury")
				)
				.when(
						mintToken("non-fungible",
								List.of(ByteString.copyFromUtf8("memo"), ByteString.copyFromUtf8("memo1"))),
						tokenAssociate("newTreasury", "non-fungible"),
						cryptoTransfer(
								movingUnique("non-fungible", 1).between("oldTreasury", "newTreasury")
						)
				)
				.then(
						tokenUpdate("non-fungible")
								.treasury("newTreasury").hasKnownStatus(TRANSACTION_REQUIRES_ZERO_TOKEN_BALANCES)
				);
	}

	public HapiApiSpec updateNftTreasuryHappyPath() {
		return defaultHapiSpec("UpdateNftTreasuryHappyPath")
				.given(
						cryptoCreate(TOKEN_TREASURY),
						cryptoCreate("newTokenTreasury"),
						newKeyNamed("adminKeyA"),
						newKeyNamed("supplyKeyA"),
						newKeyNamed("pauseKeyA"),
						tokenCreate("primary")
								.tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
								.treasury(TOKEN_TREASURY)
								.initialSupply(0)
								.adminKey("adminKeyA")
								.supplyKey("supplyKeyA")
								.pauseKey("pauseKeyA"),
						mintToken("primary", List.of(ByteString.copyFromUtf8("memo1")))
				)
				.when(
						tokenAssociate("newTokenTreasury", "primary"),
						tokenUpdate("primary")
								.treasury("newTokenTreasury").via("tokenUpdateTxn")
				)
				.then(
						getAccountBalance(TOKEN_TREASURY)
								.hasTokenBalance("primary", 0),
						getAccountBalance("newTokenTreasury")
								.hasTokenBalance("primary", 1),
						getTokenInfo("primary")
								.hasTreasury("newTokenTreasury")
								.hasPauseKey("primary")
								.hasPauseStatus(TokenPauseStatus.Unpaused)
								.logged(),
						getTokenNftInfo("primary", 1)
								.hasAccountID("newTokenTreasury")
								.logged()
				);
	}

	private HapiApiSpec safeToUpdateCustomFeesWithNewFallbackWhileTransferring() {
		final var uniqueTokenFeeKey = "uniqueTokenFeeKey";
		final var hbarCollector = "hbarFee";
		final var beneficiary = "luckyOne";
		final var multiKey = "allSeasons";
		final var sender = "sender";
		final var numRaces = 3;

		return defaultHapiSpec("SafeToUpdateCustomFeesWithNewFallbackWhileTransferring")
				.given(
						newKeyNamed(multiKey),
						cryptoCreate(hbarCollector),
						cryptoCreate(TOKEN_TREASURY),
						cryptoCreate(sender).maxAutomaticTokenAssociations(100),
						cryptoCreate(beneficiary).maxAutomaticTokenAssociations(100)
				).when().then(
						withOpContext((spec, opLog) -> {
							for (int i = 0; i < 3; i++) {
								final var name = uniqueTokenFeeKey + i;
								final var creation = tokenCreate(name)
										.tokenType(NON_FUNGIBLE_UNIQUE)
										.treasury(TOKEN_TREASURY)
										.supplyKey(multiKey)
										.feeScheduleKey(multiKey)
										.initialSupply(0);
								final var mint = mintToken(name, List.of(ByteString.copyFromUtf8("SOLO")));
								final var normalXfer = cryptoTransfer(movingUnique(name, 1L)
										.between(TOKEN_TREASURY, sender)
								)
										.fee(ONE_HBAR);
								final var update = tokenFeeScheduleUpdate(name)
										.withCustom(royaltyFeeWithFallback(
												1, 10,
												fixedHbarFeeInheritingRoyaltyCollector(1),
												hbarCollector))
										.deferStatusResolution();
								final var raceXfer = cryptoTransfer(movingUnique(name, 1L)
										.between(sender, beneficiary)
								)
										.signedBy(DEFAULT_PAYER, sender)
										.fee(ONE_HBAR)
										/* The beneficiary needs to sign now b/c of the fallback fee (and the
										 * lack of any fungible value going back to the treasury for this NFT). */
										.hasKnownStatus(INVALID_SIGNATURE);
								allRunFor(spec, creation, mint, normalXfer, update, raceXfer);
							}
						})
				);
	}

	private HapiApiSpec customFeesOnlyUpdatableWithKey() {
		final var origHbarFee = 1_234L;
		final var newHbarFee = 4_321L;

		final var tokenNoFeeKey = "justSchedule";
		final var uniqueTokenFeeKey = "uniqueTokenFeeKey";
		final var tokenWithFeeKey = "bothScheduleAndKey";
		final var hbarCollector = "hbarFee";

		final var adminKey = "admin";
		final var feeScheduleKey = "feeSchedule";
		final var newFeeScheduleKey = "feeScheduleRedux";

		return defaultHapiSpec("CustomFeesOnlyUpdatableWithKey")
				.given(
						newKeyNamed(adminKey),
						newKeyNamed(feeScheduleKey),
						newKeyNamed(newFeeScheduleKey),
						cryptoCreate(hbarCollector),
						tokenCreate(tokenNoFeeKey)
								.adminKey(adminKey)
								.withCustom(fixedHbarFee(origHbarFee, hbarCollector)),
						tokenCreate(uniqueTokenFeeKey)
								.tokenType(NON_FUNGIBLE_UNIQUE)
								.supplyKey(adminKey)
								.feeScheduleKey(feeScheduleKey)
								.initialSupply(0)
								.adminKey(adminKey)
								.withCustom(fixedHbarFee(origHbarFee, hbarCollector)),
						tokenCreate(tokenWithFeeKey)
								.adminKey(adminKey)
								.feeScheduleKey(feeScheduleKey)
								.withCustom(fixedHbarFee(origHbarFee, hbarCollector))
				).when(
						tokenUpdate(tokenNoFeeKey)
								.feeScheduleKey(newFeeScheduleKey)
								.hasKnownStatus(TOKEN_HAS_NO_FEE_SCHEDULE_KEY),
						tokenUpdate(tokenWithFeeKey)
								.usingInvalidFeeScheduleKey()
								.feeScheduleKey(newFeeScheduleKey)
								.hasPrecheck(INVALID_CUSTOM_FEE_SCHEDULE_KEY),
						tokenUpdate(tokenWithFeeKey)
								.feeScheduleKey(newFeeScheduleKey),
						tokenFeeScheduleUpdate(tokenWithFeeKey)
								.withCustom(fixedHbarFee(newHbarFee, hbarCollector)),
						tokenFeeScheduleUpdate(uniqueTokenFeeKey)
								.withCustom(royaltyFeeWithFallback(
										1, 3,
										fixedHbarFeeInheritingRoyaltyCollector(1_000),
										hbarCollector))
				).then(
						getTokenInfo(tokenWithFeeKey)
								.hasCustom(fixedHbarFeeInSchedule(newHbarFee, hbarCollector))
								.hasFeeScheduleKey(tokenWithFeeKey)
				);
	}

	public HapiApiSpec updateUniqueTreasuryWithNfts() {
		final var specialKey = "special";

		return defaultHapiSpec("UpdateUniqueTreasuryWithNfts")
				.given(
						newKeyNamed(specialKey),
						cryptoCreate("oldTreasury").balance(0L),
						cryptoCreate("newTreasury").balance(0L),
						tokenCreate("tbu")
								.tokenType(NON_FUNGIBLE_UNIQUE)
								.initialSupply(0L)
								.adminKey(specialKey)
								.supplyKey(specialKey)
								.treasury("oldTreasury")
				).when(
						mintToken("tbu", List.of(ByteString.copyFromUtf8("BLAMMO"))),
						getAccountInfo("oldTreasury").logged(),
						getAccountInfo("newTreasury").logged(),
						tokenAssociate("newTreasury", "tbu"),
						tokenUpdate("tbu")
								.memo("newMemo"),
						tokenUpdate("tbu")
								.treasury("newTreasury"),
						burnToken("tbu", List.of(1L)),
						getTokenInfo("tbu").hasTreasury("newTreasury"),
						tokenUpdate("tbu")
								.treasury("newTreasury")
								.via("treasuryUpdateTxn")
				).then(
						getAccountInfo("oldTreasury").logged(),
						getAccountInfo("newTreasury").logged(),
						getTokenInfo("tbu").hasTreasury("newTreasury")
				);
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}
