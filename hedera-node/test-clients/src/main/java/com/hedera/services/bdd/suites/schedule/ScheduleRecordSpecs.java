/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
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
import static com.hedera.services.bdd.spec.assertions.TransactionRecordAsserts.recordWith;
import static com.hedera.services.bdd.spec.assertions.TransferListAsserts.exactParticipants;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getScheduleInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTopicInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.createTopic;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.scheduleCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.scheduleDelete;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.scheduleSign;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.submitMessageTo;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.uploadInitCode;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromTo;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.overriding;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.overridingAllOf;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sourcing;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.usableTxnIdNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.validateChargedUsdWithin;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.suites.schedule.ScheduleLongTermExecutionSpecs.withAndWithoutLongTermEnabled;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_PAYER_BALANCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_TX_FEE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TRANSACTION_ID_FIELD_NOT_ALLOWED;

import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.HapiSpecSetup;
import com.hedera.services.bdd.suites.HapiSuite;
import com.hederahashgraph.api.proto.java.TransactionID;
import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ScheduleRecordSpecs extends HapiSuite {
    private static final Logger log = LogManager.getLogger(ScheduleRecordSpecs.class);
    private static final String TWO_SIG_XFER = "twoSigXfer";
    private static final String BEGIN = "begin";
    private static final String SIMPLE_XFER_SCHEDULE = "simpleXferSchedule";
    private static final String ADMIN = "admin";
    private static final String CREATION = "creation";
    private static final String PAYER = "payer";
    private static final String PAYING_SENDER = "payingSender";
    private static final String OTHER_PAYER = "otherPayer";
    private static final String SIMPLE_UPDATE = "SimpleUpdate";
    private static final String SCHEDULING_WHITELIST = "scheduling.whitelist";
    private static final String STAKING_FEES_NODE_REWARD_PERCENTAGE = "staking.fees.nodeRewardPercentage";
    private static final String STAKING_FEES_STAKING_REWARD_PERCENTAGE = "staking.fees.stakingRewardPercentage";
    private static final String TRIGGER = "trigger";
    private static final String INSOLVENT_PAYER = "insolventPayer";
    private static final String SCHEDULE = "schedule";
    private static final String UNWILLING_PAYER = "unwillingPayer";
    private static final String RECEIVER = "receiver";

    public static void main(String... args) {
        new ScheduleRecordSpecs().runSuiteAsync();
    }

    @Override
    public List<HapiSpec> getSpecsInSuite() {
        return withAndWithoutLongTermEnabled(() -> List.of(
                executionTimeIsAvailable(),
                deletionTimeIsAvailable(),
                allRecordsAreQueryable(),
                schedulingTxnIdFieldsNotAllowed(),
                canonicalScheduleOpsHaveExpectedUsdFees(),
                canScheduleChunkedMessages(),
                noFeesChargedIfTriggeredPayerIsInsolvent(),
                noFeesChargedIfTriggeredPayerIsUnwilling()));
    }

    HapiSpec canonicalScheduleOpsHaveExpectedUsdFees() {
        return defaultHapiSpec("CanonicalScheduleOpsHaveExpectedUsdFees")
                .given(
                        overriding(SCHEDULING_WHITELIST, "CryptoTransfer,ContractCall"),
                        uploadInitCode(SIMPLE_UPDATE),
                        cryptoCreate(OTHER_PAYER),
                        cryptoCreate(PAYING_SENDER),
                        cryptoCreate(RECEIVER).receiverSigRequired(true),
                        contractCreate(SIMPLE_UPDATE).gas(300_000L))
                .when(
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
                                contractCall(
                                        SIMPLE_UPDATE,
                                        "set",
                                        BigInteger.valueOf(5),
                                        BigInteger.valueOf(42))
                                        .gas(10_000L)
                                        .memo("")
                                        .fee(ONE_HBAR))
                                .payingWith(OTHER_PAYER)
                                .via("canonicalContractCall")
                                .adminKey(OTHER_PAYER))
                .then(
                        overriding(
                                SCHEDULING_WHITELIST,
                                HapiSpecSetup.getDefaultNodeProps().get(SCHEDULING_WHITELIST)),
                        validateChargedUsdWithin("canonicalCreation", 0.01, 3.0),
                        validateChargedUsdWithin("canonicalSigning", 0.001, 3.0),
                        validateChargedUsdWithin("canonicalDeletion", 0.001, 3.0),
                        validateChargedUsdWithin("canonicalContractCall", 0.1, 3.0));
    }

    public HapiSpec noFeesChargedIfTriggeredPayerIsUnwilling() {
        return defaultHapiSpec("NoFeesChargedIfTriggeredPayerIsUnwilling")
                .given(cryptoCreate(UNWILLING_PAYER))
                .when(scheduleCreate(
                        SCHEDULE,
                        cryptoTransfer(tinyBarsFromTo(GENESIS, FUNDING, 1))
                                .fee(1L))
                        .alsoSigningWith(GENESIS, UNWILLING_PAYER)
                        .via(SIMPLE_XFER_SCHEDULE)
                        // prevent multiple runs of this test causing duplicates
                        .withEntityMemo("" + new SecureRandom().nextLong())
                        .designatingPayer(UNWILLING_PAYER)
                        .savingExpectedScheduledTxnId())
                .then(getTxnRecord(SIMPLE_XFER_SCHEDULE)
                        .scheduledBy(SCHEDULE)
                        .hasPriority(recordWith()
                                .transfers(exactParticipants(ignore -> Collections.emptyList()))
                                .status(INSUFFICIENT_TX_FEE)));
    }

    public HapiSpec noFeesChargedIfTriggeredPayerIsInsolvent() {
        return defaultHapiSpec("NoFeesChargedIfTriggeredPayerIsInsolvent")
                .given(cryptoCreate(INSOLVENT_PAYER).balance(0L))
                .when(scheduleCreate(SCHEDULE, cryptoTransfer(tinyBarsFromTo(GENESIS, FUNDING, 1)))
                        .alsoSigningWith(GENESIS, INSOLVENT_PAYER)
                        .via(SIMPLE_XFER_SCHEDULE)
                        // prevent multiple runs of this test causing duplicates
                        .withEntityMemo("" + new SecureRandom().nextLong())
                        .designatingPayer(INSOLVENT_PAYER)
                        .savingExpectedScheduledTxnId())
                .then(getTxnRecord(SIMPLE_XFER_SCHEDULE)
                        .scheduledBy(SCHEDULE)
                        .hasPriority(recordWith()
                                .transfers(exactParticipants(ignore -> Collections.emptyList()))
                                .status(INSUFFICIENT_PAYER_BALANCE)));
    }

    public HapiSpec canScheduleChunkedMessages() {
        String ofGeneralInterest = "Scotch";
        AtomicReference<TransactionID> initialTxnId = new AtomicReference<>();

        return defaultHapiSpec("CanScheduleChunkedMessages")
                .given(
                        overridingAllOf(Map.of(
                                STAKING_FEES_NODE_REWARD_PERCENTAGE, "10",
                                STAKING_FEES_STAKING_REWARD_PERCENTAGE, "10")),
                        cryptoCreate(PAYING_SENDER).balance(ONE_HUNDRED_HBARS),
                        createTopic(ofGeneralInterest))
                .when(
                        withOpContext((spec, opLog) -> {
                            var subOp = usableTxnIdNamed(BEGIN).payerId(PAYING_SENDER);
                            allRunFor(spec, subOp);
                            initialTxnId.set(spec.registry().getTxnId(BEGIN));
                        }),
                        sourcing(() -> scheduleCreate(
                                "firstChunk",
                                submitMessageTo(ofGeneralInterest)
                                        .chunkInfo(3, 1, scheduledVersionOf(initialTxnId.get())))
                                .txnId(BEGIN)
                                .logged()
                                .signedBy(PAYING_SENDER)),
                        getTxnRecord(BEGIN)
                                .hasPriority(recordWith()
                                        .status(SUCCESS)
                                        .transfers(exactParticipants(spec -> List.of(
                                                spec.setup().defaultNode(),
                                                spec.setup().fundingAccount(),
                                                spec.setup().stakingRewardAccount(),
                                                spec.setup().nodeRewardAccount(),
                                                spec.registry().getAccountID(PAYING_SENDER)))))
                                .assertingOnlyPriority()
                                .logged(),
                        getTxnRecord(BEGIN)
                                .scheduled()
                                .hasPriority(recordWith()
                                        .status(SUCCESS)
                                        .transfers(exactParticipants(spec -> List.of(
                                                spec.setup().fundingAccount(),
                                                spec.setup().stakingRewardAccount(),
                                                spec.setup().nodeRewardAccount(),
                                                spec.registry().getAccountID(PAYING_SENDER)))))
                                .logged())
                .then(
                        scheduleCreate(
                                "secondChunk",
                                submitMessageTo(ofGeneralInterest).chunkInfo(3, 2, PAYING_SENDER))
                                .via("end")
                                .logged()
                                .payingWith(PAYING_SENDER),
                        getTxnRecord("end")
                                .scheduled()
                                .hasPriority(recordWith()
                                        .status(SUCCESS)
                                        .transfers(exactParticipants(spec -> List.of(
                                                spec.setup().fundingAccount(),
                                                spec.setup().stakingRewardAccount(),
                                                spec.setup().nodeRewardAccount(),
                                                spec.registry().getAccountID(PAYING_SENDER)))))
                                .logged(),
                        getTopicInfo(ofGeneralInterest).logged().hasSeqNo(2L),
                        overridingAllOf(Map.of(
                                STAKING_FEES_NODE_REWARD_PERCENTAGE,
                                HapiSpecSetup.getDefaultNodeProps().get(STAKING_FEES_NODE_REWARD_PERCENTAGE),
                                STAKING_FEES_STAKING_REWARD_PERCENTAGE,
                                HapiSpecSetup.getDefaultNodeProps().get(STAKING_FEES_STAKING_REWARD_PERCENTAGE))));
    }

    static TransactionID scheduledVersionOf(TransactionID txnId) {
        return txnId.toBuilder().setScheduled(true).build();
    }

    public HapiSpec schedulingTxnIdFieldsNotAllowed() {
        return defaultHapiSpec("SchedulingTxnIdFieldsNotAllowed")
                .given(usableTxnIdNamed("withScheduled").settingScheduledInappropriately())
                .when()
                .then(cryptoCreate("nope").txnId("withScheduled").hasPrecheck(TRANSACTION_ID_FIELD_NOT_ALLOWED));
    }

    public HapiSpec executionTimeIsAvailable() {
        return defaultHapiSpec("ExecutionTimeIsAvailable")
                .given(
                        cryptoCreate(PAYER),
                        cryptoCreate(RECEIVER).receiverSigRequired(true).balance(0L))
                .when(
                        scheduleCreate(
                                "tb",
                                cryptoTransfer(tinyBarsFromTo(PAYER, RECEIVER, 1))
                                        .fee(ONE_HBAR))
                                .savingExpectedScheduledTxnId()
                                .payingWith(PAYER)
                                .via(CREATION),
                        scheduleSign("tb").via(TRIGGER).alsoSigningWith(RECEIVER))
                .then(getScheduleInfo("tb").logged().wasExecutedBy(TRIGGER));
    }

    public HapiSpec deletionTimeIsAvailable() {
        return defaultHapiSpec("DeletionTimeIsAvailable")
                .given(
                        newKeyNamed(ADMIN),
                        cryptoCreate(PAYER),
                        cryptoCreate(RECEIVER).receiverSigRequired(true).balance(0L))
                .when(
                        scheduleCreate(
                                "ntb",
                                cryptoTransfer(tinyBarsFromTo(PAYER, RECEIVER, 1))
                                        .fee(ONE_HBAR))
                                .payingWith(PAYER)
                                .adminKey(ADMIN)
                                .via(CREATION),
                        scheduleDelete("ntb").via("deletion").signedBy(DEFAULT_PAYER, ADMIN))
                .then(getScheduleInfo("ntb").wasDeletedAtConsensusTimeOf("deletion"));
    }

    public HapiSpec allRecordsAreQueryable() {
        return defaultHapiSpec("AllRecordsAreQueryable")
                .given(
                        cryptoCreate(PAYER),
                        cryptoCreate(RECEIVER).receiverSigRequired(true).balance(0L))
                .when(
                        scheduleCreate(
                                TWO_SIG_XFER,
                                cryptoTransfer(tinyBarsFromTo(PAYER, RECEIVER, 1))
                                        .fee(ONE_HBAR))
                                .logged()
                                .savingExpectedScheduledTxnId()
                                .payingWith(PAYER)
                                .via(CREATION),
                        getAccountBalance(RECEIVER).hasTinyBars(0L))
                .then(
                        scheduleSign(TWO_SIG_XFER).via(TRIGGER).alsoSigningWith(RECEIVER),
                        getAccountBalance(RECEIVER).hasTinyBars(1L),
                        getTxnRecord(TRIGGER),
                        getTxnRecord(CREATION),
                        getTxnRecord(CREATION).scheduled(),
                        getTxnRecord(CREATION).scheduledBy(TWO_SIG_XFER));
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}
