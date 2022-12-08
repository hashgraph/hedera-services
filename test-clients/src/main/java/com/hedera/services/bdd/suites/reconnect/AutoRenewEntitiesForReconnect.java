/*
 * Copyright (C) 2021-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.bdd.suites.reconnect;

import static com.hedera.services.bdd.spec.HapiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileUpdate;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromTo;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withLiveNode;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.suites.autorenew.AutoRenewConfigChoices.disablingAutoRenewWithDefaults;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;

import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.spec.utilops.CustomSpecAssert;
import com.hedera.services.bdd.suites.HapiSuite;
import com.hedera.services.bdd.suites.autorenew.AutoRenewConfigChoices;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class AutoRenewEntitiesForReconnect extends HapiSuite {
    private static final Logger log = LogManager.getLogger(AutoRenewEntitiesForReconnect.class);

    public static void main(String... args) {
        new AutoRenewEntitiesForReconnect().runSuiteSync();
    }

    @Override
    public List<HapiSpec> getSpecsInSuite() {
        return List.of(
                runTransfersBeforeReconnect(),
                autoRenewAccountGetsDeletedOnReconnectingNodeAsWell(),
                accountAutoRenewalSuiteCleanup());
    }

    private HapiSpec autoRenewAccountGetsDeletedOnReconnectingNodeAsWell() {
        String autoDeleteAccount = "autoDeleteAccount";
        int autoRenewSecs = 1;
        return defaultHapiSpec("AutoRenewAccountGetsDeletedOnReconnectingNodeAsWell")
                .given(
                        fileUpdate(APP_PROPERTIES)
                                .payingWith(GENESIS)
                                .overridingProps(
                                        AutoRenewConfigChoices.propsForAccountAutoRenewOnWith(
                                                autoRenewSecs, 0, 100, 2))
                                .erasingProps(Set.of("minimumAutoRenewDuration")),
                        cryptoCreate(autoDeleteAccount).autoRenewSecs(autoRenewSecs).balance(0L))
                .when(
                        // do some transfers so that we pass autoRenewSecs
                        withOpContext(
                                (spec, ctxLog) -> {
                                    List<HapiSpecOperation> opsList =
                                            new ArrayList<HapiSpecOperation>();
                                    for (int i = 0; i < 50; i++) {
                                        opsList.add(
                                                cryptoTransfer(tinyBarsFromTo(GENESIS, NODE, 1L))
                                                        .logged());
                                    }
                                    CustomSpecAssert.allRunFor(spec, opsList);
                                }),
                        withLiveNode("0.0.8")
                                .within(120, TimeUnit.SECONDS)
                                .loggingAvailabilityEvery(10)
                                .sleepingBetweenRetriesFor(10))
                .then(
                        getAccountBalance(autoDeleteAccount)
                                .setNode("0.0.8")
                                .hasAnswerOnlyPrecheckFrom(INVALID_ACCOUNT_ID));
    }

    private HapiSpec accountAutoRenewalSuiteCleanup() {
        return defaultHapiSpec("accountAutoRenewalSuiteCleanup")
                .given()
                .when()
                .then(
                        fileUpdate(APP_PROPERTIES)
                                .payingWith(GENESIS)
                                .overridingProps(disablingAutoRenewWithDefaults()));
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }

    /**
     * Since reconnect is not supported when node starts from genesis, run some transactions before
     * running correctness tests so that a state is saved before reconnect.
     *
     * @return a {@link HapiSpec} to do some crypto transfer transactions before reconnect
     */
    public static HapiSpec runTransfersBeforeReconnect() {
        return defaultHapiSpec("runTransfersBeforeReconnect")
                .given()
                .when()
                .then(
                        // do some transfers to save a state before reconnect
                        withOpContext(
                                (spec, ctxLog) -> {
                                    List<HapiSpecOperation> opsList =
                                            new ArrayList<HapiSpecOperation>();
                                    for (int i = 0; i < 500; i++) {
                                        opsList.add(
                                                cryptoTransfer(tinyBarsFromTo(GENESIS, NODE, 1L)));
                                    }
                                    CustomSpecAssert.allRunFor(spec, opsList);
                                }));
    }
}
