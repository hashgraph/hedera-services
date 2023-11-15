/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

import static com.swirlds.common.io.utility.FileUtils.executeAndRename;
import static com.swirlds.common.io.utility.FileUtils.writeAndFlush;
import static com.swirlds.logging.legacy.LogMarker.EXCEPTION;
import static com.swirlds.logging.legacy.LogMarker.STARTUP;
import static com.swirlds.logging.legacy.LogMarker.STATE_TO_DISK;
import static com.swirlds.platform.config.internal.PlatformConfigUtils.writeSettingsUsed;
import static com.swirlds.platform.state.signed.SignedStateFileUtils.CURRENT_ADDRESS_BOOK_FILE_NAME;
import static com.swirlds.platform.state.signed.SignedStateFileUtils.FILE_VERSION;
import static com.swirlds.platform.state.signed.SignedStateFileUtils.HASH_INFO_FILE_NAME;
import static com.swirlds.platform.state.signed.SignedStateFileUtils.SIGNED_STATE_FILE_NAME;
import static com.swirlds.platform.state.signed.SignedStateFileUtils.VERSIONED_FILE_BYTE;

import com.swirlds.common.config.StateConfig;
import com.swirlds.common.config.singleton.ConfigurationHolder;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.io.streams.MerkleDataOutputStream;
import com.swirlds.common.merkle.utility.MerkleTreeVisualizer;
import com.swirlds.common.system.NodeId;
import com.swirlds.common.system.address.AddressBook;
import com.swirlds.logging.legacy.payload.StateSavedToDiskPayload;
import com.swirlds.platform.event.preconsensus.PreconsensusEventFile;
import com.swirlds.platform.event.preconsensus.PreconsensusEventFileManager;
import com.swirlds.platform.event.preconsensus.PreconsensusEventStreamConfig;
import com.swirlds.platform.recovery.emergencyfile.EmergencyRecoveryFile;
import com.swirlds.platform.state.State;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Utility methods for writing a signed state to disk.
 */
public final class SignedStateFileWriter {

    private static final Logger logger = LogManager.getLogger(SignedStateFileWriter.class);

    /**
     * The number of times to attempt to copy the last PCES file. Access to this file is not really coordinated between
     * this logic and the code responsible for managing PCES file lifecycle, and so there is a small chance that the
     * file moves when we attempt to make a copy. However, this probability is fairly small, and it is very unlikely
     * that we will be unable to snatch a copy in time with a few retries.
     */
    private static final int COPY_PCES_MAX_RETRIES = 10;

    private SignedStateFileWriter() {}

    /**
     * Write a file that contains information about the hash of the state. A useful nugget of information for when a
     * human needs to decide what is contained within a signed state file. If the file already exists in the given
     * directory then it is overwritten.
     *
     * @param state     the state that is being written
     * @param directory the directory where the state is being written
     */
    public static void writeHashInfoFile(final Path directory, final State state) throws IOException {
        final StateConfig stateConfig = ConfigurationHolder.getConfigData(StateConfig.class);
        final String platformInfo = state.getInfoString(stateConfig.debugHashDepth());

        logger.info(
                STATE_TO_DISK.getMarker(),
                """
                        Information for state written to disk:
                        {}""",
                platformInfo);

        final Path hashInfoFile = directory.resolve(HASH_INFO_FILE_NAME);

        final String hashInfo = new MerkleTreeVisualizer(state)
                .setDepth(stateConfig.debugHashDepth())
                .render();
        try (final BufferedWriter writer = new BufferedWriter(new FileWriter(hashInfoFile.toFile()))) {
            writer.write(hashInfo);
        }
    }

    /**
     * Write the signed state metadata file
     *
     * @param selfId      the id of the platform
     * @param directory   the directory to write to
     * @param signedState the signed state being written
     */
    public static void writeMetadataFile(
            @Nullable final NodeId selfId, @NonNull final Path directory, @NonNull final SignedState signedState)
            throws IOException {
        Objects.requireNonNull(directory, "directory must not be null");
        Objects.requireNonNull(signedState, "signedState must not be null");

        final Path metadataFile = directory.resolve(SavedStateMetadata.FILE_NAME);

        SavedStateMetadata.create(signedState, selfId, Instant.now()).write(metadataFile);
    }

    /**
     * Write a {@link SignedState} to a stream.
     *
     * @param out         the stream to write to
     * @param directory   the directory to write to
     * @param signedState the signed state to write
     */
    private static void writeStateFileToStream(
            final MerkleDataOutputStream out, final Path directory, final SignedState signedState) throws IOException {
        out.write(VERSIONED_FILE_BYTE);
        out.writeInt(FILE_VERSION);
        out.writeProtocolVersion();
        out.writeMerkleTree(directory, signedState.getState());
        out.writeSerializable(signedState.getState().getHash(), true);
        out.writeSerializable(signedState.getSigSet(), true);
    }

    /**
     * Write the signed state file.
     *
     * @param directory   the directory to write to
     * @param signedState the signed state to write
     */
    public static void writeStateFile(final Path directory, final SignedState signedState) throws IOException {
        writeAndFlush(
                directory.resolve(SIGNED_STATE_FILE_NAME), out -> writeStateFileToStream(out, directory, signedState));
    }

    /**
     * Write all files that belong in the signed state directory into a directory.
     *
     * @param platformContext the platform context
     * @param selfId          the id of the platform
     * @param directory       the directory where all files should be placed
     * @param signedState     the signed state being written to disk
     */
    public static void writeSignedStateFilesToDirectory(
            @Nullable final PlatformContext platformContext,
            @Nullable final NodeId selfId,
            @NonNull final Path directory,
            @NonNull final SignedState signedState)
            throws IOException {
        Objects.requireNonNull(platformContext);
        Objects.requireNonNull(directory);
        Objects.requireNonNull(signedState);

        writeStateFile(directory, signedState);
        writeHashInfoFile(directory, signedState.getState());
        writeMetadataFile(selfId, directory, signedState);
        writeEmergencyRecoveryFile(directory, signedState);
        writeStateAddressBookFile(directory, signedState.getAddressBook());
        writeSettingsUsed(directory, platformContext.getConfiguration());

        if (selfId != null) {
            copyPreconsensusEventStreamFilesRetryOnFailure(
                    platformContext,
                    selfId,
                    directory,
                    signedState.getState().getPlatformState().getPlatformData().getMinimumGenerationNonAncient());
        }
    }

    private static void copyPreconsensusEventStreamFilesRetryOnFailure(
            @NonNull final PlatformContext platformContext,
            @NonNull final NodeId selfId,
            @NonNull final Path destinationDirectory,
            final long minimumGenerationNonAncient)
            throws IOException {

        int triesRemaining = COPY_PCES_MAX_RETRIES;
        while (triesRemaining > 0) {
            triesRemaining--;
            try {
                copyPreconsensusEventStreamFiles(
                        platformContext, selfId, destinationDirectory, minimumGenerationNonAncient);
                return;
            } catch (final PreconsensusEventFileRenamed e) {
                if (triesRemaining == 0) {
                    logger.error(
                            EXCEPTION.getMarker(),
                            "Unable to copy the last PCES file after {} retries. "
                                    + "PCES files will not be written into the state.",
                            COPY_PCES_MAX_RETRIES);
                }
            }
        }
    }

    /**
     * Copy preconsensus event files into the signed state directory. These files are necessary for the platform to use
     * the state file as a starting point. Note: starting a node using the PCES files in the state directory does not
     * guarantee that there is no data loss (i.e. there may be transactions that reach consensus after the state
     * snapshot), but it does allow a node to start up and participate in gossip.
     *
     * <p>
     * This general strategy is not very elegant is very much a hack. But it will allow us to do migration testing using
     * real production states and streams, in the short term. In the longer term we should consider alternate and
     * cleaner strategies.
     *
     * @param platformContext             the platform context
     * @param destinationDirectory        the directory where the state is being written
     * @param minimumGenerationNonAncient the minimum generation of events that are not ancient, with respect to the
     *                                    state that is being written
     */
    private static void copyPreconsensusEventStreamFiles(
            @NonNull final PlatformContext platformContext,
            @NonNull final NodeId selfId,
            @NonNull final Path destinationDirectory,
            final long minimumGenerationNonAncient)
            throws IOException {

        final boolean copyPreconsensusStream = platformContext
                .getConfiguration()
                .getConfigData(PreconsensusEventStreamConfig.class)
                .copyRecentStreamToStateSnapshots();
        if (!copyPreconsensusStream) {
            // PCES copying is disabled
            return;
        }

        // The PCES files will be copied into this directory
        final Path pcesDestination =
                destinationDirectory.resolve("preconsensus-events").resolve(Long.toString(selfId.id()));
        Files.createDirectories(pcesDestination);

        final List<PreconsensusEventFile> allFiles = gatherPreconsensusFilesOnDisk(selfId, platformContext);
        if (allFiles.isEmpty()) {
            return;
        }

        // Sort by sequence number
        Collections.sort(allFiles);

        // Discard all files that either have an incorrect origin or that do not contain non-ancient events.
        final List<PreconsensusEventFile> filesToCopy =
                getRequiredPreconsensusFiles(allFiles, minimumGenerationNonAncient);
        if (filesToCopy.isEmpty()) {
            return;
        }

        copyPreconsensusFileList(filesToCopy, pcesDestination);
    }

    /**
     * Get the preconsensus files that we need to copy to a state. We need any file that has a matching origin and that
     * contains non-ancient events (w.r.t. the state).
     *
     * @param allFiles                    all PCES files on disk
     * @param minimumGenerationNonAncient the minimum generation of events that are not ancient, with respect to the
     *                                    state that is being written
     * @return the list of files to copy
     */
    @NonNull
    private static List<PreconsensusEventFile> getRequiredPreconsensusFiles(
            @NonNull final List<PreconsensusEventFile> allFiles, final long minimumGenerationNonAncient) {

        final List<PreconsensusEventFile> filesToCopy = new ArrayList<>();
        final PreconsensusEventFile lastFile = allFiles.get(allFiles.size() - 1);
        for (final PreconsensusEventFile file : allFiles) {
            if (file.getOrigin() == lastFile.getOrigin()
                    && file.getMaximumGeneration() >= minimumGenerationNonAncient) {
                filesToCopy.add(file);
            }
        }

        if (filesToCopy.isEmpty()) {
            logger.warn(
                    STATE_TO_DISK.getMarker(),
                    "No preconsensus event files meeting specified criteria found to copy. "
                            + "Minimum generation non-ancient: {}",
                    minimumGenerationNonAncient);
        } else if (filesToCopy.size() == 1) {
            logger.info(
                    STATE_TO_DISK.getMarker(),
                    """
                            Found 1 preconsensus event file meeting specified criteria to copy.
                                Minimum generation non-ancient: {}
                                File: {}
                            """,
                    minimumGenerationNonAncient,
                    filesToCopy.get(0).getPath());
        } else {
            logger.info(
                    STATE_TO_DISK.getMarker(),
                    """
                            Found {} preconsensus event files meeting specified criteria to copy.
                                Minimum generation non-ancient: {}
                                First file to copy: {}
                                Last file to copy: {}
                            """,
                    filesToCopy.size(),
                    minimumGenerationNonAncient,
                    filesToCopy.get(0).getPath(),
                    filesToCopy.get(filesToCopy.size() - 1).getPath());
        }

        return filesToCopy;
    }

    /**
     * Gather all PCES files on disk.
     *
     * @param selfId          the id of this node
     * @param platformContext the platform context
     * @return a list of all PCES files on disk
     */
    @NonNull
    private static List<PreconsensusEventFile> gatherPreconsensusFilesOnDisk(
            @NonNull final NodeId selfId, @NonNull final PlatformContext platformContext) throws IOException {
        final List<PreconsensusEventFile> allFiles = new ArrayList<>();
        final Path preconsensusEventStreamDirectory =
                PreconsensusEventFileManager.getDatabaseDirectory(platformContext, selfId);
        try (final Stream<Path> stream = Files.walk(preconsensusEventStreamDirectory)) {
            stream.filter(Files::isRegularFile).forEach(path -> {
                try {
                    allFiles.add(PreconsensusEventFile.of(path));
                } catch (final IOException e) {
                    // Ignore, this will get thrown for each file that is not a PCES file
                }
            });
        }

        if (allFiles.isEmpty()) {
            logger.warn(STATE_TO_DISK.getMarker(), "No preconsensus event files found to copy");
        } else if (allFiles.size() == 1) {
            logger.info(
                    STATE_TO_DISK.getMarker(),
                    """
                            Found 1 preconsensus file on disk.
                                File: {}""",
                    allFiles.get(0).getPath());
        } else {
            logger.info(
                    STATE_TO_DISK.getMarker(),
                    """
                            Found {} preconsensus files on disk.
                                First file: {}
                                Last file: {}""",
                    allFiles.size(),
                    allFiles.get(0).getPath(),
                    allFiles.get(allFiles.size() - 1).getPath());
        }

        return allFiles;
    }

    /**
     * Copy a list of preconsensus event files into a directory.
     *
     * @param filesToCopy     the files to copy
     * @param pcesDestination the directory where the files should be copied
     */
    private static void copyPreconsensusFileList(
            @NonNull final List<PreconsensusEventFile> filesToCopy, @NonNull final Path pcesDestination) {
        logger.info(
                STATE_TO_DISK.getMarker(),
                "Copying {} preconsensus event files to state snapshot directory",
                filesToCopy.size());

        // The last file might be in the process of being written, so we need to do a deep copy of it.
        // Unlike the other files we are going to copy which have a long lifespan and are expected to
        // be stable, the last file is actively in flux. It's possible that the last file will be
        // renamed by the time we get to it, which may cause this copy operation to fail. Attempt
        // to copy this file first, so that if we fail we can abort and retry without other side
        // effects.
        deepCopyPreconsensusFile(filesToCopy.get(filesToCopy.size() - 1), pcesDestination);

        // Although the last file may be currently in the process of being written, all previous files will
        // be closed and immutable and so it's safe to hard link them.
        for (int index = 0; index < filesToCopy.size() - 1; index++) {
            hardLinkPreconsensusFile(filesToCopy.get(index), pcesDestination);
        }

        logger.info(
                STATE_TO_DISK.getMarker(),
                "Finished copying {} preconsensus event files to state snapshot directory",
                filesToCopy.size());
    }

    /**
     * Hard link a PCES file.
     *
     * @param file            the file to link
     * @param pcesDestination the directory where the file should be linked into
     */
    private static void hardLinkPreconsensusFile(
            @NonNull final PreconsensusEventFile file, @NonNull final Path pcesDestination) {
        final Path destination = pcesDestination.resolve(file.getFileName());
        try {
            Files.createLink(destination, file.getPath());
        } catch (final IOException e) {
            logger.error(
                    EXCEPTION.getMarker(),
                    "Exception when hard linking preconsensus event file {} to {}",
                    file.getPath(),
                    destination,
                    e);
        }
    }

    /**
     * Attempt to deep copy a PCES file.
     *
     * @param file            the file to copy
     * @param pcesDestination the directory where the file should be copied into
     */
    private static void deepCopyPreconsensusFile(
            @NonNull final PreconsensusEventFile file, @NonNull final Path pcesDestination) {
        try {
            Files.copy(file.getPath(), pcesDestination.resolve(file.getFileName()));
        } catch (final IOException e) {
            logger.info(STARTUP.getMarker(), "unable to copy last PCES file (file was likely renamed), will retry");
            throw new PreconsensusEventFileRenamed(e);
        }
    }

    /**
     * Write the state's address book in human-readable form.
     *
     * @param directory   the directory to write to
     * @param addressBook the address book to write
     */
    private static void writeStateAddressBookFile(@NonNull final Path directory, @NonNull final AddressBook addressBook)
            throws IOException {
        final Path addressBookFile = directory.resolve(CURRENT_ADDRESS_BOOK_FILE_NAME);

        try (final BufferedWriter writer = new BufferedWriter(new FileWriter(addressBookFile.toFile()))) {
            writer.write(addressBook.toConfigText());
        }
    }

    /**
     * Writes a SignedState to a file. Also writes auxiliary files such as "settingsUsed.txt". This is the top level
     * method called by the platform when it is ready to write a state.
     *
     * @param platformContext     the platform context
     * @param selfId              the id of the platform
     * @param savedStateDirectory the directory where the state will be stored
     * @param signedState         the object to be written
     * @param stateToDiskReason   the reason the state is being written to disk
     */
    public static void writeSignedStateToDisk(
            @NonNull final PlatformContext platformContext,
            @Nullable final NodeId selfId,
            @NonNull final Path savedStateDirectory,
            @NonNull final SignedState signedState,
            @Nullable final StateToDiskReason stateToDiskReason)
            throws IOException {

        Objects.requireNonNull(platformContext);
        Objects.requireNonNull(savedStateDirectory);
        Objects.requireNonNull(signedState);

        try {
            logger.info(
                    STATE_TO_DISK.getMarker(),
                    "Started writing round {} state to disk. Reason: {}, directory: {}",
                    signedState.getRound(),
                    stateToDiskReason == null ? "UNKNOWN" : stateToDiskReason,
                    savedStateDirectory);

            executeAndRename(
                    savedStateDirectory,
                    directory -> writeSignedStateFilesToDirectory(platformContext, selfId, directory, signedState));

            logger.info(STATE_TO_DISK.getMarker(), () -> new StateSavedToDiskPayload(
                            signedState.getRound(),
                            signedState.isFreezeState(),
                            stateToDiskReason == null ? "UNKNOWN" : stateToDiskReason.toString(),
                            savedStateDirectory)
                    .toString());
        } catch (final Throwable e) {
            logger.error(
                    EXCEPTION.getMarker(),
                    "Exception when writing the signed state for round {} to disk:",
                    signedState.getRound(),
                    e);
            throw e;
        }
    }

    private static void writeEmergencyRecoveryFile(final Path savedStateDirectory, final SignedState signedState)
            throws IOException {
        new EmergencyRecoveryFile(
                        signedState.getRound(), signedState.getState().getHash(), signedState.getConsensusTimestamp())
                .write(savedStateDirectory);
    }
}
