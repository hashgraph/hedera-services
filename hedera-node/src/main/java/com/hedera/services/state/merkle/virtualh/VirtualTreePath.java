package com.hedera.services.state.merkle.virtualh;

/**
 */
public final class VirtualTreePath {
    /** Site of an account when serialized to bytes */
    public static final int BYTES = Long.BYTES;

    /**
     * A special constant that represents the Path of a root node. It isn't necessary
     * for this constant to be used, but it makes the code a little more readable.
     */
    public static final long ROOT_PATH = 0L;
    public static final long INVALID_PATH = -1L; // All 1's!

    // Utility class only
    private VirtualTreePath() {
    }

    // Gets the rank part of the pathId
    public static int getRank(long path) {
        if (path == 0) {
            return 0;
        }

        return (63 - Long.numberOfLeadingZeros(path + 1));
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
        if (path == ROOT_PATH) {
            return 0;
        }

        final var rank = getRank(path);
        return (int)(path - (2L << (rank - 1)) + 1);
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
    public static long getPathForRankAndIndex(int rank, int index) {
        if (rank < 0) {
            throw new IllegalArgumentException("Rank must be strictly non-negative");
        }

        if (index < 0) {
            throw new IllegalArgumentException("Index must be strictly non-negative");
        }

        if (rank == 0 && index == 0) {
            return ROOT_PATH;
        }

        final var maxForRank = (1L << rank);
        if (index > (maxForRank - 1)) {
            throw new IllegalArgumentException("The index ["+index+"] is too large for the number of items at this rank. maxForRank ="+maxForRank);
        }

        return index + (2L << (rank - 1)) - 1;
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
        // It turns out, all 0's followed by all 1's followed by a single 0 is always the far right node.
        // Given a valid far right like 0b0000000_00111110, -1L << (64 - numLeadingZeros) will produce a complimentary
        // mask for the high leading 0's, such as 0b11111111_11000000. Xor on the path with 0x1 will end up
        // flipping the low bit, so it becomes 0b00000000_00111111. By or'ing the two together, we should
        // get all 1's, which is represented as -1L.
        final var numLeadingZeros = Long.numberOfLeadingZeros(path);
        return ((path ^ 0x1) | (-1L << (64 - numLeadingZeros))) == -1L;
    }

    /**
     * Gets the path of a node that would be the parent of the node represented by this path.
     *
     * @return The Path of the parent. This may be null if this Path is already the root (root nodes
     *         do not have a parent).
     */
    public static long getParentPath(long path) {
        return (path - 1) >> 1;
    }

    /**
     * Gets the path of the left-child.
     *
     * @return The path of the left child. This is never null.
     */
    public static long getLeftChildPath(long path) {
        return (path << 1) + 1;
    }

    /**
     * Gets the part of the right-child.
     *
     * @return The path of the right child. This is never null.
     */
    public static long getRightChildPath(long path) {
        return (path << 1) + 2;
    }

    public static long getSiblingPath(long path) {
        if (path == ROOT_PATH) {
            return INVALID_PATH;
        }

        return isLeft(path) ? path + 1 : path - 1;
    }

    public static String toString(long path) {
        // Should print the path as a byte string
        return "Path [ rank=" + getRank(path) + ", indexInRank=" + getIndexInRank(path) + "]";
    }
}
