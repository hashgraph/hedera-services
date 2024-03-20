/*
 * Copyright (C) 2021-2024 Hedera Hashgraph, LLC
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

package com.swirlds.merkledb.files.hashmap;

import static com.swirlds.common.threading.manager.AdHocThreadManager.getStaticThreadManager;
import static com.swirlds.logging.legacy.LogMarker.EXCEPTION;
import static com.swirlds.logging.legacy.LogMarker.MERKLE_DB;
import static com.swirlds.merkledb.MerkleDb.MERKLEDB_COMPONENT;

import com.swirlds.common.config.singleton.ConfigurationHolder;
import com.swirlds.common.threading.framework.config.ThreadConfiguration;
import com.swirlds.merkledb.FileStatisticAware;
import com.swirlds.merkledb.Snapshotable;
import com.swirlds.merkledb.collections.CASableLongIndex;
import com.swirlds.merkledb.collections.LongList;
import com.swirlds.merkledb.collections.LongListDisk;
import com.swirlds.merkledb.collections.LongListOffHeap;
import com.swirlds.merkledb.collections.OffHeapUser;
import com.swirlds.merkledb.config.MerkleDbConfig;
import com.swirlds.merkledb.files.DataFileCollection;
import com.swirlds.merkledb.files.DataFileCollection.LoadedDataCallback;
import com.swirlds.merkledb.files.DataFileReader;
import com.swirlds.merkledb.serialize.KeySerializer;
import com.swirlds.virtualmap.VirtualKey;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.LongSummaryStatistics;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.collections.api.tuple.primitive.IntObjectPair;
import org.eclipse.collections.impl.map.mutable.primitive.IntObjectHashMap;

/**
 * This is a hash map implementation where the bucket index is in RAM and the buckets are on disk.
 * It maps a VirtualKey to a long value. This allows very large maps with minimal RAM usage and the
 * best performance profile as by using an in memory index we avoid the need for random disk writes.
 * Random disk writes are horrible performance wise in our testing.
 *
 * <p>This implementation depends on good hashCode() implementation on the keys, if there are too
 * many hash collisions the performance can get bad.
 *
 * <p><b>IMPORTANT: This implementation assumes a single writing thread. There can be multiple
 * readers while writing is happening.</b>
 */
public class HalfDiskHashMap<K extends VirtualKey>
        implements AutoCloseable, Snapshotable, FileStatisticAware, OffHeapUser {

    private static final Logger logger = LogManager.getLogger(HalfDiskHashMap.class);

    /** The version number for format of current data files */
    private static final int METADATA_FILE_FORMAT_VERSION = 1;
    /** Metadata file name suffix with extension. */
    private static final String METADATA_FILENAME_SUFFIX = "_metadata.hdhm";
    /** Bucket index file name suffix with extension */
    private static final String BUCKET_INDEX_FILENAME_SUFFIX = "_bucket_index.ll";
    /**
     * A marker to indicate that a value should be deleted from the map, or that there is
     * no old value to compare against in putIfEqual/deleteIfEqual
     */
    protected static final long INVALID_VALUE = Long.MIN_VALUE;

    /**
     * This is the average number of entries per bucket we aim for when filled to mapSize. It is a
     * heuristic used in calculation for how many buckets to create. The larger this number the
     * slower lookups will be but the more even distribution of entries across buckets will be. So
     * it is a matter of balance.
     */
    private static final long GOOD_AVERAGE_BUCKET_ENTRY_COUNT = 32;
    /** The limit on the number of concurrent read tasks in {@code endWriting()} */
    private static final int MAX_IN_FLIGHT = 64;

    /**
     * Long list used for mapping bucketIndex(index into list) to disk location for latest copy of
     * bucket
     */
    private final LongList bucketIndexToBucketLocation;
    /** DataFileCollection manages the files storing the buckets on disk */
    private final DataFileCollection<Bucket<K>> fileCollection;

    /**
     * This is the number of buckets needed to store mapSize entries if we ere only LOADING_FACTOR
     * percent full
     */
    private final int minimumBuckets;
    /**
     * This is the next power of 2 bigger than minimumBuckets. It needs to be a power of two, so
     * that we can optimize and avoid the cost of doing a % to find the bucket index from hash code.
     */
    private final int numOfBuckets;
    /**
     * The requested max size for the map, this is the maximum number of key/values expected to be
     * stored in this map.
     */
    private final long mapSize;
    /** The name to use for the files prefix on disk */
    private final String storeName;

    private final BucketSerializer<K> bucketSerializer;
    /** Store for session data during a writing transaction */
    private IntObjectHashMap<BucketMutation<K>> oneTransactionsData = null;
    /**
     * The thread that called startWriting. We use it to check that other writing calls are done on
     * same thread
     */
    private Thread writingThread;

    /** Executor for parallel bucket reads/updates in {@link #endWriting()} */
    private static volatile ExecutorService flushExecutor = null;

    private static ExecutorService getFlushExecutor() {
        ExecutorService exec = flushExecutor;
        if (exec == null) {
            synchronized (HalfDiskHashMap.class) {
                exec = flushExecutor;
                if (exec == null) {
                    final MerkleDbConfig config = ConfigurationHolder.getConfigData(MerkleDbConfig.class);
                    exec = Executors.newFixedThreadPool(
                            config.getNumHalfDiskHashMapFlushThreads(),
                            new ThreadConfiguration(getStaticThreadManager())
                                    .setComponent(MERKLEDB_COMPONENT)
                                    .setThreadName("HalfDiskHashMap Flushing")
                                    .setExceptionHandler((t, ex) -> logger.error(
                                            EXCEPTION.getMarker(), "Uncaught exception during HDHM flushing", ex))
                                    .buildFactory());
                    flushExecutor = exec;
                }
            }
        }
        return exec;
    }

    /**
     * Construct a new HalfDiskHashMap
     *
     * @param config                         MerkleDb config
     * @param mapSize                        The maximum map number of entries. This should be more than big enough to
     *                                       avoid too many key collisions.
     * @param keySerializer                  Serializer for converting raw data to/from keys
     * @param storeDir                       The directory to use for storing data files.
     * @param storeName                      The name for the data store, this allows more than one data store in a
     *                                       single directory.
     * @param legacyStoreName                Base name for the data store. If not null, the store will process
     *                                       files with this prefix at startup. New files in the store will be prefixed with {@code
     *                                       storeName}
     * @param preferDiskBasedIndex           When true we will use disk based index rather than ram where
     *                                       possible. This will come with a significant performance cost, especially for writing. It
     *                                       is possible to load a data source that was written with memory index with disk based
     *                                       index and vice versa.
     * @throws IOException If there was a problem creating or opening a set of data files.
     */
    public HalfDiskHashMap(
            final MerkleDbConfig config,
            final long mapSize,
            final KeySerializer<K> keySerializer,
            final Path storeDir,
            final String storeName,
            final String legacyStoreName,
            final boolean preferDiskBasedIndex)
            throws IOException {
        this.mapSize = mapSize;
        this.storeName = storeName;
        Path indexFile = storeDir.resolve(storeName + BUCKET_INDEX_FILENAME_SUFFIX);
        // create bucket serializer
        this.bucketSerializer = new BucketSerializer<>(config, keySerializer);
        // load or create new
        LoadedDataCallback<Bucket<K>> loadedDataCallback;
        if (Files.exists(storeDir)) {
            // load metadata
            Path metaDataFile = storeDir.resolve(storeName + METADATA_FILENAME_SUFFIX);
            boolean loadedLegacyMetadata = false;
            if (!Files.exists(metaDataFile)) {
                metaDataFile = storeDir.resolve(legacyStoreName + METADATA_FILENAME_SUFFIX);
                indexFile = storeDir.resolve(legacyStoreName + BUCKET_INDEX_FILENAME_SUFFIX);
                loadedLegacyMetadata = true;
            }
            if (Files.exists(metaDataFile)) {
                try (DataInputStream metaIn = new DataInputStream(Files.newInputStream(metaDataFile))) {
                    final int fileVersion = metaIn.readInt();
                    if (fileVersion != METADATA_FILE_FORMAT_VERSION) {
                        throw new IOException("Tried to read a file with incompatible file format version ["
                                + fileVersion
                                + "], expected ["
                                + METADATA_FILE_FORMAT_VERSION
                                + "].");
                    }
                    minimumBuckets = metaIn.readInt();
                    numOfBuckets = metaIn.readInt();
                }
                if (loadedLegacyMetadata) {
                    Files.delete(metaDataFile);
                }
            } else {
                logger.error(
                        EXCEPTION.getMarker(),
                        "Loading existing set of data files but no metadata file was found in [{}]",
                        storeDir.toAbsolutePath());
                throw new IOException("Can not load an existing HalfDiskHashMap from ["
                        + storeDir.toAbsolutePath()
                        + "] because metadata file is missing");
            }
            // load or rebuild index
            final boolean forceIndexRebuilding = config.indexRebuildingEnforced();
            if (Files.exists(indexFile) && !forceIndexRebuilding) {
                bucketIndexToBucketLocation =
                        preferDiskBasedIndex ? new LongListDisk(indexFile) : new LongListOffHeap(indexFile);
                loadedDataCallback = null;
            } else {
                // create new index and setup call back to rebuild
                bucketIndexToBucketLocation =
                        preferDiskBasedIndex ? new LongListDisk(indexFile) : new LongListOffHeap();
                loadedDataCallback = (dataLocation, bucket) ->
                        bucketIndexToBucketLocation.put(bucket.getBucketIndex(), dataLocation);
            }
        } else {
            // create store dir
            Files.createDirectories(storeDir);
            // create new index
            bucketIndexToBucketLocation = preferDiskBasedIndex ? new LongListDisk(indexFile) : new LongListOffHeap();
            // calculate number of entries we can store in a disk page
            minimumBuckets = (int) (mapSize / GOOD_AVERAGE_BUCKET_ENTRY_COUNT);
            // numOfBuckets is the nearest power of two greater than minimumBuckets with a min of 4096
            numOfBuckets = Integer.highestOneBit(minimumBuckets) * 2;
            // we are new so no need for a loadedDataCallback
            loadedDataCallback = null;
            logger.info(
                    MERKLE_DB.getMarker(),
                    "HalfDiskHashMap [{}] created with minimumBuckets={} and numOfBuckets={}",
                    storeName,
                    minimumBuckets,
                    numOfBuckets);
        }
        // create file collection
        fileCollection = new DataFileCollection<>(
                // Need: propagate MerkleDb config from the database
                config, storeDir, storeName, legacyStoreName, bucketSerializer, loadedDataCallback);
    }

    /**
     * Get the key serializer.
     *
     * @return the key serializer
     */
    public KeySerializer<K> getKeySerializer() {
        return bucketSerializer.getKeySerializer();
    }

    /** {@inheritDoc} */
    public void snapshot(final Path snapshotDirectory) throws IOException {
        // create snapshot directory if needed
        Files.createDirectories(snapshotDirectory);
        // write index to file
        bucketIndexToBucketLocation.writeToFile(snapshotDirectory.resolve(storeName + BUCKET_INDEX_FILENAME_SUFFIX));
        // snapshot files
        fileCollection.snapshot(snapshotDirectory);
        // write metadata
        try (DataOutputStream metaOut = new DataOutputStream(
                Files.newOutputStream(snapshotDirectory.resolve(storeName + METADATA_FILENAME_SUFFIX)))) {
            metaOut.writeInt(METADATA_FILE_FORMAT_VERSION);
            metaOut.writeInt(minimumBuckets);
            metaOut.writeInt(numOfBuckets);
            metaOut.flush();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getOffHeapConsumption() {
        if (bucketIndexToBucketLocation instanceof LongListOffHeap offheapIndex) {
            return offheapIndex.getOffHeapConsumption();
        }
        return 0;
    }

    /**
     * {@inheritDoc}
     */
    public LongSummaryStatistics getFilesSizeStatistics() {
        return fileCollection.getAllCompletedFilesSizeStatistics();
    }

    /**
     * Close this HalfDiskHashMap's data files. Once closed this HalfDiskHashMap can not be reused.
     * You should make sure you call close before system exit otherwise any files being written
     * might not be in a good state.
     *
     * @throws IOException If there was a problem closing the data files.
     */
    @Override
    public void close() throws IOException {
        bucketIndexToBucketLocation.close();
        fileCollection.close();
    }

    // =================================================================================================================
    // Writing API - Single thead safe

    /**
     * Start a writing session to the map. Each new writing session results in a new data file on
     * disk, so you should ideally batch up map writes.
     */
    public void startWriting() {
        oneTransactionsData = new IntObjectHashMap<>();
        writingThread = Thread.currentThread();
    }

    private BucketMutation<K> findBucketForUpdate(final K key, final long oldValue, final long value) {
        if (key == null) {
            throw new IllegalArgumentException("Can not write a null key");
        }
        if (oneTransactionsData == null) {
            throw new IllegalStateException(
                    "Trying to write to a HalfDiskHashMap when you have not called startWriting().");
        }
        if (Thread.currentThread() != writingThread) {
            throw new IllegalStateException("Tried to write with different thread to startWriting()");
        }
        // store key and value in transaction cache
        final int bucketIndex = computeBucketIndex(key.hashCode());
        return oneTransactionsData.getIfAbsentPut(bucketIndex, () -> new BucketMutation<>(key, oldValue, value));
    }

    /**
     * Put a key/value during the current writing session. The value will not be retrievable until
     * it is committed in the {@link #endWriting()} call.
     *
     * <p>This method may be called multiple times for the same key in a single writing
     * session. The value from the last call will be stored in this map after the session is
     * ended.
     *
     * @param key the key to store the value for
     * @param value the value to store for given key
     */
    public void put(final K key, final long value) {
        final BucketMutation<K> bucketMap = findBucketForUpdate(key, INVALID_VALUE, value);
        bucketMap.put(key, value);
    }

    /**
     * Put a key/value during the current writing session. This method is similar to {@link
     * #put(VirtualKey, long)}, but the new value is set only if the current value is equal to
     * the given {@code oldValue}.
     *
     * <p>This method may be called multiple times for the same key in a single writing
     * session. If the new value from the first call is equal to the old value in the second
     * call, the new value from the second call will be stored in this map after the session
     * is ended, otherwise the value from the second call will be ignored.
     *
     * <p>If the value for {@code oldValue} is {@link #INVALID_VALUE}, it's ignored, and this
     * method is identical to {@link #put(VirtualKey, long)}.
     *
     * @param key the key to store the value for
     * @param oldValue the value to check the current value against, or {@link #INVALID_VALUE}
     *                 if no current value check is needed
     * @param value the value to store for the given key
     */
    public void putIfEqual(final K key, final long oldValue, final long value) {
        final BucketMutation<K> bucketMap = findBucketForUpdate(key, oldValue, value);
        bucketMap.putIfEqual(key, oldValue, value);
    }

    /**
     * Delete a key entry from the map.
     *
     * @param key The key to delete entry for
     */
    public void delete(final K key) {
        put(key, INVALID_VALUE);
    }

    /**
     * Delete a key entry from the map, if the current value is equal to the given {@code oldValue}.
     * If {@code oldValue} is {@link #INVALID_VALUE}, no current value check is performed, and this
     * method is identical to {@link #delete(VirtualKey)}.
     *
     * @param key the key to delete the entry for
     * @param oldValue the value to check the current value against, or {@link #INVALID_VALUE}
     *                 if no current value check is needed
     */
    public void deleteIfEqual(final K key, final long oldValue) {
        putIfEqual(key, oldValue, INVALID_VALUE);
    }

    /**
     * End current writing session, committing all puts to data store.
     *
     * @return Data file reader for the file written
     * @throws IOException If there was a problem committing data to store
     */
    @Nullable
    public DataFileReader<Bucket<K>> endWriting() throws IOException {
        /* FUTURE WORK - https://github.com/swirlds/swirlds-platform/issues/3943 */
        if (Thread.currentThread() != writingThread) {
            throw new IllegalStateException("Tried calling endWriting with different thread to startWriting()");
        }
        writingThread = null;
        final int size = oneTransactionsData.size();
        logger.info(
                MERKLE_DB.getMarker(),
                "Finishing writing to {}, num of changed bins = {}, num of changed keys = {}",
                storeName,
                size,
                oneTransactionsData.stream().mapToLong(BucketMutation::size).sum());

        final ExecutorService flushExecutor = getFlushExecutor();
        final DataFileReader<Bucket<K>> dataFileReader;
        if (size > 0) {
            final Queue<ReadBucketResult<K>> queue = new ConcurrentLinkedQueue<>();
            final Iterator<IntObjectPair<BucketMutation<K>>> iterator =
                    oneTransactionsData.keyValuesView().iterator();

            // read and update all buckets in parallel, write sequentially in random order
            fileCollection.startWriting();
            int processed = 0;
            int inFlight = 0;
            while (processed < size) {
                // submit read tasks
                while (inFlight < MAX_IN_FLIGHT && iterator.hasNext()) {
                    IntObjectPair<BucketMutation<K>> keyValue = iterator.next();
                    final int bucketIndex = keyValue.getOne();
                    final BucketMutation<K> bucketMap = keyValue.getTwo();
                    flushExecutor.execute(() -> readUpdateQueueBucket(bucketIndex, bucketMap, queue));
                    ++inFlight;
                }

                ReadBucketResult<K> res;
                while ((res = queue.poll()) != null) {
                    --inFlight;
                    if (res.error != null) {
                        throw new RuntimeException(res.error);
                    }
                    try (final Bucket<K> bucket = res.bucket) {
                        final int bucketIndex = bucket.getBucketIndex();
                        if (bucket.isEmpty()) {
                            // bucket is missing or empty, remove it from the index
                            bucketIndexToBucketLocation.remove(bucketIndex);
                        } else {
                            // save bucket
                            final long bucketLocation = fileCollection.storeDataItem(bucket);
                            // update bucketIndexToBucketLocation
                            bucketIndexToBucketLocation.put(bucketIndex, bucketLocation);
                        }
                    } finally {
                        ++processed;
                    }
                }
            }
            // close files session
            dataFileReader = fileCollection.endWriting(0, numOfBuckets);
            // we have updated all indexes so the data file can now be included in merges
            dataFileReader.setFileCompleted();
        } else {
            dataFileReader = null;
        }

        // clear put cache
        oneTransactionsData = null;
        return dataFileReader;
    }

    /**
     * Reads a bucket with a given index from disk, updates given keys in it, and puts the bucket to
     * a queue. If an exception is thrown, it's put to the queue instead, so the number of {@code
     * ReadBucketResult} objects in the queue is consistent.
     *
     * @param bucketIndex The bucket index
     * @param keyUpdates Key/value updates to apply to the bucket
     * @param queue The queue to put the bucket or exception to
     */
    private void readUpdateQueueBucket(
            final int bucketIndex, final BucketMutation<K> keyUpdates, final Queue<ReadBucketResult<K>> queue) {
        try {
            // The bucket will be closed on the lifecycle thread
            Bucket<K> bucket = fileCollection.readDataItemUsingIndex(bucketIndexToBucketLocation, bucketIndex);
            if (bucket == null) {
                // create a new bucket
                bucket = bucketSerializer.getBucketPool().getBucket();
                bucket.setBucketIndex(bucketIndex);
            }
            // for each changed key in bucket, update bucket
            keyUpdates.forEachKeyValue(bucket::putValue);
            queue.offer(new ReadBucketResult<>(bucket, null));
        } catch (final Exception e) {
            logger.error(EXCEPTION.getMarker(), "Failed to read / update bucket", e);
            queue.offer(new ReadBucketResult<>(null, e));
        }
    }

    // =================================================================================================================
    // Reading API - Multi thead safe

    /**
     * Get a value from this map
     *
     * @param key The key to get value for
     * @param notFoundValue the value to return if the key was not found
     * @return the value retrieved from the map or {notFoundValue} if no value was stored for the
     *     given key
     * @throws IOException If there was a problem reading from the map
     */
    public long get(final K key, final long notFoundValue) throws IOException {
        if (key == null) {
            throw new IllegalArgumentException("Can not get a null key");
        }
        final int keyHash = key.hashCode();
        final int bucketIndex = computeBucketIndex(keyHash);
        try (final Bucket<K> bucket = fileCollection.readDataItemUsingIndex(bucketIndexToBucketLocation, bucketIndex)) {
            if (bucket != null) {
                return bucket.findValue(keyHash, key, notFoundValue);
            }
        }
        return notFoundValue;
    }

    // =================================================================================================================
    // Debugging Print API

    /** Debug dump stats for this map */
    public void printStats() {
        logger.info(
                MERKLE_DB.getMarker(),
                """
                        HalfDiskHashMap Stats {
                        	mapSize = {}
                        	minimumBuckets = {}
                        	numOfBuckets = {}
                        	GOOD_AVERAGE_BUCKET_ENTRY_COUNT = {}
                        }""",
                mapSize,
                minimumBuckets,
                numOfBuckets,
                GOOD_AVERAGE_BUCKET_ENTRY_COUNT);
    }

    public DataFileCollection<Bucket<K>> getFileCollection() {
        return fileCollection;
    }

    public CASableLongIndex getBucketIndexToBucketLocation() {
        return bucketIndexToBucketLocation;
    }

    // =================================================================================================================
    // Private API

    /**
     * Computes which bucket a key with the given hash falls. Depends on the fact the numOfBuckets
     * is a power of two. Based on same calculation that is used in java HashMap.
     *
     * @param keyHash the int hash for key
     * @return the index of the bucket that key falls in
     */
    private int computeBucketIndex(final int keyHash) {
        return (numOfBuckets - 1) & keyHash;
    }

    private record ReadBucketResult<K extends VirtualKey>(Bucket<K> bucket, Throwable error) {
        public ReadBucketResult {
            assert (bucket != null) ^ (error != null);
        }
    }
}
