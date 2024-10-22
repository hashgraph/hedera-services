/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.swirlds.platform.builder;

import static java.util.Objects.requireNonNull;

import com.hedera.service.gossip.IntakeEventCounter;
import com.swirlds.common.AddressBook;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.notification.NotificationEngine;
import com.swirlds.common.platform.NodeId;
import com.swirlds.common.wiring.model.WiringModel;
import com.swirlds.platform.consensus.ConsensusSnapshot;
import com.swirlds.platform.crypto.KeysAndCerts;
import com.swirlds.platform.event.PlatformEvent;
import com.swirlds.platform.event.preconsensus.PcesFileTracker;
import com.swirlds.platform.pool.TransactionPoolNexus;
import com.swirlds.platform.scratchpad.Scratchpad;
import com.swirlds.platform.state.SwirldStateManager;
import com.swirlds.platform.state.iss.IssScratchpad;
import com.swirlds.platform.state.signed.ReservedSignedState;
import com.swirlds.platform.state.signed.SignedState;
import com.swirlds.platform.system.SoftwareVersion;
import com.swirlds.platform.system.status.StatusActionSubmitter;
import com.swirlds.platform.util.RandomBuilder;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * This record contains core utilities and basic objects needed to build a platform. It should not contain any platform
 * components.
 *
 * @param platformContext                        the context for this platform
 * @param model                                  the wiring model for this platform
 * @param keysAndCerts                           an object holding all the public/private key pairs and the CSPRNG state
 *                                               for this member
 * @param selfId                                 the ID for this node
 * @param mainClassName                          the name of the app class inheriting from SwirldMain
 * @param swirldName                             the name of the swirld being run
 * @param appVersion                             the current version of the running application
 * @param initialState                           the initial state of the platform
 * @param applicationCallbacks                   the callbacks that the platform will call when certain events happen
 * @param preconsensusEventConsumer              the consumer for preconsensus events, null if publishing this data has
 *                                               not been enabled
 * @param snapshotOverrideConsumer               the consumer for snapshot overrides, null if publishing this data has
 *                                               not been enabled
 * @param intakeEventCounter                     counts events that have been received by gossip but not yet inserted
 *                                               into gossip event storage, per peer
 * @param randomBuilder                          a builder for creating random number generators
 * @param transactionPoolNexus                   provides transactions to be added to new events
 * @param isInFreezePeriodReference              a reference to a predicate that determines if a timestamp is in the
 *                                               freeze period, this can be deleted as soon as the CES is retired.
 * @param latestImmutableStateProviderReference  a reference to a method that supplies the latest immutable state. Input
 *                                               argument is a string explaining why we are getting this state (for
 *                                               debugging). Return value may be null (implementation detail of
 *                                               underlying data source), this indirection can be removed once states
 *                                               are passed within the wiring framework
 * @param initialPcesFiles                       the initial set of PCES files present when the node starts
 * @param issScratchpad                          scratchpad storage for ISS recovery
 * @param notificationEngine                     for sending notifications to the application (legacy pattern)
 * @param firstPlatform                          if this is the first platform being built (there is static setup that
 *                                               needs to be done, long term plan is to stop using static variables)
 * @param statusActionSubmitterReference         a reference to the status action submitter, this can be deleted once
 *                                               platform status management is handled by the wiring framework
 * @param getLatestCompleteStateReference        a reference to a supplier that supplies the latest immutable state,
 *                                               this is exposed here due to reconnect, can be removed once reconnect is
 *                                               made compatible with the wiring framework
 * @param loadReconnectStateReference            a reference to a consumer that loads the state for reconnect, can be
 *                                               removed once reconnect is made compatible with the wiring framework
 * @param clearAllPipelinesForReconnectReference a reference to a runnable that clears all pipelines for reconnect, can
 *                                               be removed once reconnect is made compatible with the wiring framework
 * @param swirldStateManager                     responsible for the mutable state, this is exposed here due to
 *                                               reconnect, can be removed once reconnect is made compatible with the
 *                                               wiring framework
 */
public record PlatformBuildingBlocks(
        @NonNull PlatformContext platformContext,
        @NonNull WiringModel model,
        @NonNull KeysAndCerts keysAndCerts,
        @NonNull NodeId selfId,
        @NonNull String mainClassName,
        @NonNull String swirldName,
        @NonNull SoftwareVersion appVersion,
        @NonNull ReservedSignedState initialState,
        @NonNull ApplicationCallbacks applicationCallbacks,
        @Nullable Consumer<PlatformEvent> preconsensusEventConsumer,
        @Nullable Consumer<ConsensusSnapshot> snapshotOverrideConsumer,
        @NonNull IntakeEventCounter intakeEventCounter,
        @NonNull RandomBuilder randomBuilder,
        @NonNull TransactionPoolNexus transactionPoolNexus,
        @NonNull AtomicReference<Predicate<Instant>> isInFreezePeriodReference,
        @NonNull AtomicReference<Function<String, ReservedSignedState>> latestImmutableStateProviderReference,
        @NonNull PcesFileTracker initialPcesFiles,
        @NonNull Scratchpad<IssScratchpad> issScratchpad,
        @NonNull NotificationEngine notificationEngine,
        @NonNull AtomicReference<StatusActionSubmitter> statusActionSubmitterReference,
        @NonNull SwirldStateManager swirldStateManager,
        @NonNull AtomicReference<Supplier<ReservedSignedState>> getLatestCompleteStateReference,
        @NonNull AtomicReference<Consumer<SignedState>> loadReconnectStateReference,
        @NonNull AtomicReference<Runnable> clearAllPipelinesForReconnectReference,
        boolean firstPlatform) {

    public PlatformBuildingBlocks {
        requireNonNull(platformContext);
        requireNonNull(model);
        requireNonNull(keysAndCerts);
        requireNonNull(selfId);
        requireNonNull(mainClassName);
        requireNonNull(swirldName);
        requireNonNull(appVersion);
        requireNonNull(initialState);
        requireNonNull(applicationCallbacks);
        requireNonNull(intakeEventCounter);
        requireNonNull(randomBuilder);
        requireNonNull(transactionPoolNexus);
        requireNonNull(isInFreezePeriodReference);
        requireNonNull(latestImmutableStateProviderReference);
        requireNonNull(initialPcesFiles);
        requireNonNull(issScratchpad);
        requireNonNull(notificationEngine);
        requireNonNull(statusActionSubmitterReference);
        requireNonNull(swirldStateManager);
        requireNonNull(getLatestCompleteStateReference);
        requireNonNull(loadReconnectStateReference);
        requireNonNull(clearAllPipelinesForReconnectReference);
    }

    /**
     * Get the address book from the initial state.
     *
     * @return the initial address book
     */
    @NonNull
    public AddressBook initialAddressBook() {
        return initialState.get().getState().getReadablePlatformState().getAddressBook();
    }
}
