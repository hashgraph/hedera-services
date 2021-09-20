package com.hedera.services.state.jasperdb.files.hashmap;

import com.hedera.services.state.jasperdb.collections.LongList;
import com.hedera.services.state.jasperdb.collections.LongListHeap;
import com.hedera.services.state.jasperdb.files.DataFileCollection;
import com.hedera.services.state.jasperdb.files.DataFileReader;
import com.hedera.services.state.jasperdb.files.DataItemSerializer;
import com.swirlds.virtualmap.VirtualKey;
import org.eclipse.collections.impl.list.mutable.primitive.LongArrayList;
import org.eclipse.collections.impl.map.mutable.primitive.IntObjectHashMap;
import org.eclipse.collections.impl.map.mutable.primitive.ObjectLongHashMap;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

import static com.hedera.services.state.jasperdb.files.DataFileCommon.MB;

/**
 * This is a hash map implementation where the bucket index is in RAM and the buckets are on disk. It maps a VirtualKey
 * to a long value. This allows very large maps with minimal RAM usage and the best performance profile as by using an
 * in memory index we avoid the need for random disk writes. Random disk writes are horrible performance wise in our
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
    protected static final int BUCKET_SIZE = DISK_PAGE_SIZE_BYTES;
    /** The amount of data used for storing key hash code */
    protected static final int KEY_HASHCODE_SIZE = Integer.BYTES;
    /** The amount of data used for storing value in bucket, our values are longs as this is a key to long map */
    protected static final int VALUE_SIZE = Long.BYTES;
    /** The amount of data used for storing bucket index in bucket header */
    private static final int BUCKET_INDEX_SIZE = Integer.BYTES;
    /** The amount of data used for storing the entry count in bucket header */
    private static final int BUCKET_ENTRY_COUNT_SIZE = Integer.BYTES;
    /** The amount of data used for storing the entry count in bucket header */
    private static final int BUCKET_NEXT_BUCKET_POINTER_SIZE = Long.BYTES;
    /** The amount of data used for a header in each bucket */
    protected static final int BUCKET_HEADER_SIZE = BUCKET_INDEX_SIZE + BUCKET_ENTRY_COUNT_SIZE + BUCKET_NEXT_BUCKET_POINTER_SIZE;
    /** how full should all available bins be if we are at the specified map size */
    public static final double LOADING_FACTOR = 0.5;
    /** Long list used for mapping bucketIndex(index into list) to disk location for latest copy of bucket */
    private final LongList bucketIndexToBucketLocation = new LongListHeap();
    /** DataFileCollection manages the files storing the buckets on disk */
    private final DataFileCollection<Bucket<K>> fileCollection;
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
    private final BucketSerializer<K> bucketSerializer;
    /** Store for session data during a writing transaction */
    private IntObjectHashMap<ObjectLongHashMap<K>> oneTransactionsData = null;
    /** The thread that called startWriting. We use it to check that other writing calls are done on same thread */
    private Thread writingThread;

    /**
     * Construct a new HalfDiskHashMap
     *
     * @param mapSize The maximum map number of entries. This should be more than big enough to avoid too many key collisions.
     * @param keySerializer Serializer for converting raw data to/from keys
     * @param storeDir The directory to use for storing data files.
     * @param storeName The name for the data store, this allows more than one data store in a single directory.
     * @throws IOException If there was a problem creating or opening a set of data files.
     */
    public HalfDiskHashMap(long mapSize, DataItemSerializer<K> keySerializer,
                           Path storeDir, String storeName) throws IOException {
        this.mapSize = mapSize;
        this.storeName = storeName;
        // create store dir
        Files.createDirectories(storeDir);
        // create bucket serializer
        this.bucketSerializer = new BucketSerializer<>(keySerializer);
        // calculate number of entries we can store in a disk page
        final int keySizeForCalculations = keySerializer.getTypicalSerializedSize();
        final int entrySize = KEY_HASHCODE_SIZE + keySizeForCalculations + VALUE_SIZE;
        entriesPerBucket = ((BUCKET_SIZE -BUCKET_HEADER_SIZE) / entrySize);
        minimumBuckets = (int)Math.ceil(((double)mapSize/LOADING_FACTOR)/entriesPerBucket);
        numOfBuckets = Math.max(4096,Integer.highestOneBit(minimumBuckets)*2); // nearest greater power of two with a min of 4096
        // create file collection
        fileCollection = new DataFileCollection<>(storeDir,storeName, bucketSerializer,
                (key, dataLocation, dataValue) -> bucketIndexToBucketLocation.put(key,dataLocation));
    }

    /**
     * Merge all read only files
     *
     * @param filterForFilesToMerge filter to choose which subset of files to merge
     * @throws IOException if there was a problem merging
     */
    public void mergeAll(Function<List<DataFileReader<Bucket<K>>>,List<DataFileReader<Bucket<K>>>> filterForFilesToMerge) throws IOException {
        final long START = System.currentTimeMillis();
        final List<DataFileReader<Bucket<K>>> allFilesBefore = fileCollection.getAllFullyWrittenFiles();
        final List<DataFileReader<Bucket<K>>> filesToMerge = filterForFilesToMerge.apply(allFilesBefore);
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
            try {
                oneTransactionsData.forEachKeyValue((bucketIndex, bucketMap) -> {
                    try {
                        long currentBucketLocation = bucketIndexToBucketLocation.get(bucketIndex, NON_EXISTENT_BUCKET);
                        final Bucket<K> bucket;
                        if (currentBucketLocation == NON_EXISTENT_BUCKET) {
                            // create a new bucket
                            bucket = bucketSerializer.getNewEmptyBucket();
                            bucket.setBucketIndex(bucketIndex);
                        } else {
                            // load bucket
                            bucket = fileCollection.readDataItem(currentBucketLocation);
                        }
                        // for each changed key in bucket, update bucket
                        bucketMap.forEachKeyValue((k,v) -> bucket.putValue(k.hashCode(),k,v));
                        // save bucket
                        final long bucketLocation = fileCollection.storeDataItem(bucket);
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
            // load bucket
            Bucket<K> bucket = fileCollection.readDataItem(currentBucketLocation);
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

}
