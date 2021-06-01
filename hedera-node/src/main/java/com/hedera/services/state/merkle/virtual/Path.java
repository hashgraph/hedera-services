package com.hedera.services.state.merkle.virtual;

import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * Represents the Path from the root to the node. The path is a 64-bit long value,
 * so the depth of the binary tree can never exceed 64 levels. Each bit, from LSB
 * to MSB in the long value indicates whether to go left, or right, starting from
 * the root, to find the node. The "depth" indicates how many characters in the
 * long are significant. For example, a depth of 0 means this is the root, in which
 * case the long isn't used. If the depth is 1, then this is a child of the root,
 * and the first bit indicates either the left (0) or right (1) child. And so on down
 * the tree.
 * <p>
 * The path to a leaf is significant and must not change because the Path is persisted
 * in the filesystem along with the Key that represents this node. The Key is an arbitrary
 * 256-byte string (as per EVM). Using that key, we must be able to lookup the path to the
 * node, and then get the value for the node from disk (along with some other information).
 * The path of a node *may* change over time, as the tree grows or shrinks, and all mapping
 * from Key to Path must be updated accordingly.
 */
public final class Path implements Comparable<Path> {
    public static final Path ROOT_PATH = new Path((byte)0, 0);

    final byte depth; // max depth of 64, so the byte value will be <= 63
    final long path; // The path helps form the ID.

    public Path(byte depth, long path) {
        this.depth = depth;
        this.path = path;

        if (depth < 0 || depth >= 64) {
            throw new IllegalArgumentException("Depth must be between [0,63]");
        }
    }

    /**
     * Gets whether this path refers to the root node. The root node path is
     * special, since depth is 0. No leaf node or other internal node can
     * have a depth of 0.
     *
     * @return Whether this path refers to the root node.
     */
    public boolean isRoot() {
        return depth == 0;
    }

    // Gets the position of the node represented by this path at its depth. For example,
    // the node on the far left would be index "0" while the node on the far right
    // has an index depending on the depth (depth 1, index = 1; depth 2, index = 3; depth N, index = (2^N)-1)
    public int getIndex() {
        int index = 0;
        byte power = (byte) (depth-1);
        long n = path;
        while (n != 0) {
            index += (n & 1) << power;
            n = n >> 1;
            power--;
        }

        return index;
    }

    public static Path getPathForDepthAndIndex(byte depth, int index) {
        int path = 0;
        byte power = (byte) (depth-1);
        long n = index;
        while (n != 0) {
            path += (n & 1) << power;
            n = n >> 1;
            power--;
        }

        return new Path(depth, path);
    }

    /**
     * Gets whether this Path represents a left-side node.
     * @return Whether this Path is a left-side node.
     */
    public boolean isLeft() {
        final var decisionMask = 1L << (depth - 1);
        return (path & decisionMask) == 0;
    }

    public boolean isLeftOf(Path other) {
        return other != null && compareTo(other) < 0;
    }

    public boolean isRightOf(Path other) {
        return other == null || compareTo(other) > 0;
    }

    public Path getParentPath() {
        if (depth == 0) {
            return null;
        }

        final long mask = ~(-1L << depth - 1);
        return new Path((byte)(depth - 1), path & mask);
    }

    public Path getLeftChildPath() {
        // TODO Check for max depth of 64. At that point, we should throw something.
        return new Path((byte)(depth + 1), path);
    }

    public Path getRightChildPath() {
        // TODO Check for max depth of 64. At that point, we should throw something.
        return new Path((byte)(depth + 1), path | (1L << depth));
    }

    /**
     * Compares two Paths. A Path is "less than" another path if it is either at a
     * more shallow depth (closer to the root), or to the left of the other path. It is
     * "greater than" if its depth is deeper (farther from the root) or to the right
     * of the other path.
     *
     * @param o The other path to compare with. Cannot be null.
     * @return -1 if this is left of o, 0 if they are equal, 1 if this is right of o.
     */
    @Override
    public int compareTo(@NotNull Path o) {
        // Check to see if we are equal
        if (this.depth == o.depth && this.path == o.path) {
            return 0;
        }

        // If my depth is less, then I am more shallow, so return -1
        if (this.depth < o.depth) {
            return -1;
        }

        // If my depth is more, then I am deeper, so return 1
        if (this.depth > o.depth) {
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
        Path nodeId = (Path) o;
        return depth == nodeId.depth && path == nodeId.path;
    }

    @Override
    public int hashCode() {
        return Objects.hash(depth, path);
    }

    @Override
    public String toString() {
        // Should print the path as a byte string
        return "Path [ depth=" + depth + ", path=" + path + "]";
    }
}
