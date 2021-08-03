package com.hedera.services.state.merkle.v3.files;

import com.hedera.services.state.merkle.v3.offheap.OffHeapLongList;
import com.swirlds.virtualmap.VirtualKey;
import org.eclipse.collections.impl.map.mutable.primitive.IntObjectHashMap;
import org.eclipse.collections.impl.map.mutable.primitive.ObjectLongHashMap;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.function.Supplier;

/**
 * This is a hash map implementation where the bucket index is in RAM and the buckets are on disk. It maps a VKey to a
 * long value.
 *
 * IMPORTANT: This implementation assumes a single writing thread. There can be multiple readers while writing is happening.
 */
public class HalfDiskHashMap<K extends VirtualKey> {
    /**
     * Nominal value for a bucket location that doesn't exist. It is zero, so we don't need to fill empty index memory
     * with some other value.
     */
    private static final long NON_EXISTENT_BUCKET = 0;
    /** System page size, for now hard coded as it is nearly always 4k on linux */
    private static final int DISK_PAGE_SIZE_BYTES = 4096;
    /** The amount of data used for a header in each bucket */
    private static final int BUCKET_HEADER_SIZE = Integer.BYTES + Integer.BYTES + Long.BYTES;
    /** how full should all available bins be if we are at the specified map size */
    private static final double LOADING_FACTOR = 0.5;
    private final OffHeapLongList bucketIndexToBucketLocation;
    private final DataFileCollection fileCollection;
    private final int entrySize;
    private final int valueOffsetInEntry;
    private final int minimumBuckets;
    private final int numOfBuckets;
    private final int entriesPerBucket;
    private final long mapSize;
    /** Temporary bucket buffers. */
    private final ThreadLocal<Bucket<K>> bucket;
    private IntObjectHashMap<ObjectLongHashMap<K>> oneTransactionsData = null;

    public HalfDiskHashMap(long mapSize, int keySize, Supplier<K> keyConstructor, Path storeDir, String storeName) throws IOException {
        this.mapSize = mapSize;
        // create store dir
        Files.createDirectories(storeDir);
        // create file collection
        fileCollection = new DataFileCollection(storeDir,storeName,DISK_PAGE_SIZE_BYTES);
        // create bucket index
        bucketIndexToBucketLocation = new OffHeapLongList();
        // calculate number of entries we can store in a disk page
        valueOffsetInEntry = Integer.BYTES + Integer.BYTES + keySize;
        entrySize = valueOffsetInEntry + Long.BYTES; // key hash code, key serialization version, serialized key, long value
        entriesPerBucket = ((DISK_PAGE_SIZE_BYTES-BUCKET_HEADER_SIZE) / entrySize);
        minimumBuckets = (int)Math.ceil(((double)mapSize/LOADING_FACTOR)/entriesPerBucket);
        numOfBuckets = Integer.highestOneBit(minimumBuckets)*2; // nearest greater power of two
        bucket = ThreadLocal.withInitial(() -> new Bucket<>(entrySize, valueOffsetInEntry, entriesPerBucket, keyConstructor));
    }

    public void startWriting() throws IOException {
        oneTransactionsData = new IntObjectHashMap<>();
    }

    public void endWriting() throws IOException {
        // iterate over transaction cache and save it all to file
        if (oneTransactionsData != null && !oneTransactionsData.isEmpty()) {
            //  write to files
            fileCollection.startWriting();
            // for each changed bucket
            oneTransactionsData.forEachKeyValue((bucketIndex, bucketMap) -> {
                try {
                    long currentBucketLocation = bucketIndexToBucketLocation.get(bucketIndex, NON_EXISTENT_BUCKET);
                    Bucket<K> bucket = this.bucket.get().clear();
                    if (currentBucketLocation == NON_EXISTENT_BUCKET) {
                        // create a new bucket
                        bucket.setBucketIndex(bucketIndex);
                    } else {
                        // load bucket
                        fileCollection.readData(currentBucketLocation, bucket.bucketBuffer, DataFile.DataToRead.VALUE);
                    }
                    // for each changed key in bucket, update bucket
                    bucketMap.forEachKeyValue((k,v) -> bucket.putValue(hash(k),k,v));
                    // save bucket
                    long bucketLocation;
                    bucket.bucketBuffer.rewind();
                    bucketLocation = fileCollection.storeData(bucketIndex, bucket.bucketBuffer);
                    // update bucketIndexToBucketLocation
                    bucketIndexToBucketLocation.put(bucketIndex, bucketLocation);
                } catch (Exception e) {
                    System.err.println("bucketIndex="+bucketIndex);
                    throw new RuntimeException(e);
                }
            });
            // close files session
            fileCollection.endWriting(0,numOfBuckets);
        }
        // clear put cache
        oneTransactionsData = null;
    }

    public void put(K key, long value) throws IOException {
        if (key == null) throw new IllegalArgumentException("Can not put a null key");
        if (oneTransactionsData == null) throw new IllegalStateException("Trying to write to a HalfDiskHashMap when you have not called startWriting().");
        // store key and value in transaction cache
        int bucketIndex = computeBucketIndex(hash(key));
//        System.out.print(bucketIndex+",");
        ObjectLongHashMap<K> bucketMap = oneTransactionsData.getIfAbsentPut(bucketIndex, ObjectLongHashMap::new);
        bucketMap.put(key,value);
    }

    public long get(K key, long notFoundValue) throws IOException {
        if (key == null) throw new IllegalArgumentException("Can not get a null key");
        int keyHash = hash(key);
        int bucketIndex = computeBucketIndex(keyHash);
        long currentBucketLocation = bucketIndexToBucketLocation.get(bucketIndex, NON_EXISTENT_BUCKET);
        if (currentBucketLocation != NON_EXISTENT_BUCKET) {
            Bucket<K> bucket = this.bucket.get().clear();
            // load bucket
            fileCollection.readData(currentBucketLocation,bucket.bucketBuffer, DataFile.DataToRead.VALUE);
            // get
            return bucket.findValue(keyHash,key,notFoundValue);
        }
        return notFoundValue;
    }

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
     * From Java HashMap
     *
     * Computes key.hashCode() and spreads (XORs) higher bits of hash
     * to lower.  Because the table uses power-of-two masking, sets of
     * hashes that vary only in bits above the current mask will
     * always collide. (Among known examples are sets of Float keys
     * holding consecutive whole numbers in small tables.)  So we
     * apply a transform that spreads the impact of higher bits
     * downward. There is a tradeoff between speed, utility, and
     * quality of bit-spreading. Because many common sets of hashes
     * are already reasonably distributed (so don't benefit from
     * spreading), and because we use trees to handle large sets of
     * collisions in bins, we just XOR some shifted bits in the
     * cheapest possible way to reduce systematic lossage, as well as
     * to incorporate impact of the highest bits that would otherwise
     * never be used in index calculations because of table bounds.
     */
    private static int hash(Object key) {
        int h;
        return (key == null) ? 0 : (h = key.hashCode()) ^ (h >>> 16);
    }

    /** Debug dump stats for this map */
    public void printStats() {
        System.out.println("HalfDiskHashMap Stats {");
        System.out.println("    mapSize = " + mapSize);
        System.out.println("    minimumBuckets = " + minimumBuckets);
        System.out.println("    numOfBuckets = " + numOfBuckets);
        System.out.println("    entriesPerBucket = " + entriesPerBucket);
        System.out.println("    entrySize = " + entrySize);
        System.out.println("    valueOffsetInEntry = " + valueOffsetInEntry);
        System.out.println("}");
    }

    /** Useful debug method to print the current state of the transaction cache */
    @SuppressWarnings("unused")
    public void debugDumpTransactionCache() {
        System.out.println("=========== TRANSACTION CACHE ==========================");
        for (int bucketIndex = 0; bucketIndex < numOfBuckets; bucketIndex++) {
            ObjectLongHashMap<K> bucketMap = oneTransactionsData.get(bucketIndex);
            if (bucketMap != null) {
                String tooBig = (bucketMap.size() > entriesPerBucket) ? " TOO MANY! > "+entriesPerBucket : "";
                System.out.println("bucketIndex ["+bucketIndex+"] , count="+bucketMap.size()+tooBig);
//            bucketMap.forEachKeyValue((k, l) -> {
//                System.out.println("        keyHash ["+k.hashCode()+"] bucket ["+(k.hashCode()% numOfBuckets)+"]  key ["+k+"] value ["+l+"]");
//            });
            } else {
                System.out.println("bucketIndex ["+bucketIndex+"] , EMPTY!");
            }
        }
        System.out.println("========================================================");
    }

    /**
     * Class for accessing the data in a bucket. This is designed to be used from a single thread.
     *
     * Each bucket has a header containing:
     *  - Bucket index in map hash index
     *  - Count of keys stored
     *  - Long pointer to next bucket if this one is full. TODO implement this
     */
    @SuppressWarnings("StringConcatenationInsideStringBufferAppend")
    private static final class Bucket<K extends VirtualKey> {
        private static final int BUCKET_ENTRY_COUNT_OFFSET = Integer.BYTES;
        private static final int NEXT_BUCKET_OFFSET = BUCKET_ENTRY_COUNT_OFFSET + Integer.BYTES;
        private final ByteBuffer bucketBuffer = ByteBuffer.allocateDirect(DISK_PAGE_SIZE_BYTES);
        private final int entrySize;
        private final int valueOffsetInEntry;
        private final int entriesPerBucket;
        private final Supplier<K> keyConstructor;

        private Bucket(int entrySize, int valueOffsetInEntry, int entriesPerBucket, Supplier<K> keyConstructor) {
            this.entrySize = entrySize;
            this.valueOffsetInEntry = valueOffsetInEntry;
            this.entriesPerBucket = entriesPerBucket;
            this.keyConstructor = keyConstructor;
        }

        /** Reset for next use */
        public Bucket<K> clear() {
            setBucketIndex(-1);
            setBucketEntryCount(0);
            bucketBuffer.clear();
            return this;
        }

        public int getBucketIndex() {
            return bucketBuffer.getInt(0);
        }

        public void setBucketIndex(int bucketIndex) {
            this.bucketBuffer.putInt(0,bucketIndex);
        }

        public int getBucketEntryCount() {
            return bucketBuffer.getInt(BUCKET_ENTRY_COUNT_OFFSET);
        }

        public void setBucketEntryCount(int entryCount) {
            this.bucketBuffer.putInt(BUCKET_ENTRY_COUNT_OFFSET,entryCount);
        }

        public long getNextBucketLocation() {
            return bucketBuffer.getLong(NEXT_BUCKET_OFFSET);
        }

        @SuppressWarnings("unused") // TODO still needs to be implemented over flow into a second bucket
        public void setNextBucketLocation(long bucketLocation) {
            this.bucketBuffer.putLong(NEXT_BUCKET_OFFSET,bucketLocation);
        }

        public long findValue(int keyHash, K key, long notFoundValue) throws IOException {
            int entryCount =  getBucketEntryCount();
            for (int i = 0; i < entryCount; i++) {
                int entryOffset = BUCKET_HEADER_SIZE + (i*entrySize);
                int readHash = bucketBuffer.getInt(entryOffset);
//                System.out.println("            readHash = " + readHash+" keyHash = "+keyHash+" == "+(readHash == keyHash));
                if (readHash == keyHash) {
                    // now check the full key
                    bucketBuffer.position(entryOffset+Integer.BYTES);
                    int keySerializationVersion = bucketBuffer.getInt();
                    if (key.equals(bucketBuffer,keySerializationVersion)) {
                        // yay we found it
                        return getValue(entryOffset);
                    }
                }
            }
            return notFoundValue;
        }

        private K getKey(int entryOffset) throws IOException {
            bucketBuffer.position(entryOffset+Integer.BYTES);
            int keySerializationVersion = bucketBuffer.getInt();
            K key = keyConstructor.get();
            key.deserialize(bucketBuffer,keySerializationVersion);
            return key;
        }

        private long getValue(int entryOffset) {
            return bucketBuffer.getLong(entryOffset+valueOffsetInEntry);
        }

        /**
         * Put a key/value entry into this bucket.
         *
         * @param key the entry key
         * @param value the entry value
         */
        public void putValue(int keyHash, K key, long value) {
            try {
                final int entryCount =  getBucketEntryCount();
                for (int i = 0; i < entryCount; i++) {
                    int entryOffset = BUCKET_HEADER_SIZE + (i*entrySize);
                    int readHash = bucketBuffer.getInt(entryOffset);
                    if (readHash == keyHash) {
                        // now check the full key
                        bucketBuffer.position(entryOffset+Integer.BYTES);
                        int keySerializationVersion = bucketBuffer.getInt();
                        if (key.equals(bucketBuffer,keySerializationVersion)) {
                            // yay we found it, so update value
                            bucketBuffer.putLong(entryOffset+valueOffsetInEntry, value);
                            return;
                        }
                    }
                }
                // so there are no entries that match the key
                if (entryCount < entriesPerBucket) {
                        // add a new entry
                        int entryOffset = BUCKET_HEADER_SIZE + (entryCount*entrySize);
                        // write entry
                        bucketBuffer.position(entryOffset);
                        bucketBuffer.putInt(keyHash);
                        bucketBuffer.putInt(key.getVersion());
                        key.serialize(bucketBuffer);
                        bucketBuffer.putLong(value);
                        // increment count
                        setBucketEntryCount(entryCount+1);
                } else {
                    // bucket is full
                    // TODO expand into another bucket
                    throw new IllegalStateException("Bucket is full, could not store key: "+key);
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        /** toString for debugging */
        @Override
        public String toString() {
            final int entryCount =  getBucketEntryCount();
            StringBuilder sb = new StringBuilder(
                "Bucket{bucketIndex="+getBucketIndex()+", entryCount="+entryCount+", nextBucketLocation="+getNextBucketLocation()+"\n"
            );
            try {
                for (int i = 0; i < entryCount; i++) {
                    bucketBuffer.clear();
                    int entryOffset = BUCKET_HEADER_SIZE + (i * entrySize);
                    int readHash = bucketBuffer.getInt(entryOffset);
                    bucketBuffer.position(entryOffset + Integer.BYTES);
                    int keySerializationVersion = bucketBuffer.getInt();
                    bucketBuffer.limit(bucketBuffer.position() + valueOffsetInEntry); // hack happens to be same number as keySize + Long.BYTES.
                    sb.append("    ENTRY[" + i + "] value= "+getValue(entryOffset)+" keyHash=" + readHash + " ver=" + keySerializationVersion + " key=" + getKey(entryOffset)+" hashCode="+getKey(entryOffset).hashCode()+"\n");
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            bucketBuffer.clear();
            sb.append("} RAW DATA = "+toIntsString(bucketBuffer));
            return sb.toString();
        }

        public String toIntsString(ByteBuffer buf) {
            buf.rewind();
            IntBuffer intBuf = buf.asIntBuffer();
            int[] array = new int[intBuf.remaining()];
            intBuf.get(array);
            buf.rewind();
            return Arrays.toString(array);
        }
    }
}
