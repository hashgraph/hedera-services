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

package com.hedera.services.bdd.suites.crypto;

import static com.hedera.services.bdd.junit.TestTags.CRYPTO;
import static com.hedera.services.bdd.spec.HapiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenAirdrop;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.moving;
import static com.hedera.services.bdd.suites.HapiSuite.ONE_HUNDRED_HBARS;
import static com.hederahashgraph.api.proto.java.TokenType.FUNGIBLE_COMMON;

import com.hedera.services.bdd.junit.HapiTest;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Tag;

@Tag(CRYPTO)
public class TokenAirdropSuite {
    private static final String SENDER = "sender";
    private static final String RECEIVER = "receiver";

    @HapiTest
    final Stream<DynamicTest> tokenAirdropTest() {
        final var customFeeCollector = "customFeeCollector";
        final var nonTreasurySender = "nonTreasurySender";
        final var fungibleToken = "fungibleToken";

        return defaultHapiSpec("tokenAirdrop")
                .given(
                        cryptoCreate(nonTreasurySender).balance(ONE_HUNDRED_HBARS),
                        cryptoCreate(SENDER).balance(ONE_HUNDRED_HBARS),
                        cryptoCreate(RECEIVER),
                        cryptoCreate(customFeeCollector),
                        tokenCreate(fungibleToken)
                                .treasury(SENDER)
                                .tokenType(FUNGIBLE_COMMON)
                                .initialSupply(100L),
                        tokenAirdrop(moving(1, fungibleToken).between(SENDER, nonTreasurySender))
                                .payingWith(SENDER))
                .when()
                .then();
    }
}
