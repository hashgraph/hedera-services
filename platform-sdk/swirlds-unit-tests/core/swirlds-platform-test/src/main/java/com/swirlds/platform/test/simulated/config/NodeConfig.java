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

package com.swirlds.platform.test.simulated.config;

import com.swirlds.platform.test.simulated.Latency;
import java.time.Duration;

/**
 * Configuration for {@link com.swirlds.platform.test.simulated.SimulatedEventCreationNode}
 *
 * @param createEventEvery
 * 		create an event at this interval
 * @param customLatency
 * 		set the network latency for this node to this value
 * @param intakeQueueDelay
 * 		the amount of time an event sits in the intake queue before being processed.
 * @param customBandwidth
 * 		set the bandwidth for this node (in bytes/sec) to this value, or -1 for no bandwidth restriction
 */
public record NodeConfig(
        Duration createEventEvery, Latency customLatency, Duration intakeQueueDelay, long customBandwidth) {}
