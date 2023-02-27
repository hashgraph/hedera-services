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

import java.time.Duration;
import java.util.function.Consumer;

/**
 * Parameters for an event creation simulation
 *
 * @param seed
 * 		the seed to use for randomness
 * @param numNodes
 * 		number of nodes in the network
 * @param createEventEvery
 * 		attempt event creation at this interval
 * @param maxDelay
 * 		the maximum delay between 2 nodes
 * @param simulatedTime
 * 		the amount of time to simulate
 * @param simulationStep
 * 		the step size of the fake clock
 * @param superMajority
 * 		should the supermajority of nodes create events
 * @param validator
 * 		validator of results
 */
public record EventCreationSimulationParams(
        long seed,
        int numNodes,
        Duration createEventEvery,
        Duration maxDelay,
        Duration simulatedTime,
        Duration simulationStep,
        boolean superMajority,
        Consumer<EventCreationSimulationResults> validator) {}
