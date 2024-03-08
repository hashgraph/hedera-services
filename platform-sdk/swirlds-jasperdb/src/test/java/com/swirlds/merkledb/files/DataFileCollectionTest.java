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

package com.swirlds.merkledb.files;

import static com.swirlds.common.test.fixtures.AssertionUtils.assertEventuallyTrue;
import static com.swirlds.common.test.fixtures.junit.tags.TestQualifierTags.TIMING_SENSITIVE;
import static com.swirlds.merkledb.files.DataFileCollectionTestUtils.checkData;
import static com.swirlds.merkledb.files.DataFileCollectionTestUtils.getVariableSizeDataForI;
import static com.swirlds.merkledb.test.fixtures.MerkleDbTestUtils.checkDirectMemoryIsCleanedUpToLessThanBaseUsage;
import static com.swirlds.merkledb.test.fixtures.MerkleDbTestUtils.getDirectMemoryUsedBytes;
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
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

import com.swirlds.base.units.UnitConstants;
import com.swirlds.common.config.singleton.ConfigurationHolder;
import com.swirlds.merkledb.KeyRange;
import com.swirlds.merkledb.collections.CASableLongIndex;
import com.swirlds.merkledb.collections.ImmutableIndexedObjectListUsingArray;
import com.swirlds.merkledb.collections.IndexedObject;
import com.swirlds.merkledb.collections.LongListHeap;
import com.swirlds.merkledb.config.MerkleDbConfig;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mockito;

@Tag(TIMING_SENSITIVE)
@SuppressWarnings("SameParameterValue")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class DataFileCollectionTest {

    private static final MerkleDbConfig config = ConfigurationHolder.getConfigData(MerkleDbConfig.class);

    /** Temporary directory provided by JUnit */
    @SuppressWarnings("unused")
    @TempDir
    static Path tempFileDir;

    protected static final Instant TEST_START = Instant.now();
    protected static final Map<FilesTestType, DataFileCollection<long[]>> fileCollectionMap = new HashMap<>();
    protected static final Map<FilesTestType, LongListHeap> storedOffsetsMap = new HashMap<>();

    private static final int MAX_TEST_FILE_MB = 16;

    // =================================================================================================================
    // Tests

    @Order(1)
    @ParameterizedTest
    @EnumSource(FilesTestType.class)
    void createDataFileCollection(FilesTestType testType) throws Exception {
        final DataFileCollection<long[]> fileCollection = new DataFileCollection<>(
                config, tempFileDir.resolve(testType.name()), "test", testType.dataItemSerializer, null);

        assertSame(
                Collections.emptyList(),
                fileCollection.getAllCompletedFiles(),
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
            final DataFileReader<long[]> newFile = fileCollection.endWriting(0, count + 100);
            newFile.setFileCompleted();
            assertEquals(new KeyRange(0, count + 100), fileCollection.getValidKeyRange(), "Range should be this");
            assertEquals(Files.size(newFile.getPath()), newFile.getSize());
            count += 100;
        }
        // check 10 files were created
        assertEquals(10, Files.list(tempFileDir.resolve(testType.name())).count(), "unexpected file count");
    }

    @Order(3)
    @ParameterizedTest
    @EnumSource(FilesTestType.class)
    void check1000(final FilesTestType testType) {
        checkData(fileCollectionMap.get(testType), storedOffsetsMap.get(testType), testType, 0, 1000, 10_000);
    }

    @Order(4)
    @ParameterizedTest
    @EnumSource(FilesTestType.class)
    void checkFilesStates(final FilesTestType testType) throws IOException {
        final DataFileCollection<long[]> fileCollection = fileCollectionMap.get(testType);
        for (int f = 0; f < 10; f++) {
            final DataFileReader<long[]> dataFileReader = fileCollection.getDataFile(f);
            final DataFileMetadata metadata = dataFileReader.getMetadata();
            assertEquals(f, metadata.getIndex(), "Data file metadata should know self-index");
            assertTrue(metadata.getCreationDate().isAfter(TEST_START), "Creation dates should go forward in time");
            assertTrue(metadata.getCreationDate().isBefore(Instant.now()), "Creation dates may not be in the future");
            assertEquals(100, metadata.getDataItemCount(), "unexpected DataItemCount");
            assertEquals(Files.size(dataFileReader.getPath()), dataFileReader.getSize(), "unexpected DataFileSize");
        }
    }

    boolean directMemoryUsageByDataFileIteratorWorkaroundApplied = false;

    // When the first DataFileIterator is initialized, it allocates 16Mb direct byte buffer internally.
    // Since we have direct memory usage checks after each test case, it's reported as a memory leak.
    // A workaround is to reset memory usage value right after the first usage of iterator. No need to
    // do it before each test run, it's enough to do just once
    void reinitializeDirectMemoryUsage() {
        if (!directMemoryUsageByDataFileIteratorWorkaroundApplied) {
            initializeDirectMemoryAtStart();
            directMemoryUsageByDataFileIteratorWorkaroundApplied = true;
        }
    }

    @Order(10)
    @ParameterizedTest
    @EnumSource(FilesTestType.class)
    void createDataFileCollectionWithLoadedDataCallback(final FilesTestType testType) throws Exception {
        fileCollectionMap.get(testType).close(); // close the old one so metadata is written to disk
        final LoadedDataCallbackImpl loadedDataCallbackImpl = new LoadedDataCallbackImpl();
        final DataFileCollection<long[]> fileCollection = new DataFileCollection<>(
                config,
                tempFileDir.resolve(testType.name()),
                "test",
                testType.dataItemSerializer,
                loadedDataCallbackImpl);
        fileCollectionMap.put(testType, fileCollection);
        reinitializeDirectMemoryUsage();
        // check that the 10 files were created previously (in the very first unit test) still are
        // readable
        assertEquals(
                10,
                Files.list(tempFileDir.resolve(testType.name()))
                        .filter(f ->
                                f.toString().endsWith(".pbj") || f.toString().endsWith(".jdb"))
                        .filter(f -> !f.toString().contains("metadata"))
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
        checkData(fileCollectionMap.get(testType), storedOffsetsMap.get(testType), testType, 0, 1000, 10_000);
        assertEquals(new KeyRange(0, 1000), fileCollection.getValidKeyRange(), "Should still have the valid range");
        assertTrue(
                fileCollection.isLoadedFromExistingFiles(),
                "Collection loaded from existing store should reflect this fact");

        // also try specifying a testStore (that doesn't exist) in a storeDir that does
        final LoadedDataCallbackImpl loadedDataCallbackImpl2 = new LoadedDataCallbackImpl();
        final DataFileCollection<long[]> fileCollection2 = new DataFileCollection<>(
                config,
                tempFileDir.resolve(testType.name()),
                "test2",
                testType.dataItemSerializer,
                loadedDataCallbackImpl2);
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
        final DataFileCollection.LoadedDataCallback<long[]> testCallback =
                (dataLoc, data) -> numKeysRead.incrementAndGet();
        final DataFileCollection<long[]> fileCollection = fileCollectionMap.get(testType);
        assertEquals(new KeyRange(0, 1000), fileCollection.getValidKeyRange(), "Should still have the valid range");
        fileCollection.close();
        assertDoesNotThrow(
                () -> {
                    final DataFileCollection<long[]> reopenedFileCollection = new DataFileCollection<>(
                            config,
                            tempFileDir.resolve(testType.name()),
                            "test",
                            testType.dataItemSerializer,
                            testCallback);
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
        checkData(fileCollectionMap.get(testType), storedOffsetsMap.get(testType), testType, 0, 1000, 10_000);
    }

    /**
     * Special slow wrapper on ImmutableIndexedObjectListUsingArray that slows down gets, this
     * causing threading bugs during merging where deletion and reading race each other, to be
     * always reproducible.
     */
    private static class SlowImmutableIndexedObjectListUsingArray<T extends IndexedObject>
            extends ImmutableIndexedObjectListUsingArray<T> {
        public SlowImmutableIndexedObjectListUsingArray(final Function<Integer, T[]> ap, final List<T> objects) {
            super(ap, objects);
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

    /** Reopen database using SlowImmutableIndexedObjectListUsingArray ready for merging test */
    @Order(99)
    @ParameterizedTest
    @EnumSource(FilesTestType.class)
    void closeAndReopenInSlowModeForMerging(final FilesTestType testType) throws Exception {
        final AtomicInteger numKeysRead = new AtomicInteger();
        final DataFileCollection.LoadedDataCallback<long[]> testCallback =
                (dataLoc, data) -> numKeysRead.incrementAndGet();
        final DataFileCollection<long[]> fileCollection = fileCollectionMap.get(testType);
        fileCollection.close();
        assertDoesNotThrow(
                () -> {
                    final DataFileCollection<long[]> reopenedFileCollection = new DataFileCollection<>(
                            config,
                            tempFileDir.resolve(testType.name()),
                            "test",
                            null,
                            testType.dataItemSerializer,
                            testCallback,
                            l -> new SlowImmutableIndexedObjectListUsingArray<DataFileReader<long[]>>(
                                    DataFileReader[]::new, l));
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
        final DataFileCompactor<long[]> fileCompactor = createFileCompactor("merge", fileCollection, testType);
        final LongListHeap storedOffsets = storedOffsetsMap.get(testType);
        final AtomicBoolean mergeComplete = new AtomicBoolean(false);
        final int NUM_OF_KEYS = 1000;
        final int NUM_OF_THREADS = 5;
        IntStream.range(0, NUM_OF_THREADS).parallel().forEach(thread -> {
            if (thread == 0) { // checking thread, keep reading and checking data all
                // the time while we are merging
                while (!mergeComplete.get()) {
                    try {
                        for (int i = 0; i < NUM_OF_KEYS; i++) {
                            final long[] dataItem = fileCollection.readDataItemUsingIndex(storedOffsets, i);
                            assertNotNull(dataItem, "DataItem should never be null");
                        }
                    } catch (Exception e) {
                        fail("Exception should not be thrown", e);
                    }
                }
            } else if (thread < (NUM_OF_THREADS - 1)) { // check reading item at index 100 as fast as
                // possible to try
                // and slip though cracks
                System.out.println("START READING");
                while (!mergeComplete.get()) {
                    try {
                        final long[] dataItem = fileCollection.readDataItemUsingIndex(storedOffsets, 100);
                        assertNotNull(dataItem, "DataItem should never be null");
                    } catch (IOException e) {
                        fail("Exception should not be thrown", e);
                    }
                }
            } else if (thread == (NUM_OF_THREADS - 1)) { // move thread
                System.out.println("DataFileCollectionTest.merge");
                List<Path> mergedFiles = null;
                try {
                    List<DataFileReader<long[]>> filesToMerge = fileCollection.getAllCompletedFiles();
                    System.out.println("filesToMerge = " + filesToMerge.size());
                    AtomicInteger numMoves = new AtomicInteger(0);
                    Set<Integer> allKeysExpectedToBeThere =
                            IntStream.range(0, NUM_OF_KEYS).boxed().collect(Collectors.toSet());
                    CASableLongIndex indexUpdater = new CASableLongIndex() {
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

                        public <T extends Throwable> void forEach(final LongAction<T> action)
                                throws InterruptedException, T {
                            storedOffsets.forEach(action);
                        }
                    };

                    mergedFiles = fileCompactor.compactFiles(indexUpdater, filesToMerge, 1);
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
                                    "Expect all data to be" + " correct"));
                    System.out.println("============= CHECK DONE ===========================");
                    final KeyRange validKeyRange = fileCollection.getValidKeyRange();
                    assertEquals(new KeyRange(0, NUM_OF_KEYS), validKeyRange, "Should still have values");
                    mergeComplete.set(true);
                    // all files should have been merged into 1.
                    assertNotNull(mergedFiles, "null merged files list");
                    assertEquals(1, mergedFiles.size(), "unexpected # of post-merge files");
                } catch (Exception e) {
                    fail("Exception should not be thrown", e);
                }
            }
        });
        // check we only have 1 file left
        assertEquals(
                1,
                Files.list(tempFileDir.resolve(testType.name()))
                        .filter(f ->
                                f.toString().endsWith(".pbj") || f.toString().endsWith(".jdb"))
                        .filter(f -> !f.toString().contains("metadata"))
                        .count(),
                "unexpected # of files #1");
        // After merge is complete, there should be only 1 "fully written" file, and that it is
        // empty.
        List<DataFileReader<long[]>> filesLeft = fileCollection.getAllCompletedFiles();
        assertEquals(1, filesLeft.size(), "unexpected # of files #2");

        // and trying to merge just one file is a no-op
        List<Path> secondMergeResults = fileCompactor.compactFiles(null, filesLeft, 1);
        assertNotNull(secondMergeResults, "null merged files list");
        assertEquals(0, secondMergeResults.size(), "unexpected results from second merge");
    }

    @Order(101)
    @ParameterizedTest
    @EnumSource(FilesTestType.class)
    void check1000AfterMerge(final FilesTestType testType) {
        checkData(fileCollectionMap.get(testType), storedOffsetsMap.get(testType), testType, 0, 1000, 10_000);
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
        fileCollection.endWriting(0, 1000).setFileCompleted();
        // check we now have 2 files
        assertEquals(
                2,
                Files.list(tempFileDir.resolve(testType.name()))
                        .filter(f ->
                                f.toString().endsWith(".pbj") || f.toString().endsWith(".jdb"))
                        .filter(f -> !f.toString().contains("metadata"))
                        .count(),
                "unexpected # of files");
    }

    @Order(201)
    @ParameterizedTest
    @EnumSource(FilesTestType.class)
    void check1000BeforeMerge(final FilesTestType testType) {
        checkData(fileCollectionMap.get(testType), storedOffsetsMap.get(testType), testType, 0, 50, 100_000);
        checkData(fileCollectionMap.get(testType), storedOffsetsMap.get(testType), testType, 50, 1000, 10_000);
    }

    @SuppressWarnings("unchecked")
    @Order(202)
    @ParameterizedTest
    @EnumSource(FilesTestType.class)
    void merge2(final FilesTestType testType) throws Exception {
        final DataFileCollection<long[]> fileCollection = fileCollectionMap.get(testType);
        final DataFileCompactor<long[]> fileCompactor = createFileCompactor("merge2", fileCollection, testType);
        final LongListHeap storedOffsets = storedOffsetsMap.get(testType);
        final AtomicBoolean mergeComplete = new AtomicBoolean(false);
        // start compaction paused so that we can test pausing
        fileCompactor.pauseCompaction();

        IntStream.range(0, 3).parallel().forEach(thread -> {
            if (thread == 0) { // checking thread, keep reading and checking data all
                // the time while we are merging
                while (!mergeComplete.get()) {
                    try {
                        checkData(
                                fileCollectionMap.get(testType),
                                storedOffsetsMap.get(testType),
                                testType,
                                0,
                                50,
                                100_000);
                        checkData(
                                fileCollectionMap.get(testType),
                                storedOffsetsMap.get(testType),
                                testType,
                                50,
                                1000,
                                10_000);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            } else if (thread == 1) { // move thread
                // merge 2 files
                try {
                    List<DataFileReader<long[]>> allFiles = fileCollection.getAllCompletedFiles();
                    Set<Integer> allKeysExpectedToBeThere =
                            IntStream.range(0, 1000).boxed().collect(Collectors.toSet());
                    final CASableLongIndex indexUpdater = new CASableLongIndex() {
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
                                    "check each key was in list of expected" + " keys");
                            return storedOffsets.putIfEqual(key, oldValue, newValue);
                        }

                        public <T extends Throwable> void forEach(final LongAction<T> action)
                                throws InterruptedException, T {
                            storedOffsets.forEach(action);
                        }
                    };
                    fileCompactor.compactFiles(indexUpdater, allFiles, 1);
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
                try {
                    // now let merging continue
                    fileCompactor.resumeCompaction();
                } catch (IOException e) {
                    e.printStackTrace();
                    throw new UncheckedIOException(e);
                }
            }
        });
        // check we 7 files left, as we merged 5 out of 11
        assertEquals(
                1,
                Files.list(tempFileDir.resolve(testType.name()))
                        .filter(f ->
                                f.toString().endsWith(".pbj") || f.toString().endsWith(".jdb"))
                        .filter(f -> !f.toString().contains("metadata"))
                        .count(),
                "unexpected # of files");
    }

    private static DataFileCompactor<long[]> createFileCompactor(
            String storeName, DataFileCollection<long[]> fileCollection, FilesTestType testType) {
        return new DataFileCompactor<>(
                config, storeName, fileCollection, storedOffsetsMap.get(testType), null, null, null, null) {
            @Override
            int getMinNumberOfFilesToCompact() {
                return 2;
            }
        };
    }

    @Order(203)
    @ParameterizedTest
    @EnumSource(FilesTestType.class)
    void check1000AfterMerge2(final FilesTestType testType) {
        checkData(fileCollectionMap.get(testType), storedOffsetsMap.get(testType), testType, 0, 50, 100_000);
        checkData(fileCollectionMap.get(testType), storedOffsetsMap.get(testType), testType, 50, 1000, 10_000);
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
                new DataFileCollection<>(config, dbDir, storeName, testType.dataItemSerializer, null);
        assertSame(0, fileCollection.getAllCompletedFiles().size(), "Should be no files");
        fileCollectionMap.put(testType, fileCollection);
        // create stored offsets list
        final LongListHeap storedOffsets = new LongListHeap(5000);
        storedOffsetsMap.put(testType, storedOffsets);
        // create 10x 100 item files
        populateDataFileCollection(testType, fileCollection, storedOffsets);
        // check 10 files were created and data is correct
        assertEquals(
                10,
                Files.list(dbDir)
                        .filter(file -> file.getFileName().toString().startsWith(storeName))
                        .count(),
                "expected 10 db files");
        assertSame(10, fileCollection.getAllCompletedFiles().size(), "Should be 10 files");
        checkData(fileCollectionMap.get(testType), storedOffsetsMap.get(testType), testType, 0, 1000, 10_000);
        // check all files are available for merge
        assertSame(10, fileCollection.getAllCompletedFiles().size(), "Should be 10 files available for merging");
        // close
        fileCollection.close();
        // reopen
        final DataFileCollection<long[]> fileCollection2 =
                new DataFileCollection<>(config, dbDir, storeName, testType.dataItemSerializer, null);
        final DataFileCompactor<long[]> fileCompactor = new DataFileCompactor<>(
                config, storeName, fileCollection2, storedOffsetsMap.get(testType), null, null, null, null);
        fileCollectionMap.put(testType, fileCollection2);
        // check 10 files were opened and data is correct
        assertSame(10, fileCollection2.getAllCompletedFiles().size(), "Should be 10 files");
        checkData(fileCollectionMap.get(testType), storedOffsetsMap.get(testType), testType, 0, 1000, 10_000);
        // check all files are available for merge
        assertSame(10, fileCollection2.getAllCompletedFiles().size(), "Should be 10 files available for merging");
        // merge
        fileCompactor.compactFiles(storedOffsets, fileCollection2.getAllCompletedFiles(), 1);
        // check 1 files were opened and data is correct
        assertSame(1, fileCollection2.getAllCompletedFiles().size(), "Should be 1 files");
        assertEquals(
                1,
                Files.list(dbDir)
                        .filter(file -> file.getFileName().toString().matches(storeName + ".*pbj")
                                || file.getFileName().toString().matches(storeName + ".*jdb"))
                        .filter(f -> !f.toString().contains("metadata"))
                        .count(),
                "expected 1 db files but had ["
                        + Arrays.toString(Files.list(dbDir).toArray())
                        + "]");
        checkData(fileCollectionMap.get(testType), storedOffsetsMap.get(testType), testType, 0, 1000, 10_000);
        // close db
        fileCollection2.close();
    }

    private static void populateDataFileCollection(
            FilesTestType testType, DataFileCollection<long[]> fileCollection, LongListHeap storedOffsets)
            throws IOException {
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
            fileCollection.endWriting(0, count + 100).setFileCompleted();
            count += 100;
        }
    }

    /**
     * This test emulates scenario in which compaction is interrupted by thread interruption. This event shouldn't be
     * reported as an error in the logs.
     */
    @Test
    public void testClosedByInterruptException() throws IOException {
        // mock appender to capture the log statements
        final MockAppender mockAppender = new MockAppender("testClosedByInterruptException");
        Logger logger = (Logger) LogManager.getLogger(DataFileCompactor.class);
        mockAppender.start();
        logger.addAppender(mockAppender);
        final Path dbDir = tempFileDir.resolve("testClosedByInterruptException");
        final String storeName = "testClosedByInterruptException";

        // init file collection with some content to compact
        final DataFileCollection<long[]> fileCollection =
                new DataFileCollection<>(config, dbDir, storeName, FilesTestType.fixed.dataItemSerializer, null);
        final LongListHeap storedOffsets = new LongListHeap(5000);
        final DataFileCompactor<long[]> compactor =
                new DataFileCompactor<>(config, storeName, fileCollection, storedOffsets, null, null, null, null);
        populateDataFileCollection(FilesTestType.fixed, fileCollection, storedOffsets);

        // a flag to make sure that `compactFiles` th
        AtomicBoolean closedByInterruptFromCompaction = new AtomicBoolean(false);

        final Thread thread = new Thread(() -> {
            List<DataFileReader<long[]>> allCompletedFiles = fileCollection.getAllCompletedFiles();
            DataFileReader<long[]> spy = Mockito.spy(allCompletedFiles.get(0));
            try {
                AtomicInteger count = new AtomicInteger(0);
                when(spy.getFileType()).thenAnswer(invocation -> {
                    // on the first call to getFileType(), we interrupt the thread
                    Thread.currentThread().interrupt();
                    return invocation.callRealMethod();
                });

                List<DataFileReader<long[]>> allCompletedFilesUpdated = new ArrayList<>(allCompletedFiles);
                allCompletedFilesUpdated.set(0, spy);
                compactor.compactFiles(storedOffsets, allCompletedFilesUpdated, 1);
            } catch (InterruptedException e) {
                // we expect interrupted exception here
                closedByInterruptFromCompaction.set(true);
            } catch (IOException e) {
                fail("Exception should not be thrown");
            }
            reset(spy);
        });
        thread.start();
        try {
            assertEventuallyTrue(
                    () -> {
                        if (!closedByInterruptFromCompaction.get()) {
                            return false;
                        }
                        if (mockAppender.size() == 0) {
                            return false;
                        }
                        final String logMsg = mockAppender.get(0);
                        assertTrue(logMsg.startsWith("MERKLE_DB - INFO - Failed to copy data item 0"));
                        assertTrue(logMsg.endsWith("due to thread interruption"));
                        return true;
                    },
                    Duration.ofMillis(5000),
                    "Compaction should not throw exception when interrupted");
        } finally {
            mockAppender.stop();
        }
    }

    /**
     * Keep track of initial direct memory used already, so we can check if we leek over and above
     * what we started with
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
                        + (directMemoryUsedAtStart * UnitConstants.BYTES_TO_MEBIBYTES)
                        + "MB and is now "
                        + (getDirectMemoryUsedBytes() * UnitConstants.BYTES_TO_MEBIBYTES)
                        + "MB");
    }

    private static class LoadedDataCallbackImpl implements DataFileCollection.LoadedDataCallback<long[]> {
        public final Map<Long, Long> dataLocationMap = new HashMap<>();
        public final Map<Long, long[]> dataValueMap = new HashMap<>();

        @Override
        public void newIndexEntry(final long dataLocation, final long[] dataValue) {
            final long key = dataValue[0];
            dataLocationMap.put(key, dataLocation);
            dataValueMap.put(key, dataValue);
        }
    }
}
