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

package com.swirlds.platform;

import static com.swirlds.base.ArgumentUtils.throwArgNull;
import static com.swirlds.platform.SwirldsPlatform.PLATFORM_THREAD_POOL_NAME;

import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.system.NodeId;
import com.swirlds.common.system.SoftwareVersion;
import com.swirlds.common.system.address.AddressBook;
import com.swirlds.common.threading.framework.config.StoppableThreadConfiguration;
import com.swirlds.common.threading.framework.config.ThreadConfiguration;
import com.swirlds.common.threading.manager.ThreadManager;
import com.swirlds.common.utility.Clearable;
import com.swirlds.platform.components.EventTaskCreator;
import com.swirlds.platform.components.state.StateManagementComponent;
import com.swirlds.platform.metrics.ReconnectMetrics;
import com.swirlds.platform.metrics.SyncMetrics;
import com.swirlds.platform.network.ConnectionTracker;
import com.swirlds.platform.network.NetworkMetrics;
import com.swirlds.platform.network.topology.NetworkTopology;
import com.swirlds.platform.network.topology.StaticConnectionManagers;
import com.swirlds.platform.network.unidirectional.HeartbeatProtocolResponder;
import com.swirlds.platform.network.unidirectional.HeartbeatSender;
import com.swirlds.platform.network.unidirectional.Listener;
import com.swirlds.platform.network.unidirectional.MultiProtocolResponder;
import com.swirlds.platform.network.unidirectional.ProtocolMapping;
import com.swirlds.platform.network.unidirectional.SharedConnectionLocks;
import com.swirlds.platform.network.unidirectional.UnidirectionalProtocols;
import com.swirlds.platform.reconnect.DefaultSignedStateValidator;
import com.swirlds.platform.reconnect.ReconnectHelper;
import com.swirlds.platform.reconnect.ReconnectProtocolResponder;
import com.swirlds.platform.reconnect.ReconnectThrottle;
import com.swirlds.platform.state.signed.SignedState;
import com.swirlds.platform.sync.ShadowGraphSynchronizer;
import com.swirlds.platform.sync.SimultaneousSyncThrottle;
import com.swirlds.platform.sync.SyncProtocolResponder;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;
import org.apache.commons.lang3.tuple.Pair;

/**
 * Encapsulates a sync-based gossip network.
 */
public class SyncNetwork extends GossipNetwork {

    private final ReconnectHelper reconnectHelper;
    private final PlatformMetrics platformMetrics;
    private final ShadowGraphSynchronizer shadowgraphSynchronizer;
    private final SyncManagerImpl syncManager;
    private final SimultaneousSyncThrottle simultaneousSyncThrottle;
    private final EventTaskCreator eventTaskCreator;
    private final SwirldsPlatform platform;
    private final SyncMetrics syncMetrics;
    private final StateManagementComponent stateManagementComponent;
    private final ReconnectThrottle reconnectThrottle;
    private final ReconnectMetrics reconnectMetrics;
    private final NetworkMetrics networkMetrics;

    public SyncNetwork(
            @NonNull final PlatformContext platformContext,
            @NonNull final ThreadManager threadManager,
            @NonNull final Crypto crypto,
            @NonNull final Settings settings,
            @NonNull final AddressBook initialAddressBook,
            @NonNull final NodeId selfId,
            @NonNull final SoftwareVersion appVersion,
            @NonNull final NetworkTopology topology,
            @NonNull final ConnectionTracker connectionTracker,
            @NonNull final ReconnectHelper reconnectHelper,
            @NonNull final PlatformMetrics platformMetrics,
            @NonNull final ShadowGraphSynchronizer shadowgraphSynchronizer,
            @NonNull final SyncManagerImpl syncManager,
            @NonNull final SimultaneousSyncThrottle simultaneousSyncThrottle,
            @NonNull final EventTaskCreator eventTaskCreator,
            @NonNull final SwirldsPlatform platform,
            @NonNull final SyncMetrics syncMetrics,
            @NonNull final StateManagementComponent stateManagementComponent,
            @NonNull final ReconnectThrottle reconnectThrottle,
            @NonNull final ReconnectMetrics reconnectMetrics,
            @NonNull final NetworkMetrics networkMetrics) {

        super(
                platformContext,
                threadManager,
                crypto,
                settings,
                initialAddressBook,
                selfId,
                appVersion,
                topology,
                connectionTracker);

        this.reconnectHelper = throwArgNull(reconnectHelper, "reconnectHelper");
        this.platformMetrics = throwArgNull(platformMetrics, "platformMetrics");
        this.shadowgraphSynchronizer = throwArgNull(shadowgraphSynchronizer, "shadowgraphSynchronizer");
        this.syncManager = throwArgNull(syncManager, "syncManager");
        this.simultaneousSyncThrottle = throwArgNull(simultaneousSyncThrottle, "simultaneousSyncThrottle");
        this.eventTaskCreator = throwArgNull(eventTaskCreator, "eventTaskCreator");
        this.platform = throwArgNull(platform, "platform");
        this.syncMetrics = throwArgNull(syncMetrics, "syncMetrics");
        this.stateManagementComponent = throwArgNull(stateManagementComponent, "stateManagementComponent");
        this.reconnectThrottle = throwArgNull(reconnectThrottle, "reconnectThrottle");
        this.reconnectMetrics = throwArgNull(reconnectMetrics, "reconnectMetrics");
        this.networkMetrics = throwArgNull(networkMetrics, "networkMetrics");
    }

    /**
     * Spawn a thread to initiate syncs with other users
     */
    private void spawnSyncCaller(final int callerNumber, @NonNull final SharedConnectionLocks sharedConnectionLocks) {
        // create a caller that will run repeatedly to call random members other than selfId
        final SyncCaller syncCaller = new SyncCaller(
                platform,
                initialAddressBook,
                selfId,
                callerNumber,
                reconnectHelper,
                new DefaultSignedStateValidator(),
                platformMetrics,
                shadowgraphSynchronizer,
                simultaneousSyncThrottle,
                syncManager,
                sharedConnectionLocks,
                eventTaskCreator);

        /* the thread that repeatedly initiates syncs with other members */
        final Thread syncCallerThread = new ThreadConfiguration(threadManager)
                .setPriority(settings.getThreadPrioritySync())
                .setNodeId(selfId.getId())
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
    public void start() {
        final StaticConnectionManagers connectionManagers = startCommonNetwork();

        SharedConnectionLocks sharedConnectionLocks = new SharedConnectionLocks(topology, connectionManagers);
        final MultiProtocolResponder protocolHandlers = new MultiProtocolResponder(List.of(
                ProtocolMapping.map(
                        UnidirectionalProtocols.SYNC.getInitialByte(),
                        new SyncProtocolResponder(
                                simultaneousSyncThrottle,
                                shadowgraphSynchronizer,
                                syncManager,
                                syncManager::shouldAcceptSync,
                                syncMetrics)),
                ProtocolMapping.map(
                        UnidirectionalProtocols.RECONNECT.getInitialByte(),
                        new ReconnectProtocolResponder(
                                threadManager,
                                stateManagementComponent,
                                settings.getReconnect(),
                                reconnectThrottle,
                                reconnectMetrics)),
                ProtocolMapping.map(
                        UnidirectionalProtocols.HEARTBEAT.getInitialByte(),
                        HeartbeatProtocolResponder::heartbeatProtocol)));

        for (final NodeId otherId : topology.getNeighbors()) {
            // create and start new threads to listen for incoming sync requests
            new StoppableThreadConfiguration<>(threadManager)
                    .setPriority(Thread.NORM_PRIORITY)
                    .setNodeId(selfId.getId())
                    .setComponent(PLATFORM_THREAD_POOL_NAME)
                    .setOtherNodeId(otherId.getId())
                    .setThreadName("listener")
                    .setWork(new Listener(protocolHandlers, connectionManagers.getManager(otherId, false)))
                    .build()
                    .start();

            // create and start new thread to send heartbeats on the SyncCaller channels
            new StoppableThreadConfiguration<>(threadManager)
                    .setPriority(settings.getThreadPrioritySync())
                    .setNodeId(selfId.getId())
                    .setComponent(PLATFORM_THREAD_POOL_NAME)
                    .setThreadName("heartbeat")
                    .setOtherNodeId(otherId.getId())
                    .setWork(new HeartbeatSender(
                            otherId, sharedConnectionLocks, networkMetrics, PlatformConstructor.settingsProvider()))
                    .build()
                    .start();
        }

        // start the timing AFTER the initial pause
        platformContext.getMetrics().resetAll();
        // create and start threads to call other members
        for (int i = 0; i < settings.getMaxOutgoingSyncs(); i++) {
            spawnSyncCaller(i, sharedConnectionLocks);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void halt() {}

    /**
     * {@inheritDoc}
     */
    @Override
    public void stopGossip() {
        simultaneousSyncThrottle.waitForAllSyncsToFinish();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void startGossip() {
        // no-op
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public List<Pair<Clearable, String>> getClearables() {
        return List.of();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void loadFromSignedState(SignedState signedState) {
        // no-op
    }
}
