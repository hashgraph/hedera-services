package com.hedera.services.state.merkle.virtual;

import com.swirlds.common.AbstractHashable;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.crypto.Hashable;

import java.util.Objects;

/**
 * <p>A leaf node in the virtual tree. A leaf node has no children and a single
 * parent. A newly created leaf may be <strong>detached</strong>, which means
 * that it is not part of any tree.</p>
 */
public class VirtualTreeLeaf<K, V extends Hashable> extends AbstractHashable implements VirtualTreeNode<K, V> {
    /**
     * The dataSource that saves all the important information about
     * this tree leaf.
     */
    private final VirtualDataSource<K, V> dataSource;

    /**
     * The path from the root to this node. This path can and will change as
     * the tree is modified (leaves added and removed). Whenever it changes,
     * we have to update the dataSource accordingly.
     */
    private Path path;

    /**
     * The reference to the current parent. Any node attached to a graph
     * will have a non-null parent. The pointer can and will change over
     * time as the tree is modified.
     */
    private VirtualTreeInternal<K, V> parent;

    /**
     * The VirtualRecord that represents this leaf.
     */
    private final VirtualRecord<K> record;

    /**
     * The data associated with this leaf node. The data should never be null,
     * but can change over time.
     */
    private V data;

    public VirtualTreeLeaf(VirtualDataSource<K, V> dataSource, VirtualRecord<K> record) {
        this.dataSource = Objects.requireNonNull(dataSource);
        this.record = Objects.requireNonNull(record);
    }

    public void setData(V data) {
        this.data = data;
    }

    public V getData() {
        return data;
    }

    @Override
    public Path getPath() {
        return path;
    }

    @Override
    public void adopt(Path path, VirtualTreeInternal<K, V> parent) {
        // If the path has changed from what we expected in the record, then update the DB.
        if (!record.getPath().equals(path)) {
            dataSource.writeRecord(path, new VirtualRecord<>(getHash(), path, record.getKey()));
        }

        this.path = path;
        this.parent = parent;
        this.invalidateHash();
    }

    @Override
    public VirtualTreeInternal<K, V> getParent() {
        return parent;
    }

    @Override
    public Hash getHash() {
        // Only recompute the hash if it has changed. I will know it has changed
        // if the hash is null. This means that I *require* invalidateHash to have
        // been called if either child has been updated.
        final var currentHash = super.getHash();
        if (currentHash != null) {
            return currentHash;
        }

        // Recompute the hash
        final var newHash = data.getHash();
        setHash(newHash);
        dataSource.writeHash(path, newHash);
        return newHash;
    }

    @Override
    public void invalidateHash() {
        super.invalidateHash();

        if (parent != null) {
            parent.invalidateHash();
        }
    }
}
