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

package com.swirlds.platform.test.event.linking;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.swirlds.common.config.ConsensusConfig;
import com.swirlds.common.test.RandomUtils;
import com.swirlds.platform.event.GossipEvent;
import com.swirlds.platform.event.linking.OrphanBufferingLinker;
import com.swirlds.platform.event.linking.ParentFinder;
import com.swirlds.platform.state.MinGenInfo;
import com.swirlds.platform.state.signed.SignedState;
import com.swirlds.platform.sync.Generations;
import com.swirlds.platform.test.event.EventBuilder;
import com.swirlds.platform.test.event.generator.GraphGenerator;
import com.swirlds.platform.test.event.generator.StandardGraphGenerator;
import com.swirlds.platform.test.event.source.StandardEventSource;
import com.swirlds.platform.test.graph.SimpleGraphs;
import com.swirlds.test.framework.config.TestConfigBuilder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.LongStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class OrphanBufferingLinkerTest {

    public static final int GENERATIONS_STORED = 100_000;

    /**
     * Tests graph below:
     *
     * <pre>
     * 2
     * |
     * 1
     * </pre>
     *
     * Added in the following order: 2 1
     * Expected output: 1 2
     */
    @Test
    void singleMissingParent() {
        final ConsensusConfig consensusConfig =
                new TestConfigBuilder().getOrCreateConfig().getConfigData(ConsensusConfig.class);
        final Random r = RandomUtils.getRandomPrintSeed();
        final OrphanBufferTester orphanBuffer =
                new OrphanBufferTester(pf -> new OrphanBufferingLinker(consensusConfig, pf, GENERATIONS_STORED));

        final GossipEvent e1 = EventBuilder.builder().setRandom(r).buildGossipEvent();
        final GossipEvent e2 =
                EventBuilder.builder().setRandom(r).setSelfParent(e1).buildGossipEvent();

        orphanBuffer.linkEvent(e2);
        orphanBuffer.assertGeneration();
        orphanBuffer.linkEvent(e1);
        orphanBuffer.assertGeneration(e1);
        orphanBuffer.assertGeneration(e2);
    }

    /**
     * Tests graph: {@link SimpleGraphs#graph5e2n(Random)}
     *
     * Added in the following order: 0 2 3 4 1
     * Expected output: 0 1 2 (3 4)
     * Note: it is ok for events in brackets to be reordered
     */
    @Test
    void multipleMissingParents() {
        final ConsensusConfig consensusConfig =
                new TestConfigBuilder().getOrCreateConfig().getConfigData(ConsensusConfig.class);
        final Random r = RandomUtils.getRandomPrintSeed();
        final OrphanBufferTester orphanBuffer =
                new OrphanBufferTester(pf -> new OrphanBufferingLinker(consensusConfig, pf, GENERATIONS_STORED));

        final List<GossipEvent> graph = SimpleGraphs.graph5e2n(r);

        orphanBuffer.linkEvent(graph, 0);
        orphanBuffer.assertGeneration(graph, 0);

        orphanBuffer.linkEvent(graph, 2);
        orphanBuffer.linkEvent(graph, 3);
        orphanBuffer.linkEvent(graph, 4);
        orphanBuffer.assertOrphans(List.of(graph.get(2), graph.get(3), graph.get(4)));
        orphanBuffer.assertNumOrphans(3);
        orphanBuffer.linkEvent(graph, 1);

        orphanBuffer.assertGeneration(graph, 1);
        orphanBuffer.assertGeneration(graph, 2);
        orphanBuffer.assertGeneration(graph, 3, 4);
    }

    /**
     * Tests graph: {@link SimpleGraphs#graph5e2n(Random)}
     *
     * Added in the following order: 2 0 4 3
     * Event 1 is never added, but its generation becomes ancient
     * Expected output: 0 2 (3 4)
     * Note: it is ok for events in brackets to be reordered
     */
    @Test
    void missingExpires() {
        final ConsensusConfig consensusConfig =
                new TestConfigBuilder().getOrCreateConfig().getConfigData(ConsensusConfig.class);
        final Random r = RandomUtils.getRandomPrintSeed();
        final OrphanBufferTester orphanBuffer =
                new OrphanBufferTester(pf -> new OrphanBufferingLinker(consensusConfig, pf, GENERATIONS_STORED));

        final List<GossipEvent> graph = SimpleGraphs.graph5e2n(r);

        orphanBuffer.linkEvent(graph, 2);
        orphanBuffer.linkEvent(graph, 0);
        orphanBuffer.assertGeneration(graph, 0);

        orphanBuffer.linkEvent(graph, 4);
        orphanBuffer.linkEvent(graph, 3);

        orphanBuffer.newAncientGeneration(graph.get(1).getGeneration());
        orphanBuffer.assertGeneration(graph, 2);
        orphanBuffer.assertGeneration(graph, 3, 4);
    }

    /**
     * Creates events with a {@link GraphGenerator}, shuffles them so that they are not in topological order,
     * adds them to the {@link OrphanBufferingLinker}. Expects all events to be returned by the linker.
     */
    @Test
    void eventGenerator() {
        final ConsensusConfig consensusConfig =
                new TestConfigBuilder().getOrCreateConfig().getConfigData(ConsensusConfig.class);
        final int numNodes = 10;
        final int numEvents = 10_000;

        final Random r = RandomUtils.getRandomPrintSeed();
        final StandardGraphGenerator generator = new StandardGraphGenerator(
                r.nextLong(),
                Stream.generate(StandardEventSource::new).limit(numNodes).collect(Collectors.toList()));

        final List<GossipEvent> generatedList = Stream.generate(
                        () -> generator.generateEvent().getBaseEvent())
                .limit(numEvents)
                .collect(Collectors.toList());
        Collections.shuffle(generatedList, r);

        final List<GossipEvent> returnedList = new ArrayList<>();
        final OrphanBufferTester orphanBuffer =
                new OrphanBufferTester(pf -> new OrphanBufferingLinker(consensusConfig, pf, GENERATIONS_STORED));
        for (final GossipEvent event : generatedList) {
            orphanBuffer.linkEvent(event);
            while (orphanBuffer.hasLinkedEvents()) {
                returnedList.add(orphanBuffer.pollLinkedEvent().getBaseEvent());
            }
        }

        assertEquals(generatedList.size(), returnedList.size(), "all events should have been returned");

        final Map<Long, Set<GossipEvent>> expectedMap = new HashMap<>();
        final Map<Long, Set<GossipEvent>> actualMap = new HashMap<>();

        generatedList.forEach(e -> expectedMap
                .computeIfAbsent(e.getGeneration(), k -> new HashSet<>())
                .add(e));
        returnedList.forEach(e -> actualMap
                .computeIfAbsent(e.getGeneration(), k -> new HashSet<>())
                .add(e));

        assertEquals(expectedMap, actualMap, "all events should be the same when sorted by generation");
    }

    /**
     * Tests if the orphan buffer behaves properly when loading a signed state
     */
    @Test
    void loadState() {
        final long ancientGen = 500;
        final long roundGenStart = 1000;
        final long nonAncientGen = 1010;
        final ConsensusConfig consensusConfig =
                new TestConfigBuilder().getOrCreateConfig().getConfigData(ConsensusConfig.class);
        final long roundGenEnd = roundGenStart + consensusConfig.roundsNonAncient();
        // create an orphan buffer
        final OrphanBufferingLinker orphanBuffer =
                new OrphanBufferingLinker(consensusConfig, new ParentFinder(h -> null), GENERATIONS_STORED);

        // before we load the signed state, we will add an orphan into the buffer
        // we expect it to disappear after we load state because it will be ancient
        final GossipEvent preLoadOrphan = EventBuilder.builder()
                .setSelfParent(EventBuilder.builder().setGeneration(1).buildGossipEvent())
                .buildGossipEvent();
        orphanBuffer.linkEvent(preLoadOrphan);
        Assertions.assertFalse(orphanBuffer.hasLinkedEvents(), "the orphan should not be linked");

        // create a signed state and load it into the orphan buffer
        final List<MinGenInfo> minGenInfos = LongStream.range(roundGenStart, roundGenEnd)
                .mapToObj((l) -> new MinGenInfo(l, l))
                .toList();
        final SignedState signedState = Mockito.mock(SignedState.class);
        Mockito.when(signedState.getRound()).thenReturn(roundGenEnd);
        Mockito.when(signedState.getMinGenInfo()).thenReturn(minGenInfos);
        Mockito.when(signedState.getMinGen(Mockito.anyLong())).thenCallRealMethod();
        orphanBuffer.loadFromSignedState(signedState);

        // now we create an ancient events and insert it
        final GossipEvent ancient = EventBuilder.builder()
                .setSelfParent(EventBuilder.builder().setGeneration(ancientGen).buildGossipEvent())
                .buildGossipEvent();
        orphanBuffer.linkEvent(ancient);
        Assertions.assertSame(
                ancient,
                orphanBuffer.pollLinkedEvent().getBaseEvent(),
                "the orphan buffer should return an ancient event as linked, since its parent are ancient");
        Assertions.assertFalse(orphanBuffer.hasLinkedEvents(), "there should be no event left in the buffer");

        // now we create a non-ancient orphan and insert it
        final GossipEvent orphan = EventBuilder.builder()
                .setSelfParent(
                        EventBuilder.builder().setGeneration(nonAncientGen).buildGossipEvent())
                .buildGossipEvent();
        orphanBuffer.linkEvent(orphan);
        Assertions.assertFalse(orphanBuffer.hasLinkedEvents(), "the orphan should not be linked");

        // after we update the generations, we expect only the recent orphan to be returned, the old orphan should have
        // been discarded
        orphanBuffer.updateGenerations(new Generations(roundGenEnd - 1, roundGenEnd, roundGenEnd + 1));
        Assertions.assertSame(
                orphan,
                orphanBuffer.pollLinkedEvent().getBaseEvent(),
                "the orphan buffer should return an ancient event as linked, since its parent are ancient");
        Assertions.assertFalse(orphanBuffer.hasLinkedEvents(), "there should be no event left in the buffer");
    }

    /**
     * Verify that orphans with missing parents outside the generation window are handled appropriately.
     */
    @Test
    void linkEventOutsideGenWindow() {
        final ConsensusConfig consensusConfig =
                new TestConfigBuilder().getOrCreateConfig().getConfigData(ConsensusConfig.class);
        final OrphanBufferTester orphanBuffer =
                new OrphanBufferTester(pf -> new OrphanBufferingLinker(consensusConfig, pf, GENERATIONS_STORED));
        final GossipEvent outsideWindow =
                EventBuilder.builder().setGeneration(GENERATIONS_STORED + 1).buildGossipEvent();

        assertDoesNotThrow(
                () -> orphanBuffer.linkEvent(outsideWindow),
                "Attempting to link an orphan event should not throw an exception");
        orphanBuffer.assertNumOrphans(0);
        orphanBuffer.assertNoLinkedEvents();
    }
}
