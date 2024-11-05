/*
 * Copyright (C) 2022-2024 Hedera Hashgraph, LLC
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

package com.swirlds.common;

import static com.hedera.hapi.platform.event.EventTransaction.TransactionOneOfType.APPLICATION_TRANSACTION;
import static com.hedera.hapi.platform.event.EventTransaction.TransactionOneOfType.STATE_SIGNATURE_TRANSACTION;
import static com.swirlds.common.utility.CompareTo.isLessThan;
import static com.swirlds.logging.legacy.LogMarker.EXCEPTION;

import com.hedera.hapi.platform.event.EventTransaction;
import com.hedera.pbj.runtime.OneOf;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.common.config.EventCreationConfig;
import com.swirlds.common.config.TransactionConfig;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.utility.throttle.RateLimitedLogger;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.time.Duration;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Queue;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Store a list of transactions created by self, both system and non-system, for wrapping in the next event to be
 * created.
 */
public class TransactionPoolNexus implements TransactionSupplier {

    private static final Logger logger = LogManager.getLogger(TransactionPoolNexus.class);
    private final RateLimitedLogger illegalTransactionLogger;

    /**
     * A list of transactions created by this node waiting to be put into a self-event.
     */
    private final Queue<EventTransaction> bufferedTransactions = new LinkedList<>();

    /**
     * A list of high-priority transactions created by this node waiting to be put into a self-event. Transactions in
     * this queue are always inserted into an event before transactions waiting in {@link #bufferedTransactions}.
     */
    private final Queue<EventTransaction> priorityBufferedTransactions = new LinkedList<>();

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
     * The maximum size of a transaction in bytes.
     */
    private final int maximumTransactionSize;

    /**
     * The current status of the platform.
     */
    private PlatformStatus platformStatus = PlatformStatus.STARTING_UP;

    /**
     * The maximum amount of time the platform may be in an unhealthy state before we start rejecting transactions.
     */
    private final Duration maximumPermissibleUnhealthyDuration;

    /**
     * Whether the platform is currently in a healthy state.
     */
    private boolean healthy = true;

    /**
     * Creates a new transaction pool for transactions waiting to be put in an event.
     *
     * @param platformContext the platform context
     */
    public TransactionPoolNexus(@NonNull final PlatformContext platformContext) {
        Objects.requireNonNull(platformContext);

        illegalTransactionLogger = new RateLimitedLogger(logger, platformContext.getTime(), Duration.ofMinutes(10));

        final TransactionConfig transactionConfig =
                platformContext.getConfiguration().getConfigData(TransactionConfig.class);
        maxTransactionBytesPerEvent = transactionConfig.maxTransactionBytesPerEvent();
        throttleTransactionQueueSize = transactionConfig.throttleTransactionQueueSize();

        transactionPoolMetrics = new TransactionPoolMetrics(
                platformContext, this::getBufferedTransactionCount, this::getPriorityBufferedTransactionCount);

        maximumTransactionSize = transactionConfig.transactionMaxBytes();

        final EventCreationConfig eventCreationConfig =
                platformContext.getConfiguration().getConfigData(EventCreationConfig.class);
        maximumPermissibleUnhealthyDuration = eventCreationConfig.maximumPermissibleUnhealthyDuration();
    }

    // FUTURE WORK: these checks should be unified with the checks performed when a system transaction is submitted.
    // The reason why this method coexists with submitTransaction() is due to legacy reasons, not because it
    // actually makes sense to have this distinction.

    /**
     * Attempt to submit an application transaction. Similar to
     * {@link #submitTransaction} but with extra safeguards.
     *
     * @param appTransaction the transaction to submit
     * @return true if the transaction passed all validity checks and was accepted by the consumer
     */
    public synchronized boolean submitApplicationTransaction(@NonNull final Bytes appTransaction) {
        if (!healthy || platformStatus != PlatformStatus.ACTIVE) {
            return false;
        }

        if (appTransaction == null) {
            // FUTURE WORK: This really should throw, but to avoid changing existing API this will be changed later.
            illegalTransactionLogger.error(EXCEPTION.getMarker(), "transaction is null");
            return false;
        }
        final EventTransaction transaction = new EventTransaction(new OneOf<>(APPLICATION_TRANSACTION, appTransaction));
        if (TransactionUtils.getLegacyTransactionSize(transaction) > maximumTransactionSize) {
            // FUTURE WORK: This really should throw, but to avoid changing existing API this will be changed later.
            illegalTransactionLogger.error(
                    EXCEPTION.getMarker(),
                    "transaction has {} bytes, maximum permissible transaction size is {}",
                    appTransaction.length(),
                    maximumTransactionSize);
            return false;
        }

        return submitTransaction(transaction, false);
    }

    /**
     * Attempt to submit a transaction.
     *
     * @param transaction The transaction. It must have been created by self.
     * @param priority    if true, then this transaction will be submitted before other waiting transactions that are
     *                    not marked with the priority flag. Use with moderation, adding too many priority transactions
     *                    (i.e. thousands per second) may disrupt the ability of the platform to perform some core
     *                    functionalities.
     * @return true if successful
     */
    public synchronized boolean submitTransaction(@NonNull final EventTransaction transaction, final boolean priority) {

        Objects.requireNonNull(transaction);
        final boolean isSystem = TransactionUtils.isSystemTransaction(transaction);

        // Always submit system transactions. If it's not a system transaction, then only submit it if we
        // don't violate queue size capacity restrictions.
        if (!isSystem
                && (bufferedTransactions.size() + priorityBufferedTransactions.size()) > throttleTransactionQueueSize) {
            transactionPoolMetrics.recordRejectedAppTransaction();
            return false;
        }

        if (isSystem) {
            bufferedSignatureTransactionCount++;
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
     * Update the platform status.
     *
     * @param platformStatus the new platform status
     */
    public synchronized void updatePlatformStatus(@NonNull final PlatformStatus platformStatus) {
        this.platformStatus = platformStatus;
    }

    /**
     * Report the amount of time that the system has been in an unhealthy state. Will receive a report of
     * {@link Duration#ZERO} when the system enters a healthy state.
     *
     * @param duration the amount of time that the system has been in an unhealthy state
     */
    public synchronized void reportUnhealthyDuration(@NonNull final Duration duration) {
        healthy = isLessThan(duration, maximumPermissibleUnhealthyDuration);
    }

    /**
     * Get the next transaction that should be inserted into an event, or null if there is no available transaction.
     *
     * @param currentEventSize the current size in bytes of the event being constructed
     * @return the next transaction, or null if no transaction is available
     */
    @Nullable
    private EventTransaction getNextTransaction(final int currentEventSize) {
        final int maxSize = maxTransactionBytesPerEvent - currentEventSize;

        if (!priorityBufferedTransactions.isEmpty()
                && TransactionUtils.getLegacyTransactionSize(priorityBufferedTransactions.peek()) <= maxSize) {
            return priorityBufferedTransactions.poll();
        }

        if (!bufferedTransactions.isEmpty()
                && TransactionUtils.getLegacyTransactionSize(bufferedTransactions.peek()) <= maxSize) {
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
    public synchronized List<EventTransaction> getTransactions() {
        // Early return due to no transactions waiting
        if (bufferedTransactions.isEmpty() && priorityBufferedTransactions.isEmpty()) {
            return Collections.emptyList();
        }

        final List<EventTransaction> selectedTrans = new LinkedList<>();
        int currEventSize = 0;

        while (true) {
            final EventTransaction transaction = getNextTransaction(currEventSize);

            if (transaction == null) {
                // No transaction of suitable size is available
                break;
            }

            currEventSize += TransactionUtils.getLegacyTransactionSize(transaction);
            selectedTrans.add(transaction);

            if (STATE_SIGNATURE_TRANSACTION.equals(transaction.transaction().kind())) {
                bufferedSignatureTransactionCount--;
            }
        }

        return selectedTrans;
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
    public synchronized void clear() {
        bufferedTransactions.clear();
        priorityBufferedTransactions.clear();
        bufferedSignatureTransactionCount = 0;
    }
}
