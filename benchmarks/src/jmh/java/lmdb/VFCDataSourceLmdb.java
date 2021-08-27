package lmdb;

import com.swirlds.common.crypto.DigestType;
import com.swirlds.common.crypto.Hash;
import com.swirlds.virtualmap.VirtualKey;
import com.swirlds.virtualmap.VirtualValue;
import com.swirlds.virtualmap.datasource.VirtualInternalRecord;
import com.swirlds.virtualmap.datasource.VirtualLeafRecord;
import org.lmdbjava.Dbi;
import org.lmdbjava.Env;
import org.lmdbjava.EnvFlags;
import org.lmdbjava.Txn;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

import static java.nio.ByteBuffer.allocateDirect;
import static org.lmdbjava.DbiFlags.MDB_CREATE;
import static org.lmdbjava.DbiFlags.MDB_INTEGERKEY;

@SuppressWarnings("unused")
public final class VFCDataSourceLmdb<K extends VirtualKey, V extends VirtualValue> implements SequentialInsertsVFCDataSource<K, V> {
    private final static long GB = 1024*1024*1024;
    private final static long TB = GB*1024;
    private final static int HASH_SIZE = Integer.BYTES + DigestType.SHA_384.digestLength();
    /** The size in bytes for serialized key objects */
    private final int keySizeBytes;
    private final Supplier<K> keyConstructor;
    private final Supplier<V> valueConstructor;
    private final Env<ByteBuffer> env;
    private final Dbi<ByteBuffer> pathToInternalHashesMap;
    private final Dbi<ByteBuffer> leafKeyToPathMap;
    private final Dbi<ByteBuffer> leafPathToKeyHashValueMap;

    private final ThreadLocal<ByteBuffer> pathNativeOrderBytes;
    private final ThreadLocal<ByteBuffer> pathBytes;
    private final ThreadLocal<ByteBuffer> leafKeyBytes;
    private final ThreadLocal<ByteBuffer> hashData;
    private final ThreadLocal<ByteBuffer> leafKeyHashValue;

    /**
     * Construct a new VFCDataSourceImpl, try the static factory methods if you want a simpler way of creating one.
     *
     * @param keySizeBytes the size of key when serialized in bytes
     * @param keyConstructor constructor for creating keys for deserialization
     * @param valueSizeBytes the size of value when serialized in bytes
     * @param valueConstructor constructor for creating values for deserialization
     */
    public VFCDataSourceLmdb(int keySizeBytes, Supplier<K> keyConstructor, int valueSizeBytes, Supplier<V> valueConstructor,
                             Path storageDir) throws IOException {
        this.keySizeBytes = Integer.BYTES + keySizeBytes;
        this.keyConstructor = keyConstructor;
        this.valueConstructor = valueConstructor;
        // create thread local buffers
        pathNativeOrderBytes = ThreadLocal.withInitial(() -> {
            ByteBuffer buf = allocateDirect(Long.BYTES);
            buf.order(ByteOrder.nativeOrder()); // the byte order is important to use MDB_INTEGERKEY as LMDB needs keys to be in native byte order
            return buf;
        });
        pathBytes = ThreadLocal.withInitial(() -> allocateDirect(Long.BYTES));
        leafKeyBytes = ThreadLocal.withInitial(() -> allocateDirect(this.keySizeBytes));
        hashData = ThreadLocal.withInitial(() -> allocateDirect(HASH_SIZE));
        leafKeyHashValue = ThreadLocal.withInitial(() -> allocateDirect(this.keySizeBytes + HASH_SIZE + Integer.BYTES + valueSizeBytes));
        // create storage dirs
        if (!Files.exists(storageDir)) Files.createDirectories(storageDir);
        // We always need an Env. An Env owns a physical on-disk storage file. One
        // Env can store multiple databases (ie sorted maps).
        env = Env.create()
                // LMDB also needs to know how large our DB might be. Over-estimating is OK.
                .setMapSize((long)(1.9d*TB))
                // LMDB also needs to know how many DBs (Dbi) we want to store in this Env.
                .setMaxDbs(4)
                // assume max readers is max number of cores
                .setMaxReaders(Runtime.getRuntime().availableProcessors())
                // Now let's open the Env. The same path can be concurrently opened and
                // used in different processes, but do not open the same path twice in
                // the same process at the same time.
                .open(storageDir.toFile(), EnvFlags.MDB_WRITEMAP, EnvFlags.MDB_NOSYNC, EnvFlags.MDB_NOMETASYNC, EnvFlags.MDB_NORDAHEAD);
        // We need a Dbi for each DB. A Dbi roughly equates to a sorted map. The
        // MDB_CREATE flag causes the DB to be created if it doesn't already exist.
        pathToInternalHashesMap = env.openDbi("pathToHash", MDB_CREATE,MDB_INTEGERKEY);
        leafKeyToPathMap = env.openDbi("leafKeyToPath", MDB_CREATE);
        leafPathToKeyHashValueMap = env.openDbi("leafPathToKeyHashValue", MDB_CREATE,MDB_INTEGERKEY);
    }

    //==================================================================================================================
    // Public New API methods

    public void printStats() {
        try (Txn<ByteBuffer> txn = env.txnWrite()) {
            System.out.println("pathToHashMap.stat() = " + pathToInternalHashesMap.stat(txn));
            System.out.println("leafKeyToPathMap.stat() = " + leafKeyToPathMap.stat(txn));
            System.out.println("leafPathToKeyHashValueMap.stat() = " + leafPathToKeyHashValueMap.stat(txn));
        }
    }

    /**
     * Close all data stores
     */
    @Override
    public void close() {
        env.close();
    }

    /**
     * Load a leaf record by key
     *
     * @param key they to the leaf to load record for
     * @return loaded record or null if not found
     * @throws IOException If there was a problem reading record from db
     */
    @Override
    public VirtualLeafRecord<K, V> loadLeafRecord(K key) throws IOException {
        try (Txn<ByteBuffer> txn = env.txnRead()) {
            ByteBuffer pathBytes = leafKeyToPathMap.get(txn,getLeafKeyBytes(key));
            if (pathBytes == null) {
                return null;
            }
            Objects.requireNonNull(key);
            final long path = pathBytes.rewind().getLong();
            if (path == INVALID_PATH) return null;
            return loadLeafRecord(txn, path,key);
        }
    }

    /**
     * Load a leaf record by path
     *
     * @param path the path for the leaf we are loading
     * @return loaded record or null if not found
     * @throws IOException If there was a problem reading record from db
     */
    @Override
    public VirtualLeafRecord<K, V> loadLeafRecord(long path) throws IOException {
        try (Txn<ByteBuffer> txn = env.txnRead()) {
            return loadLeafRecord(txn, path,null);
        }
    }

    /**
     * Load hash for a node with given path
     *
     * @param path the path to get hash for
     * @return loaded hash or null if hash is not stored
     * @throws IOException if there was a problem loading hash
     */
    @Override
    public Hash loadLeafHash(long path) throws IOException {
        if (path < 0) throw new IllegalArgumentException("path is less than 0");
        try (Txn<ByteBuffer> txn = env.txnRead()) {
            ByteBuffer keyHashValueBuffer = leafPathToKeyHashValueMap.get(txn,getPathNativeOrderBytes(path));
            if (keyHashValueBuffer == null) {
                return null;
            }
            // deserialize
            keyHashValueBuffer.rewind();
            // deserialize hash
            keyHashValueBuffer.rewind();
            keyHashValueBuffer.position(keySizeBytes); // jump over key
            return Hash.fromByteBuffer(keyHashValueBuffer);
        }
    }

    /**
     * Load hash for a leaf node with given path
     *
     * @param path the path to get hash for
     * @return loaded hash or null if hash is not stored
     * @throws IOException if there was a problem loading hash
     */
    @Override
    public VirtualInternalRecord loadInternalRecord(long path) throws IOException {
        if (path < 0) throw new IllegalArgumentException("path is less than 0");
        try (Txn<ByteBuffer> txn = env.txnRead()) {
            ByteBuffer hashBytes = pathToInternalHashesMap.get(txn,getPathNativeOrderBytes(path));
            if (hashBytes == null) return null;
            return new VirtualInternalRecord(path,getHash(hashBytes));
        }
    }

    /**
     * Load hash for a internal node with given path
     *
     * @param path the path to get hash for
     * @return loaded hash or null if hash is not stored
     * @throws IOException if there was a problem loading hash
     */
    @Override
    public Hash loadInternalHash(long path) throws IOException {
        if (path < 0) throw new IllegalArgumentException("path is less than 0");
        try (Txn<ByteBuffer> txn = env.txnRead()) {
            ByteBuffer hashBytes = pathToInternalHashesMap.get(txn,getPathNativeOrderBytes(path));
            if (hashBytes == null) return null;
            return Hash.fromByteBuffer(hashBytes);
        }
    }

    /**
     * Save a batch of data to data store.
     *
     * @param firstLeafPath the tree path for first leaf
     * @param lastLeafPath the tree path for last leaf
     * @param internalRecords list of records for internal nodes, it is assumed this is sorted by path and each path only appears once.
     * @param virtualLeafRecords list of records for leaf nodes, it is assumed this is sorted by key and each key only appears once.
     */
    @Override
    public void saveRecords(long firstLeafPath, long lastLeafPath, List<VirtualInternalRecord> internalRecords, List<VirtualLeafRecord<K, V>> virtualLeafRecords) throws IOException {
        try (Txn<ByteBuffer> txn = env.txnWrite()) {
            for (var internalRecord: internalRecords) {
                pathToInternalHashesMap.put(txn,
                        getPathNativeOrderBytes(internalRecord.getPath()),
                        getHashBytes(internalRecord.getHash())
                );
            }
            for (var leaf: virtualLeafRecords) {
                leafKeyToPathMap.put(txn,
                        getLeafKeyBytes(leaf.getKey()),
                        getPathBytes(leaf.getPath())
                );
                final ByteBuffer keyHashValueBytes = leafKeyHashValue.get().clear();
                keyHashValueBytes.putInt(leaf.getKey().getVersion());
                leaf.getKey().serialize(keyHashValueBytes);
                Hash.toByteBuffer(leaf.getHash(),keyHashValueBytes);
                keyHashValueBytes.putInt(leaf.getValue().getVersion());
                leaf.getValue().serialize(keyHashValueBytes);
                keyHashValueBytes.flip();
                leafPathToKeyHashValueMap.put(txn,
                        getPathNativeOrderBytes(leaf.getPath()),
                        keyHashValueBytes
                );
            }
            txn.commit();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    //==================================================================================================================
    // Private methods

    /**
     * load a leaf record by path, using the provided key or if null deserializing the key.
     */
    private VirtualLeafRecord<K, V> loadLeafRecord(Txn<ByteBuffer> txn, long path, K key) throws IOException {
        ByteBuffer keyHashValueBuffer = leafPathToKeyHashValueMap.get(txn,getPathNativeOrderBytes(path));
        if (keyHashValueBuffer == null) {
            return null;
        }
        // deserialize
        keyHashValueBuffer.clear();
        // deserialize key
        if (key != null) {
            // jump past the key because we don't need to deserialize it
            keyHashValueBuffer.position(keySizeBytes);
        } else {
            final int keySerializationVersion = keyHashValueBuffer.getInt();
            key = keyConstructor.get();
            key.deserialize(keyHashValueBuffer, keySerializationVersion);
        }
        // deserialize hash
        final Hash hash = Hash.fromByteBuffer(keyHashValueBuffer);
        // deserialize value
        final int valueSerializationVersion = keyHashValueBuffer.getInt();
        final V value = valueConstructor.get();
        value.deserialize(keyHashValueBuffer, valueSerializationVersion);
        // return new VirtualLeafRecord
        return new VirtualLeafRecord<>(path, hash, key, value);
    }

    //==================================================================================================================
    // Public Old API methods


    @Override
    public V loadLeafValue(long path) {
        System.err.println("!!!!!!!!! Old API Called !!!!!!!!!!!!!");
        throw new IllegalStateException("Old API Called");
    }

    @Override
    public V loadLeafValue(K key) {
        System.err.println("!!!!!!!!! Old API Called !!!!!!!!!!!!!");
        throw new IllegalStateException("Old API Called");
    }

    @Override
    public K loadLeafKey(long path) {
        System.err.println("!!!!!!!!! Old API Called !!!!!!!!!!!!!");
        throw new IllegalStateException("Old API Called");
    }

    @Override
    public long loadLeafPath(K key) {
        System.err.println("!!!!!!!!! Old API Called !!!!!!!!!!!!!");
        throw new IllegalStateException("Old API Called");
    }

    @Override
    public void saveInternal(long path, Hash hash) {
        System.err.println("!!!!!!!!! Old API Called !!!!!!!!!!!!!");
        throw new IllegalStateException("Old API Called");
    }

    @Override
    public void updateLeaf(long oldPath, long newPath, K key, Hash hash) {
        System.err.println("!!!!!!!!! Old API Called !!!!!!!!!!!!!");
        throw new IllegalStateException("Old API Called");
    }

    @Override
    public void updateLeaf(long path, K key, V value, Hash hash) {
        System.err.println("!!!!!!!!! Old API Called !!!!!!!!!!!!!");
        throw new IllegalStateException("Old API Called");
    }

    @Override
    public void addLeaf(long path, K key, V value, Hash hash) {
        System.err.println("!!!!!!!!! Old API Called !!!!!!!!!!!!!");
        throw new IllegalStateException("Old API Called");
    }

    @Override
    public Object startTransaction() {
        System.err.println("!!!!!!!!! Old API Called !!!!!!!!!!!!!");
        throw new IllegalStateException("Old API Called");
    }

    @Override
    public void commitTransaction(Object handle) {
        System.err.println("!!!!!!!!! Old API Called !!!!!!!!!!!!!");
        throw new IllegalStateException("Old API Called");
    }

    @Override
    public void saveInternal(Object handle, long path, Hash hash) {
        System.err.println("!!!!!!!!! Old API Called !!!!!!!!!!!!!");
        throw new IllegalStateException("Old API Called");
    }

    @Override
    public void updateLeaf(Object handle, long oldPath, long newPath, K key, Hash hash) {
        System.err.println("!!!!!!!!! Old API Called !!!!!!!!!!!!!");
        throw new IllegalStateException("Old API Called");
    }

    @Override
    public void updateLeaf(Object handle, long path, K key, V value, Hash hash) {
        System.err.println("!!!!!!!!! Old API Called !!!!!!!!!!!!!");
        throw new IllegalStateException("Old API Called");
    }

    @Override
    public void addLeaf(Object handle, long path, K key, V value, Hash hash) {
        System.err.println("!!!!!!!!! Old API Called !!!!!!!!!!!!!");
        throw new IllegalStateException("Old API Called");
    }

    @Override
    public void saveInternalSequential(Object handle, long path, Hash hash) {
        System.err.println("!!!!!!!!! Old API Called !!!!!!!!!!!!!");
        throw new IllegalStateException("Old API Called");
    }

    @Override
    public void addLeafSequential(Object handle, long path, K key, V value, Hash hash) {
        System.err.println("!!!!!!!!! Old API Called !!!!!!!!!!!!!");
        throw new IllegalStateException("Old API Called");
    }

    //==================================================================================================================
    // Private Util Methods

    private K getLeafKey(ByteBuffer leafKeyBytes) throws IOException{
        leafKeyBytes.rewind();
        final int keySerializationVersion =  leafKeyBytes.getInt();
        K key = keyConstructor.get();
        key.deserialize(leafKeyBytes,keySerializationVersion);
        return key;
    }

    private ByteBuffer getLeafKeyBytes(K key) throws IOException {
        final ByteBuffer leafKey = this.leafKeyBytes.get().clear();
        leafKey.putInt(key.getVersion());
        key.serialize(leafKey);
        return leafKey.flip();
    }

    private V getLeafValue(ByteBuffer leafValueBytes) throws IOException{
        leafValueBytes.rewind();
        final int valueSerializationVersion = leafValueBytes.getInt();
        V value = valueConstructor.get();
        value.deserialize(leafValueBytes,valueSerializationVersion);
        return value;
    }

    private long getPath(ByteBuffer pathBytes) {
        return pathBytes.getLong(0);
    }

    private ByteBuffer getPathNativeOrderBytes(long path) {
        ByteBuffer pathBytes = this.pathBytes.get().clear();
        pathBytes.putLong(path);
        return pathBytes.flip();
    }
    private ByteBuffer getPathBytes(long path) {
        ByteBuffer pathBytes = this.pathBytes.get().clear();
        pathBytes.putLong(path);
        return pathBytes.flip();
    }

    private Hash getHash(ByteBuffer hashBytes) throws IOException {
        hashBytes.rewind();
        return Hash.fromByteBuffer(hashBytes);
    }

    private ByteBuffer getHashBytes(Hash hash) {
        ByteBuffer hashData = this.hashData.get();
        hashData.rewind();
        Hash.toByteBuffer(hash,hashData);
        return hashData.flip();
    }
}

