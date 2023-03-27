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

package com.swirlds.platform.test.event;

import static java.lang.Integer.max;

import com.swirlds.common.constructable.ConstructableRegistry;
import com.swirlds.common.constructable.ConstructableRegistryException;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.system.address.AddressBook;
import com.swirlds.common.test.merkle.util.MerkleSerializeUtils;
import com.swirlds.common.test.state.DummySwirldState;
import com.swirlds.platform.eventhandling.SignedStateEventsAndGenerations;
import com.swirlds.platform.internal.EventImpl;
import com.swirlds.platform.state.PlatformData;
import com.swirlds.platform.state.PlatformState;
import com.swirlds.platform.state.State;
import com.swirlds.platform.state.signed.SignedState;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.Assertions;

public abstract class EventUtils {

    /**
     * Create a signed state based on the consensus events supplied
     *
     * @param events
     * 		a list of consensus events
     * @param eventsAndGenerations
     * 		holds consensus events and round generation information
     * @param addressBook
     * 		the address book used
     */
    @SuppressWarnings("unchecked")
    public static SignedState createSignedState(
            final List<IndexedEvent> events,
            final SignedStateEventsAndGenerations eventsAndGenerations,
            final AddressBook addressBook) {
        // add events to be filtered
        eventsAndGenerations.addEvents((List<EventImpl>) (List<?>) events);
        // expire those we dont need
        eventsAndGenerations.expire();

        // create a signed state with the original events
        final State originalState = new State();

        final PlatformState platformState = new PlatformState();
        platformState.setAddressBook(addressBook);
        platformState.setPlatformData(new PlatformData());

        final PlatformData platformData = new PlatformData();
        platformData
                .setRound(eventsAndGenerations.getLastRoundReceived())
                .setEvents(eventsAndGenerations.getEventsForSignedState())
                .setMinGenInfo(eventsAndGenerations.getMinGenForSignedState());

        originalState.setSwirldState(new DummySwirldState());
        originalState.setPlatformState(platformState);
        platformState.setPlatformData(platformData);

        return new SignedState(originalState, false);
    }

    /**
     * Creates a copy of the supplies SignedState to simulate a restart or reconnect
     *
     * @param signedState
     * 		the state to copy
     * @return a copy of the state
     */
    public static SignedState serializeDeserialize(final Path dir, final SignedState signedState)
            throws ConstructableRegistryException, IOException {
        final ConstructableRegistry registry = ConstructableRegistry.getInstance();
        registry.registerConstructables("com.swirlds.platform.state");
        registry.registerConstructables("com.swirlds.common.*");
        final State stateCopy = MerkleSerializeUtils.serializeDeserialize(dir, signedState.getState());
        final SignedState signedStateCopy = new SignedState(stateCopy);
        signedStateCopy.setSigSet(signedState.getSigSet());
        return signedStateCopy;
    }

    /**
     * Converts events in the SignedState to IndexedEvent
     *
     * @param signedState
     * 		the state where events are stored
     * @return events converted to IndexedEvent
     */
    public static IndexedEvent[] convertEvents(final SignedState signedState) {
        final IndexedEvent[] indexedEvents = Arrays.stream(signedState
                        .getState()
                        .getPlatformState()
                        .getPlatformData()
                        .getEvents())
                .map(IndexedEvent::new)
                .toArray(IndexedEvent[]::new);
        State.linkParents(indexedEvents);
        signedState.getState().getPlatformState().getPlatformData().setEvents(indexedEvents);
        return indexedEvents;
    }

    /**
     * Get a map from creator sequence pairs to the corresponding event.
     *
     * @param events
     * 		an array of events
     */
    public static Map<Hash, EventImpl> getEventMap(final EventImpl[] events) {
        final Map<Hash, EventImpl> map = new HashMap<>();
        for (final EventImpl event : events) {
            map.put(event.getBaseHash(), event);
        }
        return map;
    }

    /**
     * Find the max generation number for each node in a sequence of events. Assumes events are sorted and that there
     * are no forks.
     *
     * @param events
     * 		a list of events
     * @param numberOfNodes
     * 		ahe total number of nodes
     * @return an array containing the max generation number for each node
     */
    public static long[] getLastGenerationInState(final EventImpl[] events, final int numberOfNodes) {
        final long[] last = new long[numberOfNodes];
        for (final EventImpl event : events) {
            last[(int) event.getCreatorId()] = event.getGeneration();
        }
        return last;
    }

    /**
     * Choose a random integer given a list of probabilistic weights.
     *
     * @param weights
     * 		a list of weights. Each weight must be positive. Sum of all weights must be greater than 0.
     * @return an integer between 0 (inclusive) and weights.size() (exclusive)
     */
    public static int weightedChoice(final Random random, final List<Double> weights) {

        double totalWeight = 0.0;
        for (final Double weight : weights) {
            totalWeight += weight;
        }

        if (totalWeight <= 0.0) {
            throw new IllegalArgumentException("Total weight must be greater than 0.0.");
        }

        // TODO this can be done in logn time with a binary search

        final double randomValue = random.nextDouble() * totalWeight;
        double sum = 0.0;
        int choice = -1;
        for (int index = 0; index < weights.size(); index++) {
            choice = index;
            sum += weights.get(index);
            if (sum > randomValue) {
                break;
            }
        }

        return choice;
    }

    /**
     * Check to see if all events have increasing generation numbers for each node.
     */
    public static boolean areGenerationNumbersValid(final Iterable<IndexedEvent> events, final int numberOfNodes) {
        final Map<Long, Long> previousGenNumber = new HashMap<>();
        for (long nodeID = 0; nodeID < numberOfNodes; nodeID++) {
            previousGenNumber.put(nodeID, -1L);
        }

        for (final IndexedEvent event : events) {
            final long nodeID = event.getCreatorId();
            if (previousGenNumber.get(nodeID) >= event.getGeneration()) {
                return false;
            }
            previousGenNumber.put(nodeID, event.getGeneration());
        }
        return true;
    }

    /**
     * Given a list of events, check if each event comes after all of its ancestors.
     */
    public static boolean isEventOrderValid(final List<IndexedEvent> events) {
        final Set<IndexedEvent> eventsEncountered = new HashSet<>();

        for (final IndexedEvent event : events) {
            final IndexedEvent selfParent = (IndexedEvent) event.getSelfParent();
            final IndexedEvent otherParent = (IndexedEvent) event.getOtherParent();

            if (selfParent != null) {
                if (!eventsEncountered.contains(selfParent)) {
                    return false;
                }
            }

            if (otherParent != null) {
                if (!eventsEncountered.contains(otherParent)) {
                    return false;
                }
            }

            eventsEncountered.add(event);
        }
        return true;
    }

    public static void printEventList(final List<IndexedEvent> events) {
        for (final IndexedEvent event : events) {
            System.out.println(event);
        }
    }

    /**
     * Used for sorting a list of events.
     */
    public static int compareEvents(final IndexedEvent event1, final IndexedEvent event2) {
        return (int) (event1.getGeneratorIndex() - event2.getGeneratorIndex());
    }

    /**
     * Sort a list of events on generator ID.
     *
     * @param events
     * 		An unsorted list of events.
     * @return A sorted list of events.
     */
    public static List<IndexedEvent> sortEventList(final List<IndexedEvent> events) {
        final List<IndexedEvent> sortedEvents = new ArrayList<>(events);
        Collections.sort(sortedEvents, EventUtils::compareEvents);
        return sortedEvents;
    }

    /**
     * Check if two event lists contain the same values (but in a possibly different order).
     */
    public static boolean areEventListsEquivalent(List<IndexedEvent> events1, List<IndexedEvent> events2) {
        events1 = sortEventList(events1);
        events2 = sortEventList(events2);
        return events1.equals(events2);
    }

    /**
     * Add a description to a string builder as to why two events are different.
     */
    public static void getEventDifference(
            final StringBuilder sb, final IndexedEvent event1, final IndexedEvent event2) {

        if (event1.getGeneratorIndex() != event2.getGeneratorIndex()) {
            sb.append("events are completely different\n");
            return;
        }

        checkGeneration(event1, event2, sb);
        checkWitnessStatus(event1, event2, sb);
        checkRoundCreated(event1, event2, sb);
        checkIsStale(event1, event2, sb);
        checkConsensusTimestamp(event1, event2, sb);
        checkRoundReceived(event1, event2, sb);
        checkConsensusOrder(event1, event2, sb);
        checkFame(event1, event2, sb);
    }

    private static void checkFame(final IndexedEvent event1, final IndexedEvent event2, final StringBuilder sb) {
        if (event1.isFameDecided() != event2.isFameDecided()) {
            sb.append("   fame decided mismatch: ")
                    .append(event1.isFameDecided())
                    .append(" vs ")
                    .append(event2.isFameDecided())
                    .append("\n");
        } else {
            if (event1.isFamous() != event2.isFamous()) {
                sb.append("   is famous mismatch: ")
                        .append(event1.isFamous())
                        .append(" vs ")
                        .append(event2.isFamous())
                        .append("\n");
            }
        }
    }

    private static void checkConsensusOrder(
            final IndexedEvent event1, final IndexedEvent event2, final StringBuilder sb) {
        if (event1.getConsensusOrder() != event2.getConsensusOrder()) {
            sb.append("   consensus order mismatch: ")
                    .append(event1.getConsensusOrder())
                    .append(" vs ")
                    .append(event2.getConsensusOrder())
                    .append("\n");
        }
    }

    private static void checkRoundReceived(
            final IndexedEvent event1, final IndexedEvent event2, final StringBuilder sb) {
        if (event1.getRoundReceived() != event2.getRoundReceived()) {
            sb.append("   round received mismatch: ")
                    .append(event1.getRoundReceived())
                    .append(" vs ")
                    .append(event2.getRoundReceived())
                    .append("\n");
        }
    }

    private static void checkConsensusTimestamp(
            final IndexedEvent event1, final IndexedEvent event2, final StringBuilder sb) {
        if (!Objects.equals(event1.getConsensusTimestamp(), event2.getConsensusTimestamp())) {
            sb.append("   consensus timestamp mismatch: ")
                    .append(event1.getConsensusTimestamp())
                    .append(" vs ")
                    .append(event2.getConsensusTimestamp())
                    .append("\n");
        }
    }

    private static void checkIsStale(final IndexedEvent event1, final IndexedEvent event2, final StringBuilder sb) {
        if (event1.isStale() != event2.isStale()) {
            sb.append("   stale mismatch: ")
                    .append(event1.isStale())
                    .append(" vs ")
                    .append(event2.isStale())
                    .append("\n");
        }
    }

    private static void checkRoundCreated(
            final IndexedEvent event1, final IndexedEvent event2, final StringBuilder sb) {
        if (event1.getRoundCreated() != event2.getRoundCreated()) {
            sb.append("   round created mismatch: ")
                    .append(event1.getRoundCreated())
                    .append(" vs ")
                    .append(event2.getRoundCreated())
                    .append("\n");
        }
    }

    private static void checkWitnessStatus(
            final IndexedEvent event1, final IndexedEvent event2, final StringBuilder sb) {
        if (event1.isWitness() != event2.isWitness()) {
            sb.append("   witness mismatch: ")
                    .append(event1.isWitness())
                    .append(" vs ")
                    .append(event2.isWitness())
                    .append("\n");
        }
    }

    private static void checkGeneration(final IndexedEvent event1, final IndexedEvent event2, final StringBuilder sb) {
        if (event1.getGeneration() != event2.getGeneration()) {
            sb.append("   generation mismatch: ")
                    .append(event1.getGeneration())
                    .append(" vs ")
                    .append(event2.getGeneration())
                    .append("\n");
        }
    }

    /**
     * This is debugging utility. Given two lists, sort and compare each event.
     *
     * If one list of events is longer than the other, comparison ends when when the shorter list ends.
     *
     * For each event, compares the following:
     * - generation
     * - isWitness
     * - roundCreated
     *
     * For each event that has consensus, compares the following:
     * - consensusTimestamp
     * - roundReceived
     * - consensusOrder
     *
     * @param events1
     * 		the first list of events. This list should contain ALL events in their original order,
     * 		even if they were not emitted (this can happen if a generator is wrapped in a shuffled generator).
     * @param events2
     * 		the second list of events. This list should contain ALL events in their original order,
     * 		even if they were not emitted (this can happen if a generator is wrapped in a shuffled generator).
     */
    public static void printGranularEventListComparison(List<IndexedEvent> events1, List<IndexedEvent> events2) {

        events1 = sortEventList(events1);
        events2 = sortEventList(events2);

        final int maxIndex = Math.min(events1.size(), events2.size());

        for (int index = 0; index < maxIndex; index++) {

            final IndexedEvent event1 = events1.get(index);
            final IndexedEvent event2 = events2.get(index);

            if (!Objects.equals(event1, event2)) {
                final StringBuilder sb = new StringBuilder()
                        .append("----------\n")
                        .append("Events with index ")
                        .append(event1.getGeneratorIndex())
                        .append(" do not match\n");
                getEventDifference(sb, event1, event2);
                System.out.println(sb.toString());
            }
        }
    }

    /**
     * Check if a list of events are all consensus events.
     */
    public static boolean areEventsConsensusEvents(final List<IndexedEvent> events) {
        for (final IndexedEvent event : events) {
            if (!event.isConsensus()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Find the fraction of events (out of 1.0) that have reached consensus.
     */
    public static double getConsensusEventRatio(final List<IndexedEvent> events) {
        if (events.size() == 0) {
            return 0;
        }
        double total = 0;
        for (final IndexedEvent event : events) {
            if (event.isConsensus()) {
                total += 1;
            }
        }
        return total / events.size();
    }

    /**
     * A "dynamic" value that actually returns a static constant.
     *
     * @param staticValue
     * 		the value to always return
     */
    public static <T> DynamicValue<T> staticDynamicValue(final T staticValue) {
        return (Random random, long eventIndex, T previousValue) -> staticValue;
    }

    /**
     * A dynamic integer that follows a power distribution.
     *
     * P(0) = alpha
     * P(1) = (1 - alpha) * alpha
     * P(2) = (1 - alpha)^2 * alpha
     * P(3) = (1 - alpha)^3 * alpha
     * ...
     * P(n) = (1 - alpha)^n * alpha
     */
    public static DynamicValue<Integer> integerPowerDistribution(final double alpha) {
        return (Random random, long eventIndex, Integer previousValue) -> {
            int ret = 0;
            while (random.nextDouble() > alpha) {
                ret++;
            }
            return ret;
        };
    }

    /**
     * Same as integerPowerDistribution above, except if the value is below a minimum the minimum is used.
     */
    public static DynamicValue<Integer> integerPowerDistribution(final double alpha, final int minimum) {
        return (Random random, long eventIndex, Integer previousValue) -> {
            int ret = 0;
            while (random.nextDouble() > alpha) {
                ret++;
            }
            return max(ret, minimum);
        };
    }

    /**
     * Calculate the age of an event's other parent. Helper method for gatherOtherParentAges.
     *
     * @param events
     * 		a list of events
     * @param eventIndex
     * 		the index of the event to be considered. The age of the event's other parent is returned.
     */
    private static int calculateOtherParentAge(final List<IndexedEvent> events, final int eventIndex) {

        final IndexedEvent event = events.get(eventIndex);
        final IndexedEvent otherParent = (IndexedEvent) event.getOtherParent();
        if (otherParent == null) {
            return 0;
        }
        final long otherParentNode = otherParent.getCreatorId();

        int age = 0;
        for (int index = eventIndex - 1; index >= 0; index--) {
            final IndexedEvent nextEvent = events.get(index);
            if (nextEvent == otherParent) {
                break;
            }
            if (nextEvent.getCreatorId() == otherParentNode) {
                age++;
            }
        }

        return age;
    }

    /**
     * For each event, check the "age" of the other parent. Compile age data into a map.
     *
     * The age of an other parent event is defined as follows:
     * 0 = the other parent is the most recent event from it's parent node
     * 1 = the other parent is the second most recent event from its other parent
     * etc.
     *
     * This method will not work correctly in the presence of forking. Age may not be constant after shuffling
     * node order.
     *
     * @param events
     * 		a list of events
     * @param excludedNodes
     * 		if not null, do not include data about the other parents of events created by the node IDs in this set
     * @return A map: {age : number of events with that age}
     */
    public static Map<Integer, Integer> gatherOtherParentAges(
            final List<IndexedEvent> events, final Set<Long> excludedNodes) {
        final Map<Integer, Integer> map = new HashMap<>();
        for (int eventIndex = 0; eventIndex < events.size(); eventIndex++) {

            if (excludedNodes != null) {
                if (excludedNodes.contains(events.get(eventIndex).getCreatorId())) {
                    continue;
                }
            }

            final int age = calculateOtherParentAge(events, eventIndex);
            if (!map.containsKey(age)) {
                map.put(age, 0);
            }
            map.put(age, map.get(age) + 1);
        }
        return map;
    }

    /**
     * Count the number of consensus events in a list of events.
     *
     * @return pair of counts where the left value is the number of consensus events and the right value is the
     * 		number of stale events
     */
    public static Pair<Integer, Integer> countConsensusAndStaleEvents(final Iterable<IndexedEvent> events) {
        int numCons = 0;
        int numStale = 0;
        for (final IndexedEvent event : events) {
            if (event.isConsensus()) {
                numCons++;
            } else if (event.isStale()) {
                numStale++;
            }
        }
        return Pair.of(numCons, numStale);
    }

    /**
     * Assert that two events are equal. If they are not equal then cause the test to fail and
     * print a meaningful error message.
     *
     * @param description
     * 		a string that is printed if the events are unequal
     * @param e1
     * 		the first event
     * @param e2
     * 		the second event
     */
    public static void assertEventsAreEqual(final String description, final IndexedEvent e1, final IndexedEvent e2) {
        if (!Objects.equals(e1, e2)) {
            final StringBuilder sb = new StringBuilder();
            sb.append(description).append("\n");
            sb.append("Events are not equal:\n");
            sb.append("Event 1: ").append(e1).append("\n");
            sb.append("Event 2: ").append(e2).append("\n");
            getEventDifference(sb, e1, e2);
            throw new RuntimeException(sb.toString());
        }
    }

    /**
     * Assert that two lists of events are equal. If they are not equal then cause the test to fail and
     * print a meaningful error message.
     *
     * @param description
     * 		a string that is printed if the events are unequal
     * @param l1
     * 		the first list of events
     * @param l2
     * 		the second list of events
     */
    public static void assertEventListsAreEqual(
            final String description, final List<IndexedEvent> l1, final List<IndexedEvent> l2) {

        if (l1.size() != l2.size()) {
            final StringBuilder sb = new StringBuilder();
            sb.append(description).append("\n");
            sb.append("Length of event lists are unequal: ");
            sb.append(l1.size()).append(" vs ").append(l2.size());
            throw new RuntimeException(sb.toString());
        }

        for (int index = 0; index < l1.size(); index++) {
            assertEventsAreEqual(description, l1.get(index), l2.get(index));
        }
    }

    /**
     * Assert that base events are equal. This does not check any consensus data, only pre-consensus. If they are not
     * equal then cause the test to fail and print a meaningful error message.
     *
     * @param description
     * 		a string that is printed if the events are unequal
     * @param l1
     * 		the first list of events
     * @param l2
     * 		the second list of events
     */
    public static void assertBaseEventLists(
            final String description, final List<IndexedEvent> l1, final List<IndexedEvent> l2) {

        if (l1.size() != l2.size()) {
            Assertions.fail(String.format("Length of event lists are unequal: %d vs %d", l1.size(), l2.size()));
        }

        for (int index = 0; index < l1.size(); index++) {
            assertBaseEvents(description, l1.get(index), l2.get(index));
        }
    }

    /**
     * Assert that base events are equal. This does not check any consensus data, only pre-consensus. If they are not
     * equal then cause the test to fail and print a meaningful error message.
     *
     * @param description
     * 		a string that is printed if the events are unequal
     * @param e1
     * 		the first event
     * @param e2
     * 		the second event
     */
    public static void assertBaseEvents(final String description, final IndexedEvent e1, final IndexedEvent e2) {
        if (!Objects.equals(e1.getBaseEvent(), e2.getBaseEvent())) {
            final String sb =
                    description + "\n" + "Events are not equal:\n" + "Event 1: " + e1 + "\n" + "Event 2: " + e2 + "\n";
            Assertions.fail(sb);
        }
    }
}
