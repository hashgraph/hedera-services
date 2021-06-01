package com.hedera.services.state.merkle.virtual;

import com.swirlds.common.AbstractHashable;

import java.util.Objects;

/**
 * An internal (i.e. parent) node in the virtual tree. This node is just a holder of information,
 * all of the tree building / modifying logic is held in the {@link VirtualTreeMap}.
 */
public class VirtualTreeInternal extends AbstractHashable implements VirtualTreeNode {
    /**
     * The dataSource that saves all the important information about
     * this tree node.
     */
    private final VirtualDataSource dataSource;

    /**
     * The path from the root to this node. This path can and will change as
     * the tree is modified (leaves added and removed). Whenever it changes,
     * we have to update the dataSource accordingly.
     */
    private Path path;

    /**
     * The parent of this node. This may be null either if this node is unattached
     * to a tree, or if it is the root node. If this is not the root, then it can
     * move around as the tree is modified.
     */
    private VirtualTreeInternal parent;

    /**
     * The left child, or null if there isn't one.
     */
    private VirtualTreeNode leftChild;

    /**
     * The right child, or null if there isn't one.
     */
    private VirtualTreeNode rightChild;

    /**
     * Create a new VirtualTreeInternal. As created, it is suitable as
     * a root node, but it can be adopted into a tree.
     *
     * @param dataSource the required data source backing this node.
     */
    public VirtualTreeInternal(VirtualDataSource dataSource) {
        this.path = Path.ROOT_PATH;
        this.dataSource = Objects.requireNonNull(dataSource);
    }

    @Override
    public Path getPath() {
        return path;
    }

    @Override
    public void adopt(Path path, VirtualTreeInternal parent) {
        // TODO What happens if I have children? Do I modify them as well? Or throw?
        if (leftChild != null || rightChild != null) {
            throw new IllegalStateException("Refusing adoption, this internal node has children");
        }

        // The super class should have made sure the hash was empty. The datasource is
        // out of sync, but that's OK for internal nodes because we only look up the
        // hash when we're realizing, not when it already exists, and we will write
        // the updated hash as part of the save mechanism when the hash is re-computed.
        this.path = path;
        this.parent = parent;
    }

    @Override
    public VirtualTreeInternal getParent() {
        return parent;
    }

    public VirtualTreeNode getLeftChild() {
        return leftChild;
    }

    public void setLeftChild(VirtualTreeNode child) {
        leftChild = child;
        leftChild.adopt(path.getLeftChildPath(), this);
    }

    public VirtualTreeNode getRightChild() {
        return rightChild;
    }

    public void setRightChild(VirtualTreeNode child) {
        rightChild = child;
        rightChild.adopt(path.getRightChildPath(), this);
    }
}
