/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

package com.hedera.services.bdd.suites.records;

import static com.hedera.services.bdd.spec.HapiSpec.customHapiSpec;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.inParallel;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_DELETED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_DELETED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;

import com.hedera.services.bdd.junit.utils.AccountClassifier;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.spec.queries.QueryVerbs;
import com.hedera.services.bdd.suites.HapiSuite;
import java.util.List;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class BalanceValidation extends HapiSuite {
    private static final Logger log = LogManager.getLogger(BalanceValidation.class);

    private final Map<Long, Long> expectedBalances;
    private final AccountClassifier accountClassifier;

    public BalanceValidation(final Map<Long, Long> expectedBalances, final AccountClassifier accountClassifier) {
        this.expectedBalances = expectedBalances;
        this.accountClassifier = accountClassifier;
    }

    public static void main(String... args) {
        // Treasury starts with 50B hbar
        new BalanceValidation(Map.of(2L, 50_000_000_000L * TINY_PARTS_PER_WHOLE), new AccountClassifier())
                .runSuiteSync();
    }

    @Override
    public boolean canRunConcurrent() {
        return true;
    }

    @Override
    public List<HapiSpec> getSpecsInSuite() {
        return List.of(validateBalances());
    }

    private HapiSpec validateBalances() {
        return customHapiSpec("ValidateBalances")
                .withProperties(Map.of(
                        "fees.useFixedOffer", "true",
                        "fees.fixedOffer", "100000000"))
                .given()
                .when()
                .then(inParallel(expectedBalances.entrySet().stream()
                                .map(entry -> {
                                    final var accountNum = entry.getKey();
                                    return QueryVerbs.getAccountBalance(
                                                    "0.0." + accountNum, accountClassifier.isContract(accountNum))
                                            .hasAnswerOnlyPrecheckFrom(CONTRACT_DELETED, ACCOUNT_DELETED, OK)
                                            .hasTinyBars(entry.getValue());
                                })
                                .toArray(HapiSpecOperation[]::new))
                        .failOnErrors());
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}
