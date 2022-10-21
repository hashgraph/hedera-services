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
package com.hedera.services.ledger;

import static com.hedera.services.ledger.accounts.staking.StakingUtils.roundedToHbar;
import static com.hedera.services.ledger.properties.AccountProperty.BALANCE;
import static com.hedera.services.ledger.properties.AccountProperty.STAKED_ID;
import static com.hedera.services.setup.Constructables.FIRST_NODE_I;
import static com.hedera.services.setup.Constructables.FIRST_USER_I;
import static com.hedera.services.setup.Constructables.FUNDING_ID;
import static com.hedera.services.setup.Constructables.PRETEND_AMOUNT;
import static com.hedera.services.setup.Constructables.PRETEND_FEE;
import static com.hedera.services.setup.Constructables.SECS_PER_DAY;
import static com.hedera.services.setup.Constructables.SOME_TIME;
import static com.hedera.services.setup.InfrastructureInitializer.initializeStakeableAccounts;
import static com.hedera.services.setup.InfrastructureManager.loadOrCreateBundle;
import static com.hedera.services.setup.InfrastructureType.ACCOUNTS_MM;
import static com.hedera.services.setup.InfrastructureType.STAKING_INFOS_MM;

import com.hedera.services.context.SideEffectsTracker;
import com.hedera.services.ledger.accounts.staking.RewardCalculator;
import com.hedera.services.ledger.properties.AccountProperty;
import com.hedera.services.setup.Constructables;
import com.hedera.services.setup.InfrastructureBundle;
import com.hedera.services.setup.InfrastructureType;
import com.hedera.services.state.migration.HederaAccount;
import com.hedera.services.utils.EntityNum;
import com.hederahashgraph.api.proto.java.AccountID;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;
import java.util.concurrent.atomic.AtomicLong;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;

@State(Scope.Benchmark)
@Fork(1)
@Warmup(iterations = 1, time = 10)
@Measurement(iterations = 1, time = 30)
public class StakingActivityBench {
    private int i;
    private long n;
    private int round;
    private AccountID[] ids;

    @Param("3")
    int numNodes;

    @Param("1000")
    int stakeableAccounts;

    @Param("10000")
    int actionsPerRound;

    @Param("10")
    int roundsPerPeriod;

    @Param("5")
    int meanDaysBeforeNewStakePeriodStart;

    @Param("0.50")
    double stakeToNodeProb;

    @Param("0.40")
    double stakeToAccountProb;

    private int callsBetweenStakingChange;
    private RewardCalculator rewardCalculator;
    private StakingActivityApp app;
    private SideEffectsTracker sideEffects;
    private InfrastructureBundle bundle;
    private TransactionalLedger<AccountID, AccountProperty, HederaAccount> ledger;

    @Setup(Level.Trial)
    public void setupInfrastructure() {
        Constructables.registerForMerkleMap();
        Constructables.registerForAccounts();
        Constructables.registerForStakingInfo();

        bundle = loadOrCreateBundle(activeConfig(), requiredInfra());
        app = DaggerStakingActivityApp.builder().bundle(bundle).build();
        ledger = app.stakingLedger();
        sideEffects = app.sideEffects();
        rewardCalculator = app.rewardCalculator();

        ids = new AccountID[stakeableAccounts + 1001];
        for (int j = 1; j < stakeableAccounts + 1001; j++) {
            ids[j] = AccountID.newBuilder().setAccountNum(j).build();
        }
        i = 0;
        n = 0;

        initializeStakeableAccounts(
                new SplittableRandom(Constructables.SEED),
                activeConfig(),
                app.backingAccounts(),
                app.stakingInfos().get(),
                ledger);
        compareStakeToReward();

        app.txnCtx().resetFor(null, SOME_TIME, 0L);
        app.networkCtx().get().setStakingRewardsActivated(true);
        System.out.println("Beginning period is " + app.periodManager().currentStakePeriod());

        final var actionsPerDay = (long) actionsPerRound * roundsPerPeriod;
        final var avgDailyActionsPerAccount = actionsPerDay / stakeableAccounts;
        callsBetweenStakingChange =
                (int) (meanDaysBeforeNewStakePeriodStart * avgDailyActionsPerAccount);
        System.out.println(
                "A staking change will occur every ~"
                        + callsBetweenStakingChange
                        + " benchmark invocations");
    }

    @Setup(Level.Invocation)
    public void simulateRoundBoundary() {
        if (n > 0 && n % actionsPerRound == 0) {
            bundle.newRound();
            round++;
            if (round % roundsPerPeriod == 0) {
                final var finishedPeriodOffset = (round / roundsPerPeriod) - 1;
                final var finishedTimeRepresentative =
                        SOME_TIME.plusSeconds(finishedPeriodOffset * SECS_PER_DAY);
                System.out.println(
                        "\n--- END OF PERIOD @ "
                                + finishedTimeRepresentative
                                + " -> "
                                + app.periodManager().currentStakePeriod()
                                + " ---");
                app.endOfPeriodCalcs().updateNodes(finishedTimeRepresentative);
                final var currentPeriodTimeRepresentative =
                        finishedTimeRepresentative.plusSeconds(SECS_PER_DAY);
                app.txnCtx().resetFor(null, currentPeriodTimeRepresentative, 0L);
                System.out.println(
                        "- Current period is now   :: " + app.periodManager().currentStakePeriod());
                compareStakeToReward();
                System.out.println(
                        "- Pending rewards are now :: " + app.networkCtx().get().pendingRewards());
                System.out.println("- Summed detail rewards   :: " + sumDetailRewards());
            }
        }
    }

    @TearDown
    public void payAllRewards() {
        System.out.println("\nFinished");
        for (int i = 0; i < stakeableAccounts; i++) {
            rewardCalculator.reset();
            sideEffects.reset();
            ledger.begin();
            final var id = ids[FIRST_USER_I + i];
            ledger.set(id, BALANCE, (long) ledger.get(id, BALANCE) - 1);
            ledger.set(FUNDING_ID, BALANCE, (long) ledger.get(FUNDING_ID, BALANCE) + 1);
            ledger.commit();
        }
        System.out.println(
                "Pending rewards after forcing all reward payments: "
                        + app.networkCtx().get().pendingRewards());
        compareStakeToReward();
    }

    private void compareStakeToReward() {
        final var curAccounts = app.accounts().get();
        final Map<EntityNum, Long> summedStakesToReward = new HashMap<>();
        for (int i = 0; i < stakeableAccounts; i++) {
            final var num = EntityNum.fromLong(FIRST_USER_I + i);
            final var account = curAccounts.get(num);
            if (account.getStakedId() < 0) {
                final var nodeId = account.getStakedNodeAddressBookId();
                summedStakesToReward.merge(
                        EntityNum.fromLong(nodeId), roundedToHbar(account.totalStake()), Long::sum);
            }
        }
        summedStakesToReward.forEach(
                (num, amount) -> {
                    final var info = app.stakingInfos().get().get(num);
                    System.out.println(
                            "- node"
                                    + num.longValue()
                                    + " has Merkle stakeToReward "
                                    + info.getStakeToReward()
                                    + " vs "
                                    + amount
                                    + " summed stakeToReward");
                });
    }

    private long sumDetailRewards() {
        var detailPendingRewards = new AtomicLong();
        final var stakePeriodManager = app.periodManager();
        final var curPeriod = stakePeriodManager.currentStakePeriod();
        app.accounts()
                .get()
                .forEach(
                        (num, account) -> {
                            if (account.mayHavePendingReward()) {
                                final var info =
                                        app.stakingInfos()
                                                .get()
                                                .get(
                                                        EntityNum.fromLong(
                                                                account
                                                                        .getStakedNodeAddressBookId()));
                                final var detailReward =
                                        rewardCalculator.computeRewardFromDetails(
                                                account,
                                                info,
                                                curPeriod,
                                                stakePeriodManager.effectivePeriod(
                                                        account.getStakePeriodStart()));
                                detailPendingRewards.getAndAdd(detailReward);
                            }
                        });
        return detailPendingRewards.get();
    }

    @Benchmark
    public void stakingActivities() {
        // Also the staker in the case of a changed staking election
        final var payerId = advanceToNextId();
        // Also the stakee in the case of a staking election to account
        final var countpartyId = advanceToNextId();
        // Also the stakee in the case of a staking election to node
        advanceI();
        final var nodeI = Math.floorMod(i, numNodes);

        rewardCalculator.reset();
        sideEffects.reset();
        ledger.begin();
        // First we credit fees
        ledger.set(FUNDING_ID, BALANCE, (long) ledger.get(FUNDING_ID, BALANCE) + PRETEND_FEE / 2);
        final var nodeId = ids[FIRST_NODE_I + nodeI];
        ledger.set(nodeId, BALANCE, (long) ledger.get(nodeId, BALANCE) + PRETEND_FEE / 2);
        // Then we either change a staking election, or do a simple transfer
        if (n % callsBetweenStakingChange == 0) {
            ledger.set(payerId, BALANCE, (long) ledger.get(payerId, BALANCE) - PRETEND_FEE);
            // Stake to an account one-third of the time, a node two-thirds
            if (n % 3 == 0) {
                if (!payerId.equals(countpartyId)) {
                    ledger.set(payerId, STAKED_ID, countpartyId.getAccountNum());
                }
            } else {
                ledger.set(payerId, STAKED_ID, -nodeI - 1L);
            }
        } else {
            if (payerId.equals(countpartyId)) {
                ledger.set(payerId, BALANCE, (long) ledger.get(payerId, BALANCE) - PRETEND_FEE);
            } else {
                // Just do a simple transfer
                ledger.set(
                        payerId,
                        BALANCE,
                        (long) ledger.get(payerId, BALANCE) - PRETEND_FEE - PRETEND_AMOUNT);
                ledger.set(
                        countpartyId,
                        BALANCE,
                        (long) ledger.get(countpartyId, BALANCE) + PRETEND_AMOUNT);
            }
        }
        ledger.commit();

        n++;
    }

    private AccountID advanceToNextId() {
        return ids[FIRST_USER_I + Math.floorMod(i, stakeableAccounts)];
    }

    private void advanceI() {
        i = i * Constructables.MULTIPLIER + Constructables.ADDEND;
    }

    // --- Helpers ---
    private Map<String, Object> activeConfig() {
        return Map.of(
                "stakeableAccounts", stakeableAccounts,
                "stakeToNodeProb", stakeToNodeProb,
                "stakeToAccountProb", stakeToAccountProb,
                "nodeIds", numNodes);
    }

    private List<InfrastructureType> requiredInfra() {
        return List.of(ACCOUNTS_MM, STAKING_INFOS_MM);
    }
}
