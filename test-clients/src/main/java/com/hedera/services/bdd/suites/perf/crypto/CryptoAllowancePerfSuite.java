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
package com.hedera.services.bdd.suites.perf.crypto;

import static com.hedera.services.bdd.spec.HapiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoApproveAllowance;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.inParallel;

import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.utilops.LoadTest;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class CryptoAllowancePerfSuite extends LoadTest {

    private static final Logger LOG = LogManager.getLogger(CryptoAllowancePerfSuite.class);
    private static final String OWNER = "owner";
    private static final String SPENDER = "spender";
    private static final String TOKEN = "token";

    public static void main(String... args) {
        CryptoAllowancePerfSuite suite = new CryptoAllowancePerfSuite();
        suite.runSuiteSync();
    }

    @Override
    public List<HapiSpec> getSpecsInSuite() {
        return List.of(runCryptoCreatesAndTokenCreates(), runCryptoAllowances());
    }

    private HapiSpec runCryptoCreatesAndTokenCreates() {
        final int NUM_CREATES = 5000;
        return defaultHapiSpec("runCryptoCreatesAndTokenCreates")
                .given()
                .when(
                        inParallel(
                                asOpArray(
                                        NUM_CREATES,
                                        i ->
                                                (i == (NUM_CREATES - 1))
                                                        ? cryptoCreate(OWNER + i)
                                                                .balance(100_000_000_000L)
                                                                .key(GENESIS)
                                                                .withRecharging()
                                                                .rechargeWindow(30)
                                                                .payingWith(GENESIS)
                                                        : cryptoCreate(OWNER + i)
                                                                .balance(100_000_000_000L)
                                                                .key(GENESIS)
                                                                .withRecharging()
                                                                .rechargeWindow(30)
                                                                .payingWith(GENESIS)
                                                                .deferStatusResolution())),
                        inParallel(
                                asOpArray(
                                        NUM_CREATES,
                                        i ->
                                                (i == (NUM_CREATES - 1))
                                                        ? cryptoCreate(SPENDER + i)
                                                                .balance(100_000_000_000L)
                                                                .key(GENESIS)
                                                                .withRecharging()
                                                                .rechargeWindow(30)
                                                                .payingWith(GENESIS)
                                                        : cryptoCreate(SPENDER + i)
                                                                .balance(100_000_000_000L)
                                                                .key(GENESIS)
                                                                .withRecharging()
                                                                .rechargeWindow(30)
                                                                .payingWith(GENESIS)
                                                                .deferStatusResolution())))
                .then(
                        inParallel(
                                asOpArray(
                                        NUM_CREATES,
                                        i ->
                                                (i == (NUM_CREATES - 1))
                                                        ? tokenCreate(TOKEN + i)
                                                                .payingWith(GENESIS)
                                                                .initialSupply(100_000_000_000L)
                                                                .signedBy(GENESIS)
                                                        : tokenCreate(TOKEN + i)
                                                                .payingWith(GENESIS)
                                                                .signedBy(GENESIS)
                                                                .initialSupply(100_000_000_000L)
                                                                .deferStatusResolution())));
    }

    private HapiSpec runCryptoAllowances() {
        final int NUM_ALLOWANCES = 5000;
        return defaultHapiSpec("runCryptoAllowances")
                .given()
                .when(
                        inParallel(
                                asOpArray(
                                        NUM_ALLOWANCES,
                                        i ->
                                                cryptoApproveAllowance()
                                                        .payingWith(OWNER + i)
                                                        .addCryptoAllowance(
                                                                OWNER + i, SPENDER + i, 1L)
                                                        .addTokenAllowance(
                                                                OWNER + i,
                                                                TOKEN + i,
                                                                SPENDER + i,
                                                                1L))))
                .then();
    }

    @Override
    protected Logger getResultsLogger() {
        return LOG;
    }
}
