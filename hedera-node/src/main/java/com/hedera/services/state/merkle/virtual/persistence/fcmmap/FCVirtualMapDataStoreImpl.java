package com.hedera.services.state.merkle.virtual.persistence.fcmmap;

import com.hedera.services.state.merkle.virtual.persistence.FCSlotIndex;
import com.hedera.services.state.merkle.virtual.persistence.FCVirtualMapDataStore;
import com.hedera.services.state.merkle.virtual.persistence.SlotStore;
import com.swirlds.common.crypto.DigestType;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.io.SelfSerializable;
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;

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
 * @param <PK> The type for hashes keys, must implement SelfSerializable
 * @param <LP> The type for leaf paths, must implement SelfSerializable
 * @param <LK> The type for leaf keys, must implement SelfSerializable
 * @param <LD> The type for leaf data, must implement SelfSerializable
 */
@SuppressWarnings({"DuplicatedCode"})
public final class FCVirtualMapDataStoreImpl<PK extends SelfSerializable,
        LK extends SelfSerializable, LP extends SelfSerializable, LD extends SelfSerializable>
        implements FCVirtualMapDataStore<PK, LK, LP, LD> {
    /** 1 Mb of bytes */
    private static final int MB = 1024*1024;

    //==================================================================================================================
    // Config

    /** The size of each mem mapped storage file in MB */
    private final int dataFileSizeInMb;
    /** The path of the directory to store storage files */
    private final Path storageDirectory;
    /** Constructor supplier for creating leaf data */
    private final Supplier<LD> leafDataConstructor;
    /** The number of bytes for a hash key "PK", when serialized to ByteBuffer */
    private final int hashKeySizeBytes;
    /** The number of bytes for a leaf key "LK", when serialized to ByteBuffer */
    private final int leafKeySizeBytes;
    /** The number of bytes for a leaf path "LP", when serialized to ByteBuffer */
    private final int leafPathSizeBytes;
    /** The number of bytes for a leaf data "LD", when serialized to ByteBuffer */
    private final int leafDataSizeBytes;
    /** Total number of bytes to be stored for a leaf data "LD" */
    private final int leafStoreSlotSize;
    /** Total number of bytes to be stored for a hash data "LD" */
    private final int hashStoreSlotSize;

    //==================================================================================================================
    // Data Stores

    /** Store for all the tree leaves */
    private final SlotStore leafStore;
    /** Store for all the tree hashes */
    private final SlotStore hashStore;

    //==================================================================================================================
    // Indexes

    /** Find a data store slot for a hash by hash path */
    public final FCSlotIndex<PK> hashIndex;
    /** Find a data store slot for a leaf by key */
    public final FCSlotIndex<LK> leafIndex;
    /** Find a data store slot for a leaf by leaf path */
    public final FCSlotIndex<LP> leafPathIndex;

    //==================================================================================================================
    // State

    /** If this virtual map data store has been released, once released it can no longer be used */
    private boolean isReleased = false;
    /** If this data store is immutable(read only) */
    private boolean isImmutable = false;
    /** True if this store is open */
    private boolean isOpen = false;

    //==================================================================================================================
    // Constructors

    /**
     * Create new VirtualMapDataStore
     *
     * @param storageDirectory The path of the directory to store storage files
     * @param dataFileSizeInMb The size of each mem mapped storage file in MB
     * @param hashKeySizeBytes The number of bytes for a hash key "PK", when serialized to ByteBuffer
     * @param leafKeySizeBytes The number of bytes for a leaf key "LK", when serialized to ByteBuffer
     * @param leafPathSizeBytes The number of bytes for a leaf path "LP", when serialized to ByteBuffer
     * @param leafDataSizeBytes The number of bytes for a leaf data value "LD", when serialized to ByteBuffer
     */
    public FCVirtualMapDataStoreImpl(Path storageDirectory, int dataFileSizeInMb,
                                     int hashKeySizeBytes,
                                     int leafKeySizeBytes, int leafPathSizeBytes, int leafDataSizeBytes,
                                     Supplier<FCSlotIndex<PK>> hashSlotIndexSupplier,
                                     Supplier<FCSlotIndex<LP>> leafPathSlotIndexSupplier,
                                     Supplier<FCSlotIndex<LK>> leafSlotIndexSupplier,
                                     Supplier<LD> leafDataConstructor,
                                     Supplier<SlotStore> slotStoreConstructor) {
        // store config
        this.storageDirectory = storageDirectory;
        this.dataFileSizeInMb = dataFileSizeInMb;
        this.leafDataConstructor = leafDataConstructor;
        this.hashKeySizeBytes = hashKeySizeBytes;
        this.leafKeySizeBytes = leafKeySizeBytes;
        this.leafPathSizeBytes = leafPathSizeBytes;
        this.leafDataSizeBytes = leafDataSizeBytes;
        // create data stores
        this.leafStoreSlotSize =
                Integer.BYTES + leafKeySizeBytes +// size of version int and LK
                Integer.BYTES + leafPathSizeBytes +// size of version int and  LP
                Integer.BYTES + leafDataSizeBytes; // size of version int and  LD
        this.hashStoreSlotSize =
                hashKeySizeBytes +// size of PK
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
    private FCVirtualMapDataStoreImpl(FCVirtualMapDataStoreImpl<PK, LK, LP, LD> dataStoreToCopy) {
        // the copy that we are copying from becomes immutable as only the newest copy can be mutable
        dataStoreToCopy.isImmutable = true;
        // a new copy is not released and is mutable
        isReleased = false;
        isImmutable = false;
        // copy config
        this.storageDirectory = dataStoreToCopy.storageDirectory;
        this.dataFileSizeInMb = dataStoreToCopy.dataFileSizeInMb;
        this.leafDataConstructor = dataStoreToCopy.leafDataConstructor;
        this.hashKeySizeBytes = dataStoreToCopy.hashKeySizeBytes;
        this.leafKeySizeBytes = dataStoreToCopy.leafKeySizeBytes;
        this.leafPathSizeBytes = dataStoreToCopy.leafPathSizeBytes;
        this.leafStoreSlotSize = dataStoreToCopy.leafStoreSlotSize;
        this.leafDataSizeBytes = dataStoreToCopy.leafDataSizeBytes;
        this.hashStoreSlotSize = dataStoreToCopy.hashStoreSlotSize;
        this.isOpen = dataStoreToCopy.isOpen;
        // fast copy indexes
        hashIndex = dataStoreToCopy.hashIndex.copy();
        leafIndex = dataStoreToCopy.leafIndex.copy();
        leafPathIndex = dataStoreToCopy.leafPathIndex.copy();
        // reuse data stores
        leafStore = dataStoreToCopy.leafStore;
        leafStore.addReference();
        hashStore = dataStoreToCopy.hashStore;
        hashStore.addReference();
    }


    //==================================================================================================================
    // FastCopy Implementation

    @Override
    public FCVirtualMapDataStoreImpl<PK, LK, LP, LD> copy() {
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
        // open leaf store
        synchronized (leafStore) {
            leafStore.open(leafStoreSlotSize,dataFileSizeInMb*MB,storageDirectory.resolve("leaves"),"leaves_","dat", (location, fileAtSlot) -> {
//                try {
//                    final Account account = new Account(fileAtSlot.readLong(), fileAtSlot.readLong(), fileAtSlot.readLong());
//                    ObjectLongHashMap<VirtualKey> indexMap = index(account, true).leafIndex;
//                    byte[] keyBytes = new byte[keySizeBytes];
//                    fileAtSlot.read(keyBytes);
//                    indexMap.put(
//                            new VirtualKey(keyBytes),
//                            location);
//
//                    LongLongHashMap pathIndexMap = index(account, true).leafPathIndex;
//                    pathIndexMap.put(
//                            fileAtSlot.readLong(),
//                            location);
//                } catch (IOException e) {
//                    e.printStackTrace(); // TODO something better here, this should only happen if our files are corrupt
//                }
            });
        }
        // open hashes store
        synchronized (hashStore) {
            hashStore.open(hashStoreSlotSize,dataFileSizeInMb*MB,storageDirectory.resolve("hashes"),"hashes_","dat", (location, fileAtSlot) -> {
//                try {
//                    final Account account = new Account(fileAtSlot.readLong(), fileAtSlot.readLong(), fileAtSlot.readLong());
//                    final long path = fileAtSlot.readLong();
//                    LongLongHashMap indexMap = index(account, true).hashIndex;
//                    indexMap.put(path, location);
//                } catch (IOException e) {
//                    e.printStackTrace(); // TODO something better here, this should only happen if our files are corrupt
//                }
            });
        }
        isOpen = true;
    }

    /**
     * Make sure all data is flushed to disk. This is an expensive operation. The OS will write all data to disk in the
     * background, so only call this if you need to insure it is written synchronously.
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
                leafStore.deleteSlot(slotLocation);
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
     * @return a loaded leaf data or null if not found
     */
    @Override
    public LD loadLeafByKey(LK key) throws IOException {
        synchronized (leafStore) {
            return loadLeafImpl(leafIndex.getSlot(key));
        }
    }

    /**
     * Load a leaf node record from storage given a path to it
     *
     * @param leafPath The path to the leaf
     * @return a loaded leaf data or null if not found
     */
    @Override
    public LD loadLeafByPath(LP leafPath) throws IOException {
        synchronized (leafStore) {
            return loadLeafImpl(leafPathIndex.getSlot(leafPath));
        }
    }

    /**
     * Load a leaf from file, this must be called in synchronized (leafStore) block
     *
     * @param slotLocation location of leaf in file
     * @return a loaded leaf data or null if not found
     */
    private LD loadLeafImpl(long slotLocation) throws IOException {
        if (slotLocation != MemMapSlotStore.NOT_FOUND_LOCATION) {
            ByteBuffer buffer = leafStore.accessSlot(slotLocation);
            // key and path
            buffer.position(buffer.position() + Integer.BYTES + leafKeySizeBytes + Integer.BYTES + leafPathSizeBytes); // jump over
            SerializableDataInputStream inputStream = new SerializableDataInputStream(new ByteBufferInputStream(buffer));
            // data
            LD leafData = leafDataConstructor.get();
            int version = inputStream.readInt();
            leafData.deserialize(inputStream, version);
            // return buffer
            leafStore.returnSlot(slotLocation,buffer);
            return leafData;
        }
        return null;
    }

    /**
     * Save a VirtualTreeLeaf to storage
     *
     * @param leafKey The key for the leaf to store
     * @param leafPath The path for the leaf to store
     * @param leafData The data for the leaf to store
     */
    @Override
    public void saveLeaf(LK leafKey, LP leafPath, LD leafData) throws IOException {
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
            SerializableDataOutputStream outputStream = new SerializableDataOutputStream(
                    new ByteBufferOutputStream(buffer, leafStoreSlotSize));
            // write key
            outputStream.writeInt(leafKey.getVersion());
            int position = buffer.position();
            leafKey.serialize(outputStream);
            if((buffer.position() - position) != leafKeySizeBytes)
                throw new IOException("Key size doesn't match bytes written "+(buffer.position() - position)+" != "+leafKeySizeBytes);
            // write path
            outputStream.writeInt(leafPath.getVersion());
            position = buffer.position();
            leafPath.serialize(outputStream);
            if((buffer.position() - position) != leafPathSizeBytes) {
                throw new IOException("Leaf Path["+leafPath+"] size doesn't match bytes written " + (buffer.position() - position) + " != " + leafPathSizeBytes);
            }
            // write key data
            outputStream.writeInt(leafData.getVersion());
            position = buffer.position();
            leafData.serialize(outputStream);
            if((buffer.position() - position) > leafDataSizeBytes)
                throw new IOException("Leaf Data size doesn't match bytes written "+(buffer.position() - position)+" != "+leafDataSizeBytes);
            // return buffer
            leafStore.returnSlot(slotLocation,buffer);
        }
    }


    /**
     * Check if this store contains a hash by key
     *
     * @param hashKey The key of the hash to check for
     * @return true if that hash is stored, false if it is not known
     */
    @Override
    public boolean containsHash(PK hashKey) {
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
    public void deleteHash(PK hashKey) {
        synchronized (hashStore) {
            long slotLocation = hashIndex.removeSlot(hashKey);
            if (slotLocation != MemMapSlotStore.NOT_FOUND_LOCATION) hashStore.deleteSlot(slotLocation);
        }
    }

    /**
     * Load a tree hash node from storage
     *
     * @param hashKey The key of the hash to find and load
     * @return a loaded VirtualTreeInternal with path and hash set or null if not found
     */
    @Override
    public Hash loadHash(PK hashKey) throws IOException {
        synchronized (hashStore) {
            long slotLocation = hashIndex.getSlot(hashKey);
            if (slotLocation != MemMapSlotStore.NOT_FOUND_LOCATION) {
                ByteBuffer buffer = hashStore.accessSlot(slotLocation);
                SerializableDataInputStream inputStream = new SerializableDataInputStream(new ByteBufferInputStream(buffer));
                // skip hash key
                buffer.position(buffer.position() + hashKeySizeBytes);
                // hash data
                DigestType digestType = DigestType.valueOf(buffer.getInt());
                byte[] hashData = new byte[digestType.digestLength()];
                buffer.get(hashData);
                // return buffer
                leafStore.returnSlot(slotLocation,buffer);
                return new VirtualHash(digestType, hashData);
            }
        }
        return null;
    }

    /**
     * Save the hash for a imaginary hash node into storage
     *
     * @param hashKey The key of the hash to save
     * @param hashData The hash's data to store
     */
    @Override
    public void saveHash(PK hashKey, Hash hashData) throws IOException {
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
            SerializableDataOutputStream outputStream = new SerializableDataOutputStream(
                    new ByteBufferOutputStream(buffer, hashStoreSlotSize));
            int p = buffer.position();
            // write hash key
            hashKey.serialize(outputStream);
            p = buffer.position();
            // write hash data
            buffer.putInt(hashData.getDigestType().id());
            buffer.put(hashData.getValue()); // TODO Badly need a way to save a hash here without copying the byte[]
            // return buffer
            leafStore.returnSlot(slotLocation,buffer);
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

