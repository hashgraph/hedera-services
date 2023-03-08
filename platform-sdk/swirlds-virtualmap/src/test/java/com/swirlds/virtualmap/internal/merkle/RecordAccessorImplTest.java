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

package com.swirlds.virtualmap.internal.merkle;

import static com.swirlds.virtualmap.internal.Path.INVALID_PATH;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.swirlds.common.crypto.Cryptography;
import com.swirlds.common.crypto.CryptographyHolder;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.metrics.Metrics;
import com.swirlds.virtualmap.TestKey;
import com.swirlds.virtualmap.TestValue;
import com.swirlds.virtualmap.datasource.InMemoryBuilder;
import com.swirlds.virtualmap.datasource.InMemoryDataSource;
import com.swirlds.virtualmap.datasource.InMemoryKeySet;
import com.swirlds.virtualmap.datasource.VirtualDataSource;
import com.swirlds.virtualmap.datasource.VirtualInternalRecord;
import com.swirlds.virtualmap.datasource.VirtualKeySet;
import com.swirlds.virtualmap.datasource.VirtualLeafRecord;
import com.swirlds.virtualmap.internal.cache.VirtualNodeCache;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class RecordAccessorImplTest {
    private static final int MAX_PATH = 12;
    private static final Cryptography CRYPTO = CryptographyHolder.get();

    private static final long UNCHANGED_INTERNAL_PATH = 1;
    private static final long CHANGED_INTERNAL_PATH = 2;
    private static final long CHANGED_LEAF_PATH = 5;
    private static final long CHANGED_LEAF_KEY = 11;
    private static final long UNCHANGED_LEAF_PATH = 7;
    private static final long DELETED_INTERNAL_PATH = 5;
    private static final long OLD_DELETED_INTERNAL_PATH = 13; // This is bogus in the real world but OK for this test
    private static final long DELETED_LEAF_PATH = 12;
    private static final long BOGUS_LEAF_PATH = 22;

    private BreakableDataSource dataSource;
    private RecordAccessorImpl<TestKey, TestValue> records;
    private RecordAccessorImpl<TestKey, TestValue> mutableRecords;

    @BeforeEach
    void setUp() throws IOException {
        DummyVirtualStateAccessor state = new DummyVirtualStateAccessor();
        VirtualNodeCache<TestKey, TestValue> cache = new VirtualNodeCache<>();
        dataSource = new BreakableDataSource();
        records = new RecordAccessorImpl<>(state, cache, dataSource);

        // Prepopulate the database with some records
        final VirtualInternalRecord root = internal(0);
        final VirtualInternalRecord left = internal(1);
        final VirtualInternalRecord right = internal(2);
        final VirtualInternalRecord leftLeft = internal(3);
        final VirtualInternalRecord leftRight = internal(4);
        final VirtualInternalRecord rightLeft = internal(5);
        final VirtualLeafRecord<TestKey, TestValue> firstLeaf = leaf(6);
        final VirtualLeafRecord<TestKey, TestValue> secondLeaf = leaf(7);
        final VirtualLeafRecord<TestKey, TestValue> thirdLeaf = leaf(8);
        final VirtualLeafRecord<TestKey, TestValue> fourthLeaf = leaf(9);
        final VirtualLeafRecord<TestKey, TestValue> fifthLeaf = leaf(10);
        final VirtualLeafRecord<TestKey, TestValue> sixthLeaf = leaf(11);
        final VirtualLeafRecord<TestKey, TestValue> seventhLeaf = leaf(12);

        dataSource.saveRecords(
                6,
                12,
                Stream.of(root, left, right, leftLeft, leftRight, rightLeft),
                Stream.of(firstLeaf, secondLeaf, thirdLeaf, fourthLeaf, fifthLeaf, sixthLeaf, seventhLeaf),
                Stream.empty());

        // Prepopulate the cache with some of those records. Some will be deleted, some will be modified, some will
        // not be in the cache.
        final var rootChanged = internal(0);
        rootChanged.setHash(CRYPTO.digestSync("0 changed".getBytes(StandardCharsets.UTF_8)));
        final var rightChanged = internal(CHANGED_INTERNAL_PATH);
        rightChanged.setHash(CRYPTO.digestSync("2 changed".getBytes(StandardCharsets.UTF_8)));
        final var sixthLeafMoved = leaf(11);
        sixthLeafMoved.setPath(CHANGED_LEAF_PATH);
        final var seventhLeafGone = leaf(DELETED_LEAF_PATH);

        cache.putLeaf(sixthLeafMoved);
        cache.deleteLeaf(seventhLeafGone);
        cache.deleteInternal(DELETED_INTERNAL_PATH);
        cache.deleteInternal(OLD_DELETED_INTERNAL_PATH);
        mutableRecords = new RecordAccessorImpl<>(state, cache.copy(), dataSource);
        cache.prepareForHashing();
        cache.putInternal(rootChanged);
        cache.putInternal(rightChanged);

        // Set up the state for a 6 leaf in memory tree
        state.setFirstLeafPath(5);
        state.setLastLeafPath(10);
    }

    @Test
    @DisplayName("findRecord of bad path throws")
    void findRecordInvalidPathThrows() {
        assertThrows(AssertionError.class, () -> records.findRecord(INVALID_PATH), "Should have thrown");
    }

    @Test
    @DisplayName("findRecord of bad path returns null")
    void findRecordBadPath() {
        assertNull(records.findRecord(MAX_PATH + 1), "Should be null");
    }

    @Test
    @DisplayName("findRecord in cache returns same instance")
    void findRecordInCacheReturnsSameInstance() {
        // As a control, make sure if we look up one on the data source it is NOT the same instance
        assertNotSame(
                records.findRecord(UNCHANGED_INTERNAL_PATH),
                records.findRecord(UNCHANGED_INTERNAL_PATH),
                "Should be different instances");

        // Look for an internal record that we *KNOW* is in the in memory cache
        final var internal = records.findRecord(CHANGED_INTERNAL_PATH);
        assertNotNull(internal, "Did not find record");
        assertEquals(CHANGED_INTERNAL_PATH, internal.getPath(), "Unexpected path in record");
        assertSame(internal, records.findRecord(CHANGED_INTERNAL_PATH), "Did not find the same in memory instance!");

        // Look for a leaf record that we *KNOW* is in the in memory cache
        final var leaf = records.findRecord(CHANGED_LEAF_PATH);
        assertNotNull(leaf, "Did not find record");
        assertEquals(CHANGED_LEAF_PATH, leaf.getPath(), "Unexpected path in record");
        assertSame(leaf, records.findRecord(CHANGED_LEAF_PATH), "Did not find the same in memory instance!");
    }

    @Test
    @DisplayName("findRecord of record on disk works")
    void findRecordOnDiskReturns() {
        // Look for an internal record that we *KNOW* is on disk
        final var internal = records.findRecord(UNCHANGED_INTERNAL_PATH);
        assertNotNull(internal, "Did not find record");
        assertEquals(UNCHANGED_INTERNAL_PATH, internal.getPath(), "Unexpected path in record");
        assertNotSame(
                internal,
                records.findRecord(UNCHANGED_INTERNAL_PATH),
                "Found the same instance on disk? Shouldn't happen!");

        // Look for a leaf record that we *KNOW* is on disk
        final var leaf = records.findRecord(UNCHANGED_LEAF_PATH);
        assertNotNull(leaf, "Did not find record");
        assertEquals(UNCHANGED_LEAF_PATH, leaf.getPath(), "Unexpected path in record");
        assertNotSame(
                leaf, records.findRecord(UNCHANGED_LEAF_PATH), "Found the same instance on disk? Shouldn't happen!");
    }

    @Test
    @DisplayName("findRecord of record with broken data source throws")
    void findRecordOnDiskWhenBrokenThrows() {
        dataSource.throwExceptionOnLoadInternalRecordByPath = true;
        assertThrows(
                UncheckedIOException.class,
                () -> records.findRecord(UNCHANGED_INTERNAL_PATH),
                " Should have thrown UncheckedIOException");

        dataSource.throwExceptionOnLoadLeafRecordByPath = true;
        assertThrows(
                UncheckedIOException.class,
                () -> records.findRecord(UNCHANGED_LEAF_PATH),
                " Should have thrown UncheckedIOException");
    }

    @Test
    @DisplayName("findRecord of deleted record returns null")
    void findRecordWhenDeletedIsNull() {
        assertNull(records.findRecord(OLD_DELETED_INTERNAL_PATH), "Deleted records should be null");
        assertNull(records.findRecord(DELETED_LEAF_PATH), "Deleted records should be null");
    }

    @Test
    @DisplayName("findInternalRecord of invalid path throws")
    void findInternalRecordInvalidPathThrows() {
        assertThrows(AssertionError.class, () -> records.findInternalRecord(INVALID_PATH), "Should throw");
    }

    @Test
    @DisplayName("findInternalRecord of bad path returns null")
    void findInternalRecordBadPath() {
        assertNull(records.findInternalRecord(MAX_PATH + 1), "Should have been null");
    }

    @Test
    @DisplayName("findInternalRecord in cache returns same instance")
    void findInternalRecordInCacheReturnsSameInstance() {
        final var internal = records.findInternalRecord(CHANGED_INTERNAL_PATH);
        assertNotNull(internal, "Did not find record");
        assertEquals(CHANGED_INTERNAL_PATH, internal.getPath(), "Unexpected path in record");
        assertSame(
                internal,
                records.findInternalRecord(CHANGED_INTERNAL_PATH),
                "Did not find the same in memory instance!");
    }

    @Test
    @DisplayName("findInternalRecord of record on disk works")
    void findInternalRecordOnDiskReturns() {
        final var internal = records.findInternalRecord(UNCHANGED_INTERNAL_PATH);
        assertNotNull(internal, "Did not find record");
        assertEquals(UNCHANGED_INTERNAL_PATH, internal.getPath(), "Unexpected path in record");
        assertNotSame(
                internal,
                records.findInternalRecord(UNCHANGED_INTERNAL_PATH),
                "Found the same instance on disk? Shouldn't happen!");
    }

    @Test
    @DisplayName("findInternalRecord of record with broken data source throws")
    void findInternalRecordOnDiskWhenBrokenThrows() {
        dataSource.throwExceptionOnLoadInternalRecordByPath = true;
        assertThrows(
                UncheckedIOException.class,
                () -> records.findInternalRecord(UNCHANGED_INTERNAL_PATH),
                " Should have thrown UncheckedIOException");
    }

    @Test
    @DisplayName("findInternalRecord of deleted record returns null")
    void findInternalRecordWhenDeletedIsNull() {
        assertNull(records.findInternalRecord(OLD_DELETED_INTERNAL_PATH), "Deleted records should be null");
    }

    // findLeafRecord by key with "true" puts it in the cache
    // findLeafRecord by path with "true" puts it in the cache

    @Test
    @DisplayName("findLeafRecord by key of bogus key returns null")
    void findLeafRecordBogusKey() {
        assertNull(records.findLeafRecord(new TestKey(BOGUS_LEAF_PATH), false), "Should be null");
        assertNull(records.findLeafRecord(new TestKey(BOGUS_LEAF_PATH), true), "Should be null");
    }

    @Test
    @DisplayName("findLeafRecord by key in cache returns same instance")
    void findLeafRecordByKeyInCacheReturnsSameInstance() {
        final var leaf = records.findLeafRecord(new TestKey(CHANGED_LEAF_KEY), false);
        assertNotNull(leaf, "Did not find record");
        assertEquals(CHANGED_LEAF_PATH, leaf.getPath(), "Unexpected path in record");
        assertEquals(new TestKey(CHANGED_LEAF_KEY), leaf.getKey(), "Unexpected key in record");
        assertSame(
                leaf,
                records.findLeafRecord(new TestKey(CHANGED_LEAF_KEY), false),
                "Did not find the same in memory instance!");
    }

    @Test
    @DisplayName("findLeafRecord by key of record on disk works")
    void findLeafRecordByKeyOnDiskReturns() {
        final var leaf = records.findLeafRecord(new TestKey(UNCHANGED_LEAF_PATH), false);
        assertNotNull(leaf, "Did not find record");
        assertEquals(UNCHANGED_LEAF_PATH, leaf.getPath(), "Unexpected path in record");
        assertNotSame(
                leaf,
                records.findLeafRecord(new TestKey(UNCHANGED_LEAF_PATH), false),
                "Found the same instance on disk? Shouldn't happen!");
    }

    @Test
    @DisplayName("findLeafRecord by key on disk 'copy' true returns a record that is now in the cache")
    void findLeafRecordOnDiskKeyCopy() {
        final var record = mutableRecords.findLeafRecord(new TestKey(UNCHANGED_LEAF_PATH), true);
        assertNotNull(record, "Should not be null");
        assertSame(
                record,
                mutableRecords.findLeafRecord(new TestKey(UNCHANGED_LEAF_PATH), false),
                "Should have been the same");
    }

    @Test
    @DisplayName("findLeafRecord by key of record with broken data source throws")
    void findLeafRecordByKeyOnDiskWhenBrokenThrows() {
        dataSource.throwExceptionOnLoadLeafRecordByKey = true;
        final TestKey key = new TestKey(UNCHANGED_LEAF_PATH);
        assertThrows(
                UncheckedIOException.class,
                () -> records.findLeafRecord(key, false),
                "Should have thrown UncheckedIOException");
    }

    @Test
    @DisplayName("findLeafRecord by key of deleted record returns null")
    void findLeafRecordByKeyWhenDeletedIsNull() {
        assertNull(records.findLeafRecord(new TestKey(DELETED_LEAF_PATH), false), "Deleted records should be null");
    }

    @Test
    @DisplayName("findLeafRecord of bad path returns null")
    void findLeafRecordBadPath() {
        assertNull(records.findLeafRecord(BOGUS_LEAF_PATH, false), "Should be null");
        assertNull(records.findLeafRecord(BOGUS_LEAF_PATH, true), "Should be null");
    }

    @Test
    @DisplayName("findLeafRecord by path in cache returns same instance")
    void findLeafRecordByPathInCacheReturnsSameInstance() {
        final var leaf = records.findLeafRecord(CHANGED_LEAF_PATH, false);
        assertNotNull(leaf, "Did not find record");
        assertEquals(CHANGED_LEAF_PATH, leaf.getPath(), "Unexpected path in record");
        assertSame(leaf, records.findLeafRecord(CHANGED_LEAF_PATH, false), "Did not find the same in memory instance!");
    }

    @Test
    @DisplayName("findLeafRecord by path of record on disk works")
    void findLeafRecordByPathOnDiskReturns() {
        final var leaf = records.findLeafRecord(UNCHANGED_LEAF_PATH, false);
        assertNotNull(leaf, "Did not find record");
        assertEquals(UNCHANGED_LEAF_PATH, leaf.getPath(), "Unexpected path in record");
        assertNotSame(
                leaf,
                records.findLeafRecord(UNCHANGED_LEAF_PATH, false),
                "Found the same instance on disk? Shouldn't happen!");
    }

    @Test
    @DisplayName("findLeafRecord by key on disk 'copy' true returns a record that is now in the cache")
    void findLeafRecordOnDiskPathCopy() {
        final var record = mutableRecords.findLeafRecord(UNCHANGED_LEAF_PATH, true);
        assertNotNull(record, "Should not be null");
        assertSame(record, mutableRecords.findLeafRecord(UNCHANGED_LEAF_PATH, false), "Should have been the same");
    }

    @Test
    @DisplayName("findLeafRecord by path of record with broken data source throws")
    void findLeafRecordByPathOnDiskWhenBrokenThrows() {
        dataSource.throwExceptionOnLoadLeafRecordByPath = true;
        assertThrows(
                UncheckedIOException.class,
                () -> records.findLeafRecord(UNCHANGED_LEAF_PATH, false),
                "Should have thrown UncheckedIOException");
    }

    @Test
    @DisplayName("findLeafRecord by path of deleted record returns null")
    void findLeafRecordByPathWhenDeletedIsNull() {
        assertNull(records.findLeafRecord(DELETED_LEAF_PATH, false), "Deleted records should be null");
    }

    @Test
    @DisplayName("findKey consistent with findLeafRecord by path")
    void findLeafRecordByKeyByPath() {
        final TestKey key = new TestKey(UNCHANGED_LEAF_PATH);
        final long path = records.findKey(key);
        final VirtualLeafRecord<TestKey, TestValue> record = records.findLeafRecord(path, false);
        assertEquals(key, record.getKey());
    }

    private static final class BreakableDataSource implements VirtualDataSource<TestKey, TestValue> {
        private final InMemoryDataSource<TestKey, TestValue> delegate = new InMemoryBuilder().build("delegate", true);
        boolean throwExceptionOnLoadLeafRecordByKey = false;
        boolean throwExceptionOnLoadLeafRecordByPath = false;
        boolean throwExceptionOnLoadInternalRecordByPath = false;
        boolean throwExceptionOnLoadLeafHash = false;

        @Override
        public VirtualLeafRecord<TestKey, TestValue> loadLeafRecord(final TestKey key) throws IOException {
            if (throwExceptionOnLoadLeafRecordByKey) {
                throw new IOException("Thrown by loadLeafRecord by key");
            }
            return delegate.loadLeafRecord(key);
        }

        @Override
        public VirtualLeafRecord<TestKey, TestValue> loadLeafRecord(final long path) throws IOException {
            if (throwExceptionOnLoadLeafRecordByPath) {
                throw new IOException("Thrown by loadLeafRecord by path");
            }
            return delegate.loadLeafRecord(path);
        }

        @Override
        public long findKey(final TestKey key) throws IOException {
            return delegate.findKey(key);
        }

        @Override
        public VirtualInternalRecord loadInternalRecord(final long path, final boolean deserialize) throws IOException {
            if (throwExceptionOnLoadInternalRecordByPath) {
                throw new IOException("Thrown by loadInternalRecord");
            }
            return delegate.loadInternalRecord(path, deserialize);
        }

        @Override
        public Hash loadLeafHash(final long path) throws IOException {
            if (throwExceptionOnLoadLeafHash) {
                throw new IOException("Thrown by loadLeafHash");
            }

            return delegate.loadLeafHash(path);
        }

        @Override
        public void saveRecords(
                final long firstLeafPath,
                final long lastLeafPath,
                final Stream<VirtualInternalRecord> internalRecords,
                final Stream<VirtualLeafRecord<TestKey, TestValue>> leafRecordsToAddOrUpdate,
                final Stream<VirtualLeafRecord<TestKey, TestValue>> leafRecordsToDelete)
                throws IOException {
            delegate.saveRecords(
                    firstLeafPath, lastLeafPath, internalRecords, leafRecordsToAddOrUpdate, leafRecordsToDelete);
        }

        @Override
        public void close() throws IOException {
            throw new UnsupportedOperationException("Not implemented by these tests");
        }

        @Override
        public void snapshot(final Path snapshotDirectory) throws IOException {
            throw new UnsupportedOperationException("Not implemented for these tests");
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void copyStatisticsFrom(final VirtualDataSource<TestKey, TestValue> that) {
            // this database has no statistics
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void registerMetrics(final Metrics metrics) {
            // this database has no statistics
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public VirtualKeySet<TestKey> buildKeySet() {
            return new InMemoryKeySet<>();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public long estimatedSize(final long dirtyInternals, final long dirtyLeaves, final long deletedLeaves) {
            return delegate.estimatedSize(dirtyInternals, dirtyLeaves, deletedLeaves);
        }
    }

    private static VirtualInternalRecord internal(long num) {
        return new VirtualInternalRecord(num, CRYPTO.digestSync(("" + num).getBytes(StandardCharsets.UTF_8)));
    }

    private static VirtualLeafRecord<TestKey, TestValue> leaf(long num) {
        return new VirtualLeafRecord<>(
                num,
                CRYPTO.digestSync(("" + num).getBytes(StandardCharsets.UTF_8)),
                new TestKey(num),
                new TestValue(num));
    }
}
