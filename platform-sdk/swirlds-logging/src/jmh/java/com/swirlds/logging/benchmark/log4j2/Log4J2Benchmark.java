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

package com.swirlds.logging.benchmark.log4j2;

import static com.swirlds.logging.benchmark.config.Constants.FORK_COUNT;
import static com.swirlds.logging.benchmark.config.Constants.MEASUREMENT_ITERATIONS;
import static com.swirlds.logging.benchmark.config.Constants.MEASUREMENT_TIME_IN_SECONDS_PER_ITERATION;
import static com.swirlds.logging.benchmark.config.Constants.PARALLEL_THREAD_COUNT;
import static com.swirlds.logging.benchmark.config.Constants.WARMUP_ITERATIONS;
import static com.swirlds.logging.benchmark.config.Constants.WARMUP_TIME_IN_SECONDS_PER_ITERATION;

import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;

public class Log4J2Benchmark extends Log4J2BaseBenchmark {

    @Benchmark
    @Fork(value = FORK_COUNT)
    @Threads(PARALLEL_THREAD_COUNT)
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    @Warmup(
            iterations = WARMUP_ITERATIONS,
            time = WARMUP_TIME_IN_SECONDS_PER_ITERATION,
            timeUnit = TimeUnit.MILLISECONDS)
    @Measurement(
            iterations = MEASUREMENT_ITERATIONS,
            time = MEASUREMENT_TIME_IN_SECONDS_PER_ITERATION,
            timeUnit = TimeUnit.MILLISECONDS)
    public void log4J() {
        logRunner.run();
    }
}
