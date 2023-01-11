/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
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
package com.hedera.services.bdd.suites.perf.token;

import static com.hedera.services.bdd.spec.HapiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.freezeOnly;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.inParallel;

import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.utilops.LoadTest;
import com.hedera.services.bdd.spec.utilops.UtilVerbs;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class TokenCreatePerfSuite extends LoadTest {
    private static final Logger log = LogManager.getLogger(TokenCreatePerfSuite.class);

    public static void main(String... args) {
        parseArgs(args);
        TokenCreatePerfSuite suite = new TokenCreatePerfSuite();
        suite.runSuiteSync();
    }

    @Override
    public List<HapiSpec> getSpecsInSuite() {
        return List.of(runTokenCreates());
    }

    private HapiSpec runTokenCreates() {
        final int NUM_CREATES = 100000;
        return defaultHapiSpec("tokenCreatePerf")
                .given()
                .when(
                        inParallel(
                                asOpArray(
                                        NUM_CREATES,
                                        i ->
                                                (i == (NUM_CREATES - 1))
                                                        ? tokenCreate("testToken" + i)
                                                                .payingWith(GENESIS)
                                                                .initialSupply(100_000_000_000L)
                                                                .signedBy(GENESIS)
                                                        : tokenCreate("testToken" + i)
                                                                .payingWith(GENESIS)
                                                                .signedBy(GENESIS)
                                                                .initialSupply(100_000_000_000L)
                                                                .deferStatusResolution())))
                .then(
                        UtilVerbs.sleepFor(200000),
                        freezeOnly().payingWith(GENESIS).startingIn(60).seconds());
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}
