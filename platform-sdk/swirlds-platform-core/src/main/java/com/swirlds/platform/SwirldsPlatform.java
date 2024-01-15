/*
 * Copyright (C) 2016-2024 Hedera Hashgraph, LLC
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
import static com.swirlds.logging.legacy.LogMarker.EXCEPTION;
import static com.swirlds.logging.legacy.LogMarker.RECONNECT;
import static com.swirlds.logging.legacy.LogMarker.STARTUP;
import static com.swirlds.logging.legacy.LogMarker.STATE_TO_DISK;
import static com.swirlds.platform.event.creation.EventCreationManagerFactory.buildEventCreationManager;
import static com.swirlds.platform.event.preconsensus.PcesUtilities.getDatabaseDirectory;
import static com.swirlds.platform.state.address.AddressBookMetrics.registerAddressBookMetrics;
import static com.swirlds.platform.state.iss.ConsensusHashManager.DO_NOT_IGNORE_ROUNDS;
import static com.swirlds.platform.state.signed.SignedStateFileReader.getSavedStateFiles;
import static com.swirlds.platform.system.InitTrigger.GENESIS;
import static com.swirlds.platform.system.InitTrigger.RESTART;
import static com.swirlds.platform.system.SoftwareVersion.NO_VERSION;
import static com.swirlds.platform.system.UptimeData.NO_ROUND;

import com.swirlds.base.state.Startable;
import com.swirlds.base.time.Time;
import com.swirlds.base.utility.Pair;
import com.swirlds.common.config.StateConfig;
import com.swirlds.common.config.TransactionConfig;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.crypto.Signature;
import com.swirlds.common.io.IOIterator;
import com.swirlds.common.io.utility.RecycleBin;
import com.swirlds.common.merkle.crypto.MerkleCryptoFactory;
import com.swirlds.common.merkle.utility.SerializableLong;
import com.swirlds.common.metrics.Metrics;
import com.swirlds.common.notification.NotificationEngine;
import com.swirlds.common.platform.NodeId;
import com.swirlds.common.scratchpad.Scratchpad;
import com.swirlds.common.stream.EventStreamManager;
import com.swirlds.common.threading.framework.QueueThread;
import com.swirlds.common.threading.framework.config.QueueThreadConfiguration;
import com.swirlds.common.threading.framework.config.QueueThreadMetricsConfiguration;
import com.swirlds.common.threading.interrupt.InterruptableConsumer;
import com.swirlds.common.threading.manager.ThreadManager;
import com.swirlds.common.utility.AutoCloseableWrapper;
import com.swirlds.common.utility.Clearable;
import com.swirlds.common.utility.LoggingClearables;
import com.swirlds.common.utility.StackTrace;
import com.swirlds.common.wiring.model.WiringModel;
import com.swirlds.logging.legacy.LogMarker;
import com.swirlds.logging.legacy.payload.FatalErrorPayload;
import com.swirlds.platform.components.LinkedEventIntake;
import com.swirlds.platform.components.SavedStateController;
import com.swirlds.platform.components.appcomm.AppCommunicationComponent;
import com.swirlds.platform.components.state.DefaultStateManagementComponent;
import com.swirlds.platform.components.state.StateManagementComponent;
import com.swirlds.platform.components.state.output.NewLatestCompleteStateConsumer;
import com.swirlds.platform.components.transaction.system.ConsensusSystemTransactionManager;
import com.swirlds.platform.components.transaction.system.PreconsensusSystemTransactionManager;
import com.swirlds.platform.config.ThreadConfig;
import com.swirlds.platform.consensus.ConsensusConfig;
import com.swirlds.platform.consensus.NonAncientEventWindow;
import com.swirlds.platform.crypto.CryptoStatic;
import com.swirlds.platform.crypto.KeysAndCerts;
import com.swirlds.platform.crypto.PlatformSigner;
import com.swirlds.platform.dispatch.DispatchBuilder;
import com.swirlds.platform.dispatch.DispatchConfiguration;
import com.swirlds.platform.dispatch.triggers.flow.DiskStateLoadedTrigger;
import com.swirlds.platform.dispatch.triggers.flow.ReconnectStateLoadedTrigger;
import com.swirlds.platform.event.AncientMode;
import com.swirlds.platform.event.EventCounter;
import com.swirlds.platform.event.GossipEvent;
import com.swirlds.platform.event.creation.EventCreationManager;
import com.swirlds.platform.event.deduplication.EventDeduplicator;
import com.swirlds.platform.event.hashing.EventHasher;
import com.swirlds.platform.event.linking.InOrderLinker;
import com.swirlds.platform.event.orphan.OrphanBuffer;
import com.swirlds.platform.event.preconsensus.EventDurabilityNexus;
import com.swirlds.platform.event.preconsensus.PcesConfig;
import com.swirlds.platform.event.preconsensus.PcesFileManager;
import com.swirlds.platform.event.preconsensus.PcesFileReader;
import com.swirlds.platform.event.preconsensus.PcesFileTracker;
import com.swirlds.platform.event.preconsensus.PcesReplayer;
import com.swirlds.platform.event.preconsensus.PcesSequencer;
import com.swirlds.platform.event.preconsensus.PcesWriter;
import com.swirlds.platform.event.validation.AddressBookUpdate;
import com.swirlds.platform.event.validation.EventSignatureValidator;
import com.swirlds.platform.event.validation.InternalEventValidator;
import com.swirlds.platform.eventhandling.ConsensusRoundHandler;
import com.swirlds.platform.eventhandling.EventConfig;
import com.swirlds.platform.eventhandling.TransactionPool;
import com.swirlds.platform.gossip.DefaultIntakeEventCounter;
import com.swirlds.platform.gossip.Gossip;
import com.swirlds.platform.gossip.GossipFactory;
import com.swirlds.platform.gossip.IntakeEventCounter;
import com.swirlds.platform.gossip.NoOpIntakeEventCounter;
import com.swirlds.platform.gossip.shadowgraph.LatestEventTipsetTracker;
import com.swirlds.platform.gossip.shadowgraph.ShadowGraph;
import com.swirlds.platform.gossip.shadowgraph.ShadowGraphEventObserver;
import com.swirlds.platform.gossip.sync.config.SyncConfig;
import com.swirlds.platform.gui.GuiPlatformAccessor;
import com.swirlds.platform.internal.EventImpl;
import com.swirlds.platform.listeners.PlatformStatusChangeListener;
import com.swirlds.platform.listeners.PlatformStatusChangeNotification;
import com.swirlds.platform.listeners.ReconnectCompleteListener;
import com.swirlds.platform.listeners.ReconnectCompleteNotification;
import com.swirlds.platform.listeners.StateLoadedFromDiskCompleteListener;
import com.swirlds.platform.listeners.StateLoadedFromDiskNotification;
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
import com.swirlds.platform.observers.EventObserver;
import com.swirlds.platform.observers.EventObserverDispatcher;
import com.swirlds.platform.recovery.EmergencyRecoveryManager;
import com.swirlds.platform.state.State;
import com.swirlds.platform.state.SwirldStateManager;
import com.swirlds.platform.state.iss.ConsensusHashManager;
import com.swirlds.platform.state.iss.IssHandler;
import com.swirlds.platform.state.iss.IssScratchpad;
import com.swirlds.platform.state.nexus.EmergencyStateNexus;
import com.swirlds.platform.state.nexus.LatestCompleteStateNexus;
import com.swirlds.platform.state.nexus.SignedStateNexus;
import com.swirlds.platform.state.signed.ReservedSignedState;
import com.swirlds.platform.state.signed.SavedStateInfo;
import com.swirlds.platform.state.signed.SignedState;
import com.swirlds.platform.state.signed.SignedStateFileManager;
import com.swirlds.platform.state.signed.SignedStateManager;
import com.swirlds.platform.state.signed.SignedStateMetrics;
import com.swirlds.platform.state.signed.SourceOfSignedState;
import com.swirlds.platform.state.signed.StartupStateUtils;
import com.swirlds.platform.state.signed.StateDumpRequest;
import com.swirlds.platform.state.signed.StateToDiskReason;
import com.swirlds.platform.stats.StatConstructor;
import com.swirlds.platform.system.InitTrigger;
import com.swirlds.platform.system.Platform;
import com.swirlds.platform.system.Shutdown;
import com.swirlds.platform.system.SoftwareVersion;
import com.swirlds.platform.system.SwirldState;
import com.swirlds.platform.system.SystemExitCode;
import com.swirlds.platform.system.SystemExitUtils;
import com.swirlds.platform.system.UptimeData;
import com.swirlds.platform.system.address.Address;
import com.swirlds.platform.system.address.AddressBook;
import com.swirlds.platform.system.address.AddressBookUtils;
import com.swirlds.platform.system.status.PlatformStatus;
import com.swirlds.platform.system.status.PlatformStatusManager;
import com.swirlds.platform.system.status.actions.DoneReplayingEventsAction;
import com.swirlds.platform.system.status.actions.ReconnectCompleteAction;
import com.swirlds.platform.system.status.actions.StartedReplayingEventsAction;
import com.swirlds.platform.system.transaction.StateSignatureTransaction;
import com.swirlds.platform.system.transaction.SwirldTransaction;
import com.swirlds.platform.util.PlatformComponents;
import com.swirlds.platform.wiring.PlatformWiring;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
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

    /** the object that contains all key pairs and CSPRNG state for this member */
    private final KeysAndCerts keysAndCerts;
    /**
     * If a state was loaded from disk, this is the minimum generation non-ancient for that round. If starting from a
     * genesis state, this is 0.
     */
    private final long initialMinimumGenerationNonAncient;

    /**
     * The latest round to have reached consensus in the initial state
     */
    private final long startingRound;

    private final StateManagementComponent stateManagementComponent;

    /**
     * Holds the latest state that is immutable. May be unhashed (in the future), may or may not have all required
     * signatures. State is returned with a reservation.
     * <p>
     * NOTE: This is currently set when a state has finished hashing. In the future, this will be set at the moment a
     * new state is created, before it is hashed.
     */
    private final SignedStateNexus latestImmutableState = new SignedStateNexus();

    private final QueueThread<GossipEvent> intakeQueue;

    /**
     * Validates events and passes valid events further down the intake pipeline.
     */
    private final InterruptableConsumer<GossipEvent> intakeHandler;

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
     * For passing notifications between the platform and the application.
     */
    private final NotificationEngine notificationEngine;

    /**
     * The platform context for this platform. Should be used to access basic services
     */
    private final PlatformContext platformContext;

    /**
     * The initial preconsensus event files read from disk.
     */
    private final PcesFileTracker initialPcesFiles;

    /**
     * Manages the status of the platform.
     */
    private final PlatformStatusManager platformStatusManager;

    /**
     * Responsible for transmitting and receiving events from the network.
     */
    private final Gossip gossip;

    /**
     * The round of the most recent reconnect state received, or {@link UptimeData#NO_ROUND} if no reconnect state has
     * been received since startup.
     */
    private final AtomicLong latestReconnectRound = new AtomicLong(NO_ROUND);

    final ConsensusHashManager consensusHashManager;

    /** Manages emergency recovery */
    private final EmergencyRecoveryManager emergencyRecoveryManager;
    /** Controls which states are saved to disk */
    private final SavedStateController savedStateController;

    /**
     * Encapsulated wiring for the platform.
     */
    private final PlatformWiring platformWiring;

    /**
     * the browser gives the Platform what app to run. There can be multiple Platforms on one computer.
     *
     * @param platformContext          the context for this platform
     * @param keysAndCerts             an object holding all the public/private key pairs and the CSPRNG state for this
     *                                 member
     * @param recycleBin               used to delete files that may be useful for later debugging
     * @param id                       the ID for this node
     * @param mainClassName            the name of the app class inheriting from SwirldMain
     * @param swirldName               the name of the swirld being run
     * @param appVersion               the current version of the running application
     * @param initialState             the initial state of the platform
     * @param emergencyRecoveryManager used in emergency recovery.
     */
    SwirldsPlatform(
            @NonNull final PlatformContext platformContext,
            @NonNull final KeysAndCerts keysAndCerts,
            @NonNull final RecycleBin recycleBin,
            @NonNull final NodeId id,
            @NonNull final String mainClassName,
            @NonNull final String swirldName,
            @NonNull final SoftwareVersion appVersion,
            @NonNull final SignedState initialState,
            @NonNull final EmergencyRecoveryManager emergencyRecoveryManager) {

        this.platformContext = Objects.requireNonNull(platformContext, "platformContext");

        this.emergencyRecoveryManager = Objects.requireNonNull(emergencyRecoveryManager, "emergencyRecoveryManager");
        final Time time = Time.getCurrent();

        final DispatchBuilder dispatchBuilder =
                new DispatchBuilder(platformContext.getConfiguration().getConfigData(DispatchConfiguration.class));

        components = new PlatformComponents(dispatchBuilder);

        // FUTURE WORK: use a real thread manager here
        final ThreadManager threadManager = getStaticThreadManager();

        notificationEngine = NotificationEngine.buildEngine(threadManager);

        dispatchBuilder.registerObservers(this);

        reconnectStateLoadedDispatcher =
                dispatchBuilder.getDispatcher(this, ReconnectStateLoadedTrigger.class)::dispatch;
        diskStateLoadedDispatcher = dispatchBuilder.getDispatcher(this, DiskStateLoadedTrigger.class)::dispatch;

        final StateConfig stateConfig = platformContext.getConfiguration().getConfigData(StateConfig.class);
        final String actualMainClassName = stateConfig.getMainClassName(mainClassName);

        this.selfId = id;
        this.currentAddressBook = initialState.getAddressBook();

        final EmergencyStateNexus emergencyState = new EmergencyStateNexus();
        if (emergencyRecoveryManager.isEmergencyState(initialState)) {
            emergencyState.setState(initialState.reserve("emergency state nexus"));
        }
        final Consumer<PlatformStatus> statusChangeConsumer = s -> {
            notificationEngine.dispatch(PlatformStatusChangeListener.class, new PlatformStatusChangeNotification(s));
            emergencyState.platformStatusChanged(s);
        };
        platformStatusManager =
                components.add(new PlatformStatusManager(platformContext, time, threadManager, statusChangeConsumer));

        this.metrics = platformContext.getMetrics();

        metrics.getOrCreate(StatConstructor.createEnumStat(
                "PlatformStatus",
                Metrics.PLATFORM_CATEGORY,
                PlatformStatus.values(),
                platformStatusManager::getCurrentStatus));

        registerAddressBookMetrics(metrics, currentAddressBook, selfId);

        components.add(Objects.requireNonNull(recycleBin));

        final ConsensusMetrics consensusMetrics = new ConsensusMetricsImpl(this.selfId, metrics);

        final EventIntakeMetrics eventIntakeMetrics = new EventIntakeMetrics(metrics, selfId);
        final SyncMetrics syncMetrics = new SyncMetrics(metrics);
        RuntimeMetrics.setup(metrics);

        this.shadowGraph = new ShadowGraph(time, syncMetrics, currentAddressBook, selfId);

        final EventConfig eventConfig = platformContext.getConfiguration().getConfigData(EventConfig.class);

        final LatestEventTipsetTracker latestEventTipsetTracker;
        final boolean enableEventFiltering = platformContext
                .getConfiguration()
                .getConfigData(SyncConfig.class)
                .filterLikelyDuplicates();
        if (enableEventFiltering) {
            latestEventTipsetTracker =
                    new LatestEventTipsetTracker(time, currentAddressBook, selfId, eventConfig.getAncientMode());
        } else {
            latestEventTipsetTracker = null;
        }

        this.keysAndCerts = keysAndCerts;

        EventCounter.registerEventCounterMetrics(metrics);

        final AppCommunicationComponent appCommunicationComponent =
                new AppCommunicationComponent(notificationEngine, platformContext);

        components.add(appCommunicationComponent);

        metrics.addUpdater(appCommunicationComponent::updateLatestCompleteStateQueueSize);

        final Hash epochHash;
        if (emergencyRecoveryManager.getEmergencyRecoveryFile() != null) {
            epochHash = emergencyRecoveryManager.getEmergencyRecoveryFile().hash();
        } else {
            epochHash = initialState.getState().getPlatformState().getEpochHash();
        }

        StartupStateUtils.doRecoveryCleanup(
                platformContext,
                recycleBin,
                selfId,
                swirldName,
                actualMainClassName,
                epochHash,
                initialState.getRound());

        final PcesConfig preconsensusEventStreamConfig =
                platformContext.getConfiguration().getConfigData(PcesConfig.class);

        final PcesFileManager preconsensusEventFileManager;
        try {
            final Path databaseDirectory = getDatabaseDirectory(platformContext, selfId);

            // When we perform the migration to using birth round bounding, we will need to read
            // the old type and start writing the new type.
            final AncientMode currentFileType = platformContext
                            .getConfiguration()
                            .getConfigData(EventConfig.class)
                            .useBirthRoundAncientThreshold()
                    ? AncientMode.BIRTH_ROUND_THRESHOLD
                    : AncientMode.GENERATION_THRESHOLD;

            initialPcesFiles = PcesFileReader.readFilesFromDisk(
                    platformContext,
                    recycleBin,
                    databaseDirectory,
                    initialState.getRound(),
                    preconsensusEventStreamConfig.permitGaps(),
                    currentFileType);

            preconsensusEventFileManager =
                    new PcesFileManager(platformContext, initialPcesFiles, selfId, initialState.getRound());
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }

        final PcesWriter pcesWriter = new PcesWriter(platformContext, preconsensusEventFileManager);

        // Only validate preconsensus signature transactions if we are not recovering from an ISS.
        // ISS round == null means we haven't observed an ISS yet.
        // ISS round < current round means there was an ISS prior to the saved state
        //    that has already been recovered from.
        // ISS round >= current round means that the ISS happens in the future relative the initial state, meaning
        //    we may observe ISS-inducing signature transactions in the preconsensus event stream.
        final Scratchpad<IssScratchpad> issScratchpad =
                Scratchpad.create(platformContext, selfId, IssScratchpad.class, "platform.iss");
        issScratchpad.logContents();
        final SerializableLong issRound = issScratchpad.get(IssScratchpad.LAST_ISS_ROUND);

        final boolean forceIgnorePcesSignatures = platformContext
                .getConfiguration()
                .getConfigData(PcesConfig.class)
                .forceIgnorePcesSignatures();

        final boolean ignorePreconsensusSignatures;
        if (forceIgnorePcesSignatures) {
            // this is used FOR TESTING ONLY
            ignorePreconsensusSignatures = true;
        } else {
            ignorePreconsensusSignatures = issRound != null && issRound.getValue() >= initialState.getRound();
        }

        // A round that we will completely skip ISS detection for. Needed for tests that do janky state modification
        // without a software upgrade (in production this feature should not be used).
        final long roundToIgnore = stateConfig.validateInitialState() ? DO_NOT_IGNORE_ROUNDS : initialState.getRound();

        consensusHashManager = components.add(new ConsensusHashManager(
                platformContext,
                Time.getCurrent(),
                dispatchBuilder,
                currentAddressBook,
                epochHash,
                appVersion,
                ignorePreconsensusSignatures,
                roundToIgnore));

        components.add(new IssHandler(
                stateConfig,
                selfId,
                platformStatusManager,
                this::haltRequested,
                this::handleFatalError,
                appCommunicationComponent,
                issScratchpad));

        components.add(new IssMetrics(platformContext.getMetrics(), currentAddressBook));

        final SignedStateFileManager signedStateFileManager = new SignedStateFileManager(
                platformContext,
                new SignedStateMetrics(platformContext.getMetrics()),
                Time.getCurrent(),
                actualMainClassName,
                selfId,
                swirldName);

        transactionPool = new TransactionPool(platformContext);

        // FUTURE WORK: at some point this should be part of the unified platform wiring
        final WiringModel model = WiringModel.create(platformContext, Time.getCurrent());
        components.add(model);

        platformWiring = components.add(new PlatformWiring(platformContext, time));

        final LatestCompleteStateNexus latestCompleteState =
                new LatestCompleteStateNexus(stateConfig, platformContext.getMetrics());
        savedStateController = new SavedStateController(stateConfig);
        final NewLatestCompleteStateConsumer newLatestCompleteStateConsumer = ss -> {
            // the app comm component will reserve the state, this should be done by the wiring in the future
            appCommunicationComponent.newLatestCompleteStateEvent(ss);
            // the nexus expects a state to be reserved for it
            // in the future, all of these reservations will be done by the wiring
            latestCompleteState.setState(ss.reserve("setting latest complete state"));
        };

        stateManagementComponent = new DefaultStateManagementComponent(
                platformContext,
                threadManager,
                dispatchBuilder,
                newLatestCompleteStateConsumer,
                this::handleFatalError,
                platformWiring.getSaveStateToDiskInput()::put,
                platformWiring.getSignStateInput()::put);

        final EventHasher eventHasher = new EventHasher(platformContext);
        final StateSigner stateSigner = new StateSigner(new PlatformSigner(keysAndCerts), platformStatusManager);
        final PcesReplayer pcesReplayer = new PcesReplayer(
                time,
                platformWiring.getPcesReplayerEventOutput(),
                platformWiring::flushIntakePipeline,
                () -> latestImmutableState.getState("PCES replay"));
        final EventDurabilityNexus eventDurabilityNexus = new EventDurabilityNexus();
        platformWiring.bind(
                eventHasher, signedStateFileManager, stateSigner, pcesReplayer, pcesWriter, eventDurabilityNexus);

        // Load the minimum generation into the pre-consensus event writer
        final List<SavedStateInfo> savedStates =
                getSavedStateFiles(platformContext, actualMainClassName, selfId, swirldName);
        if (!savedStates.isEmpty()) {
            // The minimum generation of non-ancient events for the oldest state snapshot on disk.
            final long minimumGenerationNonAncientForOldestState =
                    savedStates.get(savedStates.size() - 1).metadata().minimumGenerationNonAncient();
            platformWiring.getPcesMinimumGenerationToStoreInput().inject(minimumGenerationNonAncientForOldestState);
        }

        components.add(stateManagementComponent);

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

        final Address address = getSelfAddress();
        final String eventStreamManagerName;
        if (!address.getMemo().isEmpty()) {
            eventStreamManagerName = address.getMemo();
        } else {
            eventStreamManagerName = String.valueOf(selfId);
        }

        final EventStreamManager<EventImpl> eventStreamManager = new EventStreamManager<>(
                platformContext,
                time,
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

        final InterruptableConsumer<ReservedSignedState> newSignedStateFromTransactionsConsumer = rs -> {
            latestImmutableState.setState(rs.getAndReserve("newSignedStateFromTransactionsConsumer"));
            latestCompleteState.newIncompleteState(rs.get().getRound());
            savedStateController.markSavedState(rs.getAndReserve("savedStateController.markSavedState"));
            stateManagementComponent.newSignedStateFromTransactions(rs);
        };

        final QueueThread<ReservedSignedState> stateHashSignQueue =
                components.add(new QueueThreadConfiguration<ReservedSignedState>(threadManager)
                        .setNodeId(selfId)
                        .setComponent(PLATFORM_THREAD_POOL_NAME)
                        .setThreadName("state-hash-sign")
                        .setHandler(newSignedStateFromTransactionsConsumer)
                        .setCapacity(1)
                        .setMetricsConfiguration(new QueueThreadMetricsConfiguration(metrics).enableBusyTimeMetric())
                        .build());

        final ThreadConfig threadConfig = platformContext.getConfiguration().getConfigData(ThreadConfig.class);

        consensusRoundHandler = components.add(new ConsensusRoundHandler(
                platformContext,
                threadManager,
                selfId,
                swirldStateManager,
                new ConsensusHandlingMetrics(metrics, time),
                eventStreamManager,
                stateHashSignQueue,
                eventDurabilityNexus::waitUntilDurable,
                platformStatusManager,
                consensusHashManager::roundCompleted,
                appVersion));

        final AddedEventMetrics addedEventMetrics = new AddedEventMetrics(this.selfId, metrics);
        final PcesSequencer sequencer = new PcesSequencer();

        final List<EventObserver> eventObservers = new ArrayList<>(List.of(
                new ShadowGraphEventObserver(shadowGraph, latestEventTipsetTracker),
                consensusRoundHandler,
                addedEventMetrics,
                eventIntakeMetrics));

        final EventObserverDispatcher eventObserverDispatcher = new EventObserverDispatcher(eventObservers);

        final SyncConfig syncConfig = platformContext.getConfiguration().getConfigData(SyncConfig.class);
        final IntakeEventCounter intakeEventCounter;
        if (syncConfig.waitForEventsInIntake()) {
            intakeEventCounter = new DefaultIntakeEventCounter(currentAddressBook);
        } else {
            intakeEventCounter = new NoOpIntakeEventCounter();
        }

        final InternalEventValidator internalEventValidator = new InternalEventValidator(
                platformContext, time, currentAddressBook.getSize() == 1, intakeEventCounter);
        final EventDeduplicator eventDeduplicator =
                new EventDeduplicator(platformContext, intakeEventCounter, eventIntakeMetrics);
        final EventSignatureValidator eventSignatureValidator = new EventSignatureValidator(
                platformContext,
                time,
                CryptoStatic::verifySignature,
                appVersion,
                initialState.getState().getPlatformState().getPreviousAddressBook(),
                currentAddressBook,
                intakeEventCounter);
        final OrphanBuffer orphanBuffer = new OrphanBuffer(platformContext, intakeEventCounter);
        final InOrderLinker inOrderLinker = new InOrderLinker(platformContext, time, intakeEventCounter);
        final LinkedEventIntake linkedEventIntake = new LinkedEventIntake(
                platformContext,
                time,
                consensusRef::get,
                eventObserverDispatcher,
                shadowGraph,
                latestEventTipsetTracker,
                intakeEventCounter,
                platformWiring.getKeystoneEventSequenceNumberOutput());

        final EventCreationManager eventCreationManager = buildEventCreationManager(
                platformContext,
                time,
                this,
                currentAddressBook,
                selfId,
                appVersion,
                transactionPool,
                this::getIntakeQueueSize,
                platformStatusManager::getCurrentStatus,
                latestReconnectRound::get);

        platformWiring.bindIntake(
                internalEventValidator,
                eventDeduplicator,
                eventSignatureValidator,
                orphanBuffer,
                inOrderLinker,
                linkedEventIntake,
                eventCreationManager,
                sequencer,
                swirldStateManager,
                signedStateManager);

        intakeHandler = platformWiring.getEventInput()::put;

        intakeQueue = components.add(new QueueThreadConfiguration<GossipEvent>(threadManager)
                .setNodeId(selfId)
                .setComponent(PLATFORM_THREAD_POOL_NAME)
                .setThreadName("event-intake")
                .setHandler(intakeHandler)
                .setCapacity(eventConfig.eventIntakeQueueSize())
                .setLogAfterPauseDuration(threadConfig.logStackTracePauseDuration())
                .setMetricsConfiguration(new QueueThreadMetricsConfiguration(metrics).enableMaxSizeMetric())
                .build());

        platformWiring.wireExternalComponents(platformStatusManager, appCommunicationComponent, transactionPool);

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
                keysAndCerts,
                notificationEngine,
                currentAddressBook,
                selfId,
                appVersion,
                epochHash,
                shadowGraph,
                latestEventTipsetTracker,
                emergencyRecoveryManager,
                consensusRef,
                intakeQueue,
                swirldStateManager,
                latestCompleteState,
                syncMetrics,
                platformStatusManager,
                this::loadReconnectState,
                this::clearAllPipelines,
                intakeEventCounter,
                () -> emergencyState.getState("emergency reconnect"));

        consensusRef.set(new ConsensusImpl(
                platformContext.getConfiguration().getConfigData(ConsensusConfig.class),
                consensusMetrics,
                getAddressBook(),
                platformContext
                        .getConfiguration()
                        .getConfigData(EventConfig.class)
                        .useBirthRoundAncientThreshold()));

        if (startedFromGenesis) {
            initialMinimumGenerationNonAncient = 0;
            startingRound = 0;
        } else {
            initialMinimumGenerationNonAncient =
                    initialState.getState().getPlatformState().getMinimumGenerationNonAncient();
            startingRound = initialState.getRound();

            latestImmutableState.setState(initialState.reserve("set latest immutable to initial state"));
            stateManagementComponent.stateToLoad(initialState, SourceOfSignedState.DISK);
            savedStateController.registerSignedStateFromDisk(initialState);
            consensusRoundHandler.loadDataFromSignedState(initialState, false);

            loadStateIntoConsensus(initialState);

            platformWiring.updateNonAncientEventWindow(NonAncientEventWindow.createUsingPlatformContext(
                    initialState.getRound(), initialMinimumGenerationNonAncient, platformContext));

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

        clearAllPipelines = new LoggingClearables(
                RECONNECT.getMarker(),
                List.of(
                        Pair.of(intakeQueue, "intakeQueue"),
                        Pair.of(platformWiring, "platformWiring"),
                        Pair.of(shadowGraph, "shadowGraph"),
                        Pair.of(consensusRoundHandler, "consensusRoundHandler"),
                        Pair.of(transactionPool, "transactionPool")));

        if (platformContext.getConfiguration().getConfigData(ThreadConfig.class).jvmAnchor()) {
            components.add(new JvmAnchor(threadManager));
        }

        // To be removed once the GUI component is better integrated with the platform.
        GuiPlatformAccessor.getInstance().setShadowGraph(selfId, shadowGraph);
        GuiPlatformAccessor.getInstance().setStateManagementComponent(selfId, stateManagementComponent);
        GuiPlatformAccessor.getInstance().setConsensusReference(selfId, consensusRef);
        GuiPlatformAccessor.getInstance().setLatestCompleteStateComponent(selfId, latestCompleteState);
    }

    /**
     * Get the current size of the intake queue. Helper method to break a circular dependency.
     *
     * @return the current size of the intake queue
     */
    private int getIntakeQueueSize() {
        return intakeQueue.size();
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
            previousSoftwareVersion = signedState.getState().getPlatformState().getCreationSoftwareVersion();
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

        initialState.getSwirldState().init(this, initialState.getPlatformState(), trigger, previousSoftwareVersion);

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
     * Loads the signed state data into consensus
     *
     * @param signedState the state to get the data from
     */
    private void loadStateIntoConsensus(@NonNull final SignedState signedState) {
        Objects.requireNonNull(signedState);

        consensusRef.get().loadFromSignedState(signedState);
        shadowGraph.startFromGeneration(consensusRef.get().getMinGenerationNonAncient());

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
                            signedState.getState().getPlatformState(),
                            InitTrigger.RECONNECT,
                            signedState.getState().getPlatformState().getCreationSoftwareVersion());
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

            // kick off transition to RECONNECT_COMPLETE before beginning to save the reconnect state to disk
            // this guarantees that the platform status will be RECONNECT_COMPLETE before the state is saved
            platformStatusManager.submitStatusAction(new ReconnectCompleteAction(signedState.getRound()));
            latestImmutableState.setState(signedState.reserve("set latest immutable to reconnect state"));
            stateManagementComponent.stateToLoad(signedState, SourceOfSignedState.RECONNECT);
            savedStateController.reconnectStateReceived(
                    signedState.reserve("savedStateController.reconnectStateReceived"));
            platformWiring.getSaveStateToDiskInput().put(signedState.reserve("save reconnect state to disk"));

            loadStateIntoConsensus(signedState);

            platformWiring
                    .getAddressBookUpdateInput()
                    .inject(new AddressBookUpdate(
                            signedState.getState().getPlatformState().getPreviousAddressBook(),
                            signedState.getState().getPlatformState().getAddressBook()));
            platformWiring.updateNonAncientEventWindow(NonAncientEventWindow.createUsingPlatformContext(
                    signedState.getRound(), signedState.getMinRoundGeneration(), platformContext));

            consensusRoundHandler.loadDataFromSignedState(signedState, true);

            platformWiring.getPcesWriterRegisterDiscontinuityInput().inject(signedState.getRound());

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
        gossip.stop();
    }

    /**
     * Start this platform.
     */
    @Override
    public void start() {
        logger.info(STARTUP.getMarker(), "Starting platform {}", selfId);

        components.start();

        metrics.start();

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

        replayPreconsensusEvents();
        try (final ReservedSignedState reservedState = latestImmutableState.getState("Get PCES recovery state")) {
            if (reservedState == null) {
                logger.warn(
                        STATE_TO_DISK.getMarker(),
                        "Trying to dump PCES recovery state to disk, but no state is available.");
            } else {
                final SignedState signedState = reservedState.get();
                signedState.markAsStateToSave(StateToDiskReason.PCES_RECOVERY_COMPLETE);

                final StateDumpRequest request =
                        StateDumpRequest.create(signedState.reserve("dumping PCES recovery state"));

                platformWiring.getDumpStateToDiskInput().put(request);
                request.waitForFinished().run();
            }
        }
    }

    /**
     * Replay preconsensus events.
     */
    private void replayPreconsensusEvents() {
        platformStatusManager.submitStatusAction(new StartedReplayingEventsAction());

        final boolean emergencyRecoveryNeeded = emergencyRecoveryManager.isEmergencyStateRequired();

        // if we need to do an emergency recovery, replaying the PCES could cause issues if the
        // minimum generation non-ancient is reversed to a smaller value, so we skip it
        if (!emergencyRecoveryNeeded) {
            final IOIterator<GossipEvent> iterator =
                    initialPcesFiles.getEventIterator(initialMinimumGenerationNonAncient, startingRound);

            logger.info(
                    STARTUP.getMarker(),
                    "replaying preconsensus event stream starting at generation {}",
                    initialMinimumGenerationNonAncient);

            platformWiring.getPcesReplayerIteratorInput().inject(iterator);
        }

        consensusHashManager.signalEndOfPreconsensusReplay();

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
        return new PlatformSigner(keysAndCerts).sign(data);
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
    public @NonNull <T extends SwirldState> AutoCloseableWrapper<T> getLatestImmutableState(
            @NonNull final String reason) {
        final ReservedSignedState wrapper = latestImmutableState.getState(reason);
        return wrapper == null
                ? AutoCloseableWrapper.empty()
                : new AutoCloseableWrapper<>((T) wrapper.get().getState().getSwirldState(), wrapper::close);
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
     * Inform all components that a fatal error has occurred, log the error, and shutdown the JVM.
     */
    private void handleFatalError(
            @Nullable final String msg, @Nullable final Throwable throwable, @NonNull final SystemExitCode exitCode) {
        logger.fatal(
                EXCEPTION.getMarker(),
                "{}\nCaller stack trace:\n{}\nThrowable provided:",
                new FatalErrorPayload("Fatal error, node will shut down. Reason: " + msg),
                StackTrace.getStackTrace().toString(),
                throwable);

        SystemExitUtils.exitSystem(exitCode, msg);
    }
}
