package com.hedera.services.state.merkle.v3;

import com.hedera.services.state.merkle.v3.files.DataFile;
import com.hedera.services.state.merkle.v3.files.MemoryIndexDiskKeyValueStore;
import com.hedera.services.state.merkle.v3.offheap.OffHeapHashList;
import com.hedera.services.state.merkle.v3.offheap.OffHeapLongList;
import com.swirlds.common.crypto.DigestType;
import com.swirlds.common.crypto.Hash;
import com.swirlds.fcmap.LongVKey;
import com.swirlds.fcmap.VFCDataSource;
import com.swirlds.fcmap.VKey;
import com.swirlds.fcmap.VValue;
import org.eclipse.collections.api.map.primitive.MutableObjectLongMap;
import org.eclipse.collections.impl.map.mutable.primitive.ObjectLongHashMap;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.function.Supplier;

import static java.nio.ByteBuffer.allocateDirect;

public class VFCDataSourceImplV3<K extends VKey, V extends VValue> implements VFCDataSource<K, V> {
    private final static int HASH_SIZE = Integer.BYTES+ DigestType.SHA_384.digestLength(); // TODO remove please for something better
    private final int keySizeBytes;
    private final int valueSizeBytes;
    private final Supplier<K> keyConstructor;
    private final Supplier<V> valueConstructor;
    private final boolean isLongKeyMode;
    private final OffHeapHashList hashStore = new OffHeapHashList();
    private final OffHeapLongList longKeyToPath;
    private final MutableObjectLongMap<K> objectKeyToPath;
    private final MemoryIndexDiskKeyValueStore pathToKeyHashValue;
    private final ThreadLocal<ByteBuffer> leafKey;
//    private final ThreadLocal<ByteBuffer> leafValue;
    private final ThreadLocal<ByteBuffer> keyHashValue;
//    private final ThreadLocal<ByteBuffer> hashData;

    /**
     * Create new VFCDataSourceImplV3
     *
     * @param keySizeBytes the size of key when serialized in bytes
     * @param keyConstructor constructor for creating keys for deserialization
     * @param valueSizeBytes the size of value when serialized in bytes
     * @param valueConstructor constructor for creating values for deserialization
     * @param storageDir directory to store data files in
     */
    public VFCDataSourceImplV3(int keySizeBytes, Supplier<K> keyConstructor, int valueSizeBytes, Supplier<V> valueConstructor,
                                Path storageDir) throws IOException {
        this.keySizeBytes = Integer.BYTES + keySizeBytes;
        this.keyConstructor = keyConstructor;
        this.valueSizeBytes = Integer.BYTES + valueSizeBytes;
        this.valueConstructor = valueConstructor;
        this.leafKey = ThreadLocal.withInitial(() -> allocateDirect(this.keySizeBytes));
//        this.leafValue = ThreadLocal.withInitial(() -> allocateDirect(HASH_SIZE+this.valueSizeBytes));
        this.keyHashValue = ThreadLocal.withInitial(() -> allocateDirect(this.keySizeBytes+HASH_SIZE+this.valueSizeBytes));
//        this.hashData = ThreadLocal.withInitial(() -> allocateDirect(HASH_SIZE));
        if (keySizeBytes == Long.BYTES) {
            isLongKeyMode = true;
            longKeyToPath = new OffHeapLongList();
            objectKeyToPath = null;
        } else {
            isLongKeyMode = false;
            longKeyToPath =  null;
            objectKeyToPath = new ObjectLongHashMap<K>().asSynchronized();
        }
        pathToKeyHashValue = new MemoryIndexDiskKeyValueStore(storageDir,"pathToKeyHashValue",1024); // TODO 1024 should be power of 2 bigger than hash size + valueSizeBytes
    }


    //==================================================================================================================
    // Public API methods

    /**
     * Close all data stores
     */
    @Override
    public void close() throws IOException {
        pathToKeyHashValue.close();
    }

    /**
     * Load hash for a leaf node with given path
     *
     * @param path the path to get hash for
     * @return loaded hash or null if hash is not stored
     * @throws IOException if there was a problem loading hash
     */
    @Override
    public Hash loadLeafHash(long path) throws IOException {
        if (path < 0) throw new IllegalArgumentException("path is less than 0");
        ByteBuffer keyHashValueBuffer = this.keyHashValue.get().clear();
        // read value
        boolean found = pathToKeyHashValue.get(path,keyHashValueBuffer, DataFile.DataToRead.VALUE);
        if (!found) return null;
        // deserialize hash
        keyHashValueBuffer.rewind();
        keyHashValueBuffer.position(keySizeBytes); // jump key
        return Hash.fromByteBuffer(keyHashValueBuffer);
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
        return hashStore.get(path);
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
        ByteBuffer keyHashValueBuffer = this.keyHashValue.get().clear();
        // read value
        boolean found = pathToKeyHashValue.get(path,keyHashValueBuffer, DataFile.DataToRead.VALUE);
        if (!found) return null;
        // deserialize value
        keyHashValueBuffer.rewind();
        keyHashValueBuffer.position(keySizeBytes+HASH_SIZE); // jump over key and hash, TODO add API to read at starting offset, to avoid this
        final int valueSerializationVersion = keyHashValueBuffer.getInt();
        V value = valueConstructor.get();
        value.deserialize(keyHashValueBuffer,valueSerializationVersion);
        return value;
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
        long path = isLongKeyMode ? longKeyToPath.get(((LongVKey)key).getKeyAsLong(), INVALID_PATH) : objectKeyToPath.getIfAbsent(key, INVALID_PATH);
        if (path == INVALID_PATH) return null;
        return loadLeafValue(path);
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
        ByteBuffer keyBuffer = this.leafKey.get().clear();
        // read key
        boolean found = pathToKeyHashValue.get(path,keyBuffer, DataFile.DataToRead.VALUE);
        if (!found) return null;
        keyBuffer.rewind();
        final int keySerializationVersion =  keyBuffer.getInt();
        K key = keyConstructor.get();
        key.deserialize(keyBuffer,keySerializationVersion);
        return key;
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
        return isLongKeyMode ? longKeyToPath.get(((LongVKey)key).getKeyAsLong(), INVALID_PATH) : objectKeyToPath.getIfAbsent(key, INVALID_PATH);
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
        hashStore.put(path,hash);
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
        addLeaf(newPath,key,loadLeafValue(oldPath),hash);
    }

    /**
     * Update a leaf at given path, the leaf must exist. Writes hash and value.
     *
     * @param path valid path to saved leaf
     * @param key the leaf's key
     * @param value the value for new leaf, can be null
     * @param hash non-null hash for the leaf
     * @throws IOException if there was a problem saving leaf update
     */
    @Override
    public void updateLeaf(long path, K key, V value, Hash hash) throws IOException {
        addLeaf(path, key,value, hash);
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
        // fill buffer
        ByteBuffer keyHashValueBuffer = this.keyHashValue.get().clear();
        // put key
        keyHashValueBuffer.putInt(key.getVersion());
        key.serialize(keyHashValueBuffer);
        // put hash
        Hash.toByteBuffer(hash,keyHashValueBuffer);
        // put value
        keyHashValueBuffer.putInt(value.getVersion());
        value.serialize(keyHashValueBuffer);
        // now save pathToKeyHashValue
        keyHashValueBuffer.flip();
        pathToKeyHashValue.put(path,keyHashValueBuffer);
        // save key to path mapping
        if (isLongKeyMode) {
            longKeyToPath.put(((LongVKey)key).getKeyAsLong(),path);
        } else {
            objectKeyToPath.put(key,path);
        }
    }

    //==================================================================================================================
    // Public API Transaction methods

    @Override
    public Object startTransaction() {
        try {
            pathToKeyHashValue.startWriting();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public void commitTransaction(Object handle) {
        try {
            pathToKeyHashValue.endWriting();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
