package com.hedera.services.state.merkle.virtual.tree;

import com.hedera.services.state.merkle.virtual.VirtualKey;
import com.hedera.services.state.merkle.virtual.VirtualValue;
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
     * The key associated with this leaf node. The key will never be null
     * and never changes over time.
     */
    private final K key;

    /**
     * The data associated with this leaf node. The data should never be null,
     * but can change over time.
     */
    private V data;

    /**
     * Creates a new VirtualTreeLeaf
     */
    public VirtualTreeLeaf(K key) {
        this.key = key;
    }

    public VirtualTreeLeaf(Hash hash, VirtualTreePath path, K key, V data) {
        super(hash, path);
        this.key = key;
        this.data = data;
    }

    public K getKey() {
        return key;
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

    @Override
    public void walk(VirtualVisitor<K, V> visitor) {
        visitor.visitLeaf(this);
    }

    @Override
    public void walkDirty(VirtualVisitor<K, V> visitor) {
        if (isDirty()) {
            visitor.visitLeaf(this);
        }
    }
}
