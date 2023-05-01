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

package com.hedera.node.app.state;

import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.transaction.TransactionReceipt;
import edu.umd.cs.findbugs.annotations.NonNull;

/** Tracks transactions and whether there is a receipt present for those transactions. */
public interface RecordCache {
    /**
     * Gets whether there is a receipt present for the given transaction.
     *
     * @param transactionID The transaction to check a receipt for
     * @return Whether we have a receipt for the given transaction
     */
    boolean isReceiptPresent(@NonNull TransactionID transactionID);

    /**
     * Records the fact that the given transaction has been encountered in pre-consensus.
     *
     * @param transactionID The transaction to track
     * @param receipt The transaction receipt to add
     */
    void addPreConsensus(@NonNull TransactionID transactionID, @NonNull TransactionReceipt receipt);
}
