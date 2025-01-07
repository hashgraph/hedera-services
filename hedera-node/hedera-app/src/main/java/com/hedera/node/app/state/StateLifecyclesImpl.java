/*
 * Copyright (C) 2024-2025 Hedera Hashgraph, LLC
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

package com.hedera.node.app.state;

import static java.util.Objects.requireNonNull;

import com.hedera.node.app.Hedera;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.platform.state.StateLifecycles;
import com.swirlds.platform.system.InitTrigger;
import com.swirlds.platform.system.Platform;
import com.swirlds.platform.system.Round;
import com.swirlds.platform.system.SoftwareVersion;
import com.swirlds.platform.system.address.AddressBook;
import com.swirlds.platform.system.events.Event;
import com.swirlds.state.State;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;

/**
 * Implements the major lifecycle events for Hedera Services by delegating to a Hedera instance.
 */
public class StateLifecyclesImpl implements StateLifecycles {
    private final Hedera hedera;

    public StateLifecyclesImpl(@NonNull final Hedera hedera) {
        this.hedera = requireNonNull(hedera);
    }

    @Override
    public void onPreHandle(@NonNull final Event event, @NonNull final State state) {
        hedera.onPreHandle(event, state);
    }

    @Override
    public void onHandleConsensusRound(@NonNull final Round round, @NonNull final State state) {
        hedera.onHandleConsensusRound(round, state);
    }

    @Override
    public void onSealConsensusRound(@NonNull final Round round, @NonNull final State state) {
        requireNonNull(state);
        requireNonNull(round);
        hedera.onSealConsensusRound(round, state);
    }

    @Override
    public void onStateInitialized(
            @NonNull final State state,
            @NonNull final Platform platform,
            @NonNull final InitTrigger trigger,
            @Nullable SoftwareVersion previousVersion) {
        hedera.onStateInitialized(state, platform, trigger);
    }

    @Override
    public void onUpdateWeight(
            @NonNull final State stateRoot,
            @NonNull final AddressBook configAddressBook,
            @NonNull final PlatformContext context) {
        // No-op
    }

    @Override
    public void onNewRecoveredState(@NonNull final State recoveredStateRoot) {
        hedera.onNewRecoveredState(recoveredStateRoot);
    }
}
