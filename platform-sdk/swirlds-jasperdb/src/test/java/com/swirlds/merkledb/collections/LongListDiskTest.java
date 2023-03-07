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

import static org.apache.commons.lang3.RandomStringUtils.randomAlphanumeric;
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
    void createHalfEmptyLongListOffHeapReadBack(LongList longList) throws IOException {
        populateList(longList);
        checkData(longList, SAMPLE_SIZE);

        longList.updateMinValidIndex(SAMPLE_SIZE / 2);
        final Path tempFile = testDirectory.resolve("LongListDiskTest.ll");
        longList.writeToFile(tempFile);
        // now open file with
        try (final LongListDisk longListDisk = new LongListDisk(tempFile)) {
            assertEquals(longList.size(), longListDisk.size(), "Unexpected value for longListDisk.size()");
            checkEmptyUpToIndex(longListDisk, SAMPLE_SIZE / 2);
            checkData(longListDisk, SAMPLE_SIZE / 2, SAMPLE_SIZE / 2);
        }
        // cleanup
        Files.delete(tempFile);
    }

    public static Stream<Arguments> inMemoryLongListProvider() {
        return Stream.of(Arguments.of(new LongListOffHeap()), Arguments.of(new LongListHeap()));
    }

    @Test
    public void updateMinToTheLowerEnd() throws IOException {
        final Path listFile = testDirectory.resolve("LongListDiskTest.ll");
        final LongListDisk longList = populateList(new LongListDisk(listFile));
        checkData(longList, SAMPLE_SIZE);
        int newMinValidIndex = SAMPLE_SIZE / 2;
        longList.updateMinValidIndex(newMinValidIndex);

        final Path halfEmptyListFile = testDirectory.resolve("LongListDiskTest_half_empty.ll");
        longList.writeToFile(halfEmptyListFile);

        try (LongListDisk halfEmptyList = new LongListDisk(halfEmptyListFile)) {
            // we cannot put a value below min valid index in effect
            assertThrows(IndexOutOfBoundsException.class, () -> halfEmptyList.put(newMinValidIndex - 1, nextInt()));
            assertThrows(
                    IndexOutOfBoundsException.class,
                    () -> halfEmptyList.putIfEqual(newMinValidIndex - 1, nextInt(), nextInt()));
            checkEmptyUpToIndex(longList, newMinValidIndex);
            // even if we update min valid index, we cannot put a value below the one that is in effect
            longList.updateMinValidIndex(0);
            assertThrows(IndexOutOfBoundsException.class, () -> halfEmptyList.put(newMinValidIndex - 1, nextInt()));
            assertThrows(
                    IndexOutOfBoundsException.class,
                    () -> halfEmptyList.putIfEqual(newMinValidIndex - 1, nextInt(), nextInt()));

            // however, once we create another file with the new min valid index, it comes into effect
            final Path zeroMinValidIndex = testDirectory.resolve("LongListDiskTest_half_empty.ll");
            longList.writeToFile(halfEmptyListFile);

            try (LongListDisk zeroMinValidIndexList = new LongListDisk(zeroMinValidIndex)) {
                for (int i = 0; i < newMinValidIndex; i++) {
                    longList.put(i, i + 100);
                }
                checkData(zeroMinValidIndexList, SAMPLE_SIZE);
            }
        }
    }

    @Test
    void createDiskReadBack() throws IOException {
        final Path tempFile = testDirectory.resolve("LongListDiskTest.ll");
        final LongListDisk longListDisk1 = new LongListDisk(tempFile);
        populateList(longListDisk1);
        checkData(longListDisk1, SMALL_SAMPLE_SIZE);
        // test changing data with putIf
        assertTrue(longListDisk1.putIfEqual(10, 110, 123), "Unexpected value from putIfEqual()");
        assertEquals(123, longListDisk1.get(10, -1), "Unexpected value from longListDisk1.get(10)");
        assertFalse(longListDisk1.putIfEqual(10, 110, 345), "Unexpected value from putIfEqual() #2");
        longListDisk1.put(10, 110); // put back
        // close
        longListDisk1.close();
        // now open file with
        final Path tempFile2 = testDirectory.resolve("LongListDiskTest2.ll");
        try (final LongListDisk longListDisk = new LongListDisk(tempFile)) {
            assertEquals(longListDisk1.size(), longListDisk.size(), "Unexpected value from longListDisk.size()");
            checkData(longListDisk, SMALL_SAMPLE_SIZE);
            // copy to a new file
            longListDisk.writeToFile(tempFile2);
            // check file sizes
            assertEquals(Files.size(tempFile), Files.size(tempFile2), "Unexpected value from Files.size(tempFile2)");
        }
        // open new file and check
        try (final LongListDisk longListDisk = new LongListDisk(tempFile2)) {
            assertEquals(longListDisk1.size(), longListDisk.size(), "Unexpected value from longListDisk.size()");
            checkData(longListDisk, SMALL_SAMPLE_SIZE);
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
        final Path listFile = testDirectory.resolve("LongListDiskTest.ll");
        final LongListDisk list = new LongListDisk(listFile);
        populateList(list);
        final long originalFileSize = Files.size(listFile);

        list.updateMinValidIndex(SMALL_SAMPLE_SIZE / 2);

        // half-empty
        checkEmptyUpToIndex(list, SMALL_SAMPLE_SIZE / 2);
        // half-full
        checkData(list, SMALL_SAMPLE_SIZE / 2, SMALL_SAMPLE_SIZE / 2);

        // if we write to the same file, it doesn't shrink after the min valid index update
        list.writeToFile(listFile);
        assertEquals(originalFileSize, Files.size(listFile));

        // if we create a copy, it'll be resized to the boundaries of to the content
        Path shrunkListFile = testDirectory.resolve(randomAlphanumeric(6));
        list.writeToFile(shrunkListFile);

        final long shrunkFileSize = Files.size(shrunkListFile);
        // size difference should be equal to the size of the removed elements
        assertEquals(SMALL_SAMPLE_SIZE / 2 * Long.BYTES, originalFileSize - shrunkFileSize);

        try (final LongListDisk loadedList = new LongListDisk(shrunkListFile)) {
            for (int i = 0; i < SMALL_SAMPLE_SIZE; i++) {
                assertEquals(list.get(i), loadedList.get(i), "Unexpected value in a loaded list");
            }
        }
    }

    private static void checkData(final LongList longList, final int sampleSize) {
        checkData(longList, 0, sampleSize);
    }

    private static void checkData(final LongList longList, final int startIndex, final int sampleSize) {
        for (int i = startIndex; i < sampleSize; i++) {
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
