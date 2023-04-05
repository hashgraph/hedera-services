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
import static com.hedera.services.bdd.suites.schedule.ScheduleLongTermExecutionSpecs.withAndWithoutLongTermEnabled;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SCHEDULE_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NO_NEW_VALID_SIGNATURES;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SCHEDULE_ALREADY_DELETED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SCHEDULE_ALREADY_EXECUTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SCHEDULE_PENDING_EXPIRATION;
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

public class ScheduleSignSpecs extends HapiSuite {
    private static final Logger log = LogManager.getLogger(ScheduleSignSpecs.class);
    private static final int SCHEDULE_EXPIRY_TIME_SECS = 10;
    private static final int SCHEDULE_EXPIRY_TIME_MS = SCHEDULE_EXPIRY_TIME_SECS * 1000;

    private static final String suiteWhitelist = "CryptoCreate,ConsensusSubmitMessage,CryptoTransfer";
    private static final String LEDGER_SCHEDULE_TX_EXPIRY_TIME_SECS = "ledger.schedule.txExpiryTimeSecs";
    private static final String defaultTxExpiry =
            HapiSpecSetup.getDefaultNodeProps().get(LEDGER_SCHEDULE_TX_EXPIRY_TIME_SECS);
    private static final String SCHEDULING_WHITELIST = "scheduling.whitelist";
    private static final String defaultWhitelist =
            HapiSpecSetup.getDefaultNodeProps().get(SCHEDULING_WHITELIST);
    private static final String NEW_SENDER_KEY = "newSKey";
    private static final String DEFERRED_XFER = "deferredXfer";
    private static final String ADMIN = "admin";
    private static final String SHARED_KEY = "sharedKey";
    private static final String TWO_SIG_XFER = "twoSigXfer";
    private static final String PAYER = "payer";
    private static final String SOMEBODY = "somebody";
    private static final String RANDOM_KEY = "randomKey";
    private static final String SENDER = "sender";
    private static final String RECEIVER = "receiver";
    private static final String BASIC_XFER = "basicXfer";
    private static final String TOKEN_A = "tokenA";

    public static void main(String... args) {
        new ScheduleSignSpecs().runSuiteSync();
    }

    @Override
    public List<HapiSpec> getSpecsInSuite() {
        return withAndWithoutLongTermEnabled(() -> List.of(
                suiteSetup(),
                signFailsDueToDeletedExpiration(),
                triggersUponAdditionalNeededSig(),
                sharedKeyWorksAsExpected(),
                overlappingKeysTreatedAsExpected(),
                retestsActivationOnSignWithEmptySigMap(),
                basicSignatureCollectionWorks(),
                addingSignaturesToNonExistingTxFails(),
                signalsIrrelevantSig(),
                signalsIrrelevantSigEvenAfterLinkedEntityUpdate(),
                triggersUponFinishingPayerSig(),
                addingSignaturesToExecutedTxFails(),
                okIfAdminKeyOverlapsWithActiveScheduleKey(),
                scheduleAlreadyExecutedDoesntRepeatTransaction(),
                receiverSigRequiredUpdateIsRecognized(),
                scheduleAlreadyExecutedOnCreateDoesntRepeatTransaction(),
                receiverSigRequiredNotConfusedByMultiSigSender(),
                receiverSigRequiredNotConfusedByOrder(),
                nestedSigningReqsWorkAsExpected(),
                changeInNestedSigningReqsRespected(),
                reductionInSigningReqsAllowsTxnToGoThrough(),
                reductionInSigningReqsAllowsTxnToGoThroughWithRandomKey(),
                signingDeletedSchedulesHasNoEffect(),
                suiteCleanup()));
    }

    private HapiSpec suiteCleanup() {
        return defaultHapiSpec("suiteCleanup")
                .given()
                .when()
                .then(fileUpdate(APP_PROPERTIES)
                        .payingWith(ADDRESS_BOOK_CONTROL)
                        .overridingProps(Map.of(
                                LEDGER_SCHEDULE_TX_EXPIRY_TIME_SECS, defaultTxExpiry,
                                SCHEDULING_WHITELIST, defaultWhitelist)));
    }

    private HapiSpec suiteSetup() {
        return defaultHapiSpec("suiteSetup")
                .given()
                .when()
                .then(fileUpdate(APP_PROPERTIES)
                        .payingWith(ADDRESS_BOOK_CONTROL)
                        .overridingProps(Map.of(SCHEDULING_WHITELIST, suiteWhitelist)));
    }

    private HapiSpec signingDeletedSchedulesHasNoEffect() {
        String sender = "X";
        String receiver = "Y";
        String schedule = "Z";
        String adminKey = ADMIN;

        return defaultHapiSpec("SigningDeletedSchedulesHasNoEffect")
                .given(
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

    private HapiSpec changeInNestedSigningReqsRespected() {
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

    private HapiSpec reductionInSigningReqsAllowsTxnToGoThrough() {
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
                        scheduleSign(schedule),
                        getAccountBalance(receiver).hasTinyBars(1L))
                .then(
                        scheduleSign(schedule)
                                .alsoSigningWith(NEW_SENDER_KEY)
                                .sigControl(forKey(NEW_SENDER_KEY, sigTwo))
                                .hasKnownStatus(SCHEDULE_ALREADY_EXECUTED),
                        getAccountBalance(receiver).hasTinyBars(1L));
    }

    private HapiSpec reductionInSigningReqsAllowsTxnToGoThroughWithRandomKey() {
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

    private HapiSpec nestedSigningReqsWorkAsExpected() {
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

    private HapiSpec receiverSigRequiredNotConfusedByOrder() {
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

    private HapiSpec receiverSigRequiredNotConfusedByMultiSigSender() {
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

    private HapiSpec receiverSigRequiredUpdateIsRecognized() {
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

    private HapiSpec scheduleAlreadyExecutedOnCreateDoesntRepeatTransaction() {
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

    private HapiSpec scheduleAlreadyExecutedDoesntRepeatTransaction() {
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

    private HapiSpec basicSignatureCollectionWorks() {
        var txnBody = cryptoTransfer(tinyBarsFromTo(SENDER, RECEIVER, 1));

        return defaultHapiSpec("BasicSignatureCollectionWorks")
                .given(
                        cryptoCreate(SENDER),
                        cryptoCreate(RECEIVER).receiverSigRequired(true),
                        scheduleCreate(BASIC_XFER, txnBody))
                .when(scheduleSign(BASIC_XFER).alsoSigningWith(RECEIVER))
                .then(getScheduleInfo(BASIC_XFER).hasSignatories(RECEIVER));
    }

    private HapiSpec signalsIrrelevantSig() {
        var txnBody = cryptoTransfer(tinyBarsFromTo(SENDER, RECEIVER, 1));

        return defaultHapiSpec("SignalsIrrelevantSig")
                .given(
                        cryptoCreate(SENDER),
                        cryptoCreate(RECEIVER),
                        newKeyNamed("somebodyelse"),
                        scheduleCreate(BASIC_XFER, txnBody))
                .when()
                .then(scheduleSign(BASIC_XFER)
                        .alsoSigningWith("somebodyelse")
                        .hasKnownStatusFrom(NO_NEW_VALID_SIGNATURES, SOME_SIGNATURES_WERE_INVALID));
    }

    private HapiSpec signalsIrrelevantSigEvenAfterLinkedEntityUpdate() {
        var txnBody = mintToken(TOKEN_A, 50000000L);

        return defaultHapiSpec("SignalsIrrelevantSigEvenAfterLinkedEntityUpdate")
                .given(
                        overriding(SCHEDULING_WHITELIST, "TokenMint"),
                        newKeyNamed(ADMIN),
                        newKeyNamed("mint"),
                        newKeyNamed("newMint"),
                        tokenCreate(TOKEN_A).adminKey(ADMIN).supplyKey("mint"),
                        scheduleCreate("tokenMintScheduled", txnBody))
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

    private HapiSpec addingSignaturesToNonExistingTxFails() {
        return defaultHapiSpec("AddingSignaturesToNonExistingTxFails")
                .given(cryptoCreate(SENDER), newKeyNamed(SOMEBODY))
                .when()
                .then(scheduleSign("0.0.123321")
                        .fee(ONE_HBAR)
                        .alsoSigningWith(SOMEBODY, SENDER)
                        .hasPrecheckFrom(OK, INVALID_SCHEDULE_ID)
                        .hasKnownStatus(INVALID_SCHEDULE_ID));
    }

    private HapiSpec addingSignaturesToExecutedTxFails() {
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

    public HapiSpec triggersUponFinishingPayerSig() {
        return defaultHapiSpec("TriggersUponFinishingPayerSig")
                .given(
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

    public HapiSpec triggersUponAdditionalNeededSig() {
        return defaultHapiSpec("TriggersUponAdditionalNeededSig")
                .given(
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

    public HapiSpec sharedKeyWorksAsExpected() {
        return defaultHapiSpec("RequiresSharedKeyToSignBothSchedulingAndScheduledTxns")
                .given(
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

    public HapiSpec okIfAdminKeyOverlapsWithActiveScheduleKey() {
        var keyGen = OverlappingKeyGenerator.withAtLeastOneOverlappingByte(2);
        var adminKey = "adminKey";
        var scheduledTxnKey = "scheduledTxnKey";

        return defaultHapiSpec("OkIfAdminKeyOverlapsWithActiveScheduleKey")
                .given(
                        newKeyNamed(adminKey).generator(keyGen).logged(),
                        newKeyNamed(scheduledTxnKey).generator(keyGen).logged(),
                        cryptoCreate(SENDER).key(scheduledTxnKey).balance(1L))
                .when(scheduleCreate(DEFERRED_XFER, cryptoTransfer(tinyBarsFromTo(SENDER, ADDRESS_BOOK_CONTROL, 1)))
                        .adminKey(adminKey))
                .then();
    }

    public HapiSpec overlappingKeysTreatedAsExpected() {
        var keyGen = OverlappingKeyGenerator.withAtLeastOneOverlappingByte(2);

        return defaultHapiSpec("OverlappingKeysTreatedAsExpected")
                .given(
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

    public HapiSpec retestsActivationOnSignWithEmptySigMap() {
        return defaultHapiSpec("RetestsActivationOnCreateWithEmptySigMap")
                .given(newKeyNamed("a"), newKeyNamed("b"), newKeyListNamed("ab", List.of("a", "b")), newKeyNamed(ADMIN))
                .when(
                        cryptoCreate(SENDER).key("ab").balance(667L),
                        scheduleCreate(
                                        "deferredFall",
                                        cryptoTransfer(tinyBarsFromTo(SENDER, FUNDING, 1))
                                                .fee(ONE_HBAR))
                                .alsoSigningWith("a"),
                        getAccountBalance(SENDER).hasTinyBars(667L),
                        cryptoUpdate(SENDER).key("a"))
                .then(
                        scheduleSign("deferredFall").alsoSigningWith(),
                        getAccountBalance(SENDER).hasTinyBars(666L));
    }

    public HapiSpec signFailsDueToDeletedExpiration() {
        final int FAST_EXPIRATION = 0;
        return defaultHapiSpec("SignFailsDueToDeletedExpiration")
                .given(
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
                        scheduleSign(TWO_SIG_XFER)
                                .alsoSigningWith(RECEIVER)
                                .hasPrecheckFrom(OK, INVALID_SCHEDULE_ID)
                                .hasKnownStatusFrom(INVALID_SCHEDULE_ID, SCHEDULE_PENDING_EXPIRATION),
                        sleepFor(2000),
                        scheduleSign(TWO_SIG_XFER)
                                .alsoSigningWith(RECEIVER)
                                .hasPrecheckFrom(OK, INVALID_SCHEDULE_ID)
                                .hasKnownStatusFrom(INVALID_SCHEDULE_ID, SCHEDULE_PENDING_EXPIRATION),
                        scheduleSign(TWO_SIG_XFER)
                                .alsoSigningWith(RECEIVER)
                                .hasPrecheckFrom(OK, INVALID_SCHEDULE_ID)
                                .hasKnownStatusFrom(INVALID_SCHEDULE_ID),
                        getScheduleInfo(TWO_SIG_XFER).hasCostAnswerPrecheck(INVALID_SCHEDULE_ID),
                        overriding(LEDGER_SCHEDULE_TX_EXPIRY_TIME_SECS, "" + defaultTxExpiry));
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}
