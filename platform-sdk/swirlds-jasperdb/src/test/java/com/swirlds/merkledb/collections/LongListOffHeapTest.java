/*
 * Copyright (C) 2021-2023 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.swirlds.merkledb.collections;

import static com.swirlds.common.utility.Units.MEBIBYTES_TO_BYTES;
import static com.swirlds.merkledb.collections.LongList.DEFAULT_MAX_LONGS_TO_STORE;
import static com.swirlds.merkledb.collections.LongList.DEFAULT_NUM_LONGS_PER_CHUNK;
import static com.swirlds.merkledb.collections.LongListOffHeap.DEFAULT_RESERVED_BUFFER_LENGTH;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.io.TempDir;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class LongListOffHeapTest extends AbstractLongListTest<LongListOffHeap> {
    @Override
    protected LongListOffHeap createLongList() {
        return new LongListOffHeap();
    }

    @Override
    protected LongListOffHeap createLongListWithChunkSizeInMb(final int chunkSizeInMb) {
        final int impliedLongsPerChunk = Math.toIntExact((((long) chunkSizeInMb * MEBIBYTES_TO_BYTES) / Long.BYTES));
        return new LongListOffHeap(impliedLongsPerChunk, DEFAULT_MAX_LONGS_TO_STORE, DEFAULT_RESERVED_BUFFER_LENGTH);
    }

    @Override
    protected LongListOffHeap createFullyParameterizedLongListWith(final int numLongsPerChunk, final long maxLongs) {
        return new LongListOffHeap(numLongsPerChunk, maxLongs, DEFAULT_RESERVED_BUFFER_LENGTH);
    }

    @Override
    protected LongListOffHeap createLongListFromFile(final Path file) throws IOException {
        return new LongListOffHeap(file);
    }

    @Test
    void addressRequiresIndirectBuffer() {
        final ByteBuffer heapBuffer = ByteBuffer.allocate(32);
        assertThrows(
                IllegalArgumentException.class,
                () -> LongListOffHeap.address(heapBuffer),
                "Only indirect buffers can be used with LongListOffHeap");
    }

    @Test
    @Order(5)
    void testCustomNumberOfLongs(@TempDir final Path tempDir) throws IOException {
        try (final LongListOffHeap list =
                createFullyParameterizedLongListWith(DEFAULT_NUM_LONGS_PER_CHUNK, getSampleSize())) {
            for (int i = 0; i < getSampleSize(); i++) {
                list.put(i, i + 1);
            }
            final Path file = tempDir.resolve("LongListOffHeapCustomLongCount.hl");
            // write longList data
            list.writeToFile(file);

            final LongListOffHeap listFromDisk = createLongListFromFile(file);
            assertEquals(list.dataCopy().size(), listFromDisk.dataCopy().size());
        }
    }

    @Test
    public void testInsertAtTheEndOfTheList() {
        final LongListOffHeap list = createLongList();
        assertDoesNotThrow(() -> list.put(DEFAULT_MAX_LONGS_TO_STORE - 1, 1));
    }

    @Test
    public void testInsertAtTheEndOfTheListCustomConfigured() {
        final int maxLongs = 10;
        final LongListOffHeap list = createFullyParameterizedLongListWith(10, maxLongs);
        assertDoesNotThrow(() -> list.put(maxLongs - 1, 1));
    }

    @Test
    public void testPersistListWithNonZeroMinValidIndex(@TempDir final Path tempDir) throws IOException {
        try (final LongListOffHeap list = createFullyParameterizedLongListWith(
                getSampleSize() / 100, // 100 chunks
                getSampleSize() + DEFAULT_NUM_LONGS_PER_CHUNK)) {
            for (int i = 1; i < getSampleSize(); i++) {
                list.put(i, i);
            }

            list.updateMinValidIndex(getSampleSize() / 2);

            final Path file = tempDir.resolve("LongListOffHeapHalfEmpty.hl");
            // write longList data
            list.writeToFile(file);

            final LongListOffHeap longListFromFile = createLongListFromFile(file);

            for (int i = 0; i < longListFromFile.size(); i++) {
                assertEquals(list.get(i), longListFromFile.get(i));
            }
        }
    }
}
