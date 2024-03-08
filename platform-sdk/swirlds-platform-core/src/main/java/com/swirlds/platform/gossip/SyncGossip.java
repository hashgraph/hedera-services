/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

import com.swirlds.base.state.Lifecycle;
import com.swirlds.base.state.LifecyclePhase;
import com.swirlds.base.state.Startable;
import com.swirlds.base.time.Time;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.merkle.synchronization.config.ReconnectConfig;
import com.swirlds.common.notification.NotificationEngine;
import com.swirlds.common.platform.NodeId;
import com.swirlds.common.threading.framework.StoppableThread;
import com.swirlds.common.threading.framework.config.StoppableThreadConfiguration;
import com.swirlds.common.threading.manager.ThreadManager;
import com.swirlds.common.threading.pool.CachedPoolParallelExecutor;
import com.swirlds.common.threading.pool.ParallelExecutor;
import com.swirlds.platform.config.BasicConfig;
import com.swirlds.platform.config.StateConfig;
import com.swirlds.platform.config.ThreadConfig;
import com.swirlds.platform.crypto.KeysAndCerts;
import com.swirlds.platform.event.GossipEvent;
import com.swirlds.platform.eventhandling.EventConfig;
import com.swirlds.platform.gossip.shadowgraph.Shadowgraph;
import com.swirlds.platform.gossip.shadowgraph.ShadowgraphSynchronizer;
import com.swirlds.platform.gossip.sync.SyncManagerImpl;
import com.swirlds.platform.gossip.sync.config.SyncConfig;
import com.swirlds.platform.gossip.sync.protocol.SyncProtocol;
import com.swirlds.platform.heartbeats.HeartbeatProtocol;
import com.swirlds.platform.metrics.ReconnectMetrics;
import com.swirlds.platform.metrics.SyncMetrics;
import com.swirlds.platform.network.Connection;
import com.swirlds.platform.network.ConnectionTracker;
import com.swirlds.platform.network.NetworkMetrics;
import com.swirlds.platform.network.NetworkUtils;
import com.swirlds.platform.network.communication.NegotiationProtocols;
import com.swirlds.platform.network.communication.NegotiatorThread;
import com.swirlds.platform.network.communication.handshake.HashCompareHandshake;
import com.swirlds.platform.network.communication.handshake.VersionCompareHandshake;
import com.swirlds.platform.network.connectivity.ConnectionServer;
import com.swirlds.platform.network.connectivity.InboundConnectionHandler;
import com.swirlds.platform.network.connectivity.OutboundConnectionCreator;
import com.swirlds.platform.network.connectivity.SocketFactory;
import com.swirlds.platform.network.topology.NetworkTopology;
import com.swirlds.platform.network.topology.StaticConnectionManagers;
import com.swirlds.platform.network.topology.StaticTopology;
import com.swirlds.platform.reconnect.DefaultSignedStateValidator;
import com.swirlds.platform.reconnect.ReconnectController;
import com.swirlds.platform.reconnect.ReconnectHelper;
import com.swirlds.platform.reconnect.ReconnectLearnerFactory;
import com.swirlds.platform.reconnect.ReconnectLearnerThrottle;
import com.swirlds.platform.reconnect.ReconnectProtocol;
import com.swirlds.platform.reconnect.ReconnectThrottle;
import com.swirlds.platform.reconnect.emergency.EmergencyReconnectProtocol;
import com.swirlds.platform.recovery.EmergencyRecoveryManager;
import com.swirlds.platform.state.SwirldStateManager;
import com.swirlds.platform.state.nexus.SignedStateNexus;
import com.swirlds.platform.state.signed.ReservedSignedState;
import com.swirlds.platform.state.signed.SignedState;
import com.swirlds.platform.system.SoftwareVersion;
import com.swirlds.platform.system.address.Address;
import com.swirlds.platform.system.address.AddressBook;
import com.swirlds.platform.system.status.PlatformStatusManager;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * Boilerplate code for gossip.
 */
public class SyncGossip implements ConnectionTracker, Lifecycle {
    private LifecyclePhase lifecyclePhase = LifecyclePhase.NOT_STARTED;

    private final ReconnectController reconnectController;

    private final AtomicBoolean gossipHalted = new AtomicBoolean(false);
    private final SyncPermitProvider syncPermitProvider;
    protected final SyncConfig syncConfig;
    protected final ShadowgraphSynchronizer syncShadowgraphSynchronizer;

    /**
     * Keeps track of the number of events in the intake pipeline from each peer
     */
    private final IntakeEventCounter intakeEventCounter;

    /**
     * A list of threads that execute the sync protocol using bidirectional connections
     */
    private final List<StoppableThread> syncProtocolThreads = new ArrayList<>();

    protected final PlatformContext platformContext;
    protected final AddressBook addressBook;
    protected final NodeId selfId;
    protected final NetworkTopology topology;
    protected final NetworkMetrics networkMetrics;
    protected final ReconnectHelper reconnectHelper;
    protected final StaticConnectionManagers connectionManagers;
    protected final FallenBehindManagerImpl fallenBehindManager;
    protected final SyncManagerImpl syncManager;
    protected final ReconnectThrottle reconnectThrottle;
    protected final ReconnectMetrics reconnectMetrics;
    protected final PlatformStatusManager platformStatusManager;

    protected final List<Startable> thingsToStart = new ArrayList<>();

    /**
     * Builds the gossip engine, depending on which flavor is requested in the configuration.
     *
     * @param platformContext               the platform context
     * @param threadManager                 the thread manager
     * @param time                          the time object used to get the current time
     * @param keysAndCerts                  private keys and public certificates
     * @param notificationEngine            used to send notifications to the app
     * @param addressBook                   the current address book
     * @param selfId                        this node's ID
     * @param appVersion                    the version of the app
     * @param epochHash                     the epoch hash of the initial state
     * @param shadowGraph                   contains non-ancient events
     * @param emergencyRecoveryManager      handles emergency recovery
     * @param receivedEventHandler          handles events received from other nodes
     * @param intakeQueueSizeSupplier       a supplier for the size of the event intake queue
     * @param swirldStateManager            manages the mutable state
     * @param latestCompleteState           holds the latest signed state that has enough signatures to be verifiable
     * @param syncMetrics                   metrics for sync
     * @param platformStatusManager         the platform status manager
     * @param loadReconnectState            a method that should be called when a state from reconnect is obtained
     * @param clearAllPipelinesForReconnect this method should be called to clear all pipelines prior to a reconnect
     * @param intakeEventCounter            keeps track of the number of events in the intake pipeline from each peer
     * @param emergencyStateSupplier        returns the emergency state if available
     */
    protected SyncGossip(
            @NonNull final PlatformContext platformContext,
            @NonNull final ThreadManager threadManager,
            @NonNull final Time time,
            @NonNull final KeysAndCerts keysAndCerts,
            @NonNull final NotificationEngine notificationEngine,
            @NonNull final AddressBook addressBook,
            @NonNull final NodeId selfId,
            @NonNull final SoftwareVersion appVersion,
            @Nullable final Hash epochHash,
            @NonNull final Shadowgraph shadowGraph,
            @NonNull final EmergencyRecoveryManager emergencyRecoveryManager,
            @NonNull final Consumer<GossipEvent> receivedEventHandler,
            @NonNull final LongSupplier intakeQueueSizeSupplier,
            @NonNull final SwirldStateManager swirldStateManager,
            @NonNull final SignedStateNexus latestCompleteState,
            @NonNull final SyncMetrics syncMetrics,
            @NonNull final PlatformStatusManager platformStatusManager,
            @NonNull final Consumer<SignedState> loadReconnectState,
            @NonNull final Runnable clearAllPipelinesForReconnect,
            @NonNull final IntakeEventCounter intakeEventCounter,
            @NonNull final Supplier<ReservedSignedState> emergencyStateSupplier) {

        this.platformContext = Objects.requireNonNull(platformContext);
        this.addressBook = Objects.requireNonNull(addressBook);
        this.selfId = Objects.requireNonNull(selfId);
        this.platformStatusManager = Objects.requireNonNull(platformStatusManager);

        Objects.requireNonNull(time);

        final ThreadConfig threadConfig = platformContext.getConfiguration().getConfigData(ThreadConfig.class);

        final BasicConfig basicConfig = platformContext.getConfiguration().getConfigData(BasicConfig.class);

        topology = new StaticTopology(addressBook, selfId, basicConfig.numConnections());

        final SocketFactory socketFactory =
                NetworkUtils.createSocketFactory(selfId, addressBook, keysAndCerts, platformContext.getConfiguration());
        // create an instance that can create new outbound connections
        final OutboundConnectionCreator connectionCreator = new OutboundConnectionCreator(
                platformContext, selfId, this, socketFactory, addressBook, shouldDoVersionCheck(), appVersion);
        connectionManagers = new StaticConnectionManagers(topology, connectionCreator);
        final InboundConnectionHandler inboundConnectionHandler = new InboundConnectionHandler(
                platformContext,
                this,
                selfId,
                addressBook,
                connectionManagers::newConnection,
                shouldDoVersionCheck(),
                appVersion,
                time);
        // allow other members to create connections to me
        final Address address = addressBook.getAddress(selfId);
        final ConnectionServer connectionServer = new ConnectionServer(
                threadManager, address.getListenPort(), socketFactory, inboundConnectionHandler::handle);
        thingsToStart.add(new StoppableThreadConfiguration<>(threadManager)
                .setPriority(threadConfig.threadPrioritySync())
                .setNodeId(selfId)
                .setComponent(PLATFORM_THREAD_POOL_NAME)
                .setThreadName("connectionServer")
                .setWork(connectionServer)
                .build());

        fallenBehindManager = buildFallenBehindManager();

        syncManager = new SyncManagerImpl(
                platformContext,
                intakeQueueSizeSupplier,
                fallenBehindManager,
                platformContext.getConfiguration().getConfigData(EventConfig.class));

        final ReconnectConfig reconnectConfig =
                platformContext.getConfiguration().getConfigData(ReconnectConfig.class);

        reconnectThrottle = new ReconnectThrottle(reconnectConfig, time);

        networkMetrics = new NetworkMetrics(platformContext.getMetrics(), selfId, addressBook);
        platformContext.getMetrics().addUpdater(networkMetrics::update);

        reconnectMetrics = new ReconnectMetrics(platformContext.getMetrics(), addressBook);

        final StateConfig stateConfig = platformContext.getConfiguration().getConfigData(StateConfig.class);
        reconnectHelper = new ReconnectHelper(
                this::pause,
                clearAllPipelinesForReconnect::run,
                swirldStateManager::getConsensusState,
                latestCompleteState::getRound,
                new ReconnectLearnerThrottle(time, selfId, reconnectConfig),
                loadReconnectState,
                new ReconnectLearnerFactory(
                        platformContext,
                        threadManager,
                        addressBook,
                        reconnectConfig.asyncStreamTimeout(),
                        reconnectMetrics),
                stateConfig);
        this.intakeEventCounter = Objects.requireNonNull(intakeEventCounter);

        final EventConfig eventConfig = platformContext.getConfiguration().getConfigData(EventConfig.class);

        syncConfig = platformContext.getConfiguration().getConfigData(SyncConfig.class);

        final ParallelExecutor shadowgraphExecutor = new CachedPoolParallelExecutor(threadManager, "node-sync");
        thingsToStart.add(shadowgraphExecutor);
        syncShadowgraphSynchronizer = new ShadowgraphSynchronizer(
                platformContext,
                shadowGraph,
                addressBook.getSize(),
                syncMetrics,
                receivedEventHandler,
                syncManager,
                intakeEventCounter,
                shadowgraphExecutor);

        reconnectController = new ReconnectController(reconnectConfig, threadManager, reconnectHelper, this::resume);

        final ProtocolConfig protocolConfig = platformContext.getConfiguration().getConfigData(ProtocolConfig.class);

        final Duration hangingThreadDuration = basicConfig.hangingThreadDuration();

        final int permitCount;
        if (syncConfig.onePermitPerPeer()) {
            permitCount = addressBook.getSize() - 1;
        } else {
            permitCount = syncConfig.syncProtocolPermitCount();
        }

        syncPermitProvider = new SyncPermitProvider(permitCount, intakeEventCounter);

        if (emergencyRecoveryManager.isEmergencyStateRequired()) {
            // If we still need an emergency recovery state, we need it via emergency reconnect.
            // Start the helper first so that it is ready to receive a connection to perform reconnect with when the
            // protocol is initiated.
            thingsToStart.addFirst(reconnectController::start);
        }

        buildSyncProtocolThreads(
                platformContext,
                threadManager,
                time,
                notificationEngine,
                selfId,
                appVersion,
                epochHash,
                emergencyRecoveryManager,
                intakeQueueSizeSupplier,
                latestCompleteState,
                syncMetrics,
                platformStatusManager,
                emergencyStateSupplier,
                hangingThreadDuration,
                protocolConfig,
                reconnectConfig,
                eventConfig);

        thingsToStart.add(() -> syncProtocolThreads.forEach(StoppableThread::start));
    }

    private void buildSyncProtocolThreads(
            final PlatformContext platformContext,
            final ThreadManager threadManager,
            final Time time,
            final NotificationEngine notificationEngine,
            final NodeId selfId,
            final SoftwareVersion appVersion,
            final Hash epochHash,
            final EmergencyRecoveryManager emergencyRecoveryManager,
            final LongSupplier intakeQueueSizeSupplier,
            final SignedStateNexus latestCompleteState,
            final SyncMetrics syncMetrics,
            final PlatformStatusManager platformStatusManager,
            final Supplier<ReservedSignedState> emergencyStateSupplier,
            final Duration hangingThreadDuration,
            final ProtocolConfig protocolConfig,
            final ReconnectConfig reconnectConfig,
            final EventConfig eventConfig) {
        for (final NodeId otherId : topology.getNeighbors()) {
            syncProtocolThreads.add(new StoppableThreadConfiguration<>(threadManager)
                    .setPriority(Thread.NORM_PRIORITY)
                    .setNodeId(selfId)
                    .setComponent(PLATFORM_THREAD_POOL_NAME)
                    .setOtherNodeId(otherId)
                    .setThreadName("SyncProtocolWith" + otherId)
                    .setHangingThreadPeriod(hangingThreadDuration)
                    .setWork(new NegotiatorThread(
                            connectionManagers.getManager(otherId, topology.shouldConnectTo(otherId)),
                            syncConfig.syncSleepAfterFailedNegotiation(),
                            List.of(
                                    new VersionCompareHandshake(
                                            appVersion, !protocolConfig.tolerateMismatchedVersion()),
                                    new HashCompareHandshake(epochHash, !protocolConfig.tolerateMismatchedEpochHash())),
                            new NegotiationProtocols(List.of(
                                    new HeartbeatProtocol(
                                            otherId,
                                            Duration.ofMillis(syncConfig.syncProtocolHeartbeatPeriod()),
                                            networkMetrics,
                                            time),
                                    new EmergencyReconnectProtocol(
                                            platformContext,
                                            time,
                                            threadManager,
                                            notificationEngine,
                                            otherId,
                                            emergencyRecoveryManager,
                                            reconnectThrottle,
                                            emergencyStateSupplier,
                                            reconnectConfig.asyncStreamTimeout(),
                                            reconnectMetrics,
                                            reconnectController,
                                            platformStatusManager,
                                            platformContext.getConfiguration()),
                                    new ReconnectProtocol(
                                            platformContext,
                                            threadManager,
                                            otherId,
                                            reconnectThrottle,
                                            () -> latestCompleteState.getState("SwirldsPlatform: ReconnectProtocol"),
                                            reconnectConfig.asyncStreamTimeout(),
                                            reconnectMetrics,
                                            reconnectController,
                                            new DefaultSignedStateValidator(platformContext),
                                            fallenBehindManager,
                                            platformStatusManager,
                                            platformContext.getConfiguration(),
                                            time),
                                    new SyncProtocol(
                                            platformContext,
                                            otherId,
                                            syncShadowgraphSynchronizer,
                                            fallenBehindManager,
                                            syncPermitProvider,
                                            gossipHalted::get,
                                            () -> intakeQueueSizeSupplier.getAsLong()
                                                    >= eventConfig.eventIntakeQueueThrottleSize(),
                                            Duration.ZERO,
                                            syncMetrics,
                                            platformStatusManager)))))
                    .build());
        }
    }

    /**
     * Build the fallen behind manager.
     */
    @NonNull
    protected FallenBehindManagerImpl buildFallenBehindManager() {
        return new FallenBehindManagerImpl(
                addressBook,
                selfId,
                topology.getConnectionGraph(),
                platformStatusManager,
                // this fallen behind impl is different from that of
                // SingleNodeSyncGossip which was a no-op. Same for the pause/resume impls
                // which only logged (but they do more here)
                () -> getReconnectController().start(),
                platformContext.getConfiguration().getConfigData(ReconnectConfig.class));
    }

    /**
     * Get the reconnect controller. This method is needed to break a circular dependency.
     */
    private ReconnectController getReconnectController() {
        return reconnectController;
    }

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
        gossipHalted.set(true);
        // wait for all existing syncs to stop. no new ones will be started, since gossip has been halted, and
        // we've fallen behind
        syncPermitProvider.waitForAllSyncsToFinish();
        for (final StoppableThread thread : syncProtocolThreads) {
            thread.stop();
        }
    }

    /**
     * This method is called when the node has finished a reconnect
     */
    public void resetFallenBehind() {
        syncManager.resetFallenBehind();
    }

    /**
     * Check if we have fallen behind.
     */
    public boolean hasFallenBehind() {
        return syncManager.hasFallenBehind();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void newConnectionOpened(@NonNull final Connection sc) {
        Objects.requireNonNull(sc);
        networkMetrics.connectionEstablished(sc);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void connectionClosed(final boolean outbound, @NonNull final Connection conn) {
        Objects.requireNonNull(conn);
        networkMetrics.recordDisconnect(conn);
    }

    /**
     * Should the network layer do a version check prior to initiating a connection?
     *
     * @return true if a version check should be done
     */
    protected boolean shouldDoVersionCheck() {
        return false;
    }

    /**
     * Stop gossiping until {@link #resume()} is called. If called when already paused then this has no effect.
     */
    protected void pause() {
        throwIfNotInPhase(LifecyclePhase.STARTED);
        gossipHalted.set(true);
        syncPermitProvider.waitForAllSyncsToFinish();
    }

    /**
     * Resume gossiping. If called when already running then this has no effect.
     */
    protected void resume() {
        throwIfNotInPhase(LifecyclePhase.STARTED);
        intakeEventCounter.reset();
        gossipHalted.set(false);
    }
}
