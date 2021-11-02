package lmdb;

import com.swirlds.common.crypto.DigestType;
import com.swirlds.common.crypto.Hash;
import com.swirlds.jasperdb.utilities.HashTools;
import com.swirlds.virtualmap.VirtualKey;
import com.swirlds.virtualmap.VirtualValue;
import com.swirlds.virtualmap.datasource.VirtualDataSource;
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
import java.util.Objects;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static java.nio.ByteBuffer.allocateDirect;
import static org.lmdbjava.DbiFlags.MDB_CREATE;
import static org.lmdbjava.DbiFlags.MDB_INTEGERKEY;

public final class VFCDataSourceLmdb<K extends VirtualKey, V extends VirtualValue> implements VirtualDataSource<K, V> {
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
     */
    @Override
    public Hash loadLeafHash(long path) {
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
            throw new RuntimeException("Needs to be reimplemented");
//            return Hash.fromByteBuffer(keyHashValueBuffer);
        }
    }

    @Override
    public void snapshot(Path path) throws IOException {

    }

    /**
     * Load hash for a leaf node with given path
     *
     * @param path the path to get hash for
     * @return loaded hash or null if hash is not stored
     */
    @Override
    public VirtualInternalRecord loadInternalRecord(long path) {
        if (path < 0) throw new IllegalArgumentException("path is less than 0");
        try (Txn<ByteBuffer> txn = env.txnRead()) {
            ByteBuffer hashBytes = pathToInternalHashesMap.get(txn,getPathNativeOrderBytes(path));
            if (hashBytes == null) return null;
            return new VirtualInternalRecord(path,getHash(hashBytes));
        }
    }

    /**
     * Save a bulk set of changes to internal nodes and leaves.
     *
     * @param firstLeafPath   the new path of first leaf node
     * @param lastLeafPath    the new path of last leaf node
     * @param internalRecords stream of new internal nodes and updated internal nodes
     * @param leafRecords     stream of new leaf nodes and updated leaf nodes
     */
    @Override
    public void saveRecords(
            long firstLeafPath,
            long lastLeafPath,
            Stream<VirtualInternalRecord> internalRecords,
            Stream<VirtualLeafRecord<K, V>> leafRecords,
            Stream<VirtualLeafRecord<K, V>> deletedLeafRecords) {
        try (Txn<ByteBuffer> txn = env.txnWrite()) {
            internalRecords.forEachOrdered(internalRecord -> pathToInternalHashesMap.put(txn,
                    getPathNativeOrderBytes(internalRecord.getPath()),
                    getHashBytes(internalRecord.getHash())
            ));
            leafRecords.forEachOrdered(leaf -> {
                try {
                    leafKeyToPathMap.put(txn,
                            getLeafKeyBytes(leaf.getKey()),
                            getPathBytes(leaf.getPath())
                    );
                    final ByteBuffer keyHashValueBytes = leafKeyHashValue.get().clear();
                    keyHashValueBytes.putInt(leaf.getKey().getVersion());
                    leaf.getKey().serialize(keyHashValueBytes);
                    HashTools.hashToByteBuffer(leaf.getHash(), keyHashValueBytes);
                    keyHashValueBytes.putInt(leaf.getValue().getVersion());
                    leaf.getValue().serialize(keyHashValueBytes);
                    keyHashValueBytes.flip();
                    leafPathToKeyHashValueMap.put(txn,
                            getPathNativeOrderBytes(leaf.getPath()),
                            keyHashValueBytes
                    );
                    throw new RuntimeException("Needs to be reimplemented");
                } catch (IOException e) {
                    e.printStackTrace();
                    throw new RuntimeException(e);
                }
            });
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
        final Hash hash = new Hash(DigestType.SHA_384);
        keyHashValueBuffer.get(hash.getValue());
        // deserialize value
        final int valueSerializationVersion = keyHashValueBuffer.getInt();
        final V value = valueConstructor.get();
        value.deserialize(keyHashValueBuffer, valueSerializationVersion);
        // return new VirtualLeafRecord
        return new VirtualLeafRecord<>(path, hash, key, value);
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

    private Hash getHash(ByteBuffer hashBytes) {
        hashBytes.rewind();
        Hash hash = new Hash(DigestType.SHA_384);
        hashBytes.get(hash.getValue());
        return hash;
    }

    private ByteBuffer getHashBytes(Hash hash) {
        ByteBuffer hashData = this.hashData.get();
        hashData.put(hash.getValue());
        return hashData.flip();
    }
}

