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

import static com.swirlds.platform.state.SwirldStateManagerUtils.fastCopy;

import com.swirlds.base.time.Time;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.system.NodeId;
import com.swirlds.common.system.Round;
import com.swirlds.common.system.SoftwareVersion;
import com.swirlds.common.system.SwirldDualState;
import com.swirlds.common.system.SwirldState;
import com.swirlds.common.system.address.AddressBook;
import com.swirlds.common.system.status.StatusActionSubmitter;
import com.swirlds.platform.FreezePeriodChecker;
import com.swirlds.platform.components.transaction.system.ConsensusSystemTransactionManager;
import com.swirlds.platform.components.transaction.system.PreconsensusSystemTransactionManager;
import com.swirlds.platform.internal.ConsensusRound;
import com.swirlds.platform.internal.EventImpl;
import com.swirlds.platform.metrics.SwirldStateMetrics;
import com.swirlds.platform.state.signed.LoadableFromSignedState;
import com.swirlds.platform.state.signed.SignedState;
import com.swirlds.platform.uptime.UptimeTracker;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Manages all interactions with the state object required by {@link SwirldState}.
 */
public class SwirldStateManager implements FreezePeriodChecker, LoadableFromSignedState {

    /**
     * Stats relevant to SwirldState operations.
     */
    private final SwirldStateMetrics stats;

    /**
     * reference to the state that reflects all known consensus transactions
     */
    private final AtomicReference<State> stateRef = new AtomicReference<>();

    /**
     * The most recent immutable state. No value until the first fast copy is created.
     */
    private final AtomicReference<State> latestImmutableState = new AtomicReference<>();

    /**
     * Handle transactions by applying them to a state
     */
    private final TransactionHandler transactionHandler;

    /**
     * Tracks and reports node uptime.
     */
    private final UptimeTracker uptimeTracker;

    /**
     * Handles system transactions pre-consensus
     */
    private final PreconsensusSystemTransactionManager preconsensusSystemTransactionManager;

    /**
     * Handles system transactions post-consensus
     */
    private final ConsensusSystemTransactionManager consensusSystemTransactionManager;

    /**
     * The current software version.
     */
    private final SoftwareVersion softwareVersion;

    /**
     * Creates a new instance with the provided state.
     *
     * @param platformContext                      the platform context
     * @param addressBook                          the address book
     * @param selfId                               this node's id
     * @param preconsensusSystemTransactionManager the manager for pre-consensus system transactions
     * @param consensusSystemTransactionManager    the manager for post-consensus system transactions
     * @param swirldStateMetrics                   metrics related to SwirldState
     * @param statusActionSubmitter                enables submitting platform status actions
     * @param state                                the genesis state
     * @param softwareVersion                      the current software version
     */
    public SwirldStateManager(
            @NonNull final PlatformContext platformContext,
            @NonNull final AddressBook addressBook,
            @NonNull final NodeId selfId,
            @NonNull final PreconsensusSystemTransactionManager preconsensusSystemTransactionManager,
            @NonNull final ConsensusSystemTransactionManager consensusSystemTransactionManager,
            @NonNull final SwirldStateMetrics swirldStateMetrics,
            @NonNull final StatusActionSubmitter statusActionSubmitter,
            @NonNull final State state,
            @NonNull final SoftwareVersion softwareVersion) {

        Objects.requireNonNull(platformContext);
        Objects.requireNonNull(addressBook);
        Objects.requireNonNull(selfId);
        this.preconsensusSystemTransactionManager = Objects.requireNonNull(preconsensusSystemTransactionManager);
        this.consensusSystemTransactionManager = Objects.requireNonNull(consensusSystemTransactionManager);
        this.stats = Objects.requireNonNull(swirldStateMetrics);
        Objects.requireNonNull(statusActionSubmitter);
        Objects.requireNonNull(state);
        this.softwareVersion = Objects.requireNonNull(softwareVersion);

        this.transactionHandler = new TransactionHandler(selfId, stats);
        this.uptimeTracker =
                new UptimeTracker(platformContext, addressBook, statusActionSubmitter, selfId, Time.getCurrent());
        initialState(state);
    }

    /**
     * Invokes the pre-handle method. Called after the event has been verified but before
     * {@link #handlePreConsensusEvent(EventImpl)}.
     *
     * @param event
     * 		the event to handle
     */
    public void preHandle(final EventImpl event) {
        final long startTime = System.nanoTime();

        State immutableState = latestImmutableState.get();
        while (!immutableState.tryReserve()) {
            immutableState = latestImmutableState.get();
        }
        transactionHandler.preHandle(event, immutableState.getSwirldState());
        immutableState.release();

        stats.preHandleTime(startTime, System.nanoTime());
    }

    /**
     * Handles an event before it reaches consensus..
     *
     * @param event
     * 		the event to handle
     */
    public void handlePreConsensusEvent(final EventImpl event) {
        final long startTime = System.nanoTime();

        preconsensusSystemTransactionManager.handleEvent(event);

        stats.preConsensusHandleTime(startTime, System.nanoTime());
    }

    /**
     * Handles the events in a consensus round. Implementations are responsible for invoking {@link
     * SwirldState#handleConsensusRound(Round, SwirldDualState)}.
     *
     * @param round
     * 		the round to handle
     */
    public void handleConsensusRound(final ConsensusRound round) {
        final State state = stateRef.get();

        uptimeTracker.handleRound(
                round,
                state.getPlatformDualState().getMutableUptimeData(),
                state.getPlatformState().getAddressBook());
        transactionHandler.handleRound(round, state);
        consensusSystemTransactionManager.handleRound(state, round);
        updateEpoch();
    }

    /**
     * Returns the consensus state. The consensus state could become immutable at any time. Modifications must
     * not be made to the returned state.
     */
    public State getConsensusState() {
        return stateRef.get();
    }

    /**
     * Invoked when a signed state is about to be created for the current freeze period.
     * <p>
     * Invoked only by the consensus handling thread, so there is no chance of the state being modified by a
     * concurrent thread.
     * </p>
     */
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
        final State consState = fastCopy(state, stats, softwareVersion);

        // Set latest immutable first to prevent the newly immutable state from being deleted between setting the
        // stateRef and the latestImmutableState
        setLatestImmutableState(state);
        setState(consState);
    }

    /**
     * Sets the consensus state to the state provided. Must be mutable and have a reference count of at least 1.
     *
     * @param state the new mutable state
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
    public State getStateForSigning() {
        fastCopyAndUpdateRefs(stateRef.get());
        return latestImmutableState.get();
    }
}
