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
import static com.swirlds.logging.legacy.LogMarker.RECONNECT;
import static com.swirlds.logging.legacy.LogMarker.STARTUP;
import static com.swirlds.logging.legacy.LogMarker.STATE_TO_DISK;
import static com.swirlds.platform.event.preconsensus.PcesBirthRoundMigration.migratePcesToBirthRoundMode;
import static com.swirlds.platform.state.BirthRoundStateMigration.modifyStateForBirthRoundMigration;
import static com.swirlds.platform.state.address.AddressBookMetrics.registerAddressBookMetrics;
import static com.swirlds.platform.state.snapshot.SignedStateFileReader.getSavedStateFiles;
import static com.swirlds.platform.system.InitTrigger.GENESIS;
import static com.swirlds.platform.system.InitTrigger.RESTART;
import static com.swirlds.platform.system.SoftwareVersion.NO_VERSION;

import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.crypto.Signature;
import com.swirlds.common.io.IOIterator;
import com.swirlds.common.merkle.crypto.MerkleCryptoFactory;
import com.swirlds.common.notification.NotificationEngine;
import com.swirlds.common.platform.NodeId;
import com.swirlds.common.stream.RunningEventHashOverride;
import com.swirlds.common.utility.AutoCloseableWrapper;
import com.swirlds.logging.legacy.LogMarker;
import com.swirlds.platform.builder.PlatformBuildingBlocks;
import com.swirlds.platform.builder.PlatformComponentBuilder;
import com.swirlds.platform.components.AppNotifier;
import com.swirlds.platform.components.DefaultAppNotifier;
import com.swirlds.platform.components.DefaultEventWindowManager;
import com.swirlds.platform.components.DefaultSavedStateController;
import com.swirlds.platform.components.EventWindowManager;
import com.swirlds.platform.components.SavedStateController;
import com.swirlds.platform.components.appcomm.DefaultLatestCompleteStateNotifier;
import com.swirlds.platform.components.appcomm.LatestCompleteStateNotifier;
import com.swirlds.platform.config.StateConfig;
import com.swirlds.platform.config.TransactionConfig;
import com.swirlds.platform.consensus.EventWindow;
import com.swirlds.platform.crypto.KeysAndCerts;
import com.swirlds.platform.crypto.PlatformSigner;
import com.swirlds.platform.event.AncientMode;
import com.swirlds.platform.event.EventCounter;
import com.swirlds.platform.event.GossipEvent;
import com.swirlds.platform.event.preconsensus.PcesFileTracker;
import com.swirlds.platform.event.preconsensus.PcesReplayer;
import com.swirlds.platform.event.validation.AddressBookUpdate;
import com.swirlds.platform.eventhandling.ConsensusRoundHandler;
import com.swirlds.platform.eventhandling.EventConfig;
import com.swirlds.platform.gossip.SyncGossip;
import com.swirlds.platform.listeners.ReconnectCompleteNotification;
import com.swirlds.platform.metrics.RuntimeMetrics;
import com.swirlds.platform.metrics.TransactionMetrics;
import com.swirlds.platform.pool.TransactionPoolNexus;
import com.swirlds.platform.publisher.DefaultPlatformPublisher;
import com.swirlds.platform.publisher.PlatformPublisher;
import com.swirlds.platform.state.PlatformState;
import com.swirlds.platform.state.State;
import com.swirlds.platform.state.SwirldStateManager;
import com.swirlds.platform.state.nexus.DefaultLatestCompleteStateNexus;
import com.swirlds.platform.state.nexus.LatestCompleteStateNexus;
import com.swirlds.platform.state.nexus.LockFreeStateNexus;
import com.swirlds.platform.state.nexus.SignedStateNexus;
import com.swirlds.platform.state.signed.DefaultStateSignatureCollector;
import com.swirlds.platform.state.signed.ReservedSignedState;
import com.swirlds.platform.state.signed.SignedState;
import com.swirlds.platform.state.signed.SignedStateMetrics;
import com.swirlds.platform.state.signed.StateSignatureCollector;
import com.swirlds.platform.state.snapshot.SavedStateInfo;
import com.swirlds.platform.state.snapshot.StateDumpRequest;
import com.swirlds.platform.state.snapshot.StateToDiskReason;
import com.swirlds.platform.system.InitTrigger;
import com.swirlds.platform.system.Platform;
import com.swirlds.platform.system.SoftwareVersion;
import com.swirlds.platform.system.SwirldState;
import com.swirlds.platform.system.address.AddressBook;
import com.swirlds.platform.system.address.AddressBookUtils;
import com.swirlds.platform.system.events.BirthRoundMigrationShim;
import com.swirlds.platform.system.events.DefaultBirthRoundMigrationShim;
import com.swirlds.platform.system.status.DefaultPlatformStatusNexus;
import com.swirlds.platform.system.status.PlatformStatusNexus;
import com.swirlds.platform.system.status.actions.DoneReplayingEventsAction;
import com.swirlds.platform.system.status.actions.ReconnectCompleteAction;
import com.swirlds.platform.system.status.actions.StartedReplayingEventsAction;
import com.swirlds.platform.system.transaction.SwirldTransaction;
import com.swirlds.platform.wiring.PlatformWiring;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.function.LongSupplier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * The swirlds consensus node platform. Responsible for the creation, gossip, and consensus of events. Also manages the
 * transaction handling and state management.
 */
public class SwirldsPlatform implements Platform {

    private static final Logger logger = LogManager.getLogger(SwirldsPlatform.class);

    /**
     * The unique ID of this node.
     */
    private final NodeId selfId;

    /**
     * the current nodes in the network and their information
     */
    private final AddressBook currentAddressBook;

    /**
     * the object that contains all key pairs and CSPRNG state for this member
     */
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

    /**
     * Handles all interaction with {@link SwirldState}
     */
    private final SwirldStateManager swirldStateManager;

    /**
     * Checks the validity of transactions and submits valid ones to the transaction pool
     */
    private final SwirldTransactionSubmitter transactionSubmitter;

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
     * Controls which states are saved to disk
     */
    private final SavedStateController savedStateController;

    /**
     * Encapsulated wiring for the platform.
     */
    private final PlatformWiring platformWiring;

    /**
     * Constructor.
     *
     * @param builder this object is responsible for building platform components and other things needed by the
     *                platform
     */
    public SwirldsPlatform(@NonNull final PlatformComponentBuilder builder) {
        final PlatformBuildingBlocks blocks = builder.getBuildingBlocks();
        platformContext = blocks.platformContext();

        final AncientMode ancientMode = platformContext
                .getConfiguration()
                .getConfigData(EventConfig.class)
                .getAncientMode();

        // The reservation on this state is held by the caller of this constructor.
        final SignedState initialState = blocks.initialState().get();

        // This method is a no-op if we are not in birth round mode, or if we have already migrated.
        final SoftwareVersion appVersion = blocks.appVersion();
        modifyStateForBirthRoundMigration(initialState, ancientMode, appVersion);

        if (ancientMode == AncientMode.BIRTH_ROUND_THRESHOLD) {
            try {
                // This method is a no-op if we have already completed birth round migration or if we are at genesis.
                migratePcesToBirthRoundMode(
                        platformContext,
                        blocks.selfId(),
                        initialState.getRound(),
                        initialState.getState().getPlatformState().getLowestJudgeGenerationBeforeBirthRoundMode());
            } catch (final IOException e) {
                throw new UncheckedIOException("Birth round migration failed during PCES migration.", e);
            }
        }

        selfId = blocks.selfId();
        initialPcesFiles = blocks.initialPcesFiles();
        notificationEngine = blocks.notificationEngine();

        currentAddressBook = initialState.getAddressBook();

        platformWiring = new PlatformWiring(platformContext, blocks.model(), blocks.applicationCallbacks());

        registerAddressBookMetrics(platformContext.getMetrics(), currentAddressBook, selfId);

        RuntimeMetrics.setup(platformContext.getMetrics());

        keysAndCerts = blocks.keysAndCerts();

        EventCounter.registerEventCounterMetrics(platformContext.getMetrics());

        final LatestCompleteStateNexus latestCompleteStateNexus = new DefaultLatestCompleteStateNexus(platformContext);

        savedStateController = new DefaultSavedStateController(platformContext);

        final SignedStateMetrics signedStateMetrics = new SignedStateMetrics(platformContext.getMetrics());
        final StateSignatureCollector stateSignatureCollector =
                new DefaultStateSignatureCollector(platformContext, signedStateMetrics);

        final LatestCompleteStateNotifier latestCompleteStateNotifier = new DefaultLatestCompleteStateNotifier();

        final PlatformStatusNexus statusNexus = new DefaultPlatformStatusNexus(platformContext);
        // Future work: all interaction with platform status should happen over the wiring framework.
        // Once this is done, these hacky references can be removed.
        blocks.platformStatusSupplierReference().set(statusNexus::getCurrentStatus);
        blocks.statusActionSubmitterReference()
                .set(x -> platformWiring.getStatusActionSubmitter().submitStatusAction(x));

        final StateSigner stateSigner = new StateSigner(new PlatformSigner(keysAndCerts), statusNexus);
        final PcesReplayer pcesReplayer = new PcesReplayer(
                platformContext.getTime(),
                platformWiring.getPcesReplayerEventOutput(),
                platformWiring::flushIntakePipeline,
                platformWiring::flushConsensusRoundHandler,
                () -> latestImmutableStateNexus.getState("PCES replay"));

        initializeState(initialState);

        final TransactionConfig transactionConfig =
                platformContext.getConfiguration().getConfigData(TransactionConfig.class);

        // This object makes a copy of the state. After this point, initialState becomes immutable.
        swirldStateManager = blocks.swirldStateManager();
        swirldStateManager.setInitialState(initialState.getState());

        final EventWindowManager eventWindowManager = new DefaultEventWindowManager();

        final ConsensusRoundHandler consensusRoundHandler = new ConsensusRoundHandler(
                platformContext, swirldStateManager, platformWiring.getStatusActionSubmitter(), appVersion);

        final boolean useOldStyleIntakeQueue = platformContext
                .getConfiguration()
                .getConfigData(EventConfig.class)
                .useOldStyleIntakeQueue();

        final LongSupplier intakeQueueSizeSupplier;
        if (useOldStyleIntakeQueue) {
            final SyncGossip gossip = (SyncGossip) builder.buildGossip();
            intakeQueueSizeSupplier = () -> gossip.getOldStyleIntakeQueueSize();
        } else {
            intakeQueueSizeSupplier = platformWiring.getIntakeQueueSizeSupplier();
        }

        blocks.intakeQueueSizeSupplierSupplier().set(intakeQueueSizeSupplier);
        blocks.isInFreezePeriodReference().set(swirldStateManager::isInFreezePeriod);

        final BirthRoundMigrationShim birthRoundMigrationShim = buildBirthRoundMigrationShim(initialState, ancientMode);

        final AppNotifier appNotifier = new DefaultAppNotifier(blocks.notificationEngine());

        final PlatformPublisher publisher = new DefaultPlatformPublisher(blocks.applicationCallbacks());

        platformWiring.bind(
                builder,
                stateSigner,
                pcesReplayer,
                stateSignatureCollector,
                eventWindowManager,
                consensusRoundHandler,
                birthRoundMigrationShim,
                latestCompleteStateNotifier,
                latestImmutableStateNexus,
                latestCompleteStateNexus,
                savedStateController,
                appNotifier,
                publisher,
                statusNexus);

        final Hash legacyRunningEventHash =
                initialState.getState().getPlatformState().getLegacyRunningEventHash() == null
                        ? platformContext.getCryptography().getNullHash()
                        : initialState.getState().getPlatformState().getLegacyRunningEventHash();
        final RunningEventHashOverride runningEventHashOverride =
                new RunningEventHashOverride(legacyRunningEventHash, false);
        platformWiring.updateRunningHash(runningEventHashOverride);

        // Load the minimum generation into the pre-consensus event writer
        final String actualMainClassName = platformContext
                .getConfiguration()
                .getConfigData(StateConfig.class)
                .getMainClassName(blocks.mainClassName());
        final List<SavedStateInfo> savedStates =
                getSavedStateFiles(platformContext, actualMainClassName, selfId, blocks.swirldName());
        if (!savedStates.isEmpty()) {
            // The minimum generation of non-ancient events for the oldest state snapshot on disk.
            final long minimumGenerationNonAncientForOldestState =
                    savedStates.get(savedStates.size() - 1).metadata().minimumGenerationNonAncient();
            platformWiring.getPcesMinimumGenerationToStoreInput().inject(minimumGenerationNonAncientForOldestState);
        }

        final TransactionPoolNexus transactionPoolNexus = blocks.transactionPoolNexus();
        transactionSubmitter = new SwirldTransactionSubmitter(
                statusNexus,
                transactionConfig,
                transaction -> transactionPoolNexus.submitTransaction(transaction, false),
                new TransactionMetrics(platformContext.getMetrics()));

        final boolean startedFromGenesis = initialState.isGenesisState();

        latestImmutableStateNexus.setState(initialState.reserve("set latest immutable to initial state"));

        if (startedFromGenesis) {
            initialAncientThreshold = 0;
            startingRound = 0;
            platformWiring.updateEventWindow(EventWindow.getGenesisEventWindow(ancientMode));
        } else {
            initialAncientThreshold = initialState.getState().getPlatformState().getAncientThreshold();
            startingRound = initialState.getRound();

            logSignedStateHash(initialState);
            platformWiring
                    .getSignatureCollectorStateInput()
                    .put(initialState.reserve("loading initial state into sig collector"));

            savedStateController.registerSignedStateFromDisk(initialState);

            platformWiring.consensusSnapshotOverride(Objects.requireNonNull(
                    initialState.getState().getPlatformState().getSnapshot()));

            // We only load non-ancient events during start up, so the initial expired threshold will be
            // equal to the ancient threshold when the system first starts. Over time as we get more events,
            // the expired threshold will continue to expand until it reaches its full size.
            platformWiring.updateEventWindow(new EventWindow(
                    initialState.getRound(),
                    initialAncientThreshold,
                    initialAncientThreshold,
                    AncientMode.getAncientMode(platformContext)));
            platformWiring.overrideIssDetectorState(initialState.reserve("initialize issDetector"));
        }

        blocks.getLatestCompleteStateReference()
                .set(() -> latestCompleteStateNexus.getState("get latest complete state for reconnect"));
        blocks.loadReconnectStateReference().set(this::loadReconnectState);
        blocks.clearAllPipelinesForReconnectReference().set(platformWiring::clear);
        blocks.latestImmutableStateProviderReference().set(latestImmutableStateNexus::getState);
    }

    /**
     * Builds the birth round migration shim if necessary.
     *
     * @param initialState the initial state
     * @param ancientMode  the ancient mode
     * @return the birth round migration shim, or null if it is not needed
     */
    @Nullable
    private BirthRoundMigrationShim buildBirthRoundMigrationShim(
            @NonNull final SignedState initialState, @NonNull final AncientMode ancientMode) {

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
     * {@inheritDoc}
     */
    @Override
    @NonNull
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
     * Used to load the state received from the sender.
     *
     * @param signedState the signed state that was received from the sender
     */
    private void loadReconnectState(final SignedState signedState) {
        // the state was received, so now we load its data into different objects
        logger.info(LogMarker.STATE_HASH.getMarker(), "RECONNECT: loadReconnectState: reloading state");
        logger.debug(RECONNECT.getMarker(), "`loadReconnectState` : reloading state");
        try {
            platformWiring.overrideIssDetectorState(signedState.reserve("reconnect state to issDetector"));

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
            // kick off transition to RECONNECT_COMPLETE before beginning to save the reconnect state to disk
            // this guarantees that the platform statusp will be RECONNECT_COMPLETE before the state is saved
            platformWiring
                    .getStatusActionSubmitter()
                    .submitStatusAction(new ReconnectCompleteAction(signedState.getRound()));
            latestImmutableStateNexus.setState(signedState.reserve("set latest immutable to reconnect state"));
            savedStateController.reconnectStateReceived(
                    signedState.reserve("savedStateController.reconnectStateReceived"));

            logSignedStateHash(signedState);
            // this will send the state to the signature collector which will send it to be written to disk.
            // in the future, we might not send it to the collector because it already has all the signatures
            // if this is the case, we must make sure to send it to the writer directly
            platformWiring
                    .getSignatureCollectorStateInput()
                    .put(signedState.reserve("loading reconnect state into sig collector"));
            platformWiring.consensusSnapshotOverride(Objects.requireNonNull(
                    signedState.getState().getPlatformState().getSnapshot()));

            platformWiring
                    .getAddressBookUpdateInput()
                    .inject(new AddressBookUpdate(
                            signedState.getState().getPlatformState().getPreviousAddressBook(),
                            signedState.getState().getPlatformState().getAddressBook()));

            final AncientMode ancientMode = platformContext
                    .getConfiguration()
                    .getConfigData(EventConfig.class)
                    .getAncientMode();

            platformWiring.updateEventWindow(new EventWindow(
                    signedState.getRound(),
                    signedState.getState().getPlatformState().getAncientThreshold(),
                    signedState.getState().getPlatformState().getAncientThreshold(),
                    ancientMode));

            final RunningEventHashOverride runningEventHashOverride = new RunningEventHashOverride(
                    signedState.getState().getPlatformState().getLegacyRunningEventHash(), true);
            platformWiring.updateRunningHash(runningEventHashOverride);
            platformWiring.getPcesWriterRegisterDiscontinuityInput().inject(signedState.getRound());

            // Notify any listeners that the reconnect has been completed
            platformWiring
                    .getNotifierWiring()
                    .getInputWire(AppNotifier::sendReconnectCompleteNotification)
                    .put(new ReconnectCompleteNotification(
                            signedState.getRound(),
                            signedState.getConsensusTimestamp(),
                            signedState.getState().getSwirldState()));

        } catch (final RuntimeException e) {
            logger.debug(RECONNECT.getMarker(), "`loadReconnectState` : FAILED, reason: {}", e.getMessage());
            // if the loading fails for whatever reason, we clear all data again in case some of it has been loaded
            platformWiring.clear();
            throw e;
        }
    }

    /**
     * Start this platform.
     */
    @Override
    public void start() {
        logger.info(STARTUP.getMarker(), "Starting platform {}", selfId);
        platformWiring.getModel().preventJvmExit();

        platformContext.getRecycleBin().start();
        platformContext.getMetrics().start();
        platformWiring.start();

        replayPreconsensusEvents();
        platformWiring.startGossip();
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
        platformContext.getRecycleBin().start();
        platformContext.getMetrics().start();
        platformWiring.start();

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
        platformWiring.getStatusActionSubmitter().submitStatusAction(new StartedReplayingEventsAction());

        final IOIterator<GossipEvent> iterator =
                initialPcesFiles.getEventIterator(initialAncientThreshold, startingRound);

        logger.info(
                STARTUP.getMarker(),
                "replaying preconsensus event stream starting at generation {}",
                initialAncientThreshold);

        platformWiring.getPcesReplayerIteratorInput().inject(iterator);

        // We have to wait for all the PCES transactions to reach the ISS detector before telling it that PCES replay is
        // done. The PCES replay will flush the intake pipeline, but we have to flush the hasher

        // FUTURE WORK: These flushes can be done by the PCES replayer.
        platformWiring.flushStateHasher();
        platformWiring.signalEndOfPcesReplay();

        platformWiring
                .getStatusActionSubmitter()
                .submitStatusAction(
                        new DoneReplayingEventsAction(platformContext.getTime().now()));
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
    @NonNull
    public PlatformContext getContext() {
        return platformContext;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public NotificationEngine getNotificationEngine() {
        return notificationEngine;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public Signature sign(@NonNull final byte[] data) {
        return new PlatformSigner(keysAndCerts).sign(data);
    }

    /**
     * Get the Address Book
     *
     * @return AddressBook
     */
    @Override
    @NonNull
    public AddressBook getAddressBook() {
        return currentAddressBook;
    }

    /**
     * {@inheritDoc}
     */
    @SuppressWarnings("unchecked")
    @Override
    @NonNull
    public <T extends SwirldState> AutoCloseableWrapper<T> getLatestImmutableState(@NonNull final String reason) {
        final ReservedSignedState wrapper = latestImmutableStateNexus.getState(reason);
        return wrapper == null
                ? AutoCloseableWrapper.empty()
                : new AutoCloseableWrapper<>((T) wrapper.get().getState().getSwirldState(), wrapper::close);
    }
}
