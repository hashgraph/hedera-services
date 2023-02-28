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

import com.swirlds.common.system.NodeId;
import com.swirlds.common.system.address.AddressBook;
import com.swirlds.common.test.RandomAddressBookGenerator;
import com.swirlds.common.test.fixtures.FakeTime;
import com.swirlds.common.utility.DurationUtils;
import com.swirlds.platform.test.simulated.Latency;
import com.swirlds.platform.test.simulated.NetworkLatency;
import com.swirlds.platform.test.simulated.SimpleSimulatedGossip;
import com.swirlds.platform.test.simulated.config.MapBuilder;
import com.swirlds.platform.test.simulated.config.NetworkConfig;
import com.swirlds.platform.test.simulated.config.NodeConfig;
import com.swirlds.platform.test.simulated.config.NodeConfigBuilder;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class ChatterWithFakeNetworkTest {

    private static final Duration FIRST_PHASE_DURATION = Duration.ofMillis(500);
    private static final Duration TOTAL_TEST_DURATION = Duration.ofSeconds(1);
    private static final Duration SLOW_NODE_INTAKE_DELAY = Duration.ofMillis(100);
    private static final Duration FAST_NODE_INTAKE_DELAY = Duration.ofMillis(0);
    private static final Duration CREATE_EVENT_EVERY = Duration.ofMillis(50);

    private static Stream<Arguments> parameters() {
        final FakeTime time = new FakeTime();

        // Configure the network to be fast
        final Map<Long, NodeConfig> initialConfigs = MapBuilder.builder(NodeConfig.class)
                .useElement(NodeConfigBuilder.builder()
                        .setCreateEventEvery(CREATE_EVENT_EVERY)
                        .setIntakeQueueDelay(FAST_NODE_INTAKE_DELAY)
                        .setCustomLatency(new Latency(Duration.ofMillis(100)))
                        .build())
                .times(1)
                .useElement(NodeConfigBuilder.builder()
                        .setCreateEventEvery(CREATE_EVENT_EVERY)
                        .setIntakeQueueDelay(FAST_NODE_INTAKE_DELAY)
                        .setCustomLatency(new Latency(Duration.ofMillis(10)))
                        .build())
                .times(2)
                .build();
        final NetworkConfig initialNetworkConfig = new NetworkConfig(FIRST_PHASE_DURATION, initialConfigs);

        // Slow one node's intake queue down
        //		final NodeConfig nodeToSlowDown = initialConfigs.get(0L);
        //		final Map<Long, NodeConfig> oneSlowNode = MapBuilder.builder(NodeConfig.class)
        //				.useElement(
        //						NodeConfigBuilder.builder(nodeToSlowDown)
        //								.setIntakeQueueDelay(SLOW_NODE_INTAKE_DELAY)
        //								.build())
        //				.times(1)
        //				.build();
        //		final NetworkConfig oneSlowNodeNetworkConfig =
        //				new NetworkConfig(TOTAL_TEST_DURATION.minus(FIRST_PHASE_DURATION),
        //				oneSlowNode);

        return Stream.of(Arguments.of(new NetworkSimulatorParams(
                1,
                time,
                List.of(
                        initialNetworkConfig
                        //								,
                        //								oneSlowNodeNetworkConfig
                        ),
                Duration.ofMillis(240),
                Duration.ofSeconds(1),
                Duration.ofMillis(10))));
    }

    @ParameterizedTest
    @MethodSource("parameters")
    void runChatter(final NetworkSimulatorParams params) {
        final Random random = new Random(params.seed());

        final AddressBook addressBook = new RandomAddressBookGenerator(random)
                .setSize(params.numNodes())
                .setHashStrategy(RandomAddressBookGenerator.HashStrategy.FAKE_HASH)
                .setSequentialIds(true)
                .build();
        final FakeTime time = params.time();

        final NetworkLatency latency = new NetworkLatency(addressBook, params.maxDelay(), random);
        final SimpleSimulatedGossip gossip = new SimpleSimulatedGossip(params.numNodes(), latency, time);

        final Map<Long, ChatterNode<CountingChatterEvent>> nodes = new HashMap<>();
        final Map<Long, DelayedIntakeQueue<CountingChatterEvent>> intakeQueues = new HashMap<>();

        // probably doesnt need to be atomic
        final AtomicLong eventCounter = new AtomicLong(0);

        int i = 0;
        final Map<Long, NodeConfig> initialNetworkConfig =
                params.networkConfig().get(0).nodeConfigs();
        for (final Map.Entry<Long, NodeConfig> entry : initialNetworkConfig.entrySet()) {
            final NodeId nodeId = NodeId.createMain(entry.getKey());
            final NodeConfig nodeConfig = entry.getValue();

            if (!nodeConfig.customLatency().isZero()) {
                latency.setLatency(nodeId.getId(), nodeConfig.customLatency());
            }

            final TimedEventCreator<CountingChatterEvent> eventCreator = new TimedEventCreator<>(
                    time,
                    nodeConfig.createEventEvery(),
                    () -> new CountingChatterEvent(nodeId.getId(), eventCounter.getAndIncrement()));

            // Keeps track of all events coming out of gossip and passes it straight on to the intake queue
            final GossipEventTracker gossipRecorder = new GossipEventTracker(nodeId);

            // An intake queue that will delay events before sending them to the orphan buffer
            final DelayedIntakeQueue<CountingChatterEvent> intakeQueue =
                    new DelayedIntakeQueue<>(time, nodeConfig.intakeQueueDelay());
            intakeQueues.put(nodeId.getId(), intakeQueue);

            // An orphan buffer that releases events once all prior events have been released
            final InOrderOrphanBuffer orphanBuffer = new InOrderOrphanBuffer(nodeId.getId());

            final SimulatedEventPipeline<CountingChatterEvent> pipeline = new SimulatedEventPipelineBuilder<
                            CountingChatterEvent>()
                    .next(gossipRecorder)
                    .next(intakeQueue)
                    .next(orphanBuffer)
                    .build();

            final ChatterNode<CountingChatterEvent> chatterNode = new ChatterNode<>(
                    params.numNodes(), nodeId, CountingChatterEvent.class, time, eventCreator, pipeline);

            gossip.setNode(chatterNode);
            nodes.put(nodeId.getId(), chatterNode);
        }

        // set communication state to allow chatter in all peers in all nodes
        nodes.values().forEach(ChatterNode::enableChatter);

        final Iterator<NetworkConfig> networkConfigIt = params.networkConfig().iterator();
        NetworkConfig curConfig = networkConfigIt.next();
        NetworkConfig nextConfig = networkConfigIt.hasNext() ? networkConfigIt.next() : null;
        Instant updateNetworkConfigAt = time.now().plus(curConfig.duration());

        // Test execution loop
        while (DurationUtils.isLonger(params.simulationTime(), time.elapsed())) {
            nodes.values().forEach(node -> {
                node.maybeCreateEvent();
                node.maybeHandleEvents();
                gossip.gossipPayloads(node.getMessagesToGossip());
            });
            gossip.distribute();
            time.tick(params.simulationStep());

            if (!time.now().isBefore(updateNetworkConfigAt) && nextConfig != null) {
                // Time to move to the next network config
                for (final Map.Entry<Long, NodeConfig> entry :
                        nextConfig.nodeConfigs().entrySet()) {
                    final long nodeId = entry.getKey();
                    final NodeConfig nodeConfig = entry.getValue();

                    final DelayedIntakeQueue<CountingChatterEvent> intakeQueue = intakeQueues.get(nodeId);
                    intakeQueue.setDelay(nodeConfig.intakeQueueDelay());

                    // FUTURE WORK: apply other updates
                }

                curConfig = nextConfig;
                updateNetworkConfigAt = time.now().plus(curConfig.duration());
                nextConfig = networkConfigIt.hasNext() ? networkConfigIt.next() : null;
            }
        }

        nodes.values().forEach(ChatterNode::printResults);
        gossip.printQueues();
    }
}
