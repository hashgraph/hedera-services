/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.bdd.suites.perf.crypto;

import static com.hedera.services.bdd.spec.HapiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.freezeOnly;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.inParallel;

import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.utilops.LoadTest;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class CryptoCreatePerfSuite extends LoadTest {
    private static final Logger log = LogManager.getLogger(CryptoCreatePerfSuite.class);

    public static void main(String... args) {
        CryptoCreatePerfSuite suite = new CryptoCreatePerfSuite();
        suite.runSuiteSync();
    }

    @Override
    public List<HapiSpec> getSpecsInSuite() {
        return List.of(runCryptoCreates());
    }

    private HapiSpec runCryptoCreates() {
        final int NUM_CREATES = 1000000;
        return defaultHapiSpec("cryptoCreatePerf")
                .given()
                .when(
                        inParallel(
                                asOpArray(
                                        NUM_CREATES,
                                        i ->
                                                (i == (NUM_CREATES - 1))
                                                        ? cryptoCreate("testAccount" + i)
                                                                .balance(100_000_000_000L)
                                                                .key(GENESIS)
                                                                .withRecharging()
                                                                .rechargeWindow(30)
                                                                .payingWith(GENESIS)
                                                        : cryptoCreate("testAccount" + i)
                                                                .balance(100_000_000_000L)
                                                                .key(GENESIS)
                                                                .withRecharging()
                                                                .rechargeWindow(30)
                                                                .payingWith(GENESIS)
                                                                .deferStatusResolution())))
                .then(freezeOnly().payingWith(GENESIS).startingIn(60).seconds());
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}
