package com.hedera.services.state.merkle.virtual.persistence.mmap;

import com.hedera.services.state.merkle.virtual.Account;
import com.hedera.services.state.merkle.virtual.VirtualKey;
import com.hedera.services.state.merkle.virtual.VirtualTreePath;
import com.hedera.services.state.merkle.virtual.VirtualValue;
import com.hedera.services.state.merkle.virtual.persistence.VirtualRecord;
import com.swirlds.common.crypto.DigestType;
import com.swirlds.common.crypto.Hash;
import org.eclipse.collections.impl.map.mutable.primitive.LongLongHashMap;
import org.eclipse.collections.impl.map.mutable.primitive.LongObjectHashMap;
import org.eclipse.collections.impl.map.mutable.primitive.ObjectLongHashMap;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Data Store backend for VirtualMap it uses three MemMapDataStores for storing the leaves, parents and paths.
 *
 * It is thread safe and can be used by multiple-threads. Only one thread can use one of the sub-data stores at a time.
 */
public final class VirtualMapDataStore {
    /** 1 Mb of bytes */
    private static final int MB = 1024*1024;
    /** The size of a hash we store in bytes, TODO what happens if we change digest? */
    public static final int HASH_SIZE_BYTES = 384/Byte.SIZE;

    /** The number of bytes for a key */
    private final int keySizeBytes;
    /** The number of bytes for a data value */
    private final int dataSizeBytes;

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
    private final MemMapDataStore leafStore;
    /**
     * Store for all the tree parents
     *
     * Contains:
     * Account -- Account.BYTES
     * Path -- VirtualTreePath.BYTES
     * Hash -- HASH_SIZE_BYTES
     */
    private final MemMapDataStore parentStore;
    /**
     * Store for all the paths
     *
     * Contains:
     * Account -- Account.BYTES
     * Key -- 1 long
     * Path -- VirtualTreePath.BYTES
     */
    private final MemMapDataStore pathStore;

    /** Map of account to index for data for that account */
    private final Map<Account, Index> indexMap = new HashMap<>();
    /** fast path map for lookup for default realm and shard */
    private final LongObjectHashMap<Index> defaultRealmShardIndex = new LongObjectHashMap<>();

    /**
     * Class for indexes for each account
     */
    private static class Index {
        public final LongLongHashMap parentIndex = new LongLongHashMap();
        public final ObjectLongHashMap<VirtualKey> leafIndex = new ObjectLongHashMap<>();
        public final LongLongHashMap leafPathIndex = new LongLongHashMap();
        public final LongLongHashMap pathIndex = new LongLongHashMap();
    }

    /**
     * Create new VirtualMapDataStore
     *
     * @param storageDirectory The path of the directory to store storage files
     * @param keySizeBytes The number of bytes for a key
     * @param dataSizeBytes The number of bytes for a data value
     */
    public VirtualMapDataStore(Path storageDirectory, int keySizeBytes, int dataSizeBytes) {
        this(storageDirectory,keySizeBytes,dataSizeBytes,100);
    }

    /**
     * Create new VirtualMapDataStore
     *
     * @param storageDirectory The path of the directory to store storage files
     * @param keySizeBytes The number of bytes for a key
     * @param dataSizeBytes The number of bytes for a data value
     * @param dataFileSizeInMb The size of each mem mapped storage file in MB
     */
    public VirtualMapDataStore(Path storageDirectory, int keySizeBytes, int dataSizeBytes, int dataFileSizeInMb) {
        /* The path of the directory to store storage files */
        this.keySizeBytes = keySizeBytes;
        this.dataSizeBytes = dataSizeBytes;
        int leafStoreSlotSize = Account.BYTES + keySizeBytes + VirtualTreePath.BYTES + dataSizeBytes + HASH_SIZE_BYTES;
        int parentStoreSlotSize = Account.BYTES + VirtualTreePath.BYTES + HASH_SIZE_BYTES;
        leafStore = new MemMapDataStore(leafStoreSlotSize,dataFileSizeInMb*MB,storageDirectory.resolve("leaves"),"leaves_","dat");
        parentStore = new MemMapDataStore(parentStoreSlotSize,dataFileSizeInMb*MB,storageDirectory.resolve("parents"),"parents_","dat");
        pathStore = new MemMapDataStore(Account.BYTES + Long.BYTES + VirtualTreePath.BYTES,dataFileSizeInMb*MB,storageDirectory.resolve("paths"),"paths_","dat");
    }

    /**
     * Open all storage files and read the indexes.
     */
    public void open(){
        // This could be faster if we open each in a thread.
        // open leaf store
        synchronized (leafStore) {
            leafStore.open((location, fileAtSlot) -> {
                try {
                    final Account account = new Account(fileAtSlot.readLong(), fileAtSlot.readLong(), fileAtSlot.readLong());
                    ObjectLongHashMap<VirtualKey> indexMap = index(account, true).leafIndex;
                    byte[] keyBytes = new byte[keySizeBytes];
                    fileAtSlot.read(keyBytes);
                    indexMap.put(
                            new VirtualKey(keyBytes),
                            location);

                    LongLongHashMap pathIndexMap = index(account, true).leafPathIndex;
                    pathIndexMap.put(
                            fileAtSlot.readLong(),
                            location);
                } catch (IOException e) {
                    e.printStackTrace(); // TODO something better here, this should only happen if our files are corrupt
                }
            });
        }
        // open parents store
        synchronized (parentStore) {
            parentStore.open((location, fileAtSlot) -> {
                try {
                    final Account account = new Account(fileAtSlot.readLong(), fileAtSlot.readLong(), fileAtSlot.readLong());
                    final long path = fileAtSlot.readLong();
                    LongLongHashMap indexMap = index(account, true).parentIndex;
                    indexMap.put(path, location);
                } catch (IOException e) {
                    e.printStackTrace(); // TODO something better here, this should only happen if our files are corrupt
                }
            });
        }
        // open path store
        synchronized (pathStore) {
            pathStore.open((location, fileAtSlot) -> {
                try {
                    final Account account = new Account(fileAtSlot.readLong(), fileAtSlot.readLong(), fileAtSlot.readLong());
                    final long key = fileAtSlot.readLong();
                    LongLongHashMap indexMap = index(account, true).pathIndex;
                    indexMap.put(key, location);
                } catch (IOException e) {
                    e.printStackTrace(); // TODO something better here, this should only happen if our files are corrupt
                }
            });
        }
    }

    /**
     * Close all storage files
     */
    public void close(){
        // close leaf store
        synchronized (leafStore) {
            leafStore.close();
        }
        // close parent store
        synchronized (parentStore) {
            parentStore.close();
        }
        // close path store
        synchronized (pathStore) {
            pathStore.close();
        }
        // clean up in memory indexes
        indexMap.clear();
        defaultRealmShardIndex.clear();
    }

    /**
     * Get the index for an account
     *
     * @param account The account to get index for
     * @param createIfNotExist if true and the index doesn't exist yet a new one is created
     * @return index for account
     */
    private Index index(Account account, boolean createIfNotExist) {
        // This is the only place in the code that access indexMap and defaultRealmShardIndex so we synchronize here
        synchronized (indexMap) {
            if (account.isDefaultShardAndRealm()) {
                if (createIfNotExist) {
                    return defaultRealmShardIndex.getIfAbsentPut(account.accountNum(), Index::new);
                } else {
                    return defaultRealmShardIndex.get(account.accountNum());
                }
            } else {
                if (createIfNotExist) {
                    return indexMap.computeIfAbsent(account, a -> new Index());
                } else {
                    return indexMap.get(account);
                }
            }
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
        // sync path store
        synchronized (pathStore) {
            pathStore.sync();
        }
    }

    /**
     * Delete a stored parent from storage, if it is stored.
     *
     * @param account The account that the parent belongs to
     * @param parentPath The path of the parent to delete
     */
    public void deleteParent(Account account, long parentPath) {
        synchronized (parentStore) {
            long slotLocation = findParent(account, parentPath);
            if (slotLocation != MemMapDataStore.NOT_FOUND_LOCATION) parentStore.deleteSlot(slotLocation);
        }
    }

    /**
     * Delete a stored leaf from storage, if it is stored.
     *
     * @param account The account that the leaf belongs to
     * @param key The key for the leaf to delete
     */
    public void deleteLeaf(Account account, VirtualKey key){
        synchronized (leafStore) {
            long slotLocation = findLeaf(account, key);
            if (slotLocation != MemMapDataStore.NOT_FOUND_LOCATION) leafStore.deleteSlot(slotLocation);
        }
    }


    /**
     * Delete a stored path from storage, if it is stored.
     *
     * @param account The account that the leaf belongs to
     * @param path The key for the path to delete
     */
    public void deletePath(Account account, long path){
        synchronized (pathStore) {
            long slotLocation = findPath(account, path);
            if (slotLocation != MemMapDataStore.NOT_FOUND_LOCATION) pathStore.deleteSlot(slotLocation);
        }
    }

    /**
     * Get the number of leaves for a given account
     *
     * @param account The account to count leaves for
     * @return 0 if the account doesn't exist otherwise the number of leaves stored for the account
     */
    public int leafCount(Account account) {
        int count = 0;
        synchronized (leafStore) {
            Index index = index(account, false);
            if (index != null) {
                count = index.leafIndex.size();
            }
        }
        return count;
    }

    /**
     * Load a tree parent node from storage
     *
     * @param account The account that the parent belongs to
     * @param path The path of the parent to find and load
     * @return a loaded VirtualTreeInternal with path and hash set or null if not found
     */
    public Hash loadParentHash(Account account, long path) {
        synchronized (parentStore) {
            long slotLocation = findParent(account, path);
            if (slotLocation != MemMapDataStore.NOT_FOUND_LOCATION) {
                ByteBuffer buffer = parentStore.accessSlot(slotLocation);
                // Account -- Account.BYTES
                // Path -- VirtualTreePath.BYTES
                buffer.position(buffer.position() + Account.BYTES + VirtualTreePath.BYTES); // jump over
                // Hash -- HASH_SIZE_BYTES
                byte[] hashBytes = new byte[HASH_SIZE_BYTES];
                buffer.get(hashBytes);
                return new VirtualHash(hashBytes);
            }
        }
        return null;
    }

    /**
     * Load a leaf node record from storage given the key for it
     *
     * @param account The account that the leaf belongs to
     * @param key The key of the leaf to find
     * @return a loaded VirtualRecord or null if not found
     */
    public VirtualRecord loadLeaf(Account account, VirtualKey key){
        synchronized (leafStore) {
            return loadLeaf(findLeaf(account, key));
        }
    }

    /**
     * Load a leaf node record from storage given a path to it
     *
     * @param account The account the leaf belongs to
     * @param path The path to the leaf
     * @return a loaded VirtualRecord or null if not found
     */
    public VirtualRecord loadLeaf(Account account, long path) {
        synchronized (leafStore) {
            return loadLeaf(findLeaf(account, path));
        }
    }

    /**
     * Load a leaf from file, this must be called while holding leafReadWriteLock.readLock
     *
     * @param slotLocation location of leaf in file
     * @return a loaded VirtualRecord or null if not found
     */
    private VirtualRecord loadLeaf(long slotLocation) {
        if (slotLocation != MemMapDataStore.NOT_FOUND_LOCATION) {
            ByteBuffer buffer = leafStore.accessSlot(slotLocation);
            // Account -- Account.BYTES
            buffer.position(buffer.position() + Account.BYTES); // jump over
            // Key -- keySizeBytes
            byte[] keyBytes = new byte[keySizeBytes];
            buffer.get(keyBytes);
            // Path -- VirtualTreePath.BYTES
            long path = buffer.getLong(); // skip it!
            // Value -- dataSizeBytes
            byte[] valueBytes = new byte[dataSizeBytes];
            buffer.get(valueBytes);
            // Hash
            byte[] hashBytes = new byte[HASH_SIZE_BYTES];
            buffer.get(hashBytes);
            return new VirtualRecord(new VirtualHash(hashBytes), path, new VirtualKey(keyBytes), new VirtualValue(valueBytes));
        }
        return null;
    }

    /**
     * Directly load the value of a leaf
     *
     * @param account The account the leaf belongs to
     * @param key The key for the data for the leaf
     * @return value of leaf
     */
    public VirtualValue loadLeafValue(Account account, VirtualKey key) {
        VirtualValue virtualValue = null;
        synchronized (leafStore) {
            long slotLocation = findLeaf(account, key);
            if (slotLocation != MemMapDataStore.NOT_FOUND_LOCATION) {
                ByteBuffer buffer = leafStore.accessSlot(slotLocation);
                // Account -- Account.BYTES
                // Key -- keySizeBytes
                // Path -- VirtualTreePath.BYTES
                buffer.position(buffer.position() + Account.BYTES + keySizeBytes + VirtualTreePath.BYTES); // jump over
                // Value -- dataSizeBytes
                byte[] valueBytes = new byte[dataSizeBytes];
                buffer.get(valueBytes);
                virtualValue = new VirtualValue(valueBytes);
            }
        }
        return virtualValue;
    }

    /**
     * Save the hash for a imaginary parent node into storage
     *
     * @param account The account that the parent belongs to
     * @param parentPath The path of the parent to save
     * @param hash The hash the node that would have been at that path
     */
    public void saveParentHash(Account account, long parentPath, Hash hash) {
        synchronized (parentStore) {
            // if already stored and if so it is an update
            long slotLocation = findParent(account, parentPath);
            if (slotLocation == MemMapDataStore.NOT_FOUND_LOCATION) {
                // find a new slot location
                slotLocation = parentStore.getNewSlot();
                // store in index
                index(account, true).parentIndex.put(parentPath, slotLocation);
            }
            // write parent into slot
            ByteBuffer buffer = parentStore.accessSlot(slotLocation);
            // Account -- Account.BYTES
            buffer.putLong(account.shardNum());
            buffer.putLong(account.realmNum());
            buffer.putLong(account.accountNum());
            // Path -- VirtualTreePath.BYTES
            buffer.putLong(parentPath);
            // Hash -- HASH_SIZE_BYTES
            buffer.put(hash.getValue());
        }
    }

    /**
     * Save a VirtualTreeLeaf to storage
     *
     * @param account The account that the leaf belongs to
     * @param leaf The leaf to store
     */
    public void saveLeaf(Account account, VirtualRecord leaf) {
        synchronized (leafStore) {
            // if already stored and if so it is an update
            long slotLocation = findLeaf(account, leaf.getKey());
            if (slotLocation == MemMapDataStore.NOT_FOUND_LOCATION) {
                // find a new slot location
                slotLocation = leafStore.getNewSlot();
                // store in indexes
                index(account, true).leafIndex.put(leaf.getKey(), slotLocation);
                index(account, true).leafPathIndex.put(leaf.getPath(), slotLocation);
            }
            // write leaf into slot
            ByteBuffer buffer = leafStore.accessSlot(slotLocation);
            // Account -- Account.BYTES
            buffer.putLong(account.shardNum());
            buffer.putLong(account.realmNum());
            buffer.putLong(account.accountNum());
            // Key -- keySizeBytes
            leaf.getKey().writeToByteBuffer(buffer);
            // Path -- VirtualTreePath.BYTES
            buffer.putLong(leaf.getPath());
            // Value -- dataSizeBytes
            leaf.getValue().writeToByteBuffer(buffer);
            // Hash -- dataSizeBytes
            buffer.put(leaf.getHash().getValue());
        }
    }

    /**
     * Write a tree path to storage
     *
     * @param account The account the path belongs to
     * @param key The long key for the path
     * @param path The path to write
     */
    public void savePath(Account account, long key, long path) {
        synchronized (pathStore) {
            // if already stored and if so it is an update
            long slotLocation = findPath(account, key);
            if (slotLocation == MemMapDataStore.NOT_FOUND_LOCATION) {
                // find a new slot location
                slotLocation = pathStore.getNewSlot();
                // store in index
                index(account, true).pathIndex.put(key, slotLocation);

            }
            // write path into slot
            ByteBuffer buffer = pathStore.accessSlot(slotLocation);
            // Account -- Account.BYTES
            buffer.putLong(account.shardNum());
            buffer.putLong(account.realmNum());
            buffer.putLong(account.accountNum());
            // Key -- 1 long
            buffer.putLong(key);
            // Path -- VirtualTreePath.BYTES
            buffer.putLong(path);
        }
    }

    /**
     * Load a Path from store
     *
     * @param account The account the path belongs to
     * @param key The long key for the path
     * @return the Path if it was found in store or null
     */
    public long loadPath(Account account, long key) {
        long path = VirtualTreePath.INVALID_PATH;
        synchronized (pathStore) {
            long slotLocation = findPath(account, key);
            if (slotLocation != MemMapDataStore.NOT_FOUND_LOCATION) {
                // read path from slot
                ByteBuffer buffer = pathStore.accessSlot(slotLocation);
                // Account -- Account.BYTES
                // Key -- 1 long
                buffer.position(buffer.position() + Account.BYTES + Long.BYTES); // jump over
                // Path -- VirtualTreePath.BYTES
                path = buffer.getLong();
            }
        }
        return path;
    }

    /**
     * Find the slot location of a parent node
     *
     * @param account The account that the parent belongs to
     * @param path The path of the parent to find
     * @return slot location of parent if it is stored or MemMapDataStore.NOT_FOUND_LOCATION if not found
     */
    private long findParent(Account account, long path) {
        Index index = index(account,false);
        if (index != null) return index.parentIndex.getIfAbsent(path, MemMapDataStore.NOT_FOUND_LOCATION);
        return MemMapDataStore.NOT_FOUND_LOCATION;
    }

    /**
     * Find the slot location of a leaf node
     *
     * @param account The account that the leaf belongs to
     * @param key The key of the leaf to find
     * @return slot location of leaf if it is stored or MemMapDataStore.NOT_FOUND_LOCATION if not found
     */
    private long findLeaf(Account account, VirtualKey key) {
        Index index = index(account, false);
        if (index != null) return index.leafIndex.getIfAbsent(key,MemMapDataStore.NOT_FOUND_LOCATION);
        return MemMapDataStore.NOT_FOUND_LOCATION;
    }

    /**
     * Find the slot location of a leaf node
     *
     * @param account The account that the leaf belongs to
     * @param path The path to the leaf to find
     * @return slot location of leaf if it is stored or MemMapDataStore.NOT_FOUND_LOCATION if not found
     */
    private long findLeaf(Account account, long path) {
        Index index = index(account, false);
        if (index != null) return index.leafPathIndex.getIfAbsent(path,MemMapDataStore.NOT_FOUND_LOCATION);
        return MemMapDataStore.NOT_FOUND_LOCATION;
    }

    /**
     * Find the slot location of a path
     *
     * @param account The account that the path belongs to
     * @param key The path of the path to find
     * @return slot location of path if it is stored or MemMapDataStore.NOT_FOUND_LOCATION if not found
     */
    private long findPath(Account account, long key) {
        Index index = index(account,false);
        if (index != null) return index.pathIndex.getIfAbsent(key, MemMapDataStore.NOT_FOUND_LOCATION);
        return MemMapDataStore.NOT_FOUND_LOCATION;
    }

    private static final class VirtualHash extends Hash {
        protected VirtualHash(byte[] value) {
            super(value, DigestType.SHA_384, true, false);
        }
    }
}

