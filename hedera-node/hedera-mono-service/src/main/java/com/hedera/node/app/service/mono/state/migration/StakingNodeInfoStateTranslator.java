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

package com.hedera.node.app.service.mono.state.migration;

import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.state.token.StakingNodeInfo;
import com.hedera.node.app.service.mono.utils.EntityNum;
import com.hedera.node.app.service.token.ReadableStakingInfoStore;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Arrays;

/**
 * Provides static methods for translating between {@link StakingNodeInfo} and {@link  com.hedera.node.app.service.mono.state.merkle.MerkleStakingInfo} both ways.
 */
public final class StakingNodeInfoStateTranslator {

    /**
     * Translates a {@link StakingNodeInfo} to a {@link  com.hedera.node.app.service.mono.state.merkle.MerkleStakingInfo}.
     * @param merkleStakingInfo the {@link com.hedera.node.app.service.mono.state.merkle.MerkleStakingInfo} to translate
     * @return the translated {@link StakingNodeInfo}
     */
    @NonNull
    public static StakingNodeInfo stakingInfoFromMerkleStakingInfo(
            @NonNull final com.hedera.node.app.service.mono.state.merkle.MerkleStakingInfo merkleStakingInfo) {
        requireNonNull(merkleStakingInfo);
        return StakingNodeInfo.newBuilder()
                .nodeNumber(merkleStakingInfo.getKey().longValue())
                .minStake(merkleStakingInfo.getMinStake())
                .maxStake(merkleStakingInfo.getMaxStake())
                .stakeToReward(merkleStakingInfo.getStakeToReward())
                .stakeToNotReward(merkleStakingInfo.getStakeToNotReward())
                .stakeRewardStart(merkleStakingInfo.getStakeRewardStart())
                .unclaimedStakeRewardStart(merkleStakingInfo.getUnclaimedStakeRewardStart())
                .stake(merkleStakingInfo.getStake())
                .rewardSumHistory(Arrays.stream(merkleStakingInfo.getRewardSumHistory())
                        .boxed()
                        .toList())
                .weight(merkleStakingInfo.getWeight())
                .build();
    }

    /**
     * Translates a {@link StakingNodeInfo} to a {@link  com.hedera.node.app.service.mono.state.merkle.MerkleStakingInfo}.
     * @param nodeId the {@link AccountID} to get the staking info for
     * @param readableStakingInfoStore the {@link ReadableStakingInfoStore} to get the staking info from
     * @return the translated {@link com.hedera.node.app.service.mono.state.merkle.MerkleStakingInfo}
     */
    @NonNull
    public static com.hedera.node.app.service.mono.state.merkle.MerkleStakingInfo merkleStakingInfoFromStakingNodeInfo(
            @NonNull final Long nodeId, @NonNull final ReadableStakingInfoStore readableStakingInfoStore) {
        requireNonNull(readableStakingInfoStore);
        requireNonNull(nodeId);
        final var optionalStakingInfo = readableStakingInfoStore.get(nodeId);
        if (optionalStakingInfo == null) {
            throw new IllegalArgumentException("Staking Info not found");
        }
        return merkleStakingInfoFromStakingNodeInfo(optionalStakingInfo);
    }

    /**
     * Translates a {@link StakingNodeInfo} to a {@link  com.hedera.node.app.service.mono.state.merkle.MerkleStakingInfo}.
     * @param stakingNodeInfo the {@link StakingNodeInfo} to translate
     * @return the translated {@link com.hedera.node.app.service.mono.state.merkle.MerkleStakingInfo}
     */
    @NonNull
    public static com.hedera.node.app.service.mono.state.merkle.MerkleStakingInfo merkleStakingInfoFromStakingNodeInfo(
            @NonNull final StakingNodeInfo stakingNodeInfo) {
        requireNonNull(stakingNodeInfo);
        com.hedera.node.app.service.mono.state.merkle.MerkleStakingInfo merkleStakingInfo =
                new com.hedera.node.app.service.mono.state.merkle.MerkleStakingInfo();

        merkleStakingInfo.setKey(EntityNum.fromLong(stakingNodeInfo.nodeNumber()));
        merkleStakingInfo.setMinStake(stakingNodeInfo.minStake());
        merkleStakingInfo.setMaxStake(stakingNodeInfo.maxStake());
        merkleStakingInfo.setStakeToReward(stakingNodeInfo.stakeToReward());
        merkleStakingInfo.setStakeToNotReward(stakingNodeInfo.stakeToNotReward());
        merkleStakingInfo.setStakeRewardStart(stakingNodeInfo.stakeRewardStart());
        merkleStakingInfo.setUnclaimedStakeRewardStart(stakingNodeInfo.unclaimedStakeRewardStart());
        merkleStakingInfo.setStake(stakingNodeInfo.stake());
        merkleStakingInfo.setRewardSumHistory(
                stakingNodeInfo.rewardSumHistory().stream().mapToLong(l -> l).toArray());
        merkleStakingInfo.setWeight(stakingNodeInfo.weight());

        return merkleStakingInfo;
    }
}
