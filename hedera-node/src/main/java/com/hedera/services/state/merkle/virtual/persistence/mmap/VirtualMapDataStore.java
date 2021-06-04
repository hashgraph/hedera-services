package com.hedera.services.state.merkle.virtual.persistence.mmap;

import com.hedera.services.state.merkle.virtual.Account;
import com.hedera.services.state.merkle.virtual.VirtualKey;
import com.hedera.services.state.merkle.virtual.VirtualValue;
import com.hedera.services.state.merkle.virtual.persistence.VirtualDataSource;
import com.hedera.services.state.merkle.virtual.tree.VirtualTreeInternal;
import com.hedera.services.state.merkle.virtual.tree.VirtualTreeLeaf;
import com.hedera.services.state.merkle.virtual.tree.VirtualTreeNode;
import com.hedera.services.state.merkle.virtual.tree.VirtualTreePath;
import com.swirlds.common.crypto.Hash;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * TODO we assume data size is less than hash length and use padded data value as the hash for leaf node
 */
@SuppressWarnings({"unused", "DuplicatedCode"})
public class VirtualMapDataStore {
    /** The size of a hash we store in bytes, TODO what happens if we change digest? */
    private static final int HASH_SIZE_BYTES = 384/Byte.SIZE;

    /**
     * Store for all the tree leaves
     *
     * Contains:
     * Account -- Account.BYTES
     * Key -- keySizeBytes
     * Path -- VirtualTreePath.BYTES
     * Value -- dataSizeBytes
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
     * Key -- 1 byte
     * Path -- VirtualTreePath.BYTES
     */
    private final MemMapDataStore pathStore;

    private final Map<Account, Map<VirtualTreePath, SlotLocation>> parentIndex = new HashMap<>();
    private final Map<Account, Map<VirtualKey, SlotLocation>> leafIndex = new HashMap<>();
    private final Map<Account, Map<Byte, SlotLocation>> pathIndex = new HashMap<>();

    private final int keySizeBytes;
    private final int dataSizeBytes;

    /**
     * Create new VirtualMapDataStore
     *
     * @param storageDirectory The path of the directory to store storage files
     * @param keySizeBytes The number of bytes for a key
     * @param dataSizeBytes The number of bytes for a data value TODO we assume data size is less than hash length and use padded data value as the hash for leaf node
     */
    public VirtualMapDataStore(Path storageDirectory, int keySizeBytes, int dataSizeBytes) {
        /* The path of the directory to store storage files */
        this.keySizeBytes = keySizeBytes;
        this.dataSizeBytes = dataSizeBytes;
        int leafStoreSlotSize = Account.BYTES + keySizeBytes + VirtualTreePath.BYTES + dataSizeBytes;
        int parentStoreSlotSize = Account.BYTES + VirtualTreePath.BYTES + HASH_SIZE_BYTES;
        leafStore = new MemMapDataStore(leafStoreSlotSize,100*MemMapDataStore.MB,storageDirectory.resolve("leaves"),"leaves_","dat");
        parentStore = new MemMapDataStore(parentStoreSlotSize,100*MemMapDataStore.MB,storageDirectory.resolve("parents"),"parents_","dat");
        pathStore = new MemMapDataStore(VirtualTreePath.BYTES,100*MemMapDataStore.MB,storageDirectory.resolve("paths"),"paths_","dat");
    }

    /**
     * Open all storage files and read the indexes.
     */
    public void open(){
        leafStore.open((fileIndex, slotIndex, fileAtSlot) -> {
            try {
                final Account account = new Account(fileAtSlot.readLong(), fileAtSlot.readLong(), fileAtSlot.readLong());
                Map<VirtualKey, SlotLocation> indexMap = leafIndex.computeIfAbsent(account, k -> new HashMap<>());
                byte[] keyBytes = new byte[keySizeBytes];
                fileAtSlot.read(keyBytes);
                indexMap.put(
                        new VirtualKey(keyBytes),
                        new SlotLocation(fileIndex,slotIndex));
            } catch (IOException e) {
                e.printStackTrace(); // TODO something better here, this should only happen if our files are corrupt
            }
        });
        parentStore.open((fileIndex, slotIndex, fileAtSlot) -> {
            try {
                final Account account = new Account(fileAtSlot.readLong(), fileAtSlot.readLong(), fileAtSlot.readLong());
                final VirtualTreePath path = new VirtualTreePath(fileAtSlot.readByte(), fileAtSlot.readLong());
                Map<VirtualTreePath, SlotLocation> indexMap = parentIndex.computeIfAbsent(account, k -> new HashMap<>());
                indexMap.put(path, new SlotLocation(fileIndex,slotIndex));
            } catch (IOException e) {
                e.printStackTrace(); // TODO something better here, this should only happen if our files are corrupt
            }
        });
        pathStore.open((fileIndex, slotIndex, fileAtSlot) -> {
            try {
                final Account account = new Account(fileAtSlot.readLong(), fileAtSlot.readLong(), fileAtSlot.readLong());
                final byte key = fileAtSlot.readByte();
                Map<Byte, SlotLocation> indexMap = pathIndex.computeIfAbsent(account, k -> new HashMap<>());
                indexMap.put(key, new SlotLocation(fileIndex,slotIndex));
            } catch (IOException e) {
                e.printStackTrace(); // TODO something better here, this should only happen if our files are corrupt
            }
        });
    }

    /**
     * Close all storage files
     */
    public void close(){
        leafStore.close();
        leafIndex.clear();
        parentStore.close();
        parentIndex.clear();
        pathStore.close();
        pathIndex.clear();
    }

    /**
     * Make sure all data is flushed to disk. This is an expensive operation. The OS will write all data to disk in the
     * background, so only call this if you need to insure it is written synchronously.
     */
    public void sync(){
        leafStore.sync();
        parentStore.sync();
        pathStore.sync();
    }

    /**
     * Delete a stored parent from storage, if it is stored.
     *
     * @param account The account that the parent belongs to
     * @param parent The parent to delete
     */
    public void delete(Account account, VirtualTreeNode parent){
        SlotLocation slotLocation = findParent(account,parent.getPath());
        if (slotLocation != null) parentStore.deleteSlot(slotLocation.fileIndex(), slotLocation.slotIndex());
    }

    /**
     * Delete a stored leaf from storage, if it is stored.
     *
     * @param account The account that the leaf belongs to
     * @param leaf The leaf to delete
     */
    public void delete(Account account, VirtualTreeLeaf leaf){
        SlotLocation slotLocation = findLeaf(account,leaf.getKey());
        if (slotLocation != null) leafStore.deleteSlot(slotLocation.fileIndex(), slotLocation.slotIndex());
    }

    /**
     * Load a tree parent node from storage
     *
     * @param account The account that the parent belongs to
     * @param path The path of the parent to find and load
     * @return a loaded VirtualTreeInternal with path and hash set or null if not found
     */
    public VirtualTreeInternal loadParent(Account account, VirtualTreePath path){
        SlotLocation slotLocation = findParent(account,path);
        if (slotLocation != null) {
            ByteBuffer buffer = parentStore.accessSlot(slotLocation.fileIndex(), slotLocation.slotIndex());
            // Account -- Account.BYTES
            // Path -- VirtualTreePath.BYTES
            buffer.position(buffer.position() + Account.BYTES + VirtualTreePath.BYTES); // jump over
            // Hash -- HASH_SIZE_BYTES
            byte[] hashBytes = new byte[HASH_SIZE_BYTES];
            buffer.get(hashBytes);
            return new VirtualTreeInternal(new Hash(hashBytes), path);
        }
        return null;
    }

    /**
     * Load a leaf node from storage
     *
     * @param account The account that the leaf belongs to
     * @param key The key of the leaf to find
     * @return a loaded VirtualTreeLeaf or null if not found
     */
    public VirtualTreeLeaf loadLeaf(Account account, VirtualKey key){
        SlotLocation slotLocation = findLeaf(account,key);
        if (slotLocation != null) {
            ByteBuffer buffer = leafStore.accessSlot(slotLocation.fileIndex(), slotLocation.slotIndex());
            // Account -- Account.BYTES
            buffer.position(buffer.position() + Account.BYTES); // jump over
            // Key -- keySizeBytes
            byte[] keyBytes = new byte[keySizeBytes];
            buffer.get(keyBytes);
            // Path -- VirtualTreePath.BYTES
            VirtualTreePath path = new VirtualTreePath(buffer.get(), buffer.getLong());
            // Value -- dataSizeBytes
            byte[] valueBytes = new byte[dataSizeBytes];
            buffer.get(valueBytes);
            // Hash TODO we assume we can use data value as hash here
            byte[] hashBytes = new byte[HASH_SIZE_BYTES];
            System.arraycopy(valueBytes,0,hashBytes,0,valueBytes.length);
            return new VirtualTreeLeaf(new Hash(hashBytes), path, new VirtualKey(keyBytes), new VirtualValue(valueBytes));
        }
        return null;
    }

    public VirtualValue get(Account account, VirtualKey key) {
        SlotLocation slotLocation = findLeaf(account,key);
        if (slotLocation != null) {
            ByteBuffer buffer = leafStore.accessSlot(slotLocation.fileIndex(), slotLocation.slotIndex());
            // Account -- Account.BYTES
            buffer.position(buffer.position() + Account.BYTES + keySizeBytes + VirtualTreePath.BYTES); // jump over
            // Value -- dataSizeBytes
            byte[] valueBytes = new byte[dataSizeBytes];
            buffer.get(valueBytes);
            return new VirtualValue(valueBytes);
        }
        return null;
    }

    /**
     * Save a VirtualTreeInternal parent node into storage
     *
     * @param account The account that the parent belongs to
     * @param parent The parent node to save
     */
    public void save(Account account, VirtualTreeInternal parent) {
        // if already stored and if so it is an update
        SlotLocation slotLocation = findParent(account,parent.getPath());
        if (slotLocation == null) {
            // find a new slot location
            slotLocation = parentStore.getNewSlot();
        }
        // write parent into slot
        ByteBuffer buffer = parentStore.accessSlot(slotLocation.fileIndex(), slotLocation.slotIndex());
        // Account -- Account.BYTES
        buffer.putLong(account.shardNum());
        buffer.putLong(account.realmNum());
        buffer.putLong(account.accountNum());
        // Path -- VirtualTreePath.BYTES
        buffer.put(parent.getPath().rank);
        buffer.putLong(parent.getPath().path);
        // Hash -- HASH_SIZE_BYTES
        buffer.put(parent.hash().getValue());
    }

    /**
     * Save a VirtualTreeLeaf to storage
     *
     * @param account The account that the leaf belongs to
     * @param leaf The leaf to store
     */
    public void save(Account account, VirtualTreeLeaf leaf) {
        // if already stored and if so it is an update
        SlotLocation slotLocation = findLeaf(account,leaf.getKey());
        if (slotLocation == null) {
            // find a new slot location
            slotLocation = leafStore.getNewSlot();
        }
        // write leaf into slot
        ByteBuffer buffer = leafStore.accessSlot(slotLocation.fileIndex(), slotLocation.slotIndex());
        // Account -- Account.BYTES
        buffer.putLong(account.shardNum());
        buffer.putLong(account.realmNum());
        buffer.putLong(account.accountNum());
        // Key -- keySizeBytes
        leaf.getKey().writeToByteBuffer(buffer);
        // Path -- VirtualTreePath.BYTES
        buffer.put(leaf.getPath().rank);
        buffer.putLong(leaf.getPath().path);
        // Value -- dataSizeBytes
        leaf.getData().writeToByteBuffer(buffer);
    }

    /**
     * Write a tree path to storage
     *
     * @param account The account the path belongs to
     * @param key The byte key for the path
     * @param path The path to write
     */
    public void save(Account account, byte key, VirtualTreePath path) {
        // if already stored and if so it is an update
        SlotLocation slotLocation = findPath(account, key);
        if (slotLocation == null) {
            // find a new slot location
            slotLocation = pathStore.getNewSlot();
        }
        // write path into slot
        ByteBuffer buffer = pathStore.accessSlot(slotLocation.fileIndex(), slotLocation.slotIndex());
        // Account -- Account.BYTES
        buffer.putLong(account.shardNum());
        buffer.putLong(account.realmNum());
        buffer.putLong(account.accountNum());
        // Key -- 1 byte
        buffer.put(key);
        // Path -- VirtualTreePath.BYTES
        buffer.put(path.rank);
        buffer.putLong(path.path);
    }

    /**
     * Load a Path from store
     *
     * @param account The account the path belongs to
     * @param key The byte key for the path
     * @return the Path if it was found in store or null
     */
    public VirtualTreePath load(Account account, byte key){
        SlotLocation slotLocation = findPath(account,key);
        if (slotLocation != null) {
            // read path from slot
            ByteBuffer buffer = pathStore.accessSlot(slotLocation.fileIndex(), slotLocation.slotIndex());
            // Account -- Account.BYTES
            // Key -- 1 byte
            buffer.position(buffer.position() + Account.BYTES + 1); // jump over
            // Path -- VirtualTreePath.BYTES
            return new VirtualTreePath(buffer.get(), buffer.getLong());
        } else {
            return null;
        }
    }

    /**
     * Find the slot location of a parent node
     *
     * @param account The account that the parent belongs to
     * @param path The path of the parent to find
     * @return slot location of parent if it is stored or null if not found
     */
    private SlotLocation findParent(Account account, VirtualTreePath path) {
        Map<VirtualTreePath, SlotLocation> indexMap = parentIndex.get(account);
        if (indexMap != null) return indexMap.get(path);
        return null;
    }

    /**
     * Find the slot location of a leaf node
     *
     * @param account The account that the leaf belongs to
     * @param key The key of the leaf to find
     * @return slot location of leaf if it is stored or null if not found
     */
    private SlotLocation findLeaf(Account account, VirtualKey key) {
        Map<VirtualKey, SlotLocation> indexMap = leafIndex.get(account);
        if(indexMap != null) return indexMap.get(key);
        return null;
    }

    /**
     * Find the slot location of a path
     *
     * @param account The account that the path belongs to
     * @param key The path of the path to find
     * @return slot location of path if it is stored or null if not found
     */
    private SlotLocation findPath(Account account, byte key) {
        Map<Byte, SlotLocation> indexMap = pathIndex.get(account);
        if (indexMap != null) return indexMap.get(key);
        return null;
    }

}
