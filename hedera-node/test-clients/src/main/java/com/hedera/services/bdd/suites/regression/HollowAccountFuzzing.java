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
import static com.hedera.services.bdd.suites.regression.factories.HollowAccountFuzzingFactory.hollowAccountFuzzingTest;
import static com.hedera.services.bdd.suites.regression.factories.HollowAccountFuzzingFactory.initOperations;

import com.hedera.services.bdd.junit.HapiTest;
import com.hedera.services.bdd.junit.HapiTestSuite;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.suites.HapiSuite;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@HapiTestSuite
public class HollowAccountFuzzing extends HapiSuite {

    private static final Logger log = LogManager.getLogger(HollowAccountFuzzing.class);

    private static final String PROPERTIES = "hollow-account-fuzzing.properties";

    public static void main(String... args) {
        new HollowAccountFuzzing().runSuiteSync();
    }

    @HapiTest
    final DynamicTest hollowAccountFuzzing() {
        return defaultHapiSpec("HollowAccountFuzzing")
                .given(initOperations())
                .when()
                .then(runWithProvider(hollowAccountFuzzingTest(PROPERTIES)).lasting(10L, TimeUnit.SECONDS));
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }

    @Override
    public List<DynamicTest> getSpecsInSuite() {
        return List.of(hollowAccountFuzzing());
    }
}
