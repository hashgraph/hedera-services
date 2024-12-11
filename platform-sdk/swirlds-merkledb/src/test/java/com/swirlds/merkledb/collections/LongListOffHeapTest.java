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
import static org.junit.jupiter.api.Assertions.*;

import com.swirlds.common.test.fixtures.io.ResourceLoader;
import com.swirlds.config.api.Configuration;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.MethodOrderer;
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

    @Test
    void writeReadSize1(@TempDir final Path tempDir) throws IOException {
        try (final AbstractLongList<?> list = createLongList()) {
            list.updateValidRange(2, 3);
            list.put(2, 123);
            final Path file = tempDir.resolve("writeReadSize1.ll");
            // write longList data
            list.writeToFile(file);
            // check file exists and contains some data
            assertTrue(Files.exists(file), "file does not exist");
            // now try and construct a new LongList reading from the file
            try (final LongList list2 = createLongListFromFile(file, CONFIGURATION)) {
                // now check data and other attributes
                assertEquals(list.capacity(), list2.capacity(), "Unexpected value for list2.capacity()");
                assertEquals(list.size(), list2.size(), "Unexpected value for list2.size()");
                assertEquals(123, list2.get(2));
            }
            // delete file as we are done with it
            Files.delete(file);
        }
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
    protected LongListOffHeap createLongListFromFile(final Path file, final Configuration configuration)
            throws IOException {
        return new LongListOffHeap(file, configuration);
    }

    @Test
    void testCustomNumberOfLongs() throws IOException {
        try (final LongListOffHeap list =
                createFullyParameterizedLongListWith(DEFAULT_NUM_LONGS_PER_CHUNK, getSampleSize())) {
            list.updateValidRange(0, getSampleSize() - 1);
            for (int i = 0; i < getSampleSize(); i++) {
                list.put(i, i + 1);
            }
            final Path file = testDirectory.resolve("LongListOffHeapCustomLongCount.ll");
            // write longList data
            if (Files.exists(file)) {
                Files.delete(file);
            }
            list.writeToFile(file);

            final LongListOffHeap listFromDisk = createLongListFromFile(file, CONFIGURATION);
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
                getSampleSize() / 100, // 100 chunks
                getSampleSize())) {
            list.updateValidRange(0, getSampleSize() - 1);
            for (int i = 1; i < getSampleSize(); i++) {
                list.put(i, i);
            }

            list.updateValidRange(getSampleSize() / 2 + chunkOffset, list.size() - 1);

            final Path file = testDirectory.resolve("LongListOffHeapHalfEmpty.ll");
            // write longList data
            if (Files.exists(file)) {
                Files.delete(file);
            }
            list.writeToFile(file);

            final LongListOffHeap longListFromFile = createLongListFromFile(file, CONFIGURATION);

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
            list.updateValidRange(0, getSampleSize() - 1);
            for (int i = 1; i < getSampleSize(); i++) {
                list.put(i, i);
            }

            list.updateValidRange(0, getSampleSize() / 2 + chunkOffset);

            final Path file = testDirectory.resolve("LongListOffHeapHalfEmpty.ll");
            // write longList data
            if (Files.exists(file)) {
                Files.delete(file);
            }
            list.writeToFile(file);

            final LongListOffHeap longListFromFile = createLongListFromFile(file, CONFIGURATION);

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
            list.updateValidRange(0, getSampleSize() - 1);
            for (int i = 0; i < getSampleSize(); i++) {
                list.put(i, i + 1);
            }
            final Path file = testDirectory.resolve("LongListOffHeap.ll");
            if (Files.exists(file)) {
                Files.delete(file);
            }
            // write longList data
            list.writeToFile(file);

            // restoring the list from the file
            try (LongListOffHeap longListFromFile = createLongListFromFile(file, CONFIGURATION)) {

                for (int i = 0; i < longListFromFile.size(); i++) {
                    assertEquals(list.get(i), longListFromFile.get(i));
                }
                // write longList data again
                Files.delete(file);
                longListFromFile.writeToFile(file);

                // restoring the list from the file again
                try (LongListOffHeap longListFromFile2 = createLongListFromFile(file, CONFIGURATION)) {
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
            list.updateValidRange(0, getSampleSize() - 1);
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
        try (final LongListOffHeap longListFromFile = createLongListFromFile(pathToList, CONFIGURATION)) {
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
