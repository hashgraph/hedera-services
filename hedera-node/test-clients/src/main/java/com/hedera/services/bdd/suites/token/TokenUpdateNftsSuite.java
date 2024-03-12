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
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTokenNftInfo;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.burnToken;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.mintToken;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenUpdateNfts;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.validateChargedUsdWithin;
import static com.hedera.services.bdd.spec.utilops.records.SnapshotMatchMode.NONDETERMINISTIC_TRANSACTION_FEES;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SIGNATURE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_HAS_NO_METADATA_KEY;
import static com.hederahashgraph.api.proto.java.TokenType.NON_FUNGIBLE_UNIQUE;

import com.google.protobuf.ByteString;
import com.hedera.services.bdd.junit.HapiTest;
import com.hedera.services.bdd.junit.HapiTestSuite;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.suites.HapiSuite;
import com.hederahashgraph.api.proto.java.TokenSupplyType;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Tag;

@HapiTestSuite
@Tag(TOKEN)
public class TokenUpdateNftsSuite extends HapiSuite {

    private static final Logger log = LogManager.getLogger(TokenUpdateNftsSuite.class);
    private static String TOKEN_TREASURY = "treasury";
    private static final String NON_FUNGIBLE_TOKEN = "nonFungible";
    private static final String SUPPLY_KEY = "supplyKey";
    private static final String METADATA_KEY = "metadataKey";

    private static final String WIPE_KEY = "wipeKey";
    private static final String NFT_TEST_METADATA = " test metadata";

    public static void main(String... args) {
        new TokenUpdateNftsSuite().runSuiteAsync();
    }

    @Override
    public boolean canRunConcurrent() {
        return true;
    }

    @Override
    public List<HapiSpec> getSpecsInSuite() {
        return List.of(updateMetadataOfNfts(), failsIfTokenHasNoMetadataKey(), updateSingleNftFeeChargedAsExpected());
    }

    @HapiTest
    private HapiSpec failsIfTokenHasNoMetadataKey() {
        return defaultHapiSpec("failsIfTokenHasNoMetadataKey")
                .given(
                        newKeyNamed(SUPPLY_KEY),
                        cryptoCreate(TOKEN_TREASURY),
                        tokenCreate(NON_FUNGIBLE_TOKEN)
                                .supplyType(TokenSupplyType.FINITE)
                                .tokenType(NON_FUNGIBLE_UNIQUE)
                                .treasury(TOKEN_TREASURY)
                                .supplyKey(SUPPLY_KEY)
                                .maxSupply(12L)
                                .initialSupply(0L),
                        mintToken(NON_FUNGIBLE_TOKEN, List.of(copyFromUtf8("a"))))
                .when()
                .then(
                        getTokenNftInfo(NON_FUNGIBLE_TOKEN, 1L).hasMetadata(ByteString.copyFromUtf8("a")),
                        tokenUpdateNfts(NON_FUNGIBLE_TOKEN, NFT_TEST_METADATA, List.of(1L))
                                .hasKnownStatus(TOKEN_HAS_NO_METADATA_KEY),
                        getTokenNftInfo(NON_FUNGIBLE_TOKEN, 1L).hasMetadata(ByteString.copyFromUtf8("a")));
    }

    @HapiTest
    final HapiSpec updateMetadataOfNfts() {
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
    final HapiSpec updateSingleNftFeeChargedAsExpected() {
        final var expectedNftUpdatePriceUsd = 0.001;
        final var nftUpdateTxn = "nftUpdateTxn";

        return defaultHapiSpec("updateNftFeeChargedAsExpected", NONDETERMINISTIC_TRANSACTION_FEES)
                .given(
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
                                        copyFromUtf8("g"))))
                .when(tokenUpdateNfts(NON_FUNGIBLE_TOKEN, NFT_TEST_METADATA, List.of(7L))
                        .signedBy(TOKEN_TREASURY, METADATA_KEY)
                        .payingWith(TOKEN_TREASURY)
                        .fee(10 * ONE_HBAR)
                        .via(nftUpdateTxn))
                .then(validateChargedUsdWithin(nftUpdateTxn, expectedNftUpdatePriceUsd, 0.01));
    }

    @HapiTest
    final HapiSpec updateMultipleNftsFeeChargedAsExpected() {
        final var expectedNftUpdatePriceUsd = 0.005;
        final var nftUpdateTxn = "nftUpdateTxn";

        return defaultHapiSpec("updateNftFeeChargedAsExpected", NONDETERMINISTIC_TRANSACTION_FEES)
                .given(
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
                                        copyFromUtf8("g"))))
                .when(tokenUpdateNfts(NON_FUNGIBLE_TOKEN, NFT_TEST_METADATA, List.of(1L, 2L, 3L, 4L, 5L))
                        .signedBy(TOKEN_TREASURY, METADATA_KEY)
                        .payingWith(TOKEN_TREASURY)
                        .fee(10 * ONE_HBAR)
                        .via(nftUpdateTxn))
                .then(validateChargedUsdWithin(nftUpdateTxn, expectedNftUpdatePriceUsd, 0.01));
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}
