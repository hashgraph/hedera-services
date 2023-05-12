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
import com.swirlds.test.framework.config.TestConfigBuilder;
import java.time.Duration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

class SimulatorTest {
    @BeforeAll
    static void beforeAll() throws ConstructableRegistryException {
        new TestConfigBuilder().getOrCreateConfig();

        ConstructableRegistry.getInstance().registerConstructables("com.swirlds");
    }

    @Test
    @Disabled("Added to debug bandwidth usage, useful for more testing in the future")
    void testBasicChatter() {
        final GossipSimulationBuilder builder = new GossipSimulationBuilder()
                .setNodeCount(2)
                .setChatterFactory(NetworkTestChatter.factory(900_000_000 / 8))
                .setLatencyDefault(Duration.ofMillis(0))
                .setIncomingBandwidthDefault(1_000_000_000 / 8)
                .setOutgoingBandwidthDefault(1_000_000_000 / 8)
                .setAverageEventsCreatedPerSecondDefault(1.0)
                .setAverageEventSizeInBytes(10_000)
                .setEventSizeInBytesStandardDeviation(1_000)
                .setDropProbabilityDefault(0.0)
                .setDebugEnabled(false)
                .setSingleThreaded(true)
                .setCSVGenerationEnabled(false);

        final GossipSimulation simulation = builder.build();
        simulation.simulate(Duration.ofSeconds(10));
        simulation.close();
    }
}
