package com.hedera.services.state.merkle.virtual;

import com.swirlds.common.crypto.Hashable;
import com.swirlds.common.io.SelfSerializable;

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
public interface VirtualTreeNode<K, V extends SelfSerializable> extends Hashable {
    /**
     * Gets the parent node of this tree node, if there is one. A detached node
     * (one not part of any tree) or a root node will return null.
     *
     * @return Gets the parent, or null if there is not one.
     */
    VirtualTreeInternal<K, V> getParent();

    /**
     * <p>Gets the path from root for this tree node.</p>
     *
     * <p>The path may change over time for a single node as that node is moved
     * around within the tree. The Path therefore cannot be cached. If the node
     * is detached and not a {@link VirtualTreeInternal}, then it will return null.</p>
     *
     * @return Gets the path from root to this node. May be null if the node is detached.
     */
    Path getPath();

    /**
     * Adopts this node to the given parent. Allows the node to update its parent pointer.
     *
     * @param path The new path for this node. Never null.
     * @param parent The new parent for this node. Probably never null.... TODO I don't think it will be...
     */
    void adopt(Path path, VirtualTreeInternal<K, V> parent);
}
