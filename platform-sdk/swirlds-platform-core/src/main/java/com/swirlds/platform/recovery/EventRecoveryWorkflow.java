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

package com.swirlds.platform.recovery;

import static com.swirlds.common.utility.CommonUtils.throwArgNull;
import static com.swirlds.logging.LogMarker.EXCEPTION;
import static com.swirlds.logging.LogMarker.STARTUP;
import static com.swirlds.platform.util.BootstrapUtils.loadAppMain;
import static com.swirlds.platform.util.BootstrapUtils.setupConstructableRegistry;

import com.swirlds.common.config.ConfigUtils;
import com.swirlds.common.config.ConsensusConfig;
import com.swirlds.common.config.singleton.ConfigurationHolder;
import com.swirlds.common.config.sources.LegacyFileConfigSource;
import com.swirlds.common.config.sources.SimpleConfigSource;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.io.IOIterator;
import com.swirlds.common.merkle.crypto.MerkleCryptoFactory;
import com.swirlds.common.notification.NotificationEngine;
import com.swirlds.common.stream.RunningHashCalculatorForStream;
import com.swirlds.common.system.InitTrigger;
import com.swirlds.common.system.Round;
import com.swirlds.common.system.SwirldDualState;
import com.swirlds.common.system.SwirldMain;
import com.swirlds.common.system.SwirldState2;
import com.swirlds.common.system.events.ConsensusEvent;
import com.swirlds.common.system.state.notifications.NewRecoveredStateListener;
import com.swirlds.common.system.state.notifications.NewRecoveredStateNotification;
import com.swirlds.common.utility.CompareTo;
import com.swirlds.config.api.Configuration;
import com.swirlds.config.api.ConfigurationBuilder;
import com.swirlds.platform.internal.EventImpl;
import com.swirlds.platform.recovery.internal.EventStreamRoundIterator;
import com.swirlds.platform.recovery.internal.RecoveryPlatform;
import com.swirlds.platform.state.EmergencyRecoveryFile;
import com.swirlds.platform.state.MinGenInfo;
import com.swirlds.platform.state.State;
import com.swirlds.platform.state.signed.SignedState;
import com.swirlds.platform.state.signed.SignedStateFileReader;
import com.swirlds.platform.state.signed.SignedStateFileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ExecutionException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * <p>
 * Handles the event stream recovery workflow.
 * </p>
 *
 * <p>
 * Note: this workflow is only compatible with {@link SwirldState2} applications.
 * </p>
 */
public final class EventRecoveryWorkflow {

    private static final Logger logger = LogManager.getLogger(EventRecoveryWorkflow.class);

    public static final long NO_FINAL_ROUND = Long.MAX_VALUE;

    private EventRecoveryWorkflow() {}

    /**
     * Read a signed state from disk and apply events from an event stream on disk. Write the resulting signed state to
     * disk.
     *
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
     */
    public static void recoverState(
            final Path signedStateFile,
            final List<Path> configurationFiles,
            final Path eventStreamDirectory,
            final String mainClassName,
            final Boolean allowPartialRounds,
            final Long finalRound,
            final Path resultingStateDirectory,
            final Long selfId)
            throws IOException {

        setupConstructableRegistry();

        final SwirldMain appMain = loadAppMain(mainClassName);

        if (!Files.exists(resultingStateDirectory)) {
            Files.createDirectories(resultingStateDirectory);
        }

        final ConfigurationBuilder configurationBuilder = ConfigurationBuilder.create();
        ConfigUtils.scanAndRegisterAllConfigTypes(configurationBuilder, "com.swirlds");

        // Recovery workflow doesn't need the metrics output.
        configurationBuilder.withSource(new SimpleConfigSource("disableMetricsOutput", "true"));

        for (final Path configurationFile : configurationFiles) {
            logger.info(STARTUP.getMarker(), "Loading configuration from {}", configurationFile);
            configurationBuilder.withSource(new LegacyFileConfigSource(configurationFile));
        }

        final Configuration configuration = configurationBuilder.build();
        ConfigurationHolder.getInstance().setConfiguration(configuration);

        logger.info(STARTUP.getMarker(), "Loading state from {}", signedStateFile);

        final SignedState initialState =
                SignedStateFileReader.readStateFile(signedStateFile).signedState();

        logger.info(STARTUP.getMarker(), "State from round {} loaded.", initialState.getRound());
        logger.info(STARTUP.getMarker(), "Loading event stream at {}", eventStreamDirectory);

        final IOIterator<Round> roundIterator =
                new EventStreamRoundIterator(eventStreamDirectory, initialState.getRound() + 1, allowPartialRounds);

        logger.info(STARTUP.getMarker(), "Reapplying transactions");

        final SignedState resultingState =
                reapplyTransactions(configuration, initialState, appMain, roundIterator, finalRound, selfId);

        logger.info(
                STARTUP.getMarker(), "Finished reapplying transactions, writing state to {}", resultingStateDirectory);

        SignedStateFileWriter.writeSignedStateFilesToDirectory(resultingStateDirectory, resultingState);

        updateEmergencyRecoveryFile(resultingStateDirectory, initialState.getConsensusTimestamp());

        logger.info(STARTUP.getMarker(), "Recovery process completed");

        resultingState.release();
    }

    /**
     * Update the resulting emergency recovery file to contain the bootstrap timestamp.
     *
     * @param recoveryFileDir the directory containing the emergency recovery file
     * @param bootstrapTime   the consensus timestamp of the bootstrap state
     */
    public static void updateEmergencyRecoveryFile(final Path recoveryFileDir, final Instant bootstrapTime) {
        try {
            // Read the existing recovery file and write it to a backup directory
            final EmergencyRecoveryFile oldRecoveryFile = EmergencyRecoveryFile.read(recoveryFileDir);
            if (oldRecoveryFile == null) {
                logger.error(EXCEPTION.getMarker(),
                        "Recovery file does not exist at {}", recoveryFileDir);
                return;
            }
            final Path backupDir = recoveryFileDir.resolve("backup");
            if (!Files.exists(backupDir)) {
                Files.createDirectory(backupDir);
            }
            oldRecoveryFile.write(backupDir);

            // Create a new recovery file with the bootstrap time, overwriting the original
            final EmergencyRecoveryFile newRecoveryFile = new EmergencyRecoveryFile(
                    oldRecoveryFile.recovery().state(),
                    bootstrapTime);
            newRecoveryFile.write(recoveryFileDir);
        } catch (final IOException e) {
            logger.error(EXCEPTION.getMarker(), "Exception occurred when updating the emergency recovery file");
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
     * @param configuration the configuration for the node
     * @param initialState  the starting signed state
     * @param appMain       the {@link SwirldMain} for the app. Ignored if null.
     * @param roundIterator an iterator that walks over transactions
     * @param finalRound    the last round to apply to the state (inclusive), will stop earlier if the event stream does
     *                      not have events from the final round
     * @param selfId        the self ID of the node
     * @return the resulting signed state
     * @throws IOException if there is a problem reading from the event stream file
     */
    public static SignedState reapplyTransactions(
            final Configuration configuration,
            final SignedState initialState,
            final SwirldMain appMain,
            final IOIterator<Round> roundIterator,
            final long finalRound,
            final long selfId)
            throws IOException {

        throwArgNull(configuration, "configuration");
        throwArgNull(initialState, "initialState");
        throwArgNull(roundIterator, "roundIterator");

        final long roundsNonAncient =
                configuration.getConfigData(ConsensusConfig.class).roundsNonAncient();

        initialState.getState().throwIfImmutable("initial state must be mutable");
        initialState.reserve();

        logger.info(STARTUP.getMarker(), "Initializing application state");

        final RecoveryPlatform platform = new RecoveryPlatform(configuration, initialState, selfId);

        initialState
                .getSwirldState()
                .init(
                        platform,
                        initialState.getState().getSwirldDualState(),
                        InitTrigger.EVENT_STREAM_RECOVERY,
                        initialState
                                .getState()
                                .getPlatformState()
                                .getPlatformData()
                                .getCreationSoftwareVersion());
        initialState.getState().markAsInitialized();

        if (appMain != null) {
            appMain.init(platform, platform.getSelfId());
        }

        SignedState signedState = initialState;

        // Apply events to the state
        while (roundIterator.hasNext()
                && (finalRound == -1 || roundIterator.peek().getRoundNum() <= finalRound)) {
            final Round round = roundIterator.next();

            logger.info(
                    STARTUP.getMarker(),
                    "Applying {} events from round {}",
                    round.getEventCount(),
                    round.getRoundNum());

            signedState = handleNextRound(signedState, round, roundsNonAncient);
            platform.setLatestState(signedState);
        }

        logger.info(STARTUP.getMarker(), "Hashing resulting signed state");
        try {
            MerkleCryptoFactory.getInstance()
                    .digestTreeAsync(signedState.getState())
                    .get();
        } catch (final InterruptedException e) {
            throw new RuntimeException("interrupted while attempting to hash the state", e);
        } catch (final ExecutionException e) {
            throw new RuntimeException(e);
        }
        logger.info(STARTUP.getMarker(), "Hashing complete");

        // Let the application know about the recovered state
        notifyStateRecovered(platform.getNotificationEngine(), signedState);

        platform.close();

        return signedState;
    }

    /**
     * Apply a single round and generate a new state. The previous state is released.
     *
     * @param previousState    the previous round's signed state
     * @param round            the next round
     * @param roundsNonAncient the number of rounds until an event becomes ancient
     * @return the resulting signed state
     */
    private static SignedState handleNextRound(
            final SignedState previousState, final Round round, final long roundsNonAncient) {

        final Instant currentRoundTimestamp = getRoundTimestamp(round);

        previousState.getState().throwIfImmutable();
        final State newState = previousState.getState().copy();
        newState.getPlatformState()
                .getPlatformData()
                .setRound(round.getRoundNum())
                .setNumEventsCons(previousState.getNumEventsCons() + round.getEventCount())
                .setHashEventsCons(getHashEventsCons(previousState.getHashEventsCons(), round))
                .setEvents(collectEventsForRound(roundsNonAncient, previousState.getEvents(), round))
                .setConsensusTimestamp(currentRoundTimestamp)
                .setMinGenInfo(getMinGenInfo(roundsNonAncient, previousState.getMinGenInfo(), round))
                .setCreationSoftwareVersion(previousState
                        .getState()
                        .getPlatformState()
                        .getPlatformData()
                        .getCreationSoftwareVersion());

        applyTransactions(
                previousState.getSwirldState().cast(),
                newState.getSwirldState().cast(),
                newState.getSwirldDualState(),
                round);

        final boolean isFreezeState = isFreezeState(
                previousState.getConsensusTimestamp(),
                currentRoundTimestamp,
                newState.getPlatformDualState().getFreezeTime());
        if (isFreezeState) {
            newState.getPlatformDualState().setLastFrozenTimeToBeCurrentFreezeTime();
        }

        final SignedState signedState = new SignedState(newState, isFreezeState);

        signedState.reserve();
        previousState.release();

        return signedState;
    }

    /**
     * Calculate the running hash at the end of a round.
     *
     * @param previousRunningHash the previous running hash
     * @param round               the current round
     * @return the running event hash at the end of the current round
     */
    static Hash getHashEventsCons(final Hash previousRunningHash, final Round round) {
        final RunningHashCalculatorForStream<EventImpl> hashCalculator = new RunningHashCalculatorForStream<>();
        hashCalculator.setRunningHash(previousRunningHash);

        for (final ConsensusEvent event : round) {
            hashCalculator.addObject((EventImpl) event);
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
        final Iterator<ConsensusEvent> iterator = round.iterator();

        while (iterator.hasNext()) {
            final ConsensusEvent event = iterator.next();

            if (!iterator.hasNext()) {
                return event.getConsensusTimestamp();
            }
        }

        throw new IllegalStateException("round has no events");
    }

    /**
     * Apply the next round of transactions and produce a new swirld state.
     *
     * @param immutableState the immutable swirld state for the previous round
     * @param mutableState   the swirld state for the current round
     * @param dualState      the dual state for the current round
     * @param round          the current round
     */
    static void applyTransactions(
            final SwirldState2 immutableState,
            final SwirldState2 mutableState,
            final SwirldDualState dualState,
            final Round round) {

        mutableState.throwIfImmutable();

        for (final ConsensusEvent event : round) {
            immutableState.preHandle(event);
        }

        mutableState.handleConsensusRound(round, dualState);

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
     * Collect the events that need to go into a signed state using the previous round's state and the new round.
     *
     * @param roundsNonAncient the number of rounds until an event becomes ancient
     * @param previousEvents   the previous round's state
     * @param round            the current round
     * @return an array of all non-ancient events
     */
    static EventImpl[] collectEventsForRound(
            final long roundsNonAncient, final EventImpl[] previousEvents, final Round round) {

        final long firstRoundToKeep = round.getRoundNum() - roundsNonAncient;

        final List<EventImpl> eventList = new ArrayList<>();
        for (final EventImpl event : previousEvents) {
            if (event.getRoundReceived() >= firstRoundToKeep) {
                eventList.add(event);
            }
        }

        for (final ConsensusEvent event : round) {
            eventList.add((EventImpl) event);
        }

        return eventList.toArray(new EventImpl[0]);
    }

    /**
     * <p>
     * Get the minimum generation info for all non-ancient rounds.
     * </p>
     *
     * <p>
     * This implementation differs from what happens in a real platform because the event stream contains insufficient
     * information to fully reconstruct all consensus data. This implementation will reuse the minimum generation info
     * from the previous rounds, and will set the minimum generation for the current round to be equal to the minimum
     * generation of all events in the current round.
     * </p>
     *
     * @param roundsNonAncient   the number of rounds until an event becomes ancient
     * @param previousMinGenInfo the previous round's minimum generation info
     * @param round              the current round
     * @return minimum generation info for the rounds described by the events
     */
    static List<MinGenInfo> getMinGenInfo(
            final long roundsNonAncient, final List<MinGenInfo> previousMinGenInfo, final Round round) {

        final long firstRoundToKeep = round.getRoundNum() - roundsNonAncient;
        final List<MinGenInfo> minGenInfos = new ArrayList<>();

        for (final MinGenInfo minGenInfo : previousMinGenInfo) {
            if (minGenInfo.round() >= firstRoundToKeep) {
                minGenInfos.add(minGenInfo);
            }
        }

        if (!round.isEmpty()) {
            long minimumGeneration = Integer.MAX_VALUE;
            for (final ConsensusEvent event : round) {
                final EventImpl eventImpl = (EventImpl) event;
                minimumGeneration = Math.min(minimumGeneration, eventImpl.getGeneration());
            }
            minGenInfos.add(new MinGenInfo(round.getRoundNum(), minimumGeneration));
        }

        return minGenInfos;
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
