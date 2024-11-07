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
import com.swirlds.virtualmap.VirtualKey;
import com.swirlds.virtualmap.VirtualMap;
import com.swirlds.virtualmap.VirtualValue;
import com.swirlds.virtualmap.internal.pipeline.VirtualRoot;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicReference;
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

    /** Number of virtual maps in the merkle tree to reconnect. */
    @Param({"1"})
    public int mapCount = 1;

    /** The probability of the teacher map having an extra node. */
    @Param({"0.05"})
    public double teacherAddProbability = 0.05;

    /** The probability of the teacher map having removed a node, while the learner still having it. */
    @Param({"0.05"})
    public double teacherRemoveProbability = 0.05;

    /**
     * The probability of the teacher map having a value under a key that differs
     * from the value under the same key in the learner map.
     */
    @Param({"0.05"})
    public double teacherModifyProbability = 0.05;

    /**
     * Emulated delay for sendAsync() calls in both Teaching- and Learning-Synchronizers,
     * or zero for no delay. This emulates slow disk I/O when reading data.
     */
    @Param({"0"})
    public long delayStorageMicroseconds = 0;

    /**
     * A percentage fuzz range for the delayStorageMicroseconds values,
     * e.g. 0.15 for a -15%..+15% range around the value.
     */
    @Param({"0.15"})
    public double delayStorageFuzzRangePercent = 0.15;

    /**
     * Emulated delay for serializeMessage() calls in both Teaching- and Learning-Synchronizers,
     * or zero for no delay. This emulates slow network I/O when sending data.
     */
    @Param({"0"})
    public long delayNetworkMicroseconds = 0;

    /**
     * A percentage fuzz range for the delayNetworkMicroseconds values,
     * e.g. 0.15 for a -15%..+15% range around the value.
     */
    @Param({"0.15"})
    public double delayNetworkFuzzRangePercent = 0.15;

    private List<VirtualMap<BenchmarkKey, BenchmarkValue>> teacherMaps;
    private List<VirtualMap<BenchmarkKey, BenchmarkValue>> learnerMaps;

    private MerkleInternal teacherTree;
    private List<VirtualMap<BenchmarkKey, BenchmarkValue>> teacherMapCopies;

    private MerkleInternal learnerTree;

    private MerkleNode reconnectedTree;

    String benchmarkName() {
        return "ReconnectBench";
    }

    /**
     * Builds a VirtualMap populator that is able to add/update, as well as remove nodes (when the value is null.)
     * Note that it doesn't support explicitly adding null values under a key.
     *
     * @param mapRef a reference to a VirtualMap instance
     * @return a populator for the map
     * @param <K> key type
     * @param <V> value type
     */
    private static <K extends VirtualKey, V extends VirtualValue> BiConsumer<K, V> buildVMPopulator(
            final AtomicReference<VirtualMap<K, V>> mapRef) {
        return (k, v) -> {
            if (v == null) {
                mapRef.get().remove(k);
            } else {
                mapRef.get().put(k, v);
            }
        };
    }

    /** Generate a state and save it to disk once for the entire benchmark. */
    @Setup
    public void setupBenchmark() {
        beforeTest("reconnect");
        updateMerkleDbPath();

        final Random random = new Random(randomSeed);

        final List<VirtualMap<BenchmarkKey, BenchmarkValue>> maps = new ArrayList<>();

        for (int mapIndex = 0; mapIndex < mapCount; mapIndex++) {
            final AtomicReference<VirtualMap<BenchmarkKey, BenchmarkValue>> teacherRef =
                    new AtomicReference<>(createEmptyMap("teacher" + mapIndex));
            final AtomicReference<VirtualMap<BenchmarkKey, BenchmarkValue>> learnerRef =
                    new AtomicReference<>(createEmptyMap("learner" + mapIndex));

            // Create some larger maps (100% size) and some smaller ones (40% size)
            final long size = (mapIndex % 2 == 0) ? (long) numFiles * numRecords : (long) numFiles * numRecords * 2 / 5;
            new StateBuilder<>(BenchmarkKey::new, BenchmarkValue::new)
                    .buildState(
                            random,
                            size,
                            teacherAddProbability,
                            teacherRemoveProbability,
                            teacherModifyProbability,
                            buildVMPopulator(teacherRef),
                            buildVMPopulator(learnerRef),
                            i -> {
                                if (i % numRecords == 0) {
                                    System.err.printf("Copying files for i=%,d\n", i);
                                    teacherRef.set(copyMap(teacherRef.get()));
                                    learnerRef.set(copyMap(learnerRef.get()));
                                }
                            });

            teacherRef.set(flushMap(teacherRef.get()));
            learnerRef.set(flushMap(learnerRef.get()));

            maps.add(teacherRef.get());
            maps.add(learnerRef.get());
        }

        final List<VirtualMap<BenchmarkKey, BenchmarkValue>> mapCopies = saveMaps(maps);
        mapCopies.forEach(this::releaseAndCloseMap);
    }

    /** Restore the saved state from disk as a new test on-disk copy for each iteration. */
    @Setup(Level.Invocation)
    public void setupInvocation() {
        updateMerkleDbPath();

        teacherMaps = new ArrayList<>(mapCount);
        learnerMaps = new ArrayList<>(mapCount);
        teacherMapCopies = new ArrayList<>(mapCount);
        for (int mapIndex = 0; mapIndex < mapCount; mapIndex++) {
            VirtualMap<BenchmarkKey, BenchmarkValue> teacherMap = restoreMap("teacher" + mapIndex);
            if (teacherMap == null) {
                throw new RuntimeException("Failed to restore the 'teacher' map #" + mapIndex);
            }
            teacherMap = flushMap(teacherMap);
            BenchmarkMetrics.register(teacherMap::registerMetrics);
            teacherMaps.add(teacherMap);

            VirtualMap<BenchmarkKey, BenchmarkValue> learnerMap = restoreMap("learner" + mapIndex);
            if (learnerMap == null) {
                throw new RuntimeException("Failed to restore the 'learner' map #" + mapIndex);
            }
            learnerMap = flushMap(learnerMap);
            BenchmarkMetrics.register(learnerMap::registerMetrics);
            learnerMaps.add(learnerMap);
        }

        teacherTree = MerkleBenchmarkUtils.createTreeForMaps(teacherMaps);
        learnerTree = MerkleBenchmarkUtils.createTreeForMaps(learnerMaps);

        for (final VirtualMap<BenchmarkKey, BenchmarkValue> teacherMap : teacherMaps) {
            teacherMapCopies.add(teacherMap.copy());
        }
    }

    @TearDown(Level.Invocation)
    public void tearDownInvocation() throws Exception {
        try {
            if (verify) {
                if (!MerkleBenchmarkUtils.areTreesEqual(reconnectedTree, teacherTree)) {
                    throw new IllegalStateException("Trees aren't equal after reconnect");
                }
            }
            for (final VirtualMap<BenchmarkKey, BenchmarkValue> learnerMap : learnerMaps) {
                final VirtualRoot root = learnerMap.getRight();
                if (!root.isHashed()) {
                    throw new IllegalStateException("Learner root node must be hashed");
                }
            }
        } finally {
            reconnectedTree.release();
            reconnectedTree = null;
            teacherTree.release();
            teacherTree = null;
            learnerTree.release();
            learnerTree = null;
            for (final VirtualMap<BenchmarkKey, BenchmarkValue> teacherMapCopy : teacherMapCopies) {
                teacherMapCopy.release();
            }
        }

        afterTest(() -> {
            // Close all data sources
            for (int mapIndex = 0; mapIndex < mapCount; mapIndex++) {
                teacherMaps.get(mapIndex).getDataSource().close();
                learnerMaps.get(mapIndex).getDataSource().close();
            }

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

        teacherMaps = null;
        learnerMaps = null;
    }

    @Benchmark
    public void reconnect() throws Exception {
        reconnectedTree = MerkleBenchmarkUtils.hashAndTestSynchronization(
                learnerTree,
                teacherTree,
                randomSeed,
                delayStorageMicroseconds,
                delayStorageFuzzRangePercent,
                delayNetworkMicroseconds,
                delayNetworkFuzzRangePercent,
                configuration);
    }

    public static void main(String[] args) throws Exception {
        ReconnectBench bench = new ReconnectBench();
        bench.setup();
        bench.setupBenchmark();
        bench.beforeTest();
        bench.setupInvocation();
        bench.reconnect();
        bench.tearDownInvocation();
        bench.afterTest();
        bench.destroyLocal();
        bench.destroy();
    }
}
