/*
 * Copyright (C) 2018-2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform.state.signed;

import static com.swirlds.common.io.utility.FileUtils.deleteDirectoryAndLog;
import static com.swirlds.common.system.UptimeData.NO_ROUND;
import static com.swirlds.logging.legacy.LogMarker.EXCEPTION;
import static com.swirlds.logging.legacy.LogMarker.STATE_TO_DISK;
import static com.swirlds.platform.SwirldsPlatform.PLATFORM_THREAD_POOL_NAME;
import static com.swirlds.platform.state.signed.SignedStateFileReader.getSavedStateFiles;
import static com.swirlds.platform.state.signed.SignedStateFileUtils.getSignedStateDirectory;
import static com.swirlds.platform.state.signed.SignedStateFileUtils.getSignedStatesBaseDirectory;
import static com.swirlds.platform.state.signed.StateToDiskReason.FIRST_ROUND_AFTER_GENESIS;
import static com.swirlds.platform.state.signed.StateToDiskReason.FREEZE_STATE;
import static com.swirlds.platform.state.signed.StateToDiskReason.PERIODIC_SNAPSHOT;
import static com.swirlds.platform.state.signed.StateToDiskReason.RECONNECT;

import com.swirlds.base.state.Startable;
import com.swirlds.base.time.Time;
import com.swirlds.common.config.StateConfig;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.system.NodeId;
import com.swirlds.common.system.status.StatusActionSubmitter;
import com.swirlds.common.system.status.actions.StateWrittenToDiskAction;
import com.swirlds.common.threading.framework.QueueThread;
import com.swirlds.common.threading.framework.config.QueueThreadConfiguration;
import com.swirlds.common.threading.interrupt.Uninterruptable;
import com.swirlds.common.threading.manager.ThreadManager;
import com.swirlds.config.api.Configuration;
import com.swirlds.logging.legacy.payload.InsufficientSignaturesPayload;
import com.swirlds.platform.components.state.output.MinimumGenerationNonAncientConsumer;
import com.swirlds.platform.components.state.output.StateToDiskAttemptConsumer;
import com.swirlds.platform.config.ThreadConfig;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * This class is responsible for managing the signed state writing pipeline.
 */
public class SignedStateFileManager implements Startable {

    private static final Logger logger = LogManager.getLogger(SignedStateFileManager.class);

    /**
     * A consumer of data when a state is written to disk
     */
    private final StateToDiskAttemptConsumer stateToDiskAttemptConsumer;

    /**
     * The timestamp of the signed state that was most recently written to disk, or null if no timestamp was recently
     * written to disk.
     */
    private Instant previousSavedStateTimestamp;

    /**
     * The ID of this node.
     */
    private final NodeId selfId;

    /**
     * The name of the application that is currently running.
     */
    private final String mainClassName;

    /**
     * The swirld name.
     */
    private final String swirldName;

    /**
     * A background queue of tasks.
     */
    private final QueueThread<Runnable> taskQueue;

    /**
     * Metrics provider
     */
    private final SignedStateMetrics metrics;

    private final Configuration configuration;

    /**
     * Provides system time
     */
    private final Time time;

    /**
     * Enables submitting platform status actions
     */
    private final StatusActionSubmitter statusActionSubmitter;

    /**
     * The minimum generation of non-ancient events for the oldest state snapshot on disk.
     */
    private long minimumGenerationNonAncientForOldestState = -1;

    /**
     * This method must be called when the minimum generation non-ancient of the oldest state snapshot on disk changes.
     */
    private final MinimumGenerationNonAncientConsumer minimumGenerationNonAncientConsumer;

    /**
     * The round number of the latest saved state, or {@link com.swirlds.common.system.UptimeData#NO_ROUND} if no state
     * has been saved since booting up.
     */
    private final AtomicLong latestSavedStateRound = new AtomicLong(NO_ROUND);

    /**
     * Creates a new instance.
     *
     * @param context                             the platform context
     * @param threadManager                       responsible for creating and managing threads
     * @param metrics                             metrics provider
     * @param time                                provides time
     * @param mainClassName                       the main class name of this node
     * @param selfId                              the ID of this node
     * @param swirldName                          the name of the swirld
     * @param stateToDiskAttemptConsumer          a consumer of data when a state is written to disk
     * @param minimumGenerationNonAncientConsumer this method must be called when the minimum generation non-ancient
     * @param statusActionSubmitter               enables submitting platform status actions
     */
    public SignedStateFileManager(
            @NonNull final PlatformContext context,
            @NonNull final ThreadManager threadManager,
            @NonNull final SignedStateMetrics metrics,
            @NonNull final Time time,
            @NonNull final String mainClassName,
            @NonNull final NodeId selfId,
            @NonNull final String swirldName,
            @NonNull final StateToDiskAttemptConsumer stateToDiskAttemptConsumer,
            @NonNull final MinimumGenerationNonAncientConsumer minimumGenerationNonAncientConsumer,
            @NonNull final StatusActionSubmitter statusActionSubmitter) {

        this.metrics = Objects.requireNonNull(metrics, "metrics must not be null");
        this.time = time;
        this.selfId = selfId;
        this.mainClassName = mainClassName;
        this.swirldName = swirldName;
        this.stateToDiskAttemptConsumer = stateToDiskAttemptConsumer;
        this.configuration = Objects.requireNonNull(context).getConfiguration();
        this.minimumGenerationNonAncientConsumer = Objects.requireNonNull(
                minimumGenerationNonAncientConsumer, "minimumGenerationNonAncientConsumer must not be null");
        this.statusActionSubmitter = Objects.requireNonNull(statusActionSubmitter);

        final ThreadConfig threadConfig = configuration.getConfigData(ThreadConfig.class);

        final StateConfig stateConfig = configuration.getConfigData(StateConfig.class);
        this.taskQueue = new QueueThreadConfiguration<Runnable>(threadManager)
                .setCapacity(stateConfig.stateSavingQueueSize())
                .setMaxBufferSize(1)
                .setPriority(threadConfig.threadPriorityNonSync())
                .setNodeId(selfId)
                .setComponent(PLATFORM_THREAD_POOL_NAME)
                .setThreadName("signed-state-file-manager")
                .setHandler(Runnable::run)
                .build();

        final List<SavedStateInfo> savedStates = getSavedStateFiles(mainClassName, selfId, swirldName);
        if (!savedStates.isEmpty()) {
            minimumGenerationNonAncientForOldestState =
                    savedStates.get(savedStates.size() - 1).metadata().minimumGenerationNonAncient();
            minimumGenerationNonAncientConsumer.newMinimumGenerationNonAncient(
                    minimumGenerationNonAncientForOldestState);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void start() {
        taskQueue.start();
    }

    /**
     * Stops the background thread.
     * <p>
     * <strong>For unit testing purposes only.</strong>
     */
    public void stop() {
        taskQueue.stop();
    }

    /**
     * Get the number of enqueued state saving tasks. The number returned here will not reflect a state saving task that
     * is currently in progress.
     */
    public int getTaskQueueSize() {
        return taskQueue.size();
    }

    /**
     * Method to be called when a state has been successfully written to disk in-band. An "in-band" write is part of
     * normal platform operations, whereas an out-of-band write is triggered due to a fault, or for debug purposes.
     * <p>
     * This method shouldn't be called if the state was written out-of-band.
     *
     * @param reservedState the state that was written to disk
     * @param directory     the directory where the state was written
     * @param start         the nano start time of the state writing process
     */
    private void stateWrittenToDiskInBand(
            @NonNull final SignedState reservedState, @NonNull final Path directory, final long start) {

        final long round = reservedState.getRound();
        if (round > latestSavedStateRound.get()) {
            latestSavedStateRound.set(round);
        }

        metrics.getWriteStateToDiskTimeMetric().update(TimeUnit.NANOSECONDS.toMillis(time.nanoTime() - start));
        statusActionSubmitter.submitStatusAction(new StateWrittenToDiskAction(round));
        stateToDiskAttemptConsumer.stateToDiskAttempt(reservedState, directory, true);

        reservedState.stateSavedToDisk();
    }

    /**
     * Method to be called when a state is being written to disk in-band, but it lacks signatures.
     * <p>
     * This method shouldn't be called if the state was written out-of-band.
     *
     * @param reservedState the state being written to disk
     */
    private void stateLacksSignatures(@NonNull final SignedState reservedState) {
        metrics.getTotalUnsignedDiskStatesMetric().increment();
        final long newCount = metrics.getTotalUnsignedDiskStatesMetric().get();

        logger.error(
                EXCEPTION.getMarker(),
                new InsufficientSignaturesPayload(("State written to disk for round %d did not have enough signatures. "
                                + "Collected signatures representing %d/%d weight. "
                                + "Total unsigned disk states so far: %d.")
                        .formatted(
                                reservedState.getRound(),
                                reservedState.getSigningWeight(),
                                reservedState.getAddressBook().getTotalWeight(),
                                newCount)));
    }

    /**
     * A save state task to be offered to the {@link #taskQueue}
     *
     * @param reservedSignedState  the reserved signed state to be written to disk
     * @param directory            the directory where the signed state will be written
     * @param reason               the reason this state is being written to disk
     * @param finishedCallback     a function that is called after state writing is complete
     * @param outOfBand            whether this state has been requested to be written out-of-band
     * @param stateLacksSignatures whether the state being written lacks signatures
     */
    private void saveStateTask(
            @NonNull final ReservedSignedState reservedSignedState,
            @NonNull final Path directory,
            @Nullable final StateToDiskReason reason,
            @Nullable final Consumer<Boolean> finishedCallback,
            final boolean outOfBand,
            final boolean stateLacksSignatures) {

        final long start = time.nanoTime();
        boolean success = false;

        try (reservedSignedState) {
            try {
                if (outOfBand) {
                    // states requested to be written out-of-band are always written to disk
                    SignedStateFileWriter.writeSignedStateToDisk(
                            selfId, directory, reservedSignedState.get(), reason, configuration);

                    success = true;
                } else {
                    if (reservedSignedState.get().hasStateBeenSavedToDisk()) {
                        logger.info(
                                STATE_TO_DISK.getMarker(),
                                "Not saving signed state for round {} to disk because it has already been saved.",
                                reservedSignedState.get().getRound());
                    } else {
                        if (stateLacksSignatures) {
                            stateLacksSignatures(reservedSignedState.get());
                        }

                        SignedStateFileWriter.writeSignedStateToDisk(
                                selfId, directory, reservedSignedState.get(), reason, configuration);
                        stateWrittenToDiskInBand(reservedSignedState.get(), directory, start);

                        success = true;
                    }
                }
            } catch (final Throwable e) {
                stateToDiskAttemptConsumer.stateToDiskAttempt(reservedSignedState.get(), directory, false);
                logger.error(
                        EXCEPTION.getMarker(),
                        "Unable to write signed state to disk for round {} to {}.",
                        reservedSignedState.get().getRound(),
                        directory,
                        e);
            } finally {
                if (finishedCallback != null) {
                    finishedCallback.accept(success);
                }
                metrics.getStateToDiskTimeMetric().update(TimeUnit.NANOSECONDS.toMillis(time.nanoTime() - start));
            }
        }
    }

    /**
     * Adds a state save task to a task queue, so that the state will eventually be written to disk.
     * <p>
     * This method will take a reservation on the signed state, and will eventually release that reservation when the
     * state has been fully written to disk (or if state saving fails).
     *
     * @param signedState          the signed state to be written
     * @param directory            the directory where the signed state will be written
     * @param reason               the reason this state is being written to disk
     * @param finishedCallback     a function that is called after state writing is complete. Is passed true if writing
     *                             succeeded, else is passed false.
     * @param outOfBand            true if this state is being written out-of-band, false otherwise
     * @param stateLacksSignatures true if the state lacks signatures, false otherwise
     * @param configuration        the configuration of the platform
     * @return true if it will be written to disk, false otherwise
     */
    private boolean saveSignedStateToDisk(
            @NonNull SignedState signedState,
            @NonNull final Path directory,
            @Nullable final StateToDiskReason reason,
            @Nullable final Consumer<Boolean> finishedCallback,
            final boolean outOfBand,
            final boolean stateLacksSignatures,
            @NonNull final Configuration configuration) {

        Objects.requireNonNull(directory);
        Objects.requireNonNull(configuration);

        final ReservedSignedState reservedSignedState =
                signedState.reserve("SignedStateFileManager.saveSignedStateToDisk()");

        final boolean accepted = taskQueue.offer(() -> saveStateTask(
                reservedSignedState, directory, reason, finishedCallback, outOfBand, stateLacksSignatures));

        if (!accepted) {
            if (finishedCallback != null) {
                finishedCallback.accept(false);
            }

            logger.error(
                    STATE_TO_DISK.getMarker(),
                    "Unable to save signed state to disk for round {} due to backlog of "
                            + "operations in the SignedStateManager task queue.",
                    reservedSignedState.get().getRound());

            reservedSignedState.close();
        }

        return accepted;
    }

    /**
     * Save a signed state to disk.
     * <p>
     * This method will be called periodically under standard operations, and should not be used to write arbitrary
     * states to disk. To write arbitrary states to disk out-of-band, use {@link #dumpState}
     *
     * @param signedState          the signed state to be written to disk.
     * @param stateLacksSignatures true if the state lacks signatures, false otherwise
     * @return true if the state will be written to disk, false otherwise
     */
    public boolean saveSignedStateToDisk(@NonNull final SignedState signedState, final boolean stateLacksSignatures) {
        Objects.requireNonNull(signedState);

        return saveSignedStateToDisk(
                signedState,
                getSignedStateDir(signedState.getRound()),
                signedState.getStateToDiskReason(),
                success -> {
                    if (success) {
                        deleteOldStates();
                    }
                },
                false,
                stateLacksSignatures,
                configuration);
    }

    /**
     * Dump a state to disk out-of-band.
     * <p>
     * Writing a state "out-of-band" means the state is being written for the sake of a human, whether for debug
     * purposes, or because of a fault. States written out-of-band will not be read automatically by the platform,
     * and will not be used as an initial state at boot time.
     * <p>
     * A dumped state will be saved in a subdirectory of the signed states base directory, with the subdirectory being
     * named after the reason the state is being written out-of-band.
     *
     * @param signedState the signed state to write to disk
     * @param reason      the reason why the state is being written out-of-band
     * @param blocking    if true then block until the state has been fully written to disk
     */
    public void dumpState(
            @NonNull final SignedState signedState, @NonNull final StateToDiskReason reason, final boolean blocking) {

        Objects.requireNonNull(signedState);
        Objects.requireNonNull(reason);

        final CountDownLatch latch = new CountDownLatch(1);

        saveSignedStateToDisk(
                signedState,
                getSignedStatesBaseDirectory()
                        .resolve(reason.getDescription())
                        .resolve(String.format("node%d_round%d", selfId.id(), signedState.getRound())),
                reason,
                success -> latch.countDown(),
                true,
                // this value will be ignored, since this is an out-of-band write
                false,
                configuration);

        if (blocking) {
            Uninterruptable.abortAndLogIfInterrupted(
                    latch::await,
                    "interrupted while waiting for state dump to complete, state dump may not be completed");
        }
    }

    /**
     * Get the directory for a particular signed state. This directory might not exist
     *
     * @param round the round number for the signed state
     * @return the File that represents the directory of the signed state for the particular round
     */
    private Path getSignedStateDir(final long round) {
        return getSignedStateDirectory(mainClassName, selfId, swirldName, round);
    }

    /**
     * Determines whether a signed state should eventually be written to disk
     * <p>
     * If it is determined that the state should be written to disk, this method returns the reason why
     * <p>
     * If it is determined that the state shouldn't be written to disk, then this method returns null
     *
     * @param signedState       the state in question
     * @param previousTimestamp the timestamp of the previous state that was saved to disk, or null if no previous state
     *                          was saved to disk
     * @param source            the source of the signed state
     * @return the reason why the state should be written to disk, or null if it shouldn't be written to disk
     */
    @Nullable
    private StateToDiskReason shouldSaveToDisk(
            @NonNull final SignedState signedState,
            @Nullable final Instant previousTimestamp,
            @NonNull final SourceOfSignedState source) {

        if (signedState.isFreezeState()) {
            // the state right before a freeze should be written to disk
            return FREEZE_STATE;
        }

        if (source == SourceOfSignedState.RECONNECT) {
            return RECONNECT;
        }

        final StateConfig stateConfig = configuration.getConfigData(StateConfig.class);
        final int saveStatePeriod = stateConfig.saveStatePeriod();
        if (saveStatePeriod <= 0) {
            // periodic state saving is disabled
            return null;
        }

        // FUTURE WORK: writing genesis state to disk is currently disabled if the saveStatePeriod is 0.
        // This is for testing purposes, to have a method of disabling state saving for tests.
        // Once a feature to disable all state saving has been added, this block should be moved in front of the
        // saveStatePeriod <=0 block, so that saveStatePeriod doesn't impact the saving of genesis state.
        if (previousTimestamp == null) {
            // the first round should be saved
            return FIRST_ROUND_AFTER_GENESIS;
        }

        if ((signedState.getConsensusTimestamp().getEpochSecond() / saveStatePeriod)
                > (previousTimestamp.getEpochSecond() / saveStatePeriod)) {
            return PERIODIC_SNAPSHOT;
        } else {
            // the period hasn't yet elapsed
            return null;
        }
    }

    /**
     * Determine if a signed state should eventually be written to disk. If the state should eventually be written, the
     * state's {@link SignedState#markAsStateToSave} method will be called, to indicate the reason
     *
     * @param signedState the signed state in question
     * @param source      the source of the signed state
     */
    public synchronized void determineIfStateShouldBeSaved(
            @NonNull final SignedState signedState, @NonNull final SourceOfSignedState source) {

        final StateToDiskReason reason = shouldSaveToDisk(signedState, previousSavedStateTimestamp, source);

        // if a null reason is returned, then there isn't anything to do, since the state shouldn't be saved
        if (reason == null) {
            return;
        }

        logger.info(
                STATE_TO_DISK.getMarker(),
                "Signed state from round {} created, "
                        + "will eventually be written to disk once sufficient signatures are collected, for reason: {}",
                signedState.getRound(),
                reason);

        previousSavedStateTimestamp = signedState.getConsensusTimestamp();
        signedState.markAsStateToSave(reason);
    }

    /**
     * This should be called at boot time when a signed state is read from the disk.
     *
     * @param signedState the signed state that was read from file at boot time
     */
    public synchronized void registerSignedStateFromDisk(final SignedState signedState) {
        previousSavedStateTimestamp = signedState.getConsensusTimestamp();
    }

    /**
     * Purge old states on the disk.
     */
    private synchronized void deleteOldStates() {
        final List<SavedStateInfo> savedStates = getSavedStateFiles(mainClassName, selfId, swirldName);

        // States are returned newest to oldest. So delete from the end of the list to delete the oldest states.
        int index = savedStates.size() - 1;
        final StateConfig stateConfig = configuration.getConfigData(StateConfig.class);
        for (; index >= stateConfig.signedStateDisk(); index--) {

            final SavedStateInfo savedStateInfo = savedStates.get(index);
            try {
                deleteDirectoryAndLog(savedStateInfo.getDirectory());
            } catch (final IOException e) {
                // Intentionally ignored, deleteDirectoryAndLog will log any exceptions that happen
            }
        }

        // Keep the minimum generation non-ancient for the oldest state up to date
        if (index >= 0) {
            final SavedStateMetadata oldestStateMetadata =
                    savedStates.get(index).metadata();
            final long oldestStateMinimumGeneration = oldestStateMetadata.minimumGenerationNonAncient();
            if (minimumGenerationNonAncientForOldestState < oldestStateMinimumGeneration) {
                minimumGenerationNonAncientForOldestState = oldestStateMinimumGeneration;
                minimumGenerationNonAncientConsumer.newMinimumGenerationNonAncient(oldestStateMinimumGeneration);
            }
        }
    }

    /**
     * Get the minimum generation non-ancient for the oldest state on disk.
     *
     * @return the minimum generation non-ancient for the oldest state on disk
     */
    public synchronized long getMinimumGenerationNonAncientForOldestState() {
        return minimumGenerationNonAncientForOldestState;
    }

    /**
     * Get the round of the latest state written to disk, or {@link com.swirlds.common.system.UptimeData#NO_ROUND} if no
     * states have been written to disk since booting up.
     *
     * @return the latest saved state round
     */
    public long getLatestSavedStateRound() {
        return latestSavedStateRound.get();
    }
}
