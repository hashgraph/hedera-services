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

import com.hedera.services.bdd.junit.HapiTest;
import com.hedera.services.bdd.junit.HapiTestSuite;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.suites.HapiSuite;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.net.InetAddress;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.stream.IntStream;

import static com.hedera.services.bdd.spec.HapiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.createTopic;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.scheduleCreate;
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

/**
 * This test is to verify restart functionality. It submits a burst of mixed operations, then
 * freezes all nodes, shuts them down, restarts them, and submits the same burst of mixed operations
 * again.
 */
@HapiTestSuite
public class MixedOpsRestartTest extends HapiSuite {
    private static final Logger log = LogManager.getLogger(MixedOpsRestartTest.class);

    public static final int NUM_SUBMISSIONS = 5;
    public static final String SUBMIT_KEY = "submitKey";
    public static final String TOKEN = "token";

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
        final String sender = "sender";
        final String receiver = "receiver";
        final String topic = "topic";

        AtomicInteger tokenId = new AtomicInteger(0);
        AtomicInteger scheduleId = new AtomicInteger(0);

        Supplier<HapiSpecOperation[]> mixedOpsBurst = () -> new HapiSpecOperation[] {
                // Submit some mixed operations
                fileUpdate(APP_PROPERTIES)
                        .payingWith(GENESIS)
                        .overridingProps(Map.of(
                        "tokens.maxPerAccount",
                        "10000000")),
                inParallel(IntStream.range(0, NUM_SUBMISSIONS)
                        .mapToObj(ignore -> cryptoTransfer(tinyBarsFromTo(sender, receiver, 1L))
                                .payingWith(sender)
                                .logging()
                                .signedBy(sender))
                        .toArray(HapiSpecOperation[]::new)),
                sleepFor(10000),
                inParallel(IntStream.range(0, NUM_SUBMISSIONS)
                        .mapToObj(ignore -> tokenCreate(TOKEN + tokenId.getAndIncrement())
                                .payingWith(GENESIS)
                                .signedBy(GENESIS)
                                .fee(ONE_HUNDRED_HBARS)
                                .initialSupply(ONE_HUNDRED_HBARS)
                                .logging()
                                .treasury("treasury"))
                        .toArray(HapiSpecOperation[]::new)),
                sleepFor(10000),
                inParallel(IntStream.range(0, NUM_SUBMISSIONS)
                        .mapToObj(i -> tokenAssociate(sender, TOKEN + i)
                                .logging()
                                .payingWith(sender)
                                .signedBy(sender))
                        .toArray(HapiSpecOperation[]::new)),
                sleepFor(10000),
                inParallel(IntStream.range(0, NUM_SUBMISSIONS)
                        .mapToObj(ignore -> scheduleCreate(
                                "schedule-" + getHostName() + "-" + scheduleId.getAndIncrement(),
                                cryptoTransfer(tinyBarsFromTo(sender, receiver, 1)))
                                .alsoSigningWith(sender)
                                .adminKey(DEFAULT_PAYER)
                                .logging())
                        .toArray(HapiSpecOperation[]::new)),
                sleepFor(10000)
        };
        return defaultHapiSpec("RestartMixedOps")
                .given(
                        newKeyNamed(SUBMIT_KEY),
                        tokenOpsEnablement(),
                        scheduleOpsEnablement(),
                        cryptoCreate("treasury").key(GENESIS),
                        cryptoCreate(sender),
                        cryptoCreate(receiver),
                        createTopic(topic).submitKeyName(SUBMIT_KEY),
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
                        // Once nodes come back ACTIVE, submit same operations again
                        cryptoCreate("treasury").key(GENESIS),
                        cryptoCreate(sender),
                        cryptoCreate(receiver),
                        createTopic(topic).submitKeyName(SUBMIT_KEY),
                        inParallel(mixedOpsBurst.get()));
    }

    private String getHostName() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            log.info("Error getting host name");
            return "Hostname-Not-Available";
        }
    }
}
