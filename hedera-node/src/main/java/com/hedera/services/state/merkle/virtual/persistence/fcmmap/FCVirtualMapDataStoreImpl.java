package com.hedera.services.state.merkle.virtual.persistence.fcmmap;

import com.hedera.services.state.merkle.virtual.persistence.FCSlotIndex;
import com.hedera.services.state.merkle.virtual.persistence.FCVirtualMapDataStore;
import com.hedera.services.state.merkle.virtual.persistence.SlotStore;
import com.swirlds.common.crypto.DigestType;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.io.SelfSerializable;
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;
import com.swirlds.common.merkle.utility.SerializableLong;
import com.swirlds.fcmap.FCVirtualRecord;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.function.Supplier;

/**
 * Data Store backend for VirtualMap it uses two MemMapDataStores for storing the leaves, hashes data and the provided
 * FCSlotIndexes for storing the mappings from keys and paths to data values.
 *
 * It is thread safe and can be used by multiple-threads. Only one thread can use one of the sub-data stores at a time.
 *
 * @param <HK> The type for hashes keys, must implement SelfSerializable
 * @param <LP> The type for leaf paths, must implement SelfSerializable
 * @param <LK> The type for leaf keys, must implement SelfSerializable
 * @param <LV> The type for leaf value, must implement SelfSerializable
 */
@SuppressWarnings({"DuplicatedCode"})
public final class FCVirtualMapDataStoreImpl<HK extends SelfSerializable,
        LK extends SelfSerializable, LP extends SelfSerializable, LV extends SelfSerializable>
        implements FCVirtualMapDataStore<HK, LK, LP, LV> {
    /** 1 Mb of bytes */
    private static final int MB = 1024*1024;

    //==================================================================================================================
    // Config

    /** The size of each mem mapped storage file in MB */
    private final int dataFileSizeInMb;
    /** The path of the directory to store storage files */
    private final Path storageDirectory;
    /** Constructor supplier for creating leaf key */
    private final Supplier<LK> leafKeyConstructor;
    /** Constructor supplier for creating leaf path */
    private final Supplier<LP> leafPathConstructor;
    /** Constructor supplier for creating leaf value */
    private final Supplier<LV> leafValueConstructor;
    /** The number of bytes for a hash key "PK", when serialized to ByteBuffer */
    private final int hashKeySizeBytes;
    /** The number of bytes for a leaf key "LK", when serialized to ByteBuffer */
    private final int leafKeySizeBytes;
    /** The number of bytes for a leaf path "LP", when serialized to ByteBuffer */
    private final int leafPathSizeBytes;
    /** The number of bytes for a leaf value "LD", when serialized to ByteBuffer */
    private final int leafValueSizeBytes;
    /** Total number of bytes to be stored for a leaf key, path and value */
    private final int leafStoreSlotSize;
    /** Total number of bytes to be stored for a hash key and value */
    private final int hashStoreSlotSize;

    //==================================================================================================================
    // Value Stores

    /** Store for all the tree leaves */
    private final SlotStore leafStore;
    /** Store for all the tree hashes */
    private final SlotStore hashStore;

    //==================================================================================================================
    // Indexes

    /** Find a value store slot for a hash by hash path */
    public final FCSlotIndex<HK> hashIndex;
    /** Find a value store slot for a leaf by key */
    public final FCSlotIndex<LK> leafIndex;
    /** Find a value store slot for a leaf by leaf path */
    public final FCSlotIndex<LP> leafPathIndex;

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
     * @param leafKeySizeBytes The number of bytes for a leaf key "LK", when serialized to ByteBuffer
     * @param leafPathSizeBytes The number of bytes for a leaf path "LP", when serialized to ByteBuffer
     * @param leafValueSizeBytes The number of bytes for a leaf value value "LV", when serialized to ByteBuffer
     */
    public FCVirtualMapDataStoreImpl(Path storageDirectory, int dataFileSizeInMb,
                                     int hashKeySizeBytes,
                                     int leafKeySizeBytes, int leafPathSizeBytes, int leafValueSizeBytes,
                                     Supplier<FCSlotIndex<HK>> hashSlotIndexSupplier,
                                     Supplier<FCSlotIndex<LP>> leafPathSlotIndexSupplier,
                                     Supplier<FCSlotIndex<LK>> leafSlotIndexSupplier,
                                     Supplier<LK> leafKeyConstructor,
                                     Supplier<LP> leafPathConstructor,
                                     Supplier<LV> leafValueConstructor,
                                     Supplier<SlotStore> slotStoreConstructor) {
        // store config
        this.storageDirectory = storageDirectory;
        this.dataFileSizeInMb = dataFileSizeInMb;
        this.leafKeyConstructor = leafKeyConstructor;
        this.leafPathConstructor = leafPathConstructor;
        this.leafValueConstructor = leafValueConstructor;
        this.hashKeySizeBytes = Integer.BYTES + hashKeySizeBytes; // we store an extra int for class serialization version
        this.leafKeySizeBytes = Integer.BYTES + leafKeySizeBytes; // we store an extra int for class serialization version
        this.leafPathSizeBytes = Integer.BYTES + leafPathSizeBytes; // we store an extra int for class serialization version
        this.leafValueSizeBytes = Integer.BYTES + leafValueSizeBytes; // we store an extra int for class serialization version
        // create value stores
        this.leafStoreSlotSize =
                this.leafKeySizeBytes +// size of version int and LK
                this.leafPathSizeBytes +// size of version int and  LP
                this.leafValueSizeBytes; // size of version int and  LD
        this.hashStoreSlotSize =
                this.hashKeySizeBytes +// size of PK
                Integer.BYTES + DigestType.SHA_384.digestLength(); // size of Hash

        leafStore = slotStoreConstructor.get();
        hashStore = slotStoreConstructor.get();
        // create indexes
        hashIndex = hashSlotIndexSupplier.get();
        hashIndex.setKeySizeBytes(hashKeySizeBytes);
        leafIndex = leafSlotIndexSupplier.get();
        leafIndex.setKeySizeBytes(leafKeySizeBytes);
        leafPathIndex = leafPathSlotIndexSupplier.get();
        leafPathIndex.setKeySizeBytes(leafPathSizeBytes);
    }


    /**
     * Copy Constructor
     *
     * @param dataStoreToCopy The data source to copy
     */
    private FCVirtualMapDataStoreImpl(FCVirtualMapDataStoreImpl<HK, LK, LP, LV> dataStoreToCopy) {
        // the copy that we are copying from becomes immutable as only the newest copy can be mutable
        dataStoreToCopy.isImmutable = true;
        // a new copy is not released and is mutable
        isReleased = false;
        isImmutable = false;
        // copy config
        this.storageDirectory = dataStoreToCopy.storageDirectory;
        this.dataFileSizeInMb = dataStoreToCopy.dataFileSizeInMb;
        this.leafKeyConstructor = dataStoreToCopy.leafKeyConstructor;
        this.leafPathConstructor = dataStoreToCopy.leafPathConstructor;
        this.leafValueConstructor = dataStoreToCopy.leafValueConstructor;
        this.hashKeySizeBytes = dataStoreToCopy.hashKeySizeBytes;
        this.leafKeySizeBytes = dataStoreToCopy.leafKeySizeBytes;
        this.leafPathSizeBytes = dataStoreToCopy.leafPathSizeBytes;
        this.leafStoreSlotSize = dataStoreToCopy.leafStoreSlotSize;
        this.leafValueSizeBytes = dataStoreToCopy.leafValueSizeBytes;
        this.hashStoreSlotSize = dataStoreToCopy.hashStoreSlotSize;
        this.isOpen = dataStoreToCopy.isOpen;
        // fast copy indexes
        hashIndex = dataStoreToCopy.hashIndex.copy();
        leafIndex = dataStoreToCopy.leafIndex.copy();
        leafPathIndex = dataStoreToCopy.leafPathIndex.copy();
        // reuse value stores
        leafStore = dataStoreToCopy.leafStore;
        leafStore.addReference();
        hashStore = dataStoreToCopy.hashStore;
        hashStore.addReference();
    }


    //==================================================================================================================
    // FastCopy Implementation

    @Override
    public FCVirtualMapDataStoreImpl<HK, LK, LP, LV> copy() {
        this.throwIfImmutable();
        this.throwIfReleased();
        if (!isOpen) throw new IllegalStateException("Only open stores can be fast copied.");
        return new FCVirtualMapDataStoreImpl<>(this);
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
        leafIndex.release();
        leafPathIndex.release();
        // close leaf store if it has no references left
        synchronized (leafStore) {
            leafStore.removeReference();
            leafStore.close();
        }
        // close hash store if it has no references left
        synchronized (hashStore) {
            leafStore.removeReference();
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
        // open leaf store
        synchronized (leafStore) {
            leafStore.open(leafStoreSlotSize,dataFileSizeInMb*MB,storageDirectory.resolve("leaves"),"leaves_","dat", null);
        }
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
        synchronized (leafStore) {
            leafStore.sync();
        }
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
     * Delete a stored leaf from storage, if it is stored.
     *
     * @param leafKey The key for the leaf to delete
     * @param leafPath The path for the leaf to delete
     */
    @Override
    public void deleteLeaf(LK leafKey, LP leafPath){
        synchronized (leafStore) {
            long slotLocation = leafIndex.removeSlot(leafKey);
            if (slotLocation != MemMapSlotStore.NOT_FOUND_LOCATION){
                leafStore.deleteSlot(slotLocation); // TODO this is not fast copy safe
                leafPathIndex.removeSlot(leafPath);
            }
        }
    }

    /**
     * Removes all leaves and hashes
     */
    public void clear() {
        // TODO, how to do efficiently
    }

    /**
     * Check if this store contains a leaf by key
     *
     * @param leafKey The key of the leaf to check for
     * @return true if that leaf is stored, false if it is not known
     */
    @Override
    public boolean containsLeafKey(LK leafKey) {
        synchronized (leafStore) {
            return leafIndex.getSlot(leafKey) != FCSlotIndex.NOT_FOUND_LOCATION;
        }
    }

    /**
     * Get the number of leaves for a given account
     *
     * @return 0 if the account doesn't exist otherwise the number of leaves stored for the account
     */
    @Override
    public int leafCount() {
        synchronized (leafStore) {
            return leafIndex.keyCount();
        }
    }

    /**
     * Load a leaf node record from storage given the key for it
     *
     * @param key The key of the leaf to find
     * @return a loaded leaf value or null if not found
     */
    @Override
    public LV loadLeafValueByKey(LK key) throws IOException {
        synchronized (leafStore) {
            return loadLeafImpl(leafIndex.getSlot(key));
        }
    }

    /**
     * Load a leaf node record from storage given a path to it
     *
     * @param leafPath The path to the leaf
     * @return a loaded leaf value or null if not found
     */
    @Override
    public LV loadLeafValueByPath(LP leafPath) throws IOException {
        synchronized (leafStore) {
            return loadLeafImpl(leafPathIndex.getSlot(leafPath));
        }
    }

    /**
     * Load a leaf from file, this must be called in synchronized (leafStore) block
     *
     * @param slotLocation location of leaf in file
     * @return a loaded leaf value or null if not found
     */
    private LV loadLeafImpl(long slotLocation) throws IOException {
        if (slotLocation == SlotStore.NOT_FOUND_LOCATION) return null;
        // access the slot
        ByteBuffer buffer = leafStore.accessSlot(slotLocation);
        int position = buffer.position();
        // key
        position += leafKeySizeBytes;
        // path
        position += leafPathSizeBytes;
        // value
        LV leafValue = readSelfSerializable(buffer,position, leafValueConstructor);
        // return buffer
        leafStore.returnSlot(slotLocation, buffer);
        return leafValue;
    }

    /**
     * Load a leaf value from storage given a path to it
     *
     * @param key The key of the leaf to find
     * @return a loaded leaf value or null if not found
     */
    public LP loadLeafPathByKey(LK key) throws IOException {
        synchronized (leafStore) {
            long slotLocation = leafIndex.getSlot(key);
            if (slotLocation == SlotStore.NOT_FOUND_LOCATION) return null;
            // access the slot
            ByteBuffer buffer = leafStore.accessSlot(slotLocation);
            int position = buffer.position();
            // key
            position += leafKeySizeBytes;
            // path
            LP leafPath = readSelfSerializable(buffer,position,leafPathConstructor);
            // return buffer
            leafStore.returnSlot(slotLocation, buffer);
            // return path
            return leafPath;
        }
    }

    /**
     * Load a leaf value from storage given a path to it
     *
     * @param leafPath The path to the leaf
     * @return a loaded leaf key and value or null if not found
     */
    public FCVirtualRecord<LK, LV> loadLeafRecordByPath(LP leafPath) throws IOException {
        synchronized (leafStore) {
            long slotLocation = leafPathIndex.getSlot(leafPath);
            if (slotLocation == SlotStore.NOT_FOUND_LOCATION) return null;
            // access the slot
            ByteBuffer buffer = leafStore.accessSlot(slotLocation);
            int position = buffer.position();
            // key
            LK leafKey = readSelfSerializable(buffer,position,leafKeyConstructor);
            position += leafKeySizeBytes;
            // path
//            LP leafPath = readSelfSerializable(buffer,position,leafPathConstructor);
            position += leafPathSizeBytes;
            // value
            LV leafValue = readSelfSerializable(buffer,position, leafValueConstructor);
            // return buffer
            leafStore.returnSlot(slotLocation, buffer);
            // return path
            return new FCVirtualRecord<>(leafKey, leafValue);
        }
    }

    /**
     * Save a VirtualTreeLeaf to storage
     *
     * @param leafKey The key for the leaf to store
     * @param leafPath The path for the leaf to store
     * @param leafValue The value for the leaf to store
     */
    @Override
    public void saveLeaf(LK leafKey, LP leafPath, LV leafValue) throws IOException {
        synchronized (leafStore) {
            // if already stored and if so it is an update
            long slotLocation = leafIndex.getSlot(leafKey);
            if (slotLocation == MemMapSlotStore.NOT_FOUND_LOCATION) {
                // find a new slot location
                slotLocation = leafStore.getNewSlot();
                // store in indexes
                leafIndex.putSlot(leafKey,slotLocation);
                leafPathIndex.putSlot(leafPath, slotLocation);
            }
            // write leaf into slot
            ByteBuffer buffer = leafStore.accessSlot(slotLocation);
            int position = buffer.position();
            // write key
            writeSelfSerializable(buffer,position, leafKey, leafKeySizeBytes);
            position += leafKeySizeBytes;
            // write path
            writeSelfSerializable(buffer,position, leafPath, leafPathSizeBytes);
            position += leafPathSizeBytes;
            // write key value
            writeSelfSerializable(buffer,position, leafValue, leafValueSizeBytes);
            // return buffer
            leafStore.returnSlot(slotLocation,buffer);
        }
    }

    /**
     * Update the path to a leaf
     *
     * @param oldPath The current path to the leaf in the store
     */
    public void updateLeafPath(LP oldPath, LP newPath) throws IOException {
        long leafSlot = leafPathIndex.removeSlot(oldPath);
        if (leafSlot == MemMapSlotStore.NOT_FOUND_LOCATION) throw new IOException("You just asked me to updateLeafPath for a leaf that doesn't exist.");
        leafPathIndex.putSlot(newPath,leafSlot);
        // update the path in the leaf's slot // TODO this is not Fast Copy Safe Yet
        // write leaf into slot
        ByteBuffer buffer = leafStore.accessSlot(leafSlot);
        int position = buffer.position();
        // write key
        position += leafKeySizeBytes;
        // write path
        writeSelfSerializable(buffer,position, newPath, leafPathSizeBytes);
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
            ByteBuffer buffer = hashStore.accessSlot(slotLocation);
            int position = buffer.position();
            // skip hash key
//                SerializableLong key = readSelfSerializable(buffer,position, SerializableLong::new);
            position += hashKeySizeBytes;
            // hash data
            buffer.position(position);
            DigestType digestType = DigestType.valueOf(buffer.getInt());
            byte[] hashData = new byte[digestType.digestLength()];
            buffer.get(hashData);
            // return buffer
            leafStore.returnSlot(slotLocation,buffer);
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
            ByteBuffer buffer = hashStore.accessSlot(slotLocation);
            int position = buffer.position();
            // write hash key
            writeSelfSerializable(buffer, position, hashKey, hashKeySizeBytes);
            position += hashKeySizeBytes;
            // write hash data
            buffer.position(position);
            buffer.putInt(hash.getDigestType().id());
            buffer.put(hash.getValue()); // TODO Badly need a way to save a hash here without copying the byte[]
            // return buffer
            leafStore.returnSlot(slotLocation,buffer);
        }
    }


    //==================================================================================================================
    // Helper Methods

    public <T extends SelfSerializable> T readSelfSerializable(ByteBuffer buffer, int startOffset, Supplier<T> constructor) throws IOException {
        buffer.position(startOffset);
        SerializableDataInputStream inputStream = new SerializableDataInputStream(new ByteBufferInputStream(buffer));
        int version = inputStream.readInt();
        T object = constructor.get();
        object.deserialize(inputStream, version);
        return object;
    }

    public void writeSelfSerializable(ByteBuffer buffer, int startOffset, SelfSerializable object, int maxNumberOfBytes) throws IOException {
        buffer.position(startOffset);
        SerializableDataOutputStream outputStream = new SerializableDataOutputStream(new ByteBufferOutputStream(buffer, maxNumberOfBytes));
        outputStream.writeInt(object.getVersion());
        object.serialize(outputStream);
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

