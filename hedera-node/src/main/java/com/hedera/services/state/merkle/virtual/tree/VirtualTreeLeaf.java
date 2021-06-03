package com.hedera.services.state.merkle.virtual.tree;

import com.hedera.services.state.merkle.virtual.Path;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.crypto.Hashable;

import java.util.Objects;

/**
 * <p>A leaf node in the virtual tree. A leaf node has no children and a single
 * parent. A newly created leaf may be <strong>detached</strong>, which means
 * that it is not part of any tree.</p>
 */
public final class VirtualTreeLeaf<K, V extends Hashable> extends VirtualTreeNode<K, V> {
    /**
     * The data associated with this leaf node. The data should never be null,
     * but can change over time.
     */
    private V data;

    /**
     * Creates a new VirtualTreeLeaf
     */
    public VirtualTreeLeaf() {
    }

    public VirtualTreeLeaf(Hash hash, Path path, V data) {
        super(hash, path);
        this.data = data;
    }

    /**
     * Set the data for this tree leaf. This causes the hash code for this leaf,
     * and all of its parents all the way to root to be invalidated.
     *
     * @param data The data, which can be null.
     */
    public void setData(V data) {
        if (!Objects.equals(this.data, data)) {
            this.data = data;
            invalidateHash();
        }
    }

    /**
     * Gets the data for this leaf.
     *
     * @return The data, which may be null.
     */
    public V getData() {
        return data;
    }

    @Override
    protected void recomputeHash() {
        setHash(data.getHash());
    }
}
