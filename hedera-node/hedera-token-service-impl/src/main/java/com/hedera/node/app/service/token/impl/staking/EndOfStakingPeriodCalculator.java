/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.token.impl.staking;

import static com.hedera.node.app.service.mono.context.properties.PropertyNames.STAKING_MAX_DAILY_STAKE_REWARD_THRESH_PER_HBAR;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.STAKING_PERIOD_MINS;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.STAKING_REWARD_HISTORY_NUM_STORED_PERIODS;
import static com.hedera.node.app.service.mono.records.TxnAwareRecordsHistorian.DEFAULT_SOURCE_ID;
import static com.hedera.node.app.service.mono.state.EntityCreator.NO_CUSTOM_FEES;
import static com.hedera.node.app.service.mono.utils.Units.HBARS_TO_TINYBARS;
import static com.hedera.node.app.service.token.impl.handlers.BaseCryptoHandler.asAccount;
import static com.hedera.node.app.service.token.impl.staking.StakingUtils.calculateUpdatedRewardSumHistory;
import static com.hedera.node.app.service.token.impl.staking.StakingUtils.readableNonZeroHistory;

import com.google.common.annotations.VisibleForTesting;
import com.hedera.hapi.node.base.Fraction;
import com.hedera.hapi.node.base.Timestamp;
import com.hedera.hapi.node.state.token.StakingNodeInfo;
import com.hedera.hapi.node.transaction.NodeStake;
import com.hedera.hapi.node.transaction.NodeStakeUpdateTransactionBody;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.mono.context.SideEffectsTracker;
import com.hedera.node.app.service.mono.context.properties.PropertySource;
import com.hedera.node.app.service.mono.records.RecordsHistorian;
import com.hedera.node.app.service.mono.state.EntityCreator;
import com.hedera.node.app.service.mono.state.merkle.MerkleNetworkContext;
import com.hedera.node.app.service.mono.store.contracts.precompile.SyntheticTxnFactory;
import com.hedera.node.app.service.token.ReadableAccountStore;
import com.hedera.node.app.service.token.impl.WritableAccountStore;
import com.hedera.node.app.service.token.impl.WritableStakingInfoStoreImpl;
import com.hedera.node.app.spi.numbers.HederaAccountNumbers;
import com.hedera.node.config.data.StakingConfig;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Singleton
public class EndOfStakingPeriodCalculator {

    private static final Logger log = LogManager.getLogger(EndOfStakingPeriodCalculator.class);

    // The exact choice of precision will not have a large effect on the per-hbar reward rate
    private static final MathContext MATH_CONTEXT = new MathContext(8, RoundingMode.DOWN);
    public static final String END_OF_STAKING_PERIOD_CALCULATIONS_MEMO = "End of staking period calculation record";
    private static final SideEffectsTracker NO_OTHER_SIDE_EFFECTS = new SideEffectsTracker();

    private final Supplier<MerkleNetworkContext> networkCtx;
    private final SyntheticTxnFactory syntheticTxnFactory;      // todo remove?
    private final RecordsHistorian recordsHistorian;    // todo replace with record builder
    private final EntityCreator creator;    // todo



    private final HederaAccountNumbers accountNumbers;
    private final StakingConfig stakingConfig;

    @Inject
    public EndOfStakingPeriodCalculator(
            @NonNull final StakingConfig stakingConfig,
            @NonNull final HederaAccountNumbers accountNumbers,
            final Supplier<MerkleNetworkContext> networkCtx,
            final SyntheticTxnFactory syntheticTxnFactory,
            final RecordsHistorian recordsHistorian,
            final EntityCreator creator
            //            @NonNull final RecordBuilder
) {
        this.networkCtx = networkCtx;
        this.syntheticTxnFactory = syntheticTxnFactory;
        this.recordsHistorian = recordsHistorian;
        this.creator = creator;

        this.accountNumbers = accountNumbers;
        this.stakingConfig = stakingConfig;
    }

    /**
     * todo
     *
     * @param consensusTime
     * @param writableStakingInfoStore
     * @param writableAccountStore
     */
    public void updateNodes(@NonNull final Instant consensusTime,
            @NonNull final WritableStakingInfoStoreImpl writableStakingInfoStore,
            @NonNull final WritableAccountStore writableAccountStore) {
        log.info("Updating node stakes for a just-finished period @ {}", consensusTime);

        if (!stakingConfig.isEnabled()) {
            log.info("Staking not enabled, nothing to do");
            return;
        }

        final var curNetworkCtx = networkCtx.get();
        final var nodeIds = writableStakingInfoStore.getAll();
        final var totalStakedRewardStart = curNetworkCtx.getTotalStakedRewardStart();
        final var rewardRate = rewardRateForEndingPeriod(totalStakedRewardStart, writableAccountStore);
        // The tinybars earned per hbar for stakers who were staked to a node whose total stakedRewardStart for the ending period was in the range [minStake, maxStake]
        final var perHbarRate = totalStakedRewardStart < HBARS_TO_TINYBARS
                ? 0
                : rewardRate / (totalStakedRewardStart / HBARS_TO_TINYBARS);
        log.info(
                "The reward rate for the period was {} tb ({} tb/hbar for nodes with in-range stake, given {} total stake reward start)",
                rewardRate,
                perHbarRate,
                totalStakedRewardStart);

        long newTotalStakedStart = 0L;
        long newTotalStakedRewardStart = 0L;
        long maxStakeOfAllNodes = 0L;

        final List<StakingNodeInfo> newNodeStakingInfos = new ArrayList<>();
        final Map<Long, StakingNodeInfo> currStakingInfos = new HashMap<>();
        final Map<Long, StakingNodeInfo> firstPassUpdatedStakingInfos = new HashMap<>();

        // First pass: //
        for (final var nodeNum : nodeIds.stream().sorted().toList()) {
            final var currStakingInfo = writableStakingInfoStore.getForModify(nodeNum);
            final var newStakingInfo = currStakingInfo.copyBuilder();
            currStakingInfos.put(nodeNum, currStakingInfo);

            // The return value is the reward rate (tinybars-per-hbar-staked-to-reward) that will be paid to all accounts who had staked-to-reward for this node long enough to be eligible in the just-finished period
            final var newRewardSumHistory = calculateUpdatedRewardSumHistory(currStakingInfo,
                    perHbarRate, stakingConfig.maxDailyStakeRewardThPerH(), stakingConfig.requireMinStakeToReward());
            log.info("   > Non-zero reward sum history is now {}", () -> readableNonZeroHistory(newRewardSumHistory.rewardSumHistory()));

            final var oldStakeRewardStart = currStakingInfo.stakeRewardStart();
            final var pendingRewardHbars = (oldStakeRewardStart - currStakingInfo.unclaimedStakeRewardStart()) / HBARS_TO_TINYBARS;
            final var recomputedStake = StakingUtils.computeStake(currStakingInfo);
            final var newStakeRewardStart = recomputedStake.stakeRewardStart();
            newStakingInfo.stake(recomputedStake.stake());
            newStakingInfo.stakeRewardStart(recomputedStake.stakeRewardStart());
            final var nodePendingRewards = pendingRewardHbars * newRewardSumHistory.pendingRewardRate();
            log.info(
                    "For node{}, the tb/hbar reward rate was {} for {} pending, " + "with stake reward start {} -> {}",
                    nodeNum,
                    newRewardSumHistory,
                    nodePendingRewards,
                    oldStakeRewardStart,
                    newStakeRewardStart);
            curNetworkCtx.increasePendingRewards(nodePendingRewards);
            newStakingInfo.unclaimedStakeRewardStart(0);

            newTotalStakedRewardStart += newStakeRewardStart;
            newTotalStakedStart += currStakingInfo.stake();
            // Update the max stake of all nodes
            maxStakeOfAllNodes = Math.max(maxStakeOfAllNodes, currStakingInfo.stake());
            // Build the node stake infos for the record
            firstPassUpdatedStakingInfos.put(nodeNum, newStakingInfo.build());
        }

        // Second pass: //
        // Update node stake infos for the record with the updated consensus weights. The weights are updated based on the updated stake of the node.
        final var sumOfConsensusWeights = stakingConfig.sumOfConsensusWeights();
        for (final Map.Entry<Long, StakingNodeInfo> entry : firstPassUpdatedStakingInfos.entrySet()) {
            final var nodeNum = entry.getKey();
            final var firstPassStakingInfo = entry.getValue();
            final var secondPassStakingInfoBuilder = firstPassStakingInfo.copyBuilder();
            // If the total stake(rewarded + non-rewarded) of a node is less than minStake, stakingInfo's stake field represents 0, as per calculation done in reviewElectionsAndRecomputeStakes. Similarly, the total stake(rewarded + non-rewarded) of the node is greater than maxStake, stakingInfo's stake field is set to maxStake.So, there is no need to clamp the stake value here. Sum of all stakes can be used to calculate the weight.
            final var updatedWeight =
                    calculateWeightFromStake(firstPassStakingInfo.stake(), newTotalStakedStart, sumOfConsensusWeights);
            final var oldWeight = firstPassStakingInfo.weight();
            secondPassStakingInfoBuilder.weight(updatedWeight);
            log.info("Node {} weight is calculated. Old weight {}, updated weight {}", nodeNum, oldWeight, updatedWeight);

            // Scale the consensus weight range [0, sumOfConsensusWeights] to [minStake, trueMaxStakeOfAllNodes] range and export to mirror node. We need to consider the true maxStakeOfAllNodes instead of maxStake becausefor a one node network, whose stake < maxStake, we assign a weight of sumOfConsensusWeights to the node. When we scale it back to stake, we need to give back the real stake of the node instead of maxStake set on the node.
            final var scaledWeightToStake = scaleUpWeightToStake(
                    updatedWeight,
                    firstPassStakingInfo.minStake(),
                    maxStakeOfAllNodes,
                    newTotalStakedStart,
                    sumOfConsensusWeights);
            secondPassStakingInfoBuilder.stake(scaledWeightToStake);
            newNodeStakingInfos.add(secondPassStakingInfoBuilder.build());
        }

        curNetworkCtx.setTotalStakedRewardStart(newTotalStakedRewardStart);
        curNetworkCtx.setTotalStakedStart(newTotalStakedStart);
        log.info(
                "Total stake start is now {} ({} rewarded), pending rewards are {} vs 0.0.800" + " balance {}",
                newTotalStakedStart,
                newTotalStakedRewardStart,
                curNetworkCtx.pendingRewards(),
                rewardsBalance(writableAccountStore));

        final var syntheticNodeStakeUpdateTxn = nodeStakeUpdate(lastInstantOfPreviousPeriodFor(consensusTime), newNodeStakingInfos, stakingConfig);
        log.info("Exporting:\n{}", newNodeStakingInfos);

        // todo: replace the records historian with the record builder
        recordsHistorian.trackPrecedingChildRecord(
                DEFAULT_SOURCE_ID,
                syntheticNodeStakeUpdateTxn,
                creator.createSuccessfulSyntheticRecord(
                        NO_CUSTOM_FEES, NO_OTHER_SIDE_EFFECTS, END_OF_STAKING_PERIOD_CALCULATIONS_MEMO));
    }

    public TransactionBody.Builder nodeStakeUpdate(
            final com.hedera.hapi.node.base.Timestamp stakingPeriodEnd, final List<NodeStake> nodeStakes, StakingConfig stakingConfig, Sta) {
        final var stakingRewardRate = stakingConfig.rewardRate();
        final var threshold = stakingConfig.startThreshold();
        final var stakingPeriod = stakingConfig.periodMins();
        final var stakingPeriodsStored = stakingConfig.rewardHistoryNumStoredPeriods();
        final var maxStakingRewardRateThPerH = stakingConfig.maxDailyStakeRewardThPerH();

        final var nodeRewardFeeFraction = com.hedera.hapi.node.base.Fraction.newBuilder()
                .numerator(stakingConfig.feesNodeRewardPercentage())
                .denominator(100L)
                .build();
        final var stakingRewardFeeFraction = Fraction.newBuilder()
                .numerator(stakingConfig.feesStakingRewardPercentage())
                .denominator(100L)
                .build();

        final var txnBody = NodeStakeUpdateTransactionBody.newBuilder()
                .endOfStakingPeriod(stakingPeriodEnd)
                .nodeStake(nodeStakes)
                .maxStakingRewardRatePerHbar(maxStakingRewardRateThPerH)
                .nodeRewardFeeFraction(nodeRewardFeeFraction)
                .stakingPeriodsStored(stakingPeriodsStored)
                .stakingPeriod(stakingPeriod)
                .stakingRewardFeeFraction(stakingRewardFeeFraction)
                .stakingStartThreshold(threshold)
                .stakingRewardRate(stakingRewardRate)
                .build();

        return com.hedera.hapi.node.transaction.TransactionBody.newBuilder().nodeStakeUpdate(txnBody);
    }

    /**
     * Scales up the weight of the node to the range [minStake, maxStakeOfAllNodes] from the consensus weight range [0, sumOfConsensusWeights].
     * @param weight weight of the node
     * @param newMinStake min stake of the node
     * @param newMaxStake real max stake of all nodes computed by taking max(stakeOfNode1, stakeOfNode2, ...)
     * @return scaled weight of the node
     */
    static long scaleUpWeightToStake(
            final int weight,
            final long newMinStake,
            final long newMaxStake,
            final long totalStakeOfAllNodes,
            final int sumOfConsensusWeights) {
        final var zeroStake = 0;
        // If zero stake return zero
        if (weight == zeroStake) {
            return zeroStake;
        } else {
            // This should never happen, but if it does, return zero
            if (totalStakeOfAllNodes == 0) {
                return 0;
            }
            final var oldMinWeight = 1L;
            // Since we are calculating weights based on the real stake values, we need to consider
            // the real max Stake and not theoretical max stake of nodes.
            // Otherwise, on a one node network with a stake < maxStake where we assign a weight of
            // sumOfConsensusWeights,
            // the scaled stake value will be greater than its real stake.
            final var oldMaxWeight = BigInteger.valueOf(newMaxStake)
                    .multiply(BigInteger.valueOf(sumOfConsensusWeights))
                    .divide(BigInteger.valueOf(totalStakeOfAllNodes))
                    .longValue();
            // Otherwise compute the interpolation of the weight in the range [minStake, maxStake]
            return BigInteger.valueOf(newMaxStake)
                    .subtract(BigInteger.valueOf(newMinStake))
                    .multiply(BigInteger.valueOf(weight - oldMinWeight))
                    .divide(BigInteger.valueOf(oldMaxWeight - oldMinWeight))
                    .add(BigInteger.valueOf(newMinStake))
                    .longValue();
        }
    }

    /**
     * Calculates consensus weight of the node. The network normalizes the weights of nodes above minStake so that the
     * total sum of weight is approximately as described by {@code GlobalDynamicProperties#sumOfConsensusWeights}.
     * The stake field in {@code MerkleStakingInfo} is already clamped to [minStake, maxStake].
     * If stake is less than minStake the weight of a node A will be 0. If stake is greater than minStake, the weight of a node A
     * will be computed so that every node above minStake has weight at least 1; but any node that has staked at least 1
     * out of every 250 whole hbars staked will have weight >= 2.
     * @param stake the stake of current node, includes stake rewarded and non-rewarded
     * @param totalStakeOfAllNodes the total stake of all nodes at the start of new period
     * @return calculated consensus weight of the node
     */
    static int calculateWeightFromStake(final long stake, final long totalStakeOfAllNodes, final int sumOfConsensusWeights) {
        // if node's total stake is less than minStake, MerkleStakingInfo stake will be zero as per calculation
        // in reviewElectionsAndRecomputeStakes and weight will be zero.
        if (stake == 0) return 0;

        // If a node's stake is not zero then totalStakeOfAllNodes can't be zero. This error should never happen. It is added to avoid divide by zero exception, in case of any bug.
        if (totalStakeOfAllNodes <= 0L) {
            log.warn("Total stake of all nodes should be greater than 0. But got {}", totalStakeOfAllNodes);
            return 0;
        }
        final var weight = BigInteger.valueOf(stake)
                .multiply(BigInteger.valueOf(sumOfConsensusWeights))
                .divide(BigInteger.valueOf(totalStakeOfAllNodes))
                .longValue();
        return (int) Math.max(weight, 1);
    }

    /**
     * Given the amount that was staked to reward at the start of the period that is now ending, returns
     * the effective per-hbar reward rate for the period.
     *
     * @param stakedToReward the amount of hbars staked to reward at the start of the ending period
     * @return the effective per-hbar reward rate for the period
     */
    @VisibleForTesting
    long rewardRateForEndingPeriod(final long stakedToReward, @NonNull final ReadableAccountStore accountStore) {
        // The balance left in 0.0.800 (in tinybars), after paying all rewards earned so far
        final var unreservedBalance = rewardsBalance(accountStore) - networkCtx.get().pendingRewards();

        final var thresholdBalance = stakingConfig.stakingRewardBalanceThreshold();
        // A number proportional to the unreserved balance, from 0 for empty, up to 1 at the threshold
        final var balanceRatio = thresholdBalance > 0L
                ? BigDecimal.valueOf(Math.min(unreservedBalance, thresholdBalance))
                        .divide(BigDecimal.valueOf(thresholdBalance), MATH_CONTEXT)
                : BigDecimal.ONE;

        // When 0.0.800 has a high balance, and less than maxStakeRewarded is staked for reward, then
        // effectiveRewardRate == rewardRate. But as the balance drops or the staking increases, then
        // it can be the case that effectiveRewardRate < rewardRate
        return BigDecimal.valueOf(stakingConfig.rewardRate())
                .multiply(balanceRatio.multiply(BigDecimal.valueOf(2).subtract(balanceRatio)))
                .multiply(
                        stakingConfig.stakingMaxStakeRewarded() >= stakedToReward
                                ? BigDecimal.ONE
                                : BigDecimal.valueOf(stakingConfig.stakingMaxStakeRewarded())
                                        .divide(BigDecimal.valueOf(stakedToReward), MATH_CONTEXT))
                .longValue();
    }

    @VisibleForTesting
    com.hedera.hapi.node.base.Timestamp lastInstantOfPreviousPeriodFor(final Instant consensusTime) {
        final var justBeforeMidNightTime = LocalDate.ofInstant(consensusTime, ZoneId.of("UTC"))
                .atStartOfDay()
                .minusNanos(1); // give out the timestamp that is just before midnight
        return Timestamp.newBuilder()
                .seconds(justBeforeMidNightTime.toEpochSecond(ZoneOffset.UTC))
                .nanos(justBeforeMidNightTime.getNano())
                .build();
    }

    private long rewardsBalance(@NonNull final ReadableAccountStore accountStore) {
        return accountStore
                .getAccountById(asAccount(accountNumbers.stakingRewardAccount()))
                .tinybarBalance();
    }
}
