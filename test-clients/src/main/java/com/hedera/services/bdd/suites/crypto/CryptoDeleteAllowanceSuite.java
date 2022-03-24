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
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.moving;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.movingUnique;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.validateChargedUsdWithin;
import static com.hedera.services.bdd.suites.crypto.CryptoApproveAllowanceSuite.MISSING_OWNER;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_DELETED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.EMPTY_ALLOWANCES;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FUNGIBLE_TOKEN_IN_NFT_ALLOWANCES;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ALLOWANCE_OWNER_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SIGNATURE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_NFT_SERIAL_NUMBER;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.MAX_ALLOWANCES_EXCEEDED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NFT_IN_FUNGIBLE_TOKEN_ALLOWANCES;
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
//				approvedForAllNotAffected(),
//				noOwnerDefaultsToPayer(),
//				invalidOwnerFails(),
//				canHaveMultipleOwners(),
//				emptyAllowancesRejected(),
//				repeatedAllowancesFail(),
//				tokenNotAssociatedToAccountFails(),
//				invalidTokenTypeFails(),
//				validatesSerialNums(),
//				serialOnTokenWipedForNftAllowance(),
//				exceedsTransactionLimit(),
//				exceedsAccountLimit(),
//				succeedsWhenTokenPausedFrozenKycRevoked(),
//				feesAsExpected()
		});
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

						cryptoAdjustAllowance()
								.payingWith("payer")
								.addCryptoAllowance(owner, spender, 100L)
								.signedBy("payer", owner)
								.blankMemo()
								.hasPrecheck(INVALID_ALLOWANCE_OWNER_ID),
						cryptoAdjustAllowance()
								.payingWith("payer")
								.addTokenAllowance(owner, token, spender, 100L)
								.signedBy("payer", owner)
								.blankMemo()
								.hasPrecheck(INVALID_ALLOWANCE_OWNER_ID),
						cryptoAdjustAllowance()
								.payingWith("payer")
								.addNftAllowance(owner, nft, spender, false, List.of(1L))
								.signedBy("payer", owner)
								.via("baseApproveTxn")
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
		return defaultHapiSpec("feesAsExpected")
				.given(
						newKeyNamed("supplyKey"),
						cryptoCreate(owner)
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
								.addCryptoAllowance(owner, spender, 100L)
								.via("adjust")
								.blankMemo()
								.logged(),
						validateChargedUsdWithin("adjust", 0.05063, 0.01),
						cryptoAdjustAllowance()
								.payingWith(owner)
								.addTokenAllowance(owner, token, spender, 100L)
								.via("adjustTokenTxn")
								.blankMemo()
								.logged(),
						validateChargedUsdWithin("adjustTokenTxn", 0.05075, 0.01)
				)
				.then(
						cryptoAdjustAllowance()
								.payingWith(owner)
								.addNftAllowance(owner, nft, spender, false, List.of(1L))
								.via("adjustNftTxn")
								.blankMemo()
								.logged(),
						validateChargedUsdWithin("adjustNftTxn", 0.05088, 0.01),
						cryptoAdjustAllowance()
								.payingWith(owner)
								.addNftAllowance(owner, nft, "spender1", true, List.of())
								.via("adjustForAllNftTxn")
								.blankMemo()
								.logged(),
						validateChargedUsdWithin("adjustForAllNftTxn", 0.05063, 0.01),
						cryptoAdjustAllowance()
								.payingWith(owner)
								.addCryptoAllowance(owner, "spender2", 100L)
								.addTokenAllowance(owner, token, "spender2", 100L)
								.addNftAllowance(owner, nft, "spender2", false, List.of(1L))
								.via("adjustTxn")
								.blankMemo()
								.logged(),
						validateChargedUsdWithin("adjustTxn", 0.05318, 0.01),
						getAccountInfo(owner)
								.has(accountWith()
										.cryptoAllowancesCount(2)
										.nftApprovedForAllAllowancesCount(3)
										.tokenAllowancesCount(2)
										.cryptoAllowancesContaining(spender, 100L)
										.tokenAllowancesContaining(token, spender, 100L)
										.nftApprovedAllowancesContaining(nft, spender, false)
								),
						cryptoAdjustAllowance()
								.payingWith(owner)
								.addCryptoAllowance(owner, "spender2", 200L)
								.via("adjustCryptoSingle")
								.blankMemo()
								.logged(),
						validateChargedUsdWithin("adjustCryptoSingle", 0.05, 0.01),
						cryptoAdjustAllowance()
								.payingWith(owner)
								.addTokenAllowance(owner, token, "spender2", 200L)
								.via("adjustTokenSingle")
								.blankMemo()
								.logged(),
						validateChargedUsdWithin("adjustTokenSingle", 0.05005, 0.01),
						cryptoAdjustAllowance()
								.payingWith(owner)
								.addNftAllowance(owner, nft, "spender2", false, List.of(2L))
								.via("adjustNftSingle")
								.blankMemo()
								.logged(),
						validateChargedUsdWithin("adjustNftSingle", 0.05024, 0.01),
						cryptoAdjustAllowance()
								.payingWith(owner)
								.addNftAllowance(owner, nft, "spender2", false, List.of(-2L))
								.via("adjustNftSingleRemove")
								.blankMemo()
								.logged(),
						validateChargedUsdWithin("adjustNftSingleRemove", 0.05010, 0.01),
						cryptoAdjustAllowance()
								.payingWith(owner)
								.addNftAllowance(owner, nft, "spender2", true, List.of())
								.via("adjustNftSingleApproveForAll")
								.blankMemo()
								.logged(),
						validateChargedUsdWithin("adjustNftSingleApproveForAll", 0.05, 0.01),
						getAccountInfo(owner)
								.has(accountWith()
										.cryptoAllowancesCount(2)
										.nftApprovedForAllAllowancesCount(3)
										.tokenAllowancesCount(2)
										.cryptoAllowancesContaining(spender, 100L)
										.tokenAllowancesContaining(token, spender, 100L)
										.nftApprovedAllowancesContaining(nft, spender, false)
								)
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
								.addNftAllowance(owner, nft, spender, false, List.of(1L, 2L)),
						revokeTokenKyc(token, owner),
						revokeTokenKyc(nft, owner),
						cryptoAdjustAllowance()
								.payingWith(owner)
								.addCryptoAllowance(owner, spender, 10L)
								.addTokenAllowance(owner, token, spender, 10L)
								.addNftAllowance(owner, nft, spender, false, List.of(-1L))

				)
				.then(
						tokenPause(token),
						tokenPause(nft),
						cryptoAdjustAllowance()
								.payingWith(owner)
								.addCryptoAllowance(owner, spender, -5L)
								.addTokenAllowance(owner, token, spender, -20L)
								.addNftAllowance(owner, nft, spender, false, List.of(3L)),

						tokenUnpause(token),
						tokenUnpause(nft),
						tokenFreeze(token, owner),
						tokenFreeze(nft, owner),
						cryptoAdjustAllowance()
								.payingWith(owner)
								.addTokenAllowance(owner, token, spender, -50L)
								.addNftAllowance(owner, nft, spender, false, List.of(-2L)),

						getAccountInfo(owner)
								.has(accountWith().cryptoAllowancesContaining(spender, 5L)
										.tokenAllowancesContaining(token, spender, 40L)
										.nftApprovedAllowancesContaining(nft, spender, false)
										.cryptoAllowancesCount(1)
										.nftApprovedForAllAllowancesCount(1)
										.tokenAllowancesCount(1)
								));
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
										"hedera.allowances.maxTransactionLimit", "4",
										"hedera.allowances.maxAccountLimit", "5")
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
								.addCryptoAllowance(owner, spender, 100L),
						cryptoAdjustAllowance()
								.payingWith(owner)
								.addCryptoAllowance(owner, spender, 100L)
								.addCryptoAllowance(owner, spender1, 100L)
								.addCryptoAllowance(owner, spender2, 100L)
								.addTokenAllowance(owner, token, spender, 100L)
								.addNftAllowance(owner, nft, spender, false, List.of(1L))
								.hasPrecheck(MAX_ALLOWANCES_EXCEEDED)

				)
				.then(
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
								.hasTokenAllowanceCount(0)
								.hasNftAllowanceCount(0),
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
								.addNftAllowance(owner, nft, spender, false, List.of(1L)),
						cryptoAdjustAllowance()
								.payingWith(owner)
								.addNftAllowance(owner, nft, spender, false, List.of(1000L))
								.hasPrecheck(INVALID_TOKEN_NFT_SERIAL_NUMBER),
						cryptoAdjustAllowance()
								.payingWith(owner)
								.addNftAllowance(owner, nft, spender, false, List.of(-1000L))
								.hasPrecheck(INVALID_TOKEN_NFT_SERIAL_NUMBER),
						cryptoAdjustAllowance()
								.payingWith(owner)
								.addNftAllowance(owner, nft, spender, false, List.of(3L))
								.hasPrecheck(SENDER_DOES_NOT_OWN_NFT_SERIAL_NO),
						cryptoAdjustAllowance()
								.payingWith(owner)
								.addNftAllowance(owner, nft, spender, false, List.of(2L, 2L, 3L, 3L))
								.hasPrecheck(REPEATED_SERIAL_NUMS_IN_NFT_ALLOWANCES),
						cryptoAdjustAllowance()
								.payingWith(owner)
								.addNftAllowance(owner, nft, spender, false, List.of())
								.hasPrecheck(EMPTY_ALLOWANCES),
						cryptoAdjustAllowance()
								.payingWith(owner)
								.addNftAllowance(owner, nft, spender, false, List.of(-2L))
								.hasPrecheck(INVALID_TOKEN_NFT_SERIAL_NUMBER),
						cryptoAdjustAllowance()
								.payingWith(owner)
								.addNftAllowance(owner, nft, spender, false, List.of(1L))
								.hasPrecheck(REPEATED_SERIAL_NUMS_IN_NFT_ALLOWANCES)
				)
				.then();
	}

	private HapiApiSpec invalidTokenTypeFails() {
		final String owner = "owner";
		final String spender = "spender";
		final String token = "token";
		final String nft = "nft";
		return defaultHapiSpec("invalidTokenTypeFails")
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
								.addTokenAllowance(owner, token, spender, 100L),
						cryptoApproveAllowance()
								.payingWith(owner)
								.addNftAllowance(owner, nft, spender, false, List.of(1L))
				)
				.then(
						cryptoAdjustAllowance()
								.payingWith(owner)
								.addTokenAllowance(owner, nft, spender, 100L)
								.hasPrecheck(NFT_IN_FUNGIBLE_TOKEN_ALLOWANCES),
						cryptoAdjustAllowance()
								.payingWith(owner)
								.addNftAllowance(owner, token, spender, false, List.of(1L))
								.hasPrecheck(FUNGIBLE_TOKEN_IN_NFT_ALLOWANCES)
				);
	}

	private HapiApiSpec emptyAllowancesRejected() {
		final String owner = "owner";
		return defaultHapiSpec("emptyAllowancesRejected")
				.given(
						cryptoCreate(owner)
								.balance(ONE_HUNDRED_HBARS)
								.maxAutomaticTokenAssociations(10)
				)
				.when(
						cryptoAdjustAllowance()
								.hasPrecheck(EMPTY_ALLOWANCES)
				)
				.then();
	}

	private HapiApiSpec tokenNotAssociatedToAccountFails() {
		final String owner = "owner";
		final String spender = "spender";
		final String token = "token";
		final String nft = "nft";
		return defaultHapiSpec("tokenNotAssociatedToAccountFails")
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
						cryptoAdjustAllowance()
								.payingWith(owner)
								.addTokenAllowance(owner, token, spender, 100L)
								.hasPrecheck(TOKEN_NOT_ASSOCIATED_TO_ACCOUNT),
						cryptoAdjustAllowance()
								.payingWith(owner)
								.addNftAllowance(owner, nft, spender, false, List.of(1L))
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

	private HapiApiSpec canHaveMultipleOwners() {
		final String owner1 = "owner1";
		final String owner2 = "owner2";
		final String spender = "spender";
		final String token = "token";
		final String nft = "nft";
		return defaultHapiSpec("canHaveMultipleOwners")
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
								.addCryptoAllowance(owner2, spender, ONE_HBAR)
								.addTokenAllowance(owner2, token, spender, 100L)
								.addNftAllowance(owner2, nft, spender, false, List.of(4L))
								.hasKnownStatus(INVALID_SIGNATURE),
						cryptoAdjustAllowance()
								.payingWith(DEFAULT_PAYER)
								.addCryptoAllowance(owner1, spender, ONE_HBAR)
								.addTokenAllowance(owner1, token, spender, 100L)
								.addNftAllowance(owner1, nft, spender, false, List.of(1L))
								.addCryptoAllowance(owner2, spender, ONE_HBAR)
								.addTokenAllowance(owner2, token, spender, 100L)
								.addNftAllowance(owner2, nft, spender, false, List.of(4L))
								.signedBy(DEFAULT_PAYER, owner1)
								.hasKnownStatus(INVALID_SIGNATURE),
						cryptoAdjustAllowance()
								.payingWith(DEFAULT_PAYER)
								.addCryptoAllowance(owner1, spender, ONE_HBAR)
								.addTokenAllowance(owner1, token, spender, 100L)
								.addNftAllowance(owner1, nft, spender, false, List.of(1L))
								.addCryptoAllowance(owner2, spender, ONE_HBAR)
								.addTokenAllowance(owner2, token, spender, 100L)
								.addNftAllowance(owner2, nft, spender, false, List.of(4L))
								.signedBy(DEFAULT_PAYER, owner2)
								.hasKnownStatus(INVALID_SIGNATURE),
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
						getTxnRecord("multiOwnerTxn")
								.hasCryptoAllowance(owner1, spender, ONE_HBAR)
								.hasTokenAllowance(owner1, token, spender, 100L)
								.hasNftAllowance(owner1, nft, spender, false, List.of(1L))
								.hasCryptoAllowance(owner2, spender, 2 * ONE_HBAR)
								.hasTokenAllowance(owner2, token, spender, 300L)
								.hasNftAllowance(owner2, nft, spender, false, List.of(4L, 5L))
								.logged()
				)
				.then(
						getAccountInfo(owner1)
								.has(accountWith()
										.tokenAllowancesContaining(token, spender, 100L)
										.cryptoAllowancesContaining(spender, ONE_HBAR)
										.nftApprovedAllowancesContaining(nft, spender, false)),
						getAccountInfo(owner2)
								.has(accountWith()
										.tokenAllowancesContaining(token, spender, 300L)
										.cryptoAllowancesContaining(spender, 2 * ONE_HBAR)
										.nftApprovedAllowancesContaining(nft, spender, false))
				);
	}

	private HapiApiSpec noOwnerDefaultsToPayer() {
		final String payer = "payer";
		final String spender = "spender";
		final String spender1 = "spender1";
		final String token = "token";
		final String nft = "nft";
		return defaultHapiSpec("noOwnerDefaultsToPayer")
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
										.noCryptoAllowances(payer)
										.noTokenAllowances(payer)
										.nftApprovedAllowancesContaining(nft, spender, false)
								));
	}

	private HapiApiSpec approvedForAllNotAffected() {
		final String owner = "owner";
		final String spender = "spender";
		final String token = "token";
		final String nft = "nft";
		return defaultHapiSpec("approvedForAllNotAffected")
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
										.nftApprovedAllowancesContaining(nft, spender, true)
								).logged()
				)
				.then(
						cryptoDeleteAllowance()
								.payingWith(owner)
								.addCryptoDeleteAllowance(owner)
								.addTokenDeleteAllowance(owner, token)
								.addNftDeleteAllowance(owner, nft, List.of(1L))
								.via("cryptoDeleteAllowanceTxn")
								.logged(),
						getTxnRecord("cryptoDeleteAllowanceTxn").logged(),
						getAccountInfo(owner)
								.has(accountWith().
										noCryptoAllowances(owner)
										.noTokenAllowances(owner)
										.nftApprovedAllowancesContaining(nft, spender, true)).logged()
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
								)
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
						getTxnRecord("cryptoDeleteAllowanceTxn").logged());
//						validateChargedUsdWithin("adjustTxn", 0.05173, 0.01),
//						getAccountInfo(owner)
//								.has(accountWith().noAllowances()));
		// NFT info should show the spender erased
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}
