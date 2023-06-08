/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.swirlds.common.io.utility;

import static com.swirlds.logging.LogMarker.EXCEPTION;

import com.swirlds.common.config.StateConfig;
import com.swirlds.common.io.config.RecycleBinConfig;
import com.swirlds.common.system.NodeId;
import com.swirlds.common.threading.locks.AutoClosableLock;
import com.swirlds.common.threading.locks.Locks;
import com.swirlds.common.threading.locks.locked.Locked;
import com.swirlds.config.api.Configuration;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * This class provides the abstraction of deleting a file, but actually moves the file to a temporary location in case
 * the file becomes useful later for debugging.
 * <p>
 * Data moved to the recycle bin persist in the temporary location for an unspecified amount of time, perhaps even no
 * time at all. Files in this temporary location may be deleted at any time without warning. It is never ok to write
 * code that depends on the existence of files in this temporary location. Files in this temporary location should be
 * treated as deleted by java code, and only used for debugging purposes.
 */
public class RecycleBin {

    private static final Logger logger = LogManager.getLogger(RecycleBin.class);

    private final Path recycleBinPath;
    private final AutoClosableLock lock = Locks.createAutoLock();

    /**
     * Create a new recycle bin.
     *
     * @param configuration the configuration object
     * @param selfId        the ID of this node
     * @throws IOException if the recycle bin directory could not be created
     */
    public RecycleBin(@NonNull final Configuration configuration, @NonNull final NodeId selfId) throws IOException {
        Objects.requireNonNull(selfId);

        final RecycleBinConfig recycleBinConfig = configuration.getConfigData(RecycleBinConfig.class);
        final StateConfig stateConfig = configuration.getConfigData(StateConfig.class);

        recycleBinPath = recycleBinConfig.getRecycleBinPath(stateConfig, selfId);
        Files.createDirectories(recycleBinPath);
    }

    /**
     * Remove a file or directory tree from its current location and move it to a temporary location.
     * <p></p>
     * Recycled data will persist in the temporary location for an unspecified amount of time, perhaps even no time at
     * all. Files in this temporary location may be deleted at any time without warning. It is never ok to write code
     * that depends on the existence of files in this temporary location. Files in this temporary location should be
     * treated as deleted by java code, and only used for debugging purposes.
     *
     * @param path the file or directory to recycle
     */
    public void recycle(@NonNull final Path path) throws IOException {
        if (!Files.exists(path)) {
            logger.warn(EXCEPTION.getMarker(), "Cannot recycle non-existent file: {}", path);
            return;
        }

        try (final Locked ignored = lock.lock()) {
            final Path fileName = path.getFileName();
            final Path recyclePath = recycleBinPath.resolve(fileName);

            if (Files.exists(recyclePath)) {
                Files.delete(recyclePath);
            }

            Files.move(path, recyclePath);
        }
    }

    /**
     * Delete all recycled files.
     */
    public void clear() throws IOException {
        try (final Locked ignored = lock.lock()) {
            FileUtils.deleteDirectory(recycleBinPath);
            Files.createDirectories(recycleBinPath);
        }
    }
}
