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
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.swirlds.common.system.NodeId;
import com.swirlds.common.test.RandomUtils;
import com.swirlds.common.test.fixtures.FakeTime;
import com.swirlds.platform.gossip.chatter.protocol.processing.ProcessingTimeMessage;
import com.swirlds.platform.test.chatter.network.framework.Network;
import com.swirlds.platform.test.chatter.network.framework.NetworkSimulator;
import com.swirlds.platform.test.chatter.network.framework.NetworkSimulatorParams;
import com.swirlds.platform.test.chatter.network.framework.NetworkSimulatorParams.NetworkSimulatorParamsBuilder;
import com.swirlds.platform.test.chatter.network.framework.Node;
import com.swirlds.platform.test.chatter.network.framework.NodeBuilder;
import com.swirlds.platform.test.chatter.network.framework.SimulatedEventPipeline;
import com.swirlds.platform.test.chatter.network.framework.SimulatedEventPipelineBuilder;
import com.swirlds.platform.test.simulated.NetworkLatency;
import com.swirlds.platform.test.simulated.SimpleSimulatedGossip;
import com.swirlds.platform.test.simulated.config.MapBuilder;
import com.swirlds.platform.test.simulated.config.NetworkConfig;
import com.swirlds.platform.test.simulated.config.NodeConfig;
import com.swirlds.platform.test.simulated.config.NodeConfigBuilder;
import java.time.Duration;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class ChatterSimulationTests {

    private static final Duration PHASE_DURATION = Duration.ofMillis(500);
    private static final Duration DEFAULT_MAX_DELAY = Duration.ofMillis(240);
    private static final Duration SLOW_NODE_INTAKE_DELAY = Duration.ofMillis(100);
    private static final Duration FAST_NODE_INTAKE_DELAY = Duration.ofMillis(0);


    @Disabled("This test currently fails due to a known bug")
    @Test
    @DisplayName(
            "Test that event processing time is always equal to the intake queue delay, and does not include orphan buffer wait time")
    void testProcessingTimeEqualsIntakeQueueDelay() {
        final Random random = RandomUtils.initRandom(1L);
        final FakeTime time = new FakeTime();

        // Step 1: Define the parameters of the network
        // Three node network, each node has the same slow intake queue
        final Map<NodeId, NodeConfig> nodeConfigs = MapBuilder.builder(NodeConfig.class)
                .useElement(NodeConfigBuilder.builder()
                        .setIntakeQueueDelay(SLOW_NODE_INTAKE_DELAY)
                        .setCreateEventEvery(Duration.ofMillis(50))
                        .build())
                .times(3)
                .build();
        final NetworkConfig networkConfig =
                new NetworkConfig("Single Phase", PHASE_DURATION, nodeConfigs);

        // Use uneven latencies to cause some events to sit in the orphan buffer for a short time
        final NetworkLatency latency = NetworkLatency.randomLatency(nodeConfigs.keySet(), DEFAULT_MAX_DELAY, random);

        final NetworkSimulatorParams params = new NetworkSimulatorParamsBuilder()
                .time(time)
                .latency(latency)
                .networkConfig(networkConfig)
                .build();

        // Step 2: Create the network
        final Network<CountingChatterEvent> network = createCountingEventNetwork(params);

        // Step 3: Execute the test
        NetworkSimulator.executeNetworkSimulation(network, params);

        network.printNetworkState();

        // Step 4: Verify outcome
        // Assert that all processing time messages are equal to the configured intake queue delay.
        // Time spent in the orphan buffer should not be included.
        AtomicBoolean nonZeroProcTimeSent = new AtomicBoolean(false);
        for (final NodeId id : network.nodeIds()) {
            network.gossip().getSentBy(id, ProcessingTimeMessage.class).forEach(procMsg -> {
                if (procMsg.getProcessingTimeInNanos() > 0) {
                    nonZeroProcTimeSent.set(true);
                }
                // Each node will send some processing time messages with a duration of zero
                // before the first gossiped event comes out of the intake queue. Only assert messages
                // sent after these zero duration messages.
                if (nonZeroProcTimeSent.get()) {
                    final Duration actualDuration = Duration.ofNanos(procMsg.getProcessingTimeInNanos());
                    assertEquals(SLOW_NODE_INTAKE_DELAY, actualDuration,
                            String.format("Node %s sent a processing time message with an invalid duration (%s)",
                                    id,
                                    actualDuration));
                }
            });
        }
    }

    /**
     * This "test" does not test anything yet. It is useful for observing behavior given various inputs. It runs a 3
     * node network with uniform latency and has three stages:
     *
     * <ol>
     *     <li>All nodes have fast intake queues (zero wait time)</li>
     *     <li>Node 0 has a slow intake queue (non-zero wait time)</li>
     *     <li>All nodes have fast intake queues again</li>
     * </ol>
     */
    @Test
    @DisplayName("Work In Progress - attempt to simulate a scenario that causes a non-zero duplicate event rate")
    void testInduceHighDuplicateEventRate() {
        final FakeTime time = new FakeTime();

        // Three node network, all nodes have a fast intake queue
        final Map<NodeId, NodeConfig> initialConfigMap = MapBuilder.builder(NodeConfig.class)
                .useElement(NodeConfigBuilder.builder()
                        .setIntakeQueueDelay(FAST_NODE_INTAKE_DELAY)
                        .build())
                .times(3)
                .build();

        final NetworkConfig initialNetworkConfig =
                new NetworkConfig("Initial Phase - All Fast Intake Queues", PHASE_DURATION, initialConfigMap);

        // One node suddenly has a slow intake queue
        final NodeId slowNodeId = NodeId.createMain(0L);
        final NodeConfig node0SlowIntake = initialConfigMap.get(slowNodeId);
        final Map<NodeId, NodeConfig> node0SlowIntakeMap = MapBuilder.builder(NodeConfig.class)
                .useElement(NodeConfigBuilder.builder(node0SlowIntake)
                        .setIntakeQueueDelay(SLOW_NODE_INTAKE_DELAY)
                        .build())
                .times(1)
                .build();
        final NetworkConfig oneSlowNodeNetworkConfig =
                new NetworkConfig("Second Phase - Node 0 has Slow Intake Queue", PHASE_DURATION,
                        node0SlowIntakeMap);

        // Slow node's intake queue is fast again
        final NodeConfig node0FastIntake = initialConfigMap.get(NodeId.createMain(0L));
        final Map<NodeId, NodeConfig> node0FastIntakeMap = MapBuilder.builder(NodeConfig.class)
                .useElement(NodeConfigBuilder.builder(node0FastIntake)
                        .setIntakeQueueDelay(FAST_NODE_INTAKE_DELAY)
                        .build())
                .times(1)
                .build();
        final NetworkConfig slowNodeRecoversNetworkConfig =
                new NetworkConfig("Third Phase - Node 0 Recovers", PHASE_DURATION, node0FastIntakeMap);

        final NetworkSimulatorParams params = new NetworkSimulatorParamsBuilder()
                .time(time)
                .networkConfig(initialNetworkConfig)
                .networkConfig(oneSlowNodeNetworkConfig)
                .networkConfig(slowNodeRecoversNetworkConfig)
                .build();

        final Network<CountingChatterEvent> network = createCountingEventNetwork(params);

        // Execute the test
        NetworkSimulator.executeNetworkSimulation(network, params);

        network.printNetworkState();

        final GossipEventTracker gossipEventTracker = network.chatterInstance(slowNodeId)
                .getPipelineComponent(GossipEventTracker.class);
        assertTrue(gossipEventTracker.getNumDuplicates() > 0);
    }

    /**
     * Creates a network that gossips {@link CountingChatterEvent}s.
     *
     * @param params parameters describing how the network and simulation should operate
     * @return the newly constructed network
     */
    private Network<CountingChatterEvent> createCountingEventNetwork(final NetworkSimulatorParams params) {
        // Create the network latency model
        final NetworkConfig initialNetworkConfig = params.networkConfigs().get(0);
        configureNetworkLatency(params.networkLatency(), initialNetworkConfig);

        // Create the gossip model
        // FUTURE WORK: make the network latency used by gossip dynamic (i.e. reconfigurable during the test)
        final SimpleSimulatedGossip gossip =
                new SimpleSimulatedGossip(params.numNodes(), params.networkLatency(), params.time());

        // Create the network
        final Network<CountingChatterEvent> network = new Network<>(gossip);

        // Create the nodes and add them to the network
        final AtomicLong eventCounter = new AtomicLong(0);
        params.nodeIds().forEach(
                (id) -> network.addNode(createCountingEventNode(id, params, eventCounter)));

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
     * @param params       the parameters of the network simulation
     * @param eventCounter the event counter used across all nodes to assign events a monotonically increasing number
     * @return the newly created node
     */
    private Node<CountingChatterEvent> createCountingEventNode(final NodeId nodeId, final NetworkSimulatorParams params,
            final AtomicLong eventCounter) {

        // An event creator that creates events with a monotonically increasing event number across all nodes
        final TimedEventCreator<CountingChatterEvent> eventCreator = new TimedEventCreator<>(
                params.time(), () -> new CountingChatterEvent(nodeId.getId(), eventCounter.getAndIncrement()));

        // Keeps track of all events coming out of chatter and passes it straight to the intake queue
        final GossipEventTracker gossipRecorder = new GossipEventTracker(nodeId);

        // An intake queue that will delay events before sending them to the orphan buffer
        final DelayableIntakeQueue<CountingChatterEvent> intakeQueue = new DelayableIntakeQueue<>(params.time());

        // Discards any duplicate events
        final EventDeduper eventDeduper = new EventDeduper(nodeId);

        // An orphan buffer that releases events once all prior events have been released
        final InOrderOrphanBuffer orphanBuffer = new InOrderOrphanBuffer(nodeId);

        final SimulatedEventPipeline<CountingChatterEvent> pipeline = new SimulatedEventPipelineBuilder<
                CountingChatterEvent>()
                .next(gossipRecorder)
                .next(intakeQueue)
                .next(eventDeduper)
                .next(orphanBuffer)
                .build();

        return new NodeBuilder<CountingChatterEvent>()
                .nodeId(nodeId)
                .numNodes(params.numNodes())
                .time(params.time())
                .eventClass(CountingChatterEvent.class)
                .eventCreator(eventCreator)
                .otherEventDelay(params.otherEventDelay())
                .procTimeInterval(params.procTimeInterval())
                .eventPipeline(pipeline)
                .build();
    }
}
