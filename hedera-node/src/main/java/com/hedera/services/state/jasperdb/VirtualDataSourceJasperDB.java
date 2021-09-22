package com.hedera.services.state.jasperdb;

import com.hedera.services.state.jasperdb.collections.HashList;
import com.hedera.services.state.jasperdb.collections.HashListOffHeap;
import com.hedera.services.state.jasperdb.collections.LongList;
import com.hedera.services.state.jasperdb.collections.LongListHeap;
import com.hedera.services.state.jasperdb.files.DataFileCollection.LoadedDataCallback;
import com.hedera.services.state.jasperdb.files.DataFileReader;
import com.hedera.services.state.jasperdb.files.MemoryIndexDiskKeyValueStore;
import com.hedera.services.state.jasperdb.files.hashmap.Bucket;
import com.hedera.services.state.jasperdb.files.hashmap.HalfDiskHashMap;
import com.hedera.services.state.jasperdb.files.hashmap.KeySerializer;
import com.swirlds.common.crypto.Hash;
import com.swirlds.virtualmap.VirtualKey;
import com.swirlds.virtualmap.VirtualLongKey;
import com.swirlds.virtualmap.VirtualValue;
import com.swirlds.virtualmap.datasource.VirtualDataSource;
import com.swirlds.virtualmap.datasource.VirtualInternalRecord;
import com.swirlds.virtualmap.datasource.VirtualLeafRecord;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.function.Function;
import java.util.stream.Stream;

import static com.hedera.services.state.jasperdb.files.DataFileCommon.newestFilesSmallerThan;

/**
 * An implementation of VirtualDataSource that uses JasperDB.
 * <p>
 * <b>IMPORTANT: This implementation assumes a single writing thread. There can be multiple readers while writing is
 * happening.</b>
 * <p>
 * It uses 3 main data stores to support the API of VirtualDataSource
 * <ul>
 *     <li>Path -> Internal Hashes (internalHashStoreRam or internalHashStoreDisk)</li>
 *     <li>Key -> Path (longKeyToPath or objectKeyToPath)</li>
 *     <li>Path -> Hash,Key & Value (pathToHashKeyValue)</li>
 * </ul>
 *
 * @param <K> type for keys
 * @param <V> type for values
 */
@SuppressWarnings({"jol", "DuplicatedCode"})
public class VirtualDataSourceJasperDB<K extends VirtualKey, V extends VirtualValue> implements VirtualDataSource<K, V> {
    /** We have an optimized mode when the keys can be represented by a single long */
    private final boolean isLongKeyMode;
    /**
     * In memory off-heap store for internal node hashes. This data is never stored on disk so on load from disk, this
     * will be empty. That should cause all internal node hashes to have to be computed on the first round which will be
     * expensive. TODO is it worth saving this to disk on close? Should we use a memMap file after all for this?
     */
    private final HashList internalHashStoreRam;
    /**
     * On disk store for internal hashes. Can be null if all hashes are being stored in ram by setting
     * internalHashesRamToDiskThreshold to Long.MAX_VALUE.
     */
    private final MemoryIndexDiskKeyValueStore<VirtualInternalRecord> internalHashStoreDisk;
    /**
     * Threshold where we switch from storing internal hashes in ram to storing them on disk. If it is 0 then everything
     * is on disk, if it is Long.MAX_VALUE then everything is in ram. Any value in the middle is the path value at which
     * we swap from ram to disk. This allows a tree where the lower levels of the tree nodes hashes are in ram and the
     * upper larger less changing layers are on disk.
     */
    private final long internalHashesRamToDiskThreshold;
    /** True when internalHashesRamToDiskThreshold is less than Long.MAX_VALUE */
    private final boolean hasDiskStoreForInternalHashes;
    /** In memory off-heap store for key to path map, this is used when isLongKeyMode=true and keys are longs */
    private final LongList longKeyToPath;
    /** Mixed disk and off-heap memory store for key to path map, this is used if isLongKeyMode=false, and we have complex keys. */
    private final HalfDiskHashMap<K> objectKeyToPath;
    /** Mixed disk and off-heap memory store for path to leaf key, hash and value */
    private final MemoryIndexDiskKeyValueStore<VirtualLeafRecord<K,V>> pathToHashKeyValue;
    /** Group for all our threads */
    private final ThreadGroup threadGroup = new ThreadGroup("JasperDB");
    /** ScheduledThreadPool for executing merges */
    private final ScheduledThreadPoolExecutor mergingExecutor =
            new ScheduledThreadPoolExecutor(1, runnable -> {
                Thread thread = new Thread(threadGroup,runnable,"Merging");
                thread.setDaemon(true); // TODO is this right, what happens if we try and quit VM part way though a merge
                return thread;
            });
    /** Thead pool storing internal records */
    private final ExecutorService storeInternalExecutor = Executors.newSingleThreadExecutor(
            runnable -> new Thread(threadGroup, runnable,"Store Internal Records"));
    /** Thead pool storing internal records */
    private final ExecutorService storeKeyToPathExecutor = Executors.newSingleThreadExecutor(
            runnable -> new Thread(threadGroup, runnable,"Store Key to Path"));
    /** When was the last medium-sized merge, only touched from single merge thread. */
    private Instant lastMediumMerge = Instant.now();
    /** When was the last full merge, only touched from single merge thread. */
    private Instant lastFullMerge = Instant.now();

    /**
     * Create new VirtualDataSourceJasperDB
     *
     * @param virtualLeafRecordSerializer Serializer for converting raw data to/from VirtualLeafRecords
     * @param virtualInternalRecordSerializer Serializer for converting raw data to/from VirtualInternalRecords
     * @param keySerializer Serializer for converting raw data to/from keys
     * @param storageDir directory to store data files in
     * @param maxNumOfKeys the maximum number of unique keys. This is used for calculating in memory index sizes
     * @param internalHashesRamToDiskThreshold When path value at which we switch from hashes in ram to stored on disk
     */
    public VirtualDataSourceJasperDB(VirtualLeafRecordSerializer<K,V> virtualLeafRecordSerializer,
                                     VirtualInternalRecordSerializer virtualInternalRecordSerializer,
                                     KeySerializer<K> keySerializer,
                                     Path storageDir, long maxNumOfKeys,
                                     boolean mergingEnabled, long internalHashesRamToDiskThreshold) throws IOException {
        final LoadedDataCallback loadedDataCallback;
        this.hasDiskStoreForInternalHashes = internalHashesRamToDiskThreshold < Long.MAX_VALUE;
        this.internalHashStoreRam = (internalHashesRamToDiskThreshold > 0) ? new HashListOffHeap() : null;
        this.internalHashStoreDisk = hasDiskStoreForInternalHashes ?
                new MemoryIndexDiskKeyValueStore<>(storageDir,"internalHashes",
                        virtualInternalRecordSerializer, null)  // TODO need to implement loaded data callback maybe?
                : null;
        this.internalHashesRamToDiskThreshold = internalHashesRamToDiskThreshold;
        if (keySerializer.getSerializedSize() == Long.BYTES) {
            isLongKeyMode = true;
            longKeyToPath = new LongListHeap();
            objectKeyToPath = null;
            loadedDataCallback = (path, dataLocation, hashKeyValueData) -> {
                // read key from hashKeyValueData, as we are in isLongKeyMode mode then the key is a single long
                long key = hashKeyValueData.getLong(0);
                // update index
                longKeyToPath.put(key,path);
            };
        } else {
            isLongKeyMode = false;
            longKeyToPath =  null;
            objectKeyToPath = new HalfDiskHashMap<>(maxNumOfKeys,keySerializer,storageDir, "objectKeyToPath");
            // we do not need callback as HalfDiskHashMap loads its own data from disk
            loadedDataCallback = null;
        }
        pathToHashKeyValue = new MemoryIndexDiskKeyValueStore<>(storageDir,"pathToHashKeyValue",
                virtualLeafRecordSerializer, loadedDataCallback);
        // If merging is enabled then merge all data files every 5 minutes
        if (mergingEnabled) {
            mergingExecutor.scheduleWithFixedDelay(this::doMerge,1,5, TimeUnit.MINUTES);
        }
    }

    //==================================================================================================================
    // Public  API methods

    /**
     * Save a batch of data to data store.
     *
     * @param firstLeafPath the tree path for first leaf
     * @param lastLeafPath the tree path for last leaf
     * @param internalRecords stream of records for internal nodes, it is assumed this is sorted by path and each path only appears once.
     * @param leafRecords stream of records for leaf nodes, it is assumed this is sorted by key and each key only appears once.
     */
    @Override
    public void saveRecords(long firstLeafPath, long lastLeafPath, Stream<VirtualInternalRecord> internalRecords,
                            Stream<VirtualLeafRecord<K, V>> leafRecords) throws IOException {
        final var countDownLatch = new CountDownLatch(1);
        // might as well write to the 3 data stores in parallel, so lets fork 2 threads for the easy stuff
        storeInternalExecutor.execute(() -> {
            try {
                writeInternalRecords(firstLeafPath, internalRecords);
            } catch (IOException e) {
                e.printStackTrace(); // TODO something better please :-)
                throw new RuntimeException(e);
            }
            countDownLatch.countDown();
        });
        // we might as well do this in the archive thread rather than leaving it waiting
        writeLeavesToPathToHashKeyValue(firstLeafPath, lastLeafPath, leafRecords);
        // wait for the other two threads in the rare case they are not finished yet. We need to have all writing done
        // before we return as when we return the state version we are writing is deleted from the cache and the flood
        // gates are opened for reads through to the data we have written here.
        try {
            countDownLatch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    /**
     * Load a leaf record by key
     *
     * @param key they to the leaf to load record for
     * @return loaded record or null if not found
     * @throws IOException If there was a problem reading record from db
     */
    @Override
    public VirtualLeafRecord<K, V> loadLeafRecord(K key) throws IOException {
        Objects.requireNonNull(key);
        final long path = isLongKeyMode
                ? longKeyToPath.get(((VirtualLongKey)key).getKeyAsLong(), INVALID_PATH)
                : objectKeyToPath.get(key, INVALID_PATH);
        if (path == INVALID_PATH) return null;
        return pathToHashKeyValue.get(path);
    }

    /**
     * Load a leaf record by path
     *
     * @param path the path for the leaf we are loading
     * @return loaded record or null if not found
     * @throws IOException If there was a problem reading record from db
     */
    @Override
    public VirtualLeafRecord<K, V> loadLeafRecord(long path) throws IOException {
        return pathToHashKeyValue.get(path);
    }

    /**
     * Load hash for a leaf node with given path
     *
     * @param path the path to get hash for
     * @return loaded hash or null if hash is not stored
     * @throws IOException if there was a problem loading hash
     */
    @Override
    public Hash loadLeafHash(long path) throws IOException {
        if (path < 0) throw new IllegalArgumentException("path is less than 0");
        // read value
        // TODO could do with a way to deserialize only the hash
        final var leafRecord = pathToHashKeyValue.get(path);
        if (leafRecord == null) return null;
        // deserialize hash
        return leafRecord.getHash();
    }

    /**
     * Load the record for an internal node by path
     *
     * @param path the path for a internal
     * @return the internal node's record if one was stored for the given path or null if not stored
     * @throws IOException If there was a problem reading the internal record
     */
    @Override
    public VirtualInternalRecord loadInternalRecord(long path) throws IOException {
        if (path < 0) throw new IllegalArgumentException("path is less than 0");
        if (path < internalHashesRamToDiskThreshold) {
            final Hash hash = internalHashStoreRam.get(path);
            if (hash == null) return null;
            return new VirtualInternalRecord(path,hash);
        } else {
            return internalHashStoreDisk.get(path);
        }
    }

    /**
     * Wait for any merges to finish and then close all data stores.
     */
    @Override
    public void close() throws IOException {
        try {
            for(var executor: new ExecutorService[]{mergingExecutor,storeInternalExecutor,storeKeyToPathExecutor}) {
                executor.shutdown();
                boolean finishedWithoutTimeout = executor.awaitTermination(5,TimeUnit.MINUTES);
                if (!finishedWithoutTimeout)
                    throw new IOException("Timeout while waiting for executor service to finish.");
            }
        } catch (InterruptedException e) {
            throw new IOException("Interrupted while waiting for merge to finish.",e);
        } finally {
            if (objectKeyToPath!= null) objectKeyToPath.close();
            pathToHashKeyValue.close();
        }
    }

    //==================================================================================================================
    // private methods

    /**
     * Write all internal records hashes to internalHashStore
     */
    private void writeInternalRecords(long firstLeafPath, Stream<VirtualInternalRecord> internalRecords) throws IOException {
        if (internalRecords != null) {
            // use an iterator rather than stream.forEach so that exceptions are propagated properly
            final Iterator<VirtualInternalRecord> internalRecordIter = internalRecords.iterator();
            if (hasDiskStoreForInternalHashes) internalHashStoreDisk.startWriting();
            while (internalRecordIter.hasNext()) {
                VirtualInternalRecord rec = internalRecordIter.next();
                if (rec.getPath() < internalHashesRamToDiskThreshold) {
                    internalHashStoreRam.put(rec.getPath(), rec.getHash());
                } else {
                    internalHashStoreDisk.put(rec.getPath(), rec);
                }
            }
            if (hasDiskStoreForInternalHashes) internalHashStoreDisk.endWriting(0,firstLeafPath-1);
        }
    }

    /**
     * Write all the given leaf records to pathToHashKeyValue
     */
    private void writeLeavesToPathToHashKeyValue(long firstLeafPath, long lastLeafPath,
                                                 Stream<VirtualLeafRecord<K, V>> leafRecords) throws IOException {
        if (leafRecords != null) {
            var leafRecordIter = leafRecords.iterator();
            // start writing
            pathToHashKeyValue.startWriting();
            if (!isLongKeyMode) objectKeyToPath.startWriting();
            // iterate over leaf records
            while (leafRecordIter.hasNext()) {
                VirtualLeafRecord<K, V> leafRecord = leafRecordIter.next();
                // update objectKeyToPath
                if (isLongKeyMode) {
                    longKeyToPath.put(((VirtualLongKey) leafRecord.getKey()).getKeyAsLong(), leafRecord.getPath());
                } else {
                    objectKeyToPath.put(leafRecord.getKey(), leafRecord.getPath());
                }
                // update pathToHashKeyValue
                pathToHashKeyValue.put(leafRecord.getPath(),leafRecord);
            }
            // end writing
            pathToHashKeyValue.endWriting(firstLeafPath, lastLeafPath);
            if(!isLongKeyMode) objectKeyToPath.endWriting();
        }
    }

    /**
     * Start a Merge. This implements the logic for how often and with what files we merge. The set of files we merge
     * must always be contiguous in order of time contained data created. As merged files have a later index but old
     * data the index can not be used alone to work out order of files to merge.
     */
    @SuppressWarnings("rawtypes")
    private void doMerge() {
        final Instant startMerge = Instant.now();
        Function<List<DataFileReader>, List<DataFileReader>> filesToMergeFilter;
        if (startMerge.minus(2, ChronoUnit.HOURS).isAfter(lastFullMerge)) { // every 2 hours
            lastFullMerge = startMerge;
            filesToMergeFilter = dataFileReaders -> dataFileReaders; // everything
        } else if (startMerge.minus(30, ChronoUnit.MINUTES).isAfter(lastMediumMerge)) { // every 30 min
            lastMediumMerge = startMerge;
            filesToMergeFilter = newestFilesSmallerThan(10*1024); // < 10Gb
        } else { // every 5 minutes
            filesToMergeFilter = newestFilesSmallerThan(3*1024); // < 3Gb
        }
        try {
            // we need to merge disk files for internal hashes if they exist and pathToHashKeyValue store
            if (hasDiskStoreForInternalHashes) {
                // horrible hack to get around generics because file filters work on any type of DataFileReader
                @SuppressWarnings("unchecked") var internalRecordFileFilter =
                        (Function<List<DataFileReader<VirtualInternalRecord>>, List<DataFileReader<VirtualInternalRecord>>>)((Object)filesToMergeFilter);
                internalHashStoreDisk.merge(internalRecordFileFilter);
            }
            // merge objectKeyToPath files
            if(!isLongKeyMode) {
                // horrible hack to get around generics because file filters work on any type of DataFileReader
                @SuppressWarnings("unchecked") var bucketFileFilter =
                        (Function<List<DataFileReader<Bucket<K>>>, List<DataFileReader<Bucket<K>>>>)((Object)filesToMergeFilter);
                objectKeyToPath.merge(bucketFileFilter);
            }
            // now do main merge of pathToHashKeyValue store
            // horrible hack to get around generics because file filters work on any type of DataFileReader
            @SuppressWarnings("unchecked") var leafRecordFileFilter =
                    (Function<List<DataFileReader<VirtualLeafRecord<K,V>>>, List<DataFileReader<VirtualLeafRecord<K,V>>>>)((Object)filesToMergeFilter);
            pathToHashKeyValue.merge(leafRecordFileFilter);
        } catch (Throwable t) {
            System.err.println("Exception while merging!");
            t.printStackTrace();
        }
    }
}
