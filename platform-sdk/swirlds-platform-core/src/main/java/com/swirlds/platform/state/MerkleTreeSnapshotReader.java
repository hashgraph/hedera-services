/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.swirlds.platform.state;

import static com.swirlds.common.io.streams.StreamDebugUtils.deserializeAndDebugOnFailure;

import com.swirlds.common.crypto.Hash;
import com.swirlds.common.io.streams.MerkleDataInputStream;
import com.swirlds.platform.state.signed.SigSet;
import com.swirlds.platform.state.snapshot.SignedStateFileUtils;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Set;

/**
 * Utility class for reading a snapshot of a {@link MerkleRoot} from disk.
 */
public class MerkleTreeSnapshotReader {

    /**
     * The previous version of the signed state file
     */
    public static final int INIT_STATE_FILE_VERSION = 1;
    /**
     * The current version of the signed state file. A file of this version no longer contains the signature set,
     * instead the signature set is stored in a separate file.
     */
    public static final int SIG_SET_SEPARATE_STATE_FILE_VERSION = 2;
    /**
     * The supported versions of the signed state file
     */
    public static final Set<Integer> SUPPORTED_STATE_FILE_VERSIONS =
            Set.of(INIT_STATE_FILE_VERSION, SIG_SET_SEPARATE_STATE_FILE_VERSION);
    /**
     * The signed state file was not versioned before, this byte was introduced to mark a versioned file
     */
    public static final byte VERSIONED_FILE_BYTE = Byte.MAX_VALUE;

    /**
     * This is a helper class to hold the data read from a state file.
     * @param state the Merkle tree state
     * @param hash the hash of the state
     * @param sigSet the signature set
     *
     */
    public record StateFileData(@NonNull MerkleRoot state, @NonNull Hash hash, @Nullable SigSet sigSet) {

        @Override
        @NonNull
        public MerkleRoot state() {
            return state;
        }

        @Override
        @NonNull
        public Hash hash() {
            return hash;
        }

        @Override
        @Nullable
        public SigSet sigSet() {
            return sigSet;
        }
    }

    /**
     * Reads a SignedState from disk using the provided snapshot reader function.
     * @param stateFile the file to read from
     * @return a signed state with it's associated hash (as computed when the state was serialized)
     * @throws IOException if there is any problems with reading from a file
     */
    @NonNull
    public static StateFileData readStateFileData(@NonNull final Path stateFile) throws IOException {
        return deserializeAndDebugOnFailure(
                () -> new BufferedInputStream(new FileInputStream(stateFile.toFile())),
                (final MerkleDataInputStream in) -> {
                    final int fileVersion = readAndCheckStateFileVersion(in);

                    final Path directory = stateFile.getParent();
                    if (fileVersion == INIT_STATE_FILE_VERSION) {
                        return readStateFileDataV1(stateFile, in, directory);
                    } else if (fileVersion == SIG_SET_SEPARATE_STATE_FILE_VERSION) {
                        return readStateFileDataV2(stateFile, in, directory);
                    } else {
                        throw new IOException("Unsupported state file version: " + fileVersion);
                    }
                });
    }

    /**
     * This method reads the state file data from a version 1 state file. This version of the state file contains
     * signature set data.
     */
    @NonNull
    private static StateFileData readStateFileDataV1(
            @NonNull final Path stateFile, @NonNull final MerkleDataInputStream in, @NonNull final Path directory)
            throws IOException {
        try {
            final MerkleRoot state = in.readMerkleTree(directory, SignedStateFileUtils.MAX_MERKLE_NODES_IN_STATE);
            final Hash hash = in.readSerializable();
            final SigSet sigSet = in.readSerializable();
            return new StateFileData(state, hash, sigSet);
        } catch (final IOException e) {
            throw new IOException("Failed to read snapshot file " + stateFile.toFile(), e);
        }
    }

    /**
     * This method reads the state file data from a version 2 state file. This version of the state file
     * doesn't contain signature set data. Instead, the signature set data is stored in a separate file,
     * and the resulting object doesn't have {@link SigSet} field initialized.
     */
    @NonNull
    private static StateFileData readStateFileDataV2(
            @NonNull final Path stateFile, @NonNull final MerkleDataInputStream in, @NonNull final Path directory)
            throws IOException {
        try {
            final MerkleRoot state = in.readMerkleTree(directory, SignedStateFileUtils.MAX_MERKLE_NODES_IN_STATE);
            final Hash hash = in.readSerializable();
            return new StateFileData(state, hash, null);

        } catch (final IOException e) {
            throw new IOException("Failed to read snapshot file " + stateFile.toFile(), e);
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
}
