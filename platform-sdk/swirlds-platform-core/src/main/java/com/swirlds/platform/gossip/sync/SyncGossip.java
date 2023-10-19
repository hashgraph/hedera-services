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

import static com.swirlds.logging.LogMarker.RECONNECT;
import static com.swirlds.platform.SwirldsPlatform.PLATFORM_THREAD_POOL_NAME;

import com.swirlds.base.state.LifecyclePhase;
import com.swirlds.base.time.Time;
import com.swirlds.base.utility.Pair;
import com.swirlds.common.config.BasicConfig;
import com.swirlds.common.config.EventConfig;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.merkle.synchronization.config.ReconnectConfig;
import com.swirlds.common.notification.NotificationEngine;
import com.swirlds.common.system.NodeId;
import com.swirlds.common.system.SoftwareVersion;
import com.swirlds.common.system.address.AddressBook;
import com.swirlds.common.system.status.PlatformStatusManager;
import com.swirlds.common.threading.framework.QueueThread;
import com.swirlds.common.threading.framework.StoppableThread;
import com.swirlds.common.threading.framework.config.StoppableThreadConfiguration;
import com.swirlds.common.threading.manager.ThreadManager;
import com.swirlds.common.threading.pool.CachedPoolParallelExecutor;
import com.swirlds.common.threading.pool.ParallelExecutor;
import com.swirlds.common.utility.Clearable;
import com.swirlds.common.utility.LoggingClearables;
import com.swirlds.common.utility.PlatformVersion;
import com.swirlds.platform.Consensus;
import com.swirlds.platform.Crypto;
import com.swirlds.platform.components.CriticalQuorum;
import com.swirlds.platform.components.CriticalQuorumImpl;
import com.swirlds.platform.components.state.StateManagementComponent;
import com.swirlds.platform.event.GossipEvent;
import com.swirlds.platform.event.linking.EventLinker;
import com.swirlds.platform.gossip.AbstractGossip;
import com.swirlds.platform.gossip.FallenBehindManagerImpl;
import com.swirlds.platform.gossip.IntakeEventCounter;
import com.swirlds.platform.gossip.ProtocolConfig;
import com.swirlds.platform.gossip.SyncPermitProvider;
import com.swirlds.platform.gossip.shadowgraph.ShadowGraph;
import com.swirlds.platform.gossip.shadowgraph.ShadowGraphSynchronizer;
import com.swirlds.platform.gossip.sync.config.SyncConfig;
import com.swirlds.platform.gossip.sync.protocol.PeerAgnosticSyncChecks;
import com.swirlds.platform.gossip.sync.protocol.SyncProtocol;
import com.swirlds.platform.heartbeats.HeartbeatProtocol;
import com.swirlds.platform.metrics.SyncMetrics;
import com.swirlds.platform.network.communication.NegotiationProtocols;
import com.swirlds.platform.network.communication.NegotiatorThread;
import com.swirlds.platform.network.communication.handshake.HashCompareHandshake;
import com.swirlds.platform.network.communication.handshake.VersionCompareHandshake;
import com.swirlds.platform.observers.EventObserverDispatcher;
import com.swirlds.platform.reconnect.DefaultSignedStateValidator;
import com.swirlds.platform.reconnect.ReconnectController;
import com.swirlds.platform.reconnect.ReconnectProtocol;
import com.swirlds.platform.reconnect.emergency.EmergencyReconnectProtocol;
import com.swirlds.platform.recovery.EmergencyRecoveryManager;
import com.swirlds.platform.state.SwirldStateManager;
import com.swirlds.platform.state.signed.SignedState;
import com.swirlds.platform.threading.PauseAndClear;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Sync gossip using the protocol negotiator.
 */
public class SyncGossip extends AbstractGossip {

    private final ReconnectController reconnectController;
    private final AtomicBoolean gossipHalted = new AtomicBoolean(false);
    private final SyncPermitProvider syncPermitProvider;
    protected final SyncConfig syncConfig;
    protected final ShadowGraphSynchronizer syncShadowgraphSynchronizer;

    /**
     * Keeps track of the number of events in the intake pipeline from each peer
     */
    private final IntakeEventCounter intakeEventCounter;

    /**
     * Holds a list of objects that need to be cleared when {@link #clear()} is called on this object.
     */
    private final Clearable clearAllInternalPipelines;

    /**
     * A list of threads that execute the sync protocol using bidirectional connections
     */
    private final List<StoppableThread> syncProtocolThreads = new ArrayList<>();

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
     * @param eventObserverDispatcher       the object used to wire event intake
     * @param syncMetrics                   metrics for sync
     * @param eventLinker                   links events to their parents, buffers orphans if configured to do so
     * @param platformStatusManager         the platform status manager
     * @param loadReconnectState            a method that should be called when a state from reconnect is obtained
     * @param clearAllPipelinesForReconnect this method should be called to clear all pipelines prior to a reconnect
     * @param intakeEventCounter            keeps track of the number of events in the intake pipeline from each peer
     */
    public SyncGossip(
            @NonNull final PlatformContext platformContext,
            @NonNull final ThreadManager threadManager,
            @NonNull final Time time,
            @NonNull final Crypto crypto,
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
            @NonNull final EventObserverDispatcher eventObserverDispatcher,
            @NonNull final SyncMetrics syncMetrics,
            @NonNull final EventLinker eventLinker,
            @NonNull final PlatformStatusManager platformStatusManager,
            @NonNull final Consumer<SignedState> loadReconnectState,
            @NonNull final Runnable clearAllPipelinesForReconnect,
            @NonNull final IntakeEventCounter intakeEventCounter) {
        super(
                platformContext,
                threadManager,
                time,
                crypto,
                addressBook,
                selfId,
                appVersion,
                intakeQueue,
                swirldStateManager,
                stateManagementComponent,
                syncMetrics,
                eventObserverDispatcher,
                platformStatusManager,
                loadReconnectState,
                clearAllPipelinesForReconnect);

        Objects.requireNonNull(eventLinker);
        this.intakeEventCounter = Objects.requireNonNull(intakeEventCounter);

        final EventConfig eventConfig = platformContext.getConfiguration().getConfigData(EventConfig.class);

        syncConfig = platformContext.getConfiguration().getConfigData(SyncConfig.class);

        final ParallelExecutor shadowgraphExecutor = new CachedPoolParallelExecutor(threadManager, "node-sync");
        thingsToStart.add(shadowgraphExecutor);
        syncShadowgraphSynchronizer = new ShadowGraphSynchronizer(
                platformContext,
                shadowGraph,
                addressBook.getSize(),
                syncMetrics,
                consensusRef::get,
                intakeQueue,
                syncManager,
                intakeEventCounter,
                shadowgraphExecutor,
                // don't send or receive init bytes if running sync as a protocol. the negotiator handles this
                false,
                () -> {});

        clearAllInternalPipelines = new LoggingClearables(
                RECONNECT.getMarker(),
                List.of(
                        Pair.of(intakeQueue, "intakeQueue"),
                        Pair.of(new PauseAndClear(intakeQueue, eventLinker), "eventLinker"),
                        Pair.of(shadowGraph, "shadowGraph")));

        final ReconnectConfig reconnectConfig =
                platformContext.getConfiguration().getConfigData(ReconnectConfig.class);

        reconnectController = new ReconnectController(reconnectConfig, threadManager, reconnectHelper, this::resume);

        final BasicConfig basicConfig = platformContext.getConfiguration().getConfigData(BasicConfig.class);
        final ProtocolConfig protocolConfig = platformContext.getConfiguration().getConfigData(ProtocolConfig.class);

        final Duration hangingThreadDuration = basicConfig.hangingThreadDuration();

        syncPermitProvider = new SyncPermitProvider(syncConfig.syncProtocolPermitCount(), intakeEventCounter);

        if (emergencyRecoveryManager.isEmergencyStateRequired()) {
            // If we still need an emergency recovery state, we need it via emergency reconnect.
            // Start the helper first so that it is ready to receive a connection to perform reconnect with when the
            // protocol is initiated.
            thingsToStart.add(0, reconnectController::start);
        }

        final PeerAgnosticSyncChecks peerAgnosticSyncChecks = new PeerAgnosticSyncChecks(List.of(
                () -> !gossipHalted.get(), () -> intakeQueue.size() < eventConfig.eventIntakeQueueThrottleSize()));

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
                                    new VersionCompareHandshake(
                                            PlatformVersion.locateOrDefault(),
                                            !protocolConfig.tolerateMismatchedVersion()),
                                    new HashCompareHandshake(epochHash, !protocolConfig.tolerateMismatchedEpochHash())),
                            new NegotiationProtocols(List.of(
                                    new HeartbeatProtocol(
                                            otherId,
                                            Duration.ofMillis(syncConfig.syncProtocolHeartbeatPeriod()),
                                            networkMetrics,
                                            time),
                                    new EmergencyReconnectProtocol(
                                            threadManager,
                                            notificationEngine,
                                            otherId,
                                            emergencyRecoveryManager,
                                            reconnectThrottle,
                                            stateManagementComponent,
                                            reconnectConfig.asyncStreamTimeout(),
                                            reconnectMetrics,
                                            reconnectController,
                                            fallenBehindManager,
                                            platformStatusManager,
                                            platformContext.getConfiguration()),
                                    new ReconnectProtocol(
                                            threadManager,
                                            otherId,
                                            reconnectThrottle,
                                            () -> stateManagementComponent.getLatestSignedState(
                                                    "SwirldsPlatform: ReconnectProtocol"),
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
                                            criticalQuorum,
                                            peerAgnosticSyncChecks,
                                            Duration.ZERO,
                                            syncMetrics,
                                            time)))))
                    .build());
        }

        thingsToStart.add(() -> syncProtocolThreads.forEach(StoppableThread::start));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected boolean unidirectionalConnectionsEnabled() {
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void stop() {
        super.stop();
        gossipHalted.set(true);
        // wait for all existing syncs to stop. no new ones will be started, since gossip has been halted, and
        // we've fallen behind
        syncPermitProvider.waitForAllSyncsToFinish();
        for (final StoppableThread thread : syncProtocolThreads) {
            thread.stop();
        }
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    protected CriticalQuorum buildCriticalQuorum() {
        return new CriticalQuorumImpl(platformContext.getMetrics(), selfId, addressBook);
    }

    /**
     * Get the reconnect controller. This method is needed to break a circular dependency.
     */
    public ReconnectController getReconnectController() {
        return reconnectController;
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    protected FallenBehindManagerImpl buildFallenBehindManager() {
        return new FallenBehindManagerImpl(
                addressBook,
                selfId,
                topology.getConnectionGraph(),
                statusActionSubmitter,
                () -> getReconnectController().start(),
                platformContext.getConfiguration().getConfigData(ReconnectConfig.class));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void loadFromSignedState(@NonNull SignedState signedState) {
        // intentional no-op
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void clear() {
        clearAllInternalPipelines.clear();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected boolean shouldDoVersionCheck() {
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void pause() {
        throwIfNotInPhase(LifecyclePhase.STARTED);
        gossipHalted.set(true);
        syncPermitProvider.waitForAllSyncsToFinish();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void resume() {
        throwIfNotInPhase(LifecyclePhase.STARTED);
        intakeEventCounter.reset();
        gossipHalted.set(false);
    }
}
