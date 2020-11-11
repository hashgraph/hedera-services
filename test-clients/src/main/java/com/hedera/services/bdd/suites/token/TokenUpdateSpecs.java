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
import com.hedera.services.bdd.suites.HapiApiSuite;
import com.hederahashgraph.api.proto.java.TokenFreezeStatus;
import com.hederahashgraph.api.proto.java.TokenKycStatus;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.Instant;
import java.util.List;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTokenInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.*;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.moving;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ADMIN_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_AUTORENEW_ACCOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_EXPIRATION_TIME;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_RENEWAL_PERIOD;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SIGNATURE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_SYMBOL;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TREASURY_ACCOUNT_FOR_TOKEN;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_IS_IMMUTABLE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_NAME_TOO_LONG;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_SYMBOL_TOO_LONG;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_WAS_DELETED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TRANSACTION_REQUIRES_ZERO_TOKEN_BALANCES;

public class TokenUpdateSpecs extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(TokenUpdateSpecs.class);
	private static final int MAX_NAME_LENGTH = 100;
	private static final int MAX_SYMBOL_LENGTH = 100;
	private static final long A_HUNDRED_SECONDS = 100;

	private static String TOKEN_TREASURY = "treasury";

	public static void main(String... args) {
		new TokenUpdateSpecs().runSuiteSync();
	}

	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		return List.of(new HapiApiSpec[] {
						symbolChanges(),
						standardImmutabilitySemanticsHold(),
						validAutoRenewWorks(),
						validatesMissingAdminKey(),
						tooLongNameCheckHolds(),
						tooLongSymbolCheckHolds(),
						numericSymbolCheckHolds(),
						nameChanges(),
						keysChange(),
						validatesAlreadyDeletedToken(),
						validatesMissingRef(),
						treasuryEvolves(),
						deletedAutoRenewAccountCheckHolds(),
						renewalPeriodCheckHolds(),
						invalidTreasuryCheckHolds(),
						updateHappyPath(),
						newTreasuryMustSign(),
						newTreasuryMustBeAssociated(),
						tokensCanBeMadeImmutableWithEmptyKeyList(),
				}
		);
	}

	private HapiApiSpec validatesAlreadyDeletedToken() {
		return defaultHapiSpec("ValidatesAlreadyDeletedToken")
				.given(
						newKeyNamed("adminKey"),
						cryptoCreate(TOKEN_TREASURY),
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
						cryptoCreate("neverToBe"),
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
						cryptoCreate("payer").balance(A_HUNDRED_HBARS)
				).when().then(
						tokenUpdate("1.2.3")
								.payingWith("payer")
								.signedBy("payer")
								.hasKnownStatus(INVALID_TOKEN_ID)
				);
	}

	private HapiApiSpec validatesMissingAdminKey() {
		return defaultHapiSpec("ValidatesMissingAdminKey")
				.given(
						cryptoCreate(TOKEN_TREASURY),
						cryptoCreate("payer")
								.balance(A_HUNDRED_HBARS),
						tokenCreate("tbd")
								.freezeDefault(false)
								.treasury(TOKEN_TREASURY)
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
						cryptoCreate("misc"),
						cryptoCreate(TOKEN_TREASURY),
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
						cryptoCreate("oldTreasury"),
						tokenCreate("tbu")
								.adminKey("adminKey")
								.treasury("oldTreasury")
				).when(
						cryptoCreate("newTreasury")
				).then(
						tokenUpdate("tbu")
								.treasury("newTreasury").hasKnownStatus(INVALID_TREASURY_ACCOUNT_FOR_TOKEN)
				);
	}

	public HapiApiSpec newTreasuryMustSign() {
		return defaultHapiSpec("NewTreasuryMustSign")
				.given(
						newKeyNamed("adminKey"),
						cryptoCreate("oldTreasury"),
						cryptoCreate("newTreasury"),
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
						cryptoCreate("oldTreasury"),
						cryptoCreate("newTreasury"),
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
						cryptoCreate("autoRenew"),
						cryptoCreate("newAutoRenew"),
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
						cryptoCreate(TOKEN_TREASURY)
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
						cryptoCreate(TOKEN_TREASURY)
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
						cryptoCreate(TOKEN_TREASURY)
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
						cryptoCreate(TOKEN_TREASURY)
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

	public HapiApiSpec numericSymbolCheckHolds() {
		var numericSymbol = "1234a";

		return defaultHapiSpec("NumericSymbolCheckHolds")
				.given(
						newKeyNamed("adminKey"),
						cryptoCreate(TOKEN_TREASURY)
				).when(
						tokenCreate("tbu")
								.adminKey("adminKey")
								.treasury(TOKEN_TREASURY)
				).then(
						tokenUpdate("tbu")
								.symbol(numericSymbol)
								.hasPrecheck(INVALID_TOKEN_SYMBOL)
				);
	}

	public HapiApiSpec deletedAutoRenewAccountCheckHolds() {
		return defaultHapiSpec("DeletedAutoRenewAccountCheckHolds")
				.given(
						newKeyNamed("adminKey"),
						cryptoCreate("autoRenewAccount"),
						cryptoCreate(TOKEN_TREASURY)
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
						cryptoCreate(TOKEN_TREASURY),
						cryptoCreate("autoRenewAccount")
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
						cryptoCreate(TOKEN_TREASURY),
						cryptoCreate("invalidTreasury")
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
		String saltedName = salted("primary");
		String newSaltedName = salted("primary");
		return defaultHapiSpec("UpdateHappyPath")
				.given(
						cryptoCreate(TOKEN_TREASURY),
						cryptoCreate("newTokenTreasury"),
						cryptoCreate("autoRenewAccount"),
						cryptoCreate("newAutoRenewAccount"),
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
								.name(newSaltedName)
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
								.hasRegisteredId("primary")
								.hasName(newSaltedName)
								.hasTreasury("newTokenTreasury")
								.hasFreezeKey("newFreezeKey")
								.hasKycKey("newKycKey")
								.hasSupplyKey("newSupplyKey")
								.hasWipeKey("newWipeKey")
								.hasTotalSupply(500)
								.hasAutoRenewAccount("newAutoRenewAccount")
								.hasAutoRenewPeriod(101L)
				);
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}
