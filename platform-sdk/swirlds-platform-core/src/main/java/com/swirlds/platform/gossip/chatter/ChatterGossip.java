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

package com.swirlds.platform.gossip.chatter;

import static com.swirlds.common.utility.CommonUtils.combineConsumers;
import static com.swirlds.logging.LogMarker.RECONNECT;
import static com.swirlds.platform.SwirldsPlatform.PLATFORM_THREAD_POOL_NAME;

import com.swirlds.base.state.LifecyclePhase;
import com.swirlds.common.config.BasicConfig;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.crypto.CryptographyHolder;
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
import com.swirlds.common.threading.pool.CachedPoolParallelExecutor;
import com.swirlds.common.threading.pool.ParallelExecutor;
import com.swirlds.common.threading.utility.SequenceCycle;
import com.swirlds.common.time.OSTime;
import com.swirlds.common.time.Time;
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
import com.swirlds.platform.components.EventCreationRules;
import com.swirlds.platform.components.EventMapper;
import com.swirlds.platform.components.state.StateManagementComponent;
import com.swirlds.platform.crypto.CryptoStatic;
import com.swirlds.platform.event.EventCreatorThread;
import com.swirlds.platform.event.EventIntakeTask;
import com.swirlds.platform.event.GossipEvent;
import com.swirlds.platform.event.creation.AncientParentsRule;
import com.swirlds.platform.event.creation.BelowIntCreationRule;
import com.swirlds.platform.event.creation.ChatterEventCreator;
import com.swirlds.platform.event.creation.ChatteringRule;
import com.swirlds.platform.event.creation.LoggingEventCreationRules;
import com.swirlds.platform.event.creation.OtherParentTracker;
import com.swirlds.platform.event.creation.StaticCreationRules;
import com.swirlds.platform.event.intake.ChatterEventMapper;
import com.swirlds.platform.event.linking.EventLinker;
import com.swirlds.platform.gossip.AbstractGossip;
import com.swirlds.platform.gossip.FallenBehindManagerImpl;
import com.swirlds.platform.gossip.chatter.communication.ChatterProtocol;
import com.swirlds.platform.gossip.chatter.config.ChatterConfig;
import com.swirlds.platform.gossip.chatter.protocol.ChatterCore;
import com.swirlds.platform.gossip.chatter.protocol.peer.PeerInstance;
import com.swirlds.platform.gossip.shadowgraph.ShadowGraph;
import com.swirlds.platform.gossip.shadowgraph.ShadowGraphSynchronizer;
import com.swirlds.platform.metrics.EventIntakeMetrics;
import com.swirlds.platform.network.communication.NegotiationProtocols;
import com.swirlds.platform.network.communication.NegotiatorThread;
import com.swirlds.platform.network.communication.handshake.VersionCompareHandshake;
import com.swirlds.platform.observers.EventObserverDispatcher;
import com.swirlds.platform.reconnect.DefaultSignedStateValidator;
import com.swirlds.platform.reconnect.ReconnectController;
import com.swirlds.platform.reconnect.ReconnectProtocol;
import com.swirlds.platform.reconnect.emergency.EmergencyReconnectProtocol;
import com.swirlds.platform.state.EmergencyRecoveryManager;
import com.swirlds.platform.state.SwirldStateManager;
import com.swirlds.platform.state.signed.SignedState;
import com.swirlds.platform.threading.PauseAndClear;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.apache.commons.lang3.tuple.Pair;

/**
 * Gossip implemented with the chatter protocol.
 */
public class ChatterGossip extends AbstractGossip {

    private final ReconnectController reconnectController;
    private final ChatterCore<GossipEvent> chatterCore;
    private final List<StoppableThread> chatterThreads = new LinkedList<>();
    private final ChatterEventMapper chatterEventMapper = new ChatterEventMapper();
    private final SequenceCycle<EventIntakeTask> intakeCycle;

    /**
     * Holds a list of objects that need to be cleared when {@link #clear()} is called on this object.
     */
    private final Clearable clearAllInternalPipelines;

    /**
     * Builds the gossip engine that implements the chatter v1 algorithm.
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
     * @param startedFromGenesis            true if this node started from a genesis state
     * @param stateManagementComponent      manages the lifecycle of the state
     * @param eventIntakeLambda             a method that is called when something needs to be added to the event intake
     *                                      queue
     * @param eventObserverDispatcher       the object used to wire event intake
     * @param eventMapper                   a data structure used to track the most recent event from each node
     * @param eventIntakeMetrics            metrics for event intake
     * @param eventLinker                   links together events, if chatter is enabled will also buffer orphans
     * @param updatePlatformStatus          a method that updates the platform status, when called
     * @param loadReconnectState            a method that should be called when a state from reconnect is obtained
     * @param clearAllPipelinesForReconnect this method should be called to clear all pipelines prior to a reconnect
     */
    public ChatterGossip(
            @NonNull final PlatformContext platformContext,
            @NonNull final ThreadManager threadManager,
            @NonNull final Time time,
            @NonNull Crypto crypto,
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
            final boolean startedFromGenesis,
            @NonNull final StateManagementComponent stateManagementComponent,
            @NonNull final InterruptableConsumer<EventIntakeTask> eventIntakeLambda,
            @NonNull final EventObserverDispatcher eventObserverDispatcher,
            @NonNull final EventMapper eventMapper,
            @NonNull final EventIntakeMetrics eventIntakeMetrics,
            @NonNull final EventLinker eventLinker,
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

        final BasicConfig basicConfig = platformContext.getConfiguration().getConfigData(BasicConfig.class);
        final ChatterConfig chatterConfig = platformContext.getConfiguration().getConfigData(ChatterConfig.class);

        chatterCore = new ChatterCore<>(
                time,
                GossipEvent.class,
                new PrepareChatterEvent(CryptographyHolder.get()),
                chatterConfig,
                networkMetrics::recordPingTime,
                platformContext.getMetrics());

        reconnectController = new ReconnectController(threadManager, reconnectHelper, this::resume);

        // first create all instances because of thread safety
        for (final NodeId otherId : topology.getNeighbors()) {
            chatterCore.newPeerInstance(otherId, eventTaskCreator::addEvent);
        }

        if (emergencyRecoveryManager.isEmergencyStateRequired()) {
            // If we still need an emergency recovery state, we need it via emergency reconnect.
            // Start the helper first so that it is ready to receive a connection to perform reconnect with when the
            // protocol is initiated.
            thingsToStart.add(0, reconnectController::start);
        }

        intakeCycle = new SequenceCycle<>(eventIntakeLambda);

        final ParallelExecutor parallelExecutor = new CachedPoolParallelExecutor(threadManager, "chatter");
        parallelExecutor.start();
        for (final NodeId otherId : topology.getNeighbors()) {
            final PeerInstance chatterPeer = chatterCore.getPeerInstance(otherId);
            final ParallelExecutor shadowgraphExecutor = PlatformConstructor.parallelExecutor(threadManager);
            shadowgraphExecutor.start();
            final ShadowGraphSynchronizer chatterSynchronizer = new ShadowGraphSynchronizer(
                    shadowGraph,
                    addressBook.getSize(),
                    syncMetrics,
                    consensusRef::get,
                    sr -> {},
                    eventTaskCreator::addEvent,
                    syncManager,
                    shadowgraphExecutor,
                    false,
                    () -> {
                        // start accepting events into the chatter queue
                        chatterPeer.communicationState().chatterSyncStartingPhase3();
                        // wait for any intake event currently being processed to finish
                        intakeCycle.waitForCurrentSequenceEnd();
                    });

            final ReconnectConfig reconnectConfig =
                    platformContext.getConfiguration().getConfigData(ReconnectConfig.class);

            chatterThreads.add(new StoppableThreadConfiguration<>(threadManager)
                    .setPriority(Thread.NORM_PRIORITY)
                    .setNodeId(selfId)
                    .setComponent(PLATFORM_THREAD_POOL_NAME)
                    .setOtherNodeId(otherId)
                    .setThreadName("ChatterReader")
                    .setHangingThreadPeriod(basicConfig.hangingThreadDuration())
                    .setWork(new NegotiatorThread(
                            connectionManagers.getManager(otherId, topology.shouldConnectTo(otherId)),
                            chatterConfig.sleepAfterFailedNegotiation(),
                            List.of(
                                    new VersionCompareHandshake(appVersion, !settings.isGossipWithDifferentVersions()),
                                    new VersionCompareHandshake(
                                            PlatformVersion.locateOrDefault(),
                                            !settings.isGossipWithDifferentVersions())),
                            new NegotiationProtocols(List.of(
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
                                    new ChatterSyncProtocol(
                                            otherId,
                                            chatterPeer.communicationState(),
                                            chatterPeer.outputAggregator(),
                                            chatterSynchronizer,
                                            fallenBehindManager),
                                    new ChatterProtocol(chatterPeer, parallelExecutor)))))
                    .build());
        }

        thingsToStart.add(() -> chatterThreads.forEach(StoppableThread::start));

        final OtherParentTracker otherParentTracker = new OtherParentTracker();
        final EventCreationRules eventCreationRules = LoggingEventCreationRules.create(
                List.of(
                        startUpEventFrozenManager,
                        freezeManager,
                        fallenBehindManager,
                        new ChatteringRule(
                                chatterConfig.chatteringCreationThreshold(),
                                chatterCore.getPeerInstances().stream()
                                        .map(PeerInstance::communicationState)
                                        .toList()),
                        swirldStateManager.getTransactionPool(),
                        new BelowIntCreationRule(intakeQueue::size, chatterConfig.chatterIntakeThrottle())),
                List.of(
                        StaticCreationRules::nullOtherParent,
                        otherParentTracker,
                        new AncientParentsRule(consensusRef::get),
                        criticalQuorum));
        final ChatterEventCreator chatterEventCreator = new ChatterEventCreator(
                appVersion,
                selfId,
                PlatformConstructor.platformSigner(crypto.getKeysAndCerts()),
                swirldStateManager.getTransactionPool(),
                combineConsumers(
                        eventTaskCreator::createdEvent, otherParentTracker::track, chatterEventMapper::mapEvent),
                chatterEventMapper::getMostRecentEvent,
                eventCreationRules,
                CryptographyHolder.get(),
                OSTime.getInstance());

        if (startedFromGenesis) {
            // if we are starting from genesis, we will create a genesis event, which is the only event that will
            // ever be created without an other-parent
            chatterEventCreator.createGenesisEvent();
        }
        final EventCreatorThread eventCreatorThread = new EventCreatorThread(
                threadManager,
                selfId,
                chatterConfig.attemptedChatterEventPerSecond(),
                addressBook,
                chatterEventCreator::createEvent,
                CryptoStatic.getNonDetRandom());
        thingsToStart.add(eventCreatorThread::start);

        eventObserverDispatcher.addObserver(new ChatterNotifier(selfId, chatterCore));
        eventObserverDispatcher.addObserver(chatterEventMapper);

        clearAllInternalPipelines = new LoggingClearables(
                RECONNECT.getMarker(),
                List.of(
                        // chatter event creator needs to be cleared first, because it sends event to intake
                        Pair.of(eventCreatorThread, "eventCreatorThread"),
                        Pair.of(intakeQueue, "intakeQueue"),
                        // eventLinker is not thread safe, so the intake thread needs to be paused while it's being
                        // cleared
                        Pair.of(new PauseAndClear(intakeQueue, eventLinker), "eventLinker"),
                        Pair.of(eventMapper, "eventMapper"),
                        Pair.of(chatterEventMapper, "chatterEventMapper"),
                        Pair.of(shadowGraph, "shadowGraph")));
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
    @NonNull
    @Override
    protected CriticalQuorum buildCriticalQuorum() {
        final ChatterConfig chatterConfig = platformContext.getConfiguration().getConfigData(ChatterConfig.class);
        return new CriticalQuorumImpl(
                platformContext.getMetrics(), selfId, addressBook, false, chatterConfig.criticalQuorumSoftening());
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
     * Get the reconnect controller. This method is needed to break a circular dependency.
     */
    public ReconnectController getReconnectController() {
        return reconnectController;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void loadFromSignedState(@NonNull SignedState signedState) {
        chatterEventMapper.loadFromSignedState(signedState);
        chatterCore.loadFromSignedState(signedState);
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public InterruptableConsumer<EventIntakeTask> getEventIntakeLambda() {
        return intakeCycle;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void stop() {
        super.stop();
        chatterCore.stopChatter();
        for (final StoppableThread thread : chatterThreads) {
            thread.stop();
        }
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
    public void clear() {
        clearAllInternalPipelines.clear();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void pause() {
        throwIfNotInPhase(LifecyclePhase.STARTED);
        chatterCore.stopChatter();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void resume() {
        throwIfNotInPhase(LifecyclePhase.STARTED);
        chatterCore.startChatter();
    }
}
