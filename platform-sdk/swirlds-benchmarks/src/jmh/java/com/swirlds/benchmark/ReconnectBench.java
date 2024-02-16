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

import com.swirlds.benchmark.reconnect.ReconnectRunner;
import com.swirlds.benchmark.reconnect.StateBuilder;
import com.swirlds.common.crypto.DigestType;
import com.swirlds.merkledb.MerkleDb;
import com.swirlds.merkledb.MerkleDbDataSourceBuilder;
import com.swirlds.merkledb.MerkleDbTableConfig;
import com.swirlds.virtualmap.VirtualKey;
import com.swirlds.virtualmap.VirtualMap;
import com.swirlds.virtualmap.VirtualValue;
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

import java.nio.file.Path;
import java.util.Random;
import java.util.function.BiConsumer;

@BenchmarkMode(Mode.AverageTime)
@Fork(value = 1)
@Warmup(iterations = 1)
@Measurement(iterations = 5)
public class ReconnectBench extends VirtualMapBaseBench {

    /** A random seed for the StateBuilder. */
    @Param({"9823452658"})
    public long randomSeed;

    /**
     * The size of the state, aka the number of nodes in the learner virtual map.
     * The teacher map may have a slightly different number of nodes depending on
     * the {@code teacherAddProbability} and {@code teacherRemoveProbability} values.
     */
    @Param({"5000000"})
    public long stateSize;

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
                        stateSize,
                        teacherAddProbability,
                        teacherRemoveProbability,
                        teacherModifyProbability,
                        buildVMPopulator(teacherMap),
                        buildVMPopulator(learnerMap));

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
