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
package com.hedera.services.bdd.suites.crypto.staking;

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
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.blockingOrder;
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

import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.suites.HapiSuite;
import com.hederahashgraph.api.proto.java.AccountAmount;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.IntFunction;
import java.util.stream.IntStream;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class StakingSuite extends HapiSuite {

    private static final Logger log = LogManager.getLogger(StakingSuite.class);
    public static final String END_OF_STAKING_PERIOD_CALCULATIONS_MEMO =
            "End of staking period calculation record";
    private static final long ONE_STAKING_PERIOD = 60_000L;
    private static final long BUFFER = 10_000L;
    private static final long SOME_REWARD_RATE = 100_000_000_000L;
    private static final String ALICE = "alice";
    private static final String BOB = "bob";
    private static final String CAROL = "carol";
    private static final long INTER_PERIOD_SLEEP_MS = ONE_STAKING_PERIOD + BUFFER;
    public static final String STAKING_START_THRESHOLD = "staking.startThreshold";
    public static final String STAKING_REWARD_RATE = "staking.rewardRate";
    public static final String FIRST_TRANSFER = "firstTransfer";
    public static final String FIRST_TXN = "firstTxn";

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
        // These specs cannot really be run in CI; they are mostly useful for local
        // validation on a network started with a staking.periodMins=1 override
        return List.of(
                losingEvenAZeroBalanceStakerTriggersStakeeRewardSituation(),
                evenOneTinybarChangeInIndirectStakingAccountTriggersStakeeRewardSituation(),
                rewardsWorkAsExpected(),
                rewardPaymentsNotRepeatedInSamePeriod(),
                getInfoQueriesReturnsPendingRewards(),
                secondOrderRewardSituationsWork(),
                endOfStakingPeriodRecTest(),
                rewardsOfDeletedAreRedirectedToBeneficiary(),
                canBeRewardedWithoutMinStakeIfSoConfigured(),
                zeroRewardEarnedWithZeroWholeHbarsStillSetsSASOLARP(),
                autoRenewalsCanTriggerStakingRewards(),
                stakeIsManagedCorrectlyInTxnsAroundPeriodBoundaries());
    }

    /**
     * Tests a scenario in which Alice repeatedly transfers her balance to Baldwin right before the
     * end of a staking period, only to receive it back shortly after that period starts.
     */
    private HapiSpec stakeIsManagedCorrectlyInTxnsAroundPeriodBoundaries() {
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
                n ->
                        getTxnRecord(returnToAliceTxns.apply(n))
                                .logged()
                                .exposingStakingRewardsTo(rewardsPaid::add);
        final IntFunction<HapiSpecOperation> sendRecordLookup =
                n ->
                        getTxnRecord(sendToBobTxns.apply(n))
                                .logged()
                                .exposingStakingRewardsTo(rewardsPaid::add);

        return defaultHapiSpec("StakeIsManagedCorrectlyInTxnsAroundPeriodBoundaries")
                .given(
                        overridingAllOf(
                                Map.of(
                                        STAKING_START_THRESHOLD,
                                        "" + ONE_HBAR,
                                        STAKING_REWARD_RATE,
                                        "273972602739726")),
                        cryptoTransfer(
                                tinyBarsFromTo(GENESIS, STAKING_REWARD, 250 * ONE_MILLION_HBARS)),
                        cryptoCreate(alice).stakedNodeId(0).balance(ONE_MILLION_HBARS),
                        cryptoCreate(baldwin).stakedNodeId(0).balance(0L),
                        // Reach a period where stakers can collect rewards
                        waitUntilStartOfNextStakingPeriod(stakePeriodMins))
                .when(
                        IntStream.range(0, numPeriodsToRepeat)
                                .mapToObj(
                                        i ->
                                                blockingOrder(
                                                        waitUntilJustBeforeNextStakingPeriod(
                                                                stakePeriodMins,
                                                                secsBeforePeriodEndToDoTransfer),
                                                        getAccountBalance(alice)
                                                                .exposingBalanceTo(
                                                                        currentAliceBalance::set),
                                                        // From Alice to Baldwin
                                                        sourcing(
                                                                () ->
                                                                        cryptoTransfer(
                                                                                        tinyBarsFromTo(
                                                                                                alice,
                                                                                                baldwin,
                                                                                                currentAliceBalance
                                                                                                        .get()))
                                                                                .via(
                                                                                        sendToBobTxns
                                                                                                .apply(
                                                                                                        i))),
                                                        sourcing(() -> sendRecordLookup.apply(i)),
                                                        // Wait until the next period starts
                                                        sleepFor(
                                                                2
                                                                        * secsBeforePeriodEndToDoTransfer
                                                                        * 1000),
                                                        // Back to Alice from Baldwin
                                                        getAccountBalance(baldwin)
                                                                .exposingBalanceTo(
                                                                        currentBaldwinBalance::set),
                                                        sourcing(
                                                                () ->
                                                                        cryptoTransfer(
                                                                                        tinyBarsFromTo(
                                                                                                baldwin,
                                                                                                alice,
                                                                                                currentBaldwinBalance
                                                                                                        .get()))
                                                                                .via(
                                                                                        returnToAliceTxns
                                                                                                .apply(
                                                                                                        i))),
                                                        sourcing(
                                                                () -> returnRecordLookup.apply(i))))
                                .toArray(HapiSpecOperation[]::new))
                .then(
                        withOpContext(
                                (spec, opLog) -> {
                                    final var registry = spec.registry();
                                    final var aliceNum =
                                            registry.getAccountID(alice).getAccountNum();
                                    final var baldwinNum =
                                            registry.getAccountID(baldwin).getAccountNum();
                                    for (int i = 0; i < rewardsPaid.size(); i++) {
                                        if (i % 2 == 0) {
                                            opLog.info(
                                                    "======= Send-to-Baldwin #{} =======", i / 2);
                                        } else {
                                            opLog.info(
                                                    "======= Return-to-Alice #{} =======", i / 2);
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
    private HapiSpec autoRenewalsCanTriggerStakingRewards() {
        final var initBalance = ONE_HBAR * 1000;
        final var minimalLifetime = 3;
        final var creation = "creation";

        return defaultHapiSpec("AutoRenewalsCanTriggerStakingRewards")
                .given(
                        overriding(STAKING_START_THRESHOLD, "" + 10 * ONE_HBAR),
                        overriding(STAKING_REWARD_RATE, "" + SOME_REWARD_RATE),
                        cryptoTransfer(tinyBarsFromTo(GENESIS, STAKING_REWARD, ONE_MILLION_HBARS)),
                        cryptoCreate("miscStaker")
                                .stakedNodeId(0)
                                .balance(ONE_HUNDRED_HBARS * 1000),
                        uploadInitCode(PAY_RECEIVABLE_CONTRACT),
                        sleepFor(INTER_PERIOD_SLEEP_MS))
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
                        sleepFor(INTER_PERIOD_SLEEP_MS),
                        cryptoTransfer(tinyBarsFromTo(GENESIS, NODE, 1L)),
                        sleepFor(INTER_PERIOD_SLEEP_MS))
                .then(cryptoTransfer(tinyBarsFromTo(GENESIS, NODE, 1L)));
    }

    private HapiSpec canBeRewardedWithoutMinStakeIfSoConfigured() {
        final var patientlyWaiting = "patientlyWaiting";

        return defaultHapiSpec("CanBeRewardedWithoutMinStakeIfSoConfigured")
                .given(
                        overridingAllOf(
                                Map.of(
                                        "staking.nodeMaxToMinStakeRatios",
                                        "0:2,1:4",
                                        "staking.requireMinStakeToReward",
                                        "true",
                                        STAKING_START_THRESHOLD,
                                        "100_000_000",
                                        STAKING_REWARD_RATE,
                                        "273972602739726")),
                        // Create the patiently waiting staker
                        cryptoCreate(patientlyWaiting).stakedNodeId(0).balance(ONE_HUNDRED_HBARS),
                        // Activate staking (but without achieving the 25B hbar minstake)
                        cryptoTransfer(tinyBarsFromTo(GENESIS, STAKING_REWARD, ONE_MILLION_HBARS)))
                .when(
                        sleepFor(INTER_PERIOD_SLEEP_MS),
                        cryptoTransfer(tinyBarsFromTo(GENESIS, FUNDING, 1L)),
                        cryptoTransfer(tinyBarsFromTo(patientlyWaiting, FUNDING, 1)),
                        getAccountBalance(patientlyWaiting).logged(),
                        // Now we should be rewardable even though node0 is far from minStake
                        overriding("staking.requireMinStakeToReward", "false"),
                        sleepFor(INTER_PERIOD_SLEEP_MS),
                        cryptoTransfer(tinyBarsFromTo(GENESIS, FUNDING, 1L)),
                        cryptoTransfer(tinyBarsFromTo(patientlyWaiting, FUNDING, 1)))
                .then(getAccountBalance(patientlyWaiting).logged());
    }

    private HapiSpec secondOrderRewardSituationsWork() {
        final long totalStakeStartCase1 = 3 * ONE_HUNDRED_HBARS;
        final long expectedRewardRate = Math.max(0, Math.min(10 * ONE_HBAR, SOME_REWARD_RATE));
        final long rewardSumHistoryCase1 =
                expectedRewardRate
                        / (totalStakeStartCase1 / TINY_PARTS_PER_WHOLE); // should be 333333333
        final long alicePendingRewardsCase1 =
                rewardSumHistoryCase1 * (2 * ONE_HUNDRED_HBARS / TINY_PARTS_PER_WHOLE);
        final long bobPendingRewardsCase1 =
                rewardSumHistoryCase1 * (ONE_HUNDRED_HBARS / TINY_PARTS_PER_WHOLE);

        return defaultHapiSpec("SecondOrderRewardSituationsWork")
                .given(
                        overriding(STAKING_START_THRESHOLD, "" + 10 * ONE_HBAR),
                        overriding(STAKING_REWARD_RATE, "" + SOME_REWARD_RATE),
                        cryptoTransfer(tinyBarsFromTo(GENESIS, STAKING_REWARD, ONE_MILLION_HBARS)))
                .when(
                        cryptoCreate(ALICE).stakedNodeId(0).balance(ONE_HUNDRED_HBARS),
                        cryptoCreate(BOB).stakedNodeId(0).balance(ONE_HUNDRED_HBARS),
                        cryptoCreate(CAROL).stakedAccountId(ALICE).balance(ONE_HUNDRED_HBARS),
                        sleepFor(INTER_PERIOD_SLEEP_MS))
                .then(
                        /* --- paid_rewards 0 for first period --- */
                        cryptoTransfer(tinyBarsFromTo(BOB, ALICE, ONE_HBAR)).via(FIRST_TRANSFER),
                        getTxnRecord(FIRST_TRANSFER)
                                .andAllChildRecords()
                                .stakingFeeExempted()
                                .hasChildRecordCount(1)
                                .hasChildRecords(
                                        recordWith().memo(END_OF_STAKING_PERIOD_CALCULATIONS_MEMO))
                                .hasPaidStakingRewards(List.of()),

                        /* --- second period reward eligible --- */
                        sleepFor(INTER_PERIOD_SLEEP_MS),
                        cryptoUpdate(CAROL)
                                .newStakedAccountId(BOB)
                                .via("secondOrderRewardSituation"),
                        getTxnRecord("secondOrderRewardSituation")
                                .andAllChildRecords()
                                .hasChildRecordCount(1)
                                .hasStakingFeesPaid()
                                .hasChildRecords(
                                        recordWith().memo(END_OF_STAKING_PERIOD_CALCULATIONS_MEMO))
                                .hasPaidStakingRewards(
                                        List.of(
                                                Pair.of(ALICE, alicePendingRewardsCase1),
                                                Pair.of(BOB, bobPendingRewardsCase1)))
                                .logged());
    }

    private HapiSpec evenOneTinybarChangeInIndirectStakingAccountTriggersStakeeRewardSituation() {
        return defaultHapiSpec(
                        "EvenOneTinybarChangeInIndirectStakingAccountTriggersStakeeRewardSituation")
                .given(
                        overriding(STAKING_START_THRESHOLD, "" + 10 * ONE_HBAR),
                        overriding(STAKING_REWARD_RATE, "" + SOME_REWARD_RATE),
                        cryptoTransfer(tinyBarsFromTo(GENESIS, STAKING_REWARD, ONE_MILLION_HBARS)))
                .when(
                        cryptoCreate(ALICE).stakedNodeId(0).balance(ONE_HUNDRED_HBARS),
                        cryptoCreate(BOB).stakedAccountId(ALICE).balance(ONE_HUNDRED_HBARS),
                        cryptoCreate(CAROL).stakedAccountId(ALICE).balance(ONE_HUNDRED_HBARS),
                        sleepFor(INTER_PERIOD_SLEEP_MS),
                        cryptoTransfer(tinyBarsFromTo(DEFAULT_PAYER, FUNDING, 1L)),
                        sleepFor(INTER_PERIOD_SLEEP_MS))
                .then(
                        cryptoTransfer(tinyBarsFromTo(DEFAULT_PAYER, CAROL, 1)).via(FIRST_TRANSFER),
                        getTxnRecord(FIRST_TRANSFER).hasPaidStakingRewardsCount(1));
    }

    private HapiSpec zeroRewardEarnedWithZeroWholeHbarsStillSetsSASOLARP() {
        return defaultHapiSpec("ZeroRewardEarnedWithZeroWholeHbarsStillSetsSASOLARP")
                .given(
                        overriding(STAKING_START_THRESHOLD, "" + 10 * ONE_HBAR),
                        overriding(STAKING_REWARD_RATE, "" + SOME_REWARD_RATE),
                        cryptoTransfer(tinyBarsFromTo(GENESIS, STAKING_REWARD, ONE_MILLION_HBARS)),
                        // Ensure all periods have a non-zero reward rate
                        cryptoCreate("helpfulStaker").stakedNodeId(0).balance(ONE_MILLION_HBARS),
                        sleepFor(INTER_PERIOD_SLEEP_MS))
                .when(
                        cryptoCreate(ALICE).stakedNodeId(0).balance(0L),
                        sleepFor(INTER_PERIOD_SLEEP_MS),
                        cryptoTransfer(tinyBarsFromTo(GENESIS, ALICE, ONE_HUNDRED_HBARS)),
                        sleepFor(INTER_PERIOD_SLEEP_MS),
                        cryptoTransfer(tinyBarsFromTo(ALICE, FUNDING, ONE_HUNDRED_HBARS)),
                        sleepFor(INTER_PERIOD_SLEEP_MS))
                .then(
                        cryptoTransfer(tinyBarsFromTo(DEFAULT_PAYER, ALICE, 1)).via(FIRST_TRANSFER),
                        getTxnRecord(FIRST_TRANSFER).hasPaidStakingRewardsCount(1));
    }

    private HapiSpec losingEvenAZeroBalanceStakerTriggersStakeeRewardSituation() {
        return defaultHapiSpec("LosingEvenAZeroBalanceStakerTriggersStakeeRewardSituation")
                .given(
                        overriding(STAKING_START_THRESHOLD, "" + 10 * ONE_HBAR),
                        overriding(STAKING_REWARD_RATE, "" + SOME_REWARD_RATE),
                        cryptoTransfer(tinyBarsFromTo(GENESIS, STAKING_REWARD, ONE_MILLION_HBARS)))
                .when(
                        cryptoCreate(ALICE).stakedNodeId(0).balance(ONE_HUNDRED_HBARS),
                        cryptoCreate(BOB).stakedAccountId(ALICE).balance(0L),
                        cryptoCreate(CAROL).stakedAccountId(ALICE).balance(ONE_HUNDRED_HBARS),
                        sleepFor(INTER_PERIOD_SLEEP_MS),
                        cryptoTransfer(tinyBarsFromTo(DEFAULT_PAYER, FUNDING, 1L)),
                        sleepFor(INTER_PERIOD_SLEEP_MS))
                .then(
                        cryptoUpdate(BOB).newStakedNodeId(0L).via(FIRST_TRANSFER),
                        getTxnRecord(FIRST_TRANSFER).hasPaidStakingRewardsCount(1));
    }

    private HapiSpec getInfoQueriesReturnsPendingRewards() {
        final long expectedTotalStakedRewardStart = ONE_HUNDRED_HBARS + ONE_HUNDRED_HBARS;
        final long accountTotalStake = ONE_HUNDRED_HBARS;
        final long expectedRewardRate = Math.max(0, Math.min(10 * ONE_HBAR, SOME_REWARD_RATE));
        final long expectedRewardSumHistory =
                expectedRewardRate
                        / (expectedTotalStakedRewardStart
                                / TINY_PARTS_PER_WHOLE); // should be 500_000_000L
        final long expectedPendingReward =
                expectedRewardSumHistory * (accountTotalStake / TINY_PARTS_PER_WHOLE); //
        // should be 500_000_000L

        return defaultHapiSpec("getInfoQueriesReturnsPendingRewards")
                .given(
                        overriding(STAKING_START_THRESHOLD, "" + 10 * ONE_HBAR),
                        overriding(STAKING_REWARD_RATE, "" + SOME_REWARD_RATE),
                        cryptoTransfer(tinyBarsFromTo(GENESIS, STAKING_REWARD, 10 * ONE_HBAR)))
                .when(
                        cryptoCreate(ALICE).stakedNodeId(0).balance(ONE_HUNDRED_HBARS),
                        cryptoCreate(BOB).balance(ONE_HUNDRED_HBARS),
                        uploadInitCode(PAYABLE_CONTRACT),
                        contractCreate(PAYABLE_CONTRACT)
                                .stakedNodeId(0L)
                                .balance(ONE_HUNDRED_HBARS),
                        sleepFor(INTER_PERIOD_SLEEP_MS))
                .then(
                        /* --- staking will be activated, child record is generated at end of staking period --- */
                        cryptoTransfer(tinyBarsFromTo(GENESIS, BOB, ONE_HBAR)).via(FIRST_TXN),
                        getTxnRecord(FIRST_TXN)
                                .andAllChildRecords()
                                .hasChildRecordCount(1)
                                .hasChildRecords(
                                        recordWith().memo(END_OF_STAKING_PERIOD_CALCULATIONS_MEMO))
                                .hasPaidStakingRewards(List.of()),
                        sleepFor(INTER_PERIOD_SLEEP_MS),
                        cryptoTransfer(tinyBarsFromTo(GENESIS, BOB, ONE_HBAR)),

                        /* --- waited enough and account and contract should be eligible for rewards */
                        getAccountInfo(ALICE)
                                .has(
                                        accountWith()
                                                .stakedNodeId(0L)
                                                .pendingRewards(expectedPendingReward)),
                        getContractInfo(PAYABLE_CONTRACT)
                                .has(
                                        contractWith()
                                                .stakedNodeId(0L)
                                                .pendingRewards(expectedPendingReward)),

                        /* -- trigger a txn and see if pays expected reward */
                        cryptoTransfer(tinyBarsFromTo(BOB, ALICE, ONE_HBAR))
                                .payingWith(BOB)
                                .via("rewardTxn"),
                        getTxnRecord("rewardTxn")
                                .andAllChildRecords()
                                .hasChildRecordCount(0)
                                .hasPaidStakingRewards(
                                        List.of(Pair.of(ALICE, expectedPendingReward))),
                        contractCall(PAYABLE_CONTRACT, "deposit", 1_000L)
                                .payingWith(BOB)
                                .sending(1_000L)
                                .via("contractRewardTxn"),
                        getTxnRecord("contractRewardTxn")
                                .andAllChildRecords()
                                .hasChildRecordCount(0)
                                .hasPaidStakingRewards(
                                        List.of(Pair.of(PAYABLE_CONTRACT, expectedPendingReward))));
    }

    private HapiSpec rewardPaymentsNotRepeatedInSamePeriod() {
        return defaultHapiSpec("rewardPaymentsNotRepeatedInSamePeriod")
                .given(
                        overriding(STAKING_START_THRESHOLD, "" + 10 * ONE_HBAR),
                        overriding(STAKING_REWARD_RATE, "" + SOME_REWARD_RATE),
                        cryptoTransfer(tinyBarsFromTo(GENESIS, STAKING_REWARD, 10 * ONE_HBAR)))
                .when(
                        cryptoCreate(ALICE).stakedNodeId(0).balance(ONE_HUNDRED_HBARS),
                        cryptoCreate(BOB).balance(ONE_HUNDRED_HBARS),
                        uploadInitCode(PAYABLE_CONTRACT),
                        contractCreate(PAYABLE_CONTRACT)
                                .stakedNodeId(0L)
                                .balance(ONE_HUNDRED_HBARS),
                        sleepFor(INTER_PERIOD_SLEEP_MS))
                .then(
                        /* --- staking will be activated in the previous suite, child record is generated at end of
                        staking period. But
                        since rewardsSunHistory will be 0 for the first staking period after rewards are activated ,
                        paid_rewards will be 0 --- */
                        cryptoTransfer(tinyBarsFromTo(BOB, ALICE, ONE_HBAR)).via(FIRST_TXN),
                        getTxnRecord(FIRST_TXN)
                                .andAllChildRecords()
                                .hasChildRecordCount(1)
                                .hasChildRecords(
                                        recordWith().memo(END_OF_STAKING_PERIOD_CALCULATIONS_MEMO))
                                .hasPaidStakingRewards(List.of()),

                        /* should receive reward */
                        sleepFor(INTER_PERIOD_SLEEP_MS),
                        contractUpdate(PAYABLE_CONTRACT)
                                .newDeclinedReward(true)
                                .via("acceptsReward"),
                        getTxnRecord("acceptsReward")
                                .logged()
                                .andAllChildRecords()
                                .hasChildRecordCount(1)
                                .hasChildRecords(
                                        recordWith().memo(END_OF_STAKING_PERIOD_CALCULATIONS_MEMO))
                                .hasPaidStakingRewards(
                                        List.of(Pair.of(PAYABLE_CONTRACT, 500000000L))),
                        contractUpdate(PAYABLE_CONTRACT)
                                .newStakedNodeId(1L)
                                .hasPrecheck(INVALID_STAKING_ID),
                        contractUpdate(PAYABLE_CONTRACT)
                                .newStakedAccountId(BOB)
                                .via("samePeriodTxn"),
                        getTxnRecord("samePeriodTxn")
                                .andAllChildRecords()
                                .hasChildRecordCount(0)
                                .hasPaidStakingRewards(List.of()),

                        /* --- next period, so child record is generated at end of staking period.
                        Since rewardsSumHistory is updated during the previous staking period after rewards are
                        activated ,paid_rewards will be non-empty in this record --- */
                        sleepFor(INTER_PERIOD_SLEEP_MS),
                        cryptoTransfer(tinyBarsFromTo(BOB, ALICE, ONE_HBAR))
                                .payingWith(BOB)
                                .via(FIRST_TRANSFER),
                        getTxnRecord(FIRST_TRANSFER)
                                .andAllChildRecords()
                                .hasChildRecordCount(1)
                                .hasStakingFeesPaid()
                                .hasChildRecords(
                                        recordWith().memo(END_OF_STAKING_PERIOD_CALCULATIONS_MEMO))
                                .hasPaidStakingRewards(List.of(Pair.of(ALICE, 500000000L)))
                                .logged(),
                        /* Within the same period rewards are not awarded twice */
                        cryptoTransfer(tinyBarsFromTo(BOB, ALICE, ONE_HBAR))
                                .payingWith(BOB)
                                .via("samePeriodTransfer"),
                        getTxnRecord("samePeriodTransfer")
                                .andAllChildRecords()
                                .hasChildRecordCount(0)
                                .hasStakingFeesPaid()
                                .hasPaidStakingRewards(List.of())
                                .logged(),
                        cryptoUpdate(ALICE).newStakedAccountId(BOB).via("samePeriodUpdate"),
                        getTxnRecord("samePeriodUpdate")
                                .logged()
                                .andAllChildRecords()
                                .hasChildRecordCount(0)
                                .stakingFeeExempted()
                                .hasPaidStakingRewards(List.of()));
    }

    private HapiSpec rewardsWorkAsExpected() {
        final long expectedTotalStakedRewardStart = ONE_HUNDRED_HBARS + ONE_HBAR;
        final long expectedRewardRate = Math.max(0, Math.min(10 * ONE_HBAR, SOME_REWARD_RATE));
        final long expectedRewardSumHistory =
                expectedRewardRate
                        / (expectedTotalStakedRewardStart
                                / TINY_PARTS_PER_WHOLE); // should be 9900990L
        final long expectedPendingRewards =
                expectedRewardSumHistory
                        * (expectedTotalStakedRewardStart / TINY_PARTS_PER_WHOLE); // should be
        // 999999990L

        return defaultHapiSpec("rewardsWorkAsExpected")
                .given(
                        overriding(STAKING_START_THRESHOLD, "" + 10 * ONE_HBAR),
                        overriding(STAKING_REWARD_RATE, "" + SOME_REWARD_RATE),
                        cryptoTransfer(tinyBarsFromTo(GENESIS, STAKING_REWARD, ONE_HBAR)))
                .when(
                        cryptoCreate(ALICE).stakedNodeId(0).balance(ONE_HUNDRED_HBARS),
                        cryptoCreate(BOB).balance(ONE_HUNDRED_HBARS),
                        sleepFor(INTER_PERIOD_SLEEP_MS))
                .then(
                        /* --- staking not active, so no child record for end of staking period are generated --- */
                        cryptoTransfer(tinyBarsFromTo(BOB, ALICE, ONE_HBAR))
                                .via("noRewardTransfer"),
                        getTxnRecord("noRewardTransfer")
                                .stakingFeeExempted()
                                .andAllChildRecords()
                                .hasChildRecordCount(0),

                        /* --- staking will be activated, so child record is generated at end of staking period. But
                        since rewardsSumHistory will be 0 for the first staking period after rewards are activated ,
                        paid_rewards will be 0 --- */
                        cryptoTransfer(tinyBarsFromTo(GENESIS, STAKING_REWARD, 9 * ONE_HBAR)),
                        sleepFor(INTER_PERIOD_SLEEP_MS),
                        cryptoTransfer(tinyBarsFromTo(BOB, ALICE, ONE_HBAR)).via(FIRST_TRANSFER),
                        getTxnRecord(FIRST_TRANSFER)
                                .andAllChildRecords()
                                .stakingFeeExempted()
                                .hasChildRecordCount(1)
                                .hasChildRecords(
                                        recordWith().memo(END_OF_STAKING_PERIOD_CALCULATIONS_MEMO))
                                .hasPaidStakingRewards(List.of()),

                        /* --- staking is activated, so child record is generated at end of staking period.
                        Since rewardsSumHistory is updated during the previous staking period after rewards are
                        activated ,
                        paid_rewards will be non-empty in this record --- */
                        sleepFor(INTER_PERIOD_SLEEP_MS),
                        cryptoTransfer(tinyBarsFromTo(BOB, ALICE, ONE_HBAR))
                                .payingWith(BOB)
                                .via("secondTransfer"),
                        getTxnRecord("secondTransfer")
                                .andAllChildRecords()
                                .hasChildRecordCount(1)
                                .hasStakingFeesPaid()
                                .hasChildRecords(
                                        recordWith().memo(END_OF_STAKING_PERIOD_CALCULATIONS_MEMO))
                                .hasPaidStakingRewards(
                                        List.of(Pair.of(ALICE, expectedPendingRewards))),

                        /* Within the same period rewards are not awarded twice */
                        cryptoTransfer(tinyBarsFromTo(BOB, ALICE, ONE_HBAR))
                                .payingWith(BOB)
                                .via("expectNoReward"),
                        getTxnRecord("expectNoReward")
                                .andAllChildRecords()
                                .hasChildRecordCount(0)
                                .hasStakingFeesPaid()
                                .hasPaidStakingRewards(List.of()));
    }

    private HapiSpec endOfStakingPeriodRecTest() {
        return defaultHapiSpec("EndOfStakingPeriodRecTest")
                .given(
                        cryptoCreate("a1").balance(ONE_HUNDRED_HBARS).stakedNodeId(0),
                        cryptoCreate("a2").balance(ONE_HUNDRED_HBARS).stakedNodeId(0),
                        cryptoTransfer(
                                tinyBarsFromTo(
                                        GENESIS,
                                        STAKING_REWARD,
                                        ONE_MILLION_HBARS)) // will trigger staking
                        )
                .when(sleepFor(INTER_PERIOD_SLEEP_MS))
                .then(
                        cryptoTransfer(tinyBarsFromTo("a1", "a2", ONE_HBAR)).via("trigger"),
                        getTxnRecord("trigger")
                                .logged()
                                .hasChildRecordCount(1)
                                .hasChildRecords(
                                        recordWith().memo(END_OF_STAKING_PERIOD_CALCULATIONS_MEMO)),
                        sleepFor(INTER_PERIOD_SLEEP_MS),
                        cryptoTransfer(tinyBarsFromTo("a1", "a2", ONE_HBAR)).via("transfer"),
                        getTxnRecord("transfer")
                                .hasChildRecordCount(1)
                                .hasChildRecords(
                                        recordWith().memo(END_OF_STAKING_PERIOD_CALCULATIONS_MEMO))
                                .logged(),
                        cryptoTransfer(tinyBarsFromTo("a1", "a2", ONE_HBAR))
                                .via("noEndOfStakingPeriodRecord"),
                        getTxnRecord("noEndOfStakingPeriodRecord").hasChildRecordCount(0).logged(),
                        sleepFor(INTER_PERIOD_SLEEP_MS),
                        cryptoTransfer(tinyBarsFromTo("a1", "a2", ONE_HBAR)).via("transfer1"),
                        getTxnRecord("transfer1")
                                .hasChildRecordCount(1)
                                .hasChildRecords(
                                        recordWith().memo(END_OF_STAKING_PERIOD_CALCULATIONS_MEMO))
                                .logged());
    }

    private HapiSpec rewardsOfDeletedAreRedirectedToBeneficiary() {
        final var bob = "bob";
        final var deletion = "deletion";
        return defaultHapiSpec("RewardsOfDeletedAreRedirectedToBeneficiary")
                .given(
                        overriding(STAKING_START_THRESHOLD, "" + 10 * ONE_HBAR),
                        cryptoTransfer(tinyBarsFromTo(GENESIS, STAKING_REWARD, ONE_MILLION_HBARS)))
                .when(
                        cryptoCreate(ALICE).stakedNodeId(0).balance(33_000 * ONE_MILLION_HBARS),
                        cryptoCreate(bob).balance(0L),
                        sleepFor(150_000))
                .then(
                        cryptoDelete(ALICE).transfer(bob).via(deletion),
                        getTxnRecord(deletion).andAllChildRecords().logged());
    }
}
