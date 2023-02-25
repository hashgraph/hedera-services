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

import static com.swirlds.platform.components.TransThrottleSyncAndCreateRuleResponse.PASS;
import static com.swirlds.platform.components.TransThrottleSyncAndCreateRuleResponse.SYNC_AND_CREATE;

import com.swirlds.common.system.EventCreationRuleResponse;
import com.swirlds.common.system.transaction.ConsensusTransaction;
import com.swirlds.common.system.transaction.internal.ConsensusTransactionImpl;
import com.swirlds.common.system.transaction.internal.StateSignatureTransaction;
import com.swirlds.platform.SettingsProvider;
import com.swirlds.platform.components.TransThrottleSyncAndCreateRule;
import com.swirlds.platform.components.TransThrottleSyncAndCreateRuleResponse;
import com.swirlds.platform.components.TransactionPool;
import com.swirlds.platform.components.TransactionSupplier;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.function.BooleanSupplier;

/**
 * Store a list of transactions created by self, both system and non-system, for wrapping in the next
 * event to be created.
 */
public class EventTransactionPool implements TransactionPool, TransactionSupplier, TransThrottleSyncAndCreateRule {

    /**
     * A list of transactions created by this node waiting to be put into a self-event.
     */
    protected final Queue<ConsensusTransactionImpl> transEvent = new LinkedList<>();

    /**
     * A list of high-priority transactions created by this node waiting to be put into a self-event.
     * Transactions in this queue are always inserted into an event before transactions waiting in
     * {@link #transEvent}.
     */
    protected final Queue<ConsensusTransactionImpl> priorityTransEvent = new LinkedList<>();

    /**
     * the number of user transactions in the transEvent/priorityTransEvent lists
     */
    protected volatile int numUserTransEvent = 0;

    /**
     * the number of signature system transactions in the transEvent/priorityTransEvent lists
     */
    protected volatile int numSignatureTransEvent = 0;

    /**
     * Indicates if the system is currently in a freeze.
     */
    private final BooleanSupplier inFreeze;

    protected final SettingsProvider settings;

    // Used for creating spy objects for unit tests
    public EventTransactionPool() {
        settings = null;
        inFreeze = null;
    }

    /**
     * Creates a new transaction pool for transactions waiting to be put in an event.
     *
     * @param settings
     * 		settings to use
     * @param inFreeze
     * 		Indicates if the system is currently in a freeze
     */
    public EventTransactionPool(final SettingsProvider settings, final BooleanSupplier inFreeze) {
        this.settings = settings;
        this.inFreeze = inFreeze;
    }

    /**
     * Get the next transaction that should be inserted into an event, or null if there is no available transaction.
     *
     * @param currentEventSize
     * 		the current size in bytes of the event being constructed
     * @return the next transaction, or null if no transaction is available
     */
    @SuppressWarnings("ConstantConditions")
    private ConsensusTransactionImpl getNextTransaction(final int currentEventSize) {

        final int maxSize = settings.getMaxTransactionBytesPerEvent() - currentEventSize;

        if (!priorityTransEvent.isEmpty() && priorityTransEvent.peek().getSerializedLength() <= maxSize) {
            return priorityTransEvent.poll();
        }

        if (!transEvent.isEmpty() && transEvent.peek().getSerializedLength() <= maxSize) {
            return transEvent.poll();
        }

        return null;
    }

    /**
     * Removes as many transactions from the list waiting to be in an event that can fit (FIFO ordering), and returns
     * them as an array, along with a boolean indicating if the array of transactions returned contains a freeze state
     * signature transaction.
     */
    @Override
    public synchronized ConsensusTransactionImpl[] getTransactions() {
        // Early return due to no transactions waiting
        if (transEvent.isEmpty() && priorityTransEvent.isEmpty()) {
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

            if (!transaction.isSystem()) {
                numUserTransEvent--;
            } else {
                if (isSignatureTrans(transaction)) {
                    numSignatureTransEvent--;
                }
            }
        }

        return selectedTrans.toArray(new ConsensusTransactionImpl[0]);
    }

    private static boolean isSignatureTrans(final ConsensusTransaction transaction) {
        // check the class rather than casting and calling getType() because it is more performant
        return transaction.getClass().equals(StateSignatureTransaction.class);
    }

    /**
     * @return the number of transactions waiting to be put in an event, both user and state signature system
     * 		transactions
     */
    public int numTransForEvent() {
        return numUserTransEvent + numSignatureTransEvent;
    }

    /**
     * @return the number of signature transactions waiting to be put in an event.
     */
    public int numSignatureTransEvent() {
        return numSignatureTransEvent;
    }

    /**
     * {@inheritDoc}
     */
    @SuppressWarnings("ConstantConditions")
    @Override
    public EventCreationRuleResponse shouldCreateEvent() {
        if (numSignatureTransEvent() > 0 && inFreeze.getAsBoolean()) {
            return EventCreationRuleResponse.CREATE;
        } else {
            return EventCreationRuleResponse.PASS;
        }
    }

    /**
     * {@inheritDoc}
     */
    public TransThrottleSyncAndCreateRuleResponse shouldSyncAndCreate() {
        // if we have transactions waiting to be put into an event, initiate a sync
        if (numTransForEvent() > 0) {
            return SYNC_AND_CREATE;
        } else {
            return PASS;
        }
    }

    public boolean submitTransaction(final ConsensusTransactionImpl transaction) {
        return submitTransaction(transaction, false);
    }

    /**
     * Add the given transaction to the list of transactions to be submitted to the network.
     * If the queue is full, it does nothing and returns false immediately.
     *
     * @param transaction
     * 		The transaction. It must have been created by self.
     * @param priority
     * 		if true, then this transaction will be submitted before other waiting transactions that are
     * 		not marked with the priority flag. Use with moderation, adding too many priority transactions
     * 		(i.e. thousands per second) may disrupt the ability of the platform to perform some core functionalities.
     * @return true if successful
     */
    @SuppressWarnings("ConstantConditions")
    public synchronized boolean submitTransaction(final ConsensusTransactionImpl transaction, final boolean priority) {

        // Always submit system transactions. If it's not a system transaction, then only submit it if we
        // don't violate queue size capacity restrictions.
        if (!transaction.isSystem()
                && (transEvent.size() + priorityTransEvent.size()) > settings.getThrottleTransactionQueueSize()) {
            return false;
        }

        if (!transaction.isSystem()) {
            numUserTransEvent++;
        } else if (isSignatureTrans(transaction)) {
            numSignatureTransEvent++;
        }

        if (priority) {
            priorityTransEvent.add(transaction);
        } else {
            transEvent.add(transaction);
        }

        return true;
    }

    /**
     * get the number of transactions in the transEvent queue
     *
     * @return the number of transactions
     */
    public synchronized int getTransEventSize() {
        return transEvent.size();
    }

    /**
     * get the number of transactions in the priorityTransEvent queue
     *
     * @return the number of transactions
     */
    public synchronized int getPriorityTransEventSize() {
        return priorityTransEvent.size();
    }

    /** return a single string giving the number of transactions in transEvent */
    public synchronized String status() {
        return "transEvent size =" + (transEvent.size() + priorityTransEvent.size());
    }

    /**
     * Clear all the transactions
     */
    public synchronized void clear() {
        transEvent.clear();
        priorityTransEvent.clear();
        numUserTransEvent = 0;
        numSignatureTransEvent = 0;
    }
}
