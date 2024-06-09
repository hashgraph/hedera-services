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

package com.hedera.services.bdd.suites.schedule;

import static com.hedera.services.bdd.spec.HapiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.HapiSpec.propertyPreservingHapiSpec;
import static com.hedera.services.bdd.spec.assertions.AccountInfoAsserts.changeFromSnapshot;
import static com.hedera.services.bdd.spec.keys.ControlForKey.forKey;
import static com.hedera.services.bdd.spec.keys.KeyShape.sigs;
import static com.hedera.services.bdd.spec.keys.KeyShape.threshOf;
import static com.hedera.services.bdd.spec.keys.SigControl.OFF;
import static com.hedera.services.bdd.spec.keys.SigControl.ON;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getScheduleInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnUtils.randomUppercase;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.mintToken;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.scheduleCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.scheduleDelete;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.scheduleSign;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenUpdate;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromTo;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.balanceSnapshot;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.keyFromMutation;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyListNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.overriding;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sleepFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.submitModified;
import static com.hedera.services.bdd.spec.utilops.mod.ModificationUtils.withSuccessivelyVariedBodyIds;
import static com.hedera.services.bdd.suites.HapiSuite.ADDRESS_BOOK_CONTROL;
import static com.hedera.services.bdd.suites.HapiSuite.APP_PROPERTIES;
import static com.hedera.services.bdd.suites.HapiSuite.DEFAULT_PAYER;
import static com.hedera.services.bdd.suites.HapiSuite.FUNDING;
import static com.hedera.services.bdd.suites.HapiSuite.ONE_HBAR;
import static com.hedera.services.bdd.suites.HapiSuite.ONE_HUNDRED_HBARS;
import static com.hedera.services.bdd.suites.schedule.ScheduleUtils.ADMIN;
import static com.hedera.services.bdd.suites.schedule.ScheduleUtils.BASIC_XFER;
import static com.hedera.services.bdd.suites.schedule.ScheduleUtils.DEFAULT_TX_EXPIRY;
import static com.hedera.services.bdd.suites.schedule.ScheduleUtils.DEFERRED_XFER;
import static com.hedera.services.bdd.suites.schedule.ScheduleUtils.LEDGER_SCHEDULE_TX_EXPIRY_TIME_SECS;
import static com.hedera.services.bdd.suites.schedule.ScheduleUtils.NEW_SENDER_KEY;
import static com.hedera.services.bdd.suites.schedule.ScheduleUtils.PAYER;
import static com.hedera.services.bdd.suites.schedule.ScheduleUtils.RANDOM_KEY;
import static com.hedera.services.bdd.suites.schedule.ScheduleUtils.RECEIVER;
import static com.hedera.services.bdd.suites.schedule.ScheduleUtils.SCHEDULING_WHITELIST;
import static com.hedera.services.bdd.suites.schedule.ScheduleUtils.SENDER;
import static com.hedera.services.bdd.suites.schedule.ScheduleUtils.SHARED_KEY;
import static com.hedera.services.bdd.suites.schedule.ScheduleUtils.SOMEBODY;
import static com.hedera.services.bdd.suites.schedule.ScheduleUtils.TOKEN_A;
import static com.hedera.services.bdd.suites.schedule.ScheduleUtils.TWO_SIG_XFER;
import static com.hedera.services.bdd.suites.schedule.ScheduleUtils.VALID_SCHEDULED_TXN;
import static com.hedera.services.bdd.suites.schedule.ScheduleUtils.WHITELIST_DEFAULT;
import static com.hedera.services.bdd.suites.schedule.ScheduleUtils.WHITELIST_MINIMUM;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SCHEDULE_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NO_NEW_VALID_SIGNATURES;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SCHEDULE_ALREADY_DELETED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SCHEDULE_ALREADY_EXECUTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SCHEDULE_PENDING_EXPIRATION;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SOME_SIGNATURES_WERE_INVALID;

import com.hedera.services.bdd.junit.HapiTest;
import com.hedera.services.bdd.spec.keys.ControlForKey;
import com.hedera.services.bdd.spec.keys.OverlappingKeyGenerator;
import com.hederahashgraph.api.proto.java.Key;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.TestMethodOrder;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class ScheduleSignSpecs {
    private static final int SCHEDULE_EXPIRY_TIME_SECS = 10;
    private static final int SCHEDULE_EXPIRY_TIME_MS = SCHEDULE_EXPIRY_TIME_SECS * 1000;

    @HapiTest
    final Stream<DynamicTest> idVariantsTreatedAsExpected() {
        return defaultHapiSpec("idVariantsTreatedAsExpected")
                .given(
                        newKeyNamed(ADMIN),
                        cryptoCreate(SENDER),
                        scheduleCreate(VALID_SCHEDULED_TXN, cryptoTransfer(tinyBarsFromTo(SENDER, FUNDING, 1)))
                                .adminKey(ADMIN))
                .when()
                .then(submitModified(withSuccessivelyVariedBodyIds(), () -> scheduleSign(VALID_SCHEDULED_TXN)
                        .alsoSigningWith(SENDER)));
    }

    @HapiTest
    @Order(24)
    final Stream<DynamicTest> suiteCleanup() {
        return defaultHapiSpec("suiteCleanup")
                .given()
                .when()
                .then(fileUpdate(APP_PROPERTIES)
                        .payingWith(ADDRESS_BOOK_CONTROL)
                        .overridingProps(Map.of(
                                LEDGER_SCHEDULE_TX_EXPIRY_TIME_SECS, DEFAULT_TX_EXPIRY,
                                SCHEDULING_WHITELIST, WHITELIST_DEFAULT)));
    }

    @HapiTest
    @Order(1)
    final Stream<DynamicTest> suiteSetup() {
        return defaultHapiSpec("suiteSetup")
                .given()
                .when()
                .then(fileUpdate(APP_PROPERTIES)
                        .payingWith(ADDRESS_BOOK_CONTROL)
                        .overridingProps(Map.of(SCHEDULING_WHITELIST, WHITELIST_MINIMUM)));
    }

    @HapiTest
    @Order(21)
    final Stream<DynamicTest> signingDeletedSchedulesHasNoEffect() {
        String sender = "X";
        String receiver = "Y";
        String schedule = "Z";
        String adminKey = ADMIN;

        return defaultHapiSpec("SigningDeletedSchedulesHasNoEffect")
                .given(
                        overriding(SCHEDULING_WHITELIST, WHITELIST_MINIMUM),
                        newKeyNamed(adminKey),
                        cryptoCreate(sender),
                        cryptoCreate(receiver).balance(0L),
                        scheduleCreate(schedule, cryptoTransfer(tinyBarsFromTo(sender, receiver, 1)))
                                .adminKey(adminKey)
                                .payingWith(DEFAULT_PAYER),
                        getAccountBalance(receiver).hasTinyBars(0L))
                .when(
                        scheduleDelete(schedule).signedBy(DEFAULT_PAYER, adminKey),
                        scheduleSign(schedule).alsoSigningWith(sender).hasKnownStatus(SCHEDULE_ALREADY_DELETED))
                .then(getAccountBalance(receiver).hasTinyBars(0L));
    }

    @HapiTest
    @Order(5)
    final Stream<DynamicTest> changeInNestedSigningReqsRespected() {
        var senderShape = threshOf(2, threshOf(1, 3), threshOf(1, 3), threshOf(1, 3));
        var sigOne = senderShape.signedWith(sigs(sigs(OFF, OFF, ON), sigs(OFF, OFF, OFF), sigs(OFF, OFF, OFF)));
        var sigTwo = senderShape.signedWith(sigs(sigs(OFF, OFF, OFF), sigs(ON, ON, ON), sigs(OFF, OFF, OFF)));
        var firstSigThree = senderShape.signedWith(sigs(sigs(OFF, OFF, OFF), sigs(OFF, OFF, OFF), sigs(ON, OFF, OFF)));
        var secondSigThree = senderShape.signedWith(sigs(sigs(OFF, OFF, OFF), sigs(OFF, OFF, OFF), sigs(ON, ON, OFF)));
        String sender = "X";
        String receiver = "Y";
        String schedule = "Z";
        String senderKey = "sKey";

        return defaultHapiSpec("ChangeInNestedSigningReqsRespected")
                .given(
                        overriding(SCHEDULING_WHITELIST, WHITELIST_MINIMUM),
                        newKeyNamed(senderKey).shape(senderShape),
                        keyFromMutation(NEW_SENDER_KEY, senderKey).changing(this::bumpThirdNestedThresholdSigningReq),
                        cryptoCreate(sender).key(senderKey),
                        cryptoCreate(receiver).balance(0L),
                        scheduleCreate(schedule, cryptoTransfer(tinyBarsFromTo(sender, receiver, 1)))
                                .payingWith(DEFAULT_PAYER)
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
                        getAccountBalance(receiver).hasTinyBars(1L))
                .then(
                        scheduleSign(schedule)
                                .alsoSigningWith(NEW_SENDER_KEY)
                                .sigControl(forKey(NEW_SENDER_KEY, sigTwo))
                                .hasKnownStatus(SCHEDULE_ALREADY_EXECUTED),
                        getAccountBalance(receiver).hasTinyBars(1L));
    }

    private Key bumpThirdNestedThresholdSigningReq(Key source) {
        var newKey = source.getThresholdKey().getKeys().getKeys(2).toBuilder();
        newKey.setThresholdKey(newKey.getThresholdKeyBuilder().setThreshold(2));
        var newKeyList = source.getThresholdKey().getKeys().toBuilder().setKeys(2, newKey);
        var mutation = source.toBuilder()
                .setThresholdKey(source.getThresholdKey().toBuilder().setKeys(newKeyList))
                .build();
        return mutation;
    }

    @HapiTest
    @Order(12)
    final Stream<DynamicTest> reductionInSigningReqsAllowsTxnToGoThrough() {
        var senderShape = threshOf(2, threshOf(1, 3), threshOf(1, 3), threshOf(2, 3));
        var sigOne = senderShape.signedWith(sigs(sigs(OFF, OFF, ON), sigs(OFF, OFF, OFF), sigs(OFF, OFF, OFF)));
        var sigTwo = senderShape.signedWith(sigs(sigs(OFF, OFF, OFF), sigs(ON, ON, ON), sigs(OFF, OFF, OFF)));
        var firstSigThree = senderShape.signedWith(sigs(sigs(OFF, OFF, OFF), sigs(OFF, OFF, OFF), sigs(ON, OFF, OFF)));
        String sender = "X";
        String receiver = "Y";
        String schedule = "Z";
        String senderKey = "sKey";

        return defaultHapiSpec("ReductionInSigningReqsAllowsTxnToGoThrough")
                .given(
                        overriding(SCHEDULING_WHITELIST, WHITELIST_MINIMUM),
                        newKeyNamed(senderKey).shape(senderShape),
                        keyFromMutation(NEW_SENDER_KEY, senderKey).changing(this::lowerThirdNestedThresholdSigningReq),
                        cryptoCreate(sender).key(senderKey),
                        cryptoCreate(receiver).balance(0L),
                        scheduleCreate(schedule, cryptoTransfer(tinyBarsFromTo(sender, receiver, 1)))
                                .payingWith(DEFAULT_PAYER)
                                .alsoSigningWith(sender)
                                .sigControl(ControlForKey.forKey(senderKey, sigOne)),
                        getAccountBalance(receiver).hasTinyBars(0L))
                .when(
                        scheduleSign(schedule)
                                .alsoSigningWith(NEW_SENDER_KEY)
                                .sigControl(forKey(NEW_SENDER_KEY, firstSigThree)),
                        getAccountBalance(receiver).hasTinyBars(0L),
                        cryptoUpdate(sender).key(NEW_SENDER_KEY),
                        scheduleSign(schedule).via("signTxn"),
                        getTxnRecord("signTxn").hasScheduledTransactionId().logged(),
                        getAccountBalance(receiver).hasTinyBars(1L))
                .then(
                        scheduleSign(schedule)
                                .alsoSigningWith(NEW_SENDER_KEY)
                                .sigControl(forKey(NEW_SENDER_KEY, sigTwo))
                                .hasKnownStatus(SCHEDULE_ALREADY_EXECUTED),
                        getAccountBalance(receiver).hasTinyBars(1L));
    }

    @HapiTest
    @Order(13)
    final Stream<DynamicTest> reductionInSigningReqsAllowsTxnToGoThroughWithRandomKey() {
        var senderShape = threshOf(2, threshOf(1, 3), threshOf(1, 3), threshOf(2, 3));
        var sigOne = senderShape.signedWith(sigs(sigs(OFF, OFF, ON), sigs(OFF, OFF, OFF), sigs(OFF, OFF, OFF)));
        var sigTwo = senderShape.signedWith(sigs(sigs(OFF, OFF, OFF), sigs(ON, ON, ON), sigs(OFF, OFF, OFF)));
        var firstSigThree = senderShape.signedWith(sigs(sigs(OFF, OFF, OFF), sigs(OFF, OFF, OFF), sigs(ON, OFF, OFF)));
        String sender = "X";
        String receiver = "Y";
        String schedule = "Z";
        String senderKey = "sKey";
        String newSenderKey = NEW_SENDER_KEY;

        return defaultHapiSpec("ReductionInSigningReqsAllowsTxnToGoThroughWithRandomKey")
                .given(
                        overriding(SCHEDULING_WHITELIST, WHITELIST_MINIMUM),
                        newKeyNamed(RANDOM_KEY),
                        cryptoCreate("random").key(RANDOM_KEY),
                        newKeyNamed(senderKey).shape(senderShape),
                        keyFromMutation(newSenderKey, senderKey).changing(this::lowerThirdNestedThresholdSigningReq),
                        cryptoCreate(sender).key(senderKey),
                        cryptoCreate(receiver).balance(0L),
                        scheduleCreate(schedule, cryptoTransfer(tinyBarsFromTo(sender, receiver, 1)))
                                .payingWith(DEFAULT_PAYER)
                                .alsoSigningWith(sender)
                                .sigControl(ControlForKey.forKey(senderKey, sigOne)),
                        getAccountBalance(receiver).hasTinyBars(0L))
                .when(
                        scheduleSign(schedule)
                                .alsoSigningWith(newSenderKey)
                                .sigControl(forKey(newSenderKey, firstSigThree)),
                        getAccountBalance(receiver).hasTinyBars(0L),
                        cryptoUpdate(sender).key(newSenderKey),
                        scheduleSign(schedule).signedBy(RANDOM_KEY).payingWith("random"),
                        getAccountBalance(receiver).hasTinyBars(1L))
                .then(
                        scheduleSign(schedule)
                                .alsoSigningWith(newSenderKey)
                                .sigControl(forKey(newSenderKey, sigTwo))
                                .hasKnownStatus(SCHEDULE_ALREADY_EXECUTED),
                        getAccountBalance(receiver).hasTinyBars(1L));
    }

    private Key lowerThirdNestedThresholdSigningReq(Key source) {
        var newKey = source.getThresholdKey().getKeys().getKeys(2).toBuilder();
        newKey.setThresholdKey(newKey.getThresholdKeyBuilder().setThreshold(1));
        var newKeyList = source.getThresholdKey().getKeys().toBuilder().setKeys(2, newKey);
        var mutation = source.toBuilder()
                .setThresholdKey(source.getThresholdKey().toBuilder().setKeys(newKeyList))
                .build();
        return mutation;
    }

    @HapiTest
    @Order(6)
    final Stream<DynamicTest> nestedSigningReqsWorkAsExpected() {
        var senderShape = threshOf(2, threshOf(1, 3), threshOf(1, 3), threshOf(1, 3));
        var sigOne = senderShape.signedWith(sigs(sigs(OFF, OFF, ON), sigs(OFF, OFF, OFF), sigs(OFF, OFF, OFF)));
        var sigTwo = senderShape.signedWith(sigs(sigs(OFF, OFF, OFF), sigs(OFF, ON, OFF), sigs(OFF, OFF, OFF)));
        var sigThree = senderShape.signedWith(sigs(sigs(OFF, OFF, OFF), sigs(OFF, OFF, OFF), sigs(ON, OFF, OFF)));
        String sender = "X";
        String receiver = "Y";
        String schedule = "Z";
        String senderKey = "sKey";

        return defaultHapiSpec("NestedSigningReqsWorkAsExpected")
                .given(
                        overriding(SCHEDULING_WHITELIST, WHITELIST_MINIMUM),
                        newKeyNamed(senderKey).shape(senderShape),
                        cryptoCreate(sender).key(senderKey),
                        cryptoCreate(receiver).balance(0L),
                        scheduleCreate(schedule, cryptoTransfer(tinyBarsFromTo(sender, receiver, 1)))
                                .payingWith(DEFAULT_PAYER)
                                .alsoSigningWith(sender)
                                .sigControl(ControlForKey.forKey(senderKey, sigOne)),
                        getAccountBalance(receiver).hasTinyBars(0L))
                .when(
                        scheduleSign(schedule).alsoSigningWith(senderKey).sigControl(forKey(senderKey, sigTwo)),
                        getAccountBalance(receiver).hasTinyBars(1L))
                .then(
                        scheduleSign(schedule)
                                .alsoSigningWith(senderKey)
                                .sigControl(forKey(senderKey, sigThree))
                                .hasKnownStatus(SCHEDULE_ALREADY_EXECUTED),
                        getAccountBalance(receiver).hasTinyBars(1L));
    }

    @HapiTest
    @Order(10)
    final Stream<DynamicTest> receiverSigRequiredNotConfusedByOrder() {
        var senderShape = threshOf(1, 3);
        var sigOne = senderShape.signedWith(sigs(ON, OFF, OFF));
        var sigTwo = senderShape.signedWith(sigs(OFF, ON, OFF));
        var sigThree = senderShape.signedWith(sigs(OFF, OFF, ON));
        String sender = "X";
        String receiver = "Y";
        String schedule = "Z";
        String senderKey = "sKey";

        return defaultHapiSpec("ReceiverSigRequiredNotConfusedByOrder")
                .given(
                        overriding(SCHEDULING_WHITELIST, WHITELIST_MINIMUM),
                        newKeyNamed(senderKey).shape(senderShape),
                        cryptoCreate(sender).key(senderKey),
                        cryptoCreate(receiver).balance(0L).receiverSigRequired(true),
                        scheduleCreate(schedule, cryptoTransfer(tinyBarsFromTo(sender, receiver, 1)))
                                .payingWith(DEFAULT_PAYER),
                        scheduleSign(schedule).alsoSigningWith(receiver),
                        getAccountBalance(receiver).hasTinyBars(0L))
                .when(
                        scheduleSign(schedule).alsoSigningWith(senderKey).sigControl(forKey(senderKey, sigOne)),
                        getAccountBalance(receiver).hasTinyBars(1L))
                .then(
                        scheduleSign(schedule)
                                .alsoSigningWith(senderKey)
                                .sigControl(forKey(senderKey, sigTwo))
                                .hasKnownStatus(SCHEDULE_ALREADY_EXECUTED),
                        getAccountBalance(receiver).hasTinyBars(1L),
                        scheduleSign(schedule)
                                .alsoSigningWith(senderKey)
                                .sigControl(forKey(senderKey, sigThree))
                                .hasKnownStatus(SCHEDULE_ALREADY_EXECUTED),
                        getAccountBalance(receiver).hasTinyBars(1L));
    }

    @HapiTest
    @Order(9)
    final Stream<DynamicTest> receiverSigRequiredNotConfusedByMultiSigSender() {
        var senderShape = threshOf(1, 3);
        var sigOne = senderShape.signedWith(sigs(ON, OFF, OFF));
        var sigTwo = senderShape.signedWith(sigs(OFF, ON, OFF));
        var sigThree = senderShape.signedWith(sigs(OFF, OFF, ON));
        String sender = "X";
        String receiver = "Y";
        String schedule = "Z";
        String senderKey = "sKey";

        return defaultHapiSpec("receiverSigRequiredNotConfusedByMultiSigSender")
                .given(
                        overriding(SCHEDULING_WHITELIST, WHITELIST_MINIMUM),
                        newKeyNamed(senderKey).shape(senderShape),
                        cryptoCreate(sender).key(senderKey),
                        cryptoCreate(receiver).balance(0L).receiverSigRequired(true),
                        scheduleCreate(schedule, cryptoTransfer(tinyBarsFromTo(sender, receiver, 1)))
                                .payingWith(DEFAULT_PAYER),
                        getAccountBalance(receiver).hasTinyBars(0L))
                .when(
                        scheduleSign(schedule).alsoSigningWith(senderKey).sigControl(forKey(senderKey, sigOne)),
                        getAccountBalance(receiver).hasTinyBars(0L),
                        scheduleSign(schedule).alsoSigningWith(senderKey).sigControl(forKey(senderKey, sigTwo)),
                        getAccountBalance(receiver).hasTinyBars(0L),
                        scheduleSign(schedule).alsoSigningWith(receiver),
                        getAccountBalance(receiver).hasTinyBars(1L))
                .then(
                        scheduleSign(schedule).alsoSigningWith(receiver).hasKnownStatus(SCHEDULE_ALREADY_EXECUTED),
                        getAccountBalance(receiver).hasTinyBars(1),
                        scheduleSign(schedule)
                                .alsoSigningWith(senderKey)
                                .sigControl(forKey(senderKey, sigThree))
                                .hasKnownStatus(SCHEDULE_ALREADY_EXECUTED),
                        getAccountBalance(receiver).hasTinyBars(1L));
    }

    @HapiTest
    @Order(11)
    final Stream<DynamicTest> receiverSigRequiredUpdateIsRecognized() {
        var senderShape = threshOf(2, 3);
        var sigOne = senderShape.signedWith(sigs(ON, OFF, OFF));
        var sigTwo = senderShape.signedWith(sigs(OFF, ON, OFF));
        var sigThree = senderShape.signedWith(sigs(OFF, OFF, ON));
        String sender = "X";
        String receiver = "Y";
        String schedule = "Z";
        String senderKey = "sKey";

        return defaultHapiSpec("ReceiverSigRequiredUpdateIsRecognized")
                .given(
                        overriding(SCHEDULING_WHITELIST, WHITELIST_MINIMUM),
                        newKeyNamed(senderKey).shape(senderShape),
                        cryptoCreate(sender).key(senderKey),
                        cryptoCreate(receiver).balance(0L),
                        scheduleCreate(schedule, cryptoTransfer(tinyBarsFromTo(sender, receiver, 1)))
                                .payingWith(DEFAULT_PAYER)
                                .alsoSigningWith(sender)
                                .sigControl(forKey(senderKey, sigOne)),
                        getAccountBalance(receiver).hasTinyBars(0L))
                .when(
                        cryptoUpdate(receiver).receiverSigRequired(true),
                        scheduleSign(schedule).alsoSigningWith(senderKey).sigControl(forKey(senderKey, sigTwo)),
                        getAccountBalance(receiver).hasTinyBars(0L))
                .then(
                        scheduleSign(schedule).alsoSigningWith(receiver),
                        getAccountBalance(receiver).hasTinyBars(1),
                        scheduleSign(schedule)
                                .alsoSigningWith(senderKey)
                                .sigControl(forKey(senderKey, sigTwo))
                                .hasKnownStatus(SCHEDULE_ALREADY_EXECUTED),
                        getAccountBalance(receiver).hasTinyBars(1),
                        scheduleSign(schedule)
                                .alsoSigningWith(senderKey)
                                .sigControl(forKey(senderKey, sigThree))
                                .hasKnownStatus(SCHEDULE_ALREADY_EXECUTED),
                        getAccountBalance(receiver).hasTinyBars(1));
    }

    @HapiTest
    @Order(16)
    final Stream<DynamicTest> scheduleAlreadyExecutedOnCreateDoesntRepeatTransaction() {
        var senderShape = threshOf(1, 3);
        var sigOne = senderShape.signedWith(sigs(ON, OFF, OFF));
        var sigTwo = senderShape.signedWith(sigs(OFF, ON, OFF));
        var sigThree = senderShape.signedWith(sigs(OFF, OFF, ON));
        String sender = "X";
        String receiver = "Y";
        String schedule = "Z";
        String senderKey = "sKey";

        return defaultHapiSpec("ScheduleAlreadyExecutedOnCreateDoesntRepeatTransaction")
                .given(
                        overriding(SCHEDULING_WHITELIST, WHITELIST_MINIMUM),
                        newKeyNamed(senderKey).shape(senderShape),
                        cryptoCreate(sender).key(senderKey),
                        cryptoCreate(receiver).balance(0L),
                        scheduleCreate(schedule, cryptoTransfer(tinyBarsFromTo(sender, receiver, 1)))
                                .memo(randomUppercase(100))
                                .payingWith(sender)
                                .sigControl(forKey(sender, sigOne)),
                        getAccountBalance(receiver).hasTinyBars(1L))
                .when(
                        scheduleSign(schedule)
                                .alsoSigningWith(senderKey)
                                .sigControl(forKey(senderKey, sigTwo))
                                .hasKnownStatus(SCHEDULE_ALREADY_EXECUTED),
                        getAccountBalance(receiver).hasTinyBars(1))
                .then(
                        scheduleSign(schedule)
                                .alsoSigningWith(senderKey)
                                .sigControl(forKey(senderKey, sigThree))
                                .hasKnownStatus(SCHEDULE_ALREADY_EXECUTED),
                        getAccountBalance(receiver).hasTinyBars(1));
    }

    @HapiTest
    @Order(15)
    final Stream<DynamicTest> scheduleAlreadyExecutedDoesntRepeatTransaction() {
        var senderShape = threshOf(2, 3);
        var sigOne = senderShape.signedWith(sigs(ON, OFF, OFF));
        var sigTwo = senderShape.signedWith(sigs(OFF, ON, OFF));
        var sigThree = senderShape.signedWith(sigs(OFF, OFF, ON));
        String sender = "X";
        String receiver = "Y";
        String firstSchedule = "Z";
        String senderKey = "sKey";

        return defaultHapiSpec("ScheduleAlreadyExecutedDoesntRepeatTransaction")
                .given(
                        overriding(SCHEDULING_WHITELIST, WHITELIST_MINIMUM),
                        newKeyNamed(senderKey).shape(senderShape),
                        cryptoCreate(sender).balance(101L).key(senderKey),
                        cryptoCreate(receiver),
                        scheduleCreate(firstSchedule, cryptoTransfer(tinyBarsFromTo(sender, receiver, 1)))
                                .memo(randomUppercase(100))
                                .designatingPayer(DEFAULT_PAYER),
                        getAccountBalance(sender).hasTinyBars(101))
                .when(
                        scheduleSign(firstSchedule).alsoSigningWith(senderKey).sigControl(forKey(senderKey, sigOne)),
                        getAccountBalance(sender).hasTinyBars(101),
                        scheduleSign(firstSchedule).alsoSigningWith(senderKey).sigControl(forKey(senderKey, sigTwo)),
                        getAccountBalance(sender).hasTinyBars(100))
                .then(
                        scheduleSign(firstSchedule)
                                .alsoSigningWith(senderKey)
                                .sigControl(forKey(senderKey, sigThree))
                                .hasKnownStatus(SCHEDULE_ALREADY_EXECUTED),
                        getAccountBalance(sender).hasTinyBars(100));
    }

    @HapiTest
    @Order(4)
    final Stream<DynamicTest> basicSignatureCollectionWorks() {
        var txnBody = cryptoTransfer(tinyBarsFromTo(SENDER, RECEIVER, 1));

        return defaultHapiSpec("BasicSignatureCollectionWorks")
                .given(
                        overriding(SCHEDULING_WHITELIST, WHITELIST_MINIMUM),
                        cryptoCreate(SENDER),
                        cryptoCreate(RECEIVER).receiverSigRequired(true),
                        scheduleCreate(BASIC_XFER, txnBody))
                .when(scheduleSign(BASIC_XFER).alsoSigningWith(RECEIVER))
                .then(getScheduleInfo(BASIC_XFER).hasSignatories(RECEIVER));
    }

    @HapiTest
    @Order(19)
    final Stream<DynamicTest> signalsIrrelevantSig() {
        var txnBody = cryptoTransfer(tinyBarsFromTo(SENDER, RECEIVER, 1));

        return defaultHapiSpec("SignalsIrrelevantSig")
                .given(
                        overriding(SCHEDULING_WHITELIST, WHITELIST_MINIMUM),
                        cryptoCreate(SENDER),
                        cryptoCreate(RECEIVER),
                        newKeyNamed("somebodyelse"),
                        scheduleCreate(BASIC_XFER, txnBody))
                .when()
                .then(scheduleSign(BASIC_XFER)
                        .alsoSigningWith("somebodyelse")
                        .hasKnownStatusFrom(NO_NEW_VALID_SIGNATURES, SOME_SIGNATURES_WERE_INVALID));
    }

    @HapiTest
    @Order(20)
    final Stream<DynamicTest> signalsIrrelevantSigEvenAfterLinkedEntityUpdate() {
        var txnBody = mintToken(TOKEN_A, 50000000L);

        return defaultHapiSpec("SignalsIrrelevantSigEvenAfterLinkedEntityUpdate")
                .given(
                        overriding(SCHEDULING_WHITELIST, WHITELIST_MINIMUM),
                        newKeyNamed(ADMIN),
                        newKeyNamed("mint"),
                        newKeyNamed("newMint"),
                        tokenCreate(TOKEN_A).adminKey(ADMIN).supplyKey("mint"),
                        scheduleCreate("tokenMintScheduled", txnBody))
                .when(tokenUpdate(TOKEN_A).supplyKey("newMint").signedByPayerAnd(ADMIN))
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
                        overriding(SCHEDULING_WHITELIST, WHITELIST_MINIMUM));
    }

    @HapiTest
    @Order(3)
    final Stream<DynamicTest> addingSignaturesToNonExistingTxFails() {
        return defaultHapiSpec("AddingSignaturesToNonExistingTxFails")
                .given(cryptoCreate(SENDER), newKeyNamed(SOMEBODY))
                .when()
                .then(scheduleSign("0.0.123321")
                        .fee(ONE_HBAR)
                        .alsoSigningWith(SOMEBODY, SENDER)
                        .hasPrecheckFrom(OK, INVALID_SCHEDULE_ID)
                        .hasKnownStatus(INVALID_SCHEDULE_ID));
    }

    @HapiTest
    @Order(2)
    final Stream<DynamicTest> addingSignaturesToExecutedTxFails() {
        var txnBody = cryptoCreate(SOMEBODY);
        var creation = "basicCryptoCreate";

        return defaultHapiSpec("AddingSignaturesToExecutedTxFails")
                .given(cryptoCreate("somesigner"), scheduleCreate(creation, txnBody))
                .when(getScheduleInfo(creation).isExecuted().logged())
                .then(scheduleSign(creation)
                        .via("signing")
                        .alsoSigningWith("somesigner")
                        .hasKnownStatus(SCHEDULE_ALREADY_EXECUTED));
    }

    @HapiTest
    @Order(23)
    final Stream<DynamicTest> triggersUponFinishingPayerSig() {
        return defaultHapiSpec("TriggersUponFinishingPayerSig")
                .given(
                        overriding(SCHEDULING_WHITELIST, WHITELIST_MINIMUM),
                        cryptoCreate(PAYER).balance(ONE_HBAR),
                        cryptoCreate(SENDER).balance(1L),
                        cryptoCreate(RECEIVER).balance(0L).receiverSigRequired(true))
                .when(
                        scheduleCreate(
                                        "threeSigXfer",
                                        cryptoTransfer(tinyBarsFromTo(SENDER, RECEIVER, 1))
                                                .fee(ONE_HBAR))
                                .designatingPayer(PAYER)
                                .alsoSigningWith(SENDER, RECEIVER),
                        getAccountBalance(RECEIVER).hasTinyBars(0L),
                        scheduleSign("threeSigXfer").alsoSigningWith(PAYER))
                .then(getAccountBalance(RECEIVER).hasTinyBars(1L));
    }

    @HapiTest
    @Order(22)
    final Stream<DynamicTest> triggersUponAdditionalNeededSig() {
        return defaultHapiSpec("TriggersUponAdditionalNeededSig")
                .given(
                        overriding(SCHEDULING_WHITELIST, WHITELIST_MINIMUM),
                        cryptoCreate(SENDER).balance(1L),
                        cryptoCreate(RECEIVER).balance(0L).receiverSigRequired(true))
                .when(
                        scheduleCreate(
                                        TWO_SIG_XFER,
                                        cryptoTransfer(tinyBarsFromTo(SENDER, RECEIVER, 1))
                                                .fee(ONE_HBAR))
                                .alsoSigningWith(SENDER),
                        getAccountBalance(RECEIVER).hasTinyBars(0L),
                        scheduleSign(TWO_SIG_XFER).alsoSigningWith(RECEIVER))
                .then(getAccountBalance(RECEIVER).hasTinyBars(1L));
    }

    @HapiTest
    @Order(17)
    final Stream<DynamicTest> sharedKeyWorksAsExpected() {
        return defaultHapiSpec("RequiresSharedKeyToSignBothSchedulingAndScheduledTxns")
                .given(
                        overriding(SCHEDULING_WHITELIST, WHITELIST_MINIMUM),
                        newKeyNamed(SHARED_KEY),
                        cryptoCreate("payerWithSharedKey").key(SHARED_KEY))
                .when(scheduleCreate(
                                "deferredCreation",
                                cryptoCreate("yetToBe")
                                        .signedBy()
                                        .receiverSigRequired(true)
                                        .key(SHARED_KEY)
                                        .balance(123L)
                                        .fee(ONE_HBAR))
                        .payingWith("payerWithSharedKey")
                        .via("creation"))
                .then(getTxnRecord("creation").scheduled());
    }

    @HapiTest
    @Order(7)
    final Stream<DynamicTest> okIfAdminKeyOverlapsWithActiveScheduleKey() {
        var keyGen = OverlappingKeyGenerator.withAtLeastOneOverlappingByte(2);
        var adminKey = "adminKey";
        var scheduledTxnKey = "scheduledTxnKey";

        return defaultHapiSpec("OkIfAdminKeyOverlapsWithActiveScheduleKey")
                .given(
                        overriding(SCHEDULING_WHITELIST, WHITELIST_MINIMUM),
                        newKeyNamed(adminKey).generator(keyGen).logged(),
                        newKeyNamed(scheduledTxnKey).generator(keyGen).logged(),
                        cryptoCreate(SENDER).key(scheduledTxnKey).balance(1L))
                .when(scheduleCreate(DEFERRED_XFER, cryptoTransfer(tinyBarsFromTo(SENDER, ADDRESS_BOOK_CONTROL, 1)))
                        .adminKey(adminKey))
                .then();
    }

    @HapiTest
    @Order(8)
    final Stream<DynamicTest> overlappingKeysTreatedAsExpected() {
        var keyGen = OverlappingKeyGenerator.withAtLeastOneOverlappingByte(2);

        return defaultHapiSpec("OverlappingKeysTreatedAsExpected")
                .given(
                        overriding(SCHEDULING_WHITELIST, WHITELIST_MINIMUM),
                        newKeyNamed("aKey").generator(keyGen),
                        newKeyNamed("bKey").generator(keyGen),
                        newKeyNamed("cKey"),
                        cryptoCreate("aSender").key("aKey").balance(1L),
                        cryptoCreate("cSender").key("cKey").balance(1L),
                        balanceSnapshot("before", ADDRESS_BOOK_CONTROL))
                .when(scheduleCreate(
                        DEFERRED_XFER,
                        cryptoTransfer(
                                tinyBarsFromTo("aSender", ADDRESS_BOOK_CONTROL, 1),
                                tinyBarsFromTo("cSender", ADDRESS_BOOK_CONTROL, 1))))
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
                        getAccountBalance(ADDRESS_BOOK_CONTROL).hasTinyBars(changeFromSnapshot("before", +2)));
    }

    @HapiTest
    @Order(14)
    final Stream<DynamicTest> retestsActivationOnSignWithEmptySigMap() {
        return defaultHapiSpec("RetestsActivationOnCreateWithEmptySigMap")
                .given(newKeyNamed("a"), newKeyNamed("b"), newKeyListNamed("ab", List.of("a", "b")), newKeyNamed(ADMIN))
                .when(
                        cryptoCreate(SENDER).key("ab").balance(665L),
                        scheduleCreate(
                                        "deferredFall",
                                        cryptoTransfer(tinyBarsFromTo(SENDER, FUNDING, 1))
                                                .fee(ONE_HBAR))
                                .alsoSigningWith("a"),
                        getAccountBalance(SENDER).hasTinyBars(665L),
                        cryptoUpdate(SENDER).key("a"))
                .then(
                        scheduleSign("deferredFall").alsoSigningWith(),
                        getAccountBalance(SENDER).hasTinyBars(664L));
    }

    @HapiTest
    @Order(18)
    final Stream<DynamicTest> signFailsDueToDeletedExpiration() {
        final int FAST_EXPIRATION = 0;
        return propertyPreservingHapiSpec("SignFailsDueToDeletedExpiration")
                .preserving(LEDGER_SCHEDULE_TX_EXPIRY_TIME_SECS, SCHEDULING_WHITELIST)
                .given(
                        overriding(SCHEDULING_WHITELIST, WHITELIST_MINIMUM),
                        sleepFor(SCHEDULE_EXPIRY_TIME_MS), // await any other scheduled expiring
                        // entity to expire
                        cryptoCreate(SENDER).balance(1L),
                        cryptoCreate(RECEIVER).balance(0L).receiverSigRequired(true))
                .when(
                        overriding(LEDGER_SCHEDULE_TX_EXPIRY_TIME_SECS, "" + FAST_EXPIRATION),
                        scheduleCreate(TWO_SIG_XFER, cryptoTransfer(tinyBarsFromTo(SENDER, RECEIVER, 1)))
                                .alsoSigningWith(SENDER),
                        getAccountBalance(RECEIVER).hasTinyBars(0L))
                .then(
                        sleepFor(1000),
                        scheduleSign(TWO_SIG_XFER)
                                .alsoSigningWith(RECEIVER)
                                .hasPrecheckFrom(OK, INVALID_SCHEDULE_ID)
                                .hasKnownStatusFrom(INVALID_SCHEDULE_ID, SCHEDULE_PENDING_EXPIRATION),
                        sleepFor(2000),
                        scheduleSign(TWO_SIG_XFER)
                                .alsoSigningWith(RECEIVER)
                                .fee(ONE_HUNDRED_HBARS)
                                .hasPrecheckFrom(OK, INVALID_SCHEDULE_ID)
                                .hasKnownStatusFrom(INVALID_SCHEDULE_ID, SCHEDULE_PENDING_EXPIRATION),
                        scheduleSign(TWO_SIG_XFER)
                                .alsoSigningWith(RECEIVER)
                                .fee(ONE_HUNDRED_HBARS)
                                .hasPrecheckFrom(OK, INVALID_SCHEDULE_ID)
                                .hasKnownStatusFrom(INVALID_SCHEDULE_ID),
                        getScheduleInfo(TWO_SIG_XFER).logged());
    }
}
