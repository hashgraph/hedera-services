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

package com.hedera.services.bdd.suites.crypto.staking;

import static com.hedera.services.bdd.junit.TestTags.TIME_CONSUMING;
import static com.hedera.services.bdd.spec.HapiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.assertions.AccountInfoAsserts.accountWith;
import static com.hedera.services.bdd.spec.assertions.ContractInfoAsserts.contractWith;
import static com.hedera.services.bdd.spec.assertions.TransactionRecordAsserts.recordWith;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getContractInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoDelete;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.uploadInitCode;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromTo;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.blockingOrder;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.inParallel;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.overriding;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.overridingAllOf;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sleepFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sourcing;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.waitUntilJustBeforeNextStakingPeriod;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.waitUntilStartOfNextStakingPeriod;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.suites.autorenew.AutoRenewConfigChoices.enableContractAutoRenewWith;
import static com.hedera.services.bdd.suites.contract.hapi.ContractCallSuite.PAY_RECEIVABLE_CONTRACT;
import static com.hedera.services.bdd.suites.records.ContractRecordsSanityCheckSuite.PAYABLE_CONTRACT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_STAKING_ID;

import com.hedera.services.bdd.junit.HapiTest;
import com.hedera.services.bdd.junit.HapiTestSuite;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.suites.HapiSuite;
import com.hederahashgraph.api.proto.java.AccountAmount;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.IntFunction;
import java.util.stream.IntStream;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestMethodOrder;

@HapiTestSuite
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Tag(TIME_CONSUMING)
public class StakingSuite extends HapiSuite {

    private static final Logger log = LogManager.getLogger(StakingSuite.class);
    public static final String END_OF_STAKING_PERIOD_CALCULATIONS_MEMO = "End of staking period calculation record";
    private static final long ONE_STAKING_PERIOD = 60_000L;
    private static final long BUFFER = 10_000L;
    private static final long SOME_REWARD_RATE = 100_000_000_000L;
    private static final String ALICE = "alice";
    private static final String BOB = "bob";
    private static final String CAROL = "carol";
    private static final long INTER_PERIOD_SLEEP_MS = ONE_STAKING_PERIOD + BUFFER;
    public static final String STAKING_START_THRESHOLD = "staking.startThreshold";
    public static final String REWARD_BALANCE_THRESHOLD = "staking.rewardBalanceThreshold";
    public static final String PER_HBAR_REWARD_RATE = "staking.perHbarRewardRate";
    public static final String STAKING_REWARD_RATE = "staking.perHbarRewardRate";
    public static final String FIRST_TRANSFER = "firstTransfer";
    public static final String FIRST_TXN = "firstTxn";
    private static final long STAKING_PERIOD_MINS = 1L;

    public static void main(String... args) {
        new StakingSuite().runSuiteSync();
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }

    @Override
    public boolean canRunConcurrent() {
        return false;
    }

    @Override
    public List<HapiSpec> getSpecsInSuite() {
        return List.of(
                setUp(),
                losingEvenAZeroBalanceStakerTriggersStakeeRewardSituation(),
                evenOneTinybarChangeInIndirectStakingAccountTriggersStakeeRewardSituation(),
                stakingMetadataUpdateIsRewardOpportunity(),
                secondOrderRewardSituationsWork(),
                endOfStakingPeriodRecTest(),
                rewardsOfDeletedAreRedirectedToBeneficiary(),
                canBeRewardedWithoutMinStakeIfSoConfigured(),
                zeroRewardEarnedWithZeroWholeHbarsStillSetsSASOLARP(),
                autoRenewalsCanTriggerStakingRewards(),
                stakeIsManagedCorrectlyInTxnsAroundPeriodBoundaries(),
                zeroStakeAccountsHaveMetadataResetOnFirstDayTheyReceiveFunds());
    }

    @HapiTest
    @Order(1)
    @BeforeAll
    final HapiSpec setUp() {
        return defaultHapiSpec("setUp")
                .given(
                        overriding(STAKING_START_THRESHOLD, "" + 10 * ONE_HBAR),
                        overriding(PER_HBAR_REWARD_RATE, "" + 3_333_333),
                        overriding(REWARD_BALANCE_THRESHOLD, "" + 0),
                        cryptoTransfer(tinyBarsFromTo(GENESIS, STAKING_REWARD, ONE_MILLION_HBARS)))
                .when()
                .then();
    }

    /**
     * Tests a scenario in which many zero stake accounts are created, and then after a few staking
     * periods, a series of credits and debits are made to them, and they are confirmed to have
     * received the expected rewards (all zero).
     */
    @HapiTest
    @Order(12)
    final HapiSpec zeroStakeAccountsHaveMetadataResetOnFirstDayTheyReceiveFunds() {
        final var zeroStakeAccount = "zeroStakeAccount";
        final var numZeroStakeAccounts = 10;
        final var stakePeriodMins = 1L;

        return defaultHapiSpec("ZeroStakeAccountsHaveMetadataResetOnFirstDayTheyReceiveFunds")
                .given(
                        inParallel(IntStream.range(0, numZeroStakeAccounts)
                                .mapToObj(i -> cryptoCreate(zeroStakeAccount + i)
                                        .stakedNodeId(0)
                                        .balance(0L))
                                .toArray(HapiSpecOperation[]::new)),
                        cryptoCreate("somebody").stakedNodeId(0).balance(10 * ONE_MILLION_HBARS),
                        // Wait a few periods
                        waitUntilStartOfNextStakingPeriod(stakePeriodMins),
                        waitUntilStartOfNextStakingPeriod(stakePeriodMins),
                        waitUntilStartOfNextStakingPeriod(stakePeriodMins),
                        waitUntilStartOfNextStakingPeriod(stakePeriodMins),
                        waitUntilStartOfNextStakingPeriod(stakePeriodMins))
                .when()
                .then(sleepFor(5_000), withOpContext((spec, opLog) -> {
                    for (int i = 0; i < numZeroStakeAccounts; i++) {
                        final var target = zeroStakeAccount + i;
                        final var setupTxn = "setup" + i;
                        final var fundingTxn = "funding" + i;
                        final var withdrawingTxn = "withdrawing" + i;
                        final var first = cryptoTransfer(tinyBarsFromTo(GENESIS, target, 1))
                                .via(setupTxn);
                        final var second = cryptoTransfer(tinyBarsFromTo(GENESIS, target, ONE_MILLION_HBARS))
                                .via(fundingTxn);
                        final var third = cryptoTransfer(tinyBarsFromTo(target, GENESIS, ONE_MILLION_HBARS))
                                .via(withdrawingTxn);
                        allRunFor(
                                spec,
                                first,
                                second,
                                third,
                                getTxnRecord(setupTxn).logged(),
                                getTxnRecord(fundingTxn).logged(),
                                getTxnRecord(withdrawingTxn).logged());
                    }
                }));
    }

    /**
     * Tests a scenario in which Alice repeatedly transfers her balance to Baldwin right before the
     * end of a staking period, only to receive it back shortly after that period starts.
     */
    @HapiTest
    @Order(11)
    final HapiSpec stakeIsManagedCorrectlyInTxnsAroundPeriodBoundaries() {
        final var alice = "alice";
        final var baldwin = "baldwin";
        final var stakePeriodMins = 1L;
        final AtomicLong currentAliceBalance = new AtomicLong();
        final AtomicLong currentBaldwinBalance = new AtomicLong();
        final List<List<AccountAmount>> rewardsPaid = new ArrayList<>();

        final int numPeriodsToRepeat = 5;
        final long secsBeforePeriodEndToDoTransfer = 5;
        final IntFunction<String> returnToAliceTxns = n -> "returnToAlice" + n;
        final IntFunction<String> sendToBobTxns = n -> "sendToBob" + n;
        final IntFunction<HapiSpecOperation> returnRecordLookup =
                n -> getTxnRecord(returnToAliceTxns.apply(n)).logged().exposingStakingRewardsTo(rewardsPaid::add);
        final IntFunction<HapiSpecOperation> sendRecordLookup =
                n -> getTxnRecord(sendToBobTxns.apply(n)).logged().exposingStakingRewardsTo(rewardsPaid::add);

        return defaultHapiSpec("StakeIsManagedCorrectlyInTxnsAroundPeriodBoundaries")
                .given(
                        cryptoCreate(alice).stakedNodeId(0).balance(ONE_MILLION_HBARS),
                        cryptoCreate(baldwin).stakedNodeId(0).balance(0L),
                        // Reach a period where stakers can collect rewards
                        waitUntilStartOfNextStakingPeriod(stakePeriodMins))
                .when(IntStream.range(0, numPeriodsToRepeat)
                        .mapToObj(i -> blockingOrder(
                                waitUntilJustBeforeNextStakingPeriod(stakePeriodMins, secsBeforePeriodEndToDoTransfer),
                                getAccountBalance(alice).exposingBalanceTo(currentAliceBalance::set),
                                // From Alice to Baldwin
                                sourcing(() -> cryptoTransfer(tinyBarsFromTo(alice, baldwin, currentAliceBalance.get()))
                                        .via(sendToBobTxns.apply(i))),
                                sourcing(() -> sendRecordLookup.apply(i)),
                                // Wait until the next period starts
                                sleepFor(2 * secsBeforePeriodEndToDoTransfer * 1000),
                                // Back to Alice from Baldwin
                                getAccountBalance(baldwin).exposingBalanceTo(currentBaldwinBalance::set),
                                sourcing(() -> cryptoTransfer(
                                                tinyBarsFromTo(baldwin, alice, currentBaldwinBalance.get()))
                                        .via(returnToAliceTxns.apply(i))),
                                sourcing(() -> returnRecordLookup.apply(i))))
                        .toArray(HapiSpecOperation[]::new))
                .then(withOpContext((spec, opLog) -> {
                    final var registry = spec.registry();
                    final var aliceNum = registry.getAccountID(alice).getAccountNum();
                    final var baldwinNum = registry.getAccountID(baldwin).getAccountNum();
                    for (int i = 0; i < rewardsPaid.size(); i++) {
                        if (i % 2 == 0) {
                            opLog.info("======= Send-to-Baldwin #{} =======", i / 2);
                        } else {
                            opLog.info("======= Return-to-Alice #{} =======", i / 2);
                        }
                        final var paidThisTime = rewardsPaid.get(i);
                        var aliceReward = 0L;
                        var baldwinReward = 0L;
                        for (final var paid : paidThisTime) {
                            if (paid.getAccountID().getAccountNum() == aliceNum) {
                                aliceReward = paid.getAmount();
                            }
                            if (paid.getAccountID().getAccountNum() == baldwinNum) {
                                baldwinReward = paid.getAmount();
                            }
                        }
                        opLog.info("=  Alice   : {}", aliceReward);
                        opLog.info("=  Baldwin : {}", baldwinReward);
                        opLog.info("==============================\n");
                    }
                }));
    }

    /**
     * Creates a contract staked to a node with a lifetime just over one staking period; waits long
     * enough for it to be eligible for rewards, and then triggers its auto-renewal.
     *
     * <p>Since system records aren't queryable via HAPI, it's necessary to add logging in e.g.
     * ExpiryRecordsHelper#finalizeAndStream() to inspect the generated record and confirm staking
     * rewards are paid.
     *
     * @return the spec described above
     */
    @HapiTest
    @Order(10)
    final HapiSpec autoRenewalsCanTriggerStakingRewards() {
        final var initBalance = ONE_HBAR * 1000;
        final var minimalLifetime = 3;
        final var creation = "creation";

        return defaultHapiSpec("AutoRenewalsCanTriggerStakingRewards")
                .given(
                        cryptoCreate("miscStaker").stakedNodeId(0).balance(ONE_HUNDRED_HBARS * 1000),
                        uploadInitCode(PAY_RECEIVABLE_CONTRACT),
                        waitUntilStartOfNextStakingPeriod(STAKING_PERIOD_MINS))
                .when(
                        enableContractAutoRenewWith(minimalLifetime, 0),
                        contractCreate(PAY_RECEIVABLE_CONTRACT)
                                .gas(2_000_000)
                                .entityMemo("")
                                .stakedNodeId(0L)
                                // Lifetime is in seconds not milliseconds
                                .autoRenewSecs((INTER_PERIOD_SLEEP_MS + BUFFER) / 1000)
                                .balance(initBalance)
                                .via(creation),
                        waitUntilStartOfNextStakingPeriod(STAKING_PERIOD_MINS),
                        cryptoTransfer(tinyBarsFromTo(GENESIS, NODE, 1L)),
                        waitUntilStartOfNextStakingPeriod(STAKING_PERIOD_MINS))
                .then(
                        cryptoTransfer(tinyBarsFromTo(GENESIS, NODE, 1L)).via("triggerRenewal"),
                        getTxnRecord("triggerRenewal")
                                .andAllChildRecords()
                                .countStakingRecords()
                                .logged());
    }

    @HapiTest
    @Order(8)
    final HapiSpec canBeRewardedWithoutMinStakeIfSoConfigured() {
        final var patientlyWaiting = "patientlyWaiting";

        return defaultHapiSpec("CanBeRewardedWithoutMinStakeIfSoConfigured")
                .given(
                        overridingAllOf(Map.of(
                                "staking.nodeMaxToMinStakeRatios",
                                "0:2,1:4",
                                "staking.requireMinStakeToReward",
                                "true",
                                STAKING_START_THRESHOLD,
                                "100_000_000")),
                        // Create the patiently waiting staker
                        cryptoCreate(patientlyWaiting).stakedNodeId(0).balance(ONE_HUNDRED_HBARS))
                .when(
                        waitUntilStartOfNextStakingPeriod(STAKING_PERIOD_MINS),
                        cryptoTransfer(tinyBarsFromTo(GENESIS, FUNDING, 1L)),
                        cryptoTransfer(tinyBarsFromTo(patientlyWaiting, FUNDING, 1)),
                        getAccountBalance(patientlyWaiting).logged(),
                        // Now we should be rewardable even though node0 is far from minStake
                        overriding("staking.requireMinStakeToReward", "false"),
                        waitUntilStartOfNextStakingPeriod(STAKING_PERIOD_MINS),
                        cryptoTransfer(tinyBarsFromTo(GENESIS, FUNDING, 1L)),
                        cryptoTransfer(tinyBarsFromTo(patientlyWaiting, FUNDING, 1)))
                .then(getAccountBalance(patientlyWaiting).logged());
    }
    // HERE
    @HapiTest
    @Order(5)
    final HapiSpec secondOrderRewardSituationsWork() {
        final long totalStakeStartCase1 = 3 * ONE_HUNDRED_HBARS;
        final long rewardSumHistoryCase1 = SOME_REWARD_RATE / (totalStakeStartCase1 / TINY_PARTS_PER_WHOLE) / 100;
        final long alicePendingRewardsCase1 = rewardSumHistoryCase1 * (2 * ONE_HUNDRED_HBARS / TINY_PARTS_PER_WHOLE);
        final long bobPendingRewardsCase1 = rewardSumHistoryCase1 * (ONE_HUNDRED_HBARS / TINY_PARTS_PER_WHOLE);

        return defaultHapiSpec("SecondOrderRewardSituationsWork")
                .given()
                .when( // period 1
                        cryptoCreate(ALICE).stakedNodeId(0).balance(ONE_HUNDRED_HBARS),
                        cryptoCreate(BOB).stakedNodeId(0).balance(ONE_HUNDRED_HBARS),
                        cryptoCreate(CAROL).stakedAccountId(ALICE).balance(ONE_HUNDRED_HBARS),
                        waitUntilStartOfNextStakingPeriod(STAKING_PERIOD_MINS))
                .then(
                        /* --- period 2 - paid_rewards 0 for first period --- */
                        cryptoTransfer(tinyBarsFromTo(BOB, ALICE, ONE_HBAR)).via(FIRST_TRANSFER),
                        getTxnRecord(FIRST_TRANSFER)
                                .andAllChildRecords()
                                .countStakingRecords()
                                .stakingFeeExempted()
                                .hasChildRecordCount(1)
                                .hasChildRecords(recordWith().memo(END_OF_STAKING_PERIOD_CALCULATIONS_MEMO))
                                .hasPaidStakingRewards(List.of()),

                        /* --- second period reward eligible --- */
                        waitUntilStartOfNextStakingPeriod(STAKING_PERIOD_MINS),
                        cryptoTransfer(tinyBarsFromTo(GENESIS, FUNDING, 1L)).via("endOfStakingPeriodXfer"),
                        getAccountInfo(ALICE)
                                .has(accountWith().stakedNodeId(0L).pendingRewards(666666600L))
                                .logged(),
                        getAccountInfo(BOB)
                                .has(accountWith().stakedNodeId(0L).pendingRewards(333333300L))
                                .logged(),
                        getTxnRecord("endOfStakingPeriodXfer")
                                .andAllChildRecords()
                                .hasChildRecordCount(1)
                                .countStakingRecords()
                                .hasChildRecords(recordWith().memo(END_OF_STAKING_PERIOD_CALCULATIONS_MEMO)),
                        getAccountInfo(ALICE)
                                .has(accountWith().stakedNodeId(0L).pendingRewards(alicePendingRewardsCase1))
                                .logged(),
                        getAccountInfo(BOB)
                                .has(accountWith().stakedNodeId(0L).pendingRewards(bobPendingRewardsCase1))
                                .logged(),
                        cryptoUpdate(CAROL).newStakedAccountId(BOB).via("secondOrderRewardSituation"),
                        getTxnRecord("secondOrderRewardSituation")
                                .andAllChildRecords()
                                .countStakingRecords()
                                .hasStakingFeesPaid()
                                .hasPaidStakingRewards(List.of(
                                        Pair.of(ALICE, alicePendingRewardsCase1), Pair.of(BOB, bobPendingRewardsCase1)))
                                .logged(),
                        /* Within the same period rewards are not awarded twice */
                        cryptoTransfer(tinyBarsFromTo(BOB, ALICE, ONE_HBAR))
                                .payingWith(BOB)
                                .via("expectNoReward"),
                        getTxnRecord("expectNoReward")
                                .andAllChildRecords()
                                .countStakingRecords()
                                .hasChildRecordCount(0)
                                .hasStakingFeesPaid()
                                //                                .hasPaidStakingRewards(List.of())
                                .logged());
    }

    @HapiTest
    @Order(13)
    final HapiSpec pendingRewardsPaidBeforeStakedToMeUpdates() {
        return defaultHapiSpec("PendingRewardsPaidBeforeStakedToMeUpdates")
                .given(
                        overriding(STAKING_START_THRESHOLD, "" + 10 * ONE_HBAR),
                        overriding(PER_HBAR_REWARD_RATE, "" + ONE_HBAR),
                        overriding(REWARD_BALANCE_THRESHOLD, "" + 0),
                        cryptoTransfer(tinyBarsFromTo(GENESIS, STAKING_REWARD, ONE_MILLION_HBARS)))
                .when( // period 1
                        cryptoCreate(ALICE).stakedNodeId(0).balance(ONE_HUNDRED_HBARS),
                        cryptoCreate(CAROL).stakedNodeId(0).balance(ONE_HUNDRED_HBARS),
                        waitUntilStartOfNextStakingPeriod(STAKING_PERIOD_MINS))
                .then(
                        /* --- period 2 - paid_rewards 0 for first period --- */
                        cryptoTransfer(tinyBarsFromTo(GENESIS, FUNDING, ONE_HBAR))
                                .via(FIRST_TRANSFER),
                        getTxnRecord(FIRST_TRANSFER)
                                .andAllChildRecords()
                                .countStakingRecords()
                                .stakingFeeExempted()
                                .hasChildRecordCount(1)
                                .hasChildRecords(recordWith().memo(END_OF_STAKING_PERIOD_CALCULATIONS_MEMO)),
                        // alice - 100, carol - 100
                        /* --- third period reward eligible from period 2--- */
                        waitUntilStartOfNextStakingPeriod(STAKING_PERIOD_MINS),
                        cryptoUpdate(CAROL).newStakedAccountId(ALICE).via("stakedIdUpdate"),
                        getTxnRecord("stakedIdUpdate")
                                .andAllChildRecords()
                                .hasChildRecordCount(1)
                                .countStakingRecords()
                                .hasChildRecords(recordWith().memo(END_OF_STAKING_PERIOD_CALCULATIONS_MEMO))
                                .hasPaidStakingRewardsCount(2)
                                .hasPaidStakingRewards(
                                        List.of(Pair.of(ALICE, 100 * ONE_HBAR), Pair.of(CAROL, 100 * ONE_HBAR))),
                        // alice - 200, stakedToMe - 200
                        // carol - 200
                        /* fourth period */
                        waitUntilStartOfNextStakingPeriod(STAKING_PERIOD_MINS),
                        cryptoTransfer(tinyBarsFromTo(GENESIS, FUNDING, ONE_HBAR))
                                .via("fourthPeriod"),
                        getTxnRecord("fourthPeriod")
                                .andAllChildRecords()
                                .countStakingRecords()
                                .logged(),
                        cryptoTransfer(tinyBarsFromTo(GENESIS, ALICE, ONE_HBAR)).via("aliceFirstXfer"),
                        getTxnRecord("aliceFirstXfer")
                                .hasPaidStakingRewards(List.of(Pair.of(ALICE, 100 * ONE_HBAR)))
                                .logged(),
                        waitUntilStartOfNextStakingPeriod(STAKING_PERIOD_MINS),

                        /* fifth period */
                        cryptoTransfer(tinyBarsFromTo(GENESIS, ALICE, ONE_HBAR)).via("aliceSecondXfer"),
                        getTxnRecord("aliceSecondXfer")
                                .hasPaidStakingRewards(List.of(Pair.of(ALICE, 400 * ONE_HBAR)))
                                .logged());
    }

    @HapiTest
    @Order(3)
    final HapiSpec evenOneTinybarChangeInIndirectStakingAccountTriggersStakeeRewardSituation() {
        return defaultHapiSpec("EvenOneTinybarChangeInIndirectStakingAccountTriggersStakeeRewardSituation")
                .given()
                .when(
                        cryptoCreate(ALICE).stakedNodeId(0).balance(ONE_HUNDRED_HBARS),
                        cryptoCreate(BOB).stakedAccountId(ALICE).balance(ONE_HUNDRED_HBARS),
                        cryptoCreate(CAROL).stakedAccountId(ALICE).balance(ONE_HUNDRED_HBARS),
                        waitUntilStartOfNextStakingPeriod(STAKING_PERIOD_MINS),
                        cryptoTransfer(tinyBarsFromTo(DEFAULT_PAYER, FUNDING, 1L)),
                        waitUntilStartOfNextStakingPeriod(STAKING_PERIOD_MINS))
                .then(
                        cryptoTransfer(tinyBarsFromTo(DEFAULT_PAYER, CAROL, 1)).via(FIRST_TRANSFER),
                        getTxnRecord(FIRST_TRANSFER)
                                .andAllChildRecords()
                                .countStakingRecords()
                                .hasPaidStakingRewardsCount(1));
    }

    @HapiTest
    @Order(9)
    final HapiSpec zeroRewardEarnedWithZeroWholeHbarsStillSetsSASOLARP() {
        return defaultHapiSpec("ZeroRewardEarnedWithZeroWholeHbarsStillSetsSASOLARP")
                .given(
                        cryptoCreate("helpfulStaker").stakedNodeId(0).balance(ONE_MILLION_HBARS),
                        waitUntilStartOfNextStakingPeriod(STAKING_PERIOD_MINS))
                .when(
                        cryptoCreate(ALICE).stakedNodeId(0).balance(0L),
                        waitUntilStartOfNextStakingPeriod(STAKING_PERIOD_MINS),
                        cryptoTransfer(tinyBarsFromTo(GENESIS, ALICE, ONE_HUNDRED_HBARS)),
                        waitUntilStartOfNextStakingPeriod(STAKING_PERIOD_MINS),
                        cryptoTransfer(tinyBarsFromTo(ALICE, FUNDING, ONE_HUNDRED_HBARS)),
                        waitUntilStartOfNextStakingPeriod(STAKING_PERIOD_MINS))
                .then(
                        cryptoTransfer(tinyBarsFromTo(DEFAULT_PAYER, ALICE, 1)).via(FIRST_TRANSFER),
                        getTxnRecord(FIRST_TRANSFER).countStakingRecords().hasPaidStakingRewardsCount(1));
    }

    @HapiTest
    @Order(2)
    final HapiSpec losingEvenAZeroBalanceStakerTriggersStakeeRewardSituation() {
        return defaultHapiSpec("LosingEvenAZeroBalanceStakerTriggersStakeeRewardSituation")
                .given()
                .when(
                        cryptoCreate(ALICE).stakedNodeId(0).balance(ONE_HUNDRED_HBARS),
                        cryptoCreate(BOB).stakedAccountId(ALICE).balance(0L),
                        cryptoCreate(CAROL).stakedAccountId(ALICE).balance(ONE_HUNDRED_HBARS),
                        waitUntilStartOfNextStakingPeriod(STAKING_PERIOD_MINS),
                        cryptoTransfer(tinyBarsFromTo(DEFAULT_PAYER, FUNDING, 1L)),
                        waitUntilStartOfNextStakingPeriod(STAKING_PERIOD_MINS))
                .then(
                        cryptoUpdate(BOB).newStakedNodeId(0L).via(FIRST_TRANSFER),
                        getTxnRecord(FIRST_TRANSFER)
                                .andAllChildRecords()
                                .countStakingRecords()
                                .hasPaidStakingRewardsCount(1)
                                .logged());
    }

    @HapiTest
    @Order(4)
    final HapiSpec stakingMetadataUpdateIsRewardOpportunity() {
        return defaultHapiSpec("stakingMetadataUpdateIsRewardOpportunity")
                .given()
                .when(
                        cryptoCreate(ALICE).stakedNodeId(0).balance(ONE_HUNDRED_HBARS),
                        cryptoCreate(BOB).balance(ONE_HUNDRED_HBARS),
                        uploadInitCode(PAYABLE_CONTRACT),
                        contractCreate(PAYABLE_CONTRACT).stakedNodeId(0L).balance(ONE_HUNDRED_HBARS),
                        waitUntilStartOfNextStakingPeriod(STAKING_PERIOD_MINS))
                .then(
                        /* --- Since rewardsSunHistory will be 0 for the first staking period, paid_rewards will be 0 --- */
                        cryptoTransfer(tinyBarsFromTo(BOB, ALICE, ONE_HBAR)).via(FIRST_TXN),
                        getTxnRecord(FIRST_TXN)
                                .andAllChildRecords()
                                .countStakingRecords()
                                .hasChildRecordCount(1)
                                .hasChildRecords(recordWith().memo(END_OF_STAKING_PERIOD_CALCULATIONS_MEMO))
                                .hasPaidStakingRewards(List.of()),

                        /* should receive reward */
                        waitUntilStartOfNextStakingPeriod(STAKING_PERIOD_MINS),
                        cryptoTransfer(tinyBarsFromTo(GENESIS, FUNDING, 1L)),
                        // info queries return rewards
                        getContractInfo(PAYABLE_CONTRACT)
                                .has(contractWith().stakedNodeId(0L).pendingRewards(333333300L)),
                        contractUpdate(PAYABLE_CONTRACT).newDeclinedReward(true).via("acceptsReward"),
                        getTxnRecord("acceptsReward")
                                .logged()
                                .andAllChildRecords()
                                .countStakingRecords()
                                //                                .hasChildRecordCount(1)
                                //
                                // .hasChildRecords(recordWith().memo(END_OF_STAKING_PERIOD_CALCULATIONS_MEMO))
                                .hasPaidStakingRewards(List.of(Pair.of(PAYABLE_CONTRACT, 333333300L))),

                        // same period should not trigger reward again
                        contractUpdate(PAYABLE_CONTRACT).newStakedNodeId(111L).hasPrecheck(INVALID_STAKING_ID),
                        contractUpdate(PAYABLE_CONTRACT).newStakedAccountId(BOB).via("samePeriodTxn"),
                        getTxnRecord("samePeriodTxn")
                                .andAllChildRecords()
                                .countStakingRecords()
                                .hasChildRecordCount(0)
                                .hasPaidStakingRewards(List.of()),
                        waitUntilStartOfNextStakingPeriod(STAKING_PERIOD_MINS),
                        waitUntilStartOfNextStakingPeriod(STAKING_PERIOD_MINS),

                        /* -- trigger a deposit and see if pays expected reward */
                        contractCall(PAYABLE_CONTRACT, "deposit", BigInteger.valueOf(1_000L))
                                .payingWith(BOB)
                                .sending(1_000L)
                                .via("contractRewardTxn"),
                        getTxnRecord("contractRewardTxn")
                                .andAllChildRecords()
                                .countStakingRecords()
                                .hasChildRecordCount(1)
                                .hasChildRecords(recordWith().memo(END_OF_STAKING_PERIOD_CALCULATIONS_MEMO))
                                .logged()
                        //                                .hasPaidStakingRewards(List.of(Pair.of(PAYABLE_CONTRACT,
                        // 500000000L)))
                        );
    }

    @HapiTest
    @Order(6)
    final HapiSpec endOfStakingPeriodRecTest() {
        return defaultHapiSpec("EndOfStakingPeriodRecTest")
                .given(
                        cryptoCreate("a1").balance(ONE_MILLION_HBARS).stakedNodeId(0),
                        cryptoCreate("a2").balance(ONE_MILLION_HBARS).stakedNodeId(0),
                        cryptoTransfer(
                                tinyBarsFromTo(GENESIS, STAKING_REWARD, ONE_MILLION_HBARS)) // will trigger staking
                        )
                .when(waitUntilStartOfNextStakingPeriod(STAKING_PERIOD_MINS))
                .then(
                        cryptoTransfer(tinyBarsFromTo("a1", "a2", ONE_HBAR)).via("trigger"),
                        getTxnRecord("trigger")
                                .logged()
                                .countStakingRecords()
                                .hasChildRecordCount(1)
                                .hasChildRecords(recordWith().memo(END_OF_STAKING_PERIOD_CALCULATIONS_MEMO)),
                        waitUntilStartOfNextStakingPeriod(STAKING_PERIOD_MINS),
                        cryptoTransfer(tinyBarsFromTo("a1", "a2", ONE_HBAR)).via("transfer"),
                        getTxnRecord("transfer")
                                .countStakingRecords()
                                .hasChildRecordCount(1)
                                .hasChildRecords(recordWith().memo(END_OF_STAKING_PERIOD_CALCULATIONS_MEMO))
                                .logged(),
                        cryptoTransfer(tinyBarsFromTo("a1", "a2", ONE_HBAR)).via("noEndOfStakingPeriodRecord"),
                        getTxnRecord("noEndOfStakingPeriodRecord")
                                .countStakingRecords()
                                .hasChildRecordCount(0)
                                .logged(),
                        waitUntilStartOfNextStakingPeriod(STAKING_PERIOD_MINS),
                        cryptoTransfer(tinyBarsFromTo("a1", "a2", ONE_HBAR)).via("transfer1"),
                        getTxnRecord("transfer1")
                                .countStakingRecords()
                                .hasChildRecordCount(1)
                                .hasChildRecords(recordWith().memo(END_OF_STAKING_PERIOD_CALCULATIONS_MEMO))
                                .logged());
    }

    @HapiTest
    @Order(7)
    final HapiSpec rewardsOfDeletedAreRedirectedToBeneficiary() {
        final var bob = "bob";
        final var deletion = "deletion";
        return defaultHapiSpec("RewardsOfDeletedAreRedirectedToBeneficiary")
                .given()
                .when(
                        cryptoCreate(ALICE).stakedNodeId(0).balance(ONE_MILLION_HBARS),
                        cryptoCreate(bob).balance(0L),
                        waitUntilStartOfNextStakingPeriod(STAKING_PERIOD_MINS),
                        cryptoTransfer(tinyBarsFromTo(GENESIS, FUNDING, 1L)),
                        waitUntilStartOfNextStakingPeriod(STAKING_PERIOD_MINS))
                .then(
                        cryptoDelete(ALICE).transfer(bob).via(deletion),
                        getTxnRecord(deletion)
                                .andAllChildRecords()
                                .countStakingRecords()
                                .hasChildRecordCount(1)
                                .hasChildRecords(recordWith().memo(END_OF_STAKING_PERIOD_CALCULATIONS_MEMO))
                                .hasPaidStakingRewards(List.of(Pair.of(bob, 3333333000000L)))
                                .logged());
    }
}
