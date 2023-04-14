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
import static com.swirlds.merkledb.collections.AbstractLongList.DEFAULT_MAX_LONGS_TO_STORE;
import static com.swirlds.merkledb.collections.AbstractLongList.DEFAULT_NUM_LONGS_PER_CHUNK;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.swirlds.test.framework.ResourceLoader;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class LongListOffHeapTest extends AbstractLongListTest<LongListOffHeap> {

    @TempDir
    Path testDirectory;

    @Override
    protected LongListOffHeap createLongList() {
        return new LongListOffHeap();
    }

    @Override
    protected LongListOffHeap createLongListWithChunkSizeInMb(final int chunkSizeInMb) {
        final int impliedLongsPerChunk = Math.toIntExact((((long) chunkSizeInMb * MEBIBYTES_TO_BYTES) / Long.BYTES));
        return new LongListOffHeap(impliedLongsPerChunk, DEFAULT_MAX_LONGS_TO_STORE, 0);
    }

    @Override
    protected LongListOffHeap createFullyParameterizedLongListWith(final int numLongsPerChunk, final long maxLongs) {
        return new LongListOffHeap(numLongsPerChunk, maxLongs, 0);
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
    void testCustomNumberOfLongs() throws IOException {
        try (final LongListOffHeap list =
                createFullyParameterizedLongListWith(DEFAULT_NUM_LONGS_PER_CHUNK, getSampleSize())) {
            for (int i = 0; i < getSampleSize(); i++) {
                list.put(i, i + 1);
            }
            final Path file = testDirectory.resolve("LongListOffHeapCustomLongCount.ll");
            // write longList data
            list.writeToFile(file);

            final LongListOffHeap listFromDisk = createLongListFromFile(file);
            assertEquals(list.dataCopy().size(), listFromDisk.dataCopy().size());
        }
    }

    @Test
    void testInsertAtTheEndOfTheList() {
        final LongListOffHeap list = createLongList();
        assertDoesNotThrow(() -> list.put(DEFAULT_MAX_LONGS_TO_STORE - 1, 1));
    }

    @Test
    void testInsertAtTheEndOfTheListCustomConfigured() {
        final int maxLongs = 10;
        final LongListOffHeap list = createFullyParameterizedLongListWith(10, maxLongs);
        assertDoesNotThrow(() -> list.put(maxLongs - 1, 1));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 5000, 9999, 10000}) // chunk size is 10K longs
    void testPersistListWithNonZeroMinValidIndex(final int chunkOffset) throws IOException {
        try (final LongListOffHeap list = createFullyParameterizedLongListWith(
                getSampleSize() / 100, // 100 chunks
                getSampleSize())) {
            for (int i = 1; i < getSampleSize(); i++) {
                list.put(i, i);
            }

            list.updateValidRange(getSampleSize() / 2 + chunkOffset, list.size() - 1);

            final Path file = testDirectory.resolve("LongListOffHeapHalfEmpty.ll");
            // write longList data
            list.writeToFile(file);

            final LongListOffHeap longListFromFile = createLongListFromFile(file);

            for (int i = 0; i < longListFromFile.size(); i++) {
                assertEquals(list.get(i), longListFromFile.get(i));
            }
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 5000, 9999, 10000}) // chunk size is 10K longs
    void testPersistShrunkList(final int chunkOffset) throws IOException {
        try (final LongListOffHeap list = createFullyParameterizedLongListWith(
                getSampleSize() / 100, // 100 chunks
                getSampleSize())) {
            for (int i = 1; i < getSampleSize(); i++) {
                list.put(i, i);
            }

            list.updateValidRange(0, getSampleSize() / 2 + chunkOffset);

            final Path file = testDirectory.resolve("LongListOffHeapHalfEmpty.ll");
            // write longList data
            list.writeToFile(file);

            final LongListOffHeap longListFromFile = createLongListFromFile(file);

            for (int i = 0; i < longListFromFile.size(); i++) {
                assertEquals(list.get(i), longListFromFile.get(i));
            }
        }
    }

    @Test
    void updateListCreatedFromSnapshotPersistAndVerify() throws IOException {
        final int sampleSize = getSampleSize();
        try (final LongListOffHeap list = createFullyParameterizedLongListWith(
                sampleSize / 100, // 100 chunks, 100 longs each
                sampleSize + DEFAULT_NUM_LONGS_PER_CHUNK)) {
            for (int i = 0; i < getSampleSize(); i++) {
                list.put(i, i + 1);
            }
            final Path file = testDirectory.resolve("LongListOffHeap.ll");
            // write longList data
            list.writeToFile(file);

            // restoring the list from the file
            try (LongListOffHeap longListFromFile = createLongListFromFile(file)) {

                for (int i = 0; i < longListFromFile.size(); i++) {
                    assertEquals(list.get(i), longListFromFile.get(i));
                }
                // write longList data again
                longListFromFile.writeToFile(file);

                // restoring the list from the file again
                try (LongListOffHeap longListFromFile2 = createLongListFromFile(file)) {
                    for (int i = 0; i < longListFromFile2.size(); i++) {
                        assertEquals(longListFromFile.get(i), longListFromFile2.get(i));
                    }
                }
            }
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {2, 3, 4, 5, 10, 50})
    void minValidIndexRespectedInForEachTest(final int countDivider) throws InterruptedException {
        final int sampleSize = getSampleSize();
        try (final LongListOffHeap list = createFullyParameterizedLongListWith(
                sampleSize / 100, // 100 chunks, 100 longs each
                sampleSize + DEFAULT_NUM_LONGS_PER_CHUNK)) {
            for (int i = 1; i < getSampleSize(); i++) {
                list.put(i, i + 1);
            }
            final long minIndex = sampleSize / countDivider;
            list.updateValidRange(minIndex, list.size() - 1);
            final AtomicLong count = new AtomicLong(0);
            final Set<Long> keysInForEach = new HashSet<>();
            list.forEach((path, location) -> {
                count.incrementAndGet();
                keysInForEach.add(path);
                assertEquals(path + 1, location);
            });
            assertEquals(sampleSize - minIndex, count.get(), "Wrong number of valid index entries");
            assertEquals(sampleSize - minIndex, keysInForEach.size(), "Wrong number of valid index entries");
        }
    }

    @Test
    void testFileFormatBackwardCompatibility_halfEmpty() throws URISyntaxException, IOException {
        final Path pathToList = ResourceLoader.getFile("test_data/LongListOffHeapHalfEmpty_10k_10pc_v1.ll");
        try (final LongListOffHeap longListFromFile = createLongListFromFile(pathToList)) {
            // half-empty
            for (int i = 0; i < 5_000; i++) {
                assertEquals(0, longListFromFile.get(i));
            }
            // half-full
            for (int i = 5_000; i < 10_000; i++) {
                assertEquals(i, longListFromFile.get(i));
            }
        }
    }

    @Test
    void testUnsupportedVersion() throws URISyntaxException {
        final Path pathToList = ResourceLoader.getFile("test_data/LongListOffHeap_unsupported_version.ll");
        assertThrows(IOException.class, () -> {
            //noinspection EmptyTryBlock
            try (final LongListOffHeap ignored = new LongListOffHeap(pathToList)) {
                // no op
            }
        });
    }

    @Test
    void testBigIndex() throws IOException {
        try (LongListOffHeap list = new LongListOffHeap()) {
            long bigIndex = Integer.MAX_VALUE + 1L;
            list.updateValidRange(bigIndex, bigIndex);
            list.put(bigIndex, 1);

            assertEquals(1, list.get(bigIndex));
            final Path file = testDirectory.resolve("LongListLargeIndex.ll");
            list.writeToFile(file);
            try (LongListOffHeap listFromFile = new LongListOffHeap(file)) {
                assertEquals(1, listFromFile.get(bigIndex));
            }
        }
    }
}
