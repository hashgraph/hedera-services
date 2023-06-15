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

package com.hedera.node.app.service.token;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.state.token.StakingNodeInfo;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;

/**
 * Provides read-only methods for interacting with the underlying data storage mechanisms for
 * working with node stakimg infos.
 */
public interface ReadableStakingInfoStore {
    /**
     * Fetches a {@link StakingNodeInfo} object from state with the given {@link AccountID}. If the node could not be
     * fetched because the given node doesn't exist, returns {@code null}.
     *
     * @param nodeId given node account id
     * @return {@link StakingNodeInfo} object if successfully fetched or {@code null} if the node doesn't exist
     */
    @Nullable
    StakingNodeInfo get(@NonNull final AccountID nodeId);
}
