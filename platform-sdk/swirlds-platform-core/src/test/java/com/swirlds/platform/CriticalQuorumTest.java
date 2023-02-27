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

package com.swirlds.platform;

import static com.swirlds.common.test.RandomUtils.getRandomPrintSeed;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.swirlds.common.system.address.AddressBook;
import com.swirlds.common.system.events.BaseEventHashedData;
import com.swirlds.common.system.events.BaseEventUnhashedData;
import com.swirlds.common.system.events.ConsensusData;
import com.swirlds.common.test.RandomAddressBookGenerator;
import com.swirlds.common.test.StakeGenerators;
import com.swirlds.platform.components.CriticalQuorum;
import com.swirlds.platform.components.CriticalQuorumImpl;
import com.swirlds.platform.internal.EventImpl;
import com.swirlds.test.framework.TestComponentTags;
import com.swirlds.test.framework.TestTypeTags;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@DisplayName("Critical Quorum Test")
class CriticalQuorumTest {

    /**
     * Simple container object that contains an address book and can build new critical quorum instances.
     */
    private record CriticalQuorumBuilder(String name, AddressBook addressBook) {

        public AddressBook getAddressBook() {
            return addressBook;
        }

        public CriticalQuorum getCriticalQuorum() {
            return new CriticalQuorumImpl(addressBook);
        }

        @Override
        public String toString() {
            return name;
        }
    }

    /**
     * Build an event containing just the data required for this test.
     */
    private static EventImpl buildSimpleEvent(final long creatorId, final long roundCreated) {
        final BaseEventHashedData baseEventHashedData =
                new BaseEventHashedData(creatorId, 0, 0, (byte[]) null, null, null, null);

        final BaseEventUnhashedData baseEventUnhashedData = new BaseEventUnhashedData();

        final ConsensusData consensusData = new ConsensusData();
        final EventImpl event =
                new EventImpl(baseEventHashedData, baseEventUnhashedData, consensusData);
        event.setRoundCreated(roundCreated);

        return event;
    }

    /**
     * Build and initialize a map for tracking event counts.
     */
    private static Map<Integer, Integer> buildEventCountMap(final AddressBook addressBook) {
        final Map<Integer, Integer> eventCountMap = new HashMap<>();
        for (int nodeId = 0; nodeId < addressBook.getSize(); nodeId++) {
            eventCountMap.put(nodeId, 0);
        }
        return eventCountMap;
    }

    /**
     * Add up all the stake in the critical quorum.
     */
    private static long stakeInCriticalQuorum(final AddressBook addressBook, final CriticalQuorum criticalQuorum) {
        long stake = 0;
        for (int nodeId = 0; nodeId < addressBook.getSize(); nodeId++) {
            if (criticalQuorum.isInCriticalQuorum(nodeId)) {
                stake += addressBook.getAddress(nodeId).getStake();
            }
        }
        return stake;
    }

    /**
     * Find the minimum number of events required to be in the critical quorum.
     */
    private static long thresholdToBeInCriticalQuorum(
            final AddressBook addressBook,
            final CriticalQuorum criticalQuorum,
            final Map<Integer, Integer> eventCounts) {

        long threshold = 0;

        for (int nodeId = 0; nodeId < addressBook.getSize(); nodeId++) {
            final int eventCount = eventCounts.get(nodeId);
            if (criticalQuorum.isInCriticalQuorum(nodeId) && eventCount > threshold) {
                threshold = eventCount;
            }
        }

        return threshold;
    }

    /**
     * Get the stake of all nodes which do not exceed a given event threshold.
     */
    private static long stakeNotExceedingThreshold(
            final int threshold, final AddressBook addressBook, final Map<Integer, Integer> eventCounts) {

        long stake = 0;

        for (int nodeId = 0; nodeId < addressBook.getSize(); nodeId++) {
            if (eventCounts.get(nodeId) <= threshold) {
                stake += addressBook.getAddress(nodeId).getStake();
            }
        }

        return stake;
    }

    /**
     * Print debug info about the current state of the critical quorum.
     */
    private String criticalQuorumDebugInfo(
            final AddressBook addressBook,
            final CriticalQuorum criticalQuorum,
            final Map<Integer, Integer> eventCounts) {

        final StringBuilder sb = new StringBuilder();

        sb.append("{\n");
        for (int nodeId = 0; nodeId < addressBook.getSize(); nodeId++) {
            sb.append("   ")
                    .append(nodeId)
                    .append(": count = ")
                    .append(eventCounts.get(nodeId))
                    .append(", in critical quorum = ")
                    .append(criticalQuorum.isInCriticalQuorum(nodeId))
                    .append("\n");
        }
        sb.append("}");

        return sb.toString();
    }

    /**
     * Perform a variety of tests to sanity check the results of the critical quorum.
     */
    private void assertCriticalQuorumIsValid(
            final AddressBook addressBook,
            final CriticalQuorum criticalQuorum,
            final Map<Integer, Integer> eventCounts) {

        final long totalStake = addressBook.getTotalStake();
        final long stakeInCriticalQuorum = stakeInCriticalQuorum(addressBook, criticalQuorum);

        final long criticalQuorumThreshold = thresholdToBeInCriticalQuorum(addressBook, criticalQuorum, eventCounts);

        assertTrue(
                Utilities.isStrongMinority(stakeInCriticalQuorum, totalStake),
                () -> "critical quorum must contain stake equal to or exceeding 1/3 of the total stake."
                        + "\nWith a threshold of "
                        + criticalQuorumThreshold + " the current critical quorum only "
                        + "contains stake representing "
                        + (((float) stakeInCriticalQuorum) / totalStake) + " of the whole.\n"
                        + criticalQuorumDebugInfo(addressBook, criticalQuorum, eventCounts));

        if (criticalQuorumThreshold > 0) {
            // Threshold for the critical quorum should be the smallest possible. If a threshold 1 smaller also forms
            // a critical quorum then it should have been used instead.
            final long stakeAtLowerThreshold =
                    stakeNotExceedingThreshold((int) (criticalQuorumThreshold - 1), addressBook, eventCounts);

            assertFalse(
                    Utilities.isStrongMinority(stakeAtLowerThreshold, totalStake),
                    () -> "critical quorum is expected to contain the minimum amount of stake possible."
                            + "\nCurrent threshold is "
                            + criticalQuorumThreshold + ", but with a threshold of " + (criticalQuorumThreshold - 1)
                            + " the critical quorum contains stake representing "
                            + (((float) stakeAtLowerThreshold) / totalStake)
                            + " of the whole.\n"
                            + criticalQuorumDebugInfo(addressBook, criticalQuorum, eventCounts));
        }

        // All nodes that have an event count at or below the threshold should be part of the critical quorum.
        for (int nodeId = 0; nodeId < addressBook.getSize(); nodeId++) {
            if (eventCounts.get(nodeId) <= criticalQuorumThreshold) {
                // final node ID to make compiler happy with lambda
                final int nid = nodeId;
                assertTrue(
                        criticalQuorum.isInCriticalQuorum(nodeId),
                        () -> "node with event count below threshold should be in the critical quorum.\nThreshold is "
                                + criticalQuorumThreshold
                                + " but node " + nid + " with event count " + eventCounts.get(nid)
                                + " is not in the critical quorum.\n"
                                + criticalQuorumDebugInfo(addressBook, criticalQuorum, eventCounts));
            }
        }
    }

    /**
     * Build a variety of different configurations with which to test critical quorum
     */
    protected static Stream<Arguments> buildArguments() {
        final List<Arguments> arguments = new ArrayList<>();

        // CriticalQuorumStake with a various number of nodes, balanced
        arguments.addAll(balancedStakeArgs());

        // CriticalQuorumStake with a various number of nodes, unbalanced.
        // One node always has 0 stake (if more than 1 node).
        arguments.addAll(unbalancedStakeWithOneZeroStake());

        // CriticalQuorumStake with evenly split stake except for a single
        // node that has a strong minority of stake.
        arguments.addAll(singleNodeWithStrongMinority());

        // CriticalQuorumStake with evenly split stake except for three
        // nodes that has a strong minority of stake. Other nodes each have less stake
        // than the three that make up the strong minority.
        arguments.addAll(threeNodesWithStrongMinority());

        // CriticalQuorumStake with 1/3 of the nodes having zero stake and the
        // remaining nodes assigned a random amount of stake from 1 to 100.
        arguments.addAll(oneThirdNodesZeroStake());

        return arguments.stream();
    }

    /**
     * Creates arguments with network sizes from 3 nodes to 20 where one third of the nodes are zero-stake and the
     * remaining nodes are assigned a random stake value between 1 and 100, inclusive.
     *
     * @return test arguments
     */
    private static Collection<Arguments> oneThirdNodesZeroStake() {
        final List<Arguments> arguments = new ArrayList<>();
        for (int numNodes = 3; numNodes <= 20; numNodes++) {
            final List<Long> stakes = StakeGenerators.oneThirdNodesZeroStake(null, numNodes);
            final AddressBook addressBook = new RandomAddressBookGenerator()
                    .setSize(numNodes)
                    .setSequentialIds(true)
                    .setCustomStakeGenerator(id -> stakes.get((int) id))
                    .build();
            final String name = numNodes + " nodes, one third of nodes are zero-stake, remaining have random stake "
                    + "between [1, 90]";
            arguments.add(Arguments.of(new CriticalQuorumBuilder(name, addressBook)));
        }
        return arguments;
    }

    /**
     * Creates arguments with network sizes from 11 nodes to 20 where three nodes have a strong minority of stake
     * (evenly distributed) and the remaining stake is split evenly among the remaining nodes.
     *
     * @return test arguments
     */
    private static Collection<Arguments> threeNodesWithStrongMinority() {
        final List<Arguments> arguments = new ArrayList<>();
        for (int numNodes = 11; numNodes <= 20; numNodes++) {
            final List<Long> stakes = StakeGenerators.threeNodesWithStrongMinority(numNodes);
            final AddressBook addressBook = new RandomAddressBookGenerator()
                    .setSize(numNodes)
                    .setSequentialIds(true)
                    .setCustomStakeGenerator(id -> stakes.get((int) id))
                    .build();
            final String name =
                    numNodes + " nodes, three nodes have a strong minority, remaining stake evenly " + "distributed";
            arguments.add(Arguments.of(new CriticalQuorumBuilder(name, addressBook)));
        }
        return arguments;
    }

    /**
     * Creates arguments with network sizes from 3 nodes to 20 where a single node has a strong minority of stake and
     * the remaining stake is evenly distributed among the remaining nodes.
     *
     * @return test arguments
     */
    private static Collection<Arguments> singleNodeWithStrongMinority() {
        final List<Arguments> arguments = new ArrayList<>();
        for (int numNodes = 3; numNodes <= 9; numNodes++) {
            final List<Long> stakes = StakeGenerators.singleNodeWithStrongMinority(numNodes);
            final AddressBook addressBook = new RandomAddressBookGenerator()
                    .setSize(numNodes)
                    .setSequentialIds(true)
                    .setCustomStakeGenerator(id -> stakes.get((int) id))
                    .build();
            final String name = numNodes + " nodes, one node has strong minority, remaining stake evenly distributed";
            arguments.add(Arguments.of(new CriticalQuorumBuilder(name, addressBook)));
        }
        return arguments;
    }

    /**
     * Creates arguments with network sizes from 1 nodes to 9 where a single node has zero stake and the remaining
     * nodes are assigned an incrementing amount of stake.
     *
     * @return test arguments
     */
    private static Collection<Arguments> unbalancedStakeWithOneZeroStake() {
        final List<Arguments> arguments = new ArrayList<>();
        for (int numNodes = 1; numNodes <= 9; numNodes++) {
            final List<Long> stakes = StakeGenerators.incrementingStakeWithOneZeroStake(numNodes);
            final AddressBook addressBook = new RandomAddressBookGenerator()
                    .setSize(numNodes)
                    .setSequentialIds(true)
                    .setCustomStakeGenerator(id -> stakes.get((int) id))
                    .build();
            final String name = numNodes + " node" + (numNodes == 1 ? "" : "s") + " unbalanced";
            arguments.add(Arguments.of(new CriticalQuorumBuilder(name, addressBook)));
        }
        return arguments;
    }

    /**
     * Creates arguments with network sizes from 1 nodes to 9 where each node has a stake of 1.
     *
     * @return test arguments
     */
    private static Collection<Arguments> balancedStakeArgs() {
        final List<Arguments> arguments = new ArrayList<>();
        for (int numNodes = 1; numNodes <= 9; numNodes++) {
            final List<Long> stakes = Collections.nCopies(numNodes, 1L);
            final AddressBook addressBook = new RandomAddressBookGenerator()
                    .setSize(numNodes)
                    .setSequentialIds(true)
                    .setCustomStakeGenerator(id -> stakes.get((int) id))
                    .build();
            final String name = numNodes + " node" + (numNodes == 1 ? "" : "s") + " balanced";
            arguments.add(Arguments.of(new CriticalQuorumBuilder(name, addressBook)));
        }
        return arguments;
    }

    /**
     * Randomly create events for nodes. Assert that critical quorum is maintained.
     */
    @ParameterizedTest
    @MethodSource("buildArguments")
    @Tag(TestTypeTags.FUNCTIONAL)
    @Tag(TestComponentTags.PLATFORM)
    @Tag(TestComponentTags.GOSSIP)
    @DisplayName("Random Event Test")
    void randomEventTest(final CriticalQuorumBuilder criticalQuorumBuilder) {
        final AddressBook addressBook = criticalQuorumBuilder.getAddressBook();
        final CriticalQuorum criticalQuorum = criticalQuorumBuilder.getCriticalQuorum();
        Map<Integer, Integer> eventCounts = buildEventCountMap(addressBook);

        // Should be valid when there are no events
        assertCriticalQuorumIsValid(addressBook, criticalQuorum, eventCounts);

        final Random random = getRandomPrintSeed();

        // The number of events to simulate
        final int numberOfEvents = 100 * addressBook.getSize();

        // The probability that an event will be in a new round
        final double nextRoundChance = 0.1 / addressBook.getSize();

        long roundCreated = 0;

        for (int eventIndex = 0; eventIndex < numberOfEvents; eventIndex++) {
            // Every once in a while, proceed to the next round
            if (random.nextDouble() < nextRoundChance) {
                roundCreated++;
                eventCounts = buildEventCountMap(addressBook);
            }

            final int eventCreatorId = random.nextInt(addressBook.getSize());
            eventCounts.put(eventCreatorId, eventCounts.get(eventCreatorId) + 1);

            final EventImpl event = buildSimpleEvent(eventCreatorId, roundCreated);
            criticalQuorum.eventAdded(event);

            assertCriticalQuorumIsValid(addressBook, criticalQuorum, eventCounts);
        }
    }
}
