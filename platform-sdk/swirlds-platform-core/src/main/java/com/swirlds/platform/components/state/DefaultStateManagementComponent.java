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
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.threading.manager.ThreadManager;
import com.swirlds.platform.components.common.output.FatalErrorConsumer;
import com.swirlds.platform.state.signed.ReservedSignedState;
import com.swirlds.platform.state.signed.SignedState;
import com.swirlds.platform.state.signed.SignedStateGarbageCollector;
import com.swirlds.platform.state.signed.SignedStateHasher;
import com.swirlds.platform.state.signed.SignedStateMetrics;
import com.swirlds.platform.state.signed.SignedStateSentinel;
import com.swirlds.platform.state.signed.SourceOfSignedState;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Predicate;

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
     * A predicate that tracks the status of the hash logger offer
     */
    private final Predicate<ReservedSignedState> offerToHashLogger;

    /**
     * Used to track signed state leaks, if enabled
     */
    private final SignedStateSentinel signedStateSentinel;
    /** signs a state */
    private final Consumer<ReservedSignedState> stateSigner;
    /** collects signatures for a state */
    private final Consumer<ReservedSignedState> sigCollector;

    /**
     * @param platformContext    the platform context
     * @param threadManager      manages platform thread resources
     * @param fatalErrorConsumer consumer to invoke when a fatal error has occurred
     * @param stateSigner        signs a state
     * @param sigCollector       collects signatures for a state
     * @param signedStateMetrics metrics about signed states
     * @param offerToHashLogger  tracks status of the hash logger offer
     */
    public DefaultStateManagementComponent(
            @NonNull final PlatformContext platformContext,
            @NonNull final ThreadManager threadManager,
            @NonNull final FatalErrorConsumer fatalErrorConsumer,
            @NonNull final Consumer<ReservedSignedState> stateSigner,
            @NonNull final Consumer<ReservedSignedState> sigCollector,
            @NonNull final SignedStateMetrics signedStateMetrics,
            @NonNull final Predicate<ReservedSignedState> offerToHashLogger) {

        Objects.requireNonNull(platformContext);
        Objects.requireNonNull(threadManager);
        Objects.requireNonNull(fatalErrorConsumer);

        // Various metrics about signed states

        this.signedStateGarbageCollector = new SignedStateGarbageCollector(threadManager, signedStateMetrics);
        this.signedStateSentinel = new SignedStateSentinel(platformContext, threadManager, Time.getCurrent());
        this.stateSigner = Objects.requireNonNull(stateSigner);
        this.sigCollector = Objects.requireNonNull(sigCollector);
        this.offerToHashLogger = Objects.requireNonNull(offerToHashLogger);

        signedStateHasher = new SignedStateHasher(signedStateMetrics, fatalErrorConsumer);
    }

    private void logHashes(@NonNull final SignedState signedState) {
        if (signedState.getState().getHash() != null) {
            final ReservedSignedState rss = signedState.reserve("logging hash state");
            final boolean offerAccepted = offerToHashLogger.test(rss);
            if (!offerAccepted) {
                rss.close();
            }
        }
    }

    @Override
    public void newSignedStateFromTransactions(@NonNull final ReservedSignedState signedState) {
        try (signedState) {
            signedState.get().setGarbageCollector(signedStateGarbageCollector);
            signedStateHasher.hashState(signedState.get());

            logHashes(signedState.get());

            stateSigner.accept(signedState.getAndReserve("signing state from transactions"));

            sigCollector.accept(
                    signedState.getAndReserve("DefaultStateManagementComponent.newSignedStateFromTransactions"));
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void stateToLoad(final SignedState signedState, final SourceOfSignedState sourceOfSignedState) {
        signedState.setGarbageCollector(signedStateGarbageCollector);
        logHashes(signedState);
        sigCollector.accept(signedState.reserve("DefaultStateManagementComponent.stateToLoad"));
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
}
