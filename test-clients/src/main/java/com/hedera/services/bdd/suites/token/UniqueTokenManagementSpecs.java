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
import com.hedera.services.bdd.spec.transactions.TxnUtils;
import com.hedera.services.bdd.spec.utilops.UtilVerbs;
import com.hedera.services.bdd.suites.HapiApiSuite;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenSupplyType;
import com.hederahashgraph.api.proto.java.TokenType;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.Assert;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountNftInfos;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getReceipt;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTokenInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTokenNftInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTokenNftInfos;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.queries.crypto.ExpectedTokenRel.relationshipWith;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.burnToken;
import static com.hedera.services.bdd.spec.queries.token.HapiTokenNftInfo.newTokenNftInfo;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoDelete;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.mintToken;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenDelete;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_DELETED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.BATCH_SIZE_LIMIT_EXCEEDED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_NFT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_NFT_SERIAL_NUMBER;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NOT_SUPPORTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_QUERY_RANGE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_WAS_DELETED;
import static com.hederahashgraph.api.proto.java.TokenType.NON_FUNGIBLE_UNIQUE;

public class UniqueTokenManagementSpecs extends HapiApiSuite {
	private static final org.apache.logging.log4j.Logger log = LogManager.getLogger(UniqueTokenManagementSpecs.class);
	private static final String A_TOKEN = "TokenA";
	private static final String NFT = "nft";
	private static final String FUNGIBLE_TOKEN = "fungible";
	private static final String SUPPLY_KEY = "supplyKey";
	private static final String FIRST_USER = "Client1";
	private static final int BIGGER_THAN_LIMIT = 11;

	public static void main(String... args) {
		new UniqueTokenManagementSpecs().runSuiteSync();
	}

	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		return List.of(new HapiApiSpec[]{
						getTokenNftInfoWorks(),
						uniqueTokenHappyPath(),
						tokenMintWorksWhenAccountsAreFrozenByDefault(),
						failsWithDeletedToken(),
						happyPathWithRepeatedMetadata(),
						failsGetTokenNftInfoWithNoNft(),
						failsWithDeletedToken(),
						failsWithLargeBatchSize(),
						failsWithTooLongMetadata(),
						failsWithInvalidMetadataFromBatch(),
						distinguishesFeeSubTypes(),
						burnHappyPath(),
						failsOnInvalidSerialNumber(),
						respectsBurnBatchConstraints(),
						treasuryBalanceCorrectAfterBurn(),
						burnWorksWhenAccountsAreFrozenByDefault(),
						uniqueTokenMintReceiptCheck(),
                      	associatesNftAsExpected(),
						failsWithAccountWithoutNfts(),
						validatesQueryOutOfRange(),
						failsWithInvalidQueryBoundaries(),
						getAccountNftsInfoFailsWithDeletedAccount(),
						getAccountNftsInfoFailsWithInexistentAccount(),
						associatesTokenNftInfosAsExpected(),
						validateTokenNftInfosOutOfRange(),
						failsWithTokenWithoutNfts(),
						failsWithTokenNftsInvalidQueryBoundaries(),
						failsWithDeletedTokenNft(),
						failsWithFungibleTokenGetNftInfos()
                }
        );
    }

	private HapiApiSpec associatesNftAsExpected() {
		return defaultHapiSpec("AssociatesNftAsExpected")
				.given(
						newKeyNamed(SUPPLY_KEY),
						cryptoCreate(TOKEN_TREASURY),
						tokenCreate(NFT)
								.initialSupply(0)
								.tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
								.supplyType(TokenSupplyType.INFINITE)
								.supplyKey(SUPPLY_KEY)
								.treasury(TOKEN_TREASURY)
				).when(
						mintToken(NFT, List.of(
								metadata("some metadata"),
								metadata("some metadata2"),
								metadata("some metadata3")
						))
				).then(
						getAccountNftInfos(TOKEN_TREASURY, 0, 2)
								.hasNfts(
										newTokenNftInfo(NFT, 1, TOKEN_TREASURY, metadata("some metadata")),
										newTokenNftInfo(NFT, 2, TOKEN_TREASURY, metadata("some metadata2"))
								)
								.logged()
				);
	}

	private HapiApiSpec validatesQueryOutOfRange() {
		return defaultHapiSpec("ValidatesQueryOutOfRange")
				.given(
						newKeyNamed(SUPPLY_KEY),
						cryptoCreate(TOKEN_TREASURY),
						tokenCreate(NFT)
								.initialSupply(0)
								.tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
								.supplyType(TokenSupplyType.INFINITE)
								.supplyKey(SUPPLY_KEY)
								.treasury(TOKEN_TREASURY)
				).when(
						mintToken(NFT, List.of(
								metadata("some metadata"),
								metadata("some metadata2"),
								metadata("some metadata3")
						))
				).then(
						getAccountNftInfos(TOKEN_TREASURY, 0, 6)
								.hasCostAnswerPrecheck(INVALID_QUERY_RANGE)
				);
	}

	private HapiApiSpec failsWithAccountWithoutNfts() {
		return defaultHapiSpec("FailsWithAccountWithoutNfts")
				.given(
						cryptoCreate(FIRST_USER)
				).when().then(
						getAccountNftInfos(FIRST_USER, 0, 2)
								.hasCostAnswerPrecheck(INVALID_QUERY_RANGE)
				);
	}

	private HapiApiSpec getAccountNftsInfoFailsWithDeletedAccount() {
		return defaultHapiSpec("GetAccountNftsInfoFailsWithDeletedAccount")
				.given(
						cryptoCreate(FIRST_USER),
						cryptoDelete(FIRST_USER)
				).when().then(
						getAccountNftInfos(FIRST_USER, 0, 2)
								.hasCostAnswerPrecheck(ACCOUNT_DELETED)
				);
	}

	private HapiApiSpec getAccountNftsInfoFailsWithInexistentAccount() {
		return defaultHapiSpec("GetAccountNftsInfoFailsWithInexistentAccount")
				.given().when().then(
						getAccountNftInfos("0.0.123", 0, 2)
								.hasCostAnswerPrecheck(INVALID_ACCOUNT_ID)
				);
	}

	private HapiApiSpec failsWithInvalidQueryBoundaries() {
		return defaultHapiSpec("FailsWithInvalidQueryBoundaries")
				.given(
						cryptoCreate(FIRST_USER)
				).when().then(
						getAccountNftInfos(FIRST_USER, 2, 0)
								.hasCostAnswerPrecheck(INVALID_QUERY_RANGE),
						getAccountNftInfos(FIRST_USER, 0, 100000000)
								.hasCostAnswerPrecheck(INVALID_QUERY_RANGE)
				);
	}

	private HapiApiSpec burnWorksWhenAccountsAreFrozenByDefault() {
		return defaultHapiSpec("burnWorksWhenAccountsAreFrozenByDefault")
				.given(
						newKeyNamed(SUPPLY_KEY),
						cryptoCreate(TOKEN_TREASURY),
						tokenCreate(NFT)
								.tokenType(NON_FUNGIBLE_UNIQUE)
								.supplyType(TokenSupplyType.INFINITE)
								.initialSupply(0)
								.supplyKey(SUPPLY_KEY)
								.treasury(TOKEN_TREASURY),
						mintToken(NFT, List.of(metadata("memo")))
				)
				.when(
						burnToken(NFT, List.of(1L)).via("burnTxn").logged()
				)
				.then(
						getTxnRecord("burnTxn")
								.hasCostAnswerPrecheck(OK),
						getTokenNftInfo(NFT, 1)
								.hasCostAnswerPrecheck(INVALID_NFT_ID),
						getTokenNftInfos(NFT, 0, 1).hasCostAnswerPrecheck(INVALID_QUERY_RANGE),
						getAccountInfo(TOKEN_TREASURY).hasOwnedNfts(0),
						getAccountNftInfos(TOKEN_TREASURY, 0, 1).hasCostAnswerPrecheck(INVALID_QUERY_RANGE)
				);
	}

	private HapiApiSpec failsOnInvalidSerialNumber() {
		return defaultHapiSpec("failsOnInvalidSerialNumber")
				.given(
						newKeyNamed(SUPPLY_KEY),
						cryptoCreate(TOKEN_TREASURY),
						tokenCreate(NFT)
								.tokenType(NON_FUNGIBLE_UNIQUE)
								.supplyType(TokenSupplyType.INFINITE)
								.initialSupply(0)
								.supplyKey(SUPPLY_KEY)
								.treasury(TOKEN_TREASURY),
						mintToken(NFT, List.of(metadata("memo"))))
				.when()
				.then(
						burnToken(NFT, List.of(0L, 1L, 2L)).via("burnTxn").hasPrecheck(INVALID_NFT_ID),
						getAccountInfo(TOKEN_TREASURY).hasOwnedNfts(1),
						getAccountNftInfos(TOKEN_TREASURY, 0, 1)
								.hasNfts(
										newTokenNftInfo(NFT, 1, TOKEN_TREASURY, metadata("memo"))
								)
								.logged()
				);
	}

	private HapiApiSpec respectsBurnBatchConstraints() {
		return defaultHapiSpec("respectsBurnBatchConstraints")
				.given(
						newKeyNamed(SUPPLY_KEY),
						cryptoCreate(TOKEN_TREASURY),
						tokenCreate(NFT)
								.tokenType(NON_FUNGIBLE_UNIQUE)
								.supplyType(TokenSupplyType.INFINITE)
								.initialSupply(0)
								.supplyKey(SUPPLY_KEY)
								.treasury(TOKEN_TREASURY),
						mintToken(NFT, List.of(metadata("memo"))))
				.when(
				)
				.then(
						burnToken(NFT, LongStream.range(0, 1000).boxed().collect(Collectors.toList())).via("burnTxn")
								.hasPrecheck(BATCH_SIZE_LIMIT_EXCEEDED)
				);
	}

	private HapiApiSpec burnHappyPath() {
		return defaultHapiSpec("burnHappyEnd")
				.given(
						newKeyNamed(SUPPLY_KEY),
						cryptoCreate(TOKEN_TREASURY),
						tokenCreate(NFT)
								.tokenType(NON_FUNGIBLE_UNIQUE)
								.supplyType(TokenSupplyType.INFINITE)
								.initialSupply(0)
								.supplyKey(SUPPLY_KEY)
								.treasury(TOKEN_TREASURY),
						mintToken(NFT, List.of(metadata("memo")))
				).when(
						burnToken(NFT, List.of(1L)).via("burnTxn")
				).then(
						getTokenNftInfo(NFT, 1)
								.hasCostAnswerPrecheck(INVALID_NFT_ID),
						getTokenInfo(NFT)
								.hasTotalSupply(0),
						getAccountBalance(TOKEN_TREASURY)
								.hasTokenBalance(NFT, 0),
						getAccountInfo(TOKEN_TREASURY).hasToken(relationshipWith(NFT)).hasOwnedNfts(0)
				);
	}

	private HapiApiSpec treasuryBalanceCorrectAfterBurn() {
		return defaultHapiSpec("burnsExactGivenTokens")
				.given(
						newKeyNamed(SUPPLY_KEY),
						cryptoCreate(TOKEN_TREASURY),
						tokenCreate(NFT)
								.tokenType(NON_FUNGIBLE_UNIQUE)
								.supplyType(TokenSupplyType.INFINITE)
								.initialSupply(0)
								.supplyKey(SUPPLY_KEY)
								.treasury(TOKEN_TREASURY),
						mintToken(NFT, List.of(metadata("1"), metadata("2"), metadata("3"), metadata("4"), metadata("5")))
				)
				.when(
						burnToken(NFT, List.of(3L, 4L, 5L)).via("burnTxn")
				)
				.then(
						getTokenNftInfo(NFT, 1)
								.hasSerialNum(1)
								.hasCostAnswerPrecheck(OK),
						getTokenNftInfo(NFT, 2)
								.hasSerialNum(2)
								.hasCostAnswerPrecheck(OK),
						getTokenNftInfo(NFT, 3)
								.hasCostAnswerPrecheck(INVALID_NFT_ID),
						getTokenNftInfo(NFT, 4)
								.hasCostAnswerPrecheck(INVALID_NFT_ID),
						getTokenNftInfo(NFT, 5)
								.hasCostAnswerPrecheck(INVALID_NFT_ID),
						getTokenInfo(NFT)
								.hasTotalSupply(2),
						getAccountBalance(TOKEN_TREASURY)
								.hasTokenBalance(NFT, 2),
						getAccountInfo(TOKEN_TREASURY)
								.hasOwnedNfts(2),
						getTokenNftInfos(NFT, 0, 2)
								.hasNfts(
										newTokenNftInfo(NFT, 1, TOKEN_TREASURY, metadata("1")),
										newTokenNftInfo(NFT, 2, TOKEN_TREASURY, metadata("2"))
								)
								.logged(),
						getAccountNftInfos(TOKEN_TREASURY, 0, 2)
								.hasNfts(
										newTokenNftInfo(NFT, 1, TOKEN_TREASURY, metadata("1")),
										newTokenNftInfo(NFT, 2, TOKEN_TREASURY, metadata("2"))
								)
								.logged()
				);
	}

	private HapiApiSpec distinguishesFeeSubTypes() {
		return defaultHapiSpec("happyPathFiveMintOneMetadata")
				.given(
						newKeyNamed(SUPPLY_KEY),
						cryptoCreate(TOKEN_TREASURY),
						cryptoCreate("customPayer"),
						tokenCreate(NFT)
								.tokenType(NON_FUNGIBLE_UNIQUE)
								.supplyType(TokenSupplyType.INFINITE)
								.initialSupply(0)
								.supplyKey(SUPPLY_KEY)
								.treasury(TOKEN_TREASURY),
						tokenCreate(FUNGIBLE_TOKEN)
								.tokenType(TokenType.FUNGIBLE_COMMON)
								.supplyType(TokenSupplyType.FINITE)
								.initialSupply(10)
								.maxSupply(1100)
								.supplyKey(SUPPLY_KEY)
								.treasury(TOKEN_TREASURY)
				).when(
						mintToken(NFT, List.of(metadata("memo"))).payingWith("customPayer").signedBy("customPayer", "supplyKey").via("mintNFT"),
						mintToken(FUNGIBLE_TOKEN, 100L).payingWith("customPayer").signedBy("customPayer", "supplyKey").via("mintFungible")
				).then(
						UtilVerbs.withOpContext((spec, opLog) -> {
							var mintNFT = getTxnRecord("mintNFT");
							var mintFungible = getTxnRecord("mintFungible");
							allRunFor(spec, mintNFT, mintFungible);
							var nftFee = mintNFT.getResponseRecord().getTransactionFee();
							var fungibleFee = mintFungible.getResponseRecord().getTransactionFee();
							Assert.assertNotEquals(
									"NFT Fee should NOT equal to the Fungible Fee!",
									nftFee,
									fungibleFee);
						})
				);
	}

	private HapiApiSpec failsWithTooLongMetadata() {
		return defaultHapiSpec("failsWithTooLongMetadata")
				.given(
						newKeyNamed(SUPPLY_KEY),
						cryptoCreate(TOKEN_TREASURY),
						tokenCreate(NFT)
								.tokenType(NON_FUNGIBLE_UNIQUE)
								.supplyType(TokenSupplyType.INFINITE)
								.initialSupply(0)
								.supplyKey(SUPPLY_KEY)
								.treasury(TOKEN_TREASURY)
				).when().then(
						mintToken(NFT, List.of(
								metadataOfLength(101)
						)).hasPrecheck(ResponseCodeEnum.METADATA_TOO_LONG)
				);
	}

	private HapiApiSpec failsWithInvalidMetadataFromBatch() {
		return defaultHapiSpec("failsWithInvalidMetadataFromBatch")
				.given(
						newKeyNamed(SUPPLY_KEY),
						cryptoCreate(TOKEN_TREASURY),
						tokenCreate(NFT)
								.tokenType(NON_FUNGIBLE_UNIQUE)
								.supplyType(TokenSupplyType.INFINITE)
								.initialSupply(0)
								.supplyKey(SUPPLY_KEY)
								.treasury(TOKEN_TREASURY)
				).when().then(
						mintToken(NFT, List.of(
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
						tokenCreate(NFT)
								.tokenType(NON_FUNGIBLE_UNIQUE)
								.supplyType(TokenSupplyType.INFINITE)
								.initialSupply(0)
								.supplyKey(SUPPLY_KEY)
								.treasury(TOKEN_TREASURY)
				).when().then(
						mintToken(NFT, batchOfSize(BIGGER_THAN_LIMIT))
								.hasPrecheck(BATCH_SIZE_LIMIT_EXCEEDED)
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
						tokenCreate(NFT)
								.tokenType(NON_FUNGIBLE_UNIQUE)
								.supplyKey(SUPPLY_KEY)
								.supplyType(TokenSupplyType.INFINITE)
								.initialSupply(0)
								.supplyKey(SUPPLY_KEY)
								.treasury(TOKEN_TREASURY)
				).when(
						mintToken(NFT,
								List.of(metadata("memo"), metadata("memo1"))).via("mintTxn")
				).then(
						getReceipt("mintTxn").logged(),
						getTokenNftInfo(NFT, 1)
								.hasSerialNum(1)
								.hasMetadata(metadata("memo"))
								.hasTokenID(NFT)
								.hasAccountID(TOKEN_TREASURY)
								.hasValidCreationTime(),

						getTokenNftInfo(NFT, 2)
								.hasSerialNum(2)
								.hasMetadata(metadata("memo1"))
								.hasTokenID(NFT)
								.hasAccountID(TOKEN_TREASURY)
								.hasValidCreationTime(),

						getTokenNftInfo(NFT, 3)
								.hasCostAnswerPrecheck(INVALID_NFT_ID),

						getTokenNftInfos(NFT, 0, 1)
								.hasNfts(
										newTokenNftInfo(NFT, 1, TOKEN_TREASURY, metadata("memo"))
								)
								.logged(),

						getTokenNftInfos(NFT, 1, 2)
								.hasNfts(
										newTokenNftInfo(NFT, 2, TOKEN_TREASURY, metadata("memo1"))
								)
								.logged(),

						getTokenNftInfos(NFT, 0, 2)
								.hasNfts(
										newTokenNftInfo(NFT, 1, TOKEN_TREASURY, metadata("memo")),
										newTokenNftInfo(NFT, 2, TOKEN_TREASURY, metadata("memo1"))
								)
								.logged(),

						getAccountBalance(TOKEN_TREASURY)
								.hasTokenBalance(NFT, 2),

						getTokenInfo(NFT)
								.hasTreasury(TOKEN_TREASURY)
								.hasTotalSupply(2),

						getAccountInfo(TOKEN_TREASURY)
								.hasToken(relationshipWith(NFT)).hasOwnedNfts(2),

						getAccountNftInfos(TOKEN_TREASURY, 0, 2)
								.hasNfts(
										newTokenNftInfo(NFT, 1, TOKEN_TREASURY, metadata("memo")),
										newTokenNftInfo(NFT, 2, TOKEN_TREASURY, metadata("memo1"))
								)
								.logged()
				);
	}

	private HapiApiSpec tokenMintWorksWhenAccountsAreFrozenByDefault() {
		return defaultHapiSpec("happyPathWithFrozenToken")
				.given(
						newKeyNamed(SUPPLY_KEY),
						newKeyNamed("tokenFreezeKey"),
						cryptoCreate(TOKEN_TREASURY).balance(0L),
						tokenCreate(NFT)
								.tokenType(NON_FUNGIBLE_UNIQUE)
								.supplyKey(SUPPLY_KEY)
								.freezeKey("tokenFreezeKey")
								.freezeDefault(true)
								.initialSupply(0)
								.treasury(TOKEN_TREASURY)
				).when(
						mintToken(NFT, List.of(metadata("memo")))
								.via("mintTxn")
				).then(
						getTokenNftInfo(NFT, 1)
								.hasTokenID(NFT)
								.hasAccountID(TOKEN_TREASURY)
								.hasMetadata(metadata("memo"))
								.hasValidCreationTime(),

						getTokenNftInfos(NFT, 0, 1)
								.hasNfts(
										newTokenNftInfo(NFT, 1, TOKEN_TREASURY, metadata("memo"))
								)
								.logged(),

						getAccountInfo(TOKEN_TREASURY).hasOwnedNfts(1),

						getAccountNftInfos(TOKEN_TREASURY, 0, 1)
								.hasNfts(
										newTokenNftInfo(NFT, 1, TOKEN_TREASURY, metadata("memo"))
								)
								.logged()
				);
	}

	private HapiApiSpec failsWithDeletedToken() {
		return defaultHapiSpec("failsWithDeletedToken").given(
				newKeyNamed(SUPPLY_KEY),
				newKeyNamed("adminKey"),
				cryptoCreate(TOKEN_TREASURY),
				tokenCreate(NFT)
						.supplyKey(SUPPLY_KEY)
						.adminKey("adminKey")
						.treasury(TOKEN_TREASURY)
		).when(
				tokenDelete(NFT)
		).then(
				mintToken(NFT, List.of(metadata("memo")))
						.via("mintTxn")
						.hasKnownStatus(TOKEN_WAS_DELETED),

				getTokenNftInfo(NFT, 1)
						.hasCostAnswerPrecheck(INVALID_NFT_ID),

				getTokenInfo(NFT)
						.isDeleted()
		);
	}

	private HapiApiSpec failsGetTokenNftInfoWithNoNft() {
		return defaultHapiSpec("failsGetTokenNftInfoWithNoNft")
				.given(
						newKeyNamed(SUPPLY_KEY),
						cryptoCreate(TOKEN_TREASURY)
				)
				.when(
						tokenCreate(NFT)
								.tokenType(NON_FUNGIBLE_UNIQUE)
								.supplyType(TokenSupplyType.INFINITE)
								.supplyKey(SUPPLY_KEY)
								.initialSupply(0)
								.treasury(TOKEN_TREASURY),
						mintToken(NFT, List.of(metadata("memo"))).via("mintTxn")
				)
				.then(
						getTokenNftInfo(NFT, 0)
								.hasCostAnswerPrecheck(INVALID_TOKEN_NFT_SERIAL_NUMBER),
						getTokenNftInfo(NFT, -1)
								.hasCostAnswerPrecheck(INVALID_TOKEN_NFT_SERIAL_NUMBER),
						getTokenNftInfo(NFT, 2)
								.hasCostAnswerPrecheck(INVALID_NFT_ID)
				);
	}

	private HapiApiSpec getTokenNftInfoWorks() {
		return defaultHapiSpec("getTokenNftInfoWorks")
				.given(
						newKeyNamed(SUPPLY_KEY),
						cryptoCreate(TOKEN_TREASURY)
				).when(
						tokenCreate(NFT)
								.tokenType(NON_FUNGIBLE_UNIQUE)
								.supplyType(TokenSupplyType.INFINITE)
								.supplyKey(SUPPLY_KEY)
								.initialSupply(0)
								.treasury(TOKEN_TREASURY),
						mintToken(NFT, List.of(metadata("memo")))
				).then(
						getTokenNftInfo(NFT, 0)
								.hasCostAnswerPrecheck(INVALID_TOKEN_NFT_SERIAL_NUMBER),
						getTokenNftInfo(NFT, -1)
								.hasCostAnswerPrecheck(INVALID_TOKEN_NFT_SERIAL_NUMBER),
						getTokenNftInfo(NFT, 2)
								.hasCostAnswerPrecheck(INVALID_NFT_ID),
						getTokenNftInfo(NFT, 1)
								.hasTokenID(NFT)
								.hasAccountID(TOKEN_TREASURY)
								.hasMetadata(metadata("memo"))
								.hasSerialNum(1)
								.hasValidCreationTime()
				);
	}

	private HapiApiSpec happyPathWithRepeatedMetadata() {
		return defaultHapiSpec("happyPathWithRepeatedMetadata")
				.given(
						newKeyNamed(SUPPLY_KEY),
						cryptoCreate(TOKEN_TREASURY),
						tokenCreate(NFT)
								.tokenType(NON_FUNGIBLE_UNIQUE)
								.supplyType(TokenSupplyType.INFINITE)
								.supplyKey(SUPPLY_KEY)
								.initialSupply(0)
								.treasury(TOKEN_TREASURY)
				).when(
						mintToken(NFT, List.of(metadata("memo"), metadata("memo")))
								.via("mintTxn")
				).then(
						getTokenNftInfo(NFT, 1)
								.hasSerialNum(1)
								.hasMetadata(metadata("memo"))
								.hasAccountID(TOKEN_TREASURY)
								.hasTokenID(NFT)
								.hasValidCreationTime(),

						getTokenNftInfo(NFT, 2)
								.hasSerialNum(2)
								.hasMetadata(metadata("memo"))
								.hasAccountID(TOKEN_TREASURY)
								.hasTokenID(NFT)
								.hasValidCreationTime(),

						getTokenNftInfos(NFT, 0, 2)
								.hasNfts(
										newTokenNftInfo(NFT, 1, TOKEN_TREASURY, metadata("memo")),
										newTokenNftInfo(NFT, 2, TOKEN_TREASURY, metadata("memo"))
								)
								.logged(),

						getAccountInfo(TOKEN_TREASURY).hasOwnedNfts(2),

						getAccountNftInfos(TOKEN_TREASURY, 0, 2)
								.hasNfts(
										newTokenNftInfo(NFT, 1, TOKEN_TREASURY, metadata("memo")),
										newTokenNftInfo(NFT, 2, TOKEN_TREASURY, metadata("memo"))
								)
								.logged()
				);
	}

	private HapiApiSpec uniqueTokenMintReceiptCheck() {
		return defaultHapiSpec("UniqueTokenMintReceiptCheck")
				.given(
						cryptoCreate(TOKEN_TREASURY),
						cryptoCreate(FIRST_USER),
						newKeyNamed("supplyKey"),
						tokenCreate(A_TOKEN)
								.tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
								.initialSupply(0)
								.supplyKey("supplyKey")
								.treasury(TOKEN_TREASURY)
				)
				.when(
						mintToken(A_TOKEN, List.of(metadata("memo"))).via("mintTransferTxn")
				)
				.then(
						UtilVerbs.withOpContext((spec, opLog) -> {
							var mintNft = getTxnRecord("mintTransferTxn");
							allRunFor(spec, mintNft);
							var tokenTransferLists = mintNft.getResponseRecord().getTokenTransferListsList();
							Assert.assertEquals(1, tokenTransferLists.size());
							tokenTransferLists.stream().forEach(tokenTransferList -> {
								Assert.assertEquals(1, tokenTransferList.getNftTransfersList().size());
								tokenTransferList.getNftTransfersList().stream().forEach(nftTransfers -> {
									Assert.assertEquals(AccountID.getDefaultInstance(), nftTransfers.getSenderAccountID());
									Assert.assertEquals(TxnUtils.asId(TOKEN_TREASURY, spec), nftTransfers.getReceiverAccountID());
									Assert.assertEquals(1L, nftTransfers.getSerialNumber());
								});
							});
						}),
						getTxnRecord("mintTransferTxn").logged(),
						getReceipt("mintTransferTxn").logged()
				);
	}

	private HapiApiSpec associatesTokenNftInfosAsExpected() {
		return defaultHapiSpec("AssociatesTokenNftInfosAsExpected")
				.given(
						newKeyNamed(SUPPLY_KEY),
						cryptoCreate(TOKEN_TREASURY),
						tokenCreate(NFT)
								.initialSupply(0)
								.tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
								.supplyType(TokenSupplyType.INFINITE)
								.supplyKey(SUPPLY_KEY)
								.treasury(TOKEN_TREASURY)
				).when(
						mintToken(NFT, List.of(
								metadata("some metadata"),
								metadata("some metadata2"),
								metadata("some metadata3")
						))
				).then(
						getTokenNftInfos(NFT, 0, 3)
								.hasNfts(
										newTokenNftInfo(NFT, 1, TOKEN_TREASURY, metadata("some metadata")),
										newTokenNftInfo(NFT, 2, TOKEN_TREASURY, metadata("some metadata2")),
										newTokenNftInfo(NFT, 3, TOKEN_TREASURY, metadata("some metadata3"))
								)
								.logged()
				);
	}

	private HapiApiSpec validateTokenNftInfosOutOfRange() {
		return defaultHapiSpec("ValidateTokenNftInfosOutOfRange")
				.given(
						newKeyNamed(SUPPLY_KEY),
						cryptoCreate(TOKEN_TREASURY),
						tokenCreate(NFT)
								.initialSupply(0)
								.tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
								.supplyType(TokenSupplyType.INFINITE)
								.supplyKey(SUPPLY_KEY)
								.treasury(TOKEN_TREASURY)
				).when(
						mintToken(NFT, List.of(
								metadata("some metadata"),
								metadata("some metadata2"),
								metadata("some metadata3")
						))
				).then(
						getTokenNftInfos(NFT, 0, 6)
								.hasCostAnswerPrecheck(INVALID_QUERY_RANGE)
				);
	}

	private HapiApiSpec failsWithTokenWithoutNfts() {
		return defaultHapiSpec("FailsWithTokenWithoutNfts")
				.given(
						newKeyNamed(SUPPLY_KEY),
						cryptoCreate(TOKEN_TREASURY),
						tokenCreate(NFT)
								.initialSupply(0)
								.tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
								.supplyType(TokenSupplyType.INFINITE)
								.supplyKey(SUPPLY_KEY)
								.treasury(TOKEN_TREASURY)
				).when()
				.then(
						getTokenNftInfos(NFT, 0, 2)
								.hasCostAnswerPrecheck(INVALID_QUERY_RANGE)
				);
	}

	private HapiApiSpec failsWithTokenNftsInvalidQueryBoundaries() {
		return defaultHapiSpec("FailsWithTokenNftsInvalidQueryBoundaries")
				.given(
						newKeyNamed(SUPPLY_KEY),
						cryptoCreate(TOKEN_TREASURY),
						tokenCreate(NFT)
								.initialSupply(0)
								.tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
								.supplyType(TokenSupplyType.INFINITE)
								.supplyKey(SUPPLY_KEY)
								.treasury(TOKEN_TREASURY)
				).when().then(
						getTokenNftInfos(NFT, 2, 0)
								.hasCostAnswerPrecheck(INVALID_QUERY_RANGE),
						getTokenNftInfos(NFT, 0, 100000000)
								.hasCostAnswerPrecheck(INVALID_QUERY_RANGE)
				);
	}

	private HapiApiSpec failsWithDeletedTokenNft() {
		return defaultHapiSpec("FailsWithDeletedTokenNft")
				.given(
						newKeyNamed(SUPPLY_KEY),
						newKeyNamed("nftAdmin"),
						cryptoCreate(TOKEN_TREASURY),
						tokenCreate(NFT)
								.initialSupply(0)
								.tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
								.supplyType(TokenSupplyType.INFINITE)
								.supplyKey(SUPPLY_KEY)
								.adminKey("nftAdmin")
								.treasury(TOKEN_TREASURY)
				).when(
						tokenDelete(NFT)
				)
				.then(
						getTokenNftInfos(NFT, 0, 2)
								.hasCostAnswerPrecheck(TOKEN_WAS_DELETED)
				);
	}

	public HapiApiSpec failsWithFungibleTokenGetNftInfos() {
		return defaultHapiSpec("FailsWithFungibleTokenGetNftInfos")
				.given(
						cryptoCreate(TOKEN_TREASURY).balance(0L)
				).when(
						tokenCreate(A_TOKEN)
								.initialSupply(1_000)
								.treasury(TOKEN_TREASURY)
				).then(
						getTokenNftInfos(A_TOKEN, 0, 2)
								.hasCostAnswerPrecheck(NOT_SUPPORTED)
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