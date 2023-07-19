/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

import static com.hedera.node.app.service.token.impl.handlers.staking.StakingUtils.roundedToHbar;
import static com.hedera.node.app.service.token.impl.handlers.staking.StakingUtils.totalStake;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.state.token.Account;
import com.hedera.node.app.service.token.impl.WritableStakingInfoStore;
import edu.umd.cs.findbugs.annotations.NonNull;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class StakeInfoHelper {
    private static final Logger log = LogManager.getLogger(StakeInfoHelper.class);

    public StakeInfoHelper() {}

    /**
     * Increases the unclaimed stake reward start for the given node by the given amount
     *
     * @param nodeId the node's numeric ID
     * @param amount the amount to increase the unclaimed stake reward start by
     */
    public void increaseUnclaimedStakeRewards(
            @NonNull final Long nodeId, final long amount, @NonNull final WritableStakingInfoStore stakingInfoStore) {
        requireNonNull(nodeId);
        requireNonNull(stakingInfoStore);

        final var currentStakingInfo = stakingInfoStore.get(nodeId);
        final var currentStakeRewardStart = currentStakingInfo.stakeRewardStart();
        final var newUnclaimedStakeRewardStart = currentStakingInfo.unclaimedStakeRewardStart() + amount;

        final var newStakingInfo =
                currentStakingInfo.copyBuilder().unclaimedStakeRewardStart(newUnclaimedStakeRewardStart);
        if (newUnclaimedStakeRewardStart > currentStakeRewardStart) {
            log.warn(
                    "Asked to release {} more rewards for node{} (now {}), but only {} was staked",
                    amount,
                    nodeId,
                    newUnclaimedStakeRewardStart,
                    currentStakeRewardStart);
            newStakingInfo.unclaimedStakeRewardStart(currentStakeRewardStart);
        }

        stakingInfoStore.put(nodeId, newStakingInfo.build());
    }

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
        final var copy = stakingInfo.copyBuilder();
        if (isDeclineReward) {
            copy.stakeToNotReward(stakingInfo.stakeToNotReward() + stakeToAward);
        } else {
            copy.stakeToReward(stakingInfo.stakeToReward() + stakeToAward);
        }
        stakingInfoStore.put(nodeId, copy.build());
    }

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
            copy.stakeToNotReward(stakingInfo.stakeToNotReward() - stakeToWithdraw);
        } else {
            copy.stakeToReward(stakingInfo.stakeToReward() - stakeToWithdraw);
        }
        stakingInfoStore.put(nodeId, copy.build());
    }
}
