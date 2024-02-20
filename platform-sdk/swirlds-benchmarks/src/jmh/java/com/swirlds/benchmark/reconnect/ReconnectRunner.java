/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.swirlds.benchmark.reconnect;

import com.swirlds.benchmark.BenchmarkKey;
import com.swirlds.benchmark.BenchmarkValue;
import com.swirlds.common.merkle.MerkleInternal;
import com.swirlds.common.merkle.MerkleNode;
import com.swirlds.common.merkle.synchronization.config.ReconnectConfig;
import com.swirlds.config.api.Configuration;
import com.swirlds.virtualmap.VirtualMap;
import com.swirlds.virtualmap.internal.pipeline.VirtualRoot;

/**
 * A runner for reconnect tests.
 */
public class ReconnectRunner {
    /**
     * Run reconnect with the given teacher and learner maps.
     *
     * @param teacherMap a teacher map aka a good, desired state
     * @param learnerMap a learner map aka a stale, outdated, "wrong" state
     * @throws Exception thrown if the reconnect code throws any exceptions
     */
    public static void reconnect(
            final Configuration configuration,
            final ReconnectConfig reconnectConfig,
            final VirtualMap<BenchmarkKey, BenchmarkValue> teacherMap,
            final VirtualMap<BenchmarkKey, BenchmarkValue> learnerMap)
            throws Exception {
        final MerkleInternal teacherTree = createTreeForMap(teacherMap);
        final VirtualMap<BenchmarkKey, BenchmarkValue> copy = teacherMap.copy();
        final MerkleInternal learnerTree = createTreeForMap(learnerMap);

        try {
            final MerkleNode node = MerkleBenchmarkUtils.hashAndTestSynchronization(
                    learnerTree, teacherTree, configuration, reconnectConfig);
            node.release();
            final VirtualRoot root = learnerMap.getRight();
            if (!root.isHashed()) {
                throw new IllegalStateException("Learner root node must be hashed");
            }
        } finally {
            teacherTree.release();
            learnerTree.release();
            copy.release();
        }
    }

    private static MerkleInternal createTreeForMap(final VirtualMap<BenchmarkKey, BenchmarkValue> map) {
        final BenchmarkMerkleInternal tree = MerkleBenchmarkUtils.buildLessSimpleTree();
        tree.getChild(1).asInternal().setChild(3, map);
        tree.reserve();
        return tree;
    }
}
