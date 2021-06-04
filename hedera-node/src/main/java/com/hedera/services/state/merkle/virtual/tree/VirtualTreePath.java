package com.hedera.services.state.merkle.virtual.tree;

import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * Represents a path in a virtual tree from the root to a node. The path is
 * represented as a string of bits and a rank, such that each bit represents either
 * the left or right node as you traverse from the root node downwards. The root
 * node is found at rank 0, while the first level of children are found at rank 1,
 * and so forth down the tree. Every node in the tree has a unique path at any given
 * point in time.
 */
public final class VirtualTreePath implements Comparable<VirtualTreePath> {
    /** Site of an account when serialized to bytes */
    public static final int BYTES = Long.BYTES + 1;

    /**
     * A special constant that represents the Path of a root node. It isn't necessary
     * for this constant to be used, but it makes the code a little more readable.
     */
    public static final VirtualTreePath ROOT_PATH = new VirtualTreePath((byte)0, 0);

    /**
     *
     */
    public final byte rank;
    public final long path; // The path helps form the ID.

    public VirtualTreePath(byte rank, long path) {
        this.rank = rank;
        this.path = path;

        if (rank < 0 || rank >= 64) {
            throw new IllegalArgumentException("Rank must be between [0,63]");
        }
    }

    /**
     * Gets whether this path refers to the root node. The root node path is
     * special, since rank is 0. No leaf node or other internal node can
     * have a rank of 0.
     *
     * @return Whether this path refers to the root node.
     */
    public boolean isRoot() {
        return rank == 0;
    }

    // Gets the position of the node represented by this path at its rank. For example,
    // the node on the far left would be index "0" while the node on the far right
    // has an index depending on the rank (rank 1, index = 1; rank 2, index = 3; rank N, index = (2^N)-1)

    /**
     * Gets the zero-based index of the Path within its rank, where {@code 0} is the
     * left-most node at that rank, and the number increases going towards the right.
     *
     * @return A non-negative index of the node with this path within its rank.
     */
    public int getIndex() {
        int index = 0;
        byte power = (byte) (rank -1);
        long n = path;
        while (n != 0) {
            index += (n & 1) << power;
            n = n >> 1;
            power--;
        }

        return index;
    }

    /**
     * Gets the Path representing a node located at the given rank and the given index
     * within that rank. Throws an IllegalArgumentException if the index is greater than
     * the max possible for that rank.
     *
     * @param rank A non-negative rank for the node
     * @param index The non-negative index into the rank at which to find the node
     * @return A Path representing the path to the node found at the given rank and index. Never returns null.
     */
    public static VirtualTreePath getPathForRankAndIndex(byte rank, int index) {
        if (rank < 0) {
            throw new IllegalArgumentException("Rank must be strictly non-negative");
        }

        if (index < 0) {
            throw new IllegalArgumentException("Index must be strictly non-negative");
        }

        // TODO Throw an example if the index is too large for the rank.

        int path = 0;
        byte power = (byte) (rank-1);
        long n = index;
        while (n != 0) {
            path += (n & 1) << power;
            n = n >> 1;
            power--;
        }

        return new VirtualTreePath(rank, path);
    }

    /**
     * Gets whether this Path represents a left-side node.
     *
     * @return Whether this Path is a left-side node.
     */
    public boolean isLeft() {
        final var decisionMask = 1L << (rank - 1);
        return (path & decisionMask) == 0;
    }

    public boolean isFarRight() {
        final var mask = ~(-1L << rank);
        return (path & mask) == mask;
    }

    public boolean isFarLeft() {
        return path == 0;
    }

    /**
     * Gets whether this path comes <strong>before</strong> the {@code other} path.
     * A path comes before if it has a lesser rank, or if it has a lesser index within the
     * same rank as the other Path.
     *
     * @param other The other path. Must not be null.
     * @return Whether this Path comes before the other Path.
     */
    public boolean isBefore(VirtualTreePath other) {
        return compareTo(Objects.requireNonNull(other)) < 0;
    }

    /**
     * Gets whether this path comes <strong>after</strong> the {@code other} path.
     * A path comes after if it has a greater rank, or if it has a greater index within
     * the same rank.
     *
     * @param other The other path. Must not be null.
     * @return Whether this Path comes afer the other Path.
     */
    public boolean isAfter(VirtualTreePath other) {
        return compareTo(Objects.requireNonNull(other)) > 0;
    }

    /**
     * Gets the path of a node that would be the parent of the node represented by this path.
     *
     * @return The Path of the parent. This may be null if this Path is already the root (root nodes
     *         do not have a parent).
     */
    public VirtualTreePath getParentPath() {
        if (rank == 0) {
            return null;
        }

        final long mask = ~(-1L << rank - 1);
        return new VirtualTreePath((byte)(rank - 1), path & mask);
    }

    /**
     * Gets the path of the left-child.
     *
     * @return The path of the left child. This is never null.
     */
    public VirtualTreePath getLeftChildPath() {
        return new VirtualTreePath((byte)(rank + 1), path);
    }

    /**
     * Gets the part of the right-child.
     *
     * @return The path of the right child. This is never null.
     */
    public VirtualTreePath getRightChildPath() {
        return new VirtualTreePath((byte)(rank + 1), path | (1L << rank));
    }

    public boolean isParentOf(VirtualTreePath other) {
        if (rank < other.rank) {
            final var mask = ~(0xFFFFFFFFFFFFFFFFL << rank);
            return (path & mask) == (other.path & mask);
        } else {
            return false;
        }
    }

    /**
     * Compares two Paths. A Path is "less than" another path if it is either at a
     * more shallow rank (closer to the root), or to the left of the other path. It is
     * "greater than" if its rank is deeper (farther from the root) or to the right
     * of the other path.
     *
     * @param o The other path to compare with. Cannot be null.
     * @return -1 if this is left of o, 0 if they are equal, 1 if this is right of o.
     */
    @Override
    public int compareTo(@NotNull VirtualTreePath o) {
        // Check to see if we are equal
        if (this.rank == o.rank && this.path == o.path) {
            return 0;
        }

        // If my rank is less, then I am more shallow, so return -1
        if (this.rank < o.rank) {
            return -1;
        }

        // If my rank is more, then I am deeper, so return 1
        if (this.rank > o.rank) {
            return 1;
        }

        // Check the sequence of 0's and 1's in the two paths.
        // I don't need to check the positions that are the same, I only need to check the first position
        // at which they differ. If my first different position is a 0, then I'm to the left (return -1).
        // If my first different position is a 1, then I'm to the right (return 1).
        long firstDiffPos = this.path ^ o.path;

        long p1 = this.path;
        while ((firstDiffPos & 0x1) != 1) {
            firstDiffPos >>= 1;
            p1 >>= 1;

            if (firstDiffPos == 0) {
                throw new IllegalStateException("Unexpected failure in the algorithm. This should not be possible.");
            }
        }

        return ((p1 & 0x1) == 0) ? -1 : 1;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        VirtualTreePath nodeId = (VirtualTreePath) o;
        return rank == nodeId.rank && path == nodeId.path;
    }

    @Override
    public int hashCode() {
        return Objects.hash(rank, path);
    }

    @Override
    public String toString() {
        // Should print the path as a byte string
        return "Path [ rank=" + rank + ", path=" + path + "]";
    }
}
