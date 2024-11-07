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

package com.swirlds.virtual.merkle.reconnect;

import static com.swirlds.common.test.fixtures.io.ResourceLoader.loadLog4jContext;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.swirlds.common.constructable.ClassConstructorPair;
import com.swirlds.common.constructable.ConstructableRegistry;
import com.swirlds.common.constructable.ConstructableRegistryException;
import com.swirlds.common.crypto.DigestType;
import com.swirlds.common.io.utility.LegacyTemporaryFileBuilder;
import com.swirlds.common.merkle.MerkleInternal;
import com.swirlds.common.merkle.MerkleNode;
import com.swirlds.common.merkle.synchronization.config.ReconnectConfig;
import com.swirlds.common.merkle.synchronization.config.ReconnectConfig_;
import com.swirlds.common.merkle.synchronization.task.Lesson;
import com.swirlds.common.merkle.synchronization.task.QueryResponse;
import com.swirlds.common.test.fixtures.merkle.dummy.DummyMerkleInternal;
import com.swirlds.common.test.fixtures.merkle.dummy.DummyMerkleLeaf;
import com.swirlds.common.test.fixtures.merkle.util.MerkleTestUtils;
import com.swirlds.config.extensions.test.fixtures.TestConfigBuilder;
import com.swirlds.merkle.test.fixtures.FakeVirtualMap;
import com.swirlds.merkledb.MerkleDb;
import com.swirlds.merkledb.MerkleDbDataSourceBuilder;
import com.swirlds.merkledb.MerkleDbTableConfig;
import com.swirlds.virtual.merkle.TestKey;
import com.swirlds.virtual.merkle.TestKeySerializer;
import com.swirlds.virtual.merkle.TestValue;
import com.swirlds.virtual.merkle.TestValueSerializer;
import com.swirlds.virtualmap.VirtualMap;
import com.swirlds.virtualmap.datasource.VirtualDataSourceBuilder;
import com.swirlds.virtualmap.internal.merkle.VirtualMapState;
import com.swirlds.virtualmap.internal.merkle.VirtualNode;
import com.swirlds.virtualmap.internal.merkle.VirtualRootNode;
import com.swirlds.virtualmap.internal.pipeline.VirtualRoot;
import com.swirlds.virtualmap.serialize.KeySerializer;
import com.swirlds.virtualmap.serialize.ValueSerializer;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Path;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;

public class VirtualMapReconnectTestBase {

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

    // Custom reconnect config to make tests with timeouts faster
    protected static ReconnectConfig reconnectConfig = new TestConfigBuilder()
            .withValue("reconnect.asyncStreamTimeout", "20s")
            .withValue("reconnect.maxAckDelay", "1000ms")
            .getOrCreateConfig()
            .getConfigData(ReconnectConfig.class);

    protected VirtualMap<TestKey, TestValue> teacherMap;
    protected VirtualMap<TestKey, TestValue> learnerMap;
    protected BrokenBuilder teacherBuilder;
    protected BrokenBuilder learnerBuilder;

    VirtualDataSourceBuilder createBuilder() throws IOException {
        // The tests create maps with identical names. They would conflict with each other in the default
        // MerkleDb instance, so let's use a new (temp) database location for every run
        final Path defaultVirtualMapPath = LegacyTemporaryFileBuilder.buildTemporaryFile();
        MerkleDb.setDefaultPath(defaultVirtualMapPath);
        final MerkleDbTableConfig tableConfig = new MerkleDbTableConfig((short) 1, DigestType.SHA_384);
        tableConfig.hashesRamToDiskThreshold(0);
        return new MerkleDbDataSourceBuilder(tableConfig);
    }

    BrokenBuilder createBrokenBuilder(final VirtualDataSourceBuilder delegate) {
        return new BrokenBuilder(delegate);
    }

    @BeforeAll
    public static void setup() throws Exception {
        ConstructableRegistry.getInstance()
                .registerConstructable(new ClassConstructorPair(TestKeySerializer.class, TestKeySerializer::new));
        ConstructableRegistry.getInstance()
                .registerConstructable(new ClassConstructorPair(TestValueSerializer.class, TestValueSerializer::new));
        ConstructableRegistry.getInstance()
                .registerConstructable(new ClassConstructorPair(BrokenBuilder.class, BrokenBuilder::new));
    }

    @BeforeEach
    void setupEach() throws Exception {
        final KeySerializer<TestKey> keySerializer = new TestKeySerializer();
        final ValueSerializer<TestValue> valueSerializer = new TestValueSerializer();
        final VirtualDataSourceBuilder dataSourceBuilder = createBuilder();
        teacherBuilder = createBrokenBuilder(dataSourceBuilder);
        learnerBuilder = createBrokenBuilder(dataSourceBuilder);
        teacherMap = new VirtualMap<>("Teacher", keySerializer, valueSerializer, teacherBuilder);
        learnerMap = new VirtualMap<>("Learner", keySerializer, valueSerializer, learnerBuilder);
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
        registry.registerConstructable(new ClassConstructorPair(Lesson.class, Lesson::new));
        registry.registerConstructable(new ClassConstructorPair(VirtualMap.class, VirtualMap::new));
        registry.registerConstructable(new ClassConstructorPair(VirtualMapState.class, VirtualMapState::new));
        registry.registerConstructable(new ClassConstructorPair(VirtualRootNode.class, VirtualRootNode::new));
        registry.registerConstructable(new ClassConstructorPair(TestKey.class, TestKey::new));
        registry.registerConstructable(new ClassConstructorPair(TestValue.class, TestValue::new));
        registry.registerConstructable(new ClassConstructorPair(FakeVirtualMap.class, FakeVirtualMap::new));

        new TestConfigBuilder()
                .withValue(ReconnectConfig_.ACTIVE, "true")
                // This is lower than the default, helps test that is supposed to fail to finish faster.
                .withValue(ReconnectConfig_.ASYNC_STREAM_TIMEOUT, "5000ms")
                .withValue(ReconnectConfig_.MAX_ACK_DELAY, "1000ms")
                .getOrCreateConfig();
    }

    protected MerkleInternal createTreeForMap(final MerkleNode map) {
        final MerkleInternal tree = MerkleTestUtils.buildLessSimpleTree();
        tree.getChild(1).asInternal().setChild(3, map, null, true);
        tree.reserve();
        return tree;
    }

    protected void reconnect() {
        reconnectMultipleTimes(1);
    }

    protected void reconnectMultipleTimes(final int attempts) {
        reconnectMultipleTimes(attempts, x -> x);
    }

    /**
     * The number of reconnect attempts. Only the last attempt should succeed.
     *
     * @param attempts                the number of times reconnect will be attempted
     * @param brokenTeacherMapBuilder builds the teacher map to be used on all but the last attempt
     */
    protected void reconnectMultipleTimes(
            final int attempts, final Function<VirtualMap<TestKey, TestValue>, MerkleNode> brokenTeacherMapBuilder) {

        // Make sure virtual map data is flushed to disk (data source), otherwise all
        // data for reconnects would be loaded from virtual node cache
        final VirtualRootNode<TestKey, TestValue> virtualRootNode =
                teacherMap.asInternal().getChild(1);
        virtualRootNode.enableFlush();

        final VirtualMap<TestKey, TestValue> teacherCopy = teacherMap.copy();
        teacherMap.release();
        try {
            virtualRootNode.waitUntilFlushed();
        } catch (final InterruptedException z) {
            throw new RuntimeException("Interrupted exception while waiting for virtual map to flush");
        }
        teacherMap = teacherCopy;

        final MerkleInternal teacherTree = createTreeForMap(teacherMap);
        final VirtualMap<TestKey, TestValue> copy = teacherMap.copy();

        final MerkleInternal learnerTree = createTreeForMap(learnerMap);

        try {
            for (int i = 0; i < attempts; i++) {

                final boolean failureExpected = i + 1 < attempts;

                final MerkleInternal brokenTeacherTree = createTreeForMap(brokenTeacherMapBuilder.apply(teacherMap));
                // Hack: undo the reservation from the broken tree. Reconnect test utils expect every node to have
                // a reference count of exactly one, and it's a lot simpler to "fix" the extra reference than to
                // deviate.
                if (teacherMap.getReservationCount() > 1) {
                    teacherMap.release();
                }
                try {
                    final MerkleNode node = MerkleTestUtils.hashAndTestSynchronization(
                            learnerTree, failureExpected ? brokenTeacherTree : teacherTree, reconnectConfig);
                    node.release();
                    assertFalse(failureExpected, "We should only succeed on the last try");
                    final VirtualRoot root = learnerMap.getRight();
                    assertTrue(root.isHashed(), "Learner root node must be hashed");
                } catch (Exception e) {
                    if (!failureExpected) {
                        e.printStackTrace(System.err);
                    }
                    assertTrue(failureExpected, "We did not expect an exception on this reconnect attempt! " + e);
                }

                // Reference counts should not "leak" when a reconnect fails
                teacherTree
                        .treeIterator()
                        .setFilter(node -> !(node instanceof VirtualNode))
                        .setDescendantFilter(node -> !(node instanceof VirtualNode))
                        .forEachRemaining((final MerkleNode node) -> {
                            assertEquals(1, node.getReservationCount(), "unexpected reference count");
                        });
            }
        } finally {
            teacherTree.release();
            learnerTree.release();
            copy.release();
        }
    }
}
