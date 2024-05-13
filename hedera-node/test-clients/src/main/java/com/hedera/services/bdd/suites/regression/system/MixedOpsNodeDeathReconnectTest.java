/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

import static com.hedera.services.bdd.junit.TestTags.ND_RECONNECT;
import static com.hedera.services.bdd.spec.HapiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.createTopic;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.mintToken;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.inParallel;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.shutDownNode;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sleepFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.startNode;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.waitForNodeToBeBehind;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.waitForNodeToBecomeActive;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.waitForNodeToFinishReconnect;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.waitForNodeToShutDown;
import static com.hedera.services.bdd.suites.perf.PerfUtilOps.scheduleOpsEnablement;
import static com.hedera.services.bdd.suites.perf.PerfUtilOps.tokenOpsEnablement;
import static com.hedera.services.bdd.suites.regression.system.MixedOperations.ADMIN_KEY;
import static com.hedera.services.bdd.suites.regression.system.MixedOperations.NFT;
import static com.hedera.services.bdd.suites.regression.system.MixedOperations.PAYER;
import static com.hedera.services.bdd.suites.regression.system.MixedOperations.RECEIVER;
import static com.hedera.services.bdd.suites.regression.system.MixedOperations.SENDER;
import static com.hedera.services.bdd.suites.regression.system.MixedOperations.SOME_BYTE_CODE;
import static com.hedera.services.bdd.suites.regression.system.MixedOperations.SUBMIT_KEY;
import static com.hedera.services.bdd.suites.regression.system.MixedOperations.TOPIC;
import static com.hedera.services.bdd.suites.regression.system.MixedOperations.TREASURY;
import static com.hedera.services.bdd.suites.token.TokenTransactSpecs.SUPPLY_KEY;

import com.google.protobuf.ByteString;
import com.hedera.services.bdd.junit.HapiTest;
import com.hedera.services.bdd.junit.HapiTestSuite;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.spec.HapiSpecSetup;
import com.hedera.services.bdd.suites.HapiSuite;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Tag;

/**
 * This test is to verify reconnect functionality. It submits a burst of mixed operations, then
 * shuts one node,and starts it back after some time. Node will reconnect, and once reconnect is completed
 * submits the same burst of mixed operations again.
 */
@HapiTestSuite
@Tag(ND_RECONNECT)
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
    public List<DynamicTest> getSpecsInSuite() {
        return List.of(reconnectMixedOps());
    }

    @HapiTest
    final DynamicTest reconnectMixedOps() {
        final AtomicInteger tokenId = new AtomicInteger(0);
        final AtomicInteger scheduleId = new AtomicInteger(0);
        final AtomicInteger contractId = new AtomicInteger(0);
        AtomicInteger nftId = new AtomicInteger(0);
        Random r = new Random(38582L);
        Supplier<HapiSpecOperation[]> mixedOpsBurst =
                new MixedOperations(NUM_SUBMISSIONS).mixedOps(tokenId, nftId, scheduleId, contractId, r);
        return defaultHapiSpec("RestartMixedOps")
                .given(
                        newKeyNamed(SUBMIT_KEY),
                        newKeyNamed(SUPPLY_KEY),
                        newKeyNamed(ADMIN_KEY),
                        tokenOpsEnablement(),
                        scheduleOpsEnablement(),
                        cryptoCreate(PAYER).balance(100 * ONE_MILLION_HBARS),
                        cryptoCreate(TREASURY),
                        cryptoCreate(SENDER).balance(ONE_MILLION_HBARS).payingWith(PAYER),
                        cryptoCreate(RECEIVER).balance(ONE_MILLION_HBARS).payingWith(PAYER),
                        createTopic(TOPIC).submitKeyName(SUBMIT_KEY).payingWith(PAYER),
                        fileCreate(SOME_BYTE_CODE)
                                .path(HapiSpecSetup.getDefaultInstance().defaultContractPath()),
                        // Kill node 2
                        shutDownNode("Carol").logged(),
                        // Wait for it to shut down
                        waitForNodeToShutDown("Carol", 75).logged(),
                        // This sleep is needed, since the ports of shutdown node may still be in time_wait status,
                        // which will cause an error that address is already in use when restarting nodes.
                        // Sleep long enough (120s or 180 secs for TIME_WAIT status to be finished based on
                        // kernel settings), so restarting node succeeds.
                        sleepFor(180_000L).logged())
                .when(
                        // Submit operations when node 2 is down
                        inParallel(mixedOpsBurst.get()),
                        sleepFor(10000),
                        inParallel(IntStream.range(0, NUM_SUBMISSIONS)
                                .mapToObj(ignore -> mintToken(
                                                NFT + nftId.getAndDecrement(),
                                                List.of(ByteString.copyFromUtf8("a"), ByteString.copyFromUtf8("b")))
                                        .logging())
                                .toArray(HapiSpecOperation[]::new)),
                        // start all nodes
                        startNode("Carol").logged(),
                        // wait for node 2 to go BEHIND
                        waitForNodeToBeBehind("Carol", 60).logged(),
                        // Node 2 will try to reconnect and comes to RECONNECT_COMPLETE
                        waitForNodeToFinishReconnect("Carol", 60).logged(),
                        // Node 2 successfully reconnects and becomes ACTIVE
                        waitForNodeToBecomeActive("Carol", 60).logged())
                .then(
                        // Once node 2 come back ACTIVE, submit some operations again
                        cryptoCreate(PAYER).balance(100 * ONE_MILLION_HBARS),
                        cryptoCreate(TREASURY).balance(ONE_MILLION_HBARS).payingWith(PAYER),
                        cryptoCreate(SENDER).balance(ONE_MILLION_HBARS).payingWith(PAYER),
                        cryptoCreate(RECEIVER).balance(ONE_MILLION_HBARS).payingWith(PAYER),
                        createTopic(TOPIC).submitKeyName(SUBMIT_KEY).payingWith(PAYER),
                        fileCreate(SOME_BYTE_CODE)
                                .path(HapiSpecSetup.getDefaultInstance().defaultContractPath()),
                        inParallel(mixedOpsBurst.get()));
    }
}
