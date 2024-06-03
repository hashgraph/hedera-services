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

package com.hedera.node.app.service.token.impl.codec;

import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.state.token.NetworkStakingRewards;
import com.hedera.node.app.service.mono.state.merkle.MerkleNetworkContext;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Networking Staking Translator from merkle network context to network staking rewards.
 */
public final class NetworkingStakingTranslator {

    private NetworkingStakingTranslator() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Converts a Part of {@link MerkleNetworkContext} to {@link NetworkStakingRewards}.
     * @param merkleNetworkContext the {@link MerkleNetworkContext} from which to convert
     * @return the {@link NetworkStakingRewards} converted from the {@link MerkleNetworkContext}
     */
    @NonNull
    public static NetworkStakingRewards networkStakingRewardsFromMerkleNetworkContext(
            @NonNull final MerkleNetworkContext merkleNetworkContext) {
        requireNonNull(merkleNetworkContext);
        return NetworkStakingRewards.newBuilder()
                .stakingRewardsActivated(merkleNetworkContext.areRewardsActivated())
                .totalStakedRewardStart(merkleNetworkContext.getTotalStakedRewardStart())
                .totalStakedStart(merkleNetworkContext.getTotalStakedStart())
                .pendingRewards(merkleNetworkContext.pendingRewards())
                .build();
    }
}
