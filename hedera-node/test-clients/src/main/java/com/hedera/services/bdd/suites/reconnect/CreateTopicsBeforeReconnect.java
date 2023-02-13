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
package com.hedera.services.bdd.suites.reconnect;

import static com.hedera.services.bdd.spec.HapiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.createTopic;
import static com.hedera.services.bdd.spec.utilops.LoadTest.defaultLoadTest;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.noOp;
import static com.hedera.services.bdd.suites.reconnect.CreateAccountsBeforeReconnect.DEFAULT_MINS_FOR_RECONNECT_TESTS;
import static com.hedera.services.bdd.suites.reconnect.CreateAccountsBeforeReconnect.DEFAULT_THREADS_FOR_RECONNECT_TESTS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.BUSY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.DUPLICATE_TRANSACTION;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.PLATFORM_TRANSACTION_NOT_CREATED;

import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.suites.HapiSuite;
import com.hedera.services.bdd.suites.perf.PerfTestLoadSettings;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class CreateTopicsBeforeReconnect extends HapiSuite {
    private static final Logger log = LogManager.getLogger(CreateTopicsBeforeReconnect.class);

    private static final int TOPIC_CREATION_LIMIT = 20_000;
    private static final int TOPIC_CREATION_RECONNECT_TPS = 120;

    public static void main(String... args) {
        new CreateTopicsBeforeReconnect().runSuiteSync();
    }

    private static final AtomicInteger topicNumber = new AtomicInteger(0);

    @Override
    public List<HapiSpec> getSpecsInSuite() {
        return List.of(runCreateTopics());
    }

    private synchronized HapiSpecOperation generateTopicCreateOperation() {
        final long topic = topicNumber.getAndIncrement();
        if (topic >= TOPIC_CREATION_LIMIT) {
            return noOp();
        }

        return createTopic("topic" + topic)
                .noLogging()
                .hasRetryPrecheckFrom(BUSY, DUPLICATE_TRANSACTION, PLATFORM_TRANSACTION_NOT_CREATED)
                .deferStatusResolution();
    }

    private HapiSpec runCreateTopics() {
        PerfTestLoadSettings settings =
                new PerfTestLoadSettings(
                        TOPIC_CREATION_RECONNECT_TPS,
                        DEFAULT_MINS_FOR_RECONNECT_TESTS,
                        DEFAULT_THREADS_FOR_RECONNECT_TESTS);

        Supplier<HapiSpecOperation[]> createBurst =
                () -> new HapiSpecOperation[] {generateTopicCreateOperation()};

        return defaultHapiSpec("RunCreateTopics")
                .given(/*logIt(ignore -> settings.toString())*/ )
                .when()
                .then(defaultLoadTest(createBurst, settings));
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}
