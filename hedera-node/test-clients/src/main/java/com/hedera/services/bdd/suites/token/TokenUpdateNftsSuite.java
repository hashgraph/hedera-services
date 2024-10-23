/*
 * Copyright (C) 2020-2024 Hedera Hashgraph, LLC
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

import static com.google.protobuf.ByteString.copyFromUtf8;
import static com.hedera.services.bdd.junit.TestTags.TOKEN;
import static com.hedera.services.bdd.spec.HapiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.HapiSpec.hapiTest;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTokenNftInfo;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.burnToken;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.mintToken;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenAssociate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenUpdateNfts;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.submitModified;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.validateChargedUsdWithin;
import static com.hedera.services.bdd.spec.utilops.mod.ModificationUtils.withSuccessivelyVariedBodyIds;
import static com.hedera.services.bdd.suites.HapiSuite.DEFAULT_PAYER;
import static com.hedera.services.bdd.suites.HapiSuite.GENESIS;
import static com.hedera.services.bdd.suites.HapiSuite.ONE_HBAR;
import static com.hedera.services.bdd.suites.HapiSuite.ONE_HUNDRED_HBARS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SIGNATURE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_HAS_NO_METADATA_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_HAS_NO_METADATA_OR_SUPPLY_KEY;
import static com.hederahashgraph.api.proto.java.TokenType.NON_FUNGIBLE_UNIQUE;

import com.google.protobuf.ByteString;
import com.hedera.services.bdd.junit.HapiTest;
import com.hedera.services.bdd.spec.transactions.token.TokenMovement;
import com.hederahashgraph.api.proto.java.TokenSupplyType;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Tag;

@Tag(TOKEN)
public class TokenUpdateNftsSuite {
    private static String TOKEN_TREASURY = "treasury";
    private static final String NON_FUNGIBLE_TOKEN = "nonFungible";
    private static final String SUPPLY_KEY = "supplyKey";
    private static final String METADATA_KEY = "metadataKey";
    private static final String ADMIN_KEY = "adminKey";

    private static final String WIPE_KEY = "wipeKey";
    private static final String NFT_TEST_METADATA = " test metadata";
    private static final String RECEIVER = "receiver";

    @HapiTest
    final Stream<DynamicTest> idVariantsTreatedAsExpected() {
        return defaultHapiSpec("idVariantsTreatedAsExpected")
                .given(
                        newKeyNamed("multiKey"),
                        cryptoCreate(TOKEN_TREASURY),
                        tokenCreate(NON_FUNGIBLE_TOKEN)
                                .tokenType(NON_FUNGIBLE_UNIQUE)
                                .treasury(TOKEN_TREASURY)
                                .supplyKey("multiKey")
                                .metadataKey("multiKey")
                                .initialSupply(0L),
                        mintToken(NON_FUNGIBLE_TOKEN, List.of(copyFromUtf8("a"), copyFromUtf8("b"))))
                .when()
                .then(submitModified(withSuccessivelyVariedBodyIds(), () -> tokenUpdateNfts(
                                NON_FUNGIBLE_TOKEN, NFT_TEST_METADATA, List.of(1L, 2L))
                        .signedBy(DEFAULT_PAYER, "multiKey")));
    }

    /**
     * <a href="https://github.com/hashgraph/hedera-improvement-proposal/blob/main/HIP/hip-850.md">HIP-850</a>
     * Tests that the transaction fails if it is not signed by the metadata key or the supply key.
     * @return the dynamic test
     */
    @HapiTest
    final Stream<DynamicTest> failsIfNoMetadataKeyOrSupplyKeySigns() {
        return defaultHapiSpec("failsIfNoMetadataKeyOrSupplyKeySigns")
                .given(
                        newKeyNamed(SUPPLY_KEY),
                        newKeyNamed(METADATA_KEY),
                        cryptoCreate(TOKEN_TREASURY).maxAutomaticTokenAssociations(4),
                        cryptoCreate(RECEIVER).maxAutomaticTokenAssociations(4),
                        tokenCreate(NON_FUNGIBLE_TOKEN)
                                .supplyType(TokenSupplyType.FINITE)
                                .tokenType(NON_FUNGIBLE_UNIQUE)
                                .treasury(TOKEN_TREASURY)
                                .maxSupply(12L)
                                .supplyKey(SUPPLY_KEY)
                                .metadataKey(METADATA_KEY)
                                .initialSupply(0L),
                        tokenAssociate(RECEIVER, NON_FUNGIBLE_TOKEN),
                        mintToken(NON_FUNGIBLE_TOKEN, List.of(copyFromUtf8("a"), copyFromUtf8("b"))))
                .when()
                .then(tokenUpdateNfts(NON_FUNGIBLE_TOKEN, NFT_TEST_METADATA, List.of(1L))
                        .payingWith(TOKEN_TREASURY)
                        .fee(10 * ONE_HBAR)
                        .via("nftUpdateTxn")
                        .hasKnownStatus(INVALID_SIGNATURE));
    }

    /**
     * <a href="https://github.com/hashgraph/hedera-improvement-proposal/blob/main/HIP/hip-850.md">HIP-850</a>
     * Tests that the transaction succeeds if it is signed by the supply key and metadata key while serials are in the treasury.
     * @return the dynamic test
     */
    @HapiTest
    final Stream<DynamicTest> supplyKeyAndMetadataKeyInTreasury() {
        return defaultHapiSpec("supplyKeyAndMetadataKeyInTreasury")
                .given(
                        newKeyNamed(SUPPLY_KEY),
                        newKeyNamed(METADATA_KEY),
                        cryptoCreate(TOKEN_TREASURY).maxAutomaticTokenAssociations(4),
                        cryptoCreate(RECEIVER).maxAutomaticTokenAssociations(4),
                        tokenCreate(NON_FUNGIBLE_TOKEN)
                                .supplyType(TokenSupplyType.FINITE)
                                .tokenType(NON_FUNGIBLE_UNIQUE)
                                .treasury(TOKEN_TREASURY)
                                .maxSupply(12L)
                                .supplyKey(SUPPLY_KEY)
                                .metadataKey(METADATA_KEY)
                                .initialSupply(0L),
                        tokenAssociate(RECEIVER, NON_FUNGIBLE_TOKEN),
                        mintToken(NON_FUNGIBLE_TOKEN, List.of(copyFromUtf8("a"), copyFromUtf8("b"))))
                .when()
                .then(tokenUpdateNfts(NON_FUNGIBLE_TOKEN, NFT_TEST_METADATA, List.of(1L))
                        .signedBy(SUPPLY_KEY, METADATA_KEY)
                        .payingWith(TOKEN_TREASURY)
                        .fee(10 * ONE_HBAR)
                        .via("nftUpdateTxn")
                        .hasKnownStatus(SUCCESS));
    }

    /**
     * <a href="https://github.com/hashgraph/hedera-improvement-proposal/blob/main/HIP/hip-850.md">HIP-850</a>
     * Tests that the transaction succeeds if it is signed by the supply key and metadata key while serials are outside the treasury.
     * @return the dynamic test
     */
    @HapiTest
    final Stream<DynamicTest> supplyKeyAndMetadataKeyOutsideTreasury() {
        return defaultHapiSpec("supplyKeyAndMetadataKeyOutsideTreasury")
                .given(
                        newKeyNamed(SUPPLY_KEY),
                        newKeyNamed(METADATA_KEY),
                        cryptoCreate(TOKEN_TREASURY).maxAutomaticTokenAssociations(4),
                        cryptoCreate(RECEIVER).maxAutomaticTokenAssociations(4),
                        tokenCreate(NON_FUNGIBLE_TOKEN)
                                .supplyType(TokenSupplyType.FINITE)
                                .tokenType(NON_FUNGIBLE_UNIQUE)
                                .treasury(TOKEN_TREASURY)
                                .maxSupply(12L)
                                .supplyKey(SUPPLY_KEY)
                                .metadataKey(METADATA_KEY)
                                .initialSupply(0L),
                        tokenAssociate(RECEIVER, NON_FUNGIBLE_TOKEN),
                        mintToken(NON_FUNGIBLE_TOKEN, List.of(copyFromUtf8("a"), copyFromUtf8("b"))))
                .when(cryptoTransfer(
                        TokenMovement.movingUnique(NON_FUNGIBLE_TOKEN, 1L).between(TOKEN_TREASURY, RECEIVER)))
                .then(tokenUpdateNfts(NON_FUNGIBLE_TOKEN, NFT_TEST_METADATA, List.of(1L))
                        .signedBy(SUPPLY_KEY, METADATA_KEY)
                        .payingWith(TOKEN_TREASURY)
                        .fee(10 * ONE_HBAR)
                        .via("nftUpdateTxn")
                        .hasKnownStatus(SUCCESS));
    }

    /**
     * <a href="https://github.com/hashgraph/hedera-improvement-proposal/blob/main/HIP/hip-850.md">HIP-850</a>
     * Tests that the transaction succeeds if it is signed by the metadata key while serials are in the treasury.
     * @return the dynamic test
     */
    @HapiTest
    final Stream<DynamicTest> metadataKeySignedInTreasury() {
        return defaultHapiSpec("supplyKeyAndMetadataKeyOutsideTreasury")
                .given(
                        newKeyNamed(SUPPLY_KEY),
                        newKeyNamed(METADATA_KEY),
                        cryptoCreate(TOKEN_TREASURY).maxAutomaticTokenAssociations(4),
                        cryptoCreate(RECEIVER).maxAutomaticTokenAssociations(4),
                        tokenCreate(NON_FUNGIBLE_TOKEN)
                                .supplyType(TokenSupplyType.FINITE)
                                .tokenType(NON_FUNGIBLE_UNIQUE)
                                .treasury(TOKEN_TREASURY)
                                .maxSupply(12L)
                                .supplyKey(SUPPLY_KEY)
                                .metadataKey(METADATA_KEY)
                                .initialSupply(0L),
                        tokenAssociate(RECEIVER, NON_FUNGIBLE_TOKEN),
                        mintToken(NON_FUNGIBLE_TOKEN, List.of(copyFromUtf8("a"), copyFromUtf8("b"))))
                .when()
                .then(tokenUpdateNfts(NON_FUNGIBLE_TOKEN, NFT_TEST_METADATA, List.of(1L))
                        .signedBy(METADATA_KEY)
                        .payingWith(TOKEN_TREASURY)
                        .fee(10 * ONE_HBAR)
                        .via("nftUpdateTxn")
                        .hasKnownStatus(SUCCESS));
    }

    /**
     * <a href="https://github.com/hashgraph/hedera-improvement-proposal/blob/main/HIP/hip-850.md">HIP-850</a>
     * Tests the transaction succeeds if it is signed by the metadata key while serials are outside the treasury.
     * @return the dynamic test
     */
    @HapiTest
    final Stream<DynamicTest> metadataKeySignedOutsideTreasury() {
        return defaultHapiSpec("metadataKeySignedOutsideTreasury")
                .given(
                        newKeyNamed(SUPPLY_KEY),
                        newKeyNamed(METADATA_KEY),
                        cryptoCreate(TOKEN_TREASURY).maxAutomaticTokenAssociations(4),
                        cryptoCreate(RECEIVER).maxAutomaticTokenAssociations(4),
                        tokenCreate(NON_FUNGIBLE_TOKEN)
                                .supplyType(TokenSupplyType.FINITE)
                                .tokenType(NON_FUNGIBLE_UNIQUE)
                                .treasury(TOKEN_TREASURY)
                                .maxSupply(12L)
                                .supplyKey(SUPPLY_KEY)
                                .metadataKey(METADATA_KEY)
                                .initialSupply(0L),
                        tokenAssociate(RECEIVER, NON_FUNGIBLE_TOKEN),
                        mintToken(NON_FUNGIBLE_TOKEN, List.of(copyFromUtf8("a"), copyFromUtf8("b"))))
                .when(cryptoTransfer(
                        TokenMovement.movingUnique(NON_FUNGIBLE_TOKEN, 1L).between(TOKEN_TREASURY, RECEIVER)))
                .then(tokenUpdateNfts(NON_FUNGIBLE_TOKEN, NFT_TEST_METADATA, List.of(1L))
                        .signedBy(METADATA_KEY)
                        .payingWith(TOKEN_TREASURY)
                        .fee(10 * ONE_HBAR)
                        .via("nftUpdateTxn")
                        .hasKnownStatus(SUCCESS));
    }

    /**
     * <a href="https://github.com/hashgraph/hedera-improvement-proposal/blob/main/HIP/hip-850.md">HIP-850</a>
     * Tests that the transaction succeeds if it is signed by the supply key while serials are in the treasury.
     * @return the dynamic test
     */
    @HapiTest
    final Stream<DynamicTest> supplyKeySignedInTreasury() {
        return defaultHapiSpec("supplyKeySignedInTreasury")
                .given(
                        newKeyNamed(SUPPLY_KEY),
                        newKeyNamed(METADATA_KEY),
                        cryptoCreate(TOKEN_TREASURY).maxAutomaticTokenAssociations(4),
                        cryptoCreate(RECEIVER).maxAutomaticTokenAssociations(4),
                        tokenCreate(NON_FUNGIBLE_TOKEN)
                                .supplyType(TokenSupplyType.FINITE)
                                .tokenType(NON_FUNGIBLE_UNIQUE)
                                .treasury(TOKEN_TREASURY)
                                .maxSupply(12L)
                                .supplyKey(SUPPLY_KEY)
                                .metadataKey(METADATA_KEY)
                                .initialSupply(0L),
                        tokenAssociate(RECEIVER, NON_FUNGIBLE_TOKEN),
                        mintToken(NON_FUNGIBLE_TOKEN, List.of(copyFromUtf8("a"), copyFromUtf8("b"))))
                .when()
                .then(tokenUpdateNfts(NON_FUNGIBLE_TOKEN, NFT_TEST_METADATA, List.of(1L))
                        .signedBy(SUPPLY_KEY)
                        .payingWith(TOKEN_TREASURY)
                        .fee(10 * ONE_HBAR)
                        .via("nftUpdateTxn")
                        .hasKnownStatus(SUCCESS));
    }

    /**
     * <a href="https://github.com/hashgraph/hedera-improvement-proposal/blob/main/HIP/hip-850.md">HIP-850</a>
     * Tests that the transaction fails if it is signed by the supply key while serials are outside the treasury.
     * @return the dynamic test
     */
    @HapiTest
    final Stream<DynamicTest> supplyKeySignedOutsideTreasury() {
        return defaultHapiSpec("supplyKeySignedOutsideTreasury")
                .given(
                        newKeyNamed(SUPPLY_KEY),
                        newKeyNamed(METADATA_KEY),
                        cryptoCreate(TOKEN_TREASURY).maxAutomaticTokenAssociations(4),
                        cryptoCreate(RECEIVER).maxAutomaticTokenAssociations(4),
                        tokenCreate(NON_FUNGIBLE_TOKEN)
                                .supplyType(TokenSupplyType.FINITE)
                                .tokenType(NON_FUNGIBLE_UNIQUE)
                                .treasury(TOKEN_TREASURY)
                                .maxSupply(12L)
                                .supplyKey(SUPPLY_KEY)
                                .metadataKey(METADATA_KEY)
                                .initialSupply(0L),
                        tokenAssociate(RECEIVER, NON_FUNGIBLE_TOKEN),
                        mintToken(NON_FUNGIBLE_TOKEN, List.of(copyFromUtf8("a"), copyFromUtf8("b"))))
                .when(cryptoTransfer(
                        TokenMovement.movingUnique(NON_FUNGIBLE_TOKEN, 1L).between(TOKEN_TREASURY, RECEIVER)))
                .then(tokenUpdateNfts(NON_FUNGIBLE_TOKEN, NFT_TEST_METADATA, List.of(1L))
                        .signedBy(SUPPLY_KEY)
                        .payingWith(TOKEN_TREASURY)
                        .fee(10 * ONE_HBAR)
                        .via("nftUpdateTxn")
                        .hasKnownStatus(INVALID_SIGNATURE));
    }

    /**
     * <a href="https://github.com/hashgraph/hedera-improvement-proposal/blob/main/HIP/hip-850.md">HIP-850</a>
     * Tests that the transaction succeeds if it is signed by the supply key (Token no metadata key) while serials are in the treasury.
     * @return the dynamic test
     */
    @HapiTest
    final Stream<DynamicTest> noMetadataKeySignedInTreasury() {
        return defaultHapiSpec("noMetadataKeySignedInTreasury")
                .given(
                        newKeyNamed(SUPPLY_KEY),
                        newKeyNamed(METADATA_KEY),
                        cryptoCreate(TOKEN_TREASURY).maxAutomaticTokenAssociations(4),
                        cryptoCreate(RECEIVER).maxAutomaticTokenAssociations(4),
                        tokenCreate(NON_FUNGIBLE_TOKEN)
                                .supplyType(TokenSupplyType.FINITE)
                                .tokenType(NON_FUNGIBLE_UNIQUE)
                                .treasury(TOKEN_TREASURY)
                                .maxSupply(12L)
                                .supplyKey(SUPPLY_KEY)
                                .initialSupply(0L),
                        tokenAssociate(RECEIVER, NON_FUNGIBLE_TOKEN),
                        mintToken(NON_FUNGIBLE_TOKEN, List.of(copyFromUtf8("a"), copyFromUtf8("b"))))
                .when()
                .then(tokenUpdateNfts(NON_FUNGIBLE_TOKEN, NFT_TEST_METADATA, List.of(1L))
                        .signedBy(SUPPLY_KEY)
                        .payingWith(TOKEN_TREASURY)
                        .fee(10 * ONE_HBAR)
                        .via("nftUpdateTxn")
                        .hasKnownStatus(SUCCESS));
    }

    /**
     * <a href="https://github.com/hashgraph/hedera-improvement-proposal/blob/main/HIP/hip-850.md">HIP-850</a>
     * Tests that the transaction fails if it is signed by the supply key (Token no metadata key) while serials are outside the treasury.
     * @return the dynamic test
     */
    @HapiTest
    final Stream<DynamicTest> noMetadataKeySignedOutsideTreasury() {
        return defaultHapiSpec("noMetadataKeySignedOutsideTreasury")
                .given(
                        newKeyNamed(SUPPLY_KEY),
                        newKeyNamed(METADATA_KEY),
                        cryptoCreate(TOKEN_TREASURY).maxAutomaticTokenAssociations(4),
                        cryptoCreate(RECEIVER).maxAutomaticTokenAssociations(4),
                        tokenCreate(NON_FUNGIBLE_TOKEN)
                                .supplyType(TokenSupplyType.FINITE)
                                .tokenType(NON_FUNGIBLE_UNIQUE)
                                .treasury(TOKEN_TREASURY)
                                .maxSupply(12L)
                                .supplyKey(SUPPLY_KEY)
                                .initialSupply(0L),
                        tokenAssociate(RECEIVER, NON_FUNGIBLE_TOKEN),
                        mintToken(NON_FUNGIBLE_TOKEN, List.of(copyFromUtf8("a"), copyFromUtf8("b"))))
                .when(cryptoTransfer(
                        TokenMovement.movingUnique(NON_FUNGIBLE_TOKEN, 1L).between(TOKEN_TREASURY, RECEIVER)))
                .then(tokenUpdateNfts(NON_FUNGIBLE_TOKEN, NFT_TEST_METADATA, List.of(1L))
                        .signedBy(SUPPLY_KEY)
                        .payingWith(TOKEN_TREASURY)
                        .fee(10 * ONE_HBAR)
                        .via("nftUpdateTxn")
                        .hasKnownStatus(TOKEN_HAS_NO_METADATA_KEY));
    }

    /**
     * <a href="https://github.com/hashgraph/hedera-improvement-proposal/blob/main/HIP/hip-850.md">HIP-850</a>
     * Tests that the transaction will fail if the Token does not have a metadata key or supply key after minting.
     * @return the dynamic test
     */
    @HapiTest
    final Stream<DynamicTest> noTokenMetadataKeyOrSupplyKeyAfterMinting() {
        return defaultHapiSpec("noTokenMetadataKeyOrSupplyKeyAfterMinting")
                .given(
                        newKeyNamed(SUPPLY_KEY),
                        newKeyNamed(METADATA_KEY),
                        newKeyNamed(ADMIN_KEY),
                        cryptoCreate(TOKEN_TREASURY).maxAutomaticTokenAssociations(4),
                        cryptoCreate(RECEIVER).maxAutomaticTokenAssociations(4),
                        tokenCreate(NON_FUNGIBLE_TOKEN)
                                .supplyType(TokenSupplyType.FINITE)
                                .tokenType(NON_FUNGIBLE_UNIQUE)
                                .treasury(TOKEN_TREASURY)
                                .maxSupply(12L)
                                .supplyKey(SUPPLY_KEY)
                                .metadataKey(METADATA_KEY)
                                .adminKey(ADMIN_KEY)
                                .initialSupply(0L),
                        tokenAssociate(RECEIVER, NON_FUNGIBLE_TOKEN),
                        mintToken(NON_FUNGIBLE_TOKEN, List.of(copyFromUtf8("a"), copyFromUtf8("b"))))
                .when(tokenUpdate(NON_FUNGIBLE_TOKEN)
                        .signedBy(ADMIN_KEY)
                        .properlyEmptyingSupplyKey()
                        .properlyEmptyingMetadataKey()
                        .payingWith(TOKEN_TREASURY)
                        .fee(10 * ONE_HBAR)
                        .via("tokenUpdateTxn"))
                .then(tokenUpdateNfts(NON_FUNGIBLE_TOKEN, NFT_TEST_METADATA, List.of(1L))
                        .signedBy(SUPPLY_KEY)
                        .payingWith(TOKEN_TREASURY)
                        .fee(10 * ONE_HBAR)
                        .via("nftUpdateTxn")
                        .hasKnownStatus(TOKEN_HAS_NO_METADATA_OR_SUPPLY_KEY));
    }

    @HapiTest
    final Stream<DynamicTest> updateMetadataOfNfts() {
        return defaultHapiSpec("updateMetadataOfNfts")
                .given(
                        newKeyNamed(SUPPLY_KEY),
                        newKeyNamed(METADATA_KEY),
                        cryptoCreate(TOKEN_TREASURY),
                        tokenCreate(NON_FUNGIBLE_TOKEN)
                                .supplyType(TokenSupplyType.FINITE)
                                .tokenType(NON_FUNGIBLE_UNIQUE)
                                .treasury(TOKEN_TREASURY)
                                .maxSupply(12L)
                                .supplyKey(SUPPLY_KEY)
                                .metadataKey(METADATA_KEY)
                                .initialSupply(0L),
                        mintToken(
                                NON_FUNGIBLE_TOKEN,
                                List.of(
                                        copyFromUtf8("a"),
                                        copyFromUtf8("b"),
                                        copyFromUtf8("c"),
                                        copyFromUtf8("d"),
                                        copyFromUtf8("e"),
                                        copyFromUtf8("f"),
                                        copyFromUtf8("g"))))
                .when()
                .then(
                        getTokenNftInfo(NON_FUNGIBLE_TOKEN, 7L)
                                .hasSerialNum(7L)
                                .hasMetadata(ByteString.copyFromUtf8("g")),
                        tokenUpdateNfts(NON_FUNGIBLE_TOKEN, NFT_TEST_METADATA, List.of(7L))
                                .signedBy(GENESIS)
                                .hasKnownStatus(INVALID_SIGNATURE),
                        tokenUpdateNfts(NON_FUNGIBLE_TOKEN, NFT_TEST_METADATA, List.of(7L))
                                .signedBy(DEFAULT_PAYER, METADATA_KEY),
                        getTokenNftInfo(NON_FUNGIBLE_TOKEN, 7L)
                                .hasSerialNum(7L)
                                .hasMetadata(ByteString.copyFromUtf8(NFT_TEST_METADATA)),
                        burnToken(NON_FUNGIBLE_TOKEN, List.of(7L)),
                        getAccountBalance(TOKEN_TREASURY).hasTokenBalance(NON_FUNGIBLE_TOKEN, 6L));
    }

    @HapiTest
    final Stream<DynamicTest> updateSingleNftFeeChargedAsExpected() {
        final var expectedNftUpdatePriceUsd = 0.001;
        final var nftUpdateTxn = "nftUpdateTxn";

        return hapiTest(
                newKeyNamed(SUPPLY_KEY),
                newKeyNamed(WIPE_KEY),
                newKeyNamed(METADATA_KEY),
                cryptoCreate(TOKEN_TREASURY).balance(ONE_HUNDRED_HBARS),
                tokenCreate(NON_FUNGIBLE_TOKEN)
                        .supplyType(TokenSupplyType.FINITE)
                        .tokenType(NON_FUNGIBLE_UNIQUE)
                        .treasury(TOKEN_TREASURY)
                        .maxSupply(12L)
                        .wipeKey(WIPE_KEY)
                        .supplyKey(SUPPLY_KEY)
                        .metadataKey(METADATA_KEY)
                        .initialSupply(0L),
                mintToken(
                        NON_FUNGIBLE_TOKEN,
                        List.of(
                                copyFromUtf8("a"),
                                copyFromUtf8("b"),
                                copyFromUtf8("c"),
                                copyFromUtf8("d"),
                                copyFromUtf8("e"),
                                copyFromUtf8("f"),
                                copyFromUtf8("g"))),
                tokenUpdateNfts(NON_FUNGIBLE_TOKEN, NFT_TEST_METADATA, List.of(7L))
                        .signedBy(TOKEN_TREASURY, METADATA_KEY)
                        .payingWith(TOKEN_TREASURY)
                        .fee(10 * ONE_HBAR)
                        .via(nftUpdateTxn),
                validateChargedUsdWithin(nftUpdateTxn, expectedNftUpdatePriceUsd, 0.01));
    }

    @HapiTest
    final Stream<DynamicTest> updateMultipleNftsFeeChargedAsExpected() {
        final var expectedNftUpdatePriceUsd = 0.005;
        final var nftUpdateTxn = "nftUpdateTxn";

        return hapiTest(
                newKeyNamed(SUPPLY_KEY),
                newKeyNamed(WIPE_KEY),
                newKeyNamed(METADATA_KEY),
                cryptoCreate(TOKEN_TREASURY).balance(ONE_HUNDRED_HBARS),
                tokenCreate(NON_FUNGIBLE_TOKEN)
                        .supplyType(TokenSupplyType.FINITE)
                        .tokenType(NON_FUNGIBLE_UNIQUE)
                        .treasury(TOKEN_TREASURY)
                        .maxSupply(12L)
                        .wipeKey(WIPE_KEY)
                        .supplyKey(SUPPLY_KEY)
                        .metadataKey(METADATA_KEY)
                        .initialSupply(0L),
                mintToken(
                        NON_FUNGIBLE_TOKEN,
                        List.of(
                                copyFromUtf8("a"),
                                copyFromUtf8("b"),
                                copyFromUtf8("c"),
                                copyFromUtf8("d"),
                                copyFromUtf8("e"),
                                copyFromUtf8("f"),
                                copyFromUtf8("g"))),
                tokenUpdateNfts(NON_FUNGIBLE_TOKEN, NFT_TEST_METADATA, List.of(1L, 2L, 3L, 4L, 5L))
                        .signedBy(TOKEN_TREASURY, METADATA_KEY)
                        .payingWith(TOKEN_TREASURY)
                        .fee(10 * ONE_HBAR)
                        .via(nftUpdateTxn),
                validateChargedUsdWithin(nftUpdateTxn, expectedNftUpdatePriceUsd, 0.01));
    }
}
