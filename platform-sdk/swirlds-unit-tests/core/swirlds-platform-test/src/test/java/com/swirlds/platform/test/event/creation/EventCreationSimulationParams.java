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

import com.swirlds.platform.test.simulated.config.NodeConfig;
import java.time.Duration;
import java.util.List;

/**
 * Parameters for an event creation simulation
 *
 * @param seed
 * 		the seed to use for randomness
 * @param nodeConfigs
 * 		configuration for each node, the number of nodes is determined by the list size
 * @param maxDelay
 * 		the maximum delay between 2 nodes
 * @param simulatedTime
 * 		the amount of time to simulate
 * @param simulationStep
 * 		the step size of the fake clock
 */
public record EventCreationSimulationParams(
        long seed,
        List<NodeConfig> nodeConfigs,
        Duration maxDelay,
        Duration simulatedTime,
        Duration simulationStep,
        EventCreationExpectedResults expectedResults) {
    public int numNodes() {
        return nodeConfigs.size();
    }
}
