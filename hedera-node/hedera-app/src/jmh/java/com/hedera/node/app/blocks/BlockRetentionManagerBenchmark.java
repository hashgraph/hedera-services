/*
 * Copyright (C) 2024-2025 Hedera Hashgraph, LLC
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

package com.hedera.node.app.blocks;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Comparator;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;

@State(Scope.Benchmark)
@Fork(value = 1)
@Warmup(iterations = 1)
@Measurement(iterations = 3)
public class BlockRetentionManagerBenchmark {

    private BlockRetentionManager blockRetentionManager;
    private Path uploadedDir;

    // if we produce one block per second then in general:
    //  - for an hour we will have 3600 blocks
    //  - for a day we will have 3600 * 24 = 86400 blocks
    //  - for a week we will have 3600 * 168 = 604800 blocks
    //
    // NOTE: an exception to the above might be the case for daylight savings time switches
    @Param({"3600", "86400", "604800"})
    private int blockFilesCount;

    @Param({"2", "4", "8"})
    private int cleanupThreadPoolSize;

    public static void main(String... args) throws Exception {
        org.openjdk.jmh.Main.main(
                new String[] {"com.hedera.node.app.blocks.BlockRetentionManagerBenchmark.benchmarkCleanupExpiredBlocks"
                });
    }

    @Setup(Level.Invocation)
    public void setUpInvocation() throws IOException {
        uploadedDir = Files.createTempDirectory("uploaded");
        blockRetentionManager =
                new BlockRetentionManager(uploadedDir, Duration.ZERO, Duration.ZERO, cleanupThreadPoolSize);

        // Create a number of block files for the benchmark
        for (int i = 0; i < blockFilesCount; i++) {
            Files.createFile(uploadedDir.resolve("block" + i + BlockRetentionManager.BLOCK_FILE_EXTENSION));
        }
    }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.SECONDS)
    public void benchmarkCleanupExpiredBlocks() {
        blockRetentionManager.cleanupExpiredBlocks();
    }

    @TearDown(Level.Invocation)
    public void tearDownInvocation() throws IOException {
        blockRetentionManager.shutdown();
        try (Stream<Path> files = Files.walk(uploadedDir)) {
            files.sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
        }
    }
}
