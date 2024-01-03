/*
 * Copyright (C) 2020-2024 Hedera Hashgraph, LLC
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

import static com.hedera.services.bdd.spec.HapiSpec.customHapiSpec;
import static com.hedera.services.bdd.spec.HapiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.keys.ControlForKey.forKey;
import static com.hedera.services.bdd.spec.keys.KeyShape.SIMPLE;
import static com.hedera.services.bdd.spec.keys.KeyShape.listOf;
import static com.hedera.services.bdd.spec.keys.KeyShape.sigs;
import static com.hedera.services.bdd.spec.keys.KeyShape.threshOf;
import static com.hedera.services.bdd.spec.keys.SigControl.OFF;
import static com.hedera.services.bdd.spec.keys.SigControl.ON;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getFileInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getReceipt;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getScheduleInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnUtils.nAscii;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.createTopic;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileDelete;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.scheduleCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.scheduleCreateFunctionless;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.scheduleSign;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromTo;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.overriding;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sleepFor;
import static com.hedera.services.bdd.suites.schedule.ScheduleUtils.ADMIN;
import static com.hedera.services.bdd.suites.schedule.ScheduleUtils.BASIC_XFER;
import static com.hedera.services.bdd.suites.schedule.ScheduleUtils.CONTINUE;
import static com.hedera.services.bdd.suites.schedule.ScheduleUtils.COPYCAT;
import static com.hedera.services.bdd.suites.schedule.ScheduleUtils.CREATION;
import static com.hedera.services.bdd.suites.schedule.ScheduleUtils.DEFAULT_TX_EXPIRY;
import static com.hedera.services.bdd.suites.schedule.ScheduleUtils.DESIGNATING_PAYER;
import static com.hedera.services.bdd.suites.schedule.ScheduleUtils.ENTITY_MEMO;
import static com.hedera.services.bdd.suites.schedule.ScheduleUtils.FIRST_PAYER;
import static com.hedera.services.bdd.suites.schedule.ScheduleUtils.NEVER_TO_BE;
import static com.hedera.services.bdd.suites.schedule.ScheduleUtils.ONLY_BODY;
import static com.hedera.services.bdd.suites.schedule.ScheduleUtils.ONLY_BODY_AND_ADMIN_KEY;
import static com.hedera.services.bdd.suites.schedule.ScheduleUtils.ONLY_BODY_AND_MEMO;
import static com.hedera.services.bdd.suites.schedule.ScheduleUtils.ONLY_BODY_AND_PAYER;
import static com.hedera.services.bdd.suites.schedule.ScheduleUtils.ORIGINAL;
import static com.hedera.services.bdd.suites.schedule.ScheduleUtils.PAYER;
import static com.hedera.services.bdd.suites.schedule.ScheduleUtils.RECEIVER;
import static com.hedera.services.bdd.suites.schedule.ScheduleUtils.SCHEDULING_WHITELIST;
import static com.hedera.services.bdd.suites.schedule.ScheduleUtils.SECOND_PAYER;
import static com.hedera.services.bdd.suites.schedule.ScheduleUtils.SENDER;
import static com.hedera.services.bdd.suites.schedule.ScheduleUtils.VALID_SCHEDULE;
import static com.hedera.services.bdd.suites.schedule.ScheduleUtils.WHITELIST_DEFAULT;
import static com.hedera.services.bdd.suites.schedule.ScheduleUtils.withAndWithoutLongTermEnabled;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_ID_DOES_NOT_EXIST;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.BUSY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.IDENTICAL_SCHEDULE_ALREADY_CREATED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ADMIN_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SCHEDULE_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ZERO_BYTE_IN_STRING;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.MEMO_TOO_LONG;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SCHEDULED_TRANSACTION_NOT_IN_WHITELIST;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SOME_SIGNATURES_WERE_INVALID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.UNRESOLVABLE_REQUIRED_SIGNERS;

import com.hedera.services.bdd.junit.HapiTest;
import com.hedera.services.bdd.junit.HapiTestSuite;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.HapiSpecSetup;
import com.hedera.services.bdd.spec.keys.KeyShape;
import com.hedera.services.bdd.spec.keys.OverlappingKeyGenerator;
import com.hedera.services.bdd.spec.keys.SigControl;
import com.hedera.services.bdd.suites.HapiSuite;
import java.security.SecureRandom;
import java.util.List;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@HapiTestSuite
public class ScheduleCreateSpecs extends HapiSuite {
    private static final Logger log = LogManager.getLogger(ScheduleCreateSpecs.class);

    public static void main(String... args) {
        new ScheduleCreateSpecs().runSuiteSync();
    }

    @Override
    public List<HapiSpec> getSpecsInSuite() {
        return withAndWithoutLongTermEnabled(() -> List.of(
                notIdenticalScheduleIfScheduledTxnChanges(),
                notIdenticalScheduleIfAdminKeyChanges(),
                notIdenticalScheduleIfMemoChanges(),
                recognizesIdenticalScheduleEvenWithDifferentDesignatedPayer(),
                rejectsSentinelKeyListAsAdminKey(),
                rejectsMalformedScheduledTxnMemo(),
                bodyOnlyCreation(),
                onlyBodyAndAdminCreation(),
                onlyBodyAndMemoCreation(),
                bodyAndSignatoriesCreation(),
                bodyAndPayerCreation(),
                rejectsUnresolvableReqSigners(),
                triggersImmediatelyWithBothReqSimpleSigs(),
                onlySchedulesWithMissingReqSimpleSigs(),
                failsWithNonExistingPayerAccountId(),
                failsWithTooLongMemo(),
                detectsKeysChangedBetweenExpandSigsAndHandleTxn(),
                doesntTriggerUntilPayerSigns(),
                requiresExtantPayer(),
                rejectsFunctionlessTxn(),
                functionlessTxnBusyWithNonExemptPayer(),
                whitelistWorks(),
                preservesRevocationServiceSemanticsForFileDelete(),
                worksAsExpectedWithDefaultScheduleId(),
                infoIncludesTxnIdFromCreationReceipt(),
                suiteCleanup(),
                validateSignersInInfo()));
    }

    @HapiTest
    final HapiSpec suiteCleanup() {
        return defaultHapiSpec("suiteCleanup")
                .given()
                .when()
                .then(overriding("ledger.schedule.txExpiryTimeSecs", DEFAULT_TX_EXPIRY));
    }

    @HapiTest
    final HapiSpec worksAsExpectedWithDefaultScheduleId() {
        return defaultHapiSpec("WorksAsExpectedWithDefaultScheduleId")
                .given()
                .when()
                .then(getScheduleInfo("0.0.0").hasCostAnswerPrecheck(INVALID_SCHEDULE_ID));
    }

    @HapiTest
    final HapiSpec bodyOnlyCreation() {
        return customHapiSpec("bodyOnlyCreation")
                .withProperties(Map.of("default.keyAlgorithm", "SECP256K1"))
                .given(overriding(SCHEDULING_WHITELIST, "CryptoTransfer,CryptoCreate"), cryptoCreate(SENDER))
                .when(
                        scheduleCreate(ONLY_BODY, cryptoTransfer(tinyBarsFromTo(SENDER, GENESIS, 1)))
                                .recordingScheduledTxn(),
                        scheduleSign(ONLY_BODY).alsoSigningWith(SENDER))
                .then(getScheduleInfo(ONLY_BODY)
                        .hasScheduleId(ONLY_BODY)
                        .hasRecordedScheduledTxn()
                        .logged());
    }

    @HapiTest
    final HapiSpec validateSignersInInfo() {
        return customHapiSpec(VALID_SCHEDULE)
                .withProperties(Map.of("default.keyAlgorithm", "SECP256K1"))
                .given(cryptoCreate(SENDER))
                .when(
                        scheduleCreate(VALID_SCHEDULE, cryptoTransfer(tinyBarsFromTo(SENDER, GENESIS, 1)))
                                .recordingScheduledTxn(),
                        scheduleSign(VALID_SCHEDULE).alsoSigningWith(SENDER))
                .then(getScheduleInfo(VALID_SCHEDULE)
                        .hasScheduleId(VALID_SCHEDULE)
                        .hasRecordedScheduledTxn()
                        .hasSignatories(SENDER));
    }

    @HapiTest
    final HapiSpec onlyBodyAndAdminCreation() {
        return defaultHapiSpec("OnlyBodyAndAdminCreation")
                .given(newKeyNamed(ADMIN), cryptoCreate(SENDER))
                .when(scheduleCreate(ONLY_BODY_AND_ADMIN_KEY, cryptoTransfer(tinyBarsFromTo(SENDER, GENESIS, 1)))
                        .adminKey(ADMIN)
                        .recordingScheduledTxn())
                .then(getScheduleInfo(ONLY_BODY_AND_ADMIN_KEY)
                        .hasScheduleId(ONLY_BODY_AND_ADMIN_KEY)
                        .hasAdminKey(ADMIN)
                        .hasRecordedScheduledTxn());
    }

    @HapiTest
    final HapiSpec onlyBodyAndMemoCreation() {
        return defaultHapiSpec("OnlyBodyAndMemoCreation")
                .given(overriding(SCHEDULING_WHITELIST, "CryptoTransfer,CryptoCreate"), cryptoCreate(SENDER))
                .when(scheduleCreate(ONLY_BODY_AND_MEMO, cryptoTransfer(tinyBarsFromTo(SENDER, GENESIS, 1)))
                        .recordingScheduledTxn()
                        .withEntityMemo("sample memo"))
                .then(getScheduleInfo(ONLY_BODY_AND_MEMO)
                        .hasScheduleId(ONLY_BODY_AND_MEMO)
                        .hasEntityMemo("sample memo")
                        .hasRecordedScheduledTxn());
    }

    @HapiTest
    final HapiSpec bodyAndPayerCreation() {
        return defaultHapiSpec("BodyAndPayerCreation")
                .given(cryptoCreate(PAYER))
                .when(scheduleCreate(
                                ONLY_BODY_AND_PAYER,
                                cryptoTransfer(tinyBarsFromTo(PAYER, GENESIS, 1))
                                        .memo("SURPRISE!!!"))
                        .recordingScheduledTxn()
                        // prevent multiple runs of this test causing duplicates
                        .withEntityMemo("" + new SecureRandom().nextLong())
                        .designatingPayer(PAYER))
                .then(getScheduleInfo(ONLY_BODY_AND_PAYER)
                        .hasScheduleId(ONLY_BODY_AND_PAYER)
                        .hasPayerAccountID(PAYER)
                        .hasRecordedScheduledTxn());
    }

    @HapiTest
    final HapiSpec bodyAndSignatoriesCreation() {
        var scheduleName = "onlyBodyAndSignatories";
        var scheduledTxn = cryptoTransfer(tinyBarsFromTo(SENDER, RECEIVER, 1));

        return defaultHapiSpec("BodyAndSignatoriesCreation")
                .given(
                        overriding(SCHEDULING_WHITELIST, "CryptoTransfer,CryptoCreate"),
                        cryptoCreate("payingAccount"),
                        newKeyNamed("adminKey"),
                        cryptoCreate(SENDER),
                        cryptoCreate(RECEIVER).receiverSigRequired(true))
                .when(scheduleCreate(scheduleName, scheduledTxn)
                        .adminKey("adminKey")
                        .recordingScheduledTxn()
                        .designatingPayer("payingAccount")
                        .alsoSigningWith(RECEIVER))
                .then(getScheduleInfo(scheduleName)
                        .hasScheduleId(scheduleName)
                        .hasSignatories(RECEIVER)
                        .hasRecordedScheduledTxn());
    }

    @HapiTest
    final HapiSpec failsWithNonExistingPayerAccountId() {
        return defaultHapiSpec("FailsWithNonExistingPayerAccountId")
                .given(overriding(SCHEDULING_WHITELIST, "CryptoTransfer,CryptoCreate"))
                .when(scheduleCreate("invalidPayer", cryptoCreate("secondary"))
                        .designatingPayer(DESIGNATING_PAYER)
                        .hasKnownStatus(ACCOUNT_ID_DOES_NOT_EXIST))
                .then();
    }

    @HapiTest
    final HapiSpec failsWithTooLongMemo() {
        return defaultHapiSpec("FailsWithTooLongMemo")
                .given()
                .when(scheduleCreate("invalidMemo", cryptoCreate("secondary"))
                        .withEntityMemo(nAscii(101))
                        .hasPrecheck(MEMO_TOO_LONG))
                .then();
    }

    @HapiTest
    final HapiSpec notIdenticalScheduleIfScheduledTxnChanges() {
        return defaultHapiSpec("NotIdenticalScheduleIfScheduledTxnChanges")
                .given(
                        overriding(SCHEDULING_WHITELIST, "CryptoTransfer,CryptoCreate"),
                        cryptoCreate(SENDER).balance(1L),
                        cryptoCreate(FIRST_PAYER),
                        scheduleCreate(
                                        ORIGINAL,
                                        cryptoTransfer(tinyBarsFromTo(SENDER, FUNDING, 1))
                                                .memo("A")
                                                .fee(ONE_HBAR))
                                .payingWith(FIRST_PAYER))
                .when()
                .then(scheduleCreate(
                                CONTINUE,
                                cryptoTransfer(tinyBarsFromTo(SENDER, FUNDING, 1))
                                        .memo("B")
                                        .fee(ONE_HBAR))
                        .payingWith(FIRST_PAYER));
    }

    @HapiTest
    final HapiSpec notIdenticalScheduleIfMemoChanges() {
        return defaultHapiSpec("NotIdenticalScheduleIfMemoChanges")
                .given(
                        overriding(SCHEDULING_WHITELIST, "CryptoTransfer,CryptoCreate"),
                        cryptoCreate(SENDER).balance(1L),
                        scheduleCreate(
                                        ORIGINAL,
                                        cryptoTransfer(tinyBarsFromTo(SENDER, FUNDING, 1))
                                                .fee(ONE_HBAR))
                                .withEntityMemo(ENTITY_MEMO))
                .when()
                .then(scheduleCreate(
                                CONTINUE,
                                cryptoTransfer(tinyBarsFromTo(SENDER, FUNDING, 1))
                                        .fee(ONE_HBAR))
                        .withEntityMemo("The whole time he was at the Bodies, til"));
    }

    @HapiTest
    final HapiSpec notIdenticalScheduleIfAdminKeyChanges() {
        return defaultHapiSpec("notIdenticalScheduleIfAdminKeyChanges")
                .given(
                        newKeyNamed("adminA"),
                        newKeyNamed("adminB"),
                        cryptoCreate(SENDER).balance(1L),
                        cryptoCreate(FIRST_PAYER),
                        scheduleCreate(
                                        ORIGINAL,
                                        cryptoTransfer(tinyBarsFromTo(SENDER, FUNDING, 1))
                                                .fee(ONE_HBAR))
                                .adminKey("adminA")
                                .withEntityMemo(ENTITY_MEMO)
                                .designatingPayer(FIRST_PAYER)
                                .payingWith(FIRST_PAYER))
                .when()
                .then(scheduleCreate(
                                CONTINUE,
                                cryptoTransfer(tinyBarsFromTo(SENDER, FUNDING, 1))
                                        .fee(ONE_HBAR))
                        .adminKey("adminB")
                        .withEntityMemo(ENTITY_MEMO)
                        .designatingPayer(FIRST_PAYER)
                        .payingWith(FIRST_PAYER));
    }

    @HapiTest
    final HapiSpec recognizesIdenticalScheduleEvenWithDifferentDesignatedPayer() {
        return defaultHapiSpec("recognizesIdenticalScheduleEvenWithDifferentDesignatedPayer")
                .given(
                        overriding(SCHEDULING_WHITELIST, "CryptoTransfer,CryptoCreate"),
                        cryptoCreate(SENDER).balance(1L),
                        cryptoCreate(FIRST_PAYER),
                        scheduleCreate(
                                        ORIGINAL,
                                        cryptoTransfer(tinyBarsFromTo(SENDER, FUNDING, 1))
                                                .fee(ONE_HBAR))
                                .designatingPayer(FIRST_PAYER)
                                .payingWith(FIRST_PAYER)
                                .savingExpectedScheduledTxnId())
                .when(cryptoCreate(SECOND_PAYER))
                .then(
                        scheduleCreate(
                                        "duplicate",
                                        cryptoTransfer(tinyBarsFromTo(SENDER, FUNDING, 1))
                                                .fee(ONE_HBAR))
                                .payingWith(SECOND_PAYER)
                                .designatingPayer(SECOND_PAYER)
                                .via(COPYCAT)
                                .hasKnownStatus(IDENTICAL_SCHEDULE_ALREADY_CREATED),
                        getTxnRecord(COPYCAT).logged(),
                        getReceipt(COPYCAT).hasSchedule(ORIGINAL).hasScheduledTxnId(ORIGINAL));
    }

    @HapiTest
    final HapiSpec rejectsSentinelKeyListAsAdminKey() {
        return defaultHapiSpec("RejectsSentinelKeyListAsAdminKey")
                .given()
                .when()
                .then(scheduleCreate(CREATION, cryptoTransfer(tinyBarsFromTo(GENESIS, FUNDING, 1)))
                        .usingSentinelKeyListForAdminKey()
                        .hasPrecheck(INVALID_ADMIN_KEY));
    }

    @HapiTest
    final HapiSpec rejectsMalformedScheduledTxnMemo() {
        return defaultHapiSpec("RejectsMalformedScheduledTxnMemo")
                .given(
                        cryptoCreate("ntb").memo(ZERO_BYTE_MEMO).hasPrecheck(INVALID_ZERO_BYTE_IN_STRING),
                        cryptoCreate(SENDER))
                .when()
                .then(
                        scheduleCreate(
                                        CREATION,
                                        cryptoTransfer(tinyBarsFromTo(SENDER, FUNDING, 1))
                                                .memo(nAscii(101)))
                                .hasPrecheck(MEMO_TOO_LONG),
                        scheduleCreate(
                                        "creationPartDeux",
                                        cryptoTransfer(tinyBarsFromTo(SENDER, FUNDING, 1))
                                                .memo("Here's s\u0000 to chew on!"))
                                .hasPrecheck(INVALID_ZERO_BYTE_IN_STRING));
    }

    @HapiTest
    final HapiSpec infoIncludesTxnIdFromCreationReceipt() {
        return defaultHapiSpec("InfoIncludesTxnIdFromCreationReceipt")
                .given(
                        overriding(SCHEDULING_WHITELIST, "CryptoTransfer,CryptoCreate"),
                        cryptoCreate(SENDER),
                        scheduleCreate(CREATION, cryptoTransfer(tinyBarsFromTo(SENDER, FUNDING, 1)))
                                .savingExpectedScheduledTxnId())
                .when()
                .then(getScheduleInfo(CREATION)
                        .hasScheduleId(CREATION)
                        .hasScheduledTxnIdSavedBy(CREATION)
                        .logged());
    }

    @HapiTest
    final HapiSpec preservesRevocationServiceSemanticsForFileDelete() {
        KeyShape waclShape = listOf(SIMPLE, threshOf(2, 3));
        SigControl adequateSigs = waclShape.signedWith(sigs(OFF, sigs(ON, ON, OFF)));
        SigControl inadequateSigs = waclShape.signedWith(sigs(OFF, sigs(ON, OFF, OFF)));
        SigControl compensatorySigs = waclShape.signedWith(sigs(OFF, sigs(OFF, OFF, ON)));

        String shouldBeInstaDeleted = "tbd";
        String shouldBeDeletedEventually = "tbdl";

        return defaultHapiSpec("PreservesRevocationServiceSemanticsForFileDelete")
                .given(
                        overriding(SCHEDULING_WHITELIST, "FileDelete"),
                        fileCreate(shouldBeInstaDeleted).waclShape(waclShape),
                        fileCreate(shouldBeDeletedEventually).waclShape(waclShape))
                .when(
                        scheduleCreate("validRevocation", fileDelete(shouldBeInstaDeleted))
                                .alsoSigningWith(shouldBeInstaDeleted)
                                .sigControl(forKey(shouldBeInstaDeleted, adequateSigs)),
                        sleepFor(1_000L),
                        getFileInfo(shouldBeInstaDeleted).hasDeleted(true))
                .then(
                        scheduleCreate("notYetValidRevocation", fileDelete(shouldBeDeletedEventually))
                                .alsoSigningWith(shouldBeDeletedEventually)
                                .sigControl(forKey(shouldBeDeletedEventually, inadequateSigs)),
                        getFileInfo(shouldBeDeletedEventually).hasDeleted(false),
                        scheduleSign("notYetValidRevocation")
                                .alsoSigningWith(shouldBeDeletedEventually)
                                .sigControl(forKey(shouldBeDeletedEventually, compensatorySigs)),
                        sleepFor(1_000L),
                        getFileInfo(shouldBeDeletedEventually).hasDeleted(true),
                        overriding(SCHEDULING_WHITELIST, WHITELIST_DEFAULT));
    }

    @HapiTest
    public HapiSpec detectsKeysChangedBetweenExpandSigsAndHandleTxn() {
        var keyGen = OverlappingKeyGenerator.withAtLeastOneOverlappingByte(2);
        String aKey = "a", bKey = "b";

        return defaultHapiSpec("DetectsKeysChangedBetweenExpandSigsAndHandleTxn")
                .given(newKeyNamed(aKey).generator(keyGen), newKeyNamed(bKey).generator(keyGen))
                .when(cryptoCreate(SENDER), cryptoCreate(RECEIVER).key(aKey).receiverSigRequired(true))
                .then(
                        cryptoUpdate(RECEIVER).key(bKey).deferStatusResolution(),
                        scheduleCreate(
                                        "outdatedXferSigs",
                                        cryptoTransfer(tinyBarsFromTo(SENDER, RECEIVER, 1))
                                                .fee(ONE_HBAR))
                                .alsoSigningWith(aKey)
                                /* In the rare, but possible, case that the overlapping byte shared by aKey
                                 * and bKey is _also_ shared by the DEFAULT_PAYER, the bKey prefix in the sig
                                 * map will probably not collide with aKey any more, and we will get
                                 * SUCCESS instead of SOME_SIGNATURES_WERE_INVALID.
                                 *
                                 * So we need this to stabilize CI. But if just testing locally, you may
                                 * only use .hasKnownStatus(SOME_SIGNATURES_WERE_INVALID) and it will pass
                                 * >99.99% of the time. */
                                .hasKnownStatusFrom(SOME_SIGNATURES_WERE_INVALID, SUCCESS));
    }

    @HapiTest
    public HapiSpec onlySchedulesWithMissingReqSimpleSigs() {
        return defaultHapiSpec("OnlySchedulesWithMissingReqSimpleSigs")
                .given(
                        overriding(SCHEDULING_WHITELIST, "CryptoTransfer,CryptoCreate"),
                        cryptoCreate(SENDER).balance(1L),
                        cryptoCreate(RECEIVER).balance(0L).receiverSigRequired(true))
                .when(scheduleCreate(BASIC_XFER, cryptoTransfer(tinyBarsFromTo(SENDER, RECEIVER, 1)))
                        .alsoSigningWith(SENDER))
                .then(getAccountBalance(SENDER).hasTinyBars(1L));
    }

    @HapiTest
    public HapiSpec requiresExtantPayer() {
        return defaultHapiSpec("RequiresExtantPayer")
                .given()
                .when()
                .then(scheduleCreate(
                                NEVER_TO_BE, cryptoCreate("nope").key(GENESIS).receiverSigRequired(true))
                        .designatingPayer(DESIGNATING_PAYER)
                        .hasKnownStatus(ACCOUNT_ID_DOES_NOT_EXIST));
    }

    @HapiTest
    public HapiSpec doesntTriggerUntilPayerSigns() {
        return defaultHapiSpec("DoesntTriggerUntilPayerSigns")
                .given(
                        cryptoCreate(PAYER).balance(ONE_HBAR * 5),
                        cryptoCreate(SENDER).balance(1L),
                        cryptoCreate(RECEIVER).receiverSigRequired(true).balance(0L))
                .when(scheduleCreate(
                                BASIC_XFER,
                                cryptoTransfer(tinyBarsFromTo(SENDER, RECEIVER, 1L))
                                        .fee(ONE_HBAR))
                        .designatingPayer(PAYER)
                        .alsoSigningWith(SENDER, RECEIVER)
                        .via(BASIC_XFER)
                        .recordingScheduledTxn())
                .then(
                        getScheduleInfo(BASIC_XFER).isNotExecuted(),
                        getAccountBalance(SENDER).hasTinyBars(1L),
                        getAccountBalance(RECEIVER).hasTinyBars(0L),
                        scheduleSign(BASIC_XFER).alsoSigningWith(PAYER).hasKnownStatus(SUCCESS),
                        getTxnRecord(BASIC_XFER).scheduled(),
                        getScheduleInfo(BASIC_XFER).isExecuted().hasRecordedScheduledTxn(),
                        // Very strange. HapiTest fails because We have a
                        // scheduled and executed record, but the balances did not change...
                        getAccountBalance(RECEIVER).hasTinyBars(1L),
                        getAccountBalance(SENDER).hasTinyBars(0L));
    }

    @HapiTest
    public HapiSpec triggersImmediatelyWithBothReqSimpleSigs() {
        long initialBalance = HapiSpecSetup.getDefaultInstance().defaultBalance();
        long transferAmount = 1;

        return defaultHapiSpec("TriggersImmediatelyWithBothReqSimpleSigs")
                .given(cryptoCreate(SENDER), cryptoCreate(RECEIVER).receiverSigRequired(true))
                .when(scheduleCreate(
                                BASIC_XFER,
                                cryptoTransfer(tinyBarsFromTo(SENDER, RECEIVER, transferAmount))
                                        .memo("Shocked, I tell you!"))
                        .alsoSigningWith(SENDER, RECEIVER)
                        .via(BASIC_XFER)
                        .recordingScheduledTxn())
                .then(
                        getTxnRecord(BASIC_XFER).scheduled(),
                        getScheduleInfo(BASIC_XFER).isExecuted().hasRecordedScheduledTxn(),
                        getAccountBalance(SENDER).hasTinyBars(initialBalance - transferAmount),
                        getAccountBalance(RECEIVER).hasTinyBars(initialBalance + transferAmount));
    }

    @HapiTest
    public HapiSpec rejectsUnresolvableReqSigners() {
        return defaultHapiSpec("RejectsUnresolvableReqSigners")
                .given()
                .when()
                .then(scheduleCreate(
                                "xferWithImaginaryAccount",
                                cryptoTransfer(
                                        tinyBarsFromTo(DEFAULT_PAYER, FUNDING, 1),
                                        tinyBarsFromTo(DESIGNATING_PAYER, FUNDING, 1)))
                        .hasKnownStatus(UNRESOLVABLE_REQUIRED_SIGNERS));
    }

    @HapiTest
    public HapiSpec rejectsFunctionlessTxn() {
        return defaultHapiSpec("RejectsFunctionlessTxn")
                .given()
                .when()
                .then(scheduleCreateFunctionless("unknown")
                        .hasKnownStatus(SCHEDULED_TRANSACTION_NOT_IN_WHITELIST)
                        .payingWith(GENESIS));
    }

    @HapiTest
    public HapiSpec functionlessTxnBusyWithNonExemptPayer() {
        return defaultHapiSpec("FunctionlessTxnBusyWithNonExemptPayer")
                .given()
                .when()
                .then(
                        cryptoCreate(SENDER),
                        scheduleCreateFunctionless("unknown").hasPrecheck(BUSY).payingWith(SENDER));
    }

    @HapiTest
    public HapiSpec whitelistWorks() {
        return defaultHapiSpec("whitelistWorks")
                .given(scheduleCreate("nope", createTopic(NEVER_TO_BE))
                        .hasKnownStatus(SCHEDULED_TRANSACTION_NOT_IN_WHITELIST))
                .when(
                        overriding(SCHEDULING_WHITELIST, "ConsensusCreateTopic"),
                        scheduleCreate("ok", createTopic(NEVER_TO_BE))
                                // prevent multiple runs of this test causing duplicates
                                .withEntityMemo("" + new SecureRandom().nextLong()))
                .then(overriding(SCHEDULING_WHITELIST, WHITELIST_DEFAULT));
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}
