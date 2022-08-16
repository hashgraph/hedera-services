/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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
package com.hedera.services.context.primitives;

import com.hedera.services.ServicesState;
import com.hedera.services.config.NetworkInfo;
import com.hedera.services.context.ImmutableStateChildren;
import com.hedera.services.context.MutableStateChildren;
import com.hedera.services.context.StateChildren;
import com.hedera.services.exceptions.NoValidSignedStateException;
import com.hedera.services.state.migration.StateVersions;
import com.hedera.services.store.schedule.ScheduleStore;
import com.hedera.services.utils.NonAtomicReference;
import com.swirlds.common.system.Platform;
import com.swirlds.common.utility.AutoCloseableWrapper;
import java.util.Optional;
import java.util.function.Consumer;
import javax.inject.Inject;
import javax.inject.Singleton;

/** A factory that provides view of last completed signed state and its state children. */
@Singleton
public class SignedStateViewFactory {
    private final Platform platform;
    private final ScheduleStore scheduleStore;
    private final NetworkInfo networkInfo;

    @Inject
    public SignedStateViewFactory(
            final Platform platform,
            final ScheduleStore scheduleStore,
            final NetworkInfo nodeInfo) {
        this.platform = platform;
        this.scheduleStore = scheduleStore;
        this.networkInfo = nodeInfo;
    }

    /**
     * Try to update the mutable state children with the children from the signed state.
     *
     * @param children mutable children provided
     * @throws NoValidSignedStateException if the provided state is invalid
     */
    public void tryToUpdateToLatestSignedChildren(final MutableStateChildren children)
            throws NoValidSignedStateException {
        doWithLatest(state -> children.updateFromImmutable(state, state.getTimeOfLastHandledTxn()));
    }

    /**
     * Gets the immutable state children from the latest signedState from platform. Returns {@code
     * Optional.empty()} when platform has no valid signed state.
     *
     * @return children from the latest signed state, if present
     */
    public Optional<StateChildren> childrenOfLatestSignedState() {
        final var ref = new NonAtomicReference<StateChildren>();
        try {
            doWithLatest(state -> ref.set(new ImmutableStateChildren(state)));
            return Optional.of(ref.get());
        } catch (NoValidSignedStateException ignore) {
            return Optional.empty();
        }
    }

    /**
     * Returns a {@link StateView} backed by the {@link StateChildren} of the latest signed state,
     * if available.
     *
     * @return the requested view, if present
     */
    public Optional<StateView> latestSignedStateView() {
        return childrenOfLatestSignedState()
                .map(children -> new StateView(scheduleStore, children, networkInfo));
    }

    /**
     * Checks if the provided state is usable as the latest signed state.
     *
     * @param state a services state
     * @return if the given state is usable
     */
    public static boolean isUsable(final ServicesState state) {
        if (state == null) {
            return false;
        }
        // Since we can't get the enclosing platform SignedState, we don't know exactly when this
        // state was signed.
        // So we just use, as a guaranteed lower bound, the consensus time of its last-handled
        // transaction.
        final var latestSigningTime = state.getTimeOfLastHandledTxn();

        // There are a few edge cases that disqualify a state:
        //   1. No transactions have been handled (latestSigningTime == null); abort now to avoid
        // downstream NPE
        //   2. The state isn't of the CURRENT_VERSION---this likely means a larger problem is at
        // hand
        //   3. The state has not completed an init() call---again, likely a symptom of a larger
        // problem
        return latestSigningTime != null
                && state.getStateVersion() == StateVersions.CURRENT_VERSION
                && state.isInitialized();
    }

    /**
     * Uses the last completed signed state from platform to perform a given action.
     * <b>IMPORTANT:</b> should be called everytime we try to get or update state children, to
     * ensure they reflect the latest state.
     *
     * @param action what to do with the latest state
     * @throws NoValidSignedStateException
     */
    private void doWithLatest(final Consumer<ServicesState> action)
            throws NoValidSignedStateException {
        try (final AutoCloseableWrapper<ServicesState> wrapper =
                platform.getLastCompleteSwirldState()) {
            final var signedState = wrapper.get();
            if (!isUsable(signedState)) {
                throw new NoValidSignedStateException();
            }
            action.accept(signedState);
        }
    }
}
