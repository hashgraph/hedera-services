/*
 * Copyright (C) 2021-2024 Hedera Hashgraph, LLC
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
import static com.hedera.services.bdd.spec.HapiSpec.hapiTest;
import static com.hedera.services.bdd.spec.assertions.AccountInfoAsserts.changeFromSnapshot;
import static com.hedera.services.bdd.spec.keys.ControlForKey.forKey;
import static com.hedera.services.bdd.spec.keys.KeyShape.sigs;
import static com.hedera.services.bdd.spec.keys.KeyShape.threshOf;
import static com.hedera.services.bdd.spec.keys.SigControl.OFF;
import static com.hedera.services.bdd.spec.keys.SigControl.ON;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getScheduleInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.mintToken;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.scheduleCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.scheduleSign;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenUpdate;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromTo;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.balanceSnapshot;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.keyFromMutation;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyListNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sleepFor;
import static com.hedera.services.bdd.suites.HapiSuite.ADDRESS_BOOK_CONTROL;
import static com.hedera.services.bdd.suites.HapiSuite.DEFAULT_PAYER;
import static com.hedera.services.bdd.suites.HapiSuite.FUNDING;
import static com.hedera.services.bdd.suites.HapiSuite.GENESIS;
import static com.hedera.services.bdd.suites.HapiSuite.ONE_HBAR;
import static com.hedera.services.bdd.suites.HapiSuite.ONE_MILLION_HBARS;
import static com.hedera.services.bdd.suites.HapiSuite.THOUSAND_HBAR;
import static com.hedera.services.bdd.suites.hip423.LongTermScheduleUtils.ADMIN;
import static com.hedera.services.bdd.suites.hip423.LongTermScheduleUtils.BASIC_XFER;
import static com.hedera.services.bdd.suites.hip423.LongTermScheduleUtils.BEFORE;
import static com.hedera.services.bdd.suites.hip423.LongTermScheduleUtils.CREATE_TXN;
import static com.hedera.services.bdd.suites.hip423.LongTermScheduleUtils.CREATION;
import static com.hedera.services.bdd.suites.hip423.LongTermScheduleUtils.DEFERRED_CREATION;
import static com.hedera.services.bdd.suites.hip423.LongTermScheduleUtils.DEFERRED_FALL;
import static com.hedera.services.bdd.suites.hip423.LongTermScheduleUtils.DEFERRED_XFER;
import static com.hedera.services.bdd.suites.hip423.LongTermScheduleUtils.EXTRA_KEY;
import static com.hedera.services.bdd.suites.hip423.LongTermScheduleUtils.NEW_SENDER_KEY;
import static com.hedera.services.bdd.suites.hip423.LongTermScheduleUtils.PAYER;
import static com.hedera.services.bdd.suites.hip423.LongTermScheduleUtils.RECEIVER;
import static com.hedera.services.bdd.suites.hip423.LongTermScheduleUtils.SENDER;
import static com.hedera.services.bdd.suites.hip423.LongTermScheduleUtils.SENDER_TXN;
import static com.hedera.services.bdd.suites.hip423.LongTermScheduleUtils.SHARED_KEY;
import static com.hedera.services.bdd.suites.hip423.LongTermScheduleUtils.THREE_SIG_XFER;
import static com.hedera.services.bdd.suites.hip423.LongTermScheduleUtils.TOKEN_A;
import static com.hedera.services.bdd.suites.hip423.LongTermScheduleUtils.TWO_SIG_XFER;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SCHEDULE_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NO_NEW_VALID_SIGNATURES;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.RECORD_NOT_FOUND;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SOME_SIGNATURES_WERE_INVALID;

import com.hedera.services.bdd.junit.HapiTest;
import com.hedera.services.bdd.junit.HapiTestLifecycle;
import com.hedera.services.bdd.junit.support.TestLifecycle;
import com.hedera.services.bdd.spec.keys.ControlForKey;
import com.hedera.services.bdd.spec.keys.OverlappingKeyGenerator;
import com.hederahashgraph.api.proto.java.Key;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.TestMethodOrder;

@HapiTestLifecycle
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class ScheduleLongTermSignTest {

    private static final long ONE_MINUTE = 60;
    private static final long THIRTY_MINUTES = 30 * ONE_MINUTE;
    private static final String PAYING_ACCOUNT = "payingAccount";
    private static final String RECEIVER = "receiver";
    private static final String SENDER = "sender";

    @BeforeAll
    static void beforeAll(@NonNull final TestLifecycle lifecycle) {
        // override and preserve old values
        lifecycle.overrideInClass(Map.of(
                "scheduling.longTermEnabled",
                "true",
                "scheduling.whitelist",
                "ConsensusSubmitMessage,CryptoTransfer,TokenMint,TokenBurn,"
                        + "CryptoCreate,CryptoUpdate,FileUpdate,SystemDelete,SystemUndelete,"
                        + "Freeze,ContractCall,ContractCreate,ContractUpdate,ContractDelete"));
    }

    @HapiTest
    @Order(1)
    final Stream<DynamicTest> changeInNestedSigningReqsRespected() {
        var senderShape = threshOf(2, threshOf(1, 3), threshOf(1, 3), threshOf(1, 3));
        var sigOne = senderShape.signedWith(sigs(sigs(OFF, OFF, ON), sigs(OFF, OFF, OFF), sigs(OFF, OFF, OFF)));
        var firstSigThree = senderShape.signedWith(sigs(sigs(OFF, OFF, OFF), sigs(OFF, OFF, OFF), sigs(ON, OFF, OFF)));
        var secondSigThree = senderShape.signedWith(sigs(sigs(OFF, OFF, OFF), sigs(OFF, OFF, OFF), sigs(ON, ON, OFF)));
        String sender = "X";
        String receiver = "Y";
        String schedule = "Z";
        String senderKey = "sKey";

        return defaultHapiSpec("ChangeInNestedSigningReqsRespectedAtExpiry")
                .given(
                        newKeyNamed(senderKey).shape(senderShape),
                        keyFromMutation(NEW_SENDER_KEY, senderKey).changing(this::bumpThirdNestedThresholdSigningReq),
                        cryptoCreate(sender).key(senderKey).via(SENDER_TXN),
                        cryptoCreate(receiver).balance(0L),
                        scheduleCreate(schedule, cryptoTransfer(tinyBarsFromTo(sender, receiver, 1)))
                                .payingWith(DEFAULT_PAYER)
                                .waitForExpiry()
                                .withRelativeExpiry(SENDER_TXN, 5)
                                .recordingScheduledTxn()
                                .alsoSigningWith(sender)
                                .sigControl(ControlForKey.forKey(senderKey, sigOne)),
                        getAccountBalance(receiver).hasTinyBars(0L))
                .when(
                        cryptoUpdate(sender).key(NEW_SENDER_KEY),
                        scheduleSign(schedule)
                                .alsoSigningWith(NEW_SENDER_KEY)
                                .sigControl(forKey(NEW_SENDER_KEY, firstSigThree)),
                        getAccountBalance(receiver).hasTinyBars(0L),
                        scheduleSign(schedule)
                                .alsoSigningWith(NEW_SENDER_KEY)
                                .sigControl(forKey(NEW_SENDER_KEY, secondSigThree)),
                        getAccountBalance(receiver).hasTinyBars(0L))
                .then(
                        getScheduleInfo(schedule)
                                .hasScheduleId(schedule)
                                .hasWaitForExpiry()
                                .isNotExecuted()
                                .isNotDeleted()
                                .hasRelativeExpiry(SENDER_TXN, 5)
                                .hasRecordedScheduledTxn(),
                        sleepFor(TimeUnit.SECONDS.toMillis(6)),
                        cryptoCreate("foo"),
                        getScheduleInfo(schedule).hasCostAnswerPrecheck(INVALID_SCHEDULE_ID),
                        getAccountBalance(receiver).hasTinyBars(1L));
    }

    @HapiTest
    @Order(2)
    final Stream<DynamicTest> reductionInSigningReqsAllowsTxnToGoThrough() {
        var senderShape = threshOf(2, threshOf(1, 3), threshOf(1, 3), threshOf(2, 3));
        var sigOne = senderShape.signedWith(sigs(sigs(OFF, OFF, ON), sigs(OFF, OFF, OFF), sigs(OFF, OFF, OFF)));
        var sigTwo = senderShape.signedWith(sigs(sigs(OFF, OFF, OFF), sigs(ON, ON, ON), sigs(OFF, OFF, OFF)));
        var firstSigThree = senderShape.signedWith(sigs(sigs(OFF, OFF, OFF), sigs(OFF, OFF, OFF), sigs(ON, OFF, OFF)));
        String sender = "X";
        String receiver = "Y";
        String schedule = "Z";
        String senderKey = "sKey";

        return defaultHapiSpec("ReductionInSigningReqsAllowsTxnToGoThroughAtExpiry")
                .given(
                        newKeyNamed(senderKey).shape(senderShape),
                        keyFromMutation(NEW_SENDER_KEY, senderKey).changing(this::lowerThirdNestedThresholdSigningReq),
                        cryptoCreate(sender).key(senderKey).via(SENDER_TXN),
                        cryptoCreate(receiver).balance(0L),
                        scheduleCreate(schedule, cryptoTransfer(tinyBarsFromTo(sender, receiver, 1)))
                                .payingWith(DEFAULT_PAYER)
                                .waitForExpiry()
                                .withRelativeExpiry(SENDER_TXN, 5)
                                .recordingScheduledTxn()
                                .alsoSigningWith(sender)
                                .sigControl(ControlForKey.forKey(senderKey, sigOne)),
                        getAccountBalance(receiver).hasTinyBars(0L))
                .when(
                        scheduleSign(schedule)
                                .alsoSigningWith(NEW_SENDER_KEY)
                                .sigControl(forKey(NEW_SENDER_KEY, firstSigThree)),
                        getAccountBalance(receiver).hasTinyBars(0L),
                        cryptoUpdate(sender).key(NEW_SENDER_KEY),
                        scheduleSign(schedule).hasKnownStatus(NO_NEW_VALID_SIGNATURES),
                        getAccountBalance(receiver).hasTinyBars(0L))
                .then(
                        getScheduleInfo(schedule)
                                .hasScheduleId(schedule)
                                .hasWaitForExpiry()
                                .isNotExecuted()
                                .isNotDeleted()
                                .hasRelativeExpiry(SENDER_TXN, 5)
                                .hasRecordedScheduledTxn(),
                        sleepFor(TimeUnit.SECONDS.toMillis(6)),
                        cryptoCreate("foo"),
                        scheduleSign(schedule)
                                .alsoSigningWith(NEW_SENDER_KEY)
                                .sigControl(forKey(NEW_SENDER_KEY, sigTwo))
                                .hasKnownStatus(INVALID_SCHEDULE_ID),
                        getAccountBalance(receiver).hasTinyBars(1L));
    }

    @HapiTest
    @Order(3)
    final Stream<DynamicTest> reductionInSigningReqsAllowsTxnToGoThroughAtExpiryWithWaitForExpiry() {
        var senderShape = threshOf(2, threshOf(1, 3), threshOf(1, 3), threshOf(2, 3));
        var sigOne = senderShape.signedWith(sigs(sigs(OFF, OFF, ON), sigs(OFF, OFF, OFF), sigs(OFF, OFF, OFF)));
        var firstSigThree = senderShape.signedWith(sigs(sigs(OFF, OFF, OFF), sigs(OFF, OFF, OFF), sigs(ON, OFF, OFF)));
        String sender = "X";
        String receiver = "Y";
        String schedule = "Z";
        String senderKey = "sKey";

        return hapiTest(
                newKeyNamed(senderKey).shape(senderShape),
                keyFromMutation(NEW_SENDER_KEY, senderKey).changing(this::lowerThirdNestedThresholdSigningReq),
                cryptoCreate(sender).key(senderKey).via(SENDER_TXN),
                cryptoCreate(receiver).balance(0L),
                scheduleCreate(schedule, cryptoTransfer(tinyBarsFromTo(sender, receiver, 1)))
                        .payingWith(DEFAULT_PAYER)
                        .waitForExpiry()
                        .withRelativeExpiry(SENDER_TXN, 5)
                        .recordingScheduledTxn()
                        .alsoSigningWith(sender)
                        .sigControl(ControlForKey.forKey(senderKey, sigOne)),
                getAccountBalance(receiver).hasTinyBars(0L),
                scheduleSign(schedule)
                        .alsoSigningWith(NEW_SENDER_KEY)
                        .sigControl(forKey(NEW_SENDER_KEY, firstSigThree)),
                getAccountBalance(receiver).hasTinyBars(0L),
                cryptoUpdate(sender).key(NEW_SENDER_KEY),
                getAccountBalance(receiver).hasTinyBars(0L),
                getScheduleInfo(schedule)
                        .hasScheduleId(schedule)
                        .hasWaitForExpiry(true)
                        .isNotExecuted()
                        .isNotDeleted()
                        .hasRelativeExpiry(SENDER_TXN, 5)
                        .hasRecordedScheduledTxn(),
                sleepFor(TimeUnit.SECONDS.toMillis(6)),
                cryptoCreate("foo"),
                sleepFor(500),
                getAccountBalance(receiver).hasTinyBars(1L));
    }

    @HapiTest
    @Order(4)
    final Stream<DynamicTest> nestedSigningReqsWorkAsExpected() {
        var senderShape = threshOf(2, threshOf(1, 3), threshOf(1, 3), threshOf(1, 3));
        var sigOne = senderShape.signedWith(sigs(sigs(OFF, OFF, ON), sigs(OFF, OFF, OFF), sigs(OFF, OFF, OFF)));
        var sigTwo = senderShape.signedWith(sigs(sigs(OFF, OFF, OFF), sigs(OFF, ON, OFF), sigs(OFF, OFF, OFF)));
        String sender = "X";
        String receiver = "Y";
        String schedule = "Z";
        String senderKey = "sKey";

        return defaultHapiSpec("NestedSigningReqsWorkAsExpectedAtExpiry")
                .given(
                        newKeyNamed(senderKey).shape(senderShape),
                        cryptoCreate(sender).key(senderKey).via(SENDER_TXN),
                        cryptoCreate(receiver).balance(0L),
                        scheduleCreate(schedule, cryptoTransfer(tinyBarsFromTo(sender, receiver, 1)))
                                .payingWith(DEFAULT_PAYER)
                                .waitForExpiry()
                                .withRelativeExpiry(SENDER_TXN, 5)
                                .recordingScheduledTxn()
                                .alsoSigningWith(sender)
                                .sigControl(ControlForKey.forKey(senderKey, sigOne)),
                        getAccountBalance(receiver).hasTinyBars(0L))
                .when(
                        scheduleSign(schedule).alsoSigningWith(senderKey).sigControl(forKey(senderKey, sigTwo)),
                        getAccountBalance(receiver).hasTinyBars(0L))
                .then(
                        getScheduleInfo(schedule)
                                .hasScheduleId(schedule)
                                .hasWaitForExpiry()
                                .isNotExecuted()
                                .isNotDeleted()
                                .hasRelativeExpiry(SENDER_TXN, 5)
                                .hasRecordedScheduledTxn(),
                        sleepFor(TimeUnit.SECONDS.toMillis(6)),
                        cryptoCreate("foo"),
                        getScheduleInfo(schedule).hasCostAnswerPrecheck(INVALID_SCHEDULE_ID),
                        getAccountBalance(receiver).hasTinyBars(1L));
    }

    @HapiTest
    @Order(5)
    final Stream<DynamicTest> receiverSigRequiredNotConfusedByOrder() {
        var senderShape = threshOf(1, 3);
        var sigOne = senderShape.signedWith(sigs(ON, OFF, OFF));
        String sender = "X";
        String receiver = "Y";
        String schedule = "Z";
        String senderKey = "sKey";

        return defaultHapiSpec("ReceiverSigRequiredNotConfusedByOrderAtExpiry")
                .given(
                        newKeyNamed(senderKey).shape(senderShape),
                        cryptoCreate(sender).key(senderKey).via(SENDER_TXN),
                        cryptoCreate(receiver).balance(0L).receiverSigRequired(true),
                        scheduleCreate(schedule, cryptoTransfer(tinyBarsFromTo(sender, receiver, 1)))
                                .waitForExpiry()
                                .withRelativeExpiry(SENDER_TXN, 5)
                                .recordingScheduledTxn()
                                .payingWith(DEFAULT_PAYER),
                        scheduleSign(schedule).alsoSigningWith(receiver),
                        getAccountBalance(receiver).hasTinyBars(0L))
                .when(
                        scheduleSign(schedule).alsoSigningWith(senderKey).sigControl(forKey(senderKey, sigOne)),
                        getAccountBalance(receiver).hasTinyBars(0L))
                .then(
                        getScheduleInfo(schedule)
                                .hasScheduleId(schedule)
                                .hasWaitForExpiry()
                                .isNotExecuted()
                                .isNotDeleted()
                                .hasRelativeExpiry(SENDER_TXN, 5)
                                .hasRecordedScheduledTxn(),
                        sleepFor(TimeUnit.SECONDS.toMillis(6)),
                        cryptoCreate("foo"),
                        getScheduleInfo(schedule).hasCostAnswerPrecheck(INVALID_SCHEDULE_ID),
                        getAccountBalance(receiver).hasTinyBars(1L));
    }

    @HapiTest
    @Order(6)
    final Stream<DynamicTest> extraSigsDontMatterAtExpiry() {
        var senderShape = threshOf(1, 3);
        var sigOne = senderShape.signedWith(sigs(ON, OFF, OFF));
        var sigTwo = senderShape.signedWith(sigs(OFF, ON, OFF));
        var sigThree = senderShape.signedWith(sigs(OFF, OFF, ON));
        String sender = "X";
        String receiver = "Y";
        String schedule = "Z";
        String senderKey = "sKey";

        return defaultHapiSpec("ExtraSigsDontMatterAtExpiry")
                .given(
                        cryptoCreate(PAYER).balance(ONE_MILLION_HBARS).payingWith(GENESIS),
                        newKeyNamed(senderKey).shape(senderShape),
                        newKeyNamed(EXTRA_KEY),
                        cryptoCreate(sender).key(senderKey).via(SENDER_TXN),
                        cryptoCreate(receiver).balance(0L).receiverSigRequired(true),
                        scheduleCreate(schedule, cryptoTransfer(tinyBarsFromTo(sender, receiver, 1)))
                                .waitForExpiry()
                                .withRelativeExpiry(SENDER_TXN, 10)
                                .recordingScheduledTxn()
                                .payingWith(PAYER),
                        scheduleSign(schedule)
                                .payingWith(PAYER)
                                .fee(THOUSAND_HBAR)
                                .alsoSigningWith(receiver),
                        getAccountBalance(receiver).hasTinyBars(0L))
                .when(
                        scheduleSign(schedule)
                                .payingWith(PAYER)
                                .fee(THOUSAND_HBAR)
                                .alsoSigningWith(senderKey)
                                .sigControl(forKey(senderKey, sigOne)),
                        getAccountBalance(receiver).hasTinyBars(0L),
                        scheduleSign(schedule)
                                .payingWith(PAYER)
                                .fee(THOUSAND_HBAR)
                                .alsoSigningWith(senderKey)
                                .sigControl(forKey(senderKey, sigTwo)),
                        getAccountBalance(receiver).hasTinyBars(0L),
                        scheduleSign(schedule)
                                .payingWith(PAYER)
                                .fee(THOUSAND_HBAR)
                                .alsoSigningWith(EXTRA_KEY)
                                .hasKnownStatus(NO_NEW_VALID_SIGNATURES),
                        getAccountBalance(receiver).hasTinyBars(0L),
                        scheduleSign(schedule)
                                .payingWith(PAYER)
                                .fee(THOUSAND_HBAR)
                                .alsoSigningWith(senderKey)
                                .sigControl(forKey(senderKey, sigTwo))
                                .hasKnownStatus(NO_NEW_VALID_SIGNATURES),
                        getAccountBalance(receiver).hasTinyBars(0L),
                        scheduleSign(schedule)
                                .payingWith(PAYER)
                                .fee(THOUSAND_HBAR)
                                .alsoSigningWith(senderKey)
                                .sigControl(forKey(senderKey, sigThree)),
                        getAccountBalance(receiver).hasTinyBars(0L),
                        scheduleSign(schedule)
                                .payingWith(PAYER)
                                .fee(THOUSAND_HBAR)
                                .alsoSigningWith(senderKey)
                                .sigControl(forKey(senderKey, sigTwo))
                                .hasKnownStatus(NO_NEW_VALID_SIGNATURES),
                        getAccountBalance(receiver).hasTinyBars(0L),
                        scheduleSign(schedule)
                                .payingWith(PAYER)
                                .fee(THOUSAND_HBAR)
                                .alsoSigningWith(EXTRA_KEY)
                                .hasKnownStatus(NO_NEW_VALID_SIGNATURES),
                        getAccountBalance(receiver).hasTinyBars(0L),
                        scheduleSign(schedule)
                                .payingWith(PAYER)
                                .fee(THOUSAND_HBAR)
                                .alsoSigningWith(senderKey)
                                .sigControl(forKey(senderKey, sigOne))
                                .hasKnownStatus(NO_NEW_VALID_SIGNATURES),
                        getAccountBalance(receiver).hasTinyBars(0L))
                .then(
                        getScheduleInfo(schedule)
                                .hasScheduleId(schedule)
                                .hasWaitForExpiry()
                                .isNotExecuted()
                                .isNotDeleted()
                                .hasRelativeExpiry(SENDER_TXN, 10)
                                .hasRecordedScheduledTxn(),
                        sleepFor(TimeUnit.SECONDS.toMillis(11)),
                        cryptoCreate("foo"),
                        getScheduleInfo(schedule).hasCostAnswerPrecheck(INVALID_SCHEDULE_ID),
                        getAccountBalance(receiver).hasTinyBars(1L));
    }

    @HapiTest
    @Order(7)
    final Stream<DynamicTest> receiverSigRequiredNotConfusedByMultiSigSender() {
        var senderShape = threshOf(1, 3);
        var sigOne = senderShape.signedWith(sigs(ON, OFF, OFF));
        var sigTwo = senderShape.signedWith(sigs(OFF, ON, OFF));
        String sender = "X";
        String receiver = "Y";
        String schedule = "Z";
        String senderKey = "sKey";

        return defaultHapiSpec("ReceiverSigRequiredNotConfusedByMultiSigSenderAtExpiry")
                .given(
                        newKeyNamed(senderKey).shape(senderShape),
                        cryptoCreate(sender).key(senderKey).via(SENDER_TXN),
                        cryptoCreate(receiver).balance(0L).receiverSigRequired(true),
                        scheduleCreate(schedule, cryptoTransfer(tinyBarsFromTo(sender, receiver, 1)))
                                .waitForExpiry()
                                .withRelativeExpiry(SENDER_TXN, 5)
                                .recordingScheduledTxn()
                                .payingWith(DEFAULT_PAYER),
                        getAccountBalance(receiver).hasTinyBars(0L))
                .when(
                        scheduleSign(schedule).alsoSigningWith(senderKey).sigControl(forKey(senderKey, sigOne)),
                        getAccountBalance(receiver).hasTinyBars(0L),
                        scheduleSign(schedule).alsoSigningWith(senderKey).sigControl(forKey(senderKey, sigTwo)),
                        getAccountBalance(receiver).hasTinyBars(0L),
                        scheduleSign(schedule).alsoSigningWith(receiver),
                        getAccountBalance(receiver).hasTinyBars(0L))
                .then(
                        getScheduleInfo(schedule)
                                .hasScheduleId(schedule)
                                .hasWaitForExpiry()
                                .isNotExecuted()
                                .isNotDeleted()
                                .hasRelativeExpiry(SENDER_TXN, 5)
                                .hasRecordedScheduledTxn(),
                        sleepFor(TimeUnit.SECONDS.toMillis(6)),
                        cryptoCreate("foo"),
                        getScheduleInfo(schedule).hasCostAnswerPrecheck(INVALID_SCHEDULE_ID),
                        getAccountBalance(receiver).hasTinyBars(1L));
    }

    @HapiTest
    @Order(8)
    final Stream<DynamicTest> receiverSigRequiredUpdateIsRecognized() {
        var senderShape = threshOf(2, 3);
        var sigOne = senderShape.signedWith(sigs(ON, OFF, OFF));
        var sigTwo = senderShape.signedWith(sigs(OFF, ON, OFF));
        String sender = "X";
        String receiver = "Y";
        String schedule = "Z";
        String senderKey = "sKey";

        return defaultHapiSpec("ReceiverSigRequiredUpdateIsRecognizedAtExpiry")
                .given(
                        newKeyNamed(senderKey).shape(senderShape),
                        cryptoCreate(sender).key(senderKey).via(SENDER_TXN),
                        cryptoCreate(receiver).balance(0L),
                        scheduleCreate(schedule, cryptoTransfer(tinyBarsFromTo(sender, receiver, 1)))
                                .payingWith(DEFAULT_PAYER)
                                .waitForExpiry()
                                .withRelativeExpiry(SENDER_TXN, 5)
                                .recordingScheduledTxn()
                                .alsoSigningWith(sender)
                                .sigControl(forKey(senderKey, sigOne)),
                        getAccountBalance(receiver).hasTinyBars(0L))
                .when(
                        cryptoUpdate(receiver).receiverSigRequired(true),
                        scheduleSign(schedule).alsoSigningWith(senderKey).sigControl(forKey(senderKey, sigTwo)),
                        getAccountBalance(receiver).hasTinyBars(0L))
                .then(
                        scheduleSign(schedule).alsoSigningWith(receiver),
                        getAccountBalance(receiver).hasTinyBars(0),
                        getScheduleInfo(schedule)
                                .hasScheduleId(schedule)
                                .hasWaitForExpiry()
                                .isNotExecuted()
                                .isNotDeleted()
                                .hasRelativeExpiry(SENDER_TXN, 5)
                                .hasRecordedScheduledTxn(),
                        sleepFor(TimeUnit.SECONDS.toMillis(6)),
                        cryptoCreate("foo"),
                        getAccountBalance(receiver).hasTinyBars(1),
                        scheduleSign(schedule)
                                .alsoSigningWith(senderKey)
                                .sigControl(forKey(senderKey, sigTwo))
                                .hasKnownStatus(INVALID_SCHEDULE_ID),
                        getAccountBalance(receiver).hasTinyBars(1));
    }

    @HapiTest
    @Order(9)
    final Stream<DynamicTest> basicSignatureCollectionWorks() {
        var txnBody = cryptoTransfer(tinyBarsFromTo(SENDER, RECEIVER, 1));

        return defaultHapiSpec("BasicSignatureCollectionWorksWithExpiryAndWait")
                .given(
                        cryptoCreate(SENDER).via(SENDER_TXN),
                        cryptoCreate(RECEIVER).receiverSigRequired(true),
                        scheduleCreate(BASIC_XFER, txnBody)
                                .waitForExpiry()
                                .withRelativeExpiry(SENDER_TXN, 5)
                                .payingWith(SENDER))
                .when(scheduleSign(BASIC_XFER).alsoSigningWith(RECEIVER))
                .then(
                        getScheduleInfo(BASIC_XFER).hasSignatories(RECEIVER, SENDER),
                        // note: the sleepFor and cryptoCreate operations are added only to clear the schedule before
                        // the next state. This was needed because an edge case in the BaseTranslator occur.
                        // When scheduleCreate trigger the schedules execution scheduleRef field is not the correct one.
                        sleepFor(6000),
                        cryptoCreate("foo"));
    }

    @HapiTest
    @Order(10)
    final Stream<DynamicTest> signalsIrrelevantSig() {
        var txnBody = cryptoTransfer(tinyBarsFromTo(SENDER, RECEIVER, 1));

        return defaultHapiSpec("SignalsIrrelevantSigWithExpiryAndWait")
                .given(
                        cryptoCreate(SENDER).via(SENDER_TXN),
                        cryptoCreate(RECEIVER),
                        newKeyNamed("somebodyelse"),
                        scheduleCreate(BASIC_XFER, txnBody).waitForExpiry().withRelativeExpiry(SENDER_TXN, 5))
                .when()
                .then(
                        scheduleSign(BASIC_XFER)
                                .alsoSigningWith("somebodyelse")
                                .hasKnownStatusFrom(NO_NEW_VALID_SIGNATURES, SOME_SIGNATURES_WERE_INVALID),

                        // note: the sleepFor and cryptoCreate operations are added only to clear the schedule before
                        // the next state. This was needed because an edge case in the BaseTranslator occur.
                        // When scheduleCreate trigger the schedules execution scheduleRef field is not the correct one.
                        sleepFor(6000),
                        cryptoCreate("foo"));
    }

    @HapiTest
    @Order(11)
    final Stream<DynamicTest> signalsIrrelevantSigEvenAfterLinkedEntityUpdate() {
        var txnBody = mintToken(TOKEN_A, 50000000L);

        return defaultHapiSpec("SignalsIrrelevantSigEvenAfterLinkedEntityUpdateWithExpiryAndWait")
                .given(
                        newKeyNamed(ADMIN),
                        newKeyNamed("mint"),
                        newKeyNamed("newMint"),
                        tokenCreate(TOKEN_A).adminKey(ADMIN).supplyKey("mint").via(CREATE_TXN),
                        scheduleCreate("tokenMintScheduled", txnBody)
                                .waitForExpiry()
                                .withRelativeExpiry(CREATE_TXN, 5))
                .when(tokenUpdate(TOKEN_A).supplyKey("newMint"))
                .then(
                        scheduleSign("tokenMintScheduled")
                                .alsoSigningWith("mint")
                                /* In the rare, but possible, case that the the mint and newMint keys overlap
                                 * in their first byte (and that byte is not shared by the DEFAULT_PAYER),
                                 * we will get SOME_SIGNATURES_WERE_INVALID instead of NO_NEW_VALID_SIGNATURES.
                                 *
                                 * So we need this to stabilize CI. But if just testing locally, you may
                                 * only use .hasKnownStatus(NO_NEW_VALID_SIGNATURES) and it will pass
                                 * >99.99% of the time. */
                                .hasKnownStatusFrom(NO_NEW_VALID_SIGNATURES, SOME_SIGNATURES_WERE_INVALID),

                        // note: the sleepFor and cryptoCreate operations are added only to clear the schedule before
                        // the next state. This was needed because an edge case in the BaseTranslator occur.
                        // When scheduleCreate trigger the schedules execution scheduleRef field is not the correct one.
                        sleepFor(6000),
                        cryptoCreate("foo"));
    }

    @HapiTest
    @Order(12)
    public Stream<DynamicTest> triggersUponFinishingPayerSig() {
        return defaultHapiSpec("TriggersUponFinishingPayerSigAtExpiry")
                .given(
                        cryptoCreate(PAYER).balance(ONE_HBAR),
                        cryptoCreate(SENDER).balance(1L).via(SENDER_TXN),
                        cryptoCreate(RECEIVER).balance(0L).receiverSigRequired(true))
                .when(
                        scheduleCreate(
                                        THREE_SIG_XFER,
                                        cryptoTransfer(tinyBarsFromTo(SENDER, RECEIVER, 1))
                                                .fee(ONE_HBAR))
                                .designatingPayer(PAYER)
                                .waitForExpiry()
                                .withRelativeExpiry(SENDER_TXN, 5)
                                .recordingScheduledTxn()
                                .alsoSigningWith(SENDER, RECEIVER),
                        getAccountBalance(RECEIVER).hasTinyBars(0L),
                        scheduleSign(THREE_SIG_XFER).alsoSigningWith(PAYER))
                .then(
                        getAccountBalance(RECEIVER).hasTinyBars(0L),
                        getScheduleInfo(THREE_SIG_XFER)
                                .hasScheduleId(THREE_SIG_XFER)
                                .hasWaitForExpiry()
                                .isNotExecuted()
                                .isNotDeleted()
                                .hasRelativeExpiry(SENDER_TXN, 5)
                                .hasRecordedScheduledTxn(),
                        sleepFor(TimeUnit.SECONDS.toMillis(6)),
                        cryptoCreate("foo"),
                        getScheduleInfo(THREE_SIG_XFER).hasCostAnswerPrecheck(INVALID_SCHEDULE_ID),
                        getAccountBalance(RECEIVER).hasTinyBars(1L));
    }

    @HapiTest
    @Order(13)
    public Stream<DynamicTest> triggersUponAdditionalNeededSig() {
        return defaultHapiSpec("TriggersUponAdditionalNeededSigAtExpiry")
                .given(
                        cryptoCreate(SENDER).balance(1L).via(SENDER_TXN),
                        cryptoCreate(RECEIVER).balance(0L).receiverSigRequired(true))
                .when(
                        scheduleCreate(
                                        TWO_SIG_XFER,
                                        cryptoTransfer(tinyBarsFromTo(SENDER, RECEIVER, 1))
                                                .fee(ONE_HBAR))
                                .waitForExpiry()
                                .withRelativeExpiry(SENDER_TXN, 5)
                                .recordingScheduledTxn()
                                .alsoSigningWith(SENDER),
                        getAccountBalance(RECEIVER).hasTinyBars(0L),
                        scheduleSign(TWO_SIG_XFER).alsoSigningWith(RECEIVER))
                .then(
                        getAccountBalance(RECEIVER).hasTinyBars(0L),
                        getScheduleInfo(TWO_SIG_XFER)
                                .hasScheduleId(TWO_SIG_XFER)
                                .hasWaitForExpiry()
                                .isNotExecuted()
                                .isNotDeleted()
                                .hasRelativeExpiry(SENDER_TXN, 5)
                                .hasRecordedScheduledTxn(),
                        sleepFor(TimeUnit.SECONDS.toMillis(6)),
                        cryptoCreate("foo"),
                        getScheduleInfo(TWO_SIG_XFER).hasCostAnswerPrecheck(INVALID_SCHEDULE_ID),
                        getAccountBalance(RECEIVER).hasTinyBars(1L));
    }

    @HapiTest
    @Order(14)
    public Stream<DynamicTest> sharedKeyWorksAsExpected() {
        return defaultHapiSpec("RequiresSharedKeyToSignBothSchedulingAndScheduledTxnsAtExpiry")
                .given(
                        newKeyNamed(SHARED_KEY),
                        cryptoCreate("payerWithSharedKey").key(SHARED_KEY).via(CREATE_TXN))
                .when(scheduleCreate(
                                DEFERRED_CREATION,
                                cryptoCreate("yetToBe")
                                        .signedBy()
                                        .receiverSigRequired(true)
                                        .key(SHARED_KEY)
                                        .balance(123L)
                                        .fee(ONE_HBAR))
                        .waitForExpiry()
                        .withRelativeExpiry(CREATE_TXN, 5)
                        .recordingScheduledTxn()
                        .payingWith("payerWithSharedKey")
                        .via(CREATION))
                .then(
                        getTxnRecord(CREATION).scheduled().hasAnswerOnlyPrecheck(RECORD_NOT_FOUND),
                        getScheduleInfo(DEFERRED_CREATION)
                                .hasScheduleId(DEFERRED_CREATION)
                                .hasWaitForExpiry()
                                .isNotExecuted()
                                .isNotDeleted()
                                .hasRelativeExpiry(CREATE_TXN, 5)
                                .hasRecordedScheduledTxn(),
                        sleepFor(TimeUnit.SECONDS.toMillis(6)),
                        cryptoCreate("foo"),
                        getScheduleInfo(DEFERRED_CREATION).hasCostAnswerPrecheck(INVALID_SCHEDULE_ID),
                        getTxnRecord(CREATION).scheduled());
    }

    @HapiTest
    @Order(15)
    public Stream<DynamicTest> overlappingKeysTreatedAsExpected() {
        var keyGen = OverlappingKeyGenerator.withAtLeastOneOverlappingByte(2);

        return defaultHapiSpec("OverlappingKeysTreatedAsExpectedAtExpiry")
                .given(
                        newKeyNamed("aKey").generator(keyGen),
                        newKeyNamed("bKey").generator(keyGen),
                        newKeyNamed("cKey"),
                        cryptoCreate("aSender").key("aKey").balance(1L).via(SENDER_TXN),
                        cryptoCreate("cSender").key("cKey").balance(1L),
                        balanceSnapshot(BEFORE, ADDRESS_BOOK_CONTROL))
                .when(scheduleCreate(
                                DEFERRED_XFER,
                                cryptoTransfer(
                                        tinyBarsFromTo("aSender", ADDRESS_BOOK_CONTROL, 1),
                                        tinyBarsFromTo("cSender", ADDRESS_BOOK_CONTROL, 1)))
                        .waitForExpiry()
                        .withRelativeExpiry(SENDER_TXN, 5)
                        .recordingScheduledTxn())
                .then(
                        scheduleSign(DEFERRED_XFER).alsoSigningWith("aKey"),
                        scheduleSign(DEFERRED_XFER).alsoSigningWith("aKey").hasKnownStatus(NO_NEW_VALID_SIGNATURES),
                        scheduleSign(DEFERRED_XFER)
                                .alsoSigningWith("aKey", "bKey")
                                .hasKnownStatus(NO_NEW_VALID_SIGNATURES),
                        scheduleSign(DEFERRED_XFER)
                                .alsoSigningWith("bKey")
                                /* In the rare, but possible, case that the overlapping byte shared by aKey
                                 * and bKey is _also_ shared by the DEFAULT_PAYER, the bKey prefix in the sig
                                 * map will probably not collide with aKey any more, and we will get
                                 * NO_NEW_VALID_SIGNATURES instead of SOME_SIGNATURES_WERE_INVALID.
                                 *
                                 * So we need this to stabilize CI. But if just testing locally, you may
                                 * only use .hasKnownStatus(SOME_SIGNATURES_WERE_INVALID) and it will pass
                                 * >99.99% of the time. */
                                .hasKnownStatusFrom(SOME_SIGNATURES_WERE_INVALID, NO_NEW_VALID_SIGNATURES),
                        scheduleSign(DEFERRED_XFER).alsoSigningWith("aKey", "bKey", "cKey"),
                        getAccountBalance(ADDRESS_BOOK_CONTROL).hasTinyBars(changeFromSnapshot(BEFORE, 0)),
                        getScheduleInfo(DEFERRED_XFER)
                                .hasScheduleId(DEFERRED_XFER)
                                .hasWaitForExpiry()
                                .isNotExecuted()
                                .isNotDeleted()
                                .hasRelativeExpiry(SENDER_TXN, 5)
                                .hasRecordedScheduledTxn(),
                        sleepFor(TimeUnit.SECONDS.toMillis(6)),
                        cryptoCreate("foo"),
                        getScheduleInfo(DEFERRED_XFER).hasCostAnswerPrecheck(INVALID_SCHEDULE_ID),
                        getAccountBalance(ADDRESS_BOOK_CONTROL).hasTinyBars(changeFromSnapshot(BEFORE, +2)));
    }

    @HapiTest
    @Order(15)
    public Stream<DynamicTest> retestsActivationOnSignWithEmptySigMap() {
        return defaultHapiSpec("RetestsActivationOnCreateWithEmptySigMapAtExpiry")
                .given(newKeyNamed("a"), newKeyNamed("b"), newKeyListNamed("ab", List.of("a", "b")), newKeyNamed(ADMIN))
                .when(
                        cryptoCreate(SENDER).key("ab").balance(667L).via(SENDER_TXN),
                        scheduleCreate(
                                        DEFERRED_FALL,
                                        cryptoTransfer(tinyBarsFromTo(SENDER, FUNDING, 1))
                                                .fee(ONE_HBAR))
                                .waitForExpiry()
                                .withRelativeExpiry(SENDER_TXN, 5)
                                .recordingScheduledTxn()
                                .alsoSigningWith("a"),
                        getAccountBalance(SENDER).hasTinyBars(667L),
                        cryptoUpdate(SENDER).key("a"))
                .then(
                        scheduleSign(DEFERRED_FALL).alsoSigningWith().hasKnownStatus(NO_NEW_VALID_SIGNATURES),
                        getAccountBalance(SENDER).hasTinyBars(667L),
                        getScheduleInfo(DEFERRED_FALL)
                                .hasScheduleId(DEFERRED_FALL)
                                .hasWaitForExpiry()
                                .isNotExecuted()
                                .isNotDeleted()
                                .hasRelativeExpiry(SENDER_TXN, 5)
                                .hasRecordedScheduledTxn(),
                        sleepFor(TimeUnit.SECONDS.toMillis(6)),
                        cryptoCreate("foo"),
                        getScheduleInfo(DEFERRED_FALL).hasCostAnswerPrecheck(INVALID_SCHEDULE_ID),
                        getAccountBalance(SENDER).hasTinyBars(666L));
    }

    @HapiTest
    @Order(16)
    final Stream<DynamicTest> scheduledTransactionWithWaitForExpiryFalseLessThen30Mins() {
        final var schedule = "s";
        return hapiTest(
                cryptoCreate(SENDER),
                cryptoCreate(PAYING_ACCOUNT),
                cryptoCreate(RECEIVER).balance(0L),
                scheduleCreate(schedule, cryptoTransfer(tinyBarsFromTo(SENDER, RECEIVER, 1L)))
                        .waitForExpiry(false)
                        .expiringIn(ONE_MINUTE),
                getAccountBalance(RECEIVER).hasTinyBars(0L),
                scheduleSign(schedule).alsoSigningWith(SENDER),
                getAccountBalance(RECEIVER).hasTinyBars(1L));
    }

    @HapiTest
    @Order(17)
    final Stream<DynamicTest> scheduledTransactionWithWaitForExpiryFalseMoreThen30Mins() {
        final var schedule = "s";
        return hapiTest(
                cryptoCreate(SENDER),
                cryptoCreate(PAYING_ACCOUNT),
                cryptoCreate(RECEIVER).balance(0L),
                scheduleCreate(schedule, cryptoTransfer(tinyBarsFromTo(SENDER, RECEIVER, 1L)))
                        .waitForExpiry(false)
                        .expiringIn(THIRTY_MINUTES * 2),
                getAccountBalance(RECEIVER).hasTinyBars(0L),
                scheduleSign(schedule).alsoSigningWith(SENDER),
                getAccountBalance(RECEIVER).hasTinyBars(1L));
    }

    @HapiTest
    @Order(18)
    final Stream<DynamicTest> scheduledTriggeredWhenAllKeysHaveSigned() {
        final var schedule = "s";

        return hapiTest(
                cryptoCreate(SENDER),
                cryptoCreate(PAYING_ACCOUNT),
                cryptoCreate(RECEIVER).receiverSigRequired(true).balance(0L),
                scheduleCreate(schedule, cryptoTransfer(tinyBarsFromTo(SENDER, RECEIVER, 1L)))
                        .waitForExpiry(false)
                        .expiringIn(THIRTY_MINUTES * 2),
                getAccountBalance(RECEIVER).hasTinyBars(0L),

                // only sign with one of the required keys
                scheduleSign(schedule).alsoSigningWith(SENDER),

                // the balance is not change
                getAccountBalance(RECEIVER).hasTinyBars(0L),

                // sign with the other required key
                scheduleSign(schedule).alsoSigningWith(RECEIVER),

                // the balance is changed
                getAccountBalance(RECEIVER).hasTinyBars(1L));
    }

    @HapiTest
    @Order(19)
    final Stream<DynamicTest> scheduleSignWithNotNeededSignature() {
        final var schedule = "s";

        return hapiTest(
                cryptoCreate(SENDER),
                cryptoCreate(PAYING_ACCOUNT),
                cryptoCreate("dummy"),
                cryptoCreate(RECEIVER).receiverSigRequired(true).balance(0L),
                scheduleCreate(schedule, cryptoTransfer(tinyBarsFromTo(SENDER, RECEIVER, 1L)))
                        .waitForExpiry()
                        .expiringIn(THIRTY_MINUTES),
                scheduleSign(schedule).alsoSigningWith("dummy").hasKnownStatus(NO_NEW_VALID_SIGNATURES));
    }

    @HapiTest
    @Order(20)
    final Stream<DynamicTest> scheduleSignWithEmptyKey() {
        final var schedule = "s";

        return hapiTest(
                cryptoCreate(SENDER),
                cryptoCreate(PAYING_ACCOUNT),
                cryptoCreate("dummy"),
                cryptoCreate(RECEIVER).receiverSigRequired(true).balance(0L),
                scheduleCreate(schedule, cryptoTransfer(tinyBarsFromTo(SENDER, RECEIVER, 1L)))
                        .waitForExpiry()
                        .expiringIn(THIRTY_MINUTES),
                scheduleSign(schedule).alsoSigningWith().hasKnownStatus(NO_NEW_VALID_SIGNATURES));
    }

    @HapiTest
    @Order(21)
    final Stream<DynamicTest> test() {
        final var schedule = "s";

        return hapiTest(
                cryptoCreate(SENDER),
                cryptoCreate(PAYING_ACCOUNT),
                cryptoCreate("dummy"),
                cryptoCreate(RECEIVER).receiverSigRequired(true).balance(0L),
                scheduleCreate(schedule, cryptoTransfer(tinyBarsFromTo(SENDER, RECEIVER, 1L)))
                        .waitForExpiry(false)
                        .expiringIn(THIRTY_MINUTES),
                scheduleSign(schedule).alsoSigningWith(RECEIVER),
                scheduleSign(schedule).alsoSigningWith(SENDER),
                cryptoCreate("trigger"),
                getAccountBalance(RECEIVER).hasTinyBars(1L));
    }

    private Key lowerThirdNestedThresholdSigningReq(Key source) {
        var newKey = source.getThresholdKey().getKeys().getKeys(2).toBuilder();
        newKey.setThresholdKey(newKey.getThresholdKeyBuilder().setThreshold(1));
        var newKeyList = source.getThresholdKey().getKeys().toBuilder().setKeys(2, newKey);
        return source.toBuilder()
                .setThresholdKey(source.getThresholdKey().toBuilder().setKeys(newKeyList))
                .build();
    }

    private Key bumpThirdNestedThresholdSigningReq(Key source) {
        var newKey = source.getThresholdKey().getKeys().getKeys(2).toBuilder();
        newKey.setThresholdKey(newKey.getThresholdKeyBuilder().setThreshold(2));
        var newKeyList = source.getThresholdKey().getKeys().toBuilder().setKeys(2, newKey);
        return source.toBuilder()
                .setThresholdKey(source.getThresholdKey().toBuilder().setKeys(newKeyList))
                .build();
    }
}
