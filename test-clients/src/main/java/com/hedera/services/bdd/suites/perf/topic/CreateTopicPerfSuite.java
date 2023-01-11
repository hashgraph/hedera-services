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
import static com.hedera.services.bdd.spec.keys.KeyShape.SIMPLE;
import static com.hedera.services.bdd.spec.keys.KeyShape.listOf;
import static com.hedera.services.bdd.spec.keys.KeyShape.threshOf;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTopicInfo;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.createTopic;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.finishThroughputObs;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.inParallel;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.startThroughputObs;

import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.keys.KeyShape;
import com.hedera.services.bdd.suites.HapiSuite;
import java.util.Arrays;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class CreateTopicPerfSuite extends HapiSuite {
    private static final Logger log = LogManager.getLogger(CreateTopicPerfSuite.class);

    public static void main(String... args) {
        CreateTopicPerfSuite suite = new CreateTopicPerfSuite();
        suite.runSuiteSync();
    }

    @Override
    public List<HapiSpec> getSpecsInSuite() {
        return Arrays.asList(createTopicPerf());
    }

    @Override
    public boolean canRunConcurrent() {
        return false;
    }

    private HapiSpec createTopicPerf() {
        final int NUM_TOPICS = 100000;

        KeyShape submitKeyShape = threshOf(2, SIMPLE, SIMPLE, listOf(2));

        return defaultHapiSpec("createTopicPerf")
                .given()
                .when(
                        startThroughputObs("createTopicThroughput").msToSaturateQueues(50L),
                        inParallel(
                                // only ask for record for the last transaction
                                asOpArray(
                                        NUM_TOPICS,
                                        i ->
                                                (i == (NUM_TOPICS - 1))
                                                        ? createTopic("testTopic" + i)
                                                                .submitKeyShape(submitKeyShape)
                                                        : createTopic("testTopic" + i)
                                                                .submitKeyShape(submitKeyShape)
                                                                .deferStatusResolution())))
                .then(
                        // wait until the record of the last transaction are ready
                        finishThroughputObs("createTopicThroughput")
                                .gatedByQuery(() -> getTopicInfo("testTopic" + (NUM_TOPICS - 1)))
                                .sleepMs(1_000L)
                                .expiryMs(300_000L));
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}
