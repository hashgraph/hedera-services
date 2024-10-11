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

import com.google.protobuf.ByteString;
import com.hedera.services.bdd.junit.HapiTest;
import com.hedera.services.bdd.junit.HapiTestLifecycle;
import com.hedera.services.bdd.junit.support.TestLifecycle;
import edu.umd.cs.findbugs.annotations.NonNull;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;

import java.util.Arrays;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static com.hedera.services.bdd.junit.TestTags.TOKEN;
import static com.hedera.services.bdd.spec.HapiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.burnToken;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.mintToken;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.validateChargedUsdWithin;

@Tag(TOKEN)
@HapiTestLifecycle
@DisplayName("Token Bulk Operation")
public class TokenBulkOperationsTest  extends BulkOperationsBase {

    private static final String ONE_NFT_TXN = "oneNftTxn";
    private static final String NFT_TXN = "nftTxn";
    private static final String NFT_BURN_TXN = "nftBurnTxn";
    private static final String FT_TXN = "ftTxn";

    private static final double ALLOWED_DIFFERENCE_PERCENTAGE = 0.01;
    private static final double EXPECTED_FT_MINT_PRICE = 0.001;
    private static final double EXPECTED_NFT_MINT_PRICE = 0.02;
    private static final double EXPECTED_NFT_BURN_PRICE = 0.001;


    @BeforeAll
    static void beforeAll(@NonNull final TestLifecycle lifecycle) {
        lifecycle.doAdhoc(createTokensAndAccounts());
    }

    @Nested
    @DisplayName("without custom fees")
    class BulkTokenOperationsWithoutCustomFees {

        @HapiTest
        final Stream<DynamicTest> mintOneNftToken() {
            var nftSupplyKey = "nftSupplyKey";
            return defaultHapiSpec("NFT without custom fees mint one NFT token results in correct fee")
                    .given()
                    .when(
                            mintToken(
                                    NFT_TOKEN,
                                    IntStream.range(0, 1)
                                            .mapToObj(a -> ByteString.copyFromUtf8(String.valueOf(a)))
                                            .toList())
                                    .payingWith(OWNER)
                                    .signedBy(nftSupplyKey)
                                    .blankMemo()
                                    .via(ONE_NFT_TXN))
                    .then(
                            validateChargedUsdWithin(ONE_NFT_TXN, EXPECTED_NFT_MINT_PRICE, ALLOWED_DIFFERENCE_PERCENTAGE));
        }

        @HapiTest
        final Stream<DynamicTest> mintNftTokens() {
            var nftSupplyKey = "nftSupplyKey";
            return defaultHapiSpec("NFT without custom fees bulk mint results in correct fee")
                    .given()
                    .when(
                            mintToken(
                                    NFT_TOKEN,
                                    IntStream.range(0, 10)
                                            .mapToObj(a -> ByteString.copyFromUtf8(String.valueOf(a)))
                                            .toList())
                                    .payingWith(OWNER)
                                    .signedBy(nftSupplyKey)
                                    .blankMemo()
                                    .via(NFT_TXN))
                    .then(
                            validateChargedUsdWithin(NFT_TXN, EXPECTED_NFT_MINT_PRICE * 10, ALLOWED_DIFFERENCE_PERCENTAGE));
        }

        @HapiTest
        final Stream<DynamicTest> burnOneNFtToken() {
            var nftSupplyKey = "nftSupplyKey";
            return defaultHapiSpec("NFT without custom fees burn one NFT token results in correct fee")
                    .given(
                            mintToken(
                                    NFT_BURN_TOKEN,
                                    IntStream.range(0, 10)
                                            .mapToObj(a -> ByteString.copyFromUtf8(String.valueOf(a)))
                                            .toList())
                                    .payingWith(OWNER)
                                    .signedBy(nftSupplyKey)
                                    .blankMemo()
                                    .via(NFT_BURN_TXN))
                    .when(
                            burnToken(NFT_BURN_TOKEN, Arrays.asList(1L))
                                    .payingWith(OWNER)
                                    .blankMemo()
                                    .via(NFT_BURN_TXN))
                    .then(
                            validateChargedUsdWithin(NFT_BURN_TXN, EXPECTED_NFT_BURN_PRICE, ALLOWED_DIFFERENCE_PERCENTAGE));
        }

        @HapiTest
        final Stream<DynamicTest> burnNFtTokens() {
            var nftSupplyKey = "nftSupplyKey";
            return defaultHapiSpec("NFT without custom fees burn one NFT token results in correct fee")
                    .given(
                            mintToken(
                                    NFT_BURN_TOKEN,
                                    IntStream.range(0, 10)
                                            .mapToObj(a -> ByteString.copyFromUtf8(String.valueOf(a)))
                                            .toList())
                                    .payingWith(OWNER)
                                    .signedBy(nftSupplyKey)
                                    .blankMemo()
                                    .via(NFT_BURN_TXN))
                    .when(
                            burnToken(NFT_BURN_TOKEN, Arrays.asList(1L, 2L, 3L, 4L, 5L))
                                    .payingWith(OWNER)
                                    .blankMemo()
                                    .via(NFT_BURN_TXN))
                    .then(
                            validateChargedUsdWithin(NFT_BURN_TXN, EXPECTED_NFT_BURN_PRICE * 5, ALLOWED_DIFFERENCE_PERCENTAGE));
        }
    }
}
