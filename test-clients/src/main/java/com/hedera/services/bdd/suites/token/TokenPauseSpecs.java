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
import com.hedera.services.bdd.suites.HapiApiSuite;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenPauseStatus;
import com.hederahashgraph.api.proto.java.TokenSupplyType;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTokenInfo;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.burnToken;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.grantTokenKyc;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.mintToken;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.revokeTokenKyc;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenAssociate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenDelete;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenDissociate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenFeeScheduleUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenFreeze;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenPause;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenUnfreeze;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenUnpause;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.wipeTokenAccount;
import static com.hedera.services.bdd.spec.transactions.token.CustomFeeSpecs.fixedHbarFee;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.moving;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.movingUnique;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.suites.utils.MiscEETUtils.metadata;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_IS_PAUSED;
import static com.hederahashgraph.api.proto.java.TokenType.FUNGIBLE_COMMON;
import static com.hederahashgraph.api.proto.java.TokenType.NON_FUNGIBLE_UNIQUE;

public class TokenPauseSpecs extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(TokenPauseSpecs.class);

	private static String TOKEN_TREASURY = "treasury";

	public static void main(String... args) {
		new TokenPauseSpecs().runSuiteSync();
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}

	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		return List.of(new HapiApiSpec[] {
				cannotChangePauseStatusIfMissingPauseKey(),
				pausedFungibleTokenCannotBeUsed(),
				pausedNonFungibleUniqueCannotBeUsed(),
				unpauseWorks()
		});
	}

	private HapiApiSpec unpauseWorks() {
		String pauseKey = "pauseKey";
		String kycKey = "kycKey";
		String firstUser = "firstUser";
		String token = "primary";

		return defaultHapiSpec("UnpauseWorks")
				.given(
						cryptoCreate(TOKEN_TREASURY),
						cryptoCreate(firstUser),
						newKeyNamed(pauseKey),
						newKeyNamed(kycKey)
				)
				.when(
						tokenCreate(token)
								.tokenType(FUNGIBLE_COMMON)
								.supplyType(TokenSupplyType.FINITE)
								.maxSupply(1000)
								.initialSupply(500)
								.decimals(1)
								.pauseKey(pauseKey)
								.kycKey(kycKey)
								.treasury(TOKEN_TREASURY),
						tokenAssociate(firstUser, token),
						grantTokenKyc(token, firstUser),
						tokenPause(token)
				)
				.then(
						cryptoTransfer(moving(10, token)
								.between(TOKEN_TREASURY, firstUser))
								.hasKnownStatus(TOKEN_IS_PAUSED),
						tokenUnpause(token),
						cryptoTransfer(moving(10, token)
								.between(TOKEN_TREASURY, firstUser)),
						getAccountInfo(firstUser)
								.logged()
				);
	}

	private HapiApiSpec pausedNonFungibleUniqueCannotBeUsed() {
		String uniqueToken = "nonFungibleUnique";
		String pauseKey = "pauseKey";
		String supplyKey = "supplyKey";
		String adminKey = "adminKey";
		String kycKey = "kycKey";
		String firstUser = "firstUser";
		String secondUser = "secondUser";

		return defaultHapiSpec("PausedNonFungibleUniqueCannotBeUsed")
				.given(
						cryptoCreate(TOKEN_TREASURY),
						cryptoCreate(firstUser),
						cryptoCreate(secondUser),
						newKeyNamed(pauseKey),
						newKeyNamed(adminKey),
						newKeyNamed(kycKey),
						newKeyNamed(supplyKey)
				)
				.when(
						tokenCreate(uniqueToken)
								.tokenType(NON_FUNGIBLE_UNIQUE)
								.supplyType(TokenSupplyType.FINITE)
								.pauseKey(pauseKey)
								.supplyKey(supplyKey)
								.adminKey(adminKey)
								.kycKey(kycKey)
								.initialSupply(0)
								.maxSupply(100)
								.treasury(TOKEN_TREASURY),
						tokenAssociate(firstUser, uniqueToken),
						mintToken(uniqueToken,
								List.of(metadata("firstMinted"), metadata("SecondMinted"))),
						grantTokenKyc(uniqueToken, firstUser),
						cryptoTransfer(movingUnique(uniqueToken, 1L)
								.between(TOKEN_TREASURY, firstUser)),
						tokenPause(uniqueToken)
				)
				.then(
						getTokenInfo(uniqueToken)
								.logged()
								.hasPauseKey(uniqueToken)
								.hasPauseStatus(TokenPauseStatus.Paused),
						cryptoTransfer(movingUnique(uniqueToken, 2L)
								.between(TOKEN_TREASURY, firstUser))
								.hasKnownStatus(TOKEN_IS_PAUSED),
						tokenAssociate(secondUser, uniqueToken)
								.hasKnownStatus(TOKEN_IS_PAUSED),
						mintToken(uniqueToken, List.of(metadata("thirdMinted")))
								.hasKnownStatus(TOKEN_IS_PAUSED),
						burnToken(uniqueToken, List.of(2L))
								.hasKnownStatus(TOKEN_IS_PAUSED)
				);
	}

	private HapiApiSpec pausedFungibleTokenCannotBeUsed() {
		String pauseKey = "pauseKey";
		String supplyKey = "supplyKey";
		String freezeKey = "freezeKey";
		String adminKey = "adminKey";
		String kycKey = "kycKey";
		String wipeKey = "wipeKey";
		String feeScheduleKey = "feeScheduleKey";
		String token = "primary";
		String firstUser = "firstUser";
		String secondUser = "secondUser";
		return defaultHapiSpec("pausedFungibleTokenCannotBeUsed")
				.given(
						cryptoCreate(TOKEN_TREASURY),
						cryptoCreate(firstUser),
						cryptoCreate(secondUser),
						newKeyNamed(pauseKey),
						newKeyNamed(adminKey),
						newKeyNamed(freezeKey),
						newKeyNamed(kycKey),
						newKeyNamed(feeScheduleKey),
						newKeyNamed(supplyKey),
						newKeyNamed(wipeKey)
				)
				.when(
						tokenCreate(token)
								.tokenType(FUNGIBLE_COMMON)
								.supplyType(TokenSupplyType.FINITE)
								.maxSupply(1000)
								.initialSupply(500)
								.decimals(1)
								.treasury(TOKEN_TREASURY)
								.adminKey(adminKey)
								.pauseKey(pauseKey)
								.freezeKey(freezeKey)
								.kycKey(kycKey)
								.wipeKey(wipeKey)
								.supplyKey(supplyKey)
								.feeScheduleKey(feeScheduleKey),
						tokenAssociate(firstUser, token),
						grantTokenKyc(token, firstUser),
						cryptoTransfer(moving(100, token)
								.between(TOKEN_TREASURY, firstUser)),
						tokenPause(token)
				)
				.then(
						getTokenInfo(token)
								.logged()
								.hasPauseKey(token)
								.hasPauseStatus(TokenPauseStatus.Paused),
						tokenAssociate(secondUser, token)
								.hasKnownStatus(TOKEN_IS_PAUSED),
						cryptoTransfer(moving(10, token)
								.between(TOKEN_TREASURY, firstUser))
								.hasKnownStatus(TOKEN_IS_PAUSED),
						tokenDissociate(firstUser, token)
								.hasKnownStatus(TOKEN_IS_PAUSED),
						mintToken(token, 1)
								.hasKnownStatus(TOKEN_IS_PAUSED),
						burnToken(token, 1)
								.hasKnownStatus(TOKEN_IS_PAUSED),
						tokenFreeze(token, firstUser)
								.hasKnownStatus(TOKEN_IS_PAUSED),
						tokenUnfreeze(token, firstUser)
								.hasKnownStatus(TOKEN_IS_PAUSED),
						revokeTokenKyc(token, firstUser)
								.hasKnownStatus(TOKEN_IS_PAUSED),
						grantTokenKyc(token, firstUser)
								.hasKnownStatus(TOKEN_IS_PAUSED),
						tokenFeeScheduleUpdate(token)
								.withCustom(fixedHbarFee(100, TOKEN_TREASURY))
								.hasKnownStatus(TOKEN_IS_PAUSED),
						wipeTokenAccount(token, firstUser, 10)
								.hasKnownStatus(TOKEN_IS_PAUSED),
						tokenUpdate(token)
								.name("newName")
								.hasKnownStatus(TOKEN_IS_PAUSED),
						tokenDelete(token)
								.hasKnownStatus(TOKEN_IS_PAUSED)
				);
	}

	private HapiApiSpec cannotChangePauseStatusIfMissingPauseKey() {
		return defaultHapiSpec("CannotChangePauseStatusIfMissingPauseKey")
				.given(
						cryptoCreate(TOKEN_TREASURY)
				)
				.when(
						tokenCreate("primary")
								.tokenType(FUNGIBLE_COMMON)
								.supplyType(TokenSupplyType.FINITE)
								.maxSupply(1000)
								.initialSupply(500)
								.decimals(1)
								.treasury(TOKEN_TREASURY),
						tokenCreate("non-fungible-unique-primary")
								.tokenType(NON_FUNGIBLE_UNIQUE)
								.supplyType(TokenSupplyType.FINITE)
								.initialSupply(0)
								.maxSupply(100)
								.treasury(TOKEN_TREASURY)
				)
				.then(
						tokenPause("primary")
								.signedBy(GENESIS)
								.hasKnownStatus(ResponseCodeEnum.TOKEN_HAS_NO_PAUSE_KEY),
						tokenUnpause("primary")
								.signedBy(GENESIS)
								.hasKnownStatus(ResponseCodeEnum.TOKEN_HAS_NO_PAUSE_KEY),
						tokenPause("non-fungible-unique-primary")
								.signedBy(GENESIS)
								.hasKnownStatus(ResponseCodeEnum.TOKEN_HAS_NO_PAUSE_KEY),
						tokenUnpause("non-fungible-unique-primary")
								.signedBy(GENESIS)
								.hasKnownStatus(ResponseCodeEnum.TOKEN_HAS_NO_PAUSE_KEY)
				);
	}
}
