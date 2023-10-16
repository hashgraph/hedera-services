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

import static com.swirlds.common.test.fixtures.RandomUtils.getRandomPrintSeed;
import static com.swirlds.common.utility.Threshold.STRONG_MINORITY;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.swirlds.common.metrics.noop.NoOpMetrics;
import com.swirlds.common.system.BasicSoftwareVersion;
import com.swirlds.common.system.NodeId;
import com.swirlds.common.system.address.Address;
import com.swirlds.common.system.address.AddressBook;
import com.swirlds.common.system.events.BaseEventHashedData;
import com.swirlds.common.system.events.BaseEventUnhashedData;
import com.swirlds.common.system.events.ConsensusData;
import com.swirlds.common.test.fixtures.RandomAddressBookGenerator;
import com.swirlds.common.test.fixtures.WeightGenerators;
import com.swirlds.platform.components.CriticalQuorum;
import com.swirlds.platform.components.CriticalQuorumImpl;
import com.swirlds.platform.internal.EventImpl;
import com.swirlds.test.framework.TestComponentTags;
import com.swirlds.test.framework.TestTypeTags;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
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
            return new CriticalQuorumImpl(new NoOpMetrics(), new NodeId(0), addressBook);
        }

        @Override
        public String toString() {
            return name;
        }
    }

    /**
     * Build an event containing just the data required for this test.
     */
    private static EventImpl buildSimpleEvent(@NonNull final NodeId creatorId, final long roundCreated) {
        final BaseEventHashedData baseEventHashedData = new BaseEventHashedData(
                new BasicSoftwareVersion(1), creatorId, 0, 0, (byte[]) null, null, Instant.now(), null);

        final BaseEventUnhashedData baseEventUnhashedData = new BaseEventUnhashedData();

        final ConsensusData consensusData = new ConsensusData();
        final EventImpl event = new EventImpl(baseEventHashedData, baseEventUnhashedData, consensusData);
        event.setRoundCreated(roundCreated);

        return event;
    }

    /**
     * Build and initialize a map for tracking event counts.
     */
    private static Map<NodeId, Integer> buildEventCountMap(final AddressBook addressBook) {
        final Map<NodeId, Integer> eventCountMap = new HashMap<>();
        for (final Address address : addressBook) {
            eventCountMap.put(address.getNodeId(), 0);
        }
        return eventCountMap;
    }

    /**
     * Add up all the weight in the critical quorum.
     */
    private static long weightInCriticalQuorum(final AddressBook addressBook, final CriticalQuorum criticalQuorum) {
        long weight = 0;
        for (int index = 0; index < addressBook.getSize(); index++) {
            NodeId nodeId = addressBook.getNodeId(index);
            if (criticalQuorum.isInCriticalQuorum(nodeId)) {
                weight += addressBook.getAddress(nodeId).getWeight();
            }
        }
        return weight;
    }

    /**
     * Find the minimum number of events required to be in the critical quorum.
     */
    private static long thresholdToBeInCriticalQuorum(
            final AddressBook addressBook,
            final CriticalQuorum criticalQuorum,
            final Map<NodeId, Integer> eventCounts) {

        long threshold = 0;

        for (final Address address : addressBook) {
            final int eventCount = eventCounts.get(address.getNodeId());
            if (criticalQuorum.isInCriticalQuorum(address.getNodeId()) && eventCount > threshold) {
                threshold = eventCount;
            }
        }

        return threshold;
    }

    /**
     * Get the weight of all nodes which do not exceed a given event threshold.
     */
    private static long weightNotExceedingThreshold(
            final int threshold, final AddressBook addressBook, final Map<NodeId, Integer> eventCounts) {

        long weight = 0;

        for (final Address address : addressBook) {
            final NodeId nodeId = address.getNodeId();
            if (eventCounts.get(nodeId) <= threshold) {
                weight += addressBook.getAddress(nodeId).getWeight();
            }
        }

        return weight;
    }

    /**
     * Print debug info about the current state of the critical quorum.
     */
    private String criticalQuorumDebugInfo(
            final AddressBook addressBook,
            final CriticalQuorum criticalQuorum,
            final Map<NodeId, Integer> eventCounts) {

        final StringBuilder sb = new StringBuilder();

        sb.append("{\n");
        for (final Address address : addressBook) {
            final NodeId nodeId = address.getNodeId();
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
            final Map<NodeId, Integer> eventCounts) {

        final long totalWeight = addressBook.getTotalWeight();
        final long weightInCriticalQuorum = weightInCriticalQuorum(addressBook, criticalQuorum);

        final long criticalQuorumThreshold = thresholdToBeInCriticalQuorum(addressBook, criticalQuorum, eventCounts);

        assertTrue(
                STRONG_MINORITY.isSatisfiedBy(weightInCriticalQuorum, totalWeight),
                () -> "critical quorum must contain weight equal to or exceeding 1/3 of the total weight."
                        + "\nWith a threshold of "
                        + criticalQuorumThreshold + " the current critical quorum only "
                        + "contains weight representing "
                        + (((float) weightInCriticalQuorum) / totalWeight) + " of the whole.\n"
                        + criticalQuorumDebugInfo(addressBook, criticalQuorum, eventCounts));

        if (criticalQuorumThreshold > 0) {
            // Threshold for the critical quorum should be the smallest possible. If a threshold 1 smaller also forms
            // a critical quorum then it should have been used instead.
            final long weightAtLowerThreshold =
                    weightNotExceedingThreshold((int) (criticalQuorumThreshold - 1), addressBook, eventCounts);

            assertFalse(
                    STRONG_MINORITY.isSatisfiedBy(weightAtLowerThreshold, totalWeight),
                    () -> "critical quorum is expected to contain the minimum amount of weight possible."
                            + "\nCurrent threshold is "
                            + criticalQuorumThreshold + ", but with a threshold of " + (criticalQuorumThreshold - 1)
                            + " the critical quorum contains weight representing "
                            + (((float) weightAtLowerThreshold) / totalWeight)
                            + " of the whole.\n"
                            + criticalQuorumDebugInfo(addressBook, criticalQuorum, eventCounts));
        }

        // All nodes that have an event count at or below the threshold should be part of the critical quorum.
        for (final Address address : addressBook) {
            final NodeId nodeId = address.getNodeId();
            if (eventCounts.get(nodeId) <= criticalQuorumThreshold) {
                // final node ID to make compiler happy with lambda
                assertTrue(
                        criticalQuorum.isInCriticalQuorum(nodeId),
                        () -> "node with event count below threshold should be in the critical quorum.\nThreshold is "
                                + criticalQuorumThreshold
                                + " but node " + nodeId + " with event count " + eventCounts.get(nodeId)
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

        // CriticalQuorumWeight with a various number of nodes, balanced
        arguments.addAll(balancedWeightArgs());

        // CriticalQuorumWeight with a various number of nodes, unbalanced.
        // One node always has 0 weight (if more than 1 node).
        arguments.addAll(unbalancedWeightWithOneZeroWeight());

        // CriticalQuorumWeight with evenly split weight except for a single
        // node that has a strong minority of weight.
        arguments.addAll(singleNodeWithStrongMinority());

        // CriticalQuorumWeight with evenly split weight except for three
        // nodes that has a strong minority of weight. Other nodes each have less weight
        // than the three that make up the strong minority.
        arguments.addAll(threeNodesWithStrongMinority());

        // CriticalQuorumWeight with 1/3 of the nodes having zero weight and the
        // remaining nodes assigned a random amount of weight from 1 to 100.
        arguments.addAll(oneThirdNodesZeroWeight());

        return arguments.stream();
    }

    /**
     * Creates arguments with network sizes from 3 nodes to 20 where one third of the nodes are zero-weight and the
     * remaining nodes are assigned a random weight value between 1 and 100, inclusive.
     *
     * @return test arguments
     */
    private static Collection<Arguments> oneThirdNodesZeroWeight() {
        final List<Arguments> arguments = new ArrayList<>();
        for (int numNodes = 3; numNodes <= 20; numNodes++) {
            final List<Long> weights = WeightGenerators.oneThirdNodesZeroWeight(null, numNodes);
            final AtomicInteger index = new AtomicInteger(0);
            final AddressBook addressBook = new RandomAddressBookGenerator()
                    .setSize(numNodes)
                    .setCustomWeightGenerator(id -> weights.get(index.getAndIncrement()))
                    .build();
            final String name = numNodes + " nodes, one third of nodes are zero-weight, remaining have random weight "
                    + "between [1, 90]";
            arguments.add(Arguments.of(new CriticalQuorumBuilder(name, addressBook)));
        }
        return arguments;
    }

    /**
     * Creates arguments with network sizes from 11 nodes to 20 where three nodes have a strong minority of weight
     * (evenly distributed) and the remaining weight is split evenly among the remaining nodes.
     *
     * @return test arguments
     */
    private static Collection<Arguments> threeNodesWithStrongMinority() {
        final List<Arguments> arguments = new ArrayList<>();
        for (int numNodes = 11; numNodes <= 20; numNodes++) {
            final List<Long> weights = WeightGenerators.threeNodesWithStrongMinority(numNodes);
            final AtomicInteger index = new AtomicInteger(0);
            final AddressBook addressBook = new RandomAddressBookGenerator()
                    .setSize(numNodes)
                    .setCustomWeightGenerator(id -> weights.get(index.getAndIncrement()))
                    .build();
            final String name =
                    numNodes + " nodes, three nodes have a strong minority, remaining weight evenly " + "distributed";
            arguments.add(Arguments.of(new CriticalQuorumBuilder(name, addressBook)));
        }
        return arguments;
    }

    /**
     * Creates arguments with network sizes from 3 nodes to 20 where a single node has a strong minority of weight and
     * the remaining weight is evenly distributed among the remaining nodes.
     *
     * @return test arguments
     */
    private static Collection<Arguments> singleNodeWithStrongMinority() {
        final List<Arguments> arguments = new ArrayList<>();
        for (int numNodes = 3; numNodes <= 9; numNodes++) {
            final List<Long> weights = WeightGenerators.singleNodeWithStrongMinority(numNodes);
            final AtomicInteger index = new AtomicInteger(0);
            final AddressBook addressBook = new RandomAddressBookGenerator()
                    .setSize(numNodes)
                    .setCustomWeightGenerator(id -> weights.get(index.getAndIncrement()))
                    .build();
            final String name = numNodes + " nodes, one node has strong minority, remaining weight evenly distributed";
            arguments.add(Arguments.of(new CriticalQuorumBuilder(name, addressBook)));
        }
        return arguments;
    }

    /**
     * Creates arguments with network sizes from 1 nodes to 9 where a single node has zero weight and the remaining
     * nodes are assigned an incrementing amount of weight.
     *
     * @return test arguments
     */
    private static Collection<Arguments> unbalancedWeightWithOneZeroWeight() {
        final List<Arguments> arguments = new ArrayList<>();
        for (int numNodes = 1; numNodes <= 9; numNodes++) {
            final List<Long> weights = WeightGenerators.incrementingWeightWithOneZeroWeight(numNodes);
            final AtomicInteger index = new AtomicInteger(0);
            final AddressBook addressBook = new RandomAddressBookGenerator()
                    .setSize(numNodes)
                    .setCustomWeightGenerator(id -> weights.get(index.getAndIncrement()))
                    .build();
            final String name = numNodes + " node" + (numNodes == 1 ? "" : "s") + " unbalanced";
            arguments.add(Arguments.of(new CriticalQuorumBuilder(name, addressBook)));
        }
        return arguments;
    }

    /**
     * Creates arguments with network sizes from 1 nodes to 9 where each node has a weight of 1.
     *
     * @return test arguments
     */
    private static Collection<Arguments> balancedWeightArgs() {
        final List<Arguments> arguments = new ArrayList<>();
        for (int numNodes = 1; numNodes <= 9; numNodes++) {
            final AddressBook addressBook = new RandomAddressBookGenerator()
                    .setSize(numNodes)
                    .setCustomWeightGenerator(id -> 1L)
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
        Map<NodeId, Integer> eventCounts = buildEventCountMap(addressBook);

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
            // Noncontiguous NodeId Compatibility: random NodeIds can use random NodeId indexes.
            final NodeId nodeId = addressBook.getNodeId(eventCreatorId);
            eventCounts.put(nodeId, eventCounts.get(nodeId) + 1);

            final EventImpl event = buildSimpleEvent(nodeId, roundCreated);
            criticalQuorum.eventAdded(event);

            assertCriticalQuorumIsValid(addressBook, criticalQuorum, eventCounts);
        }
    }
}
