/*
 * Copyright (C) 2022-2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.token.impl;

import static com.hedera.node.app.service.token.impl.TokenServiceImpl.STAKING_NETWORK_REWARDS_KEY;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.state.token.NetworkStakingRewards;
import com.swirlds.platform.state.spi.WritableSingletonState;
import com.swirlds.platform.state.spi.WritableStates;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Default implementation of {@link WritableNetworkStakingRewardsStore}
 */
public class WritableNetworkStakingRewardsStore extends ReadableNetworkStakingRewardsStoreImpl {

    /** The underlying data storage class that holds staking reward data for all nodes. */
    private final WritableSingletonState<NetworkStakingRewards> stakingRewardsState;
    /**
     * Create a new {@link WritableNetworkStakingRewardsStore} instance.
     *
     * @param states The state to use.
     */
    public WritableNetworkStakingRewardsStore(@NonNull final WritableStates states) {
        super(states);
        this.stakingRewardsState = requireNonNull(states).getSingleton(STAKING_NETWORK_REWARDS_KEY);
    }

    /**
     * Persists the staking rewards data to the underlying storage.
     * @param stakingRewards The staking rewards data to persist.
     */
    public void put(@NonNull final NetworkStakingRewards stakingRewards) {
        requireNonNull(stakingRewards);
        stakingRewardsState.put(stakingRewards);
    }
}
