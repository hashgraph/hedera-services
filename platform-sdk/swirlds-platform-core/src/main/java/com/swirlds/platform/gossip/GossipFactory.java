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
import com.swirlds.common.notification.NotificationEngine;
import com.swirlds.common.system.NodeId;
import com.swirlds.common.system.SoftwareVersion;
import com.swirlds.common.system.address.AddressBook;
import com.swirlds.common.threading.framework.QueueThread;
import com.swirlds.common.threading.interrupt.InterruptableConsumer;
import com.swirlds.common.threading.manager.ThreadManager;
import com.swirlds.platform.Consensus;
import com.swirlds.platform.Crypto;
import com.swirlds.platform.FreezeManager;
import com.swirlds.platform.StartUpEventFrozenManager;
import com.swirlds.platform.components.EventMapper;
import com.swirlds.platform.components.state.StateManagementComponent;
import com.swirlds.platform.event.EventIntakeTask;
import com.swirlds.platform.event.linking.EventLinker;
import com.swirlds.platform.gossip.chatter.ChatterGossip;
import com.swirlds.platform.gossip.chatter.config.ChatterConfig;
import com.swirlds.platform.gossip.shadowgraph.ShadowGraph;
import com.swirlds.platform.gossip.sync.LegacySyncGossip;
import com.swirlds.platform.gossip.sync.SingleNodeSyncGossip;
import com.swirlds.platform.gossip.sync.SyncGossip;
import com.swirlds.platform.gossip.sync.config.SyncConfig;
import com.swirlds.platform.metrics.EventIntakeMetrics;
import com.swirlds.platform.observers.EventObserverDispatcher;
import com.swirlds.platform.recovery.EmergencyRecoveryManager;
import com.swirlds.platform.state.SwirldStateManager;
import com.swirlds.platform.state.signed.SignedState;
import edu.umd.cs.findbugs.annotations.NonNull;
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
     * @param shadowGraph                   contains non-ancient events
     * @param emergencyRecoveryManager      handles emergency recovery
     * @param consensusRef                  a pointer to consensus
     * @param intakeQueue                   the event intake queue
     * @param freezeManager                 handles freezes
     * @param startUpEventFrozenManager     prevents event creation during startup
     * @param swirldStateManager            manages the mutable state
     * @param startedFromGenesis            true if this node started from a genesis state
     * @param stateManagementComponent      manages the lifecycle of the state
     * @param eventIntakeLambda             a method that is called when something needs to be added to the event intake
     *                                      queue
     * @param eventObserverDispatcher       the object used to wire event intake
     * @param eventMapper                   a data structure used to track the most recent event from each node
     * @param eventIntakeMetrics            metrics for event intake
     * @param eventLinker                   links together events, if chatter is enabled will also buffer orphans
     * @param updatePlatformStatus          a method that updates the platform status, when called
     * @param loadReconnectState            a method that should be called when a state from reconnect is obtained
     * @param clearAllPipelinesForReconnect this method should be called to clear all pipelines prior to a reconnect
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
            @NonNull final ShadowGraph shadowGraph,
            @NonNull final EmergencyRecoveryManager emergencyRecoveryManager,
            @NonNull final AtomicReference<Consensus> consensusRef,
            @NonNull final QueueThread<EventIntakeTask> intakeQueue,
            @NonNull final FreezeManager freezeManager,
            @NonNull final StartUpEventFrozenManager startUpEventFrozenManager,
            @NonNull final SwirldStateManager swirldStateManager,
            final boolean startedFromGenesis,
            @NonNull final StateManagementComponent stateManagementComponent,
            @NonNull final InterruptableConsumer<EventIntakeTask> eventIntakeLambda,
            @NonNull final EventObserverDispatcher eventObserverDispatcher,
            @NonNull final EventMapper eventMapper,
            @NonNull final EventIntakeMetrics eventIntakeMetrics,
            @NonNull final EventLinker eventLinker,
            @NonNull final Runnable updatePlatformStatus,
            @NonNull final Consumer<SignedState> loadReconnectState,
            @NonNull final Runnable clearAllPipelinesForReconnect) {

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
        Objects.requireNonNull(freezeManager);
        Objects.requireNonNull(startUpEventFrozenManager);
        Objects.requireNonNull(swirldStateManager);
        Objects.requireNonNull(stateManagementComponent);
        Objects.requireNonNull(eventIntakeLambda);
        Objects.requireNonNull(eventObserverDispatcher);
        Objects.requireNonNull(eventMapper);
        Objects.requireNonNull(eventIntakeMetrics);
        Objects.requireNonNull(eventLinker);
        Objects.requireNonNull(updatePlatformStatus);
        Objects.requireNonNull(loadReconnectState);
        Objects.requireNonNull(clearAllPipelinesForReconnect);

        final ChatterConfig chatterConfig = platformContext.getConfiguration().getConfigData(ChatterConfig.class);
        final SyncConfig syncConfig = platformContext.getConfiguration().getConfigData(SyncConfig.class);

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
                    shadowGraph,
                    emergencyRecoveryManager,
                    consensusRef,
                    intakeQueue,
                    freezeManager,
                    startUpEventFrozenManager,
                    swirldStateManager,
                    startedFromGenesis,
                    stateManagementComponent,
                    eventIntakeLambda,
                    eventObserverDispatcher,
                    eventMapper,
                    eventIntakeMetrics,
                    eventLinker,
                    updatePlatformStatus,
                    loadReconnectState,
                    clearAllPipelinesForReconnect);
        } else if (syncConfig.syncAsProtocolEnabled()) {
            if (addressBook.getSize() == 1) {
                logger.info(STARTUP.getMarker(), "Using SingleNodeSyncGossip");
                return new SingleNodeSyncGossip(
                        platformContext,
                        threadManager,
                        crypto,
                        addressBook,
                        selfId,
                        appVersion,
                        shadowGraph,
                        intakeQueue,
                        freezeManager,
                        startUpEventFrozenManager,
                        swirldStateManager,
                        stateManagementComponent,
                        eventIntakeLambda,
                        eventObserverDispatcher,
                        eventMapper,
                        eventIntakeMetrics,
                        updatePlatformStatus,
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
                        shadowGraph,
                        emergencyRecoveryManager,
                        consensusRef,
                        intakeQueue,
                        freezeManager,
                        startUpEventFrozenManager,
                        swirldStateManager,
                        stateManagementComponent,
                        eventIntakeLambda,
                        eventObserverDispatcher,
                        eventMapper,
                        eventIntakeMetrics,
                        updatePlatformStatus,
                        loadReconnectState,
                        clearAllPipelinesForReconnect);
            }
        } else {
            logger.info(STARTUP.getMarker(), "Using LegacySyncGossip");
            return new LegacySyncGossip(
                    platformContext,
                    threadManager,
                    crypto,
                    addressBook,
                    selfId,
                    appVersion,
                    shadowGraph,
                    consensusRef,
                    intakeQueue,
                    freezeManager,
                    startUpEventFrozenManager,
                    swirldStateManager,
                    stateManagementComponent,
                    eventIntakeLambda,
                    eventObserverDispatcher,
                    eventMapper,
                    eventIntakeMetrics,
                    updatePlatformStatus,
                    loadReconnectState,
                    clearAllPipelinesForReconnect);
        }
    }
}
