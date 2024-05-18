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
import static com.hedera.services.bdd.spec.assertions.AccountDetailsAsserts.accountDetailsWith;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountDetails;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountDetailsNoPayment;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountInfo;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoApproveAllowance;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.mintToken;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenAssociate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromTo;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.movingUnique;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.suites.HapiSuite.FUNDING;
import static com.hedera.services.bdd.suites.HapiSuite.GENESIS;
import static com.hedera.services.bdd.suites.HapiSuite.NODE;
import static com.hedera.services.bdd.suites.HapiSuite.ONE_HBAR;
import static com.hedera.services.bdd.suites.HapiSuite.ONE_HUNDRED_HBARS;
import static com.hedera.services.bdd.suites.HapiSuite.TOKEN_TREASURY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NOT_SUPPORTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.TokenType.NON_FUNGIBLE_UNIQUE;

import com.google.protobuf.ByteString;
import com.hedera.services.bdd.junit.HapiTest;
import com.hederahashgraph.api.proto.java.TokenSupplyType;
import com.hederahashgraph.api.proto.java.TokenType;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Tag;

@Tag(CRYPTO)
public class RandomOps {
    @HapiTest
    final Stream<DynamicTest> getAccountDetailsDemo() {
        final String owner = "owner";
        final String spender = "spender";
        final String token = "token";
        final String nft = "nft";
        return defaultHapiSpec("getAccountDetailsDemo")
                .given(
                        newKeyNamed("supplyKey"),
                        cryptoCreate(owner).balance(ONE_HUNDRED_HBARS).maxAutomaticTokenAssociations(10),
                        cryptoCreate(spender).balance(ONE_HUNDRED_HBARS),
                        cryptoCreate(TOKEN_TREASURY)
                                .balance(100 * ONE_HUNDRED_HBARS)
                                .maxAutomaticTokenAssociations(10),
                        tokenCreate(token)
                                .tokenType(TokenType.FUNGIBLE_COMMON)
                                .supplyType(TokenSupplyType.FINITE)
                                .supplyKey("supplyKey")
                                .maxSupply(1000L)
                                .initialSupply(10L)
                                .treasury(TOKEN_TREASURY),
                        tokenCreate(nft)
                                .maxSupply(10L)
                                .initialSupply(0)
                                .supplyType(TokenSupplyType.FINITE)
                                .tokenType(NON_FUNGIBLE_UNIQUE)
                                .supplyKey("supplyKey")
                                .treasury(TOKEN_TREASURY),
                        tokenAssociate(owner, token),
                        tokenAssociate(owner, nft),
                        mintToken(
                                        nft,
                                        List.of(
                                                ByteString.copyFromUtf8("a"),
                                                ByteString.copyFromUtf8("b"),
                                                ByteString.copyFromUtf8("c")))
                                .via("nftTokenMint"),
                        mintToken(token, 500L).via("tokenMint"),
                        cryptoTransfer(movingUnique(nft, 1L, 2L, 3L).between(TOKEN_TREASURY, owner)))
                .when(cryptoApproveAllowance()
                        .payingWith(owner)
                        .addCryptoAllowance(owner, spender, 100L)
                        .addTokenAllowance(owner, token, spender, 100L)
                        .addNftAllowance(owner, nft, spender, true, List.of(1L))
                        .via("approveTxn")
                        .fee(ONE_HBAR)
                        .blankMemo()
                        .logged())
                .then(
                        /* NetworkGetExecutionTime requires superuser payer */
                        getAccountDetails(owner)
                                .payingWith(owner)
                                .hasCostAnswerPrecheck(NOT_SUPPORTED)
                                .hasAnswerOnlyPrecheck(NOT_SUPPORTED),
                        getAccountDetails(owner)
                                .payingWith(GENESIS)
                                .has(accountDetailsWith()
                                        .cryptoAllowancesCount(1)
                                        .nftApprovedForAllAllowancesCount(1)
                                        .tokenAllowancesCount(1)
                                        .cryptoAllowancesContaining(spender, 100L)
                                        .tokenAllowancesContaining(token, spender, 100L)),
                        getAccountDetailsNoPayment(owner)
                                .payingWith(GENESIS)
                                .has(accountDetailsWith()
                                        .cryptoAllowancesCount(2)
                                        .nftApprovedForAllAllowancesCount(1)
                                        .tokenAllowancesCount(2)
                                        .cryptoAllowancesContaining(spender, 100L)
                                        .tokenAllowancesContaining(token, spender, 100L))
                                .hasCostAnswerPrecheck(NOT_SUPPORTED));
    }

    @HapiTest
    final Stream<DynamicTest> retryLimitDemo() {
        return defaultHapiSpec("RetryLimitDemo")
                .given()
                .when()
                .then(
                        getAccountInfo("0.0.2").hasRetryAnswerOnlyPrecheck(OK).setRetryLimit(5),
                        cryptoTransfer(tinyBarsFromTo(GENESIS, NODE, 1L))
                                .hasRetryPrecheckFrom(OK)
                                .setRetryLimit(3),
                        cryptoTransfer(tinyBarsFromTo(GENESIS, FUNDING, 7L)));
    }
}
