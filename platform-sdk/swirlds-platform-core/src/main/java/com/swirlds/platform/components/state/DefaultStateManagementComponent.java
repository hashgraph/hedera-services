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

package com.swirlds.platform.components.state;

import com.swirlds.base.time.Time;
import com.swirlds.common.config.StateConfig;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.threading.manager.ThreadManager;
import com.swirlds.platform.components.SavedStateController;
import com.swirlds.platform.components.common.output.FatalErrorConsumer;
import com.swirlds.platform.components.state.output.NewLatestCompleteStateConsumer;
import com.swirlds.platform.dispatch.DispatchBuilder;
import com.swirlds.platform.dispatch.triggers.flow.StateHashedTrigger;
import com.swirlds.platform.state.signed.ReservedSignedState;
import com.swirlds.platform.state.signed.SignedState;
import com.swirlds.platform.state.signed.SignedStateGarbageCollector;
import com.swirlds.platform.state.signed.SignedStateHasher;
import com.swirlds.platform.state.signed.SignedStateInfo;
import com.swirlds.platform.state.signed.SignedStateManager;
import com.swirlds.platform.state.signed.SignedStateMetrics;
import com.swirlds.platform.state.signed.SignedStateSentinel;
import com.swirlds.platform.state.signed.SourceOfSignedState;
import com.swirlds.platform.util.HashLogger;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * The default implementation of {@link StateManagementComponent}.
 */
public class DefaultStateManagementComponent implements StateManagementComponent {

    /**
     * Signed states are deleted on this background thread.
     */
    private final SignedStateGarbageCollector signedStateGarbageCollector;

    /**
     * Hashes SignedStates.
     */
    private final SignedStateHasher signedStateHasher;

    /**
     * Keeps track of various signed states in various stages of collecting signatures
     */
    private final SignedStateManager signedStateManager;

    /**
     * A logger for hash stream data
     */
    private final HashLogger hashLogger;

    /**
     * Used to track signed state leaks, if enabled
     */
    private final SignedStateSentinel signedStateSentinel;

    private final SavedStateController savedStateController;

    private final Consumer<ReservedSignedState> stateSigner;

    /**
     * @param platformContext                    the platform context
     * @param threadManager                      manages platform thread resources
     * @param dispatchBuilder                    builds dispatchers. This is deprecated, do not wire new things together
     *                                           with this.
     * @param newLatestCompleteStateConsumer     consumer to invoke when there is a new latest complete signed state
     * @param fatalErrorConsumer                 consumer to invoke when a fatal error has occurred
     * @param savedStateController               controls which states are saved to disk
     */
    public DefaultStateManagementComponent(
            @NonNull final PlatformContext platformContext,
            @NonNull final ThreadManager threadManager,
            @NonNull final DispatchBuilder dispatchBuilder,
            @NonNull final NewLatestCompleteStateConsumer newLatestCompleteStateConsumer,
            @NonNull final FatalErrorConsumer fatalErrorConsumer,
            @NonNull final SavedStateController savedStateController,
            @NonNull final Consumer<ReservedSignedState> stateSigner) {

        Objects.requireNonNull(platformContext);
        Objects.requireNonNull(threadManager);
        Objects.requireNonNull(newLatestCompleteStateConsumer);
        Objects.requireNonNull(fatalErrorConsumer);

        // Various metrics about signed states
        final SignedStateMetrics signedStateMetrics = new SignedStateMetrics(platformContext.getMetrics());
        this.signedStateGarbageCollector = new SignedStateGarbageCollector(threadManager, signedStateMetrics);
        this.signedStateSentinel = new SignedStateSentinel(platformContext, threadManager, Time.getCurrent());
        this.savedStateController = Objects.requireNonNull(savedStateController);
        this.stateSigner = Objects.requireNonNull(stateSigner);

        hashLogger =
                new HashLogger(threadManager, platformContext.getConfiguration().getConfigData(StateConfig.class));

        final StateHashedTrigger stateHashedTrigger =
                dispatchBuilder.getDispatcher(this, StateHashedTrigger.class)::dispatch;
        signedStateHasher = new SignedStateHasher(signedStateMetrics, stateHashedTrigger, fatalErrorConsumer);

        signedStateManager = new SignedStateManager(
                platformContext.getConfiguration().getConfigData(StateConfig.class),
                signedStateMetrics,
                newLatestCompleteStateConsumer,
                this::stateHasEnoughSignatures,
                this::stateLacksSignatures);
    }

    /**
     * Handles a signed state that is now complete by saving it to disk, if it should be saved.
     *
     * @param signedState the newly complete signed state
     */
    private void stateHasEnoughSignatures(@NonNull final SignedState signedState) {
        savedStateController.maybeSaveState(signedState);
    }

    /**
     * Handles a signed state that did not collect enough signatures before being ejected from memory.
     *
     * @param signedState the signed state that lacks signatures
     */
    private void stateLacksSignatures(@NonNull final SignedState signedState) {
        savedStateController.maybeSaveState(signedState);
    }

    private void newSignedStateBeingTracked(final SignedState signedState, final SourceOfSignedState source) {
        // When we begin tracking a new signed state, "introduce" the state to the SignedStateFileManager
        if (source == SourceOfSignedState.DISK) {
            savedStateController.registerSignedStateFromDisk(signedState);
        }
        if (source == SourceOfSignedState.RECONNECT) {
            // a state received from reconnect should be saved to disk
            savedStateController.reconnectStateReceived(signedState);
        }

        if (signedState.getState().getHash() != null) {
            hashLogger.logHashes(signedState);
        }
    }

    @Override
    public void newSignedStateFromTransactions(@NonNull final ReservedSignedState signedState) {
        try (signedState) {
            signedState.get().setGarbageCollector(signedStateGarbageCollector);
            signedStateHasher.hashState(signedState.get());

            newSignedStateBeingTracked(signedState.get(), SourceOfSignedState.TRANSACTIONS);

            stateSigner.accept(signedState.getAndReserve("signing state from transactions"));

            signedStateManager.addState(signedState.get());
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<SignedStateInfo> getSignedStateInfo() {
        return signedStateManager.getSignedStateInfo();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void stateToLoad(final SignedState signedState, final SourceOfSignedState sourceOfSignedState) {
        signedState.setGarbageCollector(signedStateGarbageCollector);
        newSignedStateBeingTracked(signedState, sourceOfSignedState);
        signedStateManager.addState(signedState);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void start() {
        signedStateGarbageCollector.start();
        signedStateSentinel.start();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void stop() {
        signedStateSentinel.stop();
        signedStateGarbageCollector.stop();
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public SignedStateManager getSignedStateManager() {
        return signedStateManager;
    }
}
