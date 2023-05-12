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

import static com.swirlds.common.utility.Units.MICROSECONDS_TO_SECONDS;
import static com.swirlds.test.framework.ResourceLoader.loadLog4jContext;
import static org.junit.jupiter.api.Assertions.fail;

import com.swirlds.common.constructable.ConstructableRegistry;
import com.swirlds.common.constructable.ConstructableRegistryException;
import com.swirlds.common.merkle.crypto.MerkleCryptoFactory;
import com.swirlds.common.merkle.crypto.MerkleCryptography;
import com.swirlds.common.test.merkle.dummy.DummyMerkleNode;
import com.swirlds.common.test.merkle.util.MerkleTestUtils;
import com.swirlds.test.framework.TestComponentTags;
import com.swirlds.test.framework.TestTypeTags;
import java.io.FileNotFoundException;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import org.apache.commons.lang3.time.StopWatch;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

public class MerkleSynchronizationBenchmarks {

    private static MerkleCryptography cryptography;

    @BeforeAll
    public static void setUp() throws FileNotFoundException {
        cryptography = MerkleCryptoFactory.getInstance();
        loadLog4jContext();
    }

    @BeforeEach
    void registerClasses() throws ConstructableRegistryException {
        ConstructableRegistry.getInstance().registerConstructables("com.swirlds.common");
    }

    /**
     * Synchronize two merkle trees and return the time in microseconds.
     */
    private long timeSynchronization(
            DummyMerkleNode startingTree, DummyMerkleNode desiredTree, int latencyMilliseconds) {

        cryptography.digestTreeSync(startingTree);
        cryptography.digestTreeSync(desiredTree);

        StopWatch sw = new StopWatch();
        sw.start();

        try {
            MerkleTestUtils.testSynchronization(startingTree, desiredTree, latencyMilliseconds);
        } catch (Exception e) {
            fail(e);
        }

        return sw.getTime(TimeUnit.MICROSECONDS);
    }

    private double benchmarkWithParameters(
            final long seed,
            final int depth,
            final int childCount,
            final double leafMutationProbability,
            final double leafSizeAverage,
            final double leafSizeStandardDeviation,
            final int numberOfIterations,
            final boolean fullSync,
            final int latencyMilliseconds) {
        Random seedGenerator = new Random(seed);

        long totalTime = 0;

        double totalLeaves = 0;
        double totalNodes = 0;
        double sumOfDepths = 0;
        double sumOfSimilarities = 0;
        double sumOfLeafSizes = 0;

        for (int i = 0; i < numberOfIterations; i++) {
            if (i % Math.max(numberOfIterations / 10, 1) == 0) {
                System.out.println("Syncing tree " + (i + 1) + "/" + numberOfIterations);
            }

            long treeSeed = seedGenerator.nextLong();
            long mutationSeed = seedGenerator.nextLong();

            DummyMerkleNode startingTree = null;
            if (!fullSync) {
                System.out.println("Generating tree");
                startingTree = MerkleTestUtils.generateRandomBalancedTree(
                        treeSeed, depth, childCount, leafSizeAverage, leafSizeStandardDeviation);
            }

            System.out.println("Generating desired tree");
            DummyMerkleNode desiredTree = MerkleTestUtils.generateRandomBalancedTree(
                    treeSeed, depth, childCount, leafSizeAverage, leafSizeStandardDeviation);
            System.out.println("Mutating desired tree");
            MerkleTestUtils.randomlyMutateTree(
                    desiredTree,
                    leafMutationProbability,
                    0,
                    mutationSeed,
                    0,
                    0,
                    leafSizeAverage,
                    leafSizeStandardDeviation,
                    0,
                    0,
                    0);
            System.out.println("Tree generation complete");

            if (startingTree != null) {
                startingTree.reserve();
            }
            desiredTree.reserve();

            totalTime += timeSynchronization(startingTree, desiredTree, latencyMilliseconds);
            totalLeaves += MerkleTestUtils.measureNumberOfLeafNodes(desiredTree);
            totalNodes += MerkleTestUtils.measureNumberOfNodes(desiredTree);
            sumOfDepths += MerkleTestUtils.measureTreeDepth(desiredTree);
            sumOfSimilarities += MerkleTestUtils.countSimilarLeaves(startingTree, desiredTree);
            sumOfLeafSizes += MerkleTestUtils.measureAverageLeafSize(desiredTree);
        }

        final double averageTime = ((double) totalTime) / numberOfIterations;

        // Time is stored internally as microseconds, but it is easier for a human to read in seconds.
        System.out.println("Average time to synchronize: " + (averageTime * MICROSECONDS_TO_SECONDS) + "s");
        System.out.println("Average number of leaves: " + (totalLeaves / numberOfIterations));
        System.out.println("Average number of nodes: " + (totalNodes / numberOfIterations));
        System.out.println("Average tree depth: " + (sumOfDepths / numberOfIterations));
        System.out.println("Average leaf size: " + (sumOfLeafSizes / numberOfIterations));
        System.out.println(
                "Average number of leaf nodes shared between trees: " + (sumOfSimilarities / numberOfIterations));

        return averageTime;
    }

    @Test
    @Timeout(value = 2, unit = TimeUnit.MINUTES)
    @Tag(TestTypeTags.PERFORMANCE)
    @Tag(TestComponentTags.MERKLE)
    @DisplayName("Sync Small Trees")
    public void syncSmallTrees() {
        benchmarkWithParameters(1234, 15, 2, 0.1, 100, 10, 100, false, 0);
    }

    /**
     * Same as syncSmallTrees, but instead copy the entire tree.
     */
    @Test
    @Timeout(value = 2, unit = TimeUnit.MINUTES)
    @Tag(TestTypeTags.PERFORMANCE)
    @Tag(TestComponentTags.MERKLE)
    @DisplayName("Sync Small Trees Full")
    public void syncSmallTreesFull() {
        benchmarkWithParameters(1234, 15, 2, 0.1, 100, 10, 10, true, 0);
    }

    @Test
    @Timeout(value = 2, unit = TimeUnit.MINUTES)
    @Tag(TestTypeTags.PERFORMANCE)
    @Tag(TestComponentTags.MERKLE)
    @DisplayName("Sync Large Trees")
    public void syncLargeTrees() {
        benchmarkWithParameters(1234, 20, 2, 0.1, 100, 50, 1, false, 100);
    }

    /**
     * Same as syncLargeTrees, but instead copy the entire tree.
     */
    @Test
    @Timeout(value = 2, unit = TimeUnit.MINUTES)
    @Tag(TestTypeTags.PERFORMANCE)
    @Tag(TestComponentTags.MERKLE)
    @DisplayName("Sync Large Trees Full")
    public void syncLargeTreesFull() {
        benchmarkWithParameters(1234, 20, 2, 0.1, 100, 50, 1, true, 0);
    }
}
