package com.hedera.services.state.merkle.virtual;

import com.hedera.services.state.merkle.virtual.persistence.FCVirtualMapHashStore;
import com.hedera.services.state.merkle.virtual.persistence.fcmmap.FCSlotIndexUsingMemMapFile;
import com.hedera.services.state.merkle.virtual.persistence.fcmmap.FCVirtualMapHashStoreImpl;
import com.hedera.services.state.merkle.virtual.persistence.fcmmap.MemMapSlotStore;
import com.swirlds.common.crypto.Hash;
import com.swirlds.fcmap.FCHashStore;

import java.io.IOException;
import java.nio.file.Path;

public final class HashStoreImpl implements FCHashStore {
    private static final int BINS_PER_FILE = 128;
    private static final int UNIQUE_KEYS_PER_BIN = 8;
    private static final int DUP_KEYS_PER_BIN = 8;
    private static final int MUTATIONS_PER_KEY = 32; // Should only need 12, so a little extra for safety
    private static final int DATA_FILE_SIZE_MB = 32;
    private static final int BINS_PER_LOCK = 16;

    private final FCVirtualMapHashStore<PathKey> dataStore;
    private boolean immutable = false;
    private boolean released = false;

    public HashStoreImpl(String name, int maxKeys) {
        final var keysPerFile = BINS_PER_FILE * UNIQUE_KEYS_PER_BIN;
        final var num = (maxKeys / keysPerFile) + 1;
        final var numFiles = Integer.highestOneBit(num) << 1; // The next highest power of 2.

        try {
            final var index = new FCSlotIndexUsingMemMapFile<PathKey>(
                    Path.of("data/" + name + "/hash-index"),
                    "hash-index",
                    BINS_PER_FILE * numFiles,
                    numFiles,
                    PathKey.SERIALIZED_SIZE,
                    UNIQUE_KEYS_PER_BIN + DUP_KEYS_PER_BIN,
                    MUTATIONS_PER_KEY,
                    BINS_PER_LOCK);

            this.dataStore = new FCVirtualMapHashStoreImpl<>(
                    Path.of("data/" + name),
                    DATA_FILE_SIZE_MB,
                    PathKey.SERIALIZED_SIZE,
                    index,
                    MemMapSlotStore::new);
        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    private HashStoreImpl(HashStoreImpl source) {
        this.dataStore = source.dataStore.copy();
        source.immutable = true;
        released = false;
        immutable = false;
    }

    private PathKey createKey(long path) {
        return new PathKey(path);
    }

    @Override
    public Hash loadHash(long path) {
        try {
            return dataStore.loadHash(createKey(path));
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    @Override
    public void saveHash(long path, Hash hash) {
        try {
            dataStore.saveHash(createKey(path), hash);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void deleteHash(long path) {
        try {
            dataStore.deleteHash(createKey(path));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void release() {
        dataStore.release();
        released = true;
    }

    @Override
    public HashStoreImpl copy() {
        return new HashStoreImpl(this);
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
