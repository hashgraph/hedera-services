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
    private static final int SMALL_SAMPLE_SIZE = 120;

    /**
     * Temporary directory provided by JUnit
     */
    @SuppressWarnings("unused")
    @TempDir
    Path testDirectory;

    @Test
    void createOffHeapReadBack() throws IOException {
        final LongListOffHeap longListOffHeap = populateList(new LongListOffHeap());
        checkData(longListOffHeap, SAMPLE_SIZE);
        final Path tempFile = testDirectory.resolve("LongListDiskTest.ll");
        longListOffHeap.writeToFile(tempFile);
        // now open file with
        try (final LongListDisk longListDisk = new LongListDisk(tempFile)) {
            assertEquals(longListOffHeap.size(), longListDisk.size(), "Unexpected value for longListDisk.size()");
            checkData(longListDisk, SAMPLE_SIZE);
        }
        // cleanup
        Files.delete(tempFile);
    }

    @Test
    void createHeapReadBack() throws IOException {
        final LongListHeap longListHeap = populateList(new LongListHeap());
        checkData(longListHeap, SAMPLE_SIZE);
        final Path tempFile = testDirectory.resolve("LongListDiskTest.ll");
        longListHeap.writeToFile(tempFile);
        // now open file with
        try (final LongListDisk longListDisk = new LongListDisk(tempFile)) {
            assertEquals(longListHeap.size(), longListDisk.size(), "Unexpected value for longListDisk.size()");
            checkData(longListDisk, SAMPLE_SIZE);
        }
        // cleanup
        Files.delete(tempFile);
    }

    @ParameterizedTest
    @MethodSource("inMemoryLongListProvider")
    void createHalfEmptyLongListOffHeapReadBack(LongList<?> longList) throws IOException {
        populateList(longList);
        checkData(longList, SAMPLE_SIZE);

        longList.updateMinValidIndex(SAMPLE_SIZE / 2);
        final Path tempFile = testDirectory.resolve("LongListDiskTest.ll");
        longList.writeToFile(tempFile);
        // now open file with
        try (final LongListDisk longListDisk = new LongListDisk(tempFile)) {
            assertEquals(longList.size(), longListDisk.size(), "Unexpected value for longListDisk.size()");
            checkEmptyUpToIndex(longListDisk, SAMPLE_SIZE / 2);
            checkData(longListDisk, SAMPLE_SIZE / 2, SAMPLE_SIZE);
        }
        // cleanup
        Files.delete(tempFile);
    }

    public static Stream<Arguments> inMemoryLongListProvider() {
        return Stream.of(Arguments.of(new LongListOffHeap()), Arguments.of(new LongListHeap()));
    }

    @Test
    public void updateMinToTheLowerEnd() throws IOException {
        final LongListDisk longList = populateList(new LongListDisk());
        checkData(longList, SAMPLE_SIZE);
        int newMinValidIndex = SAMPLE_SIZE / 2;
        longList.updateMinValidIndex(newMinValidIndex);

        final Path halfEmptyListFile = testDirectory.resolve("LongListDiskTest_half_empty.ll");
        longList.writeToFile(halfEmptyListFile);

        try (LongListDisk halfEmptyList = new LongListDisk(halfEmptyListFile)) {
            // check that it's half-empty indeed
            checkEmptyUpToIndex(halfEmptyList, newMinValidIndex);
            // and half-full
            checkData(halfEmptyList, SAMPLE_SIZE / 2, SAMPLE_SIZE);

            // if we try to put a value below min valid index, it should have no effect
            int belowMinValidIndex = newMinValidIndex - 1;
            int belowMinIndexValue = nextInt();
            halfEmptyList.put(belowMinValidIndex, belowMinIndexValue);
            assertEquals(IMPERMISSIBLE_VALUE, halfEmptyList.get(belowMinValidIndex));
            halfEmptyList.putIfEqual(belowMinValidIndex, IMPERMISSIBLE_VALUE, belowMinIndexValue);
            assertEquals(IMPERMISSIBLE_VALUE, halfEmptyList.get(belowMinValidIndex));

            // however, once we update min valid index, we should be able to put values below it
            halfEmptyList.updateMinValidIndex(0);
            halfEmptyList.put(belowMinValidIndex, belowMinIndexValue);
            assertEquals(belowMinIndexValue, halfEmptyList.get(belowMinValidIndex));
            halfEmptyList.putIfEqual(belowMinValidIndex, IMPERMISSIBLE_VALUE, belowMinIndexValue);
            assertEquals(belowMinIndexValue, halfEmptyList.get(belowMinValidIndex));

            // check that it still works after restoring from a file
            final Path zeroMinValidIndex = testDirectory.resolve("LongListDiskTest_zero_min_valid_index.ll");
            halfEmptyList.writeToFile(zeroMinValidIndex);

            try (LongListDisk zeroMinValidIndexList = new LongListDisk(zeroMinValidIndex)) {
                checkEmptyUpToIndex(zeroMinValidIndexList, newMinValidIndex - 2);
                checkData(zeroMinValidIndexList, SAMPLE_SIZE / 2, SAMPLE_SIZE);
                for (int i = 0; i < newMinValidIndex; i++) {
                    zeroMinValidIndexList.put(i, i + 100);
                }
                checkData(zeroMinValidIndexList, SAMPLE_SIZE);
            }
        }
    }

    @Test
    void createDiskReadBack() throws IOException {
        final LongListDisk longListDisk = new LongListDisk();
        populateList(longListDisk);
        checkData(longListDisk, SMALL_SAMPLE_SIZE);
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
            checkData(longListDiskRestored, SMALL_SAMPLE_SIZE);
        }
    }

    @Test
    public void testBackwardCompatibility_halfEmpty() throws URISyntaxException, IOException {
        final Path pathToList = ResourceLoader.getFile("test_data/LongListOffHeapHalfEmpty_10k_10pc_v1.ll");
        try (final LongListDisk longListDisk = new LongListDisk(pathToList)) {
            // half-empty
            checkEmptyUpToIndex(longListDisk, 5000);
            // half-full
            for (int i = 5_000; i < 10_000; i++) {
                assertEquals(i, longListDisk.get(i));
            }
        }
    }

    @Test
    public void testShrinkList() throws IOException {
        final LongListDisk list = new LongListDisk();
        populateList(list);
        checkData(list, 0, SAMPLE_SIZE);
        // temporary file channel doesn't contain the header
        final long originalFileSize = list.getCurrentFileChannel().size() + list.currentFileHeaderSize;

        list.updateMinValidIndex(SAMPLE_SIZE / 2);

        // half-empty
        checkEmptyUpToIndex(list, SAMPLE_SIZE / 2);
        // half-full
        checkData(list, SAMPLE_SIZE / 2, SAMPLE_SIZE);

        final Path shrinkedListFile = testDirectory.resolve("LongListDiskTest.ll");
        // if we write to the same file, it doesn't shrink after the min valid index update
        list.writeToFile(shrinkedListFile);
        assertEquals(SAMPLE_SIZE / 2 * Long.BYTES, originalFileSize - Files.size(shrinkedListFile));

        try (final LongListDisk loadedList = new LongListDisk(shrinkedListFile, 0)) {
            for (int i = 0; i < SAMPLE_SIZE; i++) {
                assertEquals(list.get(i), loadedList.get(i), "Unexpected value in a loaded list");
            }
        }
    }

    @Test
    public void testReuseOfChunks() throws IOException {
        final LongListDisk list = new LongListDisk(100, SAMPLE_SIZE * 2, 0);
        populateList(list);
        checkData(list, 0, SAMPLE_SIZE);
        // temporary file channel doesn't contain the header
        final long originalChannelSize = list.getCurrentFileChannel().size();

        list.updateMinValidIndex(SAMPLE_SIZE / 2);

        for (int i = SAMPLE_SIZE; i < SAMPLE_SIZE * 1.5; i++) {
            list.put(i, i + 100);
        }

        assertEquals(list.getCurrentFileChannel().size(), originalChannelSize);

        checkEmptyUpToIndex(list, SAMPLE_SIZE / 2);
        checkData(list, SAMPLE_SIZE / 2, (int) (1.5 * SAMPLE_SIZE));
    }

    private static void checkData(final LongList<?> longList, final int sampleSize) {
        checkData(longList, 0, sampleSize);
    }

    private static void checkData(final LongList<?> longList, final int startIndex, final int endIndex) {
        for (int i = startIndex; i < endIndex; i++) {
            assertEquals(i + 100, longList.get(i, -1), "Unexpected value from longList.get(" + i + ")");
        }
    }

    private static <T extends LongList<?>> T populateList(T longList) {
        for (int i = 0; i < SAMPLE_SIZE; i++) {
            longList.put(i, i + 100);
        }
        return longList;
    }

    private static void checkEmptyUpToIndex(LongList<?> longList, int index) {
        for (int i = 0; i < index; i++) {
            assertEquals(0, longList.get(i), "Unexpected value for index " + i);
        }
    }
}
