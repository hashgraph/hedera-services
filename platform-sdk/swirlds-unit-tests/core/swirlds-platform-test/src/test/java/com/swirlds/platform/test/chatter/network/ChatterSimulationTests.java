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

package com.swirlds.platform.test.chatter.network;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.swirlds.common.system.NodeId;
import com.swirlds.common.system.address.AddressBook;
import com.swirlds.common.test.RandomAddressBookGenerator;
import com.swirlds.common.test.fixtures.FakeTime;
import com.swirlds.platform.chatter.protocol.processing.ProcessingTimeMessage;
import com.swirlds.platform.test.chatter.network.framework.ChatterInstance;
import com.swirlds.platform.test.chatter.network.framework.Network;
import com.swirlds.platform.test.chatter.network.framework.NetworkSimulator;
import com.swirlds.platform.test.chatter.network.framework.NetworkSimulatorParams;
import com.swirlds.platform.test.chatter.network.framework.Node;
import com.swirlds.platform.test.chatter.network.framework.SimulatedEventPipeline;
import com.swirlds.platform.test.chatter.network.framework.SimulatedEventPipelineBuilder;
import com.swirlds.platform.test.simulated.NetworkLatency;
import com.swirlds.platform.test.simulated.SimpleSimulatedGossip;
import com.swirlds.platform.test.simulated.config.MapBuilder;
import com.swirlds.platform.test.simulated.config.NetworkConfig;
import com.swirlds.platform.test.simulated.config.NodeConfig;
import com.swirlds.platform.test.simulated.config.NodeConfigBuilder;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class ChatterSimulationTests {

    private static final Duration FIRST_PHASE_DURATION = Duration.ofMillis(500);
    private static final Duration TOTAL_TEST_DURATION = Duration.ofSeconds(1);
    private static final Duration SLOW_NODE_INTAKE_DELAY = Duration.ofMillis(100);
    private static final Duration FAST_NODE_INTAKE_DELAY = Duration.ofMillis(0);
    private static final Duration DEFAULT_MAX_DELAY = Duration.ofMillis(240);
    private static final Duration DEFAULT_SIMULATION_TIME = Duration.ofSeconds(1);
    private static final Duration DEFAULT_SIMULATION_STEP = Duration.ofMillis(10);


    @Test
    @DisplayName(
            "Test that event processing time is always equal to the intake queue delay, and does not include orphan buffer wait time")
    void testProcessingTimeEqualsIntakeQueueDelay() {
        final FakeTime time = new FakeTime();

        // Three node network, 1 node with slow event processing, 2 nodes with fast event processing
        final Map<NodeId, NodeConfig> initialConfigs = MapBuilder.builder(NodeConfig.class)
                .useElement(NodeConfigBuilder.builder()
                        .setIntakeQueueDelay(SLOW_NODE_INTAKE_DELAY)
                        .setCreateEventEvery(Duration.ofMillis(100))
                        .build())
                .times(3)
                .build();

        final NetworkConfig initialNetworkConfig =
                new NetworkConfig("Initial Phase", FIRST_PHASE_DURATION, initialConfigs);

        final NetworkSimulatorParams params = new NetworkSimulatorParams(
                1,
                time,
                List.of(initialNetworkConfig),
                DEFAULT_MAX_DELAY,
                DEFAULT_SIMULATION_TIME,
                DEFAULT_SIMULATION_STEP);

        final Network<CountingChatterEvent> network = createCountingEventNetwork(params);

        // Execute the test
        NetworkSimulator.executeNetworkSimulation(network, params);

        network.printNetworkState();

        // Assert that all processing time messages are equal to the configured intake queue delay.
        // Time spent in the orphan buffer should not be included.
        network.nodeIds().forEach(id -> {
            network.gossip().getDeliveredTo(id, ProcessingTimeMessage.class).forEach(procMsg -> {
                assertEquals(SLOW_NODE_INTAKE_DELAY.toNanos(), procMsg.getProcessingTimeInNanos());
            });
        });
    }

    @Test
    void testPhases() {
        final FakeTime time = new FakeTime();

        // Three node network, 1 node with slow event processing, 2 nodes with fast event processing
        final Map<NodeId, NodeConfig> initialConfigs = MapBuilder.builder(NodeConfig.class)
                .useElement(NodeConfigBuilder.builder()
                        //                        .setIntakeQueueDelay(SLOW_NODE_INTAKE_DELAY)
                        .setIntakeQueueDelay(FAST_NODE_INTAKE_DELAY)
                        .build())
                .times(1)
                .useElement(NodeConfigBuilder.builder()
                        .setIntakeQueueDelay(FAST_NODE_INTAKE_DELAY)
                        .build())
                .times(2)
                .build();

        final NetworkConfig initialNetworkConfig =
                new NetworkConfig("Initial Phase", FIRST_PHASE_DURATION, initialConfigs);

        final NodeConfig nodeToSlowDown = initialConfigs.get(NodeId.createMain(0L));
        final Map<NodeId, NodeConfig> oneSlowNode = MapBuilder.builder(NodeConfig.class)
                .useElement(NodeConfigBuilder.builder(nodeToSlowDown)
                        .setIntakeQueueDelay(SLOW_NODE_INTAKE_DELAY)
                        .build())
                .times(1)
                .build();
        final NetworkConfig oneSlowNodeNetworkConfig =
                new NetworkConfig("Second Phase", TOTAL_TEST_DURATION.minus(FIRST_PHASE_DURATION), oneSlowNode);

        final NetworkSimulatorParams params = new NetworkSimulatorParams(
                1,
                time,
                List.of(initialNetworkConfig, oneSlowNodeNetworkConfig),
                DEFAULT_MAX_DELAY,
                DEFAULT_SIMULATION_TIME,
                DEFAULT_SIMULATION_STEP);

        final Network<CountingChatterEvent> network = createCountingEventNetwork(params);

        // Execute the test
        NetworkSimulator.executeNetworkSimulation(network, params);

        network.printNetworkState();
    }

    /**
     * Creates a network that gossips {@link CountingChatterEvent}.
     *
     * @param params parameters describing how the network and simulation should operate
     * @return the newly constructed network
     */
    private Network<CountingChatterEvent> createCountingEventNetwork(final NetworkSimulatorParams params) {
        final Random random = new Random(params.seed());

        final AddressBook addressBook = new RandomAddressBookGenerator(random)
                .setSize(params.numNodes())
                .setHashStrategy(RandomAddressBookGenerator.HashStrategy.FAKE_HASH)
                .setSequentialIds(true)
                .build();

        // Create the network latency model
        final NetworkConfig initialNetworkConfig = params.networkConfig().get(0);
        final NetworkLatency networkLatency = new NetworkLatency(addressBook, params.maxDelay(), random);
        configureNetworkLatency(networkLatency, initialNetworkConfig);

        // Create the gossip model
        // FUTURE WORK: make the network latency used by gossip dynamic (i.e. reconfigurable during the test)
        final SimpleSimulatedGossip gossip =
                new SimpleSimulatedGossip(params.numNodes(), networkLatency, params.time());

        // Create the network
        final Network<CountingChatterEvent> network = new Network<>(gossip);

        // Create the nodes and add them to the network
        final Set<NodeId> nodeIds = initialNetworkConfig.nodeConfigs().keySet();
        final AtomicLong eventCounter = new AtomicLong(0);
        nodeIds.forEach(
                (id) -> network.addNode(createCountingEventNode(id, eventCounter, params.numNodes(), params.time())));

        return network;
    }

    /**
     * Set up the network latency model with any custom latency configurations.
     *
     * @param latency       the network latency to update
     * @param networkConfig the network configuration to apply to the network latency model
     */
    private void configureNetworkLatency(final NetworkLatency latency, final NetworkConfig networkConfig) {
        for (final Map.Entry<NodeId, NodeConfig> entry :
                networkConfig.nodeConfigs().entrySet()) {
            final NodeId nodeId = entry.getKey();
            final NodeConfig nodeConfig = entry.getValue();
            if (!nodeConfig.customLatency().isZero()) {
                latency.setLatency(nodeId.getId(), nodeConfig.customLatency());
            }
        }
    }

    /**
     * Create a node that gossips {@link CountingChatterEvent}s with a chatter instance, delayable intake queue, and
     * counting event creator.
     *
     * @param nodeId       the node id of the node to create
     * @param eventCounter the event counter used across all nodes to assign events a monotonically increasing number
     * @param numNodes     the number of nodes in the network
     * @param time         the instance of time used in the simulation
     * @return the newly created node
     */
    private Node<CountingChatterEvent> createCountingEventNode(
            final NodeId nodeId, final AtomicLong eventCounter, final int numNodes, final FakeTime time) {

        // An event creator that creates events with a monotonically increasing event number across all nodes
        final TimedEventCreator<CountingChatterEvent> eventCreator = new TimedEventCreator<>(
                time, () -> new CountingChatterEvent(nodeId.getId(), eventCounter.getAndIncrement()));

        // Keeps track of all events coming out of chatter and passes it straight to the intake queue
        final GossipEventTracker gossipRecorder = new GossipEventTracker(nodeId);

        // An intake queue that will delay events before sending them to the orphan buffer
        final DelayableIntakeQueue<CountingChatterEvent> intakeQueue = new DelayableIntakeQueue<>(time);

        // An orphan buffer that releases events once all prior events have been released
        final InOrderOrphanBuffer orphanBuffer = new InOrderOrphanBuffer(nodeId.getId());

        final SimulatedEventPipeline<CountingChatterEvent> pipeline = new SimulatedEventPipelineBuilder<
                        CountingChatterEvent>()
                .next(gossipRecorder)
                .next(intakeQueue)
                .next(orphanBuffer)
                .build();

        final ChatterInstance<CountingChatterEvent> chatterInstance =
                new ChatterInstance<>(numNodes, nodeId, CountingChatterEvent.class, time, eventCreator, pipeline);

        return new Node<>(nodeId, chatterInstance);
    }
}
