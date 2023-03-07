/*
 * Copyright (C) 2016-2023 Hedera Hashgraph, LLC
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

package com.swirlds.merkle.map.test.benchmark;

import static com.swirlds.merkle.map.test.benchmark.MerkleMapBenchmarkUtils.generateInitialState;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.swirlds.common.constructable.ConstructableRegistry;
import com.swirlds.common.constructable.ConstructableRegistryException;
import com.swirlds.common.test.benchmark.Benchmark;
import com.swirlds.common.test.benchmark.BenchmarkConfiguration;
import com.swirlds.common.test.benchmark.BenchmarkOperation;
import com.swirlds.common.test.benchmark.BenchmarkStatistic;
import com.swirlds.merkle.map.MerkleMap;
import com.swirlds.merkle.map.test.benchmark.operations.CreateAccountOperation;
import com.swirlds.merkle.map.test.benchmark.operations.DeleteAccountOperation;
import com.swirlds.merkle.map.test.benchmark.operations.ReadBalanceOperation;
import com.swirlds.merkle.map.test.benchmark.operations.TransferOperation;
import com.swirlds.test.framework.TestComponentTags;
import com.swirlds.test.framework.TestTypeTags;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@DisplayName("MerkleMap Benchmark")
class MerkleMapBenchmark {

    private static final Random random = new Random();

    private static final int DATA_SIZE = 100;

    @BeforeAll
    static void setUp() throws ConstructableRegistryException {
        final ConstructableRegistry registry = ConstructableRegistry.getInstance();
        registry.registerConstructables("com.swirlds.merkle.map");
        registry.registerConstructables("com.swirlds.merkle.tree");
    }

    @Test
    @Tag(TestTypeTags.PERFORMANCE)
    @Tag(TestComponentTags.MMAP)
    @DisplayName("Standard MerkleMap Benchmark")
    void standardMerkleMapBenchmark() throws InterruptedException {

        final int initialStateSize = 2_000_000;
        System.out.println("Generating initial state with " + initialStateSize + " accounts");

        final MerkleMapBenchmarkMetadata metadata = new MerkleMapBenchmarkMetadata();
        final MerkleMap<BenchmarkKey, BenchmarkAccount> merkleState =
                generateInitialState(random, DATA_SIZE, metadata, initialStateSize, BenchmarkAccount::new);

        final double createAccountWeight = 0.5;
        final double deleteAccountWeight = createAccountWeight;
        final double transferWeight = 99.0;

        // To make it easier to reason about, ensure that all weights sum to 100
        final double sum = createAccountWeight + deleteAccountWeight + transferWeight;
        assertEquals(100.0, sum, "weights should equal 100");

        final List<BenchmarkOperation<MerkleMap<BenchmarkKey, BenchmarkAccount>, MerkleMapBenchmarkMetadata>>
                mutableOperations = List.of(
                        new CreateAccountOperation<>(createAccountWeight, DATA_SIZE, BenchmarkAccount::new),
                        new DeleteAccountOperation<>(deleteAccountWeight),
                        new TransferOperation<>(transferWeight));

        final List<BenchmarkOperation<MerkleMap<BenchmarkKey, BenchmarkAccount>, MerkleMapBenchmarkMetadata>>
                immutableOperations = List.of(new ReadBalanceOperation<>(100));

        final Benchmark<MerkleMap<BenchmarkKey, BenchmarkAccount>, MerkleMapBenchmarkMetadata> benchmark =
                new BenchmarkConfiguration<MerkleMap<BenchmarkKey, BenchmarkAccount>, MerkleMapBenchmarkMetadata>()
                        .setInitialState(merkleState)
                        .setBenchmarkMetadata(metadata)
                        .setMutableStateOperations(mutableOperations)
                        .setImmutableStateOperations(immutableOperations)
                        .setTestDuration(Duration.ofSeconds(60))
                        .build();

        benchmark.printSettings();

        System.out.println("\nStarting benchmark");

        Collection<BenchmarkStatistic> statistics = benchmark.run();

        for (final BenchmarkStatistic statistic : statistics) {
            System.out.println(statistic);
        }
    }
}
