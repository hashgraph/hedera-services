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
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountInfo;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
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
    }
}
