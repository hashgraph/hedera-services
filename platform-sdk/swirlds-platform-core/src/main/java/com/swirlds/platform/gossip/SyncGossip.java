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

import static com.swirlds.platform.consensus.ConsensusConstants.ROUND_UNDEFINED;

import com.hedera.hapi.node.state.roster.Roster;
import com.hedera.hapi.node.state.roster.RosterEntry;
import com.swirlds.base.state.Startable;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.merkle.synchronization.config.ReconnectConfig;
import com.swirlds.common.platform.NodeId;
import com.swirlds.common.threading.framework.StoppableThread;
import com.swirlds.common.threading.framework.config.StoppableThreadConfiguration;
import com.swirlds.common.threading.manager.ThreadManager;
import com.swirlds.common.threading.pool.CachedPoolParallelExecutor;
import com.swirlds.common.threading.pool.ParallelExecutor;
import com.swirlds.common.wiring.model.WiringModel;
import com.swirlds.common.wiring.wires.input.BindableInputWire;
import com.swirlds.common.wiring.wires.output.StandardOutputWire;
import com.swirlds.platform.Utilities;
import com.swirlds.platform.config.BasicConfig;
import com.swirlds.platform.config.StateConfig;
import com.swirlds.platform.config.ThreadConfig;
import com.swirlds.platform.consensus.EventWindow;
import com.swirlds.platform.crypto.KeysAndCerts;
import com.swirlds.platform.event.PlatformEvent;
import com.swirlds.platform.gossip.permits.SyncPermitProvider;
import com.swirlds.platform.gossip.shadowgraph.Shadowgraph;
import com.swirlds.platform.gossip.shadowgraph.ShadowgraphSynchronizer;
import com.swirlds.platform.gossip.sync.SyncManagerImpl;
import com.swirlds.platform.gossip.sync.config.SyncConfig;
import com.swirlds.platform.metrics.ReconnectMetrics;
import com.swirlds.platform.metrics.SyncMetrics;
import com.swirlds.platform.network.Connection;
import com.swirlds.platform.network.ConnectionTracker;
import com.swirlds.platform.network.NetworkMetrics;
import com.swirlds.platform.network.NetworkPeerIdentifier;
import com.swirlds.platform.network.NetworkUtils;
import com.swirlds.platform.network.PeerInfo;
import com.swirlds.platform.network.communication.NegotiationProtocols;
import com.swirlds.platform.network.communication.ProtocolNegotiatorThread;
import com.swirlds.platform.network.communication.handshake.VersionCompareHandshake;
import com.swirlds.platform.network.connectivity.ConnectionServer;
import com.swirlds.platform.network.connectivity.InboundConnectionHandler;
import com.swirlds.platform.network.connectivity.OutboundConnectionCreator;
import com.swirlds.platform.network.connectivity.SocketFactory;
import com.swirlds.platform.network.protocol.HeartbeatProtocolFactory;
import com.swirlds.platform.network.protocol.ProtocolFactory;
import com.swirlds.platform.network.protocol.ProtocolRunnable;
import com.swirlds.platform.network.protocol.ReconnectProtocolFactory;
import com.swirlds.platform.network.protocol.SyncProtocolFactory;
import com.swirlds.platform.network.topology.NetworkTopology;
import com.swirlds.platform.network.topology.StaticConnectionManagers;
import com.swirlds.platform.network.topology.StaticTopology;
import com.swirlds.platform.reconnect.DefaultSignedStateValidator;
import com.swirlds.platform.reconnect.ReconnectController;
import com.swirlds.platform.reconnect.ReconnectHelper;
import com.swirlds.platform.reconnect.ReconnectLearnerFactory;
import com.swirlds.platform.reconnect.ReconnectLearnerThrottle;
import com.swirlds.platform.reconnect.ReconnectThrottle;
import com.swirlds.platform.roster.RosterUtils;
import com.swirlds.platform.state.SwirldStateManager;
import com.swirlds.platform.state.signed.ReservedSignedState;
import com.swirlds.platform.state.signed.SignedState;
import com.swirlds.platform.system.SoftwareVersion;
import com.swirlds.platform.system.address.AddressBook;
import com.swirlds.platform.system.status.PlatformStatus;
import com.swirlds.platform.system.status.StatusActionSubmitter;
import com.swirlds.platform.wiring.NoInput;
import com.swirlds.platform.wiring.components.Gossip;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * Boilerplate code for gossip.
 */
public class SyncGossip implements ConnectionTracker, Gossip {
    public static final String PLATFORM_THREAD_POOL_NAME = "platform-core";

    private boolean started = false;

    private final ReconnectController reconnectController;

    private final AtomicBoolean gossipHalted = new AtomicBoolean(false);
    private final SyncPermitProvider syncPermitProvider;
    private final SyncConfig syncConfig;
    private final Shadowgraph shadowgraph;
    private final ShadowgraphSynchronizer syncShadowgraphSynchronizer;

    /**
     * Keeps track of the number of events in the intake pipeline from each peer
     */
    private final IntakeEventCounter intakeEventCounter;

    /**
     * A list of threads that execute the sync protocol using bidirectional connections
     */
    private final List<StoppableThread> syncProtocolThreads = new ArrayList<>();

    private final NetworkTopology topology;
    private final NetworkMetrics networkMetrics;
    private final ReconnectHelper reconnectHelper;
    private final StaticConnectionManagers connectionManagers;
    private final FallenBehindManagerImpl fallenBehindManager;
    private final SyncManagerImpl syncManager;
    private final ReconnectThrottle reconnectThrottle;
    private final ReconnectMetrics reconnectMetrics;

    protected final StatusActionSubmitter statusActionSubmitter;
    protected final AtomicReference<PlatformStatus> currentPlatformStatus =
            new AtomicReference<>(PlatformStatus.STARTING_UP);

    private final List<Startable> thingsToStart = new ArrayList<>();

    private Consumer<PlatformEvent> receivedEventHandler;

    /**
     * Builds the gossip engine, depending on which flavor is requested in the configuration.
     *
     * @param platformContext               the platform context
     * @param threadManager                 the thread manager
     * @param keysAndCerts                  private keys and public certificates
     * @param roster                        the current roster
     * @param selfId                        this node's ID
     * @param appVersion                    the version of the app
     * @param swirldStateManager            manages the mutable state
     * @param latestCompleteState           holds the latest signed state that has enough signatures to be verifiable
     * @param statusActionSubmitter         for submitting updates to the platform status manager
     * @param loadReconnectState            a method that should be called when a state from reconnect is obtained
     * @param clearAllPipelinesForReconnect this method should be called to clear all pipelines prior to a reconnect
     * @param intakeEventCounter            keeps track of the number of events in the intake pipeline from each peer
     */
    public SyncGossip(
            @NonNull final PlatformContext platformContext,
            @NonNull final ThreadManager threadManager,
            @NonNull final KeysAndCerts keysAndCerts,
            @NonNull final Roster roster,
            @NonNull final NodeId selfId,
            @NonNull final SoftwareVersion appVersion,
            @NonNull final SwirldStateManager swirldStateManager,
            @NonNull final Supplier<ReservedSignedState> latestCompleteState,
            @NonNull final StatusActionSubmitter statusActionSubmitter,
            @NonNull final Consumer<SignedState> loadReconnectState,
            @NonNull final Runnable clearAllPipelinesForReconnect,
            @NonNull final IntakeEventCounter intakeEventCounter) {

        shadowgraph = new Shadowgraph(platformContext, roster.rosterEntries().size(), intakeEventCounter);

        this.statusActionSubmitter = Objects.requireNonNull(statusActionSubmitter);

        final ThreadConfig threadConfig = platformContext.getConfiguration().getConfigData(ThreadConfig.class);

        final BasicConfig basicConfig = platformContext.getConfiguration().getConfigData(BasicConfig.class);
        final List<PeerInfo> peers = Utilities.createPeerInfoList(roster, selfId);

        topology = new StaticTopology(peers, selfId);
        final NetworkPeerIdentifier peerIdentifier = new NetworkPeerIdentifier(platformContext, peers);
        final SocketFactory socketFactory =
                NetworkUtils.createSocketFactory(selfId, peers, keysAndCerts, platformContext.getConfiguration());
        // create an instance that can create new outbound connections
        final OutboundConnectionCreator connectionCreator =
                new OutboundConnectionCreator(platformContext, selfId, this, socketFactory, roster);
        connectionManagers = new StaticConnectionManagers(topology, connectionCreator);
        final InboundConnectionHandler inboundConnectionHandler = new InboundConnectionHandler(
                platformContext,
                this,
                peerIdentifier,
                selfId,
                connectionManagers::newConnection,
                platformContext.getTime());
        // allow other members to create connections to me
        final RosterEntry rosterEntry = RosterUtils.getRosterEntry(roster, selfId.id());
        // Assume all ServiceEndpoints use the same port and use the port from the first endpoint.
        // Previously, this code used a "local port" corresponding to the internal endpoint,
        // which should normally be the second entry in the endpoints list if it's obtained via
        // a regular AddressBook -> Roster conversion.
        // The assumption must be correct, otherwise, if ports were indeed different, then the old code
        // using the AddressBook would never have listened on a port associated with the external endpoint,
        // thus not allowing anyone to connect to the node from outside the local network, which we'd have noticed.
        final ConnectionServer connectionServer = new ConnectionServer(
                threadManager, RosterUtils.fetchPort(rosterEntry, 0), socketFactory, inboundConnectionHandler::handle);
        thingsToStart.add(new StoppableThreadConfiguration<>(threadManager)
                .setPriority(threadConfig.threadPrioritySync())
                .setNodeId(selfId)
                .setComponent(PLATFORM_THREAD_POOL_NAME)
                .setThreadName("connectionServer")
                .setWork(connectionServer)
                .build());

        fallenBehindManager = new FallenBehindManagerImpl(
                selfId,
                topology,
                statusActionSubmitter,
                () -> getReconnectController().start(),
                platformContext.getConfiguration().getConfigData(ReconnectConfig.class));

        syncManager = new SyncManagerImpl(platformContext, fallenBehindManager);

        final ReconnectConfig reconnectConfig =
                platformContext.getConfiguration().getConfigData(ReconnectConfig.class);

        reconnectThrottle = new ReconnectThrottle(reconnectConfig, platformContext.getTime());

        networkMetrics = new NetworkMetrics(platformContext.getMetrics(), selfId, roster);
        platformContext.getMetrics().addUpdater(networkMetrics::update);

        final AddressBook addressBook = RosterUtils.buildAddressBook(roster);
        reconnectMetrics = new ReconnectMetrics(platformContext.getMetrics(), addressBook);

        final StateConfig stateConfig = platformContext.getConfiguration().getConfigData(StateConfig.class);

        final LongSupplier getRoundSupplier = () -> {
            try (final ReservedSignedState reservedState = latestCompleteState.get()) {
                if (reservedState == null || reservedState.isNull()) {
                    return ROUND_UNDEFINED;
                }

                return reservedState.get().getRound();
            }
        };

        reconnectHelper = new ReconnectHelper(
                this::pause,
                clearAllPipelinesForReconnect::run,
                swirldStateManager::getConsensusState,
                getRoundSupplier,
                new ReconnectLearnerThrottle(platformContext.getTime(), selfId, reconnectConfig),
                state -> {
                    loadReconnectState.accept(state);
                    syncManager.resetFallenBehind();
                },
                new ReconnectLearnerFactory(
                        platformContext,
                        threadManager,
                        addressBook,
                        reconnectConfig.asyncStreamTimeout(),
                        reconnectMetrics),
                stateConfig);
        this.intakeEventCounter = Objects.requireNonNull(intakeEventCounter);

        syncConfig = platformContext.getConfiguration().getConfigData(SyncConfig.class);

        final ParallelExecutor shadowgraphExecutor = new CachedPoolParallelExecutor(threadManager, "node-sync");
        thingsToStart.add(shadowgraphExecutor);
        final SyncMetrics syncMetrics = new SyncMetrics(platformContext.getMetrics());
        syncShadowgraphSynchronizer = new ShadowgraphSynchronizer(
                platformContext,
                shadowgraph,
                roster.rosterEntries().size(),
                syncMetrics,
                event -> receivedEventHandler.accept(event),
                syncManager,
                intakeEventCounter,
                shadowgraphExecutor);

        reconnectController = new ReconnectController(reconnectConfig, threadManager, reconnectHelper, this::resume);

        final ProtocolConfig protocolConfig = platformContext.getConfiguration().getConfigData(ProtocolConfig.class);

        final Duration hangingThreadDuration = basicConfig.hangingThreadDuration();

        final int permitCount;
        if (syncConfig.onePermitPerPeer()) {
            permitCount = roster.rosterEntries().size() - 1;
        } else {
            permitCount = syncConfig.syncProtocolPermitCount();
        }

        syncPermitProvider = new SyncPermitProvider(platformContext, permitCount);

        buildSyncProtocolThreads(
                platformContext,
                threadManager,
                selfId,
                appVersion,
                latestCompleteState,
                syncMetrics,
                currentPlatformStatus::get,
                hangingThreadDuration,
                protocolConfig,
                reconnectConfig);

        thingsToStart.add(() -> syncProtocolThreads.forEach(StoppableThread::start));
    }

    private void buildSyncProtocolThreads(
            final PlatformContext platformContext,
            final ThreadManager threadManager,
            final NodeId selfId,
            final SoftwareVersion appVersion,
            final Supplier<ReservedSignedState> getLatestCompleteState,
            final SyncMetrics syncMetrics,
            final Supplier<PlatformStatus> platformStatusSupplier,
            final Duration hangingThreadDuration,
            final ProtocolConfig protocolConfig,
            final ReconnectConfig reconnectConfig) {

        final ProtocolFactory syncProtocolFactory = new SyncProtocolFactory(
                platformContext,
                syncShadowgraphSynchronizer,
                fallenBehindManager,
                syncPermitProvider,
                intakeEventCounter,
                gossipHalted::get,
                Duration.ZERO,
                syncMetrics,
                platformStatusSupplier);

        final ProtocolFactory reconnectProtocolFactory = new ReconnectProtocolFactory(
                platformContext,
                threadManager,
                reconnectThrottle,
                getLatestCompleteState,
                reconnectConfig.asyncStreamTimeout(),
                reconnectMetrics,
                reconnectController,
                new DefaultSignedStateValidator(platformContext),
                fallenBehindManager,
                platformStatusSupplier,
                platformContext.getConfiguration());

        final ProtocolFactory heartbeatProtocolFactory = new HeartbeatProtocolFactory(
                Duration.ofMillis(syncConfig.syncProtocolHeartbeatPeriod()), networkMetrics, platformContext.getTime());
        final VersionCompareHandshake versionCompareHandshake =
                new VersionCompareHandshake(appVersion, !protocolConfig.tolerateMismatchedVersion());
        final List<ProtocolRunnable> handshakeProtocols = List.of(versionCompareHandshake);
        for (final NodeId otherId : topology.getNeighbors()) {
            syncProtocolThreads.add(new StoppableThreadConfiguration<>(threadManager)
                    .setPriority(Thread.NORM_PRIORITY)
                    .setNodeId(selfId)
                    .setComponent(PLATFORM_THREAD_POOL_NAME)
                    .setOtherNodeId(otherId)
                    .setThreadName("SyncProtocolWith" + otherId)
                    .setHangingThreadPeriod(hangingThreadDuration)
                    .setWork(new ProtocolNegotiatorThread(
                            connectionManagers.getManager(otherId, topology.shouldConnectTo(otherId)),
                            syncConfig.syncSleepAfterFailedNegotiation(),
                            handshakeProtocols,
                            new NegotiationProtocols(List.of(
                                    heartbeatProtocolFactory.build(otherId),
                                    reconnectProtocolFactory.build(otherId),
                                    syncProtocolFactory.build(otherId))),
                            platformContext.getTime()))
                    .build());
        }
    }

    /**
     * Get the reconnect controller. This method is needed to break a circular dependency.
     */
    private ReconnectController getReconnectController() {
        return reconnectController;
    }

    /**
     * Start gossiping.
     */
    private void start() {
        if (started) {
            throw new IllegalStateException("Gossip already started");
        }
        started = true;
        thingsToStart.forEach(Startable::start);
    }

    /**
     * Stop gossiping.
     */
    private void stop() {
        if (!started) {
            throw new IllegalStateException("Gossip not started");
        }
        syncManager.haltRequestedObserver("stopping gossip");
        gossipHalted.set(true);
        // wait for all existing syncs to stop. no new ones will be started, since gossip has been halted, and
        // we've fallen behind
        syncPermitProvider.waitForAllPermitsToBeReleased();
        for (final StoppableThread thread : syncProtocolThreads) {
            thread.stop();
        }
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
     * Stop gossiping until {@link #resume()} is called. If called when already paused then this has no effect.
     */
    private void pause() {
        if (!started) {
            throw new IllegalStateException("Gossip not started");
        }
        gossipHalted.set(true);
        syncPermitProvider.waitForAllPermitsToBeReleased();
    }

    /**
     * Resume gossiping. Undoes the effect of {@link #pause()}. Should be called exactly once after each call to
     * {@link #pause()}.
     */
    private void resume() {
        if (!started) {
            throw new IllegalStateException("Gossip not started");
        }
        intakeEventCounter.reset();
        gossipHalted.set(false);

        // Revoke all permits when we begin gossiping again. Presumably we are behind the pack,
        // and so we want to avoid talking to too many peers at once until we've had a chance
        // to properly catch up.
        syncPermitProvider.revokeAll();
    }

    /**
     * Clear the internal state of the gossip engine.
     */
    private void clear() {
        shadowgraph.clear();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void bind(
            @NonNull final WiringModel model,
            @NonNull final BindableInputWire<PlatformEvent, Void> eventInput,
            @NonNull final BindableInputWire<EventWindow, Void> eventWindowInput,
            @NonNull final StandardOutputWire<PlatformEvent> eventOutput,
            @NonNull final BindableInputWire<NoInput, Void> startInput,
            @NonNull final BindableInputWire<NoInput, Void> stopInput,
            @NonNull final BindableInputWire<NoInput, Void> clearInput,
            @NonNull final BindableInputWire<Duration, Void> systemHealthInput,
            @NonNull final BindableInputWire<PlatformStatus, Void> platformStatusInput) {

        startInput.bindConsumer(ignored -> start());
        stopInput.bindConsumer(ignored -> stop());
        clearInput.bindConsumer(ignored -> clear());

        eventInput.bindConsumer(shadowgraph::addEvent);
        eventWindowInput.bindConsumer(shadowgraph::updateEventWindow);

        systemHealthInput.bindConsumer(syncPermitProvider::reportUnhealthyDuration);
        platformStatusInput.bindConsumer(currentPlatformStatus::set);

        receivedEventHandler = eventOutput::forward;
    }
}
