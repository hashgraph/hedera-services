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
import static com.hedera.services.bdd.spec.HapiSpec.hapiTest;
import static com.hedera.services.bdd.spec.assertions.AccountInfoAsserts.changeFromSnapshot;
import static com.hedera.services.bdd.spec.assertions.AccountInfoAsserts.lessThan;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.contractCallLocal;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getContractBytecode;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getFileContents;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getFileInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getScheduleInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTokenInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTopicInfo;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.createTopic;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.scheduleCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.uploadInitCode;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromTo;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.balanceSnapshot;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sleepFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.suites.HapiSuite.DEFAULT_PAYER;
import static com.hedera.services.bdd.suites.HapiSuite.ONE_HUNDRED_HBARS;
import static com.hedera.services.bdd.suites.HapiSuite.flattened;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hedera.services.bdd.junit.HapiTest;
import com.hedera.services.bdd.junit.HapiTestLifecycle;
import com.hedera.services.bdd.junit.OrderedInIsolation;
import com.hedera.services.bdd.junit.support.TestLifecycle;
import com.hedera.services.bdd.suites.regression.system.LifecycleTest;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;

/**
 * A class with Node Operator Queries tests
 */
@Tag(CRYPTO)
@DisplayName("Node Operator Queries")
@HapiTestLifecycle
@OrderedInIsolation
public class AsNodeOperatorQueriesTest extends NodeOperatorQueriesBase implements LifecycleTest {

    @BeforeAll
    static void beforeAll(@NonNull final TestLifecycle lifecycle) {
        lifecycle.doAdhoc(createAllAccountsAndTokens());
    }

    @HapiTest
    final Stream<DynamicTest> nodeOperatorQueryVerifyPayerBalanceForAccountBalance() {
        return hapiTest(
                cryptoCreate(NODE_OPERATOR).balance(ONE_HUNDRED_HBARS),
                cryptoCreate(PAYER).balance(ONE_HUNDRED_HBARS),
                // perform getAccountBalance() query, pay for the query with payer account
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

    @Nested
    @DisplayName("Verify node operator port cannot be accessed the feature flag is disabled")
    class VerifyPortCannotBeAccessedWhenDisabled {

        @Order(0)
        @HapiTest
        final Stream<DynamicTest> nodeOperatorQueryPortNotAccessibleForAccountBalance() {
            return hapiTest(flattened(
                    restartWithDisabledNodeOperatorGrpcPort(),
                    nodeOperatorAccount(),
                    payerAccount(),
                    // perform getAccountBalance() query, pay for the query with payer account
                    getAccountBalance(NODE_OPERATOR).payingWith(PAYER),
                    sleepFor(1000),
                    // assert payer is charged
                    getAccountBalance(PAYER).hasTinyBars(ONE_HUNDRED_HBARS),
                    withOpContext((spec, opLog) -> assertThatThrownBy(() -> {
                                final var getAccountBalanceAsNodeOperator = getAccountBalance(NODE_OPERATOR)
                                        .payingWith(PAYER)
                                        .asNodeOperator();
                                allRunFor(spec, getAccountBalanceAsNodeOperator);
                            })
                            .isInstanceOf(IllegalStateException.class)
                            .hasMessageContaining("io.grpc.StatusRuntimeException: UNAVAILABLE: io exception"))));
        }

        @Order(1)
        @HapiTest
        final Stream<DynamicTest> nodeOperatorQueryPortNotAccessibleForAccountInfo() {
            return hapiTest(flattened(
                    restartWithDisabledNodeOperatorGrpcPort(),
                    nodeOperatorAccount(),
                    payerAccount(),
                    getAccountInfo(NODE_OPERATOR).payingWith(PAYER),
                    sleepFor(1000),
                    getAccountBalance(PAYER).hasTinyBars(lessThan(ONE_HUNDRED_HBARS)),
                    withOpContext((spec, opLog) -> assertThatThrownBy(() -> {
                                final var getAccountInfoAsNodeOperator = getAccountInfo(NODE_OPERATOR)
                                        .payingWith(PAYER)
                                        .asNodeOperator();
                                allRunFor(spec, getAccountInfoAsNodeOperator);
                            })
                            .isInstanceOf(IllegalStateException.class)
                            .hasMessageContaining("io.grpc.StatusRuntimeException: UNAVAILABLE: io exception"))));
        }

        @Order(2)
        @HapiTest
        final Stream<DynamicTest> nodeOperatorQueryPortNotAccessibleForTopicInfo() {
            return hapiTest(flattened(
                    restartWithDisabledNodeOperatorGrpcPort(),
                    nodeOperatorAccount(),
                    payerAccount(),
                    nodeOperatorAccount(),
                    createTopic(TOPIC),
                    getTopicInfo(TOPIC).payingWith(PAYER),
                    sleepFor(1000),
                    getAccountBalance(PAYER).hasTinyBars(lessThan(ONE_HUNDRED_HBARS)),
                    withOpContext((spec, opLog) -> assertThatThrownBy(() -> {
                                final var getTopicInfoAsNodeOperator = getTopicInfo(TOPIC)
                                        .payingWith(NODE_OPERATOR)
                                        .asNodeOperator();
                                allRunFor(spec, getTopicInfoAsNodeOperator);
                            })
                            .isInstanceOf(IllegalStateException.class)
                            .hasMessageContaining("io.grpc.StatusRuntimeException: UNAVAILABLE: io exception"))));
        }

        @Order(3)
        @HapiTest
        final Stream<DynamicTest> nodeOperatorQueryPortNotAccessibleForTokenInfo() {
            return hapiTest(flattened(
                    restartWithDisabledNodeOperatorGrpcPort(),
                    nodeOperatorAccount(),
                    payerAccount(),
                    nodeOperatorAccount(),
                    tokenCreate(TOKEN),
                    getTokenInfo(TOKEN).payingWith(PAYER),
                    sleepFor(1000),
                    getAccountBalance(PAYER).hasTinyBars(lessThan(ONE_HUNDRED_HBARS)),
                    withOpContext((spec, opLog) -> assertThatThrownBy(() -> {
                                final var getTokenInfoAsNodeOperator = getTokenInfo(TOKEN)
                                        .payingWith(NODE_OPERATOR)
                                        .asNodeOperator();
                                allRunFor(spec, getTokenInfoAsNodeOperator);
                            })
                            .isInstanceOf(IllegalStateException.class)
                            .hasMessageContaining("io.grpc.StatusRuntimeException: UNAVAILABLE: io exception"))));
        }

        @Order(4)
        @HapiTest
        final Stream<DynamicTest> nodeOperatorQueryPortNotAccessibleForFileContents() {
            return hapiTest(flattened(
                    restartWithDisabledNodeOperatorGrpcPort(),
                    nodeOperatorAccount(),
                    payerAccount(),
                    fileCreate(FILE).contents("anyContentAgain").payingWith(PAYER),
                    getFileContents(FILE).payingWith(PAYER),
                    sleepFor(1000),
                    getAccountBalance(PAYER).hasTinyBars(lessThan(ONE_HUNDRED_HBARS)),
                    withOpContext((spec, opLog) -> assertThatThrownBy(() -> {
                                final var getFileContentsAsNodeOperator = getFileContents(FILE)
                                        .payingWith(NODE_OPERATOR)
                                        .asNodeOperator();
                                allRunFor(spec, getFileContentsAsNodeOperator);
                            })
                            .isInstanceOf(IllegalStateException.class)
                            .hasMessageContaining("io.grpc.StatusRuntimeException: UNAVAILABLE: io exception"))));
        }

        @Order(5)
        @HapiTest
        final Stream<DynamicTest> nodeOperatorQueryPortNotAccessibleForFileInfo() {
            return hapiTest(flattened(
                    restartWithDisabledNodeOperatorGrpcPort(),
                    nodeOperatorAccount(),
                    payerAccount(),
                    fileCreate(FILE).contents("anyContentAgain").payingWith(PAYER),
                    getFileInfo(FILE).payingWith(PAYER),
                    sleepFor(1000),
                    getAccountBalance(PAYER).hasTinyBars(lessThan(ONE_HUNDRED_HBARS)),
                    withOpContext((spec, opLog) -> assertThatThrownBy(() -> {
                                final var getFileInfoAsNodeOperator = getFileInfo(FILE)
                                        .payingWith(NODE_OPERATOR)
                                        .asNodeOperator();
                                allRunFor(spec, getFileInfoAsNodeOperator);
                            })
                            .isInstanceOf(IllegalStateException.class)
                            .hasMessageContaining("io.grpc.StatusRuntimeException: UNAVAILABLE: io exception"))));
        }

        @Order(6)
        @HapiTest
        final Stream<DynamicTest> nodeOperatorQueryPortNotAccessibleForContractCall() {
            return hapiTest(flattened(
                    restartWithDisabledNodeOperatorGrpcPort(),
                    nodeOperatorAccount(),
                    payerAccount(),
                    uploadInitCode("CreateTrivial"),
                    contractCreate("CreateTrivial").gas(100_000L).payingWith(PAYER),
                    contractCall("CreateTrivial", "create").gas(785_000),
                    contractCallLocal("CreateTrivial", "getIndirect")
                            .gas(300_000L)
                            .payingWith(PAYER),
                    sleepFor(1000),
                    getAccountBalance(PAYER).hasTinyBars(lessThan(ONE_HUNDRED_HBARS)),
                    withOpContext((spec, opLog) -> assertThatThrownBy(() -> {
                                final var getContractCallLocalAsNodeOperator = contractCallLocal(
                                                "CreateTrivial", "getIndirect")
                                        .gas(300_000L)
                                        .payingWith(PAYER)
                                        .asNodeOperator();
                                allRunFor(spec, getContractCallLocalAsNodeOperator);
                            })
                            .isInstanceOf(IllegalStateException.class)
                            .hasMessageContaining("io.grpc.StatusRuntimeException: UNAVAILABLE: io exception"))));
        }

        @Order(7)
        @HapiTest
        final Stream<DynamicTest> nodeOperatorQueryPortNotAccessibleForContractBytecode() {
            return hapiTest(flattened(
                    restartWithDisabledNodeOperatorGrpcPort(),
                    nodeOperatorAccount(),
                    payerAccount(),
                    uploadInitCode("CreateTrivial"),
                    contractCreate("CreateTrivial").gas(100_000L).payingWith(PAYER),
                    contractCall("CreateTrivial", "create").gas(785_000),
                    getContractBytecode("CreateTrivial").payingWith(PAYER),
                    sleepFor(1000),
                    getAccountBalance(PAYER).hasTinyBars(lessThan(ONE_HUNDRED_HBARS)),
                    withOpContext((spec, opLog) -> assertThatThrownBy(() -> {
                                final var getContractBytecodeAsNodeOperator = getContractBytecode("CreateTrivial")
                                        .payingWith(PAYER)
                                        .asNodeOperator();
                                allRunFor(spec, getContractBytecodeAsNodeOperator);
                            })
                            .isInstanceOf(IllegalStateException.class)
                            .hasMessageContaining("io.grpc.StatusRuntimeException: UNAVAILABLE: io exception"))));
        }

        @Order(8)
        @HapiTest
        final Stream<DynamicTest> nodeOperatorQueryPortNotAccessibleForScheduleInfo() {
            final var txnToSchedule = cryptoTransfer(tinyBarsFromTo(PAYER, DEFAULT_PAYER, 1));
            return hapiTest(flattened(
                    restartWithDisabledNodeOperatorGrpcPort(),
                    nodeOperatorAccount(),
                    payerAccount(),
                    scheduleCreate(SCHEDULE, txnToSchedule),
                    getScheduleInfo(SCHEDULE).payingWith(PAYER),
                    sleepFor(1000),
                    getAccountBalance(PAYER).hasTinyBars(lessThan(ONE_HUNDRED_HBARS)),
                    withOpContext((spec, opLog) -> assertThatThrownBy(() -> {
                                final var getScheduleInfoAsNodeOperator = getScheduleInfo(SCHEDULE)
                                        .payingWith(PAYER)
                                        .asNodeOperator();
                                allRunFor(spec, getScheduleInfoAsNodeOperator);
                            })
                            .isInstanceOf(IllegalStateException.class)
                            .hasMessageContaining("io.grpc.StatusRuntimeException: UNAVAILABLE: io exception"))));
        }
    }
}
