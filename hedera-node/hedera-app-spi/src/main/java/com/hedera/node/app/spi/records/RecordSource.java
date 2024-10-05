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

package com.hedera.node.app.spi.records;

import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.transaction.TransactionReceipt;
import com.hedera.hapi.node.transaction.TransactionRecord;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;
import java.util.function.Consumer;

/**
 * A source of {@link TransactionRecord}s and {@link TransactionReceipt}s for one or more {@link TransactionID}'s.
 */
public interface RecordSource {
    /**
     * A receipt with its originating {@link TransactionID}.
     * @param txnId the transaction id
     * @param receipt the matching receipt
     */
    record IdentifiedReceipt(@NonNull TransactionID txnId, @NonNull TransactionReceipt receipt) {
        public IdentifiedReceipt {
            requireNonNull(txnId);
            requireNonNull(receipt);
        }
    }

    /**
     * Returns all identified receipts known to this source.
     * @return the receipts
     */
    List<IdentifiedReceipt> identifiedReceipts();

    /**
     * Perform the given action on each transaction record known to this source.
     * @param action the action to perform
     */
    void forEachTxnRecord(@NonNull Consumer<TransactionRecord> action);

    /**
     * Returns the priority receipt for the given transaction id.
     * @throws IllegalArgumentException if the transaction id is unknown
     */
    TransactionReceipt receiptOf(@NonNull TransactionID txnId);

    /**
     * Returns all child receipts for the given transaction id.
     */
    List<TransactionReceipt> childReceiptsOf(@NonNull TransactionID txnId);
}
