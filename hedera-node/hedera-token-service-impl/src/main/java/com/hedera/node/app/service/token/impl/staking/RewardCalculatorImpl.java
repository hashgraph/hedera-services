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

import static com.hedera.node.app.service.token.Units.HBARS_TO_TINYBARS;

import com.google.common.annotations.VisibleForTesting;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.hapi.node.state.token.StakingNodeInfo;
import com.hedera.node.app.service.mono.ledger.accounts.staking.StakePeriodManager;
import com.hedera.node.app.service.token.ReadableStakingInfoStore;
import com.hedera.node.app.service.token.impl.utils.RewardCalculator;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class RewardCalculatorImpl implements RewardCalculator {
    private final StakePeriodManager stakePeriodManager;

    @Inject
    public RewardCalculatorImpl(@NonNull final StakePeriodManager stakePeriodManager) {
        this.stakePeriodManager = stakePeriodManager;
    }

    public long computePendingReward(
            @NonNull final Account account, @NonNull final ReadableStakingInfoStore stakingInfoStore) {
        final var effectiveStart = stakePeriodManager.effectivePeriod(account.stakePeriodStart());
        if (!stakePeriodManager.isRewardable(effectiveStart)) {
            return 0;
        }

        final var addressBookId = calculateNodeAddressId(account.stakedNodeId());
        final var stakingInfo = stakingInfoStore.get(addressBookId);
        final var rewardOffered =
                computeRewardFromDetails(account, stakingInfo, stakePeriodManager.currentStakePeriod(), effectiveStart);
        return account.declineReward() ? 0 : rewardOffered;
    }

    public long estimatePendingRewards(
            @NonNull final Account account, @Nullable final StakingNodeInfo nodeStakingInfo) {
        final var effectiveStart = stakePeriodManager.effectivePeriod(account.stakePeriodStart());
        if (!stakePeriodManager.isEstimatedRewardable(effectiveStart)) {
            return 0;
        }
        final var rewardOffered = computeRewardFromDetails(
                account, nodeStakingInfo, stakePeriodManager.estimatedCurrentStakePeriod(), effectiveStart);
        return account.declineReward() ? 0 : rewardOffered;
    }

    public long epochSecondAtStartOfPeriod(final long stakePeriod) {
        return stakePeriodManager.epochSecondAtStartOfPeriod(stakePeriod);
    }

    @VisibleForTesting
    public long computeRewardFromDetails(
            @NonNull final Account account,
            @Nullable final StakingNodeInfo nodeStakingInfo,
            final long currentStakePeriod,
            final long effectiveStart) {
        if (nodeStakingInfo == null) {
            return 0L;
        }
        final var rewardSumHistory = nodeStakingInfo.rewardSumHistory();
        return rewardFor(account, rewardSumHistory, currentStakePeriod, effectiveStart);
    }

    private long rewardFor(
            @NonNull final Account account,
            @NonNull final List<Long> rewardSumHistory,
            final long currentStakePeriod,
            final long effectiveStart) {
        final var rewardFrom = (int) (currentStakePeriod - 1 - effectiveStart);
        if (rewardFrom == 0) {
            return 0;
        }

        final var firstRewardSum = rewardSumHistory.get(0);
        final var rewardFromSum = rewardSumHistory.get(rewardFrom);
        if (account.stakeAtStartOfLastRewardedPeriod() != -1) {
            final var rewardFromMinus1Sum = rewardSumHistory.get(rewardFrom - 1);

            // Two-step computation; first, the reward from the last period the account changed its
            // stake in...
            return account.stakeAtStartOfLastRewardedPeriod()
                            / HBARS_TO_TINYBARS
                            * (rewardFromMinus1Sum - rewardFromSum)
                    // ...and second, the reward for all following periods
                    + calculateTotalStake(account) / HBARS_TO_TINYBARS * (firstRewardSum - rewardFromMinus1Sum);
        } else {
            return calculateTotalStake(account) / HBARS_TO_TINYBARS * (firstRewardSum - rewardFromSum);
        }
    }

    private static long calculateNodeAddressId(long stakedNodeId) {
        return -stakedNodeId - 1L;
    }

    private static long calculateTotalStake(@NonNull final Account account) {
        return account.stakedToMe() + account.tinybarBalance();
    }
}
