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

import com.swirlds.common.config.BasicConfig;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.merkle.synchronization.config.ReconnectConfig;
import com.swirlds.common.notification.NotificationEngine;
import com.swirlds.common.system.NodeId;
import com.swirlds.common.system.SoftwareVersion;
import com.swirlds.common.system.address.AddressBook;
import com.swirlds.common.threading.framework.QueueThread;
import com.swirlds.common.threading.framework.StoppableThread;
import com.swirlds.common.threading.framework.config.StoppableThreadConfiguration;
import com.swirlds.common.threading.interrupt.InterruptableConsumer;
import com.swirlds.common.threading.manager.ThreadManager;
import com.swirlds.common.threading.pool.ParallelExecutor;
import com.swirlds.common.time.Time;
import com.swirlds.common.utility.Clearable;
import com.swirlds.common.utility.LoggingClearables;
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
import com.swirlds.platform.gossip.shadowgraph.SingleNodeNetworkSync;
import com.swirlds.platform.gossip.sync.config.SyncConfig;
import com.swirlds.platform.metrics.EventIntakeMetrics;
import com.swirlds.platform.observers.EventObserverDispatcher;
import com.swirlds.platform.reconnect.ReconnectController;
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
public class SingleNodeSyncGossip extends AbstractGossip {

    private final ReconnectController reconnectController;
    private final AtomicBoolean gossipHalted = new AtomicBoolean(false);
    protected final SyncConfig syncConfig;
    protected final ShadowGraphSynchronizer syncShadowgraphSynchronizer;
    private final InterruptableConsumer<EventIntakeTask> eventIntakeLambda;
    private final Clearable clearAllPipelines;

    /**
     * A list of threads that execute the sync protocol using bidirectional connections
     */
    private final List<StoppableThread> syncProtocolThreads = new ArrayList<>();

    public SingleNodeSyncGossip(
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
            @NonNull final FreezeManager freezeManager,
            @NonNull final StartUpEventFrozenManager startUpEventFrozenManager,
            @NonNull final SwirldStateManager swirldStateManager,
            @NonNull final StateManagementComponent stateManagementComponent,
            @NonNull final InterruptableConsumer<EventIntakeTask> eventIntakeLambda,
            @NonNull final EventObserverDispatcher eventObserverDispatcher,
            @NonNull final EventMapper eventMapper,
            @NonNull final EventIntakeMetrics eventIntakeMetrics,
            @NonNull final Runnable updatePlatformStatus,
            @NonNull final Consumer<SignedState> loadReconnectState) {
        super(
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
                eventMapper,
                eventIntakeMetrics,
                eventObserverDispatcher,
                updatePlatformStatus,
                loadReconnectState);

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
                !syncConfig.syncAsProtocolEnabled(),
                () -> {});

        clearAllPipelines = new LoggingClearables(
                RECONNECT.getMarker(),
                List.of(
                        Pair.of(intakeQueue, "intakeQueue"),
                        Pair.of(eventMapper, "eventMapper"),
                        Pair.of(shadowGraph, "shadowGraph")));

        reconnectController = new ReconnectController(threadManager, reconnectHelper, () -> gossipHalted.set(false));

        final BasicConfig basicConfig = platformContext.getConfiguration().getConfigData(BasicConfig.class);

        final Duration hangingThreadDuration = basicConfig.hangingThreadDuration();

        syncProtocolThreads.add(new StoppableThreadConfiguration<>(threadManager)
                .setPriority(Thread.NORM_PRIORITY)
                .setNodeId(selfId.id())
                .setComponent(PLATFORM_THREAD_POOL_NAME)
                .setOtherNodeId(selfId.id())
                .setThreadName("SingleNodeNetworkSync")
                .setHangingThreadPeriod(hangingThreadDuration)
                .setWork(new SingleNodeNetworkSync(
                        updatePlatformStatus, eventTaskCreator::createEvent, () -> 0, selfId.id()))
                .build());

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
        for (final StoppableThread thread : syncProtocolThreads) {
            thread.stop();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected CriticalQuorum buildCriticalQuorum() {
        return new CriticalQuorumImpl(platformContext.getMetrics(), selfId.id(), addressBook);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected FallenBehindManagerImpl buildFallenBehindManager() {
        return new FallenBehindManagerImpl(
                selfId,
                topology.getConnectionGraph(),
                updatePlatformStatus,
                () -> {},
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
        clearAllPipelines.clear();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected boolean doVersionCheck() {
        return false;
    }
}
