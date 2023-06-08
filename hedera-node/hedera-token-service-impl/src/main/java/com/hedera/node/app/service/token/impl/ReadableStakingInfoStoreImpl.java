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

package com.hedera.node.app.service.token.impl;

import static com.hedera.node.app.service.token.impl.TokenServiceImpl.STAKING_INFO_KEY;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.state.token.StakingNodeInfo;
import com.hedera.node.app.service.token.ReadableStakingInfoStore;
import com.hedera.node.app.spi.state.ReadableKVState;
import com.hedera.node.app.spi.state.ReadableStates;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;

/**
 * Default implementation of {@link ReadableStakingInfoStore}
 */
public class ReadableStakingInfoStoreImpl implements ReadableStakingInfoStore {

    /** The underlying data storage class that holds the account data. */
    private final ReadableKVState<AccountID, StakingNodeInfo> stakingInfoState;
    /**
     * Create a new {@link ReadableStakingInfoStoreImpl} instance.
     *
     * @param states The state to use.
     */
    public ReadableStakingInfoStoreImpl(@NonNull final ReadableStates states) {
        this.stakingInfoState = states.get(STAKING_INFO_KEY);
    }

    @Nullable
    @Override
    public StakingNodeInfo get(@NonNull final AccountID nodeId) {
        return getStakingInfoLeaf(nodeId);
    }

    private StakingNodeInfo getStakingInfoLeaf(final AccountID nodeId) {
        return stakingInfoState.get(nodeId);
    }
}
