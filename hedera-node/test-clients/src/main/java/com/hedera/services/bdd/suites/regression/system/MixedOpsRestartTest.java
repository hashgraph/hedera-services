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

import static com.hedera.services.bdd.junit.TestTags.RESTART;
import static com.hedera.services.bdd.spec.HapiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.createTopic;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.mintToken;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.freezeOnly;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.inParallel;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.shutDownAllNodes;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sleepFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.startAllNodes;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.waitForNodesToBecomeActive;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.waitForNodesToFreeze;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.waitForNodesToShutDown;
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
import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.spec.HapiSpecSetup;
import com.hedera.services.bdd.suites.HapiSuite;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Tag;

/**
 * This test is to verify restart functionality. It submits a burst of mixed operations, then
 * freezes all nodes, shuts them down, restarts them, and submits the same burst of mixed operations
 * again.
 */
@HapiTestSuite
@Tag(RESTART)
public class MixedOpsRestartTest extends HapiSuite {
    private static final Logger log = LogManager.getLogger(MixedOpsRestartTest.class);

    private static final int NUM_SUBMISSIONS = 20;

    public static void main(String... args) {
        new MixedOpsRestartTest().runSuiteSync();
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }

    @Override
    public List<Stream<DynamicTest>> getSpecsInSuite() {
        return List.of(restartMixedOps());
    }

    @HapiTest
    final Stream<DynamicTest> restartMixedOps() {
        AtomicInteger tokenId = new AtomicInteger(0);
        AtomicInteger scheduleId = new AtomicInteger(0);
        AtomicInteger contractId = new AtomicInteger(0);
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
                        cryptoCreate(TREASURY).payingWith(PAYER),
                        cryptoCreate(SENDER).payingWith(PAYER),
                        cryptoCreate(RECEIVER).payingWith(PAYER),
                        createTopic(TOPIC).submitKeyName(SUBMIT_KEY).payingWith(PAYER),
                        fileCreate(SOME_BYTE_CODE)
                                .path(HapiSpecSetup.getDefaultInstance().defaultContractPath()),
                        inParallel(mixedOpsBurst.get()),
                        sleepFor(10000),
                        inParallel(IntStream.range(0, NUM_SUBMISSIONS)
                                .mapToObj(ignore -> mintToken(
                                                NFT + nftId.getAndDecrement(),
                                                List.of(ByteString.copyFromUtf8("a"), ByteString.copyFromUtf8("b")))
                                        .logging())
                                .toArray(HapiSpecOperation[]::new)))
                .when(
                        // freeze nodes
                        freezeOnly().startingIn(10).payingWith(GENESIS),
                        // wait for all nodes to be in FREEZE status
                        waitForNodesToFreeze(75).logged(),
                        // shut down all nodes, since the platform doesn't automatically go back to ACTIVE status
                        shutDownAllNodes().logged(),
                        // wait for all nodes to be shut down
                        waitForNodesToShutDown(60).logged(),
                        // This sleep is needed, since the ports of shutdown nodes may still be in time_wait status,
                        // which will cause an error that address is already in use when restarting nodes.
                        // Sleep long enough (120s or 180 secs for TIME_WAIT status to be finished based on
                        // kernel settings), so restarting nodes succeeds.
                        sleepFor(180_000L).logged(),
                        // start all nodes
                        startAllNodes().logged(),
                        // wait for all nodes to be ACTIVE
                        waitForNodesToBecomeActive(100).logged())
                .then(
                        // Once nodes come back ACTIVE, submit some operations again
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
