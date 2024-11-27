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
import static com.swirlds.platform.state.snapshot.SignedStateFileUtils.SIGNATURE_SET_FILE_NAME;
import static com.swirlds.platform.state.snapshot.SignedStateFileUtils.SUPPORTED_SIGSET_VERSIONS;
import static java.nio.file.Files.exists;

import com.swirlds.common.config.StateCommonConfig;
import com.swirlds.common.config.singleton.ConfigurationHolder;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.io.streams.MerkleDataInputStream;
import com.swirlds.common.platform.NodeId;
import com.swirlds.config.api.Configuration;
import com.swirlds.platform.crypto.CryptoStatic;
import com.swirlds.platform.state.MerkleRoot;
import com.swirlds.platform.state.signed.SignedState;
import com.swirlds.state.merkle.MerkleTreeSnapshotReader;
import com.swirlds.state.merkle.SigSet;
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

    /**
     * Reads a SignedState from disk. If the reader throws an exception, it is propagated by this method to the caller.
     *
     * @param configuration                 the configuration for this node
     * @param stateFile                     the file to read from
     * @return a signed state with it's associated hash (as computed when the state was serialized)
     * @throws IOException if there is any problems with reading from a file
     */
    public static @NonNull DeserializedSignedState readStateFile(
            @NonNull final Configuration configuration, @NonNull final Path stateFile) throws IOException {

        Objects.requireNonNull(configuration);
        Objects.requireNonNull(stateFile);

        checkSignedStatePath(stateFile);

        final DeserializedSignedState returnState;
        final MerkleTreeSnapshotReader.StateFileData data = MerkleTreeSnapshotReader.readStateFileData(stateFile);

        final MerkleTreeSnapshotReader.StateFileData normalizedData;
        if (data.sigSet() == null) {
            final File sigSetFile =
                    stateFile.getParent().resolve(SIGNATURE_SET_FILE_NAME).toFile();
            normalizedData = deserializeAndDebugOnFailure(
                    () -> new BufferedInputStream(new FileInputStream(sigSetFile)),
                    (final MerkleDataInputStream in) -> {
                        readAndCheckSigSetFileVersion(in);
                        final SigSet sigSet = in.readSerializable();
                        return new MerkleTreeSnapshotReader.StateFileData(data.stateRoot(), data.hash(), sigSet);
                    });
        } else {
            normalizedData = data;
        }

        final SignedState newSignedState = new SignedState(
                configuration,
                CryptoStatic::verifySignature,
                (MerkleRoot) normalizedData.stateRoot(),
                "SignedStateFileReader.readStateFile()",
                false,
                false,
                false);

        newSignedState.setSigSet(normalizedData.sigSet());

        returnState = new DeserializedSignedState(
                newSignedState.reserve("SignedStateFileReader.readStateFile()"), normalizedData.hash());

        return returnState;
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
     * Read the version from a signature set file and check it
     *
     * @param in the stream to read from
     * @throws IOException if the version is invalid
     */
    private static void readAndCheckSigSetFileVersion(@NonNull final MerkleDataInputStream in) throws IOException {
        final int fileVersion = in.readInt();
        if (!SUPPORTED_SIGSET_VERSIONS.contains(fileVersion)) {
            throw new IOException("Unsupported file version: " + fileVersion);
        }
        in.readProtocolVersion();
    }
}
