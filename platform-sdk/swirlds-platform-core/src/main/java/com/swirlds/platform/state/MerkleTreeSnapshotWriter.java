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

import static com.swirlds.common.io.utility.FileUtils.writeAndFlush;
import static com.swirlds.logging.legacy.LogMarker.EXCEPTION;
import static com.swirlds.logging.legacy.LogMarker.STATE_TO_DISK;
import static com.swirlds.platform.state.snapshot.SignedStateFileUtils.SIGNED_STATE_FILE_NAME;
import static com.swirlds.platform.state.snapshot.SignedStateFileUtils.SIG_SET_SEPARATE_STATE_FILE_VERSION;
import static com.swirlds.platform.state.snapshot.SignedStateFileUtils.VERSIONED_FILE_BYTE;

import com.swirlds.common.io.streams.MerkleDataOutputStream;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.nio.file.Path;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Utility class for writing a snapshot of a {@link MerkleRoot} to disk.
 */
public final class MerkleTreeSnapshotWriter {

    private static final Logger logger = LogManager.getLogger(MerkleTreeSnapshotWriter.class);

    private MerkleTreeSnapshotWriter() {
        // prevent instantiation
    }

    /**
     * Writes a snapshot of the given {@link MerkleRoot} to the given {@link Path}.
     * @param merkleRoot the {@link MerkleRoot} to write
     * @param targetPath the {@link Path} to write the snapshot to
     */
    static void createSnapshot(@NonNull final MerkleRoot merkleRoot, @NonNull final Path targetPath) {
        final long round = merkleRoot.getReadablePlatformState().getRound();
        logger.info(STATE_TO_DISK.getMarker(), "Creating a snapshot on demand in {} for round {}", targetPath, round);
        try {
            writeMerkleRootToFile(targetPath, merkleRoot);
            logger.info(
                    STATE_TO_DISK.getMarker(),
                    "Successfully created a snapshot on demand in {}  for round {}",
                    targetPath,
                    round);
        } catch (final Throwable e) {
            logger.error(
                    EXCEPTION.getMarker(),
                    "Unable to write a snapshot on demand for round {} to {}.",
                    round,
                    targetPath,
                    e);
        }
    }

    private static void writeMerkleRootToFile(@NonNull final Path directory, @NonNull final MerkleRoot merkleRoot)
            throws IOException {
        writeAndFlush(
                directory.resolve(SIGNED_STATE_FILE_NAME), out -> writeMerkleRootToStream(out, directory, merkleRoot));
    }

    private static void writeMerkleRootToStream(
            @NonNull final MerkleDataOutputStream out,
            @NonNull final Path directory,
            @NonNull final MerkleRoot merkleRoot)
            throws IOException {
        out.write(VERSIONED_FILE_BYTE);
        out.writeInt(SIG_SET_SEPARATE_STATE_FILE_VERSION);
        out.writeProtocolVersion();
        out.writeMerkleTree(directory, merkleRoot);
        out.writeSerializable(merkleRoot.getHash(), true);
    }
}
