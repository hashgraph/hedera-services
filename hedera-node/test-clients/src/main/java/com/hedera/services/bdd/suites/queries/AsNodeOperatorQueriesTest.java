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

package com.hedera.services.bdd.suites.queries;

import static com.hedera.services.bdd.junit.TestTags.CRYPTO;
import static com.hedera.services.bdd.spec.HapiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.HapiSpec.hapiTest;
import static com.hedera.services.bdd.spec.assertions.AccountInfoAsserts.lessThan;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getContractBytecode;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getContractInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getFileContents;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getFileInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getScheduleInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTokenInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTopicInfo;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.createTopic;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.scheduleCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.uploadInitCode;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromTo;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.doAdhoc;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sleepFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.verify;
import static com.hedera.services.bdd.suites.HapiSuite.DEFAULT_PAYER;
import static com.hedera.services.bdd.suites.HapiSuite.ONE_HUNDRED_HBARS;
import static com.hederahashgraph.api.proto.java.TokenType.NON_FUNGIBLE_UNIQUE;
import static org.assertj.core.api.Assertions.assertThat;

import com.hedera.services.bdd.junit.HapiTest;
import com.hedera.services.bdd.junit.HapiTestLifecycle;
import com.hedera.services.bdd.junit.support.TestLifecycle;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenSupplyType;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;

@Tag(CRYPTO)
@HapiTestLifecycle
@DisplayName("Node Operator Queries")
/**
 * A class with Node Operator Queries tests
 */
public class AsNodeOperatorQueriesTest extends NodeOperatorQueriesBase {

    @Nested
    @DisplayName("verify payer balance")
    /**
     * A class with Node Operator tests that verify the payer balance
     */
    class PerformNodeOperatorQueryAndVerifyPayerBalance {

        @BeforeAll
        static void beforeAll(@NonNull final TestLifecycle lifecycle) {
            lifecycle.doAdhoc(createAllAccountsAndTokens());
        }

        @HapiTest
        final Stream<DynamicTest> nodeOperatorQueryVerifyPayerBalanceForAccountBalance_WithoutOpContext() {
            // declare payer account balance variables
            final AtomicLong currentPayerBalance = new AtomicLong();
            final AtomicLong payerBalanceAfterFirstQuery = new AtomicLong();
            final AtomicLong payerBalanceAfterSecondQuery = new AtomicLong();
            return hapiTest(
                    cryptoCreate(NODE_OPERATOR).balance(ONE_HUNDRED_HBARS),
                    cryptoCreate(PAYER).balance(ONE_HUNDRED_HBARS),
                    getAccountBalance(PAYER).exposingBalanceTo(currentPayerBalance::set),
                    // perform paid query, pay for the query with payer account
                    // the grpc client performs the query to different ports
                    getAccountBalance(NODE_OPERATOR).payingWith(PAYER),
                    sleepFor(3_000),
                    getAccountBalance(PAYER).exposingBalanceTo(payerBalanceAfterFirstQuery::set),
                    // perform free query to local port with asNodeOperator() method
                    getAccountBalance(NODE_OPERATOR).payingWith(PAYER).asNodeOperator(),
                    sleepFor(3_000),
                    // assert payer account balance is not changed
                    getAccountBalance(PAYER).exposingBalanceTo(payerBalanceAfterSecondQuery::set),
                    doAdhoc(() -> {
                        assertThat(currentPayerBalance.get())
                                .withFailMessage("Balances are not equal!")
                                .isEqualTo(payerBalanceAfterFirstQuery.get());
                        assertThat(payerBalanceAfterFirstQuery.get())
                                .withFailMessage("Balances are not equal!")
                                .isEqualTo(payerBalanceAfterSecondQuery.get());
                    }));
        }

        @HapiTest
        final Stream<DynamicTest> nodeOperatorQueryVerifyPayerBalanceForAccountInfo_WithoutOpContext() {
            // declare payer account balance variables
            final AtomicLong currentPayerBalance = new AtomicLong();
            final AtomicLong payerBalanceAfterFirstQuery = new AtomicLong();
            final AtomicLong payerBalanceAfterSecondQuery = new AtomicLong();
            return hapiTest(
                    cryptoCreate(NODE_OPERATOR).balance(ONE_HUNDRED_HBARS),
                    cryptoCreate(PAYER).balance(ONE_HUNDRED_HBARS),
                    getAccountBalance(PAYER).exposingBalanceTo(currentPayerBalance::set),
                    // perform paid query, pay for the query with payer account
                    // the grpc client performs the query to different ports
                    getAccountInfo(NODE_OPERATOR).payingWith(PAYER),
                    sleepFor(3_000),
                    getAccountBalance(PAYER).exposingBalanceTo(payerBalanceAfterFirstQuery::set),
                    // perform free query to local port with asNodeOperator() method
                    getAccountInfo(NODE_OPERATOR).payingWith(PAYER).asNodeOperator(),
                    sleepFor(3_000),
                    // assert payer account balance is not changed
                    getAccountBalance(PAYER).exposingBalanceTo(payerBalanceAfterSecondQuery::set),
                    doAdhoc(() -> {
                        assertThat(payerBalanceAfterFirstQuery.get())
                                .withFailMessage("Balances are equal!")
                                .isNotEqualTo(currentPayerBalance.get());
                        assertThat(payerBalanceAfterFirstQuery.get())
                                .withFailMessage("Balances are not equal!")
                                .isEqualTo(payerBalanceAfterSecondQuery.get());
                    }));
        }

        /**
         * AccountInfoQuery
         *  1.) Tests that verifies the payer balance is not charged when a node operator AccountInfoQuery is performed.
         */
        @HapiTest
        final Stream<DynamicTest> nodeOperatorAccountInfoQueryNotCharged() {
            return hapiTest(
                    newKeyNamed("NODE_OPERATOR_KEY"),
                    cryptoCreate(NODE_OPERATOR).balance(ONE_HUNDRED_HBARS).key("NODE_OPERATOR_KEY"),
                    getAccountInfo(NODE_OPERATOR).payingWith(NODE_OPERATOR).asNodeOperator(),
                    getAccountBalance(NODE_OPERATOR).hasTinyBars(ONE_HUNDRED_HBARS));
        }

        /**
         * AccountInfoQuery
         *  2.)  Tests that a signed transaction is not required for the query if it is performed as node operator.
         */
        @HapiTest
        final Stream<DynamicTest> nodeOperatorAccountInfoQueryNotSigned() {
            return hapiTest(
                    newKeyNamed("NODE_OPERATOR_KEY"),
                    cryptoCreate(NODE_OPERATOR).balance(ONE_HUNDRED_HBARS).key("NODE_OPERATOR_KEY"),
                    getAccountInfo(NODE_OPERATOR)
                            .payingWith(NODE_OPERATOR)
                            .signedBy(DEFAULT_PAYER)
                            .asNodeOperator(),
                    sleepFor(3000),
                    getAccountBalance(NODE_OPERATOR).hasTinyBars(ONE_HUNDRED_HBARS));
        }

        /**
         * AccountInfoQuery
         *  3.) Tests that verifies the payer balance is charged when a AccountInfoQuery is performed.
         */
        @HapiTest
        final Stream<DynamicTest> nodeOperatorAccountInfoQueryCharged() {
            final var balance = new AtomicLong();
            return defaultHapiSpec("nodeOperatorAccountInfoQueryCharged")
                    .given(cryptoCreate(NODE_OPERATOR).balance(ONE_HUNDRED_HBARS))
                    .when(getAccountInfo(NODE_OPERATOR)
                            .payingWith(NODE_OPERATOR)
                            .via("accountInfoQueryTxn"),
                            sleepFor(1000))
                    .then(getAccountBalance(NODE_OPERATOR).exposingBalanceTo(balance::set), doAdhoc(() -> {
                        assertThat(balance.get()).isLessThan(ONE_HUNDRED_HBARS);
                    }));
        }

        /**
         * AccountBalanceQuery
         *  1.) Tests that verifies the payer balance is not charged(free query) when a node operator AccountBalanceQuery is performed.
         */
        @HapiTest
        final Stream<DynamicTest> nodeOperatorAccountBalanceQueryNotCharged() {
            return hapiTest(
                    newKeyNamed("NODE_OPERATOR_KEY"),
                    cryptoCreate(NODE_OPERATOR).balance(ONE_HUNDRED_HBARS).key("NODE_OPERATOR_KEY"),
                    getAccountBalance(NODE_OPERATOR).payingWith(NODE_OPERATOR).asNodeOperator(),
                    getAccountBalance(NODE_OPERATOR).hasTinyBars(ONE_HUNDRED_HBARS));
        }

        /**
         * AccountBalanceQuery
         *  2.)  Tests that a signed transaction is not required for the query if it is performed as node operator.
         */
        @HapiTest
        final Stream<DynamicTest> nodeOperatorAccountBalanceQueryNotSigned() {
            return hapiTest(
                    newKeyNamed("NODE_OPERATOR_KEY"),
                    cryptoCreate(NODE_OPERATOR).balance(ONE_HUNDRED_HBARS).key("NODE_OPERATOR_KEY"),
                    getAccountBalance(NODE_OPERATOR)
                            .payingWith(NODE_OPERATOR)
                            .signedBy(DEFAULT_PAYER)
                            .asNodeOperator(),
                    getAccountBalance(NODE_OPERATOR).hasTinyBars(ONE_HUNDRED_HBARS));
        }

        /**
         * TokenInfoQuery - FT
         *  1.) Tests that verifies the payer balance is not charged when a node operator TokenInfoQuery is performed.
         */
        @HapiTest
        final Stream<DynamicTest> nodeOperatorTokenInfoQueryNotCharged() {
            return hapiTest(
                    newKeyNamed("NODE_OPERATOR_KEY"),
                    cryptoCreate(NODE_OPERATOR).balance(ONE_HUNDRED_HBARS).key("NODE_OPERATOR_KEY"),
                    tokenCreate(FUNGIBLE_QUERY_TOKEN),
                    getTokenInfo(FUNGIBLE_QUERY_TOKEN).payingWith(NODE_OPERATOR).asNodeOperator(),
                    getAccountBalance(NODE_OPERATOR).hasTinyBars(ONE_HUNDRED_HBARS));
        }

        /**
         * TokenInfoQuery - FT
         *  2.) Tests that verifies the payer balance is charged when a AccountInfoQuery is performed.
         */
        @HapiTest
        final Stream<DynamicTest> nodeOperatorTokenInfoQueryCharged() {
            final var balance = new AtomicLong();
            return defaultHapiSpec("nodeOperatorTokenInfoQueryCharged")
                    .given(cryptoCreate(NODE_OPERATOR).balance(ONE_HUNDRED_HBARS), tokenCreate(FUNGIBLE_QUERY_TOKEN))
                    .when(getTokenInfo(FUNGIBLE_QUERY_TOKEN).payingWith(NODE_OPERATOR))
                    .then(
                            sleepFor(3000),
                            getAccountBalance(NODE_OPERATOR).exposingBalanceTo(balance::set),
                            doAdhoc(() -> {
                                assertThat(balance.get()).isLessThan(ONE_HUNDRED_HBARS);
                            }));
        }

        /**
         * TokenInfoQuery - FT
         *  3.) Tests that a signed transaction is not required for the query if it is performed as node operator.
         */
        @HapiTest
        final Stream<DynamicTest> nodeOperatorTokenInfoQuerySignature() {
            return hapiTest(
                    newKeyNamed("NODE_OPERATOR_KEY"),
                    cryptoCreate(NODE_OPERATOR).balance(ONE_HUNDRED_HBARS).key("NODE_OPERATOR_KEY"),
                    tokenCreate(FUNGIBLE_QUERY_TOKEN),
                    getTokenInfo(FUNGIBLE_QUERY_TOKEN)
                            .payingWith(NODE_OPERATOR)
                            .signedBy(DEFAULT_PAYER)
                            .asNodeOperator(),
                    getAccountBalance(NODE_OPERATOR).hasTinyBars(ONE_HUNDRED_HBARS));
        }

        /**
         * TokenInfoQuery - NFT
         *  1.) Tests that verifies the payer balance is not charged when a node operator TokenInfoQuery is performed.
         */
        @HapiTest
        final Stream<DynamicTest> nodeOperatorTokenInfoQueryNftNotCharged() {
            return hapiTest(
                    newKeyNamed("NODE_OPERATOR_KEY"),
                    newKeyNamed(SUPPLY_KEY),
                    newKeyNamed(WIPE_KEY),
                    cryptoCreate(TREASURY),
                    cryptoCreate(NODE_OPERATOR).balance(ONE_HUNDRED_HBARS).key("NODE_OPERATOR_KEY"),
                    tokenCreate(NON_FUNGIBLE_TOKEN)
                            .supplyType(TokenSupplyType.FINITE)
                            .tokenType(NON_FUNGIBLE_UNIQUE)
                            .treasury(TREASURY)
                            .maxSupply(12L)
                            .wipeKey(WIPE_KEY)
                            .supplyKey(SUPPLY_KEY)
                            .initialSupply(0L),
                    getTokenInfo(NON_FUNGIBLE_TOKEN).payingWith(NODE_OPERATOR).asNodeOperator(),
                    getAccountBalance(NODE_OPERATOR).hasTinyBars(ONE_HUNDRED_HBARS));
        }

        /**
         * TokenInfoQuery - NFT
         *  2.) Tests that verifies the payer balance is charged when a AccountInfoQuery is performed.
         */
        @HapiTest
        final Stream<DynamicTest> nodeOperatorTokenInfoQueryNftCharged() {
            final var balance = new AtomicLong();
            return defaultHapiSpec("nodeOperatorTokenInfoQueryCharged")
                    .given(
                            newKeyNamed(SUPPLY_KEY),
                            newKeyNamed(WIPE_KEY),
                            cryptoCreate(TREASURY),
                            cryptoCreate(NODE_OPERATOR).balance(ONE_HUNDRED_HBARS),
                            tokenCreate(NON_FUNGIBLE_TOKEN)
                                    .supplyType(TokenSupplyType.FINITE)
                                    .tokenType(NON_FUNGIBLE_UNIQUE)
                                    .treasury(TREASURY)
                                    .maxSupply(12L)
                                    .wipeKey(WIPE_KEY)
                                    .supplyKey(SUPPLY_KEY)
                                    .initialSupply(0L))
                    .when(getTokenInfo(FUNGIBLE_QUERY_TOKEN).payingWith(NODE_OPERATOR))
                    .then(
                            sleepFor(3000),
                            getAccountBalance(NODE_OPERATOR).exposingBalanceTo(balance::set),
                            doAdhoc(() -> {
                                assertThat(balance.get()).isLessThan(ONE_HUNDRED_HBARS);
                            }));
        }

        /**
         * TokenInfoQuery - NFT
         *  3.) Tests that a signed transaction is not required for the query if it is performed as node operator.
         */
        @HapiTest
        final Stream<DynamicTest> nodeOperatorTokenInfoQueryNftSignature() {
            return hapiTest(
                    newKeyNamed("NODE_OPERATOR_KEY"),
                    newKeyNamed(SUPPLY_KEY),
                    newKeyNamed(WIPE_KEY),
                    cryptoCreate(TREASURY),
                    cryptoCreate(NODE_OPERATOR).balance(ONE_HUNDRED_HBARS).key("NODE_OPERATOR_KEY"),
                    tokenCreate(NON_FUNGIBLE_TOKEN)
                            .supplyType(TokenSupplyType.FINITE)
                            .tokenType(NON_FUNGIBLE_UNIQUE)
                            .treasury(TREASURY)
                            .maxSupply(12L)
                            .wipeKey(WIPE_KEY)
                            .supplyKey(SUPPLY_KEY)
                            .initialSupply(0L),
                    getTokenInfo(FUNGIBLE_QUERY_TOKEN)
                            .payingWith(NODE_OPERATOR)
                            .signedBy(DEFAULT_PAYER)
                            .asNodeOperator(),
                    getAccountBalance(NODE_OPERATOR).hasTinyBars(ONE_HUNDRED_HBARS));
        }

        /**
         * TopicInfoQuery
         *  1.) Tests that verifies the payer balance is not charged when a node operator TopicInfoQuery is performed.
         */
        @HapiTest
        final Stream<DynamicTest> nodeOperatorTopicInfoQueryNotCharged() {
            return hapiTest(
                    newKeyNamed("NODE_OPERATOR_KEY"),
                    cryptoCreate(NODE_OPERATOR).balance(ONE_HUNDRED_HBARS).key("NODE_OPERATOR_KEY"),
                    createTopic(TOPIC),
                    getTopicInfo(TOPIC).payingWith(NODE_OPERATOR).asNodeOperator(),
                    getAccountBalance(NODE_OPERATOR).hasTinyBars(ONE_HUNDRED_HBARS));
        }

        /**
         * TopicInfoQuery
         *  2.) Tests that verifies the payer balance is charged when a TopicInfoQuery is performed.
         */
        @HapiTest
        final Stream<DynamicTest> nodeOperatorTopicInfoQueryCharged() {
            final var balance = new AtomicLong();
            return defaultHapiSpec("nodeOperatorTopicInfoQueryCharged")
                    .given(cryptoCreate(NODE_OPERATOR).balance(ONE_HUNDRED_HBARS), createTopic(TOPIC))
                    .when(getTopicInfo(TOPIC).payingWith(NODE_OPERATOR))
                    .then(
                            sleepFor(4000),
                            getAccountBalance(NODE_OPERATOR).exposingBalanceTo(balance::set),
                            verify(() -> {
                                assertThat(balance.get()).isLessThan(ONE_HUNDRED_HBARS);
                            }));
        }

        /**
         * Get File Contents tests
         */
        @HapiTest
        @DisplayName("Only node operators aren't charged for file contents queries")
        final Stream<DynamicTest> fileGetContentsQueryNodeOperatorNotCharged() {
            final var filename = "anyFile.txt";
            return hapiTest(
                    cryptoCreate(NODE_OPERATOR).balance(ONE_HUNDRED_HBARS),
                    cryptoCreate(PAYER).balance(ONE_HUNDRED_HBARS),
                    fileCreate(filename).contents("anyContent"),
                    getFileContents(filename).payingWith(NODE_OPERATOR).asNodeOperator(),
                    getFileContents(filename).payingWith(PAYER),
                    sleepFor(1_000),
                    getAccountBalance(NODE_OPERATOR).hasTinyBars(ONE_HUNDRED_HBARS),
                    getAccountBalance(PAYER).hasTinyBars(lessThan(ONE_HUNDRED_HBARS)));
        }

        @HapiTest
        @DisplayName("Only node operators don't need to sign file contents queries")
        final Stream<DynamicTest> fileGetContentsNoSigRequired() {
            final var filename = "anyFile.txt";
            final var someoneElse = "someoneElse";
            return hapiTest(
                    newKeyNamed(someoneElse),
                    cryptoCreate(NODE_OPERATOR).balance(ONE_HUNDRED_HBARS),
                    cryptoCreate(PAYER).balance(ONE_HUNDRED_HBARS),
                    fileCreate(filename).contents("anyContent"),
                    // Sign the node operator query request with a totally unrelated key, to show that there is no
                    // signature check
                    getFileContents(filename)
                            .payingWith(NODE_OPERATOR)
                            .signedBy(someoneElse)
                            .asNodeOperator()
                            .hasAnswerOnlyPrecheck(ResponseCodeEnum.OK),
                    // But the non-node operator submitter must still sign
                    getFileContents(filename)
                            .payingWith(PAYER)
                            .signedBy(someoneElse)
                            .hasAnswerOnlyPrecheck(ResponseCodeEnum.INVALID_SIGNATURE));
        }
        /**
         * Get File Info tests
         */
        @HapiTest
        @DisplayName("Only node operators aren't charged for file info queries")
        final Stream<DynamicTest> getFileInfoQueryNodeOperatorNotCharged() {
            final var filename = "anyFile.txt";
            return hapiTest(
                    cryptoCreate(NODE_OPERATOR).balance(ONE_HUNDRED_HBARS),
                    cryptoCreate(PAYER).balance(ONE_HUNDRED_HBARS),
                    fileCreate(filename).contents("anyContentAgain").payingWith(PAYER),
                    // Both the node operator and payer submit queries
                    getFileInfo(filename).payingWith(NODE_OPERATOR).asNodeOperator(),
                    getFileInfo(filename).payingWith(PAYER),
                    sleepFor(1_000),
                    // The node operator wasn't charged
                    getAccountBalance(NODE_OPERATOR).hasTinyBars(ONE_HUNDRED_HBARS),
                    // But the payer was charged
                    getAccountBalance(PAYER).hasTinyBars(lessThan(ONE_HUNDRED_HBARS)));
        }

        @HapiTest
        @DisplayName("Only node operators don't need to sign file info queries")
        final Stream<DynamicTest> getFileInfoQueryNoSigRequired() {
            final var filename = "anyFile.json";
            String someoneElse = "someoneElse";
            return hapiTest(
                    newKeyNamed(someoneElse),
                    cryptoCreate(NODE_OPERATOR).balance(ONE_HUNDRED_HBARS),
                    cryptoCreate(PAYER).balance(ONE_HUNDRED_HBARS),
                    fileCreate(filename).contents("anyContentAgain"),
                    // Sign the node operator query request with a totally unrelated key, to show that there is no
                    // signature check
                    getFileInfo(filename)
                            .payingWith(NODE_OPERATOR)
                            .signedBy(someoneElse)
                            .asNodeOperator()
                            .hasAnswerOnlyPrecheck(ResponseCodeEnum.OK),
                    // But the non-node operator submitter must still sign
                    getFileInfo(filename)
                            .payingWith(PAYER)
                            .signedBy(someoneElse)
                            .hasAnswerOnlyPrecheck(ResponseCodeEnum.INVALID_SIGNATURE));
        }
        /**
         * Get a Smart Contract Function tests
         */
        @HapiTest
        @DisplayName("Only node operators aren't charged for contract info queries")
        final Stream<DynamicTest> getSmartContractQueryNodeOperatorNotCharged() {
            final var contract = "PretendPair"; // any contract, nothing special about this one
            return hapiTest(
                    cryptoCreate(NODE_OPERATOR).balance(ONE_HUNDRED_HBARS),
                    cryptoCreate(PAYER).balance(ONE_HUNDRED_HBARS),
                    uploadInitCode(contract),
                    contractCreate(contract),
                    // Both the node operator and payer submit queries
                    getContractInfo(contract).payingWith(NODE_OPERATOR).asNodeOperator(),
                    getContractInfo(contract).payingWith(PAYER),
                    sleepFor(1_000),
                    // The node operator wasn't charged
                    getAccountBalance(NODE_OPERATOR).hasTinyBars(ONE_HUNDRED_HBARS),
                    // But the payer was charged
                    getAccountBalance(PAYER).hasTinyBars(lessThan(ONE_HUNDRED_HBARS)));
        }

        @HapiTest
        @DisplayName("Only node operators don't need to sign contract info queries")
        final Stream<DynamicTest> getSmartContractQuerySigNotRequired() {
            final var contract = "PretendPair"; // any contract, nothing special about this one
            final var someoneElse = "someoneElse";
            return hapiTest(
                    newKeyNamed(someoneElse),
                    cryptoCreate(NODE_OPERATOR).balance(ONE_HUNDRED_HBARS),
                    cryptoCreate(PAYER).balance(ONE_HUNDRED_HBARS),
                    uploadInitCode(contract),
                    contractCreate(contract),
                    // Sign the node operator query request with a totally unrelated key, to show that there is no
                    // signature check
                    getContractInfo(contract)
                            .payingWith(NODE_OPERATOR)
                            .signedBy(someoneElse)
                            .asNodeOperator()
                            .hasAnswerOnlyPrecheck(ResponseCodeEnum.OK),
                    // But the non-node operator submitter must still sign
                    getContractInfo(contract)
                            .payingWith(PAYER)
                            .signedBy(someoneElse)
                            .hasAnswerOnlyPrecheck(ResponseCodeEnum.INVALID_SIGNATURE));
        }
        /**
         * Get a Smart Contract Bytecode tests
         */
        @HapiTest
        @DisplayName("Only node operators aren't charged for contract bytecode queries")
        final Stream<DynamicTest> getContractBytecodeQueryNodeOperatorNotCharged() {
            final var contract = "PretendPair"; // any contract, nothing special about this one
            return hapiTest(
                    cryptoCreate(NODE_OPERATOR).balance(ONE_HUNDRED_HBARS),
                    cryptoCreate(PAYER).balance(ONE_HUNDRED_HBARS),
                    uploadInitCode(contract),
                    contractCreate(contract),
                    // Both the node operator and payer submit queries
                    getContractBytecode(contract).payingWith(NODE_OPERATOR).asNodeOperator(),
                    getContractBytecode(contract).payingWith(PAYER),
                    sleepFor(1_000),
                    // The node operator wasn't charged
                    getAccountBalance(NODE_OPERATOR).hasTinyBars(ONE_HUNDRED_HBARS),
                    // But the payer was charged
                    getAccountBalance(PAYER).hasTinyBars(lessThan(ONE_HUNDRED_HBARS)));
        }

        @HapiTest
        @DisplayName("Only node operators don't need to sign contract bytecode queries")
        final Stream<DynamicTest> getContractBytecodeQueryNoSigRequired() {
            final var contract = "PretendPair"; // any contract, nothing special about this one
            final var someoneElse = "someoneElse";
            return hapiTest(
                    newKeyNamed(someoneElse),
                    cryptoCreate(NODE_OPERATOR).balance(ONE_HUNDRED_HBARS),
                    cryptoCreate(PAYER).balance(ONE_HUNDRED_HBARS),
                    uploadInitCode(contract),
                    contractCreate(contract),
                    // Sign the node operator query request with a totally unrelated key, to show that there is no
                    // signature check
                    getContractBytecode(contract)
                            .payingWith(NODE_OPERATOR)
                            .signedBy(someoneElse)
                            .asNodeOperator()
                            .hasAnswerOnlyPrecheck(ResponseCodeEnum.OK),
                    // But the non-node operator submitter must still sign
                    getContractBytecode(contract)
                            .payingWith(PAYER)
                            .signedBy(someoneElse)
                            .hasAnswerOnlyPrecheck(ResponseCodeEnum.INVALID_SIGNATURE));
        }
        /**
         * Get Schedule Info tests
         */
        @HapiTest
        @DisplayName("Only node operators aren't charged for schedule info queries")
        final Stream<DynamicTest> getScheduleInfoQueryNodeOperatorNotCharged() {
            final var txnToSchedule =
                    cryptoTransfer(tinyBarsFromTo(PAYER, DEFAULT_PAYER, 1)); // any txn, nothing special here
            final var schedule = "anySchedule";
            return hapiTest(
                    cryptoCreate(NODE_OPERATOR).balance(ONE_HUNDRED_HBARS),
                    cryptoCreate(PAYER).balance(ONE_HUNDRED_HBARS),
                    scheduleCreate(schedule, txnToSchedule),
                    // Both the node operator and payer submit queries
                    getScheduleInfo(schedule).payingWith(NODE_OPERATOR).asNodeOperator(),
                    getScheduleInfo(schedule).payingWith(PAYER),
                    sleepFor(1_000),
                    // The node operator wasn't charged
                    getAccountBalance(NODE_OPERATOR).hasTinyBars(ONE_HUNDRED_HBARS),
                    // But the payer was charged
                    getAccountBalance(PAYER).hasTinyBars(lessThan(ONE_HUNDRED_HBARS)));
        }

        @HapiTest
        @DisplayName("Only node operators don't need to sign schedule info queries")
        final Stream<DynamicTest> getScheduleInfoQueryNoSigRequired() {
            final var txnToSchedule =
                    cryptoTransfer(tinyBarsFromTo(PAYER, DEFAULT_PAYER, 1)); // any txn, nothing special here
            final var schedule = "anySchedule";
            final var someoneElse = "someoneElse";
            return hapiTest(
                    newKeyNamed(someoneElse),
                    cryptoCreate(NODE_OPERATOR).balance(ONE_HUNDRED_HBARS),
                    cryptoCreate(PAYER).balance(ONE_HUNDRED_HBARS),
                    scheduleCreate(schedule, txnToSchedule),
                    // Sign the node operator query request with a totally unrelated key, to show that there is no
                    // signature check
                    getScheduleInfo(schedule)
                            .payingWith(NODE_OPERATOR)
                            .signedBy(someoneElse)
                            .asNodeOperator()
                            .hasAnswerOnlyPrecheck(ResponseCodeEnum.OK),
                    // But the non-node operator submitter must still sign
                    getScheduleInfo(schedule)
                            .payingWith(PAYER)
                            .signedBy(someoneElse)
                            .hasAnswerOnlyPrecheck(ResponseCodeEnum.INVALID_SIGNATURE));
        }
    }
}
