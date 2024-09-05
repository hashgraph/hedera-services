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

import com.hedera.hapi.node.state.token.StakingNodeInfo;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

/**
 * This class includes utility methods for dealing with staking period calculations (the updates to
 * staking performed at the end of each staking period). That said, <b>these methods should NOT
 * update any state</b> (e.g. by passing in a writable store)
 */
public final class EndOfStakingPeriodUtils {
    private EndOfStakingPeriodUtils() {
        throw new UnsupportedOperationException("Utility Class");
    }

    /**
     * Creates a human-readable string summary of the given reward sums history, omitting any trailing zeros.
     *
     * @param rewardSumHistory the rewards sum history to summarize
     * @return the summary
     */
    public static String readableNonZeroHistory(@NonNull final List<Long> rewardSumHistory) {
        int firstZero = -1;
        for (int i = 0; i < rewardSumHistory.size(); i++) {
            if (rewardSumHistory.get(i) == 0) {
                firstZero = i;
                break;
            }
        }

        return (firstZero == -1)
                ? rewardSumHistory.toString()
                : IntStream.range(0, firstZero)
                        .mapToObj(rewardSumHistory::get)
                        .toList()
                        .toString();
    }

    /**
     * Stores both the new reward sum history and the new per-hbar reward rate for a node.
     */
    public record RewardSumHistory(List<Long> rewardSumHistory, long pendingRewardRate) {}

    /**
     * Calculates a reward sum history for a node based on the node's past reward sums.
     * <p>
     * <b>NOTE: this method does not alter any state!</b> It merely performs the calculation and returns the result
     *
     * @param currentInfo the node's current staking info
     * @param perHbarRate the current per-hbar reward rate for this node
     * @param maxPerHbarRate the maximum per-hbar reward rate for this node
     * @param requireMinStakeToReward if true, will require the node's stake to meet a threshold
     *                                in order to receive rewards
     * @return the calculated {@link RewardSumHistory}
     */
    public static RewardSumHistory calculateRewardSumHistory(
            @NonNull final StakingNodeInfo currentInfo,
            final long perHbarRate,
            final long maxPerHbarRate,
            final boolean requireMinStakeToReward) {
        final var currRewardSumHistory = currentInfo.rewardSumHistory();
        final var newRewardSumHistory = new ArrayList<>(currRewardSumHistory);
        final var droppedRewardSum = currRewardSumHistory.get(currRewardSumHistory.size() - 1);
        for (int i = currRewardSumHistory.size() - 1; i > 0; i--) {
            newRewardSumHistory.set(i, currRewardSumHistory.get(i - 1) - droppedRewardSum);
        }
        newRewardSumHistory.set(0, currRewardSumHistory.get(0) - droppedRewardSum);

        long perHbarRateThisNode = 0;
        // If this node was "active"---i.e., node.numRoundsWithJudge / numRoundsInPeriod >= activeThreshold---and it had
        // non-zero stakedReward at the start of the ending staking period, then it should give rewards for this staking
        // period, unless its effective stake was less than minStake, and hence zero here (note the active condition
        // will only be checked in a later release)
        final var rewardableStake = requireMinStakeToReward
                ? Math.min(currentInfo.stakeRewardStart(), currentInfo.stake())
                : currentInfo.stakeRewardStart();
        if (rewardableStake > 0) {
            perHbarRateThisNode = perHbarRate;
            // But if the node had more the maximum stakeRewardStart, "down-scale" its reward rate to ensure the
            // accounts staking to this node will receive a fraction of the total rewards that does not exceed
            // node.stakedRewardStart / totalStakedRewardedStart; use arbitrary-precision arithmetic because there is no
            // inherent bound on (maxStake * pendingRewardRate)
            if (currentInfo.stakeRewardStart() > currentInfo.maxStake()) {
                perHbarRateThisNode = BigInteger.valueOf(perHbarRateThisNode)
                        .multiply(BigInteger.valueOf(currentInfo.maxStake()))
                        .divide(BigInteger.valueOf(currentInfo.stakeRewardStart()))
                        .longValueExact();
            }
        }
        perHbarRateThisNode = Math.min(perHbarRateThisNode, maxPerHbarRate);
        newRewardSumHistory.set(0, newRewardSumHistory.get(0) + perHbarRateThisNode);

        return new RewardSumHistory(newRewardSumHistory, perHbarRateThisNode);
    }

    /**
     * Stores both the new stake and the new stakeRewardStart for a node.
     */
    public record StakeResult(long stake, long stakeRewardStart) {}

    /**
     * Computes a clamped stake value between [minStake, maxStake] for a node based on the node's
     * current staking info. The new {@code stakeRewardStart} value is also computed
     *
     * @param stakingInfo the node's current staking info
     * @return the calculated {@link StakeResult}
     */
    @NonNull
    public static StakeResult computeNextStake(@NonNull final StakingNodeInfo stakingInfo) {
        final var totalStake = stakingInfo.stakeToReward() + stakingInfo.stakeToNotReward();
        final long newStake;
        if (totalStake > stakingInfo.maxStake()) {
            newStake = stakingInfo.maxStake();
        } else if (totalStake < stakingInfo.minStake()) {
            newStake = 0;
        } else {
            newStake = totalStake;
        }
        return new StakeResult(newStake, stakingInfo.stakeToReward());
    }
}
