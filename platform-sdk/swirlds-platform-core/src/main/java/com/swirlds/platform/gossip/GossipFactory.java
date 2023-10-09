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

package com.swirlds.platform.gossip;

import static com.swirlds.logging.LogMarker.STARTUP;

import com.swirlds.base.time.Time;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.notification.NotificationEngine;
import com.swirlds.common.system.NodeId;
import com.swirlds.common.system.SoftwareVersion;
import com.swirlds.common.system.address.AddressBook;
import com.swirlds.common.system.status.PlatformStatusManager;
import com.swirlds.common.threading.framework.QueueThread;
import com.swirlds.common.threading.manager.ThreadManager;
import com.swirlds.platform.Consensus;
import com.swirlds.platform.Crypto;
import com.swirlds.platform.components.state.StateManagementComponent;
import com.swirlds.platform.event.GossipEvent;
import com.swirlds.platform.event.linking.EventLinker;
import com.swirlds.platform.event.validation.EventValidator;
import com.swirlds.platform.gossip.chatter.ChatterGossip;
import com.swirlds.platform.gossip.chatter.config.ChatterConfig;
import com.swirlds.platform.gossip.shadowgraph.ShadowGraph;
import com.swirlds.platform.gossip.sync.SingleNodeSyncGossip;
import com.swirlds.platform.gossip.sync.SyncGossip;
import com.swirlds.platform.metrics.SyncMetrics;
import com.swirlds.platform.observers.EventObserverDispatcher;
import com.swirlds.platform.recovery.EmergencyRecoveryManager;
import com.swirlds.platform.state.SwirldStateManager;
import com.swirlds.platform.state.signed.SignedState;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Builds the gossip engine, depending on which flavor is requested in the configuration.
 */
public final class GossipFactory {

    private static final Logger logger = LogManager.getLogger(GossipFactory.class);

    private GossipFactory() {}

    /**
     * Builds the gossip engine, depending on which flavor is requested in the configuration.
     *
     * @param platformContext               the platform context
     * @param threadManager                 the thread manager
     * @param time                          the wall clock time
     * @param crypto                        can be used to sign things
     * @param notificationEngine            used to send notifications to the app
     * @param addressBook                   the current address book
     * @param selfId                        this node's ID
     * @param appVersion                    the version of the app
     * @param epochHash                     the epoch hash of the initial state
     * @param shadowGraph                   contains non-ancient events
     * @param emergencyRecoveryManager      handles emergency recovery
     * @param consensusRef                  a pointer to consensus
     * @param intakeQueue                   the event intake queue
     * @param swirldStateManager            manages the mutable state
     * @param stateManagementComponent      manages the lifecycle of the state
     * @param eventValidator                validates events and passes valid events further along the intake pipeline
     * @param eventObserverDispatcher       the object used to wire event intake
     * @param syncMetrics                   metrics for sync
     * @param eventLinker                   links together events, if chatter is enabled will also buffer orphans
     * @param platformStatusManager         the platform status manager
     * @param loadReconnectState            a method that should be called when a state from reconnect is obtained
     * @param clearAllPipelinesForReconnect this method should be called to clear all pipelines prior to a reconnect
     * @param intakeEventCounter            keeps track of the number of events in the intake pipeline from each peer
     * @return the gossip engine
     */
    public static Gossip buildGossip(
            @NonNull final PlatformContext platformContext,
            @NonNull final ThreadManager threadManager,
            @NonNull final Time time,
            @NonNull Crypto crypto,
            @NonNull final NotificationEngine notificationEngine,
            @NonNull final AddressBook addressBook,
            @NonNull final NodeId selfId,
            @NonNull final SoftwareVersion appVersion,
            @Nullable final Hash epochHash,
            @NonNull final ShadowGraph shadowGraph,
            @NonNull final EmergencyRecoveryManager emergencyRecoveryManager,
            @NonNull final AtomicReference<Consensus> consensusRef,
            @NonNull final QueueThread<GossipEvent> intakeQueue,
            @NonNull final SwirldStateManager swirldStateManager,
            @NonNull final StateManagementComponent stateManagementComponent,
            @NonNull final EventValidator eventValidator,
            @NonNull final EventObserverDispatcher eventObserverDispatcher,
            @NonNull final SyncMetrics syncMetrics,
            @NonNull final EventLinker eventLinker,
            @NonNull final PlatformStatusManager platformStatusManager,
            @NonNull final Consumer<SignedState> loadReconnectState,
            @NonNull final Runnable clearAllPipelinesForReconnect,
            @NonNull final IntakeEventCounter intakeEventCounter) {

        Objects.requireNonNull(platformContext);
        Objects.requireNonNull(threadManager);
        Objects.requireNonNull(time);
        Objects.requireNonNull(crypto);
        Objects.requireNonNull(notificationEngine);
        Objects.requireNonNull(addressBook);
        Objects.requireNonNull(selfId);
        Objects.requireNonNull(appVersion);
        Objects.requireNonNull(shadowGraph);
        Objects.requireNonNull(emergencyRecoveryManager);
        Objects.requireNonNull(consensusRef);
        Objects.requireNonNull(intakeQueue);
        Objects.requireNonNull(swirldStateManager);
        Objects.requireNonNull(stateManagementComponent);
        Objects.requireNonNull(eventValidator);
        Objects.requireNonNull(eventObserverDispatcher);
        Objects.requireNonNull(syncMetrics);
        Objects.requireNonNull(eventLinker);
        Objects.requireNonNull(platformStatusManager);
        Objects.requireNonNull(loadReconnectState);
        Objects.requireNonNull(clearAllPipelinesForReconnect);
        Objects.requireNonNull(intakeEventCounter);

        final ChatterConfig chatterConfig = platformContext.getConfiguration().getConfigData(ChatterConfig.class);

        if (chatterConfig.useChatter()) {
            logger.info(STARTUP.getMarker(), "Using ChatterGossip");
            return new ChatterGossip(
                    platformContext,
                    threadManager,
                    time,
                    crypto,
                    notificationEngine,
                    addressBook,
                    selfId,
                    appVersion,
                    epochHash,
                    shadowGraph,
                    emergencyRecoveryManager,
                    consensusRef,
                    intakeQueue,
                    swirldStateManager,
                    stateManagementComponent,
                    eventValidator,
                    eventObserverDispatcher,
                    syncMetrics,
                    eventLinker,
                    platformStatusManager,
                    loadReconnectState,
                    clearAllPipelinesForReconnect);
        } else {
            if (addressBook.getSize() == 1) {
                logger.info(STARTUP.getMarker(), "Using SingleNodeSyncGossip");
                return new SingleNodeSyncGossip(
                        platformContext,
                        threadManager,
                        time,
                        crypto,
                        addressBook,
                        selfId,
                        appVersion,
                        shadowGraph,
                        intakeQueue,
                        swirldStateManager,
                        stateManagementComponent,
                        eventObserverDispatcher,
                        syncMetrics,
                        platformStatusManager,
                        loadReconnectState,
                        clearAllPipelinesForReconnect);
            } else {
                logger.info(STARTUP.getMarker(), "Using SyncGossip");
                return new SyncGossip(
                        platformContext,
                        threadManager,
                        time,
                        crypto,
                        notificationEngine,
                        addressBook,
                        selfId,
                        appVersion,
                        epochHash,
                        shadowGraph,
                        emergencyRecoveryManager,
                        consensusRef,
                        intakeQueue,
                        swirldStateManager,
                        stateManagementComponent,
                        eventObserverDispatcher,
                        syncMetrics,
                        eventLinker,
                        platformStatusManager,
                        loadReconnectState,
                        clearAllPipelinesForReconnect,
                        intakeEventCounter);
            }
        }
    }
}
