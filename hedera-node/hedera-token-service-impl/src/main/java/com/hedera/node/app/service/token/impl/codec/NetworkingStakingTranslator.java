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

package com.hedera.node.app.service.token.impl.codec;

import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.state.token.NetworkStakingRewards;
import com.hedera.node.app.service.token.ReadableNetworkStakingRewardsStore;
import edu.umd.cs.findbugs.annotations.NonNull;

public final class NetworkingStakingTranslator {

    @NonNull
    /**
     * Converts a Part of {@link com.hedera.node.app.service.mono.state.merkle.MerkleNetworkContext} to {@link NetworkStakingRewards}.
     * @param merkleNetworkContext the {@link com.hedera.node.app.service.mono.state.merkle.MerkleNetworkContext}
     * @return the {@link NetworkStakingRewards} converted from the {@link com.hedera.node.app.service.mono.state.merkle.MerkleNetworkContext}
     */
    public static NetworkStakingRewards networkStakingRewardsFromMerkleNetworkContext(
            @NonNull final com.hedera.node.app.service.mono.state.merkle.MerkleNetworkContext merkleNetworkContext) {
        requireNonNull(merkleNetworkContext);
        return NetworkStakingRewards.newBuilder()
                .stakingRewardsActivated(merkleNetworkContext.areRewardsActivated())
                .totalStakedRewardStart(merkleNetworkContext.getTotalStakedRewardStart())
                .totalStakedStart(merkleNetworkContext.getTotalStakedStart())
                .pendingRewards(merkleNetworkContext.pendingRewards())
                .build();
    }

    @NonNull
    /***
     * Converts a {@link ReadableNetworkStakingRewardsStore} to a {@link com.hedera.node.app.service.mono.state.merkle.MerkleNetworkContext}.
     * @param readableNetworkStakingRewardsStore the {@link ReadableNetworkStakingRewardsStore} to convert
     * @return the {@link com.hedera.node.app.service.mono.state.merkle.MerkleNetworkContext}
     */
    public static com.hedera.node.app.service.mono.state.merkle.MerkleNetworkContext
            merkleNetworkContextFromNetworkStakingRewards(
                    @NonNull ReadableNetworkStakingRewardsStore readableNetworkStakingRewardsStore) {
        requireNonNull(readableNetworkStakingRewardsStore);
        final var networkStakingRewards = readableNetworkStakingRewardsStore.get();
        return merkleNetworkContextFromNetworkStakingRewards(networkStakingRewards);
    }

    @NonNull
    /***
     * Converts a {@link NetworkStakingRewards} to a {@link com.hedera.node.app.service.mono.state.merkle.MerkleNetworkContext}.
     * @param networkStakingRewards the {@link NetworkStakingRewards} to convert
     * @return the {@link com.hedera.node.app.service.mono.state.merkle.MerkleNetworkContext}
     */
    public static com.hedera.node.app.service.mono.state.merkle.MerkleNetworkContext
            merkleNetworkContextFromNetworkStakingRewards(@NonNull NetworkStakingRewards networkStakingRewards) {
        requireNonNull(networkStakingRewards);
        final com.hedera.node.app.service.mono.state.merkle.MerkleNetworkContext merkleNetworkContext =
                new com.hedera.node.app.service.mono.state.merkle.MerkleNetworkContext();

        merkleNetworkContext.setStakingRewardsActivated(networkStakingRewards.stakingRewardsActivated());
        merkleNetworkContext.setTotalStakedRewardStart(networkStakingRewards.totalStakedRewardStart());
        merkleNetworkContext.setTotalStakedStart(networkStakingRewards.totalStakedStart());
        merkleNetworkContext.setPendingRewards(networkStakingRewards.pendingRewards());
        return merkleNetworkContext;
    }
}
