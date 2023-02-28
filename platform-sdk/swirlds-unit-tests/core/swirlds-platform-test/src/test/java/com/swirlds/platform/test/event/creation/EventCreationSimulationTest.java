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

package com.swirlds.platform.test.event.creation;

import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.system.NodeId;
import com.swirlds.common.system.address.AddressBook;
import com.swirlds.common.test.RandomAddressBookGenerator;
import com.swirlds.common.test.fixtures.FakeTime;
import com.swirlds.common.utility.DurationUtils;
import com.swirlds.platform.test.consensus.TestIntake;
import com.swirlds.platform.test.simulated.Latency;
import com.swirlds.platform.test.simulated.SimpleSimulatedGossip;
import com.swirlds.platform.test.simulated.SimulatedEventCreationNode;
import com.swirlds.test.framework.context.TestPlatformContextBuilder;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.Stream;
import org.junit.jupiter.api.Assertions;
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
                        10,
                        Duration.ofMillis(20),
                        Duration.ofMillis(240),
                        Duration.ofSeconds(5),
                        Duration.ofMillis(10),
                        true,
                        r -> {
                            Assertions.assertTrue(2400 < r.numEventsCreated());
                            Assertions.assertTrue(1800 < r.numConsEvents());
                            Assertions.assertNotNull(r.avgC2C());
                            Assertions.assertNotNull(r.maxC2C());
                            Assertions.assertTrue(DurationUtils.isLonger(Duration.ofSeconds(2), r.maxC2C()));
                            Assertions.assertTrue(DurationUtils.isLonger(Duration.ofMillis(1600), r.avgC2C()));
                            Assertions.assertNotNull(r.maxRoundSize());
                            Assertions.assertTrue(280 > r.maxRoundSize());
                        })),
                // tests whether we stop creating events after a while if we dont have supermajority
                Arguments.of(new EventCreationSimulationParams(
                        1,
                        10,
                        Duration.ofMillis(20),
                        Duration.ofMillis(240),
                        Duration.ofSeconds(5),
                        Duration.ofMillis(10),
                        false,
                        r -> {
                            Assertions.assertEquals(0, r.numConsEvents());
                            Assertions.assertTrue(250 > r.numEventsCreated());
                        })));
    }

    /**
     * Simulate event creation by a number of nodes. This test creates instances of nodes that create events and
     * simulates gossip between them. It works by incrementing the fake clock in steps, then executing any work needed
     * to be done, such as event creation and gossip. It is single threaded.
     *
     * @param params the test parameters to use
     */
    @ParameterizedTest
    @MethodSource("parameters")
    void simulateEventCreation(final EventCreationSimulationParams params) {
        final PlatformContext platformContext =
                TestPlatformContextBuilder.create().build();

        final Random random = new Random(params.seed());

        final AddressBook addressBook = new RandomAddressBookGenerator(random)
                .setSize(params.numNodes())
                .setHashStrategy(RandomAddressBookGenerator.HashStrategy.FAKE_HASH)
                .setSequentialIds(true)
                .build();
        final FakeTime time = new FakeTime();
        final TestIntake consensus = new TestIntake(addressBook, time);
        final SimpleSimulatedGossip gossip =
                new SimpleSimulatedGossip(params.numNodes(), new Latency(addressBook, params.maxDelay(), random), time);

        final List<SimulatedEventCreationNode> nodes = new ArrayList<>();
        for (int i = 0; i < params.numNodes(); i++) {
            final SimulatedEventCreationNode node = new SimulatedEventCreationNode(
                    platformContext,
                    random,
                    time,
                    addressBook,
                    List.of(gossip::gossipEvent, consensus::addEvent),
                    NodeId.createMain(i),
                    h -> consensus.getShadowGraph().getEvent(h),
                    params.superMajority() || i > params.numNodes() / 2);
            nodes.add(node);
            gossip.setNode(node);
        }

        Instant nextEventCreation = time.now();
        while (DurationUtils.isLonger(params.simulatedTime(), time.elapsed())) {
            if (!time.now().isBefore(nextEventCreation)) {
                for (final SimulatedEventCreationNode node : nodes) {
                    node.createEvent();
                }
                nextEventCreation = nextEventCreation.plus(params.createEventEvery());
            }
            gossip.distribute();

            time.tick(params.simulationStep());
        }

        final EventCreationSimulationResults results = EventCreationSimulationResults.calculateResults(
                consensus.getNumEventsAdded(), consensus.getConsensusRounds());

        results.printResults();
        params.validator().accept(results);
    }
}
