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
import static com.swirlds.merkledb.files.DataFileCommon.FOOTER_SIZE;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.swirlds.common.utility.Units;
import com.swirlds.merkledb.KeyRange;
import com.swirlds.merkledb.collections.CASable;
import com.swirlds.merkledb.collections.ImmutableIndexedObjectListUsingArray;
import com.swirlds.merkledb.collections.IndexedObject;
import com.swirlds.merkledb.collections.LongListHeap;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

@SuppressWarnings("SameParameterValue")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class DataFileCollectionTest {

    /**
     * Temporary directory provided by JUnit
     */
    @SuppressWarnings("unused")
    @TempDir
    static Path tempFileDir;

    protected static final Instant TEST_START = Instant.now();
    protected static final Map<FilesTestType, DataFileCollection<long[]>> fileCollectionMap = new HashMap<>();
    protected static final Map<FilesTestType, LongListHeap> storedOffsetsMap = new HashMap<>();

    protected static long fixedSizeDataFileSize;

    private static final int MAX_TEST_FILE_MB = 16;

    // =================================================================================================================
    // Helper Methods

    /**
     * For tests, we want to have all different data sizes, so we use this function to choose how many times to repeat
     * the data value long
     */
    private static int getRepeatCountForKey(final long key) {
        return (int) (key % 20L);
    }

    /**
     * Create an example variable sized data item with lengths of data from 1 to 20.
     */
    private static long[] getVariableSizeDataForI(final int i, final int valueAddition) {
        final int repeatCount = getRepeatCountForKey(i);
        final long[] dataValue = new long[1 + repeatCount];
        dataValue[0] = i;
        for (int j = 1; j < dataValue.length; j++) {
            dataValue[j] = i + valueAddition;
        }
        return dataValue;
    }

    protected static void checkData(
            final FilesTestType testType, final int fromIndex, final int toIndexExclusive, final int valueAddition) {
        final DataFileCollection<long[]> fileCollection = fileCollectionMap.get(testType);
        final LongListHeap storedOffsets = storedOffsetsMap.get(testType);
        // now read back all the data and check all data
        for (int i = fromIndex; i < toIndexExclusive; i++) {
            // test read with index
            final long fi = i;
            final long[] dataItem2 = assertDoesNotThrow(
                    () -> fileCollection.readDataItemUsingIndex(storedOffsets, fi), "Read should not a exception.");
            checkDataItem(testType, valueAddition, dataItem2, i);
            try {
                assertNull(
                        fileCollection.readDataItemUsingIndex(storedOffsets, fi, false),
                        "A null should be returned with deserialize=false");
            } catch (IOException e) {
                e.printStackTrace();
                fail("No exceptional conditions expected here");
            }
        }
    }

    private static void checkDataItem(
            FilesTestType testType, final int valueAddition, final long[] dataItem, final int expectedKey) {
        assertNotNull(dataItem, "dataItem should not be null");
        switch (testType) {
            default:
            case fixed:
                assertEquals(2, dataItem.length, "unexpected length"); // size
                assertEquals(expectedKey, dataItem[0], "unexpected key"); // key
                assertEquals(expectedKey + valueAddition, dataItem[1], "unexpected value"); // value
                break;
            case variable:
                assertEquals(
                        Arrays.toString(getVariableSizeDataForI(expectedKey, valueAddition)),
                        Arrays.toString(dataItem),
                        "unexpected dataItem value");
                break;
        }
    }

    // =================================================================================================================
    // Tests

    @Order(1)
    @ParameterizedTest
    @EnumSource(FilesTestType.class)
    void createDataFileCollection(FilesTestType testType) throws Exception {
        final DataFileCollection<long[]> fileCollection = new DataFileCollection<>(
                tempFileDir.resolve(testType.name()), "test", testType.dataItemSerializer, null);

        assertSame(
                Collections.emptyList(),
                fileCollection.getAllFullyWrittenFiles(),
                "Initially there are no fully written files");
        assertSame(
                Collections.emptyList(),
                fileCollection.getAllFullyWrittenFiles(1),
                "Initially there are no fully written files");
        assertThrows(
                IOException.class,
                () -> fileCollection.storeDataItem(new long[0]),
                "Should not able to store data until writing is started");
        assertNull(
                fileCollection.readDataItem(DataFileCommon.NON_EXISTENT_DATA_LOCATION),
                "Should return null for non-existent data location");
        assertThrows(
                IOException.class,
                () -> fileCollection.readDataItem(-1),
                "Should not be able to read negative indices");
        assertEquals(new KeyRange(-1L, -1L), fileCollection.getValidKeyRange(), "start off with empty range");

        fileCollectionMap.put(testType, fileCollection);
        // create stored offsets list
        final LongListHeap storedOffsets = new LongListHeap(5000);
        storedOffsetsMap.put(testType, storedOffsets);
        // create 10x 100 item files
        int count = 0;
        for (int f = 0; f < 10; f++) {
            fileCollection.startWriting();
            if (f == 0) {
                assertThrows(
                        IOException.class,
                        fileCollection::startWriting,
                        "Cannot start writing until previous start has ended");
            }
            // put in 1000 items
            for (int i = count; i < count + 100; i++) {
                long[] dataValue;
                switch (testType) {
                    default:
                    case fixed:
                        dataValue = new long[] {i, i + 10_000};
                        break;
                    case variable:
                        dataValue = getVariableSizeDataForI(i, 10_000);
                        break;
                }
                // store in file
                storedOffsets.put(i, fileCollection.storeDataItem(dataValue));
            }
            fileCollection.endWriting(0, count + 100);
            assertEquals(new KeyRange(0, count + 100), fileCollection.getValidKeyRange(), "Range should be this");
            count += 100;
        }
        // check 10 files were created
        assertEquals(10, Files.list(tempFileDir.resolve(testType.name())).count(), "unexpected file count");
    }

    @Order(2)
    @Test
    void checkFileSizes() throws Exception {
        // we can only check for fixed size files easily
        final FilesTestType testType = FilesTestType.fixed;
        final long dataWritten = testType.dataItemSerializer.getSerializedSize() * 100L;
        final int paddingBytesNeeded = (int) (DataFileCommon.PAGE_SIZE - (dataWritten % DataFileCommon.PAGE_SIZE));
        fixedSizeDataFileSize = dataWritten + paddingBytesNeeded + FOOTER_SIZE;
        Files.list(tempFileDir.resolve(testType.name())).forEach(file -> {
            try {
                assertEquals(fixedSizeDataFileSize, Files.size(file), "unexpected file size");
            } catch (IOException e) {
                e.printStackTrace();
                fail("No exceptional conditions expected here");
            }
        });
    }

    @Order(3)
    @ParameterizedTest
    @EnumSource(FilesTestType.class)
    void check1000(final FilesTestType testType) {
        checkData(testType, 0, 1000, 10_000);
    }

    @Order(4)
    @ParameterizedTest
    @EnumSource(FilesTestType.class)
    void checkFilesStates(final FilesTestType testType) {
        final DataFileCollection<long[]> fileCollection = fileCollectionMap.get(testType);
        for (int f = 0; f < 10; f++) {
            final DataFileReader<long[]> dataFileReader = fileCollection.getDataFile(f);
            final DataFileMetadata metadata = dataFileReader.getMetadata();
            assertFalse(metadata.isMergeFile(), "Data files are not merge files");
            assertEquals(f, metadata.getIndex(), "Data file metadata should know self-index");
            assertTrue(metadata.getCreationDate().isAfter(TEST_START), "Creation dates should go forward in time");
            assertTrue(metadata.getCreationDate().isBefore(Instant.now()), "Creation dates may not be in the future");
            assertEquals(100, metadata.getDataItemCount(), "unexpected DataItemCount");
            assertEquals(0, dataFileReader.getSize() % DataFileCommon.PAGE_SIZE, "unexpected # DataFileReaders");
            if (testType == FilesTestType.fixed) {
                assertEquals(fixedSizeDataFileSize, dataFileReader.getSize(), "unexpected DataFileSize");
            }
        }
    }

    @Order(10)
    @ParameterizedTest
    @EnumSource(FilesTestType.class)
    void createDataFileCollectionWithLoadedDataCallback(final FilesTestType testType) throws Exception {
        fileCollectionMap.get(testType).close(); // close the old one so metadata is written to disk
        final LoadedDataCallbackImpl loadedDataCallbackImpl = new LoadedDataCallbackImpl();
        final DataFileCollection<long[]> fileCollection = new DataFileCollection<>(
                tempFileDir.resolve(testType.name()), "test", testType.dataItemSerializer, loadedDataCallbackImpl);
        fileCollectionMap.put(testType, fileCollection);
        // check that the 10 files were created previously (in the very first unit test) still are readable
        assertEquals(
                10,
                Files.list(tempFileDir.resolve(testType.name()))
                        .filter(f -> f.toString().endsWith(".jdb"))
                        .count(),
                "Temp file should not have changed since previous test in sequence");
        // examine loadedDataCallbackImpl content's map sizes as well as checking the data
        assertEquals(
                1000,
                loadedDataCallbackImpl.dataLocationMap.size(),
                "Size of data location map in collection loaded from store should reflect known size");
        assertEquals(
                1000,
                loadedDataCallbackImpl.dataValueMap.size(),
                "Size of data value map in collection loaded from store should reflect known size");
        checkData(testType, 0, 1000, 10_000);
        assertEquals(new KeyRange(0, 1000), fileCollection.getValidKeyRange(), "Should still have the valid range");
        assertTrue(
                fileCollection.isLoadedFromExistingFiles(),
                "Collection loaded from existing store should reflect this fact");

        // also try specifying a testStore (that doesn't exist) in a storeDir that does
        final LoadedDataCallbackImpl loadedDataCallbackImpl2 = new LoadedDataCallbackImpl();
        final DataFileCollection<long[]> fileCollection2 = new DataFileCollection<>(
                tempFileDir.resolve(testType.name()), "test2", testType.dataItemSerializer, loadedDataCallbackImpl2);
        assertEquals(
                0,
                loadedDataCallbackImpl2.dataLocationMap.size(),
                "Size of data location map in an empty collection should be zero");
        assertEquals(
                0,
                loadedDataCallbackImpl2.dataValueMap.size(),
                "Size of data value map in an empty collection should be zero");
        assertFalse(
                fileCollection2.isLoadedFromExistingFiles(),
                "Loaded-from-existing flag should be false if the test store doesn't exist");
        assertEquals(new KeyRange(-1L, -1L), fileCollection2.getValidKeyRange(), "Should be empty range");
    }

    @Order(50)
    @ParameterizedTest
    @EnumSource(FilesTestType.class)
    void closeAndReopen(final FilesTestType testType) throws Exception {
        final AtomicInteger numKeysRead = new AtomicInteger();
        final DataFileCollection.LoadedDataCallback testCallback =
                (key, dataLoc, buffer) -> numKeysRead.incrementAndGet();
        final DataFileCollection<long[]> fileCollection = fileCollectionMap.get(testType);
        assertEquals(new KeyRange(0, 1000), fileCollection.getValidKeyRange(), "Should still have the valid range");
        fileCollection.close();
        assertDoesNotThrow(
                () -> {
                    final DataFileCollection<long[]> reopenedFileCollection = new DataFileCollection<>(
                            tempFileDir.resolve(testType.name()), "test", testType.dataItemSerializer, testCallback);
                    fileCollectionMap.put(testType, reopenedFileCollection);
                },
                "Shouldn't be a problem re-opening a closed collection");
        assertEquals(1000, numKeysRead.get(), "Should have been 1000 keys in the collection");
        final DataFileCollection<long[]> reopened = fileCollectionMap.get(testType);
        assertEquals(new KeyRange(0, 1000), reopened.getValidKeyRange(), "Should still have the valid range");
    }

    @Order(51)
    @ParameterizedTest
    @EnumSource(FilesTestType.class)
    void check1000AfterReopen(final FilesTestType testType) {
        checkData(testType, 0, 1000, 10_000);
    }

    /**
     * Special slow wrapper on ImmutableIndexedObjectListUsingArray that slows down gets, this causing threading bugs
     * during merging where deletion and reading race each other, to be always reproducible.
     */
    private static class SlowImmutableIndexedObjectListUsingArray<T extends IndexedObject>
            extends ImmutableIndexedObjectListUsingArray<T> {
        public SlowImmutableIndexedObjectListUsingArray(final List<T> objects) {
            super(objects);
        }

        @Override
        public T get(final int objectIndex) {
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            return super.get(objectIndex);
        }
    }

    /**
     * Reopen database using SlowImmutableIndexedObjectListUsingArray ready for merging test
     */
    @Order(99)
    @ParameterizedTest
    @EnumSource(FilesTestType.class)
    void closeAndReopenInSlowModeForMerging(final FilesTestType testType) throws Exception {
        final AtomicInteger numKeysRead = new AtomicInteger();
        final DataFileCollection.LoadedDataCallback testCallback =
                (key, dataLoc, buffer) -> numKeysRead.incrementAndGet();
        final DataFileCollection<long[]> fileCollection = fileCollectionMap.get(testType);
        fileCollection.close();
        assertDoesNotThrow(
                () -> {
                    final DataFileCollection<long[]> reopenedFileCollection = new DataFileCollection<>(
                            tempFileDir.resolve(testType.name()),
                            "test",
                            null,
                            testType.dataItemSerializer,
                            testCallback,
                            SlowImmutableIndexedObjectListUsingArray::new);
                    fileCollectionMap.put(testType, reopenedFileCollection);
                },
                "Shouldn't be a problem re-opening a closed collection");
        assertEquals(1000, numKeysRead.get(), "Should have been 1000 keys in the collection");
        final DataFileCollection<long[]> reopened = fileCollectionMap.get(testType);
        assertEquals(new KeyRange(0, 1000), reopened.getValidKeyRange(), "Should still have the valid range");
    }

    @Order(100)
    @ParameterizedTest
    @EnumSource(FilesTestType.class)
    void merge(final FilesTestType testType) throws Exception {
        final DataFileCollection<long[]> fileCollection = fileCollectionMap.get(testType);
        final LongListHeap storedOffsets = storedOffsetsMap.get(testType);
        final AtomicBoolean mergeComplete = new AtomicBoolean(false);
        final int NUM_OF_KEYS = 1000;
        final int NUM_OF_THREADS = 5;
        IntStream.range(0, NUM_OF_THREADS).parallel().forEach(thread -> {
            if (thread == 0) { // checking thread, keep reading and checking data all the time while we are merging
                while (!mergeComplete.get()) {
                    try {
                        for (int i = 0; i < NUM_OF_KEYS; i++) {
                            final long[] dataItem = fileCollection.readDataItemUsingIndex(storedOffsets, i);
                            assertNotNull(dataItem, "DataItem should never be null");
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            } else if (thread < (NUM_OF_THREADS - 1)) { // check reading item at index 100 as fast as possible to try
                // and slip though cracks
                System.out.println("START READING");
                while (!mergeComplete.get()) {
                    try {
                        final long[] dataItem = fileCollection.readDataItemUsingIndex(storedOffsets, 100);
                        assertNotNull(dataItem, "DataItem should never be null");
                    } catch (ClosedChannelException e) {
                        e.printStackTrace();
                        fail("Exception should not be thrown");
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            } else if (thread == (NUM_OF_THREADS - 1)) { // move thread
                System.out.println("DataFileCollectionTest.merge");
                List<Path> mergeResults = null;
                try {
                    List<DataFileReader<long[]>> filesToMerge =
                            fileCollection.getAllFullyWrittenFiles(MAX_TEST_FILE_MB);
                    System.out.println("filesToMerge = " + filesToMerge.size());
                    AtomicInteger numMoves = new AtomicInteger(0);
                    Set<Integer> allKeysExpectedToBeThere =
                            IntStream.range(0, NUM_OF_KEYS).boxed().collect(Collectors.toSet());
                    CASable indexUpdater = new CASable() {
                        public long get(long key) {
                            return storedOffsets.get(key);
                        }

                        public boolean putIfEqual(long key, long oldValue, long newValue) {
                            numMoves.getAndIncrement();
                            assertTrue(
                                    DataFileCommon.fileIndexFromDataLocation(oldValue) < 10,
                                    "check we are moving from a old file");
                            assertEquals(
                                    10,
                                    DataFileCommon.fileIndexFromDataLocation(newValue),
                                    "check we are moving to a new file 10");
                            assertTrue(
                                    allKeysExpectedToBeThere.remove((int) key),
                                    "check each key was in list of expected keys");
                            return storedOffsets.putIfEqual(key, oldValue, newValue);
                        }
                    };

                    mergeResults = fileCollection.mergeFiles(indexUpdater, filesToMerge, new Semaphore(1));
                    assertTrue(allKeysExpectedToBeThere.isEmpty(), "check there were no missed keys");
                    System.out.println(
                            "============= MERGE [" + numMoves.get() + "] MOVES DONE ===========================");
                    assertEquals(NUM_OF_KEYS, numMoves.get(), "unexpected # of moves");

                    System.out.println("============= MERGE DONE ===========================");
                    // check all locations are now in new file created during merge
                    storedOffsets.stream()
                            .forEach(location -> assertEquals(
                                    10,
                                    DataFileCommon.fileIndexFromDataLocation(location),
                                    "Expect all data to be correct"));
                    System.out.println("============= CHECK DONE ===========================");
                } catch (Exception e) {
                    e.printStackTrace();
                    fail("Exception should not be thrown");
                }
                final KeyRange validKeyRange = fileCollection.getValidKeyRange();
                assertEquals(new KeyRange(0, NUM_OF_KEYS), validKeyRange, "Should still have values");
                mergeComplete.set(true);
                // all files should have been merged into 1.
                assertEquals(1, mergeResults == null ? -1 : mergeResults.size(), "unexpected # of post-merge files");
            }
        });
        // check we only have 1 file left
        assertEquals(
                1,
                Files.list(tempFileDir.resolve(testType.name()))
                        .filter(f -> f.toString().endsWith(".jdb"))
                        .count(),
                "unexpected # of files #1");
        // After merge is complete, there should be only 1 "fully written" file, and that it is empty.
        List<DataFileReader<long[]>> filesLeft = fileCollection.getAllFullyWrittenFiles(Integer.MAX_VALUE);
        assertEquals(1, filesLeft.size(), "unexpected # of files #2");
        filesLeft = fileCollection.getAllFullyWrittenFiles(1); // files with size less than 1 are empty
        assertEquals(1, filesLeft.size(), "unexpected # of files #3");

        // and trying to merge just one file is a no-op
        List<Path> secondMergeResults = fileCollection.mergeFiles(null, filesLeft, new Semaphore(1));
        assertEquals(0, secondMergeResults.size(), "unexpected results from second merge");
    }

    @Order(101)
    @ParameterizedTest
    @EnumSource(FilesTestType.class)
    void check1000AfterMerge(final FilesTestType testType) {
        checkData(testType, 0, 1000, 10_000);
    }

    @Order(200)
    @ParameterizedTest
    @EnumSource(FilesTestType.class)
    void changeSomeData(final FilesTestType testType) throws Exception {
        final DataFileCollection<long[]> fileCollection = fileCollectionMap.get(testType);
        final LongListHeap storedOffsets = storedOffsetsMap.get(testType);
        fileCollection.startWriting();
        // put in 1000 items
        for (int i = 0; i < 50; i++) {
            long[] dataValue;
            switch (testType) {
                default:
                case fixed:
                    dataValue = new long[] {i, i + 100_000};
                    break;
                case variable:
                    dataValue = getVariableSizeDataForI(i, 100_000);
                    break;
            }
            // store in file
            storedOffsets.put(i, fileCollection.storeDataItem(dataValue));
        }
        fileCollection.endWriting(0, 1000);
        // check we now have 2 files
        assertEquals(
                2,
                Files.list(tempFileDir.resolve(testType.name()))
                        .filter(f -> f.toString().endsWith(".jdb"))
                        .count(),
                "unexpected # of files");
    }

    @Order(201)
    @ParameterizedTest
    @EnumSource(FilesTestType.class)
    void check1000BeforeMerge(final FilesTestType testType) {
        checkData(testType, 0, 50, 100_000);
        checkData(testType, 50, 1000, 10_000);
    }

    @Order(202)
    @ParameterizedTest
    @EnumSource(FilesTestType.class)
    void merge2(final FilesTestType testType) throws Exception {
        final DataFileCollection<long[]> fileCollection = fileCollectionMap.get(testType);
        final LongListHeap storedOffsets = storedOffsetsMap.get(testType);
        final AtomicBoolean mergeComplete = new AtomicBoolean(false);
        // start merging paused so that we can test pausing
        final Semaphore mergePaused = new Semaphore(0);

        IntStream.range(0, 3).parallel().forEach(thread -> {
            if (thread == 0) { // checking thread, keep reading and checking data all the time while we are merging
                while (!mergeComplete.get()) {
                    try {
                        checkData(testType, 0, 50, 100_000);
                        checkData(testType, 50, 1000, 10_000);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            } else if (thread == 1) { // move thread
                // merge 2 files
                try {
                    List<DataFileReader<long[]>> allFiles = fileCollection.getAllFullyWrittenFiles();
                    Set<Integer> allKeysExpectedToBeThere =
                            IntStream.range(0, 1000).boxed().collect(Collectors.toSet());
                    final CASable indexUpdater = new CASable() {
                        public long get(long key) {
                            return storedOffsets.get(key);
                        }

                        public boolean putIfEqual(long key, long oldValue, long newValue) {
                            assertTrue(
                                    DataFileCommon.fileIndexFromDataLocation(oldValue) < 12,
                                    "check we are moving from a old file");
                            assertEquals(
                                    12,
                                    DataFileCommon.fileIndexFromDataLocation(newValue),
                                    "check we are moving to a new file 10");
                            assertTrue(
                                    allKeysExpectedToBeThere.remove((int) key),
                                    "check each key was in list of expected keys");
                            return storedOffsets.putIfEqual(key, oldValue, newValue);
                        }
                    };
                    fileCollection.mergeFiles(indexUpdater, allFiles, mergePaused);
                    assertTrue(allKeysExpectedToBeThere.isEmpty(), "check there were no missed keys");
                } catch (Exception e) {
                    e.printStackTrace();
                }
                mergeComplete.set(true);
            } else if (thread == 2) { // un-pause merging thread
                System.out.println("Unpause thread starting and waiting 300ms");
                try {
                    MILLISECONDS.sleep(300);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    throw new RuntimeException(e);
                }
                System.out.println("Un-pausing merging");
                // now let merging continue
                mergePaused.release();
            }
        });
        // check we 7 files left, as we merged 5 out of 11
        assertEquals(
                1,
                Files.list(tempFileDir.resolve(testType.name()))
                        .filter(f -> f.toString().endsWith(".jdb"))
                        .count(),
                "unexpected # of files");
    }

    @Order(203)
    @ParameterizedTest
    @EnumSource(FilesTestType.class)
    void check1000AfterMerge2(final FilesTestType testType) {
        checkData(testType, 0, 50, 100_000);
        checkData(testType, 50, 1000, 10_000);
    }

    @Order(1000)
    @ParameterizedTest
    @EnumSource(FilesTestType.class)
    void close(final FilesTestType testType) {
        assertDoesNotThrow(() -> fileCollectionMap.get(testType).close(), "unexpected exception from close()");
    }

    @Order(2000)
    @ParameterizedTest
    @EnumSource(FilesTestType.class)
    void mergeWorksAfterOpen(final FilesTestType testType) throws Exception {
        final Path dbDir = tempFileDir.resolve(testType.name());
        final String storeName = "mergeWorksAfterOpen";
        final DataFileCollection<long[]> fileCollection =
                new DataFileCollection<>(dbDir, storeName, testType.dataItemSerializer, null);
        assertSame(0, fileCollection.getAllFullyWrittenFiles().size(), "Should be no files");
        fileCollectionMap.put(testType, fileCollection);
        // create stored offsets list
        final LongListHeap storedOffsets = new LongListHeap(5000);
        storedOffsetsMap.put(testType, storedOffsets);
        // create 10x 100 item files
        int count = 0;
        for (int f = 0; f < 10; f++) {
            fileCollection.startWriting();
            // put in 1000 items
            for (int i = count; i < count + 100; i++) {
                long[] dataValue;
                switch (testType) {
                    default:
                    case fixed:
                        dataValue = new long[] {i, i + 10_000};
                        break;
                    case variable:
                        dataValue = getVariableSizeDataForI(i, 10_000);
                        break;
                }
                // store in file
                storedOffsets.put(i, fileCollection.storeDataItem(dataValue));
            }
            fileCollection.endWriting(0, count + 100).setFileAvailableForMerging(true);
            count += 100;
        }
        // check 10 files were created and data is correct
        assertEquals(
                10,
                Files.list(dbDir)
                        .filter(file -> file.getFileName().toString().startsWith(storeName))
                        .count(),
                "expected 10 db files");
        assertSame(10, fileCollection.getAllFullyWrittenFiles().size(), "Should be 10 files");
        checkData(testType, 0, 1000, 10_000);
        // check all files are available for merge
        assertSame(
                10, fileCollection.getAllFilesAvailableForMerge().size(), "Should be 10 files available for merging");
        // close
        fileCollection.close();
        // reopen
        final DataFileCollection<long[]> fileCollection2 =
                new DataFileCollection<>(dbDir, storeName, testType.dataItemSerializer, null);
        fileCollectionMap.put(testType, fileCollection2);
        // check 10 files were opened and data is correct
        assertSame(10, fileCollection2.getAllFullyWrittenFiles().size(), "Should be 10 files");
        checkData(testType, 0, 1000, 10_000);
        // check all files are available for merge
        assertSame(
                10, fileCollection2.getAllFilesAvailableForMerge().size(), "Should be 10 files available for merging");
        // merge
        fileCollection2.mergeFiles(storedOffsets, fileCollection2.getAllFilesAvailableForMerge(), new Semaphore(1));
        // check 1 files were opened and data is correct
        assertSame(1, fileCollection2.getAllFullyWrittenFiles().size(), "Should be 1 files");
        assertEquals(
                1,
                Files.list(dbDir)
                        .filter(file -> file.getFileName().toString().matches(storeName + ".*jdb"))
                        .count(),
                "expected 1 db files but had ["
                        + Arrays.toString(Files.list(dbDir).toArray()) + "]");
        checkData(testType, 0, 1000, 10_000);
        // close db
        fileCollection2.close();
    }

    /**
     * Keep track of initial direct memory used already, so we can check if we leek over and above what we started with
     */
    private long directMemoryUsedAtStart;

    @BeforeEach
    void initializeDirectMemoryAtStart() {
        directMemoryUsedAtStart = getDirectMemoryUsedBytes();
    }

    @AfterEach
    void checkDirectMemoryUsage() {
        // check all memory is freed after DB is closed
        assertTrue(
                checkDirectMemoryIsCleanedUpToLessThanBaseUsage(directMemoryUsedAtStart),
                "Direct Memory used is more than base usage even after 20 gc() calls. At start was "
                        + (directMemoryUsedAtStart * Units.BYTES_TO_MEBIBYTES) + "MB and is now "
                        + (getDirectMemoryUsedBytes() * Units.BYTES_TO_MEBIBYTES)
                        + "MB");
    }

    private static class LoadedDataCallbackImpl implements DataFileCollection.LoadedDataCallback {
        public final Map<Long, Long> dataLocationMap = new HashMap<>();
        public final Map<Long, ByteBuffer> dataValueMap = new HashMap<>();

        @Override
        public void newIndexEntry(final long key, final long dataLocation, final ByteBuffer dataValue) {
            dataLocationMap.put(key, dataLocation);
            dataValueMap.put(key, dataValue);
        }
    }
}
