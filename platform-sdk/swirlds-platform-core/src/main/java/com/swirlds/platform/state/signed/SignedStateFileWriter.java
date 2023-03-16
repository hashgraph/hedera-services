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
import static com.swirlds.logging.LogMarker.EXCEPTION;
import static com.swirlds.logging.LogMarker.STATE_TO_DISK;
import static com.swirlds.platform.state.signed.SignedStateFileUtils.FILE_VERSION;
import static com.swirlds.platform.state.signed.SignedStateFileUtils.HASH_INFO_FILE_NAME;
import static com.swirlds.platform.state.signed.SignedStateFileUtils.SIGNED_STATE_FILE_NAME;
import static com.swirlds.platform.state.signed.SignedStateFileUtils.VERSIONED_FILE_BYTE;

import com.swirlds.common.io.streams.MerkleDataOutputStream;
import com.swirlds.common.merkle.utility.MerkleTreeVisualizer;
import com.swirlds.config.api.Configuration;
import com.swirlds.logging.payloads.StateSavedToDiskPayload;
import com.swirlds.platform.Settings;
import com.swirlds.platform.state.EmergencyRecoveryFile;
import com.swirlds.platform.state.State;
import com.swirlds.platform.state.StateSettings;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Utility methods for writing a signed state to disk.
 */
public final class SignedStateFileWriter {

    private static final Logger logger = LogManager.getLogger(SignedStateFileWriter.class);

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
        final String platformInfo = state.getPlatformState().getInfoString();
        final String hashInfo = new MerkleTreeVisualizer(state)
                .setDepth(StateSettings.getDebugHashDepth())
                .render();
        logger.info(
                STATE_TO_DISK.getMarker(), "Information for state written to disk:\n{}\n{}", platformInfo, hashInfo);

        final Path hashInfoFile = directory.resolve(HASH_INFO_FILE_NAME);

        try (final BufferedWriter writer = new BufferedWriter(new FileWriter(hashInfoFile.toFile()))) {
            writer.write(hashInfo);
        }
    }

    /**
     * Write the signed state metadata file
     *
     * @param selfId        the id of the platform
     * @param directory     the directory to write to
     * @param signedState   the signed state being written
     */
    public static void writeMetadataFile(final long selfId, final Path directory, final SignedState signedState)
            throws IOException {

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
     * @param selfId        the id of the platform
     * @param directory     the directory where all files should be placed
     * @param signedState   the signed state being written to disk
     */
    public static void writeSignedStateFilesToDirectory(
            final long selfId, final Path directory, final SignedState signedState) throws IOException {

        writeStateFile(directory, signedState);
        writeHashInfoFile(directory, signedState.getState());
        writeMetadataFile(selfId, directory, signedState);
        writeEmergencyRecoveryFile(directory, signedState);
        Settings.getInstance().writeSettingsUsed(directory);
    }

    /**
     * Writes a SignedState to a file. Also writes auxiliary files such as "settingsUsed.txt". This is the top level
     * method called by the platform when it is ready to write a state.
     *
     * @param selfId              the id of the platform
     * @param savedStateDirectory the directory where the state will be stored
     * @param signedState         the object to be written
     * @param taskDescription     a description of the task
     */
    public static void writeSignedStateToDisk(
            final long selfId,
            final Path savedStateDirectory,
            final SignedState signedState,
            final String taskDescription)
            throws IOException {

        try {
            logger.info(
                    STATE_TO_DISK.getMarker(),
                    "Started writing round {} state to disk. Reason: {}",
                    signedState.getRound(),
                    taskDescription);

            executeAndRename(
                    savedStateDirectory, directory -> writeSignedStateFilesToDirectory(selfId, directory, signedState));

            logger.info(
                    STATE_TO_DISK.getMarker(),
                    () -> new StateSavedToDiskPayload(signedState.getRound(), signedState.isFreezeState()).toString());
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
