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

package com.hedera.node.app.service.token;

import com.hedera.hapi.node.state.token.StakingNodeInfo;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.Set;

/**
 * Provides read-only methods for interacting with the underlying data storage mechanisms for
 * working with node staking infos.
 */
public interface ReadableStakingInfoStore {
    /**
     * Fetches a {@link StakingNodeInfo} object from state with the given node ID. If the node could not be
     * fetched because the given node doesn't exist, returns {@code null}.
     *
     * @param nodeId the given node ID
     * @return {@link StakingNodeInfo} object if successfully fetched or {@code null} if the node doesn't exist
     */
    @Nullable
    StakingNodeInfo get(long nodeId);

    /**
     * Fetches all node IDs from state. If no nodes exist, returns an empty collection.
     *
     * <p>
     * ⚠️⚠️
     * WARNING:
     * This method only exists because staking state is small. Don't follow this pattern in other stores
     * without careful consideration!
     * ⚠️⚠️
     *
     * @return a set of all registered node IDs
     */
    @NonNull
    Set<Long> getAll();
}
