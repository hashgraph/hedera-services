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

import static com.swirlds.common.io.streams.SerializableStreamConstants.SERIALIZATION_PROTOCOL_VERSION;
import static com.swirlds.common.io.streams.SerializableStreamConstants.SIGNATURE_SET_SEPARATED_VERSION;
import static com.swirlds.common.io.streams.StreamDebugUtils.deserializeAndDebugOnFailure;
import static com.swirlds.platform.state.snapshot.SignedStateFileUtils.SIGNATURE_SET_FILE_NAME;
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

    public record StateFileData(MerkleRoot state, Hash hash, SigSet sigSet, int protocolVersion) {}

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
        StateFileData data = readStateFileData(stateFile, snapshotStateReader);

        final StateFileData normalizedData;
        if (data.sigSet == null) {
            File sigSetFile =
                    stateFile.getParent().resolve(SIGNATURE_SET_FILE_NAME).toFile();
            normalizedData = deserializeAndDebugOnFailure(
                    () -> new BufferedInputStream(new FileInputStream(sigSetFile)),
                    (final MerkleDataInputStream in) -> {
                        int protocolVersion = readAndCheckVersion(in);
                        if (protocolVersion != data.protocolVersion) {
                            throw new IOException(
                                    "Protocol versions of the state file and the signature set file do not match");
                        }
                        final SigSet sigSet = in.readSerializable();
                        return new StateFileData(data.state, data.hash, sigSet, protocolVersion);
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

    public static StateFileData readStateFileData(
            Path stateFile, CheckedBiFunction<MerkleDataInputStream, Path, MerkleRoot, IOException> snapshotStateReader)
            throws IOException {
        return deserializeAndDebugOnFailure(
                () -> new BufferedInputStream(new FileInputStream(stateFile.toFile())),
                (final MerkleDataInputStream in) -> {
                    int protocolVersion = readAndCheckVersion(in);

                    final Path directory = stateFile.getParent();
                    if (protocolVersion == SERIALIZATION_PROTOCOL_VERSION) {
                        try {
                            final MerkleRoot state = snapshotStateReader.apply(in, directory);
                            final Hash hash = in.readSerializable();
                            final SigSet sigSet = in.readSerializable();
                            return new StateFileData(state, hash, sigSet, protocolVersion);
                        } catch (final IOException e) {
                            throw new IOException("Failed to read snapshot file " + stateFile.toFile(), e);
                        }
                    } else if (protocolVersion == SIGNATURE_SET_SEPARATED_VERSION) {
                        try {
                            final MerkleRoot state = snapshotStateReader.apply(in, directory);
                            final Hash hash = in.readSerializable();
                            return new StateFileData(state, hash, null, protocolVersion);

                        } catch (final IOException e) {
                            throw new IOException("Failed to read snapshot file " + stateFile.toFile(), e);
                        }
                    } else {
                        throw new IOException("Unsupported protocol version: " + protocolVersion);
                    }
                });
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
    private static int readAndCheckVersion(@NonNull final MerkleDataInputStream in) throws IOException {
        final byte versionByte = in.readByte();
        if (versionByte != VERSIONED_FILE_BYTE) {
            throw new IOException("File is not versioned -- data corrupted or is an unsupported legacy state");
        }

        in.readInt(); // file version
        return in.readProtocolVersion();
    }
}
