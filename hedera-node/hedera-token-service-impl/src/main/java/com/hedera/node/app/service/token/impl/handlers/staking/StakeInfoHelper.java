/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

import static com.hedera.node.app.service.token.impl.handlers.staking.StakingUtilities.roundedToHbar;
import static com.hedera.node.app.service.token.impl.handlers.staking.StakingUtilities.totalStake;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.state.token.Account;
import com.hedera.node.app.service.token.impl.WritableStakingInfoStore;
import edu.umd.cs.findbugs.annotations.NonNull;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Helper class for mutating staking info in the {@link WritableStakingInfoStore}.
 */
@Singleton
public class StakeInfoHelper {
    private static final Logger log = LogManager.getLogger(StakeInfoHelper.class);

    /**
     * Default constructor for injection.
     */
    @Inject
    public StakeInfoHelper() {
        // Needed for Dagger injection
    }

    /**
     * Increases the unclaimed stake reward start for the given node by the given amount.
     *
     * @param nodeId the node's numeric ID
     * @param amount the amount to increase the unclaimed stake reward start by
     * @param stakingInfoStore the store for the staking info
     */
    public void increaseUnclaimedStakeRewards(
            @NonNull final Long nodeId, final long amount, @NonNull final WritableStakingInfoStore stakingInfoStore) {
        requireNonNull(nodeId);
        requireNonNull(stakingInfoStore);

        final var currentStakingInfo = stakingInfoStore.getForModify(nodeId);
        final var currentStakeRewardStart = currentStakingInfo.stakeRewardStart();
        final var newUnclaimedStakeRewardStart = currentStakingInfo.unclaimedStakeRewardStart() + amount;

        final var newStakingInfo = currentStakingInfo.copyBuilder();
        if (newUnclaimedStakeRewardStart > currentStakeRewardStart) {
            log.warn(
                    "Asked to release {} more rewards for node{} (now {}), but only {} was staked",
                    amount,
                    nodeId,
                    newUnclaimedStakeRewardStart,
                    currentStakeRewardStart);
            newStakingInfo.unclaimedStakeRewardStart(currentStakeRewardStart);
        } else {
            newStakingInfo.unclaimedStakeRewardStart(newUnclaimedStakeRewardStart);
        }

        stakingInfoStore.put(nodeId, newStakingInfo.build());
    }

    /**
     * Awards the stake to the node's stakeToReward or stakeToNotReward depending on the account's decline reward.
     * If declineReward is true, the stake is awarded to stakeToNotReward, otherwise it is awarded to stakeToReward.
     * @param nodeId the node's numeric ID
     * @param account the account stake to be awarded to the node
     * @param stakingInfoStore the store for the staking info
     */
    public void awardStake(
            @NonNull final Long nodeId,
            @NonNull final Account account,
            @NonNull final WritableStakingInfoStore stakingInfoStore) {
        requireNonNull(nodeId);
        requireNonNull(account);
        requireNonNull(stakingInfoStore);

        final var stakeToAward = roundedToHbar(totalStake(account));
        final var isDeclineReward = account.declineReward();

        final var stakingInfo = stakingInfoStore.get(nodeId);
        if (stakingInfo != null) {
            final var copy = stakingInfo.copyBuilder();
            if (isDeclineReward) {
                final var stakedToNotReward = stakingInfo.stakeToNotReward() + stakeToAward;
                copy.stakeToNotReward(stakedToNotReward);
            } else {
                final var stakedToReward = stakingInfo.stakeToReward() + stakeToAward;
                copy.stakeToReward(stakedToReward);
            }
            stakingInfoStore.put(nodeId, copy.build());
        } else {
            log.error("Staking info is null for node {}", nodeId);
        }
    }

    /**
     * Withdraws the stake from the node's stakeToReward or stakeToNotReward depending on the account's decline reward.
     * If declineReward is true, the stake is withdrawn from stakeToNotReward, otherwise it is withdrawn from
     * stakeToReward.
     * @param nodeId the node's numeric ID
     * @param account the account 's stake to be withdrawn from node
     * @param stakingInfoStore the store for the staking info
     */
    public void withdrawStake(
            @NonNull final Long nodeId,
            @NonNull final Account account,
            @NonNull final WritableStakingInfoStore stakingInfoStore) {
        requireNonNull(nodeId);
        requireNonNull(account);
        requireNonNull(stakingInfoStore);

        final var stakeToWithdraw = roundedToHbar(totalStake(account));
        final var isDeclineReward = account.declineReward();

        final var stakingInfo = stakingInfoStore.get(nodeId);
        final var copy = stakingInfo.copyBuilder();
        if (isDeclineReward) {
            final var stakedToNotReward = stakingInfo.stakeToNotReward() - stakeToWithdraw;
            if (stakedToNotReward < 0) {
                log.warn(
                        "Asked to withdraw {} more stake for node{} (now {}), but only {} was staked",
                        stakeToWithdraw,
                        nodeId,
                        stakedToNotReward,
                        stakingInfo.stakeToNotReward());
            }
            copy.stakeToNotReward(Math.max(0, stakedToNotReward));
        } else {
            final var stakeToReward = stakingInfo.stakeToReward() - stakeToWithdraw;
            if (stakeToReward < 0) {
                log.warn(
                        "Asked to withdraw {} more stake for node{} (now {}), but only {} was staked",
                        stakeToWithdraw,
                        nodeId,
                        stakeToReward,
                        stakingInfo.stakeToReward());
            }
            copy.stakeToReward(Math.max(0, stakeToReward));
        }
        stakingInfoStore.put(nodeId, copy.build());
    }
}
