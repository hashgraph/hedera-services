package rockdb;

import com.swirlds.common.crypto.DigestType;
import com.swirlds.common.crypto.Hash;
import com.swirlds.fcmap.VFCDataSource;
import com.swirlds.fcmap.VKey;
import com.swirlds.fcmap.VValue;
import org.eclipse.collections.impl.set.mutable.primitive.LongHashSet;
import org.lmdbjava.*;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Supplier;

import static java.nio.ByteBuffer.allocateDirect;
import static org.lmdbjava.DbiFlags.MDB_CREATE;
import static org.lmdbjava.DbiFlags.MDB_INTEGERKEY;

@SuppressWarnings({"unchecked", "unused", "jol"})
public final class VFCDataSourceLmdbTwoIndexes<K extends VKey, V extends VValue> implements SequentialInsertsVFCDataSource<K, V> {
    private final static int HASH_SIZE = Long.BYTES+ DigestType.SHA_384.digestLength();
    private final Supplier<K> keyConstructor;
    private final Supplier<V> valueConstructor;
    private final int pathSizeBytes = Long.BYTES;
    private final int keySizeBytes;
    private final int keyAndHashSizeBytes;
    private final int pathAndValueSizeBytes;

    private final Env<ByteBuffer> env;
    private final Dbi<ByteBuffer> pathToKeyAndHashMap;
    private final Dbi<ByteBuffer> keyToPathAndValueMap;

    private final ThreadLocal<ByteBuffer> pathBytes;
    private final ThreadLocal<ByteBuffer> leafKey;
    private final ThreadLocal<ByteBuffer> keyAndHashBytes;
    private final ThreadLocal<ByteBuffer> pathAndValueBytes;

    /**
     * Construct a new VFCDataSourceImpl, try the static factory methods if you want a simpler way of creating one.
     *
     * @param keySizeBytes the size of key when serialized in bytes
     * @param keyConstructor constructor for creating keys for deserialization
     * @param valueSizeBytes the size of value when serialized in bytes
     * @param valueConstructor constructor for creating values for deserialization
     */
    public VFCDataSourceLmdbTwoIndexes(int keySizeBytes, Supplier<K> keyConstructor, int valueSizeBytes, Supplier<V> valueConstructor,
                                       Path storageDir) throws IOException {
        this.keySizeBytes = Integer.BYTES + keySizeBytes;
        this.keyConstructor = keyConstructor;
        this.valueConstructor = valueConstructor;
        this.keyAndHashSizeBytes = keySizeBytes + HASH_SIZE;
        this.pathAndValueSizeBytes = pathSizeBytes + Integer.BYTES + valueSizeBytes;

        // create thread local buffers
        pathBytes = ThreadLocal.withInitial(() -> {
            ByteBuffer buf = allocateDirect(pathSizeBytes);
            buf.order(ByteOrder.nativeOrder()); // the byte order is important to use MDB_INTEGERKEY as LMDB needs keys to be in native byte order
            return buf;
        });
        leafKey = ThreadLocal.withInitial(() -> allocateDirect(this.keySizeBytes));
        keyAndHashBytes = ThreadLocal.withInitial(() -> allocateDirect(keyAndHashSizeBytes));
        pathAndValueBytes = ThreadLocal.withInitial(() -> allocateDirect(pathAndValueSizeBytes));
        // create storage dirs
        if (!Files.exists(storageDir)) Files.createDirectories(storageDir);
        // We always need an Env. An Env owns a physical on-disk storage file. One
        // Env can store many different databases (ie sorted maps).
        env = Env.create()
                // LMDB also needs to know how large our DB might be. Over-estimating is OK.
                .setMapSize(1_000_000_000*(long)(keySizeBytes+keySizeBytes+valueSizeBytes+HASH_SIZE+Long.BYTES+Long.BYTES+Long.BYTES+Long.BYTES)) // TODO just a guess so far
                // LMDB also needs to know how many DBs (Dbi) we want to store in this Env.
                .setMaxDbs(4)
                // Now let's open the Env. The same path can be concurrently opened and
                // used in different processes, but do not open the same path twice in
                // the same process at the same time.
                .open(storageDir.toFile(), EnvFlags.MDB_WRITEMAP, EnvFlags.MDB_NOSYNC);
        // We need a Dbi for each DB. A Dbi roughly equates to a sorted map. The
        // MDB_CREATE flag causes the DB to be created if it doesn't already exist.
        pathToKeyAndHashMap = env.openDbi("pathToHash", MDB_CREATE,MDB_INTEGERKEY);
//        pathToKeyAndHashMap = env.openDbi("pathToHash", MDB_CREATE);
        keyToPathAndValueMap = env.openDbi("leafKeyToValue", MDB_CREATE);
    }

    //==================================================================================================================
    // Public API methods

    public void printStats() {
        try (Txn<ByteBuffer> txn = env.txnWrite()) {
            System.out.println("pathToKeyAndHashMap.stat() = " + pathToKeyAndHashMap.stat(txn));
            System.out.println("leafKeyToPathAndValueMap.stat() = " + keyToPathAndValueMap.stat(txn));
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
     * Load hash for a node with given path
     *
     * @param path the path to get hash for
     * @return loaded hash or null if hash is not stored
     * @throws IOException if there was a problem loading hash
     */
    @Override
    public Hash loadHash(long path) throws IOException {
        if (path < 0) throw new IllegalArgumentException("path is less than 0");
        try (Txn<ByteBuffer> txn = env.txnRead()) {
            ByteBuffer keyHashBytes = pathToKeyAndHashMap.get(txn,getPathBytes(path));
            if (keyHashBytes == null) return null;
            return getHashFromKeyAndHash(keyHashBytes);
        }
    }

    /**
     * Load leaf's value
     *
     * @param path the path for leaf to get value for
     * @return loaded leaf value or null if none was saved
     * @throws IOException if there was a problem loading leaf data
     */
    @Override
    public V loadLeafValue(long path) throws IOException {
        if (path < 0) throw new IllegalArgumentException("path is less than 0");
        try (Txn<ByteBuffer> txn = env.txnRead()) {
            ByteBuffer keyHashBytes = pathToKeyAndHashMap.get(txn,getPathBytes(path));
            if (keyHashBytes == null) return null;
            keyHashBytes.position(0);
            keyHashBytes.limit(keySizeBytes);
            ByteBuffer pathValueBytes = keyToPathAndValueMap.get(txn,keyHashBytes);
            if (pathValueBytes == null) return null;
            return getValueFromPathAndValue(pathValueBytes);
        }
    }

    /**
     * Load leaf's value
     *
     * @param key the key for leaf to get value for
     * @return loaded leaf value or null if none was saved
     * @throws IOException if there was a problem loading leaf data
     */
    @Override
    public V loadLeafValue(K key) throws IOException {
        if (key == null) throw new IllegalArgumentException("key can not be null");
        try (Txn<ByteBuffer> txn = env.txnRead()) {
            ByteBuffer pathValueBytes = keyToPathAndValueMap.get(txn,getLeafKeyBytes(key));
            if (pathValueBytes == null) return null;
            return getValueFromPathAndValue(pathValueBytes);
        }
    }

    /**
     * Load a leaf's key
     *
     * @param path the path to the leaf to load key for
     * @return the loaded key for leaf or null if none was saved
     * @throws IOException if there was a problem loading key
     */
    @Override
    public K loadLeafKey(long path) throws IOException {
        if (path < 0) throw new IllegalArgumentException("path is less than 0");
        try (Txn<ByteBuffer> txn = env.txnRead()) {
            ByteBuffer keyHashBytes = pathToKeyAndHashMap.get(txn,getPathBytes(path));
            if (keyHashBytes == null) return null;
            return getKeyFromKeyAndHash(keyHashBytes);
        }
    }

    /**
     * Load path for a leaf
     *
     * @param key the key for the leaf to get path for
     * @return loaded path or null if none is stored for key
     * @throws IOException if there was a problem loading leaf's path
     */
    @Override
    public long loadLeafPath(K key) throws IOException {
        try (Txn<ByteBuffer> txn = env.txnRead()) {
            ByteBuffer pathValueBytes = keyToPathAndValueMap.get(txn,getLeafKeyBytes(key));
            if (pathValueBytes == null) return INVALID_PATH;
            return getPathFromPathAndValue(pathValueBytes);
        }
    }

    /**
     * Save a hash for a internal node
     *
     * @param path the path of the node to save hash for, if nothing has been stored for this path before it will be created.
     * @param hash a non-null hash to write
     */
    @Override
    public void saveInternal(long path, Hash hash) {
        if (path < 0) throw new IllegalArgumentException("path is less than 0");
        if (hash == null)  throw new IllegalArgumentException("Hash is null");
        // write hash
        try (Txn<ByteBuffer> txn = env.txnWrite()) {
            pathToKeyAndHashMap.put(txn,getPathBytes(path), getKeyAndHashBytes(hash));
            // commit transaction
            txn.commit();
        }
    }

    /**
     * Update a leaf moving it from one path to another. Note! any existing node at the newPath will be overridden.
     *
     * @param oldPath Must be an existing valid path
     * @param newPath Can be larger than current max path, allowing tree to grow
     * @param key The key for the leaf so we can update key->path index
     * @throws IOException if there was a problem saving leaf update
     */
    @Override
    public void updateLeaf(long oldPath, long newPath, K key, Hash hash) throws IOException {
        if (oldPath < 0) throw new IllegalArgumentException("path is less than 0");
        if (newPath < 0) throw new IllegalArgumentException("path is less than 0");

        try (Txn<ByteBuffer> txn = env.txnWrite()) {
            final ByteBuffer keyBytes = getLeafKeyBytes(key);
            final ByteBuffer pathBytes = getPathBytes(newPath);
            // write key & hash
            pathToKeyAndHashMap.put(txn, pathBytes, getKeyAndHashBytes(key, hash));
            // read value
            ByteBuffer pathAndValue = keyToPathAndValueMap.get(txn,keyBytes); // TODO ask Rich if we can pass value into this method
            // add new path
            pathAndValue.putLong(0, newPath);
            // write path & value
            keyToPathAndValueMap.put(txn, keyBytes, pathAndValue);
            // commit transaction
            txn.commit();
        }
    }

    /**
     * Update a leaf at given path, the leaf must exist. Writes hash and value.
     *
     * @param path valid path to saved leaf
     * @param key valid key for saved leaf
     * @param value the value for new leaf, can be null
     * @param hash non-null hash for the leaf
     * @throws IOException if there was a problem saving leaf update
     */
    @Override
    public void updateLeaf(long path, K key, V value, Hash hash) throws IOException {
        if (path < 0) throw new IllegalArgumentException("path is less than 0");
        if (hash == null) throw new IllegalArgumentException("Can not save null hash for leaf at path ["+path+"]");
        try (Txn<ByteBuffer> txn = env.txnWrite()) {
            final ByteBuffer keyBytes = getLeafKeyBytes(key);
            final ByteBuffer pathBytes = getPathBytes(path);
            // write hash
            pathToKeyAndHashMap.put(txn,pathBytes, getKeyAndHashBytes(key,hash));
            // write value
            keyToPathAndValueMap.put(txn, keyBytes, getPathAndValueBytes(path, value));
            // commit transaction
            txn.commit();
        }
    }

    /**
     * Add a new leaf to store
     *
     * @param path the path for the new leaf
     * @param key the non-null key for the new leaf
     * @param value the value for new leaf, can be null
     * @param hash the non-null hash for new leaf
     * @throws IOException if there was a problem writing leaf
     */
    @Override
    public void addLeaf(long path, K key, V value, Hash hash) throws IOException {
        if (path < 0) throw new IllegalArgumentException("path is less than 0");
        if (hash == null) throw new IllegalArgumentException("Can not save null hash for leaf at path ["+path+"]");
        if (key == null) throw new IllegalArgumentException("Can not save null key for leaf at path ["+path+"]");
        final ByteBuffer pathBytes = getPathBytes(path);
        final ByteBuffer keyBytes = getLeafKeyBytes(key);
        try (Txn<ByteBuffer> txn = env.txnWrite()) {
            // write key & hash
            pathToKeyAndHashMap.put(txn,pathBytes, getKeyAndHashBytes(key, hash));
            // write path & value
            keyToPathAndValueMap.put(txn, keyBytes, getPathAndValueBytes(path, value));
            // commit transaction
            txn.commit();
        } catch (Exception e) {
            System.err.println("Exception addLeaf path="+path+", key="+key+", value="+value+", hash="+hash+", pathBytes="+pathBytes+", keyBytes="+keyBytes);
            throw e;
        }
    }

    //==================================================================================================================
    // Public API Transaction methods

    @Override
    public Object startTransaction() {
        return env.txnWrite();
    }

    @Override
    public void commitTransaction(Object handle) {
        Txn<ByteBuffer> txn = (Txn<ByteBuffer>)handle;
        txn.commit();
        txn.close();
    }

    private LongHashSet longHashSet = new LongHashSet(100_000_000);

    /**
     * Save a hash for a internal node
     *
     * @param path the path of the node to save hash for, if nothing has been stored for this path before it will be created.
     * @param hash a non-null hash to write
     */
    @Override
    public void saveInternal(Object handle, long path, Hash hash) {
        if (path < 0) throw new IllegalArgumentException("path is less than 0");
        if (hash == null)  throw new IllegalArgumentException("Hash is null");
        Txn<ByteBuffer> txn = (Txn<ByteBuffer>)handle;
        // write hash
        if (longHashSet.contains(path)) {
            System.out.println("we have already seen path "+path);
        } else {
            longHashSet.add(path);
        }
        pathToKeyAndHashMap.put(txn,getPathBytes(path), getKeyAndHashBytes(hash));
    }

    /**
     * Update a leaf moving it from one path to another. Note! any existing node at the newPath will be overridden.
     *
     * @param oldPath Must be an existing valid path
     * @param newPath Can be larger than current max path, allowing tree to grow
     * @param key The key for the leaf so we can update key->path index
     * @throws IOException if there was a problem saving leaf update
     */
    @Override
    public void updateLeaf(Object handle, long oldPath, long newPath, K key, Hash hash) throws IOException {
        if (oldPath < 0) throw new IllegalArgumentException("path is less than 0");
        if (newPath < 0) throw new IllegalArgumentException("path is less than 0");
        Txn<ByteBuffer> txn = (Txn<ByteBuffer>)handle;
        final ByteBuffer keyBytes = getLeafKeyBytes(key);
        final ByteBuffer pathBytes = getPathBytes(newPath);
        // write key & hash
        pathToKeyAndHashMap.put(txn, pathBytes, getKeyAndHashBytes(key, hash));
        // read value
        ByteBuffer pathAndValue = keyToPathAndValueMap.get(txn,keyBytes); // TODO ask Rich if we can pass value into this method
        // add new path
        pathAndValue.putLong(0, newPath);
        // write path & value
        keyToPathAndValueMap.put(txn, keyBytes, pathAndValue);
    }

    /**
     * Update a leaf at given path, the leaf must exist. Writes hash and value.
     *
     * @param path valid path to saved leaf
     * @param key valid key for saved leaf
     * @param value the value for new leaf, can be null
     * @param hash non-null hash for the leaf
     * @throws IOException if there was a problem saving leaf update
     */
    @Override
    public void updateLeaf(Object handle, long path, K key,  V value, Hash hash) throws IOException {
        if (path < 0) throw new IllegalArgumentException("path is less than 0");
        if (hash == null) throw new IllegalArgumentException("Can not save null hash for leaf at path [" + path + "]");
        Txn<ByteBuffer> txn = (Txn<ByteBuffer>)handle;
        final ByteBuffer keyBytes = getLeafKeyBytes(key);
        final ByteBuffer pathBytes = getPathBytes(path);
        // write hash
        pathToKeyAndHashMap.put(txn,pathBytes, getKeyAndHashBytes(key,hash));
        // write value
        keyToPathAndValueMap.put(txn, keyBytes, getPathAndValueBytes(path, value));
    }

    /**
     * Add a new leaf to store
     *
     * @param path the path for the new leaf
     * @param key the non-null key for the new leaf
     * @param value the value for new leaf, can be null
     * @param hash the non-null hash for new leaf
     * @throws IOException if there was a problem writing leaf
     */
    @Override
    public void addLeaf(Object handle, long path, K key, V value, Hash hash) throws IOException {
        if (path < 0) throw new IllegalArgumentException("path is less than 0");
        if (hash == null) throw new IllegalArgumentException("Can not save null hash for leaf at path ["+path+"]");
        if (key == null) throw new IllegalArgumentException("Can not save null key for leaf at path ["+path+"]");
        Txn<ByteBuffer> txn = (Txn<ByteBuffer>)handle;
        final ByteBuffer pathBytes = getPathBytes(path);
        final ByteBuffer keyBytes = getLeafKeyBytes(key);
        // write key & hash
        pathToKeyAndHashMap.put(txn,pathBytes, getKeyAndHashBytes(key,hash));
        // write path & value
        keyToPathAndValueMap.put(txn, keyBytes, getPathAndValueBytes(path, value));
    }


    //==================================================================================================================
    // Public Sequential Methods

    /**
     * Save a hash for a internal node
     *
     * @param path the path of the node to save hash for, if nothing has been stored for this path before it will be created.
     * @param hash a non-null hash to write
     */
    @Override
    public void saveInternalSequential(Object handle, long path, Hash hash) {
        if (path < 0) throw new IllegalArgumentException("path is less than 0");
        if (hash == null)  throw new IllegalArgumentException("Hash is null");
        Txn<ByteBuffer> txn = (Txn<ByteBuffer>)handle;
        // write hash
        if (longHashSet.contains(path)) {
            System.out.println("we have already seen path "+path);
        } else {
            longHashSet.add(path);
        }
        pathToKeyAndHashMap.put(txn,getPathBytes(path), getKeyAndHashBytes(hash), PutFlags.MDB_APPEND);
    }

    /**
     * Add a new leaf to store
     *
     * @param path the path for the new leaf
     * @param key the non-null key for the new leaf
     * @param value the value for new leaf, can be null
     * @param hash the non-null hash for new leaf
     * @throws IOException if there was a problem writing leaf
     */
    @Override
    public void addLeafSequential(Object handle, long path, K key, V value, Hash hash) throws IOException {
        if (path < 0) throw new IllegalArgumentException("path is less than 0");
        if (hash == null) throw new IllegalArgumentException("Can not save null hash for leaf at path ["+path+"]");
        if (key == null) throw new IllegalArgumentException("Can not save null key for leaf at path ["+path+"]");
        Txn<ByteBuffer> txn = (Txn<ByteBuffer>)handle;
        final ByteBuffer pathBytes = getPathBytes(path);
        final ByteBuffer keyBytes = getLeafKeyBytes(key);
        // write key & hash
        pathToKeyAndHashMap.put(txn,pathBytes, getKeyAndHashBytes(key,hash), PutFlags.MDB_APPEND);
        // write path & value
        keyToPathAndValueMap.put(txn, keyBytes, getPathAndValueBytes(path, value), PutFlags.MDB_APPEND);
    }

    //==================================================================================================================
    // Private Util Methods

    private K getKeyFromKeyAndHash(ByteBuffer keyAndHashBytes) throws IOException{
        keyAndHashBytes.rewind();
        final int keySerializationVersion =  keyAndHashBytes.getInt();
        K key = keyConstructor.get();
        key.deserialize(keyAndHashBytes,keySerializationVersion);
        return key;
    }

    private ByteBuffer getLeafKeyBytes(K key) throws IOException {
        final ByteBuffer leafKey = this.leafKey.get();
        leafKey.rewind();
        leafKey.putInt(key.getVersion());
        key.serialize(leafKey);
        return leafKey.flip();
    }

    private ByteBuffer getPathBytes(long path) {
        ByteBuffer pathBytes = this.pathBytes.get();
        pathBytes.rewind();
        pathBytes.putLong(path);
        return pathBytes.flip();
    }


    private V getValueFromPathAndValue(ByteBuffer pathAndValueBytes) throws IOException{
        pathAndValueBytes.position(pathSizeBytes); // jump over path
        final int valueSerializationVersion = pathAndValueBytes.getInt();
        V value = valueConstructor.get();
        value.deserialize(pathAndValueBytes,valueSerializationVersion);
        return value;
    }

    private long getPathFromPathAndValue(ByteBuffer pathAndValueBytes) {
        return pathAndValueBytes.getLong(0);
    }

    private ByteBuffer getPathAndValueBytes(long path, V value) throws IOException {
        final ByteBuffer pathAndValueBytes = this.pathAndValueBytes.get();
        pathAndValueBytes.rewind();
        pathAndValueBytes.putLong(path);
        pathAndValueBytes.putInt(value.getVersion());
        value.serialize(pathAndValueBytes);
        return pathAndValueBytes.flip();
    }


    private Hash getHashFromKeyAndHash(ByteBuffer hashBytes) throws IOException {
        hashBytes.position(keySizeBytes);
        return Hash.fromByteBuffer(hashBytes);
    }

    private ByteBuffer getKeyAndHashBytes(K key, Hash hash) throws IOException {
        ByteBuffer keyAndHashBytes = this.keyAndHashBytes.get();
        keyAndHashBytes.rewind();
        keyAndHashBytes.putInt(key.getVersion());
        key.serialize(keyAndHashBytes);
        keyAndHashBytes.position(keySizeBytes); // jump over key
        Hash.toByteBuffer(hash,keyAndHashBytes);
        return keyAndHashBytes.flip();
    }

    private ByteBuffer getKeyAndHashBytes(Hash hash) {
        ByteBuffer keyAndHashBytes = this.keyAndHashBytes.get();
        keyAndHashBytes.position(keySizeBytes); // jump over key
        Hash.toByteBuffer(hash,keyAndHashBytes);
        return keyAndHashBytes.flip();
    }
}

