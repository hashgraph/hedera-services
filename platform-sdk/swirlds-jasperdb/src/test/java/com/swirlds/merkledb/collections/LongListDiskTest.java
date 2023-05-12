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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@SuppressWarnings("FieldCanBeLocal")
class LongListDiskTest {
    private static final int SAMPLE_SIZE = 10_000;
    public static final int MAX_VALID_INDEX = SAMPLE_SIZE - 1;
    public static final int HALF_SAMPLE_SIZE = SAMPLE_SIZE / 2;
    public static final int NUM_LONGS_PER_CHUNK = 10;
    /**
     * Temporary directory provided by JUnit
     */
    @SuppressWarnings("unused")
    @TempDir
    Path testDirectory;

    LongListDisk longListDisk;

    @Test
    void createOffHeapReadBack() throws IOException {
        final LongListOffHeap longListOffHeap = populateList(new LongListOffHeap(NUM_LONGS_PER_CHUNK, SAMPLE_SIZE, 0));
        checkData(longListOffHeap);
        final Path tempFile = testDirectory.resolve("LongListDiskTest.ll");
        longListOffHeap.writeToFile(tempFile);
        // now open file with
        try {
            longListDisk = new LongListDisk(tempFile);
            assertEquals(longListOffHeap.size(), longListDisk.size(), "Unexpected value for longListDisk.size()");
            checkData(longListDisk);
            longListDisk.resetTransferBuffer();
        } finally {
            // cleanup
            Files.delete(tempFile);
        }
    }

    @Test
    void createHeapReadBack() throws IOException {
        final LongListHeap longListHeap = populateList(new LongListHeap(NUM_LONGS_PER_CHUNK, SAMPLE_SIZE, 0));
        checkData(longListHeap);
        final Path tempFile = testDirectory.resolve("LongListDiskTest.ll");
        longListHeap.writeToFile(tempFile);
        // now open file with
        try {
            longListDisk = new LongListDisk(tempFile);
            assertEquals(longListHeap.size(), longListDisk.size(), "Unexpected value for longListDisk.size()");
            checkData(longListDisk);
            longListDisk.resetTransferBuffer();
        } finally {
            // cleanup
            Files.delete(tempFile);
        }
    }

    @ParameterizedTest
    @MethodSource("inMemoryLongListProvider")
    void createHalfEmptyLongListInMemoryReadBack(LongList longList, int chunkOffset) throws IOException {
        populateList(longList);
        checkData(longList);

        int newMinValidIndex = HALF_SAMPLE_SIZE + chunkOffset;
        longList.updateValidRange(newMinValidIndex, MAX_VALID_INDEX);
        final Path tempFile = testDirectory.resolve(String.format(
                "LongListDiskTest_half_empty_%s_%d.ll", longList.getClass().getSimpleName(), chunkOffset));
        longList.writeToFile(tempFile);
        // now open file with
        try {
            longListDisk = new LongListDisk(tempFile);
            assertEquals(longList.size(), longListDisk.size(), "Unexpected value for longListDisk.size()");
            checkEmptyUpToIndex(longListDisk, newMinValidIndex);
            checkData(longListDisk, newMinValidIndex, SAMPLE_SIZE);
            longListDisk.resetTransferBuffer();
        } finally {
            // cleanup
            Files.delete(tempFile);
        }
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
        longListDisk = populateList(new LongListDisk(NUM_LONGS_PER_CHUNK, SAMPLE_SIZE, 0));
        checkData(longListDisk);
        int newMinValidIndex = HALF_SAMPLE_SIZE;
        longListDisk.updateValidRange(newMinValidIndex, MAX_VALID_INDEX);

        final Path halfEmptyListFile = testDirectory.resolve("LongListDiskTest_half_empty.ll");
        longListDisk.writeToFile(halfEmptyListFile);

        try (LongListDisk halfEmptyList = new LongListDisk(halfEmptyListFile)) {
            // check that it's half-empty indeed
            checkEmptyUpToIndex(halfEmptyList, newMinValidIndex);
            // and half-full
            checkData(halfEmptyList, HALF_SAMPLE_SIZE, SAMPLE_SIZE);

            // if we try to put a value below min valid index, the operation should fail with AssertionError
            int belowMinValidIndex1 = newMinValidIndex - 1;
            int belowMinValidIndex2 = newMinValidIndex - 2;
            int belowMinIndexValue1 = nextInt();
            int belowMinIndexValue2 = nextInt();
            assertThrows(AssertionError.class, () -> halfEmptyList.put(belowMinValidIndex1, belowMinIndexValue1));
            // doesn't throw an AssertionError, but returns false
            assertFalse(halfEmptyList.putIfEqual(belowMinValidIndex2, IMPERMISSIBLE_VALUE, belowMinIndexValue2));

            // however, once we update min valid index, we should be able to put values below it
            halfEmptyList.updateValidRange(0, MAX_VALID_INDEX);
            halfEmptyList.put(belowMinValidIndex1, belowMinIndexValue1);
            assertEquals(belowMinIndexValue1, halfEmptyList.get(belowMinValidIndex1));

            assertTrue(halfEmptyList.putIfEqual(belowMinValidIndex2, IMPERMISSIBLE_VALUE, belowMinIndexValue2));
            assertEquals(belowMinIndexValue2, halfEmptyList.get(belowMinValidIndex2));

            // forcing to create one more chunk
            halfEmptyList.put(belowMinValidIndex2 - NUM_LONGS_PER_CHUNK, belowMinIndexValue2);

            // check that it still works after restoring from a file
            final Path zeroMinValidIndex = testDirectory.resolve("LongListDiskTest_zero_min_valid_index.ll");
            halfEmptyList.writeToFile(zeroMinValidIndex);

            try (LongListDisk zeroMinValidIndexList = new LongListDisk(zeroMinValidIndex)) {
                checkEmptyUpToIndex(zeroMinValidIndexList, belowMinValidIndex2 - NUM_LONGS_PER_CHUNK);
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
        longListDisk = new LongListDisk(NUM_LONGS_PER_CHUNK, SAMPLE_SIZE, 0);
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
        longListDisk = new LongListDisk(pathToList, 0);
        // half-empty
        checkEmptyUpToIndex(longListDisk, HALF_SAMPLE_SIZE);
        // half-full
        for (int i = HALF_SAMPLE_SIZE; i < SAMPLE_SIZE; i++) {
            assertEquals(i, longListDisk.get(i));
        }
    }

    @Test
    void testShrinkList_minValidIndex() throws IOException {
        longListDisk = new LongListDisk(10, SAMPLE_SIZE * 2, 0);
        populateList(longListDisk);
        checkData(longListDisk, 0, SAMPLE_SIZE);
        // temporary file channel doesn't contain the header
        final long originalFileSize = longListDisk.getCurrentFileChannel().size() + longListDisk.currentFileHeaderSize;

        longListDisk.updateValidRange(HALF_SAMPLE_SIZE, SAMPLE_SIZE * 2 - 1);

        // half-empty
        checkEmptyUpToIndex(longListDisk, HALF_SAMPLE_SIZE);
        // half-full
        checkData(longListDisk, HALF_SAMPLE_SIZE, SAMPLE_SIZE);

        final Path shrunkListFile = testDirectory.resolve("LongListDiskTest.ll");
        // if we write to the same file, it doesn't shrink after the min valid index update
        longListDisk.writeToFile(shrunkListFile);
        assertEquals(HALF_SAMPLE_SIZE * Long.BYTES, originalFileSize - Files.size(shrunkListFile));

        try (final LongListDisk loadedList = new LongListDisk(shrunkListFile, 0)) {
            for (int i = 0; i < SAMPLE_SIZE; i++) {
                assertEquals(longListDisk.get(i), loadedList.get(i), "Unexpected value in a loaded longListDisk");
            }
        }
    }

    @Test
    void testShrinkList_maxValidIndex() throws IOException {
        longListDisk = new LongListDisk(10, SAMPLE_SIZE * 2, 0);
        populateList(longListDisk);
        checkData(longListDisk, 0, SAMPLE_SIZE);
        // temporary file channel doesn't contain the header
        final long originalFileSize = longListDisk.getCurrentFileChannel().size() + longListDisk.currentFileHeaderSize;

        longListDisk.updateValidRange(0, HALF_SAMPLE_SIZE - 1);

        // half-empty
        checkEmptyFromIndex(longListDisk, HALF_SAMPLE_SIZE);
        // half-full
        checkData(longListDisk, 0, HALF_SAMPLE_SIZE - 1);

        final Path shrunkListFile = testDirectory.resolve("LongListDiskTest.ll");
        // if we write to the same file, it doesn't shrink after the min valid index update
        longListDisk.writeToFile(shrunkListFile);
        assertEquals(HALF_SAMPLE_SIZE * Long.BYTES, originalFileSize - Files.size(shrunkListFile));

        try (final LongListDisk loadedList = new LongListDisk(shrunkListFile, 0)) {
            for (int i = 0; i < SAMPLE_SIZE; i++) {
                assertEquals(longListDisk.get(i), loadedList.get(i), "Unexpected value in a loaded longListDisk");
            }
        }
    }

    @Test
    void testReuseOfChunks_minValidIndex() throws IOException {
        longListDisk = new LongListDisk(100, SAMPLE_SIZE * 2, 0);
        populateList(longListDisk);
        checkData(longListDisk, 0, SAMPLE_SIZE);
        // temporary file channel doesn't contain the header
        final long originalChannelSize = longListDisk.getCurrentFileChannel().size();

        // freeing up some chunks
        longListDisk.updateValidRange(HALF_SAMPLE_SIZE, SAMPLE_SIZE * 2 - 1);

        // using the freed up chunks
        for (int i = SAMPLE_SIZE; i < SAMPLE_SIZE + HALF_SAMPLE_SIZE; i++) {
            longListDisk.put(i, i + 100);
        }

        // a longListDisk should have the same size as before because it has the same number of entries
        assertEquals(originalChannelSize, longListDisk.getCurrentFileChannel().size());

        checkEmptyUpToIndex(longListDisk, HALF_SAMPLE_SIZE);
        checkData(longListDisk, HALF_SAMPLE_SIZE, SAMPLE_SIZE + HALF_SAMPLE_SIZE);
    }

    @Test
    void testReuseOfChunks_maxValidIndex() throws IOException {
        longListDisk = new LongListDisk(100, SAMPLE_SIZE * 2, 0);
        populateList(longListDisk);
        checkData(longListDisk, 0, SAMPLE_SIZE);
        // temporary file channel doesn't contain the header
        final long originalChannelSize = longListDisk.getCurrentFileChannel().size();

        // freeing up some chunks
        longListDisk.updateValidRange(0, HALF_SAMPLE_SIZE);

        // using the freed up chunks
        for (int i = HALF_SAMPLE_SIZE; i < SAMPLE_SIZE; i++) {
            longListDisk.put(i, i + 100);
        }

        // a longListDisk should have the same size as before because it has the same number of entries
        assertEquals(originalChannelSize, longListDisk.getCurrentFileChannel().size());

        checkData(longListDisk, 0, SAMPLE_SIZE);
    }

    @Test
    void testBigIndex() throws IOException {
        try (LongListDisk list = new LongListDisk()) {
            long bigIndex = Integer.MAX_VALUE + 1L;
            list.updateValidRange(bigIndex, bigIndex);
            list.put(bigIndex, 1);

            assertEquals(1, list.get(bigIndex));
            final Path file = testDirectory.resolve("LongListLargeIndex.ll");
            list.writeToFile(file);
            try (LongListDisk listFromFile = new LongListDisk(file)) {
                assertEquals(1, listFromFile.get(bigIndex));
            }
        }
    }

    @AfterEach
    public void tearDown() {
        if (longListDisk != null) {
            longListDisk.close();
            longListDisk.resetTransferBuffer();
        }
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

    private static void checkEmptyFromIndex(LongList longList, int index) {
        for (int i = index; i < SAMPLE_SIZE; i++) {
            assertEquals(0, longList.get(i), "Unexpected value for index " + i);
        }
    }
}
