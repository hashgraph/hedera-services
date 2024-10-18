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

package com.swirlds.platform.recovery;

import static com.swirlds.common.io.utility.FileUtils.getAbsolutePath;
import static com.swirlds.logging.legacy.LogMarker.EXCEPTION;
import static com.swirlds.logging.legacy.LogMarker.STARTUP;
import static com.swirlds.platform.builder.PlatformBuildConstants.DEFAULT_CONFIG_FILE_NAME;
import static com.swirlds.platform.util.BootstrapUtils.loadAppMain;
import static com.swirlds.platform.util.BootstrapUtils.setupConstructableRegistry;

import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.io.IOIterator;
import com.swirlds.common.merkle.crypto.MerkleCryptoFactory;
import com.swirlds.common.notification.NotificationEngine;
import com.swirlds.common.platform.NodeId;
import com.swirlds.common.stream.RunningHashCalculatorForStream;
import com.swirlds.common.utility.CompareTo;
import com.swirlds.config.api.Configuration;
import com.swirlds.config.api.ConfigurationBuilder;
import com.swirlds.platform.ApplicationDefinition;
import com.swirlds.platform.ApplicationDefinitionLoader;
import com.swirlds.platform.ParameterProvider;
import com.swirlds.platform.config.PathsConfig;
import com.swirlds.platform.config.StateConfig;
import com.swirlds.platform.consensus.ConsensusConfig;
import com.swirlds.platform.consensus.SyntheticSnapshot;
import com.swirlds.platform.crypto.CryptoStatic;
import com.swirlds.platform.event.PlatformEvent;
import com.swirlds.platform.event.hashing.DefaultEventHasher;
import com.swirlds.platform.event.preconsensus.PcesFile;
import com.swirlds.platform.event.preconsensus.PcesMutableFile;
import com.swirlds.platform.eventhandling.EventConfig;
import com.swirlds.platform.recovery.emergencyfile.EmergencyRecoveryFile;
import com.swirlds.platform.recovery.internal.EventStreamRoundIterator;
import com.swirlds.platform.recovery.internal.RecoveredState;
import com.swirlds.platform.recovery.internal.RecoveryPlatform;
import com.swirlds.platform.recovery.internal.StreamedRound;
import com.swirlds.platform.state.MerkleRoot;
import com.swirlds.platform.state.PlatformStateAccessor;
import com.swirlds.platform.state.PlatformStateModifier;
import com.swirlds.platform.state.signed.ReservedSignedState;
import com.swirlds.platform.state.signed.SignedState;
import com.swirlds.platform.state.snapshot.SignedStateFileReader;
import com.swirlds.platform.state.snapshot.SignedStateFileUtils;
import com.swirlds.platform.state.snapshot.SignedStateFileWriter;
import com.swirlds.platform.system.InitTrigger;
import com.swirlds.platform.system.Round;
import com.swirlds.platform.system.StaticSoftwareVersion;
import com.swirlds.platform.system.SwirldMain;
import com.swirlds.platform.system.SwirldState;
import com.swirlds.platform.system.events.CesEvent;
import com.swirlds.platform.system.events.ConsensusEvent;
import com.swirlds.platform.system.state.notifications.NewRecoveredStateListener;
import com.swirlds.platform.system.state.notifications.NewRecoveredStateNotification;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Handles the event stream recovery workflow.
 */
public final class EventRecoveryWorkflow {

    private static final Logger logger = LogManager.getLogger(EventRecoveryWorkflow.class);

    public static final long NO_FINAL_ROUND = Long.MAX_VALUE;

    private EventRecoveryWorkflow() {}

    /**
     * Read a signed state from disk and apply events from an event stream on disk. Write the resulting signed state to
     * disk.
     *
     * @param platformContext         the platform context
     * @param signedStateFile         the bootstrap signed state file
     * @param configurationFiles      files containing configuration
     * @param eventStreamDirectory    a directory containing the event stream
     * @param mainClassName           the fully qualified class name of the {@link SwirldMain} for the app
     * @param finalRound              if not {@link #NO_FINAL_ROUND} then stop reapplying events after this round has
     *                                been generated
     * @param resultingStateDirectory the location where the resulting state will be written
     * @param selfId                  the self ID of the node
     * @param allowPartialRounds      if true then allow the last round to be missing events, if false then ignore the
     *                                last round if it does not have all of its events
     * @param loadSigningKeys         if true then load the signing keys
     */
    public static void recoverState(
            @NonNull final PlatformContext platformContext,
            @NonNull final Path signedStateFile,
            @NonNull final List<Path> configurationFiles,
            @NonNull final Path eventStreamDirectory,
            @NonNull final String mainClassName,
            @NonNull final Boolean allowPartialRounds,
            @NonNull final Long finalRound,
            @NonNull final Path resultingStateDirectory,
            @NonNull final NodeId selfId,
            final boolean loadSigningKeys)
            throws IOException {
        Objects.requireNonNull(platformContext);
        Objects.requireNonNull(signedStateFile, "signedStateFile must not be null");
        Objects.requireNonNull(configurationFiles, "configurationFiles must not be null");
        Objects.requireNonNull(eventStreamDirectory, "eventStreamDirectory must not be null");
        Objects.requireNonNull(mainClassName, "mainClassName must not be null");
        Objects.requireNonNull(allowPartialRounds, "allowPartialRounds must not be null");
        Objects.requireNonNull(finalRound, "finalRound must not be null");
        Objects.requireNonNull(resultingStateDirectory, "resultingStateDirectory must not be null");
        Objects.requireNonNull(selfId, "selfId must not be null");

        setupConstructableRegistry();

        final PathsConfig defaultPathsConfig = ConfigurationBuilder.create()
                .withConfigDataType(PathsConfig.class)
                .build()
                .getConfigData(PathsConfig.class);

        // parameters if the app needs them
        final ApplicationDefinition appDefinition =
                ApplicationDefinitionLoader.loadDefault(defaultPathsConfig, getAbsolutePath(DEFAULT_CONFIG_FILE_NAME));
        ParameterProvider.getInstance().setParameters(appDefinition.getAppParameters());

        final SwirldMain appMain = loadAppMain(mainClassName);

        if (!Files.exists(resultingStateDirectory)) {
            Files.createDirectories(resultingStateDirectory);
        }

        logger.info(STARTUP.getMarker(), "Loading state from {}", signedStateFile);

        try (final ReservedSignedState initialState = SignedStateFileReader.readStateFile(
                        platformContext, signedStateFile, SignedStateFileUtils::readState)
                .reservedSignedState()) {
            StaticSoftwareVersion.setSoftwareVersion(
                    initialState.get().getState().getReadablePlatformState().getCreationSoftwareVersion());

            logger.info(
                    STARTUP.getMarker(),
                    "State from round {} loaded.",
                    initialState.get().getRound());
            logger.info(STARTUP.getMarker(), "Loading event stream at {}", eventStreamDirectory);

            final IOIterator<StreamedRound> roundIterator = new EventStreamRoundIterator(
                    initialState.get().getAddressBook(),
                    eventStreamDirectory,
                    initialState.get().getRound() + 1,
                    allowPartialRounds);

            logger.info(STARTUP.getMarker(), "Reapplying transactions");

            final RecoveredState recoveredState = reapplyTransactions(
                    platformContext,
                    initialState.getAndReserve("recoverState()"),
                    appMain,
                    roundIterator,
                    finalRound,
                    selfId,
                    loadSigningKeys);

            logger.info(
                    STARTUP.getMarker(),
                    "Finished reapplying transactions, writing state to {}",
                    resultingStateDirectory);

            // Make one more copy to force the state in recoveredState to be immutable.
            final MerkleRoot mutableStateCopy =
                    recoveredState.state().get().getState().copy();

            SignedStateFileWriter.writeSignedStateFilesToDirectory(
                    platformContext,
                    selfId,
                    resultingStateDirectory,
                    recoveredState.state().get());
            final StateConfig stateConfig = platformContext.getConfiguration().getConfigData(StateConfig.class);
            updateEmergencyRecoveryFile(
                    stateConfig, resultingStateDirectory, initialState.get().getConsensusTimestamp());

            logger.info(STARTUP.getMarker(), "Signed state written to disk");

            final PcesFile preconsensusEventFile = PcesFile.of(
                    platformContext
                            .getConfiguration()
                            .getConfigData(EventConfig.class)
                            .getAncientMode(),
                    Instant.now(),
                    0,
                    recoveredState.judge().getGeneration(),
                    recoveredState.judge().getGeneration(),
                    recoveredState.state().get().getRound(),
                    resultingStateDirectory);
            final PcesMutableFile mutableFile = preconsensusEventFile.getMutableFile();
            mutableFile.writeEvent(recoveredState.judge());
            mutableFile.close();

            recoveredState.state().close();
            mutableStateCopy.release();

            logger.info(STARTUP.getMarker(), "Recovery process completed");
        }
    }

    /**
     * Update the resulting emergency recovery file to contain the bootstrap timestamp.
     *
     * @param stateConfig     the state configuration for the platform
     * @param recoveryFileDir the directory containing the emergency recovery file
     * @param bootstrapTime   the consensus timestamp of the bootstrap state
     */
    public static void updateEmergencyRecoveryFile(
            @NonNull final StateConfig stateConfig,
            @NonNull final Path recoveryFileDir,
            @NonNull final Instant bootstrapTime) {
        try {
            // Read the existing recovery file and write it to a backup directory
            final EmergencyRecoveryFile oldRecoveryFile = EmergencyRecoveryFile.read(stateConfig, recoveryFileDir);
            if (oldRecoveryFile == null) {
                logger.error(EXCEPTION.getMarker(), "Recovery file does not exist at {}", recoveryFileDir);
                return;
            }
            final Path backupDir = recoveryFileDir.resolve("backup");
            if (!Files.exists(backupDir)) {
                Files.createDirectories(backupDir);
            }
            oldRecoveryFile.write(backupDir);

            // Create a new recovery file with the bootstrap time, overwriting the original
            final EmergencyRecoveryFile newRecoveryFile =
                    new EmergencyRecoveryFile(oldRecoveryFile.recovery().state(), bootstrapTime);
            newRecoveryFile.write(recoveryFileDir);
        } catch (final IOException e) {
            logger.error(
                    EXCEPTION.getMarker(),
                    "Exception occurred when updating the emergency recovery file with the bootstrap time");
        }
    }

    /**
     * Send a notification that the recovered state has been calculated.
     *
     * @param notificationEngine the notification engine used to dispatch the notification
     * @param recoveredState     the recovered state
     */
    private static void notifyStateRecovered(
            final NotificationEngine notificationEngine, final SignedState recoveredState) {
        final NewRecoveredStateNotification notification = new NewRecoveredStateNotification(
                recoveredState.getSwirldState(), recoveredState.getRound(), recoveredState.getConsensusTimestamp());
        notificationEngine.dispatch(NewRecoveredStateListener.class, notification);
    }

    /**
     * Apply transactions on top of a state to produce a new state
     *
     * @param platformContext the platform context
     * @param initialState    the starting signed state
     * @param appMain         the {@link SwirldMain} for the app. Ignored if null.
     * @param roundIterator   an iterator that walks over transactions
     * @param finalRound      the last round to apply to the state (inclusive), will stop earlier if the event stream
     *                        does not have events from the final round
     * @param selfId          the self ID of the node
     * @param loadSigningKeys if true then load the signing keys
     * @return the resulting signed state
     * @throws IOException if there is a problem reading from the event stream file
     */
    @NonNull
    public static RecoveredState reapplyTransactions(
            @NonNull final PlatformContext platformContext,
            @NonNull final ReservedSignedState initialState,
            @NonNull final SwirldMain appMain,
            @NonNull final IOIterator<StreamedRound> roundIterator,
            final long finalRound,
            @NonNull final NodeId selfId,
            final boolean loadSigningKeys)
            throws IOException {

        Objects.requireNonNull(platformContext, "platformContext must not be null");
        Objects.requireNonNull(initialState, "initialState must not be null");
        Objects.requireNonNull(appMain, "appMain must not be null");
        Objects.requireNonNull(roundIterator, "roundIterator must not be null");
        Objects.requireNonNull(selfId, "selfId must not be null");

        final Configuration configuration = platformContext.getConfiguration();

        initialState.get().getState().throwIfImmutable("initial state must be mutable");

        logger.info(STARTUP.getMarker(), "Initializing application state");

        final RecoveryPlatform platform =
                new RecoveryPlatform(configuration, initialState.get(), selfId, loadSigningKeys);

        initialState
                .get()
                .getSwirldState()
                .init(
                        platform,
                        InitTrigger.EVENT_STREAM_RECOVERY,
                        initialState.get().getState().getReadablePlatformState().getCreationSoftwareVersion());

        appMain.init(platform, platform.getSelfId());

        ReservedSignedState signedState = initialState;

        // Apply events to the state
        ConsensusEvent lastEvent = null;
        while (roundIterator.hasNext()
                && (finalRound == -1 || roundIterator.peek().getRoundNum() <= finalRound)) {
            final StreamedRound round = roundIterator.next();

            logger.info(
                    STARTUP.getMarker(),
                    "Applying {} events from round {}",
                    round.getEventCount(),
                    round.getRoundNum());

            signedState = handleNextRound(
                    platformContext, signedState, round, configuration.getConfigData(ConsensusConfig.class));
            platform.setLatestState(signedState.get());
            lastEvent = getLastEvent(round);
        }

        logger.info(STARTUP.getMarker(), "Hashing resulting signed state");
        try {
            MerkleCryptoFactory.getInstance()
                    .digestTreeAsync(signedState.get().getState())
                    .get();
        } catch (final InterruptedException e) {
            throw new RuntimeException("interrupted while attempting to hash the state", e);
        } catch (final ExecutionException e) {
            throw new RuntimeException(e);
        }
        logger.info(STARTUP.getMarker(), "Hashing complete");

        // Let the application know about the recovered state
        notifyStateRecovered(platform.getNotificationEngine(), signedState.get());

        platform.close();

        return new RecoveredState(signedState, ((CesEvent) Objects.requireNonNull(lastEvent)).getPlatformEvent());
    }

    /**
     * Apply a single round and generate a new state. The previous state is released.
     *
     * @param platformContext the current context
     * @param previousState   the previous round's signed state
     * @param round           the next round
     * @param config          the consensus configuration
     * @return the resulting signed state
     */
    private static ReservedSignedState handleNextRound(
            @NonNull final PlatformContext platformContext,
            @NonNull final ReservedSignedState previousState,
            @NonNull final StreamedRound round,
            @NonNull final ConsensusConfig config) {

        final Instant currentRoundTimestamp = getRoundTimestamp(round);
        previousState.get().getState().throwIfImmutable();
        final MerkleRoot newState = previousState.get().getState().copy();
        final PlatformEvent lastEvent = ((CesEvent) getLastEvent(round)).getPlatformEvent();
        new DefaultEventHasher().hashEvent(lastEvent);

        final PlatformStateAccessor newReadablePlatformState = newState.getReadablePlatformState();
        final PlatformStateModifier newWritablePlatformState = newState.getWritablePlatformState();
        final PlatformStateAccessor previousReadablePlatformState =
                previousState.get().getState().getReadablePlatformState();

        newWritablePlatformState.bulkUpdate(v -> {
            v.setRound(round.getRoundNum());
            v.setLegacyRunningEventHash(
                    getHashEventsCons(previousReadablePlatformState.getLegacyRunningEventHash(), round));
            v.setConsensusTimestamp(currentRoundTimestamp);
            v.setSnapshot(SyntheticSnapshot.generateSyntheticSnapshot(
                    round.getRoundNum(), lastEvent.getConsensusOrder(), currentRoundTimestamp, config, lastEvent));
            v.setCreationSoftwareVersion(previousReadablePlatformState.getCreationSoftwareVersion());
        });

        applyTransactions(
                previousState.get().getSwirldState().cast(),
                newState.getSwirldState().cast(),
                newState.getWritablePlatformState(),
                round);

        final boolean isFreezeState = isFreezeState(
                previousState.get().getConsensusTimestamp(),
                currentRoundTimestamp,
                newReadablePlatformState.getFreezeTime());
        if (isFreezeState) {
            newWritablePlatformState.setLastFrozenTime(newReadablePlatformState.getFreezeTime());
        }

        final ReservedSignedState signedState = new SignedState(
                        platformContext,
                        CryptoStatic::verifySignature,
                        newState,
                        "EventRecoveryWorkflow.handleNextRound()",
                        isFreezeState,
                        false,
                        false)
                .reserve("recovery");
        previousState.close();

        return signedState;
    }

    /**
     * Calculate the running hash at the end of a round.
     *
     * @param previousRunningHash the previous running hash
     * @param round               the current round
     * @return the running event hash at the end of the current round
     */
    static Hash getHashEventsCons(final Hash previousRunningHash, final StreamedRound round) {
        final RunningHashCalculatorForStream<CesEvent> hashCalculator = new RunningHashCalculatorForStream<>();
        hashCalculator.setRunningHash(previousRunningHash);

        for (final ConsensusEvent event : round) {
            hashCalculator.addObject((CesEvent) event);
        }

        final Hash runningHash = hashCalculator.getRunningHash();
        hashCalculator.close();

        return runningHash;
    }

    /**
     * Get the timestamp for a round (equivalent to the timestamp of the last event).
     *
     * @param round the current round
     * @return the round's timestamp
     */
    static Instant getRoundTimestamp(final Round round) {
        return getLastEvent(round).getConsensusTimestamp();
    }

    static ConsensusEvent getLastEvent(final Round round) {
        final Iterator<ConsensusEvent> iterator = round.iterator();

        while (iterator.hasNext()) {
            final ConsensusEvent event = iterator.next();

            if (!iterator.hasNext()) {
                return event;
            }
        }

        throw new IllegalStateException("round has no events");
    }

    /**
     * Apply the next round of transactions and produce a new swirld state.
     *
     * @param immutableState the immutable swirld state for the previous round
     * @param mutableState   the swirld state for the current round
     * @param platformState  the platform state for the current round
     * @param round          the current round
     */
    static void applyTransactions(
            final SwirldState immutableState,
            final SwirldState mutableState,
            final PlatformStateModifier platformState,
            final Round round) {

        mutableState.throwIfImmutable();

        for (final ConsensusEvent event : round) {
            immutableState.preHandle(event);
        }

        mutableState.handleConsensusRound(round, platformState);

        // FUTURE WORK: there are currently no system transactions that are capable of modifying
        //  the state. If/when system transactions capable of modifying state are added, this workflow
        //  must be updated to reflect that behavior.
    }

    /**
     * Check if this state should be a freeze state.
     *
     * @param previousRoundTimestamp the timestamp of the previous round
     * @param currentRoundTimestamp  the timestamp of the current round
     * @param freezeTime             the freeze time in the state
     * @return true if this round will create a freeze state
     */
    static boolean isFreezeState(
            final Instant previousRoundTimestamp, final Instant currentRoundTimestamp, final Instant freezeTime) {

        if (freezeTime == null) {
            return false;
        }

        return CompareTo.isLessThan(previousRoundTimestamp, freezeTime)
                && CompareTo.isGreaterThanOrEqualTo(currentRoundTimestamp, freezeTime);
    }

    /**
     * Repair an event stream. A damaged event stream might be created when a network is abruptly shut down in the
     * middle of writing an event stream file. After the repair has been completed, the new event stream will contain
     * only events up to the specified event, and the fill will be terminated with a hash that matches the data that
     * ends up in the resulting stream.
     *
     * @param eventStreamDirectory         the original event stream directory
     * @param repairedEventStreamDirectory the location where the repaired event stream will be written
     * @param finalEventRound              the round of the final event that should be included in the event stream
     * @param finalEventHash               the hash of the final event that should be included in the event stream
     */
    public static void repairEventStream(
            final Path eventStreamDirectory,
            final Path repairedEventStreamDirectory,
            final long finalEventRound,
            final Hash finalEventHash) {

        // FUTURE WORK https://github.com/swirlds/swirlds-platform/issues/6235

    }
}
