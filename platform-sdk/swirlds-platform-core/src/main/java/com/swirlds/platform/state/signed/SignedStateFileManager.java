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
import static com.swirlds.platform.state.signed.SignedStateFileReader.getSavedStateFiles;
import static com.swirlds.platform.state.signed.SignedStateFileUtils.getSignedStateDirectory;
import static com.swirlds.platform.state.signed.SignedStateFileUtils.getSignedStatesBaseDirectory;
import static com.swirlds.platform.state.signed.StateToDiskReason.UNKNOWN;

import com.swirlds.base.time.Time;
import com.swirlds.common.config.StateConfig;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.system.NodeId;
import com.swirlds.common.system.status.StatusActionSubmitter;
import com.swirlds.common.system.status.actions.StateWrittenToDiskAction;
import com.swirlds.common.threading.framework.QueueThread;
import com.swirlds.config.api.Configuration;
import com.swirlds.logging.legacy.payload.InsufficientSignaturesPayload;
import com.swirlds.platform.components.state.output.MinimumGenerationNonAncientConsumer;
import com.swirlds.platform.components.state.output.StateToDiskAttemptConsumer;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * This class is responsible for managing the signed state writing pipeline.
 */
public class SignedStateFileManager {

    private static final Logger logger = LogManager.getLogger(SignedStateFileManager.class);

    /**
     * A consumer of data when a state is written to disk
     */
    private final StateToDiskAttemptConsumer stateToDiskAttemptConsumer;

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
     * Metrics provider
     */
    private final SignedStateMetrics metrics;

    private final Configuration configuration;
    private final PlatformContext platformContext;

    /**
     * Provides system time
     */
    private final Time time;

    /**
     * Enables submitting platform status actions
     */
    private final StatusActionSubmitter statusActionSubmitter;

    /**
     * This method must be called when the minimum generation non-ancient of the oldest state snapshot on disk changes.
     */
    private final MinimumGenerationNonAncientConsumer minimumGenerationNonAncientConsumer;

    /**
     * Creates a new instance.
     *
     * @param configuration                       configuration
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
            @NonNull final Configuration configuration,
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
        this.platformContext = Objects.requireNonNull(context);
        this.configuration = Objects.requireNonNull(context).getConfiguration();
        this.minimumGenerationNonAncientConsumer = Objects.requireNonNull(
                minimumGenerationNonAncientConsumer, "minimumGenerationNonAncientConsumer must not be null");
        this.statusActionSubmitter = Objects.requireNonNull(statusActionSubmitter);

//TODO use for wire

//        this.taskQueue = new QueueThreadConfiguration<Runnable>(threadManager)
//                .setCapacity(stateConfig.stateSavingQueueSize())
//                .setMaxBufferSize(1)
//                .setPriority(threadConfig.threadPriorityNonSync())
//                .setNodeId(selfId)
//                .setComponent(PLATFORM_THREAD_POOL_NAME)
//                .setThreadName("signed-state-file-manager")
//                .setHandler(Runnable::run)
//                .build();

        final List<SavedStateInfo> savedStates = getSavedStateFiles(mainClassName, selfId, swirldName);
        if (!savedStates.isEmpty()) {
            // The minimum generation of non-ancient events for the oldest state snapshot on disk.
            final long minimumGenerationNonAncientForOldestState = savedStates.get(savedStates.size() - 1).metadata()
                    .minimumGenerationNonAncient();
            minimumGenerationNonAncientConsumer.newMinimumGenerationNonAncient(
                    minimumGenerationNonAncientForOldestState);
        }
    }

    /**
     * A save state task
     *
     */
    public void saveStateTask(@NonNull final StateWriteRequest request) {

        final long start = time.nanoTime();
        boolean success = false;

        final ReservedSignedState reservedSignedState = request.reservedSignedState();
        final SignedState state = reservedSignedState.get();
        final StateToDiskReason reason = Optional.ofNullable(state.getStateToDiskReason()).orElse(UNKNOWN);
        final Path directory = request.outOfBand()
                ? getSignedStatesBaseDirectory()
                .resolve(reason.getDescription())
                .resolve(String.format("node%d_round%d", selfId.id(), state.getRound()))
                : getSignedStateDir(state.getRound());

        try (reservedSignedState) {
            if (request.outOfBand()) {
                // states requested to be written out-of-band are always written to disk
                SignedStateFileWriter.writeSignedStateToDisk(
                        platformContext, selfId, directory, state, reason);

                success = true;
                return;
            }
            if (state.hasStateBeenSavedToDisk()) {
                logger.info(
                        STATE_TO_DISK.getMarker(),
                        "Not saving signed state for round {} to disk because it has already been saved.",
                        state.getRound());
                return;
            }
            if (!state.isComplete()) {
                stateLacksSignatures(state);
            }

            SignedStateFileWriter.writeSignedStateToDisk(
                    platformContext, selfId, directory, state, reason, configuration);
            stateWrittenToDiskInBand(state, directory, start);

            success = true;

        } catch (final Throwable e) {
            stateToDiskAttemptConsumer.stateToDiskAttempt(reservedSignedState.get(), directory, false);
            logger.error(
                    EXCEPTION.getMarker(),
                    "Unable to write signed state to disk for round {} to {}.",
                    reservedSignedState.get().getRound(),
                    directory,
                    e);
        } finally {
            if (success) {
                deleteOldStates();
            }
            if (request.finishedCallback() != null) {
                request.finishedCallback().accept(success);
            }
            metrics.getStateToDiskTimeMetric().update(TimeUnit.NANOSECONDS.toMillis(time.nanoTime() - start));
        }

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
     * Get the directory for a particular signed state. This directory might not exist
     *
     * @param round the round number for the signed state
     * @return the File that represents the directory of the signed state for the particular round
     */
    private Path getSignedStateDir(final long round) {
        return getSignedStateDirectory(mainClassName, selfId, swirldName, round);
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
            minimumGenerationNonAncientConsumer.newMinimumGenerationNonAncient(oldestStateMinimumGeneration);

        }
    }
}
