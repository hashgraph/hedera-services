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

package com.swirlds.merkledb.files;

import static com.swirlds.merkledb.MerkleDbTestUtils.checkDirectMemoryIsCleanedUpToLessThanBaseUsage;
import static com.swirlds.merkledb.MerkleDbTestUtils.getDirectMemoryUsedBytes;
import static com.swirlds.merkledb.files.DataFileCommon.deleteDirectoryAndContents;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.swirlds.common.utility.Units;
import com.swirlds.merkledb.collections.LongListHeap;
import com.swirlds.merkledb.collections.LongListOffHeap;
import com.swirlds.test.framework.TestQualifierTags;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class MemoryIndexDiskKeyValueStoreTest {

    /** Temporary directory provided by JUnit */
    @SuppressWarnings("unused")
    @TempDir
    Path testDirectory;

    // =================================================================================================================
    // Helper Methods

    /**
     * For tests, we want to have all different dta sizes, so we use this function to choose how
     * many times to repeat the data value long
     */
    private static int getRepeatCountForKey(final long key) {
        return (int) (key % 20L);
    }

    /** Create an example variable sized data item with lengths of data from 0 to 20. */
    private static long[] getVariableSizeDataForI(final int i, final int valueAddition) {
        final int repeatCount = getRepeatCountForKey(i);
        final long[] dataValue = new long[1 + repeatCount];
        dataValue[0] = i;
        for (int j = 1; j < dataValue.length; j++) {
            dataValue[j] = i + valueAddition;
        }
        return dataValue;
    }

    private void checkRange(
            final FilesTestType testType,
            final MemoryIndexDiskKeyValueStore<long[]> store,
            final int start,
            final int count,
            final int valueAddition)
            throws IOException {
        for (int i = start; i < (start + count); i++) {
            // read
            final var dataItem = store.get(i);
            assertNotNull(dataItem, "dataItem unexpectedly null");
            assertNull(store.get(i, false), "dataItem should be null if deserialize=false");
            switch (testType) {
                default:
                case fixed:
                    assertEquals(2, dataItem.length, "unexpected dataItem length"); // size
                    assertEquals(i, dataItem[0], "unexpected dataItem key"); // key
                    assertEquals(i + valueAddition, dataItem[1], "unexpected dataItem value"); // value
                    break;
                case variable:
                    assertEquals(
                            Arrays.toString(getVariableSizeDataForI(i, valueAddition)),
                            Arrays.toString(dataItem),
                            "unexpected dataItem value for variable-sized test");
                    break;
            }
        }
    }

    private void writeBatch(
            final FilesTestType testType,
            final MemoryIndexDiskKeyValueStore<long[]> store,
            final int start,
            final int count,
            final long lastLeafPath,
            final int valueAddition)
            throws IOException {
        store.startWriting(0);
        writeDataBatch(testType, store, start, count, valueAddition);
        store.endWriting(0, lastLeafPath);
    }

    private void writeDataBatch(
            final FilesTestType testType,
            final MemoryIndexDiskKeyValueStore<long[]> store,
            final int start,
            final int count,
            final int valueAddition)
            throws IOException {
        for (int i = start; i < (start + count); i++) {
            long[] dataValue;
            switch (testType) {
                default:
                case fixed:
                    dataValue = new long[] {i, i + valueAddition};
                    break;
                case variable:
                    dataValue = getVariableSizeDataForI(i, valueAddition);
                    break;
            }
            store.put(i, dataValue);
        }
    }

    // =================================================================================================================
    // Tests

    /*
     * RUN THE TEST IN A BACKGROUND THREAD. We do this so that we can kill the thread at the end of the test which will
     * clean up all thread local caches held.
     */
    @ParameterizedTest
    @EnumSource(FilesTestType.class)
    @Tag(TestQualifierTags.TIME_CONSUMING)
    void createDataAndCheck(final FilesTestType testType) throws Exception {
        MemoryIndexDiskKeyValueStore.enableDeepValidation = false;
        // keep track of base direct-memory usage, so we can check we did not leak
        final long directMemoryUsedAtStart = getDirectMemoryUsedBytes();
        // run test in a background thread that we can shut down.
        final ExecutorService executorService = Executors.newSingleThreadExecutor();
        var future = executorService.submit(() -> {
            createDataAndCheckImpl(testType);
            return null;
        });
        future.get(10, TimeUnit.MINUTES);
        executorService.shutdown();
        // check all memory is freed after DB is closed
        assertTrue(
                checkDirectMemoryIsCleanedUpToLessThanBaseUsage(directMemoryUsedAtStart),
                "Direct Memory used is more than base usage even after 20 gc() calls. At start was "
                        + (directMemoryUsedAtStart * Units.BYTES_TO_MEBIBYTES)
                        + "MB and is now "
                        + (getDirectMemoryUsedBytes() * Units.BYTES_TO_MEBIBYTES)
                        + "MB");
    }

    void createDataAndCheckImpl(final FilesTestType testType) throws Exception {
        // let's store hashes as easy test class
        final Path tempDir = testDirectory.resolve("DataFileTest");
        final LongListOffHeap index = new LongListOffHeap();
        final MemoryIndexDiskKeyValueStore<long[]> store = new MemoryIndexDiskKeyValueStore<>(
                tempDir, "MemoryIndexDiskKeyValueStoreTest", null, testType.dataItemSerializer, null, index);
        // write some batches of data, then check all the contents, we should end up with 3 files
        writeBatch(testType, store, 0, 1000, 1000, 1234);
        checkRange(testType, store, 0, 1000, 1234);
        writeBatch(testType, store, 1000, 1500, 2500, 1234);
        checkRange(testType, store, 0, 1500, 1234);
        writeBatch(testType, store, 1500, 2000, 3500, 1234);
        checkRange(testType, store, 0, 2000, 1234);
        // check number of files created
        assertEquals(3, Files.list(tempDir).count(), "unexpected # of files #1");
        // merge all files
        store.merge(dataFileReaders -> dataFileReaders, 2);
        // check number of files after merge
        assertEquals(1, Files.list(tempDir).count(), "unexpected # of files #2");
        // check all data
        checkRange(testType, store, 0, 2000, 1234);
        // change some data and check
        writeBatch(testType, store, 1500, 2000, 3500, 8910);
        checkRange(testType, store, 0, 1500, 1234);
        checkRange(testType, store, 1500, 2000, 8910);
        // do one more write, so we have two files and all data has same valueAddition
        writeBatch(testType, store, 0, 1500, 3500, 8910);
        // do a merge, read in parallel
        final AtomicBoolean mergeFinished = new AtomicBoolean(false);
        IntStream.range(0, 2).parallel().forEach(i -> {
            if (i == 0) {
                while (!mergeFinished.get()) {
                    try {
                        checkRange(testType, store, 0, 2000, 8910);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            } else {
                try {
                    store.merge(dataFileReaders -> dataFileReaders, 2);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                mergeFinished.set(true);
            }
        });
        // test one writer and many readers in parallel
        IntStream.range(0, 10).parallel().forEach(i -> {
            try {
                if (i == 0) {
                    writeBatch(testType, store, 2000, 50_000, 52_000, 56_000);
                } else {
                    Thread.sleep(i);
                    checkRange(testType, store, 0, 2000, 8910);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        // check all data is correct after
        checkRange(testType, store, 0, 2000, 8910);
        checkRange(testType, store, 2000, 48_000, 56_000);
        // check number of files created
        assertEquals(2, Files.list(tempDir).count(), "unexpected # of files #3");

        // create a snapshot
        final Path tempSnapshotDir = testDirectory.resolve("DataFileTestSnapshot");
        store.snapshot(tempSnapshotDir);
        // check all files are in new dir
        Files.list(tempDir).forEach(file -> {
            assertTrue(Files.exists(tempSnapshotDir.resolve(file.getFileName())), "Expected file does not exist");
            try {
                assertEquals(
                        Files.size(file),
                        Files.size(tempSnapshotDir.resolve(file.getFileName())),
                        "Unexpected value from Files.size()");
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
        // open snapshot and check data
        final LongListOffHeap snapshotIndex = new LongListOffHeap();
        final MemoryIndexDiskKeyValueStore<long[]> storeFromSnapshot = new MemoryIndexDiskKeyValueStore<>(
                tempSnapshotDir,
                "MemoryIndexDiskKeyValueStoreTest",
                null,
                testType.dataItemSerializer,
                null,
                snapshotIndex);
        checkRange(testType, storeFromSnapshot, 0, 2000, 8910);
        checkRange(testType, storeFromSnapshot, 2000, 48_000, 56_000);
        storeFromSnapshot.close();
        snapshotIndex.close();
        // clean up and delete files
        store.close();
        index.close();
        deleteDirectoryAndContents(tempDir);
        deleteDirectoryAndContents(tempSnapshotDir);
    }

    @ParameterizedTest
    @EnumSource(FilesTestType.class)
    void createDataAndCheckWithDeepValidation(final FilesTestType testType) throws Exception {
        MemoryIndexDiskKeyValueStore.enableDeepValidation = true;
        // let's store hashes as easy test class
        final Path tempDir = testDirectory.resolve("DataFileTest");
        final LongListOffHeap index = new LongListOffHeap();
        final MemoryIndexDiskKeyValueStore<long[]> store = new MemoryIndexDiskKeyValueStore<>(
                tempDir, "MemoryIndexDiskKeyValueStoreTest", null, testType.dataItemSerializer, null, index);
        // write some batches of data, then check all the contents, we should end up with 3 files
        writeBatch(testType, store, 0, 10, 10, 1234);
        writeBatch(testType, store, 10, 20, 30, 1234);
        checkRange(testType, store, 0, 20, 1234);
        // start writing new range
        store.startWriting(10);
        writeDataBatch(testType, store, 10, 30, 5678);
        // merge all files
        store.merge(dataFileReaders -> dataFileReaders, 2);
        // finish writing range
        store.endWriting(10, 30);
        checkRange(testType, store, 10, 20, 5678);
        // check get out of range
        assertNull(store.get(1), "Getting a value that is below valid key range should return null.");
        assertNull(store.get(100), "Getting a value that is above valid key range should return null.");
        // clean up and delete files
        store.close();
        index.close();
        deleteDirectoryAndContents(tempDir);
        MemoryIndexDiskKeyValueStore.enableDeepValidation = false;
    }

    @Test
    void legacyStoreNameTest() throws Exception {
        final Path tempDir = testDirectory.resolve("legacyStoreNameTest");
        final FilesTestType testType = FilesTestType.fixed;
        final LongListHeap index1 = new LongListHeap();
        final MemoryIndexDiskKeyValueStore<long[]> store1 = new MemoryIndexDiskKeyValueStore<>(
                tempDir, "store:name", null, new ExampleFixedSizeDataSerializer(), null, index1);
        writeBatch(testType, store1, 0, 100, 100, 11);
        checkRange(testType, store1, 0, 100, 11);
        store1.close();
        index1.close();

        final LongListHeap index3 = new LongListHeap();
        final MemoryIndexDiskKeyValueStore<long[]> store3 = new MemoryIndexDiskKeyValueStore<>(
                tempDir, "store_name", "store:name", new ExampleFixedSizeDataSerializer(), null, index3);
        checkRange(testType, store3, 0, 100, 11);
        assertNull(store3.get(101));
        store3.close();
        index3.close();
    }
}
