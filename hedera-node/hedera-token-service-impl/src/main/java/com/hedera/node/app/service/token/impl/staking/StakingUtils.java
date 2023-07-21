package com.hedera.node.app.service.token.impl.staking;

import com.hedera.hapi.node.state.token.StakingNodeInfo;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import edu.umd.cs.findbugs.annotations.NonNull;

public final class StakingUtils {
    private StakingUtils() {
        throw new UnsupportedOperationException("Utility Class");
    }

    /**
     * todo
     *
     * @param rewardSumHistory
     * @return
     */
    public static String readableNonZeroHistory(@NonNull final List<Long> rewardSumHistory) {
        int firstZero = -1;
        for (int i = 0; i < rewardSumHistory.size(); i++) {
            if (rewardSumHistory.get(i) == 0) {
                firstZero = i;
                break;
            }
        }

        return (firstZero == -1) ? rewardSumHistory.toString() : IntStream.range(0, firstZero).mapToObj(rewardSumHistory::get).toList().toString();
    }

    public record UpdatedRewardSumHistory(List<Long> rewardSumHistory, long pendingRewardRate) {}

    /**
     * todo
     *
     * @param currentInfo
     * @param perHbarRate
     * @param maxPerHbarRate
     * @param requireMinStakeToReward
     * @return
     */
    public static UpdatedRewardSumHistory calculateUpdatedRewardSumHistory(
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
        // If this node was "active"---i.e., node.numRoundsWithJudge / numRoundsInPeriod >= activeThreshold---and it had non-zero stakedReward at the start of the ending staking period, then it should give rewards for this staking period, unless its effective stake was less than minStake, and hence zero here (note the active condition will only be checked in a later release)
        final var rewardableStake = requireMinStakeToReward ? Math.min(currentInfo.stakeRewardStart(), currentInfo.stake()) : currentInfo.stakeRewardStart();
        if (rewardableStake > 0) {
            perHbarRateThisNode = perHbarRate;
            // But if the node had more the maximum stakeRewardStart, "down-scale" its reward rate to ensure the accounts staking to this node will receive a fraction of the total rewards that does not exceed node.stakedRewardStart / totalStakedRewardedStart; use arbitrary-precision arithmetic because there is no inherent bound on (maxStake * pendingRewardRate)
            if (currentInfo.stakeRewardStart() > currentInfo.maxStake()) {
                perHbarRateThisNode = BigInteger.valueOf(perHbarRateThisNode)
                        .multiply(BigInteger.valueOf(currentInfo.maxStake()))
                        .divide(BigInteger.valueOf(currentInfo.stakeRewardStart()))
                        .longValueExact();
            }
        }
        perHbarRateThisNode = Math.min(perHbarRateThisNode, maxPerHbarRate);
        newRewardSumHistory.set(0, newRewardSumHistory.get(0) + perHbarRateThisNode);

        return new UpdatedRewardSumHistory(newRewardSumHistory, perHbarRateThisNode);
    }

    public record StakeResult(long stake, long stakeRewardStart) {}

    /**
     * todo
     *
     * @param stakingInfo
     * @return
     */
    @NonNull
    public static StakeResult computeStake(@NonNull final StakingNodeInfo stakingInfo) {
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
