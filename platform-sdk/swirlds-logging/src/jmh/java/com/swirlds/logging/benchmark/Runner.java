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

import com.swirlds.logging.benchmark.swirldslog.SwirldsLogBenchmark;
import org.openjdk.jmh.profile.GCProfiler;
import org.openjdk.jmh.profile.MemPoolProfiler;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

/**
 * Convenience class to run benchmark with profilers from the IDE
 */
public class Runner {

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(SwirldsLogBenchmark.class.getSimpleName())
                // .include(Log4J2Benchmark.class.getSimpleName())
                .param("mode", "ROLLING")
                .param("loggingType", "FILE")
                .addProfiler(GCProfiler.class)
                .addProfiler(MemPoolProfiler.class)
                // .addProfiler(PausesProfiler.class)
                // .addProfiler(StackProfiler.class)
                // .addProfiler(ClassloaderProfiler.class)
                .build();

        new org.openjdk.jmh.runner.Runner(opt).run();
    }
}
