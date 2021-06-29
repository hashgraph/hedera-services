package com.hedera.services.state.merkle.vitrualh;

import com.hedera.services.state.merkle.virtualh.VirtualTreePath;
import org.junit.jupiter.api.Test;

import static com.hedera.services.state.merkle.virtualh.VirtualTreePath.INVALID_PATH;
import static com.hedera.services.state.merkle.virtualh.VirtualTreePath.ROOT_PATH;
import static com.hedera.services.state.merkle.virtualh.VirtualTreePath.getIndexInRank;
import static com.hedera.services.state.merkle.virtualh.VirtualTreePath.getLeftChildPath;
import static com.hedera.services.state.merkle.virtualh.VirtualTreePath.getParentPath;
import static com.hedera.services.state.merkle.virtualh.VirtualTreePath.getPathForRankAndIndex;
import static com.hedera.services.state.merkle.virtualh.VirtualTreePath.getRank;
import static com.hedera.services.state.merkle.virtualh.VirtualTreePath.getRightChildPath;
import static com.hedera.services.state.merkle.virtualh.VirtualTreePath.getSiblingPath;
import static com.hedera.services.state.merkle.virtualh.VirtualTreePath.isFarRight;
import static com.hedera.services.state.merkle.virtualh.VirtualTreePath.isLeft;
import static com.hedera.services.state.merkle.virtualh.VirtualTreePath.isRootPath;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PathTest {
    // For testing purposes, these constants represent commonly named nodes
    // that we use in our diagrams. A is the root, B is the left child of root,
    // C is the right child of root, D is the left child of B, etc.
    private static final long A = 0;
    private static final long B = 1;
    private static final long C = 2;
    private static final long D = 3;
    private static final long E = 4;
    private static final long F = 5;
    private static final long G = 6;
    private static final long H = 7;
    private static final long I = 8;
    private static final long J = 9;
    private static final long K = 10;


    @Test
    void rootPathIsRootTest() {
        final var path = VirtualTreePath.ROOT_PATH;
        assertTrue(isRootPath(path));
        assertEquals(INVALID_PATH, getParentPath(path));
        assertEquals(1, getLeftChildPath(path));
        assertEquals(2, getRightChildPath(path));
    }

    @Test
    void getRankTest() {
        assertEquals(0, getRank(ROOT_PATH));
        assertEquals(0, getRank(A));
        assertEquals(1, getRank(B));
        assertEquals(1, getRank(C));
        assertEquals(2, getRank(D));
        assertEquals(2, getRank(E));
        assertEquals(2, getRank(F));
        assertEquals(2, getRank(G));
        assertEquals(3, getRank(H));
        assertEquals(3, getRank(I));
        assertEquals(3, getRank(J));
        assertEquals(3, getRank(K));
    }

    @Test
    void isLeftTest() {
        assertFalse(isLeft(ROOT_PATH));

        final var pathsOnRight = new long[] { C, E, G, I, K };
        final var pathsOnLeft = new long[] { B, D, F, H, J };

        for (long index : pathsOnLeft) {
            assertTrue(isLeft(index));
        }

        for (long index : pathsOnRight) {
            assertFalse(isLeft(index));
        }
    }

    @Test
    void isFarRightTest() {
        assertTrue(isFarRight(ROOT_PATH));

        final var pathsOnFarRight = new long[] { C, G };

        final var everythingElse = new long[] { B, D, E, F, H, I, J, K };

        for (long index : pathsOnFarRight) {
            assertTrue(isFarRight(index));
        }

        for (long index : everythingElse) {
            assertFalse(isFarRight(index));
        }
    }

    @Test
    void getIndexInRankTest() {
        assertEquals(0, getIndexInRank(A));
        assertEquals(0, getIndexInRank(B));
        assertEquals(1, getIndexInRank(C));
        assertEquals(0, getIndexInRank(D));
        assertEquals(1, getIndexInRank(E));
        assertEquals(2, getIndexInRank(F));
        assertEquals(3, getIndexInRank(G));
        assertEquals(0, getIndexInRank(H));
        assertEquals(1, getIndexInRank(I));
        assertEquals(2, getIndexInRank(J));
        assertEquals(3, getIndexInRank(K));
    }

    @Test
    void pathForIndex() {
        assertEquals(A, getPathForRankAndIndex(0, 0));
        assertEquals(B, getPathForRankAndIndex(1, 0));
        assertEquals(C, getPathForRankAndIndex(1, 1));
        assertEquals(D, getPathForRankAndIndex(2, 0));
        assertEquals(E, getPathForRankAndIndex(2, 1));
        assertEquals(F, getPathForRankAndIndex(2, 2));
        assertEquals(G, getPathForRankAndIndex(2, 3));
        assertEquals(H, getPathForRankAndIndex(3, 0));
        assertEquals(I, getPathForRankAndIndex(3, 1));
        assertEquals(J, getPathForRankAndIndex(3, 2));
        assertEquals(K, getPathForRankAndIndex(3, 3));
    }

    @Test
    public void getParentPathTest() {
        // rank 0
        assertEquals(INVALID_PATH, getParentPath(ROOT_PATH));

        // rank 1
        assertEquals(A, getParentPath(B));
        assertEquals(A, getParentPath(C));

        // rank 2
        assertEquals(B, getParentPath(D));
        assertEquals(B, getParentPath(E));
        assertEquals(C, getParentPath(F));
        assertEquals(C, getParentPath(G));

        // rank 3
        assertEquals(D, getParentPath(H));
        assertEquals(D, getParentPath(I));
        assertEquals(E, getParentPath(J));
        assertEquals(E, getParentPath(K));
    }

    @Test
    public void getLeftChildPathTest() {
        // rank 1
        assertEquals(B, getLeftChildPath(ROOT_PATH));

        // rank 2
        assertEquals(D, getLeftChildPath(B));
        assertEquals(F, getLeftChildPath(C));

        // rank 3
        assertEquals(H, getLeftChildPath(D));
        assertEquals(J, getLeftChildPath(E));
    }

    @Test
    public void getRightChildPathTest() {
        // rank 1
        assertEquals(C, getRightChildPath(ROOT_PATH));

        // rank 2
        assertEquals(E, getRightChildPath(B));
        assertEquals(G, getRightChildPath(C));

        // rank 3
        assertEquals(I, getRightChildPath(D));
        assertEquals(K, getRightChildPath(E));
    }

    @Test
    public void getSiblingPathTest() {
        assertEquals(INVALID_PATH, getSiblingPath(ROOT_PATH));
        assertEquals(B, getSiblingPath(C));
        assertEquals(C, getSiblingPath(B));
        assertEquals(D, getSiblingPath(E));
        assertEquals(E, getSiblingPath(D));
        assertEquals(F, getSiblingPath(G));
        assertEquals(G, getSiblingPath(F));
        assertEquals(H, getSiblingPath(I));
        assertEquals(I, getSiblingPath(H));
        assertEquals(J, getSiblingPath(K));
        assertEquals(K, getSiblingPath(J));

    }
}
