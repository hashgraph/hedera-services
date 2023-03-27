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

package com.swirlds.platform;

import static com.swirlds.base.ArgumentUtils.throwArgNull;
import static com.swirlds.common.utility.CommonUtils.combineConsumers;
import static com.swirlds.platform.SwirldsPlatform.PLATFORM_THREAD_POOL_NAME;

import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.crypto.CryptographyHolder;
import com.swirlds.common.notification.NotificationEngine;
import com.swirlds.common.system.NodeId;
import com.swirlds.common.system.SoftwareVersion;
import com.swirlds.common.system.address.AddressBook;
import com.swirlds.common.threading.framework.QueueThread;
import com.swirlds.common.threading.framework.StoppableThread;
import com.swirlds.common.threading.framework.config.StoppableThreadConfiguration;
import com.swirlds.common.threading.manager.ThreadManager;
import com.swirlds.common.threading.pool.CachedPoolParallelExecutor;
import com.swirlds.common.threading.pool.ParallelExecutor;
import com.swirlds.common.threading.utility.SequenceCycle;
import com.swirlds.common.time.OSTime;
import com.swirlds.common.time.Time;
import com.swirlds.common.utility.Clearable;
import com.swirlds.common.utility.PlatformVersion;
import com.swirlds.common.utility.Startable;
import com.swirlds.platform.chatter.ChatterSyncProtocol;
import com.swirlds.platform.chatter.PrepareChatterEvent;
import com.swirlds.platform.chatter.communication.ChatterProtocol;
import com.swirlds.platform.chatter.config.ChatterConfig;
import com.swirlds.platform.chatter.protocol.ChatterCore;
import com.swirlds.platform.chatter.protocol.peer.PeerInstance;
import com.swirlds.platform.components.CriticalQuorum;
import com.swirlds.platform.components.EventCreationRules;
import com.swirlds.platform.components.EventTaskCreator;
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
import com.swirlds.platform.metrics.ReconnectMetrics;
import com.swirlds.platform.metrics.SyncMetrics;
import com.swirlds.platform.network.ConnectionTracker;
import com.swirlds.platform.network.NetworkMetrics;
import com.swirlds.platform.network.communication.NegotiationProtocols;
import com.swirlds.platform.network.communication.NegotiatorThread;
import com.swirlds.platform.network.communication.handshake.VersionCompareHandshake;
import com.swirlds.platform.network.topology.NetworkTopology;
import com.swirlds.platform.network.topology.StaticConnectionManagers;
import com.swirlds.platform.reconnect.DefaultSignedStateValidator;
import com.swirlds.platform.reconnect.FallenBehindManagerImpl;
import com.swirlds.platform.reconnect.ReconnectController;
import com.swirlds.platform.reconnect.ReconnectProtocol;
import com.swirlds.platform.reconnect.ReconnectThrottle;
import com.swirlds.platform.reconnect.emergency.EmergencyReconnectProtocol;
import com.swirlds.platform.state.EmergencyRecoveryManager;
import com.swirlds.platform.state.SwirldStateManager;
import com.swirlds.platform.sync.ShadowGraph;
import com.swirlds.platform.sync.ShadowGraphSynchronizer;
import com.swirlds.platform.sync.SyncManager;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.LinkedList;
import java.util.List;
import org.apache.commons.lang3.tuple.Pair;

/**
 * Encapsulates a chatter-based gossip network.
 */
public class ChatterNetwork extends GossipNetwork implements Startable {

    private final ChatterCore<GossipEvent> chatterCore;
    private final NetworkMetrics networkMetrics;
    private final SyncMetrics syncMetrics;
    private final EventTaskCreator eventTaskCreator;
    private final EmergencyRecoveryManager emergencyRecoveryManager;
    private final ShadowGraph shadowGraph;
    private final ReconnectController reconnectController;
    private final ReconnectThrottle reconnectThrottle;
    private final Consensus consensus;
    private final SyncManager syncManager;
    private final NotificationEngine notificationEngine;
    private final StateManagementComponent stateManagementComponent;
    private final FallenBehindManagerImpl fallenBehindManager;
    private final SwirldStateManager swirldStateManager;
    private final boolean startedFromGenesis;
    private final ReconnectMetrics reconnectMetrics;
    private final ChatterEventMapper chatterEventMapper = new ChatterEventMapper();
    private final StartUpEventFrozenManager startUpEventFrozenManager;
    private final FreezeManager freezeManager;
    private final CriticalQuorum criticalQuorum;
    private final QueueThread<EventIntakeTask> intakeQueue;
    private final SequenceCycle<EventIntakeTask> intakeCycle;

    /**
     * A list of threads that execute the chatter protocol.
     */
    private final List<StoppableThread> chatterThreads = new LinkedList<>(); // TODO methods that stop these threads

    public ChatterNetwork(
            @NonNull final PlatformContext platformContext,
            @NonNull final ThreadManager threadManager,
            @NonNull final Crypto crypto,
            @NonNull final Settings settings,
            @NonNull final AddressBook initialAddressBook,
            @NonNull final NodeId selfId,
            @NonNull final SoftwareVersion appVersion,
            @NonNull final NetworkTopology topology,
            @NonNull final ConnectionTracker connectionTracker,
            @NonNull final Time time,
            @NonNull final NetworkMetrics networkMetrics,
            @NonNull final SyncMetrics syncMetrics,
            @NonNull final EventTaskCreator eventTaskCreator,
            @NonNull final EmergencyRecoveryManager emergencyRecoveryManager,
            @NonNull final ShadowGraph shadowGraph,
            @NonNull final ReconnectController reconnectController,
            @NonNull final ReconnectThrottle reconnectThrottle,
            @NonNull final Consensus consensus,
            @NonNull final SyncManager syncManager,
            @NonNull final NotificationEngine notificationEngine,
            @NonNull final StateManagementComponent stateManagementComponent,
            @NonNull final FallenBehindManagerImpl fallenBehindManager,
            @NonNull final SwirldStateManager swirldStateManager,
            final boolean startedFromGenesis,
            @NonNull final ReconnectMetrics reconnectMetrics,
            @NonNull final StartUpEventFrozenManager startUpEventFrozenManager,
            @NonNull final FreezeManager freezeManager,
            @NonNull final CriticalQuorum criticalQuorum,
            @NonNull final QueueThread<EventIntakeTask> intakeQueue,
            @NonNull final SequenceCycle<EventIntakeTask> intakeCycle) {

        super(
                platformContext,
                threadManager,
                crypto,
                settings,
                initialAddressBook,
                selfId,
                appVersion,
                topology,
                connectionTracker);

        this.networkMetrics = throwArgNull(networkMetrics, "networkMetrics");
        this.syncMetrics = throwArgNull(syncMetrics, "syncMetrics");
        this.eventTaskCreator = throwArgNull(eventTaskCreator, "eventTaskCreator");
        this.emergencyRecoveryManager = throwArgNull(emergencyRecoveryManager, "emergencyRecoveryManager");
        this.shadowGraph = throwArgNull(shadowGraph, "shadowGraph");
        this.reconnectController = throwArgNull(reconnectController, "reconnectController");
        this.reconnectThrottle = throwArgNull(reconnectThrottle, "reconnectThrottle");
        this.consensus = throwArgNull(consensus, "consensus");
        this.syncManager = throwArgNull(syncManager, "syncManager");
        this.notificationEngine = throwArgNull(notificationEngine, "notificationEngine");
        this.stateManagementComponent = throwArgNull(stateManagementComponent, "stateManagementComponent");
        this.fallenBehindManager = throwArgNull(fallenBehindManager, "fallenBehindManager");
        this.swirldStateManager = throwArgNull(swirldStateManager, "swirldStateManager");
        this.startedFromGenesis = startedFromGenesis;
        this.reconnectMetrics = throwArgNull(reconnectMetrics, "reconnectMetrics");
        this.startUpEventFrozenManager = throwArgNull(startUpEventFrozenManager, "startUpEventFrozenManager");
        this.freezeManager = throwArgNull(freezeManager, "freezeManager");
        this.criticalQuorum = throwArgNull(criticalQuorum, "criticalQuorum");
        this.intakeQueue = throwArgNull(intakeQueue, "intakeQueue");
        this.intakeCycle = throwArgNull(intakeCycle, "intakeCycle");

        chatterCore = new ChatterCore<>(
                time,
                GossipEvent.class,
                new PrepareChatterEvent(CryptographyHolder.get()),
                settings.getChatter(),
                networkMetrics::recordPingTime,
                platformContext.getMetrics());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void start() {
        final StaticConnectionManagers connectionManagers = startCommonNetwork();

        // first create all instances because of thread safety
        for (final NodeId otherId : topology.getNeighbors()) {
            chatterCore.newPeerInstance(otherId.getId(), eventTaskCreator::addEvent);
        }

        // If we still need an emergency recovery state, we need it via emergency reconnect.
        // Start the helper now so that it is ready to receive a connection to perform reconnect with when the
        // protocol is initiated.
        // This must be after all chatter peer instances are created so that the chatter comm state can be suspended
        if (emergencyRecoveryManager.isEmergencyStateRequired()) {
            reconnectController.start();
        }

        final ParallelExecutor parallelExecutor = new CachedPoolParallelExecutor(threadManager, "chatter");
        parallelExecutor.start();
        for (final NodeId otherId : topology.getNeighbors()) {
            final PeerInstance chatterPeer = chatterCore.getPeerInstance(otherId.getId());
            final ParallelExecutor shadowgraphExecutor = PlatformConstructor.parallelExecutor(threadManager);
            shadowgraphExecutor.start();
            final ShadowGraphSynchronizer chatterSynchronizer = new ShadowGraphSynchronizer(
                    shadowGraph,
                    initialAddressBook.getSize(),
                    syncMetrics,
                    consensus,
                    sr -> {},
                    eventTaskCreator::addEvent,
                    fallenBehindManager,
                    shadowgraphExecutor,
                    false,
                    () -> {
                        // start accepting events into the chatter queue
                        chatterPeer.communicationState().chatterSyncStartingPhase3();
                        // wait for any intake event currently being processed to finish
                        intakeCycle.waitForCurrentSequenceEnd();
                    });

            final ChatterConfig chatterConfig =
                    platformContext.getConfiguration().getConfigData(ChatterConfig.class);

            chatterThreads.add(new StoppableThreadConfiguration<>(threadManager)
                    .setPriority(Thread.NORM_PRIORITY)
                    .setNodeId(selfId.getId())
                    .setComponent(PLATFORM_THREAD_POOL_NAME)
                    .setOtherNodeId(otherId.getId())
                    .setThreadName("ChatterReader")
                    .setHangingThreadPeriod(chatterConfig.hangingThreadDuration())
                    .setWork(new NegotiatorThread(
                            connectionManagers.getManager(otherId, topology.shouldConnectTo(otherId)),
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
                                            settings.getReconnect().getAsyncStreamTimeoutMilliseconds(),
                                            reconnectMetrics,
                                            reconnectController),
                                    new ReconnectProtocol(
                                            threadManager,
                                            otherId,
                                            reconnectThrottle,
                                            () -> stateManagementComponent
                                                    .getLatestSignedState()
                                                    .get(),
                                            settings.getReconnect().getAsyncStreamTimeoutMilliseconds(),
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
                    .build(true));
        }
        final OtherParentTracker otherParentTracker = new OtherParentTracker();
        final EventCreationRules eventCreationRules = LoggingEventCreationRules.create(
                List.of(
                        startUpEventFrozenManager,
                        freezeManager,
                        fallenBehindManager,
                        new ChatteringRule(
                                settings.getChatter().getChatteringCreationThreshold(),
                                chatterCore.getPeerInstances().stream()
                                        .map(PeerInstance::communicationState)
                                        .toList()),
                        swirldStateManager.getTransactionPool(),
                        new BelowIntCreationRule(
                                intakeQueue::size, settings.getChatter().getChatterIntakeThrottle())),
                List.of(
                        StaticCreationRules::nullOtherParent,
                        otherParentTracker,
                        new AncientParentsRule(consensus),
                        criticalQuorum));
        final ChatterEventCreator chatterEventCreator = new ChatterEventCreator(
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
                settings.getChatter().getAttemptedChatterEventPerSecond(),
                initialAddressBook,
                chatterEventCreator::createEvent,
                CryptoStatic.getNonDetRandom());

        // TODO
        //        clearAllPipelines = new LoggingClearables(
        //                RECONNECT.getMarker(),
        //                List.of(
        //                        // chatter event creator needs to be cleared first, because it sends event to intake
        //                        Pair.of(eventCreatorThread, "eventCreatorThread"),
        //                        Pair.of(intakeQueue, "intakeQueue"),
        //                        // eventLinker is not thread safe, so the intake thread needs to be paused while its
        // being
        //                        // cleared
        //                        Pair.of(new PauseAndClear(intakeQueue, eventLinker), "eventLinker"),
        //                        Pair.of(eventMapper, "eventMapper"),
        //                        Pair.of(chatterEventMapper, "chatterEventMapper"),
        //                        Pair.of(shadowGraph, "shadowGraph"),
        //                        Pair.of(preConsensusEventHandler, "preConsensusEventHandler"),
        //                        Pair.of(consensusRoundHandler, "consensusRoundHandler"),
        //                        Pair.of(swirldStateManager, "swirldStateManager")));
        eventCreatorThread.start();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void halt() {
        for (final StoppableThread thread : chatterThreads) {
            thread.stop();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<Pair<Clearable, String>> getClearables() {
        return List.of(); // TODO
    }
}
