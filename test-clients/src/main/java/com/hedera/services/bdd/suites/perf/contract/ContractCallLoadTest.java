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
package com.hedera.services.bdd.suites.perf.contract;

import static com.hedera.services.bdd.spec.HapiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getContractInfo;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.uploadInitCode;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.inParallel;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.logIt;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.PLATFORM_TRANSACTION_NOT_CREATED;

import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.spec.utilops.LoadTest;
import com.hedera.services.bdd.suites.perf.PerfTestLoadSettings;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ContractCallLoadTest extends LoadTest {
    private static final Logger log = LogManager.getLogger(ContractCallLoadTest.class);
    private static final String VERBOSE_DEPOSIT = "VerboseDeposit";
    private static final String BALANCE_LOOKUP = "BalanceLookup";

    public static void main(String... args) {
        parseArgs(args);

        ContractCallLoadTest suite = new ContractCallLoadTest();
        suite.runSuiteSync();
    }

    @Override
    public List<HapiSpec> getSpecsInSuite() {
        return List.of(runContractCalls());
    }

    private HapiSpec runContractCalls() {
        PerfTestLoadSettings settings = new PerfTestLoadSettings();
        final AtomicInteger submittedSoFar = new AtomicInteger(0);
        final String DEPOSIT_MEMO = "So we out-danced thought, body perfection brought...";

        Supplier<HapiSpecOperation[]> callBurst =
                () ->
                        new HapiSpecOperation[] {
                            inParallel(
                                    IntStream.range(0, settings.getBurstSize())
                                            .mapToObj(
                                                    i ->
                                                            contractCall(
                                                                            VERBOSE_DEPOSIT,
                                                                            "deposit",
                                                                            i + 1,
                                                                            0,
                                                                            DEPOSIT_MEMO)
                                                                    .sending(i + 1)
                                                                    .noLogging()
                                                                    .suppressStats(true)
                                                                    .hasRetryPrecheckFrom(
                                                                            PLATFORM_TRANSACTION_NOT_CREATED)
                                                                    .deferStatusResolution())
                                            .toArray(n -> new HapiSpecOperation[n])),
                            logIt(
                                    ignore ->
                                            String.format(
                                                    "Now a total of %d transactions submitted.",
                                                    submittedSoFar.addAndGet(
                                                            settings.getBurstSize()))),
                        };

        return defaultHapiSpec("runContractCalls")
                .given(
                        withOpContext(
                                (spec, ignore) -> settings.setFrom(spec.setup().ciPropertiesMap())),
                        logIt(ignore -> settings.toString()))
                .when(
                        uploadInitCode(VERBOSE_DEPOSIT, BALANCE_LOOKUP),
                        contractCreate(VERBOSE_DEPOSIT),
                        contractCreate(BALANCE_LOOKUP).balance(1L),
                        getContractInfo(VERBOSE_DEPOSIT).hasExpectedInfo().logged())
                .then(defaultLoadTest(callBurst, settings));
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}
