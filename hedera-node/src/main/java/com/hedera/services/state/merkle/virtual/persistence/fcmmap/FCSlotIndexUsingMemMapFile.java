package com.hedera.services.state.merkle.virtual.persistence.fcmmap;

import com.hedera.services.state.merkle.virtual.persistence.FCSlotIndex;
import com.swirlds.fcmap.VKey;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.LongSupplier;

/**
 *
 * TODO load and save version from file
 *
 * @param <K> Key type
 */
@SuppressWarnings("jol")
public final class FCSlotIndexUsingMemMapFile<K extends VKey> implements FCSlotIndex<K> {

    //==================================================================================================================
    // Config

    /** The path of the directory to store storage files */
    private final Path storageDirectory;

    private final String name;

    private final int numOfBins;
    private final int numOfFiles;
    private final int numOfBinsPerFile;
    private final int numOfKeysPerBin;
    private final int keySizeBytes;
    private final int maxNumberOfKeys;
    private final int maxNumberOfMutations;
    private final int shiftRightCountForFileSubKey;

    //==================================================================================================================
    // State

    /** Array of BinFiles we are using, only set once in constructor and not changed */
    private final BinFile<K>[] files;

    /** If this virtual map data store has been released, once released it can no longer be used */
    private final AtomicBoolean isReleased = new AtomicBoolean(false);

    /** If true the data in this version can no longer be changed */
    private final AtomicBoolean isImmutable = new AtomicBoolean(false);

    /** Number of instances of copy that refer to the same files[], this is shared across all copies */
    private final AtomicInteger referenceCount;

    /** Number of keys in this version */
    private final AtomicInteger keyCount = new AtomicInteger(0);

    /** Monotonically increasing version number that is incremented every time copy() is called on the mutable copy. */
    private final long version;

    //==================================================================================================================
    // Constructors

    /**
     *
     * @param storageDirectory
     * @param name
     * @param numOfBins
     * @param numOfFiles
     * @param keySizeBytes The number of
     * @param maxNumberOfKeys The maximum number of key that this map can store, this assumes even key hash distribution.
     * @param maxNumberOfMutations The maximum number of mutations per key we can store
     * @throws IOException
     */
    public FCSlotIndexUsingMemMapFile(Path storageDirectory, String name, int numOfBins, int numOfFiles, int keySizeBytes,
                                      int maxNumberOfKeys, int maxNumberOfMutations) throws IOException {
        if (!positivePowerOfTwo(numOfFiles)) throw new IllegalArgumentException("numOfFiles["+numOfFiles+"] must be a positive power of two.");
        if (!positivePowerOfTwo(numOfBins)) throw new IllegalArgumentException("numOfBins["+numOfBins+"] must be a positive power of two.");
        if (numOfBins <= (2*numOfFiles)) throw new IllegalArgumentException("numOfBins["+numOfBins+"] must be at least twice the size of numOfFiles["+numOfFiles+"].");
        this.storageDirectory = storageDirectory;
        this.name = name;
        this.numOfBins = numOfBins;
        this.numOfFiles = numOfFiles;
        this.numOfBinsPerFile = numOfBins/numOfFiles;
        this.keySizeBytes = keySizeBytes;
        this.maxNumberOfKeys = maxNumberOfKeys;
        this.maxNumberOfMutations = maxNumberOfMutations;
        // compute numOfKeysPerBin
        numOfKeysPerBin = 20; // TODO should be calculated based on maxNumberOfKeys and numOfBins
        // compute shiftRightCountForFileSubKey
        shiftRightCountForFileSubKey = Integer.bitCount(numOfFiles-1);
        // set reference count
        referenceCount = new AtomicInteger(0);
        // set initial version
        version = 1;
        // create storage directory if it doesn't exist
        if (!Files.exists(storageDirectory)) {
            Files.createDirectories(storageDirectory);
        } else {
            // check that storage directory is a directory
            if (!Files.isDirectory(storageDirectory)) {
                throw new IllegalArgumentException("storageDirectory must be a directory. ["+storageDirectory.toFile().getAbsolutePath()+"] is not a directory.");
            }
        }
        // create files
        //noinspection unchecked
        files = new BinFile[numOfFiles];
        for (int i = 0; i < numOfFiles; i++) {
            files[i] = new BinFile<>(storageDirectory.resolve(name+"_"+i+".index"),keySizeBytes, numOfKeysPerBin, numOfBinsPerFile, maxNumberOfMutations);
        }
    }

    /**
     * Construct a new FCFileMap as a fast copy of another FCFileMap
     *
     * @param toCopy The FCFileMap to copy to this new version leaving it immutable.
     */
    private FCSlotIndexUsingMemMapFile(FCSlotIndexUsingMemMapFile<K> toCopy) {
        // copy config
        this.storageDirectory = toCopy.storageDirectory;
        this.name = toCopy.name;
        this.numOfBins = toCopy.numOfBins;
        this.numOfFiles = toCopy.numOfFiles;
        this.numOfBinsPerFile = toCopy.numOfBinsPerFile;
        this.numOfKeysPerBin = toCopy.numOfKeysPerBin;
        this.keySizeBytes = toCopy.keySizeBytes;
        this.maxNumberOfKeys = toCopy.maxNumberOfKeys;
        this.maxNumberOfMutations = toCopy.maxNumberOfMutations;
        this.shiftRightCountForFileSubKey = toCopy.shiftRightCountForFileSubKey;
        // state
        this.keyCount.set(toCopy.keyCount.get());
        this.isReleased.set(false);
        this.files = toCopy.files;
        this.referenceCount = toCopy.referenceCount;
        referenceCount.incrementAndGet(); // add this class as a new reference
        // set our incremental version
        version = toCopy.version + 1;
        // tell files version has changed
        for (BinFile<K> file : files) {
            file.versionChanged(toCopy.version, version);
        }
        // mark instance we copied from as immutable
        toCopy.isImmutable.set(true);
    }

    //==================================================================================================================
    // FastCopy Implementation

    @Override
    public FCSlotIndexUsingMemMapFile<K> copy() {
        this.throwIfReleased();
        return new FCSlotIndexUsingMemMapFile<>(this);
    }

    //==================================================================================================================
    // Releasable Implementation

    @Override
    public void release() {
        isReleased.set(true);
        // release version in all files
        for(BinFile<K> file:files) {
            file.releaseVersion(version);
        }
        // if we were the last reference then close all the files
        if (referenceCount.decrementAndGet() <= 0){
//            for (BinFile<K> file : files) {
//                try {
//                    file.close();
//                } catch (IOException e) {
//                    e.printStackTrace(); // TODO is there a better option here
//                }
//            }
        }
    }

    @Override
    public boolean isReleased() {
        return isReleased.get();
    }

    //==================================================================================================================
    // FCSlotIndex Implementation

    @Override
    public long getSlot(K key) {
        if (key == null) throw new IllegalArgumentException("Key can not be null");
        if (isReleased.get()) throw new IllegalStateException("You can not access a released index.");
        int keyHash = key.hashCode();
        // find right bin file
        BinFile<K> file = getFileForKey(keyHash);
        // ask bin file
        return file.getSlot(version, getFileSubKeyHash(keyHash), key);
    }

    @Override
    public long getSlotIfAbsentPut(K key, LongSupplier newValueSupplier) {
        if (key == null) throw new IllegalArgumentException("Key can not be null");
        if (isReleased.get()) throw new IllegalStateException("You can not access a released index.");
        int keyHash = key.hashCode();
        // find right bin file
        BinFile<K> file = getFileForKey(keyHash);
        // ask bin file
        return file.getSlotIfAbsentPut(version, getFileSubKeyHash(keyHash), key, newValueSupplier);
    }

    @Override
    public void putSlot(K key, long slot) {
        if (key == null) throw new IllegalArgumentException("Key can not be null");
        if (isReleased.get()) throw new IllegalStateException("You can not access a released index.");
        if (isImmutable.get()) throw new IllegalStateException("You can not put on a immutable index.");
        int keyHash = key.hashCode();
        // find right bin file
        BinFile<K> file = getFileForKey(keyHash);
        // ask bin file
        boolean alreadyExisted = file.putSlot(version, getFileSubKeyHash(keyHash), key, slot);
        if (!alreadyExisted) keyCount.incrementAndGet();
    }

    @Override
    public long removeSlot(K key) {
        if (key == null) throw new IllegalArgumentException("Key can not be null");
        if (isReleased.get()) throw new IllegalStateException("You can not access a released index.");
        if (isImmutable.get()) throw new IllegalStateException("You can not remove on a immutable index.");
        int keyHash = key.hashCode();
        // find right bin file
        BinFile<K> file = getFileForKey(keyHash);
        // ask bin file
        long slotLocation = file.removeKey(version, getFileSubKeyHash(keyHash), key);
        if (slotLocation != FCSlotIndex.NOT_FOUND_LOCATION) keyCount.decrementAndGet();
        return slotLocation;
    }

    @Override
    public int keyCount() {
        if (isReleased.get()) throw new IllegalStateException("You can not access a released index.");
        return keyCount.get();
    }

    //==================================================================================================================
    // Util functions

    /** Simple way to check of a integer is a power of two */
    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    private static boolean positivePowerOfTwo(int n){
        return n > 0 && (n & n-1)==0;
    }

    /** for a given hash find out which file it is in */
    private BinFile<K> getFileForKey(int keyHash) {
        final int fileBitMask = numOfFiles-1;
        return files[keyHash & fileBitMask];
    }

    /** Gets the sub key hash from key hash, this is the part of key that specifies the bin inside a file */
    private int getFileSubKeyHash(int keyHash) {
        return keyHash >> shiftRightCountForFileSubKey;
    }
}

