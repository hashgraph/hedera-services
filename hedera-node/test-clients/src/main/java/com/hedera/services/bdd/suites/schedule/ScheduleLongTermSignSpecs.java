/*
 * Copyright (C) 2021-2023 Hedera Hashgraph, LLC
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
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileUpdate;
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
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.overriding;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sleepFor;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SCHEDULE_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NO_NEW_VALID_SIGNATURES;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.RECORD_NOT_FOUND;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SOME_SIGNATURES_WERE_INVALID;

import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.HapiSpecSetup;
import com.hedera.services.bdd.spec.keys.ControlForKey;
import com.hedera.services.bdd.spec.keys.OverlappingKeyGenerator;
import com.hedera.services.bdd.suites.HapiSuite;
import com.hederahashgraph.api.proto.java.Key;
import java.util.List;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ScheduleLongTermSignSpecs extends HapiSuite {
    private static final Logger log = LogManager.getLogger(ScheduleLongTermSignSpecs.class);

    private static final String suiteWhitelist = "CryptoCreate,ConsensusSubmitMessage,CryptoTransfer";

    public static final String SCHEDULING_WHITELIST = "scheduling.whitelist";
    private static final String defaultWhitelist =
            HapiSpecSetup.getDefaultNodeProps().get(SCHEDULING_WHITELIST);
    private static final String NEW_SKEY = "newSKey";
    private static final String SENDER = "sender";
    private static final String RECEIVER = "receiver";
    private static final String TOKEN_A = "tokenA";
    private static final String SENDER_TXN = "senderTxn";
    private static final String PAYER = "payer";
    private static final String EXTRA_KEY = "extraKey";
    private static final String BASIC_XFER = "basicXfer";
    private static final String ADMIN = "admin";
    private static final String CREATE_TXN = "createTxn";
    private static final String THREE_SIG_XFER = "threeSigXfer";
    private static final String TWO_SIG_XFER = "twoSigXfer";
    private static final String SHARED_KEY = "sharedKey";
    private static final String DEFERRED_CREATION = "deferredCreation";
    private static final String CREATION = "creation";
    private static final String A_SENDER_TXN = "aSenderTxn";
    private static final String BEFORE = "before";
    private static final String DEFERRED_XFER = "deferredXfer";
    private static final String DEFERRED_FALL = "deferredFall";

    public static void main(String... args) {
        new ScheduleLongTermSignSpecs().runSuiteSync();
    }

    @Override
    public List<HapiSpec> getSpecsInSuite() {
        return List.of(
                ScheduleLongTermExecutionSpecs.enableLongTermScheduledTransactions(),
                suiteSetup(),
                triggersUponAdditionalNeededSig(),
                sharedKeyWorksAsExpected(),
                overlappingKeysTreatedAsExpected(),
                retestsActivationOnSignWithEmptySigMap(),
                basicSignatureCollectionWorks(),
                signalsIrrelevantSig(),
                signalsIrrelevantSigEvenAfterLinkedEntityUpdate(),
                triggersUponFinishingPayerSig(),
                receiverSigRequiredUpdateIsRecognized(),
                receiverSigRequiredNotConfusedByMultiSigSender(),
                receiverSigRequiredNotConfusedByOrder(),
                extraSigsDontMatterAtExpiry(),
                nestedSigningReqsWorkAsExpected(),
                changeInNestedSigningReqsRespected(),
                reductionInSigningReqsAllowsTxnToGoThrough(),
                reductionInSigningReqsAllowsTxnToGoThroughAtExpiryWithNoWaitForExpiry(),
                suiteCleanup(),
                ScheduleLongTermExecutionSpecs.setLongTermScheduledTransactionsToDefault());
    }

    private HapiSpec suiteCleanup() {
        return defaultHapiSpec("suiteCleanup")
                .given()
                .when()
                .then(fileUpdate(APP_PROPERTIES)
                        .payingWith(ADDRESS_BOOK_CONTROL)
                        .overridingProps(Map.of(SCHEDULING_WHITELIST, defaultWhitelist)));
    }

    private HapiSpec suiteSetup() {
        return defaultHapiSpec("suiteSetup")
                .given()
                .when()
                .then(fileUpdate(APP_PROPERTIES)
                        .payingWith(ADDRESS_BOOK_CONTROL)
                        .overridingProps(Map.of(SCHEDULING_WHITELIST, suiteWhitelist)));
    }

    private HapiSpec changeInNestedSigningReqsRespected() {
        var senderShape = threshOf(2, threshOf(1, 3), threshOf(1, 3), threshOf(1, 3));
        var sigOne = senderShape.signedWith(sigs(sigs(OFF, OFF, ON), sigs(OFF, OFF, OFF), sigs(OFF, OFF, OFF)));
        var firstSigThree = senderShape.signedWith(sigs(sigs(OFF, OFF, OFF), sigs(OFF, OFF, OFF), sigs(ON, OFF, OFF)));
        var secondSigThree = senderShape.signedWith(sigs(sigs(OFF, OFF, OFF), sigs(OFF, OFF, OFF), sigs(ON, ON, OFF)));
        String sender = "X";
        String receiver = "Y";
        String schedule = "Z";
        String senderKey = "sKey";
        String newSenderKey = NEW_SKEY;

        return defaultHapiSpec("ChangeInNestedSigningReqsRespectedAtExpiry")
                .given(
                        newKeyNamed(senderKey).shape(senderShape),
                        keyFromMutation(newSenderKey, senderKey).changing(this::bumpThirdNestedThresholdSigningReq),
                        cryptoCreate(sender).key(senderKey).via(SENDER_TXN),
                        cryptoCreate(receiver).balance(0L),
                        scheduleCreate(schedule, cryptoTransfer(tinyBarsFromTo(sender, receiver, 1)))
                                .payingWith(DEFAULT_PAYER)
                                .waitForExpiry()
                                .withRelativeExpiry(SENDER_TXN, 8)
                                .recordingScheduledTxn()
                                .alsoSigningWith(sender)
                                .sigControl(ControlForKey.forKey(senderKey, sigOne)),
                        getAccountBalance(receiver).hasTinyBars(0L))
                .when(
                        cryptoUpdate(sender).key(newSenderKey),
                        scheduleSign(schedule)
                                .alsoSigningWith(newSenderKey)
                                .sigControl(forKey(newSenderKey, firstSigThree)),
                        getAccountBalance(receiver).hasTinyBars(0L),
                        scheduleSign(schedule)
                                .alsoSigningWith(newSenderKey)
                                .sigControl(forKey(newSenderKey, secondSigThree)),
                        getAccountBalance(receiver).hasTinyBars(0L))
                .then(
                        getScheduleInfo(schedule)
                                .hasScheduleId(schedule)
                                .hasWaitForExpiry()
                                .isNotExecuted()
                                .isNotDeleted()
                                .hasRelativeExpiry(SENDER_TXN, 8)
                                .hasRecordedScheduledTxn(),
                        sleepFor(9000),
                        cryptoCreate("foo"),
                        getScheduleInfo(schedule).hasCostAnswerPrecheck(INVALID_SCHEDULE_ID),
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

    private HapiSpec reductionInSigningReqsAllowsTxnToGoThrough() {
        var senderShape = threshOf(2, threshOf(1, 3), threshOf(1, 3), threshOf(2, 3));
        var sigOne = senderShape.signedWith(sigs(sigs(OFF, OFF, ON), sigs(OFF, OFF, OFF), sigs(OFF, OFF, OFF)));
        var sigTwo = senderShape.signedWith(sigs(sigs(OFF, OFF, OFF), sigs(ON, ON, ON), sigs(OFF, OFF, OFF)));
        var firstSigThree = senderShape.signedWith(sigs(sigs(OFF, OFF, OFF), sigs(OFF, OFF, OFF), sigs(ON, OFF, OFF)));
        String sender = "X";
        String receiver = "Y";
        String schedule = "Z";
        String senderKey = "sKey";
        String newSenderKey = NEW_SKEY;

        return defaultHapiSpec("ReductionInSigningReqsAllowsTxnToGoThroughAtExpiry")
                .given(
                        newKeyNamed(senderKey).shape(senderShape),
                        keyFromMutation(newSenderKey, senderKey).changing(this::lowerThirdNestedThresholdSigningReq),
                        cryptoCreate(sender).key(senderKey).via(SENDER_TXN),
                        cryptoCreate(receiver).balance(0L),
                        scheduleCreate(schedule, cryptoTransfer(tinyBarsFromTo(sender, receiver, 1)))
                                .payingWith(DEFAULT_PAYER)
                                .waitForExpiry()
                                .withRelativeExpiry(SENDER_TXN, 8)
                                .recordingScheduledTxn()
                                .alsoSigningWith(sender)
                                .sigControl(ControlForKey.forKey(senderKey, sigOne)),
                        getAccountBalance(receiver).hasTinyBars(0L))
                .when(
                        scheduleSign(schedule)
                                .alsoSigningWith(newSenderKey)
                                .sigControl(forKey(newSenderKey, firstSigThree)),
                        getAccountBalance(receiver).hasTinyBars(0L),
                        cryptoUpdate(sender).key(newSenderKey),
                        scheduleSign(schedule).hasKnownStatus(NO_NEW_VALID_SIGNATURES),
                        getAccountBalance(receiver).hasTinyBars(0L))
                .then(
                        getScheduleInfo(schedule)
                                .hasScheduleId(schedule)
                                .hasWaitForExpiry()
                                .isNotExecuted()
                                .isNotDeleted()
                                .hasRelativeExpiry(SENDER_TXN, 8)
                                .hasRecordedScheduledTxn(),
                        sleepFor(9000),
                        cryptoCreate("foo"),
                        scheduleSign(schedule)
                                .alsoSigningWith(newSenderKey)
                                .sigControl(forKey(newSenderKey, sigTwo))
                                .hasPrecheck(INVALID_SCHEDULE_ID),
                        getAccountBalance(receiver).hasTinyBars(1L));
    }

    private HapiSpec reductionInSigningReqsAllowsTxnToGoThroughAtExpiryWithNoWaitForExpiry() {
        var senderShape = threshOf(2, threshOf(1, 3), threshOf(1, 3), threshOf(2, 3));
        var sigOne = senderShape.signedWith(sigs(sigs(OFF, OFF, ON), sigs(OFF, OFF, OFF), sigs(OFF, OFF, OFF)));
        var sigTwo = senderShape.signedWith(sigs(sigs(OFF, OFF, OFF), sigs(ON, ON, ON), sigs(OFF, OFF, OFF)));
        var firstSigThree = senderShape.signedWith(sigs(sigs(OFF, OFF, OFF), sigs(OFF, OFF, OFF), sigs(ON, OFF, OFF)));
        String sender = "X";
        String receiver = "Y";
        String schedule = "Z";
        String senderKey = "sKey";
        String newSenderKey = NEW_SKEY;

        return defaultHapiSpec("ReductionInSigningReqsAllowsTxnToGoThroughAtExpiryWithNoWaitForExpiry")
                .given(
                        newKeyNamed(senderKey).shape(senderShape),
                        keyFromMutation(newSenderKey, senderKey).changing(this::lowerThirdNestedThresholdSigningReq),
                        cryptoCreate(sender).key(senderKey).via(SENDER_TXN),
                        cryptoCreate(receiver).balance(0L),
                        scheduleCreate(schedule, cryptoTransfer(tinyBarsFromTo(sender, receiver, 1)))
                                .payingWith(DEFAULT_PAYER)
                                .withRelativeExpiry(SENDER_TXN, 8)
                                .recordingScheduledTxn()
                                .alsoSigningWith(sender)
                                .sigControl(ControlForKey.forKey(senderKey, sigOne)),
                        getAccountBalance(receiver).hasTinyBars(0L))
                .when(
                        scheduleSign(schedule)
                                .alsoSigningWith(newSenderKey)
                                .sigControl(forKey(newSenderKey, firstSigThree)),
                        getAccountBalance(receiver).hasTinyBars(0L),
                        cryptoUpdate(sender).key(newSenderKey),
                        getAccountBalance(receiver).hasTinyBars(0L))
                .then(
                        getScheduleInfo(schedule)
                                .hasScheduleId(schedule)
                                .hasWaitForExpiry(false)
                                .isNotExecuted()
                                .isNotDeleted()
                                .hasRelativeExpiry(SENDER_TXN, 8)
                                .hasRecordedScheduledTxn(),
                        sleepFor(9000),
                        cryptoCreate("foo"),
                        scheduleSign(schedule)
                                .alsoSigningWith(newSenderKey)
                                .sigControl(forKey(newSenderKey, sigTwo))
                                .hasPrecheck(INVALID_SCHEDULE_ID),
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

    private HapiSpec nestedSigningReqsWorkAsExpected() {
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
                                .withRelativeExpiry(SENDER_TXN, 8)
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
                                .hasRelativeExpiry(SENDER_TXN, 8)
                                .hasRecordedScheduledTxn(),
                        sleepFor(9000),
                        cryptoCreate("foo"),
                        getScheduleInfo(schedule).hasCostAnswerPrecheck(INVALID_SCHEDULE_ID),
                        getAccountBalance(receiver).hasTinyBars(1L));
    }

    private HapiSpec receiverSigRequiredNotConfusedByOrder() {
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
                                .withRelativeExpiry(SENDER_TXN, 8)
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
                                .hasRelativeExpiry(SENDER_TXN, 8)
                                .hasRecordedScheduledTxn(),
                        sleepFor(9000),
                        cryptoCreate("foo"),
                        getScheduleInfo(schedule).hasCostAnswerPrecheck(INVALID_SCHEDULE_ID),
                        getAccountBalance(receiver).hasTinyBars(1L));
    }

    private HapiSpec extraSigsDontMatterAtExpiry() {
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
                                .withRelativeExpiry(SENDER_TXN, 20)
                                .recordingScheduledTxn()
                                .payingWith(DEFAULT_PAYER),
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
                                .hasRelativeExpiry(SENDER_TXN, 20)
                                .hasRecordedScheduledTxn(),
                        sleepFor(21000),
                        cryptoCreate("foo"),
                        getScheduleInfo(schedule).hasCostAnswerPrecheck(INVALID_SCHEDULE_ID),
                        getAccountBalance(receiver).hasTinyBars(1L));
    }

    private HapiSpec receiverSigRequiredNotConfusedByMultiSigSender() {
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
                                .withRelativeExpiry(SENDER_TXN, 8)
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
                                .hasRelativeExpiry(SENDER_TXN, 8)
                                .hasRecordedScheduledTxn(),
                        sleepFor(9000),
                        cryptoCreate("foo"),
                        getScheduleInfo(schedule).hasCostAnswerPrecheck(INVALID_SCHEDULE_ID),
                        getAccountBalance(receiver).hasTinyBars(1L));
    }

    private HapiSpec receiverSigRequiredUpdateIsRecognized() {
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
                                .withRelativeExpiry(SENDER_TXN, 8)
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
                                .hasRelativeExpiry(SENDER_TXN, 8)
                                .hasRecordedScheduledTxn(),
                        sleepFor(9000),
                        cryptoCreate("foo"),
                        getAccountBalance(receiver).hasTinyBars(1),
                        scheduleSign(schedule)
                                .alsoSigningWith(senderKey)
                                .sigControl(forKey(senderKey, sigTwo))
                                .hasPrecheck(INVALID_SCHEDULE_ID),
                        getAccountBalance(receiver).hasTinyBars(1));
    }

    private HapiSpec basicSignatureCollectionWorks() {
        var txnBody = cryptoTransfer(tinyBarsFromTo(SENDER, RECEIVER, 1));

        return defaultHapiSpec("BasicSignatureCollectionWorksWithExpiryAndWait")
                .given(
                        cryptoCreate(SENDER).via(SENDER_TXN),
                        cryptoCreate(RECEIVER).receiverSigRequired(true),
                        scheduleCreate(BASIC_XFER, txnBody).waitForExpiry().withRelativeExpiry(SENDER_TXN, 8))
                .when(scheduleSign(BASIC_XFER).alsoSigningWith(RECEIVER))
                .then(getScheduleInfo(BASIC_XFER).hasSignatories(RECEIVER));
    }

    private HapiSpec signalsIrrelevantSig() {
        var txnBody = cryptoTransfer(tinyBarsFromTo(SENDER, RECEIVER, 1));

        return defaultHapiSpec("SignalsIrrelevantSigWithExpiryAndWait")
                .given(
                        cryptoCreate(SENDER).via(SENDER_TXN),
                        cryptoCreate(RECEIVER),
                        newKeyNamed("somebodyelse"),
                        scheduleCreate(BASIC_XFER, txnBody).waitForExpiry().withRelativeExpiry(SENDER_TXN, 8))
                .when()
                .then(scheduleSign(BASIC_XFER)
                        .alsoSigningWith("somebodyelse")
                        .hasKnownStatusFrom(NO_NEW_VALID_SIGNATURES, SOME_SIGNATURES_WERE_INVALID));
    }

    private HapiSpec signalsIrrelevantSigEvenAfterLinkedEntityUpdate() {
        var txnBody = mintToken(TOKEN_A, 50000000L);

        return defaultHapiSpec("SignalsIrrelevantSigEvenAfterLinkedEntityUpdateWithExpiryAndWait")
                .given(
                        overriding(SCHEDULING_WHITELIST, "TokenMint"),
                        newKeyNamed(ADMIN),
                        newKeyNamed("mint"),
                        newKeyNamed("newMint"),
                        tokenCreate(TOKEN_A)
                                .adminKey(ADMIN)
                                .supplyKey("mint")
                                .via(CREATE_TXN),
                        scheduleCreate("tokenMintScheduled", txnBody)
                                .waitForExpiry()
                                .withRelativeExpiry(CREATE_TXN, 8))
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
                        overriding(SCHEDULING_WHITELIST, suiteWhitelist));
    }

    public HapiSpec triggersUponFinishingPayerSig() {
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
                                .withRelativeExpiry(SENDER_TXN, 8)
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
                                .hasRelativeExpiry(SENDER_TXN, 8)
                                .hasRecordedScheduledTxn(),
                        sleepFor(9000),
                        cryptoCreate("foo"),
                        getScheduleInfo(THREE_SIG_XFER).hasCostAnswerPrecheck(INVALID_SCHEDULE_ID),
                        getAccountBalance(RECEIVER).hasTinyBars(1L));
    }

    public HapiSpec triggersUponAdditionalNeededSig() {
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
                                .withRelativeExpiry(SENDER_TXN, 8)
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
                                .hasRelativeExpiry(SENDER_TXN, 8)
                                .hasRecordedScheduledTxn(),
                        sleepFor(9000),
                        cryptoCreate("foo"),
                        getScheduleInfo(TWO_SIG_XFER).hasCostAnswerPrecheck(INVALID_SCHEDULE_ID),
                        getAccountBalance(RECEIVER).hasTinyBars(1L));
    }

    public HapiSpec sharedKeyWorksAsExpected() {
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
                        .withRelativeExpiry(CREATE_TXN, 8)
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
                                .hasRelativeExpiry(CREATE_TXN, 8)
                                .hasRecordedScheduledTxn(),
                        sleepFor(9000),
                        cryptoCreate("foo"),
                        getScheduleInfo(DEFERRED_CREATION).hasCostAnswerPrecheck(INVALID_SCHEDULE_ID),
                        getTxnRecord(CREATION).scheduled());
    }

    public HapiSpec overlappingKeysTreatedAsExpected() {
        var keyGen = OverlappingKeyGenerator.withAtLeastOneOverlappingByte(2);

        return defaultHapiSpec("OverlappingKeysTreatedAsExpectedAtExpiry")
                .given(
                        newKeyNamed("aKey").generator(keyGen),
                        newKeyNamed("bKey").generator(keyGen),
                        newKeyNamed("cKey"),
                        cryptoCreate("aSender").key("aKey").balance(1L).via(A_SENDER_TXN),
                        cryptoCreate("cSender").key("cKey").balance(1L),
                        balanceSnapshot(BEFORE, ADDRESS_BOOK_CONTROL))
                .when(scheduleCreate(
                        DEFERRED_XFER,
                        cryptoTransfer(
                                tinyBarsFromTo("aSender", ADDRESS_BOOK_CONTROL, 1),
                                tinyBarsFromTo("cSender", ADDRESS_BOOK_CONTROL, 1)))
                        .waitForExpiry()
                        .withRelativeExpiry(A_SENDER_TXN, 8)
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
                                .hasRelativeExpiry(A_SENDER_TXN, 8)
                                .hasRecordedScheduledTxn(),
                        sleepFor(9000),
                        cryptoCreate("foo"),
                        getScheduleInfo(DEFERRED_XFER).hasCostAnswerPrecheck(INVALID_SCHEDULE_ID),
                        getAccountBalance(ADDRESS_BOOK_CONTROL).hasTinyBars(changeFromSnapshot(BEFORE, +2)));
    }

    public HapiSpec retestsActivationOnSignWithEmptySigMap() {
        return defaultHapiSpec("RetestsActivationOnCreateWithEmptySigMapAtExpiry")
                .given(
                        newKeyNamed("a"),
                        newKeyNamed("b"),
                        newKeyListNamed("ab", List.of("a", "b")),
                        newKeyNamed(ADMIN))
                .when(
                        cryptoCreate(SENDER).key("ab").balance(667L).via(SENDER_TXN),
                        scheduleCreate(
                                DEFERRED_FALL,
                                cryptoTransfer(tinyBarsFromTo(SENDER, FUNDING, 1))
                                        .fee(ONE_HBAR))
                                .waitForExpiry()
                                .withRelativeExpiry(SENDER_TXN, 8)
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
                                .hasRelativeExpiry(SENDER_TXN, 8)
                                .hasRecordedScheduledTxn(),
                        sleepFor(9000),
                        cryptoCreate("foo"),
                        getScheduleInfo(DEFERRED_FALL).hasCostAnswerPrecheck(INVALID_SCHEDULE_ID),
                        getAccountBalance(SENDER).hasTinyBars(666L));
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}
