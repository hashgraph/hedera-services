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

import java.util.List;

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
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenFreeze;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_FROZEN_FOR_TOKEN;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FAIL_INVALID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_NFT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_NFT_SERIAL_NUMBER;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TRANSACTION_BODY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_HAS_NO_SUPPLY_KEY;

public class UniqueTokenManagementSpecs extends HapiApiSuite {
	private static final org.apache.logging.log4j.Logger log = LogManager.getLogger(UniqueTokenManagementSpecs.class);
	private static final String TOKEN_NAME = "token";

	public static void main(String... args) {
		new UniqueTokenManagementSpecs().runSuiteSync();
	}

	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		return List.of(new HapiApiSpec[]{
						getTokenNftInfoWork(),
						uniqueTokenHappyPath(),
//						sadPathWithNoSupplyKey(),
//						sadPathWithFrozenToken(),
						sadPathWithRepeatedMetadata(),
						happyPathCallingMintOnceWithFiveMetadata(),
						happyPathCallMintFiveTimesWithOneMetadata(),
				}
		);
	}


	private HapiApiSpec uniqueTokenHappyPath() {
		return defaultHapiSpec("UniqueTokenHappyPath")
				.given(
						newKeyNamed("supplyKey"),
						cryptoCreate(TOKEN_TREASURY),
						tokenCreate(TOKEN_NAME)
								.tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
								.supplyType(TokenSupplyType.INFINITE)
								.initialSupply(0)
								.supplyKey("supplyKey")
								.treasury(TOKEN_TREASURY)
				).when(
						mintToken(TOKEN_NAME, List.of(ByteString.copyFromUtf8("memo"), ByteString.copyFromUtf8("memo1"))).via("mintTxn")
				).then(
						getReceipt("mintTxn")
								.hasPriorityStatus(SUCCESS)
								.hasCostAnswerPrecheck(SUCCESS),

						getTokenNftInfo(TOKEN_NAME, 1)
								.hasSerialNum(1)
								.hasMetadata(ByteString.copyFromUtf8("memo"))
								.hasTokenID(TOKEN_NAME)
								.hasAccountID(TOKEN_TREASURY),

						getTokenNftInfo(TOKEN_NAME, 2)
								.hasSerialNum(2)
								.hasMetadata(ByteString.copyFromUtf8("memo1"))
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

	private HapiApiSpec happyPathCallingMintOnceWithFiveMetadata() {
		return defaultHapiSpec("happyPathCallingMintOnceWithFiveMetadata")
				.given(
						newKeyNamed("supplyKey"),
						cryptoCreate(TOKEN_TREASURY),
						tokenCreate(TOKEN_NAME)
								.tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
								.supplyType(TokenSupplyType.FINITE)
								.supplyKey("supplyKey")
								.initialSupply(0)
								.maxSupply(100000)
								.memo("memo")
								.treasury(TOKEN_TREASURY)
				).when(
						mintToken(TOKEN_NAME, List.of(
								ByteString.copyFromUtf8("memo"),
								ByteString.copyFromUtf8("memo1"),
								ByteString.copyFromUtf8("memo2"),
								ByteString.copyFromUtf8("memo3"),
								ByteString.copyFromUtf8("memo4")
						))
				).then(
						getTokenNftInfo(TOKEN_NAME, 1)
								.hasSerialNum(1)
								.hasMetadata(ByteString.copyFromUtf8("memo")),
						getTokenNftInfo(TOKEN_NAME, 2)
								.hasSerialNum(2)
								.hasMetadata(ByteString.copyFromUtf8("memo1")),
						getTokenNftInfo(TOKEN_NAME, 3)
								.hasSerialNum(3)
								.hasMetadata(ByteString.copyFromUtf8("memo2")),
						getTokenNftInfo(TOKEN_NAME, 4)
								.hasSerialNum(4)
								.hasMetadata(ByteString.copyFromUtf8("memo3")),

						getTokenNftInfo(TOKEN_NAME, 5).hasSerialNum(5)
								.hasMetadata(ByteString.copyFromUtf8("memo4"))
								.hasTokenID(TOKEN_NAME)
								.hasAccountID(TOKEN_TREASURY),

						getAccountInfo(TOKEN_TREASURY)
								.hasToken(relationshipWith(TOKEN_NAME))
				);
	}

	private HapiApiSpec happyPathCallMintFiveTimesWithOneMetadata() {
		return defaultHapiSpec("happyPathCallMintFiveTimesWithOneMetadata")
				.given(
						newKeyNamed("supplyKey"),
						cryptoCreate(TOKEN_TREASURY),
						tokenCreate(TOKEN_NAME)
								.tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
								.supplyType(TokenSupplyType.FINITE)
								.supplyKey("supplyKey")
								.initialSupply(0)
								.maxSupply(100000)
								.memo("memo")
								.treasury(TOKEN_TREASURY)
				).when(
						mintToken(TOKEN_NAME, List.of(ByteString.copyFromUtf8("memo"))),
						mintToken(TOKEN_NAME, List.of(ByteString.copyFromUtf8("memo"))),
						mintToken(TOKEN_NAME, List.of(ByteString.copyFromUtf8("memo"))),
						mintToken(TOKEN_NAME, List.of(ByteString.copyFromUtf8("memo"))),
						mintToken(TOKEN_NAME, List.of(ByteString.copyFromUtf8("memo")))

				).then(
						getTokenNftInfo(TOKEN_NAME, 1)
								.hasSerialNum(1)
								.hasMetadata(ByteString.copyFromUtf8("memo"))
								.hasAccountID(TOKEN_TREASURY)
								.hasTokenID(TOKEN_NAME),

						getTokenNftInfo(TOKEN_NAME, 2)
								.hasSerialNum(2)
								.hasMetadata(ByteString.copyFromUtf8("memo"))
								.hasAccountID(TOKEN_TREASURY)
								.hasTokenID(TOKEN_NAME),

						getTokenNftInfo(TOKEN_NAME, 3)
								.hasSerialNum(3)
								.hasMetadata(ByteString.copyFromUtf8("memo"))
								.hasAccountID(TOKEN_TREASURY)
								.hasTokenID(TOKEN_NAME),

						getTokenNftInfo(TOKEN_NAME, 4)
								.hasSerialNum(4)
								.hasMetadata(ByteString.copyFromUtf8("memo"))
								.hasAccountID(TOKEN_TREASURY)
								.hasTokenID(TOKEN_NAME),

						getTokenNftInfo(TOKEN_NAME, 5)
								.hasSerialNum(5)
								.hasMetadata(ByteString.copyFromUtf8("memo"))
								.hasAccountID(TOKEN_TREASURY)
								.hasTokenID(TOKEN_NAME),

						getAccountInfo(TOKEN_TREASURY)
								.hasToken(relationshipWith(TOKEN_NAME))
				);
	}

	private HapiApiSpec sadPathWithFrozenToken() {
		return defaultHapiSpec("sadPathWithFrozenToken")
				.given(
						newKeyNamed("supplyKey"),
						newKeyNamed("tokenFreezeKey"),
						cryptoCreate(TOKEN_TREASURY).balance(0L),
						tokenCreate(TOKEN_NAME)
								.tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
								.supplyType(TokenSupplyType.INFINITE)
								.initialSupply(0)
								.supplyKey("supplyKey")
								.freezeKey("tokenFreezeKey")
								.treasury(TOKEN_TREASURY)
				).when(
						tokenFreeze(TOKEN_NAME, TOKEN_TREASURY)
				).then(
						mintToken("token", List.of(ByteString.copyFromUtf8("memo"))).via("mintTxn")
								.hasKnownStatus(ResponseCodeEnum.ACCOUNT_FROZEN_FOR_TOKEN),
						getReceipt("mintTxn").hasAnswerOnlyPrecheck(ACCOUNT_FROZEN_FOR_TOKEN),
						getAccountInfo(TOKEN_TREASURY).hasNoTokenRelationship(TOKEN_NAME)
				);
	}

	private HapiApiSpec sadPathWithNoSupplyKey() {
		return defaultHapiSpec("sadPathWithNoSupplyKey").given(
				cryptoCreate(TOKEN_TREASURY),
				tokenCreate(TOKEN_NAME)
						.tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
						.supplyType(TokenSupplyType.INFINITE)
						.initialSupply(0)
						.treasury(TOKEN_TREASURY)
		).when(

		).then(
				mintToken(TOKEN_NAME, List.of(ByteString.copyFromUtf8("memo")))
						.via("mintTxt")
						.hasKnownStatus(TOKEN_HAS_NO_SUPPLY_KEY)
		);
	}

	private HapiApiSpec getTokenNftInfoWork() {

		return defaultHapiSpec("getTokenNftInfoWorks")
				.given(
						newKeyNamed("supplyKey"),
						cryptoCreate(TOKEN_TREASURY)
				).when(
						tokenCreate(TOKEN_NAME)
								.tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
								.supplyType(TokenSupplyType.FINITE)
								.supplyKey("supplyKey")
								.initialSupply(0)
								.maxSupply(100)
								.treasury(TOKEN_TREASURY),
						mintToken(TOKEN_NAME, List.of(ByteString.copyFromUtf8("memo")))
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
								.hasMetadata(ByteString.copyFromUtf8("memo"))
								.hasSerialNum(1)
				);
	}

	private HapiApiSpec sadPathWithRepeatedMetadata() {
		return defaultHapiSpec("sadPathWithRepeatedMetadata")
				.given(
						newKeyNamed("supplyKey"),
						cryptoCreate(TOKEN_TREASURY),
						tokenCreate(TOKEN_NAME)
								.tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
								.supplyType(TokenSupplyType.INFINITE)
								.initialSupply(0)
								.supplyKey("supplyKey")
								.treasury(TOKEN_TREASURY)
				).when(
				).then(
						mintToken(TOKEN_NAME, List.of(ByteString.copyFromUtf8("memo"), ByteString.copyFromUtf8("memo")))
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

}
