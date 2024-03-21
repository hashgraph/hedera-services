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

package com.hedera.node.app.service.token.impl;

import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.state.common.EntityNumber;
import com.hedera.hapi.node.state.token.StakingNodeInfo;
import com.swirlds.platform.state.spi.WritableKVState;
import com.swirlds.platform.state.spi.WritableStates;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;

/**
 * Provides write methods for modifying underlying data storage mechanisms for working with
 * a node's staking information.
 *
 * <p>This class is not exported from the module. It is an internal implementation detail.
 */
public class WritableStakingInfoStore extends ReadableStakingInfoStoreImpl {

    /** The underlying data storage class that holds the staking data. */
    private final WritableKVState<EntityNumber, StakingNodeInfo> stakingInfoState;

    /**
     * Create a new {@link WritableStakingInfoStore} instance
     *
     * @param states The state to use
     */
    public WritableStakingInfoStore(@NonNull final WritableStates states) {
        super(states);
        requireNonNull(states);

        this.stakingInfoState = states.get(TokenServiceImpl.STAKING_INFO_KEY);
    }

    /**
     * Returns the {@link StakingNodeInfo} for the given node's numeric ID (NOT the account ID)
     *
     * @param nodeId - the node ID of the node to retrieve the staking info for
     */
    @Nullable
    public StakingNodeInfo getForModify(final long nodeId) {
        return stakingInfoState.getForModify(
                EntityNumber.newBuilder().number(nodeId).build());
    }

    /**
     * Persists a new {@link StakingNodeInfo} into state
     *
     * @param nodeId the node's ID
     * @param stakingNodeInfo the staking info to persist
     */
    public void put(final long nodeId, @NonNull final StakingNodeInfo stakingNodeInfo) {
        requireNonNull(stakingNodeInfo);
        stakingInfoState.put(EntityNumber.newBuilder().number(nodeId).build(), stakingNodeInfo);
    }

    /**
     * Gets the original value associated with the given nodeId before any modifications were made to
     * it. The returned value will be {@code null} if the nodeId does not exist.
     *
     * @param nodeId The nftId.
     * @return The original value, or null if there is no such nftId in the state
     */
    @Nullable
    public StakingNodeInfo getOriginalValue(final long nodeId) {
        return stakingInfoState.getOriginalValue(
                EntityNumber.newBuilder().number(nodeId).build());
    }
}
