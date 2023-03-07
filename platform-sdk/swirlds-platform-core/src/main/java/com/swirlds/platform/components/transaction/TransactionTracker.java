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

package com.swirlds.platform.components.transaction;

import com.swirlds.platform.internal.ConsensusRound;
import com.swirlds.platform.internal.EventImpl;
import com.swirlds.platform.observers.ConsensusRoundObserver;
import com.swirlds.platform.observers.EventAddedObserver;
import com.swirlds.platform.observers.StaleEventObserver;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Tracks user transactions that have not reached consensus, as well as the round at which the last one reached
 * consensus.
 */
public class TransactionTracker implements EventAddedObserver, ConsensusRoundObserver, StaleEventObserver {
    /**
     * the number of non-consensus, non-stale events with user transactions currently in the hashgraph
     */
    private final AtomicLong numUserTransEvents;

    /**
     * the last round received with at least 1 user transaction
     */
    private volatile long lastRRwithUserTransaction;

    /**
     * the last round received after which there were no non consensus user transactions in the hashgraph, but before
     * this round there were
     */
    private volatile long lastRoundReceivedAllTransCons;

    /**
     * Default-construct a {@code TransactionTracker} instance. The resulting instance
     * contains no transaction information, and returns a round number of -1.
     */
    public TransactionTracker() {
        numUserTransEvents = new AtomicLong(0);
        reset();
    }

    /**
     * Reset this instance to its constructed state.
     */
    public void reset() {
        numUserTransEvents.set(0);
        lastRoundReceivedAllTransCons = -1;
        lastRRwithUserTransaction = -1;
    }

    /**
     * Notifies that tracker that an event is being added to the hashgraph
     *
     * @param event
     * 		the event being added
     */
    @Override
    public void eventAdded(final EventImpl event) {
        // check if this event has user transactions, if it does, increment the counter
        if (event.hasUserTransactions()) {
            numUserTransEvents.incrementAndGet();
        }
    }

    @Override
    public void consensusRound(final ConsensusRound consensusRound) {
        for (EventImpl event : consensusRound.getConsensusEvents()) {
            // check if this event has user transactions, if it does, decrement the counter
            if (event.hasUserTransactions()) {
                numUserTransEvents.decrementAndGet();
                lastRRwithUserTransaction = event.getRoundReceived();
                // we decrement the numUserTransEvents for every event that has user transactions. If the counter
                // reaches 0, we keep this value of round received
                if (numUserTransEvents.get() == 0) {
                    lastRoundReceivedAllTransCons = lastRRwithUserTransaction;
                }
            }
        }
    }

    /**
     * Notifies that tracker that an event has been declared stale
     *
     * @param event
     * 		the event being added
     */
    @Override
    public void staleEvent(final EventImpl event) {
        if (event.hasUserTransactions()) {
            numUserTransEvents.decrementAndGet();
            if (numUserTransEvents.get() == 0) {
                lastRoundReceivedAllTransCons = lastRRwithUserTransaction;
            }
        }
    }

    /**
     * @return the number of events with user transactions currently in the hashgraph that have not reached consensus
     */
    public long getNumUserTransEvents() {
        return numUserTransEvents.get();
    }

    /**
     * @return the last round received after which there were no non consensus user transactions in the hashgraph
     */
    public long getLastRoundReceivedAllTransCons() {
        return lastRoundReceivedAllTransCons;
    }

    /**
     * Update the last round-received which included at least one user (non-system) transaction.
     *
     * @param lastRRwithUserTransaction
     * 		the new value of the last such round
     */
    public void setLastRRwithUserTransaction(final long lastRRwithUserTransaction) {
        this.lastRRwithUserTransaction = lastRRwithUserTransaction;
    }
}
