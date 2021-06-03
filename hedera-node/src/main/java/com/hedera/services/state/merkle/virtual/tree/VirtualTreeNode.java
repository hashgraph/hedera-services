package com.hedera.services.state.merkle.virtual.tree;

import com.swirlds.common.crypto.CryptoFactory;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.crypto.Hashable;

import java.nio.file.Path;
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
 *
 * TODO I'd like this to be a sealed class so it can only be extended by classes in this package.
 */
public abstract class VirtualTreeNode<K, V extends Hashable> {
    static final Hash NULL_HASH = CryptoFactory.getInstance().getNullHash();

    // Note, if hash is invalidated, the tree becomes dirty. But the hash may be recomputed
    // so it is no longer NULL_HASH but the tree is still dirty. It just means we need to
    // write the state in the end.
    private boolean dirty = false;

    /**
     * The path from the root to this node. This path can and will change as
     * the tree is modified (leaves added and removed). Whenever it changes,
     * we have to update the dataSource accordingly.
     */
    private VirtualTreePath path = VirtualTreePath.ROOT_PATH;

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

    protected VirtualTreeNode(Hash defaultHash, VirtualTreePath path) {
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
    public final VirtualTreePath getPath() {
        return path;
    }

    public boolean isDirty() {
        return dirty;
    }

    // Walks up the tree marking everything as dirty
    public void makeDirty() {
        var node = this;
        while (node != null && !node.dirty) {
            node.dirty = true;
            node = node.parent;
        }
    }

    /**
     * Adopts this node to the given parent. Allows the node to update its parent pointer.
     *
     * @param path The new path for this node. Never null.
     * @param parent The new parent for this node. Probably never null.... TODO I don't think it will be...
     */
    protected void adopt(VirtualTreePath path, VirtualTreeInternal<K, V> parent) {
        this.path = Objects.requireNonNull(path);
        this.parent = Objects.requireNonNull(parent);
        this.invalidateHash();
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
     * Set the hash. To be called by the node during {@link #recomputeHash()}.
     * The set hash must not be null or the NULL_HASH.
     *
     * @param hash The hash. Must not be null.
     */
    protected final void setHash(Hash hash) {
        if (hash == NULL_HASH) {
            throw new IllegalArgumentException("Cannot use the NULL_HASH");
        }

        this.hash = Objects.requireNonNull(hash);
    }

    /**
     * Invalidates the hash from this node all the way up for each of its parent nodes.
     * Invalid hashes are the same as NULL or empty hashes (since no real node should have a NULL
     * hash as long as it has at least one child). This method assumes that once it encounters
     * a NULL_HASH it doesn't need to walk any farther up the tree.
     */
    protected final void invalidateHash() {
        var node = this;
        while (node != null && node.hash != NULL_HASH) {
            node.hash = NULL_HASH;
            node.dirty = true;
            node = node.parent;
        }
    }

    /**
     * Computes the hash and calls {@link #setHash(Hash)}.
     */
    protected abstract void recomputeHash();

    /**
     * Walks the tree starting from this node using post-order traversal, invoking the visitor
     * for each node visited.
     *
     * @param visitor The visitor. Cannot be null.
     */
    public abstract void walk(VirtualVisitor<K, V> visitor);

    /**
     * Walks the tree starting from this node using pre-order traversal, invoking the visitor
     * for each node visited. Only the dirty nodes are visited.
     *
     * TODO how does this work with deleted nodes? If we delete a node, we somehow need to
     * visit it too. Maybe this is the wrong way to do it.
     *
     * @param visitor The visitor. Cannot be null.
     */
    public abstract void walkDirty(VirtualVisitor<K, V> visitor);
}
