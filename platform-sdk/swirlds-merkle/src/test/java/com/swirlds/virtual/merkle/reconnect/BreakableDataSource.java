/*
 * Copyright (C) 2022-2024 Hedera Hashgraph, LLC
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

package com.swirlds.virtual.merkle.reconnect;

import com.swirlds.common.crypto.Hash;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.metrics.api.Metrics;
import com.swirlds.virtual.merkle.TestKey;
import com.swirlds.virtual.merkle.TestValue;
import com.swirlds.virtualmap.datasource.VirtualDataSource;
import com.swirlds.virtualmap.datasource.VirtualHashRecord;
import com.swirlds.virtualmap.datasource.VirtualLeafRecord;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

public final class BreakableDataSource implements VirtualDataSource<TestKey, TestValue> {

    final VirtualDataSource<TestKey, TestValue> delegate;
    private final BrokenBuilder builder;

    public BreakableDataSource(final BrokenBuilder builder, final VirtualDataSource<TestKey, TestValue> delegate) {
        this.delegate = Objects.requireNonNull(delegate);
        this.builder = Objects.requireNonNull(builder);
    }

    @Override
    public void saveRecords(
            final long firstLeafPath,
            final long lastLeafPath,
            final Stream<VirtualHashRecord> pathHashRecordsToUpdate,
            final Stream<VirtualLeafRecord<TestKey, TestValue>> leafRecordsToAddOrUpdate,
            final Stream<VirtualLeafRecord<TestKey, TestValue>> leafRecordsToDelete,
            final boolean isReconnectContext)
            throws IOException {
        final List<VirtualLeafRecord<TestKey, TestValue>> leaves = leafRecordsToAddOrUpdate.toList();

        if (builder.numTimesBroken < builder.numTimesToBreak) {
            // Syncronization block is not required here, as this code is never called in parallel
            // (though from different threads). `volatile` modifier is sufficient to ensure visibility.
            builder.numCalls += leaves.size();
            if (builder.numCalls > builder.numCallsBeforeThrow) {
                builder.numCalls = 0;
                builder.numTimesBroken++;
                delegate.close();
                throw new IOException("Something bad on the DB!");
            }
        }

        delegate.saveRecords(
                firstLeafPath, lastLeafPath, pathHashRecordsToUpdate, leaves.stream(), leafRecordsToDelete);
    }

    @Override
    public void close() throws IOException {
        delegate.close();
    }

    @Override
    public VirtualLeafRecord<TestKey, TestValue> loadLeafRecord(final TestKey key) throws IOException {
        return delegate.loadLeafRecord(key);
    }

    @Override
    public VirtualLeafRecord<TestKey, TestValue> loadLeafRecord(final long path) throws IOException {
        return delegate.loadLeafRecord(path);
    }

    @Override
    public long findKey(final TestKey key) throws IOException {
        return delegate.findKey(key);
    }

    @Override
    public Hash loadHash(final long path) throws IOException {
        return delegate.loadHash(path);
    }

    @Override
    public boolean loadAndWriteHash(long path, SerializableDataOutputStream out) throws IOException {
        return delegate.loadAndWriteHash(path, out);
    }

    @Override
    public void snapshot(final Path snapshotDirectory) throws IOException {
        delegate.snapshot(snapshotDirectory);
    }

    @Override
    public void copyStatisticsFrom(final VirtualDataSource<TestKey, TestValue> that) {}

    @Override
    public void registerMetrics(final Metrics metrics) {}

    @Override
    public long getFirstLeafPath() {
        return delegate.getFirstLeafPath();
    }

    @Override
    public long getLastLeafPath() {
        return delegate.getLastLeafPath();
    }

    @Override
    public long estimatedSize(final long dirtyInternals, final long dirtyLeaves) {
        return delegate.estimatedSize(dirtyInternals, dirtyLeaves);
    }

    @Override
    public void enableBackgroundCompaction() {
        delegate.enableBackgroundCompaction();
    }

    @Override
    public void stopAndDisableBackgroundCompaction() {
        delegate.stopAndDisableBackgroundCompaction();
    }
}
