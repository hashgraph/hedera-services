/*
 * Copyright (C) 2025 Hedera Hashgraph, LLC
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

package com.swirlds.platform.system;

import static com.swirlds.platform.system.InitTrigger.EVENT_STREAM_RECOVERY;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.platform.event.StateSignatureTransaction;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.platform.components.transaction.system.ScopedSystemTransaction;
import com.swirlds.platform.state.PlatformMerkleStateRoot;
import com.swirlds.platform.state.PlatformStateModifier;
import com.swirlds.platform.state.StateLifecycles;
import com.swirlds.platform.system.address.AddressBook;
import com.swirlds.platform.system.events.Event;
import com.swirlds.platform.system.state.notifications.NewRecoveredStateListener;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.function.Consumer;

/**
 * This class is a bridge between the {@link PlatformMerkleStateRoot} and the {@link StateLifecycles}.
 * It is responsible for handling the state events by delegating the calls to the {@link StateLifecycles}
 * and providing the current state to it. Note that the client code is supposed create another instance of this class
 * once another copy of the state is created.
 */
public class PlatformStateEventHandler implements StateEventHandler {

    private final PlatformMerkleStateRoot stateRoot;
    private final StateLifecycles<PlatformMerkleStateRoot> lifecycles;

    public PlatformStateEventHandler(
            PlatformMerkleStateRoot stateRoot, StateLifecycles<PlatformMerkleStateRoot> lifecycles) {
        this.stateRoot = stateRoot;
        this.lifecycles = lifecycles;
    }

    @Override
    public void init(
            @NonNull Platform platform, @NonNull InitTrigger trigger, @Nullable SoftwareVersion deserializedVersion) {
        final PlatformContext platformContext = platform.getContext();
        stateRoot.init(
                platformContext.getTime(), platformContext.getMetrics(), platformContext.getMerkleCryptography());

        // If we are initialized for event stream recovery, we have to register an
        // extra listener to make sure we call all the required Hedera lifecycles
        if (trigger == EVENT_STREAM_RECOVERY) {
            final var notificationEngine = platform.getNotificationEngine();
            notificationEngine.register(
                    NewRecoveredStateListener.class,
                    notification -> lifecycles.onNewRecoveredState(notification.getSwirldState()));
        }
        lifecycles.onStateInitialized(stateRoot, platform, trigger, deserializedVersion);
    }

    @Override
    public void preHandle(
            final Event event,
            final Consumer<ScopedSystemTransaction<StateSignatureTransaction>> stateSignatureTransaction) {
        lifecycles.onPreHandle(event, stateRoot, stateSignatureTransaction);
    }

    @Override
    public void handleConsensusRound(
            final Round round,
            final PlatformStateModifier platformState,
            final Consumer<ScopedSystemTransaction<StateSignatureTransaction>> stateSignatureTransaction) {
        stateRoot.throwIfImmutable();
        lifecycles.onHandleConsensusRound(round, stateRoot, stateSignatureTransaction);
    }

    @Override
    public void sealConsensusRound(@NonNull final Round round) {
        requireNonNull(round);
        stateRoot.throwIfImmutable();
        lifecycles.onSealConsensusRound(round, stateRoot);
    }

    @NonNull
    @Override
    public AddressBook updateWeight(
            @NonNull final AddressBook configAddressBook, @NonNull final PlatformContext context) {
        lifecycles.onUpdateWeight(stateRoot, configAddressBook, context);
        return configAddressBook;
    }

    @NonNull
    @Override
    public PlatformMerkleStateRoot getStateRoot() {
        return stateRoot;
    }

    @Override
    @NonNull
    public PlatformStateEventHandler withNewStateRoot(@NonNull final PlatformMerkleStateRoot newRoot) {
        return new PlatformStateEventHandler(newRoot, lifecycles);
    }

    @Override
    public boolean isImmutable() {
        return stateRoot.isImmutable();
    }
}
