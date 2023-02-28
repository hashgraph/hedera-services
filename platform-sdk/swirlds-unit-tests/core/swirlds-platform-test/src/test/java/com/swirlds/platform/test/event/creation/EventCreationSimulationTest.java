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

package com.swirlds.platform.test.event.creation;

import com.swirlds.common.system.NodeId;
import com.swirlds.common.system.address.AddressBook;
import com.swirlds.common.test.RandomAddressBookGenerator;
import com.swirlds.common.test.fixtures.FakeTime;
import com.swirlds.common.utility.DurationUtils;
import com.swirlds.platform.test.consensus.TestIntake;
import com.swirlds.platform.test.simulated.GossipMessage;
import com.swirlds.platform.test.simulated.NetworkLatency;
import com.swirlds.platform.test.simulated.SimpleSimulatedGossip;
import com.swirlds.platform.test.simulated.SimulatedEventCreationNode;
import com.swirlds.platform.test.simulated.config.ListBuilder;
import com.swirlds.platform.test.simulated.config.NodeConfig;
import com.swirlds.platform.test.simulated.config.NodeConfigBuilder;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Tests that simulate multiple nodes creating events in a network with simulated latencies
 */
public class EventCreationSimulationTest {

    private static Stream<Arguments> parameters() {
        return Stream.of(
                // benchmark simulation, all future changes to event creation rules should yield better results than
                // this
                Arguments.of(new EventCreationSimulationParams(
                        1,
                        ListBuilder.builder(NodeConfig.class)
                                .useElement(NodeConfigBuilder.builder()
                                        .setCreateEventEvery(Duration.ofMillis(20))
                                        .build())
                                .times(10)
                                .build(),
                        Duration.ofMillis(240),
                        Duration.ofSeconds(5),
                        Duration.ofMillis(10),
                        EventCreationExpectedResults.get()
                                .setConsensusExpected(true)
                                .setNumEventsCreatedMin(2400)
                                .setNumConsEventsMin(1800)
                                .setMaxC2CMax(Duration.ofSeconds(2))
                                .setAvgC2CMax(Duration.ofMillis(1600))
                                .setMaxRoundSizeMax(280))),
                // tests whether we stop creating events after a while if we dont have supermajority
                Arguments.of(new EventCreationSimulationParams(
                        1,
                        ListBuilder.builder(NodeConfig.class)
                                .useElement(NodeConfigBuilder.builder()
                                        .setCreateEventEvery(Duration.ofMillis(20))
                                        .build())
                                .times(5)
                                .useElement(NodeConfigBuilder.builder()
                                        .setCreateEventEvery(Duration.ofMillis(0))
                                        .build())
                                .times(5)
                                .build(),
                        Duration.ofMillis(240),
                        Duration.ofSeconds(5),
                        Duration.ofMillis(10),
                        EventCreationExpectedResults.get()
                                .setConsensusExpected(false)
                                .setNumEventsCreatedMax(260))));
    }

    /**
     * Simulate event creation by a number of nodes. This test creates instances of nodes that create events and
     * simulates gossip between them. It works by incrementing the fake clock in steps, then executing any work
     * needed to be done, such as event creation and gossip. It is single threaded.
     *
     * @param params
     * 		the test parameters to use
     */
    @ParameterizedTest
    @MethodSource("parameters")
    void simulateEventCreation(final EventCreationSimulationParams params) {
        final Random random = new Random(params.seed());

        final AddressBook addressBook = new RandomAddressBookGenerator(random)
                .setSize(params.numNodes())
                .setHashStrategy(RandomAddressBookGenerator.HashStrategy.FAKE_HASH)
                .setSequentialIds(true)
                .build();
        final FakeTime time = new FakeTime();
        final TestIntake consensus = new TestIntake(addressBook, time);
        final NetworkLatency latency = new NetworkLatency(addressBook, params.maxDelay(), random);
        List<NodeConfig> nodeConfigs = params.nodeConfigs();
        for (int i = 0; i < nodeConfigs.size(); i++) {
            final NodeConfig nodeConfig = nodeConfigs.get(i);
            if (!nodeConfig.customLatency().isZero()) {
                latency.setLatency(i, nodeConfig.customLatency());
            }
        }
        final SimpleSimulatedGossip gossip = new SimpleSimulatedGossip(params.numNodes(), latency, time);

        final List<SimulatedEventCreationNode> nodes = new ArrayList<>();
        int i = 0;
        for (NodeConfig nodeConfig : params.nodeConfigs()) {
            final NodeId selfId = NodeId.createMain(i++);
            final SimulatedEventCreationNode node = new SimulatedEventCreationNode(
                    random,
                    time,
                    addressBook,
                    List.of(e -> gossip.gossipPayload(GossipMessage.toAll(e, selfId.getId())), consensus::addEvent),
                    selfId,
                    h -> consensus.getShadowGraph().getEvent(h),
                    nodeConfig);
            nodes.add(node);
            gossip.setNode(node);
        }

        while (DurationUtils.isLonger(params.simulatedTime(), time.elapsed())) {
            nodes.forEach(SimulatedEventCreationNode::maybeCreateEvent);
            gossip.distribute();
            time.tick(params.simulationStep());
        }

        final EventCreationSimulationResults results = EventCreationSimulationResults.calculateResults(
                consensus.getNumEventsAdded(), consensus.getConsensusRounds());

        results.printResults();
        params.expectedResults().validate(results);
    }
}
