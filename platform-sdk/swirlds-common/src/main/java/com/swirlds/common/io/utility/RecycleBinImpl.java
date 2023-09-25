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

import static com.swirlds.common.io.utility.FileUtils.deleteDirectory;
import static com.swirlds.logging.legacy.LogMarker.EXCEPTION;
import static com.swirlds.logging.legacy.LogMarker.STARTUP;

import com.swirlds.base.state.Startable;
import com.swirlds.base.state.Stoppable;
import com.swirlds.base.time.Time;
import com.swirlds.common.config.StateConfig;
import com.swirlds.common.io.config.RecycleBinConfig;
import com.swirlds.common.metrics.IntegerGauge;
import com.swirlds.common.metrics.Metrics;
import com.swirlds.common.system.NodeId;
import com.swirlds.common.threading.framework.StoppableThread;
import com.swirlds.common.threading.framework.config.StoppableThreadConfiguration;
import com.swirlds.common.threading.locks.AutoClosableLock;
import com.swirlds.common.threading.locks.Locks;
import com.swirlds.common.threading.locks.locked.Locked;
import com.swirlds.common.threading.manager.ThreadManager;
import com.swirlds.common.utility.CompareTo;
import com.swirlds.config.api.Configuration;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * A standard implementation of a {@link RecycleBin}.
 */
public class RecycleBinImpl implements RecycleBin, Startable, Stoppable {

    private static final Logger logger = LogManager.getLogger(RecycleBinImpl.class);

    private final Time time;
    private final Path recycleBinPath;
    private final Duration maximumFileAge;

    /**
     * The number of top level files in the recycle bin directory.
     */
    private int topLevelRecycledFileCount;

    private final StoppableThread cleanupThread;

    private final AutoClosableLock lock = Locks.createAutoLock();

    private static final IntegerGauge.Config RECYLED_FILE_COUNT_CONFIG = new IntegerGauge.Config(
                    "platform", "recycled-file-count")
            .withDescription("The number of top level files/directories in the recycle bin, non recursive.");
    private final IntegerGauge recycledFileCountMetric;

    /**
     * Create a new recycle bin.
     *
     * @param configuration the configuration object
     * @param metrics       manages the creation of metrics
     * @param threadManager manages the creation of threads
     * @param time          provides wall clock time
     * @param selfId        the ID of this node
     * @throws IOException if the recycle bin directory could not be created
     */
    public RecycleBinImpl(
            @NonNull final Configuration configuration,
            @NonNull final Metrics metrics,
            @NonNull final ThreadManager threadManager,
            @NonNull final Time time,
            @NonNull final NodeId selfId)
            throws IOException {

        Objects.requireNonNull(selfId);
        Objects.requireNonNull(threadManager);
        this.time = Objects.requireNonNull(time);

        final RecycleBinConfig recycleBinConfig = configuration.getConfigData(RecycleBinConfig.class);
        final StateConfig stateConfig = configuration.getConfigData(StateConfig.class);

        maximumFileAge = recycleBinConfig.maximumFileAge();
        recycleBinPath = recycleBinConfig.getStorageLocation(stateConfig, selfId);
        Files.createDirectories(recycleBinPath);
        topLevelRecycledFileCount = countRecycledFiles(recycleBinPath);

        recycledFileCountMetric = metrics.getOrCreate(RECYLED_FILE_COUNT_CONFIG);
        recycledFileCountMetric.set(topLevelRecycledFileCount);

        cleanupThread = new StoppableThreadConfiguration<>(threadManager)
                .setComponent("platform")
                .setThreadName("recycle-bin-cleanup")
                .setMinimumPeriod(recycleBinConfig.collectionPeriod())
                .setWork(this::cleanup)
                .build();
    }

    /**
     * Manually clear the recycle bin.
     */
    public void clear() throws IOException {
        try (final Locked ignored = lock.lock()) {
            deleteDirectory(recycleBinPath);
            Files.createDirectories(recycleBinPath);
            topLevelRecycledFileCount = 0;
            recycledFileCountMetric.set(0);
        }
    }

    /**
     * Count the number of top level files in the recycle bin directory.
     */
    private static int countRecycledFiles(@NonNull final Path recycleBinPath) {
        try (final Stream<Path> stream = Files.list(recycleBinPath)) {
            return (int) stream.count();
        } catch (final IOException e) {
            logger.error(EXCEPTION.getMarker(), "Error counting recycle bin files", e);
            return 0;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void recycle(@NonNull final Path path) throws IOException {
        if (!Files.exists(path)) {
            // FUTURE WORK: https://github.com/hashgraph/hedera-services/issues/8621
            logger.warn(STARTUP.getMarker(), "Cannot recycle non-existent file: {}", path);
            return;
        }

        try (final Locked ignored = lock.lock()) {
            final Path fileName = path.getFileName();
            final Path recyclePath = recycleBinPath.resolve(fileName);

            if (Files.exists(recyclePath)) {
                logger.info(
                        STARTUP.getMarker(),
                        "File with the name '{}' already exists in the recycle bin, deleting previous copy.",
                        fileName);
                deleteDirectory(recyclePath);
            } else {
                topLevelRecycledFileCount++;
                recycledFileCountMetric.set(topLevelRecycledFileCount);
            }

            Files.move(path, recyclePath);
        }
    }

    /**
     * Deletes all recycle bin files/directories that are older than the maximum file age.
     */
    private void cleanup() {
        final Instant now = time.now();

        final AtomicInteger deletedCount = new AtomicInteger();

        try (final Locked ignored = lock.lock()) {
            try (final Stream<Path> stream = Files.list(recycleBinPath)) {
                stream.forEach(path -> {
                    try {
                        final Instant lastModified =
                                Files.getLastModifiedTime(path).toInstant();
                        final Duration age = Duration.between(lastModified, now);

                        if (CompareTo.isGreaterThan(age, maximumFileAge)) {
                            deleteDirectory(path);
                            deletedCount.incrementAndGet();
                            topLevelRecycledFileCount--;
                        }
                    } catch (final IOException e) {
                        logger.error(EXCEPTION.getMarker(), "Error cleaning up recycle bin file {}", path, e);
                    }
                });

            } catch (final IOException e) {
                logger.error(EXCEPTION.getMarker(), "Error cleaning up recycle bin", e);
            }
            recycledFileCountMetric.set(topLevelRecycledFileCount);
        }

        logger.info(STARTUP.getMarker(), "Deleted {} files from the recycle bin.", deletedCount.get());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void start() {
        cleanupThread.start();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void stop() {
        cleanupThread.stop();
    }
}
