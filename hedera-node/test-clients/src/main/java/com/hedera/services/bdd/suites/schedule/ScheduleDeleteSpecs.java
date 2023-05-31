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
    private static final String VALID_SCHEDULED_TXN = "validScheduledTxn";
    private static final String SENDER = "sender";
    private static final String RECEIVER = "receiver";
    private static final String ADMIN = "admin";

    public static void main(String... args) {
        new ScheduleDeleteSpecs().runSuiteAsync();
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }

    @Override
    public List<HapiSpec> getSpecsInSuite() {
        return withAndWithoutLongTermEnabled(() -> List.of(
                deleteWithNoAdminKeyFails(),
                unauthorizedDeletionFails(),
                deletingAlreadyDeletedIsObvious(),
                deletingNonExistingFails(),
                deletingExecutedIsPointless()));
    }

    private HapiSpec deleteWithNoAdminKeyFails() {
        return defaultHapiSpec("DeleteWithNoAdminKeyFails")
                .given(
                        cryptoCreate(SENDER),
                        cryptoCreate(RECEIVER),
                        scheduleCreate(VALID_SCHEDULED_TXN, cryptoTransfer(tinyBarsFromTo(SENDER, RECEIVER, 1))))
                .when()
                .then(scheduleDelete(VALID_SCHEDULED_TXN).hasKnownStatus(SCHEDULE_IS_IMMUTABLE));
    }

    private HapiSpec unauthorizedDeletionFails() {
        return defaultHapiSpec("UnauthorizedDeletionFails")
                .given(
                        newKeyNamed(ADMIN),
                        newKeyNamed("non-admin-key"),
                        cryptoCreate(SENDER),
                        cryptoCreate(RECEIVER),
                        scheduleCreate(VALID_SCHEDULED_TXN, cryptoTransfer(tinyBarsFromTo(SENDER, RECEIVER, 1)))
                                .adminKey(ADMIN))
                .when()
                .then(scheduleDelete(VALID_SCHEDULED_TXN)
                        .signedBy(DEFAULT_PAYER, "non-admin-key")
                        .hasKnownStatus(INVALID_SIGNATURE));
    }

    private HapiSpec deletingAlreadyDeletedIsObvious() {
        return defaultHapiSpec("DeletingAlreadyDeletedIsObvious")
                .given(
                        cryptoCreate(SENDER),
                        cryptoCreate(RECEIVER),
                        newKeyNamed(ADMIN),
                        scheduleCreate(VALID_SCHEDULED_TXN, cryptoTransfer(tinyBarsFromTo(SENDER, RECEIVER, 1)))
                                .adminKey(ADMIN),
                        scheduleDelete(VALID_SCHEDULED_TXN).signedBy(ADMIN, DEFAULT_PAYER))
                .when()
                .then(scheduleDelete(VALID_SCHEDULED_TXN)
                        .fee(ONE_HBAR)
                        .signedBy(ADMIN, DEFAULT_PAYER)
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
                        newKeyNamed(ADMIN),
                        scheduleCreate(VALID_SCHEDULED_TXN, submitMessageTo("ofGreatInterest"))
                                .adminKey(ADMIN))
                .when()
                .then(scheduleDelete(VALID_SCHEDULED_TXN)
                        .signedBy(ADMIN, DEFAULT_PAYER)
                        .hasKnownStatus(SCHEDULE_ALREADY_EXECUTED));
    }
}
