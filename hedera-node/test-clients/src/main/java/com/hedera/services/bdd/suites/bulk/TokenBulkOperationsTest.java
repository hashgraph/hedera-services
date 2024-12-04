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
    private static final double EXPECTED_ASSOCIATE_TOKEN_PRICE = 0.05;
    private static final double EXPECTED_DISSOCIATE_TOKEN_PRICE = 0.05;
    private static final double EXPECTED_NFT_MINT_PRICE = 0.02;
    private static final double EXPECTED_FT_MINT_PRICE = 0.001;
    private static final double EXPECTED_TOKEN_BURN_PRICE = 0.001;
    private static final double EXPECTED_NFT_UPDATE_PRICE = 0.001;

    @Nested
    @DisplayName("without custom fees")
    class BulkTokenOperationsWithoutCustomFeesTest {

        @HapiTest
        final Stream<DynamicTest> mintOneNftTokenWithoutCustomFees() {
            return mintBulkNftAndValidateFees(1);
        }

        @HapiTest
        final Stream<DynamicTest> mintBulkNftTokensWithoutCustomFees() {
            return mintBulkNftAndValidateFees(10);
        }

        @HapiTest
        final Stream<DynamicTest> mintOneFtTokenWithoutCustomFees() {
            return mintFtAndValidateFees(1);
        }

        @HapiTest
        final Stream<DynamicTest> mintFtTokensWithoutCustomFees() {
            return mintFtAndValidateFees(10);
        }

        @HapiTest
        final Stream<DynamicTest> mintOneHundredFtTokensWithoutCustomFees() {
            return mintFtAndValidateFees(100);
        }

        @HapiTest
        final Stream<DynamicTest> burnOneNFtTokenWithoutCustomFees() {
            return burnNftAndValidateFees(10, Arrays.asList(1L));
        }

        @HapiTest
        final Stream<DynamicTest> burnNftTokensWithoutCustomFees() {
            return burnNftAndValidateFees(10, Arrays.asList(1L, 2L, 3L, 4L, 5L));
        }

        @HapiTest
        final Stream<DynamicTest> burnTenNftTokensWithoutCustomFees() {
            return burnNftAndValidateFees(10, Arrays.asList(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L));
        }

        @HapiTest
        final Stream<DynamicTest> associateOneFtTokenWithoutCustomFees() {
            return associateBulkTokensAndValidateFees(List.of(FT_TOKEN));
        }

        @HapiTest
        final Stream<DynamicTest> associateBulkFtTokensWithoutCustomFees() {
            return associateBulkTokensAndValidateFees(List.of(FT_TOKEN, NFT_TOKEN, NFT_BURN_TOKEN, NFT_BURN_ONE_TOKEN));
        }

        @HapiTest
        final Stream<DynamicTest> dissociateOneTokenWithoutCustomFees() {
            return dissociateTokensAndValidateFees(new String[] {FT_TOKEN});
        }

        @HapiTest
        final Stream<DynamicTest> dissociateTokensWithoutCustomFees() {
            return dissociateTokensAndValidateFees(
                    new String[] {FT_TOKEN, NFT_TOKEN, NFT_BURN_TOKEN, NFT_BURN_ONE_TOKEN});
        }

        @HapiTest
        final Stream<DynamicTest> updateOneNftTokenWithoutCustomFees() {
            return updateBulkNftTokensAndValidateFees(10, Arrays.asList(1L));
        }

        @HapiTest
        final Stream<DynamicTest> updateBulkNftTokensWithoutCustomFees() {
            return updateBulkNftTokensAndValidateFees(10, Arrays.asList(1L, 2L, 3L, 4L, 5L));
        }

        @HapiTest
        final Stream<DynamicTest> updateTenBulkNftTokensWithoutCustomFees() {
            return updateBulkNftTokensAndValidateFees(10, Arrays.asList(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L));
        }

        // define reusable methods
        private Stream<DynamicTest> mintBulkNftAndValidateFees(final int rangeAmount) {
            final var supplyKey = "supplyKey";
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

        private Stream<DynamicTest> mintFtAndValidateFees(final int tokenAmount) {
            final var supplyKey = "supplyKey";
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

        private Stream<DynamicTest> burnNftAndValidateFees(final int mintAmount, final List<Long> burnAmounts) {
            final var supplyKey = "supplyKey";
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

        private Stream<DynamicTest> associateBulkTokensAndValidateFees(final List<String> tokens) {
            final var supplyKey = "supplyKey";
            return hapiTest(flattened(
                    createTokensAndAccounts(),
                    newKeyNamed(supplyKey),
                    cryptoCreate(ASSOCIATE_ACCOUNT).balance(ONE_HUNDRED_HBARS).key(supplyKey),
                    tokenAssociate(ASSOCIATE_ACCOUNT, tokens)
                            .payingWith(ASSOCIATE_ACCOUNT)
                            .via("associateTxn"),
                    validateChargedUsdWithin(
                            "associateTxn",
                            EXPECTED_ASSOCIATE_TOKEN_PRICE * tokens.size(),
                            ALLOWED_DIFFERENCE_PERCENTAGE)));
        }

        private Stream<DynamicTest> dissociateTokensAndValidateFees(final String[] tokens) {
            final var supplyKey = "supplyKey";
            return hapiTest(flattened(
                    createTokensAndAccounts(),
                    newKeyNamed(supplyKey),
                    cryptoCreate(ASSOCIATE_ACCOUNT).balance(ONE_HUNDRED_HBARS).key(supplyKey),
                    tokenAssociate(ASSOCIATE_ACCOUNT, tokens).payingWith(ASSOCIATE_ACCOUNT),
                    tokenDissociate(ASSOCIATE_ACCOUNT, tokens)
                            .payingWith(ASSOCIATE_ACCOUNT)
                            .via("dissociateTxn"),
                    validateChargedUsdWithin(
                            "dissociateTxn", EXPECTED_DISSOCIATE_TOKEN_PRICE, ALLOWED_DIFFERENCE_PERCENTAGE)));
        }

        private Stream<DynamicTest> updateBulkNftTokensAndValidateFees(
                final int mintAmount, final List<Long> updateAmounts) {
            final var supplyKey = "supplyKey";
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
