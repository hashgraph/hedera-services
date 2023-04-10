/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

import static com.swirlds.common.threading.manager.ThreadManagerFactory.getStaticThreadManager;

import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.common.threading.framework.config.ThreadConfiguration;
import com.swirlds.virtualmap.VirtualMap;
import com.swirlds.virtualmap.internal.pipeline.VirtualRoot;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;

@Fork(value = 1)
@BenchmarkMode(Mode.AverageTime)
@State(Scope.Thread)
@Warmup(iterations = 1)
@Measurement(iterations = 5)
public abstract class VirtualMapBench extends BaseBench {

    protected static final Logger logger = LogManager.getLogger(VirtualMapBench.class);

    protected static final String LABEL = "vm";
    protected static final String SAVED = "saved";
    protected static final String SERDE = LABEL + ".serde";
    protected static final String SNAPSHOT = "snapshot";
    protected static final long SNAPSHOT_DELAY = 60_000;

    String benchmarkName() {
        return "VirtualMapBench";
    }

    /* This map may be pre-created on demand and reused between benchmarks/iterations */
    private VirtualMap<BenchmarkKey, BenchmarkValue> virtualMapP;

    /* Run snapshots periodically */
    private boolean doSnapshots;
    private final AtomicLong snapshotTime = new AtomicLong(0L);
    /* Asynchronous hasher */
    private final ExecutorService hasher =
            Executors.newSingleThreadExecutor(getStaticThreadManager().newThreadConfiguration()
                    .setComponent("benchmark")
                    .setThreadName("hasher")
                    .setExceptionHandler((t, ex) -> logger.error("Uncaught exception during hashing", ex))
                    .buildFactory());

    @TearDown
    public void destroyLocal() throws IOException {
        if (virtualMapP != null) {
            virtualMapP.release();
            virtualMapP.getDataSource().close();
            virtualMapP = null;
        }
        hasher.shutdown();
    }

    protected VirtualMap<BenchmarkKey, BenchmarkValue> createMap() {
        return createMap(null);
    }

    protected abstract VirtualMap<BenchmarkKey, BenchmarkValue> createEmptyMap();

    protected VirtualMap<BenchmarkKey, BenchmarkValue> createMap(final long[] map) {
        final long start = System.currentTimeMillis();
        VirtualMap<BenchmarkKey, BenchmarkValue> virtualMap = restoreMap();
        if (virtualMap != null) {
            if (verify && map != null) {
                final int parallelism = ForkJoinPool.getCommonPoolParallelism();
                final AtomicLong numKeys = new AtomicLong();
                final VirtualMap<BenchmarkKey, BenchmarkValue> srcMap = virtualMap;
                IntStream.range(0, parallelism).parallel().forEach(idx -> {
                    long count = 0L;
                    for (int i = idx; i < map.length; i += parallelism) {
                        final BenchmarkValue value = srcMap.get(new BenchmarkKey(i));
                        if (value != null) {
                            map[i] = value.toLong();
                            ++count;
                        }
                    }
                    numKeys.addAndGet(count);
                });
                logger.info("Loaded {} keys in {} ms", numKeys, System.currentTimeMillis() - start);
            } else {
                logger.info("Loaded map in {} ms", System.currentTimeMillis() - start);
            }
        } else {
            virtualMap = createEmptyMap();
        }
        BenchmarkMetrics.register(virtualMap::registerMetrics);
        return virtualMap;
    }

    private void enableSnapshots() {
        snapshotTime.set(System.currentTimeMillis() + SNAPSHOT_DELAY);
        doSnapshots = true;
    }

    protected VirtualMap<BenchmarkKey, BenchmarkValue> copyMap(
            final VirtualMap<BenchmarkKey, BenchmarkValue> virtualMap) {
        final VirtualRoot root = virtualMap.getRight();
        final VirtualMap<BenchmarkKey, BenchmarkValue> newCopy = virtualMap.copy();
        hasher.execute(root::getHash);

        if (doSnapshots && System.currentTimeMillis() > snapshotTime.get()) {
            snapshotTime.set(Long.MAX_VALUE);
            new Thread(() -> {
                        try {
                            Path savedDir = getTestDir().resolve(SNAPSHOT);
                            if (!Files.exists(savedDir)) {
                                Files.createDirectory(savedDir);
                            }
                            virtualMap.getRight().getHash();
                            try (final SerializableDataOutputStream out =
                                    new SerializableDataOutputStream(Files.newOutputStream(savedDir.resolve(SERDE)))) {
                                virtualMap.serialize(out, savedDir);
                            }
                            virtualMap.release();
                            Utils.deleteRecursively(savedDir);

                            snapshotTime.set(System.currentTimeMillis() + SNAPSHOT_DELAY);
                        } catch (Exception ex) {
                            logger.error("Failed to take a snapshot", ex);
                        }
                    })
                    .start();
        } else {
            virtualMap.release();
        }

        return newCopy;
    }

    /*
     * Ensure map is fully flushed to disk. Save map to disk if saving data is specified.
     */
    protected VirtualMap<BenchmarkKey, BenchmarkValue> flushMap(
            final VirtualMap<BenchmarkKey, BenchmarkValue> virtualMap) {
        final long start = System.currentTimeMillis();
        VirtualMap<BenchmarkKey, BenchmarkValue> curMap = virtualMap;
        for (; ; ) {
            final VirtualMap<BenchmarkKey, BenchmarkValue> oldCopy = curMap;
            curMap = curMap.copy();
            oldCopy.release();
            final VirtualRoot root = oldCopy.getRight();
            if (root.shouldBeFlushed()) {
                try {
                    root.waitUntilFlushed();
                } catch (InterruptedException ex) {
                    logger.warn("Interrupted", ex);
                    Thread.currentThread().interrupt();
                }
                break;
            }
        }
        logger.info("Flushed map in {} ms", System.currentTimeMillis() - start);

        if (getConfig().saveDataDirectory()) {
            curMap = saveMap(curMap);
        }

        return curMap;
    }

    protected void verifyMap(long[] map, VirtualMap<BenchmarkKey, BenchmarkValue> virtualMap) {
        if (!verify) {
            return;
        }

        long start = System.currentTimeMillis();
        final AtomicInteger index = new AtomicInteger(0);
        final AtomicInteger countGood = new AtomicInteger(0);
        final AtomicInteger countBad = new AtomicInteger(0);
        final AtomicInteger countMissing = new AtomicInteger(0);

        IntStream.range(0, ForkJoinPool.getCommonPoolParallelism()).parallel().forEach(thread -> {
            int idx;
            while ((idx = index.getAndIncrement()) < map.length) {
                BenchmarkValue dataItem = virtualMap.get(new BenchmarkKey(idx));
                if (dataItem == null) {
                    if (map[idx] != 0L) {
                        countMissing.getAndIncrement();
                    }
                } else if (!dataItem.equals(new BenchmarkValue(map[idx]))) {
                    countBad.getAndIncrement();
                } else {
                    countGood.getAndIncrement();
                }
            }
        });
        if (countBad.get() != 0 || countMissing.get() != 0) {
            logger.error(
                    "FAIL verified {} keys, {} bad, {} missing in {} ms",
                    countGood.get(),
                    countBad.get(),
                    countMissing.get(),
                    System.currentTimeMillis() - start);
        } else {
            logger.info("PASS verified {} keys in {} ms", countGood.get(), System.currentTimeMillis() - start);
        }
    }

    protected VirtualMap<BenchmarkKey, BenchmarkValue> saveMap(
            final VirtualMap<BenchmarkKey, BenchmarkValue> virtualMap) {
        final VirtualMap<BenchmarkKey, BenchmarkValue> curMap = virtualMap.copy();
        try {
            final long start = System.currentTimeMillis();
            final Path savedDir = getBenchDir().resolve(SAVED).resolve(LABEL);
            if (Files.exists(savedDir)) {
                Utils.deleteRecursively(savedDir);
            }
            Files.createDirectories(savedDir);
            virtualMap.getRight().getHash();
            try (final SerializableDataOutputStream out =
                    new SerializableDataOutputStream(Files.newOutputStream(savedDir.resolve(SERDE)))) {
                virtualMap.serialize(out, savedDir);
            }
            logger.info("Saved map in {} ms", System.currentTimeMillis() - start);
        } catch (IOException ex) {
            logger.error("Error saving VirtualMap", ex);
        } finally {
            virtualMap.release();
        }
        return curMap;
    }

    protected VirtualMap<BenchmarkKey, BenchmarkValue> restoreMap() {
        final Path savedDir = getBenchDir().resolve(SAVED).resolve(LABEL);
        if (Files.exists(savedDir)) {
            try {
                final VirtualMap<BenchmarkKey, BenchmarkValue> virtualMap = new VirtualMap<>();
                try (final SerializableDataInputStream in =
                        new SerializableDataInputStream(Files.newInputStream(savedDir.resolve(SERDE)))) {
                    virtualMap.deserialize(in, savedDir, virtualMap.getVersion());
                }
                return virtualMap;
            } catch (IOException ex) {
                logger.error("Error loading saved map: {}", ex.getMessage());
            }
        }
        return null;
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

        // Update values
        long start = System.currentTimeMillis();
        enableSnapshots();
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

        long start = System.currentTimeMillis();
        enableSnapshots();
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
