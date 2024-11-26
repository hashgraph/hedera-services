/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.TestMethodOrder;

@HapiTestLifecycle
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class ScheduleLongTermCreateTests {

    @BeforeAll
    static void beforeAll(@NonNull final TestLifecycle testLifecycle) {
        testLifecycle.overrideInClass(Map.of("scheduling.longTermEnabled", "true"));
    }

    // TODO: is this the expected behavior?
    @Disabled
    @HapiTest
    final Stream<DynamicTest> scheduleCreateDefaultsTo30min() {
        return hapiTest(
                cryptoCreate(RECEIVER).balance(0L),
                scheduleCreate("one", cryptoTransfer(tinyBarsFromTo(DEFAULT_PAYER,RECEIVER, 1L)))
                        .via("createTxn"),
                getScheduleInfo("one").hasRelativeExpiry("createTxn", TimeUnit.MINUTES.toSeconds(30)));
    }

    @HapiTest
    final Stream<DynamicTest> scheduleCreateMinimumTime() {
        return hapiTest(
                cryptoCreate(RECEIVER).balance(0L),
                scheduleCreate("one", cryptoTransfer(tinyBarsFromTo(DEFAULT_PAYER, RECEIVER, 1L)))
                        .waitForExpiry(false)
                        .expiringIn(2)
                        .via("createTxn"),
                getScheduleInfo("one").isExecuted().hasRelativeExpiry("createTxn", 2));
    }
}
