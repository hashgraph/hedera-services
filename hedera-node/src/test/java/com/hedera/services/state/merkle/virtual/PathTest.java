package com.hedera.services.state.merkle.virtual;

import com.hedera.services.state.merkle.virtual.tree.VirtualTreePath;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class PathTest {

    @Test
    void rootPathIsRoot() {
        final var path = VirtualTreePath.ROOT_PATH;
        assertEquals(0, path.rank);
        assertEquals(0, path.path);
        assertNull(path.getParentPath());
        assertEquals(new VirtualTreePath((byte)1, 0b0), path.getLeftChildPath());
        assertEquals(new VirtualTreePath((byte)1, 0b1), path.getRightChildPath());
    }

    @Test
    void leftPath() {
        final var path = new VirtualTreePath((byte)1, 0b0);
        assertEquals(1, path.rank);
        assertEquals(0, path.path);
        assertEquals(VirtualTreePath.ROOT_PATH, path.getParentPath());
        assertEquals(new VirtualTreePath((byte)2, 0b00), path.getLeftChildPath());
        assertEquals(new VirtualTreePath((byte)2, 0b10), path.getRightChildPath());
    }

    @Test
    void index() {
        var path = new VirtualTreePath((byte)3, 0b0000);
        assertEquals(0, path.getIndex());

        path = new VirtualTreePath((byte)3, 0b0100);
        assertEquals(1, path.getIndex());

        path = new VirtualTreePath((byte)3, 0b0010);
        assertEquals(2, path.getIndex());

        path = new VirtualTreePath((byte)3, 0b0110);
        assertEquals(3, path.getIndex());

        path = new VirtualTreePath((byte)3, 0b0001);
        assertEquals(4, path.getIndex());

        path = new VirtualTreePath((byte)3, 0b0101);
        assertEquals(5, path.getIndex());

        path = new VirtualTreePath((byte)3, 0b0011);
        assertEquals(6, path.getIndex());

        path = new VirtualTreePath((byte)3, 0b0111);
        assertEquals(7, path.getIndex());
    }

    @Test
    void pathForIndex() {
        var path = new VirtualTreePath((byte)3, 0b0000);
        assertEquals(path, VirtualTreePath.getPathForRankAndIndex((byte)3, 0));

        path = new VirtualTreePath((byte)3, 0b0100);
        assertEquals(path, VirtualTreePath.getPathForRankAndIndex((byte)3, 1));

        path = new VirtualTreePath((byte)3, 0b0010);
        assertEquals(path, VirtualTreePath.getPathForRankAndIndex((byte)3, 2));

        path = new VirtualTreePath((byte)3, 0b0110);
        assertEquals(path, VirtualTreePath.getPathForRankAndIndex((byte)3, 3));

        path = new VirtualTreePath((byte)3, 0b0001);
        assertEquals(path, VirtualTreePath.getPathForRankAndIndex((byte)3, 4));

        path = new VirtualTreePath((byte)3, 0b0101);
        assertEquals(path, VirtualTreePath.getPathForRankAndIndex((byte)3, 5));

        path = new VirtualTreePath((byte)3, 0b0011);
        assertEquals(path, VirtualTreePath.getPathForRankAndIndex((byte)3, 6));

        path = new VirtualTreePath((byte)3, 0b0111);
        assertEquals(path, VirtualTreePath.getPathForRankAndIndex((byte)3, 7));
    }
}
