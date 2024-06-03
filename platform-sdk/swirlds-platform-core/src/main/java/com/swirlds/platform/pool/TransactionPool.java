/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.swirlds.platform.pool;

import com.swirlds.common.wiring.component.InputWireLabel;
import com.swirlds.platform.system.transaction.ConsensusTransactionImpl;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Coordinates and manages a pool of transactions waiting to be submitted.
 */
public interface TransactionPool {

    /**
     * Submit a system transaction to the transaction pool. Transaction will be included in a future event, if
     * possible.
     *
     * @param transaction the system transaction to submit
     */
    @InputWireLabel("submit transaction")
    void submitSystemTransaction(@NonNull ConsensusTransactionImpl transaction);

    /**
     * Clear the transaction pool.
     */
    void clear();
}
