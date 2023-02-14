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
package com.hedera.services.bdd.suites.regression;

import static com.hedera.services.bdd.spec.transactions.TxnVerbs.createTopic;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.submitMessageTo;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.runWithProvider;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.suites.regression.factories.RegressionProviderFactory.factoryFrom;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import com.hedera.services.bdd.spec.HapiPropertySource;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.HapiSpecSetup;
import com.hedera.services.bdd.spec.assertions.TransactionRecordAsserts;
import com.hedera.services.bdd.spec.props.NodeConnectInfo;
import com.hedera.services.bdd.spec.queries.QueryVerbs;
import com.hedera.services.bdd.suites.HapiSuite;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class UmbrellaReduxWithCustomNodes extends HapiSuite {
    private static final Logger log = LogManager.getLogger(UmbrellaRedux.class);

    public static final String DEFAULT_PROPERTIES = "regression-file_ops.properties";
    public static final String MESSAGE_SUBMISSION_SIMPLE = "messageSubmissionSimple";

    public static String nodeId = "0.0.";
    public static String nodeAddress = "";
    public static String payer = "0.0.";
    public static int topic_running_hash_version = 0;

    private AtomicLong duration = new AtomicLong(1);
    private AtomicInteger maxOpsPerSec = new AtomicInteger(Integer.MAX_VALUE);
    private AtomicInteger maxPendingOps = new AtomicInteger(Integer.MAX_VALUE);
    private AtomicInteger backoffSleepSecs = new AtomicInteger(1);
    private AtomicInteger statusTimeoutSecs = new AtomicInteger(5);
    private AtomicReference<String> props = new AtomicReference<>(DEFAULT_PROPERTIES);
    private AtomicReference<TimeUnit> unit = new AtomicReference<>(MILLISECONDS);

    public static void main(String... args) {
        if (args.length < 4) {
            HapiSpecSetup defaultSetup = HapiSpecSetup.getDefaultInstance();
            NodeConnectInfo nodeInfo = defaultSetup.nodes().get(0);
            nodeId += nodeInfo.getAccount().getAccountNum();
            nodeAddress = nodeInfo.getHost();
            log.info(
                    "using default nodeId {} and node address {} -- RUNNING CUSTOM NET MIGRATION ",
                    nodeId,
                    nodeAddress);
            payer += defaultSetup.defaultPayer().getAccountNum();
            topic_running_hash_version = defaultSetup.defaultTopicRunningHashVersion();
        } else {
            nodeId += args[0];
            nodeAddress = args[1];
            payer += args[2];
            topic_running_hash_version = Integer.parseInt(args[3]);
        }

        UmbrellaReduxWithCustomNodes umbrellaReduxWithCustomNodes =
                new UmbrellaReduxWithCustomNodes();
        umbrellaReduxWithCustomNodes.runSuiteSync();
    }

    @Override
    public List<HapiSpec> getSpecsInSuite() {
        return List.of(runUmbrellaReduxWithCustomNodes(), messageSubmissionSimple());
    }

    private HapiSpec messageSubmissionSimple() {
        return HapiSpec.customHapiSpec(MESSAGE_SUBMISSION_SIMPLE)
                .withProperties(
                        Map.of(
                                "default.topic.runningHash.version", topic_running_hash_version,
                                "default.node", nodeId,
                                "default.payer", payer,
                                "nodes", nodeAddress + ":" + nodeId))
                .given(
                        newKeyNamed("submitKey"),
                        createTopic("testTopic").submitKeyName("submitKey"))
                .when(cryptoCreate("civilian").sendThreshold(1L))
                .then(
                        submitMessageTo("testTopic")
                                .message("testmessage")
                                .payingWith("civilian")
                                .hasKnownStatus(SUCCESS)
                                .via(MESSAGE_SUBMISSION_SIMPLE),
                        QueryVerbs.getTxnRecord(MESSAGE_SUBMISSION_SIMPLE)
                                .logged()
                                .hasPriority(
                                        TransactionRecordAsserts.recordWith()
                                                .checkTopicRunningHashVersion(
                                                        topic_running_hash_version)));
    }

    private HapiSpec runUmbrellaReduxWithCustomNodes() {
        return HapiSpec.customHapiSpec("RunUmbrellaReduxWithCustomNodes")
                .withProperties(
                        Map.of(
                                "status.wait.timeout.ms",
                                Integer.toString(1_000 * statusTimeoutSecs.get()),
                                "default.node",
                                nodeId,
                                "default.payer",
                                payer,
                                "nodes",
                                nodeAddress + ":" + nodeId))
                .given()
                .when()
                .then(
                        withOpContext((spec, opLog) -> configureFromCi(spec)),
                        runWithProvider(factoryFrom(props::get))
                                .lasting(duration::get, unit::get)
                                .maxOpsPerSec(maxOpsPerSec::get)
                                .maxPendingOps(maxPendingOps::get)
                                .backoffSleepSecs(backoffSleepSecs::get));
    }

    private void configureFromCi(HapiSpec spec) {
        HapiPropertySource ciProps = spec.setup().ciPropertiesMap();
        if (ciProps.has("duration")) {
            duration.set(ciProps.getLong("duration"));
        }
        if (ciProps.has("unit")) {
            unit.set(ciProps.getTimeUnit("unit"));
        }
        if (ciProps.has("maxOpsPerSec")) {
            maxOpsPerSec.set(ciProps.getInteger("maxOpsPerSec"));
        }
        if (ciProps.has("props")) {
            props.set(ciProps.get("props"));
        }
        if (ciProps.has("maxPendingOps")) {
            maxPendingOps.set(ciProps.getInteger("maxPendingOps"));
        }
        if (ciProps.has("backoffSleepSecs")) {
            backoffSleepSecs.set(ciProps.getInteger("backoffSleepSecs"));
        }
        if (ciProps.has("statusTimeoutSecs")) {
            statusTimeoutSecs.set(ciProps.getInteger("statusTimeoutSecs"));
        }
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}
