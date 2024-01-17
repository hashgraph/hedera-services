/*
 * Copyright (C) 2016-2024 Hedera Hashgraph, LLC
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

import com.swirlds.common.platform.NodeId;
import com.swirlds.platform.system.transaction.SystemTransaction;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Handles system transactions pre-consensus
 *
 * @param <T> the system transaction type
 */
@FunctionalInterface
public interface PreconsensusSystemTransactionHandler<T extends SystemTransaction> {

    /**
     * Execute the pre-consensus system transaction handler
     *
     * @param nodeId      the id of the node which created the transaction
     * @param transaction the transaction being handled
     */
    void handle(@NonNull NodeId nodeId, @NonNull T transaction);
}
