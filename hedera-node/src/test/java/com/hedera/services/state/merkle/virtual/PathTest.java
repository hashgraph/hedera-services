package com.hedera.services.state.merkle.virtual;

import com.hedera.services.state.merkle.virtual.tree.VirtualTreePath;
import org.junit.jupiter.api.Test;

import static com.hedera.services.state.merkle.virtual.tree.VirtualTreePath.asPath;
import static com.hedera.services.state.merkle.virtual.tree.VirtualTreePath.getIndexInRank;
import static com.hedera.services.state.merkle.virtual.tree.VirtualTreePath.getLeftChildPath;
import static com.hedera.services.state.merkle.virtual.tree.VirtualTreePath.getParentPath;
import static com.hedera.services.state.merkle.virtual.tree.VirtualTreePath.getBreadcrumbs;
import static com.hedera.services.state.merkle.virtual.tree.VirtualTreePath.getPathForRankAndIndex;
import static com.hedera.services.state.merkle.virtual.tree.VirtualTreePath.getRank;
import static com.hedera.services.state.merkle.virtual.tree.VirtualTreePath.getRightChildPath;
import static com.hedera.services.state.merkle.virtual.tree.VirtualTreePath.isRootPath;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PathTest {

    @Test
    void rootPathIsRoot() {
        final var path = VirtualTreePath.ROOT_PATH;
        assertTrue(isRootPath(path));
        assertEquals(-1L, getParentPath(path));
        assertEquals(asPath((byte)1, 0b0), getLeftChildPath(path));
        assertEquals(asPath((byte)1, 0b1), getRightChildPath(path));
    }

    @Test
    void leftPath() {
        final var path = asPath((byte)1, 0b0);
        assertEquals(1, getRank(path));
        assertEquals(0, getBreadcrumbs(path));
        assertEquals(VirtualTreePath.ROOT_PATH, getParentPath(path));
        assertEquals(asPath((byte)2, 0b00), getLeftChildPath(path));
        assertEquals(asPath((byte)2, 0b10), getRightChildPath(path));
    }

    @Test
    void index() {
        var path = asPath((byte)3, 0b0000);
        assertEquals(0, getIndexInRank(path));

        path = asPath((byte)3, 0b0100);
        assertEquals(1, getIndexInRank(path));

        path = asPath((byte)3, 0b0010);
        assertEquals(2, getIndexInRank(path));

        path = asPath((byte)3, 0b0110);
        assertEquals(3, getIndexInRank(path));

        path = asPath((byte)3, 0b0001);
        assertEquals(4, getIndexInRank(path));

        path = asPath((byte)3, 0b0101);
        assertEquals(5, getIndexInRank(path));

        path = asPath((byte)3, 0b0011);
        assertEquals(6, getIndexInRank(path));

        path = asPath((byte)3, 0b0111);
        assertEquals(7, getIndexInRank(path));
    }

    @Test
    void pathForIndex() {
        var path = asPath((byte)3, 0b0000);
        assertEquals(path, getPathForRankAndIndex((byte)3, 0));

        path = asPath((byte)3, 0b0100);
        assertEquals(path, getPathForRankAndIndex((byte)3, 1));

        path = asPath((byte)3, 0b0010);
        assertEquals(path, getPathForRankAndIndex((byte)3, 2));

        path = asPath((byte)3, 0b0110);
        assertEquals(path, getPathForRankAndIndex((byte)3, 3));

        path = asPath((byte)3, 0b0001);
        assertEquals(path, getPathForRankAndIndex((byte)3, 4));

        path = asPath((byte)3, 0b0101);
        assertEquals(path, getPathForRankAndIndex((byte)3, 5));

        path = asPath((byte)3, 0b0011);
        assertEquals(path, getPathForRankAndIndex((byte)3, 6));

        path = asPath((byte)3, 0b0111);
        assertEquals(path, getPathForRankAndIndex((byte)3, 7));
    }
}
