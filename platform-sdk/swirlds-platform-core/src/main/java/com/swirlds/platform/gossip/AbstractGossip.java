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

import static com.swirlds.platform.SwirldsPlatform.PLATFORM_THREAD_POOL_NAME;

import com.swirlds.base.state.LifecyclePhase;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.crypto.config.CryptoConfig;
import com.swirlds.common.notification.NotificationEngine;
import com.swirlds.common.system.NodeId;
import com.swirlds.common.system.SoftwareVersion;
import com.swirlds.common.system.address.Address;
import com.swirlds.common.system.address.AddressBook;
import com.swirlds.common.threading.framework.QueueThread;
import com.swirlds.common.threading.framework.config.StoppableThreadConfiguration;
import com.swirlds.common.threading.manager.ThreadManager;
import com.swirlds.common.time.Time;
import com.swirlds.platform.Consensus;
import com.swirlds.platform.Crypto;
import com.swirlds.platform.FreezeManager;
import com.swirlds.platform.PlatformConstructor;
import com.swirlds.platform.Settings;
import com.swirlds.platform.StartUpEventFrozenManager;
import com.swirlds.platform.StaticSettingsProvider;
import com.swirlds.platform.components.CriticalQuorum;
import com.swirlds.platform.components.EventCreationRules;
import com.swirlds.platform.components.EventMapper;
import com.swirlds.platform.components.EventTaskCreator;
import com.swirlds.platform.components.state.StateManagementComponent;
import com.swirlds.platform.event.EventIntakeTask;
import com.swirlds.platform.gossip.chatter.config.ChatterConfig;
import com.swirlds.platform.gossip.shadowgraph.ShadowGraph;
import com.swirlds.platform.gossip.sync.SyncManagerImpl;
import com.swirlds.platform.gossip.sync.config.SyncConfig;
import com.swirlds.platform.metrics.EventIntakeMetrics;
import com.swirlds.platform.metrics.ReconnectMetrics;
import com.swirlds.platform.metrics.SyncMetrics;
import com.swirlds.platform.network.ConnectionTracker;
import com.swirlds.platform.network.NetworkMetrics;
import com.swirlds.platform.network.connectivity.ConnectionServer;
import com.swirlds.platform.network.connectivity.InboundConnectionHandler;
import com.swirlds.platform.network.connectivity.OutboundConnectionCreator;
import com.swirlds.platform.network.connectivity.SocketFactory;
import com.swirlds.platform.network.topology.NetworkTopology;
import com.swirlds.platform.network.topology.StaticConnectionManagers;
import com.swirlds.platform.network.topology.StaticTopology;
import com.swirlds.platform.reconnect.ReconnectHelper;
import com.swirlds.platform.reconnect.ReconnectThrottle;
import com.swirlds.platform.state.SwirldStateManager;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Boilerplate code for gossip.
 */
public abstract class AbstractGossip implements Gossip {

    private LifecyclePhase lifecyclePhase = LifecyclePhase.NOT_STARTED;

    protected final PlatformContext platformContext;
    protected final ThreadManager threadManager;
    protected final Time time;
    protected final Crypto crypto;
    protected final NotificationEngine notificationEngine;
    protected final AddressBook addressBook;
    protected final NodeId selfId;
    protected final NetworkTopology topology;
    protected final Settings settings = Settings.getInstance();
    protected final CriticalQuorum criticalQuorum;
    protected final NetworkMetrics networkMetrics;
    protected final SyncMetrics syncMetrics;
    protected final ShadowGraph shadowGraph;
    protected final EventTaskCreator eventTaskCreator;
    protected final ReconnectHelper reconnectHelper;
    protected final StaticConnectionManagers connectionManagers;
    protected final AtomicReference<Consensus> consensusRef;
    protected final QueueThread<EventIntakeTask> intakeQueue;
    protected final FreezeManager freezeManager;
    protected final StartUpEventFrozenManager startUpEventFrozenManager;
    protected final FallenBehindManagerImpl fallenBehindManager;
    protected final SwirldStateManager swirldStateManager;
    protected final SyncManagerImpl syncManager;
    protected final ReconnectThrottle reconnectThrottle;
    protected final StateManagementComponent stateManagementComponent;
    protected final ReconnectMetrics reconnectMetrics;
    protected final EventMapper eventMapper;
    protected final EventIntakeMetrics eventIntakeMetrics;

    // TODO: things to construct internally:
    //  - connection tracker
    //  - topology

    // TODO don't start stuff in the constructor

    public AbstractGossip(
            @NonNull final PlatformContext platformContext,
            @NonNull final ThreadManager threadManager,
            @NonNull final Time time,
            @NonNull final Crypto crypto,
            @NonNull final NotificationEngine notificationEngine,
            @NonNull final AddressBook addressBook,
            @NonNull final NodeId selfId,
            @NonNull final SoftwareVersion appVersion,
            @NonNull final ConnectionTracker connectionTracker,
            @NonNull final ShadowGraph shadowGraph,
            @NonNull final ReconnectHelper reconnectHelper,
            @NonNull final AtomicReference<Consensus> consensusRef,
            @NonNull final QueueThread<EventIntakeTask> intakeQueue,
            @NonNull final FreezeManager freezeManager,
            @NonNull final StartUpEventFrozenManager startUpEventFrozenManager,
            @NonNull final FallenBehindManagerImpl fallenBehindManager,
            @NonNull final SwirldStateManager swirldStateManager,
            @NonNull final ReconnectThrottle reconnectThrottle,
            @NonNull final StateManagementComponent stateManagementComponent,
            @NonNull final ReconnectMetrics reconnectMetrics,
            @NonNull final EventMapper eventMapper,
            @NonNull final EventIntakeMetrics eventIntakeMetrics) {

        this.platformContext = Objects.requireNonNull(platformContext);
        this.threadManager = Objects.requireNonNull(threadManager);
        this.time = Objects.requireNonNull(time);
        this.crypto = Objects.requireNonNull(crypto);
        this.notificationEngine = Objects.requireNonNull(notificationEngine);
        this.addressBook = Objects.requireNonNull(addressBook);
        this.selfId = Objects.requireNonNull(selfId);
        this.shadowGraph = Objects.requireNonNull(shadowGraph);
        this.reconnectHelper = Objects.requireNonNull(reconnectHelper);
        this.consensusRef = Objects.requireNonNull(consensusRef);
        this.intakeQueue = Objects.requireNonNull(intakeQueue);
        this.freezeManager = Objects.requireNonNull(freezeManager);
        this.startUpEventFrozenManager = Objects.requireNonNull(startUpEventFrozenManager);
        this.fallenBehindManager = Objects.requireNonNull(fallenBehindManager);
        this.swirldStateManager = Objects.requireNonNull(swirldStateManager);
        this.reconnectThrottle = Objects.requireNonNull(reconnectThrottle);
        this.stateManagementComponent = Objects.requireNonNull(stateManagementComponent);
        this.reconnectMetrics = Objects.requireNonNull(reconnectMetrics);
        this.eventMapper = Objects.requireNonNull(eventMapper);
        this.eventIntakeMetrics = Objects.requireNonNull(eventIntakeMetrics);

        criticalQuorum = buildCriticalQuorum();

        topology = new StaticTopology(
                selfId,
                addressBook.getSize(),
                settings.getNumConnections(),
                // unidirectional connections are ONLY used for old-style syncs, that don't run as a protocol
                unidirectionalConnectionsEnabled());

        final SyncConfig syncConfig = platformContext.getConfiguration().getConfigData(SyncConfig.class);

        final SocketFactory socketFactory = PlatformConstructor.socketFactory(
                crypto.getKeysAndCerts(), platformContext.getConfiguration().getConfigData(CryptoConfig.class));
        // create an instance that can create new outbound connections
        final ChatterConfig chatterConfig = platformContext.getConfiguration().getConfigData(ChatterConfig.class);
        final OutboundConnectionCreator connectionCreator = new OutboundConnectionCreator(
                selfId,
                StaticSettingsProvider.getSingleton(),
                connectionTracker,
                socketFactory,
                addressBook,
                // only do a version check for old-style sync
                !chatterConfig.useChatter() && !syncConfig.syncAsProtocolEnabled(),
                appVersion);
        connectionManagers = new StaticConnectionManagers(topology, connectionCreator);
        final InboundConnectionHandler inboundConnectionHandler = new InboundConnectionHandler(
                connectionTracker,
                selfId,
                addressBook,
                connectionManagers::newConnection,
                StaticSettingsProvider.getSingleton(),
                // only do a version check for old-style sync
                !chatterConfig.useChatter() && !syncConfig.syncAsProtocolEnabled(),
                appVersion);
        // allow other members to create connections to me
        final Address address = addressBook.getAddress(selfId.id());
        final ConnectionServer connectionServer = new ConnectionServer(
                threadManager,
                address.getListenAddressIpv4(),
                address.getListenPortIpv4(),
                socketFactory,
                inboundConnectionHandler::handle);
        new StoppableThreadConfiguration<>(threadManager)
                .setPriority(settings.getThreadPrioritySync())
                .setNodeId(selfId.id())
                .setComponent(PLATFORM_THREAD_POOL_NAME)
                .setThreadName("connectionServer")
                .setWork(connectionServer)
                .build()
                .start();

        syncManager = new SyncManagerImpl(
                platformContext.getMetrics(),
                intakeQueue,
                topology.getConnectionGraph(),
                selfId,
                new EventCreationRules(
                        List.of(swirldStateManager.getTransactionPool(), startUpEventFrozenManager, freezeManager)),
                criticalQuorum,
                addressBook,
                fallenBehindManager);

        eventTaskCreator = new EventTaskCreator(
                eventMapper,
                // hashgraph and state get separate copies of the address book
                addressBook,
                selfId,
                eventIntakeMetrics,
                intakeQueue,
                StaticSettingsProvider.getSingleton(),
                syncManager,
                ThreadLocalRandom::current);

        networkMetrics = new NetworkMetrics(platformContext.getMetrics(), selfId, addressBook.getSize());
        syncMetrics = new SyncMetrics(platformContext.getMetrics());
    }

    /**
     * If true, use unidirectional connections between nodes.
     */
    protected abstract boolean unidirectionalConnectionsEnabled();

    /**
     * Build the critical quorum object.
     */
    protected abstract CriticalQuorum buildCriticalQuorum();

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public LifecyclePhase getLifecyclePhase() {
        return lifecyclePhase;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void start() {
        throwIfNotInPhase(LifecyclePhase.NOT_STARTED);
        lifecyclePhase = LifecyclePhase.STARTED;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void stop() {
        throwIfNotInPhase(LifecyclePhase.STARTED);
        lifecyclePhase = LifecyclePhase.STOPPED;
        syncManager.haltRequestedObserver("stopping gossip");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void restFallenBehind() {
        syncManager.resetFallenBehind();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean hasFallenBehind() {
        return syncManager.hasFallenBehind();
    }
}
