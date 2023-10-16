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

import static com.swirlds.common.system.InitTrigger.GENESIS;
import static com.swirlds.common.system.InitTrigger.RESTART;
import static com.swirlds.common.system.SoftwareVersion.NO_VERSION;
import static com.swirlds.common.system.UptimeData.NO_ROUND;
import static com.swirlds.common.threading.interrupt.Uninterruptable.abortAndThrowIfInterrupted;
import static com.swirlds.common.threading.manager.AdHocThreadManager.getStaticThreadManager;
import static com.swirlds.logging.LogMarker.EXCEPTION;
import static com.swirlds.logging.LogMarker.RECONNECT;
import static com.swirlds.logging.LogMarker.STARTUP;
import static com.swirlds.platform.event.creation.EventCreationManagerFactory.buildEventCreationManager;
import static com.swirlds.platform.state.address.AddressBookMetrics.registerAddressBookMetrics;
import static com.swirlds.platform.state.iss.ConsensusHashManager.DO_NOT_IGNORE_ROUNDS;

import com.swirlds.base.state.Startable;
import com.swirlds.base.time.Time;
import com.swirlds.base.utility.Pair;
import com.swirlds.common.config.BasicConfig;
import com.swirlds.common.config.ConsensusConfig;
import com.swirlds.common.config.EventConfig;
import com.swirlds.common.config.StateConfig;
import com.swirlds.common.config.TransactionConfig;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.crypto.Signature;
import com.swirlds.common.io.utility.RecycleBin;
import com.swirlds.common.merkle.crypto.MerkleCryptoFactory;
import com.swirlds.common.metrics.FunctionGauge;
import com.swirlds.common.metrics.Metrics;
import com.swirlds.common.metrics.extensions.PhaseTimer;
import com.swirlds.common.metrics.extensions.PhaseTimerBuilder;
import com.swirlds.common.notification.NotificationEngine;
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
import com.swirlds.common.system.address.AddressBookUtils;
import com.swirlds.common.system.events.EventDescriptor;
import com.swirlds.common.system.status.PlatformStatus;
import com.swirlds.common.system.status.PlatformStatusManager;
import com.swirlds.common.system.status.actions.DoneReplayingEventsAction;
import com.swirlds.common.system.status.actions.ReconnectCompleteAction;
import com.swirlds.common.system.status.actions.StartedReplayingEventsAction;
import com.swirlds.common.system.transaction.internal.StateSignatureTransaction;
import com.swirlds.common.system.transaction.internal.SwirldTransaction;
import com.swirlds.common.system.transaction.internal.SystemTransaction;
import com.swirlds.common.threading.framework.QueueThread;
import com.swirlds.common.threading.framework.config.QueueThreadConfiguration;
import com.swirlds.common.threading.framework.config.QueueThreadMetricsConfiguration;
import com.swirlds.common.threading.manager.ThreadManager;
import com.swirlds.common.utility.AutoCloseableWrapper;
import com.swirlds.common.utility.Clearable;
import com.swirlds.common.utility.LoggingClearables;
import com.swirlds.logging.LogMarker;
import com.swirlds.platform.components.EventIntake;
import com.swirlds.platform.components.appcomm.AppCommunicationComponent;
import com.swirlds.platform.components.state.StateManagementComponent;
import com.swirlds.platform.components.transaction.system.ConsensusSystemTransactionManager;
import com.swirlds.platform.components.transaction.system.PreconsensusSystemTransactionManager;
import com.swirlds.platform.components.wiring.ManualWiring;
import com.swirlds.platform.config.ThreadConfig;
import com.swirlds.platform.crypto.CryptoStatic;
import com.swirlds.platform.crypto.PlatformSigner;
import com.swirlds.platform.dispatch.DispatchBuilder;
import com.swirlds.platform.dispatch.DispatchConfiguration;
import com.swirlds.platform.dispatch.triggers.flow.DiskStateLoadedTrigger;
import com.swirlds.platform.dispatch.triggers.flow.ReconnectStateLoadedTrigger;
import com.swirlds.platform.event.EventCounter;
import com.swirlds.platform.event.EventUtils;
import com.swirlds.platform.event.GossipEvent;
import com.swirlds.platform.event.creation.AsyncEventCreationManager;
import com.swirlds.platform.event.linking.EventLinker;
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
import com.swirlds.platform.eventhandling.TransactionPool;
import com.swirlds.platform.gossip.DefaultIntakeEventCounter;
import com.swirlds.platform.gossip.Gossip;
import com.swirlds.platform.gossip.GossipFactory;
import com.swirlds.platform.gossip.IntakeEventCounter;
import com.swirlds.platform.gossip.NoOpIntakeEventCounter;
import com.swirlds.platform.gossip.chatter.config.ChatterConfig;
import com.swirlds.platform.gossip.shadowgraph.ShadowGraph;
import com.swirlds.platform.gossip.shadowgraph.ShadowGraphEventObserver;
import com.swirlds.platform.gossip.sync.config.SyncConfig;
import com.swirlds.platform.gui.GuiPlatformAccessor;
import com.swirlds.platform.intake.EventIntakePhase;
import com.swirlds.platform.internal.EventImpl;
import com.swirlds.platform.metrics.AddedEventMetrics;
import com.swirlds.platform.metrics.ConsensusHandlingMetrics;
import com.swirlds.platform.metrics.ConsensusMetrics;
import com.swirlds.platform.metrics.ConsensusMetricsImpl;
import com.swirlds.platform.metrics.EventIntakeMetrics;
import com.swirlds.platform.metrics.IssMetrics;
import com.swirlds.platform.metrics.RuntimeMetrics;
import com.swirlds.platform.metrics.SwirldStateMetrics;
import com.swirlds.platform.metrics.SyncMetrics;
import com.swirlds.platform.metrics.TransactionMetrics;
import com.swirlds.platform.observers.ConsensusRoundObserver;
import com.swirlds.platform.observers.EventObserverDispatcher;
import com.swirlds.platform.observers.PreConsensusEventObserver;
import com.swirlds.platform.recovery.EmergencyRecoveryManager;
import com.swirlds.platform.state.State;
import com.swirlds.platform.state.SwirldStateManager;
import com.swirlds.platform.state.iss.ConsensusHashManager;
import com.swirlds.platform.state.iss.IssHandler;
import com.swirlds.platform.state.signed.ReservedSignedState;
import com.swirlds.platform.state.signed.SignedState;
import com.swirlds.platform.state.signed.SignedStateManager;
import com.swirlds.platform.state.signed.SourceOfSignedState;
import com.swirlds.platform.state.signed.StartupStateUtils;
import com.swirlds.platform.state.signed.StateToDiskReason;
import com.swirlds.platform.stats.StatConstructor;
import com.swirlds.platform.system.Shutdown;
import com.swirlds.platform.threading.PauseAndLoad;
import com.swirlds.platform.util.PlatformComponents;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class SwirldsPlatform implements Platform {

    public static final String PLATFORM_THREAD_POOL_NAME = "platform-core";
    /** use this for all logging, as controlled by the optional data/log4j2.xml file */
    private static final Logger logger = LogManager.getLogger(SwirldsPlatform.class);
    /**
     * the ID of the member running this. Since a node can be a main node or a mirror node, the ID is not a primitive
     * value
     */
    private final NodeId selfId;
    /**
     * The shadow graph manager. This wraps a shadow graph, which is an Event graph that adds child pointers to the
     * Hashgraph Event graph. Used for gossiping.
     */
    private final ShadowGraph shadowGraph;

    /**
     * the object used to calculate consensus. it is volatile because the whole object is replaced when reading a state
     * from disk or getting it through reconnect
     */
    private final AtomicReference<Consensus> consensusRef = new AtomicReference<>();
    /** the current nodes in the network and their information */
    private final AddressBook currentAddressBook;

    private final Metrics metrics;

    private final ConsensusMetrics consensusMetrics;

    /** the object that contains all key pairs and CSPRNG state for this member */
    private final Crypto crypto;
    /**
     * If a state was loaded from disk, this is the minimum generation non-ancient for that round. If starting from a
     * genesis state, this is 0.
     */
    private final long initialMinimumGenerationNonAncient;

    private final StateManagementComponent stateManagementComponent;
    private final QueueThread<GossipEvent> intakeQueue;
    private final QueueThread<ReservedSignedState> stateHashSignQueue;
    private final EventLinker eventLinker;

    /**
     * Validates events and passes valid events further down the pipeline.
     */
    private final EventValidator eventValidator;
    /** Contains all validators for events */
    private final GossipEventValidators eventValidators;

    /** Stores and processes consensus events including sending them to {@link SwirldStateManager} for handling */
    private final ConsensusRoundHandler consensusRoundHandler;

    private final TransactionPool transactionPool;
    /** Handles all interaction with {@link SwirldState} */
    private final SwirldStateManager swirldStateManager;
    /** Checks the validity of transactions and submits valid ones to the transaction pool */
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
     * Manages the status of the platform.
     */
    private final PlatformStatusManager platformStatusManager;

    /**
     * Responsible for transmitting and receiving events from the network.
     */
    private final Gossip gossip;

    /**
     * Allows files to be deleted, and potentially recovered later for debugging.
     */
    private final RecycleBin recycleBin;

    /**
     * Creates new events.
     */
    private final AsyncEventCreationManager eventCreator;

    /**
     * The round of the most recent reconnect state received, or {@link com.swirlds.common.system.UptimeData#NO_ROUND}
     * if no reconnect state has been received since startup.
     */
    private final AtomicLong latestReconnectRound = new AtomicLong(NO_ROUND);

    /** Manages emergency recovery */
    private final EmergencyRecoveryManager emergencyRecoveryManager;

    /**
     * the browser gives the Platform what app to run. There can be multiple Platforms on one computer.
     *
     * @param platformContext          the context for this platform
     * @param crypto                   an object holding all the public/private key pairs and the CSPRNG state for this
     *                                 member
     * @param recycleBin               used to delete files that may be useful for later debugging
     * @param id                       the ID for this node
     * @param mainClassName            the name of the app class inheriting from SwirldMain
     * @param swirldName               the name of the swirld being run
     * @param appVersion               the current version of the running application
     * @param softwareUpgrade          true if a software upgrade occurred since the last run.
     * @param initialState             the initial state of the platform
     * @param emergencyRecoveryManager used in emergency recovery.
     */
    SwirldsPlatform(
            @NonNull final PlatformContext platformContext,
            @NonNull final Crypto crypto,
            @NonNull final RecycleBin recycleBin,
            @NonNull final NodeId id,
            @NonNull final String mainClassName,
            @NonNull final String swirldName,
            @NonNull final SoftwareVersion appVersion,
            final boolean softwareUpgrade,
            @NonNull final SignedState initialState,
            @NonNull final EmergencyRecoveryManager emergencyRecoveryManager) {

        this.platformContext = Objects.requireNonNull(platformContext, "platformContext");
        this.emergencyRecoveryManager = Objects.requireNonNull(emergencyRecoveryManager, "emergencyRecoveryManager");
        final Time time = Time.getCurrent();

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
        this.currentAddressBook = initialState.getAddressBook();

        platformStatusManager =
                components.add(new PlatformStatusManager(platformContext, time, threadManager, notificationEngine));

        this.metrics = platformContext.getMetrics();

        metrics.getOrCreate(StatConstructor.createEnumStat(
                "PlatformStatus",
                Metrics.PLATFORM_CATEGORY,
                PlatformStatus.values(),
                platformStatusManager::getCurrentStatus));

        registerAddressBookMetrics(metrics, currentAddressBook, selfId);

        this.recycleBin = components.add(Objects.requireNonNull(recycleBin));

        this.consensusMetrics = new ConsensusMetricsImpl(this.selfId, metrics);

        final EventIntakeMetrics eventIntakeMetrics = new EventIntakeMetrics(metrics, selfId);
        final SyncMetrics syncMetrics = new SyncMetrics(metrics);
        RuntimeMetrics.setup(metrics);

        this.shadowGraph = new ShadowGraph(syncMetrics, currentAddressBook.getSize());

        this.crypto = crypto;

        EventCounter.registerEventCounterMetrics(metrics);

        // Manually wire components for now.
        final ManualWiring wiring = new ManualWiring(platformContext, threadManager, dispatchBuilder, getAddressBook());
        metrics.addUpdater(wiring::updateMetrics);
        final AppCommunicationComponent appCommunicationComponent =
                wiring.wireAppCommunicationComponent(notificationEngine);

        final Hash epochHash;
        if (emergencyRecoveryManager.getEmergencyRecoveryFile() != null) {
            epochHash = emergencyRecoveryManager.getEmergencyRecoveryFile().hash();
        } else {
            epochHash =
                    initialState.getState().getPlatformState().getPlatformData().getEpochHash();
        }

        StartupStateUtils.doRecoveryCleanup(
                platformContext,
                recycleBin,
                selfId,
                swirldName,
                actualMainClassName,
                epochHash,
                initialState.getRound());

        preconsensusEventFileManager = buildPreconsensusEventFileManager(initialState.getRound(), softwareUpgrade);

        preconsensusEventWriter = components.add(buildPreconsensusEventWriter(preconsensusEventFileManager));

        final long roundToIgnore = stateConfig.validateInitialState() ? DO_NOT_IGNORE_ROUNDS : initialState.getRound();
        final ConsensusHashManager consensusHashManager = components.add(new ConsensusHashManager(
                platformContext,
                Time.getCurrent(),
                dispatchBuilder,
                currentAddressBook,
                epochHash,
                appVersion,
                roundToIgnore));

        components.add(new IssHandler(
                Time.getCurrent(),
                dispatchBuilder,
                stateConfig,
                selfId,
                platformStatusManager,
                this::haltRequested,
                wiring::handleFatalError,
                appCommunicationComponent));

        components.add(new IssMetrics(platformContext.getMetrics(), currentAddressBook));

        stateManagementComponent = wiring.wireStateManagementComponent(
                new PlatformSigner(crypto.getKeysAndCerts()),
                actualMainClassName,
                selfId,
                swirldName,
                txn -> this.createSystemTransaction(txn, true),
                this::haltRequested,
                appCommunicationComponent,
                preconsensusEventWriter,
                platformStatusManager::getCurrentStatus,
                platformStatusManager::submitStatusAction);
        wiring.registerComponents(components);

        final SignedStateManager signedStateManager = stateManagementComponent.getSignedStateManager();

        final PreconsensusSystemTransactionManager preconsensusSystemTransactionManager =
                new PreconsensusSystemTransactionManager();
        preconsensusSystemTransactionManager.addHandler(
                StateSignatureTransaction.class, signedStateManager::handlePreconsensusSignatureTransaction);

        final ConsensusSystemTransactionManager consensusSystemTransactionManager =
                new ConsensusSystemTransactionManager();
        consensusSystemTransactionManager.addHandler(
                StateSignatureTransaction.class,
                (ignored, nodeId, txn, v) ->
                        consensusHashManager.handlePostconsensusSignatureTransaction(nodeId, txn, v));
        consensusSystemTransactionManager.addHandler(
                StateSignatureTransaction.class,
                (ignored, nodeId, txn, v) -> signedStateManager.handlePostconsensusSignatureTransaction(nodeId, txn));

        // FUTURE WORK remove this when there are no more ShutdownRequestedTriggers being dispatched
        components.add(new Shutdown());

        final EventConfig eventConfig = platformContext.getConfiguration().getConfigData(EventConfig.class);

        final Address address = getSelfAddress();
        final String eventStreamManagerName;
        if (!address.getMemo().isEmpty()) {
            eventStreamManagerName = address.getMemo();
        } else {
            eventStreamManagerName = String.valueOf(selfId);
        }

        final EventStreamManager<EventImpl> eventStreamManager = new EventStreamManager<>(
                platformContext,
                threadManager,
                getSelfId(),
                this,
                eventStreamManagerName,
                eventConfig.enableEventStreaming(),
                eventConfig.eventsLogDir(),
                eventConfig.eventsLogPeriod(),
                eventConfig.eventStreamQueueCapacity(),
                this::isLastEventBeforeRestart);

        initializeState(initialState);

        final TransactionConfig transactionConfig =
                platformContext.getConfiguration().getConfigData(TransactionConfig.class);

        transactionPool = new TransactionPool(platformContext);

        // This object makes a copy of the state. After this point, initialState becomes immutable.
        swirldStateManager = new SwirldStateManager(
                platformContext,
                currentAddressBook,
                selfId,
                preconsensusSystemTransactionManager,
                consensusSystemTransactionManager,
                new SwirldStateMetrics(platformContext.getMetrics()),
                platformStatusManager,
                initialState.getState(),
                appVersion);

        stateHashSignQueue = components.add(new QueueThreadConfiguration<ReservedSignedState>(threadManager)
                .setNodeId(selfId)
                .setComponent(PLATFORM_THREAD_POOL_NAME)
                .setThreadName("state-hash-sign")
                .setHandler(stateManagementComponent::newSignedStateFromTransactions)
                .setCapacity(1)
                .setMetricsConfiguration(new QueueThreadMetricsConfiguration(metrics).enableBusyTimeMetric())
                .build());

        final ThreadConfig threadConfig = platformContext.getConfiguration().getConfigData(ThreadConfig.class);
        final PreConsensusEventHandler preConsensusEventHandler = components.add(new PreConsensusEventHandler(
                metrics, threadManager, selfId, swirldStateManager, consensusMetrics, threadConfig));
        consensusRoundHandler = components.add(new ConsensusRoundHandler(
                platformContext,
                threadManager,
                selfId,
                swirldStateManager,
                new ConsensusHandlingMetrics(metrics, time),
                eventStreamManager,
                stateHashSignQueue,
                preconsensusEventWriter::waitUntilDurable,
                platformStatusManager,
                consensusHashManager::roundCompleted,
                appVersion));

        final AddedEventMetrics addedEventMetrics = new AddedEventMetrics(this.selfId, metrics);
        final PreconsensusEventStreamSequencer sequencer = new PreconsensusEventStreamSequencer();

        final EventObserverDispatcher eventObserverDispatcher = new EventObserverDispatcher(
                new ShadowGraphEventObserver(shadowGraph),
                consensusRoundHandler,
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

        final SyncConfig syncConfig = platformContext.getConfiguration().getConfigData(SyncConfig.class);
        final IntakeEventCounter intakeEventCounter;
        if (syncConfig.waitForEventsInIntake()) {
            intakeEventCounter = new DefaultIntakeEventCounter(currentAddressBook);
        } else {
            intakeEventCounter = new NoOpIntakeEventCounter();
        }

        eventLinker = buildEventLinker(isDuplicateChecks, intakeEventCounter);

        final PhaseTimer<EventIntakePhase> eventIntakePhaseTimer = new PhaseTimerBuilder<>(
                        platformContext, time, "platform", EventIntakePhase.class)
                .setInitialPhase(EventIntakePhase.IDLE)
                .enableFractionalMetrics()
                .build();

        final EventIntake eventIntake = new EventIntake(
                platformContext,
                threadManager,
                time,
                selfId,
                eventLinker,
                consensusRef::get,
                currentAddressBook,
                eventObserverDispatcher,
                eventIntakePhaseTimer,
                shadowGraph,
                preConsensusEventHandler::preconsensusEvent,
                intakeEventCounter);

        final BasicConfig basicConfig = platformContext.getConfiguration().getConfigData(BasicConfig.class);

        final List<GossipEventValidator> validators = new ArrayList<>();
        // it is very important to discard ancient events, otherwise the deduplication will not work, since it
        // doesn't track ancient events
        validators.add(new AncientValidator(consensusRef::get));
        validators.add(new EventDeduplication(isDuplicateChecks, eventIntakeMetrics));
        validators.add(StaticValidators.buildParentValidator(currentAddressBook.getSize()));
        validators.add(new TransactionSizeValidator(transactionConfig.maxTransactionBytesPerEvent()));
        // some events in the PCES might have been created by nodes that are no longer in the current
        // address book but are in the previous one, so we need both for signature validation
        if (basicConfig.verifyEventSigs()) {
            validators.add(new SignatureValidator(
                    initialState.getState().getPlatformState().getPreviousAddressBook(),
                    currentAddressBook,
                    appVersion,
                    CryptoStatic::verifySignature,
                    time));
        }

        eventValidators = new GossipEventValidators(validators);
        eventValidator = new EventValidator(
                eventValidators, eventIntake::addUnlinkedEvent, eventIntakePhaseTimer, intakeEventCounter);

        intakeQueue = components.add(new QueueThreadConfiguration<GossipEvent>(threadManager)
                .setNodeId(selfId)
                .setComponent(PLATFORM_THREAD_POOL_NAME)
                .setThreadName("event-intake")
                .setHandler(eventValidator::validateEvent)
                .setCapacity(eventConfig.eventIntakeQueueSize())
                .setLogAfterPauseDuration(threadConfig.logStackTracePauseDuration())
                .setMetricsConfiguration(new QueueThreadMetricsConfiguration(metrics).enableMaxSizeMetric())
                .build());

        eventCreator = buildEventCreationManager(
                platformContext,
                threadManager,
                time,
                this,
                currentAddressBook,
                selfId,
                appVersion,
                transactionPool,
                intakeQueue,
                eventObserverDispatcher,
                platformStatusManager::getCurrentStatus,
                latestReconnectRound::get,
                stateManagementComponent::getLatestSavedStateRound);

        transactionSubmitter = new SwirldTransactionSubmitter(
                platformStatusManager::getCurrentStatus,
                transactionConfig,
                transaction -> transactionPool.submitTransaction(transaction, false),
                new TransactionMetrics(metrics));

        final boolean startedFromGenesis = initialState.isGenesisState();

        gossip = GossipFactory.buildGossip(
                platformContext,
                threadManager,
                time,
                crypto,
                notificationEngine,
                currentAddressBook,
                selfId,
                appVersion,
                epochHash,
                shadowGraph,
                emergencyRecoveryManager,
                consensusRef,
                intakeQueue,
                swirldStateManager,
                stateManagementComponent,
                eventValidator,
                eventObserverDispatcher,
                syncMetrics,
                eventLinker,
                platformStatusManager,
                this::loadReconnectState,
                this::clearAllPipelines,
                intakeEventCounter);

        consensusRef.set(new ConsensusImpl(
                platformContext.getConfiguration().getConfigData(ConsensusConfig.class),
                consensusMetrics,
                getAddressBook()));

        if (startedFromGenesis) {
            initialMinimumGenerationNonAncient = 0;
        } else {
            initialMinimumGenerationNonAncient =
                    initialState.getState().getPlatformState().getPlatformData().getMinimumGenerationNonAncient();

            stateManagementComponent.stateToLoad(initialState, SourceOfSignedState.DISK);
            consensusRoundHandler.loadDataFromSignedState(initialState, false);

            loadStateIntoConsensus(initialState);
            loadStateIntoEventCreator(initialState);
            eventLinker.loadFromSignedState(initialState);

            // We don't want to invoke these callbacks until after we are starting up.
            final long round = initialState.getRound();
            final Hash hash = initialState.getState().getHash();
            components.add((Startable) () -> {
                // If we loaded from disk then call the appropriate dispatch.
                // It is important that this is sent after the ConsensusHashManager
                // is initialized.
                diskStateLoadedDispatcher.dispatch(round, hash);

                // Let the app know that a state was loaded.
                notificationEngine.dispatch(
                        StateLoadedFromDiskCompleteListener.class, new StateLoadedFromDiskNotification());
            });
        }

        final Clearable pauseEventCreation = eventCreator::pauseEventCreation;

        clearAllPipelines = new LoggingClearables(
                RECONNECT.getMarker(),
                List.of(
                        Pair.of(pauseEventCreation, "eventCreator"),
                        Pair.of(gossip, "gossip"),
                        Pair.of(preConsensusEventHandler, "preConsensusEventHandler"),
                        Pair.of(consensusRoundHandler, "consensusRoundHandler"),
                        Pair.of(transactionPool, "transactionPool")));

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
        return transactionPool.submitTransaction(systemTransaction, priority);
    }

    /**
     * Initialize the state.
     *
     * @param signedState the state to initialize
     */
    private void initializeState(@NonNull final SignedState signedState) {

        final SoftwareVersion previousSoftwareVersion;
        final InitTrigger trigger;

        if (signedState.isGenesisState()) {
            previousSoftwareVersion = NO_VERSION;
            trigger = GENESIS;
        } else {
            previousSoftwareVersion =
                    signedState.getState().getPlatformState().getPlatformData().getCreationSoftwareVersion();
            trigger = RESTART;
        }

        final State initialState = signedState.getState();

        // Although the state from disk / genesis state is initially hashed, we are actually dealing with a copy
        // of that state here. That copy should have caused the hash to be cleared.
        if (initialState.getHash() != null) {
            throw new IllegalStateException("Expected initial state to be unhashed");
        }
        if (initialState.getSwirldState().getHash() != null) {
            throw new IllegalStateException("Expected initial swirld state to be unhashed");
        }

        initialState.getSwirldState().init(this, initialState.getSwirldDualState(), trigger, previousSoftwareVersion);

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

        // If our hash changes as a result of the new address book then our old signatures may become invalid.
        signedState.pruneInvalidSignatures();

        final StateConfig stateConfig = platformContext.getConfiguration().getConfigData(StateConfig.class);
        logger.info(
                STARTUP.getMarker(),
                """
                        The platform is using the following initial state:
                        {}""",
                signedState.getState().getInfoString(stateConfig.debugHashDepth()));
    }

    /**
     * Load the signed state (either at reboot or reconnect) into the event creator.
     *
     * @param signedState the signed state to load from
     */
    private void loadStateIntoEventCreator(@NonNull final SignedState signedState) {
        Objects.requireNonNull(signedState);

        try {
            eventCreator.setMinimumGenerationNonAncient(
                    signedState.getState().getPlatformState().getPlatformData().getMinimumGenerationNonAncient());

            // newer states will not have events, so we need to check for null
            if (signedState.getState().getPlatformState().getPlatformData().getEvents() == null) {
                return;
            }

            // The event creator may not be started yet. To avoid filling up queues, only register
            // the latest event from each creator. These are the only ones the event creator cares about.

            final Map<NodeId, EventImpl> latestEvents = new HashMap<>();

            for (final EventImpl event :
                    signedState.getState().getPlatformState().getPlatformData().getEvents()) {
                latestEvents.put(event.getCreatorId(), event);
            }

            for (final EventImpl event : latestEvents.values()) {
                eventCreator.registerEvent(event);
            }

        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("interrupted while loading state into event creator", e);
        }
    }

    /**
     * Loads the signed state data into consensus
     *
     * @param signedState the state to get the data from
     */
    private void loadStateIntoConsensus(@NonNull final SignedState signedState) {
        Objects.requireNonNull(signedState);

        consensusRef.get().loadFromSignedState(signedState);

        // old states will have events in them that need to be loaded, newer states will not
        if (signedState.getEvents() != null) {
            shadowGraph.initFromEvents(
                    EventUtils.prepareForShadowGraph(
                            // we need to pass in a copy of the array, otherwise prepareForShadowGraph will rearrange
                            // the events in the signed state which will cause issues for other components that depend
                            // on it
                            signedState.getEvents().clone()),
                    // we need to provide the minGen from consensus so that expiry matches after a restart/reconnect
                    consensusRef.get().getMinRoundGeneration());
        } else {
            shadowGraph.startFromGeneration(consensusRef.get().getMinGenerationNonAncient());
        }

        gossip.loadFromSignedState(signedState);
    }

    /**
     * Used to load the state received from the sender.
     *
     * @param signedState the signed state that was received from the sender
     */
    private void loadReconnectState(final SignedState signedState) {
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

            // Before attempting to load the state, verify that the platform AB matches the state AB.
            AddressBookUtils.verifyReconnectAddressBooks(getAddressBook(), signedState.getAddressBook());

            swirldStateManager.loadFromSignedState(signedState);

            latestReconnectRound.set(signedState.getRound());
            stateManagementComponent.stateToLoad(signedState, SourceOfSignedState.RECONNECT);

            loadStateIntoConsensus(signedState);
            loadStateIntoEventCreator(signedState);
            // eventLinker is not thread safe, which is not a problem regularly because it is only used by a single
            // thread. after a reconnect, it needs to load the minimum generation from a state on a different thread,
            // so the intake thread is paused before the data is loaded and unpaused after. this ensures that the
            // thread will get the up-to-date data loaded
            new PauseAndLoad(intakeQueue, eventLinker).loadFromSignedState(signedState);

            // we need to use the address books from state for validating events, because they might be different
            // from the ones we had before the reconnect
            intakeQueue.pause();
            try {
                eventValidators.replaceValidator(
                        SignatureValidator.VALIDATOR_NAME,
                        new SignatureValidator(
                                signedState.getState().getPlatformState().getPreviousAddressBook(),
                                signedState.getState().getPlatformState().getAddressBook(),
                                appVersion,
                                CryptoStatic::verifySignature,
                                Time.getCurrent()));
            } finally {
                intakeQueue.resume();
            }

            consensusRoundHandler.loadDataFromSignedState(signedState, true);

            try {
                preconsensusEventWriter.registerDiscontinuity(signedState.getRound());
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
        eventCreator.resumeEventCreation();
        platformStatusManager.submitStatusAction(new ReconnectCompleteAction(signedState.getRound()));
    }

    /**
     * This observer is called when a system freeze is requested. Permanently stops event creation and gossip.
     *
     * @param reason the reason why the system is being frozen.
     */
    private void haltRequested(final String reason) {
        logger.error(EXCEPTION.getMarker(), "System halt requested. Reason: {}", reason);
        gossip.stop();
    }

    /**
     * Build the event linker.
     */
    @NonNull
    private EventLinker buildEventLinker(
            @NonNull final List<Predicate<EventDescriptor>> isDuplicateChecks,
            @NonNull final IntakeEventCounter intakeEventCounter) {

        Objects.requireNonNull(isDuplicateChecks);
        Objects.requireNonNull(intakeEventCounter);

        final ParentFinder parentFinder = new ParentFinder(shadowGraph::hashgraphEvent);
        final ChatterConfig chatterConfig = platformContext.getConfiguration().getConfigData(ChatterConfig.class);

        final OrphanBufferingLinker orphanBuffer = new OrphanBufferingLinker(
                platformContext.getConfiguration().getConfigData(ConsensusConfig.class),
                parentFinder,
                chatterConfig.futureGenerationLimit(),
                intakeEventCounter);
        metrics.getOrCreate(
                new FunctionGauge.Config<>("intake", "numOrphans", Integer.class, orphanBuffer::getNumOrphans)
                        .withDescription("the number of events without parents buffered")
                        .withFormat("%d"));

        isDuplicateChecks.add(orphanBuffer::isOrphan);

        return orphanBuffer;
    }

    /**
     * Build the preconsensus event file manager.
     *
     * @param startingRound   the round number of the initial state being loaded into the system
     * @param softwareUpgrade whether or not this node is starting up after a software upgrade
     */
    @NonNull
    private PreconsensusEventFileManager buildPreconsensusEventFileManager(
            final long startingRound, final boolean softwareUpgrade) {
        try {
            clearPCESOnSoftwareUpgradeIfConfigured(softwareUpgrade);
            return new PreconsensusEventFileManager(
                    platformContext, Time.getCurrent(), recycleBin, selfId, startingRound);
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
        logger.info(STARTUP.getMarker(), "Starting platform {}", selfId);

        components.start();

        metrics.start();

        // The event creator is intentionally started before replaying the preconsensus event stream.
        // This prevents the event creator's intake queue from filling up and blocking. Note that
        // this component won't actually create events until the platform has the appropriate status.
        eventCreator.start();

        replayPreconsensusEvents();
        gossip.start();
    }

    /**
     * Performs a PCES recovery:
     * <ul>
     *     <li>Starts all components for handling events</li>
     *     <li>Does not start gossip</li>
     *     <li>Replays events from PCES, reaches consensus on them and handles them</li>
     *     <li>Saves the last state produces by this replay to disk</li>
     * </ul>
     */
    public void performPcesRecovery() {
        components.start();
        eventCreator.start();
        replayPreconsensusEvents();
        stateManagementComponent.dumpLatestImmutableState(StateToDiskReason.PCES_RECOVERY_COMPLETE, true);
    }

    /**
     * If configured to do so, replay preconsensus events.
     */
    private void replayPreconsensusEvents() {
        platformStatusManager.submitStatusAction(new StartedReplayingEventsAction());

        final boolean enableReplay = platformContext
                .getConfiguration()
                .getConfigData(PreconsensusEventStreamConfig.class)
                .enableReplay();
        final boolean emergencyRecoveryNeeded = emergencyRecoveryManager.isEmergencyStateRequired();

        // if we need to do an emergency recovery, replaying the PCES could cause issues if the
        // minimum generation non-ancient is reversed to a smaller value, so we skip it
        if (enableReplay && !emergencyRecoveryNeeded) {
            PreconsensusEventReplayWorkflow.replayPreconsensusEvents(
                    platformContext,
                    threadManager,
                    Time.getCurrent(),
                    preconsensusEventFileManager,
                    preconsensusEventWriter,
                    eventValidator,
                    intakeQueue,
                    consensusRoundHandler,
                    stateHashSignQueue,
                    stateManagementComponent,
                    initialMinimumGenerationNonAncient);
        }

        platformStatusManager.submitStatusAction(
                new DoneReplayingEventsAction(Time.getCurrent().now()));
    }

    /**
     * {@inheritDoc}
     */
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

    /**
     * {@inheritDoc}
     */
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
        return currentAddressBook;
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

    /**
     * Clears the preconsensus event stream if a software upgrade has occurred and the configuration specifies that the
     * stream should be cleared on software upgrade.
     *
     * @param softwareUpgrade true if a software upgrade has occurred
     * @throws UncheckedIOException if the required changes on software upgrade cannot be performed
     */
    private void clearPCESOnSoftwareUpgradeIfConfigured(final boolean softwareUpgrade) {
        final boolean clearOnSoftwareUpgrade = platformContext
                .getConfiguration()
                .getConfigData(PreconsensusEventStreamConfig.class)
                .clearOnSoftwareUpgrade();

        if (softwareUpgrade && clearOnSoftwareUpgrade) {
            logger.info(STARTUP.getMarker(), "Clearing the preconsensus event stream on software upgrade.");
            PreconsensusEventFileManager.clear(platformContext, recycleBin, selfId);
        }
    }
}
