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

import com.swirlds.common.test.fixtures.FakeTime;
import com.swirlds.platform.test.simulated.config.NetworkConfig;
import java.time.Duration;
import java.util.List;

/**
 * Parameters for a network simulation
 *
 * @param seed           the seed to use for randomness
 * @param time           the time instance to use to advance time during the test
 * @param networkConfig  configuration for the network for a period of time. When a network configs time effective time
 *                       elapses, the next network config is applied. Only nodes whose configuration changes need to be
 *                       supplied. The first number of node configurations in the first item defines the size of the
 *                       network.
 * @param maxDelay       the maximum delay between 2 nodes
 * @param simulationTime the amount of time to simulate
 * @param simulationStep the step size of the fake clock
 */
public record NetworkSimulatorParams(
        long seed,
        FakeTime time,
        List<NetworkConfig> networkConfig,
        Duration maxDelay,
        Duration simulationTime,
        Duration simulationStep) {

    public int numNodes() {
        if (networkConfig == null || networkConfig.isEmpty()) {
            return 0;
        }
        return networkConfig.get(0).getNumConfigs();
    }
}
