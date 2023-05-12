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

package com.hedera.services.bdd.suites.perf.file;

import static com.hedera.services.bdd.spec.HapiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileCreate;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.inParallel;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.logIt;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.runLoadTest;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.BUSY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.DUPLICATE_TRANSACTION;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.PLATFORM_TRANSACTION_NOT_CREATED;
import static java.util.concurrent.TimeUnit.MINUTES;

import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.spec.transactions.TxnUtils;
import com.hedera.services.bdd.spec.transactions.TxnVerbs;
import com.hedera.services.bdd.suites.HapiSuite;
import com.hedera.services.bdd.suites.perf.PerfTestLoadSettings;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class FileUpdateLoadTest extends HapiSuite {
    private static final Logger log = LogManager.getLogger(FileUpdateLoadTest.class);

    public static void main(String... args) {
        FileUpdateLoadTest suite = new FileUpdateLoadTest();
        suite.runSuiteSync();
    }

    @Override
    public List<HapiSpec> getSpecsInSuite() {
        return List.of(runFileUpdates());
    }

    private HapiSpec runFileUpdates() {
        PerfTestLoadSettings settings = new PerfTestLoadSettings();
        final AtomicInteger submittedSoFar = new AtomicInteger(0);
        final byte[] NEW_CONTENTS = TxnUtils.randomUtf8Bytes(TxnUtils.BYTES_4K);

        Supplier<HapiSpecOperation[]> fileUpdateBurst = () -> new HapiSpecOperation[] {
            inParallel(IntStream.range(0, settings.getBurstSize())
                    .mapToObj(i -> TxnVerbs.fileUpdate("target")
                            .fee(Integer.MAX_VALUE)
                            .contents(NEW_CONTENTS)
                            .noLogging()
                            .hasPrecheckFrom(OK, BUSY, DUPLICATE_TRANSACTION, PLATFORM_TRANSACTION_NOT_CREATED)
                            .deferStatusResolution())
                    .toArray(n -> new HapiSpecOperation[n])),
            logIt(ignore -> String.format(
                    "Now a total of %d file updates submitted.", submittedSoFar.addAndGet(settings.getBurstSize()))),
        };

        return defaultHapiSpec("RunFileUpdates")
                .given(
                        withOpContext(
                                (spec, ignore) -> settings.setFrom(spec.setup().ciPropertiesMap())),
                        logIt(ignore -> settings.toString()))
                .when(fileCreate("target").contents("The initial contents!"))
                .then(runLoadTest(fileUpdateBurst)
                        .tps(settings::getTps)
                        .tolerance(settings::getTolerancePercentage)
                        .allowedSecsBelow(settings::getAllowedSecsBelow)
                        .lasting(settings::getMins, () -> MINUTES));
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}
