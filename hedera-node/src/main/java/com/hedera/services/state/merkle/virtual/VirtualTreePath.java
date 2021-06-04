package com.hedera.services.state.merkle.virtual;

import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * Represents a path in a virtual tree from the root to a node. The path is
 * represented as a string of bits and a rank, such that each bit represents either
 * the left or right node as you traverse from the root node downwards. The root
 * node is found at rank 0, while the first level of children are found at rank 1,
 * and so forth down the tree. Every node in the tree has a unique path at any given
 * point in time.
 *
 * TODO: NOTE! To optimize, we now use MSB->LSB of breadcrumbs! So 10 is left first (1)
 * and then right (0), and 110 is left first (1) and then left again (1) and then right (0).
 */
public final class VirtualTreePath {
    /** Site of an account when serialized to bytes */
    public static final int BYTES = Long.BYTES;

    private static final long BREADCRUMB_MASK = 0x00FFFFFFFFFFFFFFL;
    private static final long RANK_MASK = 0xFF00000000000000L;
    private static final long ALL_SET = -1L;

    /**
     * A special constant that represents the Path of a root node. It isn't necessary
     * for this constant to be used, but it makes the code a little more readable.
     */
    public static final long ROOT_PATH = 0L;
    public static final long INVALID_PATH = -1L; // All 1's!

    // Utility class only
    private VirtualTreePath() {
    }

    // combines the rank and path. Rank will replace the top byte of the path.
    // There are many breadcrumbs that don't make sense, for example if I have
    // a rank of "1", there can only be valid values of "0" and "1" for
    // breadcrumbs. I could zero out anything in breadcrumbs that is invalid,
    // and maybe this is a good idea for safety reasons.
    public static long asPath(byte rank, long breadcrumbs) {
        return ((long)rank << 56) | (BREADCRUMB_MASK & (~(ALL_SET << rank) & breadcrumbs));
    }

    // Gets the rank part of the pathId
    public static byte getRank(long path) {
        return (byte) ((RANK_MASK & path) >> 56);
    }

    // Gets the path from the pathId
    public static long getBreadcrumbs(long path) {
        return BREADCRUMB_MASK & path;
    }

    /**
     * Gets whether this path refers to the root node. The root node path is
     * special, since rank is 0. No leaf node or other internal node can
     * have a rank of 0.
     *
     * @return Whether this path refers to the root node.
     */
    public static boolean isRootPath(long path) {
        return path == 0;
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
    public static int getIndexInRank(long path) {
        final var rank = getRank(path);
        final var breadcrumbs = getBreadcrumbs(path);
        final var maxForRank = (1L << rank);
        return (int) (maxForRank - breadcrumbs - 1);
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
    public static long getPathForRankAndIndex(byte rank, int index) {
        if (rank < 0) {
            throw new IllegalArgumentException("Rank must be strictly non-negative");
        }

        if (index < 0) {
            throw new IllegalArgumentException("Index must be strictly non-negative");
        }

        final var maxForRank = (1L << rank);
        if (index > (maxForRank - 1)) {
            throw new IllegalArgumentException("The index is too large for the number of items at this rank");
        }

        final var breadcrumbs = (maxForRank - index - 1);
        return asPath(rank, breadcrumbs);
    }

    /**
     * Gets whether this Path represents a left-side node.
     *
     * @return Whether this Path is a left-side node.
     */
    public static boolean isLeft(long path) {
        return (path & 0x1) == 1;
    }

    public static boolean isFarRight(long path) {
        final var breadcrumbs = getBreadcrumbs(path);
        return breadcrumbs == 0;
    }

    public boolean isFarLeft(long path) {
        final var rank = getRank(path);
        final var breadcrumbs = getBreadcrumbs(path);
        final var maxForRank = (1L << rank);
        return (maxForRank - breadcrumbs) == 1;
    }

    /**
     * Gets whether this path comes <strong>before</strong> the {@code other} path.
     * A path comes before if it has a lesser rank, or if it has a lesser index within the
     * same rank as the other Path.
     *
     * @return Whether this Path comes before the other Path.
     */
    public static boolean isBefore(long a, long b) {
        return compareTo(a, b) < 0;
    }

    /**
     * Gets whether this path comes <strong>after</strong> the {@code other} path.
     * A path comes after if it has a greater rank, or if it has a greater index within
     * the same rank.
     *
     * @return Whether this Path comes after the other Path.
     */
    public static boolean isAfter(long a, long b) {
        return compareTo(a, b) > 0;
    }

    /**
     * Gets the path of a node that would be the parent of the node represented by this path.
     *
     * @return The Path of the parent. This may be null if this Path is already the root (root nodes
     *         do not have a parent).
     */
    public static long getParentPath(long path) {
        final byte rank = getRank(path);
        if (rank == 0) {
            return INVALID_PATH;
        }

        final var breadcrumbs = getBreadcrumbs(path);
        return asPath((byte) (rank - 1), breadcrumbs >> 1);
    }

    /**
     * Gets the path of the left-child.
     *
     * @return The path of the left child. This is never null.
     */
    public static long getLeftChildPath(long path) {
        final var rank = getRank(path);
        final var breadcrumbs = getBreadcrumbs(path);
        return asPath((byte)(rank + 1), (breadcrumbs << 1) | 0x1);
    }

    /**
     * Gets the part of the right-child.
     *
     * @return The path of the right child. This is never null.
     */
    public static long getRightChildPath(long path) {
        final var rank = getRank(path);
        final var breadcrumbs = getBreadcrumbs(path);
        return asPath((byte)(rank + 1), breadcrumbs << 1);
    }

    public static boolean isParentOf(long a, long b) {
        final var rankA = getRank(a);
        final var rankB = getRank(b);
        if (rankB <= rankA) {
            return false;
        }

        final var breadA = getBreadcrumbs(a);
        final var breadB = getBreadcrumbs(b);
        return (breadB >> (rankB - rankA)) == breadA;
    }

    /**
     * Compares two Paths. A Path is "less than" another path if it is either at a
     * more shallow rank (closer to the root), or to the left of the other path. It is
     * "greater than" if its rank is deeper (farther from the root) or to the right
     * of the other path.
     *
     * @param a The first path.
     * @param b The other path to compare with.
     * @return -1 if this is left of o, 0 if they are equal, 1 if this is right of o.
     */
    private static int compareTo(long a, long b) {
        // Check to see if we are equal
        if (a == b) {
            return 0;
        }

        // If my rank is less, then I am more shallow, so return -1
        final var rankA = getRank(a);
        final var rankB = getRank(b);
        if (rankA < rankB) {
            return -1;
        }

        // If my rank is more, then I am deeper, so return 1
        if (rankA > rankB) {
            return 1;
        }

        // Check the sequence of 0's and 1's in the two paths.
        // I don't need to check the positions that are the same, I only need to check the first position
        // at which they differ. If my first different position is a 0, then I'm to the left (return -1).
        // If my first different position is a 1, then I'm to the right (return 1).
        long firstDiffPos = a ^ b;

        long p1 = a;
        while ((firstDiffPos & 0x1) != 1) {
            firstDiffPos >>= 1;
            p1 >>= 1;

            if (firstDiffPos == 0) {
                throw new IllegalStateException("Unexpected failure in the algorithm. This should not be possible.");
            }
        }

        return ((p1 & 0x1) == 0) ? -1 : 1;
    }

    public static String toString(long path) {
        // Should print the path as a byte string
        return "Path [ rank=" + getRank(path) + ", breadcrumbs=" + getBreadcrumbs(path) + "]";
    }
}
