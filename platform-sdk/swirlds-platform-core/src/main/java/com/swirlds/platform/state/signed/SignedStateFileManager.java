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
import com.swirlds.config.api.Configuration;
import com.swirlds.logging.legacy.payload.InsufficientSignaturesPayload;
import com.swirlds.platform.event.EventConstants;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * This class is responsible for managing the signed state writing pipeline.
 */
public class SignedStateFileManager {

    private static final Logger logger = LogManager.getLogger(SignedStateFileManager.class);

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
     * Creates a new instance.
     *
     * @param context                             the platform context
     * @param metrics                             metrics provider
     * @param time                                provides time
     * @param mainClassName                       the main class name of this node
     * @param selfId                              the ID of this node
     * @param swirldName                          the name of the swirld
     */
    public SignedStateFileManager(
            @NonNull final PlatformContext context,
            @NonNull final SignedStateMetrics metrics,
            @NonNull final Time time,
            @NonNull final String mainClassName,
            @NonNull final NodeId selfId,
            @NonNull final String swirldName) {

        this.metrics = Objects.requireNonNull(metrics, "metrics must not be null");
        this.time = time;
        this.selfId = selfId;
        this.mainClassName = mainClassName;
        this.swirldName = swirldName;
        this.platformContext = Objects.requireNonNull(context);
        this.configuration = Objects.requireNonNull(context.getConfiguration());
    }

    /**
     * Method to be called when a state has been successfully written to disk in-band. An "in-band" write is part of
     * normal platform operations, whereas an out-of-band write is triggered due to a fault, or for debug purposes.
     * <p>
     * This method shouldn't be called if the state was written out-of-band.
     */
    public StateSavingResult saveStateTask(@NonNull final ReservedSignedState reservedSignedState) {
        final long start = time.nanoTime();
        final StateSavingResult stateSavingResult;
        final boolean success;

        // the state is reserved before it is handed to this method, and it is released when we are done
        try (reservedSignedState) {
            final SignedState signedState = reservedSignedState.get();
            success = saveStateTask(signedState, false);
            final long minGen = success ? deleteOldStates() : EventConstants.GENERATION_UNDEFINED;
            stateSavingResult = new StateSavingResult(
                    signedState.getRound(),
                    success,
                    signedState.isFreezeState(),
                    signedState.getConsensusTimestamp(),
                    minGen
            );
        }
        metrics.getStateToDiskTimeMetric().update(TimeUnit.NANOSECONDS.toMillis(time.nanoTime() - start));
        metrics.getWriteStateToDiskTimeMetric().update(TimeUnit.NANOSECONDS.toMillis(time.nanoTime() - start));

        return stateSavingResult;
    }

    public void dumpStateTask(@NonNull final StateDumpRequest request) {
        final boolean success;

        // the state is reserved before it is handed to this method, and it is released when we are done
        try (final ReservedSignedState reservedSignedState = request.reservedSignedState()) {
            success = saveStateTask(reservedSignedState.get(), true);
        }
        if (request.finishedCallback() != null) {
            request.finishedCallback().accept(success);
        }
    }

    /**
     * A save state task
     */
    private boolean saveStateTask(@NonNull final SignedState state, final boolean outOfBand) {
        final StateToDiskReason reason = Optional.ofNullable(state.getStateToDiskReason()).orElse(UNKNOWN);
        final Path directory = outOfBand
                ? getSignedStatesBaseDirectory()
                .resolve(reason.getDescription())
                .resolve(String.format("node%d_round%d", selfId.id(), state.getRound()))
                : getSignedStateDir(state.getRound());
        try {
            if (outOfBand) {
                // states requested to be written out-of-band are always written to disk
                SignedStateFileWriter.writeSignedStateToDisk(
                        platformContext, selfId, directory, state, reason);
                return true;
            }
            if (state.hasStateBeenSavedToDisk()) {
                logger.info(
                        STATE_TO_DISK.getMarker(),
                        "Not saving signed state for round {} to disk because it has already been saved.",
                        state.getRound());
                return false;
            }
            if (!state.isComplete()) {
                stateLacksSignatures(state);
            }

            SignedStateFileWriter.writeSignedStateToDisk(
                    platformContext, selfId, directory, state, reason);

            state.stateSavedToDisk();

            return true;
        } catch (final Throwable e) {
            logger.error(
                    EXCEPTION.getMarker(),
                    "Unable to write signed state to disk for round {} to {}.",
                    state.getRound(),
                    directory,
                    e);
            return false;
        }
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
    private long deleteOldStates() {
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
        if (index < 0) {
            return EventConstants.GENERATION_UNDEFINED;
        }
        final SavedStateMetadata oldestStateMetadata = savedStates.get(index).metadata();
        return oldestStateMetadata.minimumGenerationNonAncient();

    }
}
