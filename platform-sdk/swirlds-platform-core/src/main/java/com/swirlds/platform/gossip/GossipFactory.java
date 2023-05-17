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

import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.notification.NotificationEngine;
import com.swirlds.common.system.NodeId;
import com.swirlds.common.system.SoftwareVersion;
import com.swirlds.common.system.address.AddressBook;
import com.swirlds.common.threading.framework.QueueThread;
import com.swirlds.common.threading.interrupt.InterruptableConsumer;
import com.swirlds.common.threading.manager.ThreadManager;
import com.swirlds.common.time.Time;
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
import com.swirlds.platform.state.EmergencyRecoveryManager;
import com.swirlds.platform.state.SwirldStateManager;
import com.swirlds.platform.state.signed.SignedState;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Builds the gossip engine, depending on which flavor is requested in the configuration.
 */
public final class GossipFactory {

    private GossipFactory() {}

    public static Gossip buildGossip(
            @NonNull PlatformContext platformContext,
            @NonNull ThreadManager threadManager,
            @NonNull final Time time,
            @NonNull Crypto crypto,
            @NonNull final NotificationEngine notificationEngine,
            @NonNull AddressBook addressBook,
            @NonNull NodeId selfId,
            @NonNull SoftwareVersion appVersion,
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
            @NonNull final Consumer<SignedState> loadReconnectState) {

        final ChatterConfig chatterConfig = platformContext.getConfiguration().getConfigData(ChatterConfig.class);
        final SyncConfig syncConfig = platformContext.getConfiguration().getConfigData(SyncConfig.class);

        if (chatterConfig.useChatter()) {
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
                    loadReconnectState);
        } else if (syncConfig.syncAsProtocolEnabled()) {
            if (addressBook.getSize() == 1) {
                return new SingleNodeSyncGossip(
                        platformContext,
                        threadManager,
                        time,
                        crypto,
                        notificationEngine,
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
                        loadReconnectState);
            } else {
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
                        loadReconnectState);
            }
        } else {
            return new LegacySyncGossip(
                    platformContext,
                    threadManager,
                    time,
                    crypto,
                    notificationEngine,
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
                    loadReconnectState);
        }
    }
}
