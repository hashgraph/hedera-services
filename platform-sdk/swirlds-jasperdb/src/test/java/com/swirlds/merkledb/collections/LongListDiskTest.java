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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@SuppressWarnings("FieldCanBeLocal")
class LongListDiskTest {
    private final int SAMPLE_SIZE = 10_000;
    private final int SMALL_SAMPLE_SIZE = 120;

    /**
     * Temporary directory provided by JUnit
     */
    @SuppressWarnings("unused")
    @TempDir
    Path testDirectory;

    @Test
    void createOffHeapReadBack() throws IOException {
        LongListOffHeap longListOffHeap = new LongListOffHeap();
        for (int i = 0; i < SAMPLE_SIZE; i++) {
            longListOffHeap.put(i, i + 100);
        }
        checkData(longListOffHeap, SAMPLE_SIZE);
        Path tempFile = testDirectory.resolve("LongListDiskTest.ll");
        longListOffHeap.writeToFile(tempFile);
        // now open file with
        try (LongListDisk longListDisk = new LongListDisk(tempFile)) {
            assertEquals(longListOffHeap.size(), longListDisk.size(), "Unexpected value for longListDisk.size()");
            checkData(longListDisk, SAMPLE_SIZE);
        }
        // cleanup
        Files.delete(tempFile);
    }

    @Test
    void createHeapReadBack() throws IOException {
        LongListHeap longListHeap = new LongListHeap();
        for (int i = 0; i < SAMPLE_SIZE; i++) {
            longListHeap.put(i, i + 100);
        }
        checkData(longListHeap, SAMPLE_SIZE);
        Path tempFile = testDirectory.resolve("LongListDiskTest.ll");
        longListHeap.writeToFile(tempFile);
        // now open file with
        try (LongListDisk longListDisk = new LongListDisk(tempFile)) {
            assertEquals(longListHeap.size(), longListDisk.size(), "Unexpected value for longListDisk.size()");
            checkData(longListDisk, SAMPLE_SIZE);
        }
        // cleanup
        Files.delete(tempFile);
    }

    @Test
    void createDiskReadBack() throws IOException {
        Path tempFile = testDirectory.resolve("LongListDiskTest.ll");
        LongListDisk longListDisk1 = new LongListDisk(tempFile);
        for (int i = 0; i < SMALL_SAMPLE_SIZE; i++) {
            longListDisk1.put(i, i + 100);
        }
        checkData(longListDisk1, SMALL_SAMPLE_SIZE);
        // test changing data with putIf
        assertTrue(longListDisk1.putIfEqual(10, 110, 123), "Unexpected value from putIfEqual()");
        assertEquals(123, longListDisk1.get(10, -1), "Unexpected value from longListDisk1.get(10)");
        assertFalse(longListDisk1.putIfEqual(10, 110, 345), "Unexpected value from putIfEqual() #2");
        longListDisk1.put(10, 110); // put back
        // close
        longListDisk1.close();
        // now open file with
        Path tempFile2 = testDirectory.resolve("LongListDiskTest2.ll");
        try (LongListDisk longListDisk = new LongListDisk(tempFile)) {
            assertEquals(longListDisk1.size(), longListDisk.size(), "Unexpected value from longListDisk.size()");
            checkData(longListDisk, SMALL_SAMPLE_SIZE);
            // copy to a new file
            longListDisk.writeToFile(tempFile2);
            // check file sizes
            assertEquals(Files.size(tempFile), Files.size(tempFile2), "Unexpected value from Files.size(tempFile2)");
        }
        // open new file and check
        try (LongListDisk longListDisk = new LongListDisk(tempFile2)) {
            assertEquals(longListDisk1.size(), longListDisk.size(), "Unexpected value from longListDisk.size()");
            checkData(longListDisk, SMALL_SAMPLE_SIZE);
        }
    }

    private void checkData(LongList longList, int sampleSize) {
        for (int i = 0; i < sampleSize; i++) {
            assertEquals(i + 100, longList.get(i, -1), "Unexpected value from longList.get(" + i + ")");
        }
    }
}
