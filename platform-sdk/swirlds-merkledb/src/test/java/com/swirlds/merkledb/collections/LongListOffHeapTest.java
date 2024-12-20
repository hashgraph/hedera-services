/*
 * Copyright (C) 2021-2024 Hedera Hashgraph, LLC
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

import static com.swirlds.base.units.UnitConstants.MEBIBYTES_TO_BYTES;
import static com.swirlds.merkledb.collections.AbstractLongList.DEFAULT_MAX_LONGS_TO_STORE;
import static com.swirlds.merkledb.collections.AbstractLongList.DEFAULT_NUM_LONGS_PER_CHUNK;
import static com.swirlds.merkledb.test.fixtures.MerkleDbTestUtils.CONFIGURATION;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.swirlds.common.test.fixtures.io.ResourceLoader;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
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
        return new LongListOffHeap(file, CONFIGURATION);
    }

    /**
     * Provides a stream of writer-reader pairs specifically for the {@link LongListOffHeap} implementation.
     * The writer is always {@link LongListOffHeap}, and it is paired with three reader implementations
     * (heap, off-heap, and disk-based). This allows for testing whether data written by the
     * {@link LongListOffHeap} can be correctly read back by all supported long list implementations.
     * <p>
     * This method builds on {@link AbstractLongListTest#longListWriterBasedPairsProvider(Supplier)} to generate
     * the specific writer-reader combinations for the {@link LongListOffHeap} implementation.
     *
     * @return a stream of argument pairs, each containing a {@link LongListOffHeap} writer
     *         and one of the supported reader implementations
     */
    static Stream<Arguments> longListWriterReaderPairsProvider() {
        return longListWriterBasedPairsProvider(LongListOffHeap::new);
    }

    /**
     * Provides writer-reader pairs combined with range configurations for testing.
     * <p>
     * Used for {@link AbstractLongListTest#writeReadRangeElement}
     *
     * @return a stream of arguments for range-based parameterized tests
     */
    static Stream<Arguments> longListWriterReaderRangePairsProvider() {
        return longListWriterReaderRangePairsProviderBase(longListWriterReaderPairsProvider());
    }

    /**
     * Provides writer-reader pairs combined with chunk offset configurations for testing.
     * <p>
     * Used for {@link AbstractLongListTest#createHalfEmptyLongListInMemoryReadBack}
     *
     * @return a stream of arguments for chunk offset based parameterized tests
     */
    static Stream<Arguments> longListWriterReaderOffsetPairsProvider() {
        return longListWriterReaderOffsetPairsProviderBase(longListWriterReaderPairsProvider());
    }

    @Test
    void testCustomNumberOfLongs() throws IOException {
        try (final LongListOffHeap list =
                createFullyParameterizedLongListWith(DEFAULT_NUM_LONGS_PER_CHUNK, SAMPLE_SIZE)) {
            list.updateValidRange(0, SAMPLE_SIZE - 1);
            for (int i = 0; i < SAMPLE_SIZE; i++) {
                list.put(i, i + 1);
            }
            final Path file = testDirectory.resolve("LongListOffHeapCustomLongCount.ll");
            // write longList data
            if (Files.exists(file)) {
                Files.delete(file);
            }
            list.writeToFile(file);

            final LongListOffHeap listFromDisk = createLongListFromFile(file);
            assertEquals(list.dataCopy().size(), listFromDisk.dataCopy().size());
        }
    }

    @Test
    void testInsertAtTheEndOfTheList() {
        final LongListOffHeap list = createLongList();
        list.updateValidRange(0, DEFAULT_MAX_LONGS_TO_STORE - 1);
        assertDoesNotThrow(() -> list.put(DEFAULT_MAX_LONGS_TO_STORE - 1, 1));
    }

    @Test
    void testInsertAtTheEndOfTheListCustomConfigured() {
        final int maxLongs = 10;
        final LongListOffHeap list = createFullyParameterizedLongListWith(10, maxLongs);
        list.updateValidRange(0, maxLongs - 1);
        assertDoesNotThrow(() -> list.put(maxLongs - 1, 1));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 5000, 9999, 10000}) // chunk size is 10K longs
    void testPersistListWithNonZeroMinValidIndex(final int chunkOffset) throws IOException {
        try (final LongListOffHeap list = createFullyParameterizedLongListWith(
                SAMPLE_SIZE / 100, // 100 chunks
                SAMPLE_SIZE)) {
            list.updateValidRange(0, SAMPLE_SIZE - 1);
            for (int i = 1; i < SAMPLE_SIZE; i++) {
                list.put(i, i);
            }

            list.updateValidRange(SAMPLE_SIZE / 2 + chunkOffset, list.size() - 1);

            final Path file = testDirectory.resolve("LongListOffHeapHalfEmpty.ll");
            // write longList data
            if (Files.exists(file)) {
                Files.delete(file);
            }
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
                SAMPLE_SIZE / 100, // 100 chunks
                SAMPLE_SIZE)) {
            list.updateValidRange(0, SAMPLE_SIZE - 1);
            for (int i = 1; i < SAMPLE_SIZE; i++) {
                list.put(i, i);
            }

            list.updateValidRange(0, SAMPLE_SIZE / 2 + chunkOffset);

            final Path file = testDirectory.resolve("LongListOffHeapHalfEmpty.ll");
            // write longList data
            if (Files.exists(file)) {
                Files.delete(file);
            }
            list.writeToFile(file);

            final LongListOffHeap longListFromFile = createLongListFromFile(file);

            for (int i = 0; i < longListFromFile.size(); i++) {
                assertEquals(list.get(i), longListFromFile.get(i));
            }
        }
    }

    @Test
    void updateListCreatedFromSnapshotPersistAndVerify() throws IOException {
        final int sampleSize = SAMPLE_SIZE;
        try (final LongListOffHeap list = createFullyParameterizedLongListWith(
                sampleSize / 100, // 100 chunks, 100 longs each
                sampleSize + DEFAULT_NUM_LONGS_PER_CHUNK)) {
            list.updateValidRange(0, SAMPLE_SIZE - 1);
            for (int i = 0; i < SAMPLE_SIZE; i++) {
                list.put(i, i + 1);
            }
            final Path file = testDirectory.resolve("LongListOffHeap.ll");
            if (Files.exists(file)) {
                Files.delete(file);
            }
            // write longList data
            list.writeToFile(file);

            // restoring the list from the file
            try (LongListOffHeap longListFromFile = createLongListFromFile(file)) {

                for (int i = 0; i < longListFromFile.size(); i++) {
                    assertEquals(list.get(i), longListFromFile.get(i));
                }
                // write longList data again
                Files.delete(file);
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
        final int sampleSize = SAMPLE_SIZE;
        try (final LongListOffHeap list = createFullyParameterizedLongListWith(
                sampleSize / 100, // 100 chunks, 100 longs each
                sampleSize + DEFAULT_NUM_LONGS_PER_CHUNK)) {
            list.updateValidRange(0, SAMPLE_SIZE - 1);
            for (int i = 1; i < SAMPLE_SIZE; i++) {
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
            try (final LongListOffHeap ignored = new LongListOffHeap(pathToList, CONFIGURATION)) {
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
            if (Files.exists(file)) {
                Files.delete(file);
            }
            list.writeToFile(file);
            try (LongListOffHeap listFromFile = new LongListOffHeap(file, CONFIGURATION)) {
                assertEquals(1, listFromFile.get(bigIndex));
            }
        }
    }
}
