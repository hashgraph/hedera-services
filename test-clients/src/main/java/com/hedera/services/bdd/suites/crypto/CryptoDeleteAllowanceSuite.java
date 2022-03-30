package com.hedera.services.bdd.suites.crypto;

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
import com.hederahashgraph.api.proto.java.TokenSupplyType;
import com.hederahashgraph.api.proto.java.TokenType;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.Map;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.assertions.AccountInfoAsserts.accountWith;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTokenNftInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoAdjustAllowance;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoApproveAllowance;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoDelete;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoDeleteAllowance;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.grantTokenKyc;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.mintToken;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.revokeTokenKyc;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenAssociate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenFreeze;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenPause;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenUnpause;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoApproveAllowance.MISSING_OWNER;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.moving;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.movingUnique;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.validateChargedUsdWithin;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_DELETED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.EMPTY_ALLOWANCES;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FUNGIBLE_TOKEN_IN_NFT_ALLOWANCES;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ALLOWANCE_OWNER_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_NFT_SERIAL_NUMBER;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.MAX_ALLOWANCES_EXCEEDED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NFT_IN_FUNGIBLE_TOKEN_ALLOWANCES;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.REPEATED_ALLOWANCES_TO_DELETE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.REPEATED_SERIAL_NUMS_IN_NFT_ALLOWANCES;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SENDER_DOES_NOT_OWN_NFT_SERIAL_NO;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_NOT_ASSOCIATED_TO_ACCOUNT;
import static com.hederahashgraph.api.proto.java.TokenType.NON_FUNGIBLE_UNIQUE;

public class CryptoDeleteAllowanceSuite extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(CryptoDeleteAllowanceSuite.class);

	public static void main(String... args) {
		new CryptoDeleteAllowanceSuite().runSuiteSync();
	}

	@Override
	public List<HapiApiSpec> getSpecsInSuite() {
		return List.of(new HapiApiSpec[] {
				happyPathWorks(),
				approvedForAllNotAffectedOnDelete(),
				noOwnerDefaultsToPayerInDeleteAllowance(),
				invalidOwnerFails(),
				canDeleteMultipleOwners(),
				emptyAllowancesDeleteRejected(),
				repeatedAllowancesFail(),
				tokenNotAssociatedToAccountFailsOnDeleteAllowance(),
				invalidTokenTypeFailsInDeleteAllowance(),
				validatesSerialNums(),
				exceedsTransactionLimit(),
				succeedsWhenTokenPausedFrozenKycRevoked(),
				feesAsExpected()
		});
	}

	private HapiApiSpec repeatedAllowancesFail() {
		final String owner = "owner";
		final String spender = "spender";
		final String token = "token";
		final String nft = "nft";
		return defaultHapiSpec("happyPathWorks")
				.given(
						newKeyNamed("supplyKey"),
						cryptoCreate(owner)
								.balance(ONE_HUNDRED_HBARS)
								.maxAutomaticTokenAssociations(10),
						cryptoCreate(spender)
								.balance(ONE_HUNDRED_HBARS),
						cryptoCreate(TOKEN_TREASURY).balance(100 * ONE_HUNDRED_HBARS)
								.maxAutomaticTokenAssociations(10),
						tokenCreate(token)
								.tokenType(TokenType.FUNGIBLE_COMMON)
								.supplyType(TokenSupplyType.FINITE)
								.supplyKey("supplyKey")
								.maxSupply(1000L)
								.initialSupply(10L)
								.treasury(TOKEN_TREASURY),
						tokenCreate(nft)
								.maxSupply(10L)
								.initialSupply(0)
								.supplyType(TokenSupplyType.FINITE)
								.tokenType(NON_FUNGIBLE_UNIQUE)
								.supplyKey("supplyKey")
								.treasury(TOKEN_TREASURY),
						tokenAssociate(owner, token),
						tokenAssociate(owner, nft),
						mintToken(nft, List.of(
								ByteString.copyFromUtf8("a"),
								ByteString.copyFromUtf8("b"),
								ByteString.copyFromUtf8("c")
						)).via("nftTokenMint"),
						mintToken(token, 500L).via("tokenMint"),
						cryptoTransfer(movingUnique(nft, 1L, 2L, 3L)
								.between(TOKEN_TREASURY, owner))
				)
				.when(
						cryptoApproveAllowance()
								.payingWith(owner)
								.addCryptoAllowance(owner, spender, 100L)
								.addTokenAllowance(owner, token, spender, 100L)
								.addNftAllowance(owner, nft, spender, false, List.of(1L, 2L, 3L))
								.via("otherAdjustTxn"),
						getAccountInfo(owner)
								.has(accountWith()
										.cryptoAllowancesCount(1)
										.nftApprovedForAllAllowancesCount(0)
										.tokenAllowancesCount(1)
										.cryptoAllowancesContaining(spender, 100L)
										.tokenAllowancesContaining(token, spender, 100L)
								),
						getTokenNftInfo(nft, 1L).hasSpenderID(spender)
				)
				.then(
						cryptoDeleteAllowance()
								.payingWith(owner)
								.addCryptoDeleteAllowance(MISSING_OWNER)
								.addCryptoDeleteAllowance(MISSING_OWNER)
								.blankMemo()
								.hasPrecheck(REPEATED_ALLOWANCES_TO_DELETE),
						cryptoDeleteAllowance()
								.payingWith(owner)
								.addCryptoDeleteAllowance(owner)
								.addCryptoDeleteAllowance(owner)
								.addTokenDeleteAllowance(owner, token)
								.addNftDeleteAllowance(owner, nft, List.of(1L))
								.hasPrecheck(REPEATED_ALLOWANCES_TO_DELETE),
						cryptoDeleteAllowance()
								.payingWith(owner)
								.addCryptoDeleteAllowance(owner)
								.addTokenDeleteAllowance(owner, token)
								.addTokenDeleteAllowance(owner, token)
								.addNftDeleteAllowance(owner, nft, List.of(1L))
								.hasPrecheck(REPEATED_ALLOWANCES_TO_DELETE),
						cryptoDeleteAllowance()
								.payingWith(owner)
								.addCryptoDeleteAllowance(owner)
								.addTokenDeleteAllowance(owner, token)
								.addNftDeleteAllowance(owner, nft, List.of(1L))
								.addNftDeleteAllowance(owner, nft, List.of(1L))
								.hasPrecheck(REPEATED_ALLOWANCES_TO_DELETE),
						cryptoDeleteAllowance()
								.payingWith(owner)
								.addCryptoDeleteAllowance(owner)
								.addTokenDeleteAllowance(owner, token)
								.addNftDeleteAllowance(owner, nft, List.of(1L))
								.addNftDeleteAllowance(owner, nft, List.of(2L)),
						getAccountInfo(owner).has(accountWith().noAllowances()),
						getTokenNftInfo(nft, 1L).hasNoSpender(),
						getTokenNftInfo(nft, 2L).hasNoSpender(),
						getTokenNftInfo(nft, 3L).hasSpenderID(spender));
	}

	private HapiApiSpec invalidOwnerFails() {
		final String owner = "owner";
		final String spender = "spender";
		final String token = "token";
		final String nft = "nft";
		return defaultHapiSpec("invalidOwnerFails")
				.given(
						newKeyNamed("supplyKey"),
						cryptoCreate(owner)
								.balance(ONE_HUNDRED_HBARS)
								.maxAutomaticTokenAssociations(10),
						cryptoCreate("payer")
								.balance(ONE_HUNDRED_HBARS),
						cryptoCreate(spender)
								.balance(ONE_HUNDRED_HBARS),
						cryptoCreate(TOKEN_TREASURY).balance(100 * ONE_HUNDRED_HBARS)
								.maxAutomaticTokenAssociations(10),
						tokenCreate(token)
								.tokenType(TokenType.FUNGIBLE_COMMON)
								.supplyType(TokenSupplyType.FINITE)
								.supplyKey("supplyKey")
								.maxSupply(1000L)
								.initialSupply(10L)
								.treasury(TOKEN_TREASURY),
						tokenCreate(nft)
								.maxSupply(10L)
								.initialSupply(0)
								.supplyType(TokenSupplyType.FINITE)
								.tokenType(NON_FUNGIBLE_UNIQUE)
								.supplyKey("supplyKey")
								.treasury(TOKEN_TREASURY),
						mintToken(nft, List.of(
								ByteString.copyFromUtf8("a"),
								ByteString.copyFromUtf8("b"),
								ByteString.copyFromUtf8("c")
						)).via("nftTokenMint"),
						mintToken(token, 500L).via("tokenMint")
				)
				.when(
						cryptoAdjustAllowance()
								.payingWith("payer")
								.addCryptoAllowance(owner, spender, 100L)
								.signedBy("payer", owner)
								.blankMemo(),
						cryptoDelete(owner),

						cryptoDeleteAllowance()
								.payingWith("payer")
								.addCryptoDeleteAllowance(owner)
								.signedBy("payer", owner)
								.blankMemo()
								.hasPrecheck(INVALID_ALLOWANCE_OWNER_ID),
						cryptoDeleteAllowance()
								.payingWith("payer")
								.addTokenDeleteAllowance(owner, token)
								.signedBy("payer", owner)
								.blankMemo()
								.hasPrecheck(INVALID_ALLOWANCE_OWNER_ID),
						cryptoDeleteAllowance()
								.payingWith("payer")
								.addNftDeleteAllowance(owner, nft, List.of(1L))
								.signedBy("payer", owner)
								.via("baseDeleteTxn")
								.blankMemo()
								.hasPrecheck(INVALID_ALLOWANCE_OWNER_ID)
				)
				.then(
						getAccountInfo(owner).hasCostAnswerPrecheck(ACCOUNT_DELETED));
	}


	private HapiApiSpec feesAsExpected() {
		final String owner = "owner";
		final String spender = "spender";
		final String token = "token";
		final String nft = "nft";
		final String payer = "payer";
		return defaultHapiSpec("feesAsExpected")
				.given(
						newKeyNamed("supplyKey"),
						cryptoCreate(owner)
								.balance(ONE_HUNDRED_HBARS)
								.maxAutomaticTokenAssociations(10),
						cryptoCreate(payer)
								.balance(ONE_HUNDRED_HBARS)
								.maxAutomaticTokenAssociations(10),
						cryptoCreate(spender)
								.balance(ONE_HUNDRED_HBARS),
						cryptoCreate("spender1")
								.balance(ONE_HUNDRED_HBARS),
						cryptoCreate("spender2")
								.balance(ONE_HUNDRED_HBARS),
						cryptoCreate(TOKEN_TREASURY).balance(100 * ONE_HUNDRED_HBARS)
								.maxAutomaticTokenAssociations(10),
						tokenCreate(token)
								.tokenType(TokenType.FUNGIBLE_COMMON)
								.supplyType(TokenSupplyType.FINITE)
								.supplyKey("supplyKey")
								.maxSupply(1000L)
								.initialSupply(10L)
								.treasury(TOKEN_TREASURY),
						tokenCreate(nft)
								.maxSupply(10L)
								.initialSupply(0)
								.supplyType(TokenSupplyType.FINITE)
								.tokenType(NON_FUNGIBLE_UNIQUE)
								.supplyKey("supplyKey")
								.treasury(TOKEN_TREASURY),
						tokenAssociate(owner, token),
						tokenAssociate(owner, nft),
						mintToken(nft, List.of(
								ByteString.copyFromUtf8("a"),
								ByteString.copyFromUtf8("b"),
								ByteString.copyFromUtf8("c")
						)).via("nftTokenMint"),
						mintToken(token, 500L).via("tokenMint"),
						cryptoTransfer(movingUnique(nft, 1L, 2L, 3L)
								.between(TOKEN_TREASURY, owner))
				)
				.when(
						cryptoAdjustAllowance()
								.payingWith(owner)
								.addCryptoAllowance(owner, "spender2", 100L)
								.addTokenAllowance(owner, token, "spender2", 100L)
								.addNftAllowance(owner, nft, "spender2", false, List.of(1L)),
						/*--- without specifying owner */
						cryptoDeleteAllowance()
								.payingWith(owner)
								.addCryptoDeleteAllowance(MISSING_OWNER)
								.blankMemo()
								.via("baseDelete"),
						validateChargedUsdWithin("baseDelete", 0.05, 0.01),
						cryptoDeleteAllowance()
								.payingWith(owner)
								.blankMemo()
								.addTokenDeleteAllowance(MISSING_OWNER, token)
								.via("baseDeleteToken"),
						validateChargedUsdWithin("baseDeleteToken", 0.0503082888, 0.01),
						cryptoDeleteAllowance()
								.payingWith(owner)
								.blankMemo()
								.addNftDeleteAllowance(MISSING_OWNER, nft, List.of(1L))
								.via("baseDeleteNft"),
						validateChargedUsdWithin("baseDeleteNft", 0.050411052000000005, 0.01)
				)
				.then(
						cryptoAdjustAllowance()
								.payingWith(owner)
								.addCryptoAllowance(owner, "spender2", 100L)
								.addTokenAllowance(owner, token, "spender2", 100L)
								.addNftAllowance(owner, nft, "spender2", false, List.of(1L)),
						/*--- with specifying owner */
						cryptoDeleteAllowance()
								.payingWith(owner)
								.addCryptoDeleteAllowance(owner)
								.blankMemo()
								.via("baseDelete"),
						validateChargedUsdWithin("baseDelete", 0.05, 0.01),
						cryptoDeleteAllowance()
								.payingWith(owner)
								.blankMemo()
								.addTokenDeleteAllowance(owner, token)
								.via("baseDeleteToken"),
						validateChargedUsdWithin("baseDeleteToken", 0.0503082888, 0.01),
						cryptoDeleteAllowance()
								.payingWith(owner)
								.blankMemo()
								.addNftDeleteAllowance(owner, nft, List.of(1L))
								.via("baseDeleteNft"),
						validateChargedUsdWithin("baseDeleteNft", 0.050411052000000005, 0.01),

						cryptoAdjustAllowance()
								.payingWith(owner)
								.blankMemo()
								.addCryptoAllowance(owner, "spender2", 100L)
								.addTokenAllowance(owner, token, "spender2", 100L)
								.addNftAllowance(owner, nft, "spender2", false, List.of(1L)),
						/*--- by specifying owner */
						cryptoDeleteAllowance()
								.payingWith(owner) // owner = 0.0499999968
								.addCryptoDeleteAllowance(owner)
								.signedBy(owner, payer)
								.blankMemo()
								.via("baseDelete1"),
						cryptoDeleteAllowance()
								.payingWith(payer) // owner = 0.0499999968
								.addCryptoDeleteAllowance(owner)
								.signedBy(owner, payer)
								.blankMemo()
								.via("baseDelete1"),
						validateChargedUsdWithin("baseDelete1", 0.08152215, 0.05),
						cryptoDeleteAllowance()
								.payingWith(payer)
								.blankMemo()
								.addTokenDeleteAllowance(owner, token)
								.signedBy(owner, payer)
								.via("baseDeleteToken1"),
						validateChargedUsdWithin("baseDeleteToken1", 0.0818, 0.01),
						cryptoDeleteAllowance()
								.payingWith(payer)
								.blankMemo()
								.addNftDeleteAllowance(owner, nft, List.of(1L))
								.signedBy(owner, payer)
								.via("baseDeleteNft1"),
						validateChargedUsdWithin("baseDeleteNft1", 0.0819, 0.01),

						/*-- combination with owner */
						cryptoAdjustAllowance()
								.payingWith(owner)
								.addCryptoAllowance(owner, "spender2", 100L)
								.addTokenAllowance(owner, token, "spender2", 100L)
								.addNftAllowance(owner, nft, "spender2", false, List.of(1L)),
						cryptoDeleteAllowance()
								.payingWith(payer)
								.addTokenDeleteAllowance(owner, token)
								.addNftDeleteAllowance(owner, nft, List.of(1L))
								.signedBy(owner, payer)
								.blankMemo()
								.via("baseDeleteTokenNft"),
						validateChargedUsdWithin("baseDeleteTokenNft", 0.0825240972, 0.01),

						cryptoAdjustAllowance()
								.payingWith(owner)
								.addCryptoAllowance(owner, "spender2", 100L)
								.addTokenAllowance(owner, token, "spender2", 100L)
								.addNftAllowance(owner, nft, "spender2", false, List.of(1L))
								.blankMemo(),
						cryptoDeleteAllowance()
								.payingWith(payer)
								.addCryptoDeleteAllowance(owner)
								.addTokenDeleteAllowance(owner, token)
								.signedBy(owner, payer)
								.blankMemo()
								.via("baseDeleteCryptoToken"),
						validateChargedUsdWithin("baseDeleteCryptoToken", 0.0821130, 0.01),

						cryptoAdjustAllowance()
								.payingWith(owner)
								.addCryptoAllowance(owner, "spender2", 100L)
								.addTokenAllowance(owner, token, "spender2", 100L)
								.addNftAllowance(owner, nft, "spender2", false, List.of(1L)),
						cryptoDeleteAllowance()
								.payingWith(payer)
								.addCryptoDeleteAllowance(owner)
								.addNftDeleteAllowance(owner, nft, List.of(1L))
								.signedBy(owner, payer)
								.blankMemo()
								.via("baseDeleteCryptoNft"),
						validateChargedUsdWithin("baseDeleteCryptoNft", 0.082216, 0.01),

						cryptoAdjustAllowance()
								.payingWith(owner)
								.addCryptoAllowance(owner, "spender2", 100L)
								.addTokenAllowance(owner, token, "spender2", 100L)
								.addNftAllowance(owner, nft, "spender2", false, List.of(1L)),
						cryptoDeleteAllowance()
								.payingWith(payer)
								.addCryptoDeleteAllowance(owner)
								.addTokenDeleteAllowance(owner, token)
								.addNftDeleteAllowance(owner, nft, List.of(1L))
								.signedBy(owner, payer)
								.blankMemo()
								.via("baseDeleteCryptoNftToken"),
						validateChargedUsdWithin("baseDeleteCryptoNftToken", 0.08283, 0.01)
				);
	}

	private HapiApiSpec succeedsWhenTokenPausedFrozenKycRevoked() {
		final String owner = "owner";
		final String spender = "spender";
		final String spender1 = "spender1";
		final String token = "token";
		final String nft = "nft";
		return defaultHapiSpec("succeedsWhenTokenPausedFrozenKycRevoked")
				.given(
						fileUpdate(APP_PROPERTIES)
								.fee(ONE_HUNDRED_HBARS)
								.payingWith(EXCHANGE_RATE_CONTROL)
								.overridingProps(Map.of(
										"hedera.allowances.maxTransactionLimit", "20",
										"hedera.allowances.maxAccountLimit", "100")
								),

						newKeyNamed("supplyKey"),
						newKeyNamed("adminKey"),
						newKeyNamed("freezeKey"),
						newKeyNamed("kycKey"),
						newKeyNamed("pauseKey"),
						cryptoCreate(owner)
								.balance(ONE_HUNDRED_HBARS)
								.maxAutomaticTokenAssociations(10),
						cryptoCreate(spender)
								.balance(ONE_HUNDRED_HBARS),
						cryptoCreate(spender1)
								.balance(ONE_HUNDRED_HBARS),
						cryptoCreate(TOKEN_TREASURY).balance(100 * ONE_HUNDRED_HBARS)
								.maxAutomaticTokenAssociations(10),

						tokenCreate(token)
								.tokenType(TokenType.FUNGIBLE_COMMON)
								.supplyType(TokenSupplyType.FINITE)
								.supplyKey("supplyKey")
								.maxSupply(1000L)
								.initialSupply(10L)
								.kycKey("kycKey")
								.adminKey("adminKey")
								.freezeKey("freezeKey")
								.pauseKey("pauseKey")
								.treasury(TOKEN_TREASURY),
						tokenCreate(nft)
								.maxSupply(10L)
								.initialSupply(0)
								.supplyType(TokenSupplyType.FINITE)
								.tokenType(NON_FUNGIBLE_UNIQUE)
								.supplyKey("supplyKey")
								.kycKey("kycKey")
								.adminKey("adminKey")
								.freezeKey("freezeKey")
								.pauseKey("pauseKey")
								.treasury(TOKEN_TREASURY),

						tokenAssociate(owner, token),
						tokenAssociate(owner, nft),
						mintToken(nft, List.of(
								ByteString.copyFromUtf8("a"),
								ByteString.copyFromUtf8("b"),
								ByteString.copyFromUtf8("c")
						)).via("nftTokenMint"),
						mintToken(token, 500L).via("tokenMint"),
						grantTokenKyc(token, owner),
						grantTokenKyc(nft, owner),
						cryptoTransfer(movingUnique(nft, 1L, 2L, 3L)
								.between(TOKEN_TREASURY, owner))
				)
				.when(
						cryptoAdjustAllowance()
								.payingWith(owner)
								.addTokenAllowance(owner, token, spender, 100L)
								.addNftAllowance(owner, nft, spender, false, List.of(1L)),
						revokeTokenKyc(token, owner),
						revokeTokenKyc(nft, owner),
						cryptoDeleteAllowance()
								.payingWith(owner)
								.addCryptoDeleteAllowance(owner)
								.addTokenDeleteAllowance(owner, token)
								.addNftDeleteAllowance(owner, nft, List.of(1L)),
						getAccountInfo(owner).has(accountWith().noAllowances())
				)
				.then(
						cryptoAdjustAllowance()
								.payingWith(owner)
								.addTokenAllowance(owner, token, spender, 100L)
								.addNftAllowance(owner, nft, spender, false, List.of(3L)),
						tokenPause(token),
						tokenPause(nft),
						cryptoDeleteAllowance()
								.payingWith(owner)
								.addCryptoDeleteAllowance(owner)
								.addTokenDeleteAllowance(owner, token)
								.addNftDeleteAllowance(owner, nft, List.of(3L)),
						getAccountInfo(owner).has(accountWith().noAllowances()),

						tokenUnpause(token),
						tokenUnpause(nft),
						tokenFreeze(token, owner),
						tokenFreeze(nft, owner),
						cryptoAdjustAllowance()
								.payingWith(owner)
								.addTokenAllowance(owner, token, spender, 50L)
								.addNftAllowance(owner, nft, spender, false, List.of(2L)),

						cryptoDeleteAllowance()
								.payingWith(owner)
								.addCryptoDeleteAllowance(owner)
								.addTokenDeleteAllowance(owner, token)
								.addNftDeleteAllowance(owner, nft, List.of(2L)),
						getAccountInfo(owner).has(accountWith().noAllowances())
				);
	}

	private HapiApiSpec exceedsTransactionLimit() {
		final String owner = "owner";
		final String spender = "spender";
		final String spender1 = "spender1";
		final String spender2 = "spender2";
		final String token = "token";
		final String nft = "nft";
		return defaultHapiSpec("exceedsTransactionLimit")
				.given(
						newKeyNamed("supplyKey"),
						fileUpdate(APP_PROPERTIES)
								.fee(ONE_HUNDRED_HBARS)
								.payingWith(EXCHANGE_RATE_CONTROL)
								.overridingProps(Map.of(
										"hedera.allowances.maxTransactionLimit", "4")
								),
						cryptoCreate(owner)
								.balance(ONE_HUNDRED_HBARS)
								.maxAutomaticTokenAssociations(10),
						cryptoCreate(spender)
								.balance(ONE_HUNDRED_HBARS),
						cryptoCreate(spender1)
								.balance(ONE_HUNDRED_HBARS),
						cryptoCreate(spender2)
								.balance(ONE_HUNDRED_HBARS),
						cryptoCreate(TOKEN_TREASURY).balance(100 * ONE_HUNDRED_HBARS)
								.maxAutomaticTokenAssociations(10),
						tokenCreate(token)
								.tokenType(TokenType.FUNGIBLE_COMMON)
								.supplyType(TokenSupplyType.FINITE)
								.supplyKey("supplyKey")
								.maxSupply(1000L)
								.initialSupply(10L)
								.treasury(TOKEN_TREASURY),
						tokenCreate(nft)
								.maxSupply(10L)
								.initialSupply(0)
								.supplyType(TokenSupplyType.FINITE)
								.tokenType(NON_FUNGIBLE_UNIQUE)
								.supplyKey("supplyKey")
								.treasury(TOKEN_TREASURY),
						tokenAssociate(owner, token),
						tokenAssociate(owner, nft),
						mintToken(nft, List.of(
								ByteString.copyFromUtf8("a"),
								ByteString.copyFromUtf8("b"),
								ByteString.copyFromUtf8("c")
						)).via("nftTokenMint"),
						mintToken(token, 500L).via("tokenMint"),
						cryptoTransfer(movingUnique(nft, 1L, 2L, 3L)
								.between(TOKEN_TREASURY, owner))
				)
				.when(
						cryptoApproveAllowance()
								.payingWith(owner)
								.addCryptoAllowance(owner, spender, 100L)
								.addCryptoAllowance(owner, spender1, 100L)
								.addTokenAllowance(owner, token, spender, 100L)
								.addNftAllowance(owner, nft, spender, false, List.of(1L)),
						cryptoApproveAllowance()
								.payingWith(owner)
								.addCryptoAllowance(owner, spender1, 100L)
								.addCryptoAllowance(owner, spender2, 100L),
						cryptoDeleteAllowance()
								.payingWith(owner)
								.addCryptoDeleteAllowance(owner)
								.addTokenDeleteAllowance(owner, token)
								.addNftDeleteAllowance(owner, nft, List.of(1L, 2L, 3L))
								.hasPrecheck(MAX_ALLOWANCES_EXCEEDED)
				)
				.then(
						// reset
						fileUpdate(APP_PROPERTIES)
								.fee(ONE_HUNDRED_HBARS)
								.payingWith(EXCHANGE_RATE_CONTROL)
								.overridingProps(Map.of(
										"hedera.allowances.maxTransactionLimit", "20")
								)
				);
	}

	private HapiApiSpec exceedsAccountLimit() {
		final String owner = "owner";
		final String spender = "spender";
		final String spender1 = "spender1";
		final String spender2 = "spender2";
		final String token = "token";
		final String nft = "nft";
		return defaultHapiSpec("exceedsAccountLimit")
				.given(
						fileUpdate(APP_PROPERTIES)
								.fee(ONE_HUNDRED_HBARS)
								.payingWith(EXCHANGE_RATE_CONTROL)
								.overridingProps(Map.of(
										"hedera.allowances.maxAccountLimit", "4",
										"hedera.allowances.maxTransactionLimit", "5")
								),

						newKeyNamed("supplyKey"),
						cryptoCreate(owner)
								.balance(ONE_HUNDRED_HBARS)
								.maxAutomaticTokenAssociations(10),
						cryptoCreate(spender)
								.balance(ONE_HUNDRED_HBARS),
						cryptoCreate(spender1)
								.balance(ONE_HUNDRED_HBARS),
						cryptoCreate(spender2)
								.balance(ONE_HUNDRED_HBARS),
						cryptoCreate(TOKEN_TREASURY).balance(100 * ONE_HUNDRED_HBARS)
								.maxAutomaticTokenAssociations(10),
						tokenCreate(token)
								.tokenType(TokenType.FUNGIBLE_COMMON)
								.supplyType(TokenSupplyType.FINITE)
								.supplyKey("supplyKey")
								.maxSupply(1000L)
								.initialSupply(10L)
								.treasury(TOKEN_TREASURY),
						tokenCreate(nft)
								.maxSupply(10L)
								.initialSupply(0)
								.supplyType(TokenSupplyType.FINITE)
								.tokenType(NON_FUNGIBLE_UNIQUE)
								.supplyKey("supplyKey")
								.treasury(TOKEN_TREASURY),
						tokenAssociate(owner, token),
						tokenAssociate(owner, nft),
						mintToken(nft, List.of(
								ByteString.copyFromUtf8("a"),
								ByteString.copyFromUtf8("b"),
								ByteString.copyFromUtf8("c")
						)).via("nftTokenMint"),
						mintToken(token, 500L).via("tokenMint"),
						cryptoTransfer(movingUnique(nft, 1L, 2L, 3L)
								.between(TOKEN_TREASURY, owner))
				)
				.when(
						cryptoAdjustAllowance()
								.payingWith(owner)
								.addCryptoAllowance(owner, spender, 100L)
								.addCryptoAllowance(owner, spender2, 100L)
								.addTokenAllowance(owner, token, spender, 100L)
								.addNftAllowance(owner, nft, spender, false, List.of(1L)),
						getAccountInfo(owner)
								.has(accountWith()
										.cryptoAllowancesCount(2)
										.tokenAllowancesCount(1)
										.nftApprovedForAllAllowancesCount(1)
								)
				)
				.then(
						cryptoAdjustAllowance()
								.payingWith(owner)
								.addCryptoAllowance(owner, spender, -100L),
						getAccountInfo(owner)
								.has(accountWith()
										.cryptoAllowancesCount(1)
										.tokenAllowancesCount(1)
										.nftApprovedForAllAllowancesCount(1)
								),
						cryptoAdjustAllowance()
								.payingWith(owner)
								.addCryptoAllowance(owner, spender, 100L),
						cryptoAdjustAllowance()
								.payingWith(owner)
								.addCryptoAllowance(owner, spender1, 100L)
								.via("maxExceeded")
								.hasKnownStatus(MAX_ALLOWANCES_EXCEEDED),
						getTxnRecord("maxExceeded")
								.hasCryptoAllowanceCount(0)
								.hasTokenAllowanceCount(0),
						// reset
						fileUpdate(APP_PROPERTIES)
								.fee(ONE_HUNDRED_HBARS)
								.payingWith(EXCHANGE_RATE_CONTROL)
								.overridingProps(Map.of(
										"hedera.allowances.maxTransactionLimit", "20",
										"hedera.allowances.maxAccountLimit", "100")
								)
				);
	}

	private HapiApiSpec validatesSerialNums() {
		final String owner = "owner";
		final String spender = "spender";
		final String nft = "nft";
		return defaultHapiSpec("validatesSerialNums")
				.given(
						newKeyNamed("supplyKey"),
						cryptoCreate(owner)
								.balance(ONE_HUNDRED_HBARS)
								.maxAutomaticTokenAssociations(10),
						cryptoCreate(spender)
								.balance(ONE_HUNDRED_HBARS),
						cryptoCreate(TOKEN_TREASURY).balance(100 * ONE_HUNDRED_HBARS)
								.maxAutomaticTokenAssociations(10),
						tokenCreate(nft)
								.maxSupply(10L)
								.initialSupply(0)
								.supplyType(TokenSupplyType.FINITE)
								.tokenType(NON_FUNGIBLE_UNIQUE)
								.supplyKey("supplyKey")
								.treasury(TOKEN_TREASURY),
						tokenAssociate(owner, nft),
						mintToken(nft, List.of(
								ByteString.copyFromUtf8("a"),
								ByteString.copyFromUtf8("b"),
								ByteString.copyFromUtf8("c")
						)).via("nftTokenMint"),
						cryptoTransfer(movingUnique(nft, 1L, 2L)
								.between(TOKEN_TREASURY, owner))
				)
				.when(
						cryptoApproveAllowance()
								.payingWith(owner)
								.addNftAllowance(owner, nft, spender, false, List.of(1L, 2L)),
						cryptoDeleteAllowance()
								.payingWith(owner)
								.addNftDeleteAllowance(owner, nft, List.of(1L)),
						cryptoDeleteAllowance()
								.payingWith(owner)
								.addNftDeleteAllowance(owner, nft, List.of(-1L))
								.hasPrecheck(INVALID_TOKEN_NFT_SERIAL_NUMBER),
						cryptoDeleteAllowance()
								.payingWith(owner)
								.addNftDeleteAllowance(owner, nft, List.of(1000L))
								.hasPrecheck(INVALID_TOKEN_NFT_SERIAL_NUMBER),
						cryptoDeleteAllowance()
								.payingWith(owner)
								.addNftDeleteAllowance(owner, nft, List.of(3L))
								.hasPrecheck(SENDER_DOES_NOT_OWN_NFT_SERIAL_NO),
						cryptoDeleteAllowance()
								.payingWith(owner)
								.addNftDeleteAllowance(owner, nft, List.of(1L, 1L, 2L))
								.hasPrecheck(REPEATED_SERIAL_NUMS_IN_NFT_ALLOWANCES),
						cryptoDeleteAllowance()
								.payingWith(owner)
								.addNftDeleteAllowance(owner, nft, List.of())
								.hasPrecheck(EMPTY_ALLOWANCES)
				)
				.then();
	}

	private HapiApiSpec invalidTokenTypeFailsInDeleteAllowance() {
		final String owner = "owner";
		final String spender = "spender";
		final String token = "token";
		final String nft = "nft";
		return defaultHapiSpec("invalidTokenTypeFailsInDeleteAllowance")
				.given(
						newKeyNamed("supplyKey"),
						cryptoCreate(owner)
								.balance(ONE_HUNDRED_HBARS)
								.maxAutomaticTokenAssociations(10),
						cryptoCreate(spender)
								.balance(ONE_HUNDRED_HBARS),
						cryptoCreate(TOKEN_TREASURY).balance(100 * ONE_HUNDRED_HBARS)
								.maxAutomaticTokenAssociations(10),
						tokenCreate(token)
								.tokenType(TokenType.FUNGIBLE_COMMON)
								.supplyType(TokenSupplyType.FINITE)
								.supplyKey("supplyKey")
								.maxSupply(1000L)
								.initialSupply(10L)
								.treasury(TOKEN_TREASURY),
						tokenCreate(nft)
								.maxSupply(10L)
								.initialSupply(0)
								.supplyType(TokenSupplyType.FINITE)
								.tokenType(NON_FUNGIBLE_UNIQUE)
								.supplyKey("supplyKey")
								.treasury(TOKEN_TREASURY),
						tokenAssociate(owner, token),
						tokenAssociate(owner, nft),
						mintToken(nft, List.of(
								ByteString.copyFromUtf8("a"),
								ByteString.copyFromUtf8("b"),
								ByteString.copyFromUtf8("c")
						)).via("nftTokenMint"),
						mintToken(token, 500L).via("tokenMint"),
						cryptoTransfer(movingUnique(nft, 1L, 2L, 3L)
								.between(TOKEN_TREASURY, owner))
				)
				.when()
				.then(
						cryptoDeleteAllowance()
								.payingWith(owner)
								.addTokenDeleteAllowance(owner, nft)
								.hasPrecheck(NFT_IN_FUNGIBLE_TOKEN_ALLOWANCES),
						cryptoDeleteAllowance()
								.payingWith(owner)
								.addNftDeleteAllowance(owner, token, List.of(1L))
								.hasPrecheck(FUNGIBLE_TOKEN_IN_NFT_ALLOWANCES)
				);
	}

	private HapiApiSpec emptyAllowancesDeleteRejected() {
		final String owner = "owner";
		return defaultHapiSpec("emptyAllowancesDeleteRejected")
				.given(
						cryptoCreate(owner)
								.balance(ONE_HUNDRED_HBARS)
								.maxAutomaticTokenAssociations(10)
				)
				.when(
						cryptoDeleteAllowance()
								.hasPrecheck(EMPTY_ALLOWANCES)
				)
				.then();
	}

	private HapiApiSpec tokenNotAssociatedToAccountFailsOnDeleteAllowance() {
		final String owner = "owner";
		final String spender = "spender";
		final String token = "token";
		final String nft = "nft";
		return defaultHapiSpec("tokenNotAssociatedToAccountFailsOnDeleteAllowance")
				.given(
						newKeyNamed("supplyKey"),
						cryptoCreate(owner)
								.balance(ONE_HUNDRED_HBARS)
								.maxAutomaticTokenAssociations(10),
						cryptoCreate(spender)
								.balance(ONE_HUNDRED_HBARS),
						cryptoCreate(TOKEN_TREASURY).balance(100 * ONE_HUNDRED_HBARS)
								.maxAutomaticTokenAssociations(10),
						tokenCreate(token)
								.tokenType(TokenType.FUNGIBLE_COMMON)
								.supplyType(TokenSupplyType.FINITE)
								.supplyKey("supplyKey")
								.maxSupply(1000L)
								.initialSupply(10L)
								.treasury(TOKEN_TREASURY),
						tokenCreate(nft)
								.maxSupply(10L)
								.initialSupply(0)
								.supplyType(TokenSupplyType.FINITE)
								.tokenType(NON_FUNGIBLE_UNIQUE)
								.supplyKey("supplyKey")
								.treasury(TOKEN_TREASURY),
						mintToken(nft, List.of(
								ByteString.copyFromUtf8("a"),
								ByteString.copyFromUtf8("b"),
								ByteString.copyFromUtf8("c")
						)).via("nftTokenMint"),
						mintToken(token, 500L).via("tokenMint")
				)
				.when(
						cryptoDeleteAllowance()
								.payingWith(owner)
								.addTokenDeleteAllowance(owner, token)
								.hasPrecheck(TOKEN_NOT_ASSOCIATED_TO_ACCOUNT),
						cryptoDeleteAllowance()
								.payingWith(owner)
								.addNftDeleteAllowance(owner, nft, List.of(1L))
								.hasPrecheck(TOKEN_NOT_ASSOCIATED_TO_ACCOUNT)
				)
				.then(
						getAccountInfo(owner)
								.has(accountWith()
										.cryptoAllowancesCount(0)
										.nftApprovedForAllAllowancesCount(0)
										.tokenAllowancesCount(0)
								));
	}

	private HapiApiSpec canDeleteMultipleOwners() {
		final String owner1 = "owner1";
		final String owner2 = "owner2";
		final String spender = "spender";
		final String token = "token";
		final String nft = "nft";
		return defaultHapiSpec("canDeleteMultipleOwners")
				.given(
						newKeyNamed("supplyKey"),
						cryptoCreate(owner1)
								.balance(ONE_HUNDRED_HBARS)
								.maxAutomaticTokenAssociations(10),
						cryptoCreate(owner2)
								.balance(ONE_HUNDRED_HBARS)
								.maxAutomaticTokenAssociations(10),
						cryptoCreate(spender)
								.balance(ONE_HUNDRED_HBARS),
						cryptoCreate(TOKEN_TREASURY).balance(100 * ONE_HUNDRED_HBARS)
								.maxAutomaticTokenAssociations(10),
						tokenCreate(token)
								.tokenType(TokenType.FUNGIBLE_COMMON)
								.supplyType(TokenSupplyType.FINITE)
								.supplyKey("supplyKey")
								.maxSupply(10_000L)
								.initialSupply(10L)
								.treasury(TOKEN_TREASURY),
						tokenCreate(nft)
								.maxSupply(10L)
								.initialSupply(0)
								.supplyType(TokenSupplyType.FINITE)
								.tokenType(NON_FUNGIBLE_UNIQUE)
								.supplyKey("supplyKey")
								.treasury(TOKEN_TREASURY),
						tokenAssociate(owner1, token, nft),
						tokenAssociate(owner2, token, nft),
						mintToken(nft, List.of(
								ByteString.copyFromUtf8("a"),
								ByteString.copyFromUtf8("b"),
								ByteString.copyFromUtf8("c"),
								ByteString.copyFromUtf8("d"),
								ByteString.copyFromUtf8("e"),
								ByteString.copyFromUtf8("f")
						)).via("nftTokenMint"),
						mintToken(token, 1000L).via("tokenMint"),
						cryptoTransfer(
								moving(500, token).between(TOKEN_TREASURY, owner1),
								moving(500, token).between(TOKEN_TREASURY, owner2),
								movingUnique(nft, 1L, 2L, 3L).between(TOKEN_TREASURY, owner1),
								movingUnique(nft, 4L, 5L, 6L).between(TOKEN_TREASURY, owner2))
				)
				.when(
						cryptoAdjustAllowance()
								.payingWith(DEFAULT_PAYER)
								.addCryptoAllowance(owner1, spender, ONE_HBAR)
								.addTokenAllowance(owner1, token, spender, 100L)
								.addNftAllowance(owner1, nft, spender, false, List.of(1L))
								.addCryptoAllowance(owner2, spender, 2 * ONE_HBAR)
								.addTokenAllowance(owner2, token, spender, 300L)
								.addNftAllowance(owner2, nft, spender, false, List.of(4L, 5L))
								.signedBy(DEFAULT_PAYER, owner1, owner2)
								.via("multiOwnerTxn"),

						getAccountInfo(owner1)
								.has(accountWith()
										.tokenAllowancesContaining(token, spender, 100L)
										.cryptoAllowancesContaining(spender, ONE_HBAR)
										.nftApprovedForAllAllowancesCount(0)),
						getAccountInfo(owner2)
								.has(accountWith()
										.tokenAllowancesContaining(token, spender, 300L)
										.cryptoAllowancesContaining(spender, 2 * ONE_HBAR)
										.nftApprovedForAllAllowancesCount(0)),
						getTokenNftInfo(nft, 1L).hasSpenderID(spender),
						getTokenNftInfo(nft, 4L).hasSpenderID(spender),
						getTokenNftInfo(nft, 5L).hasSpenderID(spender)
				)
				.then(
						cryptoDeleteAllowance()
								.payingWith(DEFAULT_PAYER)
								.addCryptoDeleteAllowance(owner1)
								.addTokenDeleteAllowance(owner1, token)
								.addNftDeleteAllowance(owner1, nft, List.of(1L))
								.addCryptoDeleteAllowance(owner2)
								.addTokenDeleteAllowance(owner2, token)
								.addNftDeleteAllowance(owner2, nft, List.of(4L, 5L))
								.signedBy(DEFAULT_PAYER, owner1, owner2)
								.via("multiOwnerDeleteTxn"),
						getAccountInfo(owner1)
								.has(accountWith().noAllowances()),
						getAccountInfo(owner2)
								.has(accountWith().noAllowances()),
						getTokenNftInfo(nft, 1L).hasNoSpender(),
						getTokenNftInfo(nft, 4L).hasNoSpender(),
						getTokenNftInfo(nft, 5L).hasNoSpender()
				);
	}

	private HapiApiSpec noOwnerDefaultsToPayerInDeleteAllowance() {
		final String payer = "payer";
		final String spender = "spender";
		final String spender1 = "spender1";
		final String token = "token";
		final String nft = "nft";
		return defaultHapiSpec("noOwnerDefaultsToPayerInDeleteAllowance")
				.given(
						newKeyNamed("supplyKey"),
						cryptoCreate(payer)
								.balance(ONE_HUNDRED_HBARS)
								.maxAutomaticTokenAssociations(10),
						cryptoCreate(spender)
								.balance(ONE_HUNDRED_HBARS),
						cryptoCreate(spender1)
								.balance(ONE_HUNDRED_HBARS),
						cryptoCreate(TOKEN_TREASURY).balance(100 * ONE_HUNDRED_HBARS)
								.maxAutomaticTokenAssociations(10),
						tokenCreate(token)
								.tokenType(TokenType.FUNGIBLE_COMMON)
								.supplyType(TokenSupplyType.FINITE)
								.supplyKey("supplyKey")
								.maxSupply(1000L)
								.initialSupply(10L)
								.treasury(TOKEN_TREASURY),
						tokenCreate(nft)
								.maxSupply(10L)
								.initialSupply(0)
								.supplyType(TokenSupplyType.FINITE)
								.tokenType(NON_FUNGIBLE_UNIQUE)
								.supplyKey("supplyKey")
								.treasury(TOKEN_TREASURY),
						tokenAssociate(payer, token),
						tokenAssociate(payer, nft),
						mintToken(nft, List.of(
								ByteString.copyFromUtf8("a"),
								ByteString.copyFromUtf8("b"),
								ByteString.copyFromUtf8("c")
						)).via("nftTokenMint"),
						mintToken(token, 500L).via("tokenMint"),
						cryptoTransfer(movingUnique(nft, 1L, 2L, 3L)
								.between(TOKEN_TREASURY, payer))
				)
				.when(
						cryptoApproveAllowance()
								.payingWith(payer)
								.addCryptoAllowance(payer, spender, 100L)
								.addTokenAllowance(payer, token, spender, 100L)
								.addNftAllowance(payer, nft, spender, true, List.of(1L))
								.blankMemo()
								.logged(),
						cryptoDeleteAllowance()
								.payingWith(payer)
								.addCryptoDeleteAllowance(MISSING_OWNER)
								.addTokenDeleteAllowance(MISSING_OWNER, token)
								.addNftDeleteAllowance(MISSING_OWNER, nft, List.of(1L))
								.via("deleteTxn")
								.blankMemo()
								.logged(),
						getTxnRecord("deleteTxn").logged()
				)
				.then(
						getAccountInfo(payer)
								.has(accountWith()
										.noCryptoAllowances()
										.noTokenAllowances(payer)
										.nftApprovedForAllAllowancesCount(0)
								),
						getTokenNftInfo(nft, 1L).hasNoSpender()
				);
	}

	private HapiApiSpec approvedForAllNotAffectedOnDelete() {
		final String owner = "owner";
		final String spender = "spender";
		final String token = "token";
		final String nft = "nft";
		return defaultHapiSpec("approvedForAllNotAffectedOnDelete")
				.given(
						newKeyNamed("supplyKey"),
						cryptoCreate(owner)
								.balance(ONE_HUNDRED_HBARS)
								.maxAutomaticTokenAssociations(10),
						cryptoCreate(spender)
								.balance(ONE_HUNDRED_HBARS),
						cryptoCreate(TOKEN_TREASURY).balance(100 * ONE_HUNDRED_HBARS)
								.maxAutomaticTokenAssociations(10),
						tokenCreate(token)
								.tokenType(TokenType.FUNGIBLE_COMMON)
								.supplyType(TokenSupplyType.FINITE)
								.supplyKey("supplyKey")
								.maxSupply(1000L)
								.initialSupply(10L)
								.treasury(TOKEN_TREASURY),
						tokenCreate(nft)
								.maxSupply(10L)
								.initialSupply(0)
								.supplyType(TokenSupplyType.FINITE)
								.tokenType(NON_FUNGIBLE_UNIQUE)
								.supplyKey("supplyKey")
								.treasury(TOKEN_TREASURY),
						tokenAssociate(owner, token),
						tokenAssociate(owner, nft),
						mintToken(nft, List.of(
								ByteString.copyFromUtf8("a"),
								ByteString.copyFromUtf8("b"),
								ByteString.copyFromUtf8("c")
						)).via("nftTokenMint"),
						mintToken(token, 500L).via("tokenMint"),
						cryptoTransfer(movingUnique(nft, 1L, 2L, 3L)
								.between(TOKEN_TREASURY, owner))
				)
				.when(
						cryptoApproveAllowance()
								.payingWith(owner)
								.addCryptoAllowance(owner, spender, 100L)
								.addTokenAllowance(owner, token, spender, 100L)
								.addNftAllowance(owner, nft, spender, true, List.of(1L))
								.via("otherAdjustTxn"),
						getAccountInfo(owner)
								.has(accountWith()
										.cryptoAllowancesCount(1)
										.nftApprovedForAllAllowancesCount(1)
										.tokenAllowancesCount(1)
										.cryptoAllowancesContaining(spender, 100L)
										.tokenAllowancesContaining(token, spender, 100L)
										.nftApprovedAllowancesContaining(nft, spender)
								),
						getTokenNftInfo(nft, 1L).hasSpenderID(spender)
				)
				.then(
						cryptoDeleteAllowance()
								.payingWith(owner)
								.addCryptoDeleteAllowance(owner)
								.addTokenDeleteAllowance(owner, token)
								.addNftDeleteAllowance(owner, nft, List.of(1L))
								.blankMemo()
								.via("cryptoDeleteAllowanceTxn")
								.logged(),
						getTxnRecord("cryptoDeleteAllowanceTxn").logged(),
						getAccountInfo(owner)
								.has(accountWith().
										noCryptoAllowances()
										.noTokenAllowances(owner)
										.nftApprovedAllowancesContaining(nft, spender)).logged(),
						getTokenNftInfo(nft, 1L).hasNoSpender()
				);
	}

	private HapiApiSpec happyPathWorks() {
		final String owner = "owner";
		final String spender = "spender";
		final String token = "token";
		final String nft = "nft";
		return defaultHapiSpec("happyPathWorks")
				.given(
						newKeyNamed("supplyKey"),
						cryptoCreate(owner)
								.balance(ONE_HUNDRED_HBARS)
								.maxAutomaticTokenAssociations(10),
						cryptoCreate(spender)
								.balance(ONE_HUNDRED_HBARS),
						cryptoCreate(TOKEN_TREASURY).balance(100 * ONE_HUNDRED_HBARS)
								.maxAutomaticTokenAssociations(10),
						tokenCreate(token)
								.tokenType(TokenType.FUNGIBLE_COMMON)
								.supplyType(TokenSupplyType.FINITE)
								.supplyKey("supplyKey")
								.maxSupply(1000L)
								.initialSupply(10L)
								.treasury(TOKEN_TREASURY),
						tokenCreate(nft)
								.maxSupply(10L)
								.initialSupply(0)
								.supplyType(TokenSupplyType.FINITE)
								.tokenType(NON_FUNGIBLE_UNIQUE)
								.supplyKey("supplyKey")
								.treasury(TOKEN_TREASURY),
						tokenAssociate(owner, token),
						tokenAssociate(owner, nft),
						mintToken(nft, List.of(
								ByteString.copyFromUtf8("a"),
								ByteString.copyFromUtf8("b"),
								ByteString.copyFromUtf8("c")
						)).via("nftTokenMint"),
						mintToken(token, 500L).via("tokenMint"),
						cryptoTransfer(movingUnique(nft, 1L, 2L, 3L)
								.between(TOKEN_TREASURY, owner))
				)
				.when(
						cryptoApproveAllowance()
								.payingWith(owner)
								.addCryptoAllowance(owner, spender, 100L)
								.addTokenAllowance(owner, token, spender, 100L)
								.addNftAllowance(owner, nft, spender, false, List.of(1L))
								.via("otherAdjustTxn"),
						getAccountInfo(owner)
								.has(accountWith()
										.cryptoAllowancesCount(1)
										.nftApprovedForAllAllowancesCount(0)
										.tokenAllowancesCount(1)
										.cryptoAllowancesContaining(spender, 100L)
										.tokenAllowancesContaining(token, spender, 100L)
								),
						getTokenNftInfo(nft, 1L).hasSpenderID(spender)
				)
				.then(
						cryptoDeleteAllowance()
								.payingWith(owner)
								.addCryptoDeleteAllowance(MISSING_OWNER)
								.blankMemo()
								.via("baseCryptoDeleteAllowanceTxn")
								.logged(),
						validateChargedUsdWithin("baseCryptoDeleteAllowanceTxn", 0.05, 0.01), // IS different ?
						cryptoDeleteAllowance()
								.payingWith(owner)
								.addCryptoDeleteAllowance(owner)
								.addTokenDeleteAllowance(owner, token)
								.addNftDeleteAllowance(owner, nft, List.of(1L))
								.blankMemo()
								.via("cryptoDeleteAllowanceTxn")
								.logged(),
						getTxnRecord("cryptoDeleteAllowanceTxn").logged(),
						validateChargedUsdWithin("cryptoDeleteAllowanceTxn", 0.0513359, 0.01),
						getAccountInfo(owner)
								.has(accountWith().noAllowances()),
						getTokenNftInfo(nft, 1L).hasNoSpender());
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}
