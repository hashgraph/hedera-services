/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.hedera.services.bdd.suites.hip904;

import static com.hedera.services.bdd.junit.TestTags.CRYPTO;
import static com.hedera.services.bdd.spec.HapiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.mintToken;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenAirdrop;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenClaimAirdrop;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.token.HapiTokenClaimAirdrop.pendingAirdrop;
import static com.hedera.services.bdd.spec.transactions.token.HapiTokenClaimAirdrop.pendingNFTAirdrop;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.moving;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.movingUnique;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.overriding;
import static com.hedera.services.bdd.suites.HapiSuite.ONE_HUNDRED_HBARS;
import static com.hederahashgraph.api.proto.java.TokenType.FUNGIBLE_COMMON;
import static com.hederahashgraph.api.proto.java.TokenType.NON_FUNGIBLE_UNIQUE;

import com.google.protobuf.ByteString;
import com.hedera.services.bdd.junit.HapiTest;
import com.hedera.services.bdd.junit.HapiTestLifecycle;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Tag;

@Tag(CRYPTO)
@HapiTestLifecycle
@DisplayName("Claim token airdrop")
public class TokenClaimAirdropTest {
    private static String OWNER = "owner";
    private static String OWNER_2 = "owner2";
    private static String RECEIVER = "receiver";
    private static String FUNGIBLE_TOKEN = "fungibleToken";
    private static String FUNGIBLE_TOKEN_2 = "fungibleToken2";
    private static String NON_FUNGIBLE_TOKEN = "nonFungibleToken";
    private static String NFT_SUPPLY_KEY = "supplyKey";

    @HapiTest
    final Stream<DynamicTest> claimFungibleTokenAirdrop() {
        return defaultHapiSpec("should transfer fungible tokens")
                .given(
                        overriding("tokens.airdrops.enabled", "true"),
                        overriding("tokens.airdrops.claim.enabled", "true"),
                        cryptoCreate(OWNER).balance(ONE_HUNDRED_HBARS).maxAutomaticTokenAssociations(0),
                        cryptoCreate(OWNER_2).balance(ONE_HUNDRED_HBARS).maxAutomaticTokenAssociations(0),
                        cryptoCreate(RECEIVER).balance(0L),
                        tokenCreate(FUNGIBLE_TOKEN)
                                .treasury(OWNER)
                                .tokenType(FUNGIBLE_COMMON)
                                .initialSupply(1000L),
                        tokenCreate(FUNGIBLE_TOKEN_2)
                                .treasury(OWNER_2)
                                .tokenType(FUNGIBLE_COMMON)
                                .initialSupply(1000L),
                        newKeyNamed(NFT_SUPPLY_KEY),
                        tokenCreate(NON_FUNGIBLE_TOKEN)
                                .name(NON_FUNGIBLE_TOKEN)
                                .treasury(OWNER)
                                .tokenType(NON_FUNGIBLE_UNIQUE)
                                .initialSupply(0L)
                                .supplyKey(NFT_SUPPLY_KEY),
                        mintToken(
                                NON_FUNGIBLE_TOKEN,
                                List.of(ByteString.copyFromUtf8("a"), ByteString.copyFromUtf8("b"))))
                .when(
                        // do pending airdrop
                        tokenAirdrop(moving(10, FUNGIBLE_TOKEN).between(OWNER, RECEIVER))
                                .payingWith(OWNER),
                        tokenAirdrop(moving(10, FUNGIBLE_TOKEN_2).between(OWNER_2, RECEIVER))
                                .payingWith(OWNER_2),
                        tokenAirdrop(movingUnique(NON_FUNGIBLE_TOKEN, 1).between(OWNER, RECEIVER))
                                .payingWith(OWNER),

                        // do claim
                        tokenClaimAirdrop(
                                pendingAirdrop(OWNER, RECEIVER, FUNGIBLE_TOKEN),
                                pendingAirdrop(OWNER_2, RECEIVER, FUNGIBLE_TOKEN_2),
                                pendingNFTAirdrop(OWNER, RECEIVER, NON_FUNGIBLE_TOKEN, 1)))
                .then( // assert balance fungible tokens
                        getAccountBalance(OWNER).hasTokenBalance(FUNGIBLE_TOKEN, 990),
                        getAccountBalance(OWNER_2).hasTokenBalance(FUNGIBLE_TOKEN_2, 990),
                        getAccountBalance(RECEIVER).hasTokenBalance(FUNGIBLE_TOKEN, 10),
                        getAccountBalance(RECEIVER).hasTokenBalance(FUNGIBLE_TOKEN_2, 10),
                        // assert balances NFT
                        getAccountBalance(RECEIVER).hasTokenBalance(NON_FUNGIBLE_TOKEN, 1),

                        // should be free
                        getAccountBalance(RECEIVER).hasTinyBars(0L));
    }
}
