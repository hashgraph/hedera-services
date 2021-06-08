package com.hedera.services.state.merkle.virtual;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static com.hedera.services.state.merkle.virtual.VirtualTreePath.INVALID_PATH;
import static com.hedera.services.state.merkle.virtual.VirtualTreePath.ROOT_PATH;
import static com.hedera.services.state.merkle.virtual.VirtualTreePath.asPath;
import static com.hedera.services.state.merkle.virtual.VirtualTreePath.compareTo;
import static com.hedera.services.state.merkle.virtual.VirtualTreePath.getIndexInRank;
import static com.hedera.services.state.merkle.virtual.VirtualTreePath.getLeftChildPath;
import static com.hedera.services.state.merkle.virtual.VirtualTreePath.getParentPath;
import static com.hedera.services.state.merkle.virtual.VirtualTreePath.getBreadcrumbs;
import static com.hedera.services.state.merkle.virtual.VirtualTreePath.getPathForRankAndIndex;
import static com.hedera.services.state.merkle.virtual.VirtualTreePath.getRank;
import static com.hedera.services.state.merkle.virtual.VirtualTreePath.getRightChildPath;
import static com.hedera.services.state.merkle.virtual.VirtualTreePath.isFarRight;
import static com.hedera.services.state.merkle.virtual.VirtualTreePath.isLeft;
import static com.hedera.services.state.merkle.virtual.VirtualTreePath.isParentOf;
import static com.hedera.services.state.merkle.virtual.VirtualTreePath.isRootPath;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PathTest {

    @Test
    void rootPathIsRootTest() {
        final var path = VirtualTreePath.ROOT_PATH;
        assertTrue(isRootPath(path));
        assertEquals(-1L, getParentPath(path));
        assertEquals(asPath((byte)1, 0b1), getLeftChildPath(path));
        assertEquals(asPath((byte)1, 0b0), getRightChildPath(path));
    }

    @Test
    void asPathTest() {
        // rank 1, right hand side of root.
        final var expected = 0b00000001_00000000_00000000_00000000_00000000_00000000_00000000_00000001L;
        // Does a basic combination of the two work?
        assertEquals(expected, asPath((byte)1, 1));
        // What happens if I pass in breadcrumbs that don't make sense for the rank? It should get cleaned up.
        assertEquals(expected, asPath((byte)1, 0xFF));
        // Pathological case of sending breadcrumbs that also invade the rank space.
        assertEquals(expected, asPath((byte)1, -1L));
        // Can I get back a root path?
        assertEquals(ROOT_PATH, asPath((byte)0, -1L));
    }

    @Test
    void getRankTest() {
        for (int i=0; i<255; i++) {
            assertEquals((byte) i, getRank(asPath((byte) i, -1L)));
        }
    }

    @Test
    void getBreadcrumbsTest() {
        final byte rank = (byte) 0x23;
        for (int i=0; i<2048; i++) {
            final var path = asPath(rank, i);
            assertEquals(i, getBreadcrumbs(path));
        }
    }

    @Test
    void isLeftTest() {
        var path = ROOT_PATH;
        assertFalse(isLeft(path));

        final var pathsOnRight = new long[] {
                asPath((byte)1, 0b000),
                asPath((byte)2, 0b010),
                asPath((byte)2, 0b000),
                asPath((byte)3, 0b110),
                asPath((byte)3, 0b100),
                asPath((byte)3, 0b010),
                asPath((byte)3, 0b000) };

        final var pathsOnLeft = new long[] {
                asPath((byte)1, 0b001),
                asPath((byte)2, 0b011),
                asPath((byte)2, 0b001),
                asPath((byte)3, 0b111),
                asPath((byte)3, 0b101),
                asPath((byte)3, 0b011),
                asPath((byte)3, 0b001) };

        for (long index : pathsOnLeft) {
            assertTrue(isLeft(index));
        }

        for (long index : pathsOnRight) {
            assertFalse(isLeft(index));
        }
    }

    @Test
    void isFarRightTest() {
        var path = ROOT_PATH;
        assertTrue(isFarRight(path));

        final var pathsOnFarRight = new long[] {
                asPath((byte)1, 0b000),
                asPath((byte)2, 0b000),
                asPath((byte)3, 0b000) };

        final var everythingElse = new long[] {
                asPath((byte)1, 0b001),
                asPath((byte)2, 0b011),
                asPath((byte)2, 0b001),
                asPath((byte)2, 0b010),
                asPath((byte)3, 0b110),
                asPath((byte)3, 0b100),
                asPath((byte)3, 0b010),
                asPath((byte)3, 0b111),
                asPath((byte)3, 0b101),
                asPath((byte)3, 0b011),
                asPath((byte)3, 0b001) };

        for (long index : pathsOnFarRight) {
            assertTrue(isFarRight(index));
        }

        for (long index : everythingElse) {
            assertFalse(isFarRight(index));
        }
    }

    @Test
    void getIndexInRankTest() {
        var path = asPath((byte)3, 0b0111);
        assertEquals(0, getIndexInRank(path));

        path = asPath((byte)3, 0b0110);
        assertEquals(1, getIndexInRank(path));

        path = asPath((byte)3, 0b0101);
        assertEquals(2, getIndexInRank(path));

        path = asPath((byte)3, 0b0100);
        assertEquals(3, getIndexInRank(path));

        path = asPath((byte)3, 0b0011);
        assertEquals(4, getIndexInRank(path));

        path = asPath((byte)3, 0b0010);
        assertEquals(5, getIndexInRank(path));

        path = asPath((byte)3, 0b0001);
        assertEquals(6, getIndexInRank(path));

        path = asPath((byte)3, 0b0000);
        assertEquals(7, getIndexInRank(path));
    }

    @Test
    void pathForIndex() {
        var path = asPath((byte)3, 0b0111);
        assertEquals(path, getPathForRankAndIndex((byte)3, 0));

        path = asPath((byte)3, 0b0110);
        assertEquals(path, getPathForRankAndIndex((byte)3, 1));

        path = asPath((byte)3, 0b0101);
        assertEquals(path, getPathForRankAndIndex((byte)3, 2));

        path = asPath((byte)3, 0b0100);
        assertEquals(path, getPathForRankAndIndex((byte)3, 3));

        path = asPath((byte)3, 0b0011);
        assertEquals(path, getPathForRankAndIndex((byte)3, 4));

        path = asPath((byte)3, 0b0010);
        assertEquals(path, getPathForRankAndIndex((byte)3, 5));

        path = asPath((byte)3, 0b0001);
        assertEquals(path, getPathForRankAndIndex((byte)3, 6));

        path = asPath((byte)3, 0b0000);
        assertEquals(path, getPathForRankAndIndex((byte)3, 7));
    }

    @Test
    public void getParentPathTest() {
        // rank 0
        assertEquals(INVALID_PATH, getParentPath(ROOT_PATH));

        // rank 1
        assertEquals(ROOT_PATH, getParentPath(asPath((byte)1, 0b000)));
        assertEquals(ROOT_PATH, getParentPath(asPath((byte)1, 0b001)));

        // rank 2
        assertEquals(asPath((byte)1, 0b000), getParentPath(asPath((byte)2, 0b000)));
        assertEquals(asPath((byte)1, 0b000), getParentPath(asPath((byte)2, 0b001)));
        assertEquals(asPath((byte)1, 0b001), getParentPath(asPath((byte)2, 0b010)));
        assertEquals(asPath((byte)1, 0b001), getParentPath(asPath((byte)2, 0b011)));

        // rank 3
        assertEquals(asPath((byte)2, 0b000), getParentPath(asPath((byte)3, 0b000)));
        assertEquals(asPath((byte)2, 0b000), getParentPath(asPath((byte)3, 0b001)));
        assertEquals(asPath((byte)2, 0b001), getParentPath(asPath((byte)3, 0b010)));
        assertEquals(asPath((byte)2, 0b001), getParentPath(asPath((byte)3, 0b011)));
        assertEquals(asPath((byte)2, 0b010), getParentPath(asPath((byte)3, 0b100)));
        assertEquals(asPath((byte)2, 0b010), getParentPath(asPath((byte)3, 0b101)));
        assertEquals(asPath((byte)2, 0b011), getParentPath(asPath((byte)3, 0b110)));
        assertEquals(asPath((byte)2, 0b011), getParentPath(asPath((byte)3, 0b111)));
    }

    @Test
    public void getLeftChildPathTest() {
        // rank 1
        assertEquals(asPath((byte)1, 0b001), getLeftChildPath(ROOT_PATH));

        // rank 2
        assertEquals(asPath((byte)2, 0b001), getLeftChildPath(asPath((byte)1, 0b000)));
        assertEquals(asPath((byte)2, 0b011), getLeftChildPath(asPath((byte)1, 0b001)));

        // rank 3
        assertEquals(asPath((byte)3, 0b001), getLeftChildPath(asPath((byte)2, 0b000)));
        assertEquals(asPath((byte)3, 0b011), getLeftChildPath(asPath((byte)2, 0b001)));
        assertEquals(asPath((byte)3, 0b101), getLeftChildPath(asPath((byte)2, 0b010)));
        assertEquals(asPath((byte)3, 0b111), getLeftChildPath(asPath((byte)2, 0b011)));
    }

    @Test
    public void getRightChildPathTest() {
        // rank 1
        assertEquals(asPath((byte)1, 0b000), getRightChildPath(ROOT_PATH));

        // rank 2
        assertEquals(asPath((byte)2, 0b000), getRightChildPath(asPath((byte)1, 0b000)));
        assertEquals(asPath((byte)2, 0b010), getRightChildPath(asPath((byte)1, 0b001)));

        // rank 3
        assertEquals(asPath((byte)3, 0b000), getRightChildPath(asPath((byte)2, 0b000)));
        assertEquals(asPath((byte)3, 0b010), getRightChildPath(asPath((byte)2, 0b001)));
        assertEquals(asPath((byte)3, 0b100), getRightChildPath(asPath((byte)2, 0b010)));
        assertEquals(asPath((byte)3, 0b110), getRightChildPath(asPath((byte)2, 0b011)));
    }

    @Test
    public void isParentOfTest() {
        // rank 1
        assertTrue(isParentOf(ROOT_PATH, asPath((byte)1, 0b000)));
        assertTrue(isParentOf(ROOT_PATH, asPath((byte)1, 0b001)));

        // rank 2
        assertTrue(isParentOf(ROOT_PATH, asPath((byte)2, 0b000)));
        assertTrue(isParentOf(ROOT_PATH, asPath((byte)2, 0b001)));
        assertTrue(isParentOf(ROOT_PATH, asPath((byte)2, 0b010)));
        assertTrue(isParentOf(ROOT_PATH, asPath((byte)2, 0b011)));
        assertTrue(isParentOf(asPath((byte)1, 0b000), asPath((byte)2, 0b000)));
        assertTrue(isParentOf(asPath((byte)1, 0b000), asPath((byte)2, 0b001)));
        assertTrue(isParentOf(asPath((byte)1, 0b001), asPath((byte)2, 0b010)));
        assertTrue(isParentOf(asPath((byte)1, 0b001), asPath((byte)2, 0b011)));

        // rank 3
        assertTrue(isParentOf(ROOT_PATH, asPath((byte)3, 0b000)));
        assertTrue(isParentOf(ROOT_PATH, asPath((byte)3, 0b001)));
        assertTrue(isParentOf(ROOT_PATH, asPath((byte)3, 0b010)));
        assertTrue(isParentOf(ROOT_PATH, asPath((byte)3, 0b011)));
        assertTrue(isParentOf(ROOT_PATH, asPath((byte)3, 0b100)));
        assertTrue(isParentOf(ROOT_PATH, asPath((byte)3, 0b101)));
        assertTrue(isParentOf(ROOT_PATH, asPath((byte)3, 0b110)));
        assertTrue(isParentOf(ROOT_PATH, asPath((byte)3, 0b111)));
        assertTrue(isParentOf(asPath((byte)2, 0b000), asPath((byte)3, 0b000)));
        assertTrue(isParentOf(asPath((byte)2, 0b000), asPath((byte)3, 0b001)));
        assertTrue(isParentOf(asPath((byte)2, 0b001), asPath((byte)3, 0b010)));
        assertTrue(isParentOf(asPath((byte)2, 0b001), asPath((byte)3, 0b011)));
        assertTrue(isParentOf(asPath((byte)2, 0b010), asPath((byte)3, 0b100)));
        assertTrue(isParentOf(asPath((byte)2, 0b010), asPath((byte)3, 0b101)));
        assertTrue(isParentOf(asPath((byte)2, 0b011), asPath((byte)3, 0b110)));
        assertTrue(isParentOf(asPath((byte)2, 0b011), asPath((byte)3, 0b111)));
    }

    @Test
    public void comparisonTest() {
        // Construct a nice list of ordered results. Then try all permutations and make sure
        // they return the expected answers.
        final var expected = Arrays.asList(
                ROOT_PATH,
                asPath((byte)1, 0b001),
                asPath((byte)1, 0b000),
                asPath((byte)2, 0b011),
                asPath((byte)2, 0b010),
                asPath((byte)2, 0b001),
                asPath((byte)2, 0b000),
                asPath((byte)3, 0b111),
                asPath((byte)3, 0b110),
                asPath((byte)3, 0b101),
                asPath((byte)3, 0b100),
                asPath((byte)3, 0b011),
                asPath((byte)3, 0b010),
                asPath((byte)3, 0b001),
                asPath((byte)3, 0b000));

        for (int i=0; i<expected.size(); i++) {
            assertTrue(compareTo(expected.get(i), expected.get(i)) == 0);
        }

        for (int i=0; i<expected.size() - 1; i++) {
            for (int j=i+1; j<expected.size(); j++) {
                assertTrue(compareTo(expected.get(i), expected.get(j)) < 0);
            }
        }

        for (int i=expected.size() - 1; i>1; i--) {
            for (int j=0; j<i-1; j++) {
                assertTrue(compareTo(expected.get(i), expected.get(j)) > 0);
            }
        }
    }
}
