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

package com.swirlds.platform.test.consensus;

import com.swirlds.config.extensions.test.fixtures.TestConfigBuilder;
import com.swirlds.platform.consensus.ConsensusConfig;
import com.swirlds.platform.internal.ConsensusRound;
import com.swirlds.platform.test.fixtures.event.generator.StandardGraphGenerator;
import com.swirlds.platform.test.fixtures.event.source.EventSource;
import com.swirlds.platform.test.fixtures.event.source.StandardEventSource;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.stream.IntStream;

/**
 * Test utility for generating consensus events
 */
public final class GenerateConsensus {
    private GenerateConsensus() {}

    /**
     * Generate consensus rounds
     *
     * @param numNodes
     * 		the number of nodes in the hypothetical network
     * @param numEvents
     * 		the number of pre-consensus events to generate
     * @param seed
     * 		the seed to use
     * @return consensus rounds
     */
    public static Deque<ConsensusRound> generateConsensusRounds(
            final int numNodes, final int numEvents, final long seed) {
        final List<EventSource<?>> eventSources = new ArrayList<>();
        IntStream.range(0, numNodes).forEach(i -> eventSources.add(new StandardEventSource(false)));
        final StandardGraphGenerator generator = new StandardGraphGenerator(seed, eventSources);
        final TestIntake intake = new TestIntake(
                generator.getAddressBook(),
                new TestConfigBuilder().getOrCreateConfig().getConfigData(ConsensusConfig.class));

        // generate events and feed them to consensus
        for (int i = 0; i < numEvents; i++) {
            intake.addEvent(generator.generateEvent().getBaseEvent());
        }

        // return the rounds
        return intake.getConsensusRounds();
    }
}
