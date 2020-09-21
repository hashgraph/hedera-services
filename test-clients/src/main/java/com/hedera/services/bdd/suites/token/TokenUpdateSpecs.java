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

import java.time.Instant;
import java.util.List;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTokenInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.*;
import static com.hedera.services.bdd.spec.transactions.token.HapiTokenTransact.TokenMovement.moving;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_EXPIRATION_TIME;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SIGNATURE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_REF;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_IS_IMMUTABlE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_NAME_TOO_LONG;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_WAS_DELETED;

public class TokenUpdateSpecs extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(TokenUpdateSpecs.class);
	private static final int MAX_NAME_LENGTH = 100;

	private static String TOKEN_TREASURY = "treasury";

	public static void main(String... args) {
		new TokenUpdateSpecs().runSuiteSync();
	}

	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		return List.of(new HapiApiSpec[] {
						symbolChanges(),
						keysChange(),
						treasuryEvolves(),
						standardImmutabilitySemanticsHold(),
						validAutoRenewWorks(),
						validatesMissingAdminKey(),
						tooLongNameCheckHolds(),
						nameChanges(),
						validatesMissingRef(),
						validatesAlreadyDeletedToken(),
				}
		);
	}

	private HapiApiSpec validatesAlreadyDeletedToken() {
		return defaultHapiSpec("ValidatesAlreadyDeletedToken")
				.given(
						newKeyNamed("adminKey"),
						cryptoCreate(TOKEN_TREASURY),
						cryptoCreate("payer")
								.balance(A_HUNDRED_HBARS),
						tokenCreate("tbd")
								.adminKey("adminKey")
								.treasury(TOKEN_TREASURY),
						tokenDelete("tbd")
				).when().then(
						tokenUpdate("tbd")
								.hasKnownStatus(TOKEN_WAS_DELETED)
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
								.hasKnownStatus(TOKEN_IS_IMMUTABlE),
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
								.hasKnownStatus(INVALID_TOKEN_REF)
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
								.hasKnownStatus(TOKEN_IS_IMMUTABlE)
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
								.supplyKey("wipeThenSupplyKey")
				).then(
						getTokenInfo("tbu").logged(),
						grantTokenKyc("tbu", "misc")
								.signedBy(GENESIS, "freezeThenKycKey"),
						tokenUnfreeze("tbu", "misc")
								.signedBy(GENESIS, "kycThenFreezeKey"),
						getAccountInfo("misc").logged(),
						tokenTransact(moving(5, "tbu")
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
						tokenTransact(
								moving(1, "tbu").symbolicallyBetween(TOKEN_TREASURY, GENESIS))
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
		var tooLongName = "ORIGINAL" + TxnUtils.randomUppercase(MAX_NAME_LENGTH+1);

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
								.hasKnownStatus(TOKEN_NAME_TOO_LONG)
				);
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}
