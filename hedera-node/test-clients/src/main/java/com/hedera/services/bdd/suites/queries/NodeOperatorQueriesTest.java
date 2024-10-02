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

import static com.hedera.services.bdd.spec.HapiSpec.hapiTest;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountInfo;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;

import com.hedera.services.bdd.junit.HapiTest;
import com.hedera.services.bdd.junit.HapiTestLifecycle;
import com.hedera.services.bdd.junit.support.TestLifecycle;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Nested;

@HapiTestLifecycle
@DisplayName("Node Operator Queries")
/**
 * A class with Node Operator Queries tests
 */
public class NodeOperatorQueriesTest extends NodeOperatorQueriesBase {

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
            final AtomicLong initialPayerBalance = new AtomicLong();
            final AtomicLong newPayerBalance = new AtomicLong();
            final AtomicLong currentPayerBalance = new AtomicLong();
            return hapiTest(withOpContext((spec, log) -> {
                final var initialBalance = getAccountBalance(PAYER).exposingBalanceTo(initialPayerBalance::set);
                // perform paid query, pay for the query with payer account
                // the grpc client performs the query to different ports
                final var firstQuery =
                        getAccountBalance(NODE_OPERATOR).payingWith(PAYER).signedBy(PAYER);
                final var newBalance = getAccountBalance(PAYER).exposingBalanceTo(newPayerBalance::set);
                // perform free query to local port with asNodeOperator() method
                final var secondQuery = getAccountBalance(NODE_OPERATOR)
                        .payingWith(PAYER)
                        .signedBy(PAYER)
                        .asNodeOperator();
                // assert payer account balance is not changed
                final var currentBalance = getAccountBalance(PAYER).exposingBalanceTo(currentPayerBalance::set);
                allRunFor(spec, initialBalance, firstQuery, newBalance, secondQuery, currentBalance);
                Assertions.assertEquals(initialPayerBalance.get(), newPayerBalance.get(), "Balances are not equal!");
                Assertions.assertEquals(newPayerBalance.get(), currentPayerBalance.get(), "Balances are not equal!");
            }));
        }

        @HapiTest
        final Stream<DynamicTest> nodeOperatorQueryVerifyPayerBalanceForAccountInfo() {
            final AtomicLong initialPayerBalance = new AtomicLong();
            final AtomicLong newPayerBalance = new AtomicLong();
            final AtomicLong currentPayerBalance = new AtomicLong();
            return hapiTest(withOpContext((spec, log) -> {
                final var initialBalance = getAccountBalance(PAYER).exposingBalanceTo(initialPayerBalance::set);
                // perform paid query, pay for the query with payer account
                // the grpc client performs the query to different ports
                final var firstQuery =
                        getAccountInfo(NODE_OPERATOR).payingWith(PAYER).signedBy(PAYER);
                final var newBalance = getAccountBalance(PAYER).exposingBalanceTo(newPayerBalance::set);
                // perform free query to local port with asNodeOperator() method
                final var secondQuery = getAccountInfo(NODE_OPERATOR)
                        .payingWith(PAYER)
                        .signedBy(PAYER)
                        .asNodeOperator();
                // assert payer account balance is not changed
                final var currentBalance = getAccountBalance(PAYER).exposingBalanceTo(currentPayerBalance::set);
                allRunFor(spec, initialBalance, firstQuery, newBalance, secondQuery, currentBalance);
                Assertions.assertNotEquals(initialPayerBalance.get(), newPayerBalance.get(), "Balances are equal!");
                Assertions.assertEquals(newPayerBalance.get(), currentPayerBalance.get(), "Balances are not equal!");
            }));
        }
    }
}
