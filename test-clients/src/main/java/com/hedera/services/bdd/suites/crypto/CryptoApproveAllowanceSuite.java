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
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenSupplyType;
import com.hederahashgraph.api.proto.java.TokenType;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.Map;

import static com.hedera.services.bdd.spec.HapiApiSpec.customHapiSpec;
import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.assertions.AccountInfoAsserts.accountWith;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountInfo;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoApproveAllowance;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.mintToken;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenAssociate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.movingUnique;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.AMOUNT_EXCEEDS_TOKEN_MAX_SUPPLY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.EMPTY_ALLOWANCES;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FUNGIBLE_TOKEN_IN_NFT_ALLOWANCES;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_NFT_SERIAL_NUMBER;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.MAX_ALLOWANCES_EXCEEDED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NEGATIVE_ALLOWANCE_AMOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NFT_IN_FUNGIBLE_TOKEN_ALLOWANCES;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.PAYER_AND_OWNER_NOT_EQUAL;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.REPEATED_SERIAL_NUMS_IN_NFT_ALLOWANCES;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SENDER_DOES_NOT_OWN_NFT_SERIAL_NO;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SPENDER_ACCOUNT_REPEATED_IN_ALLOWANCES;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_NOT_ASSOCIATED_TO_ACCOUNT;
import static com.hederahashgraph.api.proto.java.TokenType.NON_FUNGIBLE_UNIQUE;

public class CryptoApproveAllowanceSuite extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(CryptoApproveAllowanceSuite.class);

	public static void main(String... args) {
		new CryptoApproveAllowanceSuite().runSuiteSync();
	}

	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		return List.of(new HapiApiSpec[] {
//				happyPathWorks(),
//				emptyAllowancesRejected(),
//				spenderSameAsOwnerFails(),
//				spenderAccountRepeatedFails(),
//				negativeAmountFailsForFungible(),
//				tokenNotAssociatedToAccountFails(),
//				invalidTokenTypeFails(),
//				validatesSerialNums(),
//				tokenExceedsMaxSupplyFails(),
//				OwnerNotPayerFails(),
//				serialsWipedIfApprovedForAll(),
				exceedsTransactionLimit(),
				exceedsAccountLimit(),
//				succeedsWhenTokenPausedFrozenKycRevoked()

		});
	}

	private HapiApiSpec exceedsTransactionLimit() {
		final String owner = "owner";
		final String spender = "spender";
		final String spender1 = "spender1";
		final String spender2 = "spender2";
		final String token = "token";
		final String nft = "nft";
		return customHapiSpec("exceedsTransactionLimit")
				.withProperties(Map.of(
						"hedera.allowances.maxTransactionLimit", 4
				))
				.given(
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
						cryptoApproveAllowance()
								.payingWith(owner)
								.addCryptoAllowance(owner, spender, 100L)
								.addCryptoAllowance(owner, spender1, 100L)
								.addCryptoAllowance(owner, spender2, 100L)
								.addTokenAllowance(owner, token, spender, 100L)
								.addNftAllowance(owner, nft, spender, false, List.of(1L))
								.fee(ONE_HBAR)
				)
				.then();
	}

	private HapiApiSpec exceedsAccountLimit() {
		final String owner = "owner";
		final String spender = "spender";
		final String spender1 = "spender1";
		final String spender2 = "spender2";
		final String token = "token";
		final String nft = "nft";
		return customHapiSpec("exceedsTransactionLimit")
				.withProperties(Map.of(
						"hedera.allowances.maxAccountLimit", 4
				))
				.given(
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
						cryptoApproveAllowance()
								.payingWith(owner)
								.addCryptoAllowance(owner, spender, 100L)
								.addCryptoAllowance(owner, spender1, 100L)
								.addCryptoAllowance(owner, spender2, 100L)
								.addTokenAllowance(owner, token, spender, 100L)
								.addNftAllowance(owner, nft, spender, false, List.of(1L))
								.fee(ONE_HBAR),
						getAccountInfo(owner)
								.logged()
								.has(accountWith()
										.cryptoAllowancesCount(3)
										.tokenAllowancesCount(1)
										.nftAllowancesCount(1)
								)
				)
				.then(
						cryptoCreate("spender3")
								.balance(ONE_HUNDRED_HBARS),
						cryptoApproveAllowance()
								.payingWith(owner)
								.addCryptoAllowance(owner, spender, 100L)
								.fee(ONE_HBAR)
								.hasKnownStatus(MAX_ALLOWANCES_EXCEEDED)
				);
	}

	private HapiApiSpec serialsWipedIfApprovedForAll() {
		final String owner = "owner";
		final String spender = "spender";
		final String spender1 = "spender1";
		final String nft = "nft";
		return defaultHapiSpec("serialsWipedIfApprovedForAll")
				.given(
						newKeyNamed("supplyKey"),
						cryptoCreate(owner)
								.balance(ONE_HUNDRED_HBARS)
								.maxAutomaticTokenAssociations(10),
						cryptoCreate(spender)
								.balance(ONE_HUNDRED_HBARS),
						cryptoCreate(spender1)
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
						cryptoTransfer(movingUnique(nft, 1L, 2L, 3L)
								.between(TOKEN_TREASURY, owner))
				)
				.when(
						cryptoApproveAllowance()
								.payingWith(owner)
								.addNftAllowance(owner, nft, spender, true, List.of(1L))
								.fee(ONE_HBAR),
						cryptoApproveAllowance()
								.payingWith(owner)
								.addNftAllowance(owner, nft, spender1, false, List.of(1L))
								.fee(ONE_HBAR).logged()
				)
				.then(
						getAccountInfo(owner)
								.logged()
								.has(accountWith()
										.nftAllowancesCount(2)
										.nftAllowancesContaining(nft, spender, true, List.of())
										.nftAllowancesContaining(nft, spender1, false, List.of(1L))
								));
	}

	private HapiApiSpec OwnerNotPayerFails() {
		final String owner = "owner";
		final String spender = "spender";
		final String token = "token";
		final String nft = "nft";
		return defaultHapiSpec("OwnerNotPayerFails")
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
								.addCryptoAllowance(spender, spender, 100L)
								.addTokenAllowance(spender, token, spender, 100L)
								.addNftAllowance(spender, nft, spender, false, List.of(1L))
								.fee(ONE_HBAR)
								.hasPrecheck(PAYER_AND_OWNER_NOT_EQUAL)
				)
				.then();
	}

	private HapiApiSpec tokenExceedsMaxSupplyFails() {
		final String owner = "owner";
		final String spender = "spender";
		final String token = "token";
		return defaultHapiSpec("tokenExceedsMaxSupplyFails")
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
						tokenAssociate(owner, token),
						mintToken(token, 500L).via("tokenMint")
				)
				.when(
						cryptoApproveAllowance()
								.payingWith(owner)
								.addTokenAllowance(owner, token, spender, 5000L)
								.fee(ONE_HUNDRED_HBARS)
								.hasPrecheck(AMOUNT_EXCEEDS_TOKEN_MAX_SUPPLY)
				)
				.then();
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
								.addNftAllowance(owner, nft, spender, false, List.of(1000L))
								.fee(ONE_HUNDRED_HBARS)
								.hasPrecheck(INVALID_TOKEN_NFT_SERIAL_NUMBER),
						cryptoApproveAllowance()
								.payingWith(owner)
								.addNftAllowance(owner, nft, spender, false, List.of(-1000L))
								.fee(ONE_HUNDRED_HBARS)
								.hasPrecheck(INVALID_TOKEN_NFT_SERIAL_NUMBER),
						cryptoApproveAllowance()
								.payingWith(owner)
								.addNftAllowance(owner, nft, spender, false, List.of(3L))
								.fee(ONE_HUNDRED_HBARS)
								.hasPrecheck(SENDER_DOES_NOT_OWN_NFT_SERIAL_NO),
						cryptoApproveAllowance()
								.payingWith(owner)
								.addNftAllowance(owner, nft, spender, false, List.of(2L, 2L, 3L, 3L))
								.fee(ONE_HUNDRED_HBARS)
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
								.addTokenAllowance(owner, nft, spender, 100L)
								.fee(ONE_HUNDRED_HBARS)
								.hasPrecheck(NFT_IN_FUNGIBLE_TOKEN_ALLOWANCES),
						cryptoApproveAllowance()
								.payingWith(owner)
								.addNftAllowance(owner, token, spender, false, List.of(1L))
								.fee(ONE_HUNDRED_HBARS)
								.hasPrecheck(FUNGIBLE_TOKEN_IN_NFT_ALLOWANCES)
				)
				.then();
	}

	private HapiApiSpec emptyAllowancesRejected() {
		final String owner = "owner";
		final String spender = "spender";
		final String token = "token";
		final String nft = "nft";
		return defaultHapiSpec("emptyAllowancesRejected")
				.given(
						cryptoCreate(owner)
								.balance(ONE_HUNDRED_HBARS)
								.maxAutomaticTokenAssociations(10)
				)
				.when(
						cryptoApproveAllowance()
								.hasPrecheck(EMPTY_ALLOWANCES)
								.fee(ONE_HUNDRED_HBARS)
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
						cryptoApproveAllowance()
								.payingWith(owner)
								.addTokenAllowance(owner, token, spender, 100L)
								.fee(ONE_HUNDRED_HBARS)
								.hasPrecheck(TOKEN_NOT_ASSOCIATED_TO_ACCOUNT),
						cryptoApproveAllowance()
								.payingWith(owner)
								.addNftAllowance(owner, nft, spender, false, List.of(1L))
								.fee(ONE_HUNDRED_HBARS)
								.hasPrecheck(TOKEN_NOT_ASSOCIATED_TO_ACCOUNT)
				)
				.then(
						getAccountInfo(owner).logged()
								.has(accountWith()
										.cryptoAllowancesCount(0)
										.nftAllowancesCount(0)
										.tokenAllowancesCount(0)
								));
	}

	private HapiApiSpec spenderSameAsOwnerFails() {
		final String owner = "owner";
		final String token = "token";
		final String nft = "nft";
		return defaultHapiSpec("spenderSameAsOwnerFails")
				.given(
						newKeyNamed("supplyKey"),
						cryptoCreate(owner)
								.balance(ONE_HUNDRED_HBARS)
								.maxAutomaticTokenAssociations(10),
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
								.addCryptoAllowance(owner, owner, 100L)
								.fee(ONE_HUNDRED_HBARS).hasPrecheck(ResponseCodeEnum.SPENDER_ACCOUNT_SAME_AS_OWNER),
						cryptoApproveAllowance()
								.payingWith(owner)
								.addTokenAllowance(owner, token, owner, 100L)
								.fee(ONE_HUNDRED_HBARS).hasPrecheck(ResponseCodeEnum.SPENDER_ACCOUNT_SAME_AS_OWNER),
						cryptoApproveAllowance()
								.payingWith(owner)
								.addNftAllowance(owner, nft, owner, false, List.of(1L))
								.fee(ONE_HUNDRED_HBARS).hasPrecheck(ResponseCodeEnum.SPENDER_ACCOUNT_SAME_AS_OWNER)
				)
				.then(
						getAccountInfo(owner).logged()
								.has(accountWith()
										.cryptoAllowancesCount(0)
										.nftAllowancesCount(0)
										.tokenAllowancesCount(0)
								));
	}

	private HapiApiSpec spenderAccountRepeatedFails() {
		final String owner = "owner";
		final String spender = "spender";
		final String token = "token";
		final String nft = "nft";
		return defaultHapiSpec("spenderAccountRepeatedFails")
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
								.addCryptoAllowance(owner, spender, 1000L)
								.fee(ONE_HUNDRED_HBARS)
								.hasPrecheck(SPENDER_ACCOUNT_REPEATED_IN_ALLOWANCES),
						cryptoApproveAllowance()
								.payingWith(owner)
								.addTokenAllowance(owner, token, spender, 100L)
								.addTokenAllowance(owner, token, spender, 1000L)
								.fee(ONE_HUNDRED_HBARS)
								.hasPrecheck(SPENDER_ACCOUNT_REPEATED_IN_ALLOWANCES),
						cryptoApproveAllowance()
								.payingWith(owner)
								.addNftAllowance(owner, nft, spender, false, List.of(1L))
								.addNftAllowance(owner, nft, spender, false, List.of(10L))
								.fee(ONE_HUNDRED_HBARS)
								.hasPrecheck(SPENDER_ACCOUNT_REPEATED_IN_ALLOWANCES)
				)
				.then(
						getAccountInfo(owner).logged()
								.has(accountWith()
										.cryptoAllowancesCount(0)
										.nftAllowancesCount(0)
										.tokenAllowancesCount(0)
								));
	}

	private HapiApiSpec negativeAmountFailsForFungible() {
		final String owner = "owner";
		final String spender = "spender";
		final String token = "token";
		final String nft = "nft";
		return defaultHapiSpec("negativeAmountFailsForFungible")
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
								.addCryptoAllowance(owner, spender, -100L)
								.fee(ONE_HUNDRED_HBARS)
								.hasPrecheck(NEGATIVE_ALLOWANCE_AMOUNT),
						cryptoApproveAllowance()
								.payingWith(owner)
								.addTokenAllowance(owner, token, spender, -100L)
								.fee(ONE_HUNDRED_HBARS)
								.hasPrecheck(NEGATIVE_ALLOWANCE_AMOUNT)
				)
				.then(
						getAccountInfo(owner).logged()
								.has(accountWith()
										.cryptoAllowancesCount(0)
										.nftAllowancesCount(0)
										.tokenAllowancesCount(0)
								));
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
								.fee(ONE_HBAR).logged()
				)
				.then(
						getAccountInfo(owner).logged()
								.has(accountWith()
										.cryptoAllowancesCount(1)
										.nftAllowancesCount(1)
										.tokenAllowancesCount(1)
										.cryptoAllowancesContaining(spender, 100L)
										.tokenAllowancesContaining(token, spender, 100L)
										.nftAllowancesContaining(nft, spender, false, List.of(1L))
								));
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}
