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

package com.swirlds.virtualmap.internal.reconnect;

import static com.swirlds.test.framework.ResourceLoader.loadLog4jContext;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import com.swirlds.common.constructable.ClassConstructorPair;
import com.swirlds.common.constructable.ConstructableRegistry;
import com.swirlds.common.constructable.ConstructableRegistryException;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.common.merkle.MerkleInternal;
import com.swirlds.common.merkle.synchronization.internal.QueryResponse;
import com.swirlds.common.merkle.synchronization.settings.ReconnectSettings;
import com.swirlds.common.merkle.synchronization.settings.ReconnectSettingsFactory;
import com.swirlds.common.metrics.Metrics;
import com.swirlds.common.test.merkle.dummy.DummyMerkleInternal;
import com.swirlds.common.test.merkle.dummy.DummyMerkleLeaf;
import com.swirlds.common.test.merkle.util.MerkleTestUtils;
import com.swirlds.virtualmap.TestKey;
import com.swirlds.virtualmap.TestValue;
import com.swirlds.virtualmap.VirtualMap;
import com.swirlds.virtualmap.datasource.InMemoryKeySet;
import com.swirlds.virtualmap.datasource.VirtualDataSource;
import com.swirlds.virtualmap.datasource.VirtualDataSourceBuilder;
import com.swirlds.virtualmap.datasource.VirtualInternalRecord;
import com.swirlds.virtualmap.datasource.VirtualKeySet;
import com.swirlds.virtualmap.datasource.VirtualLeafRecord;
import com.swirlds.virtualmap.internal.merkle.VirtualMapState;
import com.swirlds.virtualmap.internal.merkle.VirtualRootNode;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;

public abstract class VirtualMapReconnectTestBase {
    protected static final TestKey A_KEY = new TestKey('a');
    protected static final TestKey B_KEY = new TestKey('b');
    protected static final TestKey C_KEY = new TestKey('c');
    protected static final TestKey D_KEY = new TestKey('d');
    protected static final TestKey E_KEY = new TestKey('e');
    protected static final TestKey F_KEY = new TestKey('f');
    protected static final TestKey G_KEY = new TestKey('g');

    protected static final TestValue APPLE = new TestValue("APPLE");
    protected static final TestValue BANANA = new TestValue("BANANA");
    protected static final TestValue CHERRY = new TestValue("CHERRY");
    protected static final TestValue DATE = new TestValue("DATE");
    protected static final TestValue EGGPLANT = new TestValue("EGGPLANT");
    protected static final TestValue FIG = new TestValue("FIG");
    protected static final TestValue GRAPE = new TestValue("GRAPE");

    protected static final TestValue AARDVARK = new TestValue("AARDVARK");
    protected static final TestValue BEAR = new TestValue("BEAR");
    protected static final TestValue CUTTLEFISH = new TestValue("CUTTLEFISH");
    protected static final TestValue DOG = new TestValue("DOG");
    protected static final TestValue EMU = new TestValue("EMU");
    protected static final TestValue FOX = new TestValue("FOX");
    protected static final TestValue GOOSE = new TestValue("GOOSE");

    protected VirtualMap<TestKey, TestValue> teacherMap;
    protected VirtualMap<TestKey, TestValue> learnerMap;
    protected BrokenBuilder teacherBuilder;
    protected BrokenBuilder learnerBuilder;

    protected abstract VirtualDataSourceBuilder<TestKey, TestValue> createBuilder();

    @BeforeEach
    void setupEach() {
        final VirtualDataSourceBuilder<TestKey, TestValue> dataSourceBuilder = createBuilder();
        teacherBuilder = new BrokenBuilder(dataSourceBuilder);
        learnerBuilder = new BrokenBuilder(dataSourceBuilder);
        teacherMap = new VirtualMap<>("Teacher", teacherBuilder);
        learnerMap = new VirtualMap<>("Learner", learnerBuilder);
    }

    @BeforeAll
    public static void startup() throws ConstructableRegistryException, FileNotFoundException {
        loadLog4jContext();
        final ConstructableRegistry registry = ConstructableRegistry.getInstance();
        registry.registerConstructables("com.swirlds.common");
        registry.registerConstructables("com.swirlds.virtualmap");
        registry.registerConstructable(new ClassConstructorPair(QueryResponse.class, QueryResponse::new));
        registry.registerConstructable(new ClassConstructorPair(DummyMerkleInternal.class, DummyMerkleInternal::new));
        registry.registerConstructable(new ClassConstructorPair(DummyMerkleLeaf.class, DummyMerkleLeaf::new));
        registry.registerConstructable(new ClassConstructorPair(VirtualLeafRecord.class, VirtualLeafRecord::new));
        registry.registerConstructable(new ClassConstructorPair(VirtualMap.class, VirtualMap::new));
        registry.registerConstructable(new ClassConstructorPair(VirtualMapState.class, VirtualMapState::new));
        registry.registerConstructable(new ClassConstructorPair(VirtualRootNode.class, VirtualRootNode::new));
        registry.registerConstructable(new ClassConstructorPair(TestKey.class, TestKey::new));
        registry.registerConstructable(new ClassConstructorPair(TestValue.class, TestValue::new));
        registry.registerConstructable(new ClassConstructorPair(BrokenBuilder.class, BrokenBuilder::new));

        ReconnectSettingsFactory.configure(new ReconnectSettings() {
            @Override
            public boolean isActive() {
                return true;
            }

            @Override
            public int getReconnectWindowSeconds() {
                return -1;
            }

            @Override
            public double getFallenBehindThreshold() {
                return 0.5;
            }

            @Override
            public int getAsyncStreamTimeoutMilliseconds() {
                // This is lower than the default, helps test that is supposed to fail to finish faster.
                return 5000;
            }

            @Override
            public int getAsyncOutputStreamFlushMilliseconds() {
                return 100;
            }

            @Override
            public int getAsyncStreamBufferSize() {
                return 10_000;
            }

            @Override
            public int getMaxAckDelayMilliseconds() {
                return 1000;
            }

            @Override
            public int getMaximumReconnectFailuresBeforeShutdown() {
                return 10;
            }

            @Override
            public Duration getMinimumTimeBetweenReconnects() {
                return Duration.ofMinutes(10);
            }
        });
    }

    protected MerkleInternal createTreeForMap(VirtualMap<TestKey, TestValue> map) {
        final var tree = MerkleTestUtils.buildLessSimpleTree();
        tree.getChild(1).asInternal().setChild(3, map);
        tree.reserve();
        return tree;
    }

    protected void reconnect() throws Exception {
        reconnectMultipleTimes(1);
    }

    protected void reconnectMultipleTimes(int attempts) {
        final MerkleInternal teacherTree = createTreeForMap(teacherMap);
        final VirtualMap<TestKey, TestValue> copy = teacherMap.copy();
        final MerkleInternal learnerTree = createTreeForMap(learnerMap);
        try {
            for (int i = 0; i < attempts; i++) {
                try {
                    final var node = MerkleTestUtils.hashAndTestSynchronization(learnerTree, teacherTree);
                    node.release();
                    assertEquals(attempts - 1, i, "We should only succeed on the last try");
                } catch (Exception e) {
                    if (i == attempts - 1) {
                        fail("We did not expect an exception on this reconnect attempt!", e);
                    }
                }
            }
        } finally {
            teacherTree.release();
            learnerTree.release();
            copy.release();
        }
    }

    protected static final class BrokenBuilder implements VirtualDataSourceBuilder<TestKey, TestValue> {

        private static final long CLASS_ID = 0x5a79654cd0f96dcfL;
        private VirtualDataSourceBuilder<TestKey, TestValue> delegate;
        private int numCallsBeforeThrow = Integer.MAX_VALUE;
        private int numCalls = 0;
        private int numTimesToBreak = 0;
        private int numTimesBroken = 0;

        public BrokenBuilder() {}

        public BrokenBuilder(VirtualDataSourceBuilder<TestKey, TestValue> delegate) {
            this.delegate = delegate;
        }

        @Override
        public long getClassId() {
            return CLASS_ID;
        }

        @Override
        public int getVersion() {
            return 1;
        }

        @Override
        public void serialize(final SerializableDataOutputStream out) throws IOException {
            delegate.serialize(out);
            out.writeInt(numCallsBeforeThrow);
            out.writeInt(numTimesToBreak);
            out.writeInt(numCalls);
            out.writeInt(numTimesBroken);
        }

        @Override
        public void deserialize(final SerializableDataInputStream in, final int version) throws IOException {
            delegate.deserialize(in, version);
            numCallsBeforeThrow = in.readInt();
            numTimesToBreak = in.readInt();
            numCalls = in.readInt();
            numTimesBroken = in.readInt();
        }

        @Override
        public BreakableDataSource build(final String label, final boolean withDbCompactionEnabled) {
            return new BreakableDataSource(this, delegate.build(label, withDbCompactionEnabled));
        }

        @Override
        public BreakableDataSource copy(
                final VirtualDataSource<TestKey, TestValue> snapshotMe, final boolean makeCopyActive) {
            final var breakableSnapshot = (BreakableDataSource) snapshotMe;
            return new BreakableDataSource(this, delegate.copy(breakableSnapshot.delegate, makeCopyActive));
        }

        @Override
        public void snapshot(final Path destination, final VirtualDataSource<TestKey, TestValue> snapshotMe) {
            final var breakableSnapshot = (BreakableDataSource) snapshotMe;
            delegate.snapshot(destination, breakableSnapshot.delegate);
        }

        @Override
        public BreakableDataSource restore(final String label, final Path from) {
            return new BreakableDataSource(this, delegate.restore(label, from));
        }

        public void setNumCallsBeforeThrow(int num) {
            this.numCallsBeforeThrow = num;
        }

        public void setNumTimesToBreak(int num) {
            this.numTimesToBreak = num;
        }
    }

    protected static final class BreakableDataSource implements VirtualDataSource<TestKey, TestValue> {
        private final VirtualDataSource<TestKey, TestValue> delegate;
        private final BrokenBuilder builder;

        public BreakableDataSource(final BrokenBuilder builder, final VirtualDataSource<TestKey, TestValue> delegate) {
            this.delegate = Objects.requireNonNull(delegate);
            this.builder = Objects.requireNonNull(builder);
        }

        @Override
        public void saveRecords(
                final long firstLeafPath,
                final long lastLeafPath,
                final Stream<VirtualInternalRecord> internalRecords,
                final Stream<VirtualLeafRecord<TestKey, TestValue>> leafRecordsToAddOrUpdate,
                final Stream<VirtualLeafRecord<TestKey, TestValue>> leafRecordsToDelete)
                throws IOException {

            final List<VirtualLeafRecord<TestKey, TestValue>> leaves =
                    leafRecordsToAddOrUpdate.collect(Collectors.toList());

            if (builder.numTimesBroken < builder.numTimesToBreak) {
                builder.numCalls += leaves.size();
                if (builder.numCalls > builder.numCallsBeforeThrow) {
                    builder.numCalls = 0;
                    builder.numTimesBroken++;
                    delegate.close();
                    throw new IOException("Something bad on the DB!");
                }
            }

            delegate.saveRecords(firstLeafPath, lastLeafPath, internalRecords, leaves.stream(), leafRecordsToDelete);
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
        public void snapshot(final Path snapshotDirectory) throws IOException {
            delegate.snapshot(snapshotDirectory);
        }

        @Override
        public void copyStatisticsFrom(final VirtualDataSource<TestKey, TestValue> that) {}

        @Override
        public void registerMetrics(final Metrics metrics) {}

        @Override
        public VirtualKeySet<TestKey> buildKeySet() {
            return new InMemoryKeySet<>();
        }
    }
}
