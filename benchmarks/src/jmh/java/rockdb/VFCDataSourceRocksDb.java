package rockdb;

import com.swirlds.common.crypto.DigestType;
import com.swirlds.common.crypto.Hash;
import com.swirlds.fcmap.VFCDataSource;
import com.swirlds.fcmap.VKey;
import com.swirlds.fcmap.VValue;
import org.rocksdb.*;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * RocksDB implementation of VFCDataSource. It should support and actually benefit from being used from many threads.
 *
 * @param <K> leaf key type
 * @param <V> leaf value type
 */
@SuppressWarnings("jol")
public final class VFCDataSourceRocksDb <K extends VKey, V extends VValue> implements VFCDataSource<K, V> {
    private final static int HASH_SIZE = Long.BYTES+ DigestType.SHA_384.digestLength();
    private final WriteOptions writeOptions;
    private final Supplier<K> keyConstructor;
    private final Supplier<V> valueConstructor;
    private final int keySizeBytes;
    private final int valueSizeBytes;
    private final RocksDB db;
    private final ColumnFamilyHandle pathToHashMap;
    private final ColumnFamilyHandle leafPathToKeyMap;
    private final ColumnFamilyHandle leafKeyToPathMap;
    private final ColumnFamilyHandle leafKeyToValueMap;
    private final ThreadLocal<byte[]> pathBytes = ThreadLocal.withInitial(() -> new byte[Long.BYTES]);
    private final ThreadLocal<byte[]> hashData = ThreadLocal.withInitial(() -> new byte[HASH_SIZE]);
    private final ThreadLocal<byte[]> leafKey;
    private final ThreadLocal<byte[]> leafValue;

    /**
     * Construct a new VFCDataSourceImpl, try the static factory methods if you want a simpler way of creating one.
     *
     * @param keySizeBytes the size of key when serialized in bytes
     * @param keyConstructor constructor for creating keys for deserialization
     * @param valueSizeBytes the size of value when serialized in bytes
     * @param valueConstructor constructor for creating values for deserialization
     */
    public VFCDataSourceRocksDb(int keySizeBytes, Supplier<K> keyConstructor, int valueSizeBytes, Supplier<V> valueConstructor,
                             Path storageDir) throws IOException {
        this.keySizeBytes = keySizeBytes; // needs to include serialization version
        this.keyConstructor = keyConstructor;
        this.valueSizeBytes = valueSizeBytes; // needs to include serialization version
        this.valueConstructor = valueConstructor;
        this.leafKey = ThreadLocal.withInitial(() -> new byte[Integer.BYTES + keySizeBytes]);
        this.leafValue = ThreadLocal.withInitial(() -> new byte[Integer.BYTES + valueSizeBytes]);
        // create storage dirs
        if (!Files.exists(storageDir)) Files.createDirectories(storageDir);
        // a static method that loads the RocksDB C++ library.
        RocksDB.loadLibrary();
//        this.pathToHashMap.getEnv().setBackgroundThreads(4);
        // the Options class contains a set of configurable DB options
        // that determines the behaviour of the database.
        try {
            this.writeOptions = new WriteOptions();
            ColumnFamilyOptions columnFamilyOptions = new ColumnFamilyOptions()
//                    .optimizeLevelStyleCompaction()
//                    .optimizeUniversalStyleCompaction()
//                    .setBloomLocality()
//                    .setWriteBufferSize()
                    .optimizeForPointLookup(512);
//                    .setOptimizeFiltersForHits(true);
            ColumnFamilyDescriptor pathToHashMapDescriptor = new ColumnFamilyDescriptor(RocksDB.DEFAULT_COLUMN_FAMILY, columnFamilyOptions);
            ColumnFamilyDescriptor leafPathToKeyMapDescriptor = new ColumnFamilyDescriptor("leafPathToKey".getBytes(), columnFamilyOptions);
            ColumnFamilyDescriptor leafKeyToPathMapDescriptor = new ColumnFamilyDescriptor("leafKeyToPath".getBytes(), columnFamilyOptions);
            ColumnFamilyDescriptor leafKeyToValueMapDescriptor = new ColumnFamilyDescriptor("leafKeyToValue".getBytes(), columnFamilyOptions);
            // open db
            final DBOptions options = new DBOptions()
//                    .setIncreaseParallelism(4)
                    .setMaxBackgroundJobs(20)
                    .setCreateIfMissing(true)
                    .setCreateMissingColumnFamilies(true);
            final List<ColumnFamilyHandle> columnFamilyHandles = new ArrayList<>(4);
            this.db = RocksDB.open(options, storageDir.toAbsolutePath().toString(),
                    List.of(pathToHashMapDescriptor,leafPathToKeyMapDescriptor,leafKeyToPathMapDescriptor,leafKeyToValueMapDescriptor),
                    columnFamilyHandles);
            // get column handles
            this.pathToHashMap = columnFamilyHandles.get(0);
            this.leafPathToKeyMap = columnFamilyHandles.get(1);
            this.leafKeyToPathMap = columnFamilyHandles.get(2);
            this.leafKeyToValueMap = columnFamilyHandles.get(3);

            } catch (RocksDBException e) {
            throw new IOException("Problem opening RocksDB",e);
        }
    }


    //==================================================================================================================
    // Public API methods

    /**
     * Close all data stores
     */
    @Override
    public void close() {
        pathToHashMap.close();
        leafPathToKeyMap.close();
        leafKeyToPathMap.close();
        leafKeyToValueMap.close();
        db.close();
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
        try {
            byte[] hashData = this.hashData.get();
            final int result = db.get(pathToHashMap, getPathBytes(path),hashData);
            if (result == RocksDB.NOT_FOUND) return null;
            return getHash(hashData);
        } catch (RocksDBException e) {
            throw new IOException("Problem reading hash from db",e);
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
        try {
            byte[] leafKey = this.leafKey.get();
            byte[] leafValue = this.leafValue.get();
            // get key from path
            int result = db.get(leafPathToKeyMap, getPathBytes(path), leafKey);
            if (result == RocksDB.NOT_FOUND) return null;
            // read leaf value
            result = db.get(leafKeyToValueMap,leafKey, leafValue);
            if (result == RocksDB.NOT_FOUND) return null;
            return getLeafValue(leafValue);
        } catch (RocksDBException e) {
            throw new IOException("Problem reading leaf path",e);
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
        try {
            byte[] leafValue = this.leafValue.get();
            // read leaf data
            int result = db.get(leafKeyToValueMap, getLeafKeyBytes(key), leafValue);
            if (result == RocksDB.NOT_FOUND) return null;
            return getLeafValue(leafValue);
        } catch (RocksDBException e) {
            throw new IOException("Problem reading leaf path",e);
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
        try {
            byte[] leafKey = this.leafKey.get();
            // get key from path
            final int result = db.get(leafPathToKeyMap, getPathBytes(path), leafKey);
            if (result == RocksDB.NOT_FOUND) return null;
            return getLeafKey(leafKey);
        } catch (RocksDBException e) {
            throw new IOException("Problem reading leaf path",e);
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
        try {
            byte[] pathBytes = this.pathBytes.get();
            int result = db.get(leafKeyToPathMap, getLeafKeyBytes(key), pathBytes);
            if (result == RocksDB.NOT_FOUND) return INVALID_PATH;
            return getPath(pathBytes);
        } catch (RocksDBException e) {
            throw new IOException("Problem reading leaf path",e);
        }
    }

    /**
     * Save a hash for a internal node
     *
     * @param path the path of the node to save hash for, if nothing has been stored for this path before it will be created.
     * @param hash a non-null hash to write
     * @throws IOException if there was a problem writing the hash
     */
    @Override
    public void saveInternal(long path, Hash hash) throws IOException {
        if (path < 0) throw new IllegalArgumentException("path is less than 0");
        if (hash == null)  throw new IllegalArgumentException("Hash is null");
        // write hash
        try {
            db.put(pathToHashMap, getPathBytes(path), getHashBytes(hash));
        } catch (RocksDBException e) {
            throw new IOException("Problem saving hash to db",e);
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
        final byte[] keyBytes = getLeafKeyBytes(key);
        try {
            // read hash
            byte[] hashData = hash.getValue();

            // now update everything
            final byte[] newPathKey = getPathBytes(newPath);
            // create batch
            WriteBatch writeBatch = new WriteBatch();
            // write hash
            writeBatch.put(pathToHashMap,newPathKey, hashData);
            // write key -> path
            writeBatch.put(leafKeyToPathMap, keyBytes, newPathKey);
            // write path -> key
            writeBatch.put(leafPathToKeyMap, newPathKey, keyBytes);
            // write the batch
            db.write(writeOptions,writeBatch);
        } catch (RocksDBException e) {
            throw new IOException("Problem adding Leaf",e);
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
        final byte[] pathBytes = getPathBytes(path);
        try {
            // create batch
            WriteBatch writeBatch = new WriteBatch();
            // write hash
            writeBatch.put(pathToHashMap, pathBytes, getHashBytes(hash));
            // write value
            writeBatch.put(leafKeyToValueMap, getLeafKeyBytes(key), getLeafValueBytes(value));
            // write the batch
            db.write(writeOptions,writeBatch);
        } catch (RocksDBException e) {
            throw new IOException("Problem adding Leaf",e);
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
        final byte[] pathBytes = getPathBytes(path);
        final byte[] keyBytes = getLeafKeyBytes(key);
        try {
            // create batch
            WriteBatch writeBatch = new WriteBatch();
            // write hash
            writeBatch.put(pathToHashMap,pathBytes, getHashBytes(hash));
            // write key -> path
            writeBatch.put(leafKeyToPathMap, keyBytes, pathBytes);
            // write path -> key
            writeBatch.put(leafPathToKeyMap, pathBytes, keyBytes);
            // write value
            writeBatch.put(leafKeyToValueMap, keyBytes, getLeafValueBytes(value));
            // write the batch
            db.write(writeOptions,writeBatch);
        } catch (RocksDBException e) {
            throw new IOException("Problem adding Leaf",e);
        }
    }

    //==================================================================================================================
    // Public API Transaction methods

    @Override
    public Object startTransaction() {
        return new WriteBatch();
    }

    @Override
    public void commitTransaction(Object handle) {
        if (handle instanceof WriteBatch) {
            try {
                db.write(writeOptions,(WriteBatch) handle);
            } catch (RocksDBException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Save a hash for a internal node
     *
     * @param path the path of the node to save hash for, if nothing has been stored for this path before it will be created.
     * @param hash a non-null hash to write
     * @throws IOException if there was a problem writing the hash
     */
    @Override
    public void saveInternal(Object handle, long path, Hash hash) throws IOException {
        if (path < 0) throw new IllegalArgumentException("path is less than 0");
        if (hash == null)  throw new IllegalArgumentException("Hash is null");
        WriteBatch writeBatch = (WriteBatch) handle;
        // write hash
        try {
            writeBatch.put(pathToHashMap, getPathBytes(path), getHashBytes(hash));
        } catch (RocksDBException e) {
            throw new IOException("Problem saving hash to db",e);
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
    public void updateLeaf(Object handle, long oldPath, long newPath, K key, Hash hash) throws IOException {
        if (oldPath < 0) throw new IllegalArgumentException("path is less than 0");
        if (newPath < 0) throw new IllegalArgumentException("path is less than 0");
        final WriteBatch writeBatch = (WriteBatch) handle;
        final byte[] keyBytes = getLeafKeyBytes(key);
        try {
            // read hash
            byte[] hashData = hash.getValue();

            // now update everything
            final byte[] newPathKey = getPathBytes(newPath);
            // write hash
            writeBatch.put(pathToHashMap,newPathKey, hashData);
            // write key -> path
            writeBatch.put(leafKeyToPathMap, keyBytes, newPathKey);
            // write path -> key
            writeBatch.put(leafPathToKeyMap, newPathKey, keyBytes);
        } catch (RocksDBException e) {
            throw new IOException("Problem adding Leaf",e);
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
    public void updateLeaf(Object handle, long path, K key, V value, Hash hash) throws IOException {
        if (path < 0) throw new IllegalArgumentException("path is less than 0");
        if (hash == null) throw new IllegalArgumentException("Can not save null hash for leaf at path ["+path+"]");
        final WriteBatch writeBatch = (WriteBatch) handle;
        final byte[] pathBytes = getPathBytes(path);
        try {
            // write hash
            writeBatch.put(pathToHashMap, pathBytes, getHashBytes(hash));
            // write value
            writeBatch.put(leafKeyToValueMap, getLeafKeyBytes(key), getLeafValueBytes(value));
        } catch (RocksDBException e) {
            throw new IOException("Problem adding Leaf",e);
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
    public void addLeaf(Object handle, long path, K key, V value, Hash hash) throws IOException {
        if (path < 0) throw new IllegalArgumentException("path is less than 0");
        if (hash == null) throw new IllegalArgumentException("Can not save null hash for leaf at path ["+path+"]");
        if (key == null) throw new IllegalArgumentException("Can not save null key for leaf at path ["+path+"]");
        final WriteBatch writeBatch = (WriteBatch) handle;
        final byte[] pathBytes = getPathBytes(path);
        final byte[] keyBytes = getLeafKeyBytes(key);
        try {
            // write hash
            writeBatch.put(pathToHashMap,pathBytes, getHashBytes(hash));
            // write key -> path
            writeBatch.put(leafKeyToPathMap, keyBytes, pathBytes);
            // write path -> key
            writeBatch.put(leafPathToKeyMap, pathBytes, keyBytes);
            // write value
            writeBatch.put(leafKeyToValueMap, keyBytes, getLeafValueBytes(value));
        } catch (RocksDBException e) {
            throw new IOException("Problem adding Leaf",e);
        }
    }

    //==================================================================================================================
    // Private Utils

    private K getLeafKey(byte[] leafKeyBytes) throws IOException{
        final int keySerializationVersion =  ((leafKeyBytes[0] << 24) + (leafKeyBytes[1] << 16) + (leafKeyBytes[2] << 8) + leafKeyBytes[3]);
        K key = keyConstructor.get();
        key.deserialize(ByteBuffer.wrap(leafKeyBytes,Integer.BYTES,keySizeBytes),keySerializationVersion);
        return key;
    }

    private byte[] getLeafKeyBytes(K key) throws IOException {
        byte[] leafKey = this.leafKey.get();
        final int keySerializationVersion = key.getVersion();
        leafKey[0] = (byte)(keySerializationVersion >>> 24);
        leafKey[1] = (byte)(keySerializationVersion >>> 16);
        leafKey[2] = (byte)(keySerializationVersion >>>  8);
        leafKey[3] = (byte)(keySerializationVersion);
        key.serialize(ByteBuffer.wrap(leafKey,Integer.BYTES,keySizeBytes));
        return leafKey;
    }

    private V getLeafValue(byte[] leafValueBytes) throws IOException{
        final int valueSerializationVersion =  ((leafValueBytes[0] << 24) + (leafValueBytes[1] << 16) + (leafValueBytes[2] << 8) + leafValueBytes[3]);
        V value = valueConstructor.get();
        value.deserialize(ByteBuffer.wrap(leafValueBytes,Integer.BYTES,valueSizeBytes),valueSerializationVersion);
        return value;
    }

    private byte[] getLeafValueBytes(V value) throws IOException {
        byte[] leafValue = this.leafValue.get();
        final int valueSerializationVersion = value.getVersion();
        leafValue[0] = (byte)(valueSerializationVersion >>> 24);
        leafValue[1] = (byte)(valueSerializationVersion >>> 16);
        leafValue[2] = (byte)(valueSerializationVersion >>>  8);
        leafValue[3] = (byte)(valueSerializationVersion);
        value.serialize(ByteBuffer.wrap(leafValue,Integer.BYTES, valueSizeBytes));
        return leafValue;
    }

    private long getPath(byte[] pathBytes) {
        return (((long)pathBytes[0] << 56) +
                ((long)(pathBytes[1] & 255) << 48) +
                ((long)(pathBytes[2] & 255) << 40) +
                ((long)(pathBytes[3] & 255) << 32) +
                ((long)(pathBytes[4] & 255) << 24) +
                ((pathBytes[5] & 255) << 16) +
                ((pathBytes[6] & 255) <<  8) +
                ((pathBytes[7] & 255)));
    }

    private byte[] getPathBytes(long path) {
        byte[] pathBytes = this.pathBytes.get();
        pathBytes[0] = (byte)(path >>> 56);
        pathBytes[1] = (byte)(path >>> 48);
        pathBytes[2] = (byte)(path >>> 40);
        pathBytes[3] = (byte)(path >>> 32);
        pathBytes[4] = (byte)(path >>> 24);
        pathBytes[5] = (byte)(path >>> 16);
        pathBytes[6] = (byte)(path >>>  8);
        pathBytes[7] = (byte)(path);
        return pathBytes;
    }

    private Hash getHash(byte[] hashBytes) {
        final int digestTypeID = ((hashBytes[0] & 0xFF) << 24) |
                ((hashBytes[1] & 0xFF) << 16) |
                ((hashBytes[2] & 0xFF) << 8 ) |
                ((hashBytes[3] & 0xFF));
        final DigestType digestType = DigestType.valueOf(digestTypeID);
        final byte[] hash = new byte[digestType.digestLength()];
        System.arraycopy(hashBytes,Integer.BYTES,hash,0,hash.length);
        return new DirectHash(hash,digestType);
    }

    private byte[] getHashBytes(Hash hash) {
        byte[] hashData = this.hashData.get();
        final int digestTypeId = hash.getDigestType().id();
        hashData[0] = (byte)(digestTypeId >>> 24);
        hashData[1] = (byte)(digestTypeId >>> 16);
        hashData[2] = (byte)(digestTypeId >>>  8);
        hashData[3] = (byte)(digestTypeId);
        System.arraycopy(hash.getValue(),0,hashData,Integer.BYTES,hash.getDigestType().digestLength());
        return hashData;
    }

    public static final class DirectHash extends Hash {
        public DirectHash(byte[] bytes,DigestType digestType) {
            super(bytes, digestType, true, false);
        }
    }
}

