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

import static com.hedera.services.bdd.junit.ContextRequirement.THROTTLE_OVERRIDES;
import static com.hedera.services.bdd.junit.TestTags.CRYPTO;
import static com.hedera.services.bdd.spec.HapiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.HapiSpec.hapiTest;
import static com.hedera.services.bdd.spec.assertions.AccountInfoAsserts.changeFromSnapshot;
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
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.balanceSnapshot;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.doAdhoc;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.inParallel;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.overridingThrottles;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sleepFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.verify;
import static com.hedera.services.bdd.suites.HapiSuite.DEFAULT_PAYER;
import static com.hedera.services.bdd.suites.HapiSuite.ONE_HUNDRED_HBARS;
import static com.hedera.services.bdd.suites.HapiSuite.flattened;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.BUSY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.TokenType.NON_FUNGIBLE_UNIQUE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hedera.services.bdd.junit.HapiTest;
import com.hedera.services.bdd.junit.HapiTestLifecycle;
import com.hedera.services.bdd.junit.LeakyHapiTest;
import com.hedera.services.bdd.junit.hedera.HederaNode;
import com.hedera.services.bdd.junit.support.TestLifecycle;
import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CryptoGetInfoQuery;
import com.hederahashgraph.api.proto.java.CryptoTransferTransactionBody;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenSupplyType;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransferList;
import com.hederahashgraph.service.proto.java.CryptoServiceGrpc;
import edu.umd.cs.findbugs.annotations.NonNull;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.stream.IntStream;
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
    private static final int BURST_SIZE = 20;

    Function<String, HapiSpecOperation[]> miscTxnBurstFn = payer -> IntStream.range(0, BURST_SIZE)
            .mapToObj(i -> cryptoCreate(String.format("Account%d", i))
                    .payingWith(payer)
                    .deferStatusResolution())
            .toArray(HapiSpecOperation[]::new);

    @Nested
    @DisplayName("verify payer balance")
    /**
     * A class with Node Operator tests that verify the payer balance
     */
    class PerformNodeOperatorQueryAndVerifyPayerBalance {

        private static List<HederaNode> nodes = new ArrayList<>();

        @BeforeAll
        static void beforeAll(@NonNull final TestLifecycle lifecycle) {
            lifecycle.doAdhoc(createAllAccountsAndTokens());
            nodes = lifecycle.getNodes();
        }

        @HapiTest
        final Stream<DynamicTest> nodeOperatorQueryVerifyPayerBalanceForAccountBalance() {
            return hapiTest(
                    cryptoCreate(NODE_OPERATOR).balance(ONE_HUNDRED_HBARS),
                    cryptoCreate(PAYER).balance(ONE_HUNDRED_HBARS),
                    // perform getAccountBalance() query, pay for the query with payer account
                    // the grpc client performs the query to different ports
                    getAccountBalance(NODE_OPERATOR).payingWith(PAYER),
                    sleepFor(3_000),
                    // assert payer is charged
                    getAccountBalance(PAYER).hasTinyBars(ONE_HUNDRED_HBARS),
                    // perform free query to local port with asNodeOperator() method
                    getAccountBalance(NODE_OPERATOR).payingWith(PAYER).asNodeOperator(),
                    sleepFor(3_000),
                    // assert payer is not charged as the query is performed as node operator
                    getAccountBalance(PAYER).hasTinyBars(ONE_HUNDRED_HBARS));
        }

        @HapiTest
        final Stream<DynamicTest> nodeOperatorQueryVerifyPayerBalanceForAccountInfo() {
            return hapiTest(
                    cryptoCreate(NODE_OPERATOR).balance(ONE_HUNDRED_HBARS),
                    cryptoCreate(PAYER).balance(ONE_HUNDRED_HBARS),
                    balanceSnapshot("payerInitialBalance", PAYER),
                    // perform getAccountInfo() query, pay for the query with payer account
                    // the grpc client performs the query to different ports
                    getAccountInfo(NODE_OPERATOR).payingWith(PAYER),
                    sleepFor(3_000),
                    // assert payer is charged
                    getAccountBalance(PAYER).hasTinyBars(changeFromSnapshot("payerInitialBalance", -QUERY_COST)),
                    // perform free query to local port with asNodeOperator() method
                    getAccountInfo(NODE_OPERATOR).payingWith(PAYER).asNodeOperator(),
                    sleepFor(3_000),
                    // assert payer is not charged as the query is performed as node operator
                    getAccountBalance(PAYER).hasTinyBars(changeFromSnapshot("payerInitialBalance", -QUERY_COST)));
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
                    .when(
                            getAccountInfo(NODE_OPERATOR)
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
                            .hasAnswerOnlyPrecheck(OK),
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
                            .hasAnswerOnlyPrecheck(OK),
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
                            .hasAnswerOnlyPrecheck(OK),
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
                            .hasAnswerOnlyPrecheck(OK),
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
                            .hasAnswerOnlyPrecheck(OK),
                    // But the non-node operator submitter must still sign
                    getScheduleInfo(schedule)
                            .payingWith(PAYER)
                            .signedBy(someoneElse)
                            .hasAnswerOnlyPrecheck(ResponseCodeEnum.INVALID_SIGNATURE));
        }

        @LeakyHapiTest(requirement = THROTTLE_OVERRIDES)
        final Stream<DynamicTest> nodeOperatorCryptoGetInfoThrottled() {
            return hapiTest(flattened(
                    cryptoCreate(NODE_OPERATOR).balance(ONE_HUNDRED_HBARS),
                    overridingThrottles("testSystemFiles/node-operator-throttles.json"),
                    inParallel(miscTxnBurstFn.apply(DEFAULT_PAYER)),
                    getAccountInfo(NODE_OPERATOR).payingWith(NODE_OPERATOR).hasAnswerOnlyPrecheck(BUSY)));
        }

        @LeakyHapiTest(requirement = THROTTLE_OVERRIDES)
        final Stream<DynamicTest> nodeOperatorCryptoGetInfoNotThrottled() {
            return hapiTest(flattened(
                    cryptoCreate(NODE_OPERATOR).balance(ONE_HUNDRED_HBARS),
                    overridingThrottles("testSystemFiles/node-operator-throttles.json"),
                    inParallel(miscTxnBurstFn.apply(DEFAULT_PAYER)),
                    getAccountInfo(NODE_OPERATOR)
                            .payingWith(NODE_OPERATOR)
                            .asNodeOperator()
                            .hasAnswerOnlyPrecheck(OK)));
        }

        @HapiTest
        @DisplayName("Node Operator Submit Query not from Localhost")
        final Stream<DynamicTest> submitCryptoTransfer() {
            // Create the gRPC channel
            final int nodeOperatorGrpcPort = nodes.getFirst().getGrpcNodeOperatorPort();
            ManagedChannel channel = ManagedChannelBuilder.forAddress("0.0.0.0", nodeOperatorGrpcPort)
                    .usePlaintext()
                    .build();

            // Create the stub
            CryptoServiceGrpc.CryptoServiceBlockingStub stub = CryptoServiceGrpc.newBlockingStub(channel);

            Query query = buildCryptoAccountInfoQuery();

            return Stream.of(DynamicTest.dynamicTest("Node Operator Submit Query not from Localhost", () -> {
                // Assert that the exception is thrown
                assertThatThrownBy(() -> {
                            // Submit the transaction
                            stub.getAccountInfo(query);
                        })
                        .isInstanceOf(io.grpc.StatusRuntimeException.class);
                // Close the channel
                channel.shutdown();
            }));
        }

        @HapiTest
        @DisplayName("Node Operator Submit Crypto Transfer Localhost Node Operator Port")
        final Stream<DynamicTest> submitCryptoTransferLocalHostNodeOperatorPort() {
            // Create the gRPC channel
            final int nodeOperatorGrpcPort = nodes.getFirst().getGrpcNodeOperatorPort();
            ManagedChannel channel = ManagedChannelBuilder.forAddress("localhost", nodeOperatorGrpcPort)
                    .usePlaintext()
                    .build();

            // Create the stub
            CryptoServiceGrpc.CryptoServiceBlockingStub stub = CryptoServiceGrpc.newBlockingStub(channel);

            // Create the transaction
            final AccountID TO_ACCOUNT =
                    AccountID.newBuilder().setAccountNum(1002).build();
            final AccountID FROM_ACCOUNT =
                    AccountID.newBuilder().setAccountNum(1001).build();
            final long AMOUNT = 1000L;

            Transaction transaction = buildCryptoTransferTransaction(FROM_ACCOUNT, TO_ACCOUNT, AMOUNT);

            return Stream.of(
                    DynamicTest.dynamicTest("Node Operator Submit Crypto Transfer Localhost Node Operator Port", () -> {
                        // Assert that the exception is thrown
                        assertThatThrownBy(() -> {
                                    // Submit the transaction
                                    stub.cryptoTransfer(transaction);
                                })
                                .isInstanceOf(io.grpc.StatusRuntimeException.class)
                                .hasMessageContaining(
                                        "UNIMPLEMENTED: Method not found: proto.CryptoService/cryptoTransfer");
                        // Close the channel
                        channel.shutdown();
                    }));
        }

        private static Transaction buildCryptoTransferTransaction(
                AccountID fromAccount, AccountID toAccount, long amount) {
            // Create the transfer list
            TransferList transferList = TransferList.newBuilder()
                    .addAccountAmounts(AccountAmount.newBuilder()
                            .setAccountID(fromAccount)
                            .setAmount(-amount)
                            .build())
                    .addAccountAmounts(AccountAmount.newBuilder()
                            .setAccountID(toAccount)
                            .setAmount(amount)
                            .build())
                    .build();

            // Create the CryptoTransferTransactionBody
            CryptoTransferTransactionBody body = CryptoTransferTransactionBody.newBuilder()
                    .setTransfers(transferList)
                    .build();

            // Create the TransactionBody
            TransactionBody transactionBody =
                    TransactionBody.newBuilder().setCryptoTransfer(body).build();

            // Create the Transaction
            return Transaction.newBuilder()
                    .setSignedTransactionBytes(transactionBody.toByteString())
                    .build();
        }
    }

    private Query buildCryptoAccountInfoQuery() {
        // Define the account ID for which the account info is being requested
        final AccountID accountID = AccountID.newBuilder().setAccountNum(1001).build();

        // Create the CryptoGetInfo query
        Query query = Query.newBuilder()
                .setCryptoGetInfo(
                        CryptoGetInfoQuery.newBuilder().setAccountID(accountID).build())
                .build();

        return query;
    }
}
