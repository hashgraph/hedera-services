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

package com.swirlds.logging.benchmark;

import static com.swirlds.logging.benchmark.config.LoggingHandlingType.CONSOLE_AND_FILE_TYPE;
import static com.swirlds.logging.benchmark.config.LoggingHandlingType.CONSOLE_TYPE;
import static com.swirlds.logging.benchmark.config.LoggingHandlingType.FILE_TYPE;
import static com.swirlds.logging.benchmark.util.BenchmarkConstants.FORK_COUNT;
import static com.swirlds.logging.benchmark.util.BenchmarkConstants.MEASUREMENT_ITERATIONS;
import static com.swirlds.logging.benchmark.util.BenchmarkConstants.MEASUREMENT_TIME_IN_SECONDS_PER_ITERATION;
import static com.swirlds.logging.benchmark.util.BenchmarkConstants.PARALLEL_THREAD_COUNT;
import static com.swirlds.logging.benchmark.util.BenchmarkConstants.WARMUP_ITERATIONS;
import static com.swirlds.logging.benchmark.util.BenchmarkConstants.WARMUP_TIME_IN_SECONDS_PER_ITERATION;

import com.swirlds.logging.api.Logger;
import java.util.Objects;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;

@State(Scope.Benchmark)
public class SwirldsLogBenchmark {

    @Param({CONSOLE_TYPE, FILE_TYPE, CONSOLE_AND_FILE_TYPE})
    public String loggingType;

    Logger logger;

    LogWithSwirlds logRunner;

    @Setup(Level.Trial)
    public void init() throws Exception {
        if (Objects.equals(loggingType, FILE_TYPE)) {
            logger = ConfigureSwirldsLog.configureFileLogging().getLogger("SwirldsLogBenchmark");
        } else if (Objects.equals(loggingType, CONSOLE_TYPE)) {
            logger = ConfigureSwirldsLog.configureConsoleLogging().getLogger("SwirldsLogBenchmark");
        } else if (Objects.equals(loggingType, CONSOLE_AND_FILE_TYPE)) {
            logger = ConfigureSwirldsLog.configureFileAndConsoleLogging().getLogger("SwirldsLogBenchmark");
        }
        logRunner = new LogWithSwirlds(logger);
    }

    @Benchmark
    @Fork(FORK_COUNT)
    @Threads(PARALLEL_THREAD_COUNT)
    @BenchmarkMode(Mode.Throughput)
    @Warmup(iterations = WARMUP_ITERATIONS, time = WARMUP_TIME_IN_SECONDS_PER_ITERATION)
    @Measurement(iterations = MEASUREMENT_ITERATIONS, time = MEASUREMENT_TIME_IN_SECONDS_PER_ITERATION)
    public void swirldsLogging() {
        logRunner.run();
    }
}
