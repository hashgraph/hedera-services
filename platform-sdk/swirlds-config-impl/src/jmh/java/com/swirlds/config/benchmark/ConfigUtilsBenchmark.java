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

package com.swirlds.config.benchmark;

import com.swirlds.config.api.ConfigData;
import com.swirlds.config.api.Configuration;
import com.swirlds.config.api.ConfigurationBuilder;
import java.nio.file.FileSystem;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

@State(Scope.Benchmark)
public class ConfigUtilsBenchmark {

    Configuration configuration;

    private FileSystem fileSystem;

    public static void main(final String[] args) throws RunnerException {
        final Options opt = new OptionsBuilder().build();
        new Runner(opt).run();
    }

    @Benchmark
    @Fork(1)
    @Warmup(iterations = 3, time = 2)
    @Measurement(iterations = 5, time = 2)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    @BenchmarkMode({Mode.Throughput, Mode.SampleTime})
    public void loadConfigurationNoScan(final Blackhole blackhole) {
        final ConfigurationBuilder configurationBuilder = ConfigurationBuilder.create();
        final ConfigurationBuilder modified = configurationBuilder.withConfigDataType(AppConfig.class);
        blackhole.consume(modified);
    }

    @Benchmark
    @Fork(1)
    @Warmup(iterations = 3, time = 2)
    @Measurement(iterations = 5, time = 2)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    @BenchmarkMode({Mode.Throughput, Mode.SampleTime})
    public void loadConfigurationWithAllPackages(final Blackhole blackhole) {
        final ConfigurationBuilder configurationBuilder = ConfigurationBuilder.create();
        final ConfigurationBuilder modified = configurationBuilder.autoDiscoverExtensions();
        blackhole.consume(modified);
    }

    @ConfigData("app")
    public record AppConfig(String name, int version) {}
}
