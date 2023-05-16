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

package com.swirlds.platform.gossip.sync;

import static com.swirlds.platform.SwirldsPlatform.PLATFORM_THREAD_POOL_NAME;

import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.merkle.synchronization.config.ReconnectConfig;
import com.swirlds.common.notification.NotificationEngine;
import com.swirlds.common.system.NodeId;
import com.swirlds.common.system.SoftwareVersion;
import com.swirlds.common.system.address.AddressBook;
import com.swirlds.common.threading.framework.QueueThread;
import com.swirlds.common.threading.framework.config.StoppableThreadConfiguration;
import com.swirlds.common.threading.framework.config.ThreadConfiguration;
import com.swirlds.common.threading.interrupt.InterruptableConsumer;
import com.swirlds.common.threading.manager.ThreadManager;
import com.swirlds.common.time.Time;
import com.swirlds.platform.Consensus;
import com.swirlds.platform.Crypto;
import com.swirlds.platform.FreezeManager;
import com.swirlds.platform.PlatformConstructor;
import com.swirlds.platform.StartUpEventFrozenManager;
import com.swirlds.platform.components.EventMapper;
import com.swirlds.platform.components.state.StateManagementComponent;
import com.swirlds.platform.event.EventIntakeTask;
import com.swirlds.platform.gossip.FallenBehindManagerImpl;
import com.swirlds.platform.gossip.shadowgraph.ShadowGraph;
import com.swirlds.platform.gossip.shadowgraph.SimultaneousSyncThrottle;
import com.swirlds.platform.metrics.EventIntakeMetrics;
import com.swirlds.platform.metrics.ReconnectMetrics;
import com.swirlds.platform.network.unidirectional.HeartbeatProtocolResponder;
import com.swirlds.platform.network.unidirectional.HeartbeatSender;
import com.swirlds.platform.network.unidirectional.Listener;
import com.swirlds.platform.network.unidirectional.MultiProtocolResponder;
import com.swirlds.platform.network.unidirectional.ProtocolMapping;
import com.swirlds.platform.network.unidirectional.SharedConnectionLocks;
import com.swirlds.platform.network.unidirectional.UnidirectionalProtocols;
import com.swirlds.platform.observers.EventObserverDispatcher;
import com.swirlds.platform.reconnect.DefaultSignedStateValidator;
import com.swirlds.platform.reconnect.ReconnectHelper;
import com.swirlds.platform.reconnect.ReconnectProtocolResponder;
import com.swirlds.platform.reconnect.ReconnectThrottle;
import com.swirlds.platform.state.SwirldStateManager;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Sync gossip without the protocol negotiator.
 */
public class LegacySyncGossip extends AbstractSyncGossip {

    private final SharedConnectionLocks sharedConnectionLocks;
    private final SimultaneousSyncThrottle simultaneousSyncThrottle;

    public LegacySyncGossip(
            @NonNull PlatformContext platformContext,
            @NonNull ThreadManager threadManager,
            @NonNull final Time time,
            @NonNull Crypto crypto,
            @NonNull final NotificationEngine notificationEngine,
            @NonNull AddressBook addressBook,
            @NonNull NodeId selfId,
            @NonNull SoftwareVersion appVersion,
            @NonNull final ShadowGraph shadowGraph,
            @NonNull final AtomicReference<Consensus> consensusRef,
            @NonNull final QueueThread<EventIntakeTask> intakeQueue,
            @NonNull final SwirldStateManager swirldStateManager,
            @NonNull final FreezeManager freezeManager,
            @NonNull final StartUpEventFrozenManager startUpEventFrozenManager,
            @NonNull final FallenBehindManagerImpl fallenBehindManager,
            @NonNull final StateManagementComponent stateManagementComponent,
            @NonNull final ReconnectThrottle reconnectThrottle,
            @NonNull final ReconnectMetrics reconnectMetrics,
            @NonNull final ReconnectHelper reconnectHelper,
            @NonNull final Runnable updatePlatformStatus,
            @NonNull final InterruptableConsumer<EventIntakeTask> eventIntakeLambda,
            @NonNull final EventMapper eventMapper,
            @NonNull final EventIntakeMetrics eventIntakeMetrics,
            @NonNull final EventObserverDispatcher eventObserverDispatcher) {
        super(
                platformContext,
                threadManager,
                time,
                crypto,
                notificationEngine,
                addressBook,
                selfId,
                appVersion,
                reconnectHelper,
                updatePlatformStatus,
                intakeQueue,
                shadowGraph,
                consensusRef,
                swirldStateManager,
                freezeManager,
                startUpEventFrozenManager,
                fallenBehindManager,
                stateManagementComponent,
                reconnectThrottle,
                reconnectMetrics,
                eventIntakeLambda,
                eventMapper,
                eventIntakeMetrics,
                eventObserverDispatcher);

        sharedConnectionLocks = new SharedConnectionLocks(topology, connectionManagers);

        simultaneousSyncThrottle = new SimultaneousSyncThrottle(
                platformContext.getMetrics(), settings.getMaxIncomingSyncsInc() + settings.getMaxOutgoingSyncs());

        final ReconnectConfig reconnectConfig =
                platformContext.getConfiguration().getConfigData(ReconnectConfig.class);

        final MultiProtocolResponder protocolHandlers = new MultiProtocolResponder(List.of(
                ProtocolMapping.map(
                        UnidirectionalProtocols.SYNC.getInitialByte(),
                        new SyncProtocolResponder(
                                simultaneousSyncThrottle,
                                syncShadowgraphSynchronizer,
                                syncManager,
                                syncManager::shouldAcceptSync)),
                ProtocolMapping.map(
                        UnidirectionalProtocols.RECONNECT.getInitialByte(),
                        new ReconnectProtocolResponder(
                                threadManager,
                                stateManagementComponent,
                                reconnectConfig,
                                reconnectThrottle,
                                reconnectMetrics)),
                ProtocolMapping.map(
                        UnidirectionalProtocols.HEARTBEAT.getInitialByte(),
                        HeartbeatProtocolResponder::heartbeatProtocol)));

        for (final NodeId otherId : topology.getNeighbors()) {
            // create and start new threads to listen for incoming sync requests
            new StoppableThreadConfiguration<>(threadManager)
                    .setPriority(Thread.NORM_PRIORITY)
                    .setNodeId(selfId.id())
                    .setComponent(PLATFORM_THREAD_POOL_NAME)
                    .setOtherNodeId(otherId.id())
                    .setThreadName("listener")
                    .setWork(new Listener(protocolHandlers, connectionManagers.getManager(otherId, false)))
                    .build()
                    .start();

            // create and start new thread to send heartbeats on the SyncCaller channels
            new StoppableThreadConfiguration<>(threadManager)
                    .setPriority(settings.getThreadPrioritySync())
                    .setNodeId(selfId.id())
                    .setComponent(PLATFORM_THREAD_POOL_NAME)
                    .setThreadName("heartbeat")
                    .setOtherNodeId(otherId.id())
                    .setWork(new HeartbeatSender(
                            otherId, sharedConnectionLocks, networkMetrics, PlatformConstructor.settingsProvider()))
                    .build()
                    .start();
        }

        // create and start threads to call other members
        for (int i = 0; i < settings.getMaxOutgoingSyncs(); i++) {
            spawnSyncCaller(i);
        }
    }

    /**
     * Spawn a thread to initiate syncs with other users
     */
    private void spawnSyncCaller(final int callerNumber) {
        // create a caller that will run repeatedly to call random members other than selfId
        final SyncCaller syncCaller = new SyncCaller(
                platformContext,
                addressBook,
                selfId,
                callerNumber,
                reconnectHelper,
                new DefaultSignedStateValidator(),
                simultaneousSyncThrottle,
                syncManager,
                sharedConnectionLocks,
                eventTaskCreator,
                updatePlatformStatus,
                syncShadowgraphSynchronizer);

        /* the thread that repeatedly initiates syncs with other members */
        final Thread syncCallerThread = new ThreadConfiguration(threadManager)
                .setPriority(settings.getThreadPrioritySync())
                .setNodeId(selfId.id())
                .setComponent(PLATFORM_THREAD_POOL_NAME)
                .setThreadName("syncCaller-" + callerNumber)
                .setRunnable(syncCaller)
                .build();

        syncCallerThread.start();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected boolean unidirectionalConnectionsEnabled() {
        return true;
    }

    @Override
    public void stop() {
        super.stop();
        // wait and acquire all sync ongoing locks and release them immediately
        // this will ensure any ongoing sync are finished before we start reconnect
        // no new sync will start because we have a fallen behind status
        simultaneousSyncThrottle.waitForAllSyncsToFinish();
    }
}
