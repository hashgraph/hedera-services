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

import static com.swirlds.common.io.utility.FileUtils.getAbsolutePath;
import static com.swirlds.logging.LogMarker.STATE_TO_DISK;

import com.swirlds.common.io.utility.FileUtils;
import com.swirlds.common.system.NodeId;
import com.swirlds.platform.Settings;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Utility methods for dealing with signed states on disk.
 */
public final class SignedStateFileUtils {

    private static final Logger logger = LogManager.getLogger(SignedStateFileUtils.class);

    /**
     * Fun trivia: the file extension ".swh" stands for "SWirlds Hashgraph", although
     * this is a bit misleading... as this file doesn't actually contain a hashgraph.
     */
    public static final String SIGNED_STATE_FILE_NAME = "SignedState.swh";

    public static final String HASH_INFO_FILE_NAME = "hashInfo.txt";

    /**
     * The signed state file was not versioned before, this byte was introduced to mark a versioned file
     */
    public static final byte VERSIONED_FILE_BYTE = Byte.MAX_VALUE;

    /**
     * The current version of the signed state file
     */
    public static final int FILE_VERSION = 1;

    public static final int MAX_MERKLE_NODES_IN_STATE = Integer.MAX_VALUE;

    private SignedStateFileUtils() {}

    /**
     * <p>
     * Get the base directory where all states will be stored.
     * </p>
     *
     * <pre>
     * e.g. data/saved/
     *      |--------|
     *          |
     *       location where
     *       states are saved
     * </pre>
     *
     * @return the base directory for all signed state files
     */
    public static Path getSignedStatesBaseDirectory() {
        return getAbsolutePath(Settings.getInstance().getState().savedStateDirectory);
    }

    /**
     * <p>
     * Get the directory that contains saved states for a particular app.
     * </p>
     *
     * <pre>
     * e.g. data/saved/com.swirlds.foobar
     *      |--------| |----------------|
     *          |             |
     *          |         mainClassName
     *          |
     *       location where
     *       states are saved
     * </pre>
     *
     * @param mainClassName
     * 		the name of the app
     * @return the path of a directory, may not exist
     */
    public static Path getSignedStatesDirectoryForApp(final String mainClassName) {
        return getSignedStatesBaseDirectory().resolve(mainClassName);
    }

    /**
     * <p>
     * Get the directory that contains contains saved states for a particular node.
     * </p>
     *
     * <pre>
     * e.g. data/saved/com.swirlds.foobar/1234
     *      |--------| |----------------| |--|
     *          |             |            |
     *          |         mainClassName    |
     *          |                          |
     *       location where              selfId
     *       states are saved
     * </pre>
     *
     * @param mainClassName
     * 		the name of the app
     * @param selfId
     * 		the ID of this node
     * @return the path of a directory, may not exist
     */
    public static Path getSignedStatesDirectoryForNode(final String mainClassName, final NodeId selfId) {

        return getSignedStatesDirectoryForApp(mainClassName).resolve(Long.toString(selfId.getId()));
    }

    /**
     * <p>
     * Get the directory that contains saved states for a particular swirld (i.e. an instance of an app).
     * </p>
     *
     * <pre>
     * e.g. data/saved/com.swirlds.foobar/1234/mySwirld
     *      |--------| |----------------| |--| |------|
     *          |             |            |       |
     *          |         mainClassName    |    swirldName
     *          |                          |
     *       location where              selfId
     *       states are saved
     * </pre>
     *
     * @param mainClassName
     * 		the name of the app
     * @param selfId
     * 		the ID of this node
     * @param swirldName
     * 		the name of the swirld
     * @return the path of a directory, may not exist
     */
    public static Path getSignedStatesDirectoryForSwirld(
            final String mainClassName, final NodeId selfId, final String swirldName) {

        return getSignedStatesDirectoryForNode(mainClassName, selfId).resolve(swirldName);
    }

    /**
     * <p>
     * Get the fully qualified path to the directory for a particular signed state. This directory might not exist.
     * </p>
     *
     * <pre>
     * e.g. data/saved/com.swirlds.foobar/1234/mySwirld/1000
     *      |--------| |----------------| |--| |------| |--|
     *          |             |            |      |      |
     *          |         mainClassName    |      |    round
     *          |                          |  swirldName
     *       location where              selfId
     *       states are saved
     *
     * </pre>
     *
     * @param mainClassName
     * 		the name of the app
     * @param selfId
     * 		the ID of this node
     * @param swirldName
     * 		the name of the swirld
     * @param round
     * 		the round number of the state
     * @return the path of the signed state for the particular round
     */
    public static Path getSignedStateDirectory(
            final String mainClassName, final NodeId selfId, final String swirldName, final long round) {

        return getSignedStatesDirectoryForSwirld(mainClassName, selfId, swirldName)
                .resolve(Long.toString(round));
    }

    /**
     * Clean out all files in {@link #getSignedStatesBaseDirectory()} except for
     * the signed state files of a particular app.
     *
     * @param mainClassName
     * 		the name of the app whose state files should NOT be deleted
     */
    public static void cleanStateDirectory(final String mainClassName) {

        final Path baseDirectory = getSignedStatesBaseDirectory();
        final Path excludedDirectory = getSignedStatesDirectoryForApp(mainClassName);

        logger.info(
                STATE_TO_DISK.getMarker(),
                "Cleaning up saved state directory, "
                        + "all files and directories in {} (with the exception of {}) will be deleted.",
                baseDirectory,
                excludedDirectory);

        try (final Stream<Path> childPaths = Files.walk(baseDirectory, 1)) {
            childPaths.forEach(path -> {
                if (!path.equals(excludedDirectory) && !path.equals(baseDirectory)) {
                    try {
                        FileUtils.deleteDirectoryAndLog(path);
                    } catch (final IOException e) {
                        // Intentionally ignored, deleteDirectoryAndLog() will take care of logging an exception
                    }
                }
            });
        } catch (final IOException e) {
            logger.warn(
                    STATE_TO_DISK.getMarker(),
                    "encountered problem while attempting to clean state directory {}",
                    baseDirectory,
                    e);
        }
    }
}
