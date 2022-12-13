/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromTo;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromToWithInvalidAmounts;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.blockingOrder;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.freezeAbort;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.overriding;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.prepareUpgrade;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.recordFeeAmount;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sleepFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.suites.freeze.UpgradeSuite.poeticUpgradeLoc;
import static com.hedera.services.bdd.suites.freeze.UpgradeSuite.standardUpdateFile;
import static com.hedera.services.bdd.suites.schedule.ScheduleExecutionSpecs.ORIG_FILE;
import static com.hedera.services.bdd.suites.schedule.ScheduleExecutionSpecs.getPoeticUpgradeHash;
import static com.hedera.services.bdd.suites.schedule.ScheduleExecutionSpecs.transferListCheck;
import static com.hedera.services.bdd.suites.utils.sysfiles.serdes.ThrottleDefsLoader.protoDefsFromResource;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_DELETED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.AUTHORIZATION_FAILED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_ACCOUNT_BALANCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_PAYER_BALANCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_AMOUNTS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_FILE_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SCHEDULE_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.RECORD_NOT_FOUND;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SCHEDULE_FUTURE_THROTTLE_EXCEEDED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;

import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.spec.HapiSpecSetup;
import com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer;
import com.hedera.services.bdd.suites.HapiSuite;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Assertions;

public class ScheduleLongTermExecutionSpecs extends HapiSuite {

    private static final Logger LOG = LogManager.getLogger(ScheduleLongTermExecutionSpecs.class);

    private static final String SCHEDULING_LONG_TERM_ENABLED = "scheduling.longTermEnabled";
    private static final String DEFAULT_LONG_TERM_ENABLED =
            HapiSpecSetup.getDefaultNodeProps().get(SCHEDULING_LONG_TERM_ENABLED);

    private static final String LEDGER_SCHEDULE_TX_EXPIRY_TIME_SECS =
            "ledger.schedule.txExpiryTimeSecs";
    private static final String DEFAULT_TX_EXPIRY =
            HapiSpecSetup.getDefaultNodeProps().get(LEDGER_SCHEDULE_TX_EXPIRY_TIME_SECS);
    private static final String PAYING_ACCOUNT = "payingAccount";
    private static final String RECEIVER = "receiver";
    private static final String SENDER = "sender";
    public static final String SENDER_TXN = "senderTxn";
    private static final String BASIC_XFER = "basicXfer";
    private static final String CREATE_TX = "createTx";
    private static final String SIGN_TX = "signTx";
    private static final String SCHEDULED_TRANSACTION_BE_SUCCESSFUL =
            "Scheduled transaction be successful!";
    private static final String TRIGGERING_TXN = "triggeringTxn";
    private static final String PAYING_ACCOUNT_2 = "payingAccount2";
    private static final String FALSE = "false";
    private static final String VALID_SCHEDULE = "validSchedule";
    private static final String SUCCESS_TXN = "successTxn";
    private static final String PAYER_TXN = "payerTxn";
    private static final String WRONG_RECORD_ACCOUNT_ID = "Wrong record account ID!";
    private static final String TRANSACTION_NOT_SCHEDULED = "Transaction not scheduled!";
    private static final String WRONG_SCHEDULE_ID = "Wrong schedule ID!";
    private static final String SCHEDULING_MAX_TXN_PER_SECOND = "scheduling.maxTxnPerSecond";
    private static final String WRONG_TRANSACTION_VALID_START = "Wrong transaction valid start!";
    private static final String WRONG_CONSENSUS_TIMESTAMP = "Wrong consensus timestamp!";
    private static final String WRONG_TRANSFER_LIST = "Wrong transfer list!";
    private static final String SIMPLE_UPDATE = "SimpleUpdate";
    private static final String SCHEDULING_WHITELIST = "scheduling.whitelist";
    private static final String PAYING_ACCOUNT_TXN = "payingAccountTxn";
    private static final String LUCKY_RECEIVER = "luckyReceiver";
    private static final String SCHEDULE_CREATE_FEE = "scheduleCreateFee";
    private static final String FAILED_XFER = "failedXfer";
    private static final String WEIRDLY_POPULAR_KEY = "weirdlyPopularKey";
    private static final String SENDER_1 = "sender1";
    private static final String SENDER_2 = "sender2";
    private static final String SENDER_3 = "sender3";
    private static final String WEIRDLY_POPULAR_KEY_TXN = "weirdlyPopularKeyTxn";
    private static final String THREE_SIG_XFER = "threeSigXfer";
    private static final String SCHEDULED_TRANSACTION_SHOULD_NOT_BE_SUCCESSFUL =
            "Scheduled transaction should not be successful!";
    private static final String PAYER = "payer";

    public static void main(String... args) {
        new ScheduleLongTermExecutionSpecs().runSuiteSync();
    }

    @Override
    public List<HapiSpec> getSpecsInSuite() {
        return List.of(
                enableLongTermScheduledTransactions(),
                executionWithDefaultPayerWorks(),
                executionWithCustomPayerWorks(),
                executionWithCustomPayerThatNeverSignsFails(),
                executionWithCustomPayerWhoSignsAtCreationAsPayerWorks(),
                executionWithCustomPayerAndAdminKeyWorks(),
                executionWithDefaultPayerButNoFundsFails(),
                executionWithCustomPayerButNoFundsFails(),
                executionWithDefaultPayerButAccountDeletedFails(),
                executionWithCustomPayerButAccountDeletedFails(),
                executionWithInvalidAccountAmountsFails(),
                executionWithCryptoInsufficientAccountBalanceFails(),
                executionWithCryptoSenderDeletedFails(),
                executionTriggersWithWeirdlyRepeatedKey(),
                scheduledFreezeWorksAsExpected(),
                scheduledFreezeWithUnauthorizedPayerFails(),
                scheduledPermissionedFileUpdateWorksAsExpected(),
                scheduledPermissionedFileUpdateUnauthorizedPayerFails(),
                scheduledSystemDeleteWorksAsExpected(),
                scheduledSystemDeleteUnauthorizedPayerFails(),
                executionWithContractCallWorksAtExpiry(),
                executionWithContractCreateWorksAtExpiry(),
                futureThrottlesAreRespected(),
                disableLongTermScheduledTransactions(),
                waitForExpiryIgnoredWhenLongTermDisabled(),
                expiryIgnoredWhenLongTermDisabled(),
                waitForExpiryIgnoredWhenLongTermDisabledThenEnabled(),
                expiryIgnoredWhenLongTermDisabledThenEnabled(),
                setLongTermScheduledTransactionsToDefault());
    }

    @SuppressWarnings("java:S5960")
    private HapiSpec executionWithCustomPayerWorks() {
        return defaultHapiSpec("ExecutionAtExpiryWithCustomPayerWorks")
                .given(
                        cryptoCreate(PAYING_ACCOUNT),
                        cryptoCreate(RECEIVER),
                        cryptoCreate(SENDER).via(SENDER_TXN))
                .when(
                        scheduleCreate(
                                        BASIC_XFER,
                                        cryptoTransfer(tinyBarsFromTo(SENDER, RECEIVER, 1)))
                                .designatingPayer(PAYING_ACCOUNT)
                                .waitForExpiry()
                                .withRelativeExpiry(SENDER_TXN, 8)
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
                                .hasRelativeExpiry(SENDER_TXN, 8)
                                .hasRecordedScheduledTxn(),
                        sleepFor(9000),
                        cryptoCreate("foo").via(TRIGGERING_TXN),
                        getScheduleInfo(BASIC_XFER).hasCostAnswerPrecheck(INVALID_SCHEDULE_ID),
                        withOpContext(
                                (spec, opLog) -> {
                                    var createTx = getTxnRecord(CREATE_TX);
                                    var signTx = getTxnRecord(SIGN_TX);
                                    var triggeringTx = getTxnRecord(TRIGGERING_TXN);
                                    var triggeredTx = getTxnRecord(CREATE_TX).scheduled();
                                    allRunFor(spec, createTx, signTx, triggeredTx, triggeringTx);

                                    Assertions.assertEquals(
                                            SUCCESS,
                                            triggeredTx
                                                    .getResponseRecord()
                                                    .getReceipt()
                                                    .getStatus(),
                                            SCHEDULED_TRANSACTION_BE_SUCCESSFUL);

                                    Instant triggerTime =
                                            Instant.ofEpochSecond(
                                                    triggeringTx
                                                            .getResponseRecord()
                                                            .getConsensusTimestamp()
                                                            .getSeconds(),
                                                    triggeringTx
                                                            .getResponseRecord()
                                                            .getConsensusTimestamp()
                                                            .getNanos());

                                    Instant triggeredTime =
                                            Instant.ofEpochSecond(
                                                    triggeredTx
                                                            .getResponseRecord()
                                                            .getConsensusTimestamp()
                                                            .getSeconds(),
                                                    triggeredTx
                                                            .getResponseRecord()
                                                            .getConsensusTimestamp()
                                                            .getNanos());

                                    Assertions.assertTrue(
                                            triggerTime.isBefore(triggeredTime),
                                            WRONG_CONSENSUS_TIMESTAMP);

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
                                            createTx.getResponseRecord()
                                                    .getReceipt()
                                                    .getScheduleID(),
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

    private HapiSpec executionWithCustomPayerAndAdminKeyWorks() {
        return defaultHapiSpec("ExecutionAtExpiryWithCustomPayerAndAdminKeyWorks")
                .given(
                        newKeyNamed("adminKey"),
                        cryptoCreate(PAYING_ACCOUNT),
                        cryptoCreate(RECEIVER),
                        cryptoCreate(SENDER).via(SENDER_TXN))
                .when(
                        scheduleCreate(
                                        BASIC_XFER,
                                        cryptoTransfer(tinyBarsFromTo(SENDER, RECEIVER, 1)))
                                .designatingPayer(PAYING_ACCOUNT)
                                .adminKey("adminKey")
                                .waitForExpiry()
                                .withRelativeExpiry(SENDER_TXN, 8)
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
                                .hasRelativeExpiry(SENDER_TXN, 8)
                                .hasRecordedScheduledTxn(),
                        sleepFor(9000),
                        cryptoCreate("foo").via(TRIGGERING_TXN),
                        getScheduleInfo(BASIC_XFER).hasCostAnswerPrecheck(INVALID_SCHEDULE_ID),
                        withOpContext(
                                (spec, opLog) -> {
                                    var createTx = getTxnRecord(CREATE_TX);
                                    var signTx = getTxnRecord(SIGN_TX);
                                    var triggeringTx = getTxnRecord(TRIGGERING_TXN);
                                    var triggeredTx = getTxnRecord(CREATE_TX).scheduled();
                                    allRunFor(spec, createTx, signTx, triggeredTx, triggeringTx);

                                    Assertions.assertEquals(
                                            SUCCESS,
                                            triggeredTx
                                                    .getResponseRecord()
                                                    .getReceipt()
                                                    .getStatus(),
                                            SCHEDULED_TRANSACTION_BE_SUCCESSFUL);

                                    Instant triggerTime =
                                            Instant.ofEpochSecond(
                                                    triggeringTx
                                                            .getResponseRecord()
                                                            .getConsensusTimestamp()
                                                            .getSeconds(),
                                                    triggeringTx
                                                            .getResponseRecord()
                                                            .getConsensusTimestamp()
                                                            .getNanos());

                                    Instant triggeredTime =
                                            Instant.ofEpochSecond(
                                                    triggeredTx
                                                            .getResponseRecord()
                                                            .getConsensusTimestamp()
                                                            .getSeconds(),
                                                    triggeredTx
                                                            .getResponseRecord()
                                                            .getConsensusTimestamp()
                                                            .getNanos());

                                    Assertions.assertTrue(
                                            triggerTime.isBefore(triggeredTime),
                                            WRONG_CONSENSUS_TIMESTAMP);

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
                                            createTx.getResponseRecord()
                                                    .getReceipt()
                                                    .getScheduleID(),
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

    private HapiSpec executionWithCustomPayerWhoSignsAtCreationAsPayerWorks() {
        return defaultHapiSpec("ExecutionAtExpiryWithCustomPayerWhoSignsAtCreationAsPayerWorks")
                .given(
                        cryptoCreate(PAYING_ACCOUNT),
                        cryptoCreate(RECEIVER),
                        cryptoCreate(SENDER).via(SENDER_TXN))
                .when(
                        scheduleCreate(
                                        BASIC_XFER,
                                        cryptoTransfer(tinyBarsFromTo(SENDER, RECEIVER, 1)))
                                .payingWith(PAYING_ACCOUNT)
                                .designatingPayer(PAYING_ACCOUNT)
                                .waitForExpiry()
                                .withRelativeExpiry(SENDER_TXN, 8)
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
                                .hasRelativeExpiry(SENDER_TXN, 8)
                                .hasRecordedScheduledTxn(),
                        sleepFor(9000),
                        cryptoCreate("foo").via(TRIGGERING_TXN),
                        getScheduleInfo(BASIC_XFER).hasCostAnswerPrecheck(INVALID_SCHEDULE_ID),
                        withOpContext(
                                (spec, opLog) -> {
                                    var createTx = getTxnRecord(CREATE_TX);
                                    var signTx = getTxnRecord(SIGN_TX);
                                    var triggeringTx = getTxnRecord(TRIGGERING_TXN);
                                    var triggeredTx = getTxnRecord(CREATE_TX).scheduled();
                                    allRunFor(spec, createTx, signTx, triggeredTx, triggeringTx);

                                    Assertions.assertEquals(
                                            SUCCESS,
                                            triggeredTx
                                                    .getResponseRecord()
                                                    .getReceipt()
                                                    .getStatus(),
                                            SCHEDULED_TRANSACTION_BE_SUCCESSFUL);

                                    Instant triggerTime =
                                            Instant.ofEpochSecond(
                                                    triggeringTx
                                                            .getResponseRecord()
                                                            .getConsensusTimestamp()
                                                            .getSeconds(),
                                                    triggeringTx
                                                            .getResponseRecord()
                                                            .getConsensusTimestamp()
                                                            .getNanos());

                                    Instant triggeredTime =
                                            Instant.ofEpochSecond(
                                                    triggeredTx
                                                            .getResponseRecord()
                                                            .getConsensusTimestamp()
                                                            .getSeconds(),
                                                    triggeredTx
                                                            .getResponseRecord()
                                                            .getConsensusTimestamp()
                                                            .getNanos());

                                    Assertions.assertTrue(
                                            triggerTime.isBefore(triggeredTime),
                                            WRONG_CONSENSUS_TIMESTAMP);

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
                                            createTx.getResponseRecord()
                                                    .getReceipt()
                                                    .getScheduleID(),
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

    public HapiSpec executionWithDefaultPayerWorks() {
        long transferAmount = 1;
        return defaultHapiSpec("ExecutionAtExpiryWithDefaultPayerWorks")
                .given(
                        cryptoCreate(SENDER).via(SENDER_TXN),
                        cryptoCreate(RECEIVER),
                        cryptoCreate(PAYING_ACCOUNT),
                        scheduleCreate(
                                        BASIC_XFER,
                                        cryptoTransfer(
                                                tinyBarsFromTo(SENDER, RECEIVER, transferAmount)))
                                .waitForExpiry()
                                .withRelativeExpiry(SENDER_TXN, 8)
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
                                .hasRelativeExpiry(SENDER_TXN, 8)
                                .hasRecordedScheduledTxn(),
                        sleepFor(9000),
                        cryptoCreate("foo").via(TRIGGERING_TXN),
                        getScheduleInfo(BASIC_XFER).hasCostAnswerPrecheck(INVALID_SCHEDULE_ID),
                        withOpContext(
                                (spec, opLog) -> {
                                    var createTx = getTxnRecord(CREATE_TX);
                                    var signTx = getTxnRecord(SIGN_TX);
                                    var triggeringTx = getTxnRecord(TRIGGERING_TXN);
                                    var triggeredTx = getTxnRecord(CREATE_TX).scheduled();
                                    allRunFor(spec, createTx, signTx, triggeredTx, triggeringTx);

                                    Assertions.assertEquals(
                                            SUCCESS,
                                            triggeredTx
                                                    .getResponseRecord()
                                                    .getReceipt()
                                                    .getStatus(),
                                            SCHEDULED_TRANSACTION_BE_SUCCESSFUL);

                                    Instant triggerTime =
                                            Instant.ofEpochSecond(
                                                    triggeringTx
                                                            .getResponseRecord()
                                                            .getConsensusTimestamp()
                                                            .getSeconds(),
                                                    triggeringTx
                                                            .getResponseRecord()
                                                            .getConsensusTimestamp()
                                                            .getNanos());

                                    Instant triggeredTime =
                                            Instant.ofEpochSecond(
                                                    triggeredTx
                                                            .getResponseRecord()
                                                            .getConsensusTimestamp()
                                                            .getSeconds(),
                                                    triggeredTx
                                                            .getResponseRecord()
                                                            .getConsensusTimestamp()
                                                            .getNanos());

                                    Assertions.assertTrue(
                                            triggerTime.isBefore(triggeredTime),
                                            WRONG_CONSENSUS_TIMESTAMP);

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
                                            createTx.getResponseRecord()
                                                    .getReceipt()
                                                    .getScheduleID(),
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

    public HapiSpec executionWithContractCallWorksAtExpiry() {
        return defaultHapiSpec("ExecutionWithContractCallWorksAtExpiry")
                .given(
                        overriding(SCHEDULING_WHITELIST, "ContractCall"),
                        uploadInitCode(SIMPLE_UPDATE),
                        contractCreate(SIMPLE_UPDATE).gas(500_000L),
                        cryptoCreate(PAYING_ACCOUNT)
                                .balance(1000000000000L)
                                .via(PAYING_ACCOUNT_TXN))
                .when(
                        scheduleCreate(
                                        BASIC_XFER,
                                        contractCall(SIMPLE_UPDATE, "set", 5, 42).gas(300000L))
                                .waitForExpiry()
                                .withRelativeExpiry(PAYING_ACCOUNT_TXN, 8)
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
                                .hasRelativeExpiry(PAYING_ACCOUNT_TXN, 8)
                                .hasRecordedScheduledTxn(),
                        sleepFor(9000),
                        cryptoCreate("foo").via(TRIGGERING_TXN),
                        getScheduleInfo(BASIC_XFER).hasCostAnswerPrecheck(INVALID_SCHEDULE_ID),
                        overriding(
                                SCHEDULING_WHITELIST,
                                HapiSpecSetup.getDefaultNodeProps().get(SCHEDULING_WHITELIST)),
                        getAccountBalance(PAYING_ACCOUNT)
                                .hasTinyBars(
                                        spec ->
                                                bal ->
                                                        bal < 1000000000000L
                                                                ? Optional.empty()
                                                                : Optional.of("didnt change")),
                        withOpContext(
                                (spec, opLog) -> {
                                    var triggeredTx = getTxnRecord(CREATE_TX).scheduled();
                                    allRunFor(spec, triggeredTx);

                                    Assertions.assertEquals(
                                            SUCCESS,
                                            triggeredTx
                                                    .getResponseRecord()
                                                    .getReceipt()
                                                    .getStatus(),
                                            SCHEDULED_TRANSACTION_BE_SUCCESSFUL);

                                    Assertions.assertTrue(
                                            triggeredTx
                                                            .getResponseRecord()
                                                            .getContractCallResult()
                                                            .getContractCallResult()
                                                            .size()
                                                    >= 0);
                                }));
    }

    public HapiSpec executionWithContractCreateWorksAtExpiry() {
        return defaultHapiSpec("ExecutionWithContractCreateWorksAtExpiry")
                .given(
                        overriding(SCHEDULING_WHITELIST, "ContractCreate"),
                        uploadInitCode(SIMPLE_UPDATE),
                        cryptoCreate(PAYING_ACCOUNT)
                                .balance(1000000000000L)
                                .via(PAYING_ACCOUNT_TXN))
                .when(
                        scheduleCreate(
                                        BASIC_XFER,
                                        contractCreate(SIMPLE_UPDATE)
                                                .gas(500_000L)
                                                .adminKey(PAYING_ACCOUNT))
                                .waitForExpiry()
                                .withRelativeExpiry(PAYING_ACCOUNT_TXN, 8)
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
                                .hasRelativeExpiry(PAYING_ACCOUNT_TXN, 8)
                                .hasRecordedScheduledTxn(),
                        sleepFor(9000),
                        cryptoCreate("foo").via(TRIGGERING_TXN),
                        getScheduleInfo(BASIC_XFER).hasCostAnswerPrecheck(INVALID_SCHEDULE_ID),
                        overriding(
                                SCHEDULING_WHITELIST,
                                HapiSpecSetup.getDefaultNodeProps().get(SCHEDULING_WHITELIST)),
                        getAccountBalance(PAYING_ACCOUNT)
                                .hasTinyBars(
                                        spec ->
                                                bal ->
                                                        bal < 1000000000000L
                                                                ? Optional.empty()
                                                                : Optional.of("didnt change")),
                        withOpContext(
                                (spec, opLog) -> {
                                    var triggeredTx = getTxnRecord(CREATE_TX).scheduled();
                                    allRunFor(spec, triggeredTx);

                                    Assertions.assertEquals(
                                            SUCCESS,
                                            triggeredTx
                                                    .getResponseRecord()
                                                    .getReceipt()
                                                    .getStatus(),
                                            SCHEDULED_TRANSACTION_BE_SUCCESSFUL);

                                    Assertions.assertTrue(
                                            triggeredTx
                                                    .getResponseRecord()
                                                    .getReceipt()
                                                    .hasContractID());

                                    Assertions.assertTrue(
                                            triggeredTx
                                                            .getResponseRecord()
                                                            .getContractCreateResult()
                                                            .getContractCallResult()
                                                            .size()
                                                    >= 0);
                                }));
    }

    public HapiSpec executionWithDefaultPayerButNoFundsFails() {
        long balance = 10_000_000L;
        long noBalance = 0L;
        long transferAmount = 1L;
        return defaultHapiSpec("ExecutionAtExpiryWithDefaultPayerButNoFundsFails")
                .given(
                        cryptoCreate(PAYING_ACCOUNT).balance(balance),
                        cryptoCreate(LUCKY_RECEIVER),
                        cryptoCreate(SENDER).balance(transferAmount).via(SENDER_TXN),
                        cryptoCreate(RECEIVER).balance(noBalance),
                        scheduleCreate(
                                        BASIC_XFER,
                                        cryptoTransfer(
                                                tinyBarsFromTo(SENDER, RECEIVER, transferAmount)))
                                .waitForExpiry()
                                .withRelativeExpiry(SENDER_TXN, 8)
                                .payingWith(PAYING_ACCOUNT)
                                .recordingScheduledTxn()
                                .via(CREATE_TX),
                        recordFeeAmount(CREATE_TX, SCHEDULE_CREATE_FEE))
                .when(
                        cryptoTransfer(
                                tinyBarsFromTo(
                                        PAYING_ACCOUNT,
                                        LUCKY_RECEIVER,
                                        (spec -> {
                                            long scheduleCreateFee =
                                                    spec.registry().getAmount(SCHEDULE_CREATE_FEE);
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
                                .hasRelativeExpiry(SENDER_TXN, 8)
                                .hasRecordedScheduledTxn(),
                        sleepFor(9000),
                        cryptoCreate("foo").via(TRIGGERING_TXN),
                        getScheduleInfo(BASIC_XFER).hasCostAnswerPrecheck(INVALID_SCHEDULE_ID),
                        getAccountBalance(SENDER).hasTinyBars(transferAmount),
                        getAccountBalance(RECEIVER).hasTinyBars(noBalance),
                        withOpContext(
                                (spec, opLog) -> {
                                    var triggeredTx = getTxnRecord(CREATE_TX).scheduled();

                                    allRunFor(spec, triggeredTx);

                                    Assertions.assertEquals(
                                            INSUFFICIENT_PAYER_BALANCE,
                                            triggeredTx
                                                    .getResponseRecord()
                                                    .getReceipt()
                                                    .getStatus(),
                                            SCHEDULED_TRANSACTION_SHOULD_NOT_BE_SUCCESSFUL);
                                }));
    }

    public HapiSpec executionWithCustomPayerThatNeverSignsFails() {
        long transferAmount = 1;
        return defaultHapiSpec("ExecutionWithCustomPayerThatNeverSignsFails")
                .given(
                        cryptoCreate(PAYING_ACCOUNT),
                        cryptoCreate(SENDER).via(SENDER_TXN),
                        cryptoCreate(RECEIVER),
                        scheduleCreate(
                                        BASIC_XFER,
                                        cryptoTransfer(
                                                tinyBarsFromTo(SENDER, RECEIVER, transferAmount)))
                                .waitForExpiry()
                                .withRelativeExpiry(SENDER_TXN, 8)
                                .recordingScheduledTxn()
                                .designatingPayer(PAYING_ACCOUNT)
                                .via(CREATE_TX))
                .when(
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
                                .hasRelativeExpiry(SENDER_TXN, 8)
                                .hasRecordedScheduledTxn(),
                        sleepFor(9000),
                        cryptoCreate("foo").via(TRIGGERING_TXN),
                        getScheduleInfo(BASIC_XFER).hasCostAnswerPrecheck(INVALID_SCHEDULE_ID),
                        getTxnRecord(CREATE_TX)
                                .scheduled()
                                .hasAnswerOnlyPrecheck(RECORD_NOT_FOUND));
    }

    public HapiSpec executionWithCustomPayerButNoFundsFails() {
        long balance = 0L;
        long transferAmount = 1;
        return defaultHapiSpec("ExecutionAtExpiryWithCustomPayerButNoFundsFails")
                .given(
                        cryptoCreate(PAYING_ACCOUNT).balance(balance),
                        cryptoCreate(SENDER).via(SENDER_TXN),
                        cryptoCreate(RECEIVER),
                        scheduleCreate(
                                        BASIC_XFER,
                                        cryptoTransfer(
                                                tinyBarsFromTo(SENDER, RECEIVER, transferAmount)))
                                .waitForExpiry()
                                .withRelativeExpiry(SENDER_TXN, 8)
                                .recordingScheduledTxn()
                                .designatingPayer(PAYING_ACCOUNT)
                                .via(CREATE_TX))
                .when(
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
                                .hasRelativeExpiry(SENDER_TXN, 8)
                                .hasRecordedScheduledTxn(),
                        sleepFor(9000),
                        cryptoCreate("foo").via(TRIGGERING_TXN),
                        getScheduleInfo(BASIC_XFER).hasCostAnswerPrecheck(INVALID_SCHEDULE_ID),
                        withOpContext(
                                (spec, opLog) -> {
                                    var triggeredTx = getTxnRecord(CREATE_TX).scheduled();

                                    allRunFor(spec, triggeredTx);

                                    Assertions.assertEquals(
                                            INSUFFICIENT_PAYER_BALANCE,
                                            triggeredTx
                                                    .getResponseRecord()
                                                    .getReceipt()
                                                    .getStatus(),
                                            SCHEDULED_TRANSACTION_SHOULD_NOT_BE_SUCCESSFUL);
                                }));
    }

    public HapiSpec executionWithDefaultPayerButAccountDeletedFails() {
        long balance = 10_000_000L;
        long noBalance = 0L;
        long transferAmount = 1L;
        return defaultHapiSpec("ExecutionAtExpiryWithDefaultPayerButAccountDeletedFails")
                .given(
                        cryptoCreate(PAYING_ACCOUNT).balance(balance),
                        cryptoCreate(LUCKY_RECEIVER),
                        cryptoCreate(SENDER).balance(transferAmount).via(SENDER_TXN),
                        cryptoCreate(RECEIVER).balance(noBalance),
                        scheduleCreate(
                                        BASIC_XFER,
                                        cryptoTransfer(
                                                tinyBarsFromTo(SENDER, RECEIVER, transferAmount)))
                                .waitForExpiry()
                                .withRelativeExpiry(SENDER_TXN, 8)
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
                                .hasRelativeExpiry(SENDER_TXN, 8)
                                .hasRecordedScheduledTxn(),
                        sleepFor(9000),
                        cryptoCreate("foo").via(TRIGGERING_TXN),
                        getScheduleInfo(BASIC_XFER).hasCostAnswerPrecheck(INVALID_SCHEDULE_ID),
                        getAccountBalance(SENDER).hasTinyBars(transferAmount),
                        getAccountBalance(RECEIVER).hasTinyBars(noBalance),
                        getTxnRecord(CREATE_TX).scheduled().hasCostAnswerPrecheck(ACCOUNT_DELETED));
    }

    public HapiSpec executionWithCustomPayerButAccountDeletedFails() {
        long balance = 10_000_000L;
        long noBalance = 0L;
        long transferAmount = 1;
        return defaultHapiSpec("ExecutionAtExpiryWithCustomPayerButAccountDeletedFails")
                .given(
                        cryptoCreate(PAYING_ACCOUNT).balance(balance),
                        cryptoCreate(SENDER).balance(transferAmount).via(SENDER_TXN),
                        cryptoCreate(RECEIVER).balance(noBalance),
                        scheduleCreate(
                                        BASIC_XFER,
                                        cryptoTransfer(
                                                tinyBarsFromTo(SENDER, RECEIVER, transferAmount)))
                                .waitForExpiry()
                                .withRelativeExpiry(SENDER_TXN, 8)
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
                                .hasRelativeExpiry(SENDER_TXN, 8)
                                .hasRecordedScheduledTxn(),
                        sleepFor(9000),
                        cryptoCreate("foo").via(TRIGGERING_TXN),
                        getScheduleInfo(BASIC_XFER).hasCostAnswerPrecheck(INVALID_SCHEDULE_ID),
                        getAccountBalance(SENDER).hasTinyBars(transferAmount),
                        getAccountBalance(RECEIVER).hasTinyBars(noBalance),
                        withOpContext(
                                (spec, opLog) -> {
                                    var triggeredTx = getTxnRecord(CREATE_TX).scheduled();

                                    allRunFor(spec, triggeredTx);

                                    Assertions.assertEquals(
                                            INSUFFICIENT_PAYER_BALANCE,
                                            triggeredTx
                                                    .getResponseRecord()
                                                    .getReceipt()
                                                    .getStatus(),
                                            SCHEDULED_TRANSACTION_SHOULD_NOT_BE_SUCCESSFUL);
                                }));
    }

    public HapiSpec executionWithInvalidAccountAmountsFails() {
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
                                                tinyBarsFromToWithInvalidAmounts(
                                                        SENDER, RECEIVER, transferAmount)))
                                .waitForExpiry()
                                .withRelativeExpiry(SENDER_TXN, 8)
                                .designatingPayer(PAYING_ACCOUNT)
                                .recordingScheduledTxn()
                                .via(CREATE_TX))
                .when(
                        scheduleSign(FAILED_XFER)
                                .alsoSigningWith(SENDER, PAYING_ACCOUNT)
                                .via(SIGN_TX)
                                .hasKnownStatus(SUCCESS))
                .then(
                        getScheduleInfo(FAILED_XFER)
                                .hasScheduleId(FAILED_XFER)
                                .hasWaitForExpiry()
                                .isNotExecuted()
                                .isNotDeleted()
                                .hasRelativeExpiry(SENDER_TXN, 8)
                                .hasRecordedScheduledTxn(),
                        sleepFor(9000),
                        cryptoCreate("foo").via(TRIGGERING_TXN),
                        getScheduleInfo(FAILED_XFER).hasCostAnswerPrecheck(INVALID_SCHEDULE_ID),
                        getAccountBalance(SENDER).hasTinyBars(senderBalance),
                        getAccountBalance(RECEIVER).hasTinyBars(noBalance),
                        withOpContext(
                                (spec, opLog) -> {
                                    var triggeredTx = getTxnRecord(CREATE_TX).scheduled();

                                    allRunFor(spec, triggeredTx);

                                    Assertions.assertEquals(
                                            INVALID_ACCOUNT_AMOUNTS,
                                            triggeredTx
                                                    .getResponseRecord()
                                                    .getReceipt()
                                                    .getStatus(),
                                            SCHEDULED_TRANSACTION_SHOULD_NOT_BE_SUCCESSFUL);
                                }));
    }

    public HapiSpec executionWithCryptoInsufficientAccountBalanceFails() {
        long noBalance = 0L;
        long senderBalance = 100L;
        long transferAmount = 101L;
        long payerBalance = 1_000_000L;
        return defaultHapiSpec("ExecutionAtExpiryWithCryptoInsufficientAccountBalanceFails")
                .given(
                        cryptoCreate(PAYING_ACCOUNT).balance(payerBalance),
                        cryptoCreate(SENDER).balance(senderBalance).via(SENDER_TXN),
                        cryptoCreate(RECEIVER).balance(noBalance),
                        scheduleCreate(
                                        FAILED_XFER,
                                        cryptoTransfer(
                                                tinyBarsFromTo(SENDER, RECEIVER, transferAmount)))
                                .waitForExpiry()
                                .withRelativeExpiry(SENDER_TXN, 8)
                                .designatingPayer(PAYING_ACCOUNT)
                                .recordingScheduledTxn()
                                .via(CREATE_TX))
                .when(
                        scheduleSign(FAILED_XFER)
                                .alsoSigningWith(SENDER, PAYING_ACCOUNT)
                                .via(SIGN_TX)
                                .hasKnownStatus(SUCCESS))
                .then(
                        getScheduleInfo(FAILED_XFER)
                                .hasScheduleId(BASIC_XFER)
                                .hasWaitForExpiry()
                                .isNotExecuted()
                                .isNotDeleted()
                                .hasRelativeExpiry(SENDER_TXN, 8)
                                .hasRecordedScheduledTxn(),
                        sleepFor(9000),
                        cryptoCreate("foo").via(TRIGGERING_TXN),
                        getScheduleInfo(FAILED_XFER).hasCostAnswerPrecheck(INVALID_SCHEDULE_ID),
                        getAccountBalance(SENDER).hasTinyBars(senderBalance),
                        getAccountBalance(RECEIVER).hasTinyBars(noBalance),
                        withOpContext(
                                (spec, opLog) -> {
                                    var triggeredTx = getTxnRecord(CREATE_TX).scheduled();

                                    allRunFor(spec, triggeredTx);

                                    Assertions.assertEquals(
                                            INSUFFICIENT_ACCOUNT_BALANCE,
                                            triggeredTx
                                                    .getResponseRecord()
                                                    .getReceipt()
                                                    .getStatus(),
                                            SCHEDULED_TRANSACTION_SHOULD_NOT_BE_SUCCESSFUL);
                                }));
    }

    public HapiSpec executionWithCryptoSenderDeletedFails() {
        long noBalance = 0L;
        long senderBalance = 100L;
        long transferAmount = 101L;
        long payerBalance = 1_000_000L;
        return defaultHapiSpec("ExecutionAtExpiryWithCryptoSenderDeletedFails")
                .given(
                        cryptoCreate(PAYING_ACCOUNT).balance(payerBalance),
                        cryptoCreate(SENDER).balance(senderBalance).via(SENDER_TXN),
                        cryptoCreate(RECEIVER).balance(noBalance),
                        scheduleCreate(
                                        FAILED_XFER,
                                        cryptoTransfer(
                                                tinyBarsFromTo(SENDER, RECEIVER, transferAmount)))
                                .waitForExpiry()
                                .withRelativeExpiry(SENDER_TXN, 8)
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
                                .hasRelativeExpiry(SENDER_TXN, 8)
                                .hasRecordedScheduledTxn(),
                        sleepFor(9000),
                        cryptoCreate("foo").via(TRIGGERING_TXN),
                        getScheduleInfo(FAILED_XFER).hasCostAnswerPrecheck(INVALID_SCHEDULE_ID),
                        getAccountBalance(RECEIVER).hasTinyBars(noBalance),
                        withOpContext(
                                (spec, opLog) -> {
                                    var triggeredTx = getTxnRecord(CREATE_TX).scheduled();

                                    allRunFor(spec, triggeredTx);

                                    Assertions.assertEquals(
                                            ACCOUNT_DELETED,
                                            triggeredTx
                                                    .getResponseRecord()
                                                    .getReceipt()
                                                    .getStatus(),
                                            SCHEDULED_TRANSACTION_SHOULD_NOT_BE_SUCCESSFUL);
                                }));
    }

    public HapiSpec executionTriggersWithWeirdlyRepeatedKey() {
        String schedule = "dupKeyXfer";

        return defaultHapiSpec("ExecutionAtExpiryTriggersWithWeirdlyRepeatedKey")
                .given(
                        cryptoCreate(WEIRDLY_POPULAR_KEY).via(WEIRDLY_POPULAR_KEY_TXN),
                        cryptoCreate(SENDER_1).key(WEIRDLY_POPULAR_KEY).balance(1L),
                        cryptoCreate(SENDER_2).key(WEIRDLY_POPULAR_KEY).balance(1L),
                        cryptoCreate(SENDER_3).key(WEIRDLY_POPULAR_KEY).balance(1L),
                        cryptoCreate(RECEIVER).balance(0L),
                        scheduleCreate(
                                        schedule,
                                        cryptoTransfer(
                                                tinyBarsFromTo(SENDER_1, RECEIVER, 1L),
                                                tinyBarsFromTo(SENDER_2, RECEIVER, 1L),
                                                tinyBarsFromTo(SENDER_3, RECEIVER, 1L)))
                                .waitForExpiry()
                                .withRelativeExpiry(WEIRDLY_POPULAR_KEY_TXN, 8)
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
                                .hasRelativeExpiry(WEIRDLY_POPULAR_KEY_TXN, 8)
                                .hasRecordedScheduledTxn(),
                        sleepFor(9000),
                        cryptoCreate("foo").via(TRIGGERING_TXN),
                        getScheduleInfo(schedule).hasCostAnswerPrecheck(INVALID_SCHEDULE_ID),
                        getAccountBalance(SENDER_1).hasTinyBars(0L),
                        getAccountBalance(SENDER_2).hasTinyBars(0L),
                        getAccountBalance(SENDER_3).hasTinyBars(0L),
                        getAccountBalance(RECEIVER).hasTinyBars(3L),
                        scheduleSign(schedule)
                                .alsoSigningWith(WEIRDLY_POPULAR_KEY)
                                .hasPrecheck(INVALID_SCHEDULE_ID));
    }

    private HapiSpec scheduledFreezeWorksAsExpected() {

        final byte[] poeticUpgradeHash = getPoeticUpgradeHash();

        return defaultHapiSpec("ScheduledFreezeWorksAsExpectedAtExpiry")
                .given(
                        cryptoCreate(PAYING_ACCOUNT).via(PAYER_TXN),
                        overriding(SCHEDULING_WHITELIST, "Freeze"),
                        fileUpdate(standardUpdateFile)
                                .signedBy(FREEZE_ADMIN)
                                .path(poeticUpgradeLoc)
                                .payingWith(FREEZE_ADMIN),
                        scheduleCreate(
                                        VALID_SCHEDULE,
                                        prepareUpgrade()
                                                .withUpdateFile(standardUpdateFile)
                                                .havingHash(poeticUpgradeHash))
                                .withEntityMemo(randomUppercase(100))
                                .designatingPayer(GENESIS)
                                .payingWith(PAYING_ACCOUNT)
                                .waitForExpiry()
                                .recordingScheduledTxn()
                                .withRelativeExpiry(PAYER_TXN, 8)
                                .via(SUCCESS_TXN))
                .when(
                        scheduleSign(VALID_SCHEDULE)
                                .alsoSigningWith(GENESIS)
                                .payingWith(PAYING_ACCOUNT)
                                .hasKnownStatus(SUCCESS))
                .then(
                        getScheduleInfo(VALID_SCHEDULE)
                                .hasScheduleId(VALID_SCHEDULE)
                                .hasWaitForExpiry()
                                .isNotExecuted()
                                .isNotDeleted()
                                .hasRelativeExpiry(PAYER_TXN, 8)
                                .hasRecordedScheduledTxn(),
                        sleepFor(9000),
                        cryptoCreate("foo").via(TRIGGERING_TXN),
                        getScheduleInfo(VALID_SCHEDULE).hasCostAnswerPrecheck(INVALID_SCHEDULE_ID),
                        freezeAbort().payingWith(GENESIS),
                        overriding(
                                SCHEDULING_WHITELIST,
                                HapiSpecSetup.getDefaultNodeProps().get(SCHEDULING_WHITELIST)),
                        withOpContext(
                                (spec, opLog) -> {
                                    var triggeredTx = getTxnRecord(SUCCESS_TXN).scheduled();
                                    allRunFor(spec, triggeredTx);

                                    Assertions.assertEquals(
                                            SUCCESS,
                                            triggeredTx
                                                    .getResponseRecord()
                                                    .getReceipt()
                                                    .getStatus(),
                                            SCHEDULED_TRANSACTION_BE_SUCCESSFUL);
                                }));
    }

    private HapiSpec scheduledFreezeWithUnauthorizedPayerFails() {

        final byte[] poeticUpgradeHash = getPoeticUpgradeHash();

        return defaultHapiSpec("ScheduledFreezeWithUnauthorizedPayerFailsAtExpiry")
                .given(
                        cryptoCreate(PAYING_ACCOUNT).via(PAYER_TXN),
                        cryptoCreate(PAYING_ACCOUNT_2),
                        overriding(SCHEDULING_WHITELIST, "Freeze"),
                        fileUpdate(standardUpdateFile)
                                .signedBy(FREEZE_ADMIN)
                                .path(poeticUpgradeLoc)
                                .payingWith(FREEZE_ADMIN))
                .when()
                .then(
                        scheduleCreate(
                                        VALID_SCHEDULE,
                                        prepareUpgrade()
                                                .withUpdateFile(standardUpdateFile)
                                                .havingHash(poeticUpgradeHash))
                                .withEntityMemo(randomUppercase(100))
                                .designatingPayer(PAYING_ACCOUNT_2)
                                .waitForExpiry()
                                .withRelativeExpiry(PAYER_TXN, 8)
                                .payingWith(PAYING_ACCOUNT)
                                // future throttles will be exceeded because there is no throttle
                                // for freeze
                                // and the custom payer is not exempt from throttles like and admin
                                // user would be
                                .hasKnownStatus(SCHEDULE_FUTURE_THROTTLE_EXCEEDED),
                        overriding(
                                SCHEDULING_WHITELIST,
                                HapiSpecSetup.getDefaultNodeProps().get(SCHEDULING_WHITELIST)));
    }

    private HapiSpec scheduledPermissionedFileUpdateWorksAsExpected() {

        return defaultHapiSpec("ScheduledPermissionedFileUpdateWorksAsExpectedAtExpiry")
                .given(
                        cryptoCreate(PAYING_ACCOUNT).via(PAYER_TXN),
                        overriding(SCHEDULING_WHITELIST, "FileUpdate"),
                        scheduleCreate(
                                        VALID_SCHEDULE,
                                        fileUpdate(standardUpdateFile).contents("fooo!"))
                                .withEntityMemo(randomUppercase(100))
                                .designatingPayer(FREEZE_ADMIN)
                                .payingWith(PAYING_ACCOUNT)
                                .waitForExpiry()
                                .withRelativeExpiry(PAYER_TXN, 8)
                                .recordingScheduledTxn()
                                .via(SUCCESS_TXN))
                .when(
                        scheduleSign(VALID_SCHEDULE)
                                .alsoSigningWith(FREEZE_ADMIN)
                                .payingWith(PAYING_ACCOUNT)
                                .hasKnownStatus(SUCCESS))
                .then(
                        getScheduleInfo(VALID_SCHEDULE)
                                .hasScheduleId(VALID_SCHEDULE)
                                .hasWaitForExpiry()
                                .isNotExecuted()
                                .isNotDeleted()
                                .hasRelativeExpiry(PAYER_TXN, 8)
                                .hasRecordedScheduledTxn(),
                        sleepFor(9000),
                        cryptoCreate("foo").via(TRIGGERING_TXN),
                        getScheduleInfo(VALID_SCHEDULE).hasCostAnswerPrecheck(INVALID_SCHEDULE_ID),
                        overriding(
                                SCHEDULING_WHITELIST,
                                HapiSpecSetup.getDefaultNodeProps().get(SCHEDULING_WHITELIST)),
                        withOpContext(
                                (spec, opLog) -> {
                                    var triggeredTx = getTxnRecord(SUCCESS_TXN).scheduled();
                                    allRunFor(spec, triggeredTx);

                                    Assertions.assertEquals(
                                            SUCCESS,
                                            triggeredTx
                                                    .getResponseRecord()
                                                    .getReceipt()
                                                    .getStatus(),
                                            SCHEDULED_TRANSACTION_BE_SUCCESSFUL);
                                }));
    }

    private HapiSpec scheduledPermissionedFileUpdateUnauthorizedPayerFails() {

        return defaultHapiSpec("ScheduledPermissionedFileUpdateUnauthorizedPayerFailsAtExpiry")
                .given(
                        cryptoCreate(PAYING_ACCOUNT).via(PAYER_TXN),
                        cryptoCreate(PAYING_ACCOUNT_2),
                        overriding(SCHEDULING_WHITELIST, "FileUpdate"),
                        scheduleCreate(
                                        VALID_SCHEDULE,
                                        fileUpdate(standardUpdateFile).contents("fooo!"))
                                .withEntityMemo(randomUppercase(100))
                                .designatingPayer(PAYING_ACCOUNT_2)
                                .payingWith(PAYING_ACCOUNT)
                                .waitForExpiry()
                                .withRelativeExpiry(PAYER_TXN, 8)
                                .recordingScheduledTxn()
                                .via(SUCCESS_TXN))
                .when(
                        scheduleSign(VALID_SCHEDULE)
                                .alsoSigningWith(PAYING_ACCOUNT_2, FREEZE_ADMIN)
                                .payingWith(PAYING_ACCOUNT)
                                .hasKnownStatus(SUCCESS))
                .then(
                        getScheduleInfo(VALID_SCHEDULE)
                                .hasScheduleId(VALID_SCHEDULE)
                                .hasWaitForExpiry()
                                .isNotExecuted()
                                .isNotDeleted()
                                .hasRelativeExpiry(PAYER_TXN, 8)
                                .hasRecordedScheduledTxn(),
                        sleepFor(9000),
                        cryptoCreate("foo").via(TRIGGERING_TXN),
                        getScheduleInfo(VALID_SCHEDULE).hasCostAnswerPrecheck(INVALID_SCHEDULE_ID),
                        overriding(
                                SCHEDULING_WHITELIST,
                                HapiSpecSetup.getDefaultNodeProps().get(SCHEDULING_WHITELIST)),
                        withOpContext(
                                (spec, opLog) -> {
                                    var triggeredTx = getTxnRecord(SUCCESS_TXN).scheduled();
                                    allRunFor(spec, triggeredTx);

                                    Assertions.assertEquals(
                                            AUTHORIZATION_FAILED,
                                            triggeredTx
                                                    .getResponseRecord()
                                                    .getReceipt()
                                                    .getStatus(),
                                            "Scheduled transaction be AUTHORIZATION_FAILED!");
                                }));
    }

    private HapiSpec scheduledSystemDeleteWorksAsExpected() {

        return defaultHapiSpec("ScheduledSystemDeleteWorksAsExpectedAtExpiry")
                .given(
                        cryptoCreate(PAYING_ACCOUNT).via(PAYER_TXN),
                        fileCreate("misc").lifetime(THREE_MONTHS_IN_SECONDS).contents(ORIG_FILE),
                        overriding(SCHEDULING_WHITELIST, "SystemDelete"),
                        scheduleCreate(VALID_SCHEDULE, systemFileDelete("misc").updatingExpiry(1L))
                                .withEntityMemo(randomUppercase(100))
                                .designatingPayer(SYSTEM_DELETE_ADMIN)
                                .payingWith(PAYING_ACCOUNT)
                                .waitForExpiry()
                                .withRelativeExpiry(PAYER_TXN, 8)
                                .recordingScheduledTxn()
                                .via(SUCCESS_TXN))
                .when(
                        scheduleSign(VALID_SCHEDULE)
                                .alsoSigningWith(SYSTEM_DELETE_ADMIN)
                                .payingWith(PAYING_ACCOUNT)
                                .hasKnownStatus(SUCCESS))
                .then(
                        getScheduleInfo(VALID_SCHEDULE)
                                .hasScheduleId(VALID_SCHEDULE)
                                .hasWaitForExpiry()
                                .isNotExecuted()
                                .isNotDeleted()
                                .hasRelativeExpiry(PAYER_TXN, 8)
                                .hasRecordedScheduledTxn(),
                        sleepFor(9000),
                        cryptoCreate("foo").via(TRIGGERING_TXN),
                        getScheduleInfo(VALID_SCHEDULE).hasCostAnswerPrecheck(INVALID_SCHEDULE_ID),
                        overriding(
                                SCHEDULING_WHITELIST,
                                HapiSpecSetup.getDefaultNodeProps().get(SCHEDULING_WHITELIST)),
                        getFileInfo("misc")
                                .nodePayment(1_234L)
                                .hasAnswerOnlyPrecheck(INVALID_FILE_ID),
                        withOpContext(
                                (spec, opLog) -> {
                                    var triggeredTx = getTxnRecord(SUCCESS_TXN).scheduled();
                                    allRunFor(spec, triggeredTx);

                                    Assertions.assertEquals(
                                            SUCCESS,
                                            triggeredTx
                                                    .getResponseRecord()
                                                    .getReceipt()
                                                    .getStatus(),
                                            SCHEDULED_TRANSACTION_BE_SUCCESSFUL);
                                }));
    }

    private HapiSpec scheduledSystemDeleteUnauthorizedPayerFails() {

        return defaultHapiSpec("ScheduledSystemDeleteUnauthorizedPayerFailsAtExpiry")
                .given(
                        cryptoCreate(PAYING_ACCOUNT).via(PAYER_TXN),
                        cryptoCreate(PAYING_ACCOUNT_2),
                        fileCreate("misc").lifetime(THREE_MONTHS_IN_SECONDS).contents(ORIG_FILE),
                        overriding(SCHEDULING_WHITELIST, "SystemDelete"))
                .when()
                .then(
                        scheduleCreate(VALID_SCHEDULE, systemFileDelete("misc").updatingExpiry(1L))
                                .withEntityMemo(randomUppercase(100))
                                .designatingPayer(PAYING_ACCOUNT_2)
                                .payingWith(PAYING_ACCOUNT)
                                .waitForExpiry()
                                .withRelativeExpiry(PAYER_TXN, 8)
                                // future throttles will be exceeded because there is no throttle
                                // for system delete
                                // and the custom payer is not exempt from throttles like and admin
                                // user would be
                                .hasKnownStatus(SCHEDULE_FUTURE_THROTTLE_EXCEEDED),
                        overriding(
                                SCHEDULING_WHITELIST,
                                HapiSpecSetup.getDefaultNodeProps().get(SCHEDULING_WHITELIST)));
    }

    public HapiSpec waitForExpiryIgnoredWhenLongTermDisabled() {

        return defaultHapiSpec("WaitForExpiryIgnoredWhenLongTermDisabled")
                .given(
                        cryptoCreate(PAYER).balance(ONE_HBAR),
                        cryptoCreate(SENDER).balance(1L),
                        cryptoCreate(RECEIVER).balance(0L).receiverSigRequired(true))
                .when(
                        scheduleCreate(
                                        THREE_SIG_XFER,
                                        cryptoTransfer(tinyBarsFromTo(SENDER, RECEIVER, 1))
                                                .fee(ONE_HBAR))
                                .waitForExpiry()
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

    public HapiSpec expiryIgnoredWhenLongTermDisabled() {

        return defaultHapiSpec("ExpiryIgnoredWhenLongTermDisabled")
                .given(
                        cryptoCreate(PAYER).balance(ONE_HBAR),
                        cryptoCreate(SENDER).balance(1L).via(SENDER_TXN),
                        cryptoCreate(RECEIVER).balance(0L).receiverSigRequired(true))
                .when(
                        overriding(LEDGER_SCHEDULE_TX_EXPIRY_TIME_SECS, "" + 5),
                        scheduleCreate(
                                        THREE_SIG_XFER,
                                        cryptoTransfer(tinyBarsFromTo(SENDER, RECEIVER, 1))
                                                .fee(ONE_HBAR))
                                .withRelativeExpiry(SENDER_TXN, 500)
                                .designatingPayer(PAYER)
                                .alsoSigningWith(SENDER, RECEIVER))
                .then(
                        overriding(LEDGER_SCHEDULE_TX_EXPIRY_TIME_SECS, DEFAULT_TX_EXPIRY),
                        getScheduleInfo(THREE_SIG_XFER)
                                .hasScheduleId(THREE_SIG_XFER)
                                .isNotExecuted()
                                .isNotDeleted(),
                        sleepFor(9000),
                        cryptoCreate("foo"),
                        getScheduleInfo(THREE_SIG_XFER).hasCostAnswerPrecheck(INVALID_SCHEDULE_ID),
                        getAccountBalance(RECEIVER).hasTinyBars(0L));
    }

    public HapiSpec waitForExpiryIgnoredWhenLongTermDisabledThenEnabled() {

        return defaultHapiSpec("WaitForExpiryIgnoredWhenLongTermDisabledThenEnabled")
                .given(
                        cryptoCreate(PAYER).balance(ONE_HBAR),
                        cryptoCreate(SENDER).balance(1L),
                        cryptoCreate(RECEIVER).balance(0L).receiverSigRequired(true))
                .when(
                        scheduleCreate(
                                        THREE_SIG_XFER,
                                        cryptoTransfer(tinyBarsFromTo(SENDER, RECEIVER, 1))
                                                .fee(ONE_HBAR))
                                .waitForExpiry()
                                .designatingPayer(PAYER)
                                .alsoSigningWith(SENDER, RECEIVER),
                        getAccountBalance(RECEIVER).hasTinyBars(0L),
                        overriding(SCHEDULING_LONG_TERM_ENABLED, "true"),
                        scheduleSign(THREE_SIG_XFER).alsoSigningWith(PAYER))
                .then(
                        getAccountBalance(RECEIVER).hasTinyBars(1L),
                        getScheduleInfo(THREE_SIG_XFER)
                                .hasScheduleId(THREE_SIG_XFER)
                                .hasWaitForExpiry(false)
                                .isExecuted(),
                        overriding(SCHEDULING_LONG_TERM_ENABLED, FALSE));
    }

    public HapiSpec expiryIgnoredWhenLongTermDisabledThenEnabled() {

        return defaultHapiSpec("ExpiryIgnoredWhenLongTermDisabledThenEnabled")
                .given(
                        cryptoCreate(PAYER).balance(ONE_HBAR),
                        cryptoCreate(SENDER).balance(1L).via(SENDER_TXN),
                        cryptoCreate(RECEIVER).balance(0L).receiverSigRequired(true))
                .when(
                        overriding(LEDGER_SCHEDULE_TX_EXPIRY_TIME_SECS, "" + 5),
                        scheduleCreate(
                                        THREE_SIG_XFER,
                                        cryptoTransfer(tinyBarsFromTo(SENDER, RECEIVER, 1))
                                                .fee(ONE_HBAR))
                                .withRelativeExpiry(SENDER_TXN, 500)
                                .designatingPayer(PAYER)
                                .alsoSigningWith(SENDER, RECEIVER))
                .then(
                        overriding(LEDGER_SCHEDULE_TX_EXPIRY_TIME_SECS, DEFAULT_TX_EXPIRY),
                        overriding(SCHEDULING_LONG_TERM_ENABLED, "true"),
                        getScheduleInfo(THREE_SIG_XFER)
                                .hasScheduleId(THREE_SIG_XFER)
                                .isNotExecuted()
                                .isNotDeleted(),
                        sleepFor(9000),
                        cryptoCreate("foo"),
                        getScheduleInfo(THREE_SIG_XFER).hasCostAnswerPrecheck(INVALID_SCHEDULE_ID),
                        overriding(SCHEDULING_LONG_TERM_ENABLED, FALSE),
                        getAccountBalance(RECEIVER).hasTinyBars(0L));
    }

    private HapiSpec futureThrottlesAreRespected() {
        var artificialLimits =
                protoDefsFromResource("testSystemFiles/artificial-limits-schedule.json");
        var defaultThrottles = protoDefsFromResource("testSystemFiles/throttles-dev.json");

        return defaultHapiSpec("FutureThrottlesAreRespected")
                .given(
                        cryptoCreate(SENDER).balance(ONE_MILLION_HBARS).via(SENDER_TXN),
                        cryptoCreate(RECEIVER),
                        overriding(SCHEDULING_MAX_TXN_PER_SECOND, "100"),
                        fileUpdate(THROTTLE_DEFS)
                                .payingWith(EXCHANGE_RATE_CONTROL)
                                .contents(artificialLimits.toByteArray()),
                        sleepFor(500))
                .when(
                        blockingOrder(
                                IntStream.range(0, 17)
                                        .mapToObj(
                                                i ->
                                                        new HapiSpecOperation[] {
                                                            scheduleCreate(
                                                                            "twoSigXfer" + i,
                                                                            cryptoTransfer(
                                                                                            tinyBarsFromTo(
                                                                                                    SENDER,
                                                                                                    RECEIVER,
                                                                                                    1))
                                                                                    .fee(ONE_HBAR))
                                                                    .withEntityMemo(
                                                                            randomUppercase(100))
                                                                    .payingWith(SENDER)
                                                                    .waitForExpiry()
                                                                    .withRelativeExpiry(
                                                                            SENDER_TXN, 120),
                                                        })
                                        .flatMap(Arrays::stream)
                                        .toArray(HapiSpecOperation[]::new)),
                        scheduleCreate(
                                        "twoSigXfer",
                                        cryptoTransfer(tinyBarsFromTo(SENDER, RECEIVER, 1))
                                                .fee(ONE_HBAR))
                                .withEntityMemo(randomUppercase(100))
                                .payingWith(SENDER)
                                .waitForExpiry()
                                .withRelativeExpiry(SENDER_TXN, 120)
                                .hasKnownStatus(SCHEDULE_FUTURE_THROTTLE_EXCEEDED))
                .then(
                        overriding(
                                SCHEDULING_MAX_TXN_PER_SECOND,
                                HapiSpecSetup.getDefaultNodeProps()
                                        .get(SCHEDULING_MAX_TXN_PER_SECOND)),
                        fileUpdate(THROTTLE_DEFS)
                                .fee(ONE_HUNDRED_HBARS)
                                .payingWith(EXCHANGE_RATE_CONTROL)
                                .contents(defaultThrottles.toByteArray()),
                        cryptoTransfer(HapiCryptoTransfer.tinyBarsFromTo(GENESIS, FUNDING, 1))
                                .payingWith(GENESIS));
    }

    @Override
    protected Logger getResultsLogger() {
        return LOG;
    }

    public static HapiSpec enableLongTermScheduledTransactions() {
        return defaultHapiSpec("EnableLongTermScheduledTransactions")
                .given()
                .when()
                .then(
                        fileUpdate(APP_PROPERTIES)
                                .payingWith(ADDRESS_BOOK_CONTROL)
                                .overridingProps(Map.of(SCHEDULING_LONG_TERM_ENABLED, "true")));
    }

    public static HapiSpec disableLongTermScheduledTransactions() {
        return defaultHapiSpec("DisableLongTermScheduledTransactions")
                .given()
                .when()
                .then(
                        fileUpdate(APP_PROPERTIES)
                                .payingWith(ADDRESS_BOOK_CONTROL)
                                .overridingProps(Map.of(SCHEDULING_LONG_TERM_ENABLED, FALSE)));
    }

    public static HapiSpec setLongTermScheduledTransactionsToDefault() {
        return defaultHapiSpec("SetLongTermScheduledTransactionsToDefault")
                .given()
                .when()
                .then(
                        fileUpdate(APP_PROPERTIES)
                                .payingWith(ADDRESS_BOOK_CONTROL)
                                .overridingProps(
                                        Map.of(
                                                SCHEDULING_LONG_TERM_ENABLED,
                                                DEFAULT_LONG_TERM_ENABLED)));
    }

    public static List<HapiSpec> withAndWithoutLongTermEnabled(Supplier<List<HapiSpec>> getSpecs) {
        List<HapiSpec> list = new ArrayList<>();
        list.add(disableLongTermScheduledTransactions());
        list.addAll(getSpecs.get());
        list.add(enableLongTermScheduledTransactions());
        var withEnabled = getSpecs.get();
        withEnabled.forEach(s -> s.appendToName("WithLongTermEnabled"));
        list.addAll(withEnabled);
        list.add(setLongTermScheduledTransactionsToDefault());
        return list;
    }

    public static List<HapiSpec> withAndWithoutLongTermEnabled(
            Function<Boolean, List<HapiSpec>> getSpecs) {
        List<HapiSpec> list = new ArrayList<>();
        list.add(disableLongTermScheduledTransactions());
        list.addAll(getSpecs.apply(false));
        list.add(enableLongTermScheduledTransactions());
        var withEnabled = getSpecs.apply(true);
        withEnabled.forEach(s -> s.appendToName("WithLongTermEnabled"));
        list.addAll(withEnabled);
        list.add(setLongTermScheduledTransactionsToDefault());
        return list;
    }
}
