package com.hedera.services.state.merkle.virtual.persistence.mmap;

import com.hedera.services.state.merkle.virtual.tree.VirtualTreePath;
import com.hedera.services.state.merkle.virtual.persistence.VirtualDataSource;
import com.hedera.services.state.merkle.virtual.persistence.VirtualRecord;
import com.hedera.services.state.merkle.virtual.persistence.mmap.Key;
import com.hedera.services.state.merkle.virtual.persistence.mmap.MemMapDataSource;
import com.swirlds.common.crypto.Hash;

import java.io.IOException;
import java.nio.file.Path;

public class EthDataSource implements VirtualDataSource<Key, EthValue> {
    private final MemMapDataSource dataStorage;
    private final MemMapDataSource pathToDataStorage;

    public EthDataSource(Path storageDirectory) {
        // The value size is made up of the VirtualTreePath, Hash, and EthValue.
        // VirtualTreePath is a byte + long (9 bytes)
        // Hash is 48 bytes
        // EthValue is 32 bytes
        // For a grand total of 9+48+32=89 bytes.
        this.dataStorage = new MemMapDataSource(
                32,
                89,
                512,
                storageDirectory,
                "eth",
                "dat");

        // Key is path (9 bytes) and value is a key (32 bytes)
        this.pathToDataStorage = new MemMapDataSource(
                9,
                32,
                512,
                storageDirectory,
                "eth",
                "idx");
    }

    @Override
    public VirtualRecord<Key, EthValue> getRecord(Key key) {
        // In theory, this buffer is starting of at position 0 and has 128 bytes.
        final var buf = dataStorage.get(key);
        if (buf == null) {
            return null;
        }

        if (buf.position() != 0) {
            System.err.println("Expected position 0");
        }
        if (buf.limit() != 128) {
            System.err.println("Expected limit of 128");
        }

        // Read off the path
        final var path = new VirtualTreePath(buf.get(), buf.getLong());

        // Read off the hash
        final var hashBytes = new byte[48];
        buf.get(hashBytes);
        final var hash = new Hash(hashBytes);

        // Read off the value
        final var data = new byte[32];
        buf.get(data);
        final var value = new EthValue(data);
        return new VirtualRecord<>(hash, path, key, value);
    }

    @Override
    public VirtualRecord<Key, EthValue> getRecord(VirtualTreePath path) {
        byte[] keyBytes = new byte[9];
        byte[0] = path.getRank();
        byte[1-8] = path.getPath();
        final var buf = pathToDataStorage.get(path);
        if (buf == null) {
            return null;
        }

        return null;
    }

    @Override
    public void deleteRecord(VirtualRecord<Key, EthValue> record) {

    }

    @Override
    public void setRecord(VirtualRecord<Key, EthValue> record) {

    }

    @Override
    public void writeLastLeafPath(VirtualTreePath path) {

    }

    @Override
    public VirtualTreePath getLastLeafPath() {
        return null;
    }

    @Override
    public void writeFirstLeafPath(VirtualTreePath path) {

    }

    @Override
    public VirtualTreePath getFirstLeafPath() {
        return null;
    }

    @Override
    public void close() throws IOException {

    }
}
