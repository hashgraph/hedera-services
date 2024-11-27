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

package com.hedera.services.bdd.suites.schedule;

import static com.hedera.services.bdd.junit.ContextRequirement.FEE_SCHEDULE_OVERRIDES;
import static com.hedera.services.bdd.spec.HapiSpec.hapiTest;
import static com.hedera.services.bdd.spec.keys.ControlForKey.forKey;
import static com.hedera.services.bdd.spec.keys.KeyShape.SIMPLE;
import static com.hedera.services.bdd.spec.keys.KeyShape.listOf;
import static com.hedera.services.bdd.spec.keys.KeyShape.sigs;
import static com.hedera.services.bdd.spec.keys.KeyShape.threshOf;
import static com.hedera.services.bdd.spec.keys.SigControl.OFF;
import static com.hedera.services.bdd.spec.keys.SigControl.ON;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getFileInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getScheduleInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnUtils.randomUppercase;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileDelete;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.scheduleCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.scheduleDelete;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.scheduleSign;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.systemFileDelete;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.uploadInitCode;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromTo;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.doAdhoc;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.doWithStartupConfig;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sleepFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sourcing;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.uploadScheduledContractPrices;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.validateChargedUsdWithin;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.suites.HapiSuite.FREEZE_ADMIN;
import static com.hedera.services.bdd.suites.HapiSuite.GENESIS;
import static com.hedera.services.bdd.suites.HapiSuite.ONE_HBAR;
import static com.hedera.services.bdd.suites.HapiSuite.SOFTWARE_UPDATE_ADMIN;
import static com.hedera.services.bdd.suites.HapiSuite.SYSTEM_DELETE_ADMIN;
import static com.hedera.services.bdd.suites.HapiSuite.THREE_MONTHS_IN_SECONDS;
import static com.hedera.services.bdd.suites.freeze.UpgradeSuite.standardUpdateFile;
import static com.hedera.services.bdd.suites.schedule.ScheduleUtils.A_SCHEDULE;
import static com.hedera.services.bdd.suites.schedule.ScheduleUtils.ORIG_FILE;
import static com.hedera.services.bdd.suites.schedule.ScheduleUtils.OTHER_PAYER;
import static com.hedera.services.bdd.suites.schedule.ScheduleUtils.PAYING_ACCOUNT;
import static com.hedera.services.bdd.suites.schedule.ScheduleUtils.PAYING_ACCOUNT_2;
import static com.hedera.services.bdd.suites.schedule.ScheduleUtils.PAYING_SENDER;
import static com.hedera.services.bdd.suites.schedule.ScheduleUtils.RECEIVER;
import static com.hedera.services.bdd.suites.schedule.ScheduleUtils.SCHEDULED_TRANSACTION_MUST_SUCCEED;
import static com.hedera.services.bdd.suites.schedule.ScheduleUtils.SHARED_KEY;
import static com.hedera.services.bdd.suites.schedule.ScheduleUtils.SIGN_TX;
import static com.hedera.services.bdd.suites.schedule.ScheduleUtils.SIMPLE_UPDATE;
import static com.hedera.services.bdd.suites.schedule.ScheduleUtils.SOMEBODY;
import static com.hedera.services.bdd.suites.schedule.ScheduleUtils.SUCCESS_TXN;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.AUTHORIZATION_FAILED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_FILE_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NOT_SUPPORTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SCHEDULE_ALREADY_EXECUTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.hedera.services.bdd.junit.HapiTest;
import com.hedera.services.bdd.junit.HapiTestLifecycle;
import com.hedera.services.bdd.junit.LeakyHapiTest;
import com.hedera.services.bdd.junit.support.TestLifecycle;
import com.hedera.services.bdd.spec.keys.KeyShape;
import com.hedera.services.bdd.spec.keys.SigControl;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.math.BigInteger;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DynamicTest;

/**
 * Includes tests covering scheduled execution of operations that are not yet allowed to be scheduled, but will be.
 */
@HapiTestLifecycle
public class FutureSchedulableOpsTest {
    @BeforeAll
    static void beforeAll(@NonNull final TestLifecycle testLifecycle) {
        testLifecycle.overrideInClass(Map.of(
                "scheduling.whitelist", "ContractCall,CryptoCreate,CryptoTransfer,FileDelete,FileUpdate,SystemDelete"));
    }

    @LeakyHapiTest(requirement = FEE_SCHEDULE_OVERRIDES)
    final Stream<DynamicTest> canonicalScheduleOpsHaveExpectedUsdFees() {
        return hapiTest(
                uploadScheduledContractPrices(GENESIS),
                uploadInitCode(SIMPLE_UPDATE),
                cryptoCreate(OTHER_PAYER),
                cryptoCreate(PAYING_SENDER),
                cryptoCreate(RECEIVER).receiverSigRequired(true),
                contractCreate(SIMPLE_UPDATE).gas(300_000L),
                scheduleCreate(
                                "canonical",
                                cryptoTransfer(tinyBarsFromTo(PAYING_SENDER, RECEIVER, 1L))
                                        .memo("")
                                        .fee(ONE_HBAR))
                        .payingWith(OTHER_PAYER)
                        .via("canonicalCreation")
                        .alsoSigningWith(PAYING_SENDER)
                        .adminKey(OTHER_PAYER),
                scheduleSign("canonical")
                        .via("canonicalSigning")
                        .payingWith(PAYING_SENDER)
                        .alsoSigningWith(RECEIVER),
                scheduleCreate(
                                "tbd",
                                cryptoTransfer(tinyBarsFromTo(PAYING_SENDER, RECEIVER, 1L))
                                        .memo("")
                                        .fee(ONE_HBAR))
                        .payingWith(PAYING_SENDER)
                        .adminKey(PAYING_SENDER),
                scheduleDelete("tbd").via("canonicalDeletion").payingWith(PAYING_SENDER),
                scheduleCreate(
                                "contractCall",
                                contractCall(SIMPLE_UPDATE, "set", BigInteger.valueOf(5), BigInteger.valueOf(42))
                                        .gas(24_000)
                                        .memo("")
                                        .fee(ONE_HBAR))
                        .payingWith(OTHER_PAYER)
                        .via("canonicalContractCall")
                        .adminKey(OTHER_PAYER),
                validateChargedUsdWithin("canonicalCreation", 0.01, 3.0),
                validateChargedUsdWithin("canonicalSigning", 0.001, 3.0),
                validateChargedUsdWithin("canonicalDeletion", 0.001, 3.0),
                validateChargedUsdWithin("canonicalContractCall", 0.1, 3.0));
    }

    @HapiTest
    final Stream<DynamicTest> scheduledPermissionedFileUpdateWorksAsExpected() {
        return hapiTest(
                cryptoCreate(PAYING_ACCOUNT),
                scheduleCreate(A_SCHEDULE, fileUpdate(standardUpdateFile).contents("fooo!"))
                        .withEntityMemo(randomUppercase(100))
                        .designatingPayer(SOFTWARE_UPDATE_ADMIN)
                        .payingWith(PAYING_ACCOUNT)
                        .via(SUCCESS_TXN),
                scheduleSign(A_SCHEDULE)
                        .alsoSigningWith(SOFTWARE_UPDATE_ADMIN)
                        .payingWith(PAYING_ACCOUNT)
                        .via(SIGN_TX)
                        .hasKnownStatus(SUCCESS),
                getScheduleInfo(A_SCHEDULE).isExecuted(),
                withOpContext((spec, opLog) -> {
                    var triggeredTx = getTxnRecord(SUCCESS_TXN).scheduled();
                    allRunFor(spec, triggeredTx);

                    assertEquals(
                            SUCCESS,
                            triggeredTx.getResponseRecord().getReceipt().getStatus(),
                            SCHEDULED_TRANSACTION_MUST_SUCCEED);
                }));
    }

    @HapiTest
    final Stream<DynamicTest> scheduledPermissionedFileUpdateUnauthorizedPayerFails() {
        return hapiTest(
                cryptoCreate(PAYING_ACCOUNT),
                cryptoCreate(PAYING_ACCOUNT_2),
                scheduleCreate(A_SCHEDULE, fileUpdate("0.0.150").contents("fooo!"))
                        .withEntityMemo(randomUppercase(100))
                        .designatingPayer(PAYING_ACCOUNT_2)
                        .payingWith(PAYING_ACCOUNT)
                        .via(SUCCESS_TXN),
                scheduleSign(A_SCHEDULE)
                        .alsoSigningWith(PAYING_ACCOUNT_2, FREEZE_ADMIN)
                        .payingWith(PAYING_ACCOUNT)
                        .via(SIGN_TX)
                        .hasKnownStatus(SUCCESS),
                getScheduleInfo(A_SCHEDULE).isExecuted(),
                withOpContext((spec, opLog) -> {
                    var triggeredTx = getTxnRecord(SUCCESS_TXN).scheduled();
                    allRunFor(spec, triggeredTx);

                    assertEquals(
                            AUTHORIZATION_FAILED,
                            triggeredTx.getResponseRecord().getReceipt().getStatus(),
                            "Scheduled transaction be AUTHORIZATION_FAILED!");
                }));
    }

    @HapiTest
    final Stream<DynamicTest> scheduledSystemDeleteWorksAsExpected() {
        return hapiTest(
                cryptoCreate(PAYING_ACCOUNT),
                fileCreate("misc").lifetime(THREE_MONTHS_IN_SECONDS).contents(ORIG_FILE),
                scheduleCreate(A_SCHEDULE, systemFileDelete("misc").updatingExpiry(1L))
                        .withEntityMemo(randomUppercase(100))
                        .designatingPayer(SYSTEM_DELETE_ADMIN)
                        .payingWith(PAYING_ACCOUNT)
                        .via(SUCCESS_TXN),
                scheduleSign(A_SCHEDULE)
                        .alsoSigningWith(SYSTEM_DELETE_ADMIN)
                        .payingWith(PAYING_ACCOUNT)
                        .via(SIGN_TX)
                        .hasKnownStatus(SUCCESS),
                getScheduleInfo(A_SCHEDULE).isExecuted(),
                getFileInfo("misc").nodePayment(1_234L).hasAnswerOnlyPrecheck(INVALID_FILE_ID),
                withOpContext((spec, opLog) -> {
                    var triggeredTx = getTxnRecord(SUCCESS_TXN).scheduled();
                    allRunFor(spec, triggeredTx);

                    assertEquals(
                            SUCCESS,
                            triggeredTx.getResponseRecord().getReceipt().getStatus(),
                            SCHEDULED_TRANSACTION_MUST_SUCCEED);
                }));
    }

    @HapiTest
    final Stream<DynamicTest> hapiTestScheduledSystemDeleteUnauthorizedPayerFails() {
        final AtomicReference<String> unprivilegedThrottleExemptPayerId = new AtomicReference<>();
        return hapiTest(
                doWithStartupConfig(
                        "accounts.lastThrottleExempt",
                        value -> doAdhoc(() -> unprivilegedThrottleExemptPayerId.set("0.0." + value))),
                cryptoCreate(PAYING_ACCOUNT),
                fileCreate("misc").lifetime(THREE_MONTHS_IN_SECONDS).contents(ORIG_FILE),
                sourcing(() -> scheduleCreate(
                                A_SCHEDULE, systemFileDelete("misc").updatingExpiry(1L))
                        .withEntityMemo(randomUppercase(100))
                        .designatingPayer(unprivilegedThrottleExemptPayerId.get())
                        .payingWith(PAYING_ACCOUNT)
                        .via(SUCCESS_TXN)),
                scheduleSign(A_SCHEDULE)
                        .alsoSigningWith(GENESIS)
                        .payingWith(PAYING_ACCOUNT)
                        .via(SIGN_TX)
                        .hasKnownStatus(SUCCESS),
                getScheduleInfo(A_SCHEDULE).isExecuted(),
                getFileInfo("misc").nodePayment(1_234L),
                withOpContext((spec, opLog) -> {
                    var triggeredTx = getTxnRecord(SUCCESS_TXN).scheduled();
                    allRunFor(spec, triggeredTx);
                    assertEquals(
                            NOT_SUPPORTED,
                            triggeredTx.getResponseRecord().getReceipt().getStatus(),
                            "Scheduled transaction be NOT_SUPPORTED!");
                }));
    }

    @HapiTest
    final Stream<DynamicTest> preservesRevocationServiceSemanticsForFileDelete() {
        KeyShape waclShape = listOf(SIMPLE, threshOf(2, 3));
        SigControl adequateSigs = waclShape.signedWith(sigs(OFF, sigs(ON, ON, OFF)));
        SigControl inadequateSigs = waclShape.signedWith(sigs(OFF, sigs(ON, OFF, OFF)));
        SigControl compensatorySigs = waclShape.signedWith(sigs(OFF, sigs(OFF, OFF, ON)));

        String shouldBeInstaDeleted = "tbd";
        String shouldBeDeletedEventually = "tbdl";

        return hapiTest(
                fileCreate(shouldBeInstaDeleted).waclShape(waclShape),
                fileCreate(shouldBeDeletedEventually).waclShape(waclShape),
                scheduleCreate("validRevocation", fileDelete(shouldBeInstaDeleted))
                        .alsoSigningWith(shouldBeInstaDeleted)
                        .sigControl(forKey(shouldBeInstaDeleted, adequateSigs)),
                sleepFor(1_000L),
                getFileInfo(shouldBeInstaDeleted).hasDeleted(true),
                scheduleCreate("notYetValidRevocation", fileDelete(shouldBeDeletedEventually))
                        .alsoSigningWith(shouldBeDeletedEventually)
                        .sigControl(forKey(shouldBeDeletedEventually, inadequateSigs)),
                getFileInfo(shouldBeDeletedEventually).hasDeleted(false),
                scheduleSign("notYetValidRevocation")
                        .alsoSigningWith(shouldBeDeletedEventually)
                        .sigControl(forKey(shouldBeDeletedEventually, compensatorySigs)),
                sleepFor(1_000L),
                getFileInfo(shouldBeDeletedEventually).hasDeleted(true));
    }

    @HapiTest
    final Stream<DynamicTest> addingSignaturesToExecutedTxFails() {
        var txnBody = cryptoCreate(SOMEBODY);
        var creation = "basicCryptoCreate";

        return hapiTest(
                cryptoCreate("somesigner"),
                scheduleCreate(creation, txnBody),
                getScheduleInfo(creation).isExecuted().logged(),
                scheduleSign(creation)
                        .via("signing")
                        .alsoSigningWith("somesigner")
                        .hasKnownStatus(SCHEDULE_ALREADY_EXECUTED));
    }

    @HapiTest
    final Stream<DynamicTest> sharedKeyWorksAsExpected() {
        return hapiTest(
                newKeyNamed(SHARED_KEY),
                cryptoCreate("payerWithSharedKey").key(SHARED_KEY),
                scheduleCreate(
                                "deferredCreation",
                                cryptoCreate("yetToBe")
                                        .signedBy()
                                        .receiverSigRequired(true)
                                        .key(SHARED_KEY)
                                        .balance(123L)
                                        .fee(ONE_HBAR))
                        .payingWith("payerWithSharedKey")
                        .via("creation"),
                getTxnRecord("creation").scheduled());
    }
}
