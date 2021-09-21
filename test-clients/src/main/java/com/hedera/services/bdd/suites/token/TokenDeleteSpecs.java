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
import com.hedera.services.bdd.suites.HapiApiSuite;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.Instant;
import java.util.List;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTokenInfo;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.burnToken;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoDelete;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.grantTokenKyc;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.mintToken;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.revokeTokenKyc;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenAssociate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenDelete;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenDissociate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenFreeze;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenUnfreeze;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenUpdate;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.moving;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.validateChargedUsdWithin;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_IS_TREASURY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_EXPIRATION_TIME;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_IS_IMMUTABLE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_WAS_DELETED;
import static com.hederahashgraph.api.proto.java.TokenType.NON_FUNGIBLE_UNIQUE;

public class TokenDeleteSpecs extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(TokenDeleteSpecs.class);

	public static void main(String... args) {
		new TokenDeleteSpecs().runSuiteAsync();
	}

	@Override
	public boolean canRunAsync() {
		return true;
	}

	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		return List.of(new HapiApiSpec[] {
				deletionValidatesMissingAdminKey(),
				deletionWorksAsExpected(),
				deletionValidatesAlreadyDeletedToken(),
				treasuryBecomesDeletableAfterTokenDelete(),
				deletionValidatesRef(),

				baseTokenDeleteIsChargedExpectedFee(),
				baseTokeUpdateIsChargedExpectedFee()
				}
		);
	}

	private HapiApiSpec treasuryBecomesDeletableAfterTokenDelete() {
		return defaultHapiSpec("TreasuryBecomesDeletableAfterTokenDelete")
				.given(
						newKeyNamed("tokenAdmin"),
						cryptoCreate(TOKEN_TREASURY).balance(0L),
						tokenCreate("firstTbd")
								.adminKey("tokenAdmin")
								.treasury(TOKEN_TREASURY),
						tokenCreate("secondTbd")
								.adminKey("tokenAdmin")
								.treasury(TOKEN_TREASURY),
						cryptoDelete(TOKEN_TREASURY)
								.hasKnownStatus(ACCOUNT_IS_TREASURY),
						tokenDissociate(TOKEN_TREASURY, "firstTbd")
								.hasKnownStatus(ACCOUNT_IS_TREASURY)
				).when(
						tokenDelete("firstTbd"),
						tokenDissociate(TOKEN_TREASURY, "firstTbd"),
						cryptoDelete(TOKEN_TREASURY)
								.hasKnownStatus(ACCOUNT_IS_TREASURY),
						tokenDelete("secondTbd")
				).then(
						cryptoDelete(TOKEN_TREASURY)
				);
	}

	private HapiApiSpec deletionValidatesAlreadyDeletedToken() {
		return defaultHapiSpec("DeletionValidatesAlreadyDeletedToken")
				.given(
						newKeyNamed("multiKey"),
						cryptoCreate(TOKEN_TREASURY).balance(0L),
						tokenCreate("tbd")
								.adminKey("multiKey")
								.treasury(TOKEN_TREASURY),
						tokenDelete("tbd")
				).when().then(
						tokenDelete("tbd")
								.hasKnownStatus(TOKEN_WAS_DELETED)
				);
	}

	private HapiApiSpec deletionValidatesMissingAdminKey() {
		return defaultHapiSpec("DeletionValidatesMissingAdminKey")
				.given(
						newKeyNamed("multiKey"),
						cryptoCreate(TOKEN_TREASURY).balance(0L),
						cryptoCreate("payer"),
						tokenCreate("tbd")
								.freezeDefault(false)
								.treasury(TOKEN_TREASURY)
								.payingWith("payer")
				).when().then(
						tokenDelete("tbd")
								.payingWith("payer")
								.signedBy("payer")
								.hasKnownStatus(TOKEN_IS_IMMUTABLE)
				);
	}

	public HapiApiSpec deletionWorksAsExpected() {
		return defaultHapiSpec("DeletionWorksAsExpected")
				.given(
						newKeyNamed("multiKey"),
						cryptoCreate(TOKEN_TREASURY).balance(0L),
						cryptoCreate("payer"),
						tokenCreate("tbd")
								.adminKey("multiKey")
								.freezeKey("multiKey")
								.kycKey("multiKey")
								.wipeKey("multiKey")
								.supplyKey("multiKey")
								.freezeDefault(false)
								.treasury(TOKEN_TREASURY)
								.payingWith("payer"),
						tokenAssociate(GENESIS, "tbd")
				).when(
						getAccountInfo(TOKEN_TREASURY).logged(),
						mintToken("tbd", 1),
						burnToken("tbd", 1),
						revokeTokenKyc("tbd", GENESIS),
						grantTokenKyc("tbd", GENESIS),
						tokenFreeze("tbd", GENESIS),
						tokenUnfreeze("tbd", GENESIS),
						cryptoTransfer(moving(1, "tbd")
								.between(TOKEN_TREASURY, GENESIS)),
						tokenDelete("tbd").payingWith("payer")
				).then(
						getTokenInfo("tbd").logged(),
						getAccountInfo(TOKEN_TREASURY).logged(),
						cryptoTransfer(moving(1, "tbd")
								.between(TOKEN_TREASURY, GENESIS))
								.hasKnownStatus(TOKEN_WAS_DELETED),
						mintToken("tbd", 1)
								.hasKnownStatus(TOKEN_WAS_DELETED),
						burnToken("tbd", 1)
								.hasKnownStatus(TOKEN_WAS_DELETED),
						revokeTokenKyc("tbd", GENESIS)
								.hasKnownStatus(TOKEN_WAS_DELETED),
						grantTokenKyc("tbd", GENESIS)
								.hasKnownStatus(TOKEN_WAS_DELETED),
						tokenFreeze("tbd", GENESIS)
								.hasKnownStatus(TOKEN_WAS_DELETED),
						tokenUnfreeze("tbd", GENESIS)
								.hasKnownStatus(TOKEN_WAS_DELETED)
				);
	}

	public HapiApiSpec deletionValidatesRef() {
		return defaultHapiSpec("DeletionValidatesRef")
				.given(
						cryptoCreate("payer")
				).when().then(
						tokenDelete("0.0.0")
								.payingWith("payer")
								.signedBy("payer")
								.hasKnownStatus(INVALID_TOKEN_ID),
						tokenDelete("1.2.3")
								.payingWith("payer")
								.signedBy("payer")
								.hasKnownStatus(INVALID_TOKEN_ID)
				);
	}


	private HapiApiSpec baseTokenDeleteIsChargedExpectedFee() {
		final var uniqueToken = "nftToken";
		final var supplyKey = "supplyKey";
		final var adminKey = "adminKey";
		final var civilianPayer = "civilian";
		final var baseTxn = "baseTxn";
		final var expectedNftDeletePriceUsd = 0.001;

		return defaultHapiSpec("baseTokenDeleteIsChargedExpectedFee")
				.given(
						newKeyNamed(supplyKey),
						newKeyNamed(adminKey),
						cryptoCreate(civilianPayer).key(adminKey).balance(ONE_HUNDRED_HBARS),
						cryptoCreate(TOKEN_TREASURY),
						tokenCreate(uniqueToken)
								.initialSupply(0)
								.supplyKey(supplyKey)
								.adminKey(adminKey)
								.tokenType(NON_FUNGIBLE_UNIQUE)
								.treasury(civilianPayer)
				)
				.when(

						tokenDelete(uniqueToken)
								.payingWith(civilianPayer)
								.signedBy(civilianPayer)
								.blankMemo()
								.via(baseTxn)
				).then(
						validateChargedUsdWithin(baseTxn, expectedNftDeletePriceUsd, 0.01)
				);
	}

	private HapiApiSpec baseTokeUpdateIsChargedExpectedFee() {
		final var updateTxn = "baseUpdateTxn";
		final var tokenToUpdate = "tokenToUpdate";
		final var supplyKey = "burnsupplyKey";
		final var baseExpiry = Instant.now().getEpochSecond() + THREE_MONTHS_IN_SECONDS;
		final var expectedTokenUpdatePriceUsd = 0.001;
		return defaultHapiSpec("baseTokeUpdateIsChargedExpectedFee")
				.given(
						newKeyNamed(supplyKey),
						newKeyNamed("adminKey"),
						cryptoCreate(TOKEN_TREASURY).key("adminKey"),
						tokenCreate(tokenToUpdate)
								.initialSupply(1000)
								.supplyKey(supplyKey)
								.adminKey("adminKey")
								.payingWith(TOKEN_TREASURY)
								.blankMemo()
								.treasury(TOKEN_TREASURY)
				).when(
						tokenUpdate(tokenToUpdate)
						.expiry(baseExpiry)
								.signedBy(TOKEN_TREASURY)
								.payingWith(TOKEN_TREASURY)
								.via(updateTxn)
				).then(
						validateChargedUsdWithin(updateTxn, expectedTokenUpdatePriceUsd, 0.01)
				);
	}


	private ByteString metadata(String contents) {
		return ByteString.copyFromUtf8(contents);
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}
