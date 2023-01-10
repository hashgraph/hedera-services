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
package com.hedera.services.bdd.suites.consensus;

import static com.hedera.services.bdd.spec.HapiPropertySource.asTopicString;
import static com.hedera.services.bdd.spec.HapiSpec.customHapiSpec;
import static com.hedera.services.bdd.spec.HapiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.keys.KeyShape.SIMPLE;
import static com.hedera.services.bdd.spec.keys.KeyShape.listOf;
import static com.hedera.services.bdd.spec.keys.KeyShape.threshOf;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTopicInfo;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.createTopic;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.deleteTopic;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.submitMessageTo;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.updateTopic;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.logIt;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOPIC_ID;

import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.spec.keys.KeyShape;
import com.hedera.services.bdd.spec.queries.QueryVerbs;
import com.hedera.services.bdd.suites.HapiSuite;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.IntStream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class AssortedHcsOps extends HapiSuite {
    private static final Logger log = LogManager.getLogger(AssortedHcsOps.class);

    public static void main(String... args) {
        new AssortedHcsOps().runSuiteSync();
    }

    @Override
    public List<HapiSpec> getSpecsInSuite() {
        return List.of(
                new HapiSpec[] {
                    //						runMisc(),
                    testRechargingPayer(),
                    //						infoLookup(),
                });
    }

    final String TARGET_DIR = "./dev-system-files";

    private HapiSpec testRechargingPayer() {
        long startingBalance = 1_000_000L;

        return defaultHapiSpec("testRechargingPayer")
                .given(cryptoCreate("rechargingPayer").balance(startingBalance).withRecharging())
                .when()
                .then(
                        IntStream.range(0, 1_000)
                                .mapToObj(
                                        id ->
                                                cryptoCreate("child" + id)
                                                        .payingWith("rechargingPayer")
                                                        .balance(startingBalance / 2)
                                                        .logged())
                                .toArray(HapiSpecOperation[]::new));
    }

    private HapiSpec infoLookup() {
        return defaultHapiSpec("infoLookup")
                .given()
                .when()
                .then(QueryVerbs.getTopicInfo("0.0.1161").logged());
    }

    private HapiSpec runMisc() {
        final int SUBMIT_BURST_SIZE = 10;

        AtomicReference<String> vanillaTopic = new AtomicReference<>();
        AtomicReference<String> updatedTopic = new AtomicReference<>();
        AtomicReference<String> deletedTopic = new AtomicReference<>();

        Function<String, HapiSpecOperation[]> submitBurst =
                ref ->
                        IntStream.range(0, SUBMIT_BURST_SIZE)
                                .mapToObj(
                                        i ->
                                                submitMessageTo(ref)
                                                        .message(
                                                                String.format(
                                                                        "%s message #%d", ref, i)))
                                .toArray(n -> new HapiSpecOperation[n]);

        KeyShape origAdminKey = listOf(SIMPLE, threshOf(2, 3), SIMPLE);
        KeyShape origSubmitKey = listOf(SIMPLE, threshOf(2, 3), listOf(5));

        return customHapiSpec("RunMisc")
                .withProperties(
                        Map.of(
                                "client.feeSchedule.fromDisk",
                                "false",
                                "client.feeSchedule.path",
                                path("feeSchedule.bin"),
                                "client.exchangeRates.fromDisk",
                                "false",
                                "client.exchangeRates.path",
                                path("exchangeRates.bin")))
                .given(
                        newKeyNamed("origAdminKey").shape(origAdminKey),
                        newKeyNamed("origSubmitKey").shape(origSubmitKey),
                        createTopic("vanillaTopic").adminKeyName(GENESIS),
                        createTopic("updatedTopic")
                                .adminKeyName("origAdminKey")
                                .submitKeyName("origSubmitKey"),
                        createTopic("deletedTopic").adminKeyName(GENESIS),
                        withOpContext(
                                (spec, opLog) -> {
                                    vanillaTopic.set(
                                            asTopicString(
                                                    spec.registry().getTopicID("vanillaTopic")));
                                    updatedTopic.set(
                                            asTopicString(
                                                    spec.registry().getTopicID("updatedTopic")));
                                    deletedTopic.set(
                                            asTopicString(
                                                    spec.registry().getTopicID("deletedTopic")));
                                }))
                .when(
                        flattened(
                                submitBurst.apply("vanillaTopic"),
                                submitBurst.apply("updatedTopic"),
                                submitBurst.apply("deletedTopic"),
                                updateTopic("updatedTopic").adminKey(GENESIS).submitKey(GENESIS),
                                deleteTopic("deletedTopic")))
                .then(
                        getTopicInfo("vanillaTopic").hasSeqNo(10L),
                        getTopicInfo("updatedTopic")
                                .hasSeqNo(10L)
                                .hasAdminKey(GENESIS)
                                .hasSubmitKey(GENESIS),
                        getTopicInfo("deletedTopic").hasCostAnswerPrecheck(INVALID_TOPIC_ID),
                        logIt(
                                spec ->
                                        String.format(
                                                "Vanilla: %s, Updated: %s, Deleted: %s",
                                                vanillaTopic.get(),
                                                updatedTopic.get(),
                                                deletedTopic.get())));
    }

    private String path(String file) {
        return Path.of(TARGET_DIR, file).toString();
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}
