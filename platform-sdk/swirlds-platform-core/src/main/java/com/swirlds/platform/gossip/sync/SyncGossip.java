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
import com.swirlds.common.config.BasicConfig;
import com.swirlds.common.config.EventConfig;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.merkle.synchronization.config.ReconnectConfig;
import com.swirlds.common.notification.NotificationEngine;
import com.swirlds.common.system.NodeId;
import com.swirlds.common.system.SoftwareVersion;
import com.swirlds.common.system.address.AddressBook;
import com.swirlds.common.threading.SyncPermitProvider;
import com.swirlds.common.threading.framework.QueueThread;
import com.swirlds.common.threading.framework.StoppableThread;
import com.swirlds.common.threading.framework.config.StoppableThreadConfiguration;
import com.swirlds.common.threading.interrupt.InterruptableConsumer;
import com.swirlds.common.threading.manager.ThreadManager;
import com.swirlds.common.threading.pool.ParallelExecutor;
import com.swirlds.common.utility.Clearable;
import com.swirlds.common.utility.LoggingClearables;
import com.swirlds.common.utility.PlatformVersion;
import com.swirlds.platform.Consensus;
import com.swirlds.platform.Crypto;
import com.swirlds.platform.FreezeManager;
import com.swirlds.platform.PlatformConstructor;
import com.swirlds.platform.StartUpEventFrozenManager;
import com.swirlds.platform.components.CriticalQuorum;
import com.swirlds.platform.components.CriticalQuorumImpl;
import com.swirlds.platform.components.EventMapper;
import com.swirlds.platform.components.state.StateManagementComponent;
import com.swirlds.platform.event.EventIntakeTask;
import com.swirlds.platform.gossip.AbstractGossip;
import com.swirlds.platform.gossip.FallenBehindManagerImpl;
import com.swirlds.platform.gossip.shadowgraph.ShadowGraph;
import com.swirlds.platform.gossip.shadowgraph.ShadowGraphSynchronizer;
import com.swirlds.platform.gossip.sync.config.SyncConfig;
import com.swirlds.platform.gossip.sync.protocol.PeerAgnosticSyncChecks;
import com.swirlds.platform.gossip.sync.protocol.SyncProtocol;
import com.swirlds.platform.heartbeats.HeartbeatProtocol;
import com.swirlds.platform.metrics.EventIntakeMetrics;
import com.swirlds.platform.network.communication.NegotiationProtocols;
import com.swirlds.platform.network.communication.NegotiatorThread;
import com.swirlds.platform.network.communication.handshake.VersionCompareHandshake;
import com.swirlds.platform.observers.EventObserverDispatcher;
import com.swirlds.platform.reconnect.DefaultSignedStateValidator;
import com.swirlds.platform.reconnect.ReconnectController;
import com.swirlds.platform.reconnect.ReconnectProtocol;
import com.swirlds.platform.reconnect.emergency.EmergencyReconnectProtocol;
import com.swirlds.platform.recovery.EmergencyRecoveryManager;
import com.swirlds.platform.state.SwirldStateManager;
import com.swirlds.platform.state.signed.SignedState;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.apache.commons.lang3.tuple.Pair;

/**
 * Sync gossip using the protocol negotiator.
 */
public class SyncGossip extends AbstractGossip {

    private final ReconnectController reconnectController;
    private final AtomicBoolean gossipHalted = new AtomicBoolean(false);
    private final SyncPermitProvider syncPermitProvider;
    protected final SyncConfig syncConfig;
    protected final ShadowGraphSynchronizer syncShadowgraphSynchronizer;
    private final InterruptableConsumer<EventIntakeTask> eventIntakeLambda;

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
     * @param shadowGraph                   contains non-ancient events
     * @param emergencyRecoveryManager      handles emergency recovery
     * @param consensusRef                  a pointer to consensus
     * @param intakeQueue                   the event intake queue
     * @param freezeManager                 handles freezes
     * @param startUpEventFrozenManager     prevents event creation during startup
     * @param swirldStateManager            manages the mutable state
     * @param stateManagementComponent      manages the lifecycle of the state
     * @param eventIntakeLambda             a method that is called when something needs to be added to the event intake
     *                                      queue
     * @param eventObserverDispatcher       the object used to wire event intake
     * @param eventMapper                   a data structure used to track the most recent event from each node
     * @param eventIntakeMetrics            metrics for event intake
     * @param updatePlatformStatus          a method that updates the platform status, when called
     * @param loadReconnectState            a method that should be called when a state from reconnect is obtained
     * @param clearAllPipelinesForReconnect this method should be called to clear all pipelines prior to a reconnect
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
            @NonNull final ShadowGraph shadowGraph,
            @NonNull final EmergencyRecoveryManager emergencyRecoveryManager,
            @NonNull final AtomicReference<Consensus> consensusRef,
            @NonNull final QueueThread<EventIntakeTask> intakeQueue,
            @NonNull final FreezeManager freezeManager,
            @NonNull final StartUpEventFrozenManager startUpEventFrozenManager,
            @NonNull final SwirldStateManager swirldStateManager,
            @NonNull final StateManagementComponent stateManagementComponent,
            @NonNull final InterruptableConsumer<EventIntakeTask> eventIntakeLambda,
            @NonNull final EventObserverDispatcher eventObserverDispatcher,
            @NonNull final EventMapper eventMapper,
            @NonNull final EventIntakeMetrics eventIntakeMetrics,
            @NonNull final Runnable updatePlatformStatus,
            @NonNull final Consumer<SignedState> loadReconnectState,
            @NonNull final Runnable clearAllPipelinesForReconnect) {
        super(
                platformContext,
                threadManager,
                crypto,
                addressBook,
                selfId,
                appVersion,
                intakeQueue,
                freezeManager,
                startUpEventFrozenManager,
                swirldStateManager,
                stateManagementComponent,
                eventMapper,
                eventIntakeMetrics,
                eventObserverDispatcher,
                updatePlatformStatus,
                loadReconnectState,
                clearAllPipelinesForReconnect);

        final EventConfig eventConfig = platformContext.getConfiguration().getConfigData(EventConfig.class);
        this.eventIntakeLambda = Objects.requireNonNull(eventIntakeLambda);

        syncConfig = platformContext.getConfiguration().getConfigData(SyncConfig.class);

        final ParallelExecutor shadowgraphExecutor = PlatformConstructor.parallelExecutor(threadManager);
        thingsToStart.add(shadowgraphExecutor);
        syncShadowgraphSynchronizer = new ShadowGraphSynchronizer(
                shadowGraph,
                addressBook.getSize(),
                syncMetrics,
                consensusRef::get,
                eventTaskCreator::syncDone,
                eventTaskCreator::addEvent,
                syncManager,
                shadowgraphExecutor,
                // don't send or receive init bytes if running sync as a protocol. the negotiator handles this
                false,
                () -> {});

        clearAllInternalPipelines = new LoggingClearables(
                RECONNECT.getMarker(),
                List.of(
                        Pair.of(intakeQueue, "intakeQueue"),
                        Pair.of(eventMapper, "eventMapper"),
                        Pair.of(shadowGraph, "shadowGraph")));

        reconnectController = new ReconnectController(threadManager, reconnectHelper, this::resume);

        final BasicConfig basicConfig = platformContext.getConfiguration().getConfigData(BasicConfig.class);

        final Duration hangingThreadDuration = basicConfig.hangingThreadDuration();

        syncPermitProvider = new SyncPermitProvider(syncConfig.syncProtocolPermitCount());

        if (emergencyRecoveryManager.isEmergencyStateRequired()) {
            // If we still need an emergency recovery state, we need it via emergency reconnect.
            // Start the helper first so that it is ready to receive a connection to perform reconnect with when the
            // protocol is initiated.
            thingsToStart.add(0, reconnectController::start);
        }

        final PeerAgnosticSyncChecks peerAgnosticSyncChecks = new PeerAgnosticSyncChecks(List.of(
                () -> !gossipHalted.get(), () -> intakeQueue.size() < eventConfig.eventIntakeQueueThrottleSize()));

        final ReconnectConfig reconnectConfig =
                platformContext.getConfiguration().getConfigData(ReconnectConfig.class);

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
                                    new VersionCompareHandshake(appVersion, !settings.isGossipWithDifferentVersions()),
                                    new VersionCompareHandshake(
                                            PlatformVersion.locateOrDefault(),
                                            !settings.isGossipWithDifferentVersions())),
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
                                            reconnectConfig.asyncStreamTimeoutMilliseconds(),
                                            reconnectMetrics,
                                            reconnectController,
                                            fallenBehindManager),
                                    new ReconnectProtocol(
                                            threadManager,
                                            otherId,
                                            reconnectThrottle,
                                            () -> stateManagementComponent.getLatestSignedState(
                                                    "SwirldsPlatform: ReconnectProtocol"),
                                            reconnectConfig.asyncStreamTimeoutMilliseconds(),
                                            reconnectMetrics,
                                            reconnectController,
                                            new DefaultSignedStateValidator(),
                                            fallenBehindManager),
                                    new SyncProtocol(
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
                updatePlatformStatus,
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
    @NonNull
    @Override
    public InterruptableConsumer<EventIntakeTask> getEventIntakeLambda() {
        return eventIntakeLambda;
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
        gossipHalted.set(false);
    }
}
