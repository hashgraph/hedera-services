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

import com.swirlds.benchmark.reconnect.BenchmarkMerkleInternal;
import com.swirlds.benchmark.reconnect.BenchmarkMerkleLeaf;
import com.swirlds.benchmark.reconnect.ReconnectRunner;
import com.swirlds.benchmark.reconnect.StateBuilder;
import com.swirlds.common.constructable.ClassConstructorPair;
import com.swirlds.common.constructable.ConstructableRegistry;
import com.swirlds.common.crypto.DigestType;
import com.swirlds.common.merkle.synchronization.internal.QueryResponse;
import com.swirlds.merkledb.MerkleDb;
import com.swirlds.merkledb.MerkleDbDataSourceBuilder;
import com.swirlds.merkledb.MerkleDbTableConfig;
import com.swirlds.virtualmap.VirtualMap;
import com.swirlds.virtualmap.datasource.VirtualLeafRecord;
import com.swirlds.virtualmap.internal.merkle.VirtualMapState;
import com.swirlds.virtualmap.internal.merkle.VirtualRootNode;
import java.nio.file.Path;
import java.util.Random;
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
@Measurement(iterations = 3)
public class ReconnectBench extends VirtualMapBaseBench {

    /** A random seed for the StateBuilder. */
    @Param({"9823452658"})
    public long randomSeed;

    /**
     * The size of the state, aka the number of nodes in the teacher virtual map.
     * The learner map will have the same number of nodes, or less than that
     * depending on the {@code learnerMissingProbability} value.
     */
    @Param({"5000000"})
    public long stateSize;

    /** The probability of the learner map missing a key from the teacher map. */
    @Param({"0.05"})
    public double learnerMissingProbability;

    /**
     * The probability of the learner map having a value under a key that differs
     * from the value under the same key in the teacher map.
     */
    @Param({"0.05"})
    public double learnerDifferentProbability;

    private VirtualMap<BenchmarkKey, BenchmarkValue> teacherMap;
    private VirtualMap<BenchmarkKey, BenchmarkValue> learnerMap;

    private int dbIndex = 0;

    String benchmarkName() {
        return "ReconnectBench";
    }

    @Setup
    public static void setupBenchmark() throws Exception {
        final ConstructableRegistry registry = ConstructableRegistry.getInstance();
        registry.registerConstructables("com.swirlds.merkledb");
        registry.registerConstructables("com.swirlds.common");
        registry.registerConstructables("com.swirlds.virtualmap");
        registry.registerConstructable(new ClassConstructorPair(QueryResponse.class, QueryResponse::new));
        registry.registerConstructable(
                new ClassConstructorPair(BenchmarkMerkleInternal.class, BenchmarkMerkleInternal::new));
        registry.registerConstructable(new ClassConstructorPair(BenchmarkMerkleLeaf.class, BenchmarkMerkleLeaf::new));
        registry.registerConstructable(new ClassConstructorPair(VirtualLeafRecord.class, VirtualLeafRecord::new));
        registry.registerConstructable(new ClassConstructorPair(VirtualMap.class, VirtualMap::new));
        registry.registerConstructable(new ClassConstructorPair(VirtualMapState.class, VirtualMapState::new));
        registry.registerConstructable(new ClassConstructorPair(VirtualRootNode.class, VirtualRootNode::new));
        registry.registerConstructable(new ClassConstructorPair(BenchmarkKey.class, BenchmarkKey::new));
        registry.registerConstructable(new ClassConstructorPair(BenchmarkValue.class, BenchmarkValue::new));
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

    @Override
    protected VirtualMap<BenchmarkKey, BenchmarkValue> createEmptyMap() {
        throw new UnsupportedOperationException();
    }

    private VirtualMap<BenchmarkKey, BenchmarkValue> createEmptyMap(String label) {
        MerkleDbTableConfig<BenchmarkKey, BenchmarkValue> tableConfig = new MerkleDbTableConfig<>(
                        (short) 1, DigestType.SHA_384,
                        (short) 1, new BenchmarkKeySerializer(),
                        (short) 1, new BenchmarkValueSerializer())
                .preferDiskIndices(false);
        MerkleDbDataSourceBuilder<BenchmarkKey, BenchmarkValue> dataSourceBuilder =
                new MerkleDbDataSourceBuilder<>(tableConfig);
        return new VirtualMap<>(label, dataSourceBuilder);
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
                        stateSize,
                        learnerMissingProbability,
                        learnerDifferentProbability,
                        teacherMap::put,
                        learnerMap::put);

        teacherMap = flushMap(teacherMap);
        learnerMap = flushMap(learnerMap);
    }

    @TearDown(Level.Invocation)
    public void tearDownInvocation() throws Exception {
        final var finalTeacherMap = teacherMap;
        final var finalLearnerMap = learnerMap;

        teacherMap = null;
        learnerMap = null;

        afterTest(() -> {
            finalTeacherMap.getDataSource().close();
            finalLearnerMap.getDataSource().close();
        });
    }

    @Benchmark
    public void reconnect() throws Exception {
        ReconnectRunner.reconnect(teacherMap, learnerMap);
    }
}
