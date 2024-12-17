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

package com.swirlds.merkledb;

import static com.swirlds.common.test.fixtures.AssertionUtils.assertEventuallyEquals;
import static com.swirlds.common.test.fixtures.AssertionUtils.assertEventuallyFalse;
import static com.swirlds.merkledb.collections.LongListOffHeap.DEFAULT_RESERVED_BUFFER_LENGTH;
import static com.swirlds.merkledb.test.fixtures.MerkleDbTestUtils.*;
import static com.swirlds.merkledb.test.fixtures.TestType.fixed_fixed;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.swirlds.base.units.UnitConstants;
import com.swirlds.common.constructable.ConstructableRegistry;
import com.swirlds.common.crypto.DigestType;
import com.swirlds.common.io.utility.LegacyTemporaryFileBuilder;
import com.swirlds.common.test.fixtures.junit.tags.TestComponentTags;
import com.swirlds.merkledb.test.fixtures.TestType;
import com.swirlds.metrics.api.Metric;
import com.swirlds.metrics.api.Metrics;
import com.swirlds.virtualmap.datasource.VirtualHashRecord;
import com.swirlds.virtualmap.serialize.KeySerializer;
import com.swirlds.virtualmap.serialize.ValueSerializer;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

class MerkleDbDataSourceMetricsTest {

    public static final String TABLE_NAME = "test";
    // default number of longs per chunk
    private static final int COUNT = 1_048_576;
    private static final int HASHES_RAM_THRESHOLD = COUNT / 2;
    private static Path testDirectory;
    private MerkleDbDataSource dataSource;
    private Metrics metrics;

    @BeforeAll
    static void setup() throws Exception {
        testDirectory = LegacyTemporaryFileBuilder.buildTemporaryFile("MerkleDbDataSourceMetricsTest", CONFIGURATION);
        ConstructableRegistry.getInstance().registerConstructables("com.swirlds.merkledb");
    }

    @BeforeEach
    public void beforeEach() throws IOException {
        // check db count
        assertEventuallyEquals(
                0L, MerkleDbDataSource::getCountOfOpenDatabases, Duration.ofSeconds(1), "Expected no open dbs");
        // create db
        dataSource = createDataSource(testDirectory, TABLE_NAME, fixed_fixed, COUNT, HASHES_RAM_THRESHOLD);

        metrics = createMetrics();
        dataSource.registerMetrics(metrics);

        // check db count
        assertEventuallyEquals(
                1L, MerkleDbDataSource::getCountOfOpenDatabases, Duration.ofSeconds(1), "Expected only 1 db");
    }

    @Tag(TestComponentTags.VMAP)
    @Test
    void createInternalNodeHashesAndCheckMemoryConsumption() throws IOException {
        // no memory consumption in an empty DB
        assertNoMemoryForInternalList();
        assertNoMemoryForLeafAndKeyToPathLists();

        // create some internal nodes
        dataSource.saveRecords(
                COUNT,
                COUNT * 2,
                IntStream.range(0, COUNT).mapToObj(MerkleDbDataSourceMetricsTest::createVirtualInternalRecord),
                Stream.empty(),
                Stream.empty());

        // one 8 MB memory chunk
        assertMetricValue("ds_offheap_hashesIndexMb_" + TABLE_NAME, 8);
        assertNoMemoryForLeafAndKeyToPathLists();

        // create more internal nodes
        dataSource.saveRecords(
                COUNT * 2,
                COUNT * 4,
                IntStream.range(0, COUNT * 2).mapToObj(MerkleDbDataSourceMetricsTest::createVirtualInternalRecord),
                Stream.empty(),
                Stream.empty());

        // two 8 MB memory chunks
        final int expectedHashesIndexSize = 16;
        assertMetricValue("ds_offheap_hashesIndexMb_" + TABLE_NAME, expectedHashesIndexSize);
        // Hash list bucket is 1_000_000
        final int hashListBucketSize = 1_000_000;
        final int expectedHashListBuckets = (HASHES_RAM_THRESHOLD + hashListBucketSize - 1) / hashListBucketSize;
        final int expectedHashesListSize = (int) (expectedHashListBuckets
                * hashListBucketSize
                * DigestType.SHA_384.digestLength()
                * UnitConstants.BYTES_TO_MEBIBYTES);
        assertMetricValue("ds_offheap_hashesListMb_" + TABLE_NAME, expectedHashesListSize);
        assertMetricValue("ds_offheap_dataSourceMb_" + TABLE_NAME, expectedHashesIndexSize + expectedHashesListSize);
        assertNoMemoryForLeafAndKeyToPathLists();
    }

    @Tag(TestComponentTags.VMAP)
    @Test
    void createAndCheckLeaves() throws IOException {
        assertNoMemoryForInternalList();
        assertNoMemoryForLeafAndKeyToPathLists();
        // create some leaves
        final int firstLeafIndex = COUNT;
        final int lastLeafIndex = COUNT * 2;
        final KeySerializer keySerializer = fixed_fixed.dataType().getKeySerializer();
        final ValueSerializer valueSerializer = fixed_fixed.dataType().getValueSerializer();
        dataSource.saveRecords(
                firstLeafIndex,
                lastLeafIndex,
                Stream.empty(),
                IntStream.range(firstLeafIndex, lastLeafIndex)
                        .mapToObj(i -> fixed_fixed.dataType().createVirtualLeafRecord(i))
                        .map(r -> r.toBytes(keySerializer, valueSerializer)),
                Stream.empty());

        // only one 8 MB memory is reserved despite the fact that leaves reside in [COUNT, COUNT * 2] interval
        assertMetricValue("ds_offheap_leavesIndexMb_" + TABLE_NAME, 8);
        assertMetricValue("ds_offheap_objectKeyBucketsIndexMb_" + TABLE_NAME, 8);
        assertMetricValue("ds_offheap_dataSourceMb_" + TABLE_NAME, 16);
        assertNoMemoryForInternalList();

        dataSource.saveRecords(
                firstLeafIndex,
                lastLeafIndex + DEFAULT_RESERVED_BUFFER_LENGTH + 1,
                Stream.empty(),
                IntStream.range(firstLeafIndex, lastLeafIndex + DEFAULT_RESERVED_BUFFER_LENGTH + 1)
                        .mapToObj(i -> fixed_fixed.dataType().createVirtualLeafRecord(i))
                        .map(r -> r.toBytes(keySerializer, valueSerializer)),
                Stream.empty());

        // reserved additional memory chunk for a value that didn't fit into the previous chunk
        assertMetricValue("ds_offheap_leavesIndexMb_" + TABLE_NAME, 16);
        assertMetricValue("ds_offheap_objectKeyBucketsIndexMb_" + TABLE_NAME, 8);
        assertMetricValue("ds_offheap_dataSourceMb_" + TABLE_NAME, 24);
        assertNoMemoryForInternalList();

        dataSource.saveRecords(
                lastLeafIndex + DEFAULT_RESERVED_BUFFER_LENGTH,
                lastLeafIndex + DEFAULT_RESERVED_BUFFER_LENGTH + 1,
                Stream.empty(),
                // valid leaf index
                IntStream.of(lastLeafIndex + DEFAULT_RESERVED_BUFFER_LENGTH)
                        .mapToObj(i -> fixed_fixed.dataType().createVirtualLeafRecord(i))
                        .map(r -> r.toBytes(keySerializer, valueSerializer)),
                Stream.empty());

        // shrink the list by one chunk
        assertMetricValue("ds_offheap_leavesIndexMb_" + TABLE_NAME, 8);

        assertMetricValue("ds_offheap_objectKeyBucketsIndexMb_" + TABLE_NAME, 8);
        assertMetricValue("ds_offheap_dataSourceMb_" + TABLE_NAME, 16);
        assertNoMemoryForInternalList();
    }

    @AfterEach
    public void afterEach() throws IOException {
        dataSource.close();
        assertEventuallyEquals(
                0L, MerkleDbDataSource::getCountOfOpenDatabases, Duration.ofSeconds(1), "Expected no open dbs");
        // check the database was deleted
        assertEventuallyFalse(
                () -> Files.exists(testDirectory.resolve(TABLE_NAME)),
                Duration.ofSeconds(1),
                "Database should have been deleted by closeAndDelete()");
    }

    // =================================================================================================================
    // Helper Methods

    private void assertNoMemoryForInternalList() {
        assertMetricValue("ds_offheap_hashesIndexMb_" + TABLE_NAME, 0);
    }

    private void assertNoMemoryForLeafAndKeyToPathLists() {
        assertMetricValue("ds_offheap_leavesIndexMb_" + TABLE_NAME, 0);
        assertMetricValue("ds_offheap_objectKeyBucketsIndexMb_" + TABLE_NAME, 0);
    }

    private void assertMetricValue(final String metricPattern, final int expectedValue) {
        final Metric metric = getMetric(metrics, dataSource, metricPattern);
        assertEquals(
                expectedValue,
                Integer.valueOf(metric.get(Metric.ValueType.VALUE).toString()));
    }

    public static MerkleDbDataSource createDataSource(
            final Path testDirectory,
            final String name,
            final TestType testType,
            final int size,
            final long hashesRamToDiskThreshold)
            throws IOException {
        return testType.dataType().createDataSource(testDirectory, name, size, hashesRamToDiskThreshold, false, false);
    }

    public static VirtualHashRecord createVirtualInternalRecord(final int i) {
        return new VirtualHashRecord(i, hash(i));
    }
}
