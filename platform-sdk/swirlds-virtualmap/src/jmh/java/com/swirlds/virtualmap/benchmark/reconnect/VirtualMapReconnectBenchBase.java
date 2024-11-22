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

package com.swirlds.virtualmap.benchmark.reconnect;

import static com.swirlds.common.test.fixtures.io.ResourceLoader.loadLog4jContext;
import static com.swirlds.virtualmap.test.fixtures.VirtualMapTestUtils.CONFIGURATION;

import com.swirlds.common.constructable.ClassConstructorPair;
import com.swirlds.common.constructable.ConstructableRegistry;
import com.swirlds.common.constructable.ConstructableRegistryException;
import com.swirlds.common.merkle.MerkleInternal;
import com.swirlds.common.merkle.synchronization.config.ReconnectConfig;
import com.swirlds.common.merkle.synchronization.config.ReconnectConfig_;
import com.swirlds.common.merkle.synchronization.task.QueryResponse;
import com.swirlds.common.test.fixtures.merkle.dummy.DummyMerkleInternal;
import com.swirlds.common.test.fixtures.merkle.dummy.DummyMerkleLeaf;
import com.swirlds.common.test.fixtures.merkle.util.MerkleTestUtils;
import com.swirlds.config.extensions.test.fixtures.TestConfigBuilder;
import com.swirlds.virtualmap.VirtualMap;
import com.swirlds.virtualmap.config.VirtualMapConfig;
import com.swirlds.virtualmap.datasource.VirtualDataSourceBuilder;
import com.swirlds.virtualmap.internal.merkle.VirtualMapState;
import com.swirlds.virtualmap.internal.merkle.VirtualRootNode;
import com.swirlds.virtualmap.internal.pipeline.VirtualRoot;
import com.swirlds.virtualmap.test.fixtures.InMemoryBuilder;
import com.swirlds.virtualmap.test.fixtures.TestKey;
import com.swirlds.virtualmap.test.fixtures.TestValue;
import java.io.FileNotFoundException;
import org.junit.jupiter.api.Assertions;

/**
 * The code is partially borrowed from VirtualMapReconnectTestBase.java in swirlds-virtualmap/src/test/.
 * Ideally, it belongs to a shared test fixture, but I was unable to find a way to resolve dependencies
 * between projects and modules, so I created this copy here and removed a few static definitions that
 * are irrelevant to JMH benchmarks. In the future, this JMH-specific copy may in fact diverge
 * from the unit test base class if/when we implement performance testing-related features here
 * (e.g. artificial latencies etc.)
 */
public abstract class VirtualMapReconnectBenchBase {

    protected VirtualMap teacherMap;
    protected VirtualMap learnerMap;
    protected VirtualDataSourceBuilder teacherBuilder;
    protected VirtualDataSourceBuilder learnerBuilder;

    protected final ReconnectConfig reconnectConfig = new TestConfigBuilder()
            // This is lower than the default, helps test that is supposed to fail to finish faster.
            .withValue(ReconnectConfig_.ASYNC_STREAM_TIMEOUT, "5s")
            .withValue(ReconnectConfig_.MAX_ACK_DELAY, "1000ms")
            .getOrCreateConfig()
            .getConfigData(ReconnectConfig.class);

    protected VirtualDataSourceBuilder createBuilder() {
        return new InMemoryBuilder();
    }

    protected void setupEach() {
        teacherBuilder = createBuilder();
        learnerBuilder = createBuilder();
        teacherMap = new VirtualMap("Teacher", teacherBuilder, CONFIGURATION);
        learnerMap = new VirtualMap("Learner", learnerBuilder, CONFIGURATION);
    }

    protected static void startup() throws ConstructableRegistryException, FileNotFoundException {
        loadLog4jContext();
        final ConstructableRegistry registry = ConstructableRegistry.getInstance();
        registry.registerConstructables("com.swirlds.common");
        registry.registerConstructables("com.swirlds.virtualmap");
        registry.registerConstructable(new ClassConstructorPair(QueryResponse.class, QueryResponse::new));
        registry.registerConstructable(new ClassConstructorPair(DummyMerkleInternal.class, DummyMerkleInternal::new));
        registry.registerConstructable(new ClassConstructorPair(DummyMerkleLeaf.class, DummyMerkleLeaf::new));
        registry.registerConstructable(new ClassConstructorPair(VirtualMap.class, () -> new VirtualMap(CONFIGURATION)));
        registry.registerConstructable(new ClassConstructorPair(VirtualMapState.class, VirtualMapState::new));
        registry.registerConstructable(new ClassConstructorPair(
                VirtualRootNode.class, () -> new VirtualRootNode(CONFIGURATION.getConfigData(VirtualMapConfig.class))));
        registry.registerConstructable(new ClassConstructorPair(TestKey.class, TestKey::new));
        registry.registerConstructable(new ClassConstructorPair(TestValue.class, TestValue::new));
    }

    protected MerkleInternal createTreeForMap(VirtualMap map) {
        final var tree = MerkleTestUtils.buildLessSimpleTree();
        tree.getChild(1).asInternal().setChild(3, map);
        tree.reserve();
        return tree;
    }

    protected void reconnect() throws Exception {
        final MerkleInternal teacherTree = createTreeForMap(teacherMap);
        final VirtualMap copy = teacherMap.copy();
        final MerkleInternal learnerTree = createTreeForMap(learnerMap);
        try {
            final var node = MerkleTestUtils.hashAndTestSynchronization(learnerTree, teacherTree, reconnectConfig);
            node.release();
            final VirtualRoot root = learnerMap.getRight();
            Assertions.assertTrue(root.isHashed(), "Learner root node must be hashed");
        } finally {
            teacherTree.release();
            learnerTree.release();
            copy.release();
        }
    }
}
