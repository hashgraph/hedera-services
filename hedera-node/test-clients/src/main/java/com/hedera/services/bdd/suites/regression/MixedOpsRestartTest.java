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

package com.hedera.services.bdd.suites.regression;

import static com.hedera.services.bdd.spec.HapiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.transactions.TxnUtils.randomUtf8Bytes;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.createTopic;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.scheduleCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.submitMessageTo;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenAssociate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromTo;
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
import static com.hedera.services.bdd.suites.token.TokenTransactSpecs.SUPPLY_KEY;

import com.hedera.services.bdd.junit.HapiTest;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.suites.HapiSuite;
import com.hederahashgraph.api.proto.java.TokenSupplyType;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * This test is to verify restart functionality. It submits a burst of mixed operations, then
 * freezes all nodes, shuts them down, restarts them, and submits the same burst of mixed operations
 * again.
 */
// @HapiTestSuite // This should be disabled to be not run in CI, since it shuts down nodes
public class MixedOpsRestartTest extends HapiSuite {
    private static final Logger log = LogManager.getLogger(MixedOpsRestartTest.class);

    private static final int NUM_SUBMISSIONS = 5;
    private static final String SUBMIT_KEY = "submitKey";
    private static final String TOKEN = "token";
    private static final String SENDER = "sender";
    private static final String RECEIVER = "receiver";
    private static final String TOPIC = "topic";
    private static final String TREASURY = "treasury";

    public static void main(String... args) {
        new AddressAliasIdFuzzing().runSuiteSync();
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }

    @Override
    public List<HapiSpec> getSpecsInSuite() {
        return List.of(restartMixedOps());
    }

    @HapiTest
    private HapiSpec restartMixedOps() {
        AtomicInteger tokenId = new AtomicInteger(0);
        AtomicInteger scheduleId = new AtomicInteger(0);
        Random r = new Random(38582L);

        Supplier<HapiSpecOperation[]> mixedOpsBurst = () -> new HapiSpecOperation[] {
            // Submit some mixed operations
            fileUpdate(APP_PROPERTIES).payingWith(GENESIS).overridingProps(Map.of("tokens.maxPerAccount", "10000000")),
            inParallel(IntStream.range(0, NUM_SUBMISSIONS)
                    .mapToObj(ignore -> cryptoTransfer(tinyBarsFromTo(SENDER, RECEIVER, 1L))
                            .payingWith(SENDER)
                            .logging()
                            .signedBy(SENDER))
                    .toArray(HapiSpecOperation[]::new)),
            sleepFor(10000),
            inParallel(IntStream.range(0, NUM_SUBMISSIONS)
                    .mapToObj(ignore -> tokenCreate(TOKEN + tokenId.getAndIncrement())
                            .supplyType(TokenSupplyType.FINITE)
                            .treasury(TREASURY)
                            .autoRenewPeriod(THREE_MONTHS_IN_SECONDS)
                            .maxSupply(1000)
                            .initialSupply(500)
                            .decimals(1)
                            .adminKey("adminKey")
                            .freezeKey("freezeKey")
                            .kycKey("kycKey")
                            .supplyKey(SUPPLY_KEY)
                            .wipeKey("wipeKey")
                            .feeScheduleKey("feeScheduleKey")
                            .pauseKey("pauseKey")
                            .logging())
                    .toArray(HapiSpecOperation[]::new)),
            sleepFor(10000),
            inParallel(IntStream.range(0, NUM_SUBMISSIONS)
                    .mapToObj(i -> tokenAssociate(SENDER, TOKEN + i)
                            .logging()
                            .payingWith(SENDER)
                            .signedBy(SENDER))
                    .toArray(HapiSpecOperation[]::new)),
            sleepFor(10000),
            submitMessageTo(TOPIC)
                    .message(ArrayUtils.addAll(
                            ByteBuffer.allocate(8)
                                    .putLong(Instant.now().toEpochMilli())
                                    .array(),
                            randomUtf8Bytes(1000)))
                    .payingWith(SENDER)
                    .signedBy(SENDER, SUBMIT_KEY),
            sleepFor(10000),
            inParallel(IntStream.range(0, NUM_SUBMISSIONS)
                    .mapToObj(ignore -> scheduleCreate(
                                    "schedule" + scheduleId.incrementAndGet(),
                                    cryptoTransfer(tinyBarsFromTo(SENDER, RECEIVER, r.nextInt(1000))))
                            .payingWith(SENDER)
                            .signedBy(SENDER)
                            .adminKey(SENDER)
                            .logging())
                    .toArray(HapiSpecOperation[]::new)),
        };
        return defaultHapiSpec("RestartMixedOps")
                .given(
                        newKeyNamed(SUBMIT_KEY),
                        tokenOpsEnablement(),
                        scheduleOpsEnablement(),
                        cryptoCreate(TREASURY),
                        cryptoCreate(SENDER),
                        cryptoCreate(RECEIVER),
                        createTopic(TOPIC).submitKeyName(SUBMIT_KEY),
                        inParallel(mixedOpsBurst.get()))
                .when(
                        // freeze nodes
                        freezeOnly().startingIn(10).payingWith(GENESIS),
                        // wait for all nodes to be in FREEZE status
                        waitForNodesToFreeze(75),
                        // shut down all nodes, since the platform doesn't automatically go back to ACTIVE status
                        shutDownAllNodes(60),
                        // wait for all nodes to be shut down
                        waitForNodesToShutDown(60),
                        // start all nodes
                        startAllNodes(60),
                        // wait for all nodes to be ACTIVE
                        waitForNodesToBecomeActive(60))
                .then(
                        // Once nodes come back ACTIVE, submit some operations again
                        cryptoCreate(TREASURY).balance(ONE_MILLION_HBARS),
                        cryptoCreate(SENDER).balance(ONE_MILLION_HBARS),
                        cryptoCreate(RECEIVER).balance(ONE_MILLION_HBARS),
                        createTopic(TOPIC).submitKeyName(SUBMIT_KEY),
                        inParallel(mixedOpsBurst.get()));
    }
}
