/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform.test.state;

import static com.swirlds.common.test.RandomUtils.getRandomPrintSeed;
import static com.swirlds.platform.Utilities.isMajority;
import static com.swirlds.platform.state.iss.internal.ConsensusHashStatus.CATASTROPHIC_ISS;
import static com.swirlds.platform.state.iss.internal.ConsensusHashStatus.DECIDED;
import static com.swirlds.platform.state.iss.internal.ConsensusHashStatus.UNDECIDED;
import static com.swirlds.platform.test.utils.EqualsVerifier.randomHash;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.swirlds.common.crypto.Hash;
import com.swirlds.platform.dispatch.triggers.flow.StateHashValidityTrigger;
import com.swirlds.platform.state.iss.internal.ConsensusHashFinder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("ConsensusHashFinder Tests")
class ConsensusHashFinderTests {

    @BeforeEach
    void beforeEach() {}

    @AfterEach
    void afterEach() {}

    /**
     * A node to be added to a consensus hash builder.
     */
    private record NodeToAdd(long nodeId, long stake, Hash stateHash) {}

    /**
     * A description of a desired partition
     */
    private record PartitionDescription(Hash hash, long totalStake) {}

    /**
     * Generate a list of nodes for a given partition
     *
     * @param random
     * 		a source of randomness
     * @param firstNodeId
     * 		the first node ID. Node IDs are generated sequentially starting with this node ID.
     * @param partition
     * 		a description of the partition
     */
    private List<NodeToAdd> getPartitionNodes(
            final Random random,
            final long averageStake,
            final long standardDeviationStake,
            final long firstNodeId,
            final PartitionDescription partition) {
        final List<NodeToAdd> nodes = new ArrayList<>();
        if (partition.totalStake > 0) {
            long remainingStake = partition.totalStake;
            long nextNodeId = firstNodeId;

            while (remainingStake > 0) {
                final long nextNodeStake = Math.min(remainingStake, Math.max(1L, (long)
                        (averageStake + random.nextGaussian() * standardDeviationStake)));

                nodes.add(new NodeToAdd(nextNodeId, nextNodeStake, partition.hash));
                nextNodeId++;
                remainingStake -= nextNodeStake;
            }
        }

        return nodes;
    }

    /**
     * Given a list of partitions, return a list of nodes from those partitions.
     *
     * @param random
     * 		a source of randomness
     * @param averageStake
     * 		the average stake per node
     * @param standardDeviationStake
     * 		the standard deviation of the stake per node
     * @param partitions
     * 		a list of partitions
     */
    private List<NodeToAdd> getNodes(
            final Random random,
            final long averageStake,
            final long standardDeviationStake,
            final List<PartitionDescription> partitions) {

        final List<NodeToAdd> nodes = new ArrayList<>();
        long nextNodeId = 0;
        for (final PartitionDescription partition : partitions) {
            final List<NodeToAdd> partitionNodes =
                    getPartitionNodes(random, averageStake, standardDeviationStake, nextNodeId, partition);
            nextNodeId += partitionNodes.size();
            nodes.addAll(partitionNodes);
        }
        return nodes;
    }

    @ParameterizedTest
    @ValueSource(longs = {1, 2, 3, 7, 8, 9, 10, 99, 100, 101, 999, 1000, 1001, 1024, 1025, Long.MAX_VALUE})
    @DisplayName("Single Partition Test")
    void singlePartitionTest(final long totalStake) {

        final Random random = getRandomPrintSeed();
        final long averageStake = totalStake / 100;
        final long standardDeviationStake = totalStake / 200;
        final Hash hash = randomHash(random);

        final StateHashValidityTrigger stateHashValidityDispatcher =
                (final Long round, final Long nodeId, final Hash nodeHash, final Hash consensusHash) ->
                        assertEquals(nodeHash, consensusHash, "no disagreements expected");

        final ConsensusHashFinder hashFinder = new ConsensusHashFinder(stateHashValidityDispatcher, 0, totalStake);
        assertEquals(totalStake, hashFinder.getTotalStake(), "unexpected total stake");
        assertEquals(0, hashFinder.getHashReportedStake(), "no stake should be accumulated yet");
        assertEquals(0, hashFinder.getPartitionMap().size(), "there shouldn't be any partitions yet");

        // Add stake up until >1/2, but as soon as we meet or exceed 1/2 exit the loop
        long nextNodeId = 0;
        while (!isMajority(hashFinder.getHashReportedStake(), totalStake)) {
            assertEquals(UNDECIDED, hashFinder.getStatus(), "status should not yet be decided");

            final long nextNodeStake =
                    (long) Math.max(1, averageStake + random.nextGaussian() * standardDeviationStake);
            hashFinder.addHash(nextNodeId, nextNodeStake, hash);

            // adding the same node again should have no effect
            final long currentAccumulatedStake = hashFinder.getHashReportedStake();
            hashFinder.addHash(nextNodeId, nextNodeStake, hash);
            assertEquals(currentAccumulatedStake, hashFinder.getHashReportedStake(), "duplicates should be no-ops");

            // adding the same node with a different hash should have no effect
            hashFinder.addHash(nextNodeId, nextNodeStake, randomHash(random));
            assertEquals(currentAccumulatedStake, hashFinder.getHashReportedStake(), "duplicates should be no-ops");

            nextNodeId++;

            assertEquals(1, hashFinder.getPartitionMap().size(), "there should only be 1 partition");
            assertTrue(hashFinder.getPartitionMap().containsKey(hash), "invalid partition map");
            assertEquals(
                    nextNodeId,
                    hashFinder.getPartitionMap().get(hash).getNodes().size(),
                    "incorrect partition size");
            assertTrue(
                    hashFinder.getPartitionMap().get(hash).getNodes().contains(nextNodeId - 1),
                    "could not find node that was just added");
        }

        // At this point, we should have added enough stake to reach a threshold of >= 1/2
        assertEquals(DECIDED, hashFinder.getStatus(), "should now be decided");
        assertEquals(hash, hashFinder.getConsensusHash(), "incorrect hash");
    }

    @ParameterizedTest
    @ValueSource(longs = {99, 100, 101, 999, 1000, 1001, 1024, 1025, Long.MAX_VALUE})
    @DisplayName("Single Barely Valid Partition Test")
    void singleBarelyValidPartitionTest(final long totalStake) {
        final Random random = getRandomPrintSeed();
        final long averageStake = totalStake / 100;
        final long standardDeviationStake = totalStake / 200;
        final Hash expectedConsensusHash = randomHash(random);

        final Set<Long> disagreeingNodes = new HashSet<>();
        final Set<Long> expectedDisagreeingNodes = new HashSet<>();
        final StateHashValidityTrigger stateHashValidityDispatcher =
                (final Long round, final Long nodeId, final Hash nodeHash, final Hash consensusHash) -> {
                    assertEquals(consensusHash, expectedConsensusHash, "unexpected consensus hash");

                    if (!nodeHash.equals(consensusHash)) {
                        assertNotEquals(consensusHash, nodeHash, "hash should disagree");
                        assertTrue(
                                disagreeingNodes.add(nodeId), "should not be called multiple times on the same node");
                    }
                };

        final ConsensusHashFinder hashFinder = new ConsensusHashFinder(stateHashValidityDispatcher, 0, totalStake);

        long remainingStake = totalStake;

        final List<PartitionDescription> partitions = new LinkedList<>();

        // A partition with just enough stake to be complete
        final long stakeInConsensusPartition = totalStake / 2 + 1;
        remainingStake -= stakeInConsensusPartition;
        partitions.add(new PartitionDescription(expectedConsensusHash, stakeInConsensusPartition));

        // Create a bunch of partitions that are incomplete
        while (remainingStake > 0) {
            final long partitionStake = Math.min(remainingStake, totalStake / 10);
            remainingStake -= partitionStake;
            partitions.add(new PartitionDescription(randomHash(random), partitionStake));
        }

        // Add the nodes in a random order
        final List<NodeToAdd> nodes = getNodes(random, averageStake, standardDeviationStake, partitions);
        Collections.shuffle(nodes, random);

        for (final NodeToAdd nodeToAdd : nodes) {
            if (!nodeToAdd.stateHash().equals(expectedConsensusHash)) {
                expectedDisagreeingNodes.add(nodeToAdd.nodeId());
            }
            hashFinder.addHash(nodeToAdd.nodeId, nodeToAdd.stake, nodeToAdd.stateHash);
        }

        assertEquals(DECIDED, hashFinder.getStatus(), "should be decided by now");
        assertEquals(expectedConsensusHash, hashFinder.getConsensusHash(), "incorrect hash chosen");
        assertEquals(expectedDisagreeingNodes, disagreeingNodes, "disagreeing node set incorrect");
    }

    @ParameterizedTest
    @ValueSource(longs = {99, 100, 101, 999, 1000, 1001, 1024, 1025, Long.MAX_VALUE})
    @DisplayName("Almost Complete Partition Test")
    void almostCompletePartitionTest(final long totalStake) {
        final Random random = getRandomPrintSeed();
        final long averageStake = totalStake / 100;
        final long standardDeviationStake = totalStake / 200;

        final StateHashValidityTrigger stateHashValidityDispatcher =
                (final Long round, final Long nodeId, final Hash nodeHash, final Hash consensusHash) ->
                        fail("no disagreement expected");

        final ConsensusHashFinder hashFinder = new ConsensusHashFinder(stateHashValidityDispatcher, 0, totalStake);

        long remainingStake = totalStake;

        final List<PartitionDescription> partitions = new LinkedList<>();

        // A partition with almost enough stake to be complete
        final long stakeInBigPartition = totalStake / 2 - 1;
        remainingStake -= stakeInBigPartition;
        final Hash bigPartitionHash = randomHash(random);
        partitions.add(new PartitionDescription(bigPartitionHash, stakeInBigPartition));

        // Create a bunch of smaller partitions that are incomplete
        while (remainingStake > 0) {
            final long partitionStake = Math.min(remainingStake, totalStake / 10);
            remainingStake -= partitionStake;
            partitions.add(new PartitionDescription(randomHash(random), partitionStake));
        }

        // Add the nodes in a random order
        final List<NodeToAdd> nodes = getNodes(random, averageStake, standardDeviationStake, partitions);
        Collections.shuffle(nodes, random);

        for (final NodeToAdd nodeToAdd : nodes) {
            hashFinder.addHash(nodeToAdd.nodeId, nodeToAdd.stake, nodeToAdd.stateHash);
        }

        assertTrue(hashFinder.getPartitionMap().size() > 1, "there should be multiple partitions");
        assertEquals(CATASTROPHIC_ISS, hashFinder.getStatus(), "should have ISSed");
        assertNull(hashFinder.getConsensusHash(), "no consensus hash should have been chosen");
    }

    @ParameterizedTest
    @ValueSource(longs = {99, 100, 101, 999, 1000, 1001, 1024, 1025, Long.MAX_VALUE})
    @DisplayName("Lots Of Small Partitions Test")
    void lotsOfSmallPartitionsTest(final long totalStake) {
        final Random random = getRandomPrintSeed();
        final long averageStake = totalStake / 100;
        final long standardDeviationStake = totalStake / 200;

        final StateHashValidityTrigger stateHashValidityDispatcher =
                (final Long round, final Long nodeId, final Hash nodeHash, final Hash consensusHash) ->
                        fail("no disagreement expected");

        final ConsensusHashFinder hashFinder = new ConsensusHashFinder(stateHashValidityDispatcher, 0, totalStake);

        long remainingStake = totalStake;

        final List<PartitionDescription> partitions = new LinkedList<>();

        // Create a bunch of small partitions that are incomplete
        while (remainingStake > 0) {
            final long partitionStake = Math.min(remainingStake, totalStake / 10);
            remainingStake -= partitionStake;
            partitions.add(new PartitionDescription(randomHash(random), partitionStake));
        }

        // Add the nodes in a random order
        final List<NodeToAdd> nodes = getNodes(random, averageStake, standardDeviationStake, partitions);
        Collections.shuffle(nodes, random);

        for (final NodeToAdd nodeToAdd : nodes) {
            hashFinder.addHash(nodeToAdd.nodeId, nodeToAdd.stake, nodeToAdd.stateHash);
        }

        assertTrue(hashFinder.getPartitionMap().size() > 1, "there should be multiple partitions");
        assertEquals(CATASTROPHIC_ISS, hashFinder.getStatus(), "should have ISSed");
        assertNull(hashFinder.getConsensusHash(), "no consensus hash should have been chosen");
    }

    @ParameterizedTest
    @ValueSource(longs = {99, 100, 101, 999, 1000, 1001, 1024, 1025, Long.MAX_VALUE})
    @DisplayName("Early ISS Detection Test")
    void earlyIssDetectionTest(final long totalStake) {
        final Random random = getRandomPrintSeed();
        final long averageStake = totalStake / 100;
        final long standardDeviationStake = totalStake / 200;

        final StateHashValidityTrigger stateHashValidityDispatcher =
                (final Long round, final Long nodeId, final Hash nodeHash, final Hash consensusHash) ->
                        fail("no consensus hash should be found");

        final ConsensusHashFinder hashFinder = new ConsensusHashFinder(stateHashValidityDispatcher, 0, totalStake);

        long remainingStake = totalStake;

        // A partition with almost enough stake to be complete
        final long stakeInBigPartition = totalStake / 2 - 1;
        remainingStake -= stakeInBigPartition;
        final PartitionDescription bigPartition = new PartitionDescription(randomHash(random), stakeInBigPartition);

        // Create a bunch of partitions that are incomplete
        final List<PartitionDescription> smallPartitions = new LinkedList<>();
        while (remainingStake > 0) {
            final long partitionStake = Math.min(remainingStake, totalStake / 10);
            remainingStake -= partitionStake;
            smallPartitions.add(new PartitionDescription(randomHash(random), partitionStake));
        }

        long nextNodeId = 0;
        final List<NodeToAdd> nodes = new ArrayList<>();

        // Add the nodes from the small partitions
        for (final PartitionDescription partition : smallPartitions) {
            final List<NodeToAdd> partitionNodes =
                    getPartitionNodes(random, averageStake, standardDeviationStake, nextNodeId, partition);
            nextNodeId += partitionNodes.size();
            nodes.addAll(partitionNodes);
        }

        // Add in the almost complete partition.
        nodes.addAll(getPartitionNodes(random, averageStake, standardDeviationStake, nextNodeId, bigPartition));

        // Now, feed in the nodes in order.
        for (final NodeToAdd nodeToAdd : nodes) {
            hashFinder.addHash(nodeToAdd.nodeId, nodeToAdd.stake, nodeToAdd.stateHash);
        }

        assertEquals(CATASTROPHIC_ISS, hashFinder.getStatus(), "should be an iss");
        assertNull(hashFinder.getConsensusHash(), "no consensus hash should have been chosen");
    }

    @ParameterizedTest
    @ValueSource(longs = {99, 100, 101, 999, 1000, 1001, 1024, 1025, Long.MAX_VALUE})
    @DisplayName("Complete Partition Is Last Test Test")
    void completePartitionIsLastTest(final long totalStake) {
        final Random random = getRandomPrintSeed();
        final long averageStake = totalStake / 100;
        final long standardDeviationStake = totalStake / 200;
        final Hash expectedConsensusHash = randomHash(random);

        final Set<Long> disagreeingNodes = new HashSet<>();
        final Set<Long> expectedDisagreeingNodes = new HashSet<>();
        final StateHashValidityTrigger stateHashValidityDispatcher =
                (final Long round, final Long nodeId, final Hash nodeHash, final Hash consensusHash) -> {
                    assertEquals(consensusHash, expectedConsensusHash, "unexpected consensus hash");

                    if (!nodeHash.equals(consensusHash)) {
                        assertNotEquals(consensusHash, nodeHash, "hash should disagree");
                        assertTrue(
                                disagreeingNodes.add(nodeId), "should not be called multiple times on the same node");
                    }
                };

        final ConsensusHashFinder hashFinder = new ConsensusHashFinder(stateHashValidityDispatcher, 0, totalStake);

        long remainingStake = totalStake;

        // A partition with enough stake to be complete
        final long stakeInBigPartition = totalStake / 2L + 1L;
        remainingStake -= stakeInBigPartition;
        final PartitionDescription bigPartition = new PartitionDescription(expectedConsensusHash, stakeInBigPartition);

        // Create a bunch of partitions that are incomplete
        final List<PartitionDescription> smallPartitions = new LinkedList<>();
        while (remainingStake > 0) {
            final long partitionStake = Math.min(remainingStake, totalStake / 10);
            remainingStake -= partitionStake;
            smallPartitions.add(new PartitionDescription(randomHash(random), partitionStake));
        }

        long nextNodeId = 0;
        final List<NodeToAdd> nodes = new ArrayList<>();

        // Add the nodes from the small partitions
        for (final PartitionDescription partition : smallPartitions) {
            final List<NodeToAdd> partitionNodes =
                    getPartitionNodes(random, averageStake, standardDeviationStake, nextNodeId, partition);
            nextNodeId += partitionNodes.size();
            nodes.addAll(partitionNodes);
        }

        // Add in the big partition
        nodes.addAll(getPartitionNodes(random, averageStake, standardDeviationStake, nextNodeId, bigPartition));

        // Now, feed in the nodes in order.
        for (final NodeToAdd nodeToAdd : nodes) {
            if (!nodeToAdd.stateHash().equals(expectedConsensusHash)) {
                expectedDisagreeingNodes.add(nodeToAdd.nodeId());
            }
            hashFinder.addHash(nodeToAdd.nodeId, nodeToAdd.stake, nodeToAdd.stateHash);
        }

        assertEquals(DECIDED, hashFinder.getStatus(), "should be decided by now");
        assertEquals(expectedConsensusHash, hashFinder.getConsensusHash(), "incorrect hash chosen");
        assertEquals(expectedDisagreeingNodes, disagreeingNodes, "disagreeing node set incorrect");
    }
}
