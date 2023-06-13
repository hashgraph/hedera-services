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
 * A standard implementation of a {@link RecycleBin}.
 */
class RecycleBinImpl implements RecycleBin {

    private static final Logger logger = LogManager.getLogger(RecycleBinImpl.class);

    private final Path recycleBinPath;
    private final AutoClosableLock lock = Locks.createAutoLock();

    /**
     * Create a new recycle bin.
     *
     * @param configuration the configuration object
     * @param selfId        the ID of this node
     * @throws IOException if the recycle bin directory could not be created
     */
    public RecycleBinImpl(@NonNull final Configuration configuration, @NonNull final NodeId selfId) throws IOException {
        Objects.requireNonNull(selfId);

        final RecycleBinConfig recycleBinConfig = configuration.getConfigData(RecycleBinConfig.class);
        final StateConfig stateConfig = configuration.getConfigData(StateConfig.class);

        recycleBinPath = recycleBinConfig.getRecycleBinPath(stateConfig, selfId);
        Files.createDirectories(recycleBinPath);
    }

    /**
     * {@inheritDoc}
     */
    @Override
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
     * {@inheritDoc}
     */
    @Override
    public void clear() throws IOException {
        try (final Locked ignored = lock.lock()) {
            FileUtils.deleteDirectory(recycleBinPath);
            Files.createDirectories(recycleBinPath);
        }
    }
}
