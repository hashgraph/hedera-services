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
import com.hedera.services.bdd.spec.transactions.file.HapiFileUpdate;
import com.hedera.services.bdd.suites.HapiApiSuite;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.Map;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.scheduleCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.scheduleDelete;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromTo;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sleepFor;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SCHEDULE_ID;

public class ScheduleDeleteSpecs extends HapiApiSuite {
    private static final Logger log = LogManager.getLogger(ScheduleDeleteSpecs.class);

    private static final int SCHEDULE_EXPIRY_TIME_SECS = 10;
    private static final int SCHEDULE_EXPIRY_TIME_MS = SCHEDULE_EXPIRY_TIME_SECS * 1000;
    private static final HapiFileUpdate updateScheduleExpiryTimeSecs = fileUpdate(APP_PROPERTIES)
            .payingWith(ADDRESS_BOOK_CONTROL)
            .overridingProps(
                    Map.of("ledger.schedule.txExpiryTimeSecs", "" + SCHEDULE_EXPIRY_TIME_SECS));


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
                expiredBeforeDeletion()
        });
    }

    public HapiApiSpec expiredBeforeDeletion() {
        final int FAST_EXPIRATION = 0;
        return defaultHapiSpec("DeleteFailsDueToDeletionExpiration")
                .given(
                        sleepFor(SCHEDULE_EXPIRY_TIME_MS), // await any scheduled expiring entity to expire
                        newKeyNamed("admin"),
                        cryptoCreate("sender").balance(1L),
                        cryptoCreate("receiver").balance(0L).receiverSigRequired(true)
                ).when(
                        fileUpdate(APP_PROPERTIES)
                                .payingWith(ADDRESS_BOOK_CONTROL)
                                .overridingProps(Map.of("ledger.schedule.txExpiryTimeSecs", "" + FAST_EXPIRATION)),
                        scheduleCreate(
                                "twoSigXfer",
                                cryptoTransfer(
                                        tinyBarsFromTo("sender", "receiver", 1)
                                ).signedBy("sender")
                        )
                                .adminKey("admin")
                                .inheritingScheduledSigs(),
                        updateScheduleExpiryTimeSecs,
                        getAccountBalance("receiver").hasTinyBars(0L)
                ).then(
                        scheduleDelete("twoSigXfer")
                                .hasKnownStatus(INVALID_SCHEDULE_ID)
                );
    }
}
