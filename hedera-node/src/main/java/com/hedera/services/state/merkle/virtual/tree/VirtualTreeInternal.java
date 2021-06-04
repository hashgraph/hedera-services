package com.hedera.services.state.merkle.virtual.tree;

import com.hedera.services.state.merkle.virtual.VirtualMap;
import com.swirlds.common.crypto.CryptoFactory;
import com.swirlds.common.crypto.DigestType;
import com.swirlds.common.crypto.Hash;

import java.util.ArrayDeque;
import java.util.Objects;

import static com.hedera.services.state.merkle.virtual.tree.VirtualTreePath.getLeftChildPath;
import static com.hedera.services.state.merkle.virtual.tree.VirtualTreePath.getRightChildPath;
import static com.hedera.services.state.merkle.virtual.tree.VirtualTreePath.isParentOf;

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
    public VirtualTreeInternal(Hash hash, long path) {
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
        leftChild.adopt(getLeftChildPath(getPath()), this);
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
        rightChild.adopt(getRightChildPath(getPath()), this);
    }

    @Override
    public void adopt(long path, VirtualTreeInternal parent) {
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

    /**
     * Walks the tree starting from this node taking the most direct route to the
     * target node. Calls the visitor for each step downward.
     *
     * @param target The path of the node we're trying to walk towards.
     * @param visitor The visitor. Cannot be null.
     */
    public final void walk(long target, VirtualVisitor visitor) {
        // Maybe I was the target! In that case, visit me and quit.
        if (getPath() == target) {
            visitor.visitParent(this);
            return;
        }

        // I wasn't the target and I'm not the parent of the target so quit.
        if (!isParentOf(getPath(), target)) {
            return;
        }

        var node = this;
        while (node != null) {
            final var path = node.getPath();
            // Visit the node
            visitor.visitParent(node);

            // If we've found the target, then we're done
            if (path == target) {
                break;
            }

            // We didn't find the target yet and `node` is a parent node,
            // so we need to go down either the left or right branch.
            VirtualTreeNode nextNode;
            final var leftPath = getLeftChildPath(path);
            if (isParentOf(leftPath, target) || leftPath == target) {
                if (node.leftChild == null) {
                    visitor.visitUncreated(leftPath);
                }
                nextNode = node.leftChild;
            } else {
                final var rightPath = getRightChildPath(path);
                if (isParentOf(rightPath, target) || rightPath == target) {
                    if (node.rightChild == null) {
                        visitor.visitUncreated(rightPath);
                    }
                    nextNode = node.rightChild;
                } else {
                    // Neither left or right, we're at a dead end.
                    break;
                }
            }

            if (nextNode instanceof VirtualTreeLeaf) {
                // We found the leaf. Visit and quit.
                visitor.visitLeaf((VirtualTreeLeaf) nextNode);
                break;
            } else {
                // iterate
                node = (VirtualTreeInternal) nextNode;
            }
        }
    }

    /**
     * Walks the tree starting from this node using pre-order traversal, invoking the visitor
     * for each node visited. Only the dirty nodes are visited.
     *
     * TODO how does this work with deleted nodes? If we delete a node, we somehow need to
     * visit it too. Maybe this is the wrong way to do it.
     *
     * @param visitor The visitor. Cannot be null.
     */
    public final void walkDirty(VirtualVisitor visitor) {
        // We need a stack to keep track of which node to process next
        final var deque = new ArrayDeque<VirtualTreeNode>(64);
        deque.push(this);
        while (!deque.isEmpty()) {
            // In pre-order traversal, this node is visited first.
            final var node = deque.pop();
            final var pnode = node instanceof VirtualTreeInternal;
            if (pnode) {
                final var parent = (VirtualTreeInternal) node;
                visitor.visitParent(parent);

                // Push the right first, and then the left, so that we process
                // the left branch first as we pop off the stack.
                final var right = parent.rightChild;
                if (right != null) {
                    deque.push(right);
                }

                final var left = parent.leftChild;
                if (left != null) {
                    deque.push(left);
                }
            } else {
                visitor.visitLeaf((VirtualTreeLeaf) node);
            }
        }
    }
}
