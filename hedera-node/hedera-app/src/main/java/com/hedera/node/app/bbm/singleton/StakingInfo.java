/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.bbm.singleton;

import com.hedera.hapi.node.state.token.StakingNodeInfo;
import com.hedera.node.app.service.mono.state.merkle.MerkleStakingInfo;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Objects;

public record StakingInfo(
        int number,
        long minStake,
        long maxStake,
        long stakeToReward,
        long stakeToNotReward,
        long stakeRewardStart,
        long unclaimedStakeRewardStart,
        long stake,
        @NonNull long[] rewardSumHistory,
        int weight) {
    public static StakingInfo fromMono(@NonNull final MerkleStakingInfo stakingInfo) {
        Objects.requireNonNull(stakingInfo.getRewardSumHistory(), "rewardSumHistory");
        return new StakingInfo(
                stakingInfo.getKey().intValue(),
                stakingInfo.getMinStake(),
                stakingInfo.getMaxStake(),
                stakingInfo.getStakeToReward(),
                stakingInfo.getStakeToNotReward(),
                stakingInfo.getStakeRewardStart(),
                stakingInfo.getUnclaimedStakeRewardStart(),
                stakingInfo.getStake(),
                stakingInfo.getRewardSumHistory(),
                stakingInfo.getWeight());
    }

    public static StakingInfo fromMod(@NonNull final StakingNodeInfo stakingInfo) {
        Objects.requireNonNull(stakingInfo.rewardSumHistory(), "rewardSumHistory");
        return new StakingInfo(
                Long.valueOf(stakingInfo.nodeNumber()).intValue(),
                stakingInfo.minStake(),
                stakingInfo.maxStake(),
                stakingInfo.stakeToReward(),
                stakingInfo.stakeToNotReward(),
                stakingInfo.stakeRewardStart(),
                stakingInfo.unclaimedStakeRewardStart(),
                stakingInfo.stake(),
                stakingInfo.rewardSumHistory().stream()
                        .mapToLong(Long::longValue)
                        .toArray(),
                stakingInfo.weight());
    }

    static final byte[] EMPTY_BYTES = new byte[0];
}
