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

import static com.hedera.services.bdd.junit.RepeatableReason.NEEDS_LAST_ASSIGNED_CONSENSUS_TIME;
import static com.hedera.services.bdd.junit.TestTags.NOT_REPEATABLE;
import static com.hedera.services.bdd.spec.HapiSpec.customHapiSpec;
import static com.hedera.services.bdd.spec.HapiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.HapiSpec.hapiTest;
import static com.hedera.services.bdd.spec.assertions.AccountInfoAsserts.accountWith;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAliasedAccountInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getReceipt;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getScheduleInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnUtils.nAscii;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.createTopic;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.scheduleCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.scheduleCreateFunctionless;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.scheduleSign;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromTo;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromToWithAlias;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.doingContextual;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.overriding;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.overridingTwo;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sourcing;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.submitModified;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.spec.utilops.mod.ModificationUtils.withSuccessivelyVariedBodyIds;
import static com.hedera.services.bdd.suites.HapiSuite.CIVILIAN_PAYER;
import static com.hedera.services.bdd.suites.HapiSuite.DEFAULT_PAYER;
import static com.hedera.services.bdd.suites.HapiSuite.FUNDING;
import static com.hedera.services.bdd.suites.HapiSuite.GENESIS;
import static com.hedera.services.bdd.suites.HapiSuite.ONE_HBAR;
import static com.hedera.services.bdd.suites.HapiSuite.ONE_HUNDRED_HBARS;
import static com.hedera.services.bdd.suites.HapiSuite.ZERO_BYTE_MEMO;
import static com.hedera.services.bdd.suites.crypto.AutoAccountUpdateSuite.ALIAS;
import static com.hedera.services.bdd.suites.crypto.AutoAccountUpdateSuite.INITIAL_BALANCE;
import static com.hedera.services.bdd.suites.crypto.AutoCreateUtils.updateSpecFor;
import static com.hedera.services.bdd.suites.schedule.ScheduleUtils.ADMIN;
import static com.hedera.services.bdd.suites.schedule.ScheduleUtils.BASIC_XFER;
import static com.hedera.services.bdd.suites.schedule.ScheduleUtils.CONTINUE;
import static com.hedera.services.bdd.suites.schedule.ScheduleUtils.COPYCAT;
import static com.hedera.services.bdd.suites.schedule.ScheduleUtils.CREATION;
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
import static com.hedera.services.bdd.suites.schedule.ScheduleUtils.SECOND_PAYER;
import static com.hedera.services.bdd.suites.schedule.ScheduleUtils.SENDER;
import static com.hedera.services.bdd.suites.schedule.ScheduleUtils.VALID_SCHEDULE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_ID_DOES_NOT_EXIST;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.BUSY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.IDENTICAL_SCHEDULE_ALREADY_CREATED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ADMIN_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SCHEDULE_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ZERO_BYTE_IN_STRING;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.MEMO_TOO_LONG;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.PAYER_ACCOUNT_NOT_FOUND;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SCHEDULED_TRANSACTION_NOT_IN_WHITELIST;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SOME_SIGNATURES_WERE_INVALID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.UNRESOLVABLE_REQUIRED_SIGNERS;

import com.hedera.services.bdd.junit.HapiTest;
import com.hedera.services.bdd.junit.LeakyHapiTest;
import com.hedera.services.bdd.junit.LeakyRepeatableHapiTest;
import com.hedera.services.bdd.spec.HapiSpecSetup;
import com.hedera.services.bdd.spec.keys.OverlappingKeyGenerator;
import java.security.SecureRandom;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Tag;

public class ScheduleCreateTest {
    @LeakyRepeatableHapiTest(
            value = NEEDS_LAST_ASSIGNED_CONSENSUS_TIME,
            overrides = {"scheduling.longTermEnabled", "scheduling.maxTxnPerSec"})
    final Stream<DynamicTest> cannotScheduleTooManyTxnsInOneSecond() {
        final long lifetime = 123_456L;
        final AtomicLong expiry = new AtomicLong();
        return hapiTest(
                overridingTwo("scheduling.longTermEnabled", "true", "scheduling.maxTxnPerSec", "2"),
                cryptoCreate(CIVILIAN_PAYER).balance(10 * ONE_HUNDRED_HBARS),
                scheduleCreate("first", cryptoTransfer(tinyBarsFromTo(DEFAULT_PAYER, FUNDING, 123L)))
                        .payingWith(CIVILIAN_PAYER)
                        .fee(ONE_HBAR)
                        .expiringIn(lifetime),
                // Consensus time advances exactly one second per transaction in repeatable mode
                doingContextual(spec -> expiry.set(spec.consensusTime().getEpochSecond() + lifetime - 1)),
                sourcing(() -> scheduleCreate("second", cryptoTransfer(tinyBarsFromTo(DEFAULT_PAYER, FUNDING, 456L)))
                        .payingWith(CIVILIAN_PAYER)
                        .fee(ONE_HBAR)
                        .expiringAt(expiry.get())),
                sourcing(() -> scheduleCreate("third", cryptoTransfer(tinyBarsFromTo(DEFAULT_PAYER, FUNDING, 789)))
                        .payingWith(CIVILIAN_PAYER)
                        .fee(ONE_HBAR)
                        .expiringAt(expiry.get())
                        .hasPrecheck(BUSY)));
    }

    @HapiTest
    final Stream<DynamicTest> aliasNotAllowedAsPayer() {
        return defaultHapiSpec("BodyAndPayerCreation")
                .given(
                        newKeyNamed(ALIAS),
                        cryptoCreate(PAYER).balance(INITIAL_BALANCE * ONE_HBAR),
                        cryptoTransfer(tinyBarsFromToWithAlias(PAYER, ALIAS, 2 * ONE_HUNDRED_HBARS)),
                        withOpContext((spec, opLog) -> updateSpecFor(spec, ALIAS)),
                        getAliasedAccountInfo(ALIAS)
                                .has(accountWith().expectedBalanceWithChargedUsd((2 * ONE_HUNDRED_HBARS), 0, 0)))
                .when(
                        scheduleCreate(
                                        ONLY_BODY_AND_PAYER,
                                        cryptoTransfer(tinyBarsFromTo(PAYER, GENESIS, 1))
                                                .memo("SURPRISE!!!"))
                                .recordingScheduledTxn()
                                // prevent multiple runs of this test causing duplicates
                                .withEntityMemo("" + new SecureRandom().nextLong())
                                .designatingPayer(PAYER)
                                .payingWithAliased(PAYER)
                                .hasPrecheck(PAYER_ACCOUNT_NOT_FOUND),
                        scheduleCreate(
                                        ONLY_BODY_AND_PAYER,
                                        cryptoTransfer(tinyBarsFromTo(PAYER, GENESIS, 1))
                                                .memo("SURPRISE!!!"))
                                .recordingScheduledTxn()
                                // prevent multiple runs of this test causing duplicates
                                .withEntityMemo("" + new SecureRandom().nextLong())
                                .designatingPayer(PAYER)
                                .payingWith(PAYER))
                .then(getScheduleInfo(ONLY_BODY_AND_PAYER)
                        .hasScheduleId(ONLY_BODY_AND_PAYER)
                        .hasPayerAccountID(PAYER)
                        .hasRecordedScheduledTxn());
    }

    @HapiTest
    final Stream<DynamicTest> worksAsExpectedWithDefaultScheduleId() {
        return defaultHapiSpec("WorksAsExpectedWithDefaultScheduleId")
                .given()
                .when()
                .then(getScheduleInfo("0.0.0").hasCostAnswerPrecheck(INVALID_SCHEDULE_ID));
    }

    @HapiTest
    final Stream<DynamicTest> bodyOnlyCreation() {
        return customHapiSpec("bodyOnlyCreation")
                .withProperties(Map.of("default.keyAlgorithm", "SECP256K1"))
                .given(cryptoCreate(SENDER))
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
    final Stream<DynamicTest> validateSignersInInfo() {
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
                        .hasSignatories(DEFAULT_PAYER, SENDER));
    }

    @HapiTest
    final Stream<DynamicTest> onlyBodyAndAdminCreation() {
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
    final Stream<DynamicTest> onlyBodyAndMemoCreation() {
        return defaultHapiSpec("OnlyBodyAndMemoCreation")
                .given(cryptoCreate(SENDER))
                .when(scheduleCreate(ONLY_BODY_AND_MEMO, cryptoTransfer(tinyBarsFromTo(SENDER, GENESIS, 1)))
                        .recordingScheduledTxn()
                        .withEntityMemo("sample memo"))
                .then(getScheduleInfo(ONLY_BODY_AND_MEMO)
                        .hasScheduleId(ONLY_BODY_AND_MEMO)
                        .hasEntityMemo("sample memo")
                        .hasRecordedScheduledTxn());
    }

    @HapiTest
    final Stream<DynamicTest> idVariantsTreatedAsExpected() {
        return defaultHapiSpec("idVariantsTreatedAsExpected")
                .given(cryptoCreate(PAYER))
                .when()
                .then(submitModified(withSuccessivelyVariedBodyIds(), () -> scheduleCreate(
                                ONLY_BODY_AND_PAYER, cryptoTransfer(tinyBarsFromTo(PAYER, GENESIS, 1)))
                        .withEntityMemo("" + new SecureRandom().nextLong())
                        .designatingPayer(PAYER)));
    }

    @HapiTest
    final Stream<DynamicTest> bodyAndPayerCreation() {
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
    final Stream<DynamicTest> bodyAndSignatoriesCreation() {
        var scheduleName = "onlyBodyAndSignatories";
        var scheduledTxn = cryptoTransfer(tinyBarsFromTo(SENDER, RECEIVER, 1));

        return defaultHapiSpec("BodyAndSignatoriesCreation")
                .given(
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
    final Stream<DynamicTest> failsWithNonExistingPayerAccountId() {
        return defaultHapiSpec("FailsWithNonExistingPayerAccountId")
                .given()
                .when(scheduleCreate("invalidPayer", cryptoCreate("secondary"))
                        .designatingPayer(DESIGNATING_PAYER)
                        .hasKnownStatus(ACCOUNT_ID_DOES_NOT_EXIST))
                .then();
    }

    @HapiTest
    final Stream<DynamicTest> failsWithTooLongMemo() {
        return defaultHapiSpec("FailsWithTooLongMemo")
                .given()
                .when(scheduleCreate("invalidMemo", cryptoCreate("secondary"))
                        .withEntityMemo(nAscii(101))
                        .hasPrecheck(MEMO_TOO_LONG))
                .then();
    }

    @HapiTest
    final Stream<DynamicTest> notIdenticalScheduleIfScheduledTxnChanges() {
        return defaultHapiSpec("NotIdenticalScheduleIfScheduledTxnChanges")
                .given(
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
    final Stream<DynamicTest> notIdenticalScheduleIfMemoChanges() {
        return defaultHapiSpec("NotIdenticalScheduleIfMemoChanges")
                .given(
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
    final Stream<DynamicTest> notIdenticalScheduleIfAdminKeyChanges() {
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
    final Stream<DynamicTest> recognizesIdenticalScheduleEvenWithDifferentDesignatedPayer() {
        return defaultHapiSpec("recognizesIdenticalScheduleEvenWithDifferentDesignatedPayer")
                .given(
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
    final Stream<DynamicTest> rejectsSentinelKeyListAsAdminKey() {
        return defaultHapiSpec("RejectsSentinelKeyListAsAdminKey")
                .given()
                .when()
                .then(scheduleCreate(CREATION, cryptoTransfer(tinyBarsFromTo(GENESIS, FUNDING, 1)))
                        .usingSentinelKeyListForAdminKey()
                        .hasPrecheck(INVALID_ADMIN_KEY));
    }

    @HapiTest
    final Stream<DynamicTest> rejectsMalformedScheduledTxnMemo() {
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
    final Stream<DynamicTest> infoIncludesTxnIdFromCreationReceipt() {
        return defaultHapiSpec("InfoIncludesTxnIdFromCreationReceipt")
                .given(
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
    @Tag(NOT_REPEATABLE)
    final Stream<DynamicTest> detectsKeysChangedBetweenExpandSigsAndHandleTxn() {
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
    final Stream<DynamicTest> onlySchedulesWithMissingReqSimpleSigs() {
        return defaultHapiSpec("OnlySchedulesWithMissingReqSimpleSigs")
                .given(
                        cryptoCreate(SENDER).balance(1L),
                        cryptoCreate(RECEIVER).balance(0L).receiverSigRequired(true))
                .when(scheduleCreate(BASIC_XFER, cryptoTransfer(tinyBarsFromTo(SENDER, RECEIVER, 1)))
                        .alsoSigningWith(SENDER))
                .then(getAccountBalance(SENDER).hasTinyBars(1L));
    }

    @HapiTest
    final Stream<DynamicTest> requiresExtantPayer() {
        return defaultHapiSpec("RequiresExtantPayer")
                .given()
                .when()
                .then(scheduleCreate(
                                NEVER_TO_BE, cryptoCreate("nope").key(GENESIS).receiverSigRequired(true))
                        .designatingPayer(DESIGNATING_PAYER)
                        .hasKnownStatus(ACCOUNT_ID_DOES_NOT_EXIST));
    }

    @HapiTest
    final Stream<DynamicTest> doesntTriggerUntilPayerSigns() {
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
    final Stream<DynamicTest> triggersImmediatelyWithBothReqSimpleSigs() {
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
    final Stream<DynamicTest> rejectsUnresolvableReqSigners() {
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
    final Stream<DynamicTest> rejectsFunctionlessTxn() {
        return defaultHapiSpec("RejectsFunctionlessTxn")
                .given()
                .when()
                .then(scheduleCreateFunctionless("unknown")
                        .hasKnownStatus(SCHEDULED_TRANSACTION_NOT_IN_WHITELIST)
                        .payingWith(GENESIS));
    }

    @HapiTest
    final Stream<DynamicTest> functionlessTxnBusyWithNonExemptPayer() {
        return defaultHapiSpec("FunctionlessTxnBusyWithNonExemptPayer")
                .given()
                .when()
                .then(
                        cryptoCreate(SENDER),
                        scheduleCreateFunctionless("unknown").hasPrecheck(BUSY).payingWith(SENDER));
    }

    @LeakyHapiTest(overrides = {"scheduling.whitelist"})
    final Stream<DynamicTest> whitelistWorks() {
        return hapiTest(
                scheduleCreate("nope", createTopic(NEVER_TO_BE)).hasKnownStatus(SCHEDULED_TRANSACTION_NOT_IN_WHITELIST),
                overriding("scheduling.whitelist", "ConsensusCreateTopic"),
                scheduleCreate("ok", createTopic(NEVER_TO_BE))
                        // prevent multiple runs of this test causing duplicates
                        .withEntityMemo("" + new SecureRandom().nextLong()));
    }
}
