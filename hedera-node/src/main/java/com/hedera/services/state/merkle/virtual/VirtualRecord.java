package com.hedera.services.state.merkle.virtual;

import com.swirlds.common.crypto.Hash;

import java.util.Objects;

/**
 * Stores information related to a tree node. All tree nodes have a Path and a Hash.
 */
public final class VirtualRecord {
    private final Path path;
    private final Hash hash;
    private final Block key;

    /**
     * Creates a VirtualRecord suitable for a VirtualTreeLeaf.
     *
     * @param hash The hash associated with the data. Can be null if no hash has been computed.
     * @param path The path to the node. Cannot be null.
     * @param key The key. This must be a 256 byte array.
     */
    public VirtualRecord(Hash hash, Path path, Block key) {
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

    public Block getKey() {
        return key;
    }
}
