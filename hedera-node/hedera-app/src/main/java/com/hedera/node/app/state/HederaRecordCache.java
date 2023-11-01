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
import com.hedera.node.app.spi.records.RecordCache;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;

/**
 * A time-limited cache of transaction records and receipts.
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
public interface HederaRecordCache extends RecordCache {
    /**
     * Records the fact that the given {@link TransactionID} has been seen by the given node. If the node has already
     * been seen, then this call is a no-op. This call does not perform any additional validation of the transaction ID.
     *
     * @param nodeId The node ID of the node that submitted this transaction to consensus, as known in the address book
     * @param payerAccountId The {@link AccountID} of the "payer" of the transaction
     * @param transactionRecords The list of all related transaction records. This may be a stream of 1, if the list
     *                           only contains the user transaction. Or it may be a list including preceding
     *                           transactions, user transactions, and child transactions. There is no requirement as to
     *                           the order of items in this list.
     */
    /*HANDLE THREAD ONLY*/
    void add(long nodeId, @NonNull AccountID payerAccountId, @NonNull List<SingleTransactionRecord> transactionRecords);

    /**
     * Checks if the given transaction ID has been seen by this node. If it has not, the result is
     * {@link DuplicateCheckResult#NO_DUPLICATE}. If it has, then the result is {@link DuplicateCheckResult#SAME_NODE} if the
     * transaction was submitted by the given node before, or {@link DuplicateCheckResult#OTHER_NODE} if the transaction was
     * submitted by different node(s).
     *
     * @param transactionID The {@link TransactionID} to check
     * @param nodeId The node ID of the node that submitted the current transaction
     * @return The result of the check
     */
    @NonNull
    DuplicateCheckResult hasDuplicate(@NonNull TransactionID transactionID, long nodeId);

    /** The possible results of a duplicate check */
    enum DuplicateCheckResult {
        /** No duplicate found **/
        NO_DUPLICATE,

        /** A duplicate from the same node was found **/
        SAME_NODE,

        /** A duplicate from a different node was found **/
        OTHER_NODE
    }
}
