/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform.eventhandling;

import com.swirlds.common.config.TransactionConfig;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.system.transaction.ConsensusTransaction;
import com.swirlds.common.system.transaction.internal.ConsensusTransactionImpl;
import com.swirlds.common.system.transaction.internal.StateSignatureTransaction;
import com.swirlds.common.utility.Clearable;
import com.swirlds.platform.components.transaction.TransactionSupplier;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Queue;

/**
 * Store a list of transactions created by self, both system and non-system, for wrapping in the next event to be
 * created.
 */
public class TransactionPool implements TransactionSupplier, Clearable {

    /**
     * A list of transactions created by this node waiting to be put into a self-event.
     */
    private final Queue<ConsensusTransactionImpl> bufferedTransactions = new LinkedList<>();

    /**
     * A list of high-priority transactions created by this node waiting to be put into a self-event. Transactions in
     * this queue are always inserted into an event before transactions waiting in {@link #bufferedTransactions}.
     */
    private final Queue<ConsensusTransactionImpl> priorityBufferedTransactions = new LinkedList<>();

    /**
     * The number of buffered signature transactions waiting to be put into events.
     */
    private int bufferedSignatureTransactionCount = 0;

    /**
     * The maximum number of bytes of transactions that can be put in an event.
     */
    private final int maxTransactionBytesPerEvent;

    /**
     * The maximum desired size of the transaction queue. If the queue is larger than this, then new app transactions
     * are rejected.
     */
    private final int throttleTransactionQueueSize;

    /**
     * Metrics for the transaction pool.
     */
    private final TransactionPoolMetrics transactionPoolMetrics;

    /**
     * Creates a new transaction pool for transactions waiting to be put in an event.
     *
     * @param platformContext the platform context
     */
    public TransactionPool(@NonNull final PlatformContext platformContext) {
        Objects.requireNonNull(platformContext);

        final TransactionConfig transactionConfig =
                platformContext.getConfiguration().getConfigData(TransactionConfig.class);
        maxTransactionBytesPerEvent = transactionConfig.maxTransactionBytesPerEvent();
        throttleTransactionQueueSize = transactionConfig.throttleTransactionQueueSize();

        transactionPoolMetrics = new TransactionPoolMetrics(
                platformContext, this::getBufferedTransactionCount, this::getPriorityBufferedTransactionCount);
    }

    /**
     * Get the next transaction that should be inserted into an event, or null if there is no available transaction.
     *
     * @param currentEventSize the current size in bytes of the event being constructed
     * @return the next transaction, or null if no transaction is available
     */
    @Nullable
    @SuppressWarnings("ConstantConditions")
    private ConsensusTransactionImpl getNextTransaction(final int currentEventSize) {
        final int maxSize = maxTransactionBytesPerEvent - currentEventSize;

        if (!priorityBufferedTransactions.isEmpty()
                && priorityBufferedTransactions.peek().getSerializedLength() <= maxSize) {
            return priorityBufferedTransactions.poll();
        }

        if (!bufferedTransactions.isEmpty() && bufferedTransactions.peek().getSerializedLength() <= maxSize) {
            return bufferedTransactions.poll();
        }

        return null;
    }

    /**
     * Removes as many transactions from the list waiting to be in an event that can fit (FIFO ordering), and returns
     * them as an array, along with a boolean indicating if the array of transactions returned contains a freeze state
     * signature transaction.
     */
    @NonNull
    @Override
    public synchronized ConsensusTransactionImpl[] getTransactions() {
        // Early return due to no transactions waiting
        if (bufferedTransactions.isEmpty() && priorityBufferedTransactions.isEmpty()) {
            return new ConsensusTransactionImpl[0];
        }

        final List<ConsensusTransactionImpl> selectedTrans = new LinkedList<>();
        int currEventSize = 0;

        while (true) {
            final ConsensusTransactionImpl transaction = getNextTransaction(currEventSize);

            if (transaction == null) {
                // No transaction of suitable size is available
                break;
            }

            currEventSize += transaction.getSerializedLength();
            selectedTrans.add(transaction);

            if (transaction.isSystem() && isSignatureTransaction(transaction)) {
                bufferedSignatureTransactionCount--;
            }
        }

        return selectedTrans.toArray(new ConsensusTransactionImpl[0]);
    }

    /**
     * Check if a transaction is a signature transaction.
     *
     * @param transaction the transaction to check
     * @return true if the transaction is a signature transaction
     */
    private static boolean isSignatureTransaction(@NonNull final ConsensusTransaction transaction) {
        // check the class rather than casting and calling getType() because it is more performant
        return transaction.getClass().equals(StateSignatureTransaction.class);
    }

    /**
     * Check if there are any buffered signature transactions waiting to be put into events.
     *
     * @return true if there are any buffered signature transactions
     */
    public synchronized boolean hasBufferedSignatureTransactions() {
        return bufferedSignatureTransactionCount > 0;
    }

    /**
     * Add the given transaction to the list of transactions to be submitted to the network. If the queue is full, it
     * does nothing and returns false immediately.
     *
     * @param transaction The transaction. It must have been created by self.
     * @param priority    if true, then this transaction will be submitted before other waiting transactions that are
     *                    not marked with the priority flag. Use with moderation, adding too many priority transactions
     *                    (i.e. thousands per second) may disrupt the ability of the platform to perform some core
     *                    functionalities.
     * @return true if successful
     */
    @SuppressWarnings("ConstantConditions")
    public synchronized boolean submitTransaction(
            @NonNull final ConsensusTransactionImpl transaction, final boolean priority) {

        Objects.requireNonNull(transaction);

        // Always submit system transactions. If it's not a system transaction, then only submit it if we
        // don't violate queue size capacity restrictions.
        if (!transaction.isSystem()
                && (bufferedTransactions.size() + priorityBufferedTransactions.size()) > throttleTransactionQueueSize) {
            transactionPoolMetrics.recordRejectedAppTransaction();
            return false;
        }

        if (transaction.isSystem()) {
            if (isSignatureTransaction(transaction)) {
                bufferedSignatureTransactionCount++;
            }
            transactionPoolMetrics.recordSubmittedPlatformTransaction();
        } else {
            transactionPoolMetrics.recordAcceptedAppTransaction();
        }

        if (priority) {
            priorityBufferedTransactions.add(transaction);
        } else {
            bufferedTransactions.add(transaction);
        }

        return true;
    }

    /**
     * get the number of buffered transactions
     *
     * @return the number of transactions
     */
    private synchronized int getBufferedTransactionCount() {
        return bufferedTransactions.size();
    }

    /**
     * get the number of priority buffered transactions
     *
     * @return the number of transactions
     */
    private synchronized int getPriorityBufferedTransactionCount() {
        return priorityBufferedTransactions.size();
    }

    /**
     * Clear all the transactions
     */
    @Override
    public synchronized void clear() {
        bufferedTransactions.clear();
        priorityBufferedTransactions.clear();
        bufferedSignatureTransactionCount = 0;
    }
}
