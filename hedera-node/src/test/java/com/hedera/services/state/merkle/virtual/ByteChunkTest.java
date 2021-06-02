package com.hedera.services.state.merkle.virtual;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ByteChunkTest {
    @Test
    public void createByteChunk() {
        final var sourceArray = Arrays.copyOf(new byte[] { 1, 2, 3, 4}, 32);
        final var chunk = new ByteChunk(sourceArray);
        assertArrayEquals(sourceArray, chunk.asByteArray());
        assertNotSame(sourceArray, chunk.asByteArray());
    }

    @Test
    public void sourceArrayIsCopied() {
        final var sourceArray = Arrays.copyOf(new byte[] { 1, 2, 3, 4}, 32);
        final var chunk = new ByteChunk(sourceArray);

        // Modify the source array. This should have no impact on the internal array of the ByteChunk.
        sourceArray[0] = 10;
        assertFalse(Arrays.equals(sourceArray, chunk.asByteArray()));
    }

    @Test
    public void hashesSame() {
        final var sourceArray1 = Arrays.copyOf(new byte[] { 1, 2, 3, 4}, 32);
        final var chunk1 = new ByteChunk(sourceArray1);

        final var sourceArray2 = Arrays.copyOf(new byte[] { 1, 2, 3, 4}, 32);
        final var chunk2 = new ByteChunk(sourceArray2);

        assertEquals(chunk1.hashCode(), chunk2.hashCode());
    }

    @Test
    public void chunksAreEqual() {
        final var sourceArray1 = Arrays.copyOf(new byte[] { 1, 2, 3, 4}, 32);
        final var chunk1 = new ByteChunk(sourceArray1);

        final var sourceArray2 = Arrays.copyOf(new byte[] { 1, 2, 3, 4}, 32);
        final var chunk2 = new ByteChunk(sourceArray2);

        assertEquals(chunk1, chunk2);
    }

    @Test
    public void chunksAreNotEqual() {
        final var sourceArray1 = Arrays.copyOf(new byte[] { 1, 2, 3, 4}, 32);
        final var chunk1 = new ByteChunk(sourceArray1);

        final var sourceArray2 = Arrays.copyOf(new byte[] { 10, 2, 3, 4}, 32);
        final var chunk2 = new ByteChunk(sourceArray2);

        assertNotEquals(chunk1, chunk2);
    }

}
