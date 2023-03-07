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

package com.swirlds.platform.state;

import com.swirlds.common.system.Round;
import com.swirlds.common.system.SwirldDualState;
import com.swirlds.common.system.SwirldState;
import com.swirlds.common.system.transaction.internal.ConsensusTransactionImpl;
import com.swirlds.common.threading.framework.Stoppable;
import com.swirlds.common.threading.interrupt.InterruptableRunnable;
import com.swirlds.common.utility.Clearable;
import com.swirlds.platform.FreezePeriodChecker;
import com.swirlds.platform.components.transaction.throttle.TransThrottleSyncAndCreateRule;
import com.swirlds.platform.eventhandling.EventTransactionPool;
import com.swirlds.platform.internal.ConsensusRound;
import com.swirlds.platform.internal.EventImpl;
import com.swirlds.platform.state.signed.LoadableFromSignedState;

/**
 * The methods used to interact with instances of {@link SwirldState}.
 */
public interface SwirldStateManager
        extends FreezePeriodChecker, TransThrottleSyncAndCreateRule, Clearable, LoadableFromSignedState {

    /**
     * Invokes the pre-handle method. Called after the event has been verified but before
     * {@link #handlePreConsensusEvent(EventImpl)}.
     *
     * @param event
     * 		the event to handle
     */
    void preHandle(final EventImpl event);

    /**
     * Handles an event before it reaches consensus..
     *
     * @param event
     * 		the event to handle
     */
    void handlePreConsensusEvent(final EventImpl event);

    /**
     * Provides a {@link Runnable} to execute while waiting for pre-consensus events (q1) to process.
     *
     * @return the runnable
     */
    default InterruptableRunnable getPreConsensusWaitForWorkRunnable() {
        // tells QueueThread to execute its default method when there is nothing in the queue to process
        return null;
    }

    /**
     * Provides a {@link Runnable} to execute while waiting for consensus events (q2) to process.
     *
     * @return the runnable
     */
    default InterruptableRunnable getConsensusWaitForWorkRunnable() {
        // tells QueueThread to execute its default method when there is nothing in the queue to process
        return null;
    }

    /**
     * Determines if a pre-consensus event should be discarded or added to the pre-consensus queue (q1) for
     * processing.
     *
     * @param event
     * 		the event to discard or not
     * @return true if the event should be discarded, false otherwise
     */
    default boolean discardPreConsensusEvent(final EventImpl event) {
        return false;
    }

    /**
     * Provides the transaction pool used to store transactions submitted by this node.
     *
     * @return the transaction pool
     */
    EventTransactionPool getTransactionPool();

    /**
     * Handles the events in a consensus round. Implementations are responsible for invoking {@link
     * SwirldState#handleConsensusRound(Round, SwirldDualState)}.
     *
     * @param round
     * 		the round to handle
     */
    void handleConsensusRound(final ConsensusRound round);

    /**
     * <p>Updates the state to a fast copy of itself and returns a reference to the previous state to be used for
     * signing. The reference count of the previous state returned by this is incremented to prevent it from being
     * garbage collected until it is put in a signed state, so callers are responsible for decrementing the
     * reference count when it is no longer needed.</p>
     *
     * <p>Consensus event handling will block until this method returns. Pre-consensus
     * event handling may or may not be blocked depending on the implementation.</p>
     *
     * @return a copy of the state to use for the next signed state
     * @see State#copy()
     */
    State getStateForSigning();

    /**
     * Invoked when a signed state is about to be created for the current freeze period.
     * <p>
     * Invoked only by the consensus handling thread, so there is no chance of the state being modified by a
     * concurrent thread.
     * </p>
     */
    void savedStateInFreezePeriod();

    /**
     * Return the current state of the app. It changes frequently, so this needs to be called frequently. This
     * method also guarantees that the state will not be deleted until {@link #releaseCurrentSwirldState()} is invoked.
     *
     * @return the current app state
     */
    SwirldState getCurrentSwirldState();

    /**
     * Returns the consensus state. The consensus state could become immutable at any time. Modifications must
     * not be made to the returned state.
     */
    State getConsensusState();

    /**
     * Releases the state that was previously returned, so that another one can be obtained from {@link
     * #getCurrentSwirldState()}, and deletes it if it's not the current state being used.
     */
    default void releaseCurrentSwirldState() {
        // default is NO-OP
    }

    /**
     * <p>Submits a self transaction for any necessary processing separate from the transaction's propagation to the
     * network. A transaction must only be submitted here if it is also submitted for network propagation in {@link
     * EventTransactionPool}.</p>
     *
     * @param transaction
     * 		the transaction to submit
     */
    default boolean submitTransaction(final ConsensusTransactionImpl transaction) {
        return submitTransaction(transaction, false);
    }

    /**
     * Submits a self transaction (i.e. a transaction created by this node and put into a self event).
     * Implementations must submit this transaction for network propagation in {@link EventTransactionPool}.
     *
     * @param transaction
     * 		the transaction to submit
     * @param priority
     * 		if true, then this transaction will be added to a future event before other
     * 		non-priority transactions
     */
    boolean submitTransaction(ConsensusTransactionImpl transaction, boolean priority);

    /**
     * Called during recovery. Updates the dual state status to clear any possible inconsistency between freezeTime
     * and lastFrozenTime.
     */
    void clearFreezeTimes();

    /**
     * Gets the stop behavior of the threads applying transactions to the state
     *
     * @return the type of stop behavior of the threads applying transactions to the state
     */
    default Stoppable.StopBehavior getStopBehavior() {
        return Stoppable.StopBehavior.BLOCKING;
    }
}
