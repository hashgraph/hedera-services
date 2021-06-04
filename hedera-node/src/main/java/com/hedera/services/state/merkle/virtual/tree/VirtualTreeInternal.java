package com.hedera.services.state.merkle.virtual.tree;

import com.hedera.services.state.merkle.virtual.VirtualMap;
import com.swirlds.common.crypto.CryptoFactory;
import com.swirlds.common.crypto.DigestType;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.crypto.Hashable;

import java.util.Objects;

/**
 * An internal (i.e. parent) node in the virtual tree. This node is just a holder of information,
 * all of the tree building / modifying logic is held in the {@link VirtualMap}.
 */
public final class VirtualTreeInternal extends VirtualTreeNode {
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
     */
    public VirtualTreeInternal() {
    }

    /**
     * Create a new VirtualTreeInternal with the given initial hash value.
     *
     * @param hash The default hash. Cannot be null.
     * @param path The default path. Cannot be null.
     */
    public VirtualTreeInternal(Hash hash, VirtualTreePath path) {
        super(hash, path);
    }

    /**
     * Gets the left child of this parent node. If there is a right child,
     * then there is also always a left child.
     *
     * @return The left child, which can be null if there are no children.
     */
    public VirtualTreeNode getLeftChild() {
        return leftChild;
    }

    /**
     * Sets the left child.
     *
     * @param child The child. Cannot be null.
     */
    public void setLeftChild(VirtualTreeNode child) {
        leftChild = Objects.requireNonNull(child);
        leftChild.adopt(getPath().getLeftChildPath(), this);
    }

    /**
     * Gets the right child of this parent node.
     *
     * @return Gets the right child, which can be null
     */
    public VirtualTreeNode getRightChild() {
        return rightChild;
    }

    /**
     * Sets the right child. This cannot be null.
     *
     * @param child The right child, which cannot be null.
     */
    public void setRightChild(VirtualTreeNode child) {
        rightChild = Objects.requireNonNull(child);
        rightChild.adopt(getPath().getRightChildPath(), this);
    }

    @Override
    public void adopt(VirtualTreePath path, VirtualTreeInternal parent) {
        // TODO What happens if I have children? Do I modify them as well? Or throw?
        if (leftChild != null || rightChild != null) {
            throw new IllegalStateException("Refusing adoption, this internal node has children");
        }

        super.adopt(path, parent);
    }

    @Override
    protected void recomputeHash() {
        final var crypto = CryptoFactory.getInstance();
        final var newHash = crypto.calcRunningHash(
                leftChild == null ? NULL_HASH : leftChild.hash(),
                rightChild == null ? NULL_HASH : rightChild.hash(),
                DigestType.SHA_384);
        setHash(newHash);
    }

    @Override
    public void walk(VirtualVisitor visitor) {
        // Let the visitor know we hit a dead end. The visitor *might*
        // create a child at this time.
        if (leftChild == null) {
            visitor.visitUncreated(getPath().getLeftChildPath());
        }

        // The child may have been created by the visitor, so we should try to
        // visit it again.
        if (leftChild != null) {
            leftChild.walk(visitor);
        }

        // Let the visitor know we hit a dead end.
        if (rightChild == null) {
            visitor.visitUncreated(getPath().getRightChildPath());
        }

        // The child may have been created by the visitor, so try again.
        if (rightChild != null) {
            rightChild.walk(visitor);
        }

        // In post-order traversal, this node is visited last.
        visitor.visitParent(this);
    }

    @Override
    public void walk(VirtualTreePath target, VirtualVisitor visitor) {
        final var path = getPath();
        if (path.equals(target)) {
            visitor.visitParent(this);
        } else if (target != null && path.isParentOf(target)) {
            // If the target is null, or before this node, or on a completely
            // different branch, then we bail.
            // TODO I don't really want to do this on every iteration. Dude.
            visitor.visitParent(this);
            final var leftPath = path.getLeftChildPath();
            if (leftPath.isParentOf(target) || leftPath.equals(target)) {
                if (leftChild == null) {
                    visitor.visitUncreated(leftPath);
                }

                if (leftChild != null) {
                    leftChild.walk(target, visitor);
                }
            } else {
                final var rightPath = path.getRightChildPath();
                if (rightPath.isParentOf(target) || rightPath.equals(target)) {
                    if (rightChild == null) {
                        visitor.visitUncreated(rightPath);
                    }

                    if (rightChild != null) {
                        rightChild.walk(target, visitor);
                    }
                }
            }
        }
    }

    @Override
    public void walkDirty(VirtualVisitor visitor) {
        // In pre-order traversal, this node is visited first.
        if (isDirty()) {
            visitor.visitParent(this);

            if (leftChild != null) {
                leftChild.walkDirty(visitor);
            }

            if (rightChild != null) {
                rightChild.walkDirty(visitor);
            }
        }
    }
}
