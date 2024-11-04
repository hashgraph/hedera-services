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

package com.hedera.node.app.service.token.impl.handlers.staking;

import static com.hedera.hapi.node.base.HederaFunctionality.NODE_STAKE_UPDATE;
import static com.hedera.hapi.node.base.ResponseCodeEnum.SUCCESS;
import static com.hedera.node.app.service.token.impl.TokenServiceImpl.HBARS_TO_TINYBARS;
import static com.hedera.node.app.service.token.impl.handlers.BaseCryptoHandler.asAccount;
import static com.hedera.node.app.service.token.impl.handlers.staking.EndOfStakingPeriodUtils.asStakingRewardBuilder;
import static com.hedera.node.app.service.token.impl.handlers.staking.EndOfStakingPeriodUtils.computeExtendedRewardSumHistory;
import static com.hedera.node.app.service.token.impl.handlers.staking.EndOfStakingPeriodUtils.computeNewStakes;
import static com.hedera.node.app.service.token.impl.handlers.staking.EndOfStakingPeriodUtils.lastInstantOfPreviousPeriodFor;
import static com.hedera.node.app.service.token.impl.handlers.staking.EndOfStakingPeriodUtils.newNodeStakeUpdateBuilder;
import static com.hedera.node.app.service.token.impl.handlers.staking.EndOfStakingPeriodUtils.readableNonZeroHistory;
import static com.hedera.node.app.spi.workflows.record.StreamBuilder.transactionWith;
import static java.util.Objects.requireNonNull;

import com.google.common.annotations.VisibleForTesting;
import com.hedera.hapi.node.state.token.StakingNodeInfo;
import com.hedera.hapi.node.transaction.ExchangeRateSet;
import com.hedera.hapi.node.transaction.NodeStake;
import com.hedera.node.app.service.token.ReadableAccountStore;
import com.hedera.node.app.service.token.ReadableNetworkStakingRewardsStore;
import com.hedera.node.app.service.token.impl.WritableNetworkStakingRewardsStore;
import com.hedera.node.app.service.token.impl.WritableStakingInfoStore;
import com.hedera.node.app.service.token.records.NodeStakeUpdateStreamBuilder;
import com.hedera.node.app.service.token.records.TokenContext;
import com.hedera.node.app.spi.workflows.record.StreamBuilder;
import com.hedera.node.config.ConfigProvider;
import com.hedera.node.config.data.AccountsConfig;
import com.hedera.node.config.data.StakingConfig;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Updates the stake and reward values for all nodes at the end of a staking period.
 */
@Singleton
public class EndOfStakingPeriodUpdater {
    private static final Logger log = LogManager.getLogger(EndOfStakingPeriodUpdater.class);

    private static final String END_OF_PERIOD_MEMO = "End of staking period calculation record";
    // The exact choice of precision will not have a large effect on the per-hbar reward rate
    private static final MathContext MATH_CONTEXT = new MathContext(8, RoundingMode.DOWN);

    private final AccountsConfig accountsConfig;
    private final StakingRewardsHelper stakeRewardsHelper;

    /**
     * Constructs an {@link EndOfStakingPeriodUpdater} instance.
     *
     * @param stakeRewardsHelper the staking rewards helper
     */
    @Inject
    public EndOfStakingPeriodUpdater(
            @NonNull final StakingRewardsHelper stakeRewardsHelper, @NonNull final ConfigProvider configProvider) {
        this.stakeRewardsHelper = stakeRewardsHelper;
        final var config = configProvider.getConfiguration();
        this.accountsConfig = config.getConfigData(AccountsConfig.class);
    }

    /**
     * Updates all (relevant) staking-related values for all nodes, as well as any network reward information,
     * at the end of a staking period. This method must be invoked during handling of a transaction
     *
     * @param context the context of the transaction used to end the staking period
     * @param exchangeRates the active exchange rate set
     * @param weightUpdates the callback to use to propagate weight changes
     */
    public @Nullable StreamBuilder updateNodes(
            @NonNull final TokenContext context,
            @NonNull final ExchangeRateSet exchangeRates,
            @NonNull final BiConsumer<Long, Integer> weightUpdates) {
        requireNonNull(context);
        requireNonNull(exchangeRates);
        final var consensusTime = context.consensusTime();
        log.info("Updating node stakes for a just-finished period @ {}", consensusTime);

        // First, determine if staking is enabled. If not, there is nothing to do
        final var stakingConfig = context.configuration().getConfigData(StakingConfig.class);
        if (!stakingConfig.isEnabled()) {
            log.info("Staking not enabled, nothing to do");
            return null;
        }

        final var accountStore = context.readableStore(ReadableAccountStore.class);
        final var stakingRewardsStore = context.writableStore(WritableNetworkStakingRewardsStore.class);
        final var stakedToReward = stakingRewardsStore.totalStakeRewardStart();
        // First we compute the maximum reward rate for the just-ending period; i.e., the tinybar earned per
        // hbar for accounts that were staked to a node whose stakedRewardStart for the ending period was in
        // the range [minStake, maxStake]
        final var maxRewardRate = rewardRateGiven(stakedToReward, accountStore, stakingRewardsStore, stakingConfig);
        log.info(
                "The max reward rate for the ending period was {} tb/hbar for nodes with in-range stake, "
                        + "given {} total stake reward start",
                maxRewardRate,
                stakedToReward);

        // Tracks the reward rates for each node for the just-finished period, for later externalization
        final Map<Long, Long> nodeRewardRates = new HashMap<>();
        // Tracks the maximum stake of any node, used to scale the recomputed consensus weights
        long newMaxNodeStake = 0;
        // Accumulates the new total hbar staked to all nodes, whether rewarded or not
        long newTotalStake = 0;
        // Accumulates the new total hbar staked to all nodes that will be rewarded
        long newStakeToReward = 0;
        // Records the new staking node info for each node
        final Map<Long, StakingNodeInfo> newNodeInfos = new LinkedHashMap<>();
        final var stakingInfoStore = context.writableStore(WritableStakingInfoStore.class);
        for (final var nodeId : context.knownNodeIds().stream().sorted().toList()) {
            // The node's staking info at the e end of the period, non-final because
            // we iteratively update its reward sum history,
            var nodeInfo = requireNonNull(stakingInfoStore.getForModify(nodeId));

            // The return value here includes both the new reward sum history, and the reward rate
            // (tinybars-per-hbar) that will be paid to all accounts who had staked to reward for
            // this node long enough to be eligible in the just-finished period
            final var newRewardSumHistory = computeExtendedRewardSumHistory(
                    nodeInfo,
                    nodeInfo.deleted() ? 0 : maxRewardRate,
                    stakingConfig.perHbarRewardRate(),
                    stakingConfig.requireMinStakeToReward());
            final var nodeRewardRate = newRewardSumHistory.pendingRewardRate();
            nodeRewardRates.put(nodeId, nodeRewardRate);
            nodeInfo = nodeInfo.copyBuilder()
                    .rewardSumHistory(newRewardSumHistory.rewardSumHistory())
                    .build();
            log.info(
                    "Non-zero reward sum history for node number {} is now {}",
                    () -> nodeId,
                    () -> readableNonZeroHistory(newRewardSumHistory.rewardSumHistory()));

            // The amount of pending rewards that stakers could collect from this period
            final var pendingRewards = (nodeInfo.stakeRewardStart() - nodeInfo.unclaimedStakeRewardStart())
                    / HBARS_TO_TINYBARS
                    * nodeRewardRate;
            final var newStakes = computeNewStakes(nodeInfo);
            log.info(
                    "For node{}, the tb/hbar reward rate was {} for {} pending, with stake reward start {} -> {}",
                    nodeId,
                    nodeRewardRate,
                    pendingRewards,
                    nodeInfo.stakeRewardStart(),
                    newStakes.stakeRewardStart());

            nodeInfo = nodeInfo.copyBuilder()
                    .stake(newStakes.stake())
                    .stakeRewardStart(newStakes.stakeRewardStart())
                    .unclaimedStakeRewardStart(0)
                    .build();
            nodeInfo = stakeRewardsHelper.increasePendingRewardsBy(stakingRewardsStore, pendingRewards, nodeInfo);

            newStakeToReward += newStakes.stakeRewardStart();
            newTotalStake += nodeInfo.stake();
            // Update the max stake of all nodes
            newMaxNodeStake = Math.max(newMaxNodeStake, nodeInfo.stake());
            // We can't finalize the new node weights until we have the final stake tallies, so just record
            // the in-progress node info for now
            newNodeInfos.put(nodeId, nodeInfo);
        }

        // Accumulates the node stakes for externalization
        final var nodeStakes = new ArrayList<NodeStake>();
        // Configuration determines the total consensus weight to be distributed among nodes
        final int totalWeight = stakingConfig.sumOfConsensusWeights();
        // Final for reference in lambda
        final long maxStake = newMaxNodeStake;
        final long totalStake = newTotalStake;
        newNodeInfos.forEach((nodeId, nodeInfo) -> {
            var newNodeInfo = nodeInfo;
            // If the total stake(rewarded + non-rewarded) of a node is less than minStake, stakingInfo's stake field
            // represents 0, as per calculation done in reviewElectionsAndRecomputeStakes. Similarly, the total
            // stake(rewarded + non-rewarded) of the node is greater than maxStake, stakingInfo's stake field is set to
            // maxStake.So, there is no need to clamp the stake value here. Sum of all stakes can be used to calculate
            // the weight.
            final int newWeight =
                    nodeInfo.deleted() ? 0 : scaleStakeToWeight(nodeInfo.stake(), totalStake, totalWeight);
            log.info("Node{} weight changed from {} to {}", nodeId, nodeInfo.weight(), newWeight);
            newNodeInfo = nodeInfo.copyBuilder().weight(newWeight).build();
            weightUpdates.accept(nodeId, newWeight);

            // We rescale the weight range [0, sumOfConsensusWeights] back to [minStake, maxStake] before
            // externalizing the node stake metadata to stream consumers like mirror nodes
            final var rescaledWeight = rescaleWeight(newWeight, nodeInfo.minStake(), maxStake, totalStake, totalWeight);
            nodeStakes.add(EndOfStakingPeriodUtils.fromStakingInfo(
                    nodeRewardRates.get(nodeId),
                    nodeInfo.copyBuilder().stake(rescaledWeight).build()));
            // Persist the updated staking info
            stakingInfoStore.put(nodeId, newNodeInfo);
        });
        // Update the staking reward values for the network
        stakingRewardsStore.put(asStakingRewardBuilder(stakingRewardsStore)
                .totalStakedRewardStart(newStakeToReward)
                .totalStakedStart(newTotalStake)
                .build());
        final long rewardAccountBalance = getRewardsBalance(accountStore);
        log.info(
                "Total stake start is now {} ({} rewarded), pending rewards are {} vs 0.0.800" + " balance {}",
                newTotalStake,
                newStakeToReward,
                stakingRewardsStore.pendingRewards(),
                rewardAccountBalance);

        // Submit a synthetic node stake update transaction
        final long unreservedStakingRewardBalance = rewardAccountBalance - stakingRewardsStore.pendingRewards();
        final var syntheticNodeStakeUpdateTxn = newNodeStakeUpdateBuilder(
                lastInstantOfPreviousPeriodFor(consensusTime),
                nodeStakes,
                stakingConfig,
                stakedToReward,
                maxRewardRate,
                stakingRewardsStore.pendingRewards(),
                unreservedStakingRewardBalance,
                stakingConfig.rewardBalanceThreshold(),
                stakingConfig.maxStakeRewarded(),
                END_OF_PERIOD_MEMO);
        log.info("Exporting:\n{}", nodeStakes);
        return context.addPrecedingChildRecordBuilder(NodeStakeUpdateStreamBuilder.class, NODE_STAKE_UPDATE)
                .transaction(transactionWith(syntheticNodeStakeUpdateTxn.build()))
                .memo(END_OF_PERIOD_MEMO)
                .exchangeRate(exchangeRates)
                .status(SUCCESS);
    }

    /**
     * Scales up the weight of the node to the range [minStake, maxStakeOfAllNodes]
     * from the consensus weight range [0, sumOfConsensusWeights].
     *
     * @param weight weight of the node
     * @param newMinStake min stake of the node
     * @param newMaxStake real max stake of all nodes computed by taking max(stakeOfNode1, stakeOfNode2, ...)
     * @param totalStakeOfAllNodes total stake of all nodes at the start of new period
     * @param sumOfConsensusWeights sum of consensus weights of all nodes
     * @return scaled weight of the node
     */
    @VisibleForTesting
    public static long rescaleWeight(
            final int weight,
            final long newMinStake,
            final long newMaxStake,
            final long totalStakeOfAllNodes,
            final int sumOfConsensusWeights) {
        // Don't scale a weight of zero
        if (weight == 0) {
            return 0;
        }

        if (totalStakeOfAllNodes == 0) {
            // This should never happen, but if it does, return zero
            log.warn(
                    "Total stake of all nodes is 0, which shouldn't happen "
                            + "(weight={}, minStake={}, maxStake={}, sumOfConsensusWeights={})",
                    weight,
                    newMinStake,
                    newMaxStake,
                    sumOfConsensusWeights);
            return 0;
        }

        final var oldMinWeight = 1L;
        // Since we are calculating weights based on the real stake values, we need to consider the real max Stake and
        // not theoretical max stake of nodes. Otherwise, on a one node network with a stake < maxStake where we assign
        // a weight of sumOfConsensusWeights, the scaled stake value will be greater than its real stake.
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

    /**
     * Scales a single node's hbar stake in tinybars to a consensus weight based on the total stake of all nodes
     * and the desired total weight.
     * <p>
     * The result are normalized weights whose sum will be approximately the given total weight. That is, any node
     * with a non-zero amount of stake will have a weight of at least {@code 1}; any node with a stake of at least one
     * out of every 250 whole hbars staked will have weight at least {@code 2}; and so on.
     * @param nodeStake the stake of a single node, both rewarded and non-rewarded
     * @param totalStake the total stake of all nodes
     * @param totalWeight the desired approximate total weight of all nodes
     * @return the scaled consensus weight for the node
     */
    @VisibleForTesting
    public static int scaleStakeToWeight(final long nodeStake, final long totalStake, final int totalWeight) {
        if (nodeStake == 0) {
            return 0;
        }
        if (totalStake <= 0L) {
            log.error("Scaling {} to zero weight because total stake is {}", nodeStake, totalStake);
            return 0;
        }
        return Math.max(
                1,
                BigInteger.valueOf(nodeStake)
                        .multiply(BigInteger.valueOf(totalWeight))
                        .divide(BigInteger.valueOf(totalStake))
                        .intValueExact());
    }

    /**
     * Given the amount that was staked to reward at the start of the period that is now ending, returns
     * the effective per-hbar reward rate for the period.
     *
     * @param stakedToReward the amount of hbars staked to reward at the start of the ending period
     * @return the effective per-hbar reward rate for the period
     */
    private long rewardRateGiven(
            final long stakedToReward,
            @NonNull final ReadableAccountStore accountStore,
            @NonNull final ReadableNetworkStakingRewardsStore networkRewardsStore,
            @NonNull final StakingConfig stakingConfig) {
        // The balance that will be left in the rewards account after paying all pending rewards
        final var unreservedBalance = getRewardsBalance(accountStore) - networkRewardsStore.pendingRewards();
        // A number proportional to the unreserved balance, from 0 for empty, up to 1 at the threshold
        final var balanceRatio = ratioOf(unreservedBalance, stakingConfig.rewardBalanceThreshold());
        final var rewardRate = rescaledPerHbarRewardRate(
                balanceRatio, stakedToReward, stakingConfig.perHbarRewardRate(), stakingConfig.maxStakeRewarded());
        return stakedToReward < HBARS_TO_TINYBARS ? 0 : rewardRate;
    }

    /**
     * Given the {@code 0.0.800} unreserved balance and its threshold setting, returns the ratio of the balance to the
     * threshold, from 0 for empty, up to 1 at the threshold.
     *
     * @param unreservedBalance the balance in {@code 0.0.800} minus the pending rewards
     * @param thresholdBalance the threshold balance setting
     * @return the ratio of the balance to the threshold, from 0 for empty, up to 1 at the threshold
     */
    private BigDecimal ratioOf(final long unreservedBalance, final long thresholdBalance) {
        return thresholdBalance > 0L
                ? BigDecimal.valueOf(Math.min(unreservedBalance, thresholdBalance))
                        .divide(BigDecimal.valueOf(thresholdBalance), MATH_CONTEXT)
                : BigDecimal.ONE;
    }

    /**
     * Given the {@code 0.0.800} balance ratio relative to the threshold, the amount that was staked to reward at the
     * start of the period that is now ending, and the maximum amount of tinybars to pay as staking rewards in the
     * period, returns the effective per-hbar reward rate for the period.
     *
     * @param balanceRatio the ratio of the {@code 0.0.800} balance to the threshold
     * @param stakedToReward the amount of hbars staked to reward at the start of the ending period
     * @param maxRewardRate the maximum amount of tinybars to pay per hbar reward
     * @param maxStakeRewarded the maximum amount of stake that can be rewarded
     * @return the effective per-hbar reward rate for the period
     */
    @VisibleForTesting
    long rescaledPerHbarRewardRate(
            @NonNull final BigDecimal balanceRatio,
            final long stakedToReward,
            final long maxRewardRate,
            final long maxStakeRewarded) {
        // When 0.0.800 has a high balance, and less than maxStakeRewarded is staked for reward, then
        // effectiveRewardRate == rewardRate. But as the balance drops or the staking increases, then
        // it can be the case that effectiveRewardRate < rewardRate
        return BigDecimal.valueOf(maxRewardRate)
                .multiply(balanceRatio.multiply(BigDecimal.valueOf(2).subtract(balanceRatio)))
                .multiply(
                        maxStakeRewarded >= stakedToReward
                                ? BigDecimal.ONE
                                : BigDecimal.valueOf(maxStakeRewarded)
                                        .divide(BigDecimal.valueOf(stakedToReward), MATH_CONTEXT))
                .longValue();
    }

    private long getRewardsBalance(@NonNull final ReadableAccountStore accountStore) {
        return requireNonNull(accountStore.getAccountById(asAccount(accountsConfig.stakingRewardAccount())))
                .tinybarBalance();
    }
}
