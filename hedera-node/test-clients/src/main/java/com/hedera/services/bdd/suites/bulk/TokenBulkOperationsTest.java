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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;

import java.util.stream.IntStream;
import java.util.stream.Stream;

import static com.hedera.services.bdd.junit.TestTags.TOKEN;
import static com.hedera.services.bdd.spec.HapiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.mintToken;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.validateChargedUsdWithin;

@Tag(TOKEN)
@HapiTestLifecycle
@DisplayName("Token Bulk Operation")
public class TokenBulkOperationsTest  extends BulkOperationsBase {

    private static final String NFT_TXN = "nftTxn";

    private static final double ALLOWED_DIFFERENCE_PERCENTAGE = 0.01;
    private static final double EXPECTED_FT_MINT_PRICE = 0.001;
    private static final double EXPECTED_NFT_MINT_PRICE = 0.02;

    @Nested
    @DisplayName("without custom fees")
    class BulkTokenOperationsWithoutCustomFees {

        @HapiTest
        final Stream<DynamicTest> mintTokens() {
            var nftSupplyKey = "nftSupplyKey";
            return defaultHapiSpec("NFT without custom fees bulk mint results in correct fee")
                    .given(
                            createTokensAndAccounts())
                    .when(
                            mintToken(
                                    NFT_TOKEN,
                                    IntStream.range(0, 1)
                                            .mapToObj(a -> ByteString.copyFromUtf8(String.valueOf(a)))
                                            .toList())
                                    .payingWith(OWNER)
                                    .signedBy(nftSupplyKey)
                                    .blankMemo()
                                    .via(NFT_TXN))
                    .then(
                            validateChargedUsdWithin(NFT_TXN, EXPECTED_NFT_MINT_PRICE, ALLOWED_DIFFERENCE_PERCENTAGE));
        }
    }
}
