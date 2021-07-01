package com.hedera.services.state.merkle.virtual.persistence.fcmmap;

import com.hedera.services.state.merkle.virtual.persistence.FCSlotIndex;
import com.hedera.services.state.merkle.virtual.persistence.FCVirtualMapHashStore;
import com.hedera.services.state.merkle.virtual.persistence.SlotStore;
import com.swirlds.common.crypto.DigestType;
import com.swirlds.common.crypto.Hash;
import com.swirlds.fcmap.VKey;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Data Store backend for VirtualMap it uses two MemMapDataStores for storing the leaves, hashes data and the provided
 * FCSlotIndexes for storing the mappings from keys and paths to data values.
 *
 * It is thread safe and can be used by multiple-threads. Only one thread can use one of the sub-data stores at a time.
 *
 * @param <HK> The type for hashes keys, must implement SelfSerializable
 */
@SuppressWarnings({"DuplicatedCode"})
public final class FCVirtualMapHashStoreImpl<HK extends VKey> implements FCVirtualMapHashStore<HK> {
    /** 1 Mb of bytes */
    private static final int MB = 1024*1024;

    //==================================================================================================================
    // Config

    /** The size of each mem mapped storage file in MB */
    private final int dataFileSizeInMb;
    /** The path of the directory to store storage files */
    private final Path storageDirectory;
    /** The number of bytes for a hash key "PK", when serialized to ByteBuffer */
    private final int hashKeySizeBytes;
    /** Total number of bytes to be stored for a hash key and value */
    private final int hashStoreSlotSize;

    //==================================================================================================================
    // Value Stores

    /** Store for all the tree hashes */
    private final SlotStore hashStore;

    //==================================================================================================================
    // Indexes

    /** Find a value store slot for a hash by hash path */
    public final FCSlotIndex<HK> hashIndex;

    //==================================================================================================================
    // State

    /** If this virtual data store has been released, once released it can no longer be used */
    private final AtomicBoolean isReleased = new AtomicBoolean(false);
    /** If this data store is immutable(read only) */
    private final AtomicBoolean isImmutable = new AtomicBoolean(false);
    /** True if this store is open */
    private final AtomicBoolean isOpen;

    //==================================================================================================================
    // Constructors

    /**
     * Create new FCVirtualMapDataStoreImpl
     *
     * TODO should we move to writing class ID into data files and then using class factory to create, it is slower as
     * TODO it requires a map lookup for every read and storing extra long for every stored key, path and data value
     *
     * @param storageDirectory The path of the directory to store storage files
     * @param dataFileSizeInMb The size of each mem mapped storage file in MB
     * @param hashKeySizeBytes The number of bytes for a hash key "HK", when serialized to ByteBuffer
     * @param hashSlotIndex slot index instance to use
     * @param storeFactory factory for creating slot store
     */
    public FCVirtualMapHashStoreImpl(Path storageDirectory, int dataFileSizeInMb,
                                     int hashKeySizeBytes,
                                     FCSlotIndex<HK> hashSlotIndex,
                                     SlotStore.SlotStoreFactory storeFactory) throws IOException {
        // store config
        this.storageDirectory = storageDirectory;
        this.dataFileSizeInMb = dataFileSizeInMb;
        this.hashKeySizeBytes = Integer.BYTES + hashKeySizeBytes; // we store an extra int for class serialization version
        // create and open value store
        this.hashStoreSlotSize =
                this.hashKeySizeBytes +// size of PK
                Integer.BYTES + DigestType.SHA_384.digestLength(); // size of Hash
        hashStore = storeFactory.open(hashStoreSlotSize,dataFileSizeInMb*MB,storageDirectory.resolve("hashes"),"hashes_","dat");
        // create indexes
        hashIndex = hashSlotIndex;
        // shared isOpen state
        isOpen = new AtomicBoolean(true);
    }


    /**
     * Copy Constructor
     *
     * @param dataStoreToCopy The data source to copy
     */
    private FCVirtualMapHashStoreImpl(FCVirtualMapHashStoreImpl<HK> dataStoreToCopy) {
        // the copy that we are copying from becomes immutable as only the newest copy can be mutable
        dataStoreToCopy.isImmutable.set(true);
        // a new copy is not released and is mutable, which are default values
        // copy config
        this.storageDirectory = dataStoreToCopy.storageDirectory;
        this.dataFileSizeInMb = dataStoreToCopy.dataFileSizeInMb;
        this.hashKeySizeBytes = dataStoreToCopy.hashKeySizeBytes;
        this.hashStoreSlotSize = dataStoreToCopy.hashStoreSlotSize;
        // open is shared
        this.isOpen = dataStoreToCopy.isOpen;
        // fast copy indexes
        hashIndex = dataStoreToCopy.hashIndex.copy();
        // reuse value stores
        hashStore = dataStoreToCopy.hashStore;
    }


    //==================================================================================================================
    // FastCopy Implementation

    @Override
    public FCVirtualMapHashStoreImpl<HK> copy() {
        this.throwIfImmutable();
        this.throwIfReleased();
        if (!isOpen.get()) throw new IllegalStateException("Only open stores can be fast copied.");
        return new FCVirtualMapHashStoreImpl<>(this);
    }

    @Override
    public boolean isImmutable() {
        return isImmutable.get();
    }

    //==================================================================================================================
    // Releasable Implementation

    @Override
    public void release() {
        isReleased.set(true);
        // TODO need to do ref counting so we can close as we are the lowest level fast releasable
//        // release indexes
//        hashIndex.release();
//        // close hash store if it has no references left
//        synchronized (hashStore) {
//            hashStore.removeReference();
//            hashStore.close();
//        }
    }

    @Override
    public boolean isReleased() {
        return isReleased.get();
    }

    //==================================================================================================================
    // Data Source Implementation

    /**
     * Check if this store contains a hash by key
     *
     * @param hashKey The key of the hash to check for
     * @return true if that hash is stored, false if it is not known
     */
    @Override
    public boolean containsHash(HK hashKey) throws IOException{
        int keyHash = hashKey.hashCode();
        Object indexLock = hashIndex.acquireReadLock(keyHash); // TODO reuse hash with hashIndex call
        Object storeLock = hashStore.acquireReadLock(keyHash);
        try {
          return hashIndex.getSlot(hashKey) != FCSlotIndex.NOT_FOUND_LOCATION;
        } finally {
            hashIndex.releaseReadLock(keyHash,indexLock);
            hashStore.releaseReadLock(keyHash,storeLock);
        }
    }

    /**
     * Delete a stored hash from storage, if it is stored.
     *
     * @param hashKey The key of the hash to delete
     */
    @Override
    public void deleteHash(HK hashKey) throws IOException {
        int keyHash = hashKey.hashCode();
        Object indexLock = hashIndex.acquireWriteLock(keyHash); // TODO reuse hash with hashIndex call
        Object storeLock = hashStore.acquireWriteLock(keyHash);
        try {
            long slotLocation = hashIndex.removeSlot(hashKey);
            if (slotLocation != SlotStore.NOT_FOUND_LOCATION)
                hashStore.deleteSlot(slotLocation); // TODO this is not fast copy safe
        } finally {
            hashIndex.releaseWriteLock(keyHash, indexLock);
        }
    }

    /**
     * Load a tree hash node from storage
     *
     * @param hashKey The key of the hash to find and load
     * @return a loaded VirtualTreeInternal with path and hash set or null if not found
     */
    @Override
    public Hash loadHash(HK hashKey) throws IOException {
        int keyHash = hashKey.hashCode();
        Object indexLock = hashIndex.acquireReadLock(keyHash); // TODO reuse hash with hashIndex call
        try {
            long slotLocation = hashIndex.getSlot(hashKey);
            if (slotLocation == SlotStore.NOT_FOUND_LOCATION) return null;
            Object storeLock = hashStore.acquireReadLock(slotLocation);
            try {
                return hashStore.readSlot(slotLocation, inputStream -> {
                    int position = inputStream.position();
                    // skip hash key
                    //                SerializableLong key = inputStream.readSelfSerializable(position, SerializableLong::new);
                    position += hashKeySizeBytes;
                    // hash data
                    inputStream.position(position);
                    DigestType digestType = DigestType.valueOf(inputStream.readInt());
                    byte[] hashData = new byte[digestType.digestLength()];
                    //noinspection ResultOfMethodCallIgnored
                    inputStream.read(hashData);
                    return new VirtualHash(digestType, hashData);
                });
            } finally {
                hashStore.releaseReadLock(slotLocation,storeLock);
            }
        } finally {
            hashIndex.releaseReadLock(keyHash,indexLock);
        }
    }

    /**
     * Save the hash for a imaginary hash node into storage
     *
     * @param hashKey The key of the hash to save
     * @param hash The hash's data to store
     */
    @Override
    public void saveHash(HK hashKey, Hash hash) throws IOException {
        int keyHash = hashKey.hashCode();
        Object indexLock = hashIndex.acquireWriteLock(keyHash); // TODO reuse hash with hashIndex call
        try {
            // if already stored and if so it is an update
            long slotLocation = hashIndex.getSlotIfAbsentPut(hashKey, hashStore::getNewSlot);
            Object storeLock = hashStore.acquireWriteLock(slotLocation);
            try {
                // write hash into slot
                hashStore.writeSlot(slotLocation, outputStream -> {
                    int position = outputStream.position();
                    // write hash key
                    outputStream.writeSelfSerializable(position, hashKey, hashKeySizeBytes);
                    position += hashKeySizeBytes;
                    // write hash data
                    outputStream.position(position);
                    outputStream.writeInt(hash.getDigestType().id());
                    outputStream.write(hash.getValue()); // TODO Badly need a way to save a hash here without copying the byte[]
                });
            } finally {
                hashStore.releaseWriteLock(slotLocation, storeLock);
            }
        } finally {
            hashIndex.releaseWriteLock(keyHash, indexLock);
        }
    }

    //==================================================================================================================
    // Inner Classes

    /**
     * Class for creating hashes directly from a byte[] without copying and safety checking
     */
    private static final class VirtualHash extends Hash {
        protected VirtualHash(DigestType type, byte[] value) {
            super(value, type, true, false);
        }
    }

}

