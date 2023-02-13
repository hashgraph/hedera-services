/*
 * Copyright (C) 2021-2023 Hedera Hashgraph, LLC
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

import static com.hedera.services.bdd.spec.HapiSpec.customHapiSpec;
import static com.hedera.services.bdd.spec.HapiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.infrastructure.OpProvider.STANDARD_PERMISSIBLE_PRECHECKS;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getScheduleInfo;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.scheduleCreate;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromTo;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.overriding;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sleepFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withLiveNode;
import static com.hedera.services.bdd.suites.perf.PerfUtilOps.scheduleOpsEnablement;
import static com.hedera.services.bdd.suites.reconnect.AutoRenewEntitiesForReconnect.runTransfersBeforeReconnect;
import static com.hedera.services.bdd.suites.reconnect.ValidateTokensStateAfterReconnect.nonReconnectingNode;
import static com.hedera.services.bdd.suites.reconnect.ValidateTokensStateAfterReconnect.reconnectingNode;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.IDENTICAL_SCHEDULE_ALREADY_CREATED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SCHEDULE_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;

import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.suites.HapiSuite;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * A reconnect test in which a few schedule transactions are created while the node 0.0.8 is
 * disconnected from the network. Once the node is reconnected the state of the schedules are
 * verified on reconnected node
 */
public class SchedulesExpiryDuringReconnect extends HapiSuite {
    private static final String SCHEDULE_EXPIRY_TIME_SECS = "10";

    private static final Logger log = LogManager.getLogger(SchedulesExpiryDuringReconnect.class);

    public static void main(String... args) {
        new ValidateAppPropertiesStateAfterReconnect().runSuiteSync();
    }

    @Override
    public List<HapiSpec> getSpecsInSuite() {
        return List.of(
                runTransfersBeforeReconnect(), suiteSetup(), expireSchedulesDuringReconnect());
    }

    private HapiSpec expireSchedulesDuringReconnect() {
        String soonToBeExpiredSchedule = "schedule-1";
        String longLastingSchedule = "schedule-2";
        String oneOtherSchedule = "schedule-3";
        String duplicateSchedule = "schedule-4";
        return customHapiSpec("SchedulesExpiryDuringReconnect")
                .withProperties(Map.of("txn.start.offset.secs", "-5"))
                .given(
                        scheduleOpsEnablement(),
                        sleepFor(Duration.ofSeconds(25).toMillis()),
                        fileUpdate(APP_PROPERTIES)
                                .payingWith(GENESIS)
                                .overridingProps(Map.of("ledger.schedule.txExpiryTimeSecs", "10")),
                        scheduleCreate(
                                        soonToBeExpiredSchedule,
                                        cryptoTransfer(tinyBarsFromTo(GENESIS, FUNDING, 1))
                                                .fee(ONE_HBAR))
                                .signedBy(DEFAULT_PAYER)
                                .fee(ONE_HUNDRED_HBARS)
                                .hasPrecheckFrom(STANDARD_PERMISSIBLE_PRECHECKS)
                                .hasAnyKnownStatus()
                                .deferStatusResolution()
                                .adminKey(DEFAULT_PAYER)
                                .advertisingCreation()
                                .savingExpectedScheduledTxnId())
                .when(
                        fileUpdate(APP_PROPERTIES)
                                .payingWith(GENESIS)
                                .overridingProps(
                                        Map.of("ledger.schedule.txExpiryTimeSecs", "1800")),
                        scheduleCreate(
                                        longLastingSchedule,
                                        cryptoTransfer(tinyBarsFromTo(GENESIS, FUNDING, 2))
                                                .fee(ONE_HBAR))
                                .fee(ONE_HUNDRED_HBARS)
                                .hasPrecheckFrom(STANDARD_PERMISSIBLE_PRECHECKS)
                                .hasAnyKnownStatus()
                                .deferStatusResolution()
                                .adminKey(DEFAULT_PAYER)
                                .advertisingCreation()
                                .savingExpectedScheduledTxnId(),
                        scheduleCreate(
                                        oneOtherSchedule,
                                        cryptoTransfer(tinyBarsFromTo(GENESIS, FUNDING, 3))
                                                .fee(ONE_HBAR))
                                .signedBy(DEFAULT_PAYER)
                                .fee(ONE_HUNDRED_HBARS)
                                .hasPrecheckFrom(STANDARD_PERMISSIBLE_PRECHECKS)
                                .hasAnyKnownStatus()
                                .deferStatusResolution()
                                .adminKey(DEFAULT_PAYER)
                                .advertisingCreation()
                                .savingExpectedScheduledTxnId(),
                        getAccountBalance(GENESIS).setNode(reconnectingNode).unavailableNode(),
                        cryptoCreate("civilian1").setNode(nonReconnectingNode),
                        cryptoCreate("civilian2").setNode(nonReconnectingNode),
                        cryptoCreate("civilian3").setNode(nonReconnectingNode))
                .then(
                        cryptoTransfer(tinyBarsFromTo(GENESIS, FUNDING, 3))
                                .fee(ONE_HBAR)
                                .setNode(nonReconnectingNode),
                        cryptoTransfer(tinyBarsFromTo(GENESIS, FUNDING, 3))
                                .fee(ONE_HBAR)
                                .setNode(nonReconnectingNode),
                        cryptoTransfer(tinyBarsFromTo(GENESIS, FUNDING, 3))
                                .fee(ONE_HBAR)
                                .setNode(nonReconnectingNode),
                        withLiveNode(reconnectingNode)
                                .within(5 * 60, TimeUnit.SECONDS)
                                .loggingAvailabilityEvery(30)
                                .sleepingBetweenRetriesFor(10),
                        fileUpdate(APP_PROPERTIES)
                                .payingWith(GENESIS)
                                .overridingProps(
                                        Map.of("ledger.schedule.txExpiryTimeSecs", "1000")),
                        scheduleCreate(
                                        duplicateSchedule,
                                        cryptoTransfer(tinyBarsFromTo(GENESIS, FUNDING, 1))
                                                .fee(ONE_HBAR))
                                .fee(ONE_HUNDRED_HBARS)
                                .hasPrecheckFrom(STANDARD_PERMISSIBLE_PRECHECKS)
                                .hasKnownStatus(IDENTICAL_SCHEDULE_ALREADY_CREATED)
                                .deferStatusResolution()
                                .adminKey(DEFAULT_PAYER)
                                .advertisingCreation(),
                        getScheduleInfo(longLastingSchedule)
                                .setNode(reconnectingNode)
                                .hasScheduledTxnIdSavedBy(longLastingSchedule)
                                .hasCostAnswerPrecheck(OK),
                        getScheduleInfo(oneOtherSchedule)
                                .setNode(reconnectingNode)
                                .hasScheduledTxnIdSavedBy(oneOtherSchedule)
                                .hasCostAnswerPrecheck(OK),
                        getScheduleInfo(soonToBeExpiredSchedule)
                                .setNode(reconnectingNode)
                                .hasScheduledTxnIdSavedBy(soonToBeExpiredSchedule)
                                .hasCostAnswerPrecheck(INVALID_SCHEDULE_ID));
    }

    private HapiSpec suiteSetup() {
        return defaultHapiSpec("suiteSetup")
                .given()
                .when()
                .then(
                        overriding(
                                "ledger.schedule.txExpiryTimeSecs",
                                "" + SCHEDULE_EXPIRY_TIME_SECS));
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}
