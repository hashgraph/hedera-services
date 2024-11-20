/*
 * Copyright (C) 2022-2024 Hedera Hashgraph, LLC
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

import com.swirlds.virtualmap.VirtualMap;
import java.util.ArrayDeque;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

@Fork(value = 1)
@BenchmarkMode(Mode.AverageTime)
@State(Scope.Thread)
@Warmup(iterations = 1)
@Measurement(iterations = 5)
public abstract class VirtualMapBench extends VirtualMapBaseBench {

    String benchmarkName() {
        return "VirtualMapBench";
    }

    /**
     * [Read-update or create-write] cycle. Single-threaded.
     */
    @Benchmark
    public void update() throws Exception {
        beforeTest("update");

        logger.info(RUN_DELIMITER);

        final long[] map = new long[verify ? maxKey : 0];
        VirtualMap<BenchmarkKey, BenchmarkValue> virtualMap = createMap(map);

        if (getBenchmarkConfig().enableSnapshots()) {
            enableSnapshots();
        }

        // Update values
        long start = System.currentTimeMillis();
        for (int i = 0; i < numFiles; i++) {

            for (int j = 0; j < numRecords; ++j) {
                long id = Utils.randomLong(maxKey);
                BenchmarkKey key = new BenchmarkKey(id);
                BenchmarkValue value = virtualMap.getForModify(key);
                long val = nextValue();
                if (value != null) {
                    if ((val & 0xff) == 0) {
                        virtualMap.remove(key);
                        if (verify) map[(int) id] = 0L;
                    } else {
                        value.update(l -> l + val);
                        if (verify) map[(int) id] += val;
                    }
                } else {
                    value = new BenchmarkValue(val);
                    virtualMap.put(key, value);
                    if (verify) map[(int) id] = val;
                }
            }

            virtualMap = copyMap(virtualMap);
        }

        logger.info("Updated {} copies in {} ms", numFiles, System.currentTimeMillis() - start);

        // Ensure the map is done with hashing/merging/flushing
        final var finalMap = flushMap(virtualMap);

        verifyMap(map, finalMap);

        afterTest(() -> {
            finalMap.release();
            finalMap.getDataSource().close();
        });
    }

    /**
     * [Create-write or replace] cycle. Single-threaded.
     */
    @Benchmark
    public void create() throws Exception {
        beforeTest("create");

        logger.info(RUN_DELIMITER);

        final long[] map = new long[verify ? maxKey : 0];
        VirtualMap<BenchmarkKey, BenchmarkValue> virtualMap = createMap(map);

        // Write files
        long start = System.currentTimeMillis();
        for (int i = 0; i < numFiles; i++) {
            for (int j = 0; j < numRecords; ++j) {
                long id = Utils.randomLong(maxKey);
                final BenchmarkKey key = new BenchmarkKey(id);
                final long val = nextValue();
                final BenchmarkValue value = new BenchmarkValue(val);
                virtualMap.put(key, value);
                if (verify) {
                    map[(int) id] = val;
                }
            }

            virtualMap = copyMap(virtualMap);
        }

        logger.info("Created {} copies in {} ms", numFiles, System.currentTimeMillis() - start);

        // Ensure the map is done with hashing/merging/flushing
        final var finalMap = flushMap(virtualMap);

        verifyMap(map, finalMap);

        afterTest(() -> {
            finalMap.release();
            finalMap.getDataSource().close();
        });
    }

    /**
     * [Read-update or create-write][Remove expired] cycle. Single-threaded.
     */
    @Benchmark
    public void delete() throws Exception {
        beforeTest("delete");

        logger.info(RUN_DELIMITER);

        final long[] map = new long[verify ? maxKey : 0];
        VirtualMap<BenchmarkKey, BenchmarkValue> virtualMap = createMap(map);

        final int EXPIRY_DELAY = 180_000;
        record Expirable(long time, long id) {}
        final ArrayDeque<Expirable> expirables = new ArrayDeque<>();

        if (getBenchmarkConfig().enableSnapshots()) {
            enableSnapshots();
        }

        long start = System.currentTimeMillis();
        for (int i = 0; i < numFiles; i++) {
            // Add/update new values
            for (int j = 0; j < numRecords; ++j) {
                final long id = Utils.randomLong(maxKey);
                final BenchmarkKey key = new BenchmarkKey(id);
                BenchmarkValue value = virtualMap.getForModify(key);
                final long val = nextValue();
                if (value != null) {
                    value.update(l -> l + val);
                    if (verify) map[(int) id] += val;
                } else {
                    value = new BenchmarkValue(val);
                    virtualMap.put(key, value);
                    if (verify) map[(int) id] = val;
                }
                expirables.addLast(new Expirable(System.currentTimeMillis() + EXPIRY_DELAY, id));
            }

            // Remove expired values
            final long curTime = System.currentTimeMillis();
            for (; ; ) {
                Expirable entry = expirables.peekFirst();
                if (entry == null || entry.time > curTime) {
                    break;
                }
                virtualMap.remove(new BenchmarkKey(entry.id));
                if (verify) map[(int) entry.id] = 0L;
                expirables.removeFirst();
            }
            logger.info("Copy {} done, map size {}", i, virtualMap.size());

            virtualMap = copyMap(virtualMap);
        }

        logger.info("Updated {} copies in {} ms", numFiles, System.currentTimeMillis() - start);

        // Ensure the map is done with hashing/merging/flushing
        final var finalMap = flushMap(virtualMap);

        verifyMap(map, finalMap);

        afterTest(() -> {
            finalMap.release();
            finalMap.getDataSource().close();
        });
    }

    private void preCreateMap() {
        if (virtualMapP != null) {
            return;
        }
        virtualMapP = createMap();

        long start = System.currentTimeMillis();
        int count = 0;
        for (int i = 0; i < maxKey; i++) {
            BenchmarkKey key = new BenchmarkKey(i);
            BenchmarkValue value = new BenchmarkValue(nextValue());
            virtualMapP.put(key, value);

            if (++count == maxKey / numFiles) {
                count = 0;
                virtualMapP = copyMap(virtualMapP);
            }
        }

        logger.info("Pre-created {} records in {} ms", maxKey, System.currentTimeMillis() - start);

        virtualMapP = flushMap(virtualMapP);
    }

    /**
     * Read from a pre-created map. Parallel.
     */
    @Benchmark
    public void read() throws Exception {
        beforeTest("read");

        logger.info(RUN_DELIMITER);

        preCreateMap();

        final long start = System.currentTimeMillis();
        final AtomicLong total = new AtomicLong(0);
        IntStream.range(0, numThreads).parallel().forEach(thread -> {
            long sum = 0;
            for (int i = 0; i < numRecords; ++i) {
                final long id = Utils.randomLong(maxKey);
                BenchmarkValue value = virtualMapP.get(new BenchmarkKey(id));
                sum += value.hashCode();
            }
            total.addAndGet(sum);
        });

        logger.info(
                "Read {} records from {} threads in {} ms",
                (long) numRecords * numThreads,
                numThreads,
                System.currentTimeMillis() - start);

        afterTest(true);
    }
}
