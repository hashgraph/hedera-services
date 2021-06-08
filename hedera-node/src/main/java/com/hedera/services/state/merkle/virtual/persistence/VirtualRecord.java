package com.hedera.services.state.merkle.virtual.persistence;

import com.hedera.services.state.merkle.virtual.VirtualKey;
import com.hedera.services.state.merkle.virtual.VirtualValue;
import com.swirlds.common.crypto.CryptoFactory;
import com.swirlds.common.crypto.DigestType;
import com.swirlds.common.crypto.Hash;

import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 */
public final class VirtualRecord {
    private static final Hash NULL_HASH = CryptoFactory.getInstance().getNullHash();

    private long path;
    private Future<Hash> hash;
    private final VirtualKey key;
    private VirtualValue value;
    private boolean dirty = false;

    /**
     * Creates a VirtualRecord suitable for a VirtualTreeLeaf.
     *
     * @param hash The hash associated with the data. Cannot be null.
     * @param path The path to the node. Cannot be null.
     * @param key The key. Cannot be null.
     * @param value The value. May be null.
     */
    public VirtualRecord(Hash hash, long path, VirtualKey key, VirtualValue value) {
        this.hash = new ImmutableFuture<>(Objects.requireNonNull(hash));
        this.path = path;
        this.key = Objects.requireNonNull(key);
        this.value = value;
    }

    /**
     * Gets the path for this node. This is never null.
     *
     * @return A non-null path.
     */
    public long getPath() {
        return path;
    }

    public void setPath(long path) {
        this.path = path;
        this.dirty = true;
    }

    /**
     * Gets the Hash. This will never be null.
     *
     * @return The non-null hash.
     */
    public Hash getHash() {
        try {
            return hash.get();
        } catch (InterruptedException | ExecutionException e) {
            // TODO not sure what to do here.
            e.printStackTrace();
            return null;
        }
    }

    public Future<Hash> getFutureHash() {
        return hash;
    }

    /**
     * Gets the key. This is null for non-leaf nodes.
     *
     * @return The key. May be null if this is a leaf node, otherwise it is never null.
     */
    public VirtualKey getKey() {
        return key;
    }

    /**
     * Gets the value. This may be null.
     *
     * @return The value. May be null.
     */
    public VirtualValue getValue() {
        return value;
    }

    public void setValue(VirtualValue value) {
        this.value = value;
        this.dirty = true;

        // Create a background job to hash the darn thing.
        this.hash = value == null ?
                new ImmutableFuture<>(NULL_HASH) :
                new ImmutableFuture<>(value.getHash());
    }

    public boolean isDirty() {
        return dirty;
    }

    public void makeDirty() {
        this.dirty = true;
    }
}
