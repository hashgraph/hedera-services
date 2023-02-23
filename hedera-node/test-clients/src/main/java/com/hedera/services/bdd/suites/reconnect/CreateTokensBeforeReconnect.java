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
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.utilops.LoadTest.defaultLoadTest;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.logIt;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.noOp;
import static com.hedera.services.bdd.suites.reconnect.CreateAccountsBeforeReconnect.DEFAULT_MINS_FOR_RECONNECT_TESTS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.BUSY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.PLATFORM_TRANSACTION_NOT_CREATED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_ALREADY_ASSOCIATED_TO_ACCOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.UNKNOWN;

import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.suites.HapiSuite;
import com.hedera.services.bdd.suites.perf.PerfTestLoadSettings;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class CreateTokensBeforeReconnect extends HapiSuite {
    private static final Logger log = LogManager.getLogger(CreateTokensBeforeReconnect.class);

    private static final int TOKEN_CREATION_LIMIT = 300000;
    private static final int TOKEN_CREATION_RECONNECT_TPS = 120;
    private static final int DEFAULT_TOKEN_THREADS_FOR_RECONNECT_TESTS = 10;

    public static void main(String... args) {
        new CreateTokensBeforeReconnect().runSuiteSync();
    }

    private static final AtomicInteger tokenNumber = new AtomicInteger(0);

    @Override
    public List<HapiSpec> getSpecsInSuite() {
        return List.of(runCreateTopics());
    }

    private HapiSpecOperation generateTopicCreateOperation() {
        final long token = tokenNumber.getAndIncrement();
        if (token >= TOKEN_CREATION_LIMIT) {
            return noOp();
        }

        return tokenCreate("token" + token)
                .noLogging()
                .hasRetryPrecheckFrom(BUSY, PLATFORM_TRANSACTION_NOT_CREATED)
                .hasKnownStatusFrom(SUCCESS, TOKEN_ALREADY_ASSOCIATED_TO_ACCOUNT, UNKNOWN)
                .deferStatusResolution();
    }

    private HapiSpec runCreateTopics() {
        PerfTestLoadSettings settings = new PerfTestLoadSettings(
                TOKEN_CREATION_RECONNECT_TPS,
                DEFAULT_MINS_FOR_RECONNECT_TESTS,
                DEFAULT_TOKEN_THREADS_FOR_RECONNECT_TESTS);

        Supplier<HapiSpecOperation[]> createBurst = () -> new HapiSpecOperation[] {generateTopicCreateOperation()};

        return defaultHapiSpec("RunCreateTokens")
                .given(logIt(ignore -> settings.toString()))
                .when()
                .then(defaultLoadTest(createBurst, settings));
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}
