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
import static com.swirlds.merkledb.MerkleDb.MAX_TABLES;
import static com.swirlds.merkledb.MerkleDb.MERKLEDB_COMPONENT;

import com.swirlds.common.config.singleton.ConfigurationHolder;
import com.swirlds.common.threading.framework.config.ThreadConfiguration;
import com.swirlds.merkledb.config.MerkleDbConfig;
import java.io.IOException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
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
public class MerkleDbCompactionCoordinator {

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

    private final MerkleDbDataSource<?, ?> dataSource;
    private final MerkleDbStatisticsUpdater statisticsUpdater;
    private final String tableName;

    public MerkleDbCompactionCoordinator(MerkleDbDataSource<?, ?> dataSource) {
        this.dataSource = dataSource;
        this.statisticsUpdater = dataSource.getStatisticsUpdater();
        tableName = dataSource.getTableName();
    }

    void compactDiskStoreForObjectKeyToPathAsync() {
        submitCompactionTaskForExecution(new CompactionTask(tableName + OBJECT_KEY_TO_PATH_SUFFIX) {
            @Override
            protected boolean doCompaction() throws IOException, InterruptedException {
                assert dataSource.getObjectKeyToPath() != null;
                return dataSource
                        .getObjectKeyToPath()
                        .compact(
                                statisticsUpdater::setLeafKeysStoreCompactionTimeMs,
                                statisticsUpdater::setLeafKeysStoreCompactionSavedSpaceMb);
            }
        });
    }

    void compactDiskStoreForHashesAsync() {
        submitCompactionTaskForExecution(new CompactionTask(tableName + HASH_STORE_DISK_SUFFIX) {
            @Override
            protected boolean doCompaction() throws IOException, InterruptedException {
                return
                        dataSource
                        .getHashStoreDisk()
                        .compact(
                                statisticsUpdater::setHashesStoreCompactionTimeMs,
                                statisticsUpdater::setHashesStoreCompactionSavedSpaceMb);
            }
        });
    }

    void compactPathToKeyValueAsync() {
        submitCompactionTaskForExecution(new CompactionTask(tableName + PATH_TO_KEY_VALUE_SUFFIX) {
            @Override
            protected boolean doCompaction() throws IOException, InterruptedException {
                return dataSource
                        .getPathToKeyValue()
                        .compact(
                                statisticsUpdater::setLeavesStoreCompactionTimeMs,
                                statisticsUpdater::setLeavesStoreCompactionSavedSpaceMb);
            }
        });
    }

    public void enableBackgroundCompaction() {
        compactionEnabled.set(true);
    }

    public void stopAndDisableBackgroundCompaction() {
        synchronized (createOrGetCompactingExecutor()) {
            compactionFuturesByName.forEach((k, v) -> v.cancel(true));
            compactionFuturesByName.clear();
        }
        compactionEnabled.set(false);
    }

    @SuppressWarnings("unchecked")
    private void submitCompactionTaskForExecution(CompactionTask task) {
        if (!compactionEnabled.get()) {
            return;
        }

        if (createOrGetCompactingExecutor().isShutdown()) {
            logger.info(
                    MERKLE_DB.getMarker(),
                    "Compaction for {} is not started, because the executor is shutdown",
                    task.id);
            return;
        }

        ExecutorService executor = createOrGetCompactingExecutor();

        synchronized (executor) {
            if (compactionFuturesByName.containsKey(task.id)) {
                CompletableFuture<?> completableFuture = compactionFuturesByName.get(task.id).asCompletableFuture();
                if (completableFuture.isDone()) {
                    compactionFuturesByName.remove(task.id);
                } else {
                    logger.debug(MERKLE_DB.getMarker(), "Compaction for {} is already in progress", task.id);
                    return;
                }
            }

            InterruptibleCompletableFuture<Boolean> future = InterruptibleCompletableFuture.runAsyncInterruptibly(task, executor);
            future.asCompletableFuture()
                    .thenAccept(result -> {
                        if (result) {
                            statisticsUpdater.updateStoreFileStats();
                            statisticsUpdater.updateOffHeapStats();
                            logger.info("Finished compaction for " + task.id);
                        }
                    });
            compactionFuturesByName.put(task.id, future);
        }
    }

    ExecutorService createOrGetCompactingExecutor() {
        ExecutorService executorService = compactionExecutorServiceRef.get();
        if(executorService == null) {
            executorService = new ThreadPoolExecutor(
                    config.compactionThreads(),
                    config.compactionThreads(),
                    0L,
                    TimeUnit.MILLISECONDS,
                    new ArrayBlockingQueue<>(MAX_TABLES),
                    new ThreadConfiguration(getStaticThreadManager())
                            .setThreadGroup(new ThreadGroup("Compaction"))
                            .setComponent(MERKLEDB_COMPONENT)
                            .setThreadName("Compacting")
                            .setExceptionHandler((t, ex) ->
                                    logger.error(EXCEPTION.getMarker(), "Uncaught exception during merging", ex))
                            .buildFactory());
            if(!compactionExecutorServiceRef.compareAndSet(null, executorService)) {
                try {
                    executorService.shutdown();
                } catch (Exception e) {
                    logger.error(EXCEPTION.getMarker(), "Failed to shutdown compaction executor service", e);
                }
            }
        }

        return compactionExecutorServiceRef.get();
    }

    boolean isCompactionEnabled() {
        return compactionEnabled.get();
    }
}
