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

package com.swirlds.virtualmap.internal.cache; // NOSONAR: Needed to benchmark internal classes

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;

@State(Scope.Benchmark)
@Fork(1)
@Warmup(iterations = 1, time = 10)
@Measurement(iterations = 10, time = 15)
public class ConcurrentArrayBench {
    private static final Random RANDOM = new Random();
    private static final int MIN_THREADS = 2;
    private static final int DEFAULT_ARRAY_SIZE = 1000;

    private ConcurrentArray<Long> concurrentArray;
    private ExecutorService executor;

    @Setup(Level.Trial)
    public void setupInfrastructure() {
        int numCores = Runtime.getRuntime().availableProcessors();
        executor = Executors.newFixedThreadPool(Math.max(MIN_THREADS, numCores - 1));
    }

    @TearDown(Level.Trial)
    public void tearDown() {
        executor.shutdownNow();
    }

    @Setup(Level.Iteration)
    public void setupPerIteration() {
        concurrentArray = new ConcurrentArray<>();
        // Add 1000 elements initially to the array.
        for (int i = 0; i < DEFAULT_ARRAY_SIZE; i++) {
            concurrentArray.add(RANDOM.nextLong());
        }
    }

    @Benchmark
    public void benchmarkGet() {
        concurrentArray.get(RANDOM.nextInt(DEFAULT_ARRAY_SIZE));
    }

    @Benchmark
    public void benchmarkSeal() {
        concurrentArray.seal();
    }

    @Benchmark
    public void benchmarkSortedStream() {
        concurrentArray.seal();
        concurrentArray.sortedStream(Long::compareTo);
    }

    @Benchmark
    public void benchmarkParallelTraverse() throws ExecutionException, InterruptedException {
        concurrentArray.seal();
        concurrentArray.parallelTraverse(executor, l -> {}).get();
    }

    @Benchmark
    public void benchmarkConcurrentOps() throws ExecutionException, InterruptedException {
        concurrentArray.seal();
        List<Future<?>> futures = new ArrayList<>();
        futures.add(executor.submit(() -> concurrentArray.sortedStream(Long::compareTo)));
        futures.add(executor.submit(() -> concurrentArray.sortedStream(Long::compareTo)));
        concurrentArray.parallelTraverse(executor, i -> {}).get();
        for (Future<?> f : futures) {
            f.get();
        }
    }

    public ConcurrentArrayBench() {}
}
