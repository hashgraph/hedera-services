package com.hedera.services.state.merkle.virtual.persistence.fcmmap;

import com.hedera.services.state.merkle.virtual.persistence.FCSlotIndex;
import com.hedera.services.state.merkle.virtual.persistence.FCVirtualMapHashStore;
import com.hedera.services.state.merkle.virtual.persistence.SlotStore;
import com.swirlds.common.crypto.DigestType;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.io.SelfSerializable;

import java.io.IOException;
import java.nio.file.Path;
import java.util.function.Supplier;

/**
 * Data Store backend for VirtualMap it uses two MemMapDataStores for storing the leaves, hashes data and the provided
 * FCSlotIndexes for storing the mappings from keys and paths to data values.
 *
 * It is thread safe and can be used by multiple-threads. Only one thread can use one of the sub-data stores at a time.
 *
 * @param <HK> The type for hashes keys, must implement SelfSerializable
 */
@SuppressWarnings({"DuplicatedCode"})
public final class FCVirtualMapHashStoreImpl<HK extends SelfSerializable> implements FCVirtualMapHashStore<HK> {
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
    private boolean isReleased = false;
    /** If this data store is immutable(read only) */
    private boolean isImmutable = false;
    /** True if this store is open */
    private boolean isOpen = false;

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
     */
    public FCVirtualMapHashStoreImpl(Path storageDirectory, int dataFileSizeInMb,
                                     int hashKeySizeBytes,
                                     FCSlotIndex<HK> hashSlotIndex,
                                     Supplier<SlotStore> slotStoreConstructor) {
        // store config
        this.storageDirectory = storageDirectory;
        this.dataFileSizeInMb = dataFileSizeInMb;
        this.hashKeySizeBytes = Integer.BYTES + hashKeySizeBytes; // we store an extra int for class serialization version
        // create value stores
        this.hashStoreSlotSize =
                this.hashKeySizeBytes +// size of PK
                Integer.BYTES + DigestType.SHA_384.digestLength(); // size of Hash

        hashStore = slotStoreConstructor.get();
        // create indexes
        hashIndex = hashSlotIndex;
    }


    /**
     * Copy Constructor
     *
     * @param dataStoreToCopy The data source to copy
     */
    private FCVirtualMapHashStoreImpl(FCVirtualMapHashStoreImpl<HK> dataStoreToCopy) {
        // the copy that we are copying from becomes immutable as only the newest copy can be mutable
        dataStoreToCopy.isImmutable = true;
        // a new copy is not released and is mutable
        isReleased = false;
        isImmutable = false;
        // copy config
        this.storageDirectory = dataStoreToCopy.storageDirectory;
        this.dataFileSizeInMb = dataStoreToCopy.dataFileSizeInMb;
        this.hashKeySizeBytes = dataStoreToCopy.hashKeySizeBytes;
        this.hashStoreSlotSize = dataStoreToCopy.hashStoreSlotSize;
        this.isOpen = dataStoreToCopy.isOpen;
        // fast copy indexes
        hashIndex = dataStoreToCopy.hashIndex.copy();
        // reuse value stores
        hashStore = dataStoreToCopy.hashStore;
        hashStore.addReference();
    }


    //==================================================================================================================
    // FastCopy Implementation

    @Override
    public FCVirtualMapHashStoreImpl<HK> copy() {
        this.throwIfImmutable();
        this.throwIfReleased();
        if (!isOpen) throw new IllegalStateException("Only open stores can be fast copied.");
        return new FCVirtualMapHashStoreImpl<>(this);
    }

    @Override
    public boolean isImmutable() {
        return isImmutable;
    }

    //==================================================================================================================
    // Releasable Implementation

    @Override
    public void release() {
        isReleased = true;
        isOpen = false;
        // release indexes
        hashIndex.release();
        // close hash store if it has no references left
        synchronized (hashStore) {
            hashStore.removeReference();
            hashStore.close();
        }
    }

    @Override
    public boolean isReleased() {
        return isReleased;
    }

    //==================================================================================================================
    // Data Source Implementation

    /**
     * Open all storage files and read the indexes.
     */
    @Override
    public void open() throws IOException {
        if (isOpen) throw new IOException("Store is already open.");
        // TODO This could be faster if we open each in a thread.
        // TODO Do we need slot visitors any more?
        // open hashes store
        synchronized (hashStore) {
            hashStore.open(hashStoreSlotSize,dataFileSizeInMb*MB,storageDirectory.resolve("hashes"),"hashes_","dat", null);
        }
        isOpen = true;
    }

    /**
     * Make sure all data is flushed to disk. This is an expensive operation. The OS will write all data to disk in the
     * background, so only call this if you need to insure it is written synchronously.
     * TODO do we need this as API? Feels like data store and indexes should handle internally on release()
     */
    @Override
    public void sync(){
        // sync leaf store
        // sync hash store
        synchronized (hashStore) {
            hashStore.sync();
        }
        // TODO add syncing for index
        // hashIndex
        // leafIndex
        // leafPathIndex
    }

    /**
     * Removes all leaves and hashes
     */
    public void clear() {
        // TODO, how to do efficiently
    }

    /**
     * Check if this store contains a hash by key
     *
     * @param hashKey The key of the hash to check for
     * @return true if that hash is stored, false if it is not known
     */
    @Override
    public boolean containsHash(HK hashKey) {
        synchronized (hashStore) {
            return hashIndex.getSlot(hashKey) != FCSlotIndex.NOT_FOUND_LOCATION;
        }
    }

    /**
     * Delete a stored hash from storage, if it is stored.
     *
     * @param hashKey The key of the hash to delete
     */
    @Override
    public void deleteHash(HK hashKey) {
        synchronized (hashStore) {
            long slotLocation = hashIndex.removeSlot(hashKey);
            if (slotLocation != MemMapSlotStore.NOT_FOUND_LOCATION) hashStore.deleteSlot(slotLocation); // TODO this is not fast copy safe
        }
    }

    /**
     * Load a tree hash node from storage
     *
     * @param hashKey The key of the hash to find and load
     * @return a loaded VirtualTreeInternal with path and hash set or null if not found
     */
    @SuppressWarnings("RedundantThrows")
    @Override
    public Hash loadHash(HK hashKey) throws IOException {
        synchronized (hashStore) {
            long slotLocation = hashIndex.getSlot(hashKey);
            if (slotLocation == MemMapSlotStore.NOT_FOUND_LOCATION) return null;
            var inputStream = hashStore.accessSlotForReading(slotLocation);
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
            // return buffer
            hashStore.returnSlot(slotLocation,inputStream);
            return new VirtualHash(digestType, hashData);
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
        synchronized (hashStore) {
            // if already stored and if so it is an update
            long slotLocation = hashIndex.getSlot(hashKey);
            if (slotLocation == MemMapSlotStore.NOT_FOUND_LOCATION) {
                // find a new slot location
                slotLocation = hashStore.getNewSlot();
                // store in index
                hashIndex.putSlot(hashKey, slotLocation);
            }
            // write hash into slot
            var outputStream = hashStore.accessSlotForWriting(slotLocation);
            int position = outputStream.position();
            // write hash key
            outputStream.writeSelfSerializable(position, hashKey, hashKeySizeBytes);
            position += hashKeySizeBytes;
            // write hash data
            outputStream.position(position);
            outputStream.writeInt(hash.getDigestType().id());
            outputStream.write(hash.getValue()); // TODO Badly need a way to save a hash here without copying the byte[]
            // return buffer
            hashStore.returnSlot(slotLocation,outputStream);
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

