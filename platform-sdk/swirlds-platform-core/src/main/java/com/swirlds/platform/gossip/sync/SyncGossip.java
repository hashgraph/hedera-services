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

import com.swirlds.common.config.BasicConfig;
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
import com.swirlds.common.time.Time;
import com.swirlds.common.utility.PlatformVersion;
import com.swirlds.platform.Consensus;
import com.swirlds.platform.Crypto;
import com.swirlds.platform.FreezeManager;
import com.swirlds.platform.StartUpEventFrozenManager;
import com.swirlds.platform.components.EventMapper;
import com.swirlds.platform.components.state.StateManagementComponent;
import com.swirlds.platform.event.EventIntakeTask;
import com.swirlds.platform.gossip.FallenBehindManagerImpl;
import com.swirlds.platform.gossip.shadowgraph.ShadowGraph;
import com.swirlds.platform.gossip.sync.config.SyncConfig;
import com.swirlds.platform.gossip.sync.protocol.PeerAgnosticSyncChecks;
import com.swirlds.platform.gossip.sync.protocol.SyncProtocol;
import com.swirlds.platform.heartbeats.HeartbeatProtocol;
import com.swirlds.platform.metrics.EventIntakeMetrics;
import com.swirlds.platform.metrics.ReconnectMetrics;
import com.swirlds.platform.network.ConnectionTracker;
import com.swirlds.platform.network.communication.NegotiationProtocols;
import com.swirlds.platform.network.communication.NegotiatorThread;
import com.swirlds.platform.network.communication.handshake.VersionCompareHandshake;
import com.swirlds.platform.reconnect.DefaultSignedStateValidator;
import com.swirlds.platform.reconnect.ReconnectController;
import com.swirlds.platform.reconnect.ReconnectHelper;
import com.swirlds.platform.reconnect.ReconnectProtocol;
import com.swirlds.platform.reconnect.ReconnectThrottle;
import com.swirlds.platform.reconnect.emergency.EmergencyReconnectProtocol;
import com.swirlds.platform.state.EmergencyRecoveryManager;
import com.swirlds.platform.state.SwirldStateManager;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Sync gossip using the protocol negotiator.
 */
public class SyncGossip extends AbstractSyncGossip {

    private final ReconnectController reconnectController;
    private final AtomicBoolean gossipHalted = new AtomicBoolean(false);
    private final Time time;
    private final SyncPermitProvider syncPermitProvider;
    private final EmergencyRecoveryManager emergencyRecoveryManager;

    /**
     * A list of threads that execute the sync protocol using bidirectional connections
     */
    private final List<StoppableThread> syncProtocolThreads = new ArrayList<>();

    public SyncGossip(
            @NonNull PlatformContext platformContext,
            @NonNull ThreadManager threadManager,
            @NonNull final Time time,
            @NonNull Crypto crypto,
            @NonNull final NotificationEngine notificationEngine,
            @NonNull AddressBook addressBook,
            @NonNull NodeId selfId,
            @NonNull SoftwareVersion appVersion,
            @NonNull ConnectionTracker connectionTracker,
            @NonNull final ReconnectHelper reconnectHelper,
            @NonNull final Runnable updatePlatformStatus,
            @NonNull final EmergencyRecoveryManager emergencyRecoveryManager,
            @NonNull final QueueThread<EventIntakeTask> intakeQueue,
            @NonNull final ShadowGraph shadowGraph,
            @NonNull final AtomicReference<Consensus> consensusRef,
            @NonNull final SwirldStateManager swirldStateManager,
            @NonNull final FreezeManager freezeManager,
            @NonNull final StartUpEventFrozenManager startUpEventFrozenManager,
            @NonNull final FallenBehindManagerImpl fallenBehindManager,
            @NonNull final StateManagementComponent stateManagementComponent,
            @NonNull final ReconnectThrottle reconnectThrottle,
            @NonNull final ReconnectMetrics reconnectMetrics,
            @NonNull final InterruptableConsumer<EventIntakeTask> eventIntakeLambda,
            @NonNull final EventMapper eventMapper,
            @NonNull final EventIntakeMetrics eventIntakeMetrics) {
        super(
                platformContext,
                threadManager,
                time,
                crypto,
                notificationEngine,
                addressBook,
                selfId,
                appVersion,
                connectionTracker,
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
                eventIntakeMetrics);

        this.time = Objects.requireNonNull(time);
        this.emergencyRecoveryManager = Objects.requireNonNull(emergencyRecoveryManager);

        reconnectController = new ReconnectController(threadManager, reconnectHelper, () -> gossipHalted.set(false));

        final BasicConfig basicConfig = platformContext.getConfiguration().getConfigData(BasicConfig.class);
        final SyncConfig syncConfig = platformContext.getConfiguration().getConfigData(SyncConfig.class);

        final Duration hangingThreadDuration = basicConfig.hangingThreadDuration();

        // TODO
        //        // if this is a single node network, start dedicated thread to "sync" and create events
        //        if (addressBook.getSize() == 1) {
        //            syncProtocolThreads.add(new StoppableThreadConfiguration<>(threadManager)
        //                    .setPriority(Thread.NORM_PRIORITY)
        //                    .setNodeId(selfId.id())
        //                    .setComponent(PLATFORM_THREAD_POOL_NAME)
        //                    .setOtherNodeId(selfId.id())
        //                    .setThreadName("SingleNodeNetworkSync")
        //                    .setHangingThreadPeriod(hangingThreadDuration)
        //                    .setWork(new SingleNodeNetworkSync(
        //                            updatePlatformStatus, eventTaskCreator::createEvent, () -> 0, selfId.id()))
        //                    .build(true));
        //
        //            return;
        //        }

        syncPermitProvider = new SyncPermitProvider(syncConfig.syncProtocolPermitCount());

        // If we still need an emergency recovery state, we need it via emergency reconnect.
        // Start the helper now so that it is ready to receive a connection to perform reconnect with when the
        // protocol is initiated.
        if (emergencyRecoveryManager.isEmergencyStateRequired()) {
            reconnectController.start();
        }

        final PeerAgnosticSyncChecks peerAgnosticSyncChecks = new PeerAgnosticSyncChecks(List.of(
                () -> !gossipHalted.get(), () -> intakeQueue.size() < settings.getEventIntakeQueueThrottleSize()));

        final ReconnectConfig reconnectConfig =
                platformContext.getConfiguration().getConfigData(ReconnectConfig.class);

        for (final NodeId otherId : topology.getNeighbors()) {
            syncProtocolThreads.add(new StoppableThreadConfiguration<>(threadManager)
                    .setPriority(Thread.NORM_PRIORITY)
                    .setNodeId(selfId.id())
                    .setComponent(PLATFORM_THREAD_POOL_NAME)
                    .setOtherNodeId(otherId.id())
                    .setThreadName("SyncProtocolWith" + otherId.id())
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
                                            reconnectController),
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
                    .build(true));
        }
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
}
