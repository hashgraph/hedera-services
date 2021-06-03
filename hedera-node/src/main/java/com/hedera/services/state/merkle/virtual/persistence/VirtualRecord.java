package com.hedera.services.state.merkle.virtual.persistence;

import com.hedera.services.state.merkle.virtual.Path;
import com.swirlds.common.crypto.CryptoFactory;
import com.swirlds.common.crypto.Hash;

import java.util.Objects;

/**
 * An immutable record of a node in the Virtual Tree. It represents both
 * internal (parent) nodes and leaf nodes. Different constructors exist
 * for the different types of nodes. Leaf nodes always include a key.
 */
public final class VirtualRecord<K, V> {
    private static final Hash NULL_HASH = CryptoFactory.getInstance().getNullHash();

    private final Path path;
    private final Hash hash;
    private final K key;
    private final V value;

    /**
     * Creates a VirtualRecord suitable for a VirtualTreeLeaf.
     *
     * @param hash The hash associated with the data. Cannot be null.
     * @param path The path to the node. Cannot be null.
     * @param key The key. Cannot be null.
     * @param value The value. May be null.
     */
    public VirtualRecord(Hash hash, Path path, K key, V value) {
        this.hash = Objects.requireNonNull(hash);
        this.path = Objects.requireNonNull(path);
        this.key = Objects.requireNonNull(key);
        this.value = value;
    }

    /**
     * Creates a VirtualRecord suitable for a VirtualTreeInternal.
     *
     * @param hash The hash associated with the data. Cannot be null.
     * @param path The path to the node. Cannot be null.
     */
    public VirtualRecord(Hash hash, Path path) {
        this.hash = Objects.requireNonNull(hash);
        this.path = Objects.requireNonNull(path);
        this.key = null;
        this.value = null;
    }

    /**
     * Gets the path for this node. This is never null.
     *
     * @return A non-null path.
     */
    public Path getPath() {
        return path;
    }

    /**
     * Gets the Hash. This will never be null.
     *
     * @return The non-null hash.
     */
    public Hash getHash() {
        return hash;
    }

    /**
     * Gets the key. This is null for non-leaf nodes.
     *
     * @return The key. May be null if this is a leaf node, otherwise it is never null.
     */
    public K getKey() {
        return key;
    }

    /**
     * Gets the value. This may be null.
     *
     * @return The value. May be null.
     */
    public V getValue() {
        return value;
    }

    /**
     * Gets whether this record represents a leaf node.
     *
     * @return Whether this record represents a leaf node. Checks whether the key is null.
     */
    public boolean isLeaf() {
        return key != null;
    }

    /**
     * Creates a new VirtualRecord identical to the first, except with an invalidated (null) hash.
     * @return A non-null virtual record that is invalidated in the hash.
     */
    public VirtualRecord<K, V> invalidateHash() {
        return isLeaf() ?
                new VirtualRecord<>(NULL_HASH, path, key, value) :
                new VirtualRecord<>(NULL_HASH, path);
    }

    /**
     * Returns a new record that has an invalid hash and the given value, but the same key and path as before.
     * This can only be called on a leaf record.
     *
     * @param value The new value. Can be null.
     * @return A non-null virtual record.
     */
    public VirtualRecord<K, V> withValue(V value) {
        if (!isLeaf()) {
            throw new IllegalStateException("Cannot call this method on non-leaf records");
        }

        return new VirtualRecord<>(NULL_HASH, path, key, value);
    }

    /**
     * Gets a new record with the same values as before but with the given path, and its hash invalidated
     * if it is a non-leaf node.
     *
     * @param path The path that has changed.
     * @return A non-null virtual record
     */
    public VirtualRecord<K, V> withPath(Path path) {
        return isLeaf() ?
                new VirtualRecord<>(hash, path, key, value) :
                new VirtualRecord<>(NULL_HASH, path);
    }
}
