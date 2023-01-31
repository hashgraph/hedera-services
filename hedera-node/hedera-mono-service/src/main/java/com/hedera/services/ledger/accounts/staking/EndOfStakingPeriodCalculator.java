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
package com.hedera.services.ledger.accounts.staking;

import static com.hedera.services.context.properties.PropertyNames.ACCOUNTS_STAKING_REWARD_ACCOUNT;
import static com.hedera.services.context.properties.PropertyNames.STAKING_REWARD_RATE;
import static com.hedera.services.records.TxnAwareRecordsHistorian.DEFAULT_SOURCE_ID;
import static com.hedera.services.state.EntityCreator.NO_CUSTOM_FEES;
import static com.hedera.services.utils.Units.HBARS_TO_TINYBARS;

import com.google.common.annotations.VisibleForTesting;
import com.hedera.services.context.SideEffectsTracker;
import com.hedera.services.context.annotations.CompositeProps;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.context.properties.PropertySource;
import com.hedera.services.records.RecordsHistorian;
import com.hedera.services.state.EntityCreator;
import com.hedera.services.state.merkle.MerkleNetworkContext;
import com.hedera.services.state.merkle.MerkleStakingInfo;
import com.hedera.services.state.migration.AccountStorageAdapter;
import com.hedera.services.store.contracts.precompile.SyntheticTxnFactory;
import com.hedera.services.utils.EntityNum;
import com.hederahashgraph.api.proto.java.NodeStake;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.swirlds.merkle.map.MerkleMap;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Singleton
public class EndOfStakingPeriodCalculator {

    private static final Logger log = LogManager.getLogger(EndOfStakingPeriodCalculator.class);
    public static final String END_OF_STAKING_PERIOD_CALCULATIONS_MEMO =
            "End of staking period calculation record";
    private static final SideEffectsTracker NO_OTHER_SIDE_EFFECTS = new SideEffectsTracker();

    private final Supplier<AccountStorageAdapter> accounts;
    private final Supplier<MerkleMap<EntityNum, MerkleStakingInfo>> stakingInfos;
    private final Supplier<MerkleNetworkContext> networkCtx;
    private final SyntheticTxnFactory syntheticTxnFactory;
    private final RecordsHistorian recordsHistorian;
    private final EntityCreator creator;
    private final PropertySource properties;
    private final GlobalDynamicProperties dynamicProperties;

    @Inject
    public EndOfStakingPeriodCalculator(
            final Supplier<AccountStorageAdapter> accounts,
            final Supplier<MerkleMap<EntityNum, MerkleStakingInfo>> stakingInfos,
            final Supplier<MerkleNetworkContext> networkCtx,
            final SyntheticTxnFactory syntheticTxnFactory,
            final RecordsHistorian recordsHistorian,
            final EntityCreator creator,
            final @CompositeProps PropertySource properties,
            final GlobalDynamicProperties dynamicProperties) {
        this.accounts = accounts;
        this.stakingInfos = stakingInfos;
        this.networkCtx = networkCtx;
        this.syntheticTxnFactory = syntheticTxnFactory;
        this.recordsHistorian = recordsHistorian;
        this.creator = creator;
        this.properties = properties;
        this.dynamicProperties = dynamicProperties;
    }

    public void updateNodes(final Instant consensusTime) {
        log.info("Updating node stakes for a just-finished period @ {}", consensusTime);

        if (!dynamicProperties.isStakingEnabled()) {
            log.info("Staking not enabled, nothing to do");
            return;
        }

        final var curNetworkCtx = networkCtx.get();
        final var curStakingInfos = stakingInfos.get();
        final var rewardRate = rewardRateForEndingPeriod();
        final var totalStakedRewardStart = curNetworkCtx.getTotalStakedRewardStart();
        // The tinybars earned per hbar for stakers who were staked to a node whose total
        // stakedRewardStart for the ending period was in the range [minStake, maxStake]
        final var perHbarRate =
                totalStakedRewardStart < HBARS_TO_TINYBARS
                        ? 0
                        : rewardRate / (totalStakedRewardStart / HBARS_TO_TINYBARS);
        log.info(
                "The reward rate for the period was {} tb ({} tb/hbar for nodes with in-range"
                        + " stake, given {} total stake reward start)",
                rewardRate,
                perHbarRate,
                totalStakedRewardStart);

        long newTotalStakedStart = 0L;
        long newTotalStakedRewardStart = 0L;
        final List<NodeStake> nodeStakingInfos = new ArrayList<>();
        for (final var nodeNum : curStakingInfos.keySet().stream().sorted().toList()) {
            final var stakingInfo = curStakingInfos.getForModify(nodeNum);

            // The return value is the reward rate (tinybars-per-hbar-staked-to-reward) that will be
            // paid to all
            // accounts who had staked-to-reward for this node long enough to be eligible in the
            // just-finished period
            final var nodeRewardRate =
                    stakingInfo.updateRewardSumHistory(
                            perHbarRate,
                            dynamicProperties.maxDailyStakeRewardThPerH(),
                            dynamicProperties.requireMinStakeToReward());

            final var oldStakeRewardStart = stakingInfo.getStakeRewardStart();
            final var pendingRewardHbars =
                    stakingInfo.stakeRewardStartMinusUnclaimed() / HBARS_TO_TINYBARS;
            final var newStakeRewardStart = stakingInfo.reviewElectionsAndRecomputeStakes();
            final var nodePendingRewards = pendingRewardHbars * nodeRewardRate;
            log.info(
                    "For node{}, the tb/hbar reward rate was {} for {} pending, "
                            + "with stake reward start {} -> {}",
                    nodeNum.longValue(),
                    nodeRewardRate,
                    nodePendingRewards,
                    oldStakeRewardStart,
                    newStakeRewardStart);
            curNetworkCtx.increasePendingRewards(nodePendingRewards);
            stakingInfo.resetUnclaimedStakeRewardStart();

            newTotalStakedRewardStart += newStakeRewardStart;
            newTotalStakedStart += stakingInfo.getStake();
            nodeStakingInfos.add(
                    NodeStake.newBuilder()
                            .setNodeId(nodeNum.longValue())
                            .setRewardRate(nodeRewardRate)
                            .setStake(stakingInfo.getStake())
                            .setMinStake(stakingInfo.getMinStake())
                            .setMaxStake(stakingInfo.getMaxStake())
                            .setStakeRewarded(stakingInfo.getStakeToReward())
                            .setStakeNotRewarded(stakingInfo.getStakeToNotReward())
                            .build());
        }
        curNetworkCtx.setTotalStakedRewardStart(newTotalStakedRewardStart);
        curNetworkCtx.setTotalStakedStart(newTotalStakedStart);
        log.info(
                "Total stake start is now {} ({} rewarded), pending rewards are {} vs 0.0.800"
                        + " balance {}",
                newTotalStakedStart,
                newTotalStakedRewardStart,
                curNetworkCtx.pendingRewards(),
                rewardsBalance());

        final var syntheticNodeStakeUpdateTxn =
                syntheticTxnFactory.nodeStakeUpdate(
                        lastInstantOfPreviousPeriodFor(consensusTime),
                        nodeStakingInfos,
                        properties);
        log.info("Exporting:\n{}", nodeStakingInfos);
        recordsHistorian.trackPrecedingChildRecord(
                DEFAULT_SOURCE_ID,
                syntheticNodeStakeUpdateTxn,
                creator.createSuccessfulSyntheticRecord(
                        NO_CUSTOM_FEES,
                        NO_OTHER_SIDE_EFFECTS,
                        END_OF_STAKING_PERIOD_CALCULATIONS_MEMO));
    }

    @VisibleForTesting
    long rewardRateForEndingPeriod() {
        return Math.max(
                0,
                Math.min(
                        rewardsBalance() - networkCtx.get().pendingRewards(),
                        properties.getLongProperty(STAKING_REWARD_RATE)));
    }

    @VisibleForTesting
    Timestamp lastInstantOfPreviousPeriodFor(final Instant consensusTime) {
        final var justBeforeMidNightTime =
                LocalDate.ofInstant(consensusTime, ZoneId.of("UTC"))
                        .atStartOfDay()
                        .minusNanos(1); // give out the timestamp that is just before midnight
        return Timestamp.newBuilder()
                .setSeconds(justBeforeMidNightTime.toEpochSecond(ZoneOffset.UTC))
                .setNanos(justBeforeMidNightTime.getNano())
                .build();
    }

    private long rewardsBalance() {
        return accounts.get()
                .get(
                        EntityNum.fromLong(
                                properties.getLongProperty(ACCOUNTS_STAKING_REWARD_ACCOUNT)))
                .getBalance();
    }
}
