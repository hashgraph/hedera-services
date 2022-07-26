/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.hedera.services.bdd.suites.records;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.scheduleCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.scheduleDelete;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromTo;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.overriding;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sleepFor;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SCHEDULE_ID;

import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.spec.HapiSpecSetup;
import com.hedera.services.bdd.suites.HapiApiSuite;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class CharacterizationSuite extends HapiApiSuite {
    private static final Logger log = LogManager.getLogger(CharacterizationSuite.class);

    private static final int SCHEDULE_EXPIRY_TIME_SECS = 10;
    private static final int SCHEDULE_EXPIRY_TIME_MS = SCHEDULE_EXPIRY_TIME_SECS * 1000;

    private static final String defaultTxExpiry =
            HapiSpecSetup.getDefaultNodeProps().get("ledger.schedule.txExpiryTimeSecs");

    public static void main(String... args) {
        new CharacterizationSuite().runSuiteSync();
    }

    @Override
    public List<HapiApiSpec> getSpecsInSuite() {
        return List.of(
                /* Stateful specs from ScheduleDeleteSpecs */
                expiredBeforeDeletion(), suiteCleanup());
    }

    public HapiApiSpec expiredBeforeDeletion() {
        final int FAST_EXPIRATION = 0;
        return defaultHapiSpec("ExpiredBeforeDeletion")
                .given(
                        sleepFor(SCHEDULE_EXPIRY_TIME_MS), // await any scheduled expiring entity to
                        // expire
                        newKeyNamed("admin"),
                        cryptoCreate("sender"),
                        cryptoCreate("receiver").balance(0L).receiverSigRequired(true))
                .when(
                        overriding("ledger.schedule.txExpiryTimeSecs", "" + FAST_EXPIRATION),
                        scheduleCreate(
                                        "twoSigXfer",
                                        cryptoTransfer(tinyBarsFromTo("sender", "receiver", 1)))
                                .adminKey("admin")
                                .signedBy(DEFAULT_PAYER, "admin", "sender"),
                        getAccountBalance("receiver").hasTinyBars(0L))
                .then(
                        scheduleDelete("twoSigXfer")
                                .payingWith("sender")
                                .signedBy("sender", "admin")
                                .hasKnownStatus(INVALID_SCHEDULE_ID),
                        overriding(
                                "ledger.schedule.txExpiryTimeSecs",
                                "" + SCHEDULE_EXPIRY_TIME_SECS));
    }

    private HapiApiSpec suiteCleanup() {
        return defaultHapiSpec("suiteCleanup")
                .given()
                .when()
                .then(overriding("ledger.schedule.txExpiryTimeSecs", defaultTxExpiry));
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}
