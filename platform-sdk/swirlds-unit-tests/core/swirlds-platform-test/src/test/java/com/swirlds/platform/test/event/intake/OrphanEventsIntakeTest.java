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

package com.swirlds.platform.test.event.intake;

import static org.mockito.Mockito.mock;

import com.swirlds.common.config.ConsensusConfig;
import com.swirlds.common.config.singleton.ConfigurationHolder;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.system.NodeId;
import com.swirlds.common.test.RandomUtils;
import com.swirlds.platform.Consensus;
import com.swirlds.platform.components.EventIntake;
import com.swirlds.platform.event.GossipEvent;
import com.swirlds.platform.event.linking.OrphanBufferingLinker;
import com.swirlds.platform.event.linking.ParentFinder;
import com.swirlds.platform.intake.IntakeCycleStats;
import com.swirlds.platform.internal.EventImpl;
import com.swirlds.platform.observers.ConsensusRoundObserver;
import com.swirlds.platform.observers.EventAddedObserver;
import com.swirlds.platform.observers.EventObserverDispatcher;
import com.swirlds.platform.sync.ShadowGraph;
import com.swirlds.platform.test.consensus.ConsensusUtils;
import com.swirlds.platform.test.event.generator.StandardGraphGenerator;
import com.swirlds.platform.test.event.source.StandardEventSource;
import com.swirlds.test.framework.TestQualifierTags;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

class OrphanEventsIntakeTest {
    @Test
    @Tag(TestQualifierTags.TIME_CONSUMING)
    void eventConsensus() {
        final int numNodes = 10;
        final int numEvents = 10_000;

        final Random r = RandomUtils.getRandomPrintSeed();
        final long generatorSeed = r.nextLong();
        final Intake intake1 = new Intake(generatorSeed, r.nextLong(), numNodes);
        final Intake intake2 = new Intake(generatorSeed, r.nextLong(), numNodes);

        intake1.generateAndFeed(numEvents);
        intake2.generateAndFeed(numEvents);

        final List<EventImpl> cons1 = intake1.getConsensusEvents();
        final List<EventImpl> cons2 = intake2.getConsensusEvents();

        Assertions.assertEquals(cons1.size(), cons2.size());
        for (int i = 0; i < cons1.size(); i++) {
            Assertions.assertEquals(cons1.get(i).getBaseHash(), cons2.get(i).getBaseHash());
        }
        Assertions.assertEquals(0, intake1.getNumOrphans());
        Assertions.assertEquals(0, intake2.getNumOrphans());
    }

    private static class Intake {
        final StandardGraphGenerator generator;
        final List<EventImpl> consensusEvents;
        final OrphanBufferingLinker orphanBuffer;
        final EventIntake intake;
        final Random r;

        public Intake(final long generatorSeed, final long randomSeed, final int numNodes) {
            r = new Random(randomSeed);
            generator = new StandardGraphGenerator(
                    generatorSeed,
                    Stream.generate(StandardEventSource::new).limit(numNodes).collect(Collectors.toList()));
            final Map<Hash, EventImpl> linkedEventMap = new HashMap<>();
            consensusEvents = new ArrayList<>();
            final Consensus consensus = ConsensusUtils.buildSimpleConsensus(generator.getAddressBook());
            orphanBuffer = new OrphanBufferingLinker(
                    ConfigurationHolder.getConfigData(ConsensusConfig.class),
                    new ParentFinder(linkedEventMap::get),
                    100_000);
            intake = new EventIntake(
                    NodeId.createMain(0),
                    orphanBuffer,
                    () -> consensus,
                    generator.getAddressBook(),
                    new EventObserverDispatcher(
                            (EventAddedObserver) e -> linkedEventMap.put(e.getBaseHash(), e),
                            (ConsensusRoundObserver) rnd -> consensusEvents.addAll(rnd.getConsensusEvents())),
                    mock(IntakeCycleStats.class),
                    mock(ShadowGraph.class),
                    e -> {},
                    r -> {});
        }

        public void generateAndFeed(final int numEvents) {
            final List<GossipEvent> generatedList = Stream.generate(
                            () -> generator.generateEvent().getBaseEvent())
                    .limit(numEvents)
                    .collect(Collectors.toList());
            Collections.shuffle(generatedList, r);
            generatedList.forEach(intake::addUnlinkedEvent);
        }

        public List<EventImpl> getConsensusEvents() {
            return consensusEvents;
        }

        public int getNumOrphans() {
            return orphanBuffer.getNumOrphans();
        }
    }
}
