/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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
package com.swirlds.platform.test.consensus.framework;

import static com.swirlds.common.test.StakeGenerators.BALANCED;

import com.swirlds.common.test.RandomUtils;
import com.swirlds.common.test.ResettableRandom;
import com.swirlds.common.test.StakeGenerator;
import com.swirlds.platform.test.event.emitter.EventEmitter;
import com.swirlds.platform.test.event.emitter.EventEmitterGenerator;
import com.swirlds.platform.test.event.emitter.ShuffledEventEmitter;
import com.swirlds.platform.test.event.generator.StandardGraphGenerator;
import com.swirlds.platform.test.event.source.EventSource;
import com.swirlds.platform.test.event.source.EventSourceFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

/** A builder for {@link ConsensusTestOrchestrator} instances */
public class OrchestratorBuilder {
    private int numberOfNodes = 4;
    private StakeGenerator stakeGenerator = BALANCED;
    private long seed = 0;
    private int totalEventNum = 10_000;
    private Function<List<Long>, List<EventSource<?>>> eventSourceBuilder =
            EventSourceFactory::newStandardEventSources;
    private Consumer<EventSource<?>> eventSourceConfigurator = es -> {};
    /**
     * A function that creates an event emitter based on a graph generator and a seed. They should
     * produce emitters that will emit events in different orders. For example, nothing would be
     * tested if both returned a {@link
     * com.swirlds.platform.test.event.emitter.StandardEventEmitter}. It is for both to return
     * {@link ShuffledEventEmitter} because they will be seeded with different values and therefore
     * emit events in different orders. Each instance of consensus should receive the same events,
     * but in a different order.
     */
    private EventEmitterGenerator node1EventEmitterGenerator = ShuffledEventEmitter::new;

    private EventEmitterGenerator node2EventEmitterGenerator = ShuffledEventEmitter::new;

    public static OrchestratorBuilder builder() {
        return new OrchestratorBuilder();
    }

    public OrchestratorBuilder setEventSourceBuilder(
            final Function<List<Long>, List<EventSource<?>>> eventSourceBuilder) {
        this.eventSourceBuilder = eventSourceBuilder;
        return this;
    }

    public OrchestratorBuilder setTestInput(final TestInput testInput) {
        numberOfNodes = testInput.numberOfNodes();
        stakeGenerator = testInput.stakeGenerator();
        seed = testInput.seed();
        totalEventNum = testInput.eventsToGenerate();
        return this;
    }

    public OrchestratorBuilder setEventSourceConfigurator(
            final Consumer<EventSource<?>> eventSourceConfigurator) {
        this.eventSourceConfigurator = eventSourceConfigurator;
        return this;
    }

    public OrchestratorBuilder setNode1EventEmitterGenerator(
            final EventEmitterGenerator node1EventEmitterGenerator) {
        this.node1EventEmitterGenerator = node1EventEmitterGenerator;
        return this;
    }

    public OrchestratorBuilder setNode2EventEmitterGenerator(
            final EventEmitterGenerator node2EventEmitterGenerator) {
        this.node2EventEmitterGenerator = node2EventEmitterGenerator;
        return this;
    }

    public ConsensusTestOrchestrator build() {
        final ResettableRandom random = RandomUtils.initRandom(seed, false);
        final long stakeSeed = random.nextLong();
        final long graphSeed = random.nextLong();
        final long shuffler1Seed = random.nextLong();
        final long shuffler2Seed = random.nextLong();

        final List<Long> stakes = stakeGenerator.getStakes(stakeSeed, numberOfNodes);
        final List<EventSource<?>> eventSources = eventSourceBuilder.apply(stakes);
        for (final EventSource<?> eventSource : eventSources) {
            eventSourceConfigurator.accept(eventSource);
        }
        final StandardGraphGenerator graphGenerator =
                new StandardGraphGenerator(graphSeed, eventSources);

        // Make the graph generators create a fresh set of events.
        // Use the same seed so that they create identical graphs.
        final EventEmitter<?> node1Emitter =
                node1EventEmitterGenerator.getEventEmitter(
                        graphGenerator.cleanCopy(), shuffler1Seed);
        final EventEmitter<?> node2Emitter =
                node2EventEmitterGenerator.getEventEmitter(
                        graphGenerator.cleanCopy(), shuffler2Seed);

        final List<ConsensusTestNode> nodes = new ArrayList<>();
        // Create two instances to run consensus on. Each instance reseeds the emitter so that they
        // emit
        // events in different orders.
        nodes.add(ConsensusTestNode.genesisContext(node1Emitter));
        nodes.add(ConsensusTestNode.genesisContext(node2Emitter));

        return new ConsensusTestOrchestrator(nodes, stakes, totalEventNum);
    }
}
