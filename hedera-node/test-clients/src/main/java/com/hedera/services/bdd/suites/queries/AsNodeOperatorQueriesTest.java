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
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountInfo;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.balanceSnapshot;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.doAdhoc;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sleepFor;
import static com.hedera.services.bdd.suites.HapiSuite.ONE_HUNDRED_HBARS;
import static org.assertj.core.api.Assertions.assertThat;

import com.hedera.services.bdd.junit.HapiTest;
import com.hedera.services.bdd.junit.HapiTestLifecycle;
import com.hedera.services.bdd.junit.support.TestLifecycle;
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
        final Stream<DynamicTest> nodeOperatorQueryVerifyPayerBalanceForAccountBalance() {
            // declare payer account balance variables
            final AtomicLong currentPayerBalance = new AtomicLong();
            final AtomicLong payerBalanceAfterFirstQuery = new AtomicLong();
            final AtomicLong payerBalanceAfterSecondQuery = new AtomicLong();
            return hapiTest(
                    cryptoCreate(NODE_OPERATOR).balance(ONE_HUNDRED_HBARS),
                    cryptoCreate(PAYER).balance(ONE_HUNDRED_HBARS),
                    getAccountBalance(PAYER).exposingBalanceTo(currentPayerBalance::set),
                    // perform getAccountBalance() query, pay for the query with payer account
                    // the grpc client performs the query to different ports
                    getAccountBalance(NODE_OPERATOR).payingWith(PAYER),
                    sleepFor(3_000),
                    getAccountBalance(PAYER).exposingBalanceTo(payerBalanceAfterFirstQuery::set),
                    // perform free query to local port with asNodeOperator() method
                    getAccountBalance(NODE_OPERATOR).payingWith(PAYER).asNodeOperator(),
                    sleepFor(3_000),
                    // get payer account balance after second query
                    getAccountBalance(PAYER).exposingBalanceTo(payerBalanceAfterSecondQuery::set),
                    doAdhoc(() -> {
                        // getAccountBalance() is a free query and both queries should not be charged
                        assertThat(currentPayerBalance.get())
                                .withFailMessage("Balances should be equal but they are not!")
                                .isEqualTo(payerBalanceAfterFirstQuery.get());
                        assertThat(payerBalanceAfterFirstQuery.get())
                                .withFailMessage("Balances should be equal but they are not!")
                                .isEqualTo(payerBalanceAfterSecondQuery.get());
                    }));
        }

        @HapiTest
        final Stream<DynamicTest> nodeOperatorQueryVerifyPayerBalanceForAccountInfo() {
            // declare payer account balance variables
            final AtomicLong currentPayerBalance = new AtomicLong();
            final AtomicLong payerBalanceAfterFirstQuery = new AtomicLong();
            final AtomicLong payerBalanceAfterSecondQuery = new AtomicLong();
            return hapiTest(
                    cryptoCreate(NODE_OPERATOR).balance(ONE_HUNDRED_HBARS),
                    cryptoCreate(PAYER).balance(ONE_HUNDRED_HBARS),
                    getAccountBalance(PAYER).exposingBalanceTo(currentPayerBalance::set),
                    // perform getAccountInfo() query, pay for the query with payer account
                    // the grpc client performs the query to different ports
                    getAccountInfo(NODE_OPERATOR).payingWith(PAYER),
                    sleepFor(3_000),
                    getAccountBalance(PAYER).exposingBalanceTo(payerBalanceAfterFirstQuery::set),
                    // perform free query to local port with asNodeOperator() method
                    getAccountInfo(NODE_OPERATOR).payingWith(PAYER).asNodeOperator(),
                    sleepFor(3_000),
                    // get payer account balance after second query
                    getAccountBalance(PAYER).exposingBalanceTo(payerBalanceAfterSecondQuery::set),
                    doAdhoc(() -> {
                        // getAccountInfo() is a paid query
                        // the first query should be charged as it is not performed as node operator
                        assertThat(payerBalanceAfterFirstQuery.get())
                                .withFailMessage("Balances should not be equal but they are equal!")
                                .isNotEqualTo(currentPayerBalance.get());
                        // the second query should not be charged as it is performed as node operator
                        assertThat(payerBalanceAfterFirstQuery.get())
                                .withFailMessage("Balances should be equal but they are not!")
                                .isEqualTo(payerBalanceAfterSecondQuery.get());
                    }));
        }

        // the tests but without the atomic long variables
        @HapiTest
        final Stream<DynamicTest> nodeOperatorQueryVerifyPayerBalanceForAccountBalance_WithoutAtomicLong() {
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
        final Stream<DynamicTest> nodeOperatorQueryVerifyPayerBalanceForAccountInfo_WithoutAtomicLong() {
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
//                    balanceSnapshot("payerCurrentBalance", PAYER),
                    // perform free query to local port with asNodeOperator() method
                    getAccountInfo(NODE_OPERATOR).payingWith(PAYER).asNodeOperator(),
                    sleepFor(3_000),
                    // assert payer is not charged as the query is performed as node operator
                    getAccountBalance(PAYER).hasTinyBars(ONE_HUNDRED_HBARS - QUERY_COST),
                    getAccountBalance(PAYER).hasTinyBars(changeFromSnapshot("payerInitialBalance", -QUERY_COST)));
        }

        @HapiTest
        final Stream<DynamicTest> nodeOperatorQueryVerifyPayerBalanceForAccountInfo_WithoutAtomicLong_Old() {
            return hapiTest(
                    cryptoCreate(NODE_OPERATOR).balance(ONE_HUNDRED_HBARS),
                    cryptoCreate(PAYER).balance(ONE_HUNDRED_HBARS),
                    // perform getAccountInfo() query, pay for the query with payer account
                    // the grpc client performs the query to different ports
                    getAccountInfo(NODE_OPERATOR).payingWith(PAYER),
                    sleepFor(3_000),
                    // assert payer is charged
                    getAccountBalance(PAYER).hasTinyBars(ONE_HUNDRED_HBARS - QUERY_COST),
                    // perform free query to local port with asNodeOperator() method
                    getAccountInfo(NODE_OPERATOR).payingWith(PAYER).asNodeOperator(),
                    sleepFor(3_000),
                    // assert payer is not charged as the query is performed as node operator
                    getAccountBalance(PAYER).hasTinyBars(ONE_HUNDRED_HBARS - QUERY_COST));
        }
    }
}
