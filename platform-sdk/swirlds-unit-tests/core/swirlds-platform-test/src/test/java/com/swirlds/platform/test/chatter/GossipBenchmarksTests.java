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

package com.swirlds.platform.test.chatter;

import com.swirlds.common.constructable.ConstructableRegistry;
import com.swirlds.common.constructable.ConstructableRegistryException;
import com.swirlds.platform.test.chatter.simulator.GossipSimulation;
import com.swirlds.platform.test.chatter.simulator.GossipSimulationBuilder;
import com.swirlds.test.framework.TestQualifierTags;
import java.time.Duration;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@DisplayName("Gossip Benchmarks")
class GossipBenchmarksTests {

    @BeforeAll
    static void beforeAll() throws ConstructableRegistryException {
        ConstructableRegistry.getInstance().registerConstructables("com.swirlds");
    }

    /**
     * Get a list of a node's connections.
     */
    private static List<Long> getConnections(final GossipSimulationBuilder builder, final long source) {
        final List<Long> connectedNodes = new LinkedList<>();
        for (long destination = 0; destination < builder.getNodeCount(); destination++) {
            if (source == destination) {
                continue;
            }

            if (builder.getConnectionStatus(source, destination)) {
                connectedNodes.add(destination);
            }
        }

        return connectedNodes;
    }

    /**
     * <p>
     * Limit the connections between nodes.
     * </p>
     *
     * <p>
     * Note that no node will have fewer than the requested number of connections, but some nodes may
     * have more. This happens if it is impossible to remove a connection from a node without reducing
     * another below the requested number.
     * </p>
     *
     * @param builder
     * 		builds the simulation
     * @param desiredNumberOfConnections
     * 		the desired number of connections per each node
     */
    private static void limitConnections(
            final Random random, final GossipSimulationBuilder builder, final int desiredNumberOfConnections) {

        for (long source = 0; source < builder.getNodeCount(); source++) {

            final List<Long> connectedNodes = getConnections(builder, source);
            Collections.shuffle(connectedNodes, random);

            final Iterator<Long> iterator = connectedNodes.iterator();
            while (connectedNodes.size() > desiredNumberOfConnections && iterator.hasNext()) {

                final long destination = iterator.next();
                final int otherNodeConnectionCount =
                        getConnections(builder, destination).size();

                if (otherNodeConnectionCount > desiredNumberOfConnections) {
                    builder.setConnectionStatus(source, destination, false);
                    builder.setConnectionStatus(destination, source, false);
                    iterator.remove();
                }
            }
        }
    }

    private static List<Arguments> factories() {
        return List.of(Arguments.of(SimulatedChatterFactories.getInstance()));
    }

    @ParameterizedTest
    @MethodSource("factories")
    @Tag(TestQualifierTags.TIME_CONSUMING)
    void test(final SimulatedChatterFactory chatterFactory) {
        final GossipSimulationBuilder builder = new GossipSimulationBuilder()
                .setNodeCount(20)
                .setChatterFactory(chatterFactory)
                .setLatencyDefault(Duration.ofMillis(100))
                .setIncomingBandwidthDefault(1_000_000_000 / 8)
                .setOutgoingBandwidthDefault(1_000_000_000 / 8)
                .setAverageEventsCreatedPerSecondDefault(1.0)
                .setAverageEventSizeInBytes(10_000)
                .setEventSizeInBytesStandardDeviation(1_000)
                .setDropProbabilityDefault(0.0)
                .setDebugEnabled(false)
                .setCSVGenerationEnabled(false);

        // limitConnections(new Random(0), builder, 10);

        final GossipSimulation simulation = builder.build();
        simulation.simulate(Duration.ofSeconds(100));
        simulation.close();
    }
}
