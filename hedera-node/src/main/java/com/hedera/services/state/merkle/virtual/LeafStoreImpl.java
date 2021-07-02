package com.hedera.services.state.merkle.virtual;

import com.hedera.services.state.merkle.virtual.persistence.FCVirtualMapLeafStore;
import com.hedera.services.state.merkle.virtual.persistence.fcmmap.FCSlotIndexUsingMemMapFile;
import com.hedera.services.state.merkle.virtual.persistence.fcmmap.FCVirtualMapLeafStoreImpl;
import com.hedera.services.state.merkle.virtual.persistence.fcmmap.MemMapSlotStore;
import com.swirlds.common.io.SelfSerializable;
import com.swirlds.fcmap.FCLeafStore;
import com.swirlds.fcmap.FCVirtualRecord;

import java.io.IOException;
import java.nio.file.Path;
import java.util.function.Supplier;

public final class LeafStoreImpl<V extends SelfSerializable> implements FCLeafStore<IdKey, V> {
    private static final int BINS_PER_INDEX_FILE = 128;
    private static final int UNIQUE_KEYS_PER_BIN = 8;
    private static final int DUP_KEYS_PER_BIN = 8;
    private static final int MUTATIONS_PER_KEY = 32; // Should only need 12, so a little extra for safety
    private static final int DATA_FILE_SIZE_MB = 32;
    private static final int BINS_PER_LOCK = 16;

    private final FCVirtualMapLeafStore<IdKey, PathKey, V> dataStore;
    private boolean immutable;
    private boolean released;

    public LeafStoreImpl(String name, int maxEntities, int valueSize, Supplier<V> valueConstructor) {
        final var keysPerIndexFile = BINS_PER_INDEX_FILE * UNIQUE_KEYS_PER_BIN;
        final var num = (maxEntities / keysPerIndexFile) + 1;
        final var numIndexFiles = Integer.highestOneBit(num) << 1; // The next highest power of 2.

        try {
            final var leafPathIndex = new FCSlotIndexUsingMemMapFile<PathKey>(
                    Path.of("data/" + name + "/leaf-path-index"),
                    "leaf-path-index",
                    BINS_PER_INDEX_FILE * numIndexFiles,
                    numIndexFiles,
                    PathKey.SERIALIZED_SIZE,
                    UNIQUE_KEYS_PER_BIN + DUP_KEYS_PER_BIN,
                    MUTATIONS_PER_KEY,
                    BINS_PER_LOCK);

            final var leafKeyIndex = new FCSlotIndexUsingMemMapFile<IdKey>(
                    Path.of("data/" + name + "/leaf-key-index"),
                    "leaf-key-index",
                    BINS_PER_INDEX_FILE * numIndexFiles,
                    numIndexFiles,
                    IdKey.SERIALIZED_SIZE,
                    UNIQUE_KEYS_PER_BIN + DUP_KEYS_PER_BIN,
                    MUTATIONS_PER_KEY,
                    BINS_PER_LOCK);

            this.dataStore = new FCVirtualMapLeafStoreImpl<>(
                    Path.of("data/" + name),
                    DATA_FILE_SIZE_MB,
                    IdKey.SERIALIZED_SIZE,
                    PathKey.SERIALIZED_SIZE,
                    valueSize,
                    leafPathIndex,
                    leafKeyIndex,
                    IdKey::new, // key constructor
                    PathKey::new, // path constructor
                    valueConstructor, // value constructor
                    MemMapSlotStore::new);
        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    protected LeafStoreImpl(LeafStoreImpl<V> source) {
        this.dataStore = source.dataStore.copy();
        source.immutable = true;
        released = false;
        immutable = false;
    }

    @Override
    public long leafCount() {
        return dataStore.leafCount();
    }

    @Override
    public FCVirtualRecord<IdKey, V> loadLeafRecordByPath(long path) {
        try {
            var record = dataStore.loadLeafRecordByPath(new PathKey(path));
            return record == null ? null : new FCVirtualRecord<>(record.getKey(), record.getValue());
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    @Override
    public Long loadLeafPathByKey(IdKey key) {
        try {
            PathKey path = dataStore.loadLeafPathByKey(key);
            return path == null ? null : path.getPath();
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    @Override
    public V loadLeafValueByKey(IdKey key) {
        try {
            return dataStore.loadLeafValueByKey(key);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    @Override
    public void saveLeaf(IdKey key, long path, V value) {
        try {
            dataStore.saveLeaf(key, new PathKey(path), value);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void deleteLeaf(IdKey key, long path) {
        try {
            dataStore.deleteLeaf(key, new PathKey(path));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void updatePath(long oldPath, long newPath) {
        try {
            dataStore.updateLeafPath(new PathKey(oldPath), new PathKey(newPath));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public LeafStoreImpl<V> copy() {
        return new LeafStoreImpl<>(this);
    }

    @Override
    public void release() {
        dataStore.release();
        released = true;
    }

    @Override
    public boolean isImmutable() {
        return immutable;
    }

    @Override
    public boolean isReleased() {
        return released;
    }
}
