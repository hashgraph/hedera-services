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

package com.swirlds.merkle.test.map;

import com.swirlds.common.constructable.ConstructableRegistry;
import com.swirlds.common.constructable.ConstructableRegistryException;
import com.swirlds.common.merkle.crypto.MerkleCryptoFactory;
import com.swirlds.common.merkle.utility.KeyedMerkleLong;
import com.swirlds.common.merkle.utility.SerializableLong;
import com.swirlds.merkle.map.MerkleMap;
import java.util.Random;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * This tool provides a set of benchmarks that can be used to profile the memory usage of a MerkleMap.
 * The results of these benchmarks are intended to be interpreted using jprofiler.
 */
@DisplayName("MerkleMap Memory Benchmark")
class MerkleMapMemoryBenchmark {

    @BeforeAll
    static void setUp() throws ConstructableRegistryException {
        final ConstructableRegistry registry = ConstructableRegistry.getInstance();
        registry.registerConstructables("com.swirlds.MerkleMap");
        registry.registerConstructables("com.swirlds.merkletree");
        registry.registerConstructables("com.swirlds.common");
    }

    /**
     * Build a MerkleMap of the requested size. Map is returned fully hashed.
     */
    private MerkleMap<SerializableLong, KeyedMerkleLong<SerializableLong>> buildMap(final Random random, final int size)
            throws ExecutionException, InterruptedException {

        final MerkleMap<SerializableLong, KeyedMerkleLong<SerializableLong>> map = new MerkleMap<>(size);

        System.out.println("building map");
        for (int i = 0; i < size; i++) {
            map.put(new SerializableLong(random.nextInt()), new KeyedMerkleLong<>(random.nextInt()));
        }

        System.out.println("hashing map");
        MerkleCryptoFactory.getInstance().digestTreeAsync(map).get();

        return map;
    }

    /**
     * These tests need to be manually killed.
     */
    void pauseForever() throws InterruptedException {
        System.out.println("Pausing for a very long time.");
        Thread.sleep(Long.MAX_VALUE);
    }

    /**
     * Build a map of a given size then pause forever.
     */
    @Test
    @EnabledIfEnvironmentVariable(disabledReason = "Benchmark", named = "benchmark", matches = "true")
    void runBenchmark() throws ExecutionException, InterruptedException {
        buildMap(new Random(), 3_000_000);
        pauseForever();
    }
}
