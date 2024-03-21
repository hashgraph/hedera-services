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
import com.hedera.node.app.service.token.ReadableNetworkStakingRewardsStore;
import com.swirlds.platform.state.spi.ReadableSingletonState;
import com.swirlds.platform.state.spi.ReadableStates;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Default implementation of {@link ReadableNetworkStakingRewardsStore}
 */
public class ReadableNetworkStakingRewardsStoreImpl implements ReadableNetworkStakingRewardsStore {

    /** The underlying data storage class that holds staking reward data for all nodes. */
    private final ReadableSingletonState<NetworkStakingRewards> stakingRewardsState;
    /**
     * Create a new {@link ReadableNetworkStakingRewardsStoreImpl} instance.
     *
     * @param states The state to use.
     */
    public ReadableNetworkStakingRewardsStoreImpl(@NonNull final ReadableStates states) {
        this.stakingRewardsState = requireNonNull(states).getSingleton(STAKING_NETWORK_REWARDS_KEY);
    }

    /** {@inheritDoc} */
    @Override
    public boolean isStakingRewardsActivated() {
        return requireNonNull(stakingRewardsState.get()).stakingRewardsActivated();
    }

    /** {@inheritDoc} */
    @Override
    public long totalStakeRewardStart() {
        return requireNonNull(stakingRewardsState.get()).totalStakedRewardStart();
    }

    /** {@inheritDoc} */
    @Override
    public long totalStakedStart() {
        return requireNonNull(stakingRewardsState.get()).totalStakedStart();
    }

    /** {@inheritDoc} */
    @Override
    public long pendingRewards() {
        return requireNonNull(stakingRewardsState.get()).pendingRewards();
    }

    @Override
    public NetworkStakingRewards get() {
        return requireNonNull(stakingRewardsState.get());
    }
}
