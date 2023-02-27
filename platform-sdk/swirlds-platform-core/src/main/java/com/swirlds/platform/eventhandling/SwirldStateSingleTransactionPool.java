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

import static com.swirlds.logging.LogMarker.EXCEPTION;

import com.swirlds.common.system.transaction.ConsensusTransaction;
import com.swirlds.common.system.transaction.internal.ConsensusTransactionImpl;
import com.swirlds.platform.SettingsProvider;
import java.util.LinkedList;
import java.util.function.BooleanSupplier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Store additional lists of transactions used to keep the three states used by {@link
 * com.swirlds.platform.state.SwirldStateManagerSingle} up to date with self transactions.
 */
public class SwirldStateSingleTransactionPool extends EventTransactionPool {
    /** use this for all logging, as controlled by the optional data/log4j2.xml file */
    private static final Logger logger = LogManager.getLogger(SwirldStateSingleTransactionPool.class);

    /** list of transactions by self waiting to be handled by doCurr */
    private volatile LinkedList<ConsensusTransaction> transCurr = new LinkedList<>();
    /** list of transactions by self waiting to be handled by doWork */
    private volatile LinkedList<ConsensusTransaction> transWork = new LinkedList<>();
    /** list of transactions by self waiting to be handled by doCons (which just passes them on) */
    private final LinkedList<ConsensusTransaction> transCons = new LinkedList<>();

    /**
     * Creates a new transaction pool for tracking all transactions waiting to be put into an event or to be applied
     * to a state.
     *
     * @param settings
     * 		settings to use
     * @param inFreeze
     * 		Indicates if the system is currently in a freeze
     */
    public SwirldStateSingleTransactionPool(final SettingsProvider settings, final BooleanSupplier inFreeze) {
        super(settings, inFreeze);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public synchronized boolean submitTransaction(final ConsensusTransactionImpl transaction, final boolean priority) {
        final int t = settings.getThrottleTransactionQueueSize();

        // In order to be submitted, the transaction must be a system transaction, or all the queues must have room.
        if (!transaction.isSystem()
                && (transEvent.size() + priorityTransEvent.size() > t
                        || transCurr.size() > t
                        || transCons.size() > t
                        || transWork.size() > t)) {
            return false;
        }

        // this should stay true. The linked lists will never be full or return false.
        final boolean ans = super.submitTransaction(transaction, priority)
                && transCurr.offer(transaction)
                && transCons.offer(transaction)
                && transWork.offer(transaction);

        if (!ans) {
            logger.error(
                    EXCEPTION.getMarker(),
                    "SwirldStateSingleTransactionPool queues were all shorter than Settings"
                            + ".throttleTransactionQueueSize, yet offer returned false");
        }
        return ans; // this will be true, unless a bad error has occurred
    }

    /** remove and return the earliest-added event in transCurr, or null if none */
    public synchronized ConsensusTransaction pollCurr() {
        return transCurr.poll();
    }

    /** remove and return the earliest-added event in transWork, or null if none */
    public synchronized ConsensusTransaction pollWork() {
        return transWork.poll();
    }

    /** remove and return the earliest-added event in transCons, or null if none */
    public synchronized ConsensusTransaction pollCons() {
        return transCons.poll();
    }

    /**
     * get the number of transactions in the transCurr queue (to be handled by stateCurr)
     *
     * @return the number of transactions
     */
    public synchronized int getCurrSize() {
        return transCurr.size();
    }

    /**
     * get the number of transactions in the transWork queue (to be handled by stateWork)
     *
     * @return the number of transactions
     */
    public synchronized int getWorkSize() {
        return transWork.size();
    }

    /**
     * get the number of transactions in the transCons queue
     *
     * @return the number of transactions
     */
    public synchronized int getConsSize() {
        return transCons.size();
    }

    /** Do a shuffle: discard transCurr, move transWork to transCurr, clone transCons to transWork */
    @SuppressWarnings("unchecked") // needed because stupid Java type erasure gives no alternative
    public synchronized void shuffle() {
        transCurr = transWork;
        transWork = (LinkedList<ConsensusTransaction>) transCons.clone();
    }

    /**
     * return a single string giving the number of transactions in each list
     */
    @Override
    public synchronized String status() {
        return "SwirldStateSingleTransactionPool sizes:"
                + "transEvent=" + transEvent.size()
                + " transCurr=" + transCurr.size()
                + " transWork=" + transWork.size()
                + " transCons=" + transCons.size();
    }

    /**
     * Clear all the transactions from SwirldStateSingleTransactionPool
     */
    @Override
    public synchronized void clear() {
        super.clear();
        transCurr.clear();
        transWork.clear();
        transCons.clear();
    }
}
