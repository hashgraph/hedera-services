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

package com.swirlds.platform.gossip.chatter;

/**
 * Gossip implemented with the chatter protocol.
 */
public class ChatterGossip /*extends AbstractGossip*/ {

    //    private final ReconnectController reconnectController;
    //    private final ChatterCore<GossipEvent> chatterCore;
    //    private final List<StoppableThread> chatterThreads = new LinkedList<>();
    //    private final SequenceCycle<GossipEvent> intakeCycle;
    //
    //    /**
    //     * Holds a list of objects that need to be cleared when {@link #clear()} is called on this object.
    //     */
    //    private final Clearable clearAllInternalPipelines;
    //
    //    /**
    //     * Builds the gossip engine that implements the chatter v1 algorithm.
    //     *
    //     * @param platformContext               the platform context
    //     * @param threadManager                 the thread manager
    //     * @param time                          the wall clock time
    //     * @param keysAndCerts                  private keys and public certificates
    //     * @param notificationEngine            used to send notifications to the app
    //     * @param addressBook                   the current address book
    //     * @param selfId                        this node's ID
    //     * @param appVersion                    the version of the app
    //     * @param epochHash                     the epoch hash of the initial state
    //     * @param shadowGraph                   contains non-ancient events
    //     * @param emergencyRecoveryManager      handles emergency recovery
    //     * @param consensusRef                  a pointer to consensus
    //     * @param intakeQueue                   the event intake queue
    //     * @param swirldStateManager            manages the mutable state
    //     * @param latestCompleteState           holds the latest signed state that has enough signatures to be
    // verifiable
    //     * @param eventValidator                validates events and passes valid events along the intake pipeline
    //     * @param eventObserverDispatcher       the object used to wire event intake
    //     * @param syncMetrics                   metrics for sync
    //     * @param eventLinker                   links together events, if chatter is enabled will also buffer orphans
    //     * @param platformStatusManager         the platform status manager
    //     * @param loadReconnectState            a method that should be called when a state from reconnect is obtained
    //     * @param clearAllPipelinesForReconnect this method should be called to clear all pipelines prior to a
    // reconnect
    //     * @param emergencyStateSupplier        returns the emergency state if available
    //     */
    //    public ChatterGossip(
    //            @NonNull final PlatformContext platformContext,
    //            @NonNull final ThreadManager threadManager,
    //            @NonNull final Time time,
    //            @NonNull final KeysAndCerts keysAndCerts,
    //            @NonNull final NotificationEngine notificationEngine,
    //            @NonNull final AddressBook addressBook,
    //            @NonNull final NodeId selfId,
    //            @NonNull final SoftwareVersion appVersion,
    //            @Nullable final Hash epochHash,
    //            @NonNull final ShadowGraph shadowGraph,
    //            @NonNull final EmergencyRecoveryManager emergencyRecoveryManager,
    //            @NonNull final AtomicReference<Consensus> consensusRef,
    //            @NonNull final QueueThread<GossipEvent> intakeQueue,
    //            @NonNull final SwirldStateManager swirldStateManager,
    //            @NonNull final SignedStateNexus latestCompleteState,
    //            @NonNull final EventValidator eventValidator,
    //            @NonNull final EventObserverDispatcher eventObserverDispatcher,
    //            @NonNull final SyncMetrics syncMetrics,
    //            @NonNull final EventLinker eventLinker,
    //            @NonNull final PlatformStatusManager platformStatusManager,
    //            @NonNull final Consumer<SignedState> loadReconnectState,
    //            @NonNull final Runnable clearAllPipelinesForReconnect,
    //            @NonNull final Supplier<ReservedSignedState> emergencyStateSupplier) {
    //        super(
    //                platformContext,
    //                threadManager,
    //                time,
    //                keysAndCerts,
    //                addressBook,
    //                selfId,
    //                appVersion,
    //                intakeQueue,
    //                swirldStateManager,
    //                latestCompleteState,
    //                syncMetrics,
    //                platformStatusManager,
    //                loadReconnectState,
    //                clearAllPipelinesForReconnect);
    //
    //        final BasicConfig basicConfig = platformContext.getConfiguration().getConfigData(BasicConfig.class);
    //        final ChatterConfig chatterConfig = platformContext.getConfiguration().getConfigData(ChatterConfig.class);
    //        final ProtocolConfig protocolConfig =
    // platformContext.getConfiguration().getConfigData(ProtocolConfig.class);
    //
    //        chatterCore = new ChatterCore<>(
    //                time,
    //                GossipEvent.class,
    //                new PrepareChatterEvent(CryptographyHolder.get()),
    //                chatterConfig,
    //                networkMetrics::recordPingTime,
    //                platformContext.getMetrics());
    //
    //        final ReconnectConfig reconnectConfig =
    //                platformContext.getConfiguration().getConfigData(ReconnectConfig.class);
    //
    //        reconnectController = new ReconnectController(reconnectConfig, threadManager, reconnectHelper,
    // this::resume);
    //
    //        // first create all instances because of thread safety
    //        for (final NodeId otherId : topology.getNeighbors()) {
    //            chatterCore.newPeerInstance(otherId, intakeQueue::add);
    //        }
    //
    //        if (emergencyRecoveryManager.isEmergencyStateRequired()) {
    //            // If we still need an emergency recovery state, we need it via emergency reconnect.
    //            // Start the helper first so that it is ready to receive a connection to perform reconnect with when
    // the
    //            // protocol is initiated.
    //            thingsToStart.add(0, reconnectController::start);
    //        }
    //
    //        intakeCycle = new SequenceCycle<>(eventValidator::validateEvent);
    //
    //        final ParallelExecutor parallelExecutor = new CachedPoolParallelExecutor(threadManager, "chatter");
    //        parallelExecutor.start();
    //        for (final NodeId otherId : topology.getNeighbors()) {
    //            final PeerInstance chatterPeer = chatterCore.getPeerInstance(otherId);
    //            final ParallelExecutor shadowgraphExecutor = new CachedPoolParallelExecutor(threadManager,
    // "node-sync");
    //            shadowgraphExecutor.start();
    //            final ShadowGraphSynchronizer chatterSynchronizer = new ShadowGraphSynchronizer(
    //                    platformContext,
    //                    time,
    //                    shadowGraph,
    //                    null,
    //                    addressBook.getSize(),
    //                    syncMetrics,
    //                    consensusRef::get,
    //                    intakeQueue,
    //                    syncManager,
    //                    new NoOpIntakeEventCounter(),
    //                    shadowgraphExecutor,
    //                    false,
    //                    () -> {
    //                        // start accepting events into the chatter queue
    //                        chatterPeer.communicationState().chatterSyncStartingPhase3();
    //                        // wait for any intake event currently being processed to finish
    //                        intakeCycle.waitForCurrentSequenceEnd();
    //                    });
    //
    //            chatterThreads.add(new StoppableThreadConfiguration<>(threadManager)
    //                    .setPriority(Thread.NORM_PRIORITY)
    //                    .setNodeId(selfId)
    //                    .setComponent(PLATFORM_THREAD_POOL_NAME)
    //                    .setOtherNodeId(otherId)
    //                    .setThreadName("ChatterReader")
    //                    .setHangingThreadPeriod(basicConfig.hangingThreadDuration())
    //                    .setWork(new NegotiatorThread(
    //                            connectionManagers.getManager(otherId, topology.shouldConnectTo(otherId)),
    //                            chatterConfig.sleepAfterFailedNegotiation(),
    //                            List.of(
    //                                    new VersionCompareHandshake(
    //                                            appVersion, !protocolConfig.tolerateMismatchedVersion()),
    //                                    new HashCompareHandshake(epochHash,
    // !protocolConfig.tolerateMismatchedEpochHash())),
    //                            new NegotiationProtocols(List.of(
    //                                    new EmergencyReconnectProtocol(
    //                                            time,
    //                                            threadManager,
    //                                            notificationEngine,
    //                                            otherId,
    //                                            emergencyRecoveryManager,
    //                                            reconnectThrottle,
    //                                            emergencyStateSupplier,
    //                                            reconnectConfig.asyncStreamTimeout(),
    //                                            reconnectMetrics,
    //                                            reconnectController,
    //                                            platformStatusManager,
    //                                            platformContext.getConfiguration()),
    //                                    new ReconnectProtocol(
    //                                            threadManager,
    //                                            otherId,
    //                                            reconnectThrottle,
    //                                            () -> latestCompleteState.getState("SwirldsPlatform:
    // ReconnectProtocol"),
    //                                            reconnectConfig.asyncStreamTimeout(),
    //                                            reconnectMetrics,
    //                                            reconnectController,
    //                                            new DefaultSignedStateValidator(platformContext),
    //                                            fallenBehindManager,
    //                                            platformStatusManager,
    //                                            platformContext.getConfiguration(),
    //                                            time),
    //                                    new ChatterSyncProtocol(
    //                                            platformContext,
    //                                            otherId,
    //                                            chatterPeer.communicationState(),
    //                                            chatterPeer.outputAggregator(),
    //                                            chatterSynchronizer,
    //                                            fallenBehindManager),
    //                                    new ChatterProtocol(chatterPeer, parallelExecutor)))))
    //                    .build());
    //        }
    //
    //        thingsToStart.add(() -> chatterThreads.forEach(StoppableThread::start));
    //
    //        eventObserverDispatcher.addObserver(new ChatterNotifier(selfId, chatterCore));
    //
    //        clearAllInternalPipelines = new LoggingClearables(
    //                RECONNECT.getMarker(),
    //                List.of(
    //                        Pair.of(intakeQueue, "intakeQueue"),
    //                        // eventLinker is not thread safe, so the intake thread needs to be paused while it's
    // being
    //                        // cleared
    //                        Pair.of(new PauseAndClear(intakeQueue, eventLinker), "eventLinker"),
    //                        Pair.of(shadowGraph, "shadowGraph")));
    //    }
    //
    //    /**
    //     * {@inheritDoc}
    //     */
    //    @Override
    //    protected boolean unidirectionalConnectionsEnabled() {
    //        return false;
    //    }
    //
    //    /**
    //     * {@inheritDoc}
    //     */
    //    @NonNull
    //    @Override
    //    protected FallenBehindManagerImpl buildFallenBehindManager() {
    //        return new FallenBehindManagerImpl(
    //                addressBook,
    //                selfId,
    //                topology.getConnectionGraph(),
    //                statusActionSubmitter,
    //                () -> getReconnectController().start(),
    //                platformContext.getConfiguration().getConfigData(ReconnectConfig.class));
    //    }
    //
    //    /**
    //     * Get the reconnect controller. This method is needed to break a circular dependency.
    //     */
    //    public ReconnectController getReconnectController() {
    //        return reconnectController;
    //    }
    //
    //    /**
    //     * {@inheritDoc}
    //     */
    //    @Override
    //    public void loadFromSignedState(@NonNull SignedState signedState) {
    //        chatterCore.loadFromSignedState(signedState);
    //    }
    //
    //    /**
    //     * {@inheritDoc}
    //     */
    //    @Override
    //    public void stop() {
    //        super.stop();
    //        chatterCore.stopChatter();
    //        for (final StoppableThread thread : chatterThreads) {
    //            thread.stop();
    //        }
    //    }
    //
    //    /**
    //     * {@inheritDoc}
    //     */
    //    @Override
    //    protected boolean shouldDoVersionCheck() {
    //        return false;
    //    }
    //
    //    /**
    //     * {@inheritDoc}
    //     */
    //    @Override
    //    public void clear() {
    //        clearAllInternalPipelines.clear();
    //    }
    //
    //    /**
    //     * {@inheritDoc}
    //     */
    //    @Override
    //    public void pause() {
    //        throwIfNotInPhase(LifecyclePhase.STARTED);
    //        chatterCore.stopChatter();
    //    }
    //
    //    /**
    //     * {@inheritDoc}
    //     */
    //    @Override
    //    public void resume() {
    //        throwIfNotInPhase(LifecyclePhase.STARTED);
    //        chatterCore.startChatter();
    //    }
}
