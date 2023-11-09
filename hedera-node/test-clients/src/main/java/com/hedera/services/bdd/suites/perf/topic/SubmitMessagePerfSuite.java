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

package com.hedera.services.bdd.suites.perf.topic;

import static com.hedera.services.bdd.spec.HapiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTopicInfo;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.createTopic;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.submitMessageTo;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.finishThroughputObs;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.inParallel;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.startThroughputObs;

import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.suites.HapiSuite;
import java.util.Arrays;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class SubmitMessagePerfSuite extends HapiSuite {
    private static final Logger log = LogManager.getLogger(SubmitMessagePerfSuite.class);
    private static final String TEST_TOPIC = "testTopic";

    public static void main(String... args) {
        SubmitMessagePerfSuite suite = new SubmitMessagePerfSuite();
        suite.runSuiteSync();
    }

    @Override
    public List<HapiSpec> getSpecsInSuite() {
        return Arrays.asList(submitMessagePerf());
    }

    @Override
    public boolean canRunConcurrent() {
        return false;
    }

    private HapiSpec submitMessagePerf() {
        final int NUM_SUBMISSIONS = 100_000;

        return defaultHapiSpec("submitMessagePerf")
                .given(newKeyNamed("submitKey"), createTopic(TEST_TOPIC).submitKeyName("submitKey"))
                .when(
                        startThroughputObs("submitMessageThroughput").msToSaturateQueues(50L),
                        inParallel(
                                // only ask for record for the last transaction
                                asOpArray(
                                        NUM_SUBMISSIONS,
                                        i -> (i == (NUM_SUBMISSIONS - 1))
                                                ? submitMessageTo(TEST_TOPIC).message("testMessage" + i)
                                                : submitMessageTo(TEST_TOPIC)
                                                        .message("testMessage" + i)
                                                        .deferStatusResolution())))
                .then(finishThroughputObs("submitMessageThroughput")
                        .gatedByQuery(() -> getTopicInfo(TEST_TOPIC)
                                .hasSeqNo(NUM_SUBMISSIONS)
                                .logged())
                        .sleepMs(1_000L)
                        .expiryMs(300_000L));
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}
