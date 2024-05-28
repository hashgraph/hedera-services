/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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
import static com.swirlds.logging.legacy.LogMarker.EXCEPTION;
import static com.swirlds.logging.legacy.LogMarker.MERKLE_DB;
import static com.swirlds.merkledb.MerkleDb.MERKLEDB_COMPONENT;

import com.swirlds.common.config.singleton.ConfigurationHolder;
import com.swirlds.common.threading.framework.config.ThreadConfiguration;
import com.swirlds.merkledb.config.MerkleDbConfig;
import com.swirlds.merkledb.files.DataFileCompactor;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.io.IOException;
import java.nio.channels.ClosedByInterruptException;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
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
class MerkleDbCompactionCoordinator {

    private static final Logger logger = LogManager.getLogger(MerkleDbCompactionCoordinator.class);

    /**
     * An executor service to run compaction tasks. Accessed using {@link #getCompactionExecutor()}.
     */
    private static ExecutorService compactionExecutor = null;

    static synchronized ExecutorService getCompactionExecutor() {
        if (compactionExecutor == null) {
            final MerkleDbConfig config = ConfigurationHolder.getConfigData(MerkleDbConfig.class);
            compactionExecutor = new ThreadPoolExecutor(
                    config.compactionThreads(),
                    config.compactionThreads(),
                    50L,
                    TimeUnit.MILLISECONDS,
                    new LinkedBlockingQueue<>(),
                    new ThreadConfiguration(getStaticThreadManager())
                            .setThreadGroup(new ThreadGroup("Compaction"))
                            .setComponent(MERKLEDB_COMPONENT)
                            .setThreadName("Compacting")
                            .setExceptionHandler((t, ex) ->
                                    logger.error(EXCEPTION.getMarker(), "Uncaught exception during merging", ex))
                            .buildFactory());
        }
        return compactionExecutor;
    }

    public static final String HASH_STORE_DISK_SUFFIX = "HashStoreDisk";
    public static final String OBJECT_KEY_TO_PATH_SUFFIX = "ObjectKeyToPath";
    public static final String PATH_TO_KEY_VALUE_SUFFIX = "PathToKeyValue";
    private final AtomicBoolean compactionEnabled = new AtomicBoolean();
    // we need a map of exactly three elements, one per storage
    final ConcurrentMap<String, Future<Boolean>> compactionFuturesByName = new ConcurrentHashMap<>(3);
    private final CompactionTask objectKeyToPathTask;
    private final CompactionTask hashesStoreDiskTask;
    private final CompactionTask pathToKeyValueTask;

    @Nullable
    private final DataFileCompactor objectKeyToPath;

    @Nullable
    private final DataFileCompactor hashesStoreDisk;

    @NonNull
    private final DataFileCompactor pathToKeyValue;

    /**
     * Creates a new instance of {@link MerkleDbCompactionCoordinator}.
     * @param tableName the name of the table
     * @param objectKeyToPath an object key to path store
     * @param hashesStoreDisk a hash store
     * @param pathToKeyValue a path to key-value store
     */
    public MerkleDbCompactionCoordinator(
            @NonNull String tableName,
            @Nullable DataFileCompactor objectKeyToPath,
            @Nullable DataFileCompactor hashesStoreDisk,
            @NonNull DataFileCompactor pathToKeyValue) {
        this.objectKeyToPath = objectKeyToPath;
        this.hashesStoreDisk = hashesStoreDisk;
        this.pathToKeyValue = pathToKeyValue;
        if (objectKeyToPath != null) {
            objectKeyToPathTask = new CompactionTask(tableName + OBJECT_KEY_TO_PATH_SUFFIX, objectKeyToPath);
        } else {
            objectKeyToPathTask = null;
        }
        if (hashesStoreDisk != null) {
            hashesStoreDiskTask = new CompactionTask(tableName + HASH_STORE_DISK_SUFFIX, hashesStoreDisk);
        } else {
            hashesStoreDiskTask = null;
        }
        this.pathToKeyValueTask = new CompactionTask(tableName + PATH_TO_KEY_VALUE_SUFFIX, pathToKeyValue);
    }

    /**
     * Compacts the object key to path store asynchronously if it's present.
     */
    void compactDiskStoreForObjectKeyToPathAsync() {
        if (objectKeyToPathTask == null) {
            return;
        }
        submitCompactionTaskForExecution(objectKeyToPathTask);
    }

    /**
     * Compacts the hash store asynchronously if it's present.
     */
    void compactDiskStoreForHashesAsync() {
        if (hashesStoreDiskTask == null) {
            return;
        }
        submitCompactionTaskForExecution(hashesStoreDiskTask);
    }

    /**
     * Compacts the path to key-value store asynchronously.
     */
    void compactPathToKeyValueAsync() {
        submitCompactionTaskForExecution(pathToKeyValueTask);
    }

    /**
     * Enables background compaction.
     */
    void enableBackgroundCompaction() {
        compactionEnabled.set(true);
    }

    /**
     * Pauses compaction of all data file compactors. It may not stop compaction
     * immediately, but as soon as compaction process needs to update data source state, which is
     * critical for snapshots (e.g. update an index), it will be stopped until {@link
     * #resumeCompaction()}} is called.
     */
    public void pauseCompaction() throws IOException {
        if (hashesStoreDisk != null) {
            hashesStoreDisk.pauseCompaction();
        }

        pathToKeyValue.pauseCompaction();

        if (objectKeyToPath != null) {
            objectKeyToPath.pauseCompaction();
        }
    }
    /** Resumes previously stopped data file collection compaction. */
    public void resumeCompaction() throws IOException {
        if (hashesStoreDisk != null) {
            hashesStoreDisk.resumeCompaction();
        }

        pathToKeyValue.resumeCompaction();

        if (objectKeyToPath != null) {
            objectKeyToPath.resumeCompaction();
        }
    }

    /**
     * Stops all compactions in progress and disables background compaction.
     * All subsequent calls to compacting methods will be ignored until {@link #enableBackgroundCompaction()} is called.
     */
    void stopAndDisableBackgroundCompaction() {
        synchronized (compactionFuturesByName) {
            for (var futureEntry : compactionFuturesByName.values()) {
                futureEntry.cancel(true);
            }
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

        final ExecutorService executor = getCompactionExecutor();

        synchronized (compactionFuturesByName) {
            if (compactionFuturesByName.containsKey(task.id)) {
                Future<?> future = compactionFuturesByName.get(task.id);
                if (future.isDone()) {
                    compactionFuturesByName.remove(task.id);
                } else {
                    logger.debug(MERKLE_DB.getMarker(), "Compaction for {} is already in progress", task.id);
                    return;
                }
            }

            compactionFuturesByName.put(task.id, executor.submit(task));
        }
    }

    boolean isCompactionEnabled() {
        return compactionEnabled.get();
    }

    /**
     * A helper class representing a task to run compaction for a specific storage type.
     */
    private record CompactionTask(@NonNull String id, @NonNull DataFileCompactor compactor)
            implements Callable<Boolean> {
        private static final Logger logger = LogManager.getLogger(CompactionTask.class);

        @Override
        public Boolean call() {
            try {
                return compactor.compact();
            } catch (final InterruptedException | ClosedByInterruptException e) {
                logger.info(MERKLE_DB.getMarker(), "Interrupted while compacting, this is allowed.", e);
            } catch (Exception e) {
                // It is important that we capture all exceptions here, otherwise a single exception
                // will stop all  future merges from happening.
                logger.error(EXCEPTION.getMarker(), "[{}] Compaction failed", id, e);
            }
            return false;
        }
    }
}
