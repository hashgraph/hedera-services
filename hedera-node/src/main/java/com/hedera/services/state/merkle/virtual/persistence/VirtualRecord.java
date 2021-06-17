package com.hedera.services.state.merkle.virtual.persistence;

import com.hedera.services.state.merkle.virtual.VirtualKey;
import com.hedera.services.state.merkle.virtual.VirtualValue;
import com.swirlds.common.crypto.Hash;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

/**
 */
public final class VirtualRecord {
    private long path;
    private Future<Hash> futureHash;
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
        this.futureHash = complete(hash);
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
            return futureHash.get();
        } catch (InterruptedException | ExecutionException e) {
            // TODO not sure what to do here.
            e.printStackTrace();
            return null;
        }
    }

    public Future<Hash> getFutureHash() {
        return futureHash;
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

        futureHash = complete(value == null ? null : value.getHash());
    }

    private Future<Hash> complete(Hash hash) {
        if (hash == null) {
            return null;
        }

        final var f = new CompletableFuture<Hash>();
        f.complete(hash);
        return f;
    }

    public boolean isDirty() {
        return dirty;
    }

    public void makeDirty() {
        this.dirty = true;
    }
}
