/*
 * Copyright (C) 2022-2024 Hedera Hashgraph, LLC
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

package com.swirlds.platform.state.snapshot;

import static com.swirlds.common.io.streams.StreamDebugUtils.deserializeAndDebugOnFailure;
import static com.swirlds.platform.state.snapshot.SignedStateFileUtils.INIT_FILE_VERSION;
import static com.swirlds.platform.state.snapshot.SignedStateFileUtils.SIGNATURE_SET_FILE_NAME;
import static com.swirlds.platform.state.snapshot.SignedStateFileUtils.SIG_SET_SEPARATE_VERSION;
import static com.swirlds.platform.state.snapshot.SignedStateFileUtils.SUPPORTED_SIGSET_VERSIONS;
import static com.swirlds.platform.state.snapshot.SignedStateFileUtils.SUPPORTED_STATE_FILE_VERSIONS;
import static com.swirlds.platform.state.snapshot.SignedStateFileUtils.VERSIONED_FILE_BYTE;
import static java.nio.file.Files.exists;

import com.swirlds.base.function.CheckedBiFunction;
import com.swirlds.common.config.StateCommonConfig;
import com.swirlds.common.config.singleton.ConfigurationHolder;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.io.streams.MerkleDataInputStream;
import com.swirlds.common.platform.NodeId;
import com.swirlds.platform.crypto.CryptoStatic;
import com.swirlds.platform.state.MerkleRoot;
import com.swirlds.platform.state.signed.SigSet;
import com.swirlds.platform.state.signed.SignedState;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * Utility methods for reading a signed state from disk.
 */
public final class SignedStateFileReader {
    private SignedStateFileReader() {}

    /**
     * Same as {@link SignedStateFilePath#getSavedStateFiles(String, NodeId, String)} but uses the config from
     * {@link ConfigurationHolder}
     *
     * @deprecated this uses a static config, which means that a unit test cannot configure it for its scope. this
     * causes unit tests to fail randomly if another test sets an inadequate value in the config holder.
     */
    @Deprecated(forRemoval = true)
    @NonNull
    public static List<SavedStateInfo> getSavedStateFiles(
            @NonNull final PlatformContext platformContext,
            final String mainClassName,
            final NodeId platformId,
            final String swirldName) {
        // new instance on every call in case the config changes in the holder
        return new SignedStateFilePath(platformContext.getConfiguration().getConfigData(StateCommonConfig.class))
                .getSavedStateFiles(mainClassName, platformId, swirldName);
    }

    /**
     * This is a helper class to hold the data read from a state file.
     * @param state the Merkle tree state
     * @param hash the hash of the state
     * @param sigSet the signature set
     * @param fileVersion the version of the file
     */
    public record StateFileData(
            @NonNull MerkleRoot state, @NonNull Hash hash, @Nullable SigSet sigSet, int fileVersion) {}

    /**
     * Reads a SignedState from disk using the provided snapshot reader function. If the reader throws
     * an exception, it is propagated by this method to the caller.
     *
     * @param platformContext               the platform context
     * @param stateFile                     the file to read from
     * @param snapshotStateReader           state snapshot reading function
     * @return a signed state with it's associated hash (as computed when the state was serialized)
     * @throws IOException if there is any problems with reading from a file
     */
    public static @NonNull DeserializedSignedState readStateFile(
            @NonNull final PlatformContext platformContext,
            @NonNull final Path stateFile,
            @NonNull final CheckedBiFunction<MerkleDataInputStream, Path, MerkleRoot, IOException> snapshotStateReader)
            throws IOException {

        Objects.requireNonNull(platformContext);
        Objects.requireNonNull(stateFile);

        checkSignedStatePath(stateFile);

        final DeserializedSignedState returnState;
        final StateFileData data = readStateFileData(stateFile, snapshotStateReader);

        final StateFileData normalizedData;
        if (data.sigSet == null) {
            final File sigSetFile =
                    stateFile.getParent().resolve(SIGNATURE_SET_FILE_NAME).toFile();
            normalizedData = deserializeAndDebugOnFailure(
                    () -> new BufferedInputStream(new FileInputStream(sigSetFile)),
                    (final MerkleDataInputStream in) -> {
                        final int fileVersion = readAndCheckSigSetFileVersion(in);
                        final SigSet sigSet = in.readSerializable();
                        return new StateFileData(data.state, data.hash, sigSet, fileVersion);
                    });
        } else {
            normalizedData = data;
        }

        final SignedState newSignedState = new SignedState(
                platformContext,
                CryptoStatic::verifySignature,
                normalizedData.state,
                "SignedStateFileReader.readStateFile()",
                false,
                false,
                false);

        newSignedState.setSigSet(normalizedData.sigSet);

        returnState = new DeserializedSignedState(
                newSignedState.reserve("SignedStateFileReader.readStateFile()"), normalizedData.hash);

        return returnState;
    }

    /**
     * Reads a SignedState from disk using the provided snapshot reader function.
     * @param stateFile the file to read from
     * @param snapshotStateReader state snapshot reading function
     * @return a signed state with it's associated hash (as computed when the state was serialized)
     * @throws IOException if there is any problems with reading from a file
     */
    @NonNull
    public static StateFileData readStateFileData(
            @NonNull final Path stateFile,
            @NonNull final CheckedBiFunction<MerkleDataInputStream, Path, MerkleRoot, IOException> snapshotStateReader)
            throws IOException {
        return deserializeAndDebugOnFailure(
                () -> new BufferedInputStream(new FileInputStream(stateFile.toFile())),
                (final MerkleDataInputStream in) -> {
                    int fileVersion = readAndCheckStateFileVersion(in);

                    final Path directory = stateFile.getParent();
                    if (fileVersion == INIT_FILE_VERSION) {
                        return readStateFileDataV1(stateFile, snapshotStateReader, in, directory, fileVersion);
                    } else if (fileVersion == SIG_SET_SEPARATE_VERSION) {
                        return createStateFileDataV2(stateFile, snapshotStateReader, in, directory, fileVersion);
                    } else {
                        throw new IOException("Unsupported protocol version: " + fileVersion);
                    }
                });
    }

    @NonNull
    private static StateFileData createStateFileDataV2(
            @NonNull Path stateFile,
            @NonNull CheckedBiFunction<MerkleDataInputStream, Path, MerkleRoot, IOException> snapshotStateReader,
            @NonNull MerkleDataInputStream in,
            @NonNull Path directory,
            int fileVersion)
            throws IOException {
        try {
            final MerkleRoot state = snapshotStateReader.apply(in, directory);
            final Hash hash = in.readSerializable();
            return new StateFileData(state, hash, null, fileVersion);

        } catch (final IOException e) {
            throw new IOException("Failed to read snapshot file " + stateFile.toFile(), e);
        }
    }

    @NonNull
    private static StateFileData readStateFileDataV1(
            @NonNull Path stateFile,
            @NonNull CheckedBiFunction<MerkleDataInputStream, Path, MerkleRoot, IOException> snapshotStateReader,
            @NonNull MerkleDataInputStream in,
            @NonNull Path directory,
            int fileVersion)
            throws IOException {
        try {
            final MerkleRoot state = snapshotStateReader.apply(in, directory);
            final Hash hash = in.readSerializable();
            final SigSet sigSet = in.readSerializable();
            return new StateFileData(state, hash, sigSet, fileVersion);
        } catch (final IOException e) {
            throw new IOException("Failed to read snapshot file " + stateFile.toFile(), e);
        }
    }

    /**
     * Check the path of a signed state file
     *
     * @param stateFile the path to check
     * @throws IOException if the path is not valid
     */
    private static void checkSignedStatePath(@NonNull final Path stateFile) throws IOException {
        if (!exists(stateFile)) {
            throw new IOException("File " + stateFile.toAbsolutePath() + " does not exist!");
        }
        if (!Files.isRegularFile(stateFile)) {
            throw new IOException("File " + stateFile.toAbsolutePath() + " is not a file!");
        }
    }

    /**
     * Read the version from a signed state file and check it
     *
     * @param in the stream to read from
     * @throws IOException if the version is invalid
     * @return the protocol version
     */
    private static int readAndCheckStateFileVersion(@NonNull final MerkleDataInputStream in) throws IOException {
        final byte versionByte = in.readByte();
        if (versionByte != VERSIONED_FILE_BYTE) {
            throw new IOException("File is not versioned -- data corrupted or is an unsupported legacy state");
        }

        final int fileVersion = in.readInt();
        if (!SUPPORTED_STATE_FILE_VERSIONS.contains(fileVersion)) {
            throw new IOException("Unsupported file version: " + fileVersion);
        }
        in.readProtocolVersion();
        return fileVersion;
    }
    /**
     * Read the version from a signature set file and check it
     *
     * @param in the stream to read from
     * @throws IOException if the version is invalid
     * @return the protocol version
     */
    private static int readAndCheckSigSetFileVersion(@NonNull final MerkleDataInputStream in) throws IOException {
        final int fileVersion = in.readInt();
        if (!SUPPORTED_SIGSET_VERSIONS.contains(fileVersion)) {
            throw new IOException("Unsupported file version: " + fileVersion);
        }
        in.readProtocolVersion();
        return fileVersion;
    }
}
