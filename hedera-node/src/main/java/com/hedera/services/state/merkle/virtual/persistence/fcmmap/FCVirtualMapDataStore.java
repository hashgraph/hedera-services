package com.hedera.services.state.merkle.virtual.persistence.fcmmap;

import com.hedera.services.state.merkle.virtual.Account;
import com.hedera.services.state.merkle.virtual.VirtualTreePath;
import com.swirlds.common.FastCopyable;
import com.swirlds.common.crypto.DigestType;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.crypto.Hashable;

import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.function.Supplier;

/**
 * Data Store backend for VirtualMap it uses three MemMapDataStores for storing the leaves, parents and paths.
 *
 * It is thread safe and can be used by multiple-threads. Only one thread can use one of the sub-data stores at a time.
 *
 * @param <VMK> The key for VirtualMap, must implement ByteBufferSerializable
 * @param <LK> The key for finding leaves, must implement ByteBufferSerializable
 * @param <LD> The type for leaf data, must implement ByteBufferSerializable
 */
@SuppressWarnings({"DuplicatedCode", "jol"})
public final class FCVirtualMapDataStore<VMK extends ByteBufferSerializable, LK extends ByteBufferSerializable, LD extends ByteBufferSerializable & Hashable>
        implements FastCopyable<FCVirtualMapDataStore<VMK, LK, LD>> {
    /** 1 Mb of bytes */
    private static final int MB = 1024*1024;
    /** The size of a hash we store in bytes, TODO what happens if we change digest? */
    public static final int HASH_SIZE_BYTES = DigestType.SHA_384.digestLength();

    //==================================================================================================================
    // Config

    /** The number of bytes for a virtual map key "VMK" when serialized to a ByteBuffer */
    private final int virtualMapKeySizeBytes;
    /** The number of bytes for a leaf key "LK" when serialized to a ByteBuffer */
    private final int leafKeySizeBytes;
    /** The number of bytes for a leaf data value "LD" when serialized to a ByteBuffer */
    private final int leafDataSizeBytes;
    /** The size of each mem mapped storage file in MB */
    private final int dataFileSizeInMb;
    /** The path of the directory to store storage files */
    private final Path storageDirectory;
    /** Constructor supplier for creating Virtual Map Keys */
    private final Supplier<VMK> virtualMapKeyConstructor;
    /** Constructor supplier for creating leaf keys */
    private final Supplier<LK> leafKeyConstructor;
    /** Constructor supplier for creating leaf keys */
    private final Supplier<LD> leafDataConstructor;

    //==================================================================================================================
    // Data Stores

    /**
     * Store for all the tree leaves
     *
     * Contains:
     * Account -- Account.BYTES
     * Key -- keySizeBytes
     * Path -- VirtualTreePath.BYTES
     * Value -- dataSizeBytes
     * Hash -- HASH_SIZE_BYTES
     */
    private final FCMemMapDataStore leafStore;
    /**
     * Store for all the tree parents
     *
     * Contains:
     * Account -- Account.BYTES
     * Path -- VirtualTreePath.BYTES
     * Hash -- HASH_SIZE_BYTES
     */
    private final FCMemMapDataStore parentStore;

    //==================================================================================================================
    // Indexes

    /** Find a data store slot for a parent by parent path */
    public final FCSlotIndex<VmkAndPath<VMK>> parentIndex;
    /** Find a data store slot for a leaf by key */
    public final FCSlotIndex<VmkAndLeafKey<VMK,LK>> leafIndex;
    /** Find a data store slot for a leaf by leaf path */
    public final FCSlotIndex<VmkAndPath<VMK>> leafPathIndex;

    //==================================================================================================================
    // State

    /** If this virtual map data store has been released, once released it can no longer be used */
    private boolean isReleased = false;
    /** If this data store is immutable(read only) */
    private boolean isImmutable = false;

    //==================================================================================================================
    // Constructors

    /**
     * Create new VirtualMapDataStore
     *
     * @param storageDirectory The path of the directory to store storage files
     * @param virtualMapKeySizeBytes The number of bytes for a virtual map key "VMK", when serialized to ByteBuffer
     * @param leafKeySizeBytes The number of bytes for a leaf key "LK", when serialized to ByteBuffer
     * @param leafDataSizeBytes The number of bytes for a leaf data value "LD", when serialized to ByteBuffer
     * @param dataFileSizeInMb The size of each mem mapped storage file in MB
     */
    public FCVirtualMapDataStore(Path storageDirectory, int virtualMapKeySizeBytes, int leafKeySizeBytes, int leafDataSizeBytes, int dataFileSizeInMb,
                                 Supplier<FCSlotIndex<VmkAndPath<VMK>>> vmkSlotIndexSupplier,
                                 Supplier<FCSlotIndex<VmkAndLeafKey<VMK,LK>>> leafSlotIndexSupplier,
                                 Supplier<VMK> virtualMapKeyConstructor,Supplier<LK> leafKeyConstructor,
                                 Supplier<LD> leafDataConstructor) {
        // store config
        this.storageDirectory = storageDirectory;
        this.virtualMapKeySizeBytes = virtualMapKeySizeBytes;
        this.leafKeySizeBytes = leafKeySizeBytes;
        this.leafDataSizeBytes = leafDataSizeBytes;
        this.dataFileSizeInMb = dataFileSizeInMb;
        this.virtualMapKeyConstructor = virtualMapKeyConstructor;
        this.leafKeyConstructor = leafKeyConstructor;
        this.leafDataConstructor = leafDataConstructor;
        // create data stores
        int leafStoreSlotSize =
                virtualMapKeySizeBytes + leafKeySizeBytes +// size of VmkAndLeafKey<VMK,LK>
                HASH_SIZE_BYTES + // TODO this should include the digest type, and think about how we change the digest to larger digests
                leafDataSizeBytes; // size of LD
        int parentStoreSlotSize =
                virtualMapKeySizeBytes + Long.BYTES + // size of VmkAndPath<VMK>
                HASH_SIZE_BYTES; // TODO this should include the digest type, and think about how we change the digest to larger digests

        leafStore = new FCMemMapDataStore(leafStoreSlotSize,dataFileSizeInMb*MB,storageDirectory.resolve("leaves"),"leaves_","dat");
        parentStore = new FCMemMapDataStore(parentStoreSlotSize,dataFileSizeInMb*MB,storageDirectory.resolve("parents"),"parents_","dat");
        // create indexes
        parentIndex = vmkSlotIndexSupplier.get();
        leafIndex = leafSlotIndexSupplier.get();
        leafPathIndex = vmkSlotIndexSupplier.get();
    }


    /**
     * Copy Constructor
     *
     * @param dataStoreToCopy The data source to copy
     */
    private FCVirtualMapDataStore(FCVirtualMapDataStore<VMK,LK, LD> dataStoreToCopy) {
        // the copy that we are copying from becomes immutable as only the newest copy can be mutable
        dataStoreToCopy.isImmutable = true;
        // a new copy is not released and is mutable
        isReleased = false;
        isImmutable = false;
        // copy config
        this.storageDirectory = dataStoreToCopy.storageDirectory;
        this.virtualMapKeySizeBytes = dataStoreToCopy.virtualMapKeySizeBytes;
        this.leafKeySizeBytes = dataStoreToCopy.leafKeySizeBytes;
        this.leafDataSizeBytes = dataStoreToCopy.leafDataSizeBytes;
        this.dataFileSizeInMb = dataStoreToCopy.dataFileSizeInMb;
        this.virtualMapKeyConstructor = dataStoreToCopy.virtualMapKeyConstructor;
        this.leafKeyConstructor = dataStoreToCopy.leafKeyConstructor;
        this.leafDataConstructor = dataStoreToCopy.leafDataConstructor;
        // fast copy indexes
        parentIndex = dataStoreToCopy.parentIndex.copy();
        leafIndex = dataStoreToCopy.leafIndex.copy();
        leafPathIndex = dataStoreToCopy.leafPathIndex.copy();
        // reuse data stores
        leafStore = dataStoreToCopy.leafStore;
        parentStore = dataStoreToCopy.parentStore;
    }


    //==================================================================================================================
    // FastCopy Implementation

    @Override
    public FCVirtualMapDataStore<VMK,LK,LD> copy() {
        this.throwIfImmutable();
        this.throwIfReleased();
        return new FCVirtualMapDataStore<>(this);
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
        // TODO need to keep reference count to stores maybe
        // close leaf store
        synchronized (leafStore) {
            leafStore.close();
        }
        // close parent store
        synchronized (parentStore) {
            parentStore.close();
        }
        // TODO what else here
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
    public void open(){
        // This could be faster if we open each in a thread.
        // open leaf store
        synchronized (leafStore) {
            leafStore.open((location, fileAtSlot) -> {
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
        // open parents store
        synchronized (parentStore) {
            parentStore.open((location, fileAtSlot) -> {
//                try {
//                    final Account account = new Account(fileAtSlot.readLong(), fileAtSlot.readLong(), fileAtSlot.readLong());
//                    final long path = fileAtSlot.readLong();
//                    LongLongHashMap indexMap = index(account, true).parentIndex;
//                    indexMap.put(path, location);
//                } catch (IOException e) {
//                    e.printStackTrace(); // TODO something better here, this should only happen if our files are corrupt
//                }
            });
        }
    }

    /**
     * Make sure all data is flushed to disk. This is an expensive operation. The OS will write all data to disk in the
     * background, so only call this if you need to insure it is written synchronously.
     */
    public void sync(){
        // sync leaf store
        synchronized (leafStore) {
            leafStore.sync();
        }
        // sync parent store
        synchronized (parentStore) {
            parentStore.sync();
        }
        // TODO add syncing for index
        // parentIndex
        // leafIndex
        // leafPathIndex
    }

    /**
     * Delete a stored leaf from storage, if it is stored.
     *
     * @param mapKey The key for the virtual map who's leaf we are deleting
     * @param leafKey The key for the leaf to delete
     * @param path The path for the leaf to delete
     */
    public void deleteLeaf(VMK mapKey, LK leafKey, long path){
        synchronized (leafStore) {
            long slotLocation = leafIndex.removeSlot(new VmkAndLeafKey<>(mapKey, leafKey));
            if (slotLocation != FCMemMapDataStore.NOT_FOUND_LOCATION){
                leafStore.deleteSlot(slotLocation);
                leafPathIndex.removeSlot(new VmkAndPath<>(mapKey,path));
            }
        }
    }

    /**
     * Get the number of leaves for a given account
     *
     * @return 0 if the account doesn't exist otherwise the number of leaves stored for the account
     */
    public int leafCount() {
        synchronized (leafStore) {
            return leafIndex.keyCount();
        }
    }

    /**
     * Load a leaf node record from storage given the key for it
     *
     * @param mapKey The key for the virtual map who's leaf we are getting
     * @param key The key of the leaf to find
     * @return a loaded leaf data or null if not found
     */
    public LD loadLeaf(VMK mapKey, LK key){
        synchronized (leafStore) {
            return loadLeafImpl(leafIndex.getSlot(new VmkAndLeafKey<>(mapKey, key)));
        }
    }

    /**
     * Load a leaf node record from storage given a path to it
     *
     * @param mapKey The key for the virtual map who's leaf we are getting
     * @param path The path to the leaf
     * @return a loaded leaf data or null if not found
     */
    public LD loadLeaf(VMK mapKey, long path) {
        synchronized (leafStore) {
            return loadLeafImpl(leafPathIndex.getSlot(new VmkAndPath<>(mapKey, path)));
        }
    }

    /**
     * Load a leaf from file, this must be called in synchronized (leafStore) block
     *
     * @param slotLocation location of leaf in file
     * @return a loaded leaf data or null if not found
     */
    private LD loadLeafImpl(long slotLocation) {
        if (slotLocation != FCMemMapDataStore.NOT_FOUND_LOCATION) {
            ByteBuffer buffer = leafStore.accessSlot(slotLocation);
            // Account -- Account.BYTES
            buffer.position(buffer.position() + Account.BYTES); // jump over
            // Key -- keySizeBytes
            byte[] keyBytes = new byte[leafKeySizeBytes];
            buffer.get(keyBytes);
            // Path -- VirtualTreePath.BYTES
            long path = buffer.getLong(); // skip it!
            // Hash
            byte[] hashBytes = new byte[HASH_SIZE_BYTES];
            buffer.get(hashBytes);
            // Value -- dataSizeBytes
            LD leafData = leafDataConstructor.get();
            leafData.read(buffer);
            leafData.setHash(new VirtualHash(hashBytes));
            return leafData;
        }
        return null;
    }
//
//    /**
//     * Directly load the value of a leaf
//     *
//     * @param mapKey The key for the virtual map who's leaf we are getting
//     * @param key The key for the data for the leaf
//     * @return value of leaf
//     */
//    public VirtualValue loadLeafValue(VMK mapKey, LK key) {
//        VirtualValue virtualValue = null;
//        synchronized (leafStore) {
//            long slotLocation = leafIndex.getSlot(new VmkAndLeafKey<>(mapKey, key));
//            if (slotLocation != FCMemMapDataStore.NOT_FOUND_LOCATION) {
//                ByteBuffer buffer = leafStore.accessSlot(slotLocation);
//                // Account -- Account.BYTES
//                // Key -- keySizeBytes
//                // Path -- VirtualTreePath.BYTES
//                buffer.position(buffer.position() + Account.BYTES + keySizeBytes + VirtualTreePath.BYTES); // jump over
//                // Value -- dataSizeBytes
//                byte[] valueBytes = new byte[dataSizeBytes];
//                buffer.get(valueBytes);
//                virtualValue = new VirtualValue(valueBytes);
//            }
//        }
//        return virtualValue;
//    }

    /**
     * Save a VirtualTreeLeaf to storage
     *
     * @param mapKey The virtual map key for the leaf to store
     * @param key The key for the leaf to store
     * @param leafPath The path for the leaf to store
     * @param leafData The data for the leaf to store
     */
    public void saveLeaf(VMK mapKey, LK key, long leafPath,  LD leafData) {
        synchronized (leafStore) {
            VmkAndLeafKey<VMK,LK> vmkAndLeafKey = new VmkAndLeafKey<>(mapKey, key);
            // if already stored and if so it is an update
            long slotLocation = leafIndex.getSlot(vmkAndLeafKey);
            if (slotLocation == FCMemMapDataStore.NOT_FOUND_LOCATION) {
                // find a new slot location
                slotLocation = leafStore.getNewSlot();
                // store in indexes
                leafIndex.putSlot(vmkAndLeafKey,slotLocation);
                leafPathIndex.putSlot(new VmkAndPath<>(mapKey, leafPath), slotLocation);
            }
            // write leaf into slot
            ByteBuffer buffer = leafStore.accessSlot(slotLocation);
            // write VmkAndLeafKey
            vmkAndLeafKey.write(buffer);
            // Hash -- 384
            // TODO would be nice for a way to do this without copy
            // TODO should leaf data write the hash or us
            buffer.put(leafData.getHash().getValue());
            // Value -- dataSizeBytes
            leafData.write(buffer);
        }
    }

    /**
     * Delete a stored parent from storage, if it is stored.
     *
     * @param mapKey The key for the virtual map who's parent we are deleting
     * @param parentPath The path of the parent to delete
     */
    public void deleteParent(VMK mapKey, long parentPath) {
        synchronized (parentStore) {
            long slotLocation = parentIndex.removeSlot(new VmkAndPath<>(mapKey,parentPath));
            if (slotLocation != FCMemMapDataStore.NOT_FOUND_LOCATION) parentStore.deleteSlot(slotLocation);
        }
    }


    /**
     * Load a tree parent node from storage
     *
     * @param mapKey The key for the virtual map who's parent we are getting hash for
     * @param path The path of the parent to find and load
     * @return a loaded VirtualTreeInternal with path and hash set or null if not found
     */
    public Hash loadParentHash(VMK mapKey, long path) {
        synchronized (parentStore) {
            long slotLocation = leafPathIndex.getSlot(new VmkAndPath<>(mapKey, path));
            if (slotLocation != FCMemMapDataStore.NOT_FOUND_LOCATION) {
                ByteBuffer buffer = parentStore.accessSlot(slotLocation);
                // Key - TODO could jump probably
                VmkAndPath<VMK> key = new VmkAndPath<>();
                key.read(buffer);
                // Hash -- HASH_SIZE_BYTES
                byte[] hashBytes = new byte[HASH_SIZE_BYTES];
                buffer.get(hashBytes);
                return new VirtualHash(hashBytes);
            }
        }
        return null;
    }

    /**
     * Save the hash for a imaginary parent node into storage
     *
     * @param mapKey The key for the virtual map who's parent we are saving hash for
     * @param parentPath The path of the parent to save
     * @param hash The hash the node that would have been at that path
     */
    public void saveParentHash(VMK mapKey, long parentPath, Hash hash) {
        synchronized (parentStore) {
            var key = new VmkAndPath<>(mapKey, parentPath);
            // if already stored and if so it is an update
            long slotLocation = parentIndex.getSlot(key);
            if (slotLocation == FCMemMapDataStore.NOT_FOUND_LOCATION) {
                // find a new slot location
                slotLocation = parentStore.getNewSlot();
                // store in index
                parentIndex.putSlot(key, slotLocation);
            }
            // write parent into slot
            ByteBuffer buffer = parentStore.accessSlot(slotLocation);
            // write VmkAndPath
            key.write(buffer);
            // Hash -- HASH_SIZE_BYTES
            buffer.put(hash.getValue());
        }
    }


    //==================================================================================================================
    // Inner Classes

    /**
     * Class for creating hashes directly from a byte[] without copying and safety checking
     */
    private static final class VirtualHash extends Hash {
        protected VirtualHash(byte[] value) {
            super(value, DigestType.SHA_384, true, false);
        }
    }

}

