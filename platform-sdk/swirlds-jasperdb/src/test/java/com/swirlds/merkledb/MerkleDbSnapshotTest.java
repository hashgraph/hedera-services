/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

import static com.swirlds.common.test.fixtures.AssertionUtils.assertEventuallyTrue;
import static com.swirlds.common.test.fixtures.junit.tags.TestQualifierTags.TIMING_SENSITIVE;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.swirlds.common.constructable.ConstructableRegistry;
import com.swirlds.common.crypto.DigestType;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.io.streams.MerkleDataInputStream;
import com.swirlds.common.io.streams.MerkleDataOutputStream;
import com.swirlds.common.io.utility.TemporaryFileBuilder;
import com.swirlds.common.merkle.MerkleInternal;
import com.swirlds.common.merkle.crypto.MerkleCryptoFactory;
import com.swirlds.common.merkle.impl.PartialNaryMerkleInternal;
import com.swirlds.common.metrics.config.MetricsConfig;
import com.swirlds.common.metrics.platform.DefaultMetrics;
import com.swirlds.common.metrics.platform.DefaultMetricsFactory;
import com.swirlds.common.metrics.platform.MetricKeyRegistry;
import com.swirlds.common.test.fixtures.AssertionUtils;
import com.swirlds.config.api.Configuration;
import com.swirlds.config.extensions.test.fixtures.TestConfigBuilder;
import com.swirlds.merkledb.config.MerkleDbConfig;
import com.swirlds.merkledb.serialize.KeySerializer;
import com.swirlds.merkledb.serialize.ValueSerializer;
import com.swirlds.merkledb.test.fixtures.ExampleFixedSizeVirtualValue;
import com.swirlds.merkledb.test.fixtures.ExampleFixedSizeVirtualValueSerializer;
import com.swirlds.merkledb.test.fixtures.ExampleLongKeyFixedSize;
import com.swirlds.metrics.api.Metrics;
import com.swirlds.virtualmap.VirtualMap;
import com.swirlds.virtualmap.datasource.VirtualDataSource;
import com.swirlds.virtualmap.internal.merkle.VirtualMapState;
import com.swirlds.virtualmap.internal.merkle.VirtualRootNode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag(TIMING_SENSITIVE)
class MerkleDbSnapshotTest {

    private static final int MAPS_COUNT = 3;
    private static final int ITERATIONS = 20;
    private static final int ROUND_CHANGES = 1000;

    private static final Random RANDOM = new Random(123);

    @BeforeAll
    static void setup() throws Exception {
        ConstructableRegistry.getInstance().registerConstructables("com.swirlds.common");
        ConstructableRegistry.getInstance().registerConstructables("com.swirlds.merkledb");
        ConstructableRegistry.getInstance().registerConstructables("com.swirlds.virtualmap");
    }

    @BeforeEach
    void setupTest() throws Exception {
        MerkleDb.setDefaultPath(TemporaryFileBuilder.buildTemporaryDirectory("MerkleDbSnapshotTest"));
    }

    @AfterEach
    public void afterTest() {
        // check db count
        AssertionUtils.assertEventuallyEquals(
                0L,
                MerkleDbDataSource::getCountOfOpenDatabases,
                Duration.ofSeconds(1),
                "Expected no open dbs. Actual number of open dbs: " + MerkleDbDataSource.getCountOfOpenDatabases());
    }

    private static MerkleDbTableConfig<ExampleLongKeyFixedSize, ExampleFixedSizeVirtualValue> fixedConfig() {
        final KeySerializer<ExampleLongKeyFixedSize> keySerializer = new ExampleLongKeyFixedSize.Serializer();
        final ValueSerializer<ExampleFixedSizeVirtualValue> valueSerializer =
                new ExampleFixedSizeVirtualValueSerializer();
        return new MerkleDbTableConfig<>(
                (short) 1, DigestType.SHA_384,
                (short) keySerializer.getCurrentDataVersion(), keySerializer,
                (short) valueSerializer.getCurrentDataVersion(), valueSerializer);
    }

    private void verify(final MerkleInternal stateRoot) {
        for (int i = 0; i < MAPS_COUNT; i++) {
            final VirtualMap<ExampleLongKeyFixedSize, ExampleFixedSizeVirtualValue> vm = stateRoot.getChild(i);
            final VirtualMapState state = vm.getLeft();
            System.out.println("state.getFirstLeafPath() = " + state.getFirstLeafPath());
            System.out.println("state.getLastLeafPath() = " + state.getLastLeafPath());
            final VirtualRootNode<ExampleLongKeyFixedSize, ExampleFixedSizeVirtualValue> root = vm.getRight();
            for (int path = 0; path <= state.getLastLeafPath(); path++) {
                final Hash hash = root.getRecords().findHash(path);
                Assertions.assertNotNull(hash);
            }
        }
    }

    @Test
    void snapshotMultipleTablesTestSync() throws Exception {
        final MerkleInternal initialRoot = new TestInternalNode();
        final MerkleDbTableConfig<ExampleLongKeyFixedSize, ExampleFixedSizeVirtualValue> tableConfig = fixedConfig();
        final MerkleDbDataSourceBuilder<ExampleLongKeyFixedSize, ExampleFixedSizeVirtualValue> dsBuilder =
                new MerkleDbDataSourceBuilder<>(tableConfig);
        for (int i = 0; i < MAPS_COUNT; i++) {
            final VirtualMap<ExampleLongKeyFixedSize, ExampleFixedSizeVirtualValue> vm =
                    new VirtualMap<>("vm" + i, dsBuilder);
            registerMetrics(vm);
            initialRoot.setChild(i, vm);
        }

        final Path snapshotDir = TemporaryFileBuilder.buildTemporaryDirectory("snapshotSync");
        final Path snapshotFile = snapshotDir.resolve("state.swh");

        final AtomicReference<MerkleInternal> lastRoot = new AtomicReference<>();
        MerkleInternal stateRoot = initialRoot;
        long keyId = 0;
        for (int j = 0; j < ITERATIONS; j++) {
            final MerkleInternal newStateRoot = stateRoot.copy();
            for (int i = 0; i < MAPS_COUNT; i++) {
                final VirtualMap<ExampleLongKeyFixedSize, ExampleFixedSizeVirtualValue> vm = newStateRoot.getChild(i);
                final VirtualRootNode<ExampleLongKeyFixedSize, ExampleFixedSizeVirtualValue> root = vm.getRight();
                root.enableFlush();
                for (int k = 0; k < ROUND_CHANGES; k++) {
                    final ExampleLongKeyFixedSize key = new ExampleLongKeyFixedSize(keyId++);
                    final ExampleFixedSizeVirtualValue value = new ExampleFixedSizeVirtualValue(RANDOM.nextInt());
                    vm.put(key, value);
                }
            }
            if (j == ITERATIONS / 2) {
                MerkleCryptoFactory.getInstance().digestTreeSync(stateRoot);
                final MerkleDataOutputStream out = new MerkleDataOutputStream(
                        Files.newOutputStream(snapshotFile, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE));
                out.writeMerkleTree(snapshotDir, stateRoot);
            }
            stateRoot.release();
            stateRoot = newStateRoot;
        }
        lastRoot.set(stateRoot);

        MerkleDb.resetDefaultInstancePath();
        final MerkleDataInputStream in =
                new MerkleDataInputStream(Files.newInputStream(snapshotFile, StandardOpenOption.READ));
        final MerkleInternal restoredStateRoot = in.readMerkleTree(snapshotDir, Integer.MAX_VALUE);

        verify(restoredStateRoot);

        lastRoot.get().release();
        restoredStateRoot.release();
        closeDataSources(initialRoot);
        closeDataSources(lastRoot.get());
        closeDataSources(restoredStateRoot);
    }

    @Test
    void snapshotMultipleTablesTestAsync() throws Exception {
        final MerkleInternal initialRoot = new TestInternalNode();
        final MerkleDbTableConfig<ExampleLongKeyFixedSize, ExampleFixedSizeVirtualValue> tableConfig = fixedConfig();
        final MerkleDbDataSourceBuilder<ExampleLongKeyFixedSize, ExampleFixedSizeVirtualValue> dsBuilder =
                new MerkleDbDataSourceBuilder<>(tableConfig);
        for (int i = 0; i < MAPS_COUNT; i++) {
            final VirtualMap<ExampleLongKeyFixedSize, ExampleFixedSizeVirtualValue> vm =
                    new VirtualMap<>("vm" + i, dsBuilder);
            initialRoot.setChild(i, vm);
        }

        final AtomicReference<MerkleInternal> rootToSnapshot = new AtomicReference<>();
        final AtomicReference<MerkleInternal> lastRoot = new AtomicReference<>();
        final CountDownLatch startSnapshotLatch = new CountDownLatch(1);
        new Thread(() -> {
                    MerkleInternal stateRoot = initialRoot;
                    long keyId = 0;
                    for (int j = 0; j < ITERATIONS; j++) {
                        final MerkleInternal newStateRoot = stateRoot.copy();
                        for (int i = 0; i < MAPS_COUNT; i++) {
                            final VirtualMap<ExampleLongKeyFixedSize, ExampleFixedSizeVirtualValue> vm =
                                    newStateRoot.getChild(i);
                            final VirtualRootNode<ExampleLongKeyFixedSize, ExampleFixedSizeVirtualValue> root =
                                    vm.getRight();
                            root.enableFlush();
                            for (int k = 0; k < ROUND_CHANGES; k++) {
                                final ExampleLongKeyFixedSize key = new ExampleLongKeyFixedSize(keyId++);
                                final ExampleFixedSizeVirtualValue value =
                                        new ExampleFixedSizeVirtualValue(RANDOM.nextInt());
                                vm.put(key, value);
                            }
                        }
                        if (j == ITERATIONS / 2) {
                            rootToSnapshot.set(stateRoot);
                            startSnapshotLatch.countDown();
                        } else {
                            stateRoot.release();
                        }
                        stateRoot = newStateRoot;
                    }
                    lastRoot.set(stateRoot);
                })
                .start();

        startSnapshotLatch.await();
        assertEventuallyTrue(() -> lastRoot.get() != null, Duration.ofSeconds(10), "lastRoot is null");

        MerkleCryptoFactory.getInstance().digestTreeSync(rootToSnapshot.get());
        final Path snapshotDir = TemporaryFileBuilder.buildTemporaryDirectory("snapshotAsync");
        final Path snapshotFile = snapshotDir.resolve("state.swh");
        final MerkleDataOutputStream out = new MerkleDataOutputStream(
                Files.newOutputStream(snapshotFile, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE));
        out.writeMerkleTree(snapshotDir, rootToSnapshot.get());
        rootToSnapshot.get().release();

        MerkleDb.resetDefaultInstancePath();
        final MerkleDataInputStream in =
                new MerkleDataInputStream(Files.newInputStream(snapshotFile, StandardOpenOption.READ));
        final MerkleInternal restoredStateRoot = in.readMerkleTree(snapshotDir, Integer.MAX_VALUE);

        verify(restoredStateRoot);

        lastRoot.get().release();
        restoredStateRoot.release();
        closeDataSources(initialRoot);
        closeDataSources(restoredStateRoot);
    }

    private static void closeDataSources(MerkleInternal initialRoot) throws IOException {
        for (int i = 0; i < MAPS_COUNT; i++) {
            ((VirtualMap<?, ?>) initialRoot.getChild(i)).getDataSource().close();
        }
    }

    /*
     * This test simulates the following scenario. First, a signed state for round N is selected
     * to be flushed to disk (periodic snapshot). Before it's done, the node is disconnected from
     * network and starts a reconnect. Reconnect is successful for a different round M (M > N),
     * and snapshot for round M is written to disk. Now the node has all signatures for the old
     * round N, and that old signed state is finally written to disk.
     */
    @Test
    void testSnapshotAfterReconnect() throws Exception {
        final MerkleDbTableConfig<ExampleLongKeyFixedSize, ExampleFixedSizeVirtualValue> tableConfig = fixedConfig();
        final MerkleDbDataSourceBuilder<ExampleLongKeyFixedSize, ExampleFixedSizeVirtualValue> dsBuilder =
                new MerkleDbDataSourceBuilder<>(tableConfig);
        final VirtualDataSource<ExampleLongKeyFixedSize, ExampleFixedSizeVirtualValue> original =
                dsBuilder.build("vm", false);
        // Simulate reconnect as a learner
        final VirtualDataSource<ExampleLongKeyFixedSize, ExampleFixedSizeVirtualValue> copy =
                dsBuilder.copy(original, true);

        try {
            final Path snapshotDir = TemporaryFileBuilder.buildTemporaryDirectory("snapshot");
            dsBuilder.snapshot(snapshotDir, copy);

            final Path oldSnapshotDir = TemporaryFileBuilder.buildTemporaryDirectory("oldSnapshot");
            assertDoesNotThrow(() -> dsBuilder.snapshot(oldSnapshotDir, original));
        } finally {
            original.close();
            copy.close();
        }
    }

    private static void registerMetrics(VirtualMap<ExampleLongKeyFixedSize, ExampleFixedSizeVirtualValue> vm) {
        final Configuration configuration = new TestConfigBuilder().getOrCreateConfig();
        MetricsConfig metricsConfig = configuration.getConfigData(MetricsConfig.class);
        final MetricKeyRegistry registry = mock(MetricKeyRegistry.class);
        when(registry.register(any(), any(), any())).thenReturn(true);
        Metrics metrics = new DefaultMetrics(
                null,
                registry,
                mock(ScheduledExecutorService.class),
                new DefaultMetricsFactory(metricsConfig),
                metricsConfig);
        MerkleDbStatistics statistics =
                new MerkleDbStatistics(configuration.getConfigData(MerkleDbConfig.class), "test");
        statistics.registerMetrics(metrics);
        vm.getDataSource().registerMetrics(metrics);
    }

    public static class TestInternalNode extends PartialNaryMerkleInternal implements MerkleInternal {

        public TestInternalNode() {}

        public TestInternalNode(final TestInternalNode that) {
            for (int i = 0; i < that.getNumberOfChildren(); i++) {
                setChild(i, that.getChild(i).copy());
            }
        }

        @Override
        public long getClassId() {
            return 1357924680L;
        }

        @Override
        public int getVersion() {
            return 1;
        }

        @Override
        public MerkleInternal copy() {
            return new TestInternalNode(this);
        }
    }
}
