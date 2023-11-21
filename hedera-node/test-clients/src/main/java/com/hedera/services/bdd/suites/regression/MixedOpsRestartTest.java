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
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.createTopic;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileUpdate;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.freezeOnly;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.shutDownAllNodes;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sleepFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.startAllNodes;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.waitForNodesToBecomeActive;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.waitForNodesToFreeze;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.waitForNodesToShutDown;
import static com.hedera.services.bdd.suites.perf.PerfUtilOps.scheduleOpsEnablement;
import static com.hedera.services.bdd.suites.perf.PerfUtilOps.tokenOpsEnablement;

import com.hedera.services.bdd.junit.HapiTest;
import com.hedera.services.bdd.junit.HapiTestSuite;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.suites.HapiSuite;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

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
        return defaultHapiSpec("RestartMixedOps")
                .given(
                        newKeyNamed(SUBMIT_KEY),
                        tokenOpsEnablement(),
                        scheduleOpsEnablement(),
                        cryptoCreate("treasury").key(GENESIS),
                        fileUpdate(APP_PROPERTIES)
                                .payingWith(GENESIS)
                                .overridingProps(Map.of(
                                        "hapi.throttling.buckets.fastOpBucket.capacity",
                                        "1300000.0",
                                        "hapi.throttling.ops.consensusUpdateTopic.capacityRequired",
                                        "1.0",
                                        "hapi.throttling.ops.consensusGetTopicInfo.capacityRequired",
                                        "1.0",
                                        "hapi.throttling.ops.consensusSubmitMessage.capacityRequired",
                                        "1.0",
                                        "tokens.maxPerAccount",
                                        "10000000")),
                        cryptoCreate(sender),
                        cryptoCreate(receiver),
                        createTopic(topic).submitKeyName(SUBMIT_KEY),
                        //                        inParallel(IntStream.range(0, NUM_SUBMISSIONS)
                        //                                .mapToObj(ignore -> cryptoTransfer(tinyBarsFromTo(sender,
                        // receiver, 1L))
                        //                                        .noLogging()
                        //                                        .payingWith(sender)
                        //                                        .signedBy(sender))
                        //                                .toArray(n -> new HapiSpecOperation[n])),
                        //                        sleepFor(10000),
                        //                        inParallel(IntStream.range(0, NUM_SUBMISSIONS)
                        //                                .mapToObj(ignore -> tokenCreate(TOKEN +
                        // tokenId.getAndIncrement())
                        //                                        .payingWith(GENESIS)
                        //                                        .signedBy(GENESIS)
                        //                                        .fee(ONE_HUNDRED_HBARS)
                        //                                        .initialSupply(ONE_HUNDRED_HBARS)
                        //                                        .treasury("treasury"))
                        //                                .toArray(n -> new HapiSpecOperation[n])),
                        //                        sleepFor(10000),
                        //                        inParallel(IntStream.range(0, NUM_SUBMISSIONS)
                        //                                .mapToObj(i -> tokenAssociate(sender, TOKEN + i)
                        //                                        .payingWith(sender)
                        //                                        .signedBy(sender))
                        //                                .toArray(n -> new HapiSpecOperation[n])),
                        sleepFor(10000))
                .when(freezeOnly().startingIn(10).payingWith(GENESIS), waitForNodesToFreeze(75))
                .then(
                        shutDownAllNodes(60),
                        waitForNodesToShutDown(60),
                        startAllNodes(60),
                        waitForNodesToBecomeActive(60));
    }
}
