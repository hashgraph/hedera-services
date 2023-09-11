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

package com.hedera.services.bdd.suites.restart;

import static com.hedera.services.bdd.spec.HapiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.utilops.LoadTest.defaultLoadTest;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.freezeOnly;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.logIt;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.noOp;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.shutdownNode;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.shutdownNodes;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.startupNodes;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.waitForNodesToBecomeActive;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.waitForNodesToFreeze;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.waitForNodesToShutDown;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.BUSY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.DUPLICATE_TRANSACTION;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.PLATFORM_TRANSACTION_NOT_CREATED;

import com.hedera.services.bdd.junit.HapiTest;
import com.hedera.services.bdd.junit.HapiTestSuite;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.suites.HapiSuite;
import com.hedera.services.bdd.suites.perf.PerfTestLoadSettings;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@HapiTestSuite
public class RestartSuite extends HapiSuite {
    private static final Logger logger = LogManager.getLogger(RestartSuite.class);

    private static final int ACCOUNT_CREATION_LIMIT = 20_000;
    private static final int ACCOUNT_CREATION_TPS = 120;
    public static final int DEFAULT_MINS_FOR_TESTS = 1;
    public static final int DEFAULT_THREADS_TESTS = 1;
    private static final AtomicInteger accountNumber = new AtomicInteger(0);

    public static void main(String... args) {
        new RestartSuite().runSuiteSync();
    }

    @Override
    public List<HapiSpec> getSpecsInSuite() {
        return List.of(checkUnavailableNode());
    }

    /**
     * A simple sanity check to verify that when a node goes down, it is actually down, and all other nodes are still
     * up. We have four nodes: Alice (0.0.3), Bob (0.0.4), Carol (0.0.5), Dave (0.0.6).
     */
    @HapiTest
    private HapiSpec checkUnavailableNode() {
        return defaultHapiSpec("CheckUnavailableNode")
                .given()
                .when(shutdownNode("Bob"))// 0.0.4
                .then(getAccountBalance(GENESIS).setNode("0.0.4").unavailableNode(), // Bob is NOT available
                        getAccountBalance(GENESIS).setNode("0.0.3"),                 // Alice IS available
                        getAccountBalance(GENESIS).setNode("0.0.5"),                 // Carol IS available
                        getAccountBalance(GENESIS).setNode("0.0.6"));                // Dave IS available
    }

    /**
     * Without performing an update, we can bring down ALL nodes and bring them back up, safely.
     */
    @HapiTest
    private HapiSpec bounceAllNodes() {
        return defaultHapiSpec("BounceAllNodes")
                .given()
                .when(
                        // Freeze the network, this is needed for an orderly shutdown, to make sure the network
                        // has successfully written all state to disk.
                        freezeOnly()
                                .payingWith(GENESIS)
                                .startingIn(10)
                                .seconds(),
                        // Then wait until that freeze has taken place
                        waitForNodesToFreeze(30),
                        // Then we can shut down all the nodes. Wait for them to all stop.
                        shutdownNodes(),
                        waitForNodesToShutDown(60),
                        // Verify that they have all stopped
                        getAccountBalance(GENESIS).setNode("0.0.3").unavailableNode(), // Alice is DOWN
                        getAccountBalance(GENESIS).setNode("0.0.4").unavailableNode(), // Bob is DOWN
                        getAccountBalance(GENESIS).setNode("0.0.5").unavailableNode(), // Carol is DOWN
                        getAccountBalance(GENESIS).setNode("0.0.6").unavailableNode(), // Dave is DOWN
                        // Start the nodes back up and wait for them to become active
                        startupNodes(),
                        waitForNodesToBecomeActive(60))
                .then(
                        // Now that we have frozen, stopped, and restarted the network, all nodes should be up
                        getAccountBalance(GENESIS).setNode("0.0.3"),  // Alice is UP
                        getAccountBalance(GENESIS).setNode("0.0.4"),  // Bob is UP
                        getAccountBalance(GENESIS).setNode("0.0.5"),  // Carol is UP
                        getAccountBalance(GENESIS).setNode("0.0.6")); // Dave is UP
    }

    /**
     * Without performing an update, we can bring down ALL nodes and bring them back up, safely. And, we can do it while
     * we're accepting a load of traffic.
     */
    @HapiTest
    private HapiSpec bounceAllNodesWithTraffic() {
        PerfTestLoadSettings settings = new PerfTestLoadSettings(
                ACCOUNT_CREATION_TPS, DEFAULT_MINS_FOR_TESTS, DEFAULT_THREADS_TESTS);

        Supplier<HapiSpecOperation[]> createBurst = () -> new HapiSpecOperation[] {generateCreateAccountOperation()};

        return defaultHapiSpec("BounceAllNodesWithTraffic")
                .given(
                        logIt(ignore -> settings.toString()),
                        defaultLoadTest(createBurst, settings)
                )
                .when(
                        // Freeze the network, this is needed for an orderly shutdown, to make sure the network
                        // has successfully written all state to disk.
                        freezeOnly()
                                .payingWith(GENESIS)
                                .startingIn(10)
                                .seconds(),
                        // Then wait until that freeze has taken place
                        waitForNodesToFreeze(30),
                        // Then we can shut down all the nodes. Wait for them to all stop.
                        shutdownNodes(),
                        waitForNodesToShutDown(60),
                        // Verify that they have all stopped
                        getAccountBalance(GENESIS).setNode("0.0.3").unavailableNode(), // Alice is DOWN
                        getAccountBalance(GENESIS).setNode("0.0.4").unavailableNode(), // Bob is DOWN
                        getAccountBalance(GENESIS).setNode("0.0.5").unavailableNode(), // Carol is DOWN
                        getAccountBalance(GENESIS).setNode("0.0.6").unavailableNode(), // Dave is DOWN
                        // Start the nodes back up and wait for them to become active
                        startupNodes(),
                        waitForNodesToBecomeActive(60))
                .then(
                        // Do some additional work, just to be sure that we *can* do work and not ISS or have a problem.
                        defaultLoadTest(createBurst, settings));
    }

    @Override
    protected Logger getResultsLogger() {
        return logger;
    }


    private synchronized HapiSpecOperation generateCreateAccountOperation() {
        final long accNumber = accountNumber.getAndIncrement();
        if (accNumber >= ACCOUNT_CREATION_LIMIT) {
            return noOp();
        }

        return cryptoCreate("account" + accNumber)
                .balance(accNumber)
//                .noLogging()
                .hasRetryPrecheckFrom(BUSY, DUPLICATE_TRANSACTION, PLATFORM_TRANSACTION_NOT_CREATED)
                .deferStatusResolution(); // don't block, just move on
    }
}
