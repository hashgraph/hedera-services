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

package com.swirlds.platform.test.consensus;

import static com.swirlds.common.test.RandomUtils.initRandom;
import static com.swirlds.platform.test.consensus.ConsensusUtils.applyEventsToConsensus;
import static com.swirlds.platform.test.consensus.ConsensusUtils.buildSimpleConsensus;
import static com.swirlds.platform.test.consensus.ConsensusUtils.isRestartConsensusEquivalent;
import static com.swirlds.platform.test.consensus.ConsensusUtils.testConsensus;
import static com.swirlds.platform.test.event.EventUtils.createSignedState;
import static com.swirlds.platform.test.event.EventUtils.getEventMap;
import static com.swirlds.platform.test.event.EventUtils.getLastGenerationInState;
import static com.swirlds.platform.test.event.EventUtils.integerPowerDistribution;
import static com.swirlds.platform.test.event.source.EventSourceFactory.newStandardEventSources;
import static com.swirlds.platform.test.graph.OtherParentMatrixFactory.createBalancedOtherParentMatrix;
import static com.swirlds.platform.test.graph.OtherParentMatrixFactory.createCliqueOtherParentMatrix;
import static com.swirlds.platform.test.graph.OtherParentMatrixFactory.createPartitionedOtherParentAffinityMatrix;
import static com.swirlds.platform.test.graph.OtherParentMatrixFactory.createShunnedNodeOtherParentAffinityMatrix;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.swirlds.common.config.ConsensusConfig;
import com.swirlds.common.config.singleton.ConfigurationHolder;
import com.swirlds.common.constructable.ConstructableRegistryException;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.system.address.AddressBook;
import com.swirlds.common.test.RandomAddressBookGenerator;
import com.swirlds.common.test.StakeGenerator;
import com.swirlds.common.test.StakeGenerators;
import com.swirlds.common.threading.utility.AtomicDouble;
import com.swirlds.platform.Consensus;
import com.swirlds.platform.Utilities;
import com.swirlds.platform.eventhandling.SignedStateEventsAndGenerations;
import com.swirlds.platform.internal.ConsensusRound;
import com.swirlds.platform.internal.EventImpl;
import com.swirlds.platform.internal.SignedStateLoadingException;
import com.swirlds.platform.state.signed.SignedState;
import com.swirlds.platform.test.SimpleEventGenerator;
import com.swirlds.platform.test.event.DynamicValue;
import com.swirlds.platform.test.event.EventUtils;
import com.swirlds.platform.test.event.IndexedEvent;
import com.swirlds.platform.test.event.TestSequence;
import com.swirlds.platform.test.event.emitter.PriorityEventEmitter;
import com.swirlds.platform.test.event.emitter.StandardEventEmitter;
import com.swirlds.platform.test.event.generator.StandardGraphGenerator;
import com.swirlds.platform.test.event.source.EventSource;
import com.swirlds.platform.test.event.source.EventSourceFactory;
import com.swirlds.platform.test.event.source.ForkingEventSource;
import com.swirlds.platform.test.event.source.StandardEventSource;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

public final class ConsensusTestDefinitions {

    private ConsensusTestDefinitions() {}

    /**
     * Send an ancient event to consensus and check if it is marked stale.
     */
    public static void ancientEventTest(
            final int numberOfNodes, final StakeGenerator stakeGenerator, final int iterations, final long[] seeds) {

        final ConsensusTestDefinition testDefinition =
                new ConsensusTestDefinition("Ancient Event Tests", numberOfNodes, stakeGenerator, 10_000);

        final int ancientEventIndex = 100;
        testDefinition.setGraphGeneratorProvider(
                nodeStakes -> createAncientEventGenerator(numberOfNodes, nodeStakes, ancientEventIndex));

        final List<Integer> nodePriorities =
                IntStream.range(0, numberOfNodes).boxed().toList();
        testDefinition.setNode1EventEmitterGenerator(
                (graphGenerator, seed) -> new PriorityEventEmitter(graphGenerator, nodePriorities));
        testDefinition.setNode2EventEmitterGenerator(
                (graphGenerator, seed) -> new StandardEventEmitter(graphGenerator));

        testDefinition.setTestSequenceGenerator(td -> List.of(new TestSequence(td.getEventsPerPhase())));

        testConsensus(testDefinition, iterations, seeds);
    }

    private static StandardGraphGenerator createAncientEventGenerator(
            final int numberOfNodes, final List<Long> nodeStakes, final int ancientEventIndex) {

        final List<EventSource<?>> eventSources = EventSourceFactory.newStandardEventSources(nodeStakes);
        final StandardGraphGenerator standardGenerator = new StandardGraphGenerator(0L, eventSources);

        // All nodes are awake, connected, and created events with each other as parents
        final List<List<Double>> allNodesAwake = createBalancedOtherParentMatrix(numberOfNodes);

        // The lowest priority node is shunned. Nodes do not use its events as other parents.
        final List<List<Double>> shunLastNode =
                createShunnedNodeOtherParentAffinityMatrix(numberOfNodes, numberOfNodes - 1);

        // All nodes are awake and connected for a while, then, starting at some event X, the last node is
        // shunned so its events will not be parents to other node events.
        standardGenerator.setOtherParentAffinity((Random r, long eventIndex, List<List<Double>> previousValue) -> {
            if (eventIndex < ancientEventIndex) {
                return allNodesAwake;
            } else {
                return shunLastNode;
            }
        });

        // Set up the event sources such that the lowest priority node creates event X and then never creates
        // events again. Event X will not have any children, because the node is shunned after event X is
        // created.
        for (int i = 0; i < standardGenerator.getNumberOfSources(); i++) {
            final EventSource<?> source = standardGenerator.getSource(i);

            // This is the last source. Force it to stop creating events (go to sleep) after the event X
            if (i == standardGenerator.getNumberOfSources() - 1) {
                source.setNewEventWeight((Random random, long eventIndex, Double previousValue) -> {
                    if (eventIndex <= ancientEventIndex) {
                        return 1.0;
                    } else {
                        return 0.0;
                    }
                });
            } else {
                source.setNewEventWeight((Random random, long eventIndex, Double previousValue) -> {
                    if (eventIndex < ancientEventIndex) {
                        return 1.0;
                    } else if (eventIndex == ancientEventIndex) {
                        return 0.0;
                    } else {
                        return 1.0;
                    }
                });
            }
        }
        return standardGenerator;
    }

    /**
     * Changing the order of events (without breaking topological order) should result in the same consensus events.
     */
    public static void orderInvarianceTests(
            final int numberOfNodes, final StakeGenerator stakeGenerator, final int iterations, final long... seeds) {

        final ConsensusTestDefinition testDefinition =
                new ConsensusTestDefinition("Order Invariance Tests", numberOfNodes, stakeGenerator, 10_000);

        testDefinition.setTestSequenceGenerator(
                td -> List.of(new TestSequence(10_000).setMinimumConsensusRatio(0.9 - 0.05 * numberOfNodes)));

        testConsensus(testDefinition, iterations, seeds);
    }

    /**
     * Test consensus in the presence of forks.
     */
    public static void forkingTests(
            final int numberOfNodes, final StakeGenerator stakeGenerator, final int iterations, final long... seeds) {
        final ConsensusTestDefinition testDefinition =
                new ConsensusTestDefinition("Order Invariance Tests", numberOfNodes, stakeGenerator, 10_000);

        // Set a custom event source generator that creates forking event sources
        testDefinition.setGraphGeneratorProvider(nodeStakes -> {
            final double forkProbability = 0.1;
            final int numberOfForkedBranches = 10;
            final long totalStake = nodeStakes.stream().reduce(0L, Long::sum);

            // Determine a single forking event source that has less than a strong minority of stake
            int forkingNodeId = -1;
            for (int i = 0; i < nodeStakes.size(); i++) {
                final long stake = nodeStakes.get(i);
                if (!Utilities.isStrongMinority(stake, totalStake)) {
                    forkingNodeId = i;
                    break;
                }
            }

            final List<EventSource<?>> eventSources = new ArrayList<>(nodeStakes.size());
            for (int i = 0; i < nodeStakes.size(); i++) {
                final long stake = nodeStakes.get(i);
                if (i == forkingNodeId) {
                    eventSources.add(new ForkingEventSource(stake)
                            .setForkProbability(forkProbability)
                            .setMaximumBranchCount(numberOfForkedBranches));
                } else {
                    eventSources.add(new StandardEventSource(stake));
                }
            }
            return new StandardGraphGenerator(0, eventSources);
        });

        testDefinition.setTestSequenceGenerator(td -> List.of(new TestSequence(10_000).setMaximumStaleRatio(0.1)));

        testConsensus(testDefinition, iterations, seeds);
    }

    /**
     * Test consensus in the presence of forks.
     */
    public static void forkingTestsOldVersion(final int numberOfNodes, final int iterations, final long... seeds) {
        final ConsensusTestDefinition testDefinition = new ConsensusTestDefinition(
                "Order Invariance Tests",
                numberOfNodes,
                (l, i) -> StakeGenerators.balancedNodeStakes(numberOfNodes),
                10_000);

        // Set a custom event source generator that creates forking event sources
        testDefinition.setGraphGeneratorProvider(nodeStakes -> {
            final double forkProbability = 0.1;
            final int numberOfForkedBranches = 10;
            final int numForkingNodes = 1;

            // make the last node the forking node
            final List<EventSource<?>> eventSources = new ArrayList<>(numberOfNodes);
            for (int i = 0; i < numberOfNodes - numForkingNodes; i++) {
                eventSources.add(new StandardEventSource());
            }
            for (int i = 0; i < numForkingNodes; i++) {
                eventSources.add(new ForkingEventSource()
                        .setForkProbability(forkProbability)
                        .setMaximumBranchCount(numberOfForkedBranches));
            }
            return new StandardGraphGenerator(0, eventSources);
        });

        testDefinition.setTestSequenceGenerator(td -> List.of(new TestSequence(10_000).setMaximumStaleRatio(0.05)));

        testConsensus(testDefinition, iterations, seeds);
    }

    /**
     * Consensus should handle a partition gracefully.
     */
    public static void partitionTests(
            final int numberOfNodes, final StakeGenerator stakeGenerator, final int iterations, final long... seeds) {

        final ConsensusTestDefinition testDefinition =
                new ConsensusTestDefinition("Partition Tests", numberOfNodes, stakeGenerator, 3_000);

        testDefinition.setTestSequenceGenerator(td -> {
            // Return a simple test sequence for debugging when seeds are provided
            if (seeds != null) {
                return List.of(new TestSequence(10_000));
            }

            return strongMinorityPartitionTestSequences(td);
        });

        testConsensus(testDefinition, iterations, seeds);
    }

    /**
     * Creates three test sequences:
     *
     * <ol>
     *     <li>fully connected network</li>
     *     <li>partitioned network such that one of the partitions has a strong minority</li>
     *     <li>fully connected network</li>
     * </ol>
     */
    public static List<TestSequence> strongMinorityPartitionTestSequences(
            final ConsensusTestDefinition testDefinition) {
        final int eventsPerPhase = testDefinition.getEventsPerPhase();
        final List<Long> nodeStakes = testDefinition.getNodeStakes();
        final int numberOfNodes = testDefinition.getNumberOfNodes();

        final Set<Integer> partitionedNodes = getStrongMinorityNodes(nodeStakes);

        final List<EventSource<?>> eventSources = EventSourceFactory.newStandardEventSources(nodeStakes);

        final StandardGraphGenerator generator = new StandardGraphGenerator(0, eventSources);

        // All nodes talk to each other with equal probability
        final List<List<Double>> fullyConnected = createBalancedOtherParentMatrix(numberOfNodes);

        // >= 1/3 of nodes are partitioned from the rest of the network
        final List<List<Double>> partitioned =
                createPartitionedOtherParentAffinityMatrix(numberOfNodes, partitionedNodes);

        // Network is connected for a while, then is partitioned, then is connected for a while again.
        generator.setOtherParentAffinity((Random r, long eventIndex, List<List<Double>> previousValue) -> {
            if (eventIndex < eventsPerPhase) {
                // phase 1
                return fullyConnected;
            } else if (eventIndex < 2L * eventsPerPhase) {
                // phase 2
                return partitioned;
            } else {
                // phase 3
                return fullyConnected;
            }
        });

        // In phase 1 we expect normal consensus
        // There are fewer events that normal (10_000 is standard) so it is possible that fewer events will
        // reach consensus than expected by the default minimum ratio.
        final TestSequence phase1 = new TestSequence(eventsPerPhase).setMinimumConsensusRatio(0.8);

        // In phase 2, almost no events will reach consensus
        //   (it's possible a few tail events may reach consensus right at the beginning of the phase)
        final TestSequence phase2 =
                new TestSequence(eventsPerPhase).setMinimumConsensusRatio(0.0).setMaximumConsensusRatio(0.5);

        // In phase 3 we expect for phase 2 and phase 3 events to reach consensus
        final TestSequence phase3 = new TestSequence(eventsPerPhase).setMinimumConsensusRatio(0.8);

        return List.of(phase1, phase2, phase3);
    }

    /**
     * Simulates a partition where one partition has a quorum.
     */
    public static void subQuorumPartitionTests(
            final int numberOfNodes, final StakeGenerator stakeGenerator, final int iterations, final long... seeds) {

        final int eventsPerPhase = 3_000;
        final AtomicInteger numPartitionedNodes = new AtomicInteger();

        final ConsensusTestDefinition testDefinition = new ConsensusTestDefinition(
                "Sub Quorum Partition Tests", numberOfNodes, stakeGenerator, eventsPerPhase);

        testDefinition.setGraphGeneratorProvider(nodeStakes -> {
            final List<EventSource<?>> eventSources = new ArrayList<>(nodeStakes.size());
            for (final long stake : nodeStakes) {
                eventSources.add(new StandardEventSource(stake));
            }
            final StandardGraphGenerator graphGenerator = new StandardGraphGenerator(0, eventSources);

            final Set<Integer> partitionedNodes = getSubStrongMinorityNodes(nodeStakes);
            numPartitionedNodes.set(partitionedNodes.size());

            // All nodes talk to each other with equal probability
            final List<List<Double>> fullyConnected = createBalancedOtherParentMatrix(numberOfNodes);

            // Less than a strong minority of nodes' stake are partitioned from the network
            final List<List<Double>> partitioned =
                    createPartitionedOtherParentAffinityMatrix(numberOfNodes, partitionedNodes);

            // Network is connected for a while, then is partitioned, then is connected for a while again.
            graphGenerator.setOtherParentAffinity((Random r, long eventIndex, List<List<Double>> previousValue) -> {
                if (eventIndex < eventsPerPhase) {
                    // phase 1
                    return fullyConnected;
                } else if (eventIndex < 2L * eventsPerPhase) {
                    // phase 2
                    return partitioned;
                } else {
                    // phase 3
                    return fullyConnected;
                }
            });

            return graphGenerator;
        });

        testDefinition.setTestSequenceGenerator(td -> {

            // In phase 1 we expect normal consensus
            final TestSequence phase1 = new TestSequence(eventsPerPhase).setMinimumConsensusRatio(0.8);

            final int numNonConsPartitionNodes = numPartitionedNodes.get();
            final int numConsPartitionNodes = numberOfNodes - numNonConsPartitionNodes;
            final double consNodeRatio = (double) numConsPartitionNodes / numberOfNodes;
            final double nonConsNodeRatio = (double) numNonConsPartitionNodes / numberOfNodes;

            // In phase 2, events created by the sub-quorum partition nodes should not reach consensus, so we set
            // the min and max consensus ratio relative to the number of nodes in the quorum partition.
            final TestSequence phase2 = new TestSequence(eventsPerPhase);
            phase2.setMinimumConsensusRatio(consNodeRatio * 0.8);
            // Some seeds cause the nodes in the quorum partition to create more than it's fair share of events,
            // so we allow a little more than the exact ratio of nodes in that partition
            phase2.setMaximumConsensusRatio(consNodeRatio * 1.1);
            // Many events in the sub-quorum partition will become stale. 0.15 is somewhat arbitrary.
            phase2.setMinimumStaleRatio(nonConsNodeRatio * 0.15);
            phase2.setMaximumStaleRatio(nonConsNodeRatio);

            // In phase 3 consensus should return to normal.
            final TestSequence phase3 = new TestSequence(eventsPerPhase).setMinimumConsensusRatio(0.8);

            return List.of(phase1, phase2, phase3);
        });

        testConsensus(testDefinition, iterations, seeds);
    }

    /**
     * Creates three test sequences:
     *
     * <ol>
     *     <li>fully connected network</li>
     *     <li>partitioned network such that one of the partitions cannot reach consensus</li>
     *     <li>fully connected network</li>
     * </ol>
     */
    public static List<TestSequence> subQuorumPartitionTestSequences(final ConsensusTestDefinition testDefinition) {
        final int eventsPerPhase = testDefinition.getEventsPerPhase();
        final int numberOfNodes = testDefinition.getNumberOfNodes();

        final Set<Integer> partitionedNodes = getSubStrongMinorityNodes(testDefinition.getNodeStakes());

        // All nodes talk to each other with equal probability
        final List<List<Double>> fullyConnected = createBalancedOtherParentMatrix(numberOfNodes);

        // Less than a strong minority of nodes' stake are partitioned from the network
        final List<List<Double>> partitioned =
                createPartitionedOtherParentAffinityMatrix(numberOfNodes, partitionedNodes);

        // Network is connected for a while, then is partitioned, then is connected for a while again.
        testDefinition
                .getNode1EventEmitter()
                .getGraphGenerator()
                .setOtherParentAffinity((Random r, long eventIndex, List<List<Double>> previousValue) -> {
                    if (eventIndex < eventsPerPhase) {
                        // phase 1
                        return fullyConnected;
                    } else if (eventIndex < 2L * eventsPerPhase) {
                        // phase 2
                        return partitioned;
                    } else {
                        // phase 3
                        return fullyConnected;
                    }
                });

        // In phase 1 we expect normal consensus
        final TestSequence phase1 = new TestSequence(eventsPerPhase).setMinimumConsensusRatio(0.8);

        final int numNonConsPartitionNodes = partitionedNodes.size();
        final int numConsPartitionNodes = numberOfNodes - numNonConsPartitionNodes;
        final double consNodeRatio = (double) numConsPartitionNodes / numberOfNodes;
        final double nonConsNodeRatio = (double) numNonConsPartitionNodes / numberOfNodes;

        // In phase 2, events created by the sub-quorum partition nodes should not reach consensus, so we set
        // the min and max consensus ratio relative to the number of nodes in the quorum partition.
        final TestSequence phase2 = new TestSequence(eventsPerPhase);
        phase2.setMinimumConsensusRatio(consNodeRatio * 0.8);
        // Some seeds cause the nodes in the quorum partition to create more than it's fair share of events,
        // so we allow a little more than the exact ratio of nodes in that partition
        phase2.setMaximumConsensusRatio(consNodeRatio * 1.1);
        // Many events in the sub-quorum partition will become stale. 0.15 is somewhat arbitrary.
        phase2.setMinimumStaleRatio(nonConsNodeRatio * 0.15);
        phase2.setMaximumStaleRatio(nonConsNodeRatio);

        // In phase 3 consensus should return to normal.
        final TestSequence phase3 = new TestSequence(eventsPerPhase).setMinimumConsensusRatio(0.8);

        return List.of(phase1, phase2, phase3);
    }

    /**
     * Get a set of node ids such that their stake is at least a strong minority but not a super majority. Each group of
     * nodes (the partitioned node and non-partitions nodes) has a strong minority.
     *
     * @param nodeStakes
     * 		the stakes of each node in the network
     * @return the list of node ids
     */
    private static Set<Integer> getStrongMinorityNodes(final List<Long> nodeStakes) {
        final Set<Integer> partitionedNodes = new HashSet<>();
        final long totalStake = nodeStakes.stream().reduce(0L, Long::sum);
        long partitionedStake = 0L;
        for (int i = 0; i < nodeStakes.size(); i++) {
            // If we have enough partitioned nodes to make a strong minority, stop and return
            if (Utilities.isStrongMinority(partitionedStake, totalStake)) {
                break;
            }
            // If adding this node to the partition would give the partition a super majority, skip this node because
            // the remaining group of nodes would not have a strong minority
            if (Utilities.isSuperMajority(partitionedStake + nodeStakes.get(i), totalStake)) {
                continue;
            }
            partitionedNodes.add(i);
            partitionedStake += nodeStakes.get(i);
        }
        System.out.println("Partitioned nodes: " + partitionedNodes);
        System.out.printf(
                "\nPartition has %s (%s%%) of %s total stake.%n",
                partitionedStake, (((double) partitionedStake) / totalStake) * 100, totalStake);
        return partitionedNodes;
    }

    /**
     * Get a set of node ids such that their stake is less than a strong minority. Nodes not in the returned set will
     * have a super majority and can continue to reach consensus.
     *
     * @param nodeStakes
     * 		the stakes of each node in the network
     * @return the list of node ids
     */
    private static Set<Integer> getSubStrongMinorityNodes(final List<Long> nodeStakes) {
        final Set<Integer> partitionedNodes = new HashSet<>();
        final long totalStake = nodeStakes.stream().reduce(0L, Long::sum);
        long partitionedStake = 0L;
        for (int i = 0; i < nodeStakes.size(); i++) {
            // Leave at least two nodes not in the partition set so that gossip can continue in both
            // the partitioned nodes and non-partitioned nodes
            if (partitionedNodes.size() + 2 == nodeStakes.size()) {
                break;
            }
            // If adding this node to the partition would give the partition a strong minority, skip this node because
            // the remaining group of nodes would not have a super majority
            if (Utilities.isStrongMinority(partitionedStake + nodeStakes.get(i), totalStake)) {
                continue;
            }
            partitionedNodes.add(i);
            partitionedStake += nodeStakes.get(i);
        }
        System.out.println("Partitioned nodes: " + partitionedNodes);
        System.out.printf(
                "\nPartition has %s (%s%%) of %s total stake.%n",
                partitionedStake, (((double) partitionedStake) / totalStake) * 100, totalStake);
        return partitionedNodes;
    }

    public static void cliqueTests(
            final int numberOfNodes, final StakeGenerator stakeGenerator, final int iterations, final long... seeds) {
        final ConsensusTestDefinition testDefinition =
                new ConsensusTestDefinition("Clique Tests", numberOfNodes, stakeGenerator, 10_000);

        testDefinition.setGraphGeneratorProvider(nodeStakes -> {
            final List<EventSource<?>> eventSources = new ArrayList<>(nodeStakes.size());
            for (final Long nodeStake : nodeStakes) {
                eventSources.add(new StandardEventSource(nodeStake));
            }
            final StandardGraphGenerator generator = new StandardGraphGenerator(0, eventSources);

            // If the number of nodes is not divisible by 3 then the last clique will be slightly larger
            final int cliqueSize = numberOfNodes / 3;

            // A node to clique mapping
            final Map<Integer, Integer> cliques = new HashMap<>();
            for (int i = 0; i < cliqueSize; i++) {
                cliques.put(i, 0);
            }
            for (int i = cliqueSize; i < 2 * cliqueSize; i++) {
                cliques.put(i, 1);
            }
            for (int i = 2 * cliqueSize; i < numberOfNodes; i++) {
                cliques.put(i, 1);
            }

            // There are 3 cliques
            // Each clique syncs within itself frequently, but with outsiders it syncs rarely
            final List<List<Double>> affinity = createCliqueOtherParentMatrix(numberOfNodes, cliques);

            generator.setOtherParentAffinity(affinity);
            return generator;
        });

        testDefinition.setTestSequenceGenerator(td -> {
            // We expect for events to eventually reach consensus, but there may be a long lag between event
            // creation and consensus. This means that the minimum consensus ratio needs to be lower than usual.
            return List.of(new TestSequence(td.getEventsPerPhase())
                    .setMinimumConsensusRatio(0.7)
                    .setMaximumStaleRatio(0.05));
        });

        testConsensus(testDefinition, iterations, seeds);
    }

    public static void variableRateTests(
            final int numberOfNodes, final StakeGenerator stakeGenerator, final int iterations, final long... seeds) {

        final ConsensusTestDefinition testDefinition =
                new ConsensusTestDefinition("Variable Rate Tests", numberOfNodes, stakeGenerator, 10_000);

        // Set the event source generator to create variable rate event sources
        testDefinition.setGraphGeneratorProvider(nodeStakes -> {
            final DynamicValue<Double> variableEventWeight = (Random r, long eventIndex, Double previousValue) -> {
                if (previousValue == null) {
                    return 1.0;
                } else {
                    double value = previousValue;
                    final double nextDouble = r.nextDouble();
                    if (nextDouble < 0.1) {
                        // 10% chance that this node will speed up or slow down
                        // Nodes will never have a weight less than 0.1 though
                        final double nextGaussian = r.nextGaussian();
                        value = Math.max(0.1, previousValue + nextGaussian * 0.1);
                    }
                    return value;
                }
            };

            final List<EventSource<?>> eventSources = new ArrayList<>(nodeStakes.size());
            for (final long stake : nodeStakes) {
                eventSources.add(new StandardEventSource(stake).setNewEventWeight(variableEventWeight));
            }

            return new StandardGraphGenerator(0, eventSources);
        });

        testConsensus(testDefinition, iterations, seeds);
    }

    /**
     * One node has a tendency to use stale other parents.
     */
    public static void nodeUsesStaleOtherParents(
            final int numberOfNodes, final StakeGenerator stakeGenerator, final int iterations, final long... seeds) {

        final ConsensusTestDefinition testDefinition =
                new ConsensusTestDefinition("Node Uses Stale Other Parents", numberOfNodes, stakeGenerator, 10_000);

        final int staleNodeProvider = numberOfNodes - 1;
        final AtomicDouble minConsensusRatio = new AtomicDouble();

        testDefinition.setGraphGeneratorProvider(nodeStakes -> {
            final List<EventSource<?>> eventSources = new ArrayList<>(nodeStakes.size());
            for (final Long nodeStake : nodeStakes) {
                eventSources.add(new StandardEventSource(nodeStake));
            }
            final StandardGraphGenerator generator = new StandardGraphGenerator(0, eventSources);

            generator
                    .getSource(staleNodeProvider)
                    .setRecentEventRetentionSize(5000)
                    .setRequestedOtherParentAgeDistribution(integerPowerDistribution(0.002, 300));

            // set low to avoid false failures
            minConsensusRatio.set(0.3);

            return generator;
        });

        testDefinition.setTestSequenceGenerator(td -> List.of(new TestSequence(td.getEventsPerPhase())
                .setMinimumConsensusRatio(minConsensusRatio.get())
                .setMaximumStaleRatio(0.2)));

        testConsensus(testDefinition, iterations, seeds);
    }

    /**
     * One node has a tendency to provide stale other parents (when they are requested).
     */
    public static void nodeProvidesStaleOtherParents(
            final int numberOfNodes, final StakeGenerator stakeGenerator, final int iterations, final long... seeds) {

        final ConsensusTestDefinition testDefinition =
                new ConsensusTestDefinition("Node Provides Stale Other Parents", numberOfNodes, stakeGenerator, 10_000);

        final int staleNodeProvider = numberOfNodes - 1;
        final AtomicDouble minConsensusRatio = new AtomicDouble();
        final AtomicDouble minStaleRatio = new AtomicDouble();

        testDefinition.setGraphGeneratorProvider(nodeStakes -> {
            final List<EventSource<?>> eventSources = new ArrayList<>(nodeStakes.size());
            for (final Long nodeStake : nodeStakes) {
                eventSources.add(new StandardEventSource(nodeStake));
            }
            final StandardGraphGenerator generator = new StandardGraphGenerator(0, eventSources);

            generator
                    .getSource(staleNodeProvider)
                    .setRecentEventRetentionSize(5000)
                    .setProvidedOtherParentAgeDistribution(integerPowerDistribution(0.002, 300));

            /*
            If the node providing old events as other parents has a strong minority of stake, rounds become very
            large because many more events are required to strongly see witnesses. Larger rounds means fewer stale
            events. Possibly no stale events at all if there are not enough events to create enough rounds so that
            generations are considered ancient.
            */
            minConsensusRatio.set(0.2);
            minStaleRatio.set(0.0);

            return generator;
        });

        testDefinition.setTestSequenceGenerator(td -> List.of(new TestSequence(td.getEventsPerPhase())
                .setMinimumStaleRatio(minStaleRatio.get())
                .setMaximumStaleRatio(0.3)
                .setMinimumConsensusRatio(minConsensusRatio.get())
                .setMaximumConsensusRatio(0.95)));

        testConsensus(testDefinition, iterations, seeds);
    }

    /**
     * This test simulates a large number of nodes.
     */
    public static void manyNodeTests(final StakeGenerator stakeGenerator, final int iterations) {
        final int numberOfNodes = 50;

        final ConsensusTestDefinition testDefinition =
                new ConsensusTestDefinition("Many Node Tests", numberOfNodes, stakeGenerator, 10_000);

        // It takes a lot longer for 50 nodes to reach consensus, so don't expect the usual high ratio
        // If the number of events is significantly increased then this ratio is expected to approach 1.0.
        testDefinition.setTestSequenceGenerator(
                td -> List.of(new TestSequence(td.getEventsPerPhase()).setMinimumConsensusRatio(0.6)));

        testConsensus(testDefinition, iterations);
    }

    /**
     * This test simulates a small number of nodes.
     */
    public static void fewNodesTests(final StakeGenerator stakeGenerator, final int iterations) {
        final int numberOfNodes = 2;

        final ConsensusTestDefinition testDefinition =
                new ConsensusTestDefinition("Few Nodes Tests", numberOfNodes, stakeGenerator, 10_000);

        testConsensus(testDefinition, iterations);
    }

    /**
     * A quorum of nodes stop producing events, thus preventing consensus and round created advancement
     */
    public static void quorumOfNodesGoDownTests(
            final int numberOfNodes, final StakeGenerator stakeGenerator, final int iterations, final long... seeds) {

        final int eventsPerPhase = 3_000;

        final ConsensusTestDefinition testDefinition =
                new ConsensusTestDefinition("Quorum Goes Down Tests", numberOfNodes, stakeGenerator, eventsPerPhase);

        testDefinition.setGraphGeneratorProvider(nodeStakes -> {
            final DynamicValue<Double> disconnectingNodeWeight =
                    (Random random, long eventIndex, Double previousValue) -> {
                        if (eventIndex < eventsPerPhase) {
                            return 1.0;
                        } else if (eventIndex < 2 * eventsPerPhase) {
                            return 0.0;
                        } else {
                            return 1.0;
                        }
                    };

            final Set<Integer> quorumNodeIds = getStrongMinorityNodes(nodeStakes);

            final List<EventSource<?>> eventSources = new ArrayList<>(nodeStakes.size());
            for (int i = 0; i < nodeStakes.size(); i++) {
                final StandardEventSource source = new StandardEventSource(nodeStakes.get(i));
                if (quorumNodeIds.contains(i)) {
                    source.setNewEventWeight(disconnectingNodeWeight);
                }
                eventSources.add(source);
            }

            return new StandardGraphGenerator(0, eventSources);
        });

        testDefinition.setTestSequenceGenerator(td -> {

            // In phase 1 we expect normal consensus
            // There are fewer events that normal (10_000 is standard) so it is possible that fewer events will reach
            // consensus than expected by the default minimum ratio.
            final TestSequence phase1 = new TestSequence(eventsPerPhase).setMinimumConsensusRatio(0.8);

            // In phase 2, no events should reach consensus and should have a created round no higher than the max
            // of its parents' created rounds
            final TestSequence phase2 = new TestSequence(eventsPerPhase)
                    .setMinimumConsensusRatio(0.0)
                    .setMaximumConsensusRatio(0.0);
            phase2.setCustomValidator(ConsensusTestDefinitions::createdRoundDoesNotAdvance);

            // In phase 3 we expect normal consensus.
            final TestSequence phase3 = new TestSequence(eventsPerPhase).setMinimumConsensusRatio(0.8);

            return List.of(phase1, phase2, phase3);
        });

        testConsensus(testDefinition, iterations, seeds);
    }

    /**
     * Verifies that the created round of new events does not advance when a quorum of nodes is down.
     *
     * @param consensusEvents
     * 		events that reached consensus in the test sequence
     * @param allEvents
     * 		all events created in the test sequence
     */
    private static void createdRoundDoesNotAdvance(
            final List<IndexedEvent> consensusEvents, final List<IndexedEvent> allEvents) {
        final long firstRoundInSequence = allEvents.get(0).getRoundCreated();
        final long secondRoundInSequence = firstRoundInSequence + 1;
        final long thirdRoundInSequence = secondRoundInSequence + 1;

        for (final IndexedEvent e : allEvents) {
            final long roundCreated = e.getRoundCreated();

            // Ignore the first three rounds of events in the sequence.
            // The first round may have events from the prior sequence which has a quorum
            // The second round's events could still be strongly seen by witnesses in the previous round

            // It is possible for a third round to be created if the nodes that went down created events
            // in secondRoundInSequence in the previous sequence. I.e. the nodes that were still running
            // in this sequence created some events that had a created round of one less than the last events
            // created by the crashed nodes.
            if (roundCreated == firstRoundInSequence
                    || roundCreated == secondRoundInSequence
                    || roundCreated == thirdRoundInSequence) {
                continue;
            }

            final long spRound =
                    e.getSelfParent() == null ? -1 : e.getSelfParent().getRoundCreated();
            final long opRound =
                    e.getOtherParent() == null ? -1 : e.getOtherParent().getRoundCreated();
            assertEquals(
                    Math.max(spRound, opRound),
                    roundCreated,
                    String.format(
                            "Created round of event %s should not advance when a quorum of nodes are down.\n"
                                    + "created round: %s, sp created round: %s, op created round: %s",
                            e, roundCreated, spRound, opRound));
        }
    }

    /**
     * less than a quorum stop producing events, consensus proceeds as normal
     */
    public static void subQuorumOfNodesGoDownTests(
            final int numberOfNodes, final StakeGenerator stakeGenerator, final int iterations, final long... seeds) {

        final int eventsPerPhase = 3_000;

        final ConsensusTestDefinition testDefinition = new ConsensusTestDefinition(
                "Sub Quorum of Nodes Go Down Tests", numberOfNodes, stakeGenerator, eventsPerPhase);

        testDefinition.setGraphGeneratorProvider(nodeStakes -> {
            final DynamicValue<Double> disconnectingNodeWeight =
                    (Random random, long eventIndex, Double previousValue) -> {
                        if (eventIndex < eventsPerPhase) {
                            return 1.0;
                        } else if (eventIndex < 2 * eventsPerPhase) {
                            return 0.0;
                        } else {
                            return 1.0;
                        }
                    };

            final Set<Integer> subQuorumNodesIds = getSubStrongMinorityNodes(nodeStakes);

            final List<EventSource<?>> eventSources = new ArrayList<>(nodeStakes.size());
            for (int i = 0; i < nodeStakes.size(); i++) {
                final StandardEventSource source = new StandardEventSource(nodeStakes.get(i));
                if (subQuorumNodesIds.contains(i)) {
                    source.setNewEventWeight(disconnectingNodeWeight);
                }
                eventSources.add(new StandardEventSource());
            }

            return new StandardGraphGenerator(0, eventSources);
        });

        testDefinition.setTestSequenceGenerator(td -> {
            // In phase 1 consensus happens as normal
            final TestSequence phase1 = new TestSequence(eventsPerPhase).setMinimumConsensusRatio(0.8);

            // Consensus continues without the nodes that shut down
            final TestSequence phase2 = new TestSequence(eventsPerPhase).setMinimumConsensusRatio(0.8);

            // Consensus continues as normal
            final TestSequence phase3 = new TestSequence(eventsPerPhase).setMinimumConsensusRatio(0.8);

            return List.of(phase1, phase2, phase3);
        });

        testConsensus(testDefinition, iterations, seeds);
    }

    /**
     * There should be no problems when the probability of events landing on the same timestamp is higher than usual.
     */
    public static void repeatedTimestampTest(
            final int numberOfNodes, final StakeGenerator stakeGenerator, final int iterations, final long... seeds) {
        final int eventsPerPhase = 10_000;

        final ConsensusTestDefinition testDefinition =
                new ConsensusTestDefinition("Repeated Timestamp Test", numberOfNodes, stakeGenerator, eventsPerPhase);

        testDefinition.setTestSequenceGenerator(
                td -> List.of(new TestSequence(eventsPerPhase).setMinimumConsensusRatio(0.3)));

        testDefinition.setGraphGeneratorProvider(stakes -> {
            final List<EventSource<?>> eventSources = newStandardEventSources(stakes);
            final StandardGraphGenerator generator = new StandardGraphGenerator(0, eventSources);
            generator.setSimultaneousEventFraction(0.5);
            return generator;
        });

        testConsensus(testDefinition, iterations, seeds);
    }

    /**
     * Simulates a consensus restart. The number of nodes and number of events is chosen randomly between the supplied
     * bounds
     *
     * @param seed
     * 		a seed to use for a random generator
     * @param stateDir
     * 		the directory where saved states can be written (this method uses merkle serialization)
     * @param minNodes
     * 		minimum number of nodes
     * @param maxNodes
     * 		maximum number of nodes
     * @param minPerSeq
     * 		minimum number of events to generate
     * @param maxPerSeq
     * 		maximum number of events to generate
     */
    public static void restart(
            final Long seed,
            final Path stateDir,
            final StakeGenerator stakeGenerator,
            final int minNodes,
            final int maxNodes,
            final int minPerSeq,
            final int maxPerSeq)
            throws ConstructableRegistryException, IOException {

        System.out.println("Consensus Restart Test");
        final Random random = initRandom(seed);

        final int numberOfNodes =
                random.ints(minNodes, maxNodes + 1).findFirst().orElseThrow();
        final int eventsPerSequence =
                random.ints(minPerSeq, maxPerSeq + 1).findFirst().orElseThrow();
        final long generatorSeed = random.nextLong();

        System.out.printf("Nodes:%d events:%d\n", numberOfNodes, eventsPerSequence);

        final List<Long> nodeStakes = stakeGenerator.getStakes(seed, numberOfNodes);
        final List<EventSource<?>> eventSources = newStandardEventSources(nodeStakes);
        final StandardGraphGenerator generator = new StandardGraphGenerator(generatorSeed, eventSources);

        final TestSequence sequence = new TestSequence(eventsPerSequence);
        sequence.setMinimumConsensusRatio(0);
        final ConsensusTestContext context = new ConsensusTestContext(
                generatorSeed, new StandardEventEmitter(generator), new StandardEventEmitter(generator));

        context.runSequence(sequence);
        context.restartAllNodes(stateDir);
        context.runSequence(sequence);
    }

    public static void areAllEventsReturned(final int numberOfNodes, final StakeGenerator stakeGenerator) {
        areAllEventsReturned(numberOfNodes, stakeGenerator, new Random().nextLong());
    }

    // TODO convert to new framework
    public static void areAllEventsReturned(
            final int numberOfNodes, final StakeGenerator stakeGenerator, final long seed) {
        final int numEventsBeforeExclude = 50000;
        final int numEventsAfterExclude = 50000;
        final int totalEvents = numEventsAfterExclude + numEventsBeforeExclude;

        final Map<Hash, EventImpl> eventsNotReturned = new HashMap<>();
        final Map<Hash, EventImpl> consEvents = new HashMap<>();
        final Map<Hash, EventImpl> staleEvents = new HashMap<>();

        System.out.println("areAllEventsReturned seed: " + seed);
        final Random random = new Random(seed);

        final List<Long> nodeStakes = stakeGenerator.getStakes(seed, numberOfNodes);

        final AddressBook ab = new RandomAddressBookGenerator(random)
                .setSequentialIds(true)
                .setSize(numberOfNodes)
                .setCustomStakeGenerator(id -> nodeStakes.get((int) id))
                .setHashStrategy(RandomAddressBookGenerator.HashStrategy.FAKE_HASH)
                .build();

        // create an empty intake object
        final TestIntake intake = new TestIntake(ab);

        final SimpleEventGenerator gen = new SimpleEventGenerator(numberOfNodes, random);

        final AtomicInteger numReturned = new AtomicInteger();

        addAndUpdate(numEventsBeforeExclude, gen, intake, eventsNotReturned, consEvents, staleEvents, numReturned);

        gen.excludeOtherParent(0);

        addAndUpdate(numEventsAfterExclude, gen, intake, eventsNotReturned, consEvents, staleEvents, numReturned);

        long numNotReachedCons = 0;

        final EventImpl[] allEvents = intake.getShadowGraph().getAllEvents();
        for (final EventImpl event : allEvents) {
            if (!event.isConsensus() && !event.isStale()) {
                numNotReachedCons++;
                eventsNotReturned.remove(event.getBaseHash());
            }
        }

        assertEquals(totalEvents, numReturned.get() + numNotReachedCons, "total number of events is incorrect");
    }

    private static void addAndUpdate(
            final int numEvents,
            final SimpleEventGenerator gen,
            final TestIntake intake,
            final Map<Hash, EventImpl> eventsNotReturned,
            final Map<Hash, EventImpl> consEvents,
            final Map<Hash, EventImpl> staleEvents,
            final AtomicInteger numReturned) {
        for (int i = 0; i < numEvents; i++) {
            final EventImpl event = gen.nextEvent();
            intake.addEvent(event);
            eventsNotReturned.put(event.getBaseHash(), event);
            assertTrue(
                    intake.getConsensus().getMinGenerationNonAncient() <= event.getGeneration(),
                    intake.getConsensus().getMinGenerationNonAncient() + " > " + event.getGeneration());
            while (!intake.getConsensusRounds().isEmpty()) {
                final ConsensusRound consensusRound =
                        intake.getConsensusRounds().poll();
                numReturned.addAndGet(consensusRound.getConsensusEvents().size());
                consensusRound.getConsensusEvents().forEach(e -> checkEventAdd(e, consEvents, staleEvents, true));
                consensusRound.getConsensusEvents().forEach(e -> eventsNotReturned.remove(e.getBaseHash()));
            }
            intake.getStaleEvents().forEach(e -> checkEventAdd(e, consEvents, staleEvents, false));
            intake.getStaleEvents().forEach(e -> eventsNotReturned.remove(e.getBaseHash()));
            numReturned.addAndGet(intake.getStaleEvents().size());
            intake.getStaleEvents().clear();
        }
    }

    private static void checkEventAdd(
            final EventImpl e,
            final Map<Hash, EventImpl> consEvents,
            final Map<Hash, EventImpl> staleEvents,
            final boolean consensus) {
        final boolean inCons = consEvents.containsKey(e.getBaseHash());
        final boolean inStale = staleEvents.containsKey(e.getBaseHash());

        if (inCons || inStale) {
            fail(String.format(
                    "%s event %s already returned as %s",
                    consensus ? "Consensus" : "Stale", e.toMediumString(), inCons ? "consensus" : "stale"));
        }
        if (consensus) {
            consEvents.put(e.getBaseHash(), e);
        } else {
            staleEvents.put(e.getBaseHash(), e);
        }
    }

    // TODO Finish and convert to new framework. As it is, this does not verify anything.
    public static void staleEvent(
            final int numberOfNodes, final StakeGenerator stakeGenerator, final int iterations, final long... seeds) {
        if (seeds != null) {
            for (final long seed : seeds) {
                System.out.println("Stale Event Tests, " + numberOfNodes + " nodes" + ": seed = " + seed + "L");
                doStaleEvent(numberOfNodes, stakeGenerator, seed);
            }
        }

        for (int j = 0; j < iterations; ++j) {
            System.out.println("Stale Event Tests");
            doStaleEvent(numberOfNodes, stakeGenerator, 0);
        }
    }

    private static void doStaleEvent(final int numberOfNodes, final StakeGenerator stakeGenerator, final long seed) {
        final int numEventsBeforeExclude = 1000;
        final int numEventsAfterExclude = 10000;
        final int numEventsAfterInclude = 1000;

        final long seedToUse = seed == 0 ? new Random().nextLong() : seed;
        final Random random = initRandom(seedToUse);

        final List<Long> nodeStakes = stakeGenerator.getStakes(seedToUse, numberOfNodes);
        final AddressBook ab = new RandomAddressBookGenerator(random)
                .setSequentialIds(true)
                .setSize(numberOfNodes)
                .setCustomStakeGenerator(id -> nodeStakes.get((int) id))
                .setHashStrategy(RandomAddressBookGenerator.HashStrategy.FAKE_HASH)
                .build();

        // create an empty consensus object
        final Consensus cons = buildSimpleConsensus(ab);

        final SimpleEventGenerator gen = new SimpleEventGenerator(numberOfNodes, random);

        for (int i = 0; i < numEventsBeforeExclude; i++) {
            cons.addEvent(gen.nextEvent(), ab);
        }

        gen.excludeOtherParent(0);

        for (int i = 0; i < numEventsAfterExclude; i++) {
            cons.addEvent(gen.nextEvent(), ab);
        }

        gen.includeOtherParent(0);

        for (int i = 0; i < numEventsAfterInclude; i++) {
            cons.addEvent(gen.nextEvent(), ab);
        }
    }

    // TODO convert to new framework
    public static void reconnectSimulation(
            final Path stateDir,
            final int numberOfNodes,
            final StakeGenerator stakeGenerator,
            final int iterations,
            final long... seeds) {

        if (seeds != null) {
            for (final long seed : seeds) {
                System.out.println("Reconnect Tests, " + numberOfNodes + " nodes" + ": seed = " + seed + "L");
                try {
                    doReconnectSimulation(numberOfNodes, stateDir, stakeGenerator, seed);
                } catch (final IOException | ConstructableRegistryException | SignedStateLoadingException e) {
                    e.printStackTrace();
                    throw new RuntimeException(e);
                }
            }
        }

        for (int i = 0; i < iterations; ++i) {
            System.out.println("Reconnect Tests");
            try {
                doReconnectSimulation(numberOfNodes, stateDir, stakeGenerator, 0);
            } catch (final IOException | ConstructableRegistryException | SignedStateLoadingException e) {
                e.printStackTrace();
                throw new RuntimeException(e);
            }
        }
    }

    private static void doReconnectSimulation(
            final int numberOfNodes, final Path stateDir, final StakeGenerator stakeGenerator, final long seed)
            throws IOException, ConstructableRegistryException, SignedStateLoadingException {
        final int numEventsBeforeRestart = 10000;
        final int numEventsAfterRestart = 10000;

        final long seedToUse = seed == 0 ? new Random().nextLong() : seed;
        final Random random = initRandom(seedToUse);

        final List<Long> nodeStakes = stakeGenerator.getStakes(seedToUse, numberOfNodes);
        final List<EventSource<?>> eventSources = EventSourceFactory.newStandardEventSources(nodeStakes);
        final StandardGraphGenerator generator = new StandardGraphGenerator(random.nextInt(), eventSources);
        final StandardEventEmitter emitter = new StandardEventEmitter(generator);

        final SignedStateEventsAndGenerations eventsAndGenerations =
                new SignedStateEventsAndGenerations(ConfigurationHolder.getConfigData(ConsensusConfig.class));
        // create an empty consensus object
        final Consensus origCons =
                new ConsensusWithShadowGraph(generator.getAddressBook(), eventsAndGenerations::addRoundGeneration);

        // feed some events into it and keep the last [roundsKeep] rounds of consensus events
        final List<IndexedEvent> lastRounds = applyEventsToConsensus(emitter, origCons, numEventsBeforeRestart);
        // We create a copy of the signed state like in a restart or reconnect to closely mimic reconnect
        final SignedState signedState = EventUtils.serializeDeserialize(
                stateDir, createSignedState(lastRounds, eventsAndGenerations, generator.getAddressBook()));

        // create a new consensus object with the consensus events the last one has saved
        final ConsensusWithShadowGraph restartCons =
                new ConsensusWithShadowGraph(generator.getAddressBook(), signedState);

        // now the restartCons object needs to get the events the original has that have not yet reached consensus
        // a second generator is used because the second consensus object needs the same events, but they must be
        // different objects in memory
        final Map<Hash, EventImpl> copiesMap = getEventMap(signedState.getEvents());
        // --------------- NOTE --------------
        // This will not work if there are any forks in the state events
        // -----------------------------------
        final long[] lastGenInState = getLastGenerationInState(signedState.getEvents(), numberOfNodes);
        final StandardEventEmitter restartEmitter = emitter.cleanCopy();
        for (int i = 0; i < numEventsBeforeRestart; i++) {
            final EventImpl event = restartEmitter.emitEvent();
            if (lastGenInState[(int) event.getCreatorId()] >= event.getGeneration()) {
                // we dont add events that are already in the state or older
                continue;
            }
            // these generated events are linked to parents that are not in the consensus object, we need to link them
            // to the appropriate objects
            final EventImpl sp = copiesMap.get(event.getSelfParentHash());
            if (sp != null) {
                event.setSelfParent(sp);
            }
            final EventImpl op = copiesMap.get(event.getOtherParentHash());
            if (op != null) {
                event.setOtherParent(op);
            }

            final List<EventImpl> events = restartCons.addEvent(event, generator.getAddressBook());
            assertNull(events, "we should not have reached consensus yet");
        }

        // now both consensus objects should be in the same state
        ConsensusUtils.checkGenerations(origCons, restartCons, true);
        // we will feed them the same events and expect the same output
        final List<SingleConsensusChecker> checkers = ConsensusUtils.getSingleConsensusCheckers();
        for (int i = 0; i < numEventsAfterRestart; i++) {

            assertTrue(
                    isRestartConsensusEquivalent(emitter, restartEmitter, origCons, restartCons, 1, checkers),
                    "expected restart consensus to be equivalent");
        }
    }
}
