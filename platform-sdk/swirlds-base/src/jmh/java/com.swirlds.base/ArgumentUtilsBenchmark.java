/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.swirlds.base;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

@State(Scope.Benchmark)
public class ArgumentUtilsBenchmark {

    @Param({"content", "", "         ", "\n\n\n"})
    private String argument;

    @Benchmark
    @Fork(1)
    @Threads(1)
    @BenchmarkMode(Mode.Throughput)
    @Warmup(iterations = 1, time = 1)
    @Measurement(iterations = 1, time = 1)
    public void throwArgBlank(Blackhole blackhole) {
        try {
            String arg = ArgumentUtils.throwArgBlank(argument, "argument");
            blackhole.consume(arg);
        } catch (Exception e) {
            blackhole.consume(e);
        }
    }
}
