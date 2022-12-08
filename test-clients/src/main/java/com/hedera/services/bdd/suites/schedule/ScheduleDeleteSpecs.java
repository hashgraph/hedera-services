/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.bdd.suites.schedule;

import static com.hedera.services.bdd.spec.HapiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.createTopic;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.scheduleCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.scheduleDelete;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.submitMessageTo;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromTo;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.suites.schedule.ScheduleLongTermExecutionSpecs.withAndWithoutLongTermEnabled;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SCHEDULE_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SIGNATURE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SCHEDULE_ALREADY_DELETED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SCHEDULE_ALREADY_EXECUTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SCHEDULE_IS_IMMUTABLE;

import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.suites.HapiSuite;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ScheduleDeleteSpecs extends HapiSuite {
    private static final Logger log = LogManager.getLogger(ScheduleDeleteSpecs.class);

    public static void main(String... args) {
        new ScheduleDeleteSpecs().runSuiteAsync();
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }

    @Override
    public List<HapiSpec> getSpecsInSuite() {
        return withAndWithoutLongTermEnabled(
                () ->
                        List.of(
                                deleteWithNoAdminKeyFails(),
                                unauthorizedDeletionFails(),
                                deletingAlreadyDeletedIsObvious(),
                                deletingNonExistingFails(),
                                deletingExecutedIsPointless()));
    }

    private HapiSpec deleteWithNoAdminKeyFails() {
        return defaultHapiSpec("DeleteWithNoAdminKeyFails")
                .given(
                        cryptoCreate("sender"),
                        cryptoCreate("receiver"),
                        scheduleCreate(
                                "validScheduledTxn",
                                cryptoTransfer(tinyBarsFromTo("sender", "receiver", 1))))
                .when()
                .then(scheduleDelete("validScheduledTxn").hasKnownStatus(SCHEDULE_IS_IMMUTABLE));
    }

    private HapiSpec unauthorizedDeletionFails() {
        return defaultHapiSpec("UnauthorizedDeletionFails")
                .given(
                        newKeyNamed("admin"),
                        newKeyNamed("non-admin-key"),
                        cryptoCreate("sender"),
                        cryptoCreate("receiver"),
                        scheduleCreate(
                                        "validScheduledTxn",
                                        cryptoTransfer(tinyBarsFromTo("sender", "receiver", 1)))
                                .adminKey("admin"))
                .when()
                .then(
                        scheduleDelete("validScheduledTxn")
                                .signedBy(DEFAULT_PAYER, "non-admin-key")
                                .hasKnownStatus(INVALID_SIGNATURE));
    }

    private HapiSpec deletingAlreadyDeletedIsObvious() {
        return defaultHapiSpec("DeletingAlreadyDeletedIsObvious")
                .given(
                        cryptoCreate("sender"),
                        cryptoCreate("receiver"),
                        newKeyNamed("admin"),
                        scheduleCreate(
                                        "validScheduledTxn",
                                        cryptoTransfer(tinyBarsFromTo("sender", "receiver", 1)))
                                .adminKey("admin"),
                        scheduleDelete("validScheduledTxn").signedBy("admin", DEFAULT_PAYER))
                .when()
                .then(
                        scheduleDelete("validScheduledTxn")
                                .fee(ONE_HBAR)
                                .signedBy("admin", DEFAULT_PAYER)
                                .hasKnownStatus(SCHEDULE_ALREADY_DELETED));
    }

    private HapiSpec deletingNonExistingFails() {
        return defaultHapiSpec("DeletingNonExistingFails")
                .given()
                .when()
                .then(
                        scheduleDelete("0.0.534").fee(ONE_HBAR).hasKnownStatus(INVALID_SCHEDULE_ID),
                        scheduleDelete("0.0.0").fee(ONE_HBAR).hasKnownStatus(INVALID_SCHEDULE_ID));
    }

    private HapiSpec deletingExecutedIsPointless() {
        return defaultHapiSpec("DeletingExecutedIsPointless")
                .given(
                        createTopic("ofGreatInterest"),
                        newKeyNamed("admin"),
                        scheduleCreate("validScheduledTxn", submitMessageTo("ofGreatInterest"))
                                .adminKey("admin"))
                .when()
                .then(
                        scheduleDelete("validScheduledTxn")
                                .signedBy("admin", DEFAULT_PAYER)
                                .hasKnownStatus(SCHEDULE_ALREADY_EXECUTED));
    }
}
