/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

import com.swirlds.common.crypto.Hash;
import com.swirlds.common.io.utility.TemporaryFileBuilder;
import com.swirlds.common.metrics.Metrics;
import com.swirlds.test.framework.TestTypeTags;
import com.swirlds.virtualmap.VirtualKey;
import com.swirlds.virtualmap.VirtualLongKey;
import com.swirlds.virtualmap.VirtualMap;
import com.swirlds.virtualmap.VirtualValue;
import com.swirlds.virtualmap.datasource.VirtualDataSource;
import com.swirlds.virtualmap.datasource.VirtualDataSourceBuilder;
import com.swirlds.virtualmap.datasource.VirtualInternalRecord;
import com.swirlds.virtualmap.datasource.VirtualKeySet;
import com.swirlds.virtualmap.datasource.VirtualLeafRecord;
import com.swirlds.virtualmap.internal.pipeline.VirtualRoot;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Tags;
import org.junit.jupiter.api.Test;

/**
 * This is a regression test for swirlds/swirlds-platform/issues/6151, but
 * it can be used to find many different issues with VirtualMap.
 *
 * The test creates a virtual map and makes its copies in a loop, until it gets flushed to
 * disk. Right after a flush is started, the last map is released, which triggers virtual
 * pipeline shutdown. The test then makes sure the flush completes without exceptions.
 */
public class CloseFlushTest {

    private static Path tmpFileDir;

    @BeforeAll
    public static void setup() throws IOException {
        tmpFileDir = TemporaryFileBuilder.buildTemporaryFile();
    }

    @Test
    @Tags({@Tag(TestTypeTags.HAMMER)})
    public void closeFlushTest() throws Exception {
        final int count = 100000;
        final AtomicReference<Exception> exception = new AtomicReference<>();
        for (int j = 0; j < 100; j++) {
            final Path storeDir = tmpFileDir.resolve("closeFlushTest-" + j);
            final VirtualDataSource<VirtualLongKey, ExampleByteArrayVirtualValue> dataSource =
                    TestType.fixed_fixed.dataType().createDataSource(storeDir, "closeFlushTest", count, 0, false, true);
            // Create a custom data source builder, which creates a custom data source to capture
            // all exceptions happened in saveRecords()
            final VirtualDataSourceBuilder<VirtualLongKey, ExampleByteArrayVirtualValue> builder =
                    new CustomDataSourceBuilder<>(dataSource, exception);
            VirtualMap<VirtualLongKey, ExampleByteArrayVirtualValue> map = new VirtualMap<>("closeFlushTest", builder);
            for (int i = 0; i < count; i++) {
                final ExampleLongKeyFixedSize key = new ExampleLongKeyFixedSize(i);
                final ExampleFixedSizeVirtualValue value = new ExampleFixedSizeVirtualValue(i);
                map.put(key, value);
            }
            VirtualMap<VirtualLongKey, ExampleByteArrayVirtualValue> copy;
            VirtualRoot root;
            final CountDownLatch shutdownLatch = new CountDownLatch(1);
            while (true) {
                copy = map.copy();
                root = map.getRight();
                if (root.requestedToFlush()) {
                    final VirtualMap<VirtualLongKey, ExampleByteArrayVirtualValue> finalCopy = copy;
                    new Thread(() -> {
                                try {
                                    Thread.sleep(new Random().nextInt(500));
                                    finalCopy.release();
                                } catch (final Exception z) {
                                    throw new RuntimeException(z);
                                } finally {
                                    shutdownLatch.countDown();
                                }
                            })
                            .start();
                    break;
                }
                map.release();
                map = copy;
            }
            map.release();
            shutdownLatch.await();
            if (exception.get() != null) {
                exception.get().printStackTrace();
                break;
            }
        }
        Assertions.assertNull(exception.get(), "No exceptions expected, but caught " + exception.get());
    }

    public static class CustomDataSourceBuilder<K extends VirtualKey<? super K>, V extends VirtualValue>
            extends MerkleDbDataSourceBuilder<K, V> {

        private VirtualDataSource<K, V> delegate = null;
        private AtomicReference<Exception> exceptionSink = null;

        // Provided for deserialization
        public CustomDataSourceBuilder() {}

        public CustomDataSourceBuilder(final VirtualDataSource<K, V> delegate, AtomicReference<Exception> sink) {
            this.delegate = delegate;
            this.exceptionSink = sink;
        }

        @Override
        public long getClassId() {
            return super.getClassId() + 1;
        }

        @Override
        public VirtualDataSource<K, V> build(final String label, final boolean withDbCompactionEnabled) {
            return new VirtualDataSource<>() {
                @Override
                public void close() throws IOException {
                    delegate.close();
                }

                @Override
                public void saveRecords(
                        final long firstLeafPath,
                        final long lastLeafPath,
                        final Stream<VirtualInternalRecord> internalRecords,
                        final Stream<VirtualLeafRecord<K, V>> leafRecordsToAddOrUpdate,
                        final Stream<VirtualLeafRecord<K, V>> leafRecordsToDelete)
                        throws IOException {
                    try {
                        delegate.saveRecords(
                                firstLeafPath,
                                lastLeafPath,
                                internalRecords,
                                leafRecordsToAddOrUpdate,
                                leafRecordsToDelete);
                    } catch (final Exception e) {
                        exceptionSink.set(e);
                    }
                }

                @Override
                public VirtualLeafRecord<K, V> loadLeafRecord(final K key) throws IOException {
                    return delegate.loadLeafRecord(key);
                }

                @Override
                public VirtualLeafRecord<K, V> loadLeafRecord(final long path) throws IOException {
                    return delegate.loadLeafRecord(path);
                }

                @Override
                public long findKey(final K key) throws IOException {
                    return delegate.findKey(key);
                }

                @Override
                public VirtualInternalRecord loadInternalRecord(final long path, final boolean deserialize)
                        throws IOException {
                    return delegate.loadInternalRecord(path);
                }

                @Override
                public Hash loadLeafHash(final long path) throws IOException {
                    return delegate.loadLeafHash(path);
                }

                @Override
                public void snapshot(final Path snapshotDirectory) throws IOException {
                    delegate.snapshot(snapshotDirectory);
                }

                @Override
                public void copyStatisticsFrom(final VirtualDataSource<K, V> that) {
                    delegate.copyStatisticsFrom(that);
                }

                @Override
                public void registerMetrics(final Metrics metrics) {
                    delegate.registerMetrics(metrics);
                }

                @Override
                public VirtualKeySet<K> buildKeySet() {
                    return delegate.buildKeySet();
                }

                @Override
                public long estimatedSize(final long dirtyInternals, final long dirtyLeaves, final long deletedLeaves) {
                    return delegate.estimatedSize(dirtyInternals, dirtyLeaves, deletedLeaves);
                }
            };
        }
    }
}
