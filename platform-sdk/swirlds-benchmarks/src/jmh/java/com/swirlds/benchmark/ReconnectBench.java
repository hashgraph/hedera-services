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

package com.swirlds.benchmark;

import com.swirlds.benchmark.reconnect.MerkleBenchmarkUtils;
import com.swirlds.benchmark.reconnect.StateBuilder;
import com.swirlds.common.merkle.MerkleInternal;
import com.swirlds.common.merkle.MerkleNode;
import com.swirlds.merkledb.MerkleDb;
import com.swirlds.virtualmap.VirtualKey;
import com.swirlds.virtualmap.VirtualMap;
import com.swirlds.virtualmap.VirtualValue;
import com.swirlds.virtualmap.internal.pipeline.VirtualRoot;
import java.nio.file.Path;
import java.util.Random;
import java.util.function.BiConsumer;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;

@BenchmarkMode(Mode.AverageTime)
@Fork(value = 1)
@Warmup(iterations = 1)
@Measurement(iterations = 7)
public class ReconnectBench extends VirtualMapBaseBench {

    /** A random seed for the StateBuilder. */
    @Param({"9823452658"})
    public long randomSeed;

    /** The probability of the teacher map having an extra node. */
    @Param({"0.05"})
    public double teacherAddProbability;

    /** The probability of the teacher map having removed a node, while the learner still having it. */
    @Param({"0.05"})
    public double teacherRemoveProbability;

    /**
     * The probability of the teacher map having a value under a key that differs
     * from the value under the same key in the learner map.
     */
    @Param({"0.05"})
    public double teacherModifyProbability;

    private VirtualMap<BenchmarkKey, BenchmarkValue> teacherMap;
    private VirtualMap<BenchmarkKey, BenchmarkValue> learnerMap;
    private MerkleInternal teacherTree;
    private VirtualMap<BenchmarkKey, BenchmarkValue> copy;
    private MerkleInternal learnerTree;
    private MerkleNode node;

    private int dbIndex = 0;

    String benchmarkName() {
        return "ReconnectBench";
    }

    @Override
    public void beforeTest(String name) {
        super.beforeTest(name);
        // Use a different MerkleDb instance for every test run. With a single instance,
        // even if its folder is deleted before each run, there could be background
        // threads (virtual pipeline thread, data source compaction thread, etc.) from
        // the previous run that re-create the folder, and it results in a total mess
        final Path merkleDbPath = getTestDir().resolve("merkledb" + dbIndex++);
        MerkleDb.setDefaultPath(merkleDbPath);
    }

    /**
     * Builds a VirtualMap populator that is able to add/update, as well as remove nodes (when the value is null.)
     * Note that it doesn't support explicitly adding null values under a key.
     *
     * @param map a VirtualMap instance
     * @return a populator for the map
     * @param <K> key type
     * @param <V> value type
     */
    private static <K extends VirtualKey, V extends VirtualValue> BiConsumer<K, V> buildVMPopulator(
            final VirtualMap<K, V> map) {
        return (k, v) -> {
            if (v == null) {
                map.remove(k);
            } else {
                map.put(k, v);
            }
        };
    }

    @Setup(Level.Invocation)
    public void setupInvocation() {
        beforeTest("reconnect");

        teacherMap = createEmptyMap("teacher");
        learnerMap = createEmptyMap("learner");

        final Random random = new Random(randomSeed);
        new StateBuilder<>(BenchmarkKey::new, BenchmarkValue::new)
                .buildState(
                        random,
                        numRecords,
                        teacherAddProbability,
                        teacherRemoveProbability,
                        teacherModifyProbability,
                        buildVMPopulator(teacherMap),
                        buildVMPopulator(learnerMap));

        teacherMap = flushMap(teacherMap);
        learnerMap = flushMap(learnerMap);

        teacherTree = MerkleBenchmarkUtils.createTreeForMap(teacherMap);
        copy = teacherMap.copy();
        learnerTree = MerkleBenchmarkUtils.createTreeForMap(learnerMap);
    }

    @TearDown(Level.Invocation)
    public void tearDownInvocation() throws Exception {
        try {
            final VirtualRoot root = learnerMap.getRight();
            if (!root.isHashed()) {
                throw new IllegalStateException("Learner root node must be hashed");
            }
        } finally {
            node.release();
            node = null;
            teacherTree.release();
            teacherTree = null;
            learnerTree.release();
            learnerTree = null;
            copy.release();
            copy = null;
        }

        final var finalTeacherMap = teacherMap;
        final var finalLearnerMap = learnerMap;

        teacherMap = null;
        learnerMap = null;

        afterTest(() -> {
            finalTeacherMap.getDataSource().close();
            finalLearnerMap.getDataSource().close();

            // release()/close() would delete the DB files eventually but not right away.
            // The files/directories can even be re-created in background (see a comment at
            // beforeTest(String name) above.)
            // Add a short sleep to help prevent irrelevant warning messages from being printed
            // when the BaseBench.afterTest() deletes test files recursively right after
            // this current runnable finishes executing.
            try {
                Thread.sleep(1000);
            } catch (InterruptedException ignore) {
            }
        });
    }

    @Benchmark
    public void reconnect() throws Exception {
        node = MerkleBenchmarkUtils.hashAndTestSynchronization(learnerTree, teacherTree, configuration);
    }
}
