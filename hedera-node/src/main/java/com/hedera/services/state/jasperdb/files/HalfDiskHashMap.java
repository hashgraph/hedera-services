package com.hedera.services.state.jasperdb.files;

import com.hedera.services.state.jasperdb.collections.LongList;
import com.hedera.services.state.jasperdb.collections.LongListHeap;
import com.hedera.services.state.jasperdb.files.DataFileCollection;
import com.hedera.services.state.jasperdb.files.DataFileCommon;
import com.hedera.services.state.jasperdb.files.DataFileReader;
import com.hedera.services.state.jasperdb.files.DataFileReaderAsynchronous;
import com.swirlds.virtualmap.VirtualKey;
import org.eclipse.collections.impl.list.mutable.primitive.LongArrayList;
import org.eclipse.collections.impl.map.mutable.primitive.IntObjectHashMap;
import org.eclipse.collections.impl.map.mutable.primitive.ObjectLongHashMap;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

import static com.hedera.services.state.jasperdb.files.DataFileCommon.MB;

/**
 * This is a hash map implementation where the bucket index is in RAM and the buckets are on disk. It maps a VKey to a
 * long value. This allows very large maps with minimal RAM usage and the best performance profile as by using an in
 * memory index we avoid the need for random disk writes. Random disk writes are horrible performance wise in our
 * testing.
 *
 * This implementation depends on good hashCode() implementation on the keys, if there are too many hash collisions the
 * performance can get bad.
 *
 * IMPORTANT: This implementation assumes a single writing thread. There can be multiple readers while writing is happening.
 */
public class HalfDiskHashMap<K extends VirtualKey> implements AutoCloseable {
    /**
     * Nominal value for a bucket location that doesn't exist. It is zero, so we don't need to fill empty index memory
     * with some other value.
     */
    private static final long NON_EXISTENT_BUCKET = 0;
    /** System page size, for now hard coded as it is nearly always 4k on linux */
    private static final int DISK_PAGE_SIZE_BYTES = 4096;
    /** The max size for each bucket on disk */
    private static final int BUCKET_SIZE = DISK_PAGE_SIZE_BYTES;
    /** The amount of data used for storing key hash code */
    private static final int KEY_HASHCODE_SIZE = Integer.BYTES;
    /** The amount of data used for storing value in bucket, our values are longs as this is a key to long map */
    private static final int VALUE_SIZE = Long.BYTES;
    /** The amount of data used for storing bucket index in bucket header */
    private static final int BUCKET_INDEX_SIZE = Integer.BYTES;
    /** The amount of data used for storing the entry count in bucket header */
    private static final int BUCKET_ENTRY_COUNT_SIZE = Integer.BYTES;
    /** The amount of data used for storing the entry count in bucket header */
    private static final int BUCKET_NEXT_BUCKET_POINTER_SIZE = Long.BYTES;
    /** The amount of data used for a header in each bucket */
    private static final int BUCKET_HEADER_SIZE = BUCKET_INDEX_SIZE + BUCKET_ENTRY_COUNT_SIZE + BUCKET_NEXT_BUCKET_POINTER_SIZE;
    /** how full should all available bins be if we are at the specified map size */
    public static final double LOADING_FACTOR = 0.5;
    /** Long list used for mapping bucketIndex(index into list) to disk location for latest copy of bucket */
    private final LongList bucketIndexToBucketLocation;
    /** DataFileCollection manages the files storing the buckets on disk */
    private final DataFileCollection fileCollection;
    /** This is the number of buckets needed to store mapSize entries if we ere only LOADING_FACTOR percent full */
    private final int minimumBuckets;
    /**
     * This is the next power of 2 bigger than minimumBuckets. It needs to be a power of two, so that we can optimize
     * and avoid the cost of doing a % to find the bucket index from hash code.
     */
    private final int numOfBuckets;
    /**
     * How many entries can be stored in each bucket. We keep the buckets to 4k, so they can be loaded in a single disk
     * page. The number of entries that can be packed in depends on the keySize.
     */
    private final int entriesPerBucket;
    /**
     * The requested max size for the map, this is the maximum number of key/values expected to be stored in this map.
     */
    private final long mapSize;
    /** The name to use for the files prefix on disk */
    private final String storeName;
    /** Temporary bucket buffers. */
    private final ThreadLocal<Bucket<K>> bucket;
    /** Store for session data during a writing transaction */
    private IntObjectHashMap<ObjectLongHashMap<K>> oneTransactionsData = null;

    /**
     * Construct a new HalfDiskHashMap
     *
     * @param mapSize The maximum map number of entries. This should be more than big enough to avoid too many key collisions.
     * @param keySize The key size in bytes when serialized to a ByteBuffer. Can be DataFileCommon.VARIABLE_DATA_SIZE
     * @param averageKeySizeEstimate If keySize is DataFileCommon.VARIABLE_DATA_SIZE then we need an estimate for the
     *                               average key size to use in sizing calculations.
     * @param maxKeySize The maximum size a key can be, needed for variable sized keys.
     * @param keyConstructor The constructor to use for creating new de-serialized keys
     * @param keySizeReader Reader for reading key sizes from byte buffers containing a serialized key. This can be null
     *                      if using fixed size keys.
     * @param storeKeySerializationVersion when true we use an int for each key to store serialization version. When false
     *                                     we assume the key's serialization is fixed or the version is tracked else where.
     * @param storeDir The directory to use for storing data files.
     * @param storeName The name for the data store, this allows more than one data store in a single directory.
     * @throws IOException If there was a problem creating or opening a set of data files.
     */
    public HalfDiskHashMap(long mapSize, int keySize, int averageKeySizeEstimate, int maxKeySize,
                           Supplier<K> keyConstructor,  KeySizeReader keySizeReader,
                           boolean storeKeySerializationVersion,
                           Path storeDir, String storeName) throws IOException {
        this.mapSize = mapSize;
        this.storeName = storeName;
        // create store dir
        Files.createDirectories(storeDir);
        // create bucket index
        bucketIndexToBucketLocation = new LongListHeap();
        // calculate number of entries we can store in a disk page
//        entryHeaderSize = KEY_HASHCODE_SIZE + KEY_SERIALIZATION_VERSION_SIZE + keySize;
//        entrySize = entryHeaderSize + VALUE_SIZE; // key hash code, key serialization version, serialized key, long value
        final int keySerializationVersionSize = storeKeySerializationVersion ? Integer.BYTES : 0;
        final int keySizeForCalculations = keySize == DataFileCommon.VARIABLE_DATA_SIZE ? averageKeySizeEstimate: keySize;
        final int entrySize = KEY_HASHCODE_SIZE + keySerializationVersionSize + keySizeForCalculations + VALUE_SIZE;

                entriesPerBucket = ((BUCKET_SIZE -BUCKET_HEADER_SIZE) / entrySize);
        minimumBuckets = (int)Math.ceil(((double)mapSize/LOADING_FACTOR)/entriesPerBucket);
        numOfBuckets = Math.max(4096,Integer.highestOneBit(minimumBuckets)*2); // nearest greater power of two with a min of 4096
        bucket = ThreadLocal.withInitial(() -> new Bucket<>(KEY_HASHCODE_SIZE,
                keySerializationVersionSize,
                keySize, maxKeySize, entriesPerBucket, keyConstructor, keySizeReader));
        // create file collection
        fileCollection = new DataFileCollection(storeDir,storeName, BUCKET_SIZE,
                (key, dataLocation, dataValue) -> bucketIndexToBucketLocation.put(key,dataLocation));
    }

    /**
     * Merge all read only files
     *
     * @param filterForFilesToMerge filter to choose which subset of files to merge
     * @throws IOException if there was a problem merging
     */
    public void mergeAll(Function<List<DataFileReader>,List<DataFileReader>> filterForFilesToMerge) throws IOException {
        final long START = System.currentTimeMillis();
        final List<DataFileReader> allFilesBefore = fileCollection.getAllFullyWrittenFiles();
        final List<DataFileReader> filesToMerge = filterForFilesToMerge.apply(allFilesBefore);
        final int size = filesToMerge == null ? 0 : filesToMerge.size();
        if (size < 2) {
            System.out.println("["+storeName+"] No meed to merge as only "+size+" files.");
            return;
        }
        double filesToMergeSizeMb = filesToMerge.stream().mapToDouble(file -> {
            try {
                return file.getSize();
            } catch (IOException e) {
                e.printStackTrace();
                return 0;
            }
        }).sum() / MB;
        System.out.printf("[%s] Starting merging %,d files total %,.2f Gb...\n",storeName,size,filesToMergeSizeMb/1024);
        final List<Path> newFilesCreated = fileCollection.mergeFiles(
                // update index with all moved data
                moves -> moves.forEach(bucketIndexToBucketLocation::putIfEqual),
                filesToMerge);


        final double tookSeconds = (double) (System.currentTimeMillis() - START) / 1000d;

        double mergedFilesCreatedSizeMb = newFilesCreated.stream().mapToDouble(path -> {
            try {
                return Files.size(path);
            } catch (IOException e) {
                e.printStackTrace();
                return 0;
            }
        }).sum() / MB;

        System.out.printf("[%s] Merged %,.2f Gb files into %,.2f Gb files in %,.2f seconds. Read at %,.2f Mb/sec Written at %,.2f\n        filesToMerge = %s\n       allFilesBefore = %s\n       allFilesAfter = %s\n",
                storeName,
                filesToMergeSizeMb / 1024d,
                mergedFilesCreatedSizeMb / 1024d,
                tookSeconds,
                filesToMergeSizeMb / tookSeconds,
                mergedFilesCreatedSizeMb / tookSeconds,
                Arrays.toString(filesToMerge.stream().map(reader -> reader.getMetadata().getIndex()).toArray()),
                Arrays.toString(allFilesBefore.stream().map(reader -> reader.getMetadata().getIndex()).toArray()),
                Arrays.toString(fileCollection.getAllFullyWrittenFiles().stream().map(reader -> reader.getMetadata().getIndex()).toArray())
        );
    }

    /**
     * Close this HalfDiskHashMap's data files. Once closed this HalfDiskHashMap can not be reused. You should make
     * sure you call close before system exit otherwise any files being written might not be in a good state.
     *
     * @throws IOException If there was a problem closing the data files.
     */
    @Override
    public void close() throws IOException {
        fileCollection.close();
    }

    // =================================================================================================================
    // Writing API - Single thead safe
    private Thread writingThread;

    /**
     * Start a writing session to the map. Each new writing session results in a new data file on disk, so you should
     * ideally batch up map writes.
     */
    public void startWriting() {
        oneTransactionsData = new IntObjectHashMap<>();
        writingThread = Thread.currentThread();
    }

    /**
     * Put a key/value during the current writing session. The value will not be retrievable until it is committed in
     * the endWriting() call.
     *
     * @param key the key to store the value for
     * @param value the value to store for given key
     */
    public void put(K key, long value) {
        if (key == null) throw new IllegalArgumentException("Can not put a null key");
        if (oneTransactionsData == null) throw new IllegalStateException("Trying to write to a HalfDiskHashMap when you have not called startWriting().");
        if (Thread.currentThread() != writingThread) throw new IllegalStateException("Tried calling write with different thread to startWriting()");
        // store key and value in transaction cache
        int bucketIndex = computeBucketIndex(key.hashCode());
        ObjectLongHashMap<K> bucketMap = oneTransactionsData.getIfAbsentPut(bucketIndex, ObjectLongHashMap::new);
        bucketMap.put(key,value);
    }

    /**
     * End current writing session, committing all puts to data store.
     *
     * @throws IOException If there was a problem committing data to store
     */
    public void endWriting() throws IOException {
        // TODO JASPER, need to filter bucket for lim/max leaf path when we write them
        if (Thread.currentThread() != writingThread) throw new IllegalStateException("Tried calling endWriting with different thread to startWriting()");
        writingThread = null;
        System.out.printf("Finishing writing to %s, num of changed bins = %,d, num of changed keys = %,d \n",
                storeName,
                oneTransactionsData.size(),
                oneTransactionsData.stream().mapToLong(ObjectLongHashMap::size).sum()
        );
        // iterate over transaction cache and save it all to file
        if (oneTransactionsData != null && !oneTransactionsData.isEmpty()) {
            //  write to files
            fileCollection.startWriting();
            // for each changed bucket, write the new buckets to file but do not update index yet
            final LongArrayList indexChanges = new LongArrayList();
            final Bucket<K> bucket = this.bucket.get();
            try {
                oneTransactionsData.forEachKeyValue((bucketIndex, bucketMap) -> {
                    try {
                        long currentBucketLocation = bucketIndexToBucketLocation.get(bucketIndex, NON_EXISTENT_BUCKET);
                        bucket.clear();
                        if (currentBucketLocation == NON_EXISTENT_BUCKET) {
                            // create a new bucket
                            bucket.setBucketIndex(bucketIndex);
                        } else {
                            // load bucket
                            fileCollection.readData(currentBucketLocation, bucket.bucketBuffer, DataFileReaderAsynchronous.DataToRead.VALUE);
                        }
                        // for each changed key in bucket, update bucket
                        bucketMap.forEachKeyValue((k,v) -> bucket.putValue(k.hashCode(),k,v));
                        // save bucket
                        long bucketLocation;
                        bucket.bucketBuffer.rewind();
                        bucketLocation = fileCollection.storeData(bucketIndex, bucket.bucketBuffer);
                        // stash update bucketIndexToBucketLocation
                        indexChanges.add(bucketIndex);
                        indexChanges.add(bucketLocation);
                    } catch (IllegalStateException e) { // wrap IOExceptions
                        printStats();
                        debugDumpTransactionCacheCondensed();
                        debugDumpTransactionCache();
                        throw e;
                    } catch (IOException e) { // wrap IOExceptions
                        throw new RuntimeException(e);
                    }
                });
            } catch (RuntimeException e) { // unwrap IOExceptions
                if (e.getCause() instanceof IOException) throw (IOException) e.getCause();
                throw e;
            }
            // close files session
            fileCollection.endWriting(0,numOfBuckets);
            // for each changed bucket update index
            try {
                for (int i = 0; i < indexChanges.size(); i+=2) {
                    final long bucketIndex = indexChanges.get(i);
                    final long bucketLocation = indexChanges.get(i+1);
                    // update bucketIndexToBucketLocation
                    bucketIndexToBucketLocation.put(bucketIndex, bucketLocation);
                }
            } catch (RuntimeException e) { // unwrap IOExceptions
                if (e.getCause() instanceof IOException) throw (IOException) e.getCause();
                throw e;
            }
        }
        // clear put cache
        oneTransactionsData = null;
    }

    // =================================================================================================================
    // Reading API - Multi thead safe

    /**
     * Get a value from this map
     *
     * @param key The key to get value for
     * @param notFoundValue the value to return if the key was not found
     * @return the value retrieved from the map or {notFoundValue} if no value was stored for the given key
     * @throws IOException If there was a problem reading from the map
     */
    public long get(K key, long notFoundValue) throws IOException {
        if (key == null) throw new IllegalArgumentException("Can not get a null key");
        int keyHash = key.hashCode();
        int bucketIndex = computeBucketIndex(keyHash);
        long currentBucketLocation = bucketIndexToBucketLocation.get(bucketIndex, NON_EXISTENT_BUCKET);
        if (currentBucketLocation != NON_EXISTENT_BUCKET) {
            Bucket<K> bucket = this.bucket.get().clear();
            // load bucket
            fileCollection.readData(currentBucketLocation,bucket.bucketBuffer, DataFileReaderAsynchronous.DataToRead.VALUE);
            // get
            return bucket.findValue(keyHash,key,notFoundValue);
        }
        return notFoundValue;
    }

    // =================================================================================================================
    // Debugging Print API

    /** Debug dump stats for this map */
    public void printStats() {
        System.out.printf(
                "HalfDiskHashMap Stats {\n"+
                "    mapSize = %,d\n"+
                "    minimumBuckets = %,d\n"+
                "    numOfBuckets = %,d\n"+
                "    entriesPerBucket = %,d\n"+
                "}\n"
                , mapSize,minimumBuckets,numOfBuckets,entriesPerBucket);
    }

    /** Useful debug method to print the current state of the transaction cache */
    public void debugDumpTransactionCacheCondensed() {
        System.out.println("=========== TRANSACTION CACHE ==========================");
        for (int bucketIndex = 0; bucketIndex < numOfBuckets; bucketIndex++) {
            ObjectLongHashMap<K> bucketMap = oneTransactionsData.get(bucketIndex);
            if (bucketMap != null) {
                String tooBig = (bucketMap.size() > entriesPerBucket) ? " TOO MANY! > "+entriesPerBucket : "";
                System.out.println("bucketIndex ["+bucketIndex+"] , count="+bucketMap.size()+tooBig);
            } else {
                System.out.println("bucketIndex ["+bucketIndex+"] , EMPTY!");
            }
        }
        System.out.println("========================================================");
    }

    /** Useful debug method to print the current state of the transaction cache */
    public void debugDumpTransactionCache() {
        System.out.println("=========== TRANSACTION CACHE ==========================");
        for (int bucketIndex = 0; bucketIndex < numOfBuckets; bucketIndex++) {
            ObjectLongHashMap<K> bucketMap = oneTransactionsData.get(bucketIndex);
            if (bucketMap != null) {
                String tooBig = (bucketMap.size() > entriesPerBucket) ? " TOO MANY! > "+entriesPerBucket : "";
                System.out.println("bucketIndex ["+bucketIndex+"] , count="+bucketMap.size()+tooBig);
                bucketMap.forEachKeyValue((k, l) ->
                        System.out.println("        keyHash ["+k.hashCode()+"] bucket ["+computeBucketIndex(k.hashCode())+
                                "]  key ["+k+"] value ["+l+"]"));
//            } else {
//                System.out.println("bucketIndex ["+bucketIndex+"] , EMPTY!");
            }
        }
        System.out.println("========================================================");
    }

    // =================================================================================================================
    // Interface for key size readers

    @FunctionalInterface
    public interface KeySizeReader {
        /**
         * Read the size of the key from byte buffer at current position. Assumes current position is at beginning of
         * serialized key. The buffer's position should be restored afterwards, so that key could be deserialized from
         * the byte buffer if needed.
         *
         * @param buffer The buffer to read key's size from
         * @return the key size in bytes
         */
        int getKeySize(ByteBuffer buffer);
    }

    // =================================================================================================================
    // Private API

    /**
     * Computes which bucket a key with the given hash falls. Depends on the fact the numOfBuckets is a power of two.
     * Based on same calculation that is used in java HashMap.
     *
     * @param keyHash the int hash for key
     * @return the index of the bucket that key falls in
     */
    private int computeBucketIndex(int keyHash) {
        return (numOfBuckets-1) & keyHash;
    }

    /**
     * Class for accessing the data in a bucket. This is designed to be used from a single thread.
     *
     * Each bucket has a header containing:
     *  - int - Bucket index in map hash index
     *  - int - Count of keys stored
     *  - long - pointer to next bucket if this one is full. TODO implement this
     *  - Entry[] - array of entries
     *
     * Each Entry contains:
     *  - entryHashCodeSize(int/long) - key hash code
     *  - value - the value of the key/value pair. It is here because it is fixed size
     *  - optional int - key serialization version
     *  - key data - can be fixed size of entryKeySize or variable size
     */
    private static final class Bucket<K extends VirtualKey> {
        private static final int BUCKET_INDEX_IN_HASH_MAP_SIZE = Integer.BYTES;
        private static final int BUCKET_ENTRY_COUNT_SIZE = Integer.BYTES;
        private static final int BUCKET_ENTRY_COUNT_OFFSET = BUCKET_INDEX_IN_HASH_MAP_SIZE;
        private static final int NEXT_BUCKET_OFFSET = BUCKET_ENTRY_COUNT_OFFSET + BUCKET_ENTRY_COUNT_SIZE;
        private final ByteBuffer bucketBuffer = ByteBuffer.allocate(BUCKET_SIZE);
        private final int entryHashCodeSize;
        private final int entryKeyVersionSize;
        private final int entryKeySize;
        private final int maxKeySize;
        private final int entryValueOffset;
        private final int maxEntrySize;
        private final boolean hasFixedSizeKey;
        private final boolean storeKeySerializationVersion;
        private final int entriesPerBucket;
        private final Supplier<K> keyConstructor;
        private final KeySizeReader keySizeReader;

        /**
         * Create a new bucket
         *
         * @param entryHashCodeSize the size in bytes of key hashCode
         * @param entryKeyVersionSize key serialization version size in bytes
         * @param entryKeySize this size of serialized key inm bytes, can be DiskFileCommon.VARIABLE_DATA_SIZE
         * @param maxKeySize The maximum size a key can be, needed for variable sized keys.
         * @param entriesPerBucket the number of entries we can store in a bucket
         * @param keyConstructor constructor for creating new keys during deserialization
         */
        private Bucket(int entryHashCodeSize, int entryKeyVersionSize, int entryKeySize, int maxKeySize,
                       int entriesPerBucket, Supplier<K> keyConstructor, KeySizeReader keySizeReader) {
            this.entryHashCodeSize = entryHashCodeSize;
            this.entryValueOffset = this.entryHashCodeSize;
            this.entryKeyVersionSize = entryKeyVersionSize;
            this.entryKeySize = entryKeySize;
            this.maxKeySize = maxKeySize;
            this.hasFixedSizeKey = entryKeySize != DataFileCommon.VARIABLE_DATA_SIZE;
            this.storeKeySerializationVersion = entryKeyVersionSize > 0;
            this.entriesPerBucket = entriesPerBucket;
            this.keyConstructor = keyConstructor;
            this.keySizeReader = keySizeReader;
            this.maxEntrySize = entryHashCodeSize + VALUE_SIZE + entryKeyVersionSize + maxKeySize;
        }

        /**
         * Reset for next use
         *
         * @return this bucket for each chaining
         */
        public Bucket<K> clear() {
            setBucketIndex(-1);
            setBucketEntryCount(0);
            bucketBuffer.clear();
            return this;
        }

        /**
         * Get the index for this bucket
         */
        public int getBucketIndex() {
            return bucketBuffer.getInt(0);
        }

        /**
         * Set the index for this bucket
         */
        public void setBucketIndex(int bucketIndex) {
            this.bucketBuffer.putInt(0,bucketIndex);
        }

        /**
         * Get the number of entries stored in this bucket
         */
        public int getBucketEntryCount() {
            return bucketBuffer.getInt(BUCKET_ENTRY_COUNT_OFFSET);
        }

        /**
         * Set the number of entries stored in this bucket
         */
        private void setBucketEntryCount(int entryCount) {
            this.bucketBuffer.putInt(BUCKET_ENTRY_COUNT_OFFSET,entryCount);
        }

        /**
         * Add one to the number of entries stored in this bucket
         */
        private void incrementBucketEntryCount() {
            this.bucketBuffer.putInt(BUCKET_ENTRY_COUNT_OFFSET,bucketBuffer.getInt(BUCKET_ENTRY_COUNT_OFFSET)+1);
        }

        /**
         * Get the location of next bucket, when buckets overflow
         */
        public long getNextBucketLocation() {
            return bucketBuffer.getLong(NEXT_BUCKET_OFFSET);
        }

        /**
         * Set the location of next bucket, when buckets overflow
         *
         * TODO still needs to be implemented over flow into a second bucket
         */
        public void setNextBucketLocation(long bucketLocation) {
            this.bucketBuffer.putLong(NEXT_BUCKET_OFFSET,bucketLocation);
        }

        /**
         * Find a value for given key
         *
         * @param keyHashCode the int hash for the key
         * @param key the key object
         * @param notFoundValue the long to return if the key is not found
         * @return the stored value for given key or notFoundValue if nothing is stored for the key
         * @throws IOException If there was a problem reading the value from file
         */
        public long findValue(int keyHashCode, K key, long notFoundValue) throws IOException {
            final var found = findEntryOffset(keyHashCode,key);
            if (found.found) {
                // yay! we found it
                return getValue(found.entryOffset);
            } else {
                return notFoundValue;
            }
        }

        /**
         * Put a key/value entry into this bucket.
         *
         * @param key the entry key
         * @param value the entry value
         */
        public void putValue(int keyHashCode, K key, long value) {
            try {
                // scan over all existing key/value entries and see if there is already one for this key. If there is
                // then update it, otherwise we have at least worked out the entryOffset for the end of existing entries
                // and can use that for appending a new entry if there is room
                final var result = findEntryOffset(keyHashCode,key);
                if (result.found) {
                    // yay! we found it, so update value
                    setValue(result.entryOffset, value);
                    return;
                }
                // so there are no entries that match the key. Check if there is enough space for another entry in this bucket
                final int currentEntryCount = getBucketEntryCount();
                // TODO if we serialized the key to a temp ByteBuffer then we could do a check to see if there is enough
                //  space for this specific key rather than using max size. This might allow a extra one or two entries
                //  in a bucket at the cost of serializing to a temp buffer and writing that buffer into the bucket.
                if ((result.entryOffset + maxEntrySize) < BUCKET_SIZE) {
                        // add a new entry
                        bucketBuffer.position(result.entryOffset);
                        bucketBuffer.putInt(keyHashCode);
                        bucketBuffer.putLong(value);
                        if (entryKeyVersionSize > 0) bucketBuffer.putInt(key.getVersion());
                        key.serialize(bucketBuffer);
                        // increment count
                        incrementBucketEntryCount();
                } else {
                    // bucket is full
                    // TODO expand into another bucket
                    throw new IllegalStateException("Bucket is full, could not store key: "+key);
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        /**
         * Find the offset in bucket for an entry matching the given key, if not found then just return the offset for
         * the end of all entries.
         *
         * @param keyHashCode hash code for the key to search for
         * @param key the key to search for
         * @return either true and offset for found key entry or false and offset for end of all entries in this bucket
         * @throws IOException If there was a problem reading bucket
         */
        private FindResult findEntryOffset(int keyHashCode, K key) throws IOException {
            int entryCount =  getBucketEntryCount();
            int entryOffset = BUCKET_HEADER_SIZE;
            for (int i = 0; i < entryCount; i++) {
                int readHashCode = bucketBuffer.getInt(entryOffset);
                if (readHashCode == keyHashCode) {
                    // now check the full key
                    bucketBuffer.position(entryOffset+entryHashCodeSize+VALUE_SIZE);
                    final int keySerializationVersion = storeKeySerializationVersion ? bucketBuffer.getInt() : 1;
                    if (key.equals(bucketBuffer,keySerializationVersion)) {
                        // yay! we found it
                        return new FindResult(entryOffset,true);
                    }
                }
                // now read the key size so we can jump
                // TODO ideally we would only read this once and use in key.equals and here
                // TODO also could avoid doing this for last entry
                int keySize = getKeySize(entryOffset);
                // move to next entry
                entryOffset += entryHashCodeSize + VALUE_SIZE + entryKeyVersionSize + keySize;
            }
            return new FindResult(entryOffset, false);
        }

        /**
         * Read the size of the key for a entry
         *
         * @param entryOffset the offset to start of entry
         * @return the size of the key in bytes
         */
        private int getKeySize(int entryOffset) {
            if (hasFixedSizeKey) return entryKeySize;
            bucketBuffer.position(entryOffset+entryHashCodeSize+VALUE_SIZE+entryKeyVersionSize);
            return keySizeReader.getKeySize(bucketBuffer);
        }

        /**
         * Read a key for a given entry
         *
         * @param entryOffset the offset for the entry
         * @return The key deserialized from bucket
         * @throws IOException If there was a problem reading or deserializing the key
         */
        private K getKey(int entryOffset) throws IOException {
            bucketBuffer.position(entryOffset+Integer.BYTES);
            int keySerializationVersion = bucketBuffer.getInt();
            K key = keyConstructor.get();
            key.deserialize(bucketBuffer,keySerializationVersion);
            return key;
        }

        /**
         * Read a value for a given entry
         *
         * @param entryOffset the offset for the entry
         * @return the value stored in given entry
         */
        private long getValue(int entryOffset) {
            return bucketBuffer.getLong(entryOffset + entryValueOffset);
        }

        /**
         * Read a value for a given entry
         *
         * @param entryOffset the offset for the entry
         * @param value the value to set for entry
         */
        private void setValue(int entryOffset, long value) {
            bucketBuffer.putLong(entryOffset + entryValueOffset, value);
        }

        /** toString for debugging */
        @SuppressWarnings("StringConcatenationInsideStringBufferAppend")
        @Override
        public String toString() {
            final int entryCount =  getBucketEntryCount();
            StringBuilder sb = new StringBuilder(
                "Bucket{bucketIndex="+getBucketIndex()+", entryCount="+entryCount+", nextBucketLocation="+getNextBucketLocation()+"\n"
            );
            try {
                int entryOffset = BUCKET_HEADER_SIZE;
                for (int i = 0; i < entryCount; i++) {
                    int keySize = getKeySize(entryOffset);
                    bucketBuffer.position(entryOffset);
                    int readHash = bucketBuffer.getInt();
                    long value = bucketBuffer.getLong();
                    int keySerializationVersion = storeKeySerializationVersion ? bucketBuffer.getInt() : 1;
                    K key = keyConstructor.get();
                    key.deserialize(bucketBuffer,keySerializationVersion);
                    sb.append("    ENTRY[" + i + "] value= "+value+" keyHashCode=" + readHash + " keyVer=" + keySerializationVersion +
                            " key=" + key+" keySize="+keySize+"\n");
                    entryOffset += entryHashCodeSize + VALUE_SIZE + entryKeyVersionSize + keySize;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            bucketBuffer.clear();
            sb.append("} RAW DATA = ");
            for(byte b: bucketBuffer.array()) {
                sb.append(String.format("%02X ", b).toUpperCase());
            }
            return sb.toString();
        }
    }

    private static class FindResult {
        public final int entryOffset;
        public final boolean found;

        public FindResult(int entryOffset, boolean found) {
            this.entryOffset = entryOffset;
            this.found = found;
        }
    }
}
