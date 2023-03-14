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

import static com.swirlds.merkledb.collections.LongList.IMPERMISSIBLE_VALUE;
import static org.apache.commons.lang3.RandomUtils.nextInt;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.swirlds.test.framework.ResourceLoader;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@SuppressWarnings("FieldCanBeLocal")
class LongListDiskTest {
    private static final int SAMPLE_SIZE = 10_000;
    public static final int HALF_SAMPLE_SIZE = SAMPLE_SIZE / 2;
    public static final int NUM_LONGS_PER_CHUNK = 10;
    /**
     * Temporary directory provided by JUnit
     */
    @SuppressWarnings("unused")
    @TempDir
    Path testDirectory;

    @Test
    void createOffHeapReadBack() throws IOException {
        final LongListOffHeap longListOffHeap = populateList(new LongListOffHeap(NUM_LONGS_PER_CHUNK, SAMPLE_SIZE, 0));
        checkData(longListOffHeap);
        final Path tempFile = testDirectory.resolve("LongListDiskTest.ll");
        longListOffHeap.writeToFile(tempFile);
        // now open file with
        try (final LongListDisk longListDisk = new LongListDisk(tempFile)) {
            assertEquals(longListOffHeap.size(), longListDisk.size(), "Unexpected value for longListDisk.size()");
            checkData(longListDisk);
        }
        // cleanup
        Files.delete(tempFile);
    }

    @Test
    void createHeapReadBack() throws IOException {
        final LongListHeap longListHeap = populateList(new LongListHeap(NUM_LONGS_PER_CHUNK, SAMPLE_SIZE, 0));
        checkData(longListHeap);
        final Path tempFile = testDirectory.resolve("LongListDiskTest.ll");
        longListHeap.writeToFile(tempFile);
        // now open file with
        try (final LongListDisk longListDisk = new LongListDisk(tempFile)) {
            assertEquals(longListHeap.size(), longListDisk.size(), "Unexpected value for longListDisk.size()");
            checkData(longListDisk);
        }
        // cleanup
        Files.delete(tempFile);
    }

    @ParameterizedTest
    @MethodSource("inMemoryLongListProvider")
    void createHalfEmptyLongListInMemoryReadBack(LongList longList, int chunkOffset) throws IOException {
        populateList(longList);
        checkData(longList);

        int newMinValidIndex = HALF_SAMPLE_SIZE + chunkOffset;
        longList.updateMinValidIndex(newMinValidIndex);
        final Path tempFile = testDirectory.resolve(String.format(
                "LongListDiskTest_half_empty_%s_%d.ll", longList.getClass().getSimpleName(), chunkOffset));
        longList.writeToFile(tempFile);
        // now open file with
        try (final LongListDisk longListDisk = new LongListDisk(tempFile)) {
            assertEquals(longList.size(), longListDisk.size(), "Unexpected value for longListDisk.size()");
            checkEmptyUpToIndex(longListDisk, newMinValidIndex);
            checkData(longListDisk, newMinValidIndex, SAMPLE_SIZE);
        }
        // cleanup
        Files.delete(tempFile);
    }

    public static Stream<Arguments> inMemoryLongListProvider() {
        return Stream.of(
                Arguments.of(new LongListOffHeap(NUM_LONGS_PER_CHUNK, SAMPLE_SIZE, 0), 0),
                Arguments.of(new LongListOffHeap(NUM_LONGS_PER_CHUNK, SAMPLE_SIZE, 0), 5),
                Arguments.of(new LongListHeap(NUM_LONGS_PER_CHUNK, SAMPLE_SIZE, 0), 0),
                Arguments.of(new LongListHeap(NUM_LONGS_PER_CHUNK, SAMPLE_SIZE, 0), 5));
    }

    @Test
    void updateMinToTheLowerEnd() throws IOException {
        final LongListDisk longList = populateList(new LongListDisk(NUM_LONGS_PER_CHUNK, SAMPLE_SIZE, 0));
        checkData(longList);
        int newMinValidIndex = HALF_SAMPLE_SIZE;
        longList.updateMinValidIndex(newMinValidIndex);

        final Path halfEmptyListFile = testDirectory.resolve("LongListDiskTest_half_empty.ll");
        longList.writeToFile(halfEmptyListFile);

        try (LongListDisk halfEmptyList = new LongListDisk(halfEmptyListFile)) {
            // check that it's half-empty indeed
            checkEmptyUpToIndex(halfEmptyList, newMinValidIndex);
            // and half-full
            checkData(halfEmptyList, HALF_SAMPLE_SIZE, SAMPLE_SIZE);

            // if we try to put a value below min valid index, the operation should fail with AssertionError
            int belowMinValidIndex = newMinValidIndex - 1;
            int belowMinIndexValue = nextInt();
            assertThrows(AssertionError.class, () -> halfEmptyList.put(belowMinValidIndex, belowMinIndexValue));
            assertThrows(
                    AssertionError.class,
                    () -> halfEmptyList.putIfEqual(belowMinValidIndex, IMPERMISSIBLE_VALUE, belowMinIndexValue));

            // however, once we update min valid index, we should be able to put values below it
            halfEmptyList.updateMinValidIndex(0);
            halfEmptyList.put(belowMinValidIndex, belowMinIndexValue);
            assertEquals(belowMinIndexValue, halfEmptyList.get(belowMinValidIndex));
            halfEmptyList.putIfEqual(belowMinValidIndex, IMPERMISSIBLE_VALUE, belowMinIndexValue);
            assertEquals(belowMinIndexValue, halfEmptyList.get(belowMinValidIndex));

            // forcing to create one more chunk
            halfEmptyList.put(belowMinValidIndex - NUM_LONGS_PER_CHUNK, belowMinIndexValue);

            // check that it still works after restoring from a file
            final Path zeroMinValidIndex = testDirectory.resolve("LongListDiskTest_zero_min_valid_index.ll");
            halfEmptyList.writeToFile(zeroMinValidIndex);

            try (LongListDisk zeroMinValidIndexList = new LongListDisk(zeroMinValidIndex)) {
                checkEmptyUpToIndex(zeroMinValidIndexList, belowMinValidIndex - NUM_LONGS_PER_CHUNK);
                checkData(zeroMinValidIndexList, HALF_SAMPLE_SIZE, SAMPLE_SIZE);

                for (int i = 0; i < newMinValidIndex; i++) {
                    // assert the content is the same
                    assertEquals(halfEmptyList.get(i), zeroMinValidIndexList.get(i));

                    // refill the list
                    zeroMinValidIndexList.put(i, i + 100);
                }

                // make sure that the refilled list works as expected
                checkData(zeroMinValidIndexList);
            }
        }
    }

    @Test
    void createDiskReadBack() throws IOException {
        final LongListDisk longListDisk = new LongListDisk(NUM_LONGS_PER_CHUNK, SAMPLE_SIZE, 0);
        populateList(longListDisk);
        checkData(longListDisk);
        // test changing data with putIf
        assertTrue(longListDisk.putIfEqual(10, 110, 123), "Unexpected value from putIfEqual()");
        assertEquals(123, longListDisk.get(10, -1), "Unexpected value from longListDisk.get(10)");
        assertFalse(longListDisk.putIfEqual(10, 110, 345), "Unexpected value from putIfEqual() #2");
        longListDisk.put(10, 110); // put back

        final Path lsitFile = testDirectory.resolve("LongListDiskTest.ll");
        longListDisk.writeToFile(lsitFile);
        long listSize = longListDisk.size();
        // close
        longListDisk.close();
        // now open file with

        try (final LongListDisk longListDiskRestored = new LongListDisk(lsitFile)) {
            assertEquals(listSize, longListDiskRestored.size(), "Unexpected value from longListDiskRestored.size()");
            checkData(longListDiskRestored);
        }
    }

    @Test
    void testBackwardCompatibility_halfEmpty() throws URISyntaxException, IOException {
        final Path pathToList = ResourceLoader.getFile("test_data/LongListOffHeapHalfEmpty_10k_10pc_v1.ll");
        try (final LongListDisk longListDisk = new LongListDisk(pathToList, 0)) {
            // half-empty
            checkEmptyUpToIndex(longListDisk, HALF_SAMPLE_SIZE);
            // half-full
            for (int i = HALF_SAMPLE_SIZE; i < SAMPLE_SIZE; i++) {
                assertEquals(i, longListDisk.get(i));
            }
        }
    }

    @Test
    void testShrinkList() throws IOException {
        final LongListDisk list = new LongListDisk(10, SAMPLE_SIZE * 2, 0);
        populateList(list);
        checkData(list, 0, SAMPLE_SIZE);
        // temporary file channel doesn't contain the header
        final long originalFileSize = list.getCurrentFileChannel().size() + list.currentFileHeaderSize;

        list.updateMinValidIndex(HALF_SAMPLE_SIZE);

        // half-empty
        checkEmptyUpToIndex(list, HALF_SAMPLE_SIZE);
        // half-full
        checkData(list, HALF_SAMPLE_SIZE, SAMPLE_SIZE);

        final Path shrinkedListFile = testDirectory.resolve("LongListDiskTest.ll");
        // if we write to the same file, it doesn't shrink after the min valid index update
        list.writeToFile(shrinkedListFile);
        assertEquals(HALF_SAMPLE_SIZE * Long.BYTES, originalFileSize - Files.size(shrinkedListFile));

        try (final LongListDisk loadedList = new LongListDisk(shrinkedListFile, 0)) {
            for (int i = 0; i < SAMPLE_SIZE; i++) {
                assertEquals(list.get(i), loadedList.get(i), "Unexpected value in a loaded list");
            }
        }
    }

    @Test
    void testReuseOfChunks() throws IOException {
        final LongListDisk list = new LongListDisk(100, SAMPLE_SIZE * 2, 0);
        populateList(list);
        checkData(list, 0, SAMPLE_SIZE);
        // temporary file channel doesn't contain the header
        final long originalChannelSize = list.getCurrentFileChannel().size();

        // freeing up some chunks
        list.updateMinValidIndex(HALF_SAMPLE_SIZE);

        // using the freed up chunks
        for (int i = SAMPLE_SIZE; i < SAMPLE_SIZE + HALF_SAMPLE_SIZE; i++) {
            list.put(i, i + 100);
        }

        // a list should have the same size as before because it has the same number of entries
        assertEquals(originalChannelSize, list.getCurrentFileChannel().size());

        checkEmptyUpToIndex(list, HALF_SAMPLE_SIZE);
        checkData(list, HALF_SAMPLE_SIZE, SAMPLE_SIZE + HALF_SAMPLE_SIZE);
    }

    private static void checkData(final LongList longList) {
        checkData(longList, 0, LongListDiskTest.SAMPLE_SIZE);
    }

    private static void checkData(final LongList longList, final int startIndex, final int endIndex) {
        for (int i = startIndex; i < endIndex; i++) {
            assertEquals(i + 100, longList.get(i, -1), "Unexpected value from longList.get(" + i + ")");
        }
    }

    private static <T extends LongList> T populateList(T longList) {
        for (int i = 0; i < SAMPLE_SIZE; i++) {
            longList.put(i, i + 100);
        }
        return longList;
    }

    private static void checkEmptyUpToIndex(LongList longList, int index) {
        for (int i = 0; i < index; i++) {
            assertEquals(0, longList.get(i), "Unexpected value for index " + i);
        }
    }
}
