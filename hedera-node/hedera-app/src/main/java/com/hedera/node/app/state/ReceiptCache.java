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

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.transaction.TransactionReceipt;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.Set;

/**
 * A time-limited cache of transaction receipts.
 *
 * <p>Each {@link com.hedera.hapi.node.base.Transaction} has a unique {@link TransactionID}. Each {@link TransactionID}
 * is valid for only a certain period of time. If submitted before the {@link TransactionID#transactionValidStart()}
 * then the transaction is invalid. If submitted after the network configured expiration time from that start time,
 * then the transaction is invalid. Between those two times, the network needs to deduplicate transactions.
 *
 * <p>It may be that the same transaction is submitted by the user multiple times to the same node. The node uses this
 * cache as a mechanism to detect those duplicate transactions and reject the duplicates.
 *
 * <p>It may be that transactions with the same {@link TransactionID} may be submitted by the user to multiple nodes.
 * If this happens, we need to only process one of those transactions, but charge the node + network fees to the user
 * for each transaction they submitted.
 *
 * <p>It may be that a dishonest node receives a transaction from the user and sends multiple copies of it. This
 * needs to be detected and the node charged for the duplicate transactions.
 */
/*@ThreadSafe*/
public interface ReceiptCache {
    record CacheItem(
            @NonNull TransactionID transactionID,
            @NonNull Set<AccountID> nodeAccountIDs,
            @NonNull TransactionReceipt receipt) {}

    /**
     * Records the fact that the given {@link TransactionID} has been seen by the given node. If the node has already
     * been seen, then this call is a no-op. This call does not validate that the transaction ID is valid.
     *
     * @param transactionID The transaction to track
     * @param nodeAccountID The node that has seen this transaction
     */
    /*@ThreadSafe*/
    void record(@NonNull TransactionID transactionID, @NonNull AccountID nodeAccountID);

    /**
     * Gets the {@link CacheItem} for the given {@link TransactionID}. If the {@link TransactionID} is not present in
     * the cache, then a null response is returned.
     *
     * @param transactionID The {@link TransactionID} to lookup
     * @return The {@link CacheItem} for the given {@link TransactionID}, or null if not present
     */
    @Nullable
    /*@ThreadSafe*/
    CacheItem get(@NonNull TransactionID transactionID);

    /**
     * Updates the receipt for the given {@link TransactionID} to the given {@link TransactionReceipt}. If the
     * {@link TransactionID} is not present in the cache, then this method will fail with
     * {@link IllegalArgumentException}. This method should <b>ONLY</b> be called on the handle thread.
     *
     * @param transactionID The transaction with receipt to update
     * @param receipt The new receipt
     * @throws IllegalArgumentException If the {@link TransactionID} is not present in the cache
     */
    /*@ThreadSafe*/
    void update(@NonNull TransactionID transactionID, @NonNull TransactionReceipt receipt);
}
