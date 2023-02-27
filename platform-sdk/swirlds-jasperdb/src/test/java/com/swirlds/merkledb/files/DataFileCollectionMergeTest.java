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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.swirlds.merkledb.collections.CASable;
import com.swirlds.merkledb.collections.LongList;
import com.swirlds.merkledb.collections.LongListOffHeap;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLongArray;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@SuppressWarnings("unused")
class DataFileCollectionMergeTest {

    // Would be nice to add a test to make sure files get deleted

    /**
     * Temporary directory provided by JUnit
     */
    @TempDir
    Path tempFileDir;

    private static final long APPLE = 1001;
    private static final long BANANA = 1002;
    private static final long CHERRY = 1003;
    private static final long DATE = 1004;
    private static final long EGGPLANT = 1005;
    private static final long FIG = 1006;
    private static final long GRAPE = 1007;
    private static final long AARDVARK = 2001;
    private static final long CUTTLEFISH = 2003;
    private static final long FOX = 1006;

    @Test
    void testMerge() throws Exception {
        final Map<Long, Long> index = new HashMap<>();
        final var serializer = new ExampleFixedSizeDataSerializer();
        final var coll = new DataFileCollection<>(tempFileDir.resolve("mergeTest"), "mergeTest", serializer, null);

        coll.startWriting();
        index.put(1L, coll.storeDataItem(new long[] {1, APPLE}));
        index.put(2L, coll.storeDataItem(new long[] {2, BANANA}));
        coll.endWriting(1, 2).setFileAvailableForMerging(true);

        coll.startWriting();
        index.put(3L, coll.storeDataItem(new long[] {3, APPLE}));
        index.put(4L, coll.storeDataItem(new long[] {4, CHERRY}));
        coll.endWriting(2, 4).setFileAvailableForMerging(true);

        coll.startWriting();
        index.put(4L, coll.storeDataItem(new long[] {4, CUTTLEFISH}));
        index.put(5L, coll.storeDataItem(new long[] {5, BANANA}));
        index.put(6L, coll.storeDataItem(new long[] {6, DATE}));
        coll.endWriting(3, 6).setFileAvailableForMerging(true);

        coll.startWriting();
        index.put(7L, coll.storeDataItem(new long[] {7, APPLE}));
        index.put(8L, coll.storeDataItem(new long[] {8, EGGPLANT}));
        index.put(9L, coll.storeDataItem(new long[] {9, CUTTLEFISH}));
        index.put(10L, coll.storeDataItem(new long[] {10, FIG}));
        coll.endWriting(5, 10).setFileAvailableForMerging(true);

        final Semaphore pauseMerging = new Semaphore(1);
        final CASable indexUpdater = new CASable() {
            public long get(long key) {
                return index.get(key);
            }

            public boolean putIfEqual(long key, long oldValue, long newValue) {
                assertTrue(key >= 5, "We should not update below firstLeafPath");

                if (index.containsKey(key) && index.get(key).equals(oldValue)) {
                    index.put(key, newValue);
                    return true;
                }
                return false;
            }
        };
        coll.mergeFiles(indexUpdater, coll.getAllFilesAvailableForMerge(), pauseMerging);

        long prevKey = -1;
        for (int i = 5; i < 10; i++) {
            Long location = index.get((long) i);
            assertNotNull(location, "failed on " + i);

            long[] data = coll.readDataItem(location);
            final var key = data[0];
            final var value = data[1];
            assertTrue(key > prevKey, "failed on " + i + " key=" + key + ", prev=" + prevKey + ", value=" + value);
            prevKey = key;
        }

        assertEquals(BANANA, coll.readDataItem(index.get(5L))[1], "Not a BANANA");
        assertEquals(DATE, coll.readDataItem(index.get(6L))[1], "Not a DATE");
        assertEquals(APPLE, coll.readDataItem(index.get(7L))[1], "Not a APPLE");
        assertEquals(EGGPLANT, coll.readDataItem(index.get(8L))[1], "Not a EGGPLANT");
        assertEquals(CUTTLEFISH, coll.readDataItem(index.get(9L))[1], "Not a CUTTLEFISH");
        assertEquals(FIG, coll.readDataItem(index.get(10L))[1], "Not a FIG");

        assertEquals(1, coll.getAllFullyWrittenFiles().size(), "Too many files left over");

        final var dataFileReader = coll.getAllFullyWrittenFiles().get(0);
        final var itr = dataFileReader.createIterator();
        prevKey = -1;
        while (itr.next()) {
            final long key = itr.getDataItemsKey();
            assertTrue(key > prevKey, "Keys must be sorted in ascending order");
            assertTrue(key >= 5, "We should not update below firstLeafPath");
            prevKey = key;
        }
    }

    @Test
    @DisplayName("Re-merge files without deletion")
    void testDoubleMerge() throws Exception {
        final int MAXKEYS = 100;
        final long[] index = new long[MAXKEYS];
        final Path testDir = tempFileDir.resolve("testDoubleMerge");
        final DataFileCollection<long[]> store =
                new DataFileCollection<>(testDir, "testDoubleMerge", new ExampleFixedSizeDataSerializer(), null);

        final int numFiles = 2;
        for (long i = 0; i < numFiles; i++) {
            store.startWriting();
            for (int j = 0; j < MAXKEYS; ++j) {
                index[j] = store.storeDataItem(new long[] {j, i * j});
            }
            store.endWriting(0, index.length).setFileAvailableForMerging(true);
        }

        final CountDownLatch latch = new CountDownLatch(1);
        final Semaphore pauseMerging = new Semaphore(1);

        // Do merge in a separate thread but pause before files are deleted
        new Thread(() -> {
                    final AtomicInteger updateCount = new AtomicInteger(0);
                    final List<DataFileReader<long[]>> filesToMerge = store.getAllFilesAvailableForMerge();
                    final CASable indexUpdater = new CASable() {
                        public long get(long key) {
                            return index[(int) key];
                        }

                        public boolean putIfEqual(long key, long oldValue, long newValue) {
                            assertEquals(index[(int) key], oldValue, "Index value does not match");
                            index[(int) key] = newValue;
                            if (updateCount.incrementAndGet() == MAXKEYS) {
                                latch.countDown();
                                pauseMerging.acquireUninterruptibly();
                            }
                            return true;
                        }
                    };

                    try {
                        store.mergeFiles(indexUpdater, filesToMerge, pauseMerging);
                        store.close();
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                })
                .start();

        latch.await();

        // Create a snapshot that includes files being merged and their resulting file
        final Path snapshot = testDir.resolve("snapshot");
        store.snapshot(snapshot);
        pauseMerging.release();

        // Create a new data collection from the snapshot
        final String[] index2 = new String[MAXKEYS];
        final DataFileCollection<long[]> store2 = new DataFileCollection<>(
                snapshot,
                "testDoubleMerge",
                new ExampleFixedSizeDataSerializer(),
                (key, dataLocation, dataValue) ->
                        index2[(int) key] = DataFileCommon.dataLocationToString(dataLocation));

        // Merge all files with redundant records
        final List<DataFileReader<long[]>> filesToMerge = store2.getAllFilesAvailableForMerge();
        try {
            final CASable indexUpdater = new CASable() {
                public long get(long key) {
                    return index[(int) key];
                }

                public boolean putIfEqual(long key, long oldValue, long newValue) {
                    final String oldDataLocation = DataFileCommon.dataLocationToString(oldValue);
                    assertEquals(index2[(int) key], oldDataLocation, "Index value does not match");
                    index2[(int) key] = DataFileCommon.dataLocationToString(newValue);
                    return true;
                }
            };

            store2.mergeFiles(indexUpdater, filesToMerge, new Semaphore(1));
        } finally {
            store2.close();
        }
    }

    @Test
    @DisplayName("Merge files concurrently with writing new files")
    void testMergeAndFlush() throws Exception {
        final int MAXKEYS = 100;
        final int NUM_UPDATES = 5;
        final AtomicLongArray index = new AtomicLongArray(MAXKEYS);
        final Path testDir = tempFileDir.resolve("testMergeAndFlush");

        final DataFileCollection<long[]> store =
                new DataFileCollection<>(testDir, "testMergeAndFlush", new ExampleFixedSizeDataSerializer(), null);
        try {
            for (long i = 0; i < 2 * NUM_UPDATES; i++) {
                // Start writing a new copy
                store.startWriting();
                for (int j = 0; j < MAXKEYS; ++j) {
                    // Update half the keys
                    if ((j + i) % 2 == 0) continue;
                    long[] dataItem = store.readDataItem(index.get(j));
                    if (dataItem == null) {
                        dataItem = new long[] {j, 0};
                    }
                    dataItem[1] += j;
                    index.set(j, store.storeDataItem(dataItem));
                }

                // Intervene with merging earlier copies to disrupt file index order
                final List<DataFileReader<long[]>> filesToMerge = store.getAllFilesAvailableForMerge();
                final CASable indexUpdater = new CASable() {
                    public long get(long key) {
                        return index.get((int) key);
                    }

                    public boolean putIfEqual(long key, long oldValue, long newValue) {
                        return index.compareAndSet((int) key, oldValue, newValue);
                    }
                };

                if (filesToMerge.size() > 1) {
                    try {
                        store.mergeFiles(indexUpdater, filesToMerge, new Semaphore(1));
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                }

                // Finish writing the current copy, which has newer data but an older index than
                // the merged file
                store.endWriting(0, index.length()).setFileAvailableForMerging(true);
            }

            // Validate the result
            for (int j = 0; j < MAXKEYS; ++j) {
                final long[] dataItem = store.readDataItem(index.get(j));
                assertEquals(j, dataItem[0]);
                assertEquals(NUM_UPDATES * j, dataItem[1]);
            }
        } finally {
            store.close();
        }
    }

    @Test
    @DisplayName("Restore from disrupted index order")
    void testRestore() throws Exception {
        final int MAX_KEYS = 100;
        final int NUM_UPDATES = 3;
        final AtomicLongArray index = new AtomicLongArray(MAX_KEYS);
        final Path testDir = tempFileDir.resolve("testRestore");

        final DataFileCollection<long[]> store =
                new DataFileCollection<>(testDir, "testRestore", new ExampleFixedSizeDataSerializer(), null);
        try {
            // Initial values
            store.startWriting();
            for (int j = 0; j < MAX_KEYS; ++j) {
                index.set(j, store.storeDataItem(new long[] {j, j}));
            }
            store.endWriting(0, index.length()).setFileAvailableForMerging(true);

            // Write new copies
            for (long i = 1; i < NUM_UPDATES; i++) {
                store.startWriting();
                for (int j = 0; j < MAX_KEYS; ++j) {
                    long[] dataItem = store.readDataItem(index.get(j));
                    dataItem[1] += j;
                    index.set(j, store.storeDataItem(dataItem));
                }

                // Intervene with merging earlier copies to disrupt file index order
                final List<DataFileReader<long[]>> filesToMerge = store.getAllFilesAvailableForMerge();
                final CASable indexUpdater = new CASable() {
                    public long get(long key) {
                        return index.get((int) key);
                    }

                    public boolean putIfEqual(long key, long oldValue, long newValue) {
                        return index.compareAndSet((int) key, oldValue, newValue);
                    }
                };

                if (filesToMerge.size() > 1) {
                    try {
                        store.mergeFiles(indexUpdater, filesToMerge, new Semaphore(1));
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                }

                // Finish writing the current copy, which has newer data but an older index than
                // the merged file
                store.endWriting(0, index.length()).setFileAvailableForMerging(true);
            }

            // Restore from all files
            final AtomicLongArray reindex = new AtomicLongArray(MAX_KEYS);
            final DataFileCollection<long[]> restore = new DataFileCollection<>(
                    testDir,
                    "testRestore",
                    new ExampleFixedSizeDataSerializer(),
                    (key, dataLocation, dataValue) -> reindex.set((int) key, dataLocation));

            // Validate the result
            try {
                for (int j = 0; j < MAX_KEYS; ++j) {
                    final long[] dataItem = restore.readDataItem(reindex.get(j));
                    assertEquals(j, dataItem[0]);
                    assertEquals(NUM_UPDATES * j, dataItem[1]);
                }
            } finally {
                restore.close();
            }
        } finally {
            store.close();
        }
    }

    @ParameterizedTest
    @DisplayName("Snapshot + update in parallel with compaction")
    @ValueSource(ints = {0, 1, 2, 3})
    void testMergeUpdateSnapshotRestore(final int testParam) throws Throwable {
        final int numFiles = 10;
        final int numValues = 1000;
        final Path testDir = tempFileDir.resolve("testMergeSnapshotRestore");
        Files.createDirectories(testDir);
        final LongListOffHeap index = new LongListOffHeap();
        final DataFileCollection<long[]> store = new DataFileCollection<>(
                testDir, "testMergeSnapshotRestore", new ExampleFixedSizeDataSerializer(), null);
        // Create a few files initially
        for (int i = 0; i < numFiles; i++) {
            store.startWriting();
            for (int j = 0; j < numValues; j++) {
                final long dataLocation = store.storeDataItem(new long[] {i * numValues + j, i * numValues + j});
                index.put(i * numValues + j, dataLocation);
            }
            store.endWriting(0, index.size()).setFileAvailableForMerging(true);
        }
        // Start compaction
        final Semaphore mergingPaused = new Semaphore(1);
        // Test scenario 0: start merging with mergingPaused semaphore locked, so merging
        // won't proceed more than to the first index update
        if (testParam == 0) {
            mergingPaused.acquire();
        }
        final int filesCountBeforeMerge = store.getAllFilesAvailableForMerge().size();
        assertEquals(numFiles, filesCountBeforeMerge);
        final CountDownLatch mergeCompleteLatch = new CountDownLatch(1);
        final AtomicReference<Throwable> mergeThreadExceptions = new AtomicReference<>();
        new Thread(() -> {
                    try {
                        final List<DataFileReader<long[]>> filesToMerge = store.getAllFilesAvailableForMerge();
                        assertEquals(numFiles, filesToMerge.size());
                        store.mergeFiles(index, filesToMerge, mergingPaused);
                        // 2 = merged file + new file with updated values below, assuming there is
                        // a single merged file (there may be many, if data is large, but this shouldn't
                        // be the case in this test
                        assertEquals(2, store.getAllFilesAvailableForMerge().size());
                    } catch (final Throwable t) {
                        mergeThreadExceptions.set(t);
                    } finally {
                        mergeCompleteLatch.countDown();
                    }
                })
                .start();
        // Test scenario 1: let compaction and update run in parallel for a while
        if (testParam == 1) {
            mergingPaused.acquire();
        }
        // Update values as if it was a flush before a snapshot. It will create a new file in the store
        // in parallel to writing to another file during compaction
        store.startWriting();
        for (int i = 0; i < numFiles; i++) {
            for (int j = 0; j < numValues; j++) {
                final long dataLocation = store.storeDataItem(new long[] {i * numValues + j, i * numValues + j + 1});
                index.put(i * numValues + j, dataLocation);
            }
        }
        store.endWriting(0, index.size()).setFileAvailableForMerging(true);
        // Test scenario 2: lock the semaphore just before taking a snapshot. Compaction may still be
        // in progress or may be completed
        if (testParam == 2) {
            mergingPaused.acquire();
        }
        // Test scenario 3: wait for compaction to complete, then take a snapshot
        if (testParam == 3) {
            mergeCompleteLatch.await();
            mergingPaused.acquire();
        }
        // Snapshot. It's in the middle of compaction, as compaction should be stopped at this point waiting
        // to acquire mergingPaused semaphore
        final Path snapshotDir = tempFileDir.resolve("testMergeSnapshotRestore-snapshot");
        Files.createDirectories(snapshotDir);
        index.writeToFile(snapshotDir.resolve("index.ll"));
        store.snapshot(snapshotDir);
        // Release the semaphore to unpause merging and wait for it to complete
        mergingPaused.release();
        if (testParam != 3) {
            mergeCompleteLatch.await();
        }
        // Close the store
        store.close();

        // Merge is complete now. Check if there were any exceptions during merging files
        if (mergeThreadExceptions.get() != null) {
            throw mergeThreadExceptions.get();
        }

        // Restore
        final LongListOffHeap index2 = new LongListOffHeap(snapshotDir.resolve("index.ll"));
        final DataFileCollection<long[]> store2 = new DataFileCollection<>(
                snapshotDir, "testMergeSnapshotRestore", new ExampleFixedSizeDataSerializer(), null);
        // Check index size
        assertEquals(numFiles * numValues, index2.size());
        // Check the values
        for (int i = 0; i < index2.size(); i++) {
            final long dataLocation = index2.get(i);
            final long[] value = store2.readDataItem(dataLocation);
            assertEquals(i, value[0]);
            assertEquals(i + 1, value[1]);
        }
        store2.close();
    }

    @Test
    @DisplayName("Restore with inconsistent index")
    void testInconsistentIndex() throws Exception {
        final int MAXKEYS = 100;
        final LongList index = new LongListOffHeap();
        final Path testDir = tempFileDir.resolve("testInconsistentIndex");
        final DataFileCollection<long[]> store =
                new DataFileCollection<>(testDir, "testInconsistentIndex", new ExampleFixedSizeDataSerializer(), null);

        final int numFiles = 2;
        for (long i = 0; i < numFiles; i++) {
            store.startWriting();
            for (int j = 0; j < MAXKEYS; ++j) {
                index.put(j, store.storeDataItem(new long[] {j, i * j}));
            }
            store.endWriting(0, index.size()).setFileAvailableForMerging(true);
        }

        final Path snapshot = testDir.resolve("snapshot");
        final Path savedIndex = testDir.resolve("index.ll");

        final AtomicInteger updateCount = new AtomicInteger(0);
        final List<DataFileReader<long[]>> filesToMerge = store.getAllFilesAvailableForMerge();
        final CASable indexUpdater = new CASable() {
            public long get(long key) {
                return index.get(key);
            }

            public boolean putIfEqual(long key, long oldValue, long newValue) {
                assertTrue(
                        index.putIfEqual(key, oldValue, newValue),
                        String.format(
                                "Index values for key %d do not match: expected 0x%x actual 0x%x",
                                key, oldValue, index.get(key)));
                if (updateCount.incrementAndGet() == MAXKEYS / 2) {
                    // Start a snapshot while the index is being updated
                    try {
                        index.writeToFile(savedIndex);
                        store.snapshot(snapshot);
                    } catch (IOException ex) {
                        ex.printStackTrace();
                    }
                }
                return true;
            }
        };

        try {
            store.mergeFiles(indexUpdater, filesToMerge, new Semaphore(1));
            store.close();
        } catch (Exception ex) {
            ex.printStackTrace();
        }

        // Create a new data collection from the snapshot
        LongList index2 = new LongListOffHeap(savedIndex);
        final DataFileCollection<long[]> store2 =
                new DataFileCollection<>(snapshot, "testInconsistentIndex", new ExampleFixedSizeDataSerializer(), null);

        // Merge all files with redundant records
        final List<DataFileReader<long[]>> filesToMerge2 = store2.getAllFilesAvailableForMerge();
        final CASable indexUpdater2 = new CASable() {
            public long get(long key) {
                return index2.get(key);
            }

            public boolean putIfEqual(long key, long oldValue, long newValue) {
                assertTrue(
                        index2.putIfEqual(key, oldValue, newValue),
                        String.format(
                                "Index values for key %d do not match: expected 0x%x actual 0x%x",
                                key, oldValue, index2.get(key)));
                return true;
            }
        };

        try {
            store2.mergeFiles(indexUpdater2, filesToMerge2, new Semaphore(1));
        } finally {
            store2.close();
        }
    }
}
