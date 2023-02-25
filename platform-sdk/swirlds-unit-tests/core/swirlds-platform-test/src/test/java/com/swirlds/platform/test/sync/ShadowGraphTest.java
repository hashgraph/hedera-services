/*
 * Copyright (C) 2021-2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform.test.sync;

import static com.swirlds.platform.consensus.GraphGenerations.FIRST_GENERATION;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.mock;

import com.swirlds.common.crypto.Hash;
import com.swirlds.common.test.RandomUtils;
import com.swirlds.common.utility.CommonUtils;
import com.swirlds.platform.event.EventUtils;
import com.swirlds.platform.internal.EventImpl;
import com.swirlds.platform.metrics.SyncMetrics;
import com.swirlds.platform.sync.GenerationReservation;
import com.swirlds.platform.sync.ShadowEvent;
import com.swirlds.platform.sync.ShadowGraph;
import com.swirlds.platform.sync.ShadowGraphInsertionException;
import com.swirlds.platform.test.event.IndexedEvent;
import com.swirlds.platform.test.event.emitter.EventEmitterFactory;
import com.swirlds.platform.test.event.emitter.StandardEventEmitter;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@DisplayName("Shadow Graph Tests")
class ShadowGraphTest {

    private List<IndexedEvent> generatedEvents;
    private HashMap<Hash, Set<Hash>> ancestorsMap;
    private ShadowGraph shadowGraph;
    private Map<Long, Set<ShadowEvent>> genToShadows;
    private long maxGen;
    private StandardEventEmitter emitter;

    private static Stream<Arguments> graphSizes() {
        return Stream.of(
                Arguments.of(10, 4),
                Arguments.of(100, 4),
                Arguments.of(1000, 4),
                Arguments.of(10, 10),
                Arguments.of(100, 10),
                Arguments.of(1000, 10));
    }

    @BeforeEach
    public void setup() {
        ancestorsMap = new HashMap<>();
        generatedEvents = new ArrayList<>();
        genToShadows = new HashMap<>();
    }

    private void initShadowGraph(final Random random, final int numEvents, final int numNodes) {
        EventEmitterFactory factory = new EventEmitterFactory(random, numNodes);
        emitter = factory.newStandardEmitter();

        shadowGraph = new ShadowGraph(mock(SyncMetrics.class));

        for (int i = 0; i < numEvents; i++) {
            IndexedEvent event = emitter.emitEvent();

            Hash hash = event.getBaseHash();
            ancestorsMap.put(hash, ancestorsOf(event.getSelfParentHash(), event.getOtherParentHash()));
            assertDoesNotThrow(() -> shadowGraph.addEvent(event), "Unable to insert event into shadow graph.");
            assertTrue(
                    shadowGraph.isHashInGraph(hash),
                    "Event that was just added to the shadow graph should still be in the shadow graph.");
            generatedEvents.add(event);
            if (!genToShadows.containsKey(event.getGeneration())) {
                genToShadows.put(event.getGeneration(), new HashSet<>());
            }
            genToShadows.get(event.getGeneration()).add(shadowGraph.shadow(event));
            if (event.getGeneration() > maxGen) {
                maxGen = event.getGeneration();
            }
        }
    }

    /**
     * Tests that the {@link ShadowGraph#findAncestors(Iterable, Predicate)} returns the correct set of
     * ancestors.
     *
     * @param numEvents
     * 		the number of events to put in the shadow graph
     * @param numNodes
     * 		the number of nodes in the shadow graph
     */
    @ParameterizedTest
    @MethodSource("graphSizes")
    void testFindAncestorsForMultipleEvents(final int numEvents, final int numNodes) {
        Random random = RandomUtils.getRandomPrintSeed();

        initShadowGraph(random, numEvents, numNodes);

        Set<ShadowEvent> generatedShadows =
                generatedEvents.stream().map(shadowGraph::shadow).collect(Collectors.toSet());

        Set<ShadowEvent> generatedShadowsSubset = generatedShadows.stream()
                .filter((hash) -> random.nextDouble() < 0.5)
                .collect(Collectors.toSet());

        Set<Hash> actualAncestors = shadowGraph.findAncestors(generatedShadowsSubset, (e) -> true).stream()
                .map(ShadowEvent::getEventBaseHash)
                .collect(Collectors.toSet());

        for (ShadowEvent shadowEvent : generatedShadowsSubset) {
            assertSetsContainSameHashes(ancestorsMap.get(shadowEvent.getEventBaseHash()), actualAncestors);
        }
    }

    @RepeatedTest(10)
    void testFindAncestorsExcludesExpiredEvents() {
        Random random = RandomUtils.getRandomPrintSeed();
        initShadowGraph(random, 100, 4);

        long expireBelowGen = random.nextInt(10) + 1;

        shadowGraph.expireBelow(expireBelowGen);

        Set<ShadowEvent> allEvents = shadowGraph.findAncestors(shadowGraph.getTips(), (e) -> true);
        for (ShadowEvent event : allEvents) {
            assertTrue(
                    event.getEvent().getGeneration() >= expireBelowGen, "Ancestors should not include expired events.");
        }
    }

    private void assertSetsContainSameHashes(final Set<Hash> expected, final Set<Hash> actual) {
        for (final Hash hash : expected) {
            if (!actual.contains(hash)) {
                fail(String.format("Expected to find an ancestor with hash %s", CommonUtils.hex(hash.getValue(), 4)));
            }
        }
    }

    private Set<Hash> ancestorsOf(final Hash selfParent, final Hash otherParent) {
        Set<Hash> ancestorSet = new HashSet<>();
        if (selfParent != null) {
            ancestorSet.add(selfParent);
            if (ancestorsMap.containsKey(selfParent)) {
                ancestorSet.addAll(ancestorsMap.get(selfParent));
            }
        }
        if (otherParent != null) {
            ancestorSet.add(otherParent);
            if (ancestorsMap.containsKey(otherParent)) {
                ancestorSet.addAll(ancestorsMap.get(otherParent));
            }
        }
        return ancestorSet;
    }

    private void printAncestors() {
        for (Map.Entry<Hash, Set<Hash>> entry : ancestorsMap.entrySet()) {
            String hash = CommonUtils.hex(entry.getKey().getValue(), 4);
            List<String> parents = entry.getValue().stream()
                    .filter(Objects::nonNull)
                    .map(h -> CommonUtils.hex(h.getValue(), 4))
                    .collect(Collectors.toList());
            String parent1 = parents.size() > 0 ? parents.get(0) : "none";
            String parent2 = parents.size() > 1 ? parents.get(1) : "none";
            System.out.printf("\n%s = (%s, %s)", hash, parent1, parent2);
        }
    }

    /**
     * This test verifies a single reservation can be made and closed without any event expiry.
     *
     * @param numEvents
     * 		the number of events to put in the shadow graph
     * @param numNodes
     * 		the number of nodes in the shadow graph
     * @throws Exception
     * 		if there was an error closing the reservation
     */
    @ParameterizedTest
    @MethodSource("graphSizes")
    void testSingleReservation(final int numEvents, final int numNodes) throws Exception {
        Random random = RandomUtils.getRandomPrintSeed();
        initShadowGraph(random, numEvents, numNodes);

        GenerationReservation r1 = shadowGraph.reserve();
        assertEquals(FIRST_GENERATION, r1.getGeneration(), "First reservation should reserve generation 1");
        assertEquals(
                1,
                r1.getNumReservations(),
                "The first call to reserve() after initialization should result in 1 reservation.");

        r1.close();
        assertEquals(
                FIRST_GENERATION,
                r1.getGeneration(),
                "The generation should not be affected by a reservation being closed.");
        assertEquals(
                0,
                r1.getNumReservations(),
                "Closing the second reservation should decrement the number of reservations.");
    }

    /**
     * This test verifies multiple reservations of the same generation without any event expiry.
     *
     * @param numEvents
     * 		the number of events to put in the shadow graph
     * @param numNodes
     * 		the number of nodes in the shadow graph
     * @throws Exception
     * 		if there was an error closing the reservation
     */
    @ParameterizedTest
    @MethodSource("graphSizes")
    void testMultipleReservationsNoExpiry(final int numEvents, final int numNodes) throws Exception {
        Random random = RandomUtils.getRandomPrintSeed();
        initShadowGraph(random, numEvents, numNodes);

        GenerationReservation r1 = shadowGraph.reserve();
        GenerationReservation r2 = shadowGraph.reserve();
        assertEquals(
                r1,
                r2,
                "The second call to reserve() prior to the first being closed should return the same object as the "
                        + "first reservation.");
        assertEquals(FIRST_GENERATION, r2.getGeneration(), "Second reservation should reserve generation 1");
        assertEquals(2, r2.getNumReservations(), "The second call to reserve() should result in 2 reservations.");

        r2.close();

        assertEquals(
                FIRST_GENERATION,
                r1.getGeneration(),
                "The generation should not be affected by a reservation being closed.");
        assertEquals(
                1,
                r1.getNumReservations(),
                "Closing the second reservation should decrement the number of reservations.");

        r1.close();

        assertEquals(
                FIRST_GENERATION,
                r1.getGeneration(),
                "The generation should not be affected by a reservation being closed.");
        assertEquals(
                0,
                r1.getNumReservations(),
                "Closing the second reservation should decrement the number of reservations.");
    }

    /**
     * This test verifies multiple reservations of the same generation with event expiry.
     *
     * @param numEvents
     * 		the number of events to put in the shadow graph
     * @param numNodes
     * 		the number of nodes in the shadow graph
     * @throws Exception
     * 		if there was an error closing the reservation
     */
    @ParameterizedTest
    @MethodSource("graphSizes")
    void testMultipleReservationsWithExpiry(final int numEvents, final int numNodes) throws Exception {
        Random random = RandomUtils.getRandomPrintSeed();
        initShadowGraph(random, numEvents, numNodes);

        long expireBelowGen = FIRST_GENERATION + 1;

        GenerationReservation r1 = shadowGraph.reserve();
        shadowGraph.expireBelow(expireBelowGen);

        GenerationReservation r2 = shadowGraph.reserve();
        assertNotEquals(
                r1,
                r2,
                "The call to reserve() after the expire() method is called should not return the same reservation "
                        + "instance.");
        assertEquals(
                expireBelowGen,
                r2.getGeneration(),
                "Reservation after call to expire() should reserve the expired generation + 1");
        assertEquals(
                1, r2.getNumReservations(), "The first reservation after expire() should result in 1 reservation.");

        r2.close();

        assertEquals(
                expireBelowGen,
                r2.getGeneration(),
                "The generation should not be affected by a reservation being closed.");
        assertEquals(
                0,
                r2.getNumReservations(),
                "Closing the second reservation should decrement the number of reservations.");

        assertEquals(
                FIRST_GENERATION,
                r1.getGeneration(),
                "The generation should not be affected by a reservation being closed.");
        assertEquals(
                1,
                r1.getNumReservations(),
                "Closing the second reservation should decrement the number of reservations.");

        r1.close();

        assertEquals(
                FIRST_GENERATION,
                r1.getGeneration(),
                "The generation should not be affected by a reservation being closed.");
        assertEquals(
                0,
                r1.getNumReservations(),
                "Closing the second reservation should decrement the number of reservations.");
    }

    /**
     * This test verifies that event expiry works correctly when there are no reservations.
     *
     * @param numEvents
     * 		the number of events to put in the shadow graph
     * @param numNodes
     * 		the number of nodes in the shadow graph
     * @throws Exception
     * 		if there was an error closing the reservation
     */
    @ParameterizedTest
    @MethodSource("graphSizes")
    void testExpireNoReservations(final int numEvents, final int numNodes) {
        Random random = RandomUtils.getRandomPrintSeed();
        initShadowGraph(random, numEvents, numNodes);

        long expireBelowGen = random.nextInt((int) maxGen) + 2;
        shadowGraph.expireBelow(expireBelowGen);

        assertEventsBelowGenAreExpired(expireBelowGen);
    }

    private void assertEventsBelowGenAreExpired(final long expireBelowGen) {
        genToShadows.forEach((gen, shadowSet) -> {
            if (gen < expireBelowGen) {
                shadowSet.forEach((shadow) -> {
                    assertNull(
                            shadow.getSelfParent(), "Expired events should have their self parent reference nulled.");
                    assertNull(
                            shadow.getOtherParent(), "Expired events should have their other parent reference nulled.");
                    assertFalse(
                            shadowGraph.isHashInGraph(shadow.getEventBaseHash()),
                            "Events in an expire generation should not be in the shadow graph.");
                });
            } else {
                shadowSet.forEach(shadow -> assertTrue(
                        shadowGraph.isHashInGraph(shadow.getEventBaseHash()),
                        "Events in a non-expired generation should be in the shadow graph."));
            }
        });
    }

    /**
     * Tests that event expiry works correctly when there are reservations for generations that should be expired.
     *
     * @param numEvents
     * 		the number of events to put in the shadow graph
     * @param numNodes
     * 		the number of nodes in the shadow graph
     * @throws Exception
     * 		if there was an error closing the reservation
     */
    @ParameterizedTest
    @MethodSource("graphSizes")
    void testExpireWithReservation(final int numEvents, final int numNodes) throws Exception {
        Random random = RandomUtils.getRandomPrintSeed();
        initShadowGraph(random, numEvents, numNodes);
        SyncUtils.printEvents("generated events", generatedEvents);

        GenerationReservation r0 = shadowGraph.reserve();
        shadowGraph.expireBelow(FIRST_GENERATION + 1);
        GenerationReservation r1 = shadowGraph.reserve();
        shadowGraph.expireBelow(FIRST_GENERATION + 2);
        GenerationReservation r2 = shadowGraph.reserve();

        // release the middle reservation to ensure that generations
        // greater than the lowest reserved generation are not expired.
        r1.close();

        // release the last reservation to ensure that the reservation
        // list is iterated through in the correct order
        r2.close();

        // Attempt to expire everything up to
        shadowGraph.expireBelow(FIRST_GENERATION + 2);

        // No event should have been expired because the first generation is reserved
        assertEventsBelowGenAreExpired(0);

        r0.close();
        shadowGraph.expireBelow(FIRST_GENERATION + 2);

        // Now that the reservation is closed, ensure that the events in the below generation 2 are expired
        assertEventsBelowGenAreExpired(FIRST_GENERATION + 2);
    }

    @Test
    void testShadow() {
        initShadowGraph(RandomUtils.getRandomPrintSeed(), 0, 4);
        assertNull(shadowGraph.shadow(null), "Passing null should return null.");
        IndexedEvent event = emitter.emitEvent();
        assertDoesNotThrow(() -> shadowGraph.addEvent(event), "Adding an tip event should succeed.");
        assertEquals(
                event.getBaseHash(),
                shadowGraph.shadow(event).getEventBaseHash(),
                "Shadow event hash should match the original event hash.");
    }

    @Test
    void testShadowsNullListThrowsNPE() {
        initShadowGraph(RandomUtils.getRandomPrintSeed(), 0, 4);
        assertThrows(
                NullPointerException.class,
                () -> shadowGraph.shadows(null),
                "Passing null should cause a NullPointerException.");
    }

    @Test
    void testShadows() {
        initShadowGraph(RandomUtils.getRandomPrintSeed(), 0, 4);
        List<IndexedEvent> events = emitter.emitEvents(10);
        events.forEach(e -> assertDoesNotThrow(() -> shadowGraph.addEvent(e), "Adding new tip events should succeed."));

        List<Hash> hashes = events.stream().map(EventImpl::getBaseHash).collect(Collectors.toList());
        List<ShadowEvent> shadows = shadowGraph.shadows(hashes);
        assertEquals(
                events.size(),
                shadows.size(),
                "The number of shadow events should match the number of events provided.");

        for (ShadowEvent shadow : shadows) {
            assertTrue(
                    hashes.contains(shadow.getEventBaseHash()),
                    "Each event provided should have a shadow event with the same hash.");
        }
    }

    @Test
    void testShadowsWithUnknownEvents() {
        initShadowGraph(RandomUtils.getRandomPrintSeed(), 0, 4);
        List<IndexedEvent> events = emitter.emitEvents(10);
        events.forEach(e -> assertDoesNotThrow(() -> shadowGraph.addEvent(e), "Adding new tip events should succeed."));

        List<Hash> knownHashes = events.stream().map(EventImpl::getBaseHash).collect(Collectors.toList());
        List<Hash> unknownHashes =
                emitter.emitEvents(10).stream().map(EventImpl::getBaseHash).collect(Collectors.toList());

        List<Hash> allHashes = new ArrayList<>(knownHashes.size() + unknownHashes.size());
        allHashes.addAll(knownHashes);
        allHashes.addAll(unknownHashes);
        Collections.shuffle(allHashes);

        List<ShadowEvent> shadows = shadowGraph.shadows(allHashes);
        assertEquals(
                allHashes.size(),
                shadows.size(),
                "The number of shadow events should match the number of hashes provided.");

        for (int i = 0; i < allHashes.size(); i++) {
            Hash hash = allHashes.get(i);
            if (knownHashes.contains(hash)) {
                assertEquals(
                        hash,
                        shadows.get(i).getEventBaseHash(),
                        "Each known hash provided should have a shadow event with the same hash.");
            } else {
                assertNull(shadows.get(i), "Each unknown hash provided should have a null shadow event.");
            }
        }
    }

    @Test
    void testAddNullEvent() {
        initShadowGraph(RandomUtils.getRandomPrintSeed(), 0, 4);
        assertThrows(
                ShadowGraphInsertionException.class,
                () -> shadowGraph.addEvent(null),
                "A null event should not be added to the shadow graph.");
    }

    @RepeatedTest(10)
    void testAddDuplicateEvent() {
        Random random = RandomUtils.getRandomPrintSeed();
        initShadowGraph(random, 10, 4);
        IndexedEvent randomDuplicateEvent = generatedEvents.get(random.nextInt(generatedEvents.size()));
        assertThrows(
                ShadowGraphInsertionException.class,
                () -> shadowGraph.addEvent(randomDuplicateEvent),
                "An event that is already in the shadow graph should not be added.");
    }

    /**
     * Test that an event with a generation that has been expired from the shadow graph is not added to the graph.
     */
    @Test
    void testAddEventWithExpiredGeneration() {
        initShadowGraph(RandomUtils.getRandomPrintSeed(), 100, 4);

        shadowGraph.expireBelow(FIRST_GENERATION + 1);
        genToShadows
                .get(FIRST_GENERATION)
                .forEach(shadow -> assertThrows(
                        ShadowGraphInsertionException.class,
                        () -> shadowGraph.addEvent(shadow.getEvent()),
                        "Expired events should not be added."));
    }

    @Test
    void testAddEventWithUnknownOtherParent() {
        initShadowGraph(RandomUtils.getRandomPrintSeed(), 100, 4);

        IndexedEvent newEvent = emitter.emitEvent();
        newEvent.setOtherParent(emitter.emitEvent());

        assertDoesNotThrow(
                () -> shadowGraph.addEvent(newEvent), "Events with an unknown other parent should be added.");
    }

    @Test
    void testAddEventWithUnknownSelfParent() {
        initShadowGraph(RandomUtils.getRandomPrintSeed(), 100, 4);

        IndexedEvent newEvent = emitter.emitEvent();
        newEvent.setSelfParent(emitter.emitEvent());

        assertDoesNotThrow(() -> shadowGraph.addEvent(newEvent), "Events with an unknown self parent should be added.");
    }

    @Test
    void testAddEventWithExpiredParents() {
        initShadowGraph(RandomUtils.getRandomPrintSeed(), 100, 4);

        IndexedEvent newEvent = emitter.emitEvent();

        shadowGraph.expireBelow(newEvent.getGeneration());

        assertDoesNotThrow(() -> shadowGraph.addEvent(newEvent), "Events with expired parents should be added.");
    }

    @Test
    void testAddEventUpdatesTips() {
        initShadowGraph(RandomUtils.getRandomPrintSeed(), 100, 4);

        int tipsSize = shadowGraph.getTips().size();
        int additionalEvents = 100;

        for (int i = 0; i < additionalEvents; i++) {
            IndexedEvent newTip = emitter.emitEvent();
            assertNull(shadowGraph.shadow(newTip), "The shadow graph should not contain the new event.");
            assertDoesNotThrow(() -> shadowGraph.addEvent(newTip), "The new tip should be added to the shadow graph.");

            ShadowEvent tipShadow = shadowGraph.shadow(newTip);

            assertEquals(
                    tipsSize,
                    shadowGraph.getTips().size(),
                    "There are no forks, so the number of tips should stay the same.");
            assertTrue(shadowGraph.getTips().contains(tipShadow), "The tips should now contain the new tip.");
            assertFalse(
                    shadowGraph.getTips().contains(tipShadow.getSelfParent()),
                    "The tips should not contain the new tip's self parent.");
        }
    }

    @Test
    void testHashgraphEventWithNullHash() {
        initShadowGraph(RandomUtils.getRandomPrintSeed(), 100, 4);

        assertNull(shadowGraph.hashgraphEvent(null), "Passing a null hash should result in a null return value.");
    }

    @RepeatedTest(10)
    void testHashgraphEventWithExistingHash() {
        Random random = RandomUtils.getRandomPrintSeed();
        initShadowGraph(random, 100, 4);

        IndexedEvent randomExistingEvent = generatedEvents.get(random.nextInt(generatedEvents.size()));
        assertEquals(
                randomExistingEvent,
                shadowGraph.hashgraphEvent(randomExistingEvent.getBaseHash()),
                "Unexpected event returned.");
    }

    @Test
    void testClear() {
        Random random = RandomUtils.getRandomPrintSeed();
        initShadowGraph(random, 100, 4);

        GenerationReservation r0 = shadowGraph.reserve();
        GenerationReservation r1 = shadowGraph.reserve();
        r0.close();
        r1.close();

        shadowGraph.clear();

        assertEquals(0, shadowGraph.getTips().size(), "Shadow graph should not have any tips after being cleared.");
        for (IndexedEvent generatedEvent : generatedEvents) {
            assertNull(
                    shadowGraph.shadow(generatedEvent), "Shadow graph should not have any events after being cleared.");
        }
        r0 = shadowGraph.reserve();
        assertEquals(0, r0.getGeneration(), "The first reservation after clearing should reserve generation 0.");
        assertEquals(
                1, r0.getNumReservations(), "The first reservation after clearing should have a single reservation.");
    }

    @Test
    @DisplayName("Test that clear() disconnect all shadow events in the shadow graph")
    void testClearDisconnects() {
        Random random = RandomUtils.getRandomPrintSeed();
        initShadowGraph(random, 100, 4);

        List<ShadowEvent> tips = shadowGraph.getTips();
        Set<ShadowEvent> shadows = new HashSet<>();
        for (ShadowEvent tip : tips) {
            ShadowEvent sp = tip.getSelfParent();
            while (sp != null) {
                shadows.add(sp);
                sp = sp.getSelfParent();
            }
            shadows.add(tip);
        }

        shadowGraph.clear();

        for (ShadowEvent s : shadows) {
            assertNull(s.getSelfParent(), "after a clear, all parents should be disconnected");
            assertNull(s.getOtherParent(), "after a clear, all parents should be disconnected");
        }
    }

    @RepeatedTest(10)
    void testTipsExpired() {
        Random random = RandomUtils.getRandomPrintSeed();
        initShadowGraph(random, 100, 4);

        long oldestTipGen = Long.MAX_VALUE;
        List<ShadowEvent> tipsToExpire = new ArrayList<>();
        for (ShadowEvent tip : shadowGraph.getTips()) {
            oldestTipGen = Math.min(oldestTipGen, tip.getEvent().getGeneration());
        }

        for (ShadowEvent tip : shadowGraph.getTips()) {
            if (tip.getEvent().getGeneration() == oldestTipGen) {
                tipsToExpire.add(tip);
            }
        }

        int numTipsBeforeExpiry = shadowGraph.getTips().size();
        assertTrue(numTipsBeforeExpiry > 0, "Shadow graph should have tips after events are added.");

        shadowGraph.expireBelow(oldestTipGen + 1);

        assertEquals(
                numTipsBeforeExpiry - tipsToExpire.size(),
                shadowGraph.getTips().size(),
                "Shadow graph tips should be included in expiry.");
    }

    @Test
    void testInitFromEvents_NullEventList() {
        shadowGraph = new ShadowGraph(mock(SyncMetrics.class));
        assertThrows(
                IllegalArgumentException.class,
                () -> shadowGraph.initFromEvents(null, 0L),
                "method should throw if supplied with null");
    }

    @Test
    void testInitFromEvents_EmptyEventList() {
        shadowGraph = new ShadowGraph(mock(SyncMetrics.class));
        final List<EventImpl> empty = Collections.emptyList();
        assertThrows(
                IllegalArgumentException.class,
                () -> {
                    shadowGraph.initFromEvents(empty, 0L);
                },
                "method should throw if supplied with empty list");
    }

    @Test
    void testInitFromEvents_EventList() {
        Random random = RandomUtils.getRandomPrintSeed();
        EventEmitterFactory factory = new EventEmitterFactory(random, 4);
        emitter = factory.newStandardEmitter();
        shadowGraph = new ShadowGraph(mock(SyncMetrics.class));

        List<IndexedEvent> events = emitter.emitEvents(20);
        List<EventImpl> filteredEvents = events.stream()
                .filter(e -> e.getGeneration() > 5)
                .sorted(EventUtils::generationComparator)
                .collect(Collectors.toList());

        long minGeneration = filteredEvents.get(0).getGeneration();
        shadowGraph.initFromEvents(filteredEvents, minGeneration);

        for (EventImpl event : filteredEvents) {
            assertNotNull(shadowGraph.shadow(event), "All events should have been added to the shadow graph.");
        }

        GenerationReservation reservation = shadowGraph.reserve();
        assertEquals(
                minGeneration, reservation.getGeneration(), "The generation reserved should match the minGeneration");
    }

    @Test
    void testInitFromEvents_EventListDifferentMinGen() {
        Random random = RandomUtils.getRandomPrintSeed();
        EventEmitterFactory factory = new EventEmitterFactory(random, 4);
        emitter = factory.newStandardEmitter();
        shadowGraph = new ShadowGraph(mock(SyncMetrics.class));

        List<IndexedEvent> events = emitter.emitEvents(20);
        List<EventImpl> filteredEvents =
                events.stream().filter(e -> e.getGeneration() > 5).collect(Collectors.toList());

        long minGeneration = 2;
        shadowGraph.initFromEvents(filteredEvents, minGeneration);

        for (EventImpl event : filteredEvents) {
            assertNotNull(shadowGraph.shadow(event), "All events should have been added to the shadow graph.");
        }

        GenerationReservation reservation = shadowGraph.reserve();
        assertEquals(
                minGeneration, reservation.getGeneration(), "The generation reserved should match the minGeneration");
    }

    @Test
    void testInitFromEvents_AddEventThrows() {
        Random random = RandomUtils.getRandomPrintSeed();
        EventEmitterFactory factory = new EventEmitterFactory(random, 4);
        emitter = factory.newStandardEmitter();
        shadowGraph = new ShadowGraph(mock(SyncMetrics.class));

        List<IndexedEvent> events = emitter.emitEvents(20);
        List<EventImpl> filteredEvents =
                events.stream().filter(e -> e.getGeneration() > 5).collect(Collectors.toList());
        // shuffle the events to cause ShadowGraph.addEvent(EventImpl) to throw
        Collections.shuffle(filteredEvents);

        long minGeneration = 2;
        shadowGraph.initFromEvents(filteredEvents, minGeneration);

        GenerationReservation reservation = shadowGraph.reserve();
        assertEquals(
                minGeneration, reservation.getGeneration(), "The generation reserved should match the minGeneration");
    }

    @Test
    @Disabled("It does not make sense to run this test in CCI since the outcome can vary depending on the load."
            + "The purpose of this test is to tune the performance of this method by running the test locally.")
    void findAncestorsPerformance() throws ShadowGraphInsertionException {
        final int numEvents = 200_000;
        final int numNodes = 21;
        final int numRuns = 10;

        final Random random = RandomUtils.getRandomPrintSeed();
        EventEmitterFactory factory = new EventEmitterFactory(random, numNodes);
        emitter = factory.newStandardEmitter();
        shadowGraph = new ShadowGraph(mock(SyncMetrics.class));
        for (int i = 0; i < numEvents; i++) {
            shadowGraph.addEvent(emitter.emitEvent());
        }

        long millisMin = Integer.MAX_VALUE;
        for (int i = 0; i < numRuns; i++) {
            final List<ShadowEvent> tips = shadowGraph.getTips();
            final Instant start = Instant.now();
            final Set<ShadowEvent> ancestors =
                    shadowGraph.findAncestors(tips, s -> s.getEvent().getGeneration() >= 0);
            final long millis = start.until(Instant.now(), ChronoUnit.MILLIS);
            System.out.printf("Run %d took %d ms\n", i, millis);

            millisMin = Math.min(millis, millisMin);
        }
        double eventsPerSec = numEvents / (millisMin / 1000d);

        System.out.println("Min time taken: " + millisMin);
        System.out.printf("Events/sec: %,.3f\n", eventsPerSec);

        assertTrue(
                eventsPerSec > 5_000_000,
                "this was the performance of this test the last time it was run on a Dell Precision 5540");
    }
}
