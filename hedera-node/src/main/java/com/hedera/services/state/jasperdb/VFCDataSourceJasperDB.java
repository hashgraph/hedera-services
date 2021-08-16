package com.hedera.services.state.jasperdb;

import com.hedera.services.state.jasperdb.collections.HalfDiskHashMap;
import com.hedera.services.state.jasperdb.collections.MemoryIndexDiskKeyValueStore;
import com.hedera.services.state.jasperdb.collections.OffHeapHashList;
import com.hedera.services.state.jasperdb.collections.OffHeapLongList;
import com.hedera.services.state.jasperdb.files.DataFileCollection.LoadedDataCallback;
import com.hedera.services.state.jasperdb.files.DataFileReader;
import com.swirlds.common.crypto.DigestType;
import com.swirlds.common.crypto.Hash;
import com.swirlds.virtualmap.VirtualKey;
import com.swirlds.virtualmap.VirtualLongKey;
import com.swirlds.virtualmap.VirtualValue;
import com.swirlds.virtualmap.datasource.VirtualDataSource;
import com.swirlds.virtualmap.datasource.VirtualInternalRecord;
import com.swirlds.virtualmap.datasource.VirtualLeafRecord;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.IntStream;

import static com.hedera.services.state.jasperdb.files.DataFileCommon.newestFilesSmallerThan;
import static java.nio.ByteBuffer.allocateDirect;

/**
 * IMPORTANT: This implementation assumes a single writing thread. There can be multiple readers while writing is happening.
 *
 * @param <K> type for keys
 * @param <V> type for values
 */
@SuppressWarnings("jol")
public class VFCDataSourceJasperDB<K extends VirtualKey, V extends VirtualValue> implements VirtualDataSource<K, V> {
    private final static int HASH_SIZE = Integer.BYTES+ DigestType.SHA_384.digestLength(); // TODO remove please for something better
    /** The size in bytes for serialized key objects */
    private final int keySizeBytes;
    /** The size in bytes of serialized value objects */
    private final int valueSizeBytes;
    /** Constructor for creating new key objects during de-serialization */
    private final Supplier<K> keyConstructor;
    /** Constructor for creating new value objects during de-serialization */
    private final Supplier<V> valueConstructor;
    /** We have an optimized mode when the keys can be represented by a single long */
    private final boolean isLongKeyMode;
    /**
     * In memory off-heap store for internal node hashes. This data is never stored on disk so on load from disk, this
     * will be empty. That should cause all internal node hashes to have to be computed on the first round which will be
     * expensive. TODO is it worth saving this to disk on close?
     */
    private final OffHeapHashList hashStore = new OffHeapHashList();
    /** In memory off-heap store for key to path map, this is used when isLongKeyMode=true and keys are longs */
    private final OffHeapLongList longKeyToPath;
    /** Mixed disk and off-heap memory store for key to path map, this is used if isLongKeyMode=false, and we have complex keys. */
    private final HalfDiskHashMap<K> objectKeyToPath;
    /** Mixed disk and off-heap memory store for path to leaf key, hash and value */
    private final MemoryIndexDiskKeyValueStore pathToKeyHashValue;
    /** Thread local reusable buffer for reading keys */
    private final ThreadLocal<ByteBuffer> leafKey;
    /** Thread local reusable buffer for reading key, hash and value sets */
    private final ThreadLocal<ByteBuffer> keyHashValue;
    /** ScheduledThreadPool for executing merges */
    private final ScheduledThreadPoolExecutor mergingExecutor;
    /** When was the last medium-sized merge, only touched from single merge thread. */
    private Instant lastMediumMerge = Instant.now();
    /** When was the last full merge, only touched from single merge thread. */
    private Instant lastFullMerge = Instant.now();

    /**
     * Create new VFCDataSourceImplV3 with merging enabled
     *
     * @param keySizeBytes the size of key when serialized in bytes
     * @param keyConstructor constructor for creating keys for deserialization
     * @param valueSizeBytes the size of value when serialized in bytes
     * @param valueConstructor constructor for creating values for deserialization
     * @param storageDir directory to store data files in
     * @param maxNumOfKeys the maximum number of unique keys. This is used for calculating in memory index sizes
     */
    public VFCDataSourceJasperDB(int keySizeBytes, Supplier<K> keyConstructor, int valueSizeBytes, Supplier<V> valueConstructor,
                                 Path storageDir, long maxNumOfKeys) throws IOException {
        this(keySizeBytes, keyConstructor, valueSizeBytes,valueConstructor,storageDir,maxNumOfKeys,true);
    }

    /**
     * Create new VFCDataSourceImplV3
     *
     * @param keySizeBytes the size of key when serialized in bytes
     * @param keyConstructor constructor for creating keys for deserialization
     * @param valueSizeBytes the size of value when serialized in bytes
     * @param valueConstructor constructor for creating values for deserialization
     * @param storageDir directory to store data files in
     * @param maxNumOfKeys the maximum number of unique keys. This is used for calculating in memory index sizes
     */
    public VFCDataSourceJasperDB(int keySizeBytes, Supplier<K> keyConstructor, int valueSizeBytes, Supplier<V> valueConstructor,
                                 Path storageDir, long maxNumOfKeys, boolean mergingEnabled) throws IOException {
        this.keySizeBytes = Integer.BYTES + keySizeBytes;
        this.keyConstructor = keyConstructor;
        this.valueSizeBytes = Integer.BYTES + valueSizeBytes;
        this.valueConstructor = valueConstructor;
        this.leafKey = ThreadLocal.withInitial(() -> allocateDirect(this.keySizeBytes));
        this.keyHashValue = ThreadLocal.withInitial(() -> allocateDirect(this.keySizeBytes+HASH_SIZE+this.valueSizeBytes));
        final LoadedDataCallback loadedDataCallback;
        if (keySizeBytes == Long.BYTES) {
            isLongKeyMode = true;
            longKeyToPath = new OffHeapLongList();
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
        final int keyHashValueSize = this.keySizeBytes + HASH_SIZE + this.valueSizeBytes;
        pathToKeyHashValue = new MemoryIndexDiskKeyValueStore(storageDir,"pathToKeyHashValue",keyHashValueSize,loadedDataCallback);
        // If merging is enabled then merge all data files every 30 seconds, TODO this is just a initial implementation
        System.out.println("mergingEnabled = " + mergingEnabled);
        if (mergingEnabled) {
            ThreadGroup mergingThreadGroup = new ThreadGroup("Merging Threads");
            mergingExecutor = new ScheduledThreadPoolExecutor(1, r -> new Thread(mergingThreadGroup,r));
            mergingExecutor.setMaximumPoolSize(1);
            mergingExecutor.scheduleWithFixedDelay(this::doMerge,1,5, TimeUnit.MINUTES);
        } else {
            mergingExecutor = null;
        }
    }

    /**
     * Start a Merge
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
            pathToKeyHashValue.mergeAll(filesToMergeFilter);
        } catch (Throwable t) {
            System.err.println("Exception while merging!");
            t.printStackTrace();
        }
    }

    //==================================================================================================================
    // Public NEW API methods

    public VirtualLeafRecord<K, V> loadLeafRecord(K key) throws IOException {
        if (key == null) throw new IllegalArgumentException("key can not be null");
        long path = isLongKeyMode ? longKeyToPath.get(((VirtualLongKey)key).getKeyAsLong(), INVALID_PATH) : objectKeyToPath.get(key, INVALID_PATH);
        if (path == INVALID_PATH) return null;
        return loadLeafRecord(path,key);
    }

    public VirtualLeafRecord<K, V> loadLeafRecord(long path) throws IOException {
        return loadLeafRecord(path,null);
    }

    /**
     * load a leaf record by path, using the provided key or if null deserializing the key.
     */
    private VirtualLeafRecord<K, V> loadLeafRecord(long path, K key) throws IOException {
        // get reusable buffer
        ByteBuffer keyHashValueBuffer = this.keyHashValue.get().clear();
        // read value
        boolean found = pathToKeyHashValue.get(path,keyHashValueBuffer);
        if (!found) return null;
        // deserialize
        keyHashValueBuffer.rewind();
        // deserialize key
        if (key != null) {
            keyHashValueBuffer.position(keySizeBytes); // jump key
        } else {
            final int keySerializationVersion = keyHashValueBuffer.getInt();
            key = keyConstructor.get();
            key.deserialize(keyHashValueBuffer, keySerializationVersion);
        }
        // deserialize hash
        final Hash hash = Hash.fromByteBuffer(keyHashValueBuffer);
        // deserialize value
        final int valueSerializationVersion = keyHashValueBuffer.getInt();
        final V value = valueConstructor.get();
        value.deserialize(keyHashValueBuffer,valueSerializationVersion);
        // return new VirtualLeafRecord
        return new VirtualLeafRecord<>(path, hash, key, value);
    }

    /**
     * Save a batch of data to data store.
     *
     * @param firstLeafPath the tree path for first leaf
     * @param lastLeafPath the tree path for last leaf
     * @param internalRecords list of records for internal nodes, it is assumed this is sorted by path and each path only appears once.
     * @param leafRecords list of records for leaf nodes, it is assumed this is sorted by key and each key only appears once.
     */
    public void saveRecords(long firstLeafPath, long lastLeafPath, List<VirtualInternalRecord> internalRecords,
                            List<VirtualLeafRecord<K, V>> leafRecords) throws IOException {
        // TODO work out how to delete things using firstLeafPath & lastLeafPath
        // might as well write to the 3 data stores in parallel
        IntStream.range(0,3).parallel().forEach(action -> {
            if (action == 0) {// write internal node hashes
                if (internalRecords != null && !internalRecords.isEmpty()) {
                    for (VirtualInternalRecord rec : internalRecords) {
                        hashStore.put(rec.getPath(), rec.getHash());
                    }
                }
            } else if (action == 1) { // write leaves to pathToKeyHashValue
                if (leafRecords != null && !leafRecords.isEmpty()) {
                    try {
                        long lastPath = Long.MIN_VALUE;  //, lastKey = Long.MIN_VALUE;
                        pathToKeyHashValue.startWriting();
                        // get reusable buffer
                        ByteBuffer keyHashValueBuffer = this.keyHashValue.get();
                        for (var rec : leafRecords) {
                            final long path = rec.getPath();
                            final VirtualKey key = rec.getKey();
                            final Hash hash = rec.getHash();
                            final VirtualValue value = rec.getValue();

//                            assert key > lastKey : "saveRecords keys are not sorted, got key "+key+" after key "+lastKey;
                            if(path < lastPath) System.err.println("saveRecords paths are not sorted, got path "+path+" after path "+lastPath);
                            lastPath = path;
//
//                            long keyLong = ((VirtualLongKey) rec.getKey()).getKeyAsLong();
//                            if(keyLong < lastKey) System.err.println("saveRecords paths are not sorted, got key "+keyLong+" after key "+lastKey);
//                            lastKey = keyLong;

                            // clear buffer for reuse
                            keyHashValueBuffer.clear();
                            // put key
                            keyHashValueBuffer.putInt(key.getVersion());
                            key.serialize(keyHashValueBuffer);
                            // put hash
                            Hash.toByteBuffer(hash, keyHashValueBuffer);
                            // put value
                            keyHashValueBuffer.putInt(value.getVersion());
                            value.serialize(keyHashValueBuffer);
                            // now save pathToKeyHashValue
                            keyHashValueBuffer.flip();
                            pathToKeyHashValue.put(path, keyHashValueBuffer);
                        }
                        pathToKeyHashValue.endWriting(firstLeafPath,lastLeafPath);
                    } catch (IOException e) {
                        throw new RuntimeException(e); // TODO maybe re-wrap into IOException?
                    }
                }
            } else if (action == 2) { // write leaves to objectKeyToPath
                if (leafRecords != null && !leafRecords.isEmpty()) {
                    if (isLongKeyMode) {
                        for (var rec : leafRecords) {
                            long key = ((VirtualLongKey) rec.getKey()).getKeyAsLong();
                            longKeyToPath.put(key, rec.getPath());
                        }
                    } else {
                        try {
                            objectKeyToPath.startWriting();
                            for (var rec : leafRecords) {
                                objectKeyToPath.put(rec.getKey(), rec.getPath());
                            }
                            objectKeyToPath.endWriting();
                        } catch (IOException e) {
                            throw new RuntimeException(e); // TODO maybe re-wrap into IOException?
                        }
                    }
                }
            }
        });
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
        ByteBuffer keyHashValueBuffer = this.keyHashValue.get().clear();
        // read value
        boolean found = pathToKeyHashValue.get(path,keyHashValueBuffer);
        if (!found) return null;
        // deserialize hash
        keyHashValueBuffer.rewind();
        keyHashValueBuffer.position(keySizeBytes); // jump key
        return Hash.fromByteBuffer(keyHashValueBuffer);
    }

    /**
     * Load hash for a internal node with given path
     *
     * @param path the path to get hash for
     * @return loaded hash or null if hash is not stored
     * @throws IOException if there was a problem loading hash
     */
    @Override
    public Hash loadInternalHash(long path) throws IOException {
        if (path < 0) throw new IllegalArgumentException("path is less than 0");
        return hashStore.get(path);
    }

    /**
     * Wait for any merges to finish and then close all data stores.
     */
    @Override
    public void close() throws IOException {
        try {
            mergingExecutor.shutdown();
            boolean finishedWithoutTimeout = mergingExecutor.awaitTermination(5,TimeUnit.MINUTES);
            if (!finishedWithoutTimeout)
                throw new IOException("Timeout while waiting for merge to finish.");
        } catch (InterruptedException e) {
            throw new IOException("Interrupted while waiting for merge to finish.",e);
        } finally {
            if (objectKeyToPath!= null) objectKeyToPath.close();
            pathToKeyHashValue.close();
        }
    }

    //==================================================================================================================
    // Legacy API methods

    /**
     * Load leaf's value
     *
     * @param path the path for leaf to get value for
     * @return loaded leaf value or null if none was saved
     * @throws IOException if there was a problem loading leaf data
     */
    @Override
    public V loadLeafValue(long path) throws IOException {
        if (path < 0) throw new IllegalArgumentException("path is less than 0");
        ByteBuffer keyHashValueBuffer = this.keyHashValue.get().clear();
        // read value
        boolean found = pathToKeyHashValue.get(path,keyHashValueBuffer);
        if (!found) return null;
        // deserialize value
        keyHashValueBuffer.rewind();
        keyHashValueBuffer.position(keySizeBytes+HASH_SIZE); // jump over key and hash, TODO add API to read at starting offset, to avoid this
        final int valueSerializationVersion = keyHashValueBuffer.getInt();
        V value = valueConstructor.get();
        value.deserialize(keyHashValueBuffer,valueSerializationVersion);
        return value;
    }

    /**
     * Load leaf's value
     *
     * @param key the key for leaf to get value for
     * @return loaded leaf value or null if none was saved
     * @throws IOException if there was a problem loading leaf data
     */
    @Override
    public V loadLeafValue(K key) throws IOException {
        if (key == null) throw new IllegalArgumentException("key can not be null");
        long path = isLongKeyMode ? longKeyToPath.get(((VirtualLongKey)key).getKeyAsLong(), INVALID_PATH) : objectKeyToPath.get(key, INVALID_PATH);
        if (path == INVALID_PATH) return null;
        return loadLeafValue(path);
    }

    /**
     * Load a leaf's key
     *
     * @param path the path to the leaf to load key for
     * @return the loaded key for leaf or null if none was saved
     * @throws IOException if there was a problem loading key
     */
    @Override
    public K loadLeafKey(long path) throws IOException {
        if (path < 0) throw new IllegalArgumentException("path is less than 0");
        ByteBuffer keyBuffer = this.leafKey.get().clear();
        // read key
        boolean found = pathToKeyHashValue.get(path,keyBuffer);
        if (!found) return null;
        keyBuffer.rewind();
        final int keySerializationVersion =  keyBuffer.getInt();
        K key = keyConstructor.get();
        key.deserialize(keyBuffer,keySerializationVersion);
        return key;
    }

    /**
     * Load path for a leaf
     *
     * @param key the key for the leaf to get path for
     * @return loaded path or null if none is stored for key
     * @throws IOException if there was a problem loading leaf's path
     */
    @Override
    public long loadLeafPath(K key) throws IOException {
        return isLongKeyMode ? longKeyToPath.get(((VirtualLongKey)key).getKeyAsLong(), INVALID_PATH) : objectKeyToPath.get(key, INVALID_PATH);
    }

    /**
     * Save a hash for a internal node
     *
     * @param path the path of the node to save hash for, if nothing has been stored for this path before it will be created.
     * @param hash a non-null hash to write
     */
    @Override
    public void saveInternal(long path, Hash hash) {
        if (path < 0) throw new IllegalArgumentException("path is less than 0");
        if (hash == null)  throw new IllegalArgumentException("Hash is null");
        // write hash
        hashStore.put(path,hash);
    }

    /**
     * Update a leaf moving it from one path to another. Note! any existing node at the newPath will be overridden.
     *
     * @param oldPath Must be an existing valid path
     * @param newPath Can be larger than current max path, allowing tree to grow
     * @param key The key for the leaf so we can update key->path index
     * @throws IOException if there was a problem saving leaf update
     */
    @Override
    public void updateLeaf(long oldPath, long newPath, K key, Hash hash) throws IOException {
        addLeaf(newPath,key,loadLeafValue(oldPath),hash);
    }

    /**
     * Update a leaf at given path, the leaf must exist. Writes hash and value.
     *
     * @param path valid path to saved leaf
     * @param key the leaf's key
     * @param value the value for new leaf, can be null
     * @param hash non-null hash for the leaf
     * @throws IOException if there was a problem saving leaf update
     */
    @Override
    public void updateLeaf(long path, K key, V value, Hash hash) throws IOException {
        addLeaf(path, key,value, hash);
    }

    /**
     * Add a new leaf to store
     *
     * @param path the path for the new leaf
     * @param key the non-null key for the new leaf
     * @param value the value for new leaf, can be null
     * @param hash the non-null hash for new leaf
     * @throws IOException if there was a problem writing leaf
     */
    @Override
    public void addLeaf(long path, K key, V value, Hash hash) throws IOException {
        if (path < 0) throw new IllegalArgumentException("path is less than 0");
        if (hash == null) throw new IllegalArgumentException("Can not save null hash for leaf at path ["+path+"]");
        if (key == null) throw new IllegalArgumentException("Can not save null key for leaf at path ["+path+"]");
        // fill buffer
        ByteBuffer keyHashValueBuffer = this.keyHashValue.get().clear();
        // put key
        keyHashValueBuffer.putInt(key.getVersion());
        key.serialize(keyHashValueBuffer);
        // put hash
        Hash.toByteBuffer(hash,keyHashValueBuffer);
        // put value
        keyHashValueBuffer.putInt(value.getVersion());
        value.serialize(keyHashValueBuffer);
        // now save pathToKeyHashValue
        keyHashValueBuffer.flip();
        pathToKeyHashValue.put(path,keyHashValueBuffer);
        // save key to path mapping
        if (isLongKeyMode) {
            longKeyToPath.put(((VirtualLongKey)key).getKeyAsLong(),path);
        } else {
            objectKeyToPath.put(key,path);
        }
    }

    //==================================================================================================================
    // Legacy API Transaction methods

    @Override
    public Object startTransaction() {
        try {
            pathToKeyHashValue.startWriting();
            if (!isLongKeyMode) objectKeyToPath.startWriting();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public void commitTransaction(Object handle) {
        try {
            pathToKeyHashValue.endWriting(0, Integer.MAX_VALUE);
            if (!isLongKeyMode) objectKeyToPath.endWriting();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
