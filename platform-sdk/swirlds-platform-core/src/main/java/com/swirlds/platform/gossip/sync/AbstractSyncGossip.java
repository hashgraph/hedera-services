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

import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.notification.NotificationEngine;
import com.swirlds.common.system.NodeId;
import com.swirlds.common.system.SoftwareVersion;
import com.swirlds.common.system.address.AddressBook;
import com.swirlds.common.threading.framework.QueueThread;
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
import com.swirlds.platform.gossip.sync.config.SyncConfig;
import com.swirlds.platform.metrics.EventIntakeMetrics;
import com.swirlds.platform.metrics.ReconnectMetrics;
import com.swirlds.platform.observers.EventObserverDispatcher;
import com.swirlds.platform.reconnect.ReconnectHelper;
import com.swirlds.platform.reconnect.ReconnectThrottle;
import com.swirlds.platform.state.SwirldStateManager;
import com.swirlds.platform.state.signed.SignedState;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.commons.lang3.tuple.Pair;

/**
 * Boilerplate code for sync gossip.
 */
public abstract class AbstractSyncGossip extends AbstractGossip { // TODO should this class even exist?

    protected final SyncConfig syncConfig;
    protected final ShadowGraphSynchronizer syncShadowgraphSynchronizer;
    private final InterruptableConsumer<EventIntakeTask> eventIntakeLambda;
    private final Clearable clearAllPipelines;

    public AbstractSyncGossip(
            @NonNull PlatformContext platformContext,
            @NonNull ThreadManager threadManager,
            @NonNull final Time time,
            @NonNull Crypto crypto,
            @NonNull final NotificationEngine notificationEngine,
            @NonNull AddressBook addressBook,
            @NonNull NodeId selfId,
            @NonNull SoftwareVersion appVersion,
            @NonNull final ReconnectHelper reconnectHelper,
            @NonNull final Runnable updatePlatformStatus,
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
                shadowGraph,
                reconnectHelper,
                consensusRef,
                intakeQueue,
                freezeManager,
                startUpEventFrozenManager,
                fallenBehindManager,
                swirldStateManager,
                reconnectThrottle,
                stateManagementComponent,
                reconnectMetrics,
                eventMapper,
                eventIntakeMetrics,
                eventObserverDispatcher,
                updatePlatformStatus);

        this.eventIntakeLambda = Objects.requireNonNull(eventIntakeLambda);

        syncConfig = platformContext.getConfiguration().getConfigData(SyncConfig.class);

        final ParallelExecutor shadowgraphExecutor = PlatformConstructor.parallelExecutor(threadManager);
        shadowgraphExecutor.start(); // TODO don't start this here!
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
}
