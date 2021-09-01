package com.hedera.services.state.jasperdb;

import com.hedera.services.state.jasperdb.collections.*;
import com.hedera.services.state.jasperdb.files.DataFileCollection.LoadedDataCallback;
import com.hedera.services.state.jasperdb.files.DataFileReader;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.io.SerializableDataOutputStream;
import com.swirlds.virtualmap.VirtualKey;
import com.swirlds.virtualmap.VirtualLongKey;
import com.swirlds.virtualmap.VirtualValue;
import com.swirlds.virtualmap.datasource.VirtualDataSource;
import com.swirlds.virtualmap.datasource.VirtualInternalRecord;
import com.swirlds.virtualmap.datasource.VirtualLeafRecord;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static com.hedera.services.state.jasperdb.HashTools.HASH_SIZE_BYTES;
import static com.hedera.services.state.jasperdb.HashTools.byteBufferToHash;
import static com.hedera.services.state.jasperdb.files.DataFileCommon.VARIABLE_DATA_SIZE;
import static com.hedera.services.state.jasperdb.files.DataFileCommon.newestFilesSmallerThan;
import static java.nio.ByteBuffer.allocate;

/**
 * An implementation of VirtualDataSource that uses JasperDB.
 *
 * IMPORTANT: This implementation assumes a single writing thread. There can be multiple readers while writing is happening.
 * Also, we totally depend on the hash and key to be fixed sizes.
 *
 * @param <K> type for keys
 * @param <V> type for values
 */
@SuppressWarnings({"jol", "DuplicatedCode"})
public class VirtualDataSourceJasperDB<K extends VirtualKey, V extends VirtualValue> implements VirtualDataSource<K, V> {
    /** The size in bytes for serialized key objects */
    private final int keySizeBytes;
    /** Constructor for creating new key objects during de-serialization */
    private final Supplier<K> keyConstructor;
    /** The size in bytes for serialized value objects */
    private final int valueSizeBytes;
    /** Constructor for creating new value objects during de-serialization */
    private final Supplier<V> valueConstructor;
    /** We have an optimized mode when the keys can be represented by a single long */
    private final boolean isLongKeyMode;
    /**
     * In memory off-heap store for internal node hashes. This data is never stored on disk so on load from disk, this
     * will be empty. That should cause all internal node hashes to have to be computed on the first round which will be
     * expensive. TODO is it worth saving this to disk on close? Should we use a memMap file after all for this?
     */
    private final HashListOffHeap internalHashStoreRam;
    /**
     * On disk store for internal hashes. Can be null if all hashes are being stored in ram by setting
     * internalHashesRamToDiskThreshold to Long.MAX_VALUE.
     */
    private final MemoryIndexDiskKeyValueStore internalHashStoreDisk;
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
    private final MemoryIndexDiskKeyValueStore pathToKeyHashValue;
    /** Thread local reusable buffer for reading key, hash and value sets */
    private final ThreadLocal<ByteBuffer> keyHashValue;
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
    /** computed based on valueSizeBytes == DataFileCommon.VARIABLE_DATA_SIZE */
    private final boolean hasVariableDataSize;
    /** When was the last medium-sized merge, only touched from single merge thread. */
    private Instant lastMediumMerge = Instant.now();
    /** When was the last full merge, only touched from single merge thread. */
    private Instant lastFullMerge = Instant.now();

    /**
     * Create new VFCDataSourceImplV3 with merging enabled
     * @param keySizeBytes the size of key when serialized in bytes
     * @param keyConstructor constructor for creating keys for deserialization
     * @param valueSizeBytes the size of value when serialized in bytes
     * @param valueConstructor constructor for creating values for deserialization
     * @param storageDir directory to store data files in
     * @param maxNumOfKeys the maximum number of unique keys. This is used for calculating in memory index sizes
     * @param internalHashesRamToDiskThreshold When path value at which we switch from hashes in ram to hashes stored on
     *                                         disk. 0 means all on disk and Long.MAX_VALUE means all in ram.
     */
    public VirtualDataSourceJasperDB(int keySizeBytes, Supplier<K> keyConstructor, int valueSizeBytes, Supplier<V> valueConstructor,
                                     Path storageDir, long maxNumOfKeys, long internalHashesRamToDiskThreshold) throws IOException {
        this(keySizeBytes, keyConstructor, valueSizeBytes,valueConstructor,storageDir,
                maxNumOfKeys,true, internalHashesRamToDiskThreshold);
    }

    /**
     * Create new VFCDataSourceImplV3
     * @param keySizeBytes the size of key when serialized in bytes
     * @param keyConstructor constructor for creating keys for deserialization
     * @param valueSizeBytes the size of value when serialized in bytes
     * @param valueConstructor constructor for creating values for deserialization
     * @param storageDir directory to store data files in
     * @param maxNumOfKeys the maximum number of unique keys. This is used for calculating in memory index sizes
     * @param internalHashesRamToDiskThreshold When path value at which we switch from hashes in ram to hashes stored on
     *                                         disk. 0 means all on disk and Long.MAX_VALUE means all in ram.
     */
    public VirtualDataSourceJasperDB(int keySizeBytes, Supplier<K> keyConstructor, int valueSizeBytes, Supplier<V> valueConstructor,
                                     Path storageDir, long maxNumOfKeys, boolean mergingEnabled, long internalHashesRamToDiskThreshold) throws IOException {
        this.keySizeBytes = Integer.BYTES + keySizeBytes; // extra leading integer keeps track of the version
        this.keyConstructor = keyConstructor;
        this.valueSizeBytes = Integer.BYTES + valueSizeBytes;  // extra leading integer keeps track of the version
        this.valueConstructor = valueConstructor;
        this.hasVariableDataSize = valueSizeBytes == VARIABLE_DATA_SIZE;
        final var keyHashValueSize = hasVariableDataSize ? VARIABLE_DATA_SIZE :
                this.keySizeBytes + HASH_SIZE_BYTES + this.valueSizeBytes;
        this.keyHashValue = hasVariableDataSize ? null : ThreadLocal.withInitial(() -> allocate(keyHashValueSize));
        final LoadedDataCallback loadedDataCallback;
        this.hasDiskStoreForInternalHashes = internalHashesRamToDiskThreshold < Long.MAX_VALUE;
        this.internalHashStoreRam = (internalHashesRamToDiskThreshold > 0) ? new HashListOffHeap() : null;
        this.internalHashStoreDisk = hasDiskStoreForInternalHashes ?  new MemoryIndexDiskKeyValueStore(storageDir,"internalHashes",
                    HASH_SIZE_BYTES,null) : null;
        this.internalHashesRamToDiskThreshold = internalHashesRamToDiskThreshold;
        if (keySizeBytes == Long.BYTES) {
            isLongKeyMode = true;
            longKeyToPath = new LongListHeap();
            objectKeyToPath = null;
            loadedDataCallback = (path, dataLocation, keyHashValueData) -> {
                // read key from keyHashValueData, as we are in isLongKeyMode mode then the key is a single long
                long key = keyHashValueData.getLong(0);
                // update index
                longKeyToPath.put(key,path);
            };
        } else {
            isLongKeyMode = false;
            longKeyToPath =  null;
            objectKeyToPath = new HalfDiskHashMap<>(maxNumOfKeys,keySizeBytes,keyConstructor,storageDir,"objectKeyToPath");
            // we do not need callback as HalfDiskHashMap loads its own data from disk
            loadedDataCallback = null;
        }
        pathToKeyHashValue = new MemoryIndexDiskKeyValueStore(storageDir,"pathToKeyHashValue", keyHashValueSize, loadedDataCallback);
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
                            Stream<VirtualLeafRecord<K, V>> leafRecords) {
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
        writeLeavesToPathToKeyHashValue(firstLeafPath, lastLeafPath, leafRecords);
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
        return loadLeafRecord(path,key);
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
        return loadLeafRecord(path,null);
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
        final ByteBuffer keyHashValueBuffer; // TODO can we add method to data files to read a subset of data
        if (hasVariableDataSize) {
            // read value
            keyHashValueBuffer =  pathToKeyHashValue.get(path);
            if (keyHashValueBuffer == null) return null;
        } else {
            keyHashValueBuffer = this.keyHashValue.get().clear();
            // read value
            boolean found = pathToKeyHashValue.get(path, keyHashValueBuffer);
            if (!found) return null;
        }
        // deserialize hash
        keyHashValueBuffer.rewind();
        keyHashValueBuffer.position(keySizeBytes); // jump over key
        return byteBufferToHash(keyHashValueBuffer);
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
            return new VirtualInternalRecord(path,internalHashStoreRam.get(path));
        } else {
            ByteBuffer buf = ByteBuffer.allocate(HASH_SIZE_BYTES);
            internalHashStoreDisk.get(path,buf);
            return new VirtualInternalRecord(path,HashTools.byteBufferToHashNoCopy(buf));
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
            pathToKeyHashValue.close();
        }
    }

    //==================================================================================================================
    // private methods

    /**
     * load a leaf record by path, using the provided key or if null deserializing the key.
     */
    private VirtualLeafRecord<K, V> loadLeafRecord(long path, K key) throws IOException {
        final ByteBuffer keyHashValueBuffer;
        if (hasVariableDataSize) {
            // read value
            keyHashValueBuffer = pathToKeyHashValue.get(path);
            if (keyHashValueBuffer == null) return null;
        } else {
            // get reusable buffer
            keyHashValueBuffer = this.keyHashValue.get().clear();
            // read value
            final boolean found = pathToKeyHashValue.get(path, keyHashValueBuffer);
            if (!found) return null;
        }
        // deserialize
        keyHashValueBuffer.rewind();
        // deserialize key
        if (key != null) {
            // jump past the key because we don't need to deserialize it
            keyHashValueBuffer.position(keySizeBytes);
        } else {
            final int keySerializationVersion = keyHashValueBuffer.getInt();
            key = keyConstructor.get();
            key.deserialize(keyHashValueBuffer, keySerializationVersion);
        }
        // deserialize hash
        final Hash hash = byteBufferToHash(keyHashValueBuffer);
        // deserialize value
        final int valueSerializationVersion = keyHashValueBuffer.getInt();
        final V value = valueConstructor.get();
        value.deserialize(keyHashValueBuffer, valueSerializationVersion);
        // return new VirtualLeafRecord
        return new VirtualLeafRecord<>(path, hash, key, value);
    }

    /**
     * Write all internal records hashes to internalHashStore
     */
    private void writeInternalRecords(long firstLeafPath, Stream<VirtualInternalRecord> internalRecords) throws IOException {
        if (internalRecords != null) {
            if (hasDiskStoreForInternalHashes) internalHashStoreDisk.startWriting();
            internalRecords.forEachOrdered(rec -> {
                try {
                    if (rec.getPath() < internalHashesRamToDiskThreshold) {
                        internalHashStoreRam.put(rec.getPath(), rec.getHash());
                    } else {
                        var out = internalHashStoreDisk.startStreamingPut(rec.getPath(), HASH_SIZE_BYTES);
                        out.write(rec.getHash().getValue());
                        internalHashStoreDisk.endStreamingPut();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                    throw new RuntimeException(e);
                }
            });
            if (hasDiskStoreForInternalHashes) internalHashStoreDisk.endWriting(0,firstLeafPath-1);
        }
    }

    /**
     * Write all the given leaf records to pathToKeyHashValue
     */
    private void writeLeavesToPathToKeyHashValue(long firstLeafPath, long lastLeafPath,
                                                 Stream<VirtualLeafRecord<K, V>> leafRecords) {
        if (leafRecords != null) {
            try {
                pathToKeyHashValue.startWriting();
                // get reusable buffer
                if (!isLongKeyMode) objectKeyToPath.startWriting();

                leafRecords
                        .peek(rec -> {
                            // update objectKeyToPath
                            if (isLongKeyMode) {
                                longKeyToPath.put(((VirtualLongKey) rec.getKey()).getKeyAsLong(), rec.getPath());
                            } else {
                                objectKeyToPath.put(rec.getKey(), rec.getPath());
                            }
                        })
                        .parallel()
                        .map(rec -> new SerializedVirtualLeafRecord<>(rec,hasVariableDataSize,valueSizeBytes))
                        .forEachOrdered(serializedVirtualLeafRecord -> {
                            try {
                                // now save pathToKeyHashValue
                                final var out = pathToKeyHashValue.startStreamingPut(
                                        serializedVirtualLeafRecord.path, serializedVirtualLeafRecord.valueSize);
                                out.write(serializedVirtualLeafRecord.keyHashValue);
                                pathToKeyHashValue.endStreamingPut();
                            } catch (IOException e) {
                                e.printStackTrace();
                                throw new RuntimeException(e);
                            }
                        });
                pathToKeyHashValue.endWriting(firstLeafPath, lastLeafPath);
                if(!isLongKeyMode) objectKeyToPath.endWriting();
            } catch (IOException e) {
                // TODO maybe need a way to make sure streams / writers are closed if there is an exception?
                throw new RuntimeException(e); // TODO maybe re-wrap into IOException?
            }
        }
    }

    /**
     * Start a Merge. This implements the logic for how often and with what files we merge. The set of files we merge
     * must always be contiguous in order of time contained data created. As merged files have a later index but old
     * data the index can not be used alone to work out order of files to merge.
     */
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
            filesToMergeFilter = newestFilesSmallerThan(2*1024); // < 2Gb
        }
        try {
            // we need to merge disk files for internal hashes if they exist and pathToKeyHashValue store
            if (hasDiskStoreForInternalHashes) internalHashStoreDisk.mergeAll(filesToMergeFilter);
            // now do main merge of pathToKeyHashValue store
            pathToKeyHashValue.mergeAll(filesToMergeFilter);
        } catch (Throwable t) {
            System.err.println("Exception while merging!");
            t.printStackTrace();
        }
    }

    //==================================================================================================================
    // SerializedVirtualRecord class

    /**
     * Class used for temporary storage of serialized virtual leaf records. The allows us to use parallel background
     * threads to serialize VirtualRecord before they are written to disk.
     */
    private static final class SerializedVirtualLeafRecord<K extends VirtualKey, V extends VirtualValue> {
        /** Reusable ByteArrayOutputStreams */
        private static final ThreadLocal<ByteArrayOutputStream> reusableStreams =
                ThreadLocal.withInitial(() -> new ByteArrayOutputStream(1024));
        /** leaf path */
        final long path;
        /** leaf value size when serialized in bytes */
        final int valueSize;
        /** key, hash and value serialized to byte[] */
        final byte[] keyHashValue;

        public SerializedVirtualLeafRecord(VirtualLeafRecord<K,V> leafRecord, boolean hasVariableDataSize, int valueSizeBytes) {
            try {
                this.path = leafRecord.getPath();
                final VirtualKey key = leafRecord.getKey();
                final Hash hash = leafRecord.getHash();
                final VirtualValue value = leafRecord.getValue();

                final ByteArrayOutputStream bout = reusableStreams.get();
                bout.reset();
                final SerializableDataOutputStream outputStream = new SerializableDataOutputStream(bout);

                if (hasVariableDataSize) {
                    // now write key, hash and value to stream 1
                    // put key
                    outputStream.writeInt(key.getVersion());
                    key.serialize(outputStream);
                    // put hash
                    outputStream.write(hash.getValue());
                    // put value
                    outputStream.writeInt(value.getVersion());
                    outputStream.flush();
                    final int sizeBefore = bout.size();
                    // write value to second stream as we need its size
                    value.serialize(outputStream);
                    outputStream.flush();
                    // get value size
                    valueSize = bout.size() - sizeBefore;
                } else {
                    valueSize = valueSizeBytes;
                    // put key
                    outputStream.writeInt(key.getVersion());
                    key.serialize(outputStream);
                    // put hash
                    outputStream.write(hash.getValue());
                    // put value
                    outputStream.writeInt(value.getVersion());
                    value.serialize(outputStream);
                }
                // flush everything
                outputStream.flush();
                // store a copy of array data
                keyHashValue = bout.toByteArray();
            } catch (IOException e) {
                e.printStackTrace();
                throw new RuntimeException(e);
            }
        }
    }
}
