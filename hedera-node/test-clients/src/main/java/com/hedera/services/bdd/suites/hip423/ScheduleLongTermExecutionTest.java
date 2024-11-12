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
import static com.hedera.services.bdd.spec.HapiSpec.hapiTest;
import static com.hedera.services.bdd.spec.assertions.TransactionRecordAsserts.recordWith;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getFileInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getScheduleInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnUtils.asId;
import static com.hedera.services.bdd.spec.transactions.TxnUtils.randomUppercase;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoDelete;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.scheduleCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.scheduleSign;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.systemFileDelete;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.uploadInitCode;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromAccountToAlias;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromTo;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromToWithInvalidAmounts;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.freezeAbort;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.recordFeeAmount;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sleepFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.uploadScheduledContractPrices;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.suites.HapiSuite.DEFAULT_PAYER;
import static com.hedera.services.bdd.suites.HapiSuite.FREEZE_ADMIN;
import static com.hedera.services.bdd.suites.HapiSuite.GENESIS;
import static com.hedera.services.bdd.suites.HapiSuite.ONE_HBAR;
import static com.hedera.services.bdd.suites.HapiSuite.ONE_HUNDRED_HBARS;
import static com.hedera.services.bdd.suites.HapiSuite.SECP_256K1_SOURCE_KEY;
import static com.hedera.services.bdd.suites.HapiSuite.SYSTEM_ADMIN;
import static com.hedera.services.bdd.suites.HapiSuite.SYSTEM_DELETE_ADMIN;
import static com.hedera.services.bdd.suites.HapiSuite.THREE_MONTHS_IN_SECONDS;
import static com.hedera.services.bdd.suites.HapiSuite.flattened;
import static com.hedera.services.bdd.suites.freeze.UpgradeSuite.standardUpdateFile;
import static com.hedera.services.bdd.suites.hip423.LongTermScheduleUtils.ORIG_FILE;
import static com.hedera.services.bdd.suites.hip423.LongTermScheduleUtils.PAYING_ACCOUNT_2;
import static com.hedera.services.bdd.suites.hip423.LongTermScheduleUtils.SCHEDULED_TRANSACTION_MUST_NOT_SUCCEED;
import static com.hedera.services.bdd.suites.hip423.LongTermScheduleUtils.SCHEDULE_CREATE_FEE;
import static com.hedera.services.bdd.suites.hip423.LongTermScheduleUtils.SENDER_1;
import static com.hedera.services.bdd.suites.hip423.LongTermScheduleUtils.SENDER_2;
import static com.hedera.services.bdd.suites.hip423.LongTermScheduleUtils.SENDER_3;
import static com.hedera.services.bdd.suites.hip423.LongTermScheduleUtils.SIMPLE_UPDATE;
import static com.hedera.services.bdd.suites.hip423.LongTermScheduleUtils.SUCCESS_TXN;
import static com.hedera.services.bdd.suites.hip423.LongTermScheduleUtils.TRANSACTION_NOT_SCHEDULED;
import static com.hedera.services.bdd.suites.hip423.LongTermScheduleUtils.VALID_SCHEDULE;
import static com.hedera.services.bdd.suites.hip423.LongTermScheduleUtils.WEIRDLY_POPULAR_KEY;
import static com.hedera.services.bdd.suites.hip423.LongTermScheduleUtils.WRONG_CONSENSUS_TIMESTAMP;
import static com.hedera.services.bdd.suites.hip423.LongTermScheduleUtils.WRONG_RECORD_ACCOUNT_ID;
import static com.hedera.services.bdd.suites.hip423.LongTermScheduleUtils.WRONG_SCHEDULE_ID;
import static com.hedera.services.bdd.suites.hip423.LongTermScheduleUtils.WRONG_TRANSACTION_VALID_START;
import static com.hedera.services.bdd.suites.hip423.LongTermScheduleUtils.WRONG_TRANSFER_LIST;
import static com.hedera.services.bdd.suites.hip423.LongTermScheduleUtils.scheduleFakeUpgrade;
import static com.hedera.services.bdd.suites.hip423.LongTermScheduleUtils.transferListCheck;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_DELETED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.AUTHORIZATION_FAILED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_ACCOUNT_BALANCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_PAYER_BALANCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_AMOUNTS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_FILE_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_PAYER_SIGNATURE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SCHEDULE_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.PAYER_ACCOUNT_DELETED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;

import com.hedera.services.bdd.junit.HapiTest;
import com.hedera.services.bdd.junit.HapiTestLifecycle;
import com.hedera.services.bdd.junit.support.TestLifecycle;
import com.hedera.services.bdd.spec.queries.meta.HapiGetTxnRecord;
import com.hederahashgraph.api.proto.java.AccountID;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.math.BigInteger;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.TestMethodOrder;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@HapiTestLifecycle
public class ScheduleLongTermExecutionTest {
    private static final String PAYING_ACCOUNT = "payingAccount";
    private static final String RECEIVER = "receiver";
    private static final String SENDER = "sender";
    private static final String SENDER_TXN = "senderTxn";
    private static final String BASIC_XFER = "basicXfer";
    private static final String CREATE_TX = "createTxn";
    private static final String SIGN_TX = "sign_tx";
    private static final String TRIGGERING_TXN = "triggeringTxn";
    private static final String PAYING_ACCOUNT_TXN = "payingAccountTxn";
    private static final String LUCKY_RECEIVER = "luckyReceiver";
    private static final String FAILED_XFER = "failedXfer";
    private static final String WEIRDLY_POPULAR_KEY_TXN = "weirdlyPopularKeyTxn";
    private static final String PAYER_TXN = "payerTxn";

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

    @SuppressWarnings("java:S5960")
    @HapiTest
    @Order(1)
    final Stream<DynamicTest> executionWithCustomPayerWorks() {
        return defaultHapiSpec("ExecutionAtExpiryWithCustomPayerWorks")
                .given(
                        cryptoCreate(PAYING_ACCOUNT),
                        cryptoCreate(RECEIVER),
                        cryptoCreate(SENDER).via(SENDER_TXN))
                .when(
                        scheduleCreate(BASIC_XFER, cryptoTransfer(tinyBarsFromTo(SENDER, RECEIVER, 1)))
                                .designatingPayer(PAYING_ACCOUNT)
                                .waitForExpiry()
                                .withRelativeExpiry(SENDER_TXN, 4)
                                .recordingScheduledTxn()
                                .via(CREATE_TX),
                        scheduleSign(BASIC_XFER)
                                .alsoSigningWith(SENDER, PAYING_ACCOUNT)
                                .via(SIGN_TX)
                                .hasKnownStatus(SUCCESS))
                .then(
                        getScheduleInfo(BASIC_XFER)
                                .hasScheduleId(BASIC_XFER)
                                .hasWaitForExpiry()
                                .isNotExecuted()
                                .isNotDeleted()
                                .hasRelativeExpiry(SENDER_TXN, 4)
                                .hasRecordedScheduledTxn(),
                        sleepFor(5000),
                        cryptoCreate("foo").via(TRIGGERING_TXN),
                        getScheduleInfo(BASIC_XFER).hasCostAnswerPrecheck(INVALID_SCHEDULE_ID),
                        withOpContext((spec, opLog) -> {
                            var createTx = getTxnRecord(CREATE_TX);
                            var signTx = getTxnRecord(SIGN_TX);
                            var triggeringTx = getTxnRecord(TRIGGERING_TXN);
                            var triggeredTx = getTxnRecord(CREATE_TX).scheduled();
                            allRunFor(spec, createTx, signTx, triggeredTx, triggeringTx);

                            Assertions.assertEquals(
                                    SUCCESS,
                                    triggeredTx.getResponseRecord().getReceipt().getStatus(),
                                    SCHEDULED_TRANSACTION_MUST_NOT_SUCCEED);

                            Instant triggerTime = Instant.ofEpochSecond(
                                    triggeringTx
                                            .getResponseRecord()
                                            .getConsensusTimestamp()
                                            .getSeconds(),
                                    triggeringTx
                                            .getResponseRecord()
                                            .getConsensusTimestamp()
                                            .getNanos());

                            Instant triggeredTime = Instant.ofEpochSecond(
                                    triggeredTx
                                            .getResponseRecord()
                                            .getConsensusTimestamp()
                                            .getSeconds(),
                                    triggeredTx
                                            .getResponseRecord()
                                            .getConsensusTimestamp()
                                            .getNanos());

                            Assertions.assertTrue(triggerTime.isBefore(triggeredTime), WRONG_CONSENSUS_TIMESTAMP);

                            Assertions.assertEquals(
                                    createTx.getResponseRecord()
                                            .getTransactionID()
                                            .getTransactionValidStart(),
                                    triggeredTx
                                            .getResponseRecord()
                                            .getTransactionID()
                                            .getTransactionValidStart(),
                                    WRONG_TRANSACTION_VALID_START);

                            Assertions.assertEquals(
                                    createTx.getResponseRecord()
                                            .getTransactionID()
                                            .getAccountID(),
                                    triggeredTx
                                            .getResponseRecord()
                                            .getTransactionID()
                                            .getAccountID(),
                                    WRONG_RECORD_ACCOUNT_ID);

                            Assertions.assertTrue(
                                    triggeredTx
                                            .getResponseRecord()
                                            .getTransactionID()
                                            .getScheduled(),
                                    TRANSACTION_NOT_SCHEDULED);

                            Assertions.assertEquals(
                                    createTx.getResponseRecord().getReceipt().getScheduleID(),
                                    triggeredTx.getResponseRecord().getScheduleRef(),
                                    WRONG_SCHEDULE_ID);

                            Assertions.assertTrue(
                                    transferListCheck(
                                            triggeredTx,
                                            asId(SENDER, spec),
                                            asId(RECEIVER, spec),
                                            asId(PAYING_ACCOUNT, spec),
                                            1L),
                                    WRONG_TRANSFER_LIST);
                        }));
    }

    @HapiTest
    @Order(2)
    final Stream<DynamicTest> executionWithCustomPayerAndAdminKeyWorks() {
        return defaultHapiSpec("ExecutionAtExpiryWithCustomPayerAndAdminKeyWorks")
                .given(
                        newKeyNamed("adminKey"),
                        cryptoCreate(PAYING_ACCOUNT),
                        cryptoCreate(RECEIVER),
                        cryptoCreate(SENDER).via(SENDER_TXN))
                .when(
                        scheduleCreate(BASIC_XFER, cryptoTransfer(tinyBarsFromTo(SENDER, RECEIVER, 1)))
                                .designatingPayer(PAYING_ACCOUNT)
                                .adminKey("adminKey")
                                .waitForExpiry()
                                .withRelativeExpiry(SENDER_TXN, 4)
                                .recordingScheduledTxn()
                                .via(CREATE_TX),
                        scheduleSign(BASIC_XFER)
                                .alsoSigningWith(SENDER, PAYING_ACCOUNT)
                                .via(SIGN_TX)
                                .hasKnownStatus(SUCCESS))
                .then(
                        getScheduleInfo(BASIC_XFER)
                                .hasScheduleId(BASIC_XFER)
                                .hasWaitForExpiry()
                                .isNotExecuted()
                                .isNotDeleted()
                                .hasRelativeExpiry(SENDER_TXN, 4)
                                .hasRecordedScheduledTxn(),
                        sleepFor(5000),
                        cryptoCreate("foo").via(TRIGGERING_TXN),
                        getScheduleInfo(BASIC_XFER).hasCostAnswerPrecheck(INVALID_SCHEDULE_ID),
                        withOpContext((spec, opLog) -> {
                            var createTx = getTxnRecord(CREATE_TX);
                            var signTx = getTxnRecord(SIGN_TX);
                            var triggeringTx = getTxnRecord(TRIGGERING_TXN);
                            var triggeredTx = getTxnRecord(CREATE_TX).scheduled();
                            allRunFor(spec, createTx, signTx, triggeredTx, triggeringTx);

                            Assertions.assertEquals(
                                    SUCCESS,
                                    triggeredTx.getResponseRecord().getReceipt().getStatus(),
                                    SCHEDULED_TRANSACTION_MUST_NOT_SUCCEED);

                            Instant triggerTime = Instant.ofEpochSecond(
                                    triggeringTx
                                            .getResponseRecord()
                                            .getConsensusTimestamp()
                                            .getSeconds(),
                                    triggeringTx
                                            .getResponseRecord()
                                            .getConsensusTimestamp()
                                            .getNanos());

                            Instant triggeredTime = Instant.ofEpochSecond(
                                    triggeredTx
                                            .getResponseRecord()
                                            .getConsensusTimestamp()
                                            .getSeconds(),
                                    triggeredTx
                                            .getResponseRecord()
                                            .getConsensusTimestamp()
                                            .getNanos());

                            Assertions.assertTrue(triggerTime.isBefore(triggeredTime), WRONG_CONSENSUS_TIMESTAMP);

                            Assertions.assertEquals(
                                    createTx.getResponseRecord()
                                            .getTransactionID()
                                            .getTransactionValidStart(),
                                    triggeredTx
                                            .getResponseRecord()
                                            .getTransactionID()
                                            .getTransactionValidStart(),
                                    WRONG_TRANSACTION_VALID_START);

                            Assertions.assertEquals(
                                    createTx.getResponseRecord()
                                            .getTransactionID()
                                            .getAccountID(),
                                    triggeredTx
                                            .getResponseRecord()
                                            .getTransactionID()
                                            .getAccountID(),
                                    WRONG_RECORD_ACCOUNT_ID);

                            Assertions.assertTrue(
                                    triggeredTx
                                            .getResponseRecord()
                                            .getTransactionID()
                                            .getScheduled(),
                                    TRANSACTION_NOT_SCHEDULED);

                            Assertions.assertEquals(
                                    createTx.getResponseRecord().getReceipt().getScheduleID(),
                                    triggeredTx.getResponseRecord().getScheduleRef(),
                                    WRONG_SCHEDULE_ID);

                            Assertions.assertTrue(
                                    transferListCheck(
                                            triggeredTx,
                                            asId(SENDER, spec),
                                            asId(RECEIVER, spec),
                                            asId(PAYING_ACCOUNT, spec),
                                            1L),
                                    WRONG_TRANSFER_LIST);
                        }));
    }

    @HapiTest
    @Order(3)
    final Stream<DynamicTest> executionWithCustomPayerWhoSignsAtCreationAsPayerWorks() {
        return defaultHapiSpec("ExecutionAtExpiryWithCustomPayerWhoSignsAtCreationAsPayerWorks")
                .given(
                        cryptoCreate(PAYING_ACCOUNT),
                        cryptoCreate(RECEIVER),
                        cryptoCreate(SENDER).via(SENDER_TXN))
                .when(
                        scheduleCreate(BASIC_XFER, cryptoTransfer(tinyBarsFromTo(SENDER, RECEIVER, 1)))
                                .payingWith(PAYING_ACCOUNT)
                                .designatingPayer(PAYING_ACCOUNT)
                                .waitForExpiry()
                                .withRelativeExpiry(SENDER_TXN, 4)
                                .recordingScheduledTxn()
                                .via(CREATE_TX),
                        scheduleSign(BASIC_XFER)
                                .alsoSigningWith(SENDER)
                                .via(SIGN_TX)
                                .hasKnownStatus(SUCCESS))
                .then(
                        getScheduleInfo(BASIC_XFER)
                                .hasScheduleId(BASIC_XFER)
                                .hasWaitForExpiry()
                                .isNotExecuted()
                                .isNotDeleted()
                                .hasRelativeExpiry(SENDER_TXN, 4)
                                .hasRecordedScheduledTxn(),
                        sleepFor(5000),
                        cryptoCreate("foo").via(TRIGGERING_TXN),
                        getScheduleInfo(BASIC_XFER).hasCostAnswerPrecheck(INVALID_SCHEDULE_ID),
                        withOpContext((spec, opLog) -> {
                            var createTx = getTxnRecord(CREATE_TX);
                            var signTx = getTxnRecord(SIGN_TX);
                            var triggeringTx = getTxnRecord(TRIGGERING_TXN);
                            var triggeredTx = getTxnRecord(CREATE_TX).scheduled();
                            allRunFor(spec, createTx, signTx, triggeredTx, triggeringTx);

                            Assertions.assertEquals(
                                    SUCCESS,
                                    triggeredTx.getResponseRecord().getReceipt().getStatus(),
                                    SCHEDULED_TRANSACTION_MUST_NOT_SUCCEED);

                            Instant triggerTime = Instant.ofEpochSecond(
                                    triggeringTx
                                            .getResponseRecord()
                                            .getConsensusTimestamp()
                                            .getSeconds(),
                                    triggeringTx
                                            .getResponseRecord()
                                            .getConsensusTimestamp()
                                            .getNanos());

                            Instant triggeredTime = Instant.ofEpochSecond(
                                    triggeredTx
                                            .getResponseRecord()
                                            .getConsensusTimestamp()
                                            .getSeconds(),
                                    triggeredTx
                                            .getResponseRecord()
                                            .getConsensusTimestamp()
                                            .getNanos());

                            Assertions.assertTrue(triggerTime.isBefore(triggeredTime), WRONG_CONSENSUS_TIMESTAMP);

                            Assertions.assertEquals(
                                    createTx.getResponseRecord()
                                            .getTransactionID()
                                            .getTransactionValidStart(),
                                    triggeredTx
                                            .getResponseRecord()
                                            .getTransactionID()
                                            .getTransactionValidStart(),
                                    WRONG_TRANSACTION_VALID_START);

                            Assertions.assertEquals(
                                    createTx.getResponseRecord()
                                            .getTransactionID()
                                            .getAccountID(),
                                    triggeredTx
                                            .getResponseRecord()
                                            .getTransactionID()
                                            .getAccountID(),
                                    WRONG_RECORD_ACCOUNT_ID);

                            Assertions.assertTrue(
                                    triggeredTx
                                            .getResponseRecord()
                                            .getTransactionID()
                                            .getScheduled(),
                                    TRANSACTION_NOT_SCHEDULED);

                            Assertions.assertEquals(
                                    createTx.getResponseRecord().getReceipt().getScheduleID(),
                                    triggeredTx.getResponseRecord().getScheduleRef(),
                                    WRONG_SCHEDULE_ID);

                            Assertions.assertTrue(
                                    transferListCheck(
                                            triggeredTx,
                                            asId(SENDER, spec),
                                            asId(RECEIVER, spec),
                                            asId(PAYING_ACCOUNT, spec),
                                            1L),
                                    WRONG_TRANSFER_LIST);
                        }));
    }

    @HapiTest
    @Order(4)
    public Stream<DynamicTest> executionWithDefaultPayerWorks() {
        long transferAmount = 1;
        return defaultHapiSpec("ExecutionAtExpiryWithDefaultPayerWorks")
                .given(
                        cryptoCreate(SENDER).via(SENDER_TXN),
                        cryptoCreate(RECEIVER),
                        cryptoCreate(PAYING_ACCOUNT),
                        scheduleCreate(BASIC_XFER, cryptoTransfer(tinyBarsFromTo(SENDER, RECEIVER, transferAmount)))
                                .waitForExpiry()
                                .withRelativeExpiry(SENDER_TXN, 4)
                                .payingWith(PAYING_ACCOUNT)
                                .recordingScheduledTxn()
                                .via(CREATE_TX))
                .when(scheduleSign(BASIC_XFER).alsoSigningWith(SENDER).via(SIGN_TX))
                .then(
                        getScheduleInfo(BASIC_XFER)
                                .hasScheduleId(BASIC_XFER)
                                .hasWaitForExpiry()
                                .isNotExecuted()
                                .isNotDeleted()
                                .hasRelativeExpiry(SENDER_TXN, 4)
                                .hasRecordedScheduledTxn(),
                        sleepFor(5000),
                        cryptoCreate("foo").via(TRIGGERING_TXN),
                        getScheduleInfo(BASIC_XFER).hasCostAnswerPrecheck(INVALID_SCHEDULE_ID),
                        withOpContext((spec, opLog) -> {
                            var createTx = getTxnRecord(CREATE_TX);
                            var signTx = getTxnRecord(SIGN_TX);
                            var triggeringTx = getTxnRecord(TRIGGERING_TXN);
                            var triggeredTx = getTxnRecord(CREATE_TX).scheduled();
                            allRunFor(spec, createTx, signTx, triggeredTx, triggeringTx);

                            Assertions.assertEquals(
                                    SUCCESS,
                                    triggeredTx.getResponseRecord().getReceipt().getStatus(),
                                    SCHEDULED_TRANSACTION_MUST_NOT_SUCCEED);

                            Instant triggerTime = Instant.ofEpochSecond(
                                    triggeringTx
                                            .getResponseRecord()
                                            .getConsensusTimestamp()
                                            .getSeconds(),
                                    triggeringTx
                                            .getResponseRecord()
                                            .getConsensusTimestamp()
                                            .getNanos());

                            Instant triggeredTime = Instant.ofEpochSecond(
                                    triggeredTx
                                            .getResponseRecord()
                                            .getConsensusTimestamp()
                                            .getSeconds(),
                                    triggeredTx
                                            .getResponseRecord()
                                            .getConsensusTimestamp()
                                            .getNanos());

                            Assertions.assertTrue(triggerTime.isBefore(triggeredTime), WRONG_CONSENSUS_TIMESTAMP);

                            Assertions.assertEquals(
                                    createTx.getResponseRecord()
                                            .getTransactionID()
                                            .getTransactionValidStart(),
                                    triggeredTx
                                            .getResponseRecord()
                                            .getTransactionID()
                                            .getTransactionValidStart(),
                                    WRONG_TRANSACTION_VALID_START);

                            Assertions.assertEquals(
                                    createTx.getResponseRecord()
                                            .getTransactionID()
                                            .getAccountID(),
                                    triggeredTx
                                            .getResponseRecord()
                                            .getTransactionID()
                                            .getAccountID(),
                                    WRONG_RECORD_ACCOUNT_ID);

                            Assertions.assertTrue(
                                    triggeredTx
                                            .getResponseRecord()
                                            .getTransactionID()
                                            .getScheduled(),
                                    TRANSACTION_NOT_SCHEDULED);

                            Assertions.assertEquals(
                                    createTx.getResponseRecord().getReceipt().getScheduleID(),
                                    triggeredTx.getResponseRecord().getScheduleRef(),
                                    WRONG_SCHEDULE_ID);

                            Assertions.assertTrue(
                                    transferListCheck(
                                            triggeredTx,
                                            asId(SENDER, spec),
                                            asId(RECEIVER, spec),
                                            asId(PAYING_ACCOUNT, spec),
                                            transferAmount),
                                    WRONG_TRANSFER_LIST);
                        }));
    }

    @HapiTest
    @Order(5)
    public Stream<DynamicTest> executionWithContractCallWorksAtExpiry() {
        return defaultHapiSpec("ExecutionWithContractCallWorksAtExpiry")
                .given(
                        // upload fees for SCHEDULE_CREATE_CONTRACT_CALL
                        uploadScheduledContractPrices(GENESIS),
                        uploadInitCode(SIMPLE_UPDATE),
                        contractCreate(SIMPLE_UPDATE).gas(500_000L),
                        cryptoCreate(PAYING_ACCOUNT).balance(1000000000000L).via(PAYING_ACCOUNT_TXN))
                .when(scheduleCreate(
                                BASIC_XFER,
                                contractCall(SIMPLE_UPDATE, "set", BigInteger.valueOf(5), BigInteger.valueOf(42))
                                        .gas(300000L))
                        .waitForExpiry()
                        .withRelativeExpiry(PAYING_ACCOUNT_TXN, 4)
                        .designatingPayer(PAYING_ACCOUNT)
                        .alsoSigningWith(PAYING_ACCOUNT)
                        .recordingScheduledTxn()
                        .via(CREATE_TX))
                .then(
                        getScheduleInfo(BASIC_XFER)
                                .hasScheduleId(BASIC_XFER)
                                .hasWaitForExpiry()
                                .isNotExecuted()
                                .isNotDeleted()
                                .hasRelativeExpiry(PAYING_ACCOUNT_TXN, 4)
                                .hasRecordedScheduledTxn(),
                        sleepFor(5000),
                        cryptoCreate("foo").via(TRIGGERING_TXN),
                        getScheduleInfo(BASIC_XFER).hasCostAnswerPrecheck(INVALID_SCHEDULE_ID),
                        getAccountBalance(PAYING_ACCOUNT)
                                .hasTinyBars(spec ->
                                        bal -> bal < 1000000000000L ? Optional.empty() : Optional.of("didnt change")),
                        withOpContext((spec, opLog) -> {
                            var triggeredTx = getTxnRecord(CREATE_TX).scheduled();
                            allRunFor(spec, triggeredTx);
                            Assertions.assertEquals(
                                    SUCCESS,
                                    triggeredTx.getResponseRecord().getReceipt().getStatus(),
                                    SCHEDULED_TRANSACTION_MUST_NOT_SUCCEED);

                            Assertions.assertTrue(triggeredTx
                                            .getResponseRecord()
                                            .getContractCallResult()
                                            .getContractCallResult()
                                            .size()
                                    >= 0);
                        }));
    }

    @HapiTest
    @Order(6)
    public Stream<DynamicTest> executionWithContractCreateWorksAtExpiry() {
        return defaultHapiSpec("ExecutionWithContractCreateWorksAtExpiry")
                .given(
                        // overriding(SCHEDULING_WHITELIST, "ContractCreate"),
                        uploadInitCode(SIMPLE_UPDATE),
                        cryptoCreate(PAYING_ACCOUNT).balance(1000000000000L).via(PAYING_ACCOUNT_TXN))
                .when(scheduleCreate(
                                BASIC_XFER,
                                contractCreate(SIMPLE_UPDATE).gas(500_000L).adminKey(PAYING_ACCOUNT))
                        .waitForExpiry()
                        .withRelativeExpiry(PAYING_ACCOUNT_TXN, 4)
                        .designatingPayer(PAYING_ACCOUNT)
                        .alsoSigningWith(PAYING_ACCOUNT)
                        .recordingScheduledTxn()
                        .via(CREATE_TX))
                .then(
                        getScheduleInfo(BASIC_XFER)
                                .hasScheduleId(BASIC_XFER)
                                .hasWaitForExpiry()
                                .isNotExecuted()
                                .isNotDeleted()
                                .hasRelativeExpiry(PAYING_ACCOUNT_TXN, 4)
                                .hasRecordedScheduledTxn(),
                        sleepFor(5000),
                        cryptoCreate("foo").via(TRIGGERING_TXN),
                        getScheduleInfo(BASIC_XFER).hasCostAnswerPrecheck(INVALID_SCHEDULE_ID),
                        // todo check white list here?
                        //                        overriding(
                        //                                SCHEDULING_WHITELIST,
                        //
                        // HapiSpecSetup.getDefaultNodeProps().get(SCHEDULING_WHITELIST)),
                        getAccountBalance(PAYING_ACCOUNT)
                                .hasTinyBars(spec ->
                                        bal -> bal < 1000000000000L ? Optional.empty() : Optional.of("didnt change")),
                        withOpContext((spec, opLog) -> {
                            var triggeredTx = getTxnRecord(CREATE_TX).scheduled();
                            allRunFor(spec, triggeredTx);

                            Assertions.assertEquals(
                                    SUCCESS,
                                    triggeredTx.getResponseRecord().getReceipt().getStatus(),
                                    SCHEDULED_TRANSACTION_MUST_NOT_SUCCEED);

                            Assertions.assertTrue(
                                    triggeredTx.getResponseRecord().getReceipt().hasContractID());

                            Assertions.assertTrue(triggeredTx
                                            .getResponseRecord()
                                            .getContractCreateResult()
                                            .getContractCallResult()
                                            .size()
                                    >= 0);
                        }));
    }

    @HapiTest
    @Order(7)
    public Stream<DynamicTest> executionWithDefaultPayerButNoFundsFails() {
        long balance = 10_000_000L;
        long noBalance = 0L;
        long transferAmount = 1L;
        return defaultHapiSpec("ExecutionAtExpiryWithDefaultPayerButNoFundsFails")
                .given(
                        cryptoCreate(PAYING_ACCOUNT).balance(balance),
                        cryptoCreate(LUCKY_RECEIVER),
                        cryptoCreate(SENDER).balance(transferAmount).via(SENDER_TXN),
                        cryptoCreate(RECEIVER).balance(noBalance),
                        scheduleCreate(BASIC_XFER, cryptoTransfer(tinyBarsFromTo(SENDER, RECEIVER, transferAmount)))
                                .waitForExpiry()
                                .withRelativeExpiry(SENDER_TXN, 4)
                                .payingWith(PAYING_ACCOUNT)
                                .recordingScheduledTxn()
                                .via(CREATE_TX),
                        recordFeeAmount(CREATE_TX, SCHEDULE_CREATE_FEE))
                .when(
                        cryptoTransfer(tinyBarsFromTo(PAYING_ACCOUNT, LUCKY_RECEIVER, (spec -> {
                            long scheduleCreateFee = spec.registry().getAmount(SCHEDULE_CREATE_FEE);
                            return balance - scheduleCreateFee;
                        }))),
                        getAccountBalance(PAYING_ACCOUNT).hasTinyBars(noBalance),
                        scheduleSign(BASIC_XFER).alsoSigningWith(SENDER).hasKnownStatus(SUCCESS))
                .then(
                        getScheduleInfo(BASIC_XFER)
                                .hasScheduleId(BASIC_XFER)
                                .hasWaitForExpiry()
                                .isNotExecuted()
                                .isNotDeleted()
                                .hasRelativeExpiry(SENDER_TXN, 4)
                                .hasRecordedScheduledTxn(),
                        sleepFor(5000),
                        cryptoCreate("foo").via(TRIGGERING_TXN),
                        getScheduleInfo(BASIC_XFER).hasCostAnswerPrecheck(INVALID_SCHEDULE_ID),
                        getAccountBalance(SENDER).hasTinyBars(transferAmount),
                        getAccountBalance(RECEIVER).hasTinyBars(noBalance),
                        withOpContext((spec, opLog) -> {
                            var triggeredTx = getTxnRecord(CREATE_TX).scheduled();

                            allRunFor(spec, triggeredTx);

                            Assertions.assertEquals(
                                    INSUFFICIENT_PAYER_BALANCE,
                                    triggeredTx.getResponseRecord().getReceipt().getStatus(),
                                    SCHEDULED_TRANSACTION_MUST_NOT_SUCCEED);
                        }));
    }

    @HapiTest
    @Order(8)
    public Stream<DynamicTest> executionWithCustomPayerThatNeverSignsFails() {
        long transferAmount = 1;
        return defaultHapiSpec("ExecutionWithCustomPayerThatNeverSignsFails")
                .given(
                        cryptoCreate(PAYING_ACCOUNT),
                        cryptoCreate(SENDER).via(SENDER_TXN),
                        cryptoCreate(RECEIVER),
                        scheduleCreate(BASIC_XFER, cryptoTransfer(tinyBarsFromTo(SENDER, RECEIVER, transferAmount)))
                                .waitForExpiry()
                                .withRelativeExpiry(SENDER_TXN, 4)
                                .recordingScheduledTxn()
                                .designatingPayer(PAYING_ACCOUNT)
                                .via(CREATE_TX))
                .when(scheduleSign(BASIC_XFER)
                        .alsoSigningWith(SENDER)
                        .via(SIGN_TX)
                        .hasKnownStatus(SUCCESS))
                .then(
                        getScheduleInfo(BASIC_XFER)
                                .hasScheduleId(BASIC_XFER)
                                .hasWaitForExpiry()
                                .isNotExecuted()
                                .isNotDeleted()
                                .hasRelativeExpiry(SENDER_TXN, 4)
                                .hasRecordedScheduledTxn(),
                        sleepFor(5000),
                        cryptoCreate("foo").via(TRIGGERING_TXN),
                        getScheduleInfo(BASIC_XFER).hasCostAnswerPrecheck(INVALID_SCHEDULE_ID),
                        getTxnRecord(CREATE_TX)
                                .scheduled()
                                .hasPriority(recordWith().status(INVALID_PAYER_SIGNATURE)));
    }

    @HapiTest
    @Order(9)
    public Stream<DynamicTest> executionWithCustomPayerButNoFundsFails() {
        long balance = 0L;
        long transferAmount = 1;
        return defaultHapiSpec("ExecutionAtExpiryWithCustomPayerButNoFundsFails")
                .given(
                        cryptoCreate(PAYING_ACCOUNT).balance(balance),
                        cryptoCreate(SENDER).via(SENDER_TXN),
                        cryptoCreate(RECEIVER),
                        scheduleCreate(BASIC_XFER, cryptoTransfer(tinyBarsFromTo(SENDER, RECEIVER, transferAmount)))
                                .waitForExpiry()
                                .withRelativeExpiry(SENDER_TXN, 4)
                                .recordingScheduledTxn()
                                .designatingPayer(PAYING_ACCOUNT)
                                .via(CREATE_TX))
                .when(scheduleSign(BASIC_XFER)
                        .alsoSigningWith(SENDER, PAYING_ACCOUNT)
                        .via(SIGN_TX)
                        .hasKnownStatus(SUCCESS))
                .then(
                        getScheduleInfo(BASIC_XFER)
                                .hasScheduleId(BASIC_XFER)
                                .hasWaitForExpiry()
                                .isNotExecuted()
                                .isNotDeleted()
                                .hasRelativeExpiry(SENDER_TXN, 4)
                                .hasRecordedScheduledTxn(),
                        sleepFor(5000),
                        cryptoCreate("foo").via(TRIGGERING_TXN),
                        getScheduleInfo(BASIC_XFER).hasCostAnswerPrecheck(INVALID_SCHEDULE_ID),
                        withOpContext((spec, opLog) -> {
                            var triggeredTx = getTxnRecord(CREATE_TX).scheduled();

                            allRunFor(spec, triggeredTx);

                            Assertions.assertEquals(
                                    INSUFFICIENT_PAYER_BALANCE,
                                    triggeredTx.getResponseRecord().getReceipt().getStatus(),
                                    SCHEDULED_TRANSACTION_MUST_NOT_SUCCEED);
                        }));
    }

    @HapiTest
    @Order(10)
    public Stream<DynamicTest> executionWithDefaultPayerButAccountDeletedFails() {
        long balance = 10_000_000L;
        long noBalance = 0L;
        long transferAmount = 1L;
        return defaultHapiSpec("ExecutionAtExpiryWithDefaultPayerButAccountDeletedFails")
                .given(
                        cryptoCreate(PAYING_ACCOUNT).balance(balance),
                        cryptoCreate(LUCKY_RECEIVER),
                        cryptoCreate(SENDER).balance(transferAmount).via(SENDER_TXN),
                        cryptoCreate(RECEIVER).balance(noBalance),
                        scheduleCreate(BASIC_XFER, cryptoTransfer(tinyBarsFromTo(SENDER, RECEIVER, transferAmount)))
                                .waitForExpiry()
                                .withRelativeExpiry(SENDER_TXN, 4)
                                .recordingScheduledTxn()
                                .payingWith(PAYING_ACCOUNT)
                                .via(CREATE_TX),
                        recordFeeAmount(CREATE_TX, SCHEDULE_CREATE_FEE))
                .when(
                        cryptoDelete(PAYING_ACCOUNT),
                        scheduleSign(BASIC_XFER).alsoSigningWith(SENDER).hasKnownStatus(SUCCESS))
                .then(
                        getAccountBalance(SENDER).hasTinyBars(transferAmount),
                        getAccountBalance(RECEIVER).hasTinyBars(noBalance),
                        getScheduleInfo(BASIC_XFER)
                                .hasScheduleId(BASIC_XFER)
                                .hasWaitForExpiry()
                                .isNotExecuted()
                                .isNotDeleted()
                                .hasRelativeExpiry(SENDER_TXN, 4)
                                .hasRecordedScheduledTxn(),
                        sleepFor(5000),
                        cryptoCreate("foo").via(TRIGGERING_TXN),
                        getScheduleInfo(BASIC_XFER).hasCostAnswerPrecheck(INVALID_SCHEDULE_ID),
                        getAccountBalance(SENDER).hasTinyBars(transferAmount),
                        getAccountBalance(RECEIVER).hasTinyBars(noBalance),
                        getTxnRecord(CREATE_TX)
                                .scheduled()
                                .hasPriority(recordWith().statusFrom(PAYER_ACCOUNT_DELETED)));
    }

    @HapiTest
    @Order(11)
    public Stream<DynamicTest> executionWithCustomPayerButAccountDeletedFails() {
        long balance = 10_000_000L;
        long noBalance = 0L;
        long transferAmount = 1;
        return defaultHapiSpec("ExecutionAtExpiryWithCustomPayerButAccountDeletedFails")
                .given(
                        cryptoCreate(PAYING_ACCOUNT).balance(balance),
                        cryptoCreate(SENDER).balance(transferAmount).via(SENDER_TXN),
                        cryptoCreate(RECEIVER).balance(noBalance),
                        scheduleCreate(BASIC_XFER, cryptoTransfer(tinyBarsFromTo(SENDER, RECEIVER, transferAmount)))
                                .waitForExpiry()
                                .withRelativeExpiry(SENDER_TXN, 4)
                                .recordingScheduledTxn()
                                .designatingPayer(PAYING_ACCOUNT)
                                .alsoSigningWith(PAYING_ACCOUNT)
                                .via(CREATE_TX))
                .when(
                        cryptoDelete(PAYING_ACCOUNT),
                        scheduleSign(BASIC_XFER)
                                .alsoSigningWith(SENDER)
                                .via(SIGN_TX)
                                .hasKnownStatus(SUCCESS))
                .then(
                        getAccountBalance(SENDER).hasTinyBars(transferAmount),
                        getAccountBalance(RECEIVER).hasTinyBars(noBalance),
                        getScheduleInfo(BASIC_XFER)
                                .hasScheduleId(BASIC_XFER)
                                .hasWaitForExpiry()
                                .isNotExecuted()
                                .isNotDeleted()
                                .hasRelativeExpiry(SENDER_TXN, 4)
                                .hasRecordedScheduledTxn(),
                        sleepFor(5000),
                        cryptoCreate("foo").via(TRIGGERING_TXN),
                        getScheduleInfo(BASIC_XFER).hasCostAnswerPrecheck(INVALID_SCHEDULE_ID),
                        getAccountBalance(SENDER).hasTinyBars(transferAmount),
                        getAccountBalance(RECEIVER).hasTinyBars(noBalance),
                        getTxnRecord(CREATE_TX)
                                .scheduled()
                                .hasPriority(
                                        recordWith().statusFrom(INSUFFICIENT_ACCOUNT_BALANCE, PAYER_ACCOUNT_DELETED)));
    }

    @HapiTest
    @Order(12)
    public Stream<DynamicTest> executionWithInvalidAccountAmountsFails() {
        long transferAmount = 100;
        long senderBalance = 1000L;
        long payingAccountBalance = 1_000_000L;
        long noBalance = 0L;
        return defaultHapiSpec("ExecutionAtExpiryWithInvalidAccountAmountsFails")
                .given(
                        cryptoCreate(PAYING_ACCOUNT).balance(payingAccountBalance),
                        cryptoCreate(SENDER).balance(senderBalance).via(SENDER_TXN),
                        cryptoCreate(RECEIVER).balance(noBalance),
                        scheduleCreate(
                                        FAILED_XFER,
                                        cryptoTransfer(
                                                tinyBarsFromToWithInvalidAmounts(SENDER, RECEIVER, transferAmount)))
                                .waitForExpiry()
                                .withRelativeExpiry(SENDER_TXN, 4)
                                .designatingPayer(PAYING_ACCOUNT)
                                .recordingScheduledTxn()
                                .hasKnownStatus(INVALID_ACCOUNT_AMOUNTS))
                .when()
                .then();
    }

    @HapiTest
    @Order(13)
    public Stream<DynamicTest> executionWithCryptoInsufficientAccountBalanceFails() {
        long noBalance = 0L;
        long senderBalance = 100L;
        long transferAmount = 101L;
        long payerBalance = 1_000_000L;
        return defaultHapiSpec("ExecutionAtExpiryWithCryptoInsufficientAccountBalanceFails")
                .given(
                        cryptoCreate(PAYING_ACCOUNT).balance(payerBalance),
                        cryptoCreate(SENDER).balance(senderBalance).via(SENDER_TXN),
                        cryptoCreate(RECEIVER).balance(noBalance),
                        scheduleCreate(FAILED_XFER, cryptoTransfer(tinyBarsFromTo(SENDER, RECEIVER, transferAmount)))
                                .waitForExpiry()
                                .withRelativeExpiry(SENDER_TXN, 4)
                                .designatingPayer(PAYING_ACCOUNT)
                                .recordingScheduledTxn()
                                .via(CREATE_TX))
                .when(scheduleSign(FAILED_XFER)
                        .alsoSigningWith(SENDER, PAYING_ACCOUNT)
                        .via(SIGN_TX)
                        .hasKnownStatus(SUCCESS))
                .then(
                        getScheduleInfo(FAILED_XFER)
                                .hasScheduleId(BASIC_XFER)
                                .hasWaitForExpiry()
                                .isNotExecuted()
                                .isNotDeleted()
                                .hasRelativeExpiry(SENDER_TXN, 4)
                                .hasRecordedScheduledTxn(),
                        sleepFor(5000),
                        cryptoCreate("foo").via(TRIGGERING_TXN),
                        getScheduleInfo(FAILED_XFER).hasCostAnswerPrecheck(INVALID_SCHEDULE_ID),
                        getAccountBalance(SENDER).hasTinyBars(senderBalance),
                        getAccountBalance(RECEIVER).hasTinyBars(noBalance),
                        withOpContext((spec, opLog) -> {
                            var triggeredTx = getTxnRecord(CREATE_TX).scheduled();

                            allRunFor(spec, triggeredTx);

                            Assertions.assertEquals(
                                    INSUFFICIENT_ACCOUNT_BALANCE,
                                    triggeredTx.getResponseRecord().getReceipt().getStatus(),
                                    SCHEDULED_TRANSACTION_MUST_NOT_SUCCEED);
                        }));
    }

    @HapiTest
    @Order(14)
    public Stream<DynamicTest> executionWithCryptoSenderDeletedFails() {
        long noBalance = 0L;
        long senderBalance = 100L;
        long transferAmount = 101L;
        long payerBalance = 1_000_000L;
        return defaultHapiSpec("ExecutionAtExpiryWithCryptoSenderDeletedFails")
                .given(
                        cryptoCreate(PAYING_ACCOUNT).balance(payerBalance),
                        cryptoCreate(SENDER).balance(senderBalance).via(SENDER_TXN),
                        cryptoCreate(RECEIVER).balance(noBalance),
                        scheduleCreate(FAILED_XFER, cryptoTransfer(tinyBarsFromTo(SENDER, RECEIVER, transferAmount)))
                                .waitForExpiry()
                                .withRelativeExpiry(SENDER_TXN, 4)
                                .recordingScheduledTxn()
                                .designatingPayer(PAYING_ACCOUNT)
                                .via(CREATE_TX))
                .when(
                        cryptoDelete(SENDER),
                        scheduleSign(FAILED_XFER)
                                .alsoSigningWith(SENDER, PAYING_ACCOUNT)
                                .via(SIGN_TX)
                                .hasKnownStatus(SUCCESS))
                .then(
                        getAccountBalance(RECEIVER).hasTinyBars(noBalance),
                        getScheduleInfo(FAILED_XFER)
                                .hasScheduleId(FAILED_XFER)
                                .hasWaitForExpiry()
                                .isNotExecuted()
                                .isNotDeleted()
                                .hasRelativeExpiry(SENDER_TXN, 4)
                                .hasRecordedScheduledTxn(),
                        sleepFor(5000),
                        cryptoCreate("foo").via(TRIGGERING_TXN),
                        getScheduleInfo(FAILED_XFER).hasCostAnswerPrecheck(INVALID_SCHEDULE_ID),
                        getAccountBalance(RECEIVER).hasTinyBars(noBalance),
                        withOpContext((spec, opLog) -> {
                            var triggeredTx = getTxnRecord(CREATE_TX).scheduled();

                            allRunFor(spec, triggeredTx);

                            Assertions.assertEquals(
                                    ACCOUNT_DELETED,
                                    triggeredTx.getResponseRecord().getReceipt().getStatus(),
                                    SCHEDULED_TRANSACTION_MUST_NOT_SUCCEED);
                        }));
    }

    @HapiTest
    @Order(15)
    public Stream<DynamicTest> executionTriggersWithWeirdlyRepeatedKey() {
        String schedule = "dupKeyXfer";

        return defaultHapiSpec("ExecutionAtExpiryTriggersWithWeirdlyRepeatedKey")
                .given(
                        cryptoCreate(WEIRDLY_POPULAR_KEY),
                        cryptoCreate(SENDER_1).key(WEIRDLY_POPULAR_KEY).balance(1L),
                        cryptoCreate(SENDER_2).key(WEIRDLY_POPULAR_KEY).balance(1L),
                        cryptoCreate(SENDER_3).key(WEIRDLY_POPULAR_KEY).balance(1L),
                        cryptoCreate(RECEIVER).balance(0L).via(WEIRDLY_POPULAR_KEY_TXN),
                        scheduleCreate(
                                        schedule,
                                        cryptoTransfer(
                                                tinyBarsFromTo(SENDER_1, RECEIVER, 1L),
                                                tinyBarsFromTo(SENDER_2, RECEIVER, 1L),
                                                tinyBarsFromTo(SENDER_3, RECEIVER, 1L)))
                                .waitForExpiry()
                                .withRelativeExpiry(WEIRDLY_POPULAR_KEY_TXN, 4)
                                .payingWith(DEFAULT_PAYER)
                                .recordingScheduledTxn()
                                .via("creation"))
                .when(scheduleSign(schedule).alsoSigningWith(WEIRDLY_POPULAR_KEY))
                .then(
                        getScheduleInfo(schedule)
                                .hasScheduleId(schedule)
                                .hasWaitForExpiry()
                                .isNotExecuted()
                                .isNotDeleted()
                                .hasRelativeExpiry(WEIRDLY_POPULAR_KEY_TXN, 4)
                                .hasRecordedScheduledTxn(),
                        sleepFor(5000),
                        cryptoCreate("foo").via(TRIGGERING_TXN),
                        getScheduleInfo(schedule).hasCostAnswerPrecheck(INVALID_SCHEDULE_ID),
                        getAccountBalance(SENDER_1).hasTinyBars(0L),
                        getAccountBalance(SENDER_2).hasTinyBars(0L),
                        getAccountBalance(SENDER_3).hasTinyBars(0L),
                        getAccountBalance(RECEIVER).hasTinyBars(3L),
                        scheduleSign(schedule)
                                .alsoSigningWith(WEIRDLY_POPULAR_KEY)
                                .hasKnownStatus(INVALID_SCHEDULE_ID));
    }

    @HapiTest
    @Order(16)
    final Stream<DynamicTest> scheduledFreezeWorksAsExpected() {

        return defaultHapiSpec("ScheduledFreezeWorksAsExpectedAtExpiry")
                .given(flattened(
                        cryptoCreate(PAYING_ACCOUNT).via(PAYER_TXN),
                        scheduleFakeUpgrade(PAYING_ACCOUNT, PAYER_TXN, 4, SUCCESS_TXN)))
                .when(scheduleSign(VALID_SCHEDULE)
                        .alsoSigningWith(GENESIS)
                        .payingWith(PAYING_ACCOUNT)
                        .hasKnownStatus(SUCCESS))
                .then(
                        getScheduleInfo(VALID_SCHEDULE)
                                .hasScheduleId(VALID_SCHEDULE)
                                .hasWaitForExpiry()
                                .isNotExecuted()
                                .isNotDeleted()
                                .hasRelativeExpiry(PAYER_TXN, 4)
                                .hasRecordedScheduledTxn(),
                        sleepFor(5000),
                        cryptoCreate("foo").via(TRIGGERING_TXN),
                        getScheduleInfo(VALID_SCHEDULE).hasCostAnswerPrecheck(INVALID_SCHEDULE_ID),
                        freezeAbort().payingWith(GENESIS),
                        withOpContext((spec, opLog) -> {
                            var triggeredTx = getTxnRecord(SUCCESS_TXN).scheduled();
                            allRunFor(spec, triggeredTx);
                            Assertions.assertEquals(
                                    SUCCESS,
                                    triggeredTx.getResponseRecord().getReceipt().getStatus(),
                                    SCHEDULED_TRANSACTION_MUST_NOT_SUCCEED);
                        }));
    }

    @HapiTest
    @Order(17)
    final Stream<DynamicTest> scheduledFreezeWithUnauthorizedPayerFails() {

        return defaultHapiSpec("ScheduledFreezeWithUnauthorizedPayerFailsAtExpiry")
                .given(cryptoCreate(PAYING_ACCOUNT).via(PAYER_TXN), cryptoCreate(PAYING_ACCOUNT_2))
                .when()
                .then(
                        scheduleFakeUpgrade(PAYING_ACCOUNT, PAYER_TXN, 4, "test")
                        // future throttles will be exceeded because there is no throttle
                        // for freeze
                        // and the custom payer is not exempt from throttles like and admin
                        // user would be
                        // todo future throttle is not implemented yet
                        // .hasKnownStatus(SCHEDULE_FUTURE_THROTTLE_EXCEEDED)
                        );
    }

    @HapiTest
    @Order(18)
    final Stream<DynamicTest> scheduledPermissionedFileUpdateWorksAsExpected() {

        return defaultHapiSpec("ScheduledPermissionedFileUpdateWorksAsExpectedAtExpiry")
                .given(
                        cryptoCreate(PAYING_ACCOUNT).via(PAYER_TXN),
                        scheduleCreate(
                                        VALID_SCHEDULE,
                                        fileUpdate(standardUpdateFile).contents("fooo!"))
                                .withEntityMemo(randomUppercase(100))
                                .designatingPayer(SYSTEM_ADMIN)
                                .payingWith(PAYING_ACCOUNT)
                                .waitForExpiry()
                                .withRelativeExpiry(PAYER_TXN, 4)
                                .recordingScheduledTxn()
                                .via(SUCCESS_TXN))
                .when(scheduleSign(VALID_SCHEDULE)
                        .alsoSigningWith(SYSTEM_ADMIN)
                        .payingWith(PAYING_ACCOUNT)
                        .hasKnownStatus(SUCCESS))
                .then(
                        getScheduleInfo(VALID_SCHEDULE)
                                .hasScheduleId(VALID_SCHEDULE)
                                .hasWaitForExpiry()
                                .isNotExecuted()
                                .isNotDeleted()
                                .hasRelativeExpiry(PAYER_TXN, 4)
                                .hasRecordedScheduledTxn(),
                        sleepFor(5000),
                        cryptoCreate("foo").via(TRIGGERING_TXN),
                        getScheduleInfo(VALID_SCHEDULE).hasCostAnswerPrecheck(INVALID_SCHEDULE_ID),
                        withOpContext((spec, opLog) -> {
                            var triggeredTx = getTxnRecord(SUCCESS_TXN).scheduled();
                            allRunFor(spec, triggeredTx);

                            Assertions.assertEquals(
                                    SUCCESS,
                                    triggeredTx.getResponseRecord().getReceipt().getStatus(),
                                    SCHEDULED_TRANSACTION_MUST_NOT_SUCCEED);
                        }));
    }

    @HapiTest
    @Order(19)
    final Stream<DynamicTest> scheduledPermissionedFileUpdateUnauthorizedPayerFails() {

        return defaultHapiSpec("ScheduledPermissionedFileUpdateUnauthorizedPayerFailsAtExpiry")
                .given(
                        cryptoCreate(PAYING_ACCOUNT).via(PAYER_TXN),
                        cryptoCreate(PAYING_ACCOUNT_2),
                        scheduleCreate(
                                        VALID_SCHEDULE,
                                        fileUpdate(standardUpdateFile).contents("fooo!"))
                                .withEntityMemo(randomUppercase(100))
                                .designatingPayer(PAYING_ACCOUNT_2)
                                .payingWith(PAYING_ACCOUNT)
                                .waitForExpiry()
                                .withRelativeExpiry(PAYER_TXN, 4)
                                .recordingScheduledTxn()
                                .via(SUCCESS_TXN))
                .when(scheduleSign(VALID_SCHEDULE)
                        .alsoSigningWith(PAYING_ACCOUNT_2, FREEZE_ADMIN)
                        .payingWith(PAYING_ACCOUNT)
                        .hasKnownStatus(SUCCESS))
                .then(
                        getScheduleInfo(VALID_SCHEDULE)
                                .hasScheduleId(VALID_SCHEDULE)
                                .hasWaitForExpiry()
                                .isNotExecuted()
                                .isNotDeleted()
                                .hasRelativeExpiry(PAYER_TXN, 4)
                                .hasRecordedScheduledTxn(),
                        sleepFor(5000),
                        cryptoCreate("foo").via(TRIGGERING_TXN),
                        getScheduleInfo(VALID_SCHEDULE).hasCostAnswerPrecheck(INVALID_SCHEDULE_ID),
                        withOpContext((spec, opLog) -> {
                            var triggeredTx = getTxnRecord(SUCCESS_TXN).scheduled();
                            allRunFor(spec, triggeredTx);

                            Assertions.assertEquals(
                                    AUTHORIZATION_FAILED,
                                    triggeredTx.getResponseRecord().getReceipt().getStatus(),
                                    "Scheduled transaction be AUTHORIZATION_FAILED!");
                        }));
    }

    @HapiTest
    @Order(20)
    final Stream<DynamicTest> scheduledSystemDeleteWorksAsExpected() {

        return defaultHapiSpec("ScheduledSystemDeleteWorksAsExpectedAtExpiry")
                .given(
                        cryptoCreate(PAYING_ACCOUNT).via(PAYER_TXN),
                        fileCreate("misc").lifetime(THREE_MONTHS_IN_SECONDS).contents(ORIG_FILE),
                        scheduleCreate(VALID_SCHEDULE, systemFileDelete("misc").updatingExpiry(1L))
                                .withEntityMemo(randomUppercase(100))
                                .designatingPayer(SYSTEM_DELETE_ADMIN)
                                .payingWith(PAYING_ACCOUNT)
                                .waitForExpiry()
                                .withRelativeExpiry(PAYER_TXN, 4)
                                .recordingScheduledTxn()
                                .via(SUCCESS_TXN))
                .when(scheduleSign(VALID_SCHEDULE)
                        .alsoSigningWith(SYSTEM_DELETE_ADMIN)
                        .payingWith(PAYING_ACCOUNT)
                        .hasKnownStatus(SUCCESS))
                .then(
                        getScheduleInfo(VALID_SCHEDULE)
                                .hasScheduleId(VALID_SCHEDULE)
                                .hasWaitForExpiry()
                                .isNotExecuted()
                                .isNotDeleted()
                                .hasRelativeExpiry(PAYER_TXN, 4)
                                .hasRecordedScheduledTxn(),
                        sleepFor(5000),
                        cryptoCreate("foo").via(TRIGGERING_TXN),
                        getScheduleInfo(VALID_SCHEDULE).hasCostAnswerPrecheck(INVALID_SCHEDULE_ID),
                        getFileInfo("misc").nodePayment(1_234L).hasAnswerOnlyPrecheck(INVALID_FILE_ID),
                        withOpContext((spec, opLog) -> {
                            var triggeredTx = getTxnRecord(SUCCESS_TXN).scheduled();
                            allRunFor(spec, triggeredTx);

                            Assertions.assertEquals(
                                    SUCCESS,
                                    triggeredTx.getResponseRecord().getReceipt().getStatus(),
                                    SCHEDULED_TRANSACTION_MUST_NOT_SUCCEED);
                        }));
    }

    @HapiTest
    @Order(21)
    final Stream<DynamicTest> scheduledSystemDeleteUnauthorizedPayerFails() {

        return defaultHapiSpec("ScheduledSystemDeleteUnauthorizedPayerFailsAtExpiry")
                .given(
                        cryptoCreate(PAYING_ACCOUNT).via(PAYER_TXN),
                        cryptoCreate(PAYING_ACCOUNT_2),
                        fileCreate("misc").lifetime(THREE_MONTHS_IN_SECONDS).contents(ORIG_FILE))
                .when()
                .then(
                        scheduleCreate(VALID_SCHEDULE, systemFileDelete("misc").updatingExpiry(1L))
                                .withEntityMemo(randomUppercase(100))
                                .designatingPayer(PAYING_ACCOUNT_2)
                                .payingWith(PAYING_ACCOUNT)
                                .waitForExpiry()
                                .withRelativeExpiry(PAYER_TXN, 4)
                        // future throttles will be exceeded because there is no throttle
                        // for system delete
                        // and the custom payer is not exempt from throttles like and admin
                        // user would be
                        // todo future throttle is not implemented yet
                        //                                .hasKnownStatus(SCHEDULE_FUTURE_THROTTLE_EXCEEDED)
                        );
    }

    // todo throttles are not implemented yet!
    //    @HapiTest
    //    final Stream<DynamicTest> futureThrottlesAreRespected() {
    //        var artificialLimits = protoDefsFromResource("testSystemFiles/artificial-limits-schedule.json");
    //        var defaultThrottles = protoDefsFromResource("testSystemFiles/throttles-dev.json");
    //
    //        return defaultHapiSpec("FutureThrottlesAreRespected")
    //                .given(
    //                        cryptoCreate(SENDER).balance(ONE_MILLION_HBARS).via(SENDER_TXN),
    //                        cryptoCreate(RECEIVER),
    //                        overriding(SCHEDULING_MAX_TXN_PER_SECOND, "100"),
    //                        fileUpdate(THROTTLE_DEFS)
    //                                .payingWith(EXCHANGE_RATE_CONTROL)
    //                                .contents(artificialLimits.toByteArray()),
    //                        sleepFor(500))
    //                .when(
    //                        blockingOrder(IntStream.range(0, 17)
    //                                .mapToObj(i -> new HapiSpecOperation[] {
    //                                        scheduleCreate(
    //                                                "twoSigXfer" + i,
    //                                                cryptoTransfer(tinyBarsFromTo(SENDER, RECEIVER, 1))
    //                                                        .fee(ONE_HBAR))
    //                                                .withEntityMemo(randomUppercase(100))
    //                                                .payingWith(SENDER)
    //                                                .waitForExpiry()
    //                                                .withRelativeExpiry(SENDER_TXN, 120),
    //                                })
    //                                .flatMap(Arrays::stream)
    //                                .toArray(HapiSpecOperation[]::new)),
    //                        scheduleCreate(
    //                                "twoSigXfer",
    //                                cryptoTransfer(tinyBarsFromTo(SENDER, RECEIVER, 1))
    //                                        .fee(ONE_HBAR))
    //                                .withEntityMemo(randomUppercase(100))
    //                                .payingWith(SENDER)
    //                                .waitForExpiry()
    //                                .withRelativeExpiry(SENDER_TXN, 120)
    //                                .hasKnownStatus(SCHEDULE_FUTURE_THROTTLE_EXCEEDED))
    //                .then(
    //                        overriding(
    //                                SCHEDULING_MAX_TXN_PER_SECOND,
    //                                HapiSpecSetup.getDefaultNodeProps().get(SCHEDULING_MAX_TXN_PER_SECOND)),
    //                        fileUpdate(THROTTLE_DEFS)
    //                                .fee(ONE_HUNDRED_HBARS)
    //                                .payingWith(EXCHANGE_RATE_CONTROL)
    //                                .contents(defaultThrottles.toByteArray()),
    //                        cryptoTransfer(HapiCryptoTransfer.tinyBarsFromTo(GENESIS, FUNDING, 1))
    //                                .payingWith(GENESIS));
    //    }


    @HapiTest
    @Order(23)
    final Stream<DynamicTest> settingTheRightNanosecondsOnConsensusTimestamp() {
        final var receiver1 = "receiver1";
        final var receiver2 = "receiver2";
        final var receiver3 = "receiver3";
        final var receiver4 = "receiver4";
        return hapiTest(
                cryptoCreate(PAYING_ACCOUNT).via("createPayerTxn"),
                newKeyNamed(receiver1), newKeyNamed(receiver2), newKeyNamed(receiver3), newKeyNamed(receiver4),
                scheduleCreate(VALID_SCHEDULE, cryptoTransfer(tinyBarsFromAccountToAlias(PAYING_ACCOUNT, receiver1, ONE_HBAR, false)))
                        .withEntityMemo(randomUppercase(100))
                        .payingWith(PAYING_ACCOUNT)
                        .waitForExpiry()
                        .withRelativeExpiry("createPayerTxn", 4)
                        .recordingScheduledTxn()
                        .via("firstSchedule"),
                scheduleCreate(VALID_SCHEDULE, cryptoTransfer(tinyBarsFromAccountToAlias(PAYING_ACCOUNT, receiver2, ONE_HBAR, false), tinyBarsFromAccountToAlias(PAYING_ACCOUNT, receiver4, ONE_HBAR, false)))
                        .withEntityMemo(randomUppercase(100))
                        .payingWith(PAYING_ACCOUNT)
                        .waitForExpiry()
                        .withRelativeExpiry("createPayerTxn", 4)
                        .recordingScheduledTxn()
                        .via("secondSchedule"),
                sleepFor(5000),
                cryptoTransfer(tinyBarsFromAccountToAlias(PAYING_ACCOUNT, receiver3, ONE_HBAR, false)).via("trigger"),
                withOpContext((spec, opLog) -> {
                    // get all records
                    final var trigger = getTxnRecord("trigger").andAllChildRecords();
                    final var firstSchedule = getTxnRecord("firstSchedule").scheduled().andAllChildRecords();
                    final var secondSchedule = getTxnRecord("secondSchedule").scheduled().andAllChildRecords();
                    allRunFor(spec, trigger, firstSchedule, secondSchedule);

                    // get all nanoseconds
                    final var triggerSeconds = trigger.getResponseRecord().getConsensusTimestamp().getSeconds();
                    final var firstScheduleSeconds = firstSchedule.getResponseRecord().getConsensusTimestamp().getSeconds();
                    final var secondScheduleSeconds = secondSchedule.getResponseRecord().getConsensusTimestamp().getSeconds();

                    final var triggerChildNanos = trigger.getFirstNonStakingChildRecord().getConsensusTimestamp().getNanos();
                    final var triggerNanos = trigger.getResponseRecord().getConsensusTimestamp().getNanos();

                    final var firstScheduleChildNanos = firstSchedule.getFirstNonStakingChildRecord().getConsensusTimestamp().getNanos();
                    final var firstScheduleNanos = firstSchedule.getResponseRecord().getConsensusTimestamp().getNanos();

                    final var secondScheduleChildNanos = secondSchedule.getFirstNonStakingChildRecord().getConsensusTimestamp().getNanos();
                    final var secondScheduleSChildNanos = secondSchedule.getChildRecord(1).getConsensusTimestamp().getNanos();
                    final var secondScheduleNanos = secondSchedule.getResponseRecord().getConsensusTimestamp().getNanos();

                    Assertions.assertTrue(triggerSeconds == firstScheduleSeconds && triggerSeconds == secondScheduleSeconds, WRONG_CONSENSUS_TIMESTAMP);

                    // todo add assertions of child nanos
//                    Assertions.assertTrue(, WRONG_CONSENSUS_TIMESTAMP);
                })
        );
    }
}
