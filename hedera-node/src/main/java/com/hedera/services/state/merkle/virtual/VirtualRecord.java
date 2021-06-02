package com.hedera.services.state.merkle.virtual;

import com.swirlds.common.crypto.Hash;

import java.util.Objects;

/**
 * Stores information related to a virtual tree node in the VirtualDataSource.
 */
public final class VirtualRecord<K> {
    private final Path path;
    private final Hash hash;
    private final K key;

    /**
     * Creates a VirtualRecord suitable for a VirtualTreeLeaf.
     *
     * @param hash The hash associated with the data. Can be null if no hash has been computed.
     * @param path The path to the node. Cannot be null.
     * @param key The key. Cannot be null.
     */
    public VirtualRecord(Hash hash, Path path, K key) {
        this.hash = hash;
        this.path = Objects.requireNonNull(path);
        this.key = Objects.requireNonNull(key);
    }

    public Path getPath() {
        return path;
    }

    public Hash getHash() {
        return hash;
    }

    public K getKey() {
        return key;
    }
}
