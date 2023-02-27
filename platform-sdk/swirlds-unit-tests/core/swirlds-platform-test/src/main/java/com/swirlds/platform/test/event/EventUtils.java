/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
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
import com.swirlds.common.system.SoftwareVersion;
import com.swirlds.common.system.address.AddressBook;
import com.swirlds.common.test.merkle.util.MerkleSerializeUtils;
import com.swirlds.common.test.state.DummySwirldState2;
import com.swirlds.platform.eventhandling.SignedStateEventsAndGenerations;
import com.swirlds.platform.internal.EventImpl;
import com.swirlds.platform.state.State;
import com.swirlds.platform.state.signed.SignedState;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import org.apache.commons.lang3.tuple.Pair;

public abstract class EventUtils {
    /**
     * Creates a copy of the supplies SignedState to simulate a restart or reconnect
     *
     * @param signedState the state to copy
     * @return a copy of the state
     */
    public static SignedState serializeDeserialize(final Path dir, final SignedState signedState)
            throws ConstructableRegistryException, IOException {
        final ConstructableRegistry registry = ConstructableRegistry.getInstance();
        registry.registerConstructables("com.swirlds.platform.state");
        registry.registerConstructables("com.swirlds.common.*");
        final State stateCopy =
                MerkleSerializeUtils.serializeDeserialize(dir, signedState.getState());
        final SignedState signedStateCopy = new SignedState(stateCopy);
        signedStateCopy.setSigSet(signedState.getSigSet());
        return signedStateCopy;
    }

    /**
     * Converts events in the SignedState to IndexedEvent
     *
     * @param signedState the state where events are stored
     */
    public static void convertEvents(final SignedState signedState) {
        final IndexedEvent[] indexedEvents =
                Arrays.stream(
                                signedState
                                        .getState()
                                        .getPlatformState()
                                        .getPlatformData()
                                        .getEvents())
                        .map(IndexedEvent::new)
                        .toArray(IndexedEvent[]::new);
        State.linkParents(indexedEvents);
        signedState.getState().getPlatformState().getPlatformData().setEvents(indexedEvents);
    }

    /**
     * Choose a random integer given a list of probabilistic weights.
     *
     * @param weights a list of weights. Each weight must be positive. Sum of all weights must be
     *     greater than 0.
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

    /** Check to see if all events have increasing generation numbers for each node. */
    public static boolean areGenerationNumbersValid(
            final Iterable<IndexedEvent> events, final int numberOfNodes) {
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

    /** Given a list of events, check if each event comes after all of its ancestors. */
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

    /** Used for sorting a list of events. */
    public static int compareEvents(final IndexedEvent event1, final IndexedEvent event2) {
        return (int) (event1.getGeneratorIndex() - event2.getGeneratorIndex());
    }

    /**
     * Sort a list of events on generator ID.
     *
     * @param events An unsorted list of events.
     * @return A sorted list of events.
     */
    public static List<IndexedEvent> sortEventList(final List<IndexedEvent> events) {
        final List<IndexedEvent> sortedEvents = new ArrayList<>(events);
        sortedEvents.sort(EventUtils::compareEvents);
        return sortedEvents;
    }

    /** Check if two event lists contain the same values (but in a possibly different order). */
    public static boolean areEventListsEquivalent(
            List<IndexedEvent> events1, List<IndexedEvent> events2) {
        events1 = sortEventList(events1);
        events2 = sortEventList(events2);
        return events1.equals(events2);
    }

    /**
     * A "dynamic" value that actually returns a static constant.
     *
     * @param staticValue the value to always return
     */
    public static <T> DynamicValue<T> staticDynamicValue(final T staticValue) {
        return (Random random, long eventIndex, T previousValue) -> staticValue;
    }

    /**
     * A dynamic integer that follows a power distribution.
     *
     * <p>P(0) = alpha P(1) = (1 - alpha) * alpha P(2) = (1 - alpha)^2 * alpha P(3) = (1 - alpha)^3
     * * alpha ... P(n) = (1 - alpha)^n * alpha
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
     * Same as integerPowerDistribution above, except if the value is below a minimum the minimum is
     * used.
     */
    public static DynamicValue<Integer> integerPowerDistribution(
            final double alpha, final int minimum) {
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
     * @param events a list of events
     * @param eventIndex the index of the event to be considered. The age of the event's other
     *     parent is returned.
     */
    private static int calculateOtherParentAge(
            final List<IndexedEvent> events, final int eventIndex) {

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
     * <p>The age of an other parent event is defined as follows: 0 = the other parent is the most
     * recent event from it's parent node 1 = the other parent is the second most recent event from
     * its other parent etc.
     *
     * <p>This method will not work correctly in the presence of forking. Age may not be constant
     * after shuffling node order.
     *
     * @param events a list of events
     * @param excludedNodes if not null, do not include data about the other parents of events
     *     created by the node IDs in this set
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
     * @return pair of counts where the left value is the number of consensus events and the right
     *     value is the number of stale events
     */
    public static Pair<Integer, Integer> countConsensusAndStaleEvents(
            final Iterable<EventImpl> events) {
        int numCons = 0;
        int numStale = 0;
        for (final EventImpl event : events) {
            if (event.isConsensus()) {
                numCons++;
            } else if (event.isStale()) {
                numStale++;
            }
        }
        return Pair.of(numCons, numStale);
    }
}
