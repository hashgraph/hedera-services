/*
 * Copyright (C) 2020-2024 Hedera Hashgraph, LLC
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

import static com.swirlds.platform.test.fixtures.event.EventUtils.integerPowerDistribution;
import static com.swirlds.platform.test.graph.OtherParentMatrixFactory.createBalancedOtherParentMatrix;
import static com.swirlds.platform.test.graph.OtherParentMatrixFactory.createCliqueOtherParentMatrix;
import static com.swirlds.platform.test.graph.OtherParentMatrixFactory.createPartitionedOtherParentAffinityMatrix;
import static com.swirlds.platform.test.graph.OtherParentMatrixFactory.createShunnedNodeOtherParentAffinityMatrix;

import com.swirlds.common.platform.NodeId;
import com.swirlds.common.utility.Threshold;
import com.swirlds.config.api.ConfigurationBuilder;
import com.swirlds.platform.consensus.ConsensusConfig;
import com.swirlds.platform.consensus.ConsensusSnapshot;
import com.swirlds.platform.consensus.SyntheticSnapshot;
import com.swirlds.platform.internal.EventImpl;
import com.swirlds.platform.test.consensus.framework.ConsensusTestNode;
import com.swirlds.platform.test.consensus.framework.ConsensusTestOrchestrator;
import com.swirlds.platform.test.consensus.framework.ConsensusTestUtils;
import com.swirlds.platform.test.consensus.framework.OrchestratorBuilder;
import com.swirlds.platform.test.consensus.framework.TestInput;
import com.swirlds.platform.test.consensus.framework.validation.EventRatioValidation;
import com.swirlds.platform.test.consensus.framework.validation.Validations;
import com.swirlds.platform.test.event.emitter.PriorityEventEmitter;
import com.swirlds.platform.test.event.emitter.StandardEventEmitter;
import com.swirlds.platform.test.event.source.ForkingEventSource;
import com.swirlds.platform.test.fixtures.event.DynamicValue;
import com.swirlds.platform.test.fixtures.event.generator.StandardGraphGenerator;
import com.swirlds.platform.test.fixtures.event.source.EventSource;
import com.swirlds.platform.test.fixtures.event.source.StandardEventSource;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.Spliterators;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.IntStream;
import java.util.stream.StreamSupport;

public final class ConsensusTestDefinitions {

    private ConsensusTestDefinitions() {}

    /**
     * Changing the order of events (without breaking topological order) should result in the same consensus events.
     */
    public static void orderInvarianceTests(@NonNull final TestInput input) {
        OrchestratorBuilder.builder()
                .setTestInput(input)
                .build()
                .generateAllEvents()
                .validateAndClear(Validations.standard()
                        .ratios(EventRatioValidation.standard()
                                .setMinimumConsensusRatio(0.9 - (0.05 * input.numberOfNodes()))));
    }

    /** Send an ancient event to consensus and check if it is marked stale. */
    public static void ancient(@NonNull final TestInput input) {
        // Setup: we use a priority emitter so that the dying node's events are added last, when
        // they are already ancient
        final List<Integer> nodePriorities =
                IntStream.range(0, input.numberOfNodes()).boxed().toList();
        final ConsensusTestOrchestrator orchestrator = OrchestratorBuilder.builder()
                .setTestInput(input)
                .setNode1EventEmitterGenerator(
                        (graphGenerator, seed) -> new PriorityEventEmitter(graphGenerator, nodePriorities))
                .setNode2EventEmitterGenerator((graphGenerator, seed) -> new StandardEventEmitter(graphGenerator))
                .build();
        final int dyingNode = input.numberOfNodes() - 1;

        // Phase 1: all nodes are working normally
        orchestrator.generateEvents(0.10);

        // Phase 2: one node shunned and is not used as an other-parent. it creates some more events
        // and this ensures
        // that it will have events without any descendants. this means that the priority emitter
        // can add them at the
        // end, after all other events
        orchestrator.setOtherParentAffinity(
                createShunnedNodeOtherParentAffinityMatrix(input.numberOfNodes(), dyingNode));
        orchestrator.generateEvents(0.10);

        // Phase 3: the shunned node creates stops creating events
        orchestrator.setNewEventWeight(dyingNode, 0d);
        orchestrator.generateEvents(0.70);

        orchestrator.validateAndClear(
                Validations.standard().ratios(EventRatioValidation.blank().setMinimumConsensusRatio(0.5)));
    }

    /** Test consensus in the presence of forks. */
    public static void forkingTests(@NonNull final TestInput input) {
        // Use a custom event source generator that creates forking event sources
        final Function<List<Long>, List<EventSource<?>>> eventSourceBuilder = nodeWeights -> {
            final double forkProbability = 0.1;
            final int numberOfForkedBranches = 10;
            final long totalWeight = nodeWeights.stream().reduce(0L, Long::sum);

            // Determine a single forking event source that has less than a strong minority
            // of weigh
            int forkingNodeId = -1;
            for (int i = 0; i < nodeWeights.size(); i++) {
                final long weight = nodeWeights.get(i);
                if (!Threshold.STRONG_MINORITY.isSatisfiedBy(weight, totalWeight)) {
                    forkingNodeId = i;
                    break;
                }
            }

            final List<EventSource<?>> eventSources = new ArrayList<>(nodeWeights.size());
            for (int i = 0; i < nodeWeights.size(); i++) {
                final long weight = nodeWeights.get(i);
                if (i == forkingNodeId) {
                    eventSources.add(new ForkingEventSource(weight)
                            .setForkProbability(forkProbability)
                            .setMaximumBranchCount(numberOfForkedBranches));
                } else {
                    eventSources.add(new StandardEventSource(weight));
                }
            }
            return eventSources;
        };

        OrchestratorBuilder.builder()
                .setTestInput(input)
                .setEventSourceBuilder(eventSourceBuilder)
                .build()
                .generateEvents(1.0)
                .validateAndClear(Validations.standard()
                        .ratios(EventRatioValidation.standard().setMaximumStaleRatio(0.1)));
    }

    /**
     * Consensus should handle a partition gracefully. Creates three test phases:
     *
     * <ol>
     *   <li>fully connected network
     *   <li>partitioned network such that one of the partitions has a strong minority
     *   <li>fully connected network
     * </ol>
     */
    public static void partitionTests(@NonNull final TestInput input) {
        // Test setup
        final ConsensusTestOrchestrator orchestrator =
                OrchestratorBuilder.builder().setTestInput(input).build();
        final List<List<Double>> fullyConnected = createBalancedOtherParentMatrix(input.numberOfNodes());
        final List<List<Double>> partitioned = createPartitionedOtherParentAffinityMatrix(
                input.numberOfNodes(), ConsensusTestUtils.getStrongMinorityNodes(orchestrator.getWeights()));

        //
        // Phase 1
        //
        // setup: All nodes talk to each other with equal probability
        orchestrator.setOtherParentAffinity(fullyConnected);
        // execution: generate a third of the total events
        orchestrator.generateEvents(0.33);
        // validation: we expect normal consensus
        orchestrator.validateAndClear(Validations.standard());

        //
        // Phase 2
        //
        // setup: >= 1/3 of nodes are partitioned from the rest of the network
        orchestrator.setOtherParentAffinity(partitioned);
        // execution: generate a third of the total events
        orchestrator.generateEvents(0.33);
        // validation: almost no events will reach consensus
        //   (it's possible a few tail events may reach consensus right at the beginning of the
        // phase)
        orchestrator.validateAndClear(Validations.standard()
                .ratios(EventRatioValidation.standard()
                        .setMinimumConsensusRatio(0.0)
                        .setMaximumConsensusRatio(0.5)));

        //
        // Phase 3
        //
        // setup: All nodes talk to each other again
        orchestrator.setOtherParentAffinity(fullyConnected);
        // execution: generate a third of the total events
        orchestrator.generateEvents(0.34);
        // validation: we expect for phase 2 and phase 3 events to reach consensus
        orchestrator.validateAndClear(Validations.standard()
                .ratios(EventRatioValidation.standard()
                        .setMinimumConsensusRatio(0.8)
                        .setMaximumConsensusRatio(2.1)));
    }

    /**
     * Simulates a partition where one partition has a quorum.
     *
     * <ol>
     *   <li>fully connected network
     *   <li>partitioned network such that one of the partitions cannot reach consensus
     *   <li>fully connected network
     * </ol>
     */
    public static void subQuorumPartitionTests(@NonNull final TestInput input) {
        // Network is connected for a while, then is partitioned, then is connected for a while
        // again.
        final ConsensusTestOrchestrator orchestrator =
                OrchestratorBuilder.builder().setTestInput(input).build();
        final List<List<Double>> fullyConnected = createBalancedOtherParentMatrix(input.numberOfNodes());
        final Set<Integer> partitionedNodes = ConsensusTestUtils.getSubStrongMinorityNodes(orchestrator.getWeights());
        final int numPartitionedNodes = partitionedNodes.size();
        // Less than a strong minority of nodes' weigh are partitioned from the network
        final List<List<Double>> partitioned =
                createPartitionedOtherParentAffinityMatrix(input.numberOfNodes(), partitionedNodes);
        final int numConsPartitionNodes = input.numberOfNodes() - numPartitionedNodes;
        final double consNodeRatio = (double) numConsPartitionNodes / input.numberOfNodes();
        final double nonConsNodeRatio = (double) numPartitionedNodes / input.numberOfNodes();

        // In phase 1 we expect normal consensus
        orchestrator.setOtherParentAffinity(fullyConnected);
        orchestrator.generateEvents(0.33);
        orchestrator.validateAndClear(Validations.standard());

        // In phase 2, events created by the sub-quorum partition nodes should not reach consensus,
        // so we set
        // the min and max consensus ratio relative to the number of nodes in the quorum partition.
        orchestrator.setOtherParentAffinity(partitioned);
        orchestrator.generateEvents(0.33);
        orchestrator.validateAndClear(Validations.standard()
                .ratios(EventRatioValidation.standard()
                        .setMinimumConsensusRatio(consNodeRatio * 0.8)
                        // Some seeds cause the nodes in the quorum partition to
                        // create more than it's fair
                        // share of events, so we allow a little more than the exact
                        // ratio of nodes in that
                        // partition
                        .setMaximumConsensusRatio(consNodeRatio * 1.5)
                        // Many events in the sub-quorum partition will become
                        // stale. 0.15 is somewhat
                        // arbitrary.
                        .setMinimumStaleRatio(nonConsNodeRatio * 0.15)
                        .setMaximumStaleRatio(nonConsNodeRatio)));

        // In phase 3 consensus should return to normal.
        orchestrator.setOtherParentAffinity(fullyConnected);
        orchestrator.generateEvents(0.34);
        orchestrator.validateAndClear(Validations.standard());
    }

    public static void cliqueTests(@NonNull final TestInput input) {
        final int numberOfNodes = input.numberOfNodes();
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

        final ConsensusTestOrchestrator orchestrator =
                OrchestratorBuilder.builder().setTestInput(input).build();
        orchestrator.setOtherParentAffinity(affinity);

        orchestrator.generateAllEvents();
        orchestrator.validateAndClear(Validations.standard()
                .ratios(EventRatioValidation.standard()
                        // We expect for events to eventually reach consensus, but
                        // there may be a long lag
                        // between event creation and consensus. This means that the
                        // minimum consensus ratio
                        // needs to be lower than usual.
                        .setMinimumConsensusRatio(0.7)
                        .setMaximumStaleRatio(0.05)));
    }

    public static void variableRateTests(@NonNull final TestInput input) {
        // Set the event source generator to create variable rate event sources
        final Consumer<EventSource<?>> configureVariable = es -> {
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
            es.setNewEventWeight(variableEventWeight);
        };

        OrchestratorBuilder.builder()
                .setTestInput(input)
                .setEventSourceConfigurator(configureVariable)
                .build()
                .generateAllEvents()
                .validateAndClear(Validations.standard());
    }

    /** One node has a tendency to use stale other parents. */
    public static void usesStaleOtherParents(@NonNull final TestInput input) {
        final ConsensusTestOrchestrator orchestrator =
                OrchestratorBuilder.builder().setTestInput(input).build();
        orchestrator.configGenerators(g -> {
            // Setup: pick one node to use stale other-parents
            final NodeId staleNodeProvider = g.getAddressBook().getNodeId(0);
            g.getSource(staleNodeProvider)
                    .setRecentEventRetentionSize(5000)
                    .setRequestedOtherParentAgeDistribution(integerPowerDistribution(0.002, 300));
        });
        orchestrator.generateAllEvents();
        orchestrator.validateAndClear(Validations.standard()
                .ratios(EventRatioValidation.standard()
                        .setMinimumConsensusRatio(0.3)
                        .setMaximumStaleRatio(0.2)));
    }

    /** One node has a tendency to provide stale other parents (when they are requested). */
    public static void providesStaleOtherParents(@NonNull final TestInput input) {
        final ConsensusTestOrchestrator orchestrator =
                OrchestratorBuilder.builder().setTestInput(input).build();
        // Setup: pick one node to provide stale other-parents
        // The node's weight should be less than a strong minority so that we can reach consensus
        final NodeId staleParentProvider = StreamSupport.stream(
                        Spliterators.spliteratorUnknownSize(
                                orchestrator.getAddressBook().iterator(), 0),
                        false)
                .filter(a -> !Threshold.STRONG_MINORITY.isSatisfiedBy(
                        a.getWeight(), orchestrator.getAddressBook().getTotalWeight()))
                .findFirst()
                .orElseThrow()
                .getNodeId();
        Objects.requireNonNull(staleParentProvider, "Could not find a node with less than a strong minority of weight");
        orchestrator.configGenerators(g -> g.getSource(staleParentProvider)
                .setRecentEventRetentionSize(5000)
                .setProvidedOtherParentAgeDistribution(integerPowerDistribution(0.002, 300)));
        orchestrator.generateAllEvents();
        /* If the node providing old events as other parents has a strong minority of weigh, rounds become very
        large because many more events are required to strongly see witnesses. Larger rounds means fewer stale
        events. Possibly no stale events at all if there are not enough events to create enough rounds so that
        generations are considered ancient. */
        orchestrator.validateAndClear(
                Validations.standard().ratios(EventRatioValidation.blank().setMinimumConsensusRatio(0.2)));
    }

    /**
     * A quorum of nodes stop producing events, thus preventing consensus and round created advancement
     */
    public static void quorumOfNodesGoDown(@NonNull final TestInput input) {
        // Test setup
        final ConsensusTestOrchestrator orchestrator =
                OrchestratorBuilder.builder().setTestInput(input).build();
        final Set<Integer> quorumNodeIds = ConsensusTestUtils.getStrongMinorityNodes(orchestrator.getWeights());

        //
        // Phase 1
        //
        // execution: generate a third of the total events
        orchestrator.generateEvents(0.33);
        // validation: we expect normal consensus
        orchestrator.validateAndClear(Validations.standard());

        //
        // Phase 2
        //
        // setup: >= 1/3 of nodes stop creating events
        for (final Integer quorumNodeId : quorumNodeIds) {
            orchestrator.setNewEventWeight(quorumNodeId, 0d);
        }
        // execution: generate a third of the total events
        orchestrator.generateEvents(0.33);
        // validation: almost no events will reach consensus
        //   (it's possible a few tail events may reach consensus right at the beginning of the phase)
        orchestrator.validateAndClear(Validations.standard()
                // in this test, only 1 node could end up creating events, which means they have to be added in the same
                // order, so we disable this validation for this test
                .remove(Validations.ValidationType.DIFFERENT_ORDER)
                .ratios(EventRatioValidation.standard()
                        .setMinimumConsensusRatio(0.0)
                        .setMaximumConsensusRatio(0.2)));

        //
        // Phase 3
        //
        // setup: All nodes start creating events again
        for (final Integer quorumNodeId : quorumNodeIds) {
            orchestrator.setNewEventWeight(quorumNodeId, 1d);
        }
        // execution: generate a third of the total events
        orchestrator.generateEvents(0.34);
        // validation: we expect normal consensus
        orchestrator.validateAndClear(Validations.standard());
    }

    /** less than a quorum stop producing events, consensus proceeds as normal */
    public static void subQuorumOfNodesGoDown(@NonNull final TestInput input) {
        // Test setup
        final ConsensusTestOrchestrator orchestrator =
                OrchestratorBuilder.builder().setTestInput(input).build();
        final Set<Integer> subQuorumNodesIds = ConsensusTestUtils.getSubStrongMinorityNodes(orchestrator.getWeights());

        //
        // Phase 1
        //
        // execution: generate a third of the total events
        orchestrator.generateEvents(0.33);
        // validation: we expect normal consensus
        orchestrator.validateAndClear(Validations.standard());

        //
        // Phase 2
        //
        // setup: < 1/3 of nodes stop creating events
        for (final Integer id : subQuorumNodesIds) {
            orchestrator.setNewEventWeight(id, 0d);
        }
        // execution: generate a third of the total events
        orchestrator.generateEvents(0.33);
        // validation: Consensus continues without the nodes that shut down
        orchestrator.validateAndClear(Validations.standard());

        //
        // Phase 3
        //
        // setup: All nodes start creating events again
        for (final Integer id : subQuorumNodesIds) {
            orchestrator.setNewEventWeight(id, 1d);
        }
        // execution: generate a third of the total events
        orchestrator.generateEvents(0.34);
        // validation: we expect normal consensus
        orchestrator.validateAndClear(Validations.standard());
    }

    /**
     * There should be no problems when the probability of events landing on the same timestamp is higher than usual.
     */
    public static void repeatedTimestampTest(@NonNull final TestInput input) {
        OrchestratorBuilder.builder()
                .setTestInput(input)
                .build()
                .configGenerators(g -> ((StandardGraphGenerator) g).setSimultaneousEventFraction(0.5))
                .generateAllEvents()
                .validateAndClear(Validations.standard()
                        .ratios(EventRatioValidation.standard().setMinimumConsensusRatio(0.3)));
    }

    public static void stale(@NonNull final TestInput input) {
        // setup
        final ConsensusTestOrchestrator orchestrator =
                OrchestratorBuilder.builder().setTestInput(input).build();

        // Phase 1: all nodes are used as other parents
        orchestrator.generateEvents(0.1);

        // Phase 2: node 0 is never used as an other-parent
        orchestrator.setOtherParentAffinity(createShunnedNodeOtherParentAffinityMatrix(input.numberOfNodes(), 0));
        orchestrator.generateEvents(0.8);

        // Phase 3: all nodes are used as other-parents, again
        orchestrator.setOtherParentAffinity(createBalancedOtherParentMatrix(input.numberOfNodes()));
        orchestrator.generateEvents(0.1);
        orchestrator.validateAndClear(Validations.standard()
                .ratios(EventRatioValidation.blank()
                        // if the shunned node has a lot of weigh, not many events
                        // will reach consensus
                        .setMinimumConsensusRatio(0.1)
                        .setMinimumStaleRatio(0.1)));
    }

    /**
     * Simulates a consensus restart. The number of nodes and number of events is chosen randomly between the supplied
     * bounds
     */
    public static void restart(@NonNull final TestInput input) {

        final ConsensusTestOrchestrator orchestrator =
                OrchestratorBuilder.builder().setTestInput(input).build();

        orchestrator.generateEvents(0.5);
        orchestrator.validate(
                Validations.standard().ratios(EventRatioValidation.blank().setMinimumConsensusRatio(0.5)));
        orchestrator.restartAllNodes();
        orchestrator.clearOutput();
        orchestrator.generateEvents(0.5);
        orchestrator.validateAndClear(
                Validations.standard().ratios(EventRatioValidation.blank().setMinimumConsensusRatio(0.5)));
    }

    /** Simulates a reconnect */
    public static void reconnect(@NonNull final TestInput input) {
        final ConsensusTestOrchestrator orchestrator =
                OrchestratorBuilder.builder().setTestInput(input).build();

        orchestrator.generateEvents(0.5);
        orchestrator.validate(
                Validations.standard().ratios(EventRatioValidation.blank().setMinimumConsensusRatio(0.5)));
        orchestrator.addReconnectNode(input.platformContext());

        orchestrator.clearOutput();
        orchestrator.generateEvents(0.5);
        orchestrator.validateAndClear(
                Validations.standard().ratios(EventRatioValidation.blank().setMinimumConsensusRatio(0.5)));
    }

    public static void removeNode(@NonNull final TestInput input) {
        final ConsensusTestOrchestrator orchestrator1 =
                OrchestratorBuilder.builder().setTestInput(input).build();
        orchestrator1.generateEvents(0.5);
        orchestrator1.validate(
                Validations.standard().ratios(EventRatioValidation.blank().setMinimumConsensusRatio(0.5)));

        final ConsensusTestOrchestrator orchestrator2 = OrchestratorBuilder.builder()
                .setTestInput(input.setNumberOfNodes(input.numberOfNodes() - 1))
                .build();
        for (int i = 0; i < 2; i++) {
            orchestrator2
                    .getNodes()
                    .get(i)
                    .getIntake()
                    .loadSnapshot(orchestrator1
                            .getNodes()
                            .get(i)
                            .getIntake()
                            .getLatestRound()
                            .getSnapshot());
            final int fi = i;
            orchestrator1.getNodes().get(i).getOutput().getAddedEvents().forEach(e -> {
                orchestrator2.getNodes().get(fi).getIntake().addEvent(e.copyGossipedData());
            });
            ConsensusUtils.loadEventsIntoGenerator(
                    orchestrator1.getNodes().get(i).getOutput().getAddedEvents(),
                    orchestrator2.getNodes().get(i).getEventEmitter().getGraphGenerator(),
                    orchestrator2.getNodes().get(i).getRandom());
        }

        orchestrator2.generateEvents(0.5);
        orchestrator2.validate(
                // this used to be set to 0.5, but then a test failed because it had a ratio of 0.4999
                // the number are a bit arbitrary, but the goal is to validate that events are reaching consensus
                Validations.standard().ratios(EventRatioValidation.blank().setMinimumConsensusRatio(0.4)));
    }

    public static void syntheticSnapshot(@NonNull final TestInput input) {
        final long round = 100;
        final long lastConsensusOrder = 4000;

        final ConsensusTestOrchestrator orchestrator =
                OrchestratorBuilder.builder().setTestInput(input).build();
        final Instant snapshotTimestamp = Instant.now();
        orchestrator.getNodes().forEach(n -> {
            final int numEvents = orchestrator.getEventFraction(0.5);
            n.getEventEmitter().setCheckpoint(numEvents);
            final List<EventImpl> events = n.getEventEmitter().emitEvents(numEvents);
            n.getEventEmitter().reset();
            final Optional<EventImpl> maxGenEvent = events.stream()
                    .max(Comparator.comparingLong(EventImpl::getGeneration).thenComparing(EventImpl::getCreatorId));
            final ConsensusSnapshot syntheticSnapshot = SyntheticSnapshot.generateSyntheticSnapshot(
                    round,
                    lastConsensusOrder,
                    snapshotTimestamp,
                    ConfigurationBuilder.create()
                            .withConfigDataType(ConsensusConfig.class)
                            .build()
                            .getConfigData(ConsensusConfig.class),
                    maxGenEvent.orElseThrow().getBaseEvent());
            n.getIntake().loadSnapshot(syntheticSnapshot);
        });

        orchestrator.generateEvents(0.5);
        orchestrator.validateAndClear(Validations.standard()
                .ratios(EventRatioValidation.blank().setMaximumConsensusRatio(0))
                // only 1 event will actually be added, that is the judge, so there can be no variation in the order
                .remove(Validations.ValidationType.DIFFERENT_ORDER));

        orchestrator.generateEvents(0.5);
        orchestrator.validate(
                Validations.standard().ratios(EventRatioValidation.blank().setMinimumConsensusRatio(0.8)));
    }

    /**
     * Tests loading a genesis snapshot and continuing consensus from there
     */
    public static void genesisSnapshotTest(@NonNull final TestInput input) {
        final ConsensusTestOrchestrator orchestrator =
                OrchestratorBuilder.builder().setTestInput(input).build();
        for (final ConsensusTestNode node : orchestrator.getNodes()) {
            node.getIntake().loadSnapshot(SyntheticSnapshot.getGenesisSnapshot());
        }

        orchestrator
                .generateAllEvents()
                .validateAndClear(Validations.standard()
                        .ratios(EventRatioValidation.standard()
                                .setMinimumConsensusRatio(0.9 - (0.05 * input.numberOfNodes()))));
    }
}
