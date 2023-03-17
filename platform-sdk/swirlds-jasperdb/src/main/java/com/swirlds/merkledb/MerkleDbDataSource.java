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

package com.swirlds.merkledb;

import static com.swirlds.common.threading.manager.AdHocThreadManager.getStaticThreadManager;
import static com.swirlds.common.utility.Units.BYTES_TO_BITS;
import static com.swirlds.common.utility.Units.BYTES_TO_MEBIBYTES;
import static com.swirlds.logging.LogMarker.ERROR;
import static com.swirlds.logging.LogMarker.EXCEPTION;
import static com.swirlds.logging.LogMarker.MERKLE_DB;
import static com.swirlds.merkledb.KeyRange.INVALID_KEY_RANGE;

import com.swirlds.common.crypto.Hash;
import com.swirlds.common.metrics.FunctionGauge;
import com.swirlds.common.metrics.Metrics;
import com.swirlds.common.threading.framework.config.ThreadConfiguration;
import com.swirlds.common.utility.Units;
import com.swirlds.merkledb.collections.HashList;
import com.swirlds.merkledb.collections.HashListByteBuffer;
import com.swirlds.merkledb.collections.LongList;
import com.swirlds.merkledb.collections.LongListDisk;
import com.swirlds.merkledb.collections.LongListOffHeap;
import com.swirlds.merkledb.files.DataFileCollection;
import com.swirlds.merkledb.files.DataFileCommon;
import com.swirlds.merkledb.files.DataFileReader;
import com.swirlds.merkledb.files.MemoryIndexDiskKeyValueStore;
import com.swirlds.merkledb.files.VirtualInternalRecordSerializer;
import com.swirlds.merkledb.files.VirtualLeafRecordSerializer;
import com.swirlds.merkledb.files.hashmap.Bucket;
import com.swirlds.merkledb.files.hashmap.HalfDiskHashMap;
import com.swirlds.merkledb.files.hashmap.HalfDiskVirtualKeySet;
import com.swirlds.merkledb.files.hashmap.VirtualKeySetSerializer;
import com.swirlds.merkledb.serialize.KeyIndexType;
import com.swirlds.merkledb.serialize.KeySerializer;
import com.swirlds.merkledb.settings.MerkleDbSettings;
import com.swirlds.merkledb.settings.MerkleDbSettingsFactory;
import com.swirlds.virtualmap.VirtualKey;
import com.swirlds.virtualmap.VirtualLongKey;
import com.swirlds.virtualmap.VirtualValue;
import com.swirlds.virtualmap.datasource.VirtualDataSource;
import com.swirlds.virtualmap.datasource.VirtualInternalRecord;
import com.swirlds.virtualmap.datasource.VirtualKeySet;
import com.swirlds.virtualmap.datasource.VirtualLeafRecord;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.channels.ClosedByInterruptException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.LongSummaryStatistics;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.IntConsumer;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class MerkleDbDataSource<K extends VirtualKey<? super K>, V extends VirtualValue>
        implements VirtualDataSource<K, V> {

    private static final Logger logger = LogManager.getLogger(MerkleDbDataSource.class);

    /**
     * Since {@code com.swirlds.platform.Browser} populates settings, and it is loaded before any
     * application classes that might instantiate a data source, the {@link MerkleDbSettingsFactory}
     * holder will have been configured by the time this static initializer runs.
     */
    private static final MerkleDbSettings settings = MerkleDbSettingsFactory.get();

    /** Label for database component used in logging, stats, etc. */
    private static final String MERKLEDB_COMPONENT = "merkledb";

    /** Count of open database instances */
    private static final LongAdder COUNT_OF_OPEN_DATABASES = new LongAdder();

    private static final FunctionGauge.Config<Long> COUNT_OF_OPEN_DATABASES_CONFIG = new FunctionGauge.Config<>(
                    MerkleDbStatistics.STAT_CATEGORY,
                    "merkledb_count",
                    Long.class,
                    MerkleDbDataSource::getCountOfOpenDatabases)
            .withDescription("the number of MerkleDb instances that have been created but not" + " released")
            .withFormat("%d");

    /** The version number for format of current data files */
    private static class MetadataFileFormatVersion {
        public static final int ORIGINAL = 1;
        public static final int KEYRANGE_ONLY = 2;
    }

    /**
     * The number of threads to use for merging thread pool. THIS IS ALWAYS 1. As merging is not
     * designed for multiple merges happening concurrently.
     */
    private static final int NUMBER_OF_MERGING_THREADS = 1;

    /** Virtual database instance that hosts this data source. */
    private final MerkleDb database;

    /** Table name. Used as a subdir name in the database directory */
    private final String tableName;

    /** Table ID used by this data source in its database instance. */
    private final int tableId;

    /**
     * Table config, includes key and value serializers as well as a few other non-global params.
     */
    private final MerkleDbTableConfig<K, V> tableConfig;

    /** We have an optimized mode when the keys can be represented by a single long */
    private final boolean isLongKeyMode;

    /**
     * In memory off-heap store for path to disk location, this is used for internal hashes store.
     */
    private final LongList pathToDiskLocationInternalNodes;

    /** In memory off-heap store for path to disk location, this is used by leave store. */
    private final LongList pathToDiskLocationLeafNodes;

    /**
     * In memory off-heap store for internal node hashes. This data is never stored on disk so on
     * load from disk, this will be empty. That should cause all internal node hashes to have to be
     * computed on the first round which will be expensive.
     */
    private final HashList internalHashStoreRam;

    /**
     * On disk store for internal hashes. Can be null if all hashes are being stored in ram by
     * setting internalHashesRamToDiskThreshold to Long.MAX_VALUE.
     */
    private final MemoryIndexDiskKeyValueStore<VirtualInternalRecord> internalHashStoreDisk;

    /** True when internalHashesRamToDiskThreshold is less than Long.MAX_VALUE */
    private final boolean hasDiskStoreForInternalHashes;

    /**
     * In memory off-heap store for key to path map, this is used when isLongKeyMode=true and keys
     * are longs
     */
    private final LongList longKeyToPath;

    /**
     * Mixed disk and off-heap memory store for key to path map, this is used if
     * isLongKeyMode=false, and we have complex keys.
     */
    private final HalfDiskHashMap<K> objectKeyToPath;

    /** Mixed disk and off-heap memory store for path to leaf key, hash and value */
    private final MemoryIndexDiskKeyValueStore<VirtualLeafRecord<K, V>> pathToHashKeyValue;

    /**
     * Cache size for reading virtual leaf records. Initialized in data source creation time from
     * JasperDB settings. If the value is zero, leaf records cache isn't used.
     */
    private final int leafRecordCacheSize;

    /**
     * Virtual leaf records cache. It's a simple array indexed by leaf keys % cache size. Cache
     * eviction is not needed, as array size is fixed and can be configured in JasperDB settings.
     * Index conflicts are resolved in a very straightforward way: whatever entry is read last, it's
     * put to the cache.
     */
    @SuppressWarnings("rawtypes")
    private final VirtualLeafRecord[] leafRecordCache;

    /** ScheduledThreadPool for executing merges */
    private final ScheduledThreadPoolExecutor mergingExecutor;

    /** Future for scheduled merging thread */
    private ScheduledFuture<?> mergingFuture = null;

    /** Thread pool storing internal records */
    private final ExecutorService storeInternalExecutor;

    /** Thread pool storing key-to-path mappings */
    private final ExecutorService storeKeyToPathExecutor;

    /** Thread pool creating snapshots, it is unbounded in threads, but we use at most 7 */
    private final ExecutorService snapshotExecutor;

    /** Flag for if a snapshot is in progress */
    private final AtomicBoolean snapshotInProgress = new AtomicBoolean(false);

    /**
     * A lock for snapshots / flushes synchronization. Snapshots must be protected against
     * concurrent saves of new data. Snapshots can not start while an existing save is happening and
     * save can not start while a snapshot is active.
     */
    private final ReentrantLock flushLock = new ReentrantLock();

    /** The range of valid leaf paths for data currently stored by this data source. */
    private volatile KeyRange validLeafPathRange = INVALID_KEY_RANGE;

    private MerkleDbStatistics statistics;

    /**
     * When we register stats for the first instance, also register the global stats. If true then
     * this is the first time stats are being registered for an instance.
     */
    private static final AtomicBoolean firstStatRegistration = new AtomicBoolean(true);

    private final boolean compactionEnabled;

    /** When was the last medium-sized merge, only touched from single merge thread. */
    private Instant lastMediumMerge;

    /** When was the last full merge, only touched from single merge thread. */
    private Instant lastFullMerge;

    /** Paths to all database files and directories */
    private final MerkleDbPaths dbPaths;

    private final AtomicBoolean closed = new AtomicBoolean(false);

    /** A nanosecond-precise Clock */
    private final NanoClock clock = new NanoClock();

    public MerkleDbDataSource(
            final MerkleDb database,
            final String tableName,
            final int tableId,
            final MerkleDbTableConfig<K, V> tableConfig,
            final boolean compactionEnabled)
            throws IOException {
        this.database = database;
        this.tableName = tableName;
        this.tableId = tableId;
        this.tableConfig = tableConfig;
        this.compactionEnabled = compactionEnabled;

        // updated count of open databases
        COUNT_OF_OPEN_DATABASES.increment();

        // create thread group with label
        final ThreadGroup threadGroup = new ThreadGroup("MerkleDb-" + tableName);
        // create scheduledThreadPool for executing merges
        mergingExecutor = new ScheduledThreadPoolExecutor(
                NUMBER_OF_MERGING_THREADS,
                new ThreadConfiguration(getStaticThreadManager())
                        .setThreadGroup(threadGroup)
                        .setComponent(MERKLEDB_COMPONENT)
                        .setThreadName("Merging")
                        .setExceptionHandler((t, ex) -> logger.error(
                                EXCEPTION.getMarker(), "[{}] Uncaught exception during merging", tableName, ex))
                        .buildFactory());
        // create thread pool storing internal records
        storeInternalExecutor = Executors.newSingleThreadExecutor(new ThreadConfiguration(getStaticThreadManager())
                .setComponent(MERKLEDB_COMPONENT)
                .setThreadGroup(threadGroup)
                .setThreadName("Store Internal Records")
                .setExceptionHandler((t, ex) ->
                        logger.error(EXCEPTION.getMarker(), "[{}] Uncaught exception during storing", tableName, ex))
                .buildFactory());
        // create thread pool storing key-to-path mappings
        storeKeyToPathExecutor = Executors.newSingleThreadExecutor(new ThreadConfiguration(getStaticThreadManager())
                .setComponent(MERKLEDB_COMPONENT)
                .setThreadGroup(threadGroup)
                .setThreadName("Store Key to Path")
                .setExceptionHandler((t, ex) -> logger.error(
                        EXCEPTION.getMarker(), "[{}] Uncaught exception during storing" + " keys", tableName, ex))
                .buildFactory());
        // thread pool creating snapshots, it is unbounded in threads, but we use at most 7
        snapshotExecutor = Executors.newCachedThreadPool(new ThreadConfiguration(getStaticThreadManager())
                .setComponent(MERKLEDB_COMPONENT)
                .setThreadGroup(threadGroup)
                .setThreadName("Snapshot")
                .setExceptionHandler(
                        (t, ex) -> logger.error(EXCEPTION.getMarker(), "Uncaught exception during snapshots", ex))
                .buildFactory());

        final Path storageDir = database.getTableDir(tableName);
        dbPaths = new MerkleDbPaths(storageDir);

        // check if we are loading an existing database or creating a new one
        if (Files.exists(storageDir)) {
            // read metadata
            if (Files.exists(dbPaths.metadataFile)) {
                loadMetadata(dbPaths.metadataFile);
            } else {
                logger.info(
                        MERKLE_DB.getMarker(),
                        "[{}] Loading existing set of data files but no metadata file was found in" + " [{}]",
                        tableName,
                        storageDir.toAbsolutePath());
                throw new IOException("Can not load an existing MerkleDbDataSource from ["
                        + storageDir.toAbsolutePath()
                        + "] because metadata file is missing");
            }
        } else {
            Files.createDirectories(storageDir);
        }

        // data item serializers for internal/leaf file collections
        final VirtualInternalRecordSerializer internalRecordSerializer = new VirtualInternalRecordSerializer();
        final VirtualLeafRecordSerializer<K, V> leafRecordSerializer = new VirtualLeafRecordSerializer<>(tableConfig);

        // create path to disk location index
        final boolean forceIndexRebuilding = settings.isIndexRebuildingEnforced();
        if (tableConfig.isPreferDiskBasedIndices()) {
            pathToDiskLocationInternalNodes = new LongListDisk(dbPaths.pathToDiskLocationInternalNodesFile);
        } else if (Files.exists(dbPaths.pathToDiskLocationInternalNodesFile) && !forceIndexRebuilding) {
            pathToDiskLocationInternalNodes = new LongListOffHeap(dbPaths.pathToDiskLocationInternalNodesFile);
        } else {
            pathToDiskLocationInternalNodes = new LongListOffHeap();
        }
        // path to disk location index, leaf nodes
        if (tableConfig.isPreferDiskBasedIndices()) {
            pathToDiskLocationLeafNodes = new LongListDisk(dbPaths.pathToDiskLocationLeafNodesFile);
        } else if (Files.exists(dbPaths.pathToDiskLocationLeafNodesFile) && !forceIndexRebuilding) {
            pathToDiskLocationLeafNodes = new LongListOffHeap(dbPaths.pathToDiskLocationLeafNodesFile);
        } else {
            pathToDiskLocationLeafNodes = new LongListOffHeap(settings.getReservedBufferLengthForLeafList());
        }

        // internal node hashes store, RAM
        if (tableConfig.getInternalHashesRamToDiskThreshold() > 0) {
            if (Files.exists(dbPaths.internalHashStoreRamFile)) {
                internalHashStoreRam = new HashListByteBuffer(dbPaths.internalHashStoreRamFile);
            } else {
                internalHashStoreRam = new HashListByteBuffer();
            }
        } else {
            internalHashStoreRam = null;
        }
        // internal node hashes store, on disk
        hasDiskStoreForInternalHashes = tableConfig.getInternalHashesRamToDiskThreshold() < Long.MAX_VALUE;
        internalHashStoreDisk = hasDiskStoreForInternalHashes
                ? new MemoryIndexDiskKeyValueStore<>(
                        dbPaths.internalHashStoreDiskDirectory,
                        tableName + "_internalhashes",
                        tableName + ":internalHashes",
                        internalRecordSerializer,
                        null,
                        pathToDiskLocationInternalNodes)
                : null;

        // key to path store
        final DataFileCollection.LoadedDataCallback loadedDataCallback;
        if (tableConfig.getKeySerializer().getIndexType() == KeyIndexType.SEQUENTIAL_INCREMENTING_LONGS) {
            isLongKeyMode = true;
            objectKeyToPath = null;
            if (Files.exists(dbPaths.longKeyToPathFile)) {
                longKeyToPath = new LongListOffHeap(dbPaths.longKeyToPathFile);
                // we do not need callback longKeyToPath was written to disk, so we can load it
                // directly
                loadedDataCallback = null;
            } else {
                longKeyToPath = new LongListOffHeap();
                loadedDataCallback = (path, dataLocation, hashKeyValueData) -> {
                    // read key from hashKeyValueData, as we are in isLongKeyMode mode then
                    // the key is a single long
                    final long key = hashKeyValueData.getLong(0);
                    // update index
                    longKeyToPath.put(key, path);
                };
            }
        } else {
            isLongKeyMode = false;
            longKeyToPath = null;
            objectKeyToPath = new HalfDiskHashMap<>(
                    tableConfig.getMaxNumberOfKeys(),
                    tableConfig.getKeySerializer(),
                    dbPaths.objectKeyToPathDirectory,
                    tableName + "_objectkeytopath",
                    tableName + ":objectKeyToPath",
                    tableConfig.isPreferDiskBasedIndices());
            objectKeyToPath.printStats();
            // we do not need callback as HalfDiskHashMap loads its own data from disk
            loadedDataCallback = null;
        }

        // Create path to hash,key,value store, this will create new or load if files exist
        pathToHashKeyValue = new MemoryIndexDiskKeyValueStore<>(
                dbPaths.pathToHashKeyValueDirectory,
                tableName + "_pathtohashkeyvalue",
                tableName + ":pathToHashKeyValue",
                leafRecordSerializer,
                loadedDataCallback,
                pathToDiskLocationLeafNodes);

        // Leaf records cache
        leafRecordCacheSize = settings.getLeafRecordCacheSize();
        leafRecordCache = (leafRecordCacheSize > 0) ? new VirtualLeafRecord[leafRecordCacheSize] : null;

        // compute initial merge periods to a randomized value of now +/- 50% of merge period. So
        // each node will do
        // medium and full merges at random times.
        lastMediumMerge = Instant.now()
                .minus(settings.getMediumMergePeriod() / 2, settings.getMergePeriodUnit())
                .plus((long) (settings.getMediumMergePeriod() * Math.random()), settings.getMergePeriodUnit());
        lastFullMerge = Instant.now()
                .minus(settings.getFullMergePeriod() / 2, settings.getMergePeriodUnit())
                .plus((long) (settings.getFullMergePeriod() * Math.random()), settings.getMergePeriodUnit());

        // If merging is enabled start merging service
        if (this.compactionEnabled) {
            startBackgroundCompaction();
        }

        statistics = new MerkleDbStatistics(tableName, isLongKeyMode);

        logger.info(
                MERKLE_DB.getMarker(),
                "Created MerkleDB [{}] with store path '{}', maxNumKeys = {}, hash RAM/disk cutoff" + " = {}",
                tableName,
                storageDir,
                tableConfig.getMaxNumberOfKeys(),
                tableConfig.getInternalHashesRamToDiskThreshold());
    }

    /**
     * Start background compaction process, if it is not already running. Stop background merging,
     * interrupting the current merge if one is happening. This will not corrupt the database but
     * will leave files around.
     */
    @Override
    public void startBackgroundCompaction() {
        synchronized (mergingExecutor) {
            if (mergingFuture == null || mergingFuture.isCancelled()) {
                mergingFuture = mergingExecutor.scheduleAtFixedRate(
                        this::doMerge,
                        settings.getMergeActivatePeriod(),
                        settings.getMergeActivatePeriod(),
                        TimeUnit.SECONDS);
            }
        }
    }

    /** Stop background compaction process, if it is running. */
    @Override
    public void stopBackgroundCompaction() {
        synchronized (mergingExecutor) {
            if (mergingFuture != null) {
                mergingFuture.cancel(true);
                mergingFuture = null;
            }
        }
    }

    /** {@inheritDoc} */
    @SuppressWarnings("unchecked")
    @Override
    public VirtualKeySet<K> buildKeySet() {
        final KeySerializer<K> keySerializer =
                isLongKeyMode ? (KeySerializer<K>) new VirtualKeySetSerializer() : objectKeyToPath.getKeySerializer();
        return new HalfDiskVirtualKeySet<>(
                keySerializer,
                settings.getKeySetBloomFilterHashCount(),
                settings.getKeySetBloomFilterSizeInBytes() * BYTES_TO_BITS,
                settings.getKeySetHalfDiskHashMapSize(),
                settings.getKeySetHalfDiskHashMapBuffer());
    }

    /**
     * Get the count of open database instances. This is databases that have been opened but not yet
     * closed.
     *
     * @return Count of open databases.
     */
    static long getCountOfOpenDatabases() {
        return COUNT_OF_OPEN_DATABASES.sum();
    }

    /** Get the most recent first leaf path */
    long getFirstLeafPath() {
        return validLeafPathRange.getMinValidKey();
    }

    /** Get the most recent last leaf path */
    long getLastLeafPath() {
        return validLeafPathRange.getMaxValidKey();
    }

    /**
     * Waits for the current flush (if active) to complete and prevents new flushes from happening
     * until {@link #flushUnlock()}} is called. This method is used by MerkleDb to make sure all
     * data sources in a single database are in consistent state before taking a snapshot.
     */
    void flushLock() {
        flushLock.lock();
    }

    /**
     * Resumes data source flushes. This method is used by MerkleDb after database snapshot is
     * taken.
     */
    void flushUnlock() {
        flushLock.unlock();
    }

    /**
     * Pauses merging of all data file collections used by this data source. It may not stop merging
     * immediately, but as soon as merging process needs to update data source state, which is
     * critical for snapshots (e.g. update an index), it will be stopped until {@link
     * #resumeMerging()}} is called.
     */
    void pauseMerging() throws IOException {
        if (hasDiskStoreForInternalHashes) {
            internalHashStoreDisk.pauseMerging();
        }
        pathToHashKeyValue.pauseMerging();
        if (!isLongKeyMode) {
            objectKeyToPath.pauseMerging();
        }
    }

    /** Resumes previously stopped data file collection merging. */
    void resumeMerging() throws IOException {
        if (hasDiskStoreForInternalHashes) {
            internalHashStoreDisk.resumeMerging();
        }
        pathToHashKeyValue.resumeMerging();
        if (!isLongKeyMode) {
            objectKeyToPath.resumeMerging();
        }
    }

    /**
     * Save a batch of data to data store.
     *
     * If you call this method where not all data is provided to cover the change in
     * firstLeafPath and lastLeafPath, then any reads after this call may return rubbish or throw
     * obscure exceptions for any internals or leaves that have not been written. For example, if
     * you were to grow the tree by more than 2x, and then called this method in batches, be aware
     * that if you were to query for some record between batches that hadn't yet been saved, you
     * will encounter problems.
     *
     * @param firstLeafPath the tree path for first leaf
     * @param lastLeafPath the tree path for last leaf
     * @param internalRecords stream of records for internal nodes, it is assumed this is sorted by
     *     path and each path only appears once.
     * @param leafRecordsToAddOrUpdate stream of new leaf nodes and updated leaf nodes
     * @param leafRecordsToDelete stream of new leaf nodes to delete, The leaf record's key and path
     *     have to be populated, all other data can be null.
     * @throws IOException If there was a problem saving changes to data source
     */
    @Override
    public void saveRecords(
            final long firstLeafPath,
            final long lastLeafPath,
            final Stream<VirtualInternalRecord> internalRecords,
            final Stream<VirtualLeafRecord<K, V>> leafRecordsToAddOrUpdate,
            final Stream<VirtualLeafRecord<K, V>> leafRecordsToDelete)
            throws IOException {
        flushLock.lock();
        try {
            validLeafPathRange = new KeyRange(firstLeafPath, lastLeafPath);
            final CountDownLatch countDownLatch = new CountDownLatch(firstLeafPath > 0 ? 1 : 0);

            // might as well write to the 3 data stores in parallel, so lets fork 2 threads for the
            // easy stuff
            if (firstLeafPath > 0) {
                storeInternalExecutor.execute(() -> {
                    try {
                        writeInternalRecords(firstLeafPath, internalRecords);
                    } catch (final IOException e) {
                        logger.error(ERROR.getMarker(), "[{}] Failed to store internal records", tableName, e);
                        throw new UncheckedIOException(e);
                    } finally {
                        countDownLatch.countDown();
                    }
                });
            }

            // we might as well do this in the archive thread rather than leaving it waiting
            writeLeavesToPathToHashKeyValue(firstLeafPath, lastLeafPath, leafRecordsToAddOrUpdate, leafRecordsToDelete);
            // wait for the other two threads in the rare case they are not finished yet. We need to
            // have all writing
            // done before we return as when we return the state version we are writing is deleted
            // from the cache and
            // the flood gates are opened for reads through to the data we have written here.
            try {
                countDownLatch.await();
            } catch (final InterruptedException e) {
                logger.warn(
                        EXCEPTION.getMarker(),
                        "[{}] Interrupted while waiting on internal record storage",
                        tableName,
                        e);
                Thread.currentThread().interrupt();
            }
        } finally {
            flushLock.unlock();
            // update file stats
            updateFileStats();
            // update off-heap stats
            updateOffHeapStats();
        }
    }

    /**
     * Load a leaf record by key
     *
     * @param key they to the leaf to load record for
     * @return loaded record or null if not found
     * @throws IOException If there was a problem reading record from db
     */
    @SuppressWarnings("unchecked")
    @Override
    public VirtualLeafRecord<K, V> loadLeafRecord(final K key) throws IOException {
        Objects.requireNonNull(key);

        final long path;
        VirtualLeafRecord<K, V> cached = null;
        int cacheIndex = -1;
        if (leafRecordCache != null) {
            cacheIndex = Math.abs(key.hashCode() % leafRecordCacheSize);
            // No synchronization is needed here. Java guarantees (JLS 17.7) that reference writes
            // are atomic, so we will never get corrupted objects from the array. The object may
            // be overwritten in the cache in a different thread in parallel, but it isn't a
            // problem as cached entry key is checked below anyway
            cached = leafRecordCache[cacheIndex];
        }
        // If an entry is found in the cache, and entry key is the one requested
        if ((cached != null) && key.equals(cached.getKey())) {
            // Some cache entries contain just key and path, but no value. If the value is there,
            // just return the cached entry. If not, at least make use of the path
            if (cached.getValue() != null) {
                // A copy is returned to ensure cached value immutability.
                return cached.copy();
            }
            // Note that the path may be INVALID_PATH here, this is perfectly legal
            path = cached.getPath();
        } else {
            // Cache miss
            cached = null;
            path = isLongKeyMode
                    ? longKeyToPath.get(((VirtualLongKey) key).getKeyAsLong(), INVALID_PATH)
                    : objectKeyToPath.get(key, INVALID_PATH);
        }

        // If the key didn't map to anything, we just return null
        if (path == INVALID_PATH) {
            // Cache the result if not already cached
            if (leafRecordCache != null && cached == null) {
                leafRecordCache[cacheIndex] = new VirtualLeafRecord<K, V>(path, null, key, null);
            }
            return null;
        }

        // If the key returns a value from the map, but it lies outside the first/last
        // leaf path, then return null. This can happen if the map contains old keys
        // that haven't been removed.
        if (!validLeafPathRange.withinRange(path)) {
            return null;
        }

        statistics.cycleLeafByKeyReadsPerSecond();
        // Go ahead and lookup the value.
        VirtualLeafRecord<K, V> leafRecord = pathToHashKeyValue.get(path);

        // FUTURE WORK: once the reconnect key leak bug is fixed, this block should be removed
        if (!leafRecord.getKey().equals(key)) {
            if (settings.isReconnectKeyLeakMitigationEnabled()) {
                logger.warn(MERKLE_DB.getMarker(), "leaked key {} encountered, mitigation is enabled", key);
                return null;
            } else {
                logger.error(
                        EXCEPTION.getMarker(),
                        "leaked key {} encountered, mitigation is disabled, expect problems",
                        key);
            }
        }

        if (leafRecordCache != null) {
            // No synchronization is needed here, see the comment above
            // A copy is returned to ensure cached value immutability.
            leafRecordCache[cacheIndex] = leafRecord;
            leafRecord = leafRecord.copy();
        }

        return leafRecord;
    }

    /**
     * Load a leaf record by path
     *
     * @param path the path for the leaf we are loading
     * @return loaded record or null if not found
     * @throws IOException If there was a problem reading record from db
     */
    @Override
    public VirtualLeafRecord<K, V> loadLeafRecord(final long path) throws IOException {
        final KeyRange leafPathRange = validLeafPathRange;
        if (!leafPathRange.withinRange(path)) {
            throw new IllegalArgumentException("path (" + path + ") is not valid; must be in range " + leafPathRange);
        }
        statistics.cycleLeafByPathReadsPerSecond();
        return pathToHashKeyValue.get(path);
    }

    /**
     * Find the path of the given key
     *
     * @param key the key for a path
     * @return the path or INVALID_PATH if not stored
     * @throws IOException If there was a problem locating the key
     */
    @SuppressWarnings("unchecked")
    @Override
    public long findKey(final K key) throws IOException {
        Objects.requireNonNull(key);

        // Check the cache first
        int cacheIndex = -1;
        if (leafRecordCache != null) {
            cacheIndex = Math.abs(key.hashCode() % leafRecordCacheSize);
            // No synchronization is needed here. See the comment in loadLeafRecord(key) above
            final VirtualLeafRecord<K, V> cached = leafRecordCache[cacheIndex];
            if (cached != null && key.equals(cached.getKey())) {
                // Cached path may be a valid path or INVALID_PATH, both are legal here
                return cached.getPath();
            }
        }

        final long path = isLongKeyMode
                ? longKeyToPath.get(((VirtualLongKey) key).getKeyAsLong(), INVALID_PATH)
                : objectKeyToPath.get(key, INVALID_PATH);

        if (leafRecordCache != null) {
            // Path may be INVALID_PATH here. Still needs to be cached (negative result)
            leafRecordCache[cacheIndex] = new VirtualLeafRecord<K, V>(path, null, key, null);
        }

        return path;
    }

    /**
     * Load hash for a leaf node with given path
     *
     * @param path the path to get hash for
     * @return loaded hash or null if hash is not stored
     * @throws IOException if there was a problem loading hash
     */
    @Override
    public Hash loadLeafHash(final long path) throws IOException {
        if (path < 0) {
            throw new IllegalArgumentException("path is less than 0");
        }
        final KeyRange leafPathRange = validLeafPathRange;
        if (!leafPathRange.withinRange(path)) {
            throw new IllegalArgumentException("path (" + path + ") is not valid; must be in range " + leafPathRange);
        }

        statistics.cycleLeafByPathReadsPerSecond();
        // read value
        /* FUTURE WORK - https://github.com/swirlds/swirlds-platform/issues/3937 */
        final VirtualLeafRecord<K, V> leafRecord = pathToHashKeyValue.get(path);
        return leafRecord == null ? null : leafRecord.getHash();
    }

    /** {@inheritDoc} */
    @Override
    public VirtualInternalRecord loadInternalRecord(final long path, final boolean deserialize) throws IOException {
        if (path < 0) {
            throw new IllegalArgumentException("path is less than 0");
        }

        // It is possible that the caller will ask for an internal node that the database doesn't
        // know about. This can happen if some leaves have been added to the tree, but we haven't
        // hashed yet, so the cache doesn't have any internal records for it, and somebody
        // tries to iterate over the nodes in the tree.
        final long firstLeaf = validLeafPathRange.getMinValidKey();
        if (path >= firstLeaf) {
            return null;
        }

        VirtualInternalRecord record = null;
        if (path < tableConfig.getInternalHashesRamToDiskThreshold()) {
            if (deserialize) {
                final Hash hash = internalHashStoreRam.get(path);
                if (hash != null) {
                    statistics.cycleInternalNodeReadsPerSecond();
                    record = new VirtualInternalRecord(path, hash);
                }
            }
        } else {
            record = internalHashStoreDisk.get(path, deserialize);
            statistics.cycleInternalNodeReadsPerSecond();
        }

        return record;
    }

    /**
     * Wait for any merges to finish and then close all data stores.
     *
     * <b>After closing delete the database directory and all data!</b> For testing purpose only.
     */
    public void closeAndDelete() throws IOException {
        try {
            close();
        } finally {
            database.removeDataSource(tableName);
        }
    }

    /** Wait for any merges to finish, then close all data stores and free all resources. */
    @Override
    public void close() throws IOException {
        if (!closed.getAndSet(true)) {
            try {
                // stop merging
                stopBackgroundCompaction();
                // shut down all four DB threads
                shutdownThreadsAndWait(
                        mergingExecutor, storeInternalExecutor, storeKeyToPathExecutor, snapshotExecutor);
            } finally {
                // close all closable data stores
                logger.info(MERKLE_DB.getMarker(), "Closing Data Source [{}]", tableName);
                if (internalHashStoreRam != null) {
                    internalHashStoreRam.close();
                }
                if (internalHashStoreDisk != null) {
                    internalHashStoreDisk.close();
                }
                pathToDiskLocationInternalNodes.close();
                pathToDiskLocationLeafNodes.close();
                if (longKeyToPath != null) {
                    longKeyToPath.close();
                }
                if (objectKeyToPath != null) {
                    objectKeyToPath.close();
                }
                pathToHashKeyValue.close();
                // updated count of open databases
                COUNT_OF_OPEN_DATABASES.decrement();
                // Store metadata
                saveMetadata(dbPaths.metadataFile);
                // Notify the database
                database.closeDataSource(this);
            }
        }
    }

    /**
     * Write a snapshot of the current state of the database at this moment in time. This will block
     * till the snapshot is completely created.
     *
     *
     * <b> Only one snapshot can happen at a time, this will throw an IllegalStateException if
     * another snapshot is currently happening. </b>
     *
     * <b> IMPORTANT, after this is completed the caller owns the directory. It is responsible
     * for deleting it when it is no longer needed. </b>
     *
     * @param snapshotDirectory Directory to put snapshot into, it will be created if it doesn't
     *     exist.
     * @throws IOException If there was a problem writing the current database out to the given
     *     directory
     * @throws IllegalStateException If there is already a snapshot happening
     */
    @SuppressWarnings("ConstantConditions")
    @Override
    public void snapshot(final Path snapshotDirectory) throws IOException, IllegalStateException {
        // check if another snapshot was running
        final boolean aSnapshotWasInProgress = snapshotInProgress.getAndSet(true);
        if (aSnapshotWasInProgress) {
            throw new IllegalStateException("Tried to start a snapshot when one was already in progress");
        }
        try {
            // start timing snapshot
            final long START = System.currentTimeMillis();
            // create snapshot dir if it doesn't exist
            Files.createDirectories(snapshotDirectory);
            final MerkleDbPaths snapshotDbPaths = new MerkleDbPaths(snapshotDirectory);
            // main snapshotting process in multiple-threads
            try {
                final CountDownLatch countDownLatch = new CountDownLatch(8);
                // write all data stores
                runWithSnapshotExecutor(true, countDownLatch, "pathToDiskLocationInternalNodes", () -> {
                    pathToDiskLocationInternalNodes.writeToFile(snapshotDbPaths.pathToDiskLocationInternalNodesFile);
                    return true;
                });
                runWithSnapshotExecutor(true, countDownLatch, "pathToDiskLocationLeafNodes", () -> {
                    pathToDiskLocationLeafNodes.writeToFile(snapshotDbPaths.pathToDiskLocationLeafNodesFile);
                    return true;
                });
                runWithSnapshotExecutor(internalHashStoreRam != null, countDownLatch, "internalHashStoreRam", () -> {
                    internalHashStoreRam.writeToFile(snapshotDbPaths.internalHashStoreRamFile);
                    return true;
                });
                runWithSnapshotExecutor(internalHashStoreDisk != null, countDownLatch, "internalHashStoreDisk", () -> {
                    internalHashStoreDisk.snapshot(snapshotDbPaths.internalHashStoreDiskDirectory);
                    return true;
                });
                runWithSnapshotExecutor(longKeyToPath != null, countDownLatch, "longKeyToPath", () -> {
                    longKeyToPath.writeToFile(snapshotDbPaths.longKeyToPathFile);
                    return true;
                });
                runWithSnapshotExecutor(objectKeyToPath != null, countDownLatch, "objectKeyToPath", () -> {
                    objectKeyToPath.snapshot(snapshotDbPaths.objectKeyToPathDirectory);
                    return true;
                });
                runWithSnapshotExecutor(true, countDownLatch, "pathToHashKeyValue", () -> {
                    pathToHashKeyValue.snapshot(snapshotDbPaths.pathToHashKeyValueDirectory);
                    return true;
                });
                runWithSnapshotExecutor(true, countDownLatch, "metadata", () -> {
                    saveMetadata(snapshotDbPaths.metadataFile);
                    return true;
                });
                // wait for the others to finish
                countDownLatch.await();
            } catch (final InterruptedException e) {
                logger.error(
                        EXCEPTION.getMarker(),
                        "[{}] InterruptedException from waiting for countDownLatch in snapshot",
                        tableName,
                        e);
                Thread.currentThread().interrupt();
            }
            logger.info(
                    MERKLE_DB.getMarker(),
                    "[{}] Snapshot all finished in {} seconds",
                    tableName,
                    (System.currentTimeMillis() - START) * Units.MILLISECONDS_TO_SECONDS);
        } finally {
            snapshotInProgress.set(false);
        }
    }

    /** toString for debugging */
    @Override
    public String toString() {
        return "MerkleDbDataSource{"
                + "maxNumberOfKeys="
                + tableConfig.getMaxNumberOfKeys()
                + ", preferDiskBasedIndexes="
                + tableConfig.isPreferDiskBasedIndices()
                + ", isLongKeyMode="
                + isLongKeyMode
                + ", pathToDiskLocationInternalNodes.size="
                + pathToDiskLocationInternalNodes.size()
                + ", pathToDiskLocationLeafNodes.size="
                + pathToDiskLocationLeafNodes.size()
                + ", internalHashesRamToDiskThreshold"
                + tableConfig.getInternalHashesRamToDiskThreshold()
                + ", internalHashStoreRam.size="
                + (internalHashStoreRam == null ? null : internalHashStoreRam.size())
                + ", internalHashStoreDisk="
                + internalHashStoreDisk
                + ", hasDiskStoreForInternalHashes="
                + hasDiskStoreForInternalHashes
                + ", longKeyToPath.size="
                + (longKeyToPath == null ? null : longKeyToPath.size())
                + ", objectKeyToPath="
                + objectKeyToPath
                + ", pathToHashKeyValue="
                + pathToHashKeyValue
                + ", snapshotInProgress="
                + snapshotInProgress.get()
                + '}';
    }

    /**
     * Database instance that hosts this data source.
     *
     * @return Virtual database instance
     */
    public MerkleDb getDatabase() {
        return database;
    }

    /**
     * Table ID for this data source in its virtual database instance.
     *
     * @return Table ID
     */
    public int getTableId() {
        return tableId;
    }

    /**
     * Table name for this data source in its virtual database instance.
     *
     * @return Table name
     */
    public String getTableName() {
        return tableName;
    }

    /**
     * Table config for this data source. Includes key and value serializers, internal nodes
     * RAM/disk threshold, etc.
     *
     * @return Table config
     */
    public MerkleDbTableConfig<K, V> getTableConfig() {
        return tableConfig;
    }

    // For testing purpose
    Path getStorageDir() {
        return dbPaths.storageDir;
    }

    // For testing purpose
    long getMaxNumberOfKeys() {
        return tableConfig.getMaxNumberOfKeys();
    }

    // For testing purpose
    long getInternalHashesRamToDiskThreshold() {
        return tableConfig.getInternalHashesRamToDiskThreshold();
    }

    // For testing purpose
    boolean isPreferDiskBasedIndexes() {
        return tableConfig.isPreferDiskBasedIndices();
    }

    // For testing purpose
    boolean isCompactionEnabled() {
        return compactionEnabled;
    }

    private void saveMetadata(final Path targetFile) throws IOException {
        final KeyRange leafRange = validLeafPathRange;
        try (final DataOutputStream metaOut = new DataOutputStream(
                Files.newOutputStream(targetFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE))) {
            metaOut.writeInt(MetadataFileFormatVersion.KEYRANGE_ONLY); // serialization version
            metaOut.writeLong(leafRange.getMinValidKey());
            metaOut.writeLong(leafRange.getMaxValidKey());
            metaOut.flush();
        }
    }

    private void loadMetadata(final Path sourceFile) throws IOException {
        try (final DataInputStream metaIn = new DataInputStream(Files.newInputStream(sourceFile))) {
            final int fileVersion = metaIn.readInt();
            if (fileVersion == MetadataFileFormatVersion.ORIGINAL) {
                metaIn.readLong(); // skip internalHashesRamToDiskThreshold
            } else if (fileVersion != MetadataFileFormatVersion.KEYRANGE_ONLY) {
                throw new IOException(
                        "Tried to read a file with incompatible file format version [" + fileVersion + "].");
            }
            validLeafPathRange = new KeyRange(metaIn.readLong(), metaIn.readLong());
        }
    }

    /** {@inheritDoc} */
    @Override
    public void registerMetrics(final Metrics metrics) {
        if (firstStatRegistration.compareAndSet(true, false)) {
            // register static/global statistics
            metrics.getOrCreate(COUNT_OF_OPEN_DATABASES_CONFIG);
        }

        // register instance statistics
        statistics.registerMetrics(metrics);
    }

    /** {@inheritDoc} */
    @Override
    public void copyStatisticsFrom(final VirtualDataSource<K, V> that) {
        if (!(that instanceof MerkleDbDataSource<?, ?> thatDataSource)) {
            throw new IllegalArgumentException("can only copy statistics from MerkleDbDataSource");
        }
        statistics = thatDataSource.statistics;
    }

    // ==================================================================================================================
    // private methods

    /**
     * Update all the file size and count statistics, called by save and merge as those are the only
     * two places where files are added or removed.
     */
    private void updateFileStats() {
        if (internalHashStoreDisk != null) {
            final LongSummaryStatistics internalHashesFileSizeStats = internalHashStoreDisk.getFilesSizeStatistics();
            statistics.setInternalHashesStoreFileCount((int) internalHashesFileSizeStats.getCount());
            statistics.setInternalHashesStoreTotalFileSizeInMB(
                    internalHashesFileSizeStats.getSum() * BYTES_TO_MEBIBYTES);
        }
        if (!isLongKeyMode) {
            final LongSummaryStatistics leafKeyFileSizeStats = objectKeyToPath.getFilesSizeStatistics();
            statistics.setLeafKeyToPathStoreFileCount((int) leafKeyFileSizeStats.getCount());
            statistics.setLeafKeyToPathStoreTotalFileSizeInMB(leafKeyFileSizeStats.getSum() * BYTES_TO_MEBIBYTES);
        }
        final LongSummaryStatistics leafDataFileSizeStats = pathToHashKeyValue.getFilesSizeStatistics();
        statistics.setLeafPathToHashKeyValueStoreFileCount((int) leafDataFileSizeStats.getCount());
        statistics.setLeafPathToHashKeyValueStoreTotalFileSizeInMB(leafDataFileSizeStats.getSum() * BYTES_TO_MEBIBYTES);
    }

    private void updateOffHeapStats() {
        final int totalOffHeapMemoryConsumption = updateOffHeapStat(
                        pathToDiskLocationInternalNodes, v -> statistics.setOffHeapMemoryInternalNodesListInMB(v))
                + updateOffHeapStat(pathToDiskLocationLeafNodes, v -> statistics.setOffHeapMemoryLeafNodesListInMB(v))
                + updateOffHeapStat(longKeyToPath, v -> statistics.setOffHeapMemoryKeyToPathListInMB(v));
        statistics.setOffHeapMemoryDataSourceInMB(totalOffHeapMemoryConsumption);
    }

    private static int updateOffHeapStat(final LongList longList, final IntConsumer updateFunction) {
        if (longList instanceof LongListOffHeap longListOffHeap) {
            final int result = (int) (longListOffHeap.getOffHeapConsumption() * BYTES_TO_MEBIBYTES);
            updateFunction.accept(result);
            return result;
        } else {
            return 0;
        }
    }

    /**
     * Shutdown threads if they are running and wait for them to finish
     *
     * @param executors array of threads to shut down.
     * @throws IOException if there was a problem or timeout shutting down threads.
     */
    private void shutdownThreadsAndWait(final ExecutorService... executors) throws IOException {
        try {
            // shutdown threads
            for (final ExecutorService executor : executors) {
                if (!executor.isShutdown()) {
                    executor.shutdown();
                    final boolean finishedWithoutTimeout = executor.awaitTermination(5, TimeUnit.MINUTES);
                    if (!finishedWithoutTimeout) {
                        throw new IOException("Timeout while waiting for executor service to finish.");
                    }
                }
            }
        } catch (final InterruptedException e) {
            logger.warn(EXCEPTION.getMarker(), "[{}] Interrupted while waiting on executors to shutdown", tableName, e);
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while waiting for merge to finish.", e);
        }
    }

    /**
     * Run a runnable on background thread using snapshot ExecutorService, counting down latch when
     * done.
     *
     * @param shouldRun when true, run runnable otherwise just countdown latch
     * @param countDownLatch latch to count down when done
     * @param taskName the name of the task for logging
     * @param runnable the code to run
     */
    private void runWithSnapshotExecutor(
            final boolean shouldRun,
            final CountDownLatch countDownLatch,
            final String taskName,
            final Callable<Object> runnable) {
        if (shouldRun) {
            snapshotExecutor.submit(() -> {
                final long START = System.currentTimeMillis();
                try {
                    runnable.call();
                    logger.trace(
                            MERKLE_DB.getMarker(),
                            "[{}] Snapshot {} complete in {} seconds",
                            tableName,
                            taskName,
                            (System.currentTimeMillis() - START) * Units.MILLISECONDS_TO_SECONDS);
                    return true; // turns this into a callable, so it can throw checked
                    // exceptions
                } finally {
                    countDownLatch.countDown();
                }
            });
        } else {
            countDownLatch.countDown();
        }
    }

    /** Write all internal records hashes to internalHashStore */
    private void writeInternalRecords(final long firstLeafPath, final Stream<VirtualInternalRecord> internalRecords)
            throws IOException {
        if ((internalRecords == null) || (firstLeafPath <= 0)) {
            // nothing to do
            return;
        }

        if (hasDiskStoreForInternalHashes) {
            internalHashStoreDisk.startWriting(0);
        }

        final AtomicLong lastPath = new AtomicLong(INVALID_PATH);
        internalRecords.forEach(rec -> {
            assert rec.getPath() > lastPath.getAndSet(rec.getPath()) : "Path should be in ascending order!";
            statistics.cycleInternalNodeWritesPerSecond();
            if (rec.getPath() < tableConfig.getInternalHashesRamToDiskThreshold()) {
                internalHashStoreRam.put(rec.getPath(), rec.getHash());
            } else {
                try {
                    internalHashStoreDisk.put(rec.getPath(), rec);
                } catch (final IOException e) {
                    logger.error(EXCEPTION.getMarker(), "[{}] IOException writing internal records", tableName, e);
                    throw new UncheckedIOException(e);
                }
            }
        });

        if (hasDiskStoreForInternalHashes) {
            internalHashStoreDisk.endWriting(0, firstLeafPath - 1);
        }
    }

    /** Write all the given leaf records to pathToHashKeyValue */
    private void writeLeavesToPathToHashKeyValue(
            final long firstLeafPath,
            final long lastLeafPath,
            final Stream<VirtualLeafRecord<K, V>> leafRecordsToAddOrUpdate,
            final Stream<VirtualLeafRecord<K, V>> leafRecordsToDelete)
            throws IOException {
        if ((leafRecordsToAddOrUpdate == null) || (firstLeafPath <= 0)) {
            // nothing to do
            return;
        }

        // start writing
        pathToHashKeyValue.startWriting(firstLeafPath);
        if (!isLongKeyMode) {
            objectKeyToPath.startWriting();
        }

        // iterate over leaf records
        final AtomicLong lastPath = new AtomicLong(INVALID_PATH);
        leafRecordsToAddOrUpdate.forEach(leafRecord -> {
            assert leafRecord.getPath() > lastPath.getAndSet(leafRecord.getPath())
                    : "Path should be in ascending order!";
            statistics.cycleLeafWritesPerSecond();

            // update objectKeyToPath
            if (isLongKeyMode) {
                longKeyToPath.put(((VirtualLongKey) leafRecord.getKey()).getKeyAsLong(), leafRecord.getPath());
            } else {
                objectKeyToPath.put(leafRecord.getKey(), leafRecord.getPath());
            }

            // update pathToHashKeyValue
            try {
                pathToHashKeyValue.put(leafRecord.getPath(), leafRecord);
            } catch (final IOException e) {
                logger.error(EXCEPTION.getMarker(), "[{}] IOException writing to pathToHashKeyValue", tableName, e);
                throw new UncheckedIOException(e);
            }

            // cache the record
            invalidateReadCache(leafRecord.getKey());
        });

        // iterate over leaf records to delete
        leafRecordsToDelete.forEach(leafRecord -> {
            // update objectKeyToPath
            if (isLongKeyMode) {
                longKeyToPath.put(((VirtualLongKey) leafRecord.getKey()).getKeyAsLong(), INVALID_PATH);
            } else {
                objectKeyToPath.delete(leafRecord.getKey());
            }

            // delete from pathToHashKeyValue, we don't need to explicitly delete leaves as
            // they will be deleted on
            // next merge based on range of valid leaf paths. If a leaf at path X is deleted
            // then a new leaf is
            // inserted at path X then the record is just updated to new leaf's data.

            // delete the record from the cache
            invalidateReadCache(leafRecord.getKey());
        });

        // end writing
        pathToHashKeyValue.endWriting(firstLeafPath, lastLeafPath);
        if (!isLongKeyMode) {
            objectKeyToPath.endWriting();
        }
    }

    /**
     * Invalidates the given key in virtual leaf record cache, if the cache is enabled.
     *
     * If the key is deleted, it's still updated in the cache. It means no record with the given
     * key exists in the data source, so further lookups for the key are skipped.
     *
     * Cache index is calculated as the key's hash code % cache size. The cache is only updated,
     * if the current record at this index has the given key. If the key is different, no update is
     * performed.
     *
     * @param key Virtual leaf record key
     */
    @SuppressWarnings("unchecked")
    private void invalidateReadCache(final K key) {
        if (leafRecordCache == null) {
            return;
        }
        final int cacheIndex = Math.abs(key.hashCode() % leafRecordCacheSize);
        final VirtualLeafRecord<K, V> cached = leafRecordCache[cacheIndex];
        if ((cached != null) && key.equals(cached.getKey())) {
            leafRecordCache[cacheIndex] = null;
        }
    }

    /**
     * Start a Merge if needed, this is called by default every 30 seconds if a merge is not already
     * running. This implements the logic for how often and with what files we merge.
     *
     * <b> IMPORTANT: This method is called on a thread that can be interrupted, so it needs to
     * gracefully stop when it is interrupted. </b>
     *
     * <b> IMPORTANT: The set of files we merge must always be contiguous in order of time
     * contained data created. As merged files have a later index but old data the index can not be
     * used alone to work out order of files to merge. </b>
     *
     * @return true if merging completed successfully, false if it was interrupted or an exception
     *     occurred.
     */
    @SuppressWarnings({"rawtypes", "unchecked", "ConstantConditions"})
    boolean doMerge() {
        try {
            // calls to Instant.now() are expensive, so we only call it four times (or three if
            // isLongKeyMode)
            // rather than having separate "start" and "end" Instants for each of the two/three sub
            // merges.
            final Instant now = Instant.now(clock);
            final Instant afterInternalHashStoreDiskMerge;
            final Instant afterObjectKeyToPathMerge;
            final Instant afterPathToHashKeyValueMerge;

            final UnaryOperator<List<DataFileReader>> filesToMergeFilter;
            boolean isLargeMerge = false;
            boolean isMediumMerge = false;
            boolean isSmallMerge = false;
            if (isTimeForFullMerge(now)) {
                lastFullMerge = now;
                /* Filter nothing during a full merge */
                filesToMergeFilter = dataFileReaders -> dataFileReaders;
                isLargeMerge = true;
                logger.info(MERKLE_DB.getMarker(), "[{}] Starting Large Merge", tableName);
            } else if (isTimeForMediumMerge(now)) {
                lastMediumMerge = now;
                filesToMergeFilter = DataFileCommon.newestFilesSmallerThan(
                        settings.getMediumMergeCutoffMb(), settings.getMaxNumberOfFilesInMerge());
                isMediumMerge = true;
                logger.info(MERKLE_DB.getMarker(), "[{}] Starting Medium Merge", tableName);
            } else {
                filesToMergeFilter = DataFileCommon.newestFilesSmallerThan(
                        settings.getSmallMergeCutoffMb(), settings.getMaxNumberOfFilesInMerge());
                isSmallMerge = true;
                logger.info(MERKLE_DB.getMarker(), "[{}] Starting Small Merge", tableName);
            }

            // we need to merge disk files for internal hashes if they exist and pathToHashKeyValue
            // store
            if (hasDiskStoreForInternalHashes) {
                // horrible hack to get around generics because file filters work on any type of
                // DataFileReader
                final UnaryOperator<List<DataFileReader<VirtualInternalRecord>>> internalRecordFileFilter =
                        (UnaryOperator<List<DataFileReader<VirtualInternalRecord>>>) ((Object) filesToMergeFilter);
                internalHashStoreDisk.merge(internalRecordFileFilter, settings.getMinNumberOfFilesInMerge());
                afterInternalHashStoreDiskMerge = Instant.now(clock);
            } else {
                afterInternalHashStoreDiskMerge = now; // zero elapsed time
            }
            // merge objectKeyToPath files
            if (isLongKeyMode) {
                // third "now" is replaced by copying second "now"
                afterObjectKeyToPathMerge = afterInternalHashStoreDiskMerge;
            } else {
                // horrible hack to get around generics because file filters work on any type of
                // DataFileReader
                final UnaryOperator<List<DataFileReader<Bucket<K>>>> bucketFileFilter =
                        (UnaryOperator<List<DataFileReader<Bucket<K>>>>) ((Object) filesToMergeFilter);
                objectKeyToPath.merge(bucketFileFilter, settings.getMinNumberOfFilesInMerge());
                // set third "now"
                afterObjectKeyToPathMerge = Instant.now(clock);
            }
            // now do main merge of pathToHashKeyValue store
            // horrible hack to get around generics because file filters work on any type of
            // DataFileReader
            final UnaryOperator<List<DataFileReader<VirtualLeafRecord<K, V>>>> leafRecordFileFilter =
                    (UnaryOperator<List<DataFileReader<VirtualLeafRecord<K, V>>>>) ((Object) filesToMergeFilter);
            pathToHashKeyValue.merge(leafRecordFileFilter, settings.getMinNumberOfFilesInMerge());
            // set fourth "now"
            afterPathToHashKeyValueMerge = Instant.now(clock);

            // determine how long each of the sub-merges took.
            final Duration firstMergeDuration = Duration.between(now, afterInternalHashStoreDiskMerge);
            final Duration secondMergeDuration =
                    Duration.between(afterInternalHashStoreDiskMerge, afterObjectKeyToPathMerge);
            final Duration thirdMergeDuration =
                    Duration.between(afterObjectKeyToPathMerge, afterPathToHashKeyValueMerge);

            // update the 3 appropriate "Merge" statistics, based on
            // isSmallMerge/isMediumMerge/isLargeMerge
            if (isSmallMerge) {
                if (hasDiskStoreForInternalHashes) {
                    statistics.setInternalHashesStoreSmallMergeTime(firstMergeDuration.toSeconds()
                            + firstMergeDuration.getNano() * Units.NANOSECONDS_TO_SECONDS);
                }
                if (!isLongKeyMode) {
                    statistics.setLeafKeyToPathStoreSmallMergeTime(secondMergeDuration.toSeconds()
                            + secondMergeDuration.getNano() * Units.NANOSECONDS_TO_SECONDS);
                }
                statistics.setLeafPathToHashKeyValueStoreSmallMergeTime(
                        thirdMergeDuration.toSeconds() + thirdMergeDuration.getNano() * Units.NANOSECONDS_TO_SECONDS);
            } else if (isMediumMerge) {
                if (hasDiskStoreForInternalHashes) {
                    statistics.setInternalHashesStoreMediumMergeTime(firstMergeDuration.toSeconds()
                            + firstMergeDuration.getNano() * Units.NANOSECONDS_TO_SECONDS);
                }
                if (!isLongKeyMode) {
                    statistics.setLeafKeyToPathStoreMediumMergeTime(secondMergeDuration.toSeconds()
                            + secondMergeDuration.getNano() * Units.NANOSECONDS_TO_SECONDS);
                }
                statistics.setLeafPathToHashKeyValueStoreMediumMergeTime(
                        thirdMergeDuration.toSeconds() + thirdMergeDuration.getNano() * Units.NANOSECONDS_TO_SECONDS);
            } else if (isLargeMerge) {
                if (hasDiskStoreForInternalHashes) {
                    statistics.setInternalHashesStoreLargeMergeTime(firstMergeDuration.toSeconds()
                            + firstMergeDuration.getNano() * Units.NANOSECONDS_TO_SECONDS);
                }
                if (!isLongKeyMode) {
                    statistics.setLeafKeyToPathStoreLargeMergeTime(secondMergeDuration.toSeconds()
                            + secondMergeDuration.getNano() * Units.NANOSECONDS_TO_SECONDS);
                }
                statistics.setLeafPathToHashKeyValueStoreLargeMergeTime(
                        thirdMergeDuration.toSeconds() + thirdMergeDuration.getNano() * Units.NANOSECONDS_TO_SECONDS);
            }
            // update file stats (those statistics don't care about small vs medium vs large merge size)
            updateFileStats();
            // update off-heap usage statistic
            updateOffHeapStats();
            logger.info(MERKLE_DB.getMarker(), "[{}] Finished Small Merge", tableName);
            return true;
        } catch (final InterruptedException | ClosedByInterruptException e) {
            logger.info(MERKLE_DB.getMarker(), "Interrupted while merging, this is allowed.");
            Thread.currentThread().interrupt();
            return false;
        } catch (final Throwable e) {
            // It is important that we capture all exceptions here, otherwise a single exception
            // will stop all  future merges from happening.
            logger.error(EXCEPTION.getMarker(), "[{}] Merge failed", tableName, e);
            return false;
        }
    }

    private boolean isTimeForFullMerge(final Instant startMerge) {
        return startMerge
                .minus(settings.getFullMergePeriod(), settings.getMergePeriodUnit())
                .isAfter(lastFullMerge);
    }

    private boolean isTimeForMediumMerge(final Instant startMerge) {
        return startMerge
                .minus(settings.getMediumMergePeriod(), settings.getMergePeriodUnit())
                .isAfter(lastMediumMerge);
    }

    /**
     * Used for tests.
     *
     * @return true if we are in "long key" mode.
     */
    boolean isLongKeyMode() {
        return isLongKeyMode;
    }
}
