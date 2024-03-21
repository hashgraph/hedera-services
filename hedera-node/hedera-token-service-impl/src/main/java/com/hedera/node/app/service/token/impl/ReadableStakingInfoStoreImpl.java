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

import static com.hedera.node.app.service.token.impl.TokenServiceImpl.STAKING_INFO_KEY;

import com.hedera.hapi.node.state.common.EntityNumber;
import com.hedera.hapi.node.state.token.StakingNodeInfo;
import com.hedera.node.app.service.token.ReadableStakingInfoStore;
import com.swirlds.platform.state.spi.ReadableKVState;
import com.swirlds.platform.state.spi.ReadableStates;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Default implementation of {@link ReadableStakingInfoStore}
 */
public class ReadableStakingInfoStoreImpl implements ReadableStakingInfoStore {

    /** The underlying data storage class that holds node staking data. */
    private final ReadableKVState<EntityNumber, StakingNodeInfo> stakingInfoState;
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
    public StakingNodeInfo get(final long nodeId) {
        return stakingInfoState.get(EntityNumber.newBuilder().number(nodeId).build());
    }

    @NonNull
    @Override
    public Set<Long> getAll() {
        // For entity types that have many instances this code would be a bad idea, but for node staking info there
        // should only be a limited number of staking nodes in state. Iterating over all of them should not be expensive
        final var keysIter = stakingInfoState.keys();
        if (!keysIter.hasNext()) return Collections.emptySet();

        final var nodeIds = new HashSet<Long>();
        while (keysIter.hasNext()) {
            nodeIds.add(keysIter.next().number());
        }

        return nodeIds;
    }
}
