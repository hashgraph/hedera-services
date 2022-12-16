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
package com.hedera.services.bdd.suites.perf.topic;

import static com.hedera.services.bdd.spec.HapiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.keys.KeyShape.SIMPLE;
import static com.hedera.services.bdd.spec.keys.KeyShape.listOf;
import static com.hedera.services.bdd.spec.keys.KeyShape.threshOf;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.createTopic;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.logIt;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.BUSY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.DUPLICATE_TRANSACTION;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.PLATFORM_TRANSACTION_NOT_CREATED;

import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.spec.keys.KeyShape;
import com.hedera.services.bdd.spec.utilops.LoadTest;
import com.hedera.services.bdd.suites.perf.PerfTestLoadSettings;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class createTopicLoadTest extends LoadTest {

    private static final Logger log = LogManager.getLogger(createTopicLoadTest.class);

    public static void main(String... args) {
        parseArgs(args);

        createTopicLoadTest suite = new createTopicLoadTest();
        suite.runSuiteSync();
    }

    @Override
    public List<HapiSpec> getSpecsInSuite() {
        return List.of(runCreateTopics());
    }

    private static HapiSpec runCreateTopics() {
        PerfTestLoadSettings settings = new PerfTestLoadSettings();
        final AtomicInteger submittedSoFar = new AtomicInteger(0);
        KeyShape submitKeyShape = threshOf(2, SIMPLE, SIMPLE, listOf(2));

        Supplier<HapiSpecOperation[]> submitBurst =
                () ->
                        new HapiSpecOperation[] {
                            createTopic("testTopic" + submittedSoFar.addAndGet(1))
                                    .submitKeyShape(submitKeyShape)
                                    .noLogging()
                                    .hasRetryPrecheckFrom(
                                            BUSY,
                                            DUPLICATE_TRANSACTION,
                                            PLATFORM_TRANSACTION_NOT_CREATED)
                                    .deferStatusResolution()
                        };

        return defaultHapiSpec("runCreateTopics")
                .given(
                        withOpContext(
                                (spec, ignore) -> settings.setFrom(spec.setup().ciPropertiesMap())),
                        logIt(ignore -> settings.toString()))
                .when()
                .then(defaultLoadTest(submitBurst, settings));
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}
