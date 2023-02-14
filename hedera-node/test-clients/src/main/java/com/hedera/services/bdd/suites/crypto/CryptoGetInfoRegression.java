/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
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

import static com.hedera.services.bdd.spec.HapiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.assertions.AccountInfoAsserts.accountWith;
import static com.hedera.services.bdd.spec.keys.KeyShape.SIMPLE;
import static com.hedera.services.bdd.spec.keys.KeyShape.listOf;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountInfo;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoDelete;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.grantTokenKyc;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.mintToken;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenAssociate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.moving;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.movingUnique;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_DELETED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_PAYER_BALANCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_TX_FEE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SIGNATURE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NOT_SUPPORTED;
import static com.hederahashgraph.api.proto.java.TokenType.NON_FUNGIBLE_UNIQUE;

import com.google.protobuf.ByteString;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.keys.KeyShape;
import com.hedera.services.bdd.suites.HapiSuite;
import com.hederahashgraph.api.proto.java.TokenSupplyType;
import com.hederahashgraph.api.proto.java.TokenType;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class CryptoGetInfoRegression extends HapiSuite {
    static final Logger log = LogManager.getLogger(CryptoGetInfoRegression.class);

    public static void main(String... args) {
        new CryptoGetInfoRegression().runSuiteSync();
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }

    @Override
    public List<HapiSpec> getSpecsInSuite() {
        return List.of(
                new HapiSpec[] {
                    failsForDeletedAccount(),
                    failsForMissingAccount(),
                    failsForMissingPayment(),
                    failsForInsufficientPayment(),
                    failsForMalformedPayment(),
                    failsForUnfundablePayment(),
                    succeedsNormally(),
                    fetchesOnlyALimitedTokenAssociations()
                });
    }

    /** For Demo purpose : The limit on each account info and account balance queries is set to 5 */
    private HapiSpec fetchesOnlyALimitedTokenAssociations() {
        final int infoLimit = 3;
        final var account = "test";
        final var aKey = "tokenKey";
        final var token1 = "token1";
        final var token2 = "token2";
        final var token3 = "token3";
        final var token4 = "token4";
        final var token5 = "token5";
        final var token6 = "token6";
        final var token7 = "token7";
        final var token8 = "token8";
        return defaultHapiSpec("FetchesOnlyALimitedTokenAssociations")
                .given(
                        fileUpdate(APP_PROPERTIES)
                                .payingWith(ADDRESS_BOOK_CONTROL)
                                .overridingProps(Map.of("tokens.maxRelsPerInfoQuery", "" + 1)),
                        newKeyNamed(aKey),
                        cryptoCreate(account).balance(ONE_HUNDRED_HBARS),
                        cryptoCreate(TOKEN_TREASURY).balance(ONE_HUNDRED_HBARS),
                        tokenCreate(token1)
                                .supplyType(TokenSupplyType.FINITE)
                                .tokenType(TokenType.FUNGIBLE_COMMON)
                                .treasury(TOKEN_TREASURY)
                                .maxSupply(500)
                                .kycKey(aKey)
                                .initialSupply(100),
                        tokenCreate(token2)
                                .supplyType(TokenSupplyType.FINITE)
                                .tokenType(TokenType.FUNGIBLE_COMMON)
                                .treasury(TOKEN_TREASURY)
                                .maxSupply(500)
                                .kycKey(aKey)
                                .initialSupply(100),
                        tokenCreate(token3)
                                .supplyType(TokenSupplyType.FINITE)
                                .tokenType(TokenType.FUNGIBLE_COMMON)
                                .treasury(TOKEN_TREASURY)
                                .maxSupply(500)
                                .kycKey(aKey)
                                .initialSupply(100),
                        tokenCreate(token4)
                                .supplyType(TokenSupplyType.FINITE)
                                .tokenType(TokenType.FUNGIBLE_COMMON)
                                .treasury(TOKEN_TREASURY)
                                .maxSupply(500)
                                .kycKey(aKey)
                                .initialSupply(100),
                        tokenCreate(token5)
                                .supplyType(TokenSupplyType.FINITE)
                                .tokenType(TokenType.FUNGIBLE_COMMON)
                                .treasury(TOKEN_TREASURY)
                                .maxSupply(500)
                                .kycKey(aKey)
                                .initialSupply(100),
                        tokenCreate(token6)
                                .supplyType(TokenSupplyType.FINITE)
                                .tokenType(TokenType.FUNGIBLE_COMMON)
                                .treasury(TOKEN_TREASURY)
                                .maxSupply(500)
                                .kycKey(aKey)
                                .initialSupply(100),
                        tokenCreate(token7)
                                .supplyType(TokenSupplyType.FINITE)
                                .tokenType(NON_FUNGIBLE_UNIQUE)
                                .treasury(TOKEN_TREASURY)
                                .maxSupply(10L)
                                .initialSupply(0L)
                                .kycKey(aKey)
                                .supplyKey(aKey),
                        tokenCreate(token8)
                                .supplyType(TokenSupplyType.FINITE)
                                .tokenType(NON_FUNGIBLE_UNIQUE)
                                .treasury(TOKEN_TREASURY)
                                .maxSupply(10L)
                                .initialSupply(0L)
                                .kycKey(aKey)
                                .supplyKey(aKey),
                        mintToken(
                                token7,
                                List.of(
                                        ByteString.copyFromUtf8("a"),
                                        ByteString.copyFromUtf8("b"))),
                        mintToken(token8, List.of(ByteString.copyFromUtf8("a"))))
                .when(
                        tokenAssociate(
                                account, token1, token2, token3, token4, token5, token6, token7,
                                token8),
                        grantTokenKyc(token1, account),
                        grantTokenKyc(token2, account),
                        grantTokenKyc(token3, account),
                        grantTokenKyc(token4, account),
                        grantTokenKyc(token5, account),
                        grantTokenKyc(token6, account),
                        grantTokenKyc(token7, account),
                        grantTokenKyc(token8, account),
                        cryptoTransfer(
                                moving(10L, token1).between(TOKEN_TREASURY, account),
                                moving(20L, token2).between(TOKEN_TREASURY, account),
                                moving(30L, token3).between(TOKEN_TREASURY, account)),
                        cryptoTransfer(
                                moving(40L, token4).between(TOKEN_TREASURY, account),
                                moving(50L, token5).between(TOKEN_TREASURY, account),
                                moving(60L, token6).between(TOKEN_TREASURY, account)),
                        cryptoTransfer(
                                movingUnique(token7, 1, 2).between(TOKEN_TREASURY, account),
                                movingUnique(token8, 1).between(TOKEN_TREASURY, account)))
                .then(
                        fileUpdate(APP_PROPERTIES)
                                .payingWith(ADDRESS_BOOK_CONTROL)
                                .overridingProps(
                                        Map.of("tokens.maxRelsPerInfoQuery", "" + infoLimit)),
                        getAccountInfo(account).hasTokenRelationShipCount(infoLimit).logged());
    }

    private HapiSpec succeedsNormally() {
        long balance = 1_234_567L;
        long autoRenew = 6999999L;
        long sendThresh = 1_111L;
        long receiveThresh = 2_222L;
        long expiry = Instant.now().getEpochSecond() + autoRenew;
        KeyShape misc = listOf(SIMPLE, listOf(2));

        return defaultHapiSpec("SucceedsNormally")
                .given(newKeyNamed("misc").shape(misc))
                .when(
                        cryptoCreate("noStakingTarget").key("misc").balance(balance),
                        cryptoCreate("target").key("misc").balance(balance).stakedNodeId(0L),
                        cryptoCreate("targetWithStakedAccountId")
                                .key("misc")
                                .balance(balance)
                                .stakedAccountId("0.0.20"))
                .then(
                        getAccountInfo("noStakingTarget")
                                .has(
                                        accountWith()
                                                .accountId("noStakingTarget")
                                                .stakedNodeId(-1L) // stakedNodeId is -1 only if no
                                                // staking info is present. Will be 0
                                                // if staked account id is present.
                                                .noStakedAccountId()
                                                .key("misc")
                                                .balance(balance))
                                .logged(),
                        getAccountInfo("target")
                                .has(
                                        accountWith()
                                                .accountId("target")
                                                .noStakingNodeId()
                                                .key("misc")
                                                .balance(balance))
                                .logged(),
                        getAccountInfo("targetWithStakedAccountId")
                                .has(
                                        accountWith()
                                                .accountId("targetWithStakedAccountId")
                                                .stakedAccountId("0.0.20")
                                                .key("misc")
                                                .balance(balance))
                                .logged());
    }

    private HapiSpec failsForMissingAccount() {
        return defaultHapiSpec("FailsForMissingAccount")
                .given()
                .when()
                .then(getAccountInfo("1.2.3").hasCostAnswerPrecheck(INVALID_ACCOUNT_ID));
    }

    private HapiSpec failsForMalformedPayment() {
        return defaultHapiSpec("FailsForMalformedPayment")
                .given(newKeyNamed("wrong").shape(SIMPLE))
                .when()
                .then(
                        getAccountInfo(GENESIS)
                                .signedBy("wrong")
                                .hasAnswerOnlyPrecheck(INVALID_SIGNATURE));
    }

    private HapiSpec failsForUnfundablePayment() {
        long everything = 1_234L;
        return defaultHapiSpec("FailsForUnfundablePayment")
                .given(cryptoCreate("brokePayer").balance(everything))
                .when()
                .then(
                        getAccountInfo(GENESIS)
                                .payingWith("brokePayer")
                                .nodePayment(everything)
                                .hasAnswerOnlyPrecheck(INSUFFICIENT_PAYER_BALANCE));
    }

    private HapiSpec failsForInsufficientPayment() {
        return defaultHapiSpec("FailsForInsufficientPayment")
                .given()
                .when()
                .then(
                        getAccountInfo(GENESIS)
                                .nodePayment(1L)
                                .hasAnswerOnlyPrecheck(INSUFFICIENT_TX_FEE));
    }

    private HapiSpec failsForMissingPayment() {
        return defaultHapiSpec("FailsForMissingPayment")
                .given()
                .when()
                .then(
                        getAccountInfo(GENESIS)
                                .useEmptyTxnAsAnswerPayment()
                                .hasAnswerOnlyPrecheck(NOT_SUPPORTED));
    }

    private HapiSpec failsForDeletedAccount() {
        return defaultHapiSpec("FailsForDeletedAccount")
                .given(cryptoCreate("toBeDeleted"))
                .when(cryptoDelete("toBeDeleted").transfer(GENESIS))
                .then(getAccountInfo("toBeDeleted").hasCostAnswerPrecheck(ACCOUNT_DELETED));
    }
}
