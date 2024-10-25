/*
 * Copyright (C) 2022-2024 Hedera Hashgraph, LLC
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

import static com.hedera.services.bdd.spec.HapiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getScheduleInfo;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.scheduleCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.scheduleSign;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromTo;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.overriding;
import static com.hedera.services.bdd.suites.HapiSuite.ONE_HBAR;

import com.hedera.services.bdd.junit.support.TestLifecycle;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;

// Enable when long term scheduling is enabled
// @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
// @HapiTestLifecycle
public class DisabledLongTermExecutionScheduleTest {

    private static final String RECEIVER = "receiver";
    private static final String SENDER = "sender";
    private static final String SENDER_TXN = "senderTxn";
    private static final String CREATE_TXN = "createTxn";
    private static final String PAYER = "payer";
    private static final String THREE_SIG_XFER = "threeSigXfer";
    private static final String SCHEDULING_LONG_TERM_ENABLED = "scheduling.longTermEnabled";

    //    @BeforeAll
    static void beforeAll(@NonNull final TestLifecycle lifecycle) {
        // override and preserve old values
        lifecycle.overrideInClass(Map.of(
                "scheduling.longTermEnabled",
                "false",
                "scheduling.whitelist",
                "CryptoTransfer,ConsensusSubmitMessage,TokenBurn,TokenMint,CryptoApproveAllowance"));
    }

    //    @HapiTest
    //    @Order(1)
    public Stream<DynamicTest> waitForExpiryIgnoredWhenLongTermDisabled() {

        return defaultHapiSpec("WaitForExpiryIgnoredWhenLongTermDisabled")
                .given(
                        cryptoCreate(PAYER).balance(ONE_HBAR),
                        cryptoCreate(SENDER).balance(1L).via(SENDER_TXN),
                        cryptoCreate(RECEIVER).balance(0L).receiverSigRequired(true))
                .when(
                        scheduleCreate(
                                        THREE_SIG_XFER,
                                        cryptoTransfer(tinyBarsFromTo(SENDER, RECEIVER, 1))
                                                .fee(ONE_HBAR))
                                .withRelativeExpiry(SENDER_TXN, 50)
                                .waitForExpiry(true)
                                .designatingPayer(PAYER)
                                .alsoSigningWith(SENDER, RECEIVER),
                        getAccountBalance(RECEIVER).hasTinyBars(0L),
                        scheduleSign(THREE_SIG_XFER).alsoSigningWith(PAYER))
                .then(
                        getAccountBalance(RECEIVER).hasTinyBars(1L),
                        getScheduleInfo(THREE_SIG_XFER)
                                .hasScheduleId(THREE_SIG_XFER)
                                .hasWaitForExpiry(false)
                                .isExecuted());
    }

    //    @HapiTest
    //    @Order(2)
    public Stream<DynamicTest> expiryIgnoredWhenLongTermDisabled() {
        return defaultHapiSpec("ExpiryIgnoredWhenLongTermDisabled")
                .given(
                        cryptoCreate(SENDER).balance(ONE_HBAR).via(SENDER_TXN),
                        cryptoCreate(RECEIVER).balance(0L).receiverSigRequired(true))
                .when(scheduleCreate(
                                THREE_SIG_XFER,
                                cryptoTransfer(tinyBarsFromTo(SENDER, RECEIVER, 1))
                                        .fee(ONE_HBAR))
                        .withRelativeExpiry(SENDER_TXN, 20)
                        .waitForExpiry(true)
                        .designatingPayer(SENDER))
                .then(
                        scheduleSign(THREE_SIG_XFER).alsoSigningWith(SENDER, RECEIVER),
                        getScheduleInfo(THREE_SIG_XFER)
                                .hasScheduleId(THREE_SIG_XFER)
                                .isExecuted()
                                .isNotDeleted(),
                        getAccountBalance(RECEIVER).hasTinyBars(1L));
    }

    //    @HapiTest
    //    @Order(3)
    public Stream<DynamicTest> waitForExpiryIgnoredWhenLongTermDisabledThenEnabled() {

        return defaultHapiSpec("WaitForExpiryIgnoredWhenLongTermDisabledThenEnabled")
                .given(
                        cryptoCreate(PAYER).balance(ONE_HBAR),
                        cryptoCreate(SENDER).balance(1L).via(SENDER_TXN),
                        cryptoCreate(RECEIVER).balance(0L).receiverSigRequired(true))
                .when(
                        scheduleCreate(
                                        THREE_SIG_XFER,
                                        cryptoTransfer(tinyBarsFromTo(SENDER, RECEIVER, 1))
                                                .fee(ONE_HBAR))
                                .withRelativeExpiry(SENDER_TXN, 4)
                                .waitForExpiry(true)
                                .designatingPayer(PAYER)
                                .alsoSigningWith(SENDER, RECEIVER),
                        getAccountBalance(RECEIVER).hasTinyBars(0L),
                        overriding(SCHEDULING_LONG_TERM_ENABLED, "true"),
                        scheduleSign(THREE_SIG_XFER).alsoSigningWith(PAYER))
                .then(
                        cryptoCreate("triggerTxn"),
                        getAccountBalance(RECEIVER).hasTinyBars(1L),
                        getScheduleInfo(THREE_SIG_XFER)
                                .hasScheduleId(THREE_SIG_XFER)
                                .hasWaitForExpiry(false)
                                .isExecuted(),
                        overriding(SCHEDULING_LONG_TERM_ENABLED, "false"));
    }

    //    @HapiTest
    //    @Order(4)
    public Stream<DynamicTest> expiryIgnoredWhenLongTermDisabledThenEnabled() {
        return defaultHapiSpec("ExpiryIgnoredWhenLongTermDisabledThenEnabled")
                .given(
                        cryptoCreate(PAYER).balance(ONE_HBAR),
                        cryptoCreate(SENDER).balance(1L).via(SENDER_TXN),
                        cryptoCreate(RECEIVER).balance(0L).receiverSigRequired(true))
                .when(scheduleCreate(
                                THREE_SIG_XFER,
                                cryptoTransfer(tinyBarsFromTo(SENDER, RECEIVER, 1))
                                        .fee(ONE_HBAR))
                        .withRelativeExpiry(SENDER_TXN, 4)
                        .waitForExpiry(true)
                        .designatingPayer(PAYER)
                        .via(CREATE_TXN))
                .then(
                        getScheduleInfo(THREE_SIG_XFER)
                                .hasScheduleId(THREE_SIG_XFER)
                                .hasWaitForExpiry(false)
                                .hasRelativeExpiry(CREATE_TXN, TimeUnit.MINUTES.toSeconds(30))
                                .isNotExecuted()
                                .isNotDeleted(),
                        scheduleSign(THREE_SIG_XFER)
                                .alsoSigningWith(PAYER, SENDER, RECEIVER)
                                .payingWith(PAYER),
                        getScheduleInfo(THREE_SIG_XFER)
                                .hasScheduleId(THREE_SIG_XFER)
                                .hasWaitForExpiry(false)
                                .hasRelativeExpiry(CREATE_TXN, TimeUnit.MINUTES.toSeconds(30))
                                .isExecuted()
                                .isNotDeleted(),
                        getAccountBalance(RECEIVER).hasTinyBars(1L));
    }
}
