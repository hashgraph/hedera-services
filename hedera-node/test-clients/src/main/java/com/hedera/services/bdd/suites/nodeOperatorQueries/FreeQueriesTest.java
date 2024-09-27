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

package com.hedera.services.bdd.suites.nodeOperatorQueries;

import static com.hedera.services.bdd.spec.HapiSpec.hapiTest;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTokenInfo;
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

// @Tag(QUERIES)
@HapiTestLifecycle
@DisplayName("Free Queries")
public class FreeQueriesTest extends FreeQueriesBase {

    @Nested
    @DisplayName("verify with paid query response")
    class PerformFreeQueryAndVerifyWithPaidQueryResponse {

        @BeforeAll
        static void beforeAll(@NonNull final TestLifecycle lifecycle) {
            lifecycle.doAdhoc(createAllAccountsAndTokens());
        }

        @HapiTest
        final Stream<DynamicTest> FreeQueryVerifyWithPaidQueryForAccountBalance() {
            // declare payer account balance variables
            final AtomicLong initialPayerBalance = new AtomicLong();
            final AtomicLong newPayerBalance = new AtomicLong();
            final AtomicLong currentPayerBalance = new AtomicLong();
            return hapiTest(
                    // set initial payer balance variable
                    getAccountBalance(PAYER).exposingBalanceTo(initialPayerBalance::set),
                    // perform paid query with node operator, pay for the query with payer account
                    // the grpc client performs the query to different ports
                    getTokenInfo(FUNGIBLE_QUERY_TOKEN).payingWith(PAYER).signedBy(PAYER),
                    // assert payer account balance is changed
                    getAccountBalance(PAYER).exposingBalanceTo(newPayerBalance::set),
                    withOpContext(
                            (spec, log) -> Assertions.assertEquals(initialPayerBalance.get(), newPayerBalance.get())),
                    // assert some query data - research for me
                    // perform free query to local port with node operator, pay for the query with payer account
                    getTokenInfo(FUNGIBLE_QUERY_TOKEN)
                            .payingWith(PAYER)
                            .signedBy(PAYER)
                            .asNodeOperator(),
                    // assert payer account balance is not changed
                    getAccountBalance(PAYER).exposingBalanceTo(currentPayerBalance::set),
                    withOpContext(
                            (spec, log) -> Assertions.assertEquals(newPayerBalance.get(), currentPayerBalance.get()))
                    // assert some query data - research for me
                    );
        }
    }
}
