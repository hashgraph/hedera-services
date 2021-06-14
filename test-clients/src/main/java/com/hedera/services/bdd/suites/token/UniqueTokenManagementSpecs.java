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
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenSupplyType;
import com.hederahashgraph.api.proto.java.TokenType;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getReceipt;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTokenInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTokenNftInfo;
import static com.hedera.services.bdd.spec.queries.crypto.ExpectedTokenRel.relationshipWith;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.mintToken;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenDelete;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenFreeze;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FAIL_INVALID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_NFT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_NFT_SERIAL_NUMBER;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TRANSACTION_BODY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_WAS_DELETED;

public class UniqueTokenManagementSpecs extends HapiApiSuite {
	private static final org.apache.logging.log4j.Logger log = LogManager.getLogger(UniqueTokenManagementSpecs.class);
	private static final String TOKEN_NAME = "token";
	private static final String SUPPLY_KEY = "supplyKey";
	private static final int BIGGER_THAN_LIMIT = 11;
	public static void main(String... args) {
		new UniqueTokenManagementSpecs().runSuiteSync();
	}

	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		return List.of(new HapiApiSpec[]{
						getTokenNftInfoWorks(),
						uniqueTokenHappyPath(),
						failsWithFrozenToken(),
						failsWithDeletedToken(),
						failsWithRepeatedMetadata(),
						failsWithLargeBatchSize(),
						failsWithTooLongMetadata(),
						failsWithInvalidMetadataFromBatch(),
						happyPathWithBatchMetadata(),
						happyPathMintMultipleWithIdenticalMetadata(),
				}
		);
	}

	private HapiApiSpec failsWithTooLongMetadata() {
		return defaultHapiSpec("failsWithTooLongMetadata")
				.given(
						newKeyNamed(SUPPLY_KEY),
						cryptoCreate(TOKEN_TREASURY),
						tokenCreate(TOKEN_NAME)
								.tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
								.supplyType(TokenSupplyType.INFINITE)
								.initialSupply(0)
								.supplyKey(SUPPLY_KEY)
								.treasury(TOKEN_TREASURY)
				).when().then(
						mintToken(TOKEN_NAME, List.of(
								metadataOfLength(101)
						)).hasPrecheck(ResponseCodeEnum.METADATA_TOO_LONG)
				);
	}

	private HapiApiSpec failsWithInvalidMetadataFromBatch() {
		return defaultHapiSpec("failsWithInvalidMetadataFromBatch")
				.given(
						newKeyNamed(SUPPLY_KEY),
						cryptoCreate(TOKEN_TREASURY),
						tokenCreate(TOKEN_NAME)
								.tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
								.supplyType(TokenSupplyType.INFINITE)
								.initialSupply(0)
								.supplyKey(SUPPLY_KEY)
								.treasury(TOKEN_TREASURY)
				).when().then(
						mintToken(TOKEN_NAME, List.of(
								metadataOfLength(101),
								metadataOfLength(1)
						)).hasPrecheck(ResponseCodeEnum.METADATA_TOO_LONG)
				);
	}

	private HapiApiSpec failsWithLargeBatchSize() {
		return defaultHapiSpec("failsWithLargeBatchSize")
				.given(
						newKeyNamed(SUPPLY_KEY),
						cryptoCreate(TOKEN_TREASURY),
						tokenCreate(TOKEN_NAME)
								.tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
								.supplyType(TokenSupplyType.INFINITE)
								.initialSupply(0)
								.supplyKey(SUPPLY_KEY)
								.treasury(TOKEN_TREASURY)
				).when().then(
						mintToken(TOKEN_NAME, batchOfSize(BIGGER_THAN_LIMIT))
								.hasPrecheck(ResponseCodeEnum.BATCH_SIZE_LIMIT_EXCEEDED)
				);
	}

	private List<ByteString> batchOfSize(int size) {
		var batch = new ArrayList<ByteString>();
		for (int i = 0; i < size; i++) {
			 batch.add(metadata("memo" + i));
		}
		return batch;
	}

	private ByteString metadataOfLength(int length) {
		return ByteString.copyFrom(genRandomBytes(length));
	}

	private ByteString metadata(String contents) {
		return ByteString.copyFromUtf8(contents);
	}

	private HapiApiSpec uniqueTokenHappyPath() {
		return defaultHapiSpec("UniqueTokenHappyPath")
				.given(
						newKeyNamed(SUPPLY_KEY),
						cryptoCreate(TOKEN_TREASURY),
						tokenCreate(TOKEN_NAME)
								.tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
								.supplyType(TokenSupplyType.INFINITE)
								.initialSupply(0)
								.supplyKey(SUPPLY_KEY)
								.treasury(TOKEN_TREASURY)
				).when(
						mintToken(TOKEN_NAME,
								List.of(metadata("memo"), metadata("memo1"))).via("mintTxn")
				).then(
						getReceipt("mintTxn")
								.hasPriorityStatus(SUCCESS)
								.hasCostAnswerPrecheck(SUCCESS),

						getTokenNftInfo(TOKEN_NAME, 1)
								.hasSerialNum(1)
								.hasMetadata(metadata("memo"))
								.hasTokenID(TOKEN_NAME)
								.hasAccountID(TOKEN_TREASURY),

						getTokenNftInfo(TOKEN_NAME, 2)
								.hasSerialNum(2)
								.hasMetadata(metadata("memo1"))
								.hasTokenID(TOKEN_NAME)
								.hasAccountID(TOKEN_TREASURY),

						getTokenNftInfo(TOKEN_NAME, 3)
								.hasCostAnswerPrecheck(INVALID_NFT_ID),

						getAccountBalance(TOKEN_TREASURY)
								.hasTokenBalance(TOKEN_NAME, 2),
						getTokenInfo(TOKEN_NAME)
								.hasTreasury(TOKEN_TREASURY)
								.hasTotalSupply(0),

						getAccountInfo(TOKEN_TREASURY)
								.hasToken(relationshipWith(TOKEN_NAME))
				);
	}

	private HapiApiSpec happyPathWithBatchMetadata() {
		return defaultHapiSpec("happyPathWithBatchMetadata")
				.given(
						newKeyNamed(SUPPLY_KEY),
						cryptoCreate(TOKEN_TREASURY),
						tokenCreate(TOKEN_NAME)
								.tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
								.supplyType(TokenSupplyType.FINITE)
								.supplyKey(SUPPLY_KEY)
								.initialSupply(0)
								.maxSupply(100000)
								.memo("memo")
								.treasury(TOKEN_TREASURY)
				).when(
						mintToken(TOKEN_NAME, List.of(
								metadata("memo"),
								metadata("memo1"),
								metadata("memo2"),
								metadata("memo3"),
								metadata("memo4")
						))
				).then(
						getTokenNftInfo(TOKEN_NAME, 1)
								.hasSerialNum(1)
								.hasMetadata(metadata("memo")),
						getTokenNftInfo(TOKEN_NAME, 2)
								.hasSerialNum(2)
								.hasMetadata(metadata("memo1")),
						getTokenNftInfo(TOKEN_NAME, 3)
								.hasSerialNum(3)
								.hasMetadata(metadata("memo2")),
						getTokenNftInfo(TOKEN_NAME, 4)
								.hasSerialNum(4)
								.hasMetadata(metadata("memo3")),

						getTokenNftInfo(TOKEN_NAME, 5).hasSerialNum(5)
								.hasMetadata(metadata("memo4"))
								.hasTokenID(TOKEN_NAME)
								.hasAccountID(TOKEN_TREASURY),

						getAccountInfo(TOKEN_TREASURY)
								.hasToken(relationshipWith(TOKEN_NAME))
				);
	}

	private HapiApiSpec happyPathMintMultipleWithIdenticalMetadata() {
		return defaultHapiSpec("happyPathMintMultipleWithIdenticalMetadata")
				.given(
						newKeyNamed(SUPPLY_KEY),
						cryptoCreate(TOKEN_TREASURY),
						tokenCreate(TOKEN_NAME)
								.tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
								.supplyType(TokenSupplyType.FINITE)
								.supplyKey(SUPPLY_KEY)
								.initialSupply(0)
								.maxSupply(100000)
								.memo("memo")
								.treasury(TOKEN_TREASURY)
				).when(
						mintToken(TOKEN_NAME, List.of(metadata("memo"))),
						mintToken(TOKEN_NAME, List.of(metadata("memo"))),
						mintToken(TOKEN_NAME, List.of(metadata("memo"))),
						mintToken(TOKEN_NAME, List.of(metadata("memo"))),
						mintToken(TOKEN_NAME, List.of(metadata("memo")))

				).then(
						getTokenNftInfo(TOKEN_NAME, 1)
								.hasSerialNum(1)
								.hasMetadata(metadata("memo"))
								.hasAccountID(TOKEN_TREASURY)
								.hasTokenID(TOKEN_NAME),

						getTokenNftInfo(TOKEN_NAME, 2)
								.hasSerialNum(2)
								.hasMetadata(metadata("memo"))
								.hasAccountID(TOKEN_TREASURY)
								.hasTokenID(TOKEN_NAME),

						getTokenNftInfo(TOKEN_NAME, 3)
								.hasSerialNum(3)
								.hasMetadata(metadata("memo"))
								.hasAccountID(TOKEN_TREASURY)
								.hasTokenID(TOKEN_NAME),

						getTokenNftInfo(TOKEN_NAME, 4)
								.hasSerialNum(4)
								.hasMetadata(metadata("memo"))
								.hasAccountID(TOKEN_TREASURY)
								.hasTokenID(TOKEN_NAME),

						getTokenNftInfo(TOKEN_NAME, 5)
								.hasSerialNum(5)
								.hasMetadata(metadata("memo"))
								.hasAccountID(TOKEN_TREASURY)
								.hasTokenID(TOKEN_NAME),

						getAccountInfo(TOKEN_TREASURY)
								.hasToken(relationshipWith(TOKEN_NAME))
				);
	}

	private HapiApiSpec failsWithFrozenToken() {
		return defaultHapiSpec("failsWithFrozenToken")
				.given(
						newKeyNamed(SUPPLY_KEY),
						newKeyNamed("tokenFreezeKey"),
						cryptoCreate(TOKEN_TREASURY).balance(0L),
						tokenCreate(TOKEN_NAME)
								.tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
								.supplyType(TokenSupplyType.INFINITE)
								.initialSupply(0)
								.supplyKey(SUPPLY_KEY)
								.freezeKey("tokenFreezeKey")
								.treasury(TOKEN_TREASURY)
				).when(
						tokenFreeze(TOKEN_NAME, TOKEN_TREASURY)
				).then(
						mintToken(TOKEN_NAME, List.of(metadata("memo"))).via("mintTxn")
								.hasKnownStatus(ResponseCodeEnum.ACCOUNT_FROZEN_FOR_TOKEN),
						getTokenNftInfo(TOKEN_NAME, 1).hasCostAnswerPrecheck(INVALID_NFT_ID)
				);
	}

	private HapiApiSpec failsWithDeletedToken() {
		return defaultHapiSpec("failsWithDeletedToken").given(
				newKeyNamed(SUPPLY_KEY),
				newKeyNamed("adminKey"),
				cryptoCreate(TOKEN_TREASURY),
				tokenCreate(TOKEN_NAME)
						.supplyKey(SUPPLY_KEY)
						.adminKey("adminKey")
						.treasury(TOKEN_TREASURY)
		).when(
				tokenDelete(TOKEN_NAME)
		).then(
				mintToken(TOKEN_NAME, List.of(metadata("memo")))
						.via("mintTxn")
						.hasKnownStatus(TOKEN_WAS_DELETED),
				getTokenNftInfo(TOKEN_NAME, 1).hasCostAnswerPrecheck(INVALID_NFT_ID),
				getTokenInfo(TOKEN_NAME).isDeleted()
		);
	}

	private HapiApiSpec getTokenNftInfoWorks() {
		return defaultHapiSpec("getTokenNftInfoWorks")
				.given(
						newKeyNamed(SUPPLY_KEY),
						cryptoCreate(TOKEN_TREASURY)
				).when(
						tokenCreate(TOKEN_NAME)
								.tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
								.supplyType(TokenSupplyType.FINITE)
								.supplyKey(SUPPLY_KEY)
								.initialSupply(0)
								.maxSupply(100)
								.treasury(TOKEN_TREASURY),
						mintToken(TOKEN_NAME, List.of(metadata("memo")))
				).then(
						getTokenNftInfo(TOKEN_NAME, 0)
								.hasCostAnswerPrecheck(INVALID_TOKEN_NFT_SERIAL_NUMBER),
						getTokenNftInfo(TOKEN_NAME, -1)
								.hasCostAnswerPrecheck(INVALID_TOKEN_NFT_SERIAL_NUMBER),
						getTokenNftInfo(TOKEN_NAME, 2)
								.hasCostAnswerPrecheck(INVALID_NFT_ID),
						getTokenNftInfo(TOKEN_NAME, 1)
								.hasTokenID(TOKEN_NAME)
								.hasAccountID(TOKEN_TREASURY)
								.hasMetadata(metadata("memo"))
								.hasSerialNum(1)
				);
	}

	private HapiApiSpec failsWithRepeatedMetadata() {
		return defaultHapiSpec("failsWithRepeatedMetadata")
				.given(
						newKeyNamed(SUPPLY_KEY),
						cryptoCreate(TOKEN_TREASURY),
						tokenCreate(TOKEN_NAME)
								.tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
								.supplyType(TokenSupplyType.INFINITE)
								.initialSupply(0)
								.supplyKey(SUPPLY_KEY)
								.treasury(TOKEN_TREASURY)
				).when(
				).then(
						mintToken(TOKEN_NAME, List.of(metadata("memo"), metadata("memo")))
								.via("mintTxn")
								.hasKnownStatus(INVALID_TRANSACTION_BODY),

						getReceipt("mintTxn")
								.hasCostAnswerPrecheck(INVALID_TRANSACTION_BODY)
								.hasPriorityStatus(FAIL_INVALID)
				);
	}

	protected Logger getResultsLogger() {
		return log;
	}

	private byte[] genRandomBytes(int numBytes) {
		byte[] contents = new byte[numBytes];
		(new Random()).nextBytes(contents);
		return contents;
	}
}

