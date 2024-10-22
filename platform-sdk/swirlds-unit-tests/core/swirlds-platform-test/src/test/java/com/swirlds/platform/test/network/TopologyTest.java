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

package com.swirlds.platform.test.network;

import static com.swirlds.common.test.fixtures.RandomUtils.getRandomPrintSeed;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.swirlds.common.platform.NodeId;
import com.swirlds.common.test.fixtures.Randotron;
import com.swirlds.platform.Utilities;
import com.swirlds.platform.network.PeerInfo;
import com.swirlds.platform.network.RandomGraph;
import com.swirlds.platform.network.topology.NetworkTopology;
import com.swirlds.platform.network.topology.StaticTopology;
import com.swirlds.platform.system.address.AddressBook;
import com.swirlds.platform.test.fixtures.addressbook.RandomAddressBookBuilder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * These tests have some overlap between them, this is because they were developed independently by two different people
 */
class TopologyTest {
    private static Stream<Arguments> topologicalVariations() {
        final int maxNodes = 100;
        final Random r = new Random();
        final List<Arguments> list = new ArrayList<>();
        for (int numNodes = 0; numNodes < maxNodes; numNodes++) {
            for (int numNeighbors = 0; numNeighbors <= numNodes; numNeighbors += 2) {
                list.add(Arguments.of(numNodes, numNeighbors, r.nextLong()));
            }
        }
        return list.stream();
    }

    private static Stream<Arguments> fullyConnected() {
        final int maxNodes = 100;
        final Random r = new Random();
        final List<Arguments> list = new ArrayList<>();
        for (int numNodes = 1; numNodes < maxNodes; numNodes++) {
            list.add(Arguments.of(numNodes, numNodes + numNodes % 2, r.nextLong()));
        }
        return list.stream();
    }

    private static Stream<Arguments> failing() {
        return Stream.of(
                Arguments.of(9, 6, 7873229978642788514L) // fixed by #4828
                );
    }

    private static void addOrThrow(
            final Set<Integer> set, final int thisNode, final int otherNode, final int[] neighbors) {
        if (!set.add(otherNode)) {
            throw new RuntimeException(String.format(
                    "node %d has duplicate neighbor %d. all neighbors: %s",
                    thisNode, otherNode, Arrays.toString(neighbors)));
        }
    }

    private static void testRandomGraphWithSets(
            final RandomGraph randomGraph, final int numNodes, final int numNeighbors) {
        for (int curr = 0; curr < numNodes; curr++) {
            final int[] neighbors = randomGraph.getNeighbors(curr);
            final int finalCurr = curr;
            final HashSet<Integer> neighborSet = Arrays.stream(neighbors)
                    .collect(HashSet::new, (hs, i) -> addOrThrow(hs, finalCurr, i, neighbors), (hs1, hs2) -> {
                        for (final Integer i : hs2) {
                            addOrThrow(hs1, finalCurr, i, neighbors);
                        }
                    });
            assertEquals(
                    Math.min(numNodes - 1, numNeighbors),
                    neighbors.length,
                    "the number of neighbors should either be equal to numNeighbors, "
                            + "or it should be numNodes - 1, whichever is lower");
            for (int other = 0; other < numNodes; other++) {
                final boolean isNeighbor = neighborSet.contains(other);
                assertEquals(isNeighbor, randomGraph.isAdjacent(curr, other));
            }
        }
    }

    @ParameterizedTest
    @MethodSource({"failing", "topologicalVariations", "fullyConnected"})
    void testRandomGraphs(final int numNodes, final int numNeighbors, final long seed) throws Exception {
        System.out.println("numNodes = " + numNodes + ", numNeighbors = " + numNeighbors + ", seed = " + seed);
        final Random random = getRandomPrintSeed();
        final RandomGraph randomGraph = new RandomGraph(random, numNodes, numNeighbors, seed);

        testRandomGraphWithSets(randomGraph, numNodes, numNeighbors);
        testRandomGraphTestIterative(randomGraph, numNodes, numNeighbors, seed);
    }

    @ParameterizedTest
    @MethodSource("fullyConnected")
    void testFullyConnectedTopology(final int numNodes, final long ignoredSeed) {
        final Randotron randotron = Randotron.create();
        final AddressBook addressBook =
                RandomAddressBookBuilder.create(randotron).withSize(numNodes).build();
        for (int thisNode = 0; thisNode < numNodes; thisNode++) {
            final NodeId outOfBoundsId = addressBook.getNextAvailableNodeId();
            final NodeId thisNodeId = addressBook.getNodeId(thisNode);

            final List<PeerInfo> peers = Utilities.createPeerInfoList(addressBook, thisNodeId);

            final NetworkTopology topology = new StaticTopology(peers, thisNodeId);
            final Set<NodeId> neighbors = topology.getNeighbors();
            final Set<NodeId> expected = IntStream.range(0, numNodes)
                    .mapToObj(addressBook::getNodeId)
                    .filter(nodeId -> !Objects.equals(thisNodeId, nodeId))
                    .collect(Collectors.toSet());
            assertEquals(expected, neighbors, "all should be neighbors except me");
            for (final NodeId neighbor : neighbors) {
                assertTrue(
                        topology.shouldConnectTo(neighbor) ^ topology.shouldConnectToMe(neighbor),
                        String.format(
                                "Exactly one connection should be specified between nodes %s and %s%n",
                                thisNodeId, neighbor));
            }
            assertFalse(topology.shouldConnectTo(thisNodeId), "I should not connect to myself");
            assertFalse(topology.shouldConnectToMe(thisNodeId), "I should not connect to myself");

            assertFalse(topology.shouldConnectToMe(outOfBoundsId), "values >=numNodes should return to false");
        }
    }

    /** test a single random matrix with the given number of nodes and neighbors, created using the given seed */
    private void testRandomGraphTestIterative(
            final RandomGraph graph, final int numNodes, final int numNeighbors, final long seed) throws Exception {
        for (int x = 0; x < numNodes; x++) { // x is a row of the adjacency matrix, representing one node
            int count = 0;
            for (int y = 0; y < numNodes; y++) { // y is a column of the adjacency matrix, representing a different node
                if (x == y && graph.isAdjacent(x, y)) {
                    System.out.println(graph);
                    throw new Exception("adjacent to self: " + x
                            + " (row=" + x + " numNodes=" + numNodes + " numNeighbors=" + numNeighbors
                            + " seed=" + seed + ")");
                }
                if (graph.isAdjacent(x, y) != graph.isAdjacent(y, x)) {
                    System.out.println(graph);
                    throw new Exception("neighbor not transitive for " + x + " and " + y
                            + " (row=" + x + " numNodes=" + numNodes + " numNeighbors=" + numNeighbors
                            + " seed=" + seed + ")");
                }
                if (graph.isAdjacent(x, y)) {
                    count++;
                }
            }
            if (count != Math.min(numNeighbors, numNodes - 1)) {
                System.out.println(graph);
                throw new Exception(
                        "neighbors count is " + count + " but should be " + Math.min(numNeighbors, numNodes - 1)
                                + " (row=" + x + " numNodes=" + numNodes + " numNeighbors=" + numNeighbors
                                + " seed=" + seed + ")");
            }
            final int[] neighbors = graph.getNeighbors(x);
            for (int k = 0; k < neighbors.length; k++) {
                if (k > 0 && neighbors[k] <= neighbors[k - 1]) {
                    System.out.println(graph);
                    throw new Exception("Neighbor list out of order"
                            + " (row=" + x + " numNodes=" + numNodes + " numNeighbors=" + numNeighbors
                            + " seed=" + seed + ")");
                }
                if (!graph.isAdjacent(x, neighbors[k])) {
                    System.out.println(graph);
                    throw new Exception("Neighbor list doesn't match adjacency matrix for node " + x
                            + " (row=" + x + " numNodes=" + numNodes + " numNeighbors=" + numNeighbors
                            + " seed=" + seed + ")");
                }
            }
        }
    }
}
