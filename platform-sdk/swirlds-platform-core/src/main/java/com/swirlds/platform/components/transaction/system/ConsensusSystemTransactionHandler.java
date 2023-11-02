/*
 * Copyright (C) 2016-2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform.components.transaction.system;

import com.swirlds.common.system.NodeId;
import com.swirlds.common.system.SoftwareVersion;
import com.swirlds.common.system.transaction.internal.SystemTransaction;
import com.swirlds.platform.state.State;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;

/**
 * Handles system transactions post-consensus
 *
 * @param <T> the system transaction type
 */
@FunctionalInterface
public interface ConsensusSystemTransactionHandler<T extends SystemTransaction> {

    /**
     * Execute the post-consensus system transaction handler
     *
     * @param state        a mutable state
     * @param nodeId       the id of the node which created the transaction
     * @param transaction  the transaction being handled
     * @param eventVersion the version of the event that contains the transaction
     */
    void handle(
            @NonNull State state,
            @NonNull NodeId nodeId,
            @NonNull T transaction,
            @Nullable SoftwareVersion eventVersion);
}
