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
import static com.swirlds.platform.event.preconsensus.PcesBirthRoundMigration.migratePcesToBirthRoundMode;
import static com.swirlds.platform.event.preconsensus.PcesUtilities.getDatabaseDirectory;
import static com.swirlds.platform.state.BirthRoundStateMigration.modifyStateForBirthRoundMigration;
import static com.swirlds.platform.state.address.AddressBookMetrics.registerAddressBookMetrics;
import static com.swirlds.platform.state.iss.IssDetector.DO_NOT_IGNORE_ROUNDS;
import static com.swirlds.platform.state.signed.SignedStateFileReader.getSavedStateFiles;
import static com.swirlds.platform.system.InitTrigger.GENESIS;
import static com.swirlds.platform.system.InitTrigger.RESTART;
import static com.swirlds.platform.system.SoftwareVersion.NO_VERSION;
import static com.swirlds.platform.system.UptimeData.NO_ROUND;

import com.swirlds.base.state.Startable;
import com.swirlds.base.time.Time;
import com.swirlds.base.utility.Pair;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.crypto.Signature;
import com.swirlds.common.io.IOIterator;
import com.swirlds.common.io.utility.RecycleBin;
import com.swirlds.common.merkle.crypto.MerkleCryptoFactory;
import com.swirlds.common.merkle.utility.SerializableLong;
import com.swirlds.common.notification.NotificationEngine;
import com.swirlds.common.platform.NodeId;
import com.swirlds.common.scratchpad.Scratchpad;
import com.swirlds.common.stream.EventStreamManager;
import com.swirlds.common.stream.RunningEventHashUpdate;
import com.swirlds.common.threading.framework.QueueThread;
import com.swirlds.common.threading.framework.config.QueueThreadConfiguration;
import com.swirlds.common.threading.framework.config.QueueThreadMetricsConfiguration;
import com.swirlds.common.threading.manager.AdHocThreadManager;
import com.swirlds.common.threading.manager.ThreadManager;
import com.swirlds.common.utility.AutoCloseableWrapper;
import com.swirlds.common.utility.Clearable;
import com.swirlds.common.utility.LoggingClearables;
import com.swirlds.common.utility.StackTrace;
import com.swirlds.common.wiring.wires.output.OutputWire;
import com.swirlds.logging.legacy.LogMarker;
import com.swirlds.logging.legacy.payload.FatalErrorPayload;
import com.swirlds.metrics.api.Metrics;
import com.swirlds.platform.components.ConsensusEngine;
import com.swirlds.platform.components.DefaultConsensusEngine;
import com.swirlds.platform.components.DefaultSavedStateController;
import com.swirlds.platform.components.SavedStateController;
import com.swirlds.platform.components.appcomm.LatestCompleteStateNotifier;
import com.swirlds.platform.config.StateConfig;
import com.swirlds.platform.config.ThreadConfig;
import com.swirlds.platform.config.TransactionConfig;
import com.swirlds.platform.consensus.NonAncientEventWindow;
import com.swirlds.platform.crypto.CryptoStatic;
import com.swirlds.platform.crypto.KeysAndCerts;
import com.swirlds.platform.crypto.PlatformSigner;
import com.swirlds.platform.event.AncientMode;
import com.swirlds.platform.event.DefaultFutureEventBuffer;
import com.swirlds.platform.event.EventCounter;
import com.swirlds.platform.event.FutureEventBuffer;
import com.swirlds.platform.event.GossipEvent;
import com.swirlds.platform.event.creation.EventCreationManager;
import com.swirlds.platform.event.deduplication.EventDeduplicator;
import com.swirlds.platform.event.deduplication.StandardEventDeduplicator;
import com.swirlds.platform.event.hashing.DefaultEventHasher;
import com.swirlds.platform.event.hashing.EventHasher;
import com.swirlds.platform.event.linking.InOrderLinker;
import com.swirlds.platform.event.orphan.OrphanBuffer;
import com.swirlds.platform.event.preconsensus.DefaultPcesSequencer;
import com.swirlds.platform.event.preconsensus.EventDurabilityNexus;
import com.swirlds.platform.event.preconsensus.PcesConfig;
import com.swirlds.platform.event.preconsensus.PcesFileManager;
import com.swirlds.platform.event.preconsensus.PcesFileReader;
import com.swirlds.platform.event.preconsensus.PcesFileTracker;
import com.swirlds.platform.event.preconsensus.PcesReplayer;
import com.swirlds.platform.event.preconsensus.PcesSequencer;
import com.swirlds.platform.event.preconsensus.PcesWriter;
import com.swirlds.platform.event.validation.AddressBookUpdate;
import com.swirlds.platform.event.validation.DefaultInternalEventValidator;
import com.swirlds.platform.event.validation.EventSignatureValidator;
import com.swirlds.platform.event.validation.InternalEventValidator;
import com.swirlds.platform.eventhandling.ConsensusRoundHandler;
import com.swirlds.platform.eventhandling.EventConfig;
import com.swirlds.platform.eventhandling.TransactionPool;
import com.swirlds.platform.gossip.DefaultIntakeEventCounter;
import com.swirlds.platform.gossip.IntakeEventCounter;
import com.swirlds.platform.gossip.NoOpIntakeEventCounter;
import com.swirlds.platform.gossip.SyncGossip;
import com.swirlds.platform.gossip.shadowgraph.Shadowgraph;
import com.swirlds.platform.gossip.sync.config.SyncConfig;
import com.swirlds.platform.gui.GuiPlatformAccessor;
import com.swirlds.platform.internal.EventImpl;
import com.swirlds.platform.listeners.PlatformStatusChangeListener;
import com.swirlds.platform.listeners.PlatformStatusChangeNotification;
import com.swirlds.platform.listeners.ReconnectCompleteListener;
import com.swirlds.platform.listeners.ReconnectCompleteNotification;
import com.swirlds.platform.listeners.StateLoadedFromDiskCompleteListener;
import com.swirlds.platform.listeners.StateLoadedFromDiskNotification;
import com.swirlds.platform.listeners.StateWriteToDiskCompleteListener;
import com.swirlds.platform.listeners.StateWriteToDiskCompleteNotification;
import com.swirlds.platform.metrics.ConsensusMetrics;
import com.swirlds.platform.metrics.ConsensusMetricsImpl;
import com.swirlds.platform.metrics.RuntimeMetrics;
import com.swirlds.platform.metrics.SwirldStateMetrics;
import com.swirlds.platform.metrics.SyncMetrics;
import com.swirlds.platform.metrics.TransactionMetrics;
import com.swirlds.platform.recovery.EmergencyRecoveryManager;
import com.swirlds.platform.state.PlatformState;
import com.swirlds.platform.state.State;
import com.swirlds.platform.state.SwirldStateManager;
import com.swirlds.platform.state.iss.IssDetector;
import com.swirlds.platform.state.iss.IssHandler;
import com.swirlds.platform.state.iss.IssScratchpad;
import com.swirlds.platform.state.nexus.DefaultLatestCompleteStateNexus;
import com.swirlds.platform.state.nexus.EmergencyStateNexus;
import com.swirlds.platform.state.nexus.LatestCompleteStateNexus;
import com.swirlds.platform.state.nexus.LockFreeStateNexus;
import com.swirlds.platform.state.nexus.SignedStateNexus;
import com.swirlds.platform.state.signed.DefaultSignedStateHasher;
import com.swirlds.platform.state.signed.ReservedSignedState;
import com.swirlds.platform.state.signed.SavedStateInfo;
import com.swirlds.platform.state.signed.SignedState;
import com.swirlds.platform.state.signed.SignedStateFileManager;
import com.swirlds.platform.state.signed.SignedStateGarbageCollector;
import com.swirlds.platform.state.signed.SignedStateHasher;
import com.swirlds.platform.state.signed.SignedStateMetrics;
import com.swirlds.platform.state.signed.SignedStateSentinel;
import com.swirlds.platform.state.signed.StartupStateUtils;
import com.swirlds.platform.state.signed.StateDumpRequest;
import com.swirlds.platform.state.signed.StateSavingResult;
import com.swirlds.platform.state.signed.StateSignatureCollector;
import com.swirlds.platform.state.signed.StateToDiskReason;
import com.swirlds.platform.stats.StatConstructor;
import com.swirlds.platform.system.InitTrigger;
import com.swirlds.platform.system.Platform;
import com.swirlds.platform.system.SoftwareVersion;
import com.swirlds.platform.system.SwirldState;
import com.swirlds.platform.system.SystemExitCode;
import com.swirlds.platform.system.SystemExitUtils;
import com.swirlds.platform.system.UptimeData;
import com.swirlds.platform.system.address.Address;
import com.swirlds.platform.system.address.AddressBook;
import com.swirlds.platform.system.address.AddressBookUtils;
import com.swirlds.platform.system.events.BirthRoundMigrationShim;
import com.swirlds.platform.system.events.DefaultBirthRoundMigrationShim;
import com.swirlds.platform.system.status.PlatformStatus;
import com.swirlds.platform.system.status.PlatformStatusManager;
import com.swirlds.platform.system.status.actions.DoneReplayingEventsAction;
import com.swirlds.platform.system.status.actions.ReconnectCompleteAction;
import com.swirlds.platform.system.status.actions.StartedReplayingEventsAction;
import com.swirlds.platform.system.transaction.SwirldTransaction;
import com.swirlds.platform.util.HashLogger;
import com.swirlds.platform.util.ThingsToStart;
import com.swirlds.platform.wiring.NoInput;
import com.swirlds.platform.wiring.PlatformWiring;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class SwirldsPlatform implements Platform {

    public static final String PLATFORM_THREAD_POOL_NAME = "platform-core";

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
    private final Shadowgraph shadowGraph;

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
    private final long initialAncientThreshold;

    /**
     * The latest round to have reached consensus in the initial state
     */
    private final long startingRound;

    /**
     * Holds the latest state that is immutable. May be unhashed (in the future), may or may not have all required
     * signatures. State is returned with a reservation.
     * <p>
     * NOTE: This is currently set when a state has finished hashing. In the future, this will be set at the moment a
     * new state is created, before it is hashed.
     */
    private final SignedStateNexus latestImmutableStateNexus = new LockFreeStateNexus();

    private final TransactionPool transactionPool;
    /** Handles all interaction with {@link SwirldState} */
    private final SwirldStateManager swirldStateManager;
    /** Checks the validity of transactions and submits valid ones to the transaction pool */
    private final SwirldTransactionSubmitter transactionSubmitter;
    /** clears all pipelines to prepare for a reconnect */
    private final Clearable clearAllPipelines;

    /**
     * All things that need to be started when the platform is started.
     */
    private final ThingsToStart thingsToStart;

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
    private final SyncGossip gossip;

    /**
     * The round of the most recent reconnect state received, or {@link UptimeData#NO_ROUND} if no reconnect state has
     * been received since startup.
     */
    private final AtomicLong latestReconnectRound = new AtomicLong(NO_ROUND);

    /** Manages emergency recovery */
    private final EmergencyRecoveryManager emergencyRecoveryManager;
    /** Controls which states are saved to disk */
    private final SavedStateController savedStateController;

    private final SignedStateGarbageCollector signedStateGarbageCollector;

    /**
     * Encapsulated wiring for the platform.
     */
    private final PlatformWiring platformWiring;

    private final AncientMode ancientMode;

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

        ancientMode = platformContext
                .getConfiguration()
                .getConfigData(EventConfig.class)
                .getAncientMode();

        // This method is a no-op if we are not in birth round mode, or if we have already migrated.
        modifyStateForBirthRoundMigration(initialState, ancientMode, appVersion);

        if (ancientMode == AncientMode.BIRTH_ROUND_THRESHOLD) {
            try {
                // This method is a no-op if we have already completed birth round migration or if we are at genesis.
                migratePcesToBirthRoundMode(
                        platformContext,
                        recycleBin,
                        id,
                        initialState.getRound(),
                        initialState.getState().getPlatformState().getLowestJudgeGenerationBeforeBirthRoundMode());
            } catch (final IOException e) {
                throw new UncheckedIOException("Birth round migration failed during PCES migration.", e);
            }
        }

        this.emergencyRecoveryManager = Objects.requireNonNull(emergencyRecoveryManager, "emergencyRecoveryManager");
        final Time time = Time.getCurrent();

        thingsToStart = new ThingsToStart();

        // FUTURE WORK: use a real thread manager here
        final ThreadManager threadManager = getStaticThreadManager();

        notificationEngine = NotificationEngine.buildEngine(threadManager);

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
        platformStatusManager = thingsToStart.add(
                new PlatformStatusManager(platformContext, time, threadManager, statusChangeConsumer));

        thingsToStart.add(Objects.requireNonNull(recycleBin));

        this.metrics = platformContext.getMetrics();

        metrics.getOrCreate(StatConstructor.createEnumStat(
                "PlatformStatus",
                Metrics.PLATFORM_CATEGORY,
                PlatformStatus.values(),
                platformStatusManager::getCurrentStatus));

        registerAddressBookMetrics(metrics, currentAddressBook, selfId);

        final ConsensusMetrics consensusMetrics = new ConsensusMetricsImpl(this.selfId, metrics);

        final SyncMetrics syncMetrics = new SyncMetrics(metrics);
        RuntimeMetrics.setup(metrics);

        this.shadowGraph = new Shadowgraph(platformContext, currentAddressBook);

        final EventConfig eventConfig = platformContext.getConfiguration().getConfigData(EventConfig.class);

        this.keysAndCerts = keysAndCerts;

        EventCounter.registerEventCounterMetrics(metrics);

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
            initialPcesFiles = PcesFileReader.readFilesFromDisk(
                    platformContext,
                    recycleBin,
                    databaseDirectory,
                    initialState.getRound(),
                    preconsensusEventStreamConfig.permitGaps(),
                    ancientMode);

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

        final IssDetector issDetector = new IssDetector(
                platformContext,
                currentAddressBook,
                epochHash,
                appVersion,
                ignorePreconsensusSignatures,
                roundToIgnore);

        final SignedStateFileManager signedStateFileManager = new SignedStateFileManager(
                platformContext,
                new SignedStateMetrics(platformContext.getMetrics()),
                Time.getCurrent(),
                actualMainClassName,
                selfId,
                swirldName);

        transactionPool = new TransactionPool(platformContext);
        final LatestCompleteStateNexus latestCompleteStateNexus =
                new DefaultLatestCompleteStateNexus(stateConfig, platformContext.getMetrics());

        platformWiring = thingsToStart.add(new PlatformWiring(platformContext));

        final boolean useOldStyleIntakeQueue = eventConfig.useOldStyleIntakeQueue();

        final QueueThread<GossipEvent> oldStyleIntakeQueue;
        if (useOldStyleIntakeQueue) {
            oldStyleIntakeQueue = new QueueThreadConfiguration<GossipEvent>(AdHocThreadManager.getStaticThreadManager())
                    .setCapacity(10_000)
                    .setThreadName("old_style_intake_queue")
                    .setComponent("platform")
                    .setHandler(event -> platformWiring.getGossipEventInput().put(event))
                    .setMetricsConfiguration(new QueueThreadMetricsConfiguration(metrics).enableMaxSizeMetric())
                    .build();
            thingsToStart.add(oldStyleIntakeQueue);

        } else {
            oldStyleIntakeQueue = null;
        }

        savedStateController = new DefaultSavedStateController(stateConfig);

        final SignedStateMetrics signedStateMetrics = new SignedStateMetrics(platformContext.getMetrics());
        final StateSignatureCollector stateSignatureCollector = new StateSignatureCollector(
                platformContext.getConfiguration().getConfigData(StateConfig.class), signedStateMetrics);

        thingsToStart.add(new SignedStateSentinel(platformContext, threadManager, Time.getCurrent()));
        signedStateGarbageCollector =
                thingsToStart.add(new SignedStateGarbageCollector(threadManager, signedStateMetrics));

        final LatestCompleteStateNotifier latestCompleteStateNotifier =
                new LatestCompleteStateNotifier(notificationEngine);

        final EventHasher eventHasher = new DefaultEventHasher(platformContext);
        final StateSigner stateSigner = new StateSigner(new PlatformSigner(keysAndCerts), platformStatusManager);
        final PcesReplayer pcesReplayer = new PcesReplayer(
                time,
                platformWiring.getPcesReplayerEventOutput(),
                platformWiring::flushIntakePipeline,
                platformWiring::flushConsensusRoundHandler,
                () -> latestImmutableStateNexus.getState("PCES replay"));
        final EventDurabilityNexus eventDurabilityNexus = new EventDurabilityNexus();

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
                new SwirldStateMetrics(platformContext.getMetrics()),
                platformStatusManager,
                initialState.getState(),
                appVersion);

        final ConsensusRoundHandler consensusRoundHandler = new ConsensusRoundHandler(
                platformContext,
                swirldStateManager,
                signedStateGarbageCollector,
                eventDurabilityNexus::waitUntilDurable,
                platformStatusManager,
                appVersion);

        final PcesSequencer sequencer = new DefaultPcesSequencer();

        final SyncConfig syncConfig = platformContext.getConfiguration().getConfigData(SyncConfig.class);
        final IntakeEventCounter intakeEventCounter;
        if (syncConfig.waitForEventsInIntake()) {
            intakeEventCounter = new DefaultIntakeEventCounter(currentAddressBook);
        } else {
            intakeEventCounter = new NoOpIntakeEventCounter();
        }

        final InternalEventValidator internalEventValidator = new DefaultInternalEventValidator(
                platformContext, time, currentAddressBook.getSize() == 1, intakeEventCounter);
        final EventDeduplicator eventDeduplicator = new StandardEventDeduplicator(platformContext, intakeEventCounter);
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
        final ConsensusEngine consensusEngine = new DefaultConsensusEngine(
                platformContext, selfId, consensusRef::get, shadowGraph, intakeEventCounter, e -> {});

        final LongSupplier intakeQueueSizeSupplier =
                oldStyleIntakeQueue == null ? platformWiring.getIntakeQueueSizeSupplier() : oldStyleIntakeQueue::size;

        final EventCreationManager eventCreationManager = buildEventCreationManager(
                platformContext,
                this,
                currentAddressBook,
                selfId,
                appVersion,
                transactionPool,
                intakeQueueSizeSupplier,
                platformStatusManager::getCurrentStatus,
                latestReconnectRound::get);

        platformWiring.wireExternalComponents(platformStatusManager, transactionPool, notificationEngine);

        final FutureEventBuffer futureEventBuffer = new DefaultFutureEventBuffer(platformContext);

        final IssHandler issHandler =
                new IssHandler(stateConfig, this::haltRequested, this::handleFatalError, issScratchpad);

        final OutputWire<StateSavingResult> stateSavingResultOutput = platformWiring.getStateSavingResultOutput();
        stateSavingResultOutput.solderTo(
                "stateSavingResultNotificationEngine",
                "state saving result notification",
                stateSavingResult -> notificationEngine.dispatch(
                        StateWriteToDiskCompleteListener.class,
                        new StateWriteToDiskCompleteNotification(
                                stateSavingResult.round(),
                                stateSavingResult.consensusTimestamp(),
                                stateSavingResult.freezeState())));

        final HashLogger hashLogger =
                new HashLogger(platformContext.getConfiguration().getConfigData(StateConfig.class));

        final BirthRoundMigrationShim birthRoundMigrationShim = buildBirthRoundMigrationShim(initialState);

        final SignedStateHasher signedStateHasher =
                new DefaultSignedStateHasher(signedStateMetrics, this::handleFatalError);

        platformWiring.bind(
                eventHasher,
                internalEventValidator,
                eventDeduplicator,
                eventSignatureValidator,
                orphanBuffer,
                inOrderLinker,
                consensusEngine,
                signedStateFileManager,
                stateSigner,
                pcesReplayer,
                pcesWriter,
                eventDurabilityNexus,
                shadowGraph,
                sequencer,
                eventCreationManager,
                swirldStateManager,
                stateSignatureCollector,
                consensusRoundHandler,
                eventStreamManager,
                futureEventBuffer,
                issDetector,
                issHandler,
                hashLogger,
                birthRoundMigrationShim,
                latestCompleteStateNotifier,
                latestImmutableStateNexus,
                latestCompleteStateNexus,
                savedStateController,
                signedStateHasher);

        // Load the minimum generation into the pre-consensus event writer
        final List<SavedStateInfo> savedStates =
                getSavedStateFiles(platformContext, actualMainClassName, selfId, swirldName);
        if (!savedStates.isEmpty()) {
            // The minimum generation of non-ancient events for the oldest state snapshot on disk.
            final long minimumGenerationNonAncientForOldestState =
                    savedStates.get(savedStates.size() - 1).metadata().minimumGenerationNonAncient();
            platformWiring.getPcesMinimumGenerationToStoreInput().inject(minimumGenerationNonAncientForOldestState);
        }

        transactionSubmitter = new SwirldTransactionSubmitter(
                platformStatusManager::getCurrentStatus,
                transactionConfig,
                transaction -> transactionPool.submitTransaction(transaction, false),
                new TransactionMetrics(metrics));

        final boolean startedFromGenesis = initialState.isGenesisState();

        final Consumer<GossipEvent> eventFromGossipConsumer = oldStyleIntakeQueue == null
                ? platformWiring.getGossipEventInput()::put
                : event -> {
                    try {
                        oldStyleIntakeQueue.put(event);
                    } catch (final InterruptedException e) {
                        logger.error(
                                EXCEPTION.getMarker(), "Interrupted while adding event to old style intake queue", e);
                        Thread.currentThread().interrupt();
                    }
                };

        gossip = new SyncGossip(
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
                emergencyRecoveryManager,
                eventFromGossipConsumer,
                intakeQueueSizeSupplier,
                swirldStateManager,
                latestCompleteStateNexus,
                syncMetrics,
                platformStatusManager,
                this::loadReconnectState,
                this::clearAllPipelines,
                intakeEventCounter,
                () -> emergencyState.getState("emergency reconnect")) {};

        consensusRef.set(new ConsensusImpl(platformContext, consensusMetrics, getAddressBook()));

        if (startedFromGenesis) {
            initialAncientThreshold = 0;
            startingRound = 0;
        } else {
            initialAncientThreshold = initialState.getState().getPlatformState().getAncientThreshold();
            startingRound = initialState.getRound();

            latestImmutableStateNexus.setState(initialState.reserve("set latest immutable to initial state"));

            initialState.setGarbageCollector(signedStateGarbageCollector);
            logSignedStateHash(initialState);
            platformWiring
                    .getSignatureCollectorStateInput()
                    .put(initialState.reserve("loading initial state into sig collector"));

            savedStateController.registerSignedStateFromDisk(initialState);

            platformWiring.updateRunningHash(new RunningEventHashUpdate(initialState.getHashEventsCons(), false));

            loadStateIntoConsensus(initialState);

            // We only load non-ancient events during start up, so the initial non-expired event window will be
            // equal to the non-ancient event window when the system first starts. Over time as we get more events,
            // the non-expired event window will continue to expand until it reaches its full size.
            platformWiring.updateNonAncientEventWindow(new NonAncientEventWindow(
                    initialState.getRound(),
                    initialAncientThreshold,
                    initialAncientThreshold,
                    AncientMode.getAncientMode(platformContext)));
            platformWiring.getIssDetectorWiring().overridingState().put(initialState.reserve("initialize issDetector"));

            // We don't want to invoke these callbacks until after we are starting up.
            thingsToStart.add((Startable) () -> {
                // If we loaded from disk then call the appropriate dispatch.
                // Let the app know that a state was loaded.
                notificationEngine.dispatch(
                        StateLoadedFromDiskCompleteListener.class, new StateLoadedFromDiskNotification());
            });
        }

        clearAllPipelines = new LoggingClearables(
                RECONNECT.getMarker(),
                List.of(
                        Pair.of(platformWiring, "platformWiring"),
                        Pair.of(shadowGraph, "shadowGraph"),
                        Pair.of(transactionPool, "transactionPool")));

        if (platformContext.getConfiguration().getConfigData(ThreadConfig.class).jvmAnchor()) {
            thingsToStart.add(new JvmAnchor(threadManager));
        }

        // To be removed once the GUI component is better integrated with the platform.
        GuiPlatformAccessor.getInstance().setShadowGraph(selfId, shadowGraph);
        GuiPlatformAccessor.getInstance().setConsensusReference(selfId, consensusRef);
        GuiPlatformAccessor.getInstance().setLatestCompleteStateComponent(selfId, latestCompleteStateNexus);
        GuiPlatformAccessor.getInstance().setLatestImmutableStateComponent(selfId, latestImmutableStateNexus);
    }

    /**
     * Builds the birth round migration shim if necessary.
     *
     * @param initialState the initial state
     * @return the birth round migration shim, or null if it is not needed
     */
    @Nullable
    private BirthRoundMigrationShim buildBirthRoundMigrationShim(@NonNull final SignedState initialState) {

        if (ancientMode == AncientMode.GENERATION_THRESHOLD) {
            // We don't need the shim if we haven't migrated to birth round mode.
            return null;
        }

        final State state = initialState.getState();
        final PlatformState platformState = state.getPlatformState();

        return new DefaultBirthRoundMigrationShim(
                platformContext,
                platformState.getFirstVersionInBirthRoundMode(),
                platformState.getLastRoundBeforeBirthRoundMode(),
                platformState.getLowestJudgeGenerationBeforeBirthRoundMode());
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

        // FUTURE WORK: this needs to be updated for birth round compatibility.
        final NonAncientEventWindow eventWindow = new NonAncientEventWindow(
                signedState.getRound(),
                signedState.getState().getPlatformState().getAncientThreshold(),
                signedState.getState().getPlatformState().getAncientThreshold(),
                ancientMode);

        shadowGraph.startWithEventWindow(eventWindow);
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
            platformWiring
                    .getIssDetectorWiring()
                    .overridingState()
                    .put(signedState.reserve("reconnect state to issDetector"));

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
            latestImmutableStateNexus.setState(signedState.reserve("set latest immutable to reconnect state"));
            savedStateController.reconnectStateReceived(
                    signedState.reserve("savedStateController.reconnectStateReceived"));

            signedState.setGarbageCollector(signedStateGarbageCollector);
            logSignedStateHash(signedState);
            // this will send the state to the signature collector which will send it to be written to disk.
            // in the future, we might not send it to the collector because it already has all the signatures
            // if this is the case, we must make sure to send it to the writer directly
            platformWiring
                    .getSignatureCollectorStateInput()
                    .put(signedState.reserve("loading reconnect state into sig collector"));
            loadStateIntoConsensus(signedState);

            platformWiring
                    .getAddressBookUpdateInput()
                    .inject(new AddressBookUpdate(
                            signedState.getState().getPlatformState().getPreviousAddressBook(),
                            signedState.getState().getPlatformState().getAddressBook()));

            platformWiring.updateNonAncientEventWindow(new NonAncientEventWindow(
                    signedState.getRound(),
                    signedState.getState().getPlatformState().getAncientThreshold(),
                    signedState.getState().getPlatformState().getAncientThreshold(),
                    ancientMode));

            platformWiring.updateRunningHash(new RunningEventHashUpdate(signedState.getHashEventsCons(), true));
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

        thingsToStart.start();

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
        thingsToStart.start();

        replayPreconsensusEvents();
        try (final ReservedSignedState reservedState = latestImmutableStateNexus.getState("Get PCES recovery state")) {
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
     * Offers the given state to the hash logger
     * <p>
     * Future work: this method should be removed, since it is doing the same thing as an advanced transformer
     *
     * @param signedState the state to log
     */
    private void logSignedStateHash(@NonNull final SignedState signedState) {
        if (signedState.getState().getHash() != null) {
            final ReservedSignedState stateReservedForHasher = signedState.reserve("logging state hash");

            final boolean offerResult = platformWiring.getHashLoggerInput().offer(stateReservedForHasher);
            if (!offerResult) {
                stateReservedForHasher.close();
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
                    initialPcesFiles.getEventIterator(initialAncientThreshold, startingRound);

            logger.info(
                    STARTUP.getMarker(),
                    "replaying preconsensus event stream starting at generation {}",
                    initialAncientThreshold);

            platformWiring.getPcesReplayerIteratorInput().inject(iterator);
        }

        // We have to wait for all the PCES transactions to reach the ISS detector before telling it that PCES replay is
        // done. The PCES replay will flush the intake pipeline, but we have to flush the hasher

        // FUTURE WORK: These flushes can be done by the PCES replayer.
        platformWiring.flushStateHasher();
        platformWiring.getIssDetectorWiring().endOfPcesReplay().put(NoInput.getInstance());

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
        final ReservedSignedState wrapper = latestImmutableStateNexus.getState(reason);
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
