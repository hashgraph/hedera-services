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

package com.swirlds.merkledb.files.hashmap;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.swirlds.common.config.singleton.ConfigurationHolder;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.merkledb.config.MerkleDbConfig;
import com.swirlds.merkledb.files.DataFileCompactor;
import com.swirlds.merkledb.files.FilesTestType;
import com.swirlds.merkledb.serialize.KeySerializer;
import com.swirlds.merkledb.test.fixtures.ExampleLongKeyFixedSize;
import com.swirlds.virtualmap.VirtualLongKey;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Random;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

@SuppressWarnings({"SameParameterValue", "unchecked"})
class HalfDiskHashMapTest {

    /** Temporary directory provided by JUnit */
    @SuppressWarnings("unused")
    @TempDir
    Path tempDirPath;

    private MerkleDbConfig dbConfig = ConfigurationHolder.getConfigData(MerkleDbConfig.class);

    // =================================================================================================================
    // Helper Methods
    private HalfDiskHashMap<VirtualLongKey> createNewTempMap(FilesTestType testType, int count) throws IOException {
        // create map
        HalfDiskHashMap<VirtualLongKey> map = new HalfDiskHashMap<>(
                dbConfig,
                count,
                (KeySerializer<VirtualLongKey>) testType.keySerializer,
                tempDirPath.resolve(testType.name()),
                "HalfDiskHashMapTest",
                null,
                false);
        map.printStats();
        return map;
    }

    private static void createSomeData(
            FilesTestType testType, HalfDiskHashMap<VirtualLongKey> map, int start, int count, long dataMultiplier)
            throws IOException {
        map.startWriting();
        for (int i = start; i < (start + count); i++) {
            map.put(testType.createVirtualLongKey(i), i * dataMultiplier);
        }
        //        map.debugDumpTransactionCache();
        long START = System.currentTimeMillis();
        map.endWriting();
        printTestUpdate(START, count, "Written");
    }

    private static void checkData(
            FilesTestType testType, HalfDiskHashMap<VirtualLongKey> map, int start, int count, long dataMultiplier)
            throws IOException {
        long START = System.currentTimeMillis();
        for (int i = start; i < (start + count); i++) {
            final var key = testType.createVirtualLongKey(i);
            long result = map.get(key, -1);
            assertEquals(
                    i * dataMultiplier,
                    result,
                    "Failed to read key=" + testType.createVirtualLongKey(i) + " dataMultiplier=" + dataMultiplier);
        }
        printTestUpdate(START, count, "Read");
    }

    // =================================================================================================================
    // Tests

    @ParameterizedTest
    @EnumSource(FilesTestType.class)
    void createDataAndCheck(FilesTestType testType) throws Exception {
        final Path tempSnapshotDir = tempDirPath.resolve("DataFileTestSnapshot_" + testType.name());
        final int count = 10_000;
        // create map
        final HalfDiskHashMap<VirtualLongKey> map = createNewTempMap(testType, count);
        // create some data
        createSomeData(testType, map, 1, count, 1);
        // sequentially check data
        checkData(testType, map, 1, count, 1);
        // randomly check data
        Random random = new Random(1234);
        for (int j = 1; j < (count * 2); j++) {
            int i = 1 + random.nextInt(count);
            long result = map.get(testType.createVirtualLongKey(i), 0);
            assertEquals(i, result, "unexpected value of newVirtualLongKey");
        }
        // create snapshot
        map.snapshot(tempSnapshotDir);
        // open snapshot and check data
        HalfDiskHashMap<VirtualLongKey> mapFromSnapshot = new HalfDiskHashMap<>(
                ConfigurationHolder.getConfigData(MerkleDbConfig.class),
                count,
                (KeySerializer<VirtualLongKey>) testType.keySerializer,
                tempSnapshotDir,
                "HalfDiskHashMapTest",
                null,
                false);
        mapFromSnapshot.printStats();
        checkData(testType, mapFromSnapshot, 1, count, 1);
        // check deletion
        map.startWriting();
        map.delete(testType.createVirtualLongKey(5));
        map.delete(testType.createVirtualLongKey(50));
        map.delete(testType.createVirtualLongKey(500));
        map.endWriting();
        assertEquals(-1, map.get(testType.createVirtualLongKey(5), -1), "Expect not to exist");
        assertEquals(-1, map.get(testType.createVirtualLongKey(50), -1), "Expect not to exist");
        assertEquals(-1, map.get(testType.createVirtualLongKey(500), -1), "Expect not to exist");
        checkData(testType, map, 1, 4, 1);
        checkData(testType, map, 6, 43, 1);
        checkData(testType, map, 51, 448, 1);
        checkData(testType, map, 501, 9499, 1);
        // check close and try read after
        map.close();
        assertEquals(
                -1, map.get(testType.createVirtualLongKey(5), -1), "Expect not found result as just closed the map!");
    }

    @ParameterizedTest
    @EnumSource(FilesTestType.class)
    void multipleWriteBatchesAndMerge(FilesTestType testType) throws Exception {
        // create map
        final HalfDiskHashMap<VirtualLongKey> map = createNewTempMap(testType, 10_000);
        final DataFileCompactor dataFileCompactor = new DataFileCompactor(
                dbConfig,
                "HalfDiskHashMapTest",
                map.getFileCollection(),
                map.getBucketIndexToBucketLocation(),
                null,
                null,
                null,
                null);
        // create some data
        createSomeData(testType, map, 1, 1111, 1);
        checkData(testType, map, 1, 1111, 1);
        // create some more data
        createSomeData(testType, map, 1111, 3333, 1);
        checkData(testType, map, 1, 3333, 1);
        // create some more data
        createSomeData(testType, map, 1111, 10_000, 1);
        checkData(testType, map, 1, 10_000, 1);
        // do a merge
        dataFileCompactor.compact();
        // check all data after
        checkData(testType, map, 1, 10_000, 1);
    }

    @ParameterizedTest
    @EnumSource(FilesTestType.class)
    void updateData(FilesTestType testType) throws Exception {
        // create map
        final HalfDiskHashMap<VirtualLongKey> map = createNewTempMap(testType, 1000);
        // create some data
        createSomeData(testType, map, 0, 1000, 1);
        checkData(testType, map, 0, 1000, 1);
        // update some data
        createSomeData(testType, map, 200, 400, 2);
        checkData(testType, map, 0, 200, 1);
        checkData(testType, map, 200, 400, 2);
        checkData(testType, map, 600, 400, 1);
    }

    @Test
    void testOverwritesWithCollision() throws IOException {
        final FilesTestType testType = FilesTestType.fixed;
        try (final HalfDiskHashMap<VirtualLongKey> map = createNewTempMap(testType, 1000)) {
            map.startWriting();
            for (int i = 100; i < 300; i++) {
                map.put(new CollidableFixedLongKey(i), i);
            }
            assertDoesNotThrow(map::endWriting);
        }
    }

    private static void printTestUpdate(long start, long count, String msg) {
        long took = System.currentTimeMillis() - start;
        double timeSeconds = (double) took / 1000d;
        double perSecond = (double) count / timeSeconds;
        System.out.printf("%s : [%,d] at %,.0f per/sec, took %,.2f seconds\n", msg, count, perSecond, timeSeconds);
    }

    public static class CollidableFixedLongKey extends ExampleLongKeyFixedSize {
        private static long CLASS_ID = 0x7b305246cffbf8efL;

        public CollidableFixedLongKey() {
            super();
        }

        public CollidableFixedLongKey(final long value) {
            super(value);
        }

        @Override
        public int hashCode() {
            return (int) getValue() % 100;
        }

        @Override
        public long getClassId() {
            return CLASS_ID;
        }

        @Override
        public void deserialize(final SerializableDataInputStream in, final int dataVersion) throws IOException {
            assertEquals(getVersion(), dataVersion);
            super.deserialize(in, dataVersion);
        }
    }
}
