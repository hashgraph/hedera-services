/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
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

package com.swirlds.common.test.merkle;

import com.swirlds.common.merkle.crypto.MerkleCryptoFactory;
import com.swirlds.common.merkle.crypto.MerkleCryptography;
import com.swirlds.common.test.merkle.dummy.DummyMerkleNode;
import com.swirlds.common.test.merkle.util.MerkleTestUtils;
import com.swirlds.test.framework.TestComponentTags;
import com.swirlds.test.framework.TestTypeTags;
import java.io.IOException;
import java.util.Random;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.apache.commons.lang3.time.StopWatch;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Benchmarks for merkle hashing.
 */
@DisplayName("Merkle Hash Benchmarks")
public class MerkleHashBenchmarks {

    private static MerkleCryptography cryptography;

    @BeforeAll
    public static void setUp() {
        cryptography = MerkleCryptoFactory.getInstance();
    }

    /**
     * Hash a merkle dag and return the time in microseconds.
     */
    private long timeHash(DummyMerkleNode root, boolean sync) throws ExecutionException, InterruptedException {
        StopWatch sw = new StopWatch();
        sw.start();
        if (sync) {
            cryptography.digestTreeSync(root);
        } else {
            cryptography.digestTreeAsync(root).get();
        }
        sw.stop();

        return sw.getTime(TimeUnit.MICROSECONDS);
    }

    private double benchmarkHashesWithParameters(
            long seed,
            double numberOfLeavesAverage,
            double numberOfLeavesStandardDeviation,
            double leafSizeAverage,
            double leafSizeStandardDeviation,
            double numberOfInternalNodesAverage,
            double numberOfInternalNodesStandardDeviation,
            double numberOfInternalNodesDecayFactor,
            int numberOfIterations,
            boolean sync)
            throws IOException, InterruptedException, ExecutionException {
        Random seedGenerator = new Random(seed);

        long totalTime = 0;

        double totalLeaves = 0;
        double totalNodes = 0;
        double sumOfDepths = 0;

        for (int i = 0; i < numberOfIterations; i++) {
            if (i % Math.max(numberOfIterations / 10, 1) == 0) {
                System.out.println("Hashing tree " + i + "/" + numberOfIterations);
            }
            DummyMerkleNode tree = MerkleTestUtils.generateRandomTree(
                    seedGenerator.nextLong(),
                    numberOfLeavesAverage,
                    numberOfLeavesStandardDeviation,
                    leafSizeAverage,
                    leafSizeStandardDeviation,
                    numberOfInternalNodesAverage,
                    numberOfInternalNodesStandardDeviation,
                    numberOfInternalNodesDecayFactor);
            totalTime += timeHash(tree, sync);
            totalLeaves += MerkleTestUtils.measureNumberOfLeafNodes(tree);
            totalNodes += MerkleTestUtils.measureNumberOfNodes(tree);
            sumOfDepths += MerkleTestUtils.measureTreeDepth(tree);
        }

        final double averageTime = ((double) totalTime) / numberOfIterations;

        System.out.println("Average time to hash: " + averageTime + "us");
        System.out.println("Average number of leaves: " + (totalLeaves / numberOfIterations));
        System.out.println("Average number of nodes: " + (totalNodes / numberOfIterations));
        System.out.println("Average tree depth: " + (sumOfDepths / numberOfIterations));

        return averageTime;
    }

    @Test
    @Tag(TestTypeTags.PERFORMANCE)
    @Tag(TestComponentTags.MERKLE)
    @DisplayName("Hash Small Trees")
    public void hashSmallTrees() throws IOException, InterruptedException, ExecutionException {
        System.out.println("--- Synchronous hashing ---");
        double syncTime = benchmarkHashesWithParameters(1337, 3, 1, 64, 10, 3, 1, 0.15, 1000, true);
        System.out.println("--- Asynchronous hashing ---");
        double asyncTime = benchmarkHashesWithParameters(1337, 3, 1, 64, 10, 3, 1, 0.15, 1000, false);

        System.out.println("Speedup from multithreading: " + (syncTime / asyncTime));
    }

    @Test
    @Tag(TestTypeTags.PERFORMANCE)
    @Tag(TestComponentTags.MERKLE)
    @DisplayName("Hash Large Trees")
    public void hashLargeTrees() throws IOException, InterruptedException, ExecutionException {
        System.out.println("--- Synchronous hashing ---");
        double syncTime = benchmarkHashesWithParameters(1337, 3, 1, 64, 10, 3, 1, 0.1, 100, true);
        System.out.println("--- Asynchronous hashing ---");
        double asyncTime = benchmarkHashesWithParameters(1337, 3, 1, 64, 10, 3, 1, 0.1, 100, false);

        System.out.println("Speedup from multithreading: " + (syncTime / asyncTime));
    }

    @Test
    @Tag(TestTypeTags.PERFORMANCE)
    @Tag(TestComponentTags.MERKLE)
    @DisplayName("Hash Huge Trees")
    public void hashHugeTrees() throws IOException, InterruptedException, ExecutionException {
        System.out.println("--- Synchronous hashing ---");
        double syncTime = benchmarkHashesWithParameters(1337, 3, 1, 64, 10, 3, 1, 0.08, 10, true);
        System.out.println("--- Asynchronous hashing ---");
        double asyncTime = benchmarkHashesWithParameters(1337, 3, 1, 64, 10, 3, 1, 0.08, 10, false);

        System.out.println("Speedup from multithreading: " + (syncTime / asyncTime));
    }
}
