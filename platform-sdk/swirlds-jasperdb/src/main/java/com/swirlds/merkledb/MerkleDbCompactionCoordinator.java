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

package com.swirlds.merkledb;

import static com.swirlds.common.threading.manager.AdHocThreadManager.getStaticThreadManager;
import static com.swirlds.logging.LogMarker.EXCEPTION;
import static com.swirlds.logging.LogMarker.MERKLE_DB;
import static com.swirlds.merkledb.MerkleDb.MERKLEDB_COMPONENT;
import static java.util.Objects.requireNonNull;

import com.swirlds.common.config.singleton.ConfigurationHolder;
import com.swirlds.common.threading.framework.config.ThreadConfiguration;
import com.swirlds.merkledb.config.MerkleDbConfig;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.io.IOException;
import java.nio.channels.ClosedByInterruptException;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * This class is responsible for coordinating compaction tasks for a {@link MerkleDbDataSource}.
 * It provides convenient API for starting compactions for each of the three storage types. Also, this class makes sure
 * that there are no concurrent compactions for the same storage type. And finally it provides a way to stop all compactions
 * and keep them disabled until they are explicitly enabled again.
 * The compaction tasks are executed in a background thread pool.
 * The number of threads in the pool is defined by {@link MerkleDbConfig#compactionThreads()} property.
 *
 */
@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
class MerkleDbCompactionCoordinator {

    /**
     * Since {@code com.swirlds.platform.Browser} populates settings, and it is loaded before any
     * application classes that might instantiate a data source, the {@link ConfigurationHolder}
     * holder will have been configured by the time this static initializer runs.
     */
    private static final MerkleDbConfig config = ConfigurationHolder.getConfigData(MerkleDbConfig.class);

    /**
     * A thread pool to run compaction tasks.
     */
    private static final AtomicReference<ExecutorService> compactionExecutorServiceRef = new AtomicReference<>();

    private static final Logger logger = LogManager.getLogger(MerkleDbCompactionCoordinator.class);

    public static final String HASH_STORE_DISK_SUFFIX = "HashStoreDisk";
    public static final String OBJECT_KEY_TO_PATH_SUFFIX = "ObjectKeyToPath";
    public static final String PATH_TO_KEY_VALUE_SUFFIX = "PathToKeyValue";
    private final AtomicBoolean compactionEnabled = new AtomicBoolean();
    // we need a map of exactly three elements, one per storage
    final ConcurrentMap<String, InterruptibleCompletableFuture> compactionFuturesByName = new ConcurrentHashMap<>(3);
    private final MerkleDbStatisticsUpdater statisticsUpdater;
    private final String tableName;
    private final Optional<Compactible> objectKeyToPathOpt;
    private final Optional<Compactible> hashesStoreDiskOpt;
    private final Compactible pathToKeyValue;

    /**
     * Creates a new instance of {@link MerkleDbCompactionCoordinator}.
     * @param tableName the name of the table
     * @param statisticsUpdater a statistics updater
     * @param objectKeyToPath an object key to path store
     * @param hashesStoreDisk a hash store
     * @param pathToKeyValue a path to key-value store
     */
    public MerkleDbCompactionCoordinator(
            @NonNull String tableName,
            @NonNull MerkleDbStatisticsUpdater statisticsUpdater,
            @Nullable Compactible objectKeyToPath,
            @Nullable Compactible hashesStoreDisk,
            @NonNull Compactible pathToKeyValue) {
        this.tableName = tableName;
        this.statisticsUpdater = statisticsUpdater;
        this.objectKeyToPathOpt = Optional.ofNullable(objectKeyToPath);
        this.hashesStoreDiskOpt = Optional.ofNullable(hashesStoreDisk);
        this.pathToKeyValue = pathToKeyValue;
    }

    /**
     * Compacts the object key to path store asynchronously if it's present.
     */
    void compactDiskStoreForObjectKeyToPathAsync() {
        objectKeyToPathOpt.ifPresent(
                v -> submitCompactionTaskForExecution(new CompactionTask(tableName + OBJECT_KEY_TO_PATH_SUFFIX) {
                    @Override
                    protected boolean doCompaction() throws IOException, InterruptedException {
                        return v.compact(
                                statisticsUpdater::setLeafKeysStoreCompactionTimeMs,
                                statisticsUpdater::setLeafKeysStoreCompactionSavedSpaceMb);
                    }
                }));
    }

    /**
     * Compacts the hash store asynchronously if it's present.
     */
    void compactDiskStoreForHashesAsync() {
        hashesStoreDiskOpt.ifPresent(
                v -> submitCompactionTaskForExecution(new CompactionTask(tableName + HASH_STORE_DISK_SUFFIX) {
                    @Override
                    protected boolean doCompaction() throws IOException, InterruptedException {
                        return v.compact(
                                statisticsUpdater::setHashesStoreCompactionTimeMs,
                                statisticsUpdater::setHashesStoreCompactionSavedSpaceMb);
                    }
                }));
    }

    /**
     * Compacts the path to key-value store asynchronously.
     */
    void compactPathToKeyValueAsync() {
        submitCompactionTaskForExecution(new CompactionTask(tableName + PATH_TO_KEY_VALUE_SUFFIX) {
            @Override
            protected boolean doCompaction() throws IOException, InterruptedException {
                return pathToKeyValue.compact(
                        statisticsUpdater::setLeavesStoreCompactionTimeMs,
                        statisticsUpdater::setLeavesStoreCompactionSavedSpaceMb);
            }
        });
    }

    /**
     * Enables background compaction.
     */
    void enableBackgroundCompaction() {
        compactionEnabled.set(true);
    }

    /**
     * Stops all compactions in progress and disables background compaction.
     * All subsequent calls to compacting methods will be ignored until {@link #enableBackgroundCompaction()} is called.
     */
    void stopAndDisableBackgroundCompaction() {
        synchronized (compactionFuturesByName) {
            compactionFuturesByName.forEach((k, v) -> v.cancel());
            compactionFuturesByName.clear();
            compactionEnabled.set(false);
        }
    }

    /**
     * Submits a compaction task for execution. If a compaction task for the same storage type is already in progress,
     * the call is effectively no op.
     * @param task a compaction task to execute
     */
    private void submitCompactionTaskForExecution(CompactionTask task) {
        if (!compactionEnabled.get()) {
            return;
        }

        ExecutorService executor = createOrGetCompactingExecutor();

        synchronized (compactionFuturesByName) {
            if (compactionFuturesByName.containsKey(task.id)) {
                CompletableFuture<?> completableFuture =
                        compactionFuturesByName.get(task.id).asCompletableFuture();
                if (completableFuture.isDone()) {
                    compactionFuturesByName.remove(task.id);
                } else {
                    logger.debug(MERKLE_DB.getMarker(), "Compaction for {} is already in progress", task.id);
                    return;
                }
            }

            InterruptibleCompletableFuture<Boolean> future =
                    InterruptibleCompletableFuture.runAsyncInterruptibly(task, executor);
            future.asCompletableFuture().thenAccept(result -> {
                if (result) {
                    statisticsUpdater.updateStoreFileStats();
                    statisticsUpdater.updateOffHeapStats();
                    logger.info("Finished compaction for " + task.id);
                }
            });
            compactionFuturesByName.put(task.id, future);
        }
    }

    /**
     * Creates a thread pool for compaction tasks if it does not exist yet, or returns the existing one.
     * If the existing thread pool is shutdown, it will be replaced with a new one. Note that there is only one
     * compacting thread-pool per JVM.
     * @return a thread pool for compaction tasks
     */
    ExecutorService createOrGetCompactingExecutor() {
        ExecutorService executorService = compactionExecutorServiceRef.get();
        if (executorService == null) {
            executorService = new ThreadPoolExecutor(
                    getCompactingThreadNumber(),
                    getCompactingThreadNumber(),
                    0L,
                    TimeUnit.MILLISECONDS,
                    new LinkedBlockingDeque<>(),
                    new ThreadConfiguration(getStaticThreadManager())
                            .setThreadGroup(new ThreadGroup("Compaction"))
                            .setComponent(MERKLEDB_COMPONENT)
                            .setThreadName("Compacting")
                            .setExceptionHandler((t, ex) ->
                                    logger.error(EXCEPTION.getMarker(), "Uncaught exception during merging", ex))
                            .buildFactory());
            if (!compactionExecutorServiceRef.compareAndSet(null, executorService)) {
                try {
                    executorService.shutdown();
                } catch (Exception e) {
                    logger.error(EXCEPTION.getMarker(), "Failed to shutdown compaction executor service", e);
                }
            }
        } else {
            if (executorService.isShutdown()) {
                compactionExecutorServiceRef.compareAndSet(executorService, null);
                return createOrGetCompactingExecutor();
            }
        }

        return compactionExecutorServiceRef.get();
    }

    int getCompactingThreadNumber() {
        return config.compactionThreads();
    }

    boolean isCompactionEnabled() {
        return compactionEnabled.get();
    }

    /**
     * A helper class representing a task to run compaction for a specific storage type.
     */
    private abstract static class CompactionTask implements Callable<Boolean> {

        private static final Logger logger = LogManager.getLogger(CompactionTask.class);
        final String id;

        public CompactionTask(String id) {
            requireNonNull(id);
            this.id = id;
        }

        protected abstract boolean doCompaction() throws IOException, InterruptedException;

        @Override
        public Boolean call() {
            try {
                return doCompaction();
            } catch (final InterruptedException | ClosedByInterruptException e) {
                Thread.currentThread().interrupt();
                logger.info(MERKLE_DB.getMarker(), "Interrupted while compacting, this is allowed.", e);
            } catch (Throwable e) {
                // It is important that we capture all exceptions here, otherwise a single exception
                // will stop all  future merges from happening.
                logger.error(EXCEPTION.getMarker(), "[{}] Compaction failed", id, e);
            }
            return false;
        }
    }
}
