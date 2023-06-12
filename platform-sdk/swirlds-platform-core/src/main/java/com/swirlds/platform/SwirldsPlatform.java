/*
 * Copyright (C) 2016-2023 Hedera Hashgraph, LLC
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

import static com.swirlds.common.threading.interrupt.Uninterruptable.abortAndThrowIfInterrupted;
import static com.swirlds.common.threading.manager.AdHocThreadManager.getStaticThreadManager;
import static com.swirlds.logging.LogMarker.EXCEPTION;
import static com.swirlds.logging.LogMarker.PLATFORM_STATUS;
import static com.swirlds.logging.LogMarker.RECONNECT;
import static com.swirlds.logging.LogMarker.STARTUP;
import static com.swirlds.platform.state.GenesisStateBuilder.buildGenesisState;
import static com.swirlds.platform.state.address.AddressBookMetrics.registerAddressBookMetrics;
import static com.swirlds.platform.state.signed.ReservedSignedState.createNullReservation;

import com.swirlds.base.state.Startable;
import com.swirlds.common.config.BasicConfig;
import com.swirlds.common.config.ConsensusConfig;
import com.swirlds.common.config.StateConfig;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.crypto.Signature;
import com.swirlds.common.io.utility.RecycleBin;
import com.swirlds.common.merkle.MerkleNode;
import com.swirlds.common.merkle.crypto.MerkleCryptoFactory;
import com.swirlds.common.merkle.route.MerkleRouteIterator;
import com.swirlds.common.merkle.utility.MerkleTreeVisualizer;
import com.swirlds.common.metrics.FunctionGauge;
import com.swirlds.common.metrics.Metrics;
import com.swirlds.common.notification.NotificationEngine;
import com.swirlds.common.notification.listeners.PlatformStatusChangeListener;
import com.swirlds.common.notification.listeners.PlatformStatusChangeNotification;
import com.swirlds.common.notification.listeners.ReconnectCompleteListener;
import com.swirlds.common.notification.listeners.ReconnectCompleteNotification;
import com.swirlds.common.notification.listeners.StateLoadedFromDiskCompleteListener;
import com.swirlds.common.notification.listeners.StateLoadedFromDiskNotification;
import com.swirlds.common.stream.EventStreamManager;
import com.swirlds.common.system.InitTrigger;
import com.swirlds.common.system.NodeId;
import com.swirlds.common.system.Platform;
import com.swirlds.common.system.SoftwareVersion;
import com.swirlds.common.system.SwirldState;
import com.swirlds.common.system.address.Address;
import com.swirlds.common.system.address.AddressBook;
import com.swirlds.common.system.platformstatus.PlatformStatus;
import com.swirlds.common.system.transaction.internal.SwirldTransaction;
import com.swirlds.common.system.transaction.internal.SystemTransaction;
import com.swirlds.common.threading.framework.QueueThread;
import com.swirlds.common.threading.framework.config.QueueThreadConfiguration;
import com.swirlds.common.threading.framework.config.QueueThreadMetricsConfiguration;
import com.swirlds.common.threading.manager.ThreadManager;
import com.swirlds.common.time.OSTime;
import com.swirlds.common.time.Time;
import com.swirlds.common.utility.AutoCloseableWrapper;
import com.swirlds.common.utility.Clearable;
import com.swirlds.common.utility.LoggingClearables;
import com.swirlds.logging.LogMarker;
import com.swirlds.logging.payloads.PlatformStatusPayload;
import com.swirlds.logging.payloads.SavedStateLoadedPayload;
import com.swirlds.platform.components.EventCreationRules;
import com.swirlds.platform.components.EventCreator;
import com.swirlds.platform.components.EventIntake;
import com.swirlds.platform.components.EventMapper;
import com.swirlds.platform.components.EventTaskDispatcher;
import com.swirlds.platform.components.appcomm.AppCommunicationComponent;
import com.swirlds.platform.components.state.StateManagementComponent;
import com.swirlds.platform.components.transaction.system.PostConsensusSystemTransactionManager;
import com.swirlds.platform.components.transaction.system.PostConsensusSystemTransactionManagerFactory;
import com.swirlds.platform.components.transaction.system.PreConsensusSystemTransactionManager;
import com.swirlds.platform.components.transaction.system.PreConsensusSystemTransactionManagerFactory;
import com.swirlds.platform.components.wiring.ManualWiring;
import com.swirlds.platform.config.ThreadConfig;
import com.swirlds.platform.dispatch.DispatchBuilder;
import com.swirlds.platform.dispatch.DispatchConfiguration;
import com.swirlds.platform.dispatch.triggers.flow.DiskStateLoadedTrigger;
import com.swirlds.platform.dispatch.triggers.flow.ReconnectStateLoadedTrigger;
import com.swirlds.platform.event.EventCounter;
import com.swirlds.platform.event.EventIntakeTask;
import com.swirlds.platform.event.EventUtils;
import com.swirlds.platform.event.linking.EventLinker;
import com.swirlds.platform.event.linking.InOrderLinker;
import com.swirlds.platform.event.linking.OrphanBufferingLinker;
import com.swirlds.platform.event.linking.ParentFinder;
import com.swirlds.platform.event.preconsensus.AsyncPreconsensusEventWriter;
import com.swirlds.platform.event.preconsensus.NoOpPreconsensusEventWriter;
import com.swirlds.platform.event.preconsensus.PreconsensusEventFileManager;
import com.swirlds.platform.event.preconsensus.PreconsensusEventReplayWorkflow;
import com.swirlds.platform.event.preconsensus.PreconsensusEventStreamConfig;
import com.swirlds.platform.event.preconsensus.PreconsensusEventStreamSequencer;
import com.swirlds.platform.event.preconsensus.PreconsensusEventWriter;
import com.swirlds.platform.event.preconsensus.SyncPreconsensusEventWriter;
import com.swirlds.platform.event.validation.AncientValidator;
import com.swirlds.platform.event.validation.EventDeduplication;
import com.swirlds.platform.event.validation.EventValidator;
import com.swirlds.platform.event.validation.GossipEventValidator;
import com.swirlds.platform.event.validation.GossipEventValidators;
import com.swirlds.platform.event.validation.SignatureValidator;
import com.swirlds.platform.event.validation.StaticValidators;
import com.swirlds.platform.event.validation.TransactionSizeValidator;
import com.swirlds.platform.eventhandling.ConsensusRoundHandler;
import com.swirlds.platform.eventhandling.PreConsensusEventHandler;
import com.swirlds.platform.gossip.Gossip;
import com.swirlds.platform.gossip.GossipFactory;
import com.swirlds.platform.gossip.chatter.config.ChatterConfig;
import com.swirlds.platform.gossip.chatter.protocol.messages.EventDescriptor;
import com.swirlds.platform.gossip.shadowgraph.ShadowGraph;
import com.swirlds.platform.gossip.shadowgraph.ShadowGraphEventObserver;
import com.swirlds.platform.gui.GuiPlatformAccessor;
import com.swirlds.platform.intake.IntakeCycleStats;
import com.swirlds.platform.internal.EventImpl;
import com.swirlds.platform.metrics.AddedEventMetrics;
import com.swirlds.platform.metrics.ConsensusHandlingMetrics;
import com.swirlds.platform.metrics.ConsensusMetrics;
import com.swirlds.platform.metrics.ConsensusMetricsImpl;
import com.swirlds.platform.metrics.EventIntakeMetrics;
import com.swirlds.platform.metrics.RuntimeMetrics;
import com.swirlds.platform.metrics.SyncMetrics;
import com.swirlds.platform.metrics.TransactionMetrics;
import com.swirlds.platform.observers.ConsensusRoundObserver;
import com.swirlds.platform.observers.EventObserverDispatcher;
import com.swirlds.platform.observers.PreConsensusEventObserver;
import com.swirlds.platform.state.EmergencyRecoveryManager;
import com.swirlds.platform.state.State;
import com.swirlds.platform.state.SwirldStateManager;
import com.swirlds.platform.state.signed.ReservedSignedState;
import com.swirlds.platform.state.signed.SignedState;
import com.swirlds.platform.state.signed.SourceOfSignedState;
import com.swirlds.platform.stats.StatConstructor;
import com.swirlds.platform.system.Shutdown;
import com.swirlds.platform.system.SystemExitCode;
import com.swirlds.platform.system.SystemExitUtils;
import com.swirlds.platform.threading.PauseAndLoad;
import com.swirlds.platform.util.PlatformComponents;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import java.util.function.Supplier;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class SwirldsPlatform implements Platform, Startable {

    public static final String PLATFORM_THREAD_POOL_NAME = "platform-core";
    /** use this for all logging, as controlled by the optional data/log4j2.xml file */
    private static final Logger logger = LogManager.getLogger(SwirldsPlatform.class);
    /**
     * the ID of the member running this. Since a node can be a main node or a mirror node, the ID is not a primitive
     * value
     */
    private final NodeId selfId;

    /** A type used by Hashgraph, Statistics, and SyncUtils. Only Hashgraph modifies this type instance. */
    private final EventMapper eventMapper;
    /** The platforms freeze manager */
    private final FreezeManager freezeManager;
    /** is used for pausing event creation for a while at start up */
    private final StartUpEventFrozenManager startUpEventFrozenManager;
    /**
     * The shadow graph manager. This wraps a shadow graph, which is an Event graph that adds child pointers to the
     * Hashgraph Event graph. Used for gossiping.
     */
    private final ShadowGraph shadowGraph;
    /** The last status of the platform that was determined */
    private final AtomicReference<PlatformStatus> currentPlatformStatus =
            new AtomicReference<>(PlatformStatus.STARTING_UP);
    /**
     * the object used to calculate consensus. it is volatile because the whole object is replaced when reading a state
     * from disk or getting it through reconnect
     */
    private final AtomicReference<Consensus> consensusRef = new AtomicReference<>();
    /** set in the constructor and given to the SwirldState object in run() */
    private final AddressBook initialAddressBook;

    private final Metrics metrics;

    private final ConsensusMetrics consensusMetrics;

    /** the object that contains all key pairs and CSPRNG state for this member */
    private final Crypto crypto;
    /**
     * True if this node started from genesis.
     */
    private final boolean startedFromGenesis;
    /**
     * If a state was loaded from disk, this will have the round of that state.
     */
    private final long diskStateRound;
    /**
     * If a state was loaded from disk, this is the minimum generation non-ancient for that round. If starting from a
     * genesis state, this is 0.
     */
    private final long initialMinimumGenerationNonAncient;
    /**
     * If a state was loaded from disk, this will have the hash of that state.
     */
    private final Hash diskStateHash;

    private final StateManagementComponent stateManagementComponent;
    private final EventTaskDispatcher eventTaskDispatcher;
    private final QueueThread<EventIntakeTask> intakeQueue;
    private final QueueThread<ReservedSignedState> stateHashSignQueue;
    private final EventLinker eventLinker;
    /** Stores and processes consensus events including sending them to {@link SwirldStateManager} for handling */
    private final ConsensusRoundHandler consensusRoundHandler;
    /** Handles all interaction with {@link SwirldState} */
    private final SwirldStateManager swirldStateManager;
    /** Checks the validity of transactions and submits valid ones to the event transaction pool */
    private final SwirldTransactionSubmitter transactionSubmitter;
    /** clears all pipelines to prepare for a reconnect */
    private final Clearable clearAllPipelines;

    /**
     * Responsible for managing the lifecycle of threads on this platform.
     */
    private final ThreadManager threadManager;

    /**
     * All components that need to be started or that have dispatch observers.
     */
    private final PlatformComponents components;

    /**
     * Call this when a reconnect has been completed.
     */
    private final ReconnectStateLoadedTrigger reconnectStateLoadedDispatcher;

    /**
     * Call this when a state has been loaded from disk.
     */
    private final DiskStateLoadedTrigger diskStateLoadedDispatcher;
    /**
     * The current version of the app running on this platform.
     */
    private final SoftwareVersion appVersion;
    /**
     * For passing notifications between the platform and the application.
     */
    private final NotificationEngine notificationEngine;

    /**
     * The platform context for this platform. Should be used to access basic services
     */
    private final PlatformContext platformContext;

    /**
     * Can be used to read preconsensus event files from disk.
     */
    private final PreconsensusEventFileManager preconsensusEventFileManager;

    /**
     * Writes preconsensus events to disk.
     */
    private final PreconsensusEventWriter preconsensusEventWriter;

    /**
     * Responsible for transmitting and receiving events from the network.
     */
    private final Gossip gossip;

    /**
     * Allows files to be deleted, and potentially recovered later for debugging.
     */
    private final RecycleBin recycleBin;

    /**
     * the browser gives the Platform what app to run. There can be multiple Platforms on one computer.
     *
     * @param platformContext          the context for this platform
     * @param crypto                   an object holding all the public/private key pairs and the CSPRNG state for this
     *                                 member
     * @param initialAddressBook       the address book listing all members in the community
     * @param id                       the ID number for this member (if this computer has multiple members in one
     *                                 swirld)
     * @param mainClassName            the name of the app class inheriting from SwirldMain
     * @param swirldName               the name of the swirld being run
     * @param appVersion               the current version of the running application
     * @param genesisStateBuilder      used to construct a genesis state if no suitable state from disk can be found
     * @param loadedSignedState        used to initialize the loaded state
     * @param emergencyRecoveryManager used in emergency recovery.
     * @param softwareUpgrade          if true this is a software upgrade, if false then this is just a restart
     */
    SwirldsPlatform(
            @NonNull final PlatformContext platformContext,
            @NonNull final Crypto crypto,
            @NonNull final AddressBook initialAddressBook,
            @NonNull final NodeId id,
            @NonNull final String mainClassName,
            @NonNull final String swirldName,
            @NonNull final SoftwareVersion appVersion,
            @NonNull final Supplier<SwirldState> genesisStateBuilder,
            @NonNull final ReservedSignedState loadedSignedState,
            @NonNull final EmergencyRecoveryManager emergencyRecoveryManager,
            final boolean softwareUpgrade) {

        this.platformContext = Objects.requireNonNull(platformContext, "platformContext");
        final Time time = OSTime.getInstance();

        final DispatchBuilder dispatchBuilder =
                new DispatchBuilder(platformContext.getConfiguration().getConfigData(DispatchConfiguration.class));

        components = new PlatformComponents(dispatchBuilder);

        // FUTURE WORK: use a real thread manager here
        threadManager = getStaticThreadManager();

        notificationEngine = NotificationEngine.buildEngine(threadManager);

        dispatchBuilder.registerObservers(this);

        reconnectStateLoadedDispatcher =
                dispatchBuilder.getDispatcher(this, ReconnectStateLoadedTrigger.class)::dispatch;
        diskStateLoadedDispatcher = dispatchBuilder.getDispatcher(this, DiskStateLoadedTrigger.class)::dispatch;

        final StateConfig stateConfig = platformContext.getConfiguration().getConfigData(StateConfig.class);
        final String actualMainClassName = stateConfig.getMainClassName(mainClassName);

        this.appVersion = appVersion;

        this.selfId = id;
        this.initialAddressBook = initialAddressBook;

        this.eventMapper = new EventMapper(platformContext.getMetrics(), selfId);

        this.metrics = platformContext.getMetrics();

        metrics.getOrCreate(StatConstructor.createEnumStat(
                "PlatformStatus", Metrics.PLATFORM_CATEGORY, PlatformStatus.values(), currentPlatformStatus::get));

        registerAddressBookMetrics(metrics, initialAddressBook, selfId);

        try {
            recycleBin = new RecycleBin(platformContext.getConfiguration(), selfId);
            if (softwareUpgrade) {
                recycleBin.clear();
            }
        } catch (final IOException e) {
            throw new UncheckedIOException("Failed to initialize recycle bin", e);
        }

        this.consensusMetrics = new ConsensusMetricsImpl(this.selfId, metrics);

        final EventIntakeMetrics eventIntakeMetrics = new EventIntakeMetrics(metrics, time);
        final SyncMetrics syncMetrics = new SyncMetrics(metrics);
        RuntimeMetrics.setup(metrics);

        this.shadowGraph = new ShadowGraph(syncMetrics, initialAddressBook.getSize());

        this.crypto = crypto;

        startUpEventFrozenManager = new StartUpEventFrozenManager(metrics, Instant::now);
        freezeManager = new FreezeManager(this::checkPlatformStatus);
        FreezeMetrics.registerFreezeMetrics(metrics, freezeManager, startUpEventFrozenManager);
        EventCounter.registerEventCounterMetrics(metrics);

        // Manually wire components for now.
        final ManualWiring wiring = new ManualWiring(platformContext, threadManager, getAddressBook(), freezeManager);
        metrics.addUpdater(wiring::updateMetrics);
        final AppCommunicationComponent appCommunicationComponent =
                wiring.wireAppCommunicationComponent(notificationEngine);

        preconsensusEventFileManager = buildPreconsensusEventFileManager();
        preconsensusEventWriter = components.add(buildPreconsensusEventWriter(preconsensusEventFileManager));

        stateManagementComponent = wiring.wireStateManagementComponent(
                PlatformConstructor.platformSigner(crypto.getKeysAndCerts()),
                actualMainClassName,
                selfId,
                swirldName,
                txn -> this.createSystemTransaction(txn, true),
                this::haltRequested,
                appCommunicationComponent,
                preconsensusEventWriter,
                currentPlatformStatus::get);
        wiring.registerComponents(components);

        final PreConsensusSystemTransactionManager preConsensusSystemTransactionManager =
                new PreConsensusSystemTransactionManagerFactory()
                        .addHandlers(stateManagementComponent.getPreConsensusHandleMethods())
                        .build();

        final PostConsensusSystemTransactionManager postConsensusSystemTransactionManager =
                new PostConsensusSystemTransactionManagerFactory()
                        .addHandlers(stateManagementComponent.getPostConsensusHandleMethods())
                        .build();

        // FUTURE WORK remove this when there are no more ShutdownRequestedTriggers being dispatched
        components.add(new Shutdown());

        final Settings settings = Settings.getInstance();

        final Address address = getSelfAddress();
        final String eventStreamManagerName;
        if (address.getMemo() != null && !address.getMemo().isEmpty()) {
            eventStreamManagerName = address.getMemo();
        } else {
            eventStreamManagerName = String.valueOf(selfId);
        }
        logger.info(STARTUP.getMarker(), "initialize eventStreamManager");

        final EventStreamManager<EventImpl> eventStreamManager = new EventStreamManager<>(
                platformContext,
                threadManager,
                getSelfId(),
                this,
                eventStreamManagerName,
                settings.isEnableEventStreaming(),
                settings.getEventsLogDir(),
                settings.getEventsLogPeriod(),
                settings.getEventStreamQueueCapacity(),
                this::isLastEventBeforeRestart);

        if (loadedSignedState.isNotNull()) {
            diskStateHash = loadedSignedState.get().getState().getHash();
            diskStateRound = loadedSignedState.get().getRound();
            initialMinimumGenerationNonAncient = loadedSignedState
                    .get()
                    .getState()
                    .getPlatformState()
                    .getPlatformData()
                    .getMinimumGenerationNonAncient();
            startedFromGenesis = false;
        } else {
            diskStateHash = null;
            diskStateRound = -1;
            initialMinimumGenerationNonAncient = 0;
            startedFromGenesis = true;
        }

        final LoadedState loadedState = initializeLoadedStateFromSignedState(loadedSignedState, stateConfig);
        final PreConsensusEventHandler preConsensusEventHandler;
        try (loadedState.signedStateFromDisk) {
            final SignedState signedStateFromDisk = loadedState.signedStateFromDisk.getNullable();

            stateHashSignQueue = PlatformConstructor.stateHashSignQueue(
                    threadManager, selfId, stateManagementComponent::newSignedStateFromTransactions, metrics);
            stateHashSignQueue.start();

            final State stateToLoad;
            if (signedStateFromDisk != null) {
                logger.debug(STARTUP.getMarker(), () -> new SavedStateLoadedPayload(
                                signedStateFromDisk.getRound(),
                                signedStateFromDisk.getConsensusTimestamp(),
                                startUpEventFrozenManager.getStartUpEventFrozenEndTime())
                        .toString());

                stateToLoad = loadedState.initialState;

            } else {
                stateToLoad = buildGenesisState(this, initialAddressBook, appVersion, genesisStateBuilder);

                // if we are not starting from a saved state, don't freeze on startup
                startUpEventFrozenManager.setStartUpEventFrozenEndTime(null);
            }

            if (stateToLoad == null) {
                // this should be impossible
                throw new IllegalStateException("stateToLoad is null");
            }

            swirldStateManager = PlatformConstructor.swirldStateManager(
                    platformContext,
                    initialAddressBook,
                    selfId,
                    preConsensusSystemTransactionManager,
                    postConsensusSystemTransactionManager,
                    metrics,
                    PlatformConstructor.settingsProvider(),
                    freezeManager::isFreezeStarted,
                    stateToLoad);

            // SwirldStateManager will get a copy of the state loaded, that copy will become stateCons.
            // The original state will be saved in the SignedStateMgr and will be deleted when it becomes old

            preConsensusEventHandler = components.add(
                    new PreConsensusEventHandler(metrics, threadManager, selfId, swirldStateManager, consensusMetrics));
            consensusRoundHandler = components.add(PlatformConstructor.consensusHandler(
                    platformContext,
                    threadManager,
                    selfId,
                    PlatformConstructor.settingsProvider(),
                    swirldStateManager,
                    new ConsensusHandlingMetrics(metrics, time),
                    eventStreamManager,
                    stateHashSignQueue,
                    preconsensusEventWriter::waitUntilDurable,
                    freezeManager::freezeStarted,
                    stateManagementComponent::roundAppliedToState,
                    appVersion));

            if (signedStateFromDisk != null) {
                consensusRoundHandler.loadDataFromSignedState(signedStateFromDisk, false);
            }

            final AddedEventMetrics addedEventMetrics = new AddedEventMetrics(this.selfId, metrics);
            final PreconsensusEventStreamSequencer sequencer = new PreconsensusEventStreamSequencer();

            final EventObserverDispatcher eventObserverDispatcher = new EventObserverDispatcher(
                    new ShadowGraphEventObserver(shadowGraph),
                    consensusRoundHandler,
                    preConsensusEventHandler,
                    eventMapper,
                    addedEventMetrics,
                    eventIntakeMetrics,
                    (PreConsensusEventObserver) event -> {
                        sequencer.assignStreamSequenceNumber(event);
                        abortAndThrowIfInterrupted(
                                preconsensusEventWriter::writeEvent,
                                event,
                                "Interrupted while attempting to enqueue preconsensus event for writing");
                    },
                    (ConsensusRoundObserver) round -> {
                        abortAndThrowIfInterrupted(
                                preconsensusEventWriter::setMinimumGenerationNonAncient,
                                round.getGenerations().getMinGenerationNonAncient(),
                                "Interrupted while attempting to enqueue change in minimum generation non-ancient");

                        abortAndThrowIfInterrupted(
                                preconsensusEventWriter::requestFlush,
                                "Interrupted while requesting preconsensus event flush");
                    });

            final List<Predicate<EventDescriptor>> isDuplicateChecks = new ArrayList<>();
            isDuplicateChecks.add(d -> shadowGraph.isHashInGraph(d.getHash()));

            eventLinker = buildEventLinker(isDuplicateChecks);

            final IntakeCycleStats intakeCycleStats = new IntakeCycleStats(time, metrics);

            final EventIntake eventIntake = new EventIntake(
                    selfId,
                    eventLinker,
                    consensusRef::get,
                    initialAddressBook,
                    eventObserverDispatcher,
                    intakeCycleStats,
                    shadowGraph);

            final EventCreator eventCreator = buildEventCreator(eventIntake);

            final List<GossipEventValidator> validators = new ArrayList<>();
            // it is very important to discard ancient events, otherwise the deduplication will not work, since it
            // doesn't track ancient events
            validators.add(new AncientValidator(consensusRef::get));
            validators.add(new EventDeduplication(isDuplicateChecks, eventIntakeMetrics));
            validators.add(StaticValidators::isParentDataValid);
            validators.add(new TransactionSizeValidator(settings.getMaxTransactionBytesPerEvent()));
            if (settings.isVerifyEventSigs()) {
                validators.add(new SignatureValidator(initialAddressBook));
            }
            final GossipEventValidators eventValidators = new GossipEventValidators(validators);

            /* validates events received from gossip */
            final EventValidator eventValidator = new EventValidator(eventValidators, eventIntake::addUnlinkedEvent);

            eventTaskDispatcher = new EventTaskDispatcher(
                    time,
                    eventValidator,
                    eventCreator,
                    eventIntake::addUnlinkedEvent,
                    eventIntakeMetrics,
                    intakeCycleStats);

            intakeQueue = components.add(new QueueThreadConfiguration<EventIntakeTask>(threadManager)
                    .setNodeId(selfId)
                    .setComponent(PLATFORM_THREAD_POOL_NAME)
                    .setThreadName("event-intake")
                    // There is a circular dependency between the intake queue and gossip,
                    // which the handler lambda sidesteps (since the lambda is not invoked
                    // until after all things have been constructed).
                    .setHandler(e -> getGossip().getEventIntakeLambda().accept(e))
                    .setCapacity(settings.getEventIntakeQueueSize())
                    .setLogAfterPauseDuration(platformContext
                            .getConfiguration()
                            .getConfigData(ThreadConfig.class)
                            .logStackTracePauseDuration())
                    .setMetricsConfiguration(new QueueThreadMetricsConfiguration(metrics)
                            .enableMaxSizeMetric()
                            .enableBusyTimeMetric())
                    .build());

            transactionSubmitter = new SwirldTransactionSubmitter(
                    currentPlatformStatus::get,
                    PlatformConstructor.settingsProvider(),
                    swirldStateManager::submitTransaction,
                    new TransactionMetrics(metrics));

            gossip = GossipFactory.buildGossip(
                    platformContext,
                    threadManager,
                    time,
                    crypto,
                    notificationEngine,
                    initialAddressBook,
                    selfId,
                    appVersion,
                    shadowGraph,
                    emergencyRecoveryManager,
                    consensusRef,
                    intakeQueue,
                    freezeManager,
                    startUpEventFrozenManager,
                    swirldStateManager,
                    startedFromGenesis,
                    stateManagementComponent,
                    eventTaskDispatcher::dispatchTask,
                    eventObserverDispatcher,
                    eventMapper,
                    eventIntakeMetrics,
                    eventLinker,
                    this::checkPlatformStatus,
                    this::loadReconnectState,
                    this::clearAllPipelines);

            if (signedStateFromDisk != null) {
                loadIntoConsensusAndEventMapper(signedStateFromDisk);
                eventLinker.loadFromSignedState(signedStateFromDisk);
            } else {
                consensusRef.set(new ConsensusImpl(
                        platformContext.getConfiguration().getConfigData(ConsensusConfig.class),
                        consensusMetrics,
                        consensusRoundHandler::addMinGenInfo,
                        getAddressBook()));
            }
        }

        clearAllPipelines = new LoggingClearables(
                RECONNECT.getMarker(),
                List.of(
                        Pair.of(gossip, "gossip"),
                        Pair.of(preConsensusEventHandler, "preConsensusEventHandler"),
                        Pair.of(consensusRoundHandler, "consensusRoundHandler"),
                        Pair.of(swirldStateManager, "swirldStateManager")));

        // To be removed once the GUI component is better integrated with the platform.
        GuiPlatformAccessor.getInstance().setShadowGraph(selfId, shadowGraph);
        GuiPlatformAccessor.getInstance().setStateManagementComponent(selfId, stateManagementComponent);
        GuiPlatformAccessor.getInstance().setConsensusReference(selfId, consensusRef);
    }

    /**
     * Clears all pipelines in preparation for a reconnect. This method is needed to break a circular dependency.
     */
    private void clearAllPipelines() {
        clearAllPipelines.clear();
    }

    /**
     * Get the gossip engine. This method exists to break a circular dependency.
     */
    private Gossip getGossip() {
        return gossip;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public NodeId getSelfId() {
        return selfId;
    }

    /**
     * Stores a new system transaction that will be added to an event in the future.
     *
     * @param systemTransaction the new system transaction to be included in a future event
     * @return {@code true} if successful, {@code false} otherwise
     */
    private boolean createSystemTransaction(
            @NonNull final SystemTransaction systemTransaction, final boolean priority) {
        Objects.requireNonNull(systemTransaction);
        return swirldStateManager.submitTransaction(systemTransaction, priority);
    }

    /**
     * A container for the initial state.
     *
     * @param signedStateFromDisk the initial signed state loaded from disk
     * @param initialState        the initial {@link State} object. This is a fast copy of the state loaded from disk
     */
    private record LoadedState(@NonNull ReservedSignedState signedStateFromDisk, @Nullable State initialState) {}

    /**
     * Update the address book with the current address book read from config.txt. Eventually we will not do this, and
     * only transactions will be capable of modifying the address book.
     *
     * @param signedState the state that was loaded from disk
     * @param addressBook the address book specified in config.txt
     */
    private static void updateLoadedStateAddressBook(final SignedState signedState, final AddressBook addressBook) {
        final State state = signedState.getState();

        // Update the address book with the current address book read from config.txt.
        // Eventually we will not do this, and only transactions will be capable of
        // modifying the address book.
        state.getPlatformState().setAddressBook(addressBook.copy());

        // Invalidate a path down to the new address book
        new MerkleRouteIterator(state, state.getPlatformState().getAddressBook().getRoute())
                .forEachRemaining(MerkleNode::invalidateHash);

        // We should only have to rehash a few nodes, so simpler to use the synchronous algorithm.
        MerkleCryptoFactory.getInstance().digestTreeSync(state);

        // If our hash changes as a result of the new address book then our old signatures may become invalid.
        signedState.pruneInvalidSignatures();
    }

    /**
     * Create the LoadedState from the SignedState loaded from disk, if it is present.
     *
     * @param signedStateFromDisk the SignedState loaded from disk.
     * @param stateConfig         the state configuration
     * @return the LoadedState
     */
    @NonNull
    private LoadedState initializeLoadedStateFromSignedState(
            @NonNull final ReservedSignedState signedStateFromDisk, @NonNull final StateConfig stateConfig) {
        try (signedStateFromDisk) {
            if (signedStateFromDisk.isNotNull()) {
                updateLoadedStateAddressBook(signedStateFromDisk.get(), initialAddressBook);
                final State initialState = loadSavedState(signedStateFromDisk.get(), stateConfig);
                return new LoadedState(
                        signedStateFromDisk.getAndReserve("SwirldsPlatform.initializeLoadedStateFromSignedState()"),
                        initialState);
            }
        } catch (final Exception e) {
            logger.error(EXCEPTION.getMarker(), "Saved state not loaded:", e);
            // if requireStateLoad is on, we exit. if not, we just log it
            if (stateConfig.requireStateLoad()) {
                SystemExitUtils.exitSystem(SystemExitCode.SAVED_STATE_NOT_LOADED);
            }
        }
        return new LoadedState(createNullReservation(), null);
    }

    private State loadSavedState(
            @NonNull final SignedState signedStateFromDisk, @NonNull final StateConfig stateConfig) {
        logger.info(
                STARTUP.getMarker(),
                "Information for state loaded from disk:\n{}\n{}",
                () -> signedStateFromDisk.getState().getPlatformState().getInfoString(),
                () -> new MerkleTreeVisualizer(signedStateFromDisk.getState())
                        .setDepth(stateConfig.debugHashDepth())
                        .render());

        // The previous version of the software that was run. Null if this is the first time running, or if the previous
        // version ran before the concept of application software versioning was introduced.
        final SoftwareVersion previousSoftwareVersion = signedStateFromDisk
                .getState()
                .getPlatformState()
                .getPlatformData()
                .getCreationSoftwareVersion();
        final State initialState = signedStateFromDisk.getState().copy();
        initialState.getPlatformState().getPlatformData().setCreationSoftwareVersion(appVersion);

        final Hash initialHash = initialState.getSwirldState().getHash();
        initialState
                .getSwirldState()
                .init(this, initialState.getSwirldDualState(), InitTrigger.RESTART, previousSoftwareVersion);
        initialState.markAsInitialized();

        final Hash currentHash = initialState.getSwirldState().getHash();

        // If the current hash is null, we must hash the state
        // It's also possible that the application modified the state and hashed itself in init(), if so we need to
        // rehash the state as a whole.
        if (currentHash == null || !Objects.equals(initialHash, currentHash)) {
            initialState.invalidateHash();
            abortAndThrowIfInterrupted(
                    () -> {
                        try {
                            MerkleCryptoFactory.getInstance()
                                    .digestTreeAsync(initialState)
                                    .get();
                        } catch (final ExecutionException e) {
                            throw new RuntimeException(e);
                        }
                    },
                    "interrupted while attempting to hash the state");
        }

        stateManagementComponent.stateToLoad(signedStateFromDisk, SourceOfSignedState.DISK);

        return initialState;
    }

    /**
     * Loads the signed state data into consensus and event mapper
     *
     * @param signedState the state to get the data from
     */
    void loadIntoConsensusAndEventMapper(final SignedState signedState) {
        consensusRef.set(new ConsensusImpl(
                platformContext.getConfiguration().getConfigData(ConsensusConfig.class),
                consensusMetrics,
                consensusRoundHandler::addMinGenInfo,
                getAddressBook(),
                signedState));

        shadowGraph.initFromEvents(
                EventUtils.prepareForShadowGraph(
                        // we need to pass in a copy of the array, otherwise prepareForShadowGraph will rearrange the
                        // events in the signed state which will cause issues for other components that depend on it
                        signedState.getEvents().clone()),
                // we need to provide the minGen from consensus so that expiry matches after a restart/reconnect
                consensusRef.get().getMinRoundGeneration());

        // Data that is needed for the intake system to work
        for (final EventImpl e : signedState.getEvents()) {
            eventMapper.eventAdded(e);
        }

        gossip.loadFromSignedState(signedState);

        logger.info(
                STARTUP.getMarker(),
                "Last known events after restart are {}",
                eventMapper.getMostRecentEventsByEachCreator());
    }

    /**
     * Used to load the state received from the sender.
     *
     * @param signedState the signed state that was received from the sender
     */
    void loadReconnectState(final SignedState signedState) {
        // the state was received, so now we load its data into different objects
        logger.info(LogMarker.STATE_HASH.getMarker(), "RECONNECT: loadReconnectState: reloading state");
        logger.debug(RECONNECT.getMarker(), "`loadReconnectState` : reloading state");
        try {

            reconnectStateLoadedDispatcher.dispatch(
                    signedState.getRound(), signedState.getState().getHash());

            // It's important to call init() before loading the signed state. The loading process makes copies
            // of the state, and we want to be sure that the first state in the chain of copies has been initialized.
            final Hash reconnectHash = signedState.getState().getHash();
            signedState
                    .getSwirldState()
                    .init(
                            this,
                            signedState.getState().getSwirldDualState(),
                            InitTrigger.RECONNECT,
                            signedState
                                    .getState()
                                    .getPlatformState()
                                    .getPlatformData()
                                    .getCreationSoftwareVersion());
            if (!Objects.equals(signedState.getState().getHash(), reconnectHash)) {
                throw new IllegalStateException(
                        "State hash is not permitted to change during a reconnect init() call. Previous hash was "
                                + reconnectHash + ", new hash is "
                                + signedState.getState().getHash());
            }
            signedState.getState().markAsInitialized();

            swirldStateManager.loadFromSignedState(signedState);

            stateManagementComponent.stateToLoad(signedState, SourceOfSignedState.RECONNECT);

            loadIntoConsensusAndEventMapper(signedState);
            // eventLinker is not thread safe, which is not a problem regularly because it is only used by a single
            // thread. after a reconnect, it needs to load the minimum generation from a state on a different thread,
            // so the intake thread is paused before the data is loaded and unpaused after. this ensures that the
            // thread will get the up-to-date data loaded
            new PauseAndLoad(intakeQueue, eventLinker).loadFromSignedState(signedState);

            consensusRoundHandler.loadDataFromSignedState(signedState, true);

            try {
                preconsensusEventWriter.registerDiscontinuity();
                preconsensusEventWriter.setMinimumGenerationNonAncient(signedState
                        .getState()
                        .getPlatformState()
                        .getPlatformData()
                        .getMinimumGenerationNonAncient());
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("interrupted while loading updating PCES after reconnect", e);
            }

            // Notify any listeners that the reconnect has been completed
            notificationEngine.dispatch(
                    ReconnectCompleteListener.class,
                    new ReconnectCompleteNotification(
                            signedState.getRound(),
                            signedState.getConsensusTimestamp(),
                            signedState.getState().getSwirldState()));
        } catch (final RuntimeException e) {
            logger.debug(RECONNECT.getMarker(), "`loadReconnectState` : FAILED, reason: {}", e.getMessage());
            // if the loading fails for whatever reason, we clear all data again in case some of it has been loaded
            clearAllPipelines();
            throw e;
        }

        gossip.resetFallenBehind();
    }

    /**
     * This observer is called when a system freeze is requested. Permanently stops event creation and gossip.
     *
     * @param reason the reason why the system is being frozen.
     */
    private void haltRequested(final String reason) {
        logger.error(EXCEPTION.getMarker(), "System halt requested. Reason: {}", reason);
        freezeManager.freezeEventCreation();
        gossip.stop();
    }

    /**
     * Build the event creator.
     */
    @Nullable
    private EventCreator buildEventCreator(@NonNull final EventIntake eventIntake) {
        Objects.requireNonNull(eventIntake);
        final ChatterConfig chatterConfig = platformContext.getConfiguration().getConfigData(ChatterConfig.class);
        if (chatterConfig.useChatter()) {
            // chatter has a separate event creator in a different thread. having 2 event creators creates the risk
            // of forking, so a NPE is preferable to a fork
            return null;
        } else {
            return new EventCreator(
                    this.appVersion,
                    selfId,
                    PlatformConstructor.platformSigner(crypto.getKeysAndCerts()),
                    consensusRef::get,
                    swirldStateManager.getTransactionPool(),
                    eventIntake::addEvent,
                    eventMapper,
                    eventMapper,
                    swirldStateManager.getTransactionPool(),
                    freezeManager::isFreezeStarted,
                    new EventCreationRules(List.of()));
        }
    }

    /**
     * Build the event linker.
     */
    @NonNull
    private EventLinker buildEventLinker(@NonNull final List<Predicate<EventDescriptor>> isDuplicateChecks) {
        Objects.requireNonNull(isDuplicateChecks);
        final ParentFinder parentFinder = new ParentFinder(shadowGraph::hashgraphEvent);
        final ChatterConfig chatterConfig = platformContext.getConfiguration().getConfigData(ChatterConfig.class);
        if (chatterConfig.useChatter()) {
            final OrphanBufferingLinker orphanBuffer = new OrphanBufferingLinker(
                    platformContext.getConfiguration().getConfigData(ConsensusConfig.class),
                    parentFinder,
                    chatterConfig.futureGenerationLimit());
            metrics.getOrCreate(
                    new FunctionGauge.Config<>("intake", "numOrphans", Integer.class, orphanBuffer::getNumOrphans)
                            .withDescription("the number of events without parents buffered")
                            .withFormat("%d"));

            // when using chatter an event could be an orphan, in this case it will be stored in the orphan set
            // when its parents are found, or become ancient, it will move to the shadowgraph
            // non-orphans are also stored in the shadowgraph
            // to dedupe, we need to check both
            isDuplicateChecks.add(orphanBuffer::isOrphan);

            return orphanBuffer;
        } else {
            return new InOrderLinker(
                    platformContext.getConfiguration().getConfigData(ConsensusConfig.class),
                    parentFinder,
                    eventMapper::getMostRecentEvent);
        }
    }

    /**
     * Build the preconsensus event file manager.
     */
    @NonNull
    private PreconsensusEventFileManager buildPreconsensusEventFileManager() {
        try {
            return new PreconsensusEventFileManager(platformContext, OSTime.getInstance(), selfId);
        } catch (final IOException e) {
            throw new UncheckedIOException("unable load preconsensus files", e);
        }
    }

    /**
     * Build the preconsensus event writer.
     */
    @NonNull
    private PreconsensusEventWriter buildPreconsensusEventWriter(
            @NonNull final PreconsensusEventFileManager fileManager) {

        final PreconsensusEventStreamConfig preconsensusEventStreamConfig =
                platformContext.getConfiguration().getConfigData(PreconsensusEventStreamConfig.class);

        if (!preconsensusEventStreamConfig.enableStorage()) {
            return new NoOpPreconsensusEventWriter();
        }

        final PreconsensusEventWriter syncWriter = new SyncPreconsensusEventWriter(platformContext, fileManager);

        return new AsyncPreconsensusEventWriter(platformContext, threadManager, syncWriter);
    }

    /**
     * Start this platform.
     */
    @Override
    public void start() {
        components.start();

        sendStartupNotifications();

        metrics.start();

        replayPreconsensusEvents();
        configureStartupEventFreeze();
        gossip.start();

        // in case of a single node network, the platform status update will not be triggered by connections, so it
        // needs to be triggered now
        checkPlatformStatus();
    }

    /**
     * Send notifications that can only be sent after components have been started.
     */
    private void sendStartupNotifications() {
        if (!startedFromGenesis) {
            // If we loaded from disk then call the appropriate dispatch. This dispatch
            // must wait until after components have been started.
            diskStateLoadedDispatcher.dispatch(diskStateRound, diskStateHash);

            // Let the app know that a state was loaded.
            notificationEngine.dispatch(
                    StateLoadedFromDiskCompleteListener.class, new StateLoadedFromDiskNotification());
        }
    }

    /**
     * If configured to do so, replay preconsensus events.
     */
    private void replayPreconsensusEvents() {
        final boolean enableReplay = platformContext
                .getConfiguration()
                .getConfigData(PreconsensusEventStreamConfig.class)
                .enableReplay();
        if (!enableReplay) {
            setPlatformStatus(PlatformStatus.OBSERVING);
        } else {
            PreconsensusEventReplayWorkflow.replayPreconsensusEvents(
                    platformContext,
                    threadManager,
                    OSTime.getInstance(),
                    preconsensusEventFileManager,
                    preconsensusEventWriter,
                    eventTaskDispatcher,
                    intakeQueue,
                    consensusRoundHandler,
                    stateHashSignQueue,
                    stateManagementComponent,
                    currentPlatformStatus::get,
                    this::setPlatformStatus,
                    initialMinimumGenerationNonAncient);
        }
    }

    /**
     * We don't want to create events right after starting up. Configure that pause in event creation.
     */
    private void configureStartupEventFreeze() {
        final int freezeSecondsAfterStartup = platformContext
                .getConfiguration()
                .getConfigData(BasicConfig.class)
                .freezeSecondsAfterStartup();
        if (freezeSecondsAfterStartup > 0) {
            final Instant startUpEventFrozenEndTime = Instant.now().plusSeconds(freezeSecondsAfterStartup);
            startUpEventFrozenManager.setStartUpEventFrozenEndTime(startUpEventFrozenEndTime);
            logger.info(STARTUP.getMarker(), "startUpEventFrozenEndTime: {}", () -> startUpEventFrozenEndTime);
        }
    }

    /**
     * Checks the status of the platform and notifies the SwirldMain if there is a change in status
     */
    private void checkPlatformStatus() {
        final int numNodes = initialAddressBook.getSize();

        synchronized (currentPlatformStatus) {
            if (numNodes > 1 && gossip.activeConnectionNumber() == 0) {
                setPlatformStatus(PlatformStatus.DISCONNECTED);
            } else if (gossip.hasFallenBehind()) {
                setPlatformStatus(PlatformStatus.BEHIND);
            } else if (freezeManager.isFreezeStarted()) {
                setPlatformStatus(PlatformStatus.FREEZING);
            } else if (freezeManager.isFreezeComplete()) {
                setPlatformStatus(PlatformStatus.FREEZE_COMPLETE);
            } else {
                setPlatformStatus(PlatformStatus.ACTIVE);
            }
        }
    }

    /**
     * Change the current platform status.
     * @param newStatus the new platform status
     */
    private void setPlatformStatus(@NonNull final PlatformStatus newStatus) {
        final PlatformStatus oldStatus = currentPlatformStatus.getAndSet(newStatus);
        if (oldStatus != newStatus) {
            logger.info(PLATFORM_STATUS.getMarker(), () -> new PlatformStatusPayload(
                            "Platform status changed.", oldStatus == null ? "" : oldStatus.name(), newStatus.name())
                    .toString());

            notificationEngine.dispatch(
                    PlatformStatusChangeListener.class, new PlatformStatusChangeNotification(newStatus));
        }
    }

    /** {@inheritDoc} */
    @Override
    public boolean createTransaction(@NonNull final byte[] transaction) {
        return transactionSubmitter.submitTransaction(new SwirldTransaction(transaction));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public PlatformContext getContext() {
        return platformContext;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public NotificationEngine getNotificationEngine() {
        return notificationEngine;
    }

    /** {@inheritDoc} */
    @Override
    public Signature sign(final byte[] data) {
        return crypto.sign(data);
    }

    /**
     * Get the Address Book
     *
     * @return AddressBook
     */
    @Override
    public AddressBook getAddressBook() {
        return initialAddressBook;
    }

    /**
     * {@inheritDoc}
     */
    @SuppressWarnings("unchecked")
    @Override
    public <T extends SwirldState> AutoCloseableWrapper<T> getLatestImmutableState(@NonNull final String reason) {
        final ReservedSignedState wrapper = stateManagementComponent.getLatestImmutableState(reason);
        return new AutoCloseableWrapper<>(
                wrapper.isNull() ? null : (T) wrapper.get().getState().getSwirldState(), wrapper::close);
    }

    /**
     * {@inheritDoc}
     */
    @SuppressWarnings("unchecked")
    @Override
    public <T extends SwirldState> AutoCloseableWrapper<T> getLatestSignedState(@NonNull final String reason) {
        final ReservedSignedState wrapper = stateManagementComponent.getLatestSignedState(reason);
        return new AutoCloseableWrapper<>(
                wrapper.isNull() ? null : (T) wrapper.get().getState().getSwirldState(), wrapper::close);
    }

    /**
     * check whether the given event is the last event in its round, and the platform enters freeze period
     *
     * @param event a consensus event
     * @return whether this event is the last event to be added before restart
     */
    private boolean isLastEventBeforeRestart(final EventImpl event) {
        return event.isLastInRoundReceived() && swirldStateManager.isInFreezePeriod(event.getConsensusTimestamp());
    }
}
