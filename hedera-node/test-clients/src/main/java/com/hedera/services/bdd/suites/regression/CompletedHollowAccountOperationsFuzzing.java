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

package com.hedera.services.bdd.suites.regression;

import static com.hedera.services.bdd.spec.HapiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.runWithProvider;
import static com.hedera.services.bdd.suites.regression.factories.HollowAccountCompletedFuzzingFactory.hollowAccountFuzzingWith;
import static com.hedera.services.bdd.suites.regression.factories.HollowAccountCompletedFuzzingFactory.initOperations;

import com.hedera.services.bdd.junit.HapiTest;
import com.hedera.services.bdd.junit.HapiTestSuite;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.suites.HapiSuite;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Fuzz test, testing different operations on completed hollow account
 */
@HapiTestSuite
public class CompletedHollowAccountOperationsFuzzing extends HapiSuite {
    private static final Logger log = LogManager.getLogger(CompletedHollowAccountOperationsFuzzing.class);

    private static final String PROPERTIES = "completed-hollow-account-fuzzing.properties";

    public static void main(String... args) {
        new CompletedHollowAccountOperationsFuzzing().runSuiteSync();
    }

    @Override
    public List<DynamicTest> getSpecsInSuite() {
        return List.of(completedHollowAccountOperationsFuzzing());
    }

    @HapiTest
    final DynamicTest completedHollowAccountOperationsFuzzing() {
        return defaultHapiSpec("CompletedHollowAccountOperationsFuzzing")
                .given(initOperations())
                .when()
                .then(runWithProvider(hollowAccountFuzzingWith(PROPERTIES))
                        .loggingOff()
                        .lasting(60L, TimeUnit.SECONDS));
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}
