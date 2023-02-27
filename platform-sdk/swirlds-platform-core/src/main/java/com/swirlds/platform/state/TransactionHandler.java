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

package com.swirlds.platform.state;

import static com.swirlds.common.utility.Units.NANOSECONDS_TO_SECONDS;
import static com.swirlds.logging.LogMarker.EXCEPTION;
import static com.swirlds.platform.event.EventUtils.toShortString;
import static com.swirlds.platform.internal.EventImpl.MIN_TRANS_TIMESTAMP_INCR_NANOS;

import com.swirlds.common.system.NodeId;
import com.swirlds.common.system.SwirldDualState;
import com.swirlds.common.system.SwirldState1;
import com.swirlds.common.system.SwirldState2;
import com.swirlds.common.system.events.Event;
import com.swirlds.common.system.transaction.ConsensusTransaction;
import com.swirlds.common.system.transaction.Transaction;
import com.swirlds.platform.internal.ConsensusRound;
import com.swirlds.platform.internal.EventImpl;
import com.swirlds.platform.metrics.SwirldStateMetrics;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Iterator;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class TransactionHandler {

    /** use this for all logging, as controlled by the optional data/log4j2.xml file */
    private static final Logger logger = LogManager.getLogger(TransactionHandler.class);

    /** The id of this node. */
    private final NodeId selfId;

    /** Stats relevant to SwirldState operations. */
    private final SwirldStateMetrics stats;

    public TransactionHandler(final NodeId selfId, final SwirldStateMetrics stats) {
        this.selfId = selfId;
        this.stats = stats;
    }

    /**
     * Applies an event to the {@link SwirldState2#preHandle(Event)} method and handles any
     * exceptions gracefully.
     *
     * @param event
     * 		the event to apply
     * @param swirldState
     * 		the swirld state to apply {@code event} to
     */
    public void preHandle(final EventImpl event, final SwirldState2 swirldState) {
        try {
            swirldState.preHandle(event);
        } catch (final Throwable t) {
            logger.error(
                    EXCEPTION.getMarker(),
                    "error invoking SwirldState2.preHandle() [ nodeId = {} ] with event {}",
                    selfId.getId(),
                    event.toMediumString(),
                    t);
        }
    }

    /**
     * Applies all transaction in an event to the {@link SwirldState1#preHandle(Transaction)} method and handles any
     * exceptions gracefully.
     *
     * @param event
     * 		the event with transactions to apply
     * @param swirldState
     * 		the swirld state to apply {@code event} to
     */
    public void preHandle(final Event event, final SwirldState1 swirldState) {
        for (final Iterator<Transaction> it = event.transactionIterator(); it.hasNext(); ) {
            final Transaction transaction = it.next();
            preHandle(transaction, swirldState);
        }
    }

    /**
     * Applies a transaction to the {@link SwirldState1#preHandle(Transaction)} method and handles any
     * exceptions gracefully.
     *
     * @param transaction
     * 		the transaction to apply
     * @param swirldState
     * 		the swirld state to apply {@code event} to
     */
    public void preHandle(final Transaction transaction, final SwirldState1 swirldState) {
        try {
            swirldState.preHandle(transaction);
        } catch (final Throwable t) {
            logger.error(
                    EXCEPTION.getMarker(),
                    "error invoking SwirldState1.preHandle() [ nodeId = {} ] with transaction {}",
                    selfId.getId(),
                    transaction,
                    t);
        }
    }

    /**
     * Applies an event to {@link SwirldState1} as a pre-consensus event and handles any exceptions gracefully.
     *
     * @param swirldState
     * 		the swirld state to apply the transactions to
     * @param dualState
     * 		the dual state associated with {@code swirldState}
     * @param event
     * 		the event to apply
     */
    public void handlePreConsensusEvent(
            final SwirldState1 swirldState, final SwirldDualState dualState, final EventImpl event) {

        if (event.isEmpty()) {
            return;
        }

        // the creator of the event containing this transaction
        final long creatorId = event.getCreatorId();

        // The claimed creation time of the event holding this transaction
        final Instant timeCreated = event.getTimeCreated();

        // The actual (or estimated, if not available) consensus time of this event
        final Instant consTime = event.getEstimatedTime();

        final ConsensusTransaction[] transactions = event.getTransactions();

        for (int i = 0; i < transactions.length; i++) {
            if (transactions[i].isSystem()) {
                continue;
            }

            final Instant transConsTime = consTime.plusNanos(i * MIN_TRANS_TIMESTAMP_INCR_NANOS);
            try {
                swirldState.handleTransaction(creatorId, timeCreated, transConsTime, transactions[i], dualState);
            } catch (final Throwable t) {
                logger.error(
                        EXCEPTION.getMarker(),
                        "error invoking SwirldState.handlePreConsensusEvent() [ nodeId = {} ] with event {}",
                        selfId.getId(),
                        toShortString(event),
                        t);
            }
        }
    }

    /**
     * Applies a consensus round to SwirldState, handles any exceptions gracefully, and updates relevant statistics.
     *
     * @param round
     * 		the round to apply
     * @param state
     * 		the state to apply {@code round} to
     */
    public void handleRound(final ConsensusRound round, final State state) {
        try {
            final Instant timeOfHandle = Instant.now();
            final long startTime = System.nanoTime();

            state.getSwirldState().handleConsensusRound(round, state.getSwirldDualState());

            final double secondsElapsed = (System.nanoTime() - startTime) * NANOSECONDS_TO_SECONDS;

            // Avoid dividing by zero
            if (round.getNumAppTransactions() == 0) {
                stats.consensusTransHandleTime(secondsElapsed);
            } else {
                stats.consensusTransHandleTime(secondsElapsed / round.getNumAppTransactions());
            }
            stats.consensusTransHandled(round.getNumAppTransactions());

            for (final EventImpl event : round.getConsensusEvents()) {
                // events being played back from stream file do not have reachedConsTimestamp set,
                // since reachedConsTimestamp is not serialized and saved to stream file
                if (event.getReachedConsTimestamp() != null) {
                    stats.consensusToHandleTime(event.getReachedConsTimestamp().until(timeOfHandle, ChronoUnit.NANOS)
                            * NANOSECONDS_TO_SECONDS);
                }
            }
        } catch (final Throwable t) {
            logger.error(
                    EXCEPTION.getMarker(),
                    "error invoking SwirldState.handleConsensusRound() [ nodeId = {} ] with round {}",
                    selfId.getId(),
                    round.getRoundNum(),
                    t);
        }
    }

    /**
     * <p>Applies transactions to a state. These transactions have no event context and have not reached consensus.</p>
     *
     * @param numTransSupplier
     * 		a supplier for the number of transactions the {@code transSupplier} can provide
     * @param transSupplier
     * 		the supplier of transactions to apply
     * @param swirldState
     * 		the swirld state to apply the transactions to
     * @param dualState
     * 		the dual state associated with {@code swirldState}
     */
    public void handleTransactions(
            final IntSupplier numTransSupplier,
            final Supplier<ConsensusTransaction> transSupplier,
            final Supplier<Instant> consEstimateSupplier,
            final SwirldState1 swirldState,
            final SwirldDualState dualState) {

        final int numTrans = numTransSupplier.getAsInt();

        if (numTrans <= 0) {
            return;
        }

        // the timestamp that we estimate the transactions will have after
        // being put into an event and having consensus reached on them
        final Instant baseTime = consEstimateSupplier.get();

        for (int i = 0; i < numTrans; i++) {
            // This call must acquire a lock
            final ConsensusTransaction trans = transSupplier.get();
            if (trans == null) {
                // this shouldn't be necessary, but it's here just for safety
                break;
            }

            final Instant transConsTime = baseTime.plusNanos(i * MIN_TRANS_TIMESTAMP_INCR_NANOS);

            if (!trans.isSystem()) {
                handleSelfTransaction(swirldState, dualState, transConsTime, trans);
            }
        }
    }

    /**
     * Applies a single pre-consensus self transaction to {@link SwirldState1} and handles any exceptions gracefully.
     *
     * @param swirldState
     * 		the swirld state to apply the transactions to
     * @param dualState
     * 		the dual state associated with {@code swirldState}
     * @param consTime
     * 		the estimated consensus time of the transaction
     * @param transaction
     * 		the transaction to apply
     */
    private void handleSelfTransaction(
            final SwirldState1 swirldState,
            final SwirldDualState dualState,
            final Instant consTime,
            final Transaction transaction) {
        try {
            swirldState.handleTransaction(selfId.getId(), Instant.now(), consTime, transaction, dualState);
        } catch (final Throwable t) {
            logger.error(
                    EXCEPTION.getMarker(),
                    "error invoking SwirldState.handleTransaction() [ nodeId = {} ] with transaction {}",
                    selfId.getId(),
                    transaction,
                    t);
        }
    }
}
