/*
 * Copyright (C) 2021-2023 Hedera Hashgraph, LLC
 *
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
 */
package com.hedera.services.bdd.suites.token;

import static com.hedera.services.bdd.spec.HapiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getReceipt;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTokenInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTokenNftInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.queries.crypto.ExpectedTokenRel.relationshipWith;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.burnToken;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.mintToken;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenAssociate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenDelete;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenDissociate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.wipeTokenAccount;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.movingUnique;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.suites.utils.MiscEETUtils.batchOfSize;
import static com.hedera.services.bdd.suites.utils.MiscEETUtils.metadata;
import static com.hedera.services.bdd.suites.utils.MiscEETUtils.metadataOfLength;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_DOES_NOT_OWN_WIPED_NFT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_STILL_OWNS_NFTS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.BATCH_SIZE_LIMIT_EXCEEDED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FAIL_INVALID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_NFT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_BURN_AMOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_BURN_METADATA;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_MINT_AMOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_MINT_METADATA;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_NFT_SERIAL_NUMBER;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_WIPING_AMOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_MAX_SUPPLY_REACHED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_WAS_DELETED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TREASURY_MUST_OWN_BURNED_NFT;
import static com.hederahashgraph.api.proto.java.TokenType.FUNGIBLE_COMMON;
import static com.hederahashgraph.api.proto.java.TokenType.NON_FUNGIBLE_UNIQUE;

import com.google.protobuf.ByteString;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.transactions.TxnUtils;
import com.hedera.services.bdd.spec.transactions.token.TokenMovement;
import com.hedera.services.bdd.spec.utilops.UtilVerbs;
import com.hedera.services.bdd.suites.HapiSuite;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenSupplyType;
import com.hederahashgraph.api.proto.java.TokenType;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.LongStream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Assertions;

public class UniqueTokenManagementSpecs extends HapiSuite {
    private static final org.apache.logging.log4j.Logger log =
            LogManager.getLogger(UniqueTokenManagementSpecs.class);
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
    public List<HapiSpec> getSpecsInSuite() {
        return List.of(
                new HapiSpec[] {
                    mintFailsWithLargeBatchSize(),
                    mintFailsWithTooLongMetadata(),
                    mintFailsWithInvalidMetadataFromBatch(),
                    mintUniqueTokenHappyPath(),
                    mintTokenWorksWhenAccountsAreFrozenByDefault(),
                    mintFailsWithDeletedToken(),
                    mintUniqueTokenWorksWithRepeatedMetadata(),
                    mintDistinguishesFeeSubTypes(),
                    mintUniqueTokenReceiptCheck(),
                    populatingMetadataForFungibleDoesNotWork(),
                    populatingAmountForNonFungibleDoesNotWork(),
                    finiteNftReachesMaxSupplyProperly(),
                    burnHappyPath(),
                    canOnlyBurnFromTreasury(),
                    burnFailsOnInvalidSerialNumber(),
                    burnRespectsBurnBatchConstraints(),
                    treasuryBalanceCorrectAfterBurn(),
                    burnWorksWhenAccountsAreFrozenByDefault(),
                    serialNumbersOnlyOnFungibleBurnFails(),
                    amountOnlyOnNonFungibleBurnFails(),
                    wipeHappyPath(),
                    wipeRespectsConstraints(),
                    commonWipeFailsWhenInvokedOnUniqueToken(),
                    uniqueWipeFailsWhenInvokedOnFungibleToken(),
                    wipeFailsWithInvalidSerialNumber(),
                    getTokenNftInfoWorks(),
                    getTokenNftInfoFailsWithNoNft(),
                    tokenDissociateHappyPath(),
                    tokenDissociateFailsIfAccountOwnsUniqueTokens(),
                });
    }

    private HapiSpec populatingMetadataForFungibleDoesNotWork() {
        return defaultHapiSpec("PopulatingMetadataForFungibleDoesNotWork")
                .given(
                        newKeyNamed(SUPPLY_KEY),
                        cryptoCreate(TOKEN_TREASURY),
                        tokenCreate(FUNGIBLE_TOKEN)
                                .initialSupply(0)
                                .tokenType(TokenType.FUNGIBLE_COMMON)
                                .supplyType(TokenSupplyType.INFINITE)
                                .supplyKey(SUPPLY_KEY)
                                .treasury(TOKEN_TREASURY))
                .when(
                        mintToken(
                                        FUNGIBLE_TOKEN,
                                        List.of(
                                                metadata("some-data"),
                                                metadata("some-data2"),
                                                metadata("some-data3"),
                                                metadata("some-data4")))
                                .hasKnownStatus(INVALID_TOKEN_MINT_AMOUNT)
                                .via("should-not-work"))
                .then(
                        getAccountBalance(TOKEN_TREASURY).hasTokenBalance(FUNGIBLE_TOKEN, 0),
                        getTxnRecord("should-not-work").showsNoTransfers(),
                        UtilVerbs.withOpContext(
                                (spec, opLog) -> {
                                    var mintNFT = getTxnRecord("should-not-work");
                                    allRunFor(spec, mintNFT);
                                    var receipt = mintNFT.getResponseRecord().getReceipt();
                                    Assertions.assertEquals(0, receipt.getNewTotalSupply());
                                }));
    }

    private HapiSpec populatingAmountForNonFungibleDoesNotWork() {
        return defaultHapiSpec("PopulatingAmountForNonFungibleDoesNotWork")
                .given(
                        newKeyNamed(SUPPLY_KEY),
                        cryptoCreate(TOKEN_TREASURY),
                        tokenCreate(NFT)
                                .initialSupply(0)
                                .tokenType(NON_FUNGIBLE_UNIQUE)
                                .supplyType(TokenSupplyType.INFINITE)
                                .supplyKey(SUPPLY_KEY)
                                .treasury(TOKEN_TREASURY))
                .when(
                        mintToken(NFT, 300)
                                .hasKnownStatus(INVALID_TOKEN_MINT_METADATA)
                                .via("should-not-work"))
                .then(
                        getTxnRecord("should-not-work").showsNoTransfers(),
                        getAccountBalance(TOKEN_TREASURY).hasTokenBalance(NFT, 0),
                        UtilVerbs.withOpContext(
                                (spec, opLog) -> {
                                    var mintNFT = getTxnRecord("should-not-work");
                                    allRunFor(spec, mintNFT);
                                    var receipt = mintNFT.getResponseRecord().getReceipt();
                                    Assertions.assertEquals(0, receipt.getNewTotalSupply());
                                    Assertions.assertEquals(0, receipt.getSerialNumbersCount());
                                }));
    }

    private HapiSpec finiteNftReachesMaxSupplyProperly() {
        return defaultHapiSpec("FiniteNftReachesMaxSupplyProperly")
                .given(
                        newKeyNamed(SUPPLY_KEY),
                        cryptoCreate(TOKEN_TREASURY),
                        tokenCreate(NFT)
                                .initialSupply(0)
                                .maxSupply(3)
                                .tokenType(NON_FUNGIBLE_UNIQUE)
                                .supplyType(TokenSupplyType.FINITE)
                                .supplyKey(SUPPLY_KEY)
                                .treasury(TOKEN_TREASURY))
                .when(
                        mintToken(
                                        NFT,
                                        List.of(
                                                metadata("some-data"),
                                                metadata("some-data2"),
                                                metadata("some-data3"),
                                                metadata("some-data4")))
                                .hasKnownStatus(TOKEN_MAX_SUPPLY_REACHED)
                                .via("should-not-appear"))
                .then(
                        getTxnRecord("should-not-appear").showsNoTransfers(),
                        getAccountBalance(TOKEN_TREASURY).hasTokenBalance(NFT, 0),
                        UtilVerbs.withOpContext(
                                (spec, opLog) -> {
                                    var mintNFT = getTxnRecord("should-not-appear");
                                    allRunFor(spec, mintNFT);
                                    var receipt = mintNFT.getResponseRecord().getReceipt();
                                    Assertions.assertEquals(0, receipt.getNewTotalSupply());
                                    Assertions.assertEquals(0, receipt.getSerialNumbersCount());
                                }));
    }

    private HapiSpec serialNumbersOnlyOnFungibleBurnFails() {
        return defaultHapiSpec("SerialNumbersOnlyOnFungibleBurnFails")
                .given(
                        newKeyNamed(SUPPLY_KEY),
                        cryptoCreate(TOKEN_TREASURY),
                        tokenCreate(FUNGIBLE_TOKEN)
                                .initialSupply(0)
                                .tokenType(FUNGIBLE_COMMON)
                                .supplyType(TokenSupplyType.INFINITE)
                                .supplyKey(SUPPLY_KEY)
                                .treasury(TOKEN_TREASURY))
                .when(mintToken(FUNGIBLE_TOKEN, 300))
                .then(
                        burnToken(FUNGIBLE_TOKEN, List.of(1L, 2L, 3L))
                                .hasKnownStatus(INVALID_TOKEN_BURN_AMOUNT)
                                .via("burn-failure"),
                        getAccountBalance(TOKEN_TREASURY).hasTokenBalance(FUNGIBLE_TOKEN, 300),
                        getTxnRecord("burn-failure").showsNoTransfers(),
                        UtilVerbs.withOpContext(
                                (spec, opLog) -> {
                                    var burnTxn = getTxnRecord("burn-failure");
                                    allRunFor(spec, burnTxn);
                                    Assertions.assertEquals(
                                            0,
                                            burnTxn.getResponseRecord()
                                                    .getReceipt()
                                                    .getNewTotalSupply());
                                }));
    }

    private HapiSpec amountOnlyOnNonFungibleBurnFails() {
        return defaultHapiSpec("AmountOnlyOnNonFungibleBurnFails")
                .given(
                        newKeyNamed(SUPPLY_KEY),
                        cryptoCreate(TOKEN_TREASURY),
                        tokenCreate(NFT)
                                .initialSupply(0)
                                .tokenType(NON_FUNGIBLE_UNIQUE)
                                .supplyType(TokenSupplyType.INFINITE)
                                .supplyKey(SUPPLY_KEY)
                                .treasury(TOKEN_TREASURY))
                .when(
                        mintToken(
                                NFT,
                                List.of(
                                        metadata("some-random-data"),
                                        metadata("some-other-random-data"))))
                .then(
                        burnToken(NFT, 300)
                                .hasKnownStatus(INVALID_TOKEN_BURN_METADATA)
                                .via("burn-failure"),
                        getTxnRecord("burn-failure").showsNoTransfers(),
                        getAccountBalance(TOKEN_TREASURY).hasTokenBalance(NFT, 2),
                        UtilVerbs.withOpContext(
                                (spec, opLog) -> {
                                    var burnTxn = getTxnRecord("burn-failure");
                                    allRunFor(spec, burnTxn);
                                    Assertions.assertEquals(
                                            0,
                                            burnTxn.getResponseRecord()
                                                    .getReceipt()
                                                    .getNewTotalSupply());
                                }));
    }

    private HapiSpec burnWorksWhenAccountsAreFrozenByDefault() {
        return defaultHapiSpec("BurnWorksWhenAccountsAreFrozenByDefault")
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
                .when(burnToken(NFT, List.of(1L)).via("burnTxn"))
                .then(
                        getTxnRecord("burnTxn").hasCostAnswerPrecheck(OK),
                        getTokenNftInfo(NFT, 1).hasCostAnswerPrecheck(INVALID_NFT_ID),
                        getAccountInfo(TOKEN_TREASURY).hasOwnedNfts(0));
    }

    private HapiSpec burnFailsOnInvalidSerialNumber() {
        return defaultHapiSpec("BurnFailsOnInvalidSerialNumber")
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
                        burnToken(NFT, List.of(0L, 1L, 2L))
                                .via("burnTxn")
                                .hasPrecheck(INVALID_NFT_ID),
                        getAccountInfo(TOKEN_TREASURY).hasOwnedNfts(1));
    }

    private HapiSpec burnRespectsBurnBatchConstraints() {
        return defaultHapiSpec("BurnRespectsBurnBatchConstraints")
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
                        burnToken(
                                        NFT,
                                        LongStream.range(0, 1000)
                                                .boxed()
                                                .collect(Collectors.toList()))
                                .via("burnTxn")
                                .hasPrecheck(BATCH_SIZE_LIMIT_EXCEEDED));
    }

    private HapiSpec burnHappyPath() {
        return defaultHapiSpec("BurnHappyPath")
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
                .when(burnToken(NFT, List.of(1L)).via("burnTxn"))
                .then(
                        getTokenNftInfo(NFT, 1).hasCostAnswerPrecheck(INVALID_NFT_ID),
                        getTokenInfo(NFT).hasTotalSupply(0),
                        getAccountBalance(TOKEN_TREASURY).hasTokenBalance(NFT, 0),
                        getAccountInfo(TOKEN_TREASURY)
                                .hasToken(relationshipWith(NFT))
                                .hasOwnedNfts(0));
    }

    private HapiSpec canOnlyBurnFromTreasury() {
        final var nonTreasury = "anybodyElse";

        return defaultHapiSpec("CanOnlyBurnFromTreasury")
                .given(
                        newKeyNamed(SUPPLY_KEY),
                        cryptoCreate(TOKEN_TREASURY),
                        cryptoCreate(nonTreasury),
                        tokenCreate(NFT)
                                .tokenType(NON_FUNGIBLE_UNIQUE)
                                .supplyType(TokenSupplyType.INFINITE)
                                .initialSupply(0)
                                .supplyKey(SUPPLY_KEY)
                                .treasury(TOKEN_TREASURY),
                        mintToken(NFT, List.of(metadata("1"), metadata("2"))),
                        tokenAssociate(nonTreasury, NFT),
                        cryptoTransfer(movingUnique(NFT, 2L).between(TOKEN_TREASURY, nonTreasury)))
                .when(
                        burnToken(NFT, List.of(1L, 2L))
                                .via("burnTxn")
                                .hasKnownStatus(TREASURY_MUST_OWN_BURNED_NFT))
                .then(
                        getTokenNftInfo(NFT, 1).hasSerialNum(1),
                        getTokenNftInfo(NFT, 2).hasSerialNum(2),
                        getTokenInfo(NFT).hasTotalSupply(2),
                        getAccountBalance(nonTreasury).hasTokenBalance(NFT, 1),
                        getAccountBalance(TOKEN_TREASURY).hasTokenBalance(NFT, 1),
                        getAccountInfo(nonTreasury).hasOwnedNfts(1),
                        getAccountInfo(TOKEN_TREASURY).hasOwnedNfts(1));
    }

    private HapiSpec treasuryBalanceCorrectAfterBurn() {
        return defaultHapiSpec("TreasuryBalanceCorrectAfterBurn")
                .given(
                        newKeyNamed(SUPPLY_KEY),
                        cryptoCreate(TOKEN_TREASURY),
                        tokenCreate(NFT)
                                .tokenType(NON_FUNGIBLE_UNIQUE)
                                .supplyType(TokenSupplyType.INFINITE)
                                .initialSupply(0)
                                .supplyKey(SUPPLY_KEY)
                                .treasury(TOKEN_TREASURY),
                        mintToken(
                                NFT,
                                List.of(
                                        metadata("1"),
                                        metadata("2"),
                                        metadata("3"),
                                        metadata("4"),
                                        metadata("5"))))
                .when(burnToken(NFT, List.of(3L, 4L, 5L)).via("burnTxn"))
                .then(
                        getTokenNftInfo(NFT, 1).hasSerialNum(1).hasCostAnswerPrecheck(OK),
                        getTokenNftInfo(NFT, 2).hasSerialNum(2).hasCostAnswerPrecheck(OK),
                        getTokenNftInfo(NFT, 3).hasCostAnswerPrecheck(INVALID_NFT_ID),
                        getTokenNftInfo(NFT, 4).hasCostAnswerPrecheck(INVALID_NFT_ID),
                        getTokenNftInfo(NFT, 5).hasCostAnswerPrecheck(INVALID_NFT_ID),
                        getTokenInfo(NFT).hasTotalSupply(2),
                        getAccountBalance(TOKEN_TREASURY).hasTokenBalance(NFT, 2),
                        getAccountInfo(TOKEN_TREASURY).hasOwnedNfts(2));
    }

    private HapiSpec mintDistinguishesFeeSubTypes() {
        return defaultHapiSpec("MintDistinguishesFeeSubTypes")
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
                                .treasury(TOKEN_TREASURY))
                .when(
                        mintToken(NFT, List.of(metadata("memo")))
                                .payingWith("customPayer")
                                .signedBy("customPayer", "supplyKey")
                                .via("mintNFT"),
                        mintToken(FUNGIBLE_TOKEN, 100L)
                                .payingWith("customPayer")
                                .signedBy("customPayer", "supplyKey")
                                .via("mintFungible"))
                .then(
                        UtilVerbs.withOpContext(
                                (spec, opLog) -> {
                                    var mintNFT = getTxnRecord("mintNFT");
                                    var mintFungible = getTxnRecord("mintFungible");
                                    allRunFor(spec, mintNFT, mintFungible);
                                    var nftFee = mintNFT.getResponseRecord().getTransactionFee();
                                    var fungibleFee =
                                            mintFungible.getResponseRecord().getTransactionFee();
                                    Assertions.assertNotEquals(
                                            nftFee,
                                            fungibleFee,
                                            "NFT Fee should NOT equal to the Fungible Fee!");
                                }));
    }

    private HapiSpec mintFailsWithTooLongMetadata() {
        return defaultHapiSpec("MintFailsWithTooLongMetadata")
                .given(
                        newKeyNamed(SUPPLY_KEY),
                        cryptoCreate(TOKEN_TREASURY),
                        tokenCreate(NFT)
                                .tokenType(NON_FUNGIBLE_UNIQUE)
                                .supplyType(TokenSupplyType.INFINITE)
                                .initialSupply(0)
                                .supplyKey(SUPPLY_KEY)
                                .treasury(TOKEN_TREASURY))
                .when()
                .then(
                        mintToken(NFT, List.of(metadataOfLength(101)))
                                .hasPrecheck(ResponseCodeEnum.METADATA_TOO_LONG));
    }

    private HapiSpec mintFailsWithInvalidMetadataFromBatch() {
        return defaultHapiSpec("MintFailsWithInvalidMetadataFromBatch")
                .given(
                        newKeyNamed(SUPPLY_KEY),
                        cryptoCreate(TOKEN_TREASURY),
                        tokenCreate(NFT)
                                .tokenType(NON_FUNGIBLE_UNIQUE)
                                .supplyType(TokenSupplyType.INFINITE)
                                .initialSupply(0)
                                .supplyKey(SUPPLY_KEY)
                                .treasury(TOKEN_TREASURY))
                .when()
                .then(
                        mintToken(NFT, List.of(metadataOfLength(101), metadataOfLength(1)))
                                .hasPrecheck(ResponseCodeEnum.METADATA_TOO_LONG));
    }

    private HapiSpec mintFailsWithLargeBatchSize() {
        return defaultHapiSpec("MintFailsWithLargeBatchSize")
                .given(
                        newKeyNamed(SUPPLY_KEY),
                        cryptoCreate(TOKEN_TREASURY),
                        tokenCreate(NFT)
                                .tokenType(NON_FUNGIBLE_UNIQUE)
                                .supplyType(TokenSupplyType.INFINITE)
                                .initialSupply(0)
                                .supplyKey(SUPPLY_KEY)
                                .treasury(TOKEN_TREASURY))
                .when()
                .then(
                        mintToken(NFT, batchOfSize(BIGGER_THAN_LIMIT))
                                .hasPrecheck(BATCH_SIZE_LIMIT_EXCEEDED));
    }

    private HapiSpec mintUniqueTokenHappyPath() {
        return defaultHapiSpec("MintUniqueTokenHappyPath")
                .given(
                        newKeyNamed(SUPPLY_KEY),
                        cryptoCreate(TOKEN_TREASURY),
                        tokenCreate(NFT)
                                .tokenType(NON_FUNGIBLE_UNIQUE)
                                .supplyKey(SUPPLY_KEY)
                                .supplyType(TokenSupplyType.INFINITE)
                                .initialSupply(0)
                                .supplyKey(SUPPLY_KEY)
                                .treasury(TOKEN_TREASURY))
                .when(mintToken(NFT, List.of(metadata("memo"), metadata("memo1"))).via("mintTxn"))
                .then(
                        getReceipt("mintTxn"),
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
                        getTokenNftInfo(NFT, 3).hasCostAnswerPrecheck(INVALID_NFT_ID),
                        getAccountBalance(TOKEN_TREASURY).hasTokenBalance(NFT, 2),
                        getTokenInfo(NFT).hasTreasury(TOKEN_TREASURY).hasTotalSupply(2),
                        getAccountInfo(TOKEN_TREASURY)
                                .hasToken(relationshipWith(NFT))
                                .hasOwnedNfts(2));
    }

    private HapiSpec mintTokenWorksWhenAccountsAreFrozenByDefault() {
        return defaultHapiSpec("MintTokenWorksWhenAccountsAreFrozenByDefault")
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
                                .treasury(TOKEN_TREASURY))
                .when(mintToken(NFT, List.of(metadata("memo"))).via("mintTxn"))
                .then(
                        getTokenNftInfo(NFT, 1)
                                .hasTokenID(NFT)
                                .hasAccountID(TOKEN_TREASURY)
                                .hasMetadata(metadata("memo"))
                                .hasValidCreationTime(),
                        getAccountInfo(TOKEN_TREASURY).hasOwnedNfts(1));
    }

    private HapiSpec mintFailsWithDeletedToken() {
        return defaultHapiSpec("MintFailsWithDeletedToken")
                .given(
                        newKeyNamed(SUPPLY_KEY),
                        newKeyNamed("adminKey"),
                        cryptoCreate(TOKEN_TREASURY),
                        tokenCreate(NFT)
                                .supplyKey(SUPPLY_KEY)
                                .adminKey("adminKey")
                                .treasury(TOKEN_TREASURY))
                .when(tokenDelete(NFT))
                .then(
                        mintToken(NFT, List.of(metadata("memo")))
                                .via("mintTxn")
                                .hasKnownStatus(TOKEN_WAS_DELETED),
                        getTokenNftInfo(NFT, 1).hasCostAnswerPrecheck(INVALID_NFT_ID),
                        getTokenInfo(NFT).isDeleted());
    }

    private HapiSpec getTokenNftInfoFailsWithNoNft() {
        return defaultHapiSpec("GetTokenNftInfoFailsWithNoNft")
                .given(newKeyNamed(SUPPLY_KEY), cryptoCreate(TOKEN_TREASURY))
                .when(
                        tokenCreate(NFT)
                                .tokenType(NON_FUNGIBLE_UNIQUE)
                                .supplyType(TokenSupplyType.INFINITE)
                                .supplyKey(SUPPLY_KEY)
                                .initialSupply(0)
                                .treasury(TOKEN_TREASURY),
                        mintToken(NFT, List.of(metadata("memo"))).via("mintTxn"))
                .then(
                        getTokenNftInfo(NFT, 0)
                                .hasCostAnswerPrecheck(INVALID_TOKEN_NFT_SERIAL_NUMBER),
                        getTokenNftInfo(NFT, -1)
                                .hasCostAnswerPrecheck(INVALID_TOKEN_NFT_SERIAL_NUMBER),
                        getTokenNftInfo(NFT, 2).hasCostAnswerPrecheck(INVALID_NFT_ID));
    }

    private HapiSpec getTokenNftInfoWorks() {
        return defaultHapiSpec("GetTokenNftInfoWorks")
                .given(newKeyNamed(SUPPLY_KEY), cryptoCreate(TOKEN_TREASURY))
                .when(
                        tokenCreate(NFT)
                                .tokenType(NON_FUNGIBLE_UNIQUE)
                                .supplyType(TokenSupplyType.INFINITE)
                                .supplyKey(SUPPLY_KEY)
                                .initialSupply(0)
                                .treasury(TOKEN_TREASURY),
                        mintToken(NFT, List.of(metadata("memo"))))
                .then(
                        getTokenNftInfo(NFT, 0)
                                .hasCostAnswerPrecheck(INVALID_TOKEN_NFT_SERIAL_NUMBER),
                        getTokenNftInfo(NFT, -1)
                                .hasCostAnswerPrecheck(INVALID_TOKEN_NFT_SERIAL_NUMBER),
                        getTokenNftInfo(NFT, 2).hasCostAnswerPrecheck(INVALID_NFT_ID),
                        getTokenNftInfo(NFT, 1)
                                .hasTokenID(NFT)
                                .hasAccountID(TOKEN_TREASURY)
                                .hasMetadata(metadata("memo"))
                                .hasSerialNum(1)
                                .hasValidCreationTime());
    }

    private HapiSpec mintUniqueTokenWorksWithRepeatedMetadata() {
        return defaultHapiSpec("MintUniqueTokenWorksWithRepeatedMetadata")
                .given(
                        newKeyNamed(SUPPLY_KEY),
                        cryptoCreate(TOKEN_TREASURY),
                        tokenCreate(NFT)
                                .tokenType(NON_FUNGIBLE_UNIQUE)
                                .supplyType(TokenSupplyType.INFINITE)
                                .supplyKey(SUPPLY_KEY)
                                .initialSupply(0)
                                .treasury(TOKEN_TREASURY))
                .when(mintToken(NFT, List.of(metadata("memo"), metadata("memo"))).via("mintTxn"))
                .then(
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
                        getAccountInfo(TOKEN_TREASURY).hasOwnedNfts(2));
    }

    private HapiSpec wipeHappyPath() {
        return defaultHapiSpec("WipeHappyPath")
                .given(
                        newKeyNamed(SUPPLY_KEY),
                        newKeyNamed("wipeKey"),
                        newKeyNamed("treasuryKey"),
                        newKeyNamed("accKey"),
                        cryptoCreate(TOKEN_TREASURY).key("treasuryKey"),
                        cryptoCreate("account").key("accKey"),
                        tokenCreate(NFT)
                                .tokenType(NON_FUNGIBLE_UNIQUE)
                                .supplyType(TokenSupplyType.INFINITE)
                                .supplyKey(SUPPLY_KEY)
                                .initialSupply(0)
                                .treasury(TOKEN_TREASURY)
                                .wipeKey("wipeKey"),
                        tokenAssociate("account", NFT),
                        mintToken(
                                NFT,
                                List.of(
                                        ByteString.copyFromUtf8("memo"),
                                        ByteString.copyFromUtf8("memo2"))),
                        cryptoTransfer(movingUnique(NFT, 2L).between(TOKEN_TREASURY, "account")))
                .when(wipeTokenAccount(NFT, "account", List.of(2L)).via("wipeTxn"))
                .then(
                        getAccountInfo("account").hasOwnedNfts(0),
                        getAccountInfo(TOKEN_TREASURY).hasOwnedNfts(1),
                        getTokenInfo(NFT).hasTotalSupply(1),
                        getTokenNftInfo(NFT, 2).hasCostAnswerPrecheck(INVALID_NFT_ID),
                        getTokenNftInfo(NFT, 1).hasSerialNum(1),
                        wipeTokenAccount(NFT, "account", List.of(1L))
                                .hasKnownStatus(ACCOUNT_DOES_NOT_OWN_WIPED_NFT));
    }

    private HapiSpec wipeRespectsConstraints() {
        return defaultHapiSpec("WipeRespectsConstraints")
                .given(
                        newKeyNamed(SUPPLY_KEY),
                        newKeyNamed("wipeKey"),
                        cryptoCreate(TOKEN_TREASURY),
                        cryptoCreate("account"),
                        tokenCreate(NFT)
                                .tokenType(NON_FUNGIBLE_UNIQUE)
                                .supplyType(TokenSupplyType.INFINITE)
                                .supplyKey(SUPPLY_KEY)
                                .initialSupply(0)
                                .treasury(TOKEN_TREASURY)
                                .wipeKey("wipeKey"),
                        tokenAssociate("account", NFT),
                        mintToken(NFT, List.of(metadata("memo"), metadata("memo2"))).via("mintTxn"),
                        cryptoTransfer(movingUnique(NFT, 1, 2).between(TOKEN_TREASURY, "account")))
                .when()
                .then(
                        wipeTokenAccount(
                                        NFT,
                                        "account",
                                        LongStream.range(0, 1000)
                                                .boxed()
                                                .collect(Collectors.toList()))
                                .hasPrecheck(BATCH_SIZE_LIMIT_EXCEEDED));
    }

    private HapiSpec commonWipeFailsWhenInvokedOnUniqueToken() {
        return defaultHapiSpec("CommonWipeFailsWhenInvokedOnUniqueToken")
                .given(
                        newKeyNamed(SUPPLY_KEY),
                        newKeyNamed("wipeKey"),
                        cryptoCreate(TOKEN_TREASURY),
                        cryptoCreate("account"),
                        tokenCreate(NFT)
                                .tokenType(NON_FUNGIBLE_UNIQUE)
                                .supplyType(TokenSupplyType.INFINITE)
                                .supplyKey(SUPPLY_KEY)
                                .initialSupply(0)
                                .treasury(TOKEN_TREASURY)
                                .wipeKey("wipeKey"),
                        tokenAssociate("account", NFT),
                        mintToken(NFT, List.of(metadata("memo"))),
                        cryptoTransfer(movingUnique(NFT, 1).between(TOKEN_TREASURY, "account")))
                .when()
                .then(
                        wipeTokenAccount(NFT, "account", 1L)
                                .hasKnownStatus(FAIL_INVALID)
                                .via("wipeTxn"),
                        // no new totalSupply
                        getTokenInfo(NFT).hasTotalSupply(1),
                        // no tx record
                        getTxnRecord("wipeTxn").showsNoTransfers(),
                        // verify balance not decreased
                        getAccountInfo("account").hasOwnedNfts(1),
                        getAccountBalance("account").hasTokenBalance(NFT, 1));
    }

    private HapiSpec
            uniqueWipeFailsWhenInvokedOnFungibleToken() { // invokes unique wipe on fungible tokens
        return defaultHapiSpec("UniqueWipeFailsWhenInvokedOnFungibleToken")
                .given(
                        newKeyNamed("wipeKey"),
                        cryptoCreate(TOKEN_TREASURY),
                        cryptoCreate("account"),
                        tokenCreate(A_TOKEN)
                                .tokenType(TokenType.FUNGIBLE_COMMON)
                                .initialSupply(10)
                                .treasury(TOKEN_TREASURY)
                                .wipeKey("wipeKey"),
                        tokenAssociate("account", A_TOKEN),
                        cryptoTransfer(
                                TokenMovement.moving(5, A_TOKEN)
                                        .between(TOKEN_TREASURY, "account")))
                .when(
                        wipeTokenAccount(A_TOKEN, "account", List.of(1L, 2L))
                                .hasKnownStatus(INVALID_WIPING_AMOUNT)
                                .via("wipeTx"),
                        wipeTokenAccount(A_TOKEN, "account", List.of())
                                .hasKnownStatus(OK)
                                .via("wipeEmptySerialTx"))
                .then(
                        getTokenInfo(A_TOKEN).hasTotalSupply(10),
                        getTxnRecord("wipeTx").showsNoTransfers(),
                        getAccountBalance("account").hasTokenBalance(A_TOKEN, 5));
    }

    private HapiSpec wipeFailsWithInvalidSerialNumber() {
        return defaultHapiSpec("WipeFailsWithInvalidSerialNumber")
                .given(
                        newKeyNamed(SUPPLY_KEY),
                        newKeyNamed("wipeKey"),
                        cryptoCreate(TOKEN_TREASURY),
                        cryptoCreate("account"),
                        tokenCreate(NFT)
                                .tokenType(NON_FUNGIBLE_UNIQUE)
                                .supplyType(TokenSupplyType.INFINITE)
                                .supplyKey(SUPPLY_KEY)
                                .initialSupply(0)
                                .treasury(TOKEN_TREASURY)
                                .wipeKey("wipeKey"),
                        tokenAssociate("account", NFT),
                        mintToken(NFT, List.of(metadata("memo"), metadata("memo"))).via("mintTxn"))
                .when()
                .then(
                        wipeTokenAccount(NFT, "account", List.of(-5L, -6L))
                                .hasPrecheck(INVALID_NFT_ID));
    }

    private HapiSpec mintUniqueTokenReceiptCheck() {
        return defaultHapiSpec("mintUniqueTokenReceiptCheck")
                .given(
                        cryptoCreate(TOKEN_TREASURY),
                        cryptoCreate(FIRST_USER),
                        newKeyNamed("supplyKey"),
                        tokenCreate(A_TOKEN)
                                .tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
                                .initialSupply(0)
                                .supplyKey("supplyKey")
                                .treasury(TOKEN_TREASURY))
                .when(mintToken(A_TOKEN, List.of(metadata("memo"))).via("mintTransferTxn"))
                .then(
                        UtilVerbs.withOpContext(
                                (spec, opLog) -> {
                                    var mintNft = getTxnRecord("mintTransferTxn");
                                    allRunFor(spec, mintNft);
                                    var tokenTransferLists =
                                            mintNft.getResponseRecord().getTokenTransferListsList();
                                    Assertions.assertEquals(1, tokenTransferLists.size());
                                    tokenTransferLists.stream()
                                            .forEach(
                                                    tokenTransferList -> {
                                                        Assertions.assertEquals(
                                                                1,
                                                                tokenTransferList
                                                                        .getNftTransfersList()
                                                                        .size());
                                                        tokenTransferList
                                                                .getNftTransfersList()
                                                                .stream()
                                                                .forEach(
                                                                        nftTransfers -> {
                                                                            Assertions.assertEquals(
                                                                                    AccountID
                                                                                            .getDefaultInstance(),
                                                                                    nftTransfers
                                                                                            .getSenderAccountID());
                                                                            Assertions.assertEquals(
                                                                                    TxnUtils.asId(
                                                                                            TOKEN_TREASURY,
                                                                                            spec),
                                                                                    nftTransfers
                                                                                            .getReceiverAccountID());
                                                                            Assertions.assertEquals(
                                                                                    1L,
                                                                                    nftTransfers
                                                                                            .getSerialNumber());
                                                                        });
                                                    });
                                }),
                        getTxnRecord("mintTransferTxn"),
                        getReceipt("mintTransferTxn"));
    }

    private HapiSpec tokenDissociateHappyPath() {
        return defaultHapiSpec("tokenDissociateHappyPath")
                .given(
                        newKeyNamed(SUPPLY_KEY),
                        cryptoCreate(TOKEN_TREASURY),
                        cryptoCreate("acc"),
                        tokenCreate(NFT)
                                .initialSupply(0)
                                .tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
                                .supplyType(TokenSupplyType.INFINITE)
                                .supplyKey(SUPPLY_KEY)
                                .treasury(TOKEN_TREASURY))
                .when(tokenAssociate("acc", NFT))
                .then(
                        tokenDissociate("acc", NFT).hasKnownStatus(SUCCESS),
                        getAccountInfo("acc").hasNoTokenRelationship(NFT));
    }

    private HapiSpec tokenDissociateFailsIfAccountOwnsUniqueTokens() {
        return defaultHapiSpec("tokenDissociateFailsIfAccountOwnsUniqueTokens")
                .given(
                        newKeyNamed(SUPPLY_KEY),
                        cryptoCreate(TOKEN_TREASURY),
                        cryptoCreate("acc"),
                        tokenCreate(NFT)
                                .initialSupply(0)
                                .tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
                                .supplyType(TokenSupplyType.INFINITE)
                                .supplyKey(SUPPLY_KEY)
                                .treasury(TOKEN_TREASURY))
                .when(
                        tokenAssociate("acc", NFT),
                        mintToken(NFT, List.of(metadata("memo1"), metadata("memo2"))))
                .then(
                        cryptoTransfer(
                                TokenMovement.movingUnique(NFT, 1, 2)
                                        .between(TOKEN_TREASURY, "acc")),
                        tokenDissociate("acc", NFT).hasKnownStatus(ACCOUNT_STILL_OWNS_NFTS));
    }

    protected Logger getResultsLogger() {
        return log;
    }
}
