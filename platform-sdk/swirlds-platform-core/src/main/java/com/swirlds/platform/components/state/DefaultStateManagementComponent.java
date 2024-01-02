/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

    private final Consumer<ReservedSignedState> stateFileManager;

    private final Consumer<ReservedSignedState> stateSigner;

    /**
     * @param platformContext                    the platform context
     * @param threadManager                      manages platform thread resources
     * @param dispatchBuilder                    builds dispatchers. This is deprecated, do not wire new things together
     *                                           with this.
     * @param newLatestCompleteStateConsumer     consumer to invoke when there is a new latest complete signed state
     * @param fatalErrorConsumer                 consumer to invoke when a fatal error has occurred
     * @param stateFileManager                   writes states to disk
     */
    public DefaultStateManagementComponent(
            @NonNull final PlatformContext platformContext,
            @NonNull final ThreadManager threadManager,
            @NonNull final DispatchBuilder dispatchBuilder,
            @NonNull final NewLatestCompleteStateConsumer newLatestCompleteStateConsumer,
            @NonNull final FatalErrorConsumer fatalErrorConsumer,
            @NonNull final Consumer<ReservedSignedState> stateFileManager,
            @NonNull final Consumer<ReservedSignedState> stateSigner) {

        Objects.requireNonNull(platformContext);
        Objects.requireNonNull(threadManager);
        Objects.requireNonNull(newLatestCompleteStateConsumer);
        Objects.requireNonNull(fatalErrorConsumer);

        // Various metrics about signed states
        final SignedStateMetrics signedStateMetrics = new SignedStateMetrics(platformContext.getMetrics());
        this.signedStateGarbageCollector = new SignedStateGarbageCollector(threadManager, signedStateMetrics);
        this.signedStateSentinel = new SignedStateSentinel(platformContext, threadManager, Time.getCurrent());
        this.stateFileManager = Objects.requireNonNull(stateFileManager);
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
                this::signatureCollectionDone,
                this::signatureCollectionDone);
    }

    /**
     * Signature for a signed state is now done. We should save it to disk, if it should be saved. The state may or may
     * not have all its signatures collected.
     *
     * @param signedState the newly complete signed state
     */
    private void signatureCollectionDone(@NonNull final SignedState signedState) {
        if (signedState.isStateToSave()) {
            stateFileManager.accept(signedState.reserve("save to disk"));
        }
    }

    private void logHashes(final SignedState signedState) {
        if (signedState.getState().getHash() != null) {
            hashLogger.logHashes(signedState);
        }
    }

    @Override
    public void newSignedStateFromTransactions(@NonNull final ReservedSignedState signedState) {
        try (signedState) {
            signedState.get().setGarbageCollector(signedStateGarbageCollector);
            signedStateHasher.hashState(signedState.get());

            logHashes(signedState.get());

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
        logHashes(signedState);
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
