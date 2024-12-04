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

package com.hedera.services.bdd.suites.bulk;

import static com.hedera.services.bdd.junit.TestTags.TOKEN;
import static com.hedera.services.bdd.spec.HapiSpec.hapiTest;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.burnToken;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.mintToken;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenAssociate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenDissociate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenUpdateNfts;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.validateChargedUsdWithin;
import static com.hedera.services.bdd.suites.HapiSuite.ONE_HUNDRED_HBARS;
import static com.hedera.services.bdd.suites.HapiSuite.flattened;
import static com.hederahashgraph.api.proto.java.TokenType.FUNGIBLE_COMMON;
import static com.hederahashgraph.api.proto.java.TokenType.NON_FUNGIBLE_UNIQUE;

import com.google.protobuf.ByteString;
import com.hedera.services.bdd.junit.HapiTest;
import com.hedera.services.bdd.junit.HapiTestLifecycle;
import com.hederahashgraph.api.proto.java.TokenSupplyType;
import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;

@Tag(TOKEN)
@HapiTestLifecycle
@DisplayName("Token Bulk Operation")
public class TokenBulkOperationsTest extends BulkOperationsBase {

    private static final String TOKEN_UPDATE_METADATA = "tokenUpdateMetadata";

    private static final double ALLOWED_DIFFERENCE_PERCENTAGE = 5;
    private static final double EXPECTED_ASSOCIATE_ONE_TOKEN_PRICE = 0.08;
    private static final double EXPECTED_ASSOCIATE_BULK_PRICE = 0.05;
    private static final double EXPECTED_DISSOCIATE_TOKEN_PRICE = 0.08;
    private static final double EXPECTED_NFT_MINT_PRICE = 0.02;
    private static final double EXPECTED_FT_MINT_PRICE = 0.001;
    private static final double EXPECTED_TOKEN_BURN_PRICE = 0.001;
    private static final double EXPECTED_NFT_UPDATE_PRICE = 0.001;

    @Nested
    @DisplayName("without custom fees")
    class BulkTokenOperationsWithoutCustomFeesTest {

        @HapiTest
        final Stream<DynamicTest> mintOneNftTokenWithoutCustomFees() {
            return mintBulkNft(1);
        }

        @HapiTest
        final Stream<DynamicTest> mintBulkNftTokensWithoutCustomFees() {
            return mintBulkNft(10);
        }

        @HapiTest
        final Stream<DynamicTest> mintOneFtTokenWithoutCustomFees() {
            return mintFt(1);
        }

        @HapiTest
        final Stream<DynamicTest> mintFtTokensWithoutCustomFees() {
            return mintFt(10);
        }

        @HapiTest
        final Stream<DynamicTest> mintOneHundredFtTokensWithoutCustomFees() {
            return mintFt(100);
        }

        @HapiTest
        final Stream<DynamicTest> burnOneNFtTokenWithoutCustomFees() {
            return burnNft(10, Arrays.asList(1L));
        }

        @HapiTest
        final Stream<DynamicTest> burnNftTokensWithoutCustomFees() {
            return burnNft(10, Arrays.asList(1L, 2L, 3L, 4L, 5L));
        }

        @HapiTest
        final Stream<DynamicTest> burnTenNftTokensWithoutCustomFees() {
            return burnNft(10, Arrays.asList(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L));
        }

        @HapiTest
        final Stream<DynamicTest> associateOneFtTokenWithoutCustomFees() {
            return associateOneToken(List.of(FT_TOKEN));
        }

        @HapiTest
        final Stream<DynamicTest> associateFtTokensWithoutCustomFees() {
            return associateBulkToken(List.of(FT_TOKEN, NFT_TOKEN, NFT_BURN_TOKEN, NFT_BURN_ONE_TOKEN));
        }

        @HapiTest
        final Stream<DynamicTest> dissociateOneTokenWithoutCustomFees() {
            return dissociateToken(new String[] {FT_TOKEN});
        }

        @HapiTest
        final Stream<DynamicTest> dissociateTokensWithoutCustomFees() {
            return dissociateToken(new String[] {FT_TOKEN, NFT_TOKEN, NFT_BURN_TOKEN, NFT_BURN_ONE_TOKEN});
        }

        @HapiTest
        final Stream<DynamicTest> updateOneNftTokenWithoutCustomFees() {
            return updateBulkNftTokens(10, Arrays.asList(1L));
        }

        @HapiTest
        final Stream<DynamicTest> updateBulkNftTokensWithoutCustomFees() {
            return updateBulkNftTokens(10, Arrays.asList(1L, 2L, 3L, 4L, 5L));
        }

        @HapiTest
        final Stream<DynamicTest> updateTenBulkNftTokensWithoutCustomFees() {
            return updateBulkNftTokens(10, Arrays.asList(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L));
        }

        // define reusable methods
        private Stream<DynamicTest> mintBulkNft(int rangeAmount) {
            var supplyKey = "supplyKey";
            return hapiTest(
                    newKeyNamed(supplyKey),
                    cryptoCreate(OWNER).balance(ONE_HUNDRED_HBARS).key(supplyKey),
                    tokenCreate(NFT_TOKEN)
                            .treasury(OWNER)
                            .tokenType(NON_FUNGIBLE_UNIQUE)
                            .supplyKey(supplyKey)
                            .supplyType(TokenSupplyType.INFINITE)
                            .initialSupply(0),
                    mintToken(
                                    NFT_TOKEN,
                                    IntStream.range(0, rangeAmount)
                                            .mapToObj(a -> ByteString.copyFromUtf8(String.valueOf(a)))
                                            .toList())
                            .payingWith(OWNER)
                            .signedBy(supplyKey)
                            .blankMemo()
                            .via("mintTxn"),
                    validateChargedUsdWithin(
                            "mintTxn", EXPECTED_NFT_MINT_PRICE * rangeAmount, ALLOWED_DIFFERENCE_PERCENTAGE));
        }

        private Stream<DynamicTest> mintFt(int tokenAmount) {
            var supplyKey = "supplyKey";
            return hapiTest(
                    newKeyNamed(supplyKey),
                    cryptoCreate(OWNER).balance(ONE_HUNDRED_HBARS).key(supplyKey),
                    tokenCreate(FT_TOKEN)
                            .treasury(OWNER)
                            .tokenType(FUNGIBLE_COMMON)
                            .supplyKey(supplyKey)
                            .initialSupply(1000L),
                    mintToken(FT_TOKEN, tokenAmount)
                            .payingWith(OWNER)
                            .signedBy(supplyKey)
                            .blankMemo()
                            .via("mintTxn"),
                    validateChargedUsdWithin("mintTxn", EXPECTED_FT_MINT_PRICE, ALLOWED_DIFFERENCE_PERCENTAGE));
        }

        private Stream<DynamicTest> burnNft(int mintAmount, List<Long> burnAmounts) {
            var supplyKey = "supplyKey";
            return hapiTest(
                    newKeyNamed(supplyKey),
                    cryptoCreate(OWNER).balance(ONE_HUNDRED_HBARS).key(supplyKey),
                    tokenCreate(NFT_TOKEN)
                            .treasury(OWNER)
                            .tokenType(NON_FUNGIBLE_UNIQUE)
                            .supplyKey(supplyKey)
                            .supplyType(TokenSupplyType.INFINITE)
                            .initialSupply(0),
                    mintToken(
                                    NFT_TOKEN,
                                    IntStream.range(0, mintAmount)
                                            .mapToObj(a -> ByteString.copyFromUtf8(String.valueOf(a)))
                                            .toList())
                            .payingWith(OWNER)
                            .signedBy(supplyKey)
                            .blankMemo(),
                    burnToken(NFT_TOKEN, burnAmounts)
                            .payingWith(OWNER)
                            .blankMemo()
                            .via("burnTxn"),
                    validateChargedUsdWithin("burnTxn", EXPECTED_TOKEN_BURN_PRICE, ALLOWED_DIFFERENCE_PERCENTAGE));
        }

        private Stream<DynamicTest> associateOneToken(List<String> tokens) {
            var supplyKey = "supplyKey";
            return hapiTest(flattened(
                    createTokensAndAccounts(),
                    newKeyNamed(supplyKey),
                    cryptoCreate(ASSOCIATE_ACCOUNT).balance(ONE_HUNDRED_HBARS).key(supplyKey),
                    tokenAssociate(ASSOCIATE_ACCOUNT, tokens).payingWith(OWNER).via("associateTxn"),
                    validateChargedUsdWithin(
                            "associateTxn", EXPECTED_ASSOCIATE_ONE_TOKEN_PRICE, ALLOWED_DIFFERENCE_PERCENTAGE)));
        }

        private Stream<DynamicTest> associateBulkToken(List<String> tokens) {
            var supplyKey = "supplyKey";
            return hapiTest(flattened(
                    createTokensAndAccounts(),
                    newKeyNamed(supplyKey),
                    cryptoCreate(ASSOCIATE_ACCOUNT).balance(ONE_HUNDRED_HBARS).key(supplyKey),
                    tokenAssociate(ASSOCIATE_ACCOUNT, tokens).payingWith(OWNER).via("associateTxn"),
                    validateChargedUsdWithin("associateTxn", EXPECTED_ASSOCIATE_BULK_PRICE * tokens.size(), 15)));
        }

        private Stream<DynamicTest> dissociateToken(String[] tokens) {
            var supplyKey = "supplyKey";
            return hapiTest(flattened(
                    createTokensAndAccounts(),
                    newKeyNamed(supplyKey),
                    cryptoCreate(ASSOCIATE_ACCOUNT).balance(ONE_HUNDRED_HBARS).key(supplyKey),
                    tokenAssociate(ASSOCIATE_ACCOUNT, tokens).payingWith(OWNER),
                    tokenDissociate(ASSOCIATE_ACCOUNT, tokens).payingWith(OWNER).via("dissociateTxn"),
                    validateChargedUsdWithin(
                            "dissociateTxn", EXPECTED_DISSOCIATE_TOKEN_PRICE, ALLOWED_DIFFERENCE_PERCENTAGE)));
        }

        private Stream<DynamicTest> updateBulkNftTokens(int mintAmount, List<Long> updateAmounts) {
            var supplyKey = "supplyKey";
            return hapiTest(
                    newKeyNamed(supplyKey),
                    cryptoCreate(OWNER).balance(ONE_HUNDRED_HBARS).key(supplyKey),
                    tokenCreate(NFT_TOKEN)
                            .treasury(OWNER)
                            .tokenType(NON_FUNGIBLE_UNIQUE)
                            .supplyKey(supplyKey)
                            .supplyType(TokenSupplyType.INFINITE)
                            .initialSupply(0),
                    mintToken(
                                    NFT_TOKEN,
                                    IntStream.range(0, mintAmount)
                                            .mapToObj(a -> ByteString.copyFromUtf8(String.valueOf(a)))
                                            .toList())
                            .payingWith(OWNER)
                            .signedBy(supplyKey)
                            .blankMemo(),
                    tokenUpdateNfts(NFT_TOKEN, TOKEN_UPDATE_METADATA, updateAmounts)
                            .payingWith(OWNER)
                            .signedBy(supplyKey)
                            .blankMemo()
                            .via("updateTxn"),
                    validateChargedUsdWithin(
                            "updateTxn",
                            EXPECTED_NFT_UPDATE_PRICE * updateAmounts.size(),
                            ALLOWED_DIFFERENCE_PERCENTAGE));
        }
    }
}
