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

import static com.swirlds.logging.LogMarker.EXCEPTION;
import static com.swirlds.platform.SwirldsPlatform.PLATFORM_THREAD_POOL_NAME;

import com.swirlds.base.state.LifecyclePhase;
import com.swirlds.base.state.Startable;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.crypto.config.CryptoConfig;
import com.swirlds.common.merkle.synchronization.config.ReconnectConfig;
import com.swirlds.common.system.NodeId;
import com.swirlds.common.system.SoftwareVersion;
import com.swirlds.common.system.address.Address;
import com.swirlds.common.system.address.AddressBook;
import com.swirlds.common.threading.framework.QueueThread;
import com.swirlds.common.threading.framework.config.StoppableThreadConfiguration;
import com.swirlds.common.threading.manager.ThreadManager;
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
import com.swirlds.platform.gossip.sync.SyncManagerImpl;
import com.swirlds.platform.metrics.EventIntakeMetrics;
import com.swirlds.platform.metrics.ReconnectMetrics;
import com.swirlds.platform.metrics.SyncMetrics;
import com.swirlds.platform.network.Connection;
import com.swirlds.platform.network.ConnectionTracker;
import com.swirlds.platform.network.NetworkMetrics;
import com.swirlds.platform.network.connectivity.ConnectionServer;
import com.swirlds.platform.network.connectivity.InboundConnectionHandler;
import com.swirlds.platform.network.connectivity.OutboundConnectionCreator;
import com.swirlds.platform.network.connectivity.SocketFactory;
import com.swirlds.platform.network.topology.NetworkTopology;
import com.swirlds.platform.network.topology.StaticConnectionManagers;
import com.swirlds.platform.network.topology.StaticTopology;
import com.swirlds.platform.observers.EventObserverDispatcher;
import com.swirlds.platform.reconnect.ReconnectHelper;
import com.swirlds.platform.reconnect.ReconnectLearnerFactory;
import com.swirlds.platform.reconnect.ReconnectLearnerThrottle;
import com.swirlds.platform.reconnect.ReconnectThrottle;
import com.swirlds.platform.state.SwirldStateManager;
import com.swirlds.platform.state.signed.SignedState;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Boilerplate code for gossip.
 */
public abstract class AbstractGossip implements ConnectionTracker, Gossip {

    private static final Logger logger = LogManager.getLogger(AbstractGossip.class);

    private LifecyclePhase lifecyclePhase = LifecyclePhase.NOT_STARTED;

    protected final PlatformContext platformContext;
    protected final AddressBook addressBook;
    protected final NodeId selfId;
    protected final NetworkTopology topology;
    protected final Settings settings = Settings.getInstance();
    protected final CriticalQuorum criticalQuorum;
    protected final NetworkMetrics networkMetrics;
    protected final SyncMetrics syncMetrics;
    protected final EventTaskCreator eventTaskCreator;
    protected final ReconnectHelper reconnectHelper;
    protected final StaticConnectionManagers connectionManagers;
    protected final FallenBehindManagerImpl fallenBehindManager;
    protected final SyncManagerImpl syncManager;
    protected final ReconnectThrottle reconnectThrottle;
    protected final ReconnectMetrics reconnectMetrics;
    protected final Runnable updatePlatformStatus;
    protected final List<Startable> thingsToStart = new ArrayList<>();

    /** the number of active connections this node has to other nodes */
    private final AtomicInteger activeConnectionNumber = new AtomicInteger(0);

    /**
     * Builds the gossip engine, depending on which flavor is requested in the configuration.
     *
     * @param platformContext               the platform context
     * @param threadManager                 the thread manager
     * @param crypto                        can be used to sign things
     * @param addressBook                   the current address book
     * @param selfId                        this node's ID
     * @param appVersion                    the version of the app
     * @param intakeQueue                   the event intake queue
     * @param freezeManager                 handles freezes
     * @param startUpEventFrozenManager     prevents event creation during startup
     * @param swirldStateManager            manages the mutable state
     * @param stateManagementComponent      manages the lifecycle of the state queue
     * @param eventObserverDispatcher       the object used to wire event intake
     * @param eventMapper                   a data structure used to track the most recent event from each node
     * @param eventIntakeMetrics            metrics for event intake
     * @param updatePlatformStatus          a method that updates the platform status, when called
     * @param loadReconnectState            a method that should be called when a state from reconnect is obtained
     * @param clearAllPipelinesForReconnect this method should be called to clear all pipelines prior to a reconnect
     */
    protected AbstractGossip(
            @NonNull final PlatformContext platformContext,
            @NonNull final ThreadManager threadManager,
            @NonNull final Crypto crypto,
            @NonNull final AddressBook addressBook,
            @NonNull final NodeId selfId,
            @NonNull final SoftwareVersion appVersion,
            @NonNull final QueueThread<EventIntakeTask> intakeQueue,
            @NonNull final FreezeManager freezeManager,
            @NonNull final StartUpEventFrozenManager startUpEventFrozenManager,
            @NonNull final SwirldStateManager swirldStateManager,
            @NonNull final StateManagementComponent stateManagementComponent,
            @NonNull final EventMapper eventMapper,
            @NonNull final EventIntakeMetrics eventIntakeMetrics,
            @NonNull final EventObserverDispatcher eventObserverDispatcher,
            @NonNull final Runnable updatePlatformStatus,
            @NonNull final Consumer<SignedState> loadReconnectState,
            @NonNull final Runnable clearAllPipelinesForReconnect) {

        this.platformContext = Objects.requireNonNull(platformContext);
        this.addressBook = Objects.requireNonNull(addressBook);
        this.selfId = Objects.requireNonNull(selfId);
        this.updatePlatformStatus = Objects.requireNonNull(updatePlatformStatus);

        criticalQuorum = buildCriticalQuorum();
        eventObserverDispatcher.addObserver(criticalQuorum);

        topology = new StaticTopology(
                addressBook, selfId, settings.getNumConnections(), unidirectionalConnectionsEnabled());

        final SocketFactory socketFactory = PlatformConstructor.socketFactory(
                crypto.getKeysAndCerts(), platformContext.getConfiguration().getConfigData(CryptoConfig.class));
        // create an instance that can create new outbound connections
        final OutboundConnectionCreator connectionCreator = new OutboundConnectionCreator(
                selfId,
                StaticSettingsProvider.getSingleton(),
                this,
                socketFactory,
                addressBook,
                shouldDoVersionCheck(),
                appVersion);
        connectionManagers = new StaticConnectionManagers(topology, connectionCreator);
        final InboundConnectionHandler inboundConnectionHandler = new InboundConnectionHandler(
                this,
                selfId,
                addressBook,
                connectionManagers::newConnection,
                StaticSettingsProvider.getSingleton(),
                shouldDoVersionCheck(),
                appVersion);
        // allow other members to create connections to me
        final Address address = addressBook.getAddress(selfId);
        final ConnectionServer connectionServer = new ConnectionServer(
                threadManager,
                address.getListenAddressIpv4(),
                address.getListenPortIpv4(),
                socketFactory,
                inboundConnectionHandler::handle);
        thingsToStart.add(new StoppableThreadConfiguration<>(threadManager)
                .setPriority(settings.getThreadPrioritySync())
                .setNodeId(selfId)
                .setComponent(PLATFORM_THREAD_POOL_NAME)
                .setThreadName("connectionServer")
                .setWork(connectionServer)
                .build());

        fallenBehindManager = buildFallenBehindManager();

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
                addressBook,
                selfId,
                eventIntakeMetrics,
                intakeQueue,
                StaticSettingsProvider.getSingleton(),
                syncManager,
                ThreadLocalRandom::current);

        final ReconnectConfig reconnectConfig =
                platformContext.getConfiguration().getConfigData(ReconnectConfig.class);
        reconnectThrottle = new ReconnectThrottle(reconnectConfig);

        networkMetrics = new NetworkMetrics(platformContext.getMetrics(), selfId, addressBook);
        platformContext.getMetrics().addUpdater(networkMetrics::update);
        syncMetrics = new SyncMetrics(platformContext.getMetrics());

        reconnectMetrics = new ReconnectMetrics(platformContext.getMetrics());

        reconnectHelper = new ReconnectHelper(
                this::pause,
                clearAllPipelinesForReconnect::run,
                swirldStateManager::getConsensusState,
                stateManagementComponent::getLastCompleteRound,
                new ReconnectLearnerThrottle(selfId, reconnectConfig),
                loadReconnectState,
                new ReconnectLearnerFactory(
                        platformContext,
                        threadManager,
                        addressBook,
                        reconnectConfig.asyncStreamTimeoutMilliseconds(),
                        reconnectMetrics));
    }

    /**
     * Build the fallen behind manager.
     */
    @NonNull
    protected abstract FallenBehindManagerImpl buildFallenBehindManager();

    /**
     * If true, use unidirectional connections between nodes.
     */
    protected abstract boolean unidirectionalConnectionsEnabled();

    /**
     * Build the critical quorum object.
     */
    @NonNull
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
        thingsToStart.forEach(Startable::start);
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
    public void resetFallenBehind() {
        syncManager.resetFallenBehind();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean hasFallenBehind() {
        return syncManager.hasFallenBehind();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void newConnectionOpened(@NonNull final Connection sc) {
        Objects.requireNonNull(sc);

        activeConnectionNumber.getAndIncrement();
        updatePlatformStatus.run();
        networkMetrics.connectionEstablished(sc);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void connectionClosed(final boolean outbound, @NonNull final Connection conn) {
        Objects.requireNonNull(conn);

        final int connectionNumber = activeConnectionNumber.decrementAndGet();
        if (connectionNumber < 0) {
            logger.error(EXCEPTION.getMarker(), "activeConnectionNumber is {}, this is a bug!", connectionNumber);
        }
        updatePlatformStatus.run();

        networkMetrics.recordDisconnect(conn);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int activeConnectionNumber() {
        return activeConnectionNumber.get();
    }

    /**
     * Should the network layer do a version check prior to initiating a connection?
     *
     * @return true if a version check should be done
     */
    protected abstract boolean shouldDoVersionCheck();
}
