/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.hedera.services.bdd.suites.regression.system;

import static com.hedera.services.bdd.spec.HapiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.createTopic;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.inParallel;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.shutDownNode;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.startNode;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.waitForNodeToBeBehind;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.waitForNodeToBecomeActive;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.waitForNodeToFinishReconnect;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.waitForNodeToShutDown;
import static com.hedera.services.bdd.suites.perf.PerfUtilOps.scheduleOpsEnablement;
import static com.hedera.services.bdd.suites.perf.PerfUtilOps.tokenOpsEnablement;
import static com.hedera.services.bdd.suites.regression.system.MixedOperations.ADMIN_KEY;
import static com.hedera.services.bdd.suites.regression.system.MixedOperations.RECEIVER;
import static com.hedera.services.bdd.suites.regression.system.MixedOperations.SENDER;
import static com.hedera.services.bdd.suites.regression.system.MixedOperations.SUBMIT_KEY;
import static com.hedera.services.bdd.suites.regression.system.MixedOperations.TOPIC;
import static com.hedera.services.bdd.suites.regression.system.MixedOperations.TREASURY;
import static com.hedera.services.bdd.suites.token.TokenTransactSpecs.SUPPLY_KEY;

import com.hedera.services.bdd.junit.HapiTest;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.suites.HapiSuite;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * This test is to verify reconnect functionality. It submits a burst of mixed operations, then
 * shuts one node,and starts it back after some time. Node will reconnect, and once reconnect is completed
 * submits the same burst of mixed operations again.
 */
// @HapiTestSuite // This should be enabled once there is a different tag to be run in CI, since it shuts down nodes
public class MixedOpsNodeDeathReconnectTest extends HapiSuite {
    private static final Logger log = LogManager.getLogger(MixedOpsNodeDeathReconnectTest.class);

    private static final int NUM_SUBMISSIONS = 700;

    public static void main(String... args) {
        new MixedOpsNodeDeathReconnectTest().runSuiteSync();
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }

    @Override
    public List<HapiSpec> getSpecsInSuite() {
        return List.of(reconnectMixedOps());
    }

    @HapiTest
    private HapiSpec reconnectMixedOps() {
        AtomicInteger tokenId = new AtomicInteger(0);
        AtomicInteger scheduleId = new AtomicInteger(0);
        Random r = new Random(38582L);
        Supplier<HapiSpecOperation[]> mixedOpsBurst =
                new MixedOperations(NUM_SUBMISSIONS).mixedOps(tokenId, scheduleId, r);
        return defaultHapiSpec("RestartMixedOps")
                .given(
                        newKeyNamed(SUBMIT_KEY),
                        newKeyNamed(SUPPLY_KEY),
                        newKeyNamed(ADMIN_KEY),
                        tokenOpsEnablement(),
                        scheduleOpsEnablement(),
                        cryptoCreate(TREASURY),
                        cryptoCreate(SENDER).balance(ONE_MILLION_HBARS),
                        cryptoCreate(RECEIVER).balance(ONE_MILLION_HBARS),
                        createTopic(TOPIC).submitKeyName(SUBMIT_KEY),
                        // Kill node 2
                        shutDownNode("Carol"),
                        // Wait for it to shut down
                        waitForNodeToShutDown("Carol", 75))
                .when(
                        // Submit operations when node 2 is down
                        inParallel(mixedOpsBurst.get()),
                        // start all nodes
                        startNode("Carol"),
                        // wait for node 2 to go BEHIND
                        waitForNodeToBeBehind("Carol", 60),
                        // Node 2 will try to reconnect and comes to RECONNECT_COMPLETE
                        waitForNodeToFinishReconnect("Carol", 60),
                        // Node 2 successfully reconnects and becomes ACTIVE
                        waitForNodeToBecomeActive("Carol", 60))
                .then(
                        // Once node 2 come back ACTIVE, submit some operations again
                        cryptoCreate(TREASURY).balance(ONE_MILLION_HBARS),
                        cryptoCreate(SENDER).balance(ONE_MILLION_HBARS),
                        cryptoCreate(RECEIVER).balance(ONE_MILLION_HBARS),
                        createTopic(TOPIC).submitKeyName(SUBMIT_KEY),
                        inParallel(mixedOpsBurst.get()));
    }
}
