package com.hedera.services.bdd.suites.schedule;

/*-
 * ‌
 * Hedera Services Test Clients
 * ​
 * Copyright (C) 2018 - 2020 Hedera Hashgraph, LLC
 * ​
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
 * ‍
 */

import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.suites.HapiApiSuite;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getScheduleInfo;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.scheduleCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.scheduleDelete;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromTo;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.overriding;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sleepFor;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SCHEDULE_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SIGNATURE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SCHEDULE_IS_IMMUTABLE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;

public class ScheduleDeleteSpecs extends HapiApiSuite {
    private static final Logger log = LogManager.getLogger(ScheduleDeleteSpecs.class);

    private static final int SCHEDULE_EXPIRY_TIME_SECS = 10;
    private static final int SCHEDULE_EXPIRY_TIME_MS = SCHEDULE_EXPIRY_TIME_SECS * 1000;
    private static final HapiSpecOperation updateScheduleExpiryTimeSecs =
            overriding("ledger.schedule.txExpiryTimeSecs", "" + SCHEDULE_EXPIRY_TIME_SECS);


    public static void main(String... args) {
        new ScheduleDeleteSpecs().runSuiteSync();
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }

    @Override
    protected List<HapiApiSpec> getSpecsInSuite() {
        return List.of(new HapiApiSpec[] {
                    followsHappyPath(),
                    deleteWithNoAdminKeyFails(),
                    unauthorizedDeletionFails(),
                    deletingADeletedTxnFails(),
                    deletingNonExistingFails(),
                    deletingExecutedFails(),
                    expiredBeforeDeletion()
                }
        );
    }

    private HapiApiSpec followsHappyPath() {
        return defaultHapiSpec("FollowsHappyPath")
                .given(
                        updateScheduleExpiryTimeSecs,
                        cryptoCreate("sender"),
                        cryptoCreate("receiver"),
                        newKeyNamed("admin"),
                        overriding("scheduling.whitelist", "CryptoTransfer"),
                        scheduleCreate("validScheduledTxn",
                                cryptoTransfer(tinyBarsFromTo("sender", "receiver", 1)))
                                .adminKey("admin")
                )
                .when(
                        scheduleDelete("validScheduledTxn")
                                .signedBy(DEFAULT_PAYER, "admin")
                                .hasKnownStatus(SUCCESS)
                )
                .then(
                        getScheduleInfo("validScheduledTxn")
                                .hasCostAnswerPrecheck(INVALID_SCHEDULE_ID)
                );
    }

    public HapiApiSpec expiredBeforeDeletion() {
        final int FAST_EXPIRATION = 0;
        return defaultHapiSpec("ExpiredBeforeDeletion")
                .given(
                        sleepFor(SCHEDULE_EXPIRY_TIME_MS), // await any scheduled expiring entity to expire
                        newKeyNamed("admin"),
                        overriding("scheduling.whitelist", "CryptoTransfer"),
                        cryptoCreate("sender").balance(1L),
                        cryptoCreate("receiver").balance(0L).receiverSigRequired(true)
                ).when(
                        overriding("ledger.schedule.txExpiryTimeSecs", "" + FAST_EXPIRATION),
                        scheduleCreate(
                                "twoSigXfer",
                                cryptoTransfer(
                                        tinyBarsFromTo("sender", "receiver", 1)
                                ).signedBy("sender")
                        )
                                .adminKey("admin")
                                .signedBy(DEFAULT_PAYER, "admin")
                                .inheritingScheduledSigs(),
                        updateScheduleExpiryTimeSecs,
                        getAccountBalance("receiver").hasTinyBars(0L)
                ).then(
                        scheduleDelete("twoSigXfer")
                                .hasKnownStatus(INVALID_SCHEDULE_ID)
                );
    }

    private HapiApiSpec deleteWithNoAdminKeyFails() {
        return defaultHapiSpec("DeleteWithNoAdminKeyFails")
                .given(
                        updateScheduleExpiryTimeSecs,
                        overriding("scheduling.whitelist", "CryptoTransfer"),
                        cryptoCreate("sender"),
                        cryptoCreate("receiver"),
                        scheduleCreate("validScheduledTxn",
                                cryptoTransfer(tinyBarsFromTo("sender", "receiver", 1)))
                )
                .when(
                )
                .then(
                        scheduleDelete("validScheduledTxn")
                                .hasKnownStatus(SCHEDULE_IS_IMMUTABLE)
                );
    }

    private HapiApiSpec unauthorizedDeletionFails() {
        return defaultHapiSpec("UnauthorizedDeletionFails")
                .given(
                        updateScheduleExpiryTimeSecs,
                        overriding("scheduling.whitelist", "CryptoTransfer"),
                        newKeyNamed("admin"),
                        newKeyNamed("non-admin-key"),
                        cryptoCreate("sender"),
                        cryptoCreate("receiver"),
                        scheduleCreate("validScheduledTxn",
                                cryptoTransfer(tinyBarsFromTo("sender", "receiver", 1)))
                                .adminKey("admin")
                )
                .when(
                )
                .then(
                        scheduleDelete("validScheduledTxn")
                                .signedBy(DEFAULT_PAYER, "non-admin-key")
                                .hasKnownStatus(INVALID_SIGNATURE)
                );
    }

    private HapiApiSpec deletingADeletedTxnFails() {
        return defaultHapiSpec("DeletingADeletedTxnFails")
                .given(
                        updateScheduleExpiryTimeSecs,
                        overriding("scheduling.whitelist", "CryptoTransfer"),
                        cryptoCreate("sender"),
                        cryptoCreate("receiver"),
                        newKeyNamed("admin"),
                        scheduleCreate("validScheduledTxn",
                                    cryptoTransfer(tinyBarsFromTo("sender", "receiver", 1)))
                                .adminKey("admin"),
                        scheduleDelete("validScheduledTxn")
                                .signedBy("admin", DEFAULT_PAYER))
                .when(
                )
                .then(
                        scheduleDelete("validScheduledTxn")
                                .signedBy("admin", DEFAULT_PAYER)
                                .hasKnownStatus(INVALID_SCHEDULE_ID)
                );
    }

    private HapiApiSpec deletingNonExistingFails() {
        return defaultHapiSpec("DeletingNonExistingFails")
                .given(
                        updateScheduleExpiryTimeSecs
                )
                .when()
                .then(
                        scheduleDelete("0.0.534")
                                .hasKnownStatus(INVALID_SCHEDULE_ID)
                );
    }

    private HapiApiSpec deletingExecutedFails() {
        return defaultHapiSpec("DeletingExpiredFails")
                .given(
                        updateScheduleExpiryTimeSecs,
                        overriding("scheduling.whitelist", "CryptoCreate"),
                        newKeyNamed("admin"),
                        scheduleCreate("validScheduledTxn",
                                cryptoCreate("newImmediate"))
                                .adminKey("admin")
                )
                .when()
                .then(
                        scheduleDelete("validScheduledTxn")
                                .signedBy("admin", DEFAULT_PAYER)
                                .hasKnownStatus(INVALID_SCHEDULE_ID)
                );
    }
}
