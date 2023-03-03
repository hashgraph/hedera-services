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

import static com.swirlds.logging.LogMarker.RECONNECT;
import static com.swirlds.platform.state.SwirldStateManagerUtils.fastCopy;

import com.swirlds.common.system.NodeId;
import com.swirlds.common.system.SwirldState;
import com.swirlds.common.system.SwirldState2;
import com.swirlds.common.system.transaction.internal.ConsensusTransactionImpl;
import com.swirlds.platform.SettingsProvider;
import com.swirlds.platform.components.transaction.system.PostConsensusSystemTransactionManager;
import com.swirlds.platform.components.transaction.system.PreConsensusSystemTransactionManager;
import com.swirlds.platform.components.transaction.throttle.TransThrottleSyncAndCreateRuleResponse;
import com.swirlds.platform.eventhandling.EventTransactionPool;
import com.swirlds.platform.internal.ConsensusRound;
import com.swirlds.platform.internal.EventImpl;
import com.swirlds.platform.metrics.SwirldStateMetrics;
import com.swirlds.platform.state.signed.SignedState;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * <p>Manages all interactions with the state object required by {@link SwirldState2}.</p>
 *
 * <p>Two threads interact with states in this class: pre-consensus event handler and consensus event handler.
 * Transactions are submitted by a different thread. Other threads can access the states by calling
 * {@link #getCurrentSwirldState()} and {@link #getConsensusState()}. Sync threads access state to check if there is
 * an active freeze period. Careful attention must be paid to changes in this class regarding locking and
 * synchronization in this class and its utility classes.</p>
 */
public class SwirldStateManagerDouble implements SwirldStateManager {

    /** use this for all logging, as controlled by the optional data/log4j2.xml file */
    private static final Logger logger = LogManager.getLogger(SwirldStateManagerDouble.class);

    /** Stats relevant to SwirldState operations. */
    private final SwirldStateMetrics stats;

    /** reference to the state that reflects all known consensus transactions */
    private final AtomicReference<State> stateRef = new AtomicReference<>();

    /** The most recent immutable state. No value until the first fast copy is created. */
    private final AtomicReference<State> latestImmutableState = new AtomicReference<>();

    /** Contains self transactions to be included in the next event. */
    private final EventTransactionPool transactionPool;

    /** Handle transactions by applying them to a state */
    private final TransactionHandler transactionHandler;

    /**
     * Handles system transactions pre-consensus
     */
    private final PreConsensusSystemTransactionManager preConsensusSystemTransactionManager;

    /**
     * Handles system transactions post-consensus
     */
    private final PostConsensusSystemTransactionManager postConsensusSystemTransactionManager;

    // Used for creating mock instances in unit testing
    public SwirldStateManagerDouble() {
        stats = null;
        transactionPool = null;
        preConsensusSystemTransactionManager = null;
        postConsensusSystemTransactionManager = null;
        transactionHandler = null;
    }

    /**
     * Creates a new instance with the provided state.
     *
     * @param selfId
     * 		this node's id
     * @param preConsensusSystemTransactionManager
     * 		the manager for pre-consensus system transactions
     * @param postConsensusSystemTransactionManager
     * 		the manager for post-consensus system transactions
     * @param swirldStateMetrics
     * 		metrics related to SwirldState
     * @param settings
     * 		a static settings provider
     * @param inFreeze
     * 		indicates if the system is currently in a freeze
     * @param state
     * 		the genesis state
     */
    public SwirldStateManagerDouble(
            final NodeId selfId,
            final PreConsensusSystemTransactionManager preConsensusSystemTransactionManager,
            final PostConsensusSystemTransactionManager postConsensusSystemTransactionManager,
            final SwirldStateMetrics swirldStateMetrics,
            final SettingsProvider settings,
            final BooleanSupplier inFreeze,
            final State state) {

        this.preConsensusSystemTransactionManager = preConsensusSystemTransactionManager;
        this.postConsensusSystemTransactionManager = postConsensusSystemTransactionManager;
        this.stats = swirldStateMetrics;
        this.transactionPool = new EventTransactionPool(settings, inFreeze);
        this.transactionHandler = new TransactionHandler(selfId, stats);
        initialState(state);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean submitTransaction(final ConsensusTransactionImpl transaction, final boolean priority) {
        return transactionPool.submitTransaction(transaction, priority);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void preHandle(final EventImpl event) {
        final long startTime = System.nanoTime();

        State immutableState = latestImmutableState.get();
        while (!immutableState.tryReserve()) {
            immutableState = latestImmutableState.get();
        }
        transactionHandler.preHandle(event, (SwirldState2) immutableState.getSwirldState());
        immutableState.release();

        stats.preHandleTime(startTime, System.nanoTime());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void handlePreConsensusEvent(final EventImpl event) {
        final long startTime = System.nanoTime();

        preConsensusSystemTransactionManager.handleEvent(event);

        stats.preConsensusHandleTime(startTime, System.nanoTime());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void handleConsensusRound(final ConsensusRound round) {
        transactionHandler.handleRound(round, stateRef.get());
        postConsensusSystemTransactionManager.handleRound(stateRef.get(), round);
        updateEpoch();
    }

    /**
     * {@inheritDoc}
     */
    public SwirldState getCurrentSwirldState() {
        return stateRef.get().getSwirldState();
    }

    /**
     * IMPORTANT: this method is for unit testing purposes only. The returned state may be deleted at any time while the
     * caller is using it.
     * <p>
     * Returns the most recent immutable state.
     *
     * @return latest immutable state
     */
    public State getLatestImmutableState() {
        return latestImmutableState.get();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public State getConsensusState() {
        return stateRef.get();
    }

    /**
     * {@inheritDoc}
     * <p>
     * NOTE: This will only clear current transactions, it will not prevent new transactions from being added while
     * clear is being called
     */
    @Override
    public void clear() {
        // clear the transactions
        logger.info(RECONNECT.getMarker(), "SwirldStateManagerDouble: clearing transactionPool");
        transactionPool.clear();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void savedStateInFreezePeriod() {
        // set current DualState's lastFrozenTime to be current freezeTime
        stateRef.get().getPlatformDualState().setLastFrozenTimeToBeCurrentFreezeTime();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void loadFromSignedState(final SignedState signedState) {
        final State state = signedState.getState();

        state.throwIfDestroyed("state must not be destroyed");
        state.throwIfImmutable("state must be mutable");

        fastCopyAndUpdateRefs(state);
    }

    private void initialState(final State state) {
        state.throwIfDestroyed("state must not be destroyed");
        state.throwIfImmutable("state must be mutable");

        if (stateRef.get() != null) {
            throw new IllegalStateException("Attempt to set initial state when there is already a state reference.");
        }

        // Create a fast copy so there is always an immutable state to
        // invoke handleTransaction on for pre-consensus transactions
        fastCopyAndUpdateRefs(state);
    }

    private void fastCopyAndUpdateRefs(final State state) {
        final State consState = fastCopy(state, stats);

        // Set latest immutable first to prevent the newly immutable state from being deleted between setting the
        // stateRef and the latestImmutableState
        setLatestImmutableState(state);
        setState(consState);
    }

    /**
     * Sets the consensus state to the state provided. Must be mutable and have a reference count of at least 1.
     *
     * @param state
     * 		the new mutable state
     */
    private void setState(final State state) {
        final State currVal = stateRef.get();
        if (currVal != null) {
            currVal.release();
        }
        // Do not increment the reference count because the state provided already has a reference count of at least
        // one to represent this reference and to prevent it from being deleted before this reference is set.
        stateRef.set(state);
    }

    private void setLatestImmutableState(final State immutableState) {
        final State currVal = latestImmutableState.get();
        if (currVal != null) {
            currVal.release();
        }
        immutableState.reserve();
        latestImmutableState.set(immutableState);
    }

    /**
     * {@inheritDoc}
     *
     * Only invoked during state recovery.
     */
    @Override
    public void clearFreezeTimes() {
        // It is possible, though unlikely, that this operation is executed multiple times. Each failed attempt will
        // leak a state, but since this is only called during recovery after which the node shuts down, it is
        // acceptable. This leak will be eliminated with ticket swirlds/swirlds-platform/issues/5256.
        stateRef.getAndUpdate(s -> {
            s.getPlatformDualState().setFreezeTime(null);
            s.getPlatformDualState().setLastFrozenTimeToBeCurrentFreezeTime();
            return s;
        });
    }

    private void updateEpoch() {
        final PlatformState platformState = stateRef.get().getPlatformState();
        if (platformState != null) {
            platformState.getPlatformData().updateEpochHash();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isInFreezePeriod(final Instant timestamp) {
        return SwirldStateManagerUtils.isInFreezePeriod(timestamp, getConsensusState());
    }

    /**
     * {@inheritDoc}
     *
     * Only invoked by the consensus handler thread
     */
    @Override
    public State getStateForSigning() {
        fastCopyAndUpdateRefs(stateRef.get());
        return latestImmutableState.get();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public TransThrottleSyncAndCreateRuleResponse shouldSyncAndCreate() {
        return SwirldStateManagerUtils.shouldSyncAndCreate(getConsensusState());
    }

    /**
     * {@inheritDoc}
     */
    public EventTransactionPool getTransactionPool() {
        return transactionPool;
    }
}
