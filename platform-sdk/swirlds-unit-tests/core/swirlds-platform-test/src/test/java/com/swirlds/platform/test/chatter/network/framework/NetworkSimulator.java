/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.swirlds.platform.test.chatter.network.framework;

import com.swirlds.base.test.fixtures.time.FakeTime;
import com.swirlds.common.utility.DurationUtils;
import com.swirlds.platform.test.simulated.config.NetworkConfig;
import java.time.Instant;
import java.util.Map.Entry;

/**
 * Executes a simulated chatter network
 */
public class NetworkSimulator {

    /**
     * Simulates a chatter network.
     *
     * @param network the network to use in the simulation
     * @param params  defines the parameters of the simulation
     */
    public static void executeNetworkSimulation(final Network<?> network, final NetworkSimulatorParams params) {
        preflight(network);

        final FakeTime time = params.time();

        maybeUpdateNetworkConfig(network, params);

        printFormattedMessage("Simulation Starting");

        while (DurationUtils.isLonger(params.simulationTime(), time.elapsed())) {
            advanceNetworkOneStep(network, params);
            maybeUpdateNetworkConfig(network, params);
        }

        printFormattedMessage("Simulation Complete");
    }

    private static void printFormattedMessage(final String msg) {
        System.out.println("-----------------------------" + msg + "-----------------------------");
    }

    /**
     * Update the network configuration, if it is time to do so.
     *
     * @param network the network to update
     * @param params  the parameters of the network
     */
    private static void maybeUpdateNetworkConfig(final Network<?> network, final NetworkSimulatorParams params) {
        final FakeTime time = params.time();

        for (final Entry<Instant, NetworkConfig> entry : params.networkConfigs().entrySet()) {
            final Instant configEffectiveTime = entry.getKey();
            final NetworkConfig networkConfig = entry.getValue();

            final Instant now = time.now();
            final Instant lastTime = time.now().minus(params.simulationStep());

            // if this is the first time step activating this config, move to the next config and return
            if ((now.isAfter(configEffectiveTime) || now.equals(configEffectiveTime))
                    && lastTime.isBefore(configEffectiveTime)) {

                System.out.printf(
                        """
                                Applying Network Configuration
                                \tName: %s
                                \tElapsed Time: %s
                                """,
                        networkConfig.name(), time.elapsed());
                network.applyNetworkConfig(networkConfig);
                return;
            }
        }
    }

    /**
     * Performs all pre-flight actions that must occur before test execution can start.
     *
     * @param network the network to prepare for test execution
     */
    private static void preflight(final Network<?> network) {
        // set communication state to allow chatter in all peers in all nodes
        network.enableChatter();
    }

    /**
     * Advance the network a single time step, including creating events, handling events, and gossiping events.
     *
     * @param network the network of nodes to advance one simulation step
     * @param params  defines the parameters of the simulation
     */
    private static void advanceNetworkOneStep(final Network<?> network, final NetworkSimulatorParams params) {
        network.forEachChatterInstance(chatterInstance -> {
            chatterInstance.maybeCreateEvent();
            chatterInstance.maybeHandleEvents();
            network.gossip().gossipPayloads(chatterInstance.getMessagesToGossip());
        });
        network.gossip().distribute();
        params.time().tick(params.simulationStep());
    }
}
