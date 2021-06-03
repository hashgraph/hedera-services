package com.hedera.services.state.merkle.virtual.tree;

import com.hedera.services.state.merkle.virtual.Path;
import com.swirlds.common.crypto.CryptoFactory;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.crypto.Hashable;

import java.util.Objects;

/**
 * <p>The "virtual" tree is a simple, lightweight, binary merkle tree used for
 * determining hashes for contract storage. It is not a MerkleNode because there is
 * no need to implement SerializableDet. The tree is made up of {@link VirtualTreeInternal}
 * (parent) nodes and {@link VirtualTreeLeaf} nodes.</p>
 *
 * <p>Although not part of this interface, all virtual tree nodes are backed by a
 * VirtualDataSource, and any change to the tree structure results in updates to the
 * data source.</p>
 */
public abstract class VirtualTreeNode<K, V extends Hashable> {
    static final Hash NULL_HASH = CryptoFactory.getInstance().getNullHash();

    /**
     * The path from the root to this node. This path can and will change as
     * the tree is modified (leaves added and removed). Whenever it changes,
     * we have to update the dataSource accordingly.
     */
    private Path path = Path.ROOT_PATH;

    /**
     * The hash for this node. For an internal node, this hash is the result of hashing
     * both the "left" and "right" children. For a leaf, it is the result of hashing
     * the data.
     */
    private Hash hash = NULL_HASH;

    /**
     * The parent of this node. This may be null either if this node is unattached
     * to a tree, or if it is the root node. If this is not the root, then it can
     * move around as the tree is modified.
     */
    private VirtualTreeInternal<K, V> parent = null;

    /**
     * Gets the parent node of this tree node, if there is one. A detached node
     * (one not part of any tree) or a root node will return null.
     *
     * @return Gets the parent, or null if there is not one.
     */
    public final VirtualTreeInternal<K, V> getParent() {
        return parent;
    }

    protected VirtualTreeNode() {

    }

    protected VirtualTreeNode(Hash defaultHash, Path path) {
        this.hash = Objects.requireNonNull(defaultHash);
        this.path = Objects.requireNonNull(path);
    }

    /**
     * <p>Gets the path from root for this tree node.</p>
     *
     * <p>The path may change over time for a single node as that node is moved
     * around within the tree. The Path therefore cannot be cached. If the node
     * is detached and not a {@link VirtualTreeInternal}, then it will return null.</p>
     *
     * @return Gets the path from root to this node. May be null if the node is detached.
     */
    public final Path getPath() {
        return path;
    }

    /**
     * Adopts this node to the given parent. Allows the node to update its parent pointer.
     *
     * @param path The new path for this node. Never null.
     * @param parent The new parent for this node. Probably never null.... TODO I don't think it will be...
     */
    protected void adopt(Path path, VirtualTreeInternal<K, V> parent) {
        this.path = Objects.requireNonNull(path);
        this.parent = Objects.requireNonNull(parent);
//        this.invalidateHash();
    }

    /**
     * Computes and returns the hash for this merkle node, which includes by definition
     * the hashes of all of its children.
     *
     * @return The hash. Will never be null.
     */
    public final Hash hash() {
        // If the hash is invalid, then we need to recompute it.
        if (this.hash == NULL_HASH) {
            recomputeHash();
        }

        return this.hash;
    }

    /**
     * Invalidates the hash from this node all the way up for
     * each of its parent nodes. Invalid hashes are the same as
     * NULL or empty hashes (since no real node should have a NULL
     * hash as long as it has at least one child).
     */
    protected final void invalidateHash() {
        this.hash = NULL_HASH;
        if (parent != null) {
            parent.invalidateHash();
        }
    }

    /**
     * Set the hash. To be called by the node during {@link #recomputeHash()}.
     *
     * @param hash The hash. Must not be null.
     */
    protected final void setHash(Hash hash) {
        this.hash = Objects.requireNonNull(hash);
    }

    /**
     * Computes the hash and calls {@link #setHash(Hash)}.
     */
    protected abstract void recomputeHash();
}
