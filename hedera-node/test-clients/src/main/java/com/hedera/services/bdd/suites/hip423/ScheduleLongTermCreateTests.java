// SPDX-License-Identifier: Apache-2.0
package com.hedera.services.bdd.suites.hip423;

import static com.hedera.services.bdd.spec.HapiSpec.hapiTest;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getScheduleInfo;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.scheduleCreate;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromTo;
import static com.hedera.services.bdd.suites.HapiSuite.DEFAULT_PAYER;
import static com.hedera.services.bdd.suites.hip423.LongTermScheduleUtils.RECEIVER;

import com.hedera.services.bdd.junit.HapiTest;
import com.hedera.services.bdd.junit.HapiTestLifecycle;
import com.hedera.services.bdd.junit.support.TestLifecycle;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.TestMethodOrder;

@HapiTestLifecycle
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class ScheduleLongTermCreateTests {
    private static final long MAX_SCHEDULE_EXPIRY_TIME = TimeUnit.DAYS.toSeconds(60);

    @BeforeAll
    static void beforeAll(@NonNull final TestLifecycle testLifecycle) {
        testLifecycle.overrideInClass(Map.of(
                "scheduling.longTermEnabled",
                "true",
                "scheduling.maxExpirationFutureSeconds",
                Long.toString(MAX_SCHEDULE_EXPIRY_TIME)));
    }

    @HapiTest
    final Stream<DynamicTest> scheduleCreateDefaultsToMaxValueFromConfig() {
        return hapiTest(
                cryptoCreate(RECEIVER).balance(0L),
                scheduleCreate("one", cryptoTransfer(tinyBarsFromTo(DEFAULT_PAYER, RECEIVER, 1L)))
                        .via("createTxn"),
                getScheduleInfo("one").hasRelativeExpiry("createTxn", MAX_SCHEDULE_EXPIRY_TIME));
    }
}
