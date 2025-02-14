/*
 * Copyright (C) 2020-2024 Hedera Hashgraph, LLC
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

import static com.swirlds.platform.test.consensus.ConsensusTestArgs.BIRTH_ROUND_PLATFORM_CONTEXT;
import static com.swirlds.platform.test.consensus.ConsensusTestArgs.DEFAULT_PLATFORM_CONTEXT;
import static com.swirlds.platform.test.fixtures.event.EventUtils.areGenerationNumbersValid;
import static com.swirlds.platform.test.fixtures.event.EventUtils.gatherOtherParentAges;
import static com.swirlds.platform.test.fixtures.event.EventUtils.integerPowerDistribution;
import static com.swirlds.platform.test.fixtures.event.EventUtils.isEventOrderValid;
import static com.swirlds.platform.test.fixtures.event.EventUtils.staticDynamicValue;
import static java.util.Arrays.asList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.platform.NodeId;
import com.swirlds.common.test.fixtures.junit.tags.TestComponentTags;
import com.swirlds.platform.internal.EventImpl;
import com.swirlds.platform.system.address.AddressBook;
import com.swirlds.platform.test.event.source.ForkingEventSource;
import com.swirlds.platform.test.fixtures.event.DynamicValue;
import com.swirlds.platform.test.fixtures.event.DynamicValueGenerator;
import com.swirlds.platform.test.fixtures.event.generator.GraphGenerator;
import com.swirlds.platform.test.fixtures.event.generator.StandardGraphGenerator;
import com.swirlds.platform.test.fixtures.event.source.StandardEventSource;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Sanity checks for the event generator utilities.
 */
@DisplayName("Event Generator Tests")
public class GraphGeneratorTests {

    /**
     * Assert that two lists of events are distinct but equal objects.
     */
    private void assertEventListEquality(final List<EventImpl> events1, final List<EventImpl> events2) {
        assertEquals(events1.size(), events2.size());
        for (int index = 0; index < events1.size(); index++) {
            final EventImpl event1 = events1.get(index);
            final EventImpl event2 = events2.get(index);

            assertNotSame(event1, event2);
            assertEquals(event1, event2);
        }
    }

    /**
     * Ensure that a generator has sane output after a reset.
     */
    public void validateReset(final GraphGenerator<?> generator) {
        System.out.println("Validate Reset");
        final int numberOfEvents = 1000;

        generator.reset();

        final List<EventImpl> events1 = generator.generateEvents(numberOfEvents);
        assertEquals(numberOfEvents, events1.size());

        generator.reset();

        final List<EventImpl> events2 = generator.generateEvents(numberOfEvents);
        assertEquals(numberOfEvents, events2.size());

        assertEventListEquality(events1, events2);

        generator.reset();
    }

    /**
     * Ensure that a copy made of a new generator has sane output.
     */
    public void validateCopyOfNewGenerator(final GraphGenerator<?> generator) {
        System.out.println("Validate Copy of New Generator");
        final GraphGenerator<?> generatorCopy = generator.copy();

        final int numberOfEvents = 1000;

        final List<EventImpl> events1 = generator.generateEvents(numberOfEvents);
        assertEquals(numberOfEvents, events1.size());

        final List<EventImpl> events2 = generatorCopy.generateEvents(numberOfEvents);
        assertEquals(numberOfEvents, events2.size());

        assertEventListEquality(events1, events2);

        generator.reset();
    }

    /**
     * Ensure that a copy made of an active generator has sane output.
     */
    public void validateCopyOfActiveGenerator(final GraphGenerator<?> generator) {
        System.out.println("Validate Copy of Active Generator");

        final int numberOfEvents = 1000;

        generator.skip(numberOfEvents);
        final GraphGenerator<?> generatorCopy = generator.copy();

        final List<EventImpl> events1 = generator.generateEvents(numberOfEvents);
        assertEquals(numberOfEvents, events1.size());

        final List<EventImpl> events2 = generatorCopy.generateEvents(numberOfEvents);
        assertEquals(numberOfEvents, events2.size());

        assertEventListEquality(events1, events2);

        generator.reset();
    }

    /**
     * Ensure that a clean copy made of an active generator has sane output.
     */
    public void validateCleanCopyOfActiveGenerator(final GraphGenerator<?> generator) {
        System.out.println("Validate Clean Copy of Active Generator");

        final int numberOfEvents = 1000;

        generator.skip(numberOfEvents);
        final List<EventImpl> events1 = generator.generateEvents(numberOfEvents);
        assertEquals(numberOfEvents, events1.size());

        final GraphGenerator<?> generatorCopy = generator.cleanCopy();

        generatorCopy.skip(numberOfEvents);
        final List<EventImpl> events2 = generatorCopy.generateEvents(numberOfEvents);
        assertEquals(numberOfEvents, events2.size());

        assertEventListEquality(events1, events2);

        generator.reset();
    }

    /**
     * Verify that a certain percent of events have a node as the other parent.
     *
     * @param events
     * 		A list of events.
     * @param nodeId
     * 		The node ID to check.
     * @param expectedRatio
     * 		The expected ratio (as a fraction of 1.0).
     * @param tolerance
     * 		The allowable tolerance. For example, a ratio of 0.25 with a tolerance of 0.01 will accept
     * 		any value between 0.24 and 0.26.
     */
    protected void verifyExpectedOtherParentRatio(
            final List<EventImpl> events, final NodeId nodeId, final double expectedRatio, final double tolerance) {

        int count = 0;
        for (final EventImpl event : events) {
            final NodeId otherParentId;
            if (event.getBaseEvent().getOtherParents().isEmpty()) {
                otherParentId = null;
            } else {
                otherParentId =
                        event.getBaseEvent().getOtherParents().getFirst().creator();
            }

            if (Objects.equals(otherParentId, nodeId)) {
                count++;
            }
        }

        final double ratio = ((double) count) / events.size();

        assertTrue(ratio <= expectedRatio + tolerance);
        assertTrue(ratio >= expectedRatio - tolerance);
    }

    /**
     * Verify that a certain percent of events originate from a given node.
     *
     * @param events
     * 		A list of events.
     * @param nodeId
     * 		The node ID to check.
     * @param expectedRatio
     * 		The expected ratio (as a fraction of 1.0).
     * @param tolerance
     * 		The allowable tolerance. For example, a ratio of 0.25 with a tolerance of 0.01 will accept
     * 		any value between 0.24 and 0.26.
     */
    protected void verifyExpectedParentRatio(
            final List<EventImpl> events, final NodeId nodeId, final double expectedRatio, final double tolerance) {

        int count = 0;
        for (final EventImpl event : events) {
            if (Objects.equals(event.getCreatorId(), nodeId)) {
                count++;
            }
        }

        final double ratio = ((double) count) / events.size();

        assertTrue(ratio <= expectedRatio + tolerance);
        assertTrue(ratio >= expectedRatio - tolerance);
    }

    /**
     * Ensure that the distribution of events between nodes behaves as expected.
     */
    public void validateParentDistribution(GraphGenerator<?> generator) {
        System.out.println("Validate Parent Distribution");

        assertEquals(4, generator.getNumberOfSources());
        final AddressBook addressBook = generator.getAddressBook();

        // Test even weights
        generator = generator.cleanCopy();
        generator.getSource(addressBook.getNodeId(0)).setNewEventWeight(1.0);
        generator.getSource(addressBook.getNodeId(1)).setNewEventWeight(1.0);
        generator.getSource(addressBook.getNodeId(2)).setNewEventWeight(1.0);
        generator.getSource(addressBook.getNodeId(3)).setNewEventWeight(1.0);

        List<EventImpl> events = generator.generateEvents(1000);
        verifyExpectedParentRatio(events, addressBook.getNodeId(0), 0.25, 0.05);
        verifyExpectedParentRatio(events, addressBook.getNodeId(1), 0.25, 0.05);
        verifyExpectedParentRatio(events, addressBook.getNodeId(2), 0.25, 0.05);
        verifyExpectedParentRatio(events, addressBook.getNodeId(3), 0.25, 0.05);

        // Test un-even weights
        generator.reset();
        generator.getSource(addressBook.getNodeId(0)).setNewEventWeight(0.5);
        generator.getSource(addressBook.getNodeId(1)).setNewEventWeight(1.0);
        generator.getSource(addressBook.getNodeId(2)).setNewEventWeight(1.0);
        generator.getSource(addressBook.getNodeId(3)).setNewEventWeight(2.0);

        events = generator.generateEvents(1000);
        verifyExpectedParentRatio(events, addressBook.getNodeId(0), 0.5 / 4.5, 0.05);
        verifyExpectedParentRatio(events, addressBook.getNodeId(1), 1.0 / 4.5, 0.05);
        verifyExpectedParentRatio(events, addressBook.getNodeId(2), 1.0 / 4.5, 0.05);
        verifyExpectedParentRatio(events, addressBook.getNodeId(3), 2.0 / 4.5, 0.05);

        // Test dynamic weights
        generator.reset();
        generator.getSource(addressBook.getNodeId(0)).setNewEventWeight(1.0);
        generator.getSource(addressBook.getNodeId(1)).setNewEventWeight(1.0);
        generator.getSource(addressBook.getNodeId(2)).setNewEventWeight(1.0);
        final DynamicValue<Double> dynamicWeight = (Random random, long eventIndex, Double previousValue) -> {
            if (eventIndex < 1000) {
                return 0.0;
            } else if (eventIndex < 2000) {
                return 1.0;
            } else {
                return 2.0;
            }
        };
        generator.getSource(addressBook.getNodeId(3)).setNewEventWeight(dynamicWeight);

        events = generator.generateEvents(1000);
        verifyExpectedParentRatio(events, addressBook.getNodeId(0), 0.33, 0.05);
        verifyExpectedParentRatio(events, addressBook.getNodeId(1), 0.33, 0.05);
        verifyExpectedParentRatio(events, addressBook.getNodeId(2), 0.33, 0.05);
        verifyExpectedParentRatio(events, addressBook.getNodeId(3), 0.0, 0.05);

        events = generator.generateEvents(1000);
        verifyExpectedParentRatio(events, addressBook.getNodeId(0), 0.25, 0.05);
        verifyExpectedParentRatio(events, addressBook.getNodeId(1), 0.25, 0.05);
        verifyExpectedParentRatio(events, addressBook.getNodeId(2), 0.25, 0.05);
        verifyExpectedParentRatio(events, addressBook.getNodeId(3), 0.25, 0.05);

        events = generator.generateEvents(1000);
        verifyExpectedParentRatio(events, addressBook.getNodeId(0), 0.2, 0.05);
        verifyExpectedParentRatio(events, addressBook.getNodeId(1), 0.2, 0.05);
        verifyExpectedParentRatio(events, addressBook.getNodeId(2), 0.2, 0.05);
        verifyExpectedParentRatio(events, addressBook.getNodeId(3), 0.4, 0.05);
    }

    /**
     * If given a StandardGraphGenerator or a ShuffledEventGenerator containing a StandardGraphGenerator, return
     * the StandardGraphGenerator.
     */
    private StandardGraphGenerator getBaseGenerator(final GraphGenerator<?> generator) {
        if (generator instanceof StandardGraphGenerator) {
            return (StandardGraphGenerator) generator;
        } else {
            throw new IllegalStateException("Unrecognized type of GraphGenerator: " + generator.getClass());
        }
    }

    /**
     * Ensure that the distribution of other parents behaves as expected.
     */
    public void validateOtherParentDistribution(GraphGenerator<?> generator) {
        System.out.println("Validate Other Parent Distribution");

        // Even distribution
        generator = generator.cleanCopy();
        final AddressBook addressBook = generator.getAddressBook();
        StandardGraphGenerator baseGenerator = getBaseGenerator(generator);
        baseGenerator.setOtherParentAffinity(asList(
                asList(0.0, 1.0, 1.0, 1.0),
                asList(1.0, 0.0, 1.0, 1.0),
                asList(1.0, 1.0, 0.0, 1.0),
                asList(1.0, 1.0, 1.0, 0.0)));
        List<EventImpl> events = generator.generateEvents(1000);
        verifyExpectedOtherParentRatio(events, addressBook.getNodeId(0), 0.25, 0.05);
        verifyExpectedOtherParentRatio(events, addressBook.getNodeId(1), 0.25, 0.05);
        verifyExpectedOtherParentRatio(events, addressBook.getNodeId(2), 0.25, 0.05);
        verifyExpectedOtherParentRatio(events, addressBook.getNodeId(3), 0.25, 0.05);

        // Node 0 is never used as the other parent
        generator.reset();
        baseGenerator = getBaseGenerator(generator);
        baseGenerator.setOtherParentAffinity(asList(
                asList(0.0, 1.0, 1.0, 1.0),
                asList(0.0, 0.0, 1.0, 1.0),
                asList(0.0, 1.0, 0.0, 1.0),
                asList(0.0, 1.0, 1.0, 0.0)));
        events = generator.generateEvents(1000);
        verifyExpectedOtherParentRatio(events, addressBook.getNodeId(0), 0.0, 0.0);
        verifyExpectedOtherParentRatio(events, addressBook.getNodeId(1), 0.333, 0.05);
        verifyExpectedOtherParentRatio(events, addressBook.getNodeId(2), 0.333, 0.05);
        verifyExpectedOtherParentRatio(events, addressBook.getNodeId(3), 0.333, 0.05);

        // Node 3 is never used as the other parent
        generator.reset();
        baseGenerator = getBaseGenerator(generator);
        baseGenerator.setOtherParentAffinity(asList(
                asList(0.0, 1.0, 1.0, 0.0),
                asList(1.0, 0.0, 1.0, 0.0),
                asList(1.0, 1.0, 0.0, 0.0),
                asList(1.0, 1.0, 1.0, 0.0)));
        events = generator.generateEvents(1000);
        verifyExpectedOtherParentRatio(events, addressBook.getNodeId(0), 0.333, 0.05);
        verifyExpectedOtherParentRatio(events, addressBook.getNodeId(1), 0.333, 0.05);
        verifyExpectedOtherParentRatio(events, addressBook.getNodeId(2), 0.333, 0.05);
        verifyExpectedOtherParentRatio(events, addressBook.getNodeId(3), 0.0, 0.0);

        // Node 0 uses node 1 as the other parent twice as often as it uses either 2 or 3
        generator.reset();
        baseGenerator = getBaseGenerator(generator);
        baseGenerator.setOtherParentAffinity(asList(
                asList(0.0, 2.0, 1.0, 1.0),
                asList(1.0, 0.0, 1.0, 1.0),
                asList(1.0, 1.0, 0.0, 1.0),
                asList(1.0, 1.0, 1.0, 0.0)));
        events = generator.generateEvents(1000);
        verifyExpectedOtherParentRatio(events, addressBook.getNodeId(0), 0.25, 0.05);
        verifyExpectedOtherParentRatio(events, addressBook.getNodeId(1), 0.5 * 0.333 + 0.25 * 0.5, 0.05);
        verifyExpectedOtherParentRatio(events, addressBook.getNodeId(2), 0.5 * 0.333 + 0.25 * 0.25, 0.05);
        verifyExpectedOtherParentRatio(events, addressBook.getNodeId(3), 0.5 * 0.333 + 0.25 * 0.25, 0.05);

        // Dynamic other parent affinity
        generator.reset();
        baseGenerator = getBaseGenerator(generator);

        final List<List<Double>> evenAffinity = asList(
                asList(0.0, 1.0, 1.0, 1.0),
                asList(1.0, 0.0, 1.0, 1.0),
                asList(1.0, 1.0, 0.0, 1.0),
                asList(1.0, 1.0, 1.0, 0.0));
        final List<List<Double>> node0NeverOtherParentAffinity = asList(
                asList(0.0, 1.0, 1.0, 1.0),
                asList(0.0, 0.0, 1.0, 1.0),
                asList(0.0, 1.0, 0.0, 1.0),
                asList(0.0, 1.0, 1.0, 0.0));
        final List<List<Double>> node3NeverOtherParentAffinity = asList(
                asList(0.0, 1.0, 1.0, 0.0),
                asList(1.0, 0.0, 1.0, 0.0),
                asList(1.0, 1.0, 0.0, 0.0),
                asList(1.0, 1.0, 1.0, 0.0));
        final DynamicValue<List<List<Double>>> affinityGenerator =
                (Random random, long eventIndex, List<List<Double>> previousValue) -> {
                    if (eventIndex < 1000) {
                        return evenAffinity;
                    } else if (eventIndex < 2000) {
                        return node0NeverOtherParentAffinity;
                    } else {
                        return node3NeverOtherParentAffinity;
                    }
                };
        baseGenerator.setOtherParentAffinity(affinityGenerator);

        events = generator.generateEvents(1000);
        verifyExpectedOtherParentRatio(events, addressBook.getNodeId(0), 0.25, 0.05);
        verifyExpectedOtherParentRatio(events, addressBook.getNodeId(1), 0.25, 0.05);
        verifyExpectedOtherParentRatio(events, addressBook.getNodeId(2), 0.25, 0.05);
        verifyExpectedOtherParentRatio(events, addressBook.getNodeId(3), 0.25, 0.05);

        events = generator.generateEvents(1000);
        verifyExpectedOtherParentRatio(events, addressBook.getNodeId(0), 0.0, 0.0);
        verifyExpectedOtherParentRatio(events, addressBook.getNodeId(1), 0.333, 0.05);
        verifyExpectedOtherParentRatio(events, addressBook.getNodeId(2), 0.333, 0.05);
        verifyExpectedOtherParentRatio(events, addressBook.getNodeId(3), 0.333, 0.05);

        events = generator.generateEvents(1000);
        verifyExpectedOtherParentRatio(events, addressBook.getNodeId(0), 0.333, 0.05);
        verifyExpectedOtherParentRatio(events, addressBook.getNodeId(1), 0.333, 0.05);
        verifyExpectedOtherParentRatio(events, addressBook.getNodeId(2), 0.333, 0.05);
        verifyExpectedOtherParentRatio(events, addressBook.getNodeId(3), 0.0, 0.0);
    }

    /**
     * Check that events emitted by this generator are in the proper order.
     */
    public void validateEventOrder(final GraphGenerator<?> generator) {
        System.out.println("Validate Event Order");
        final List<EventImpl> events = generator.generateEvents(1000);
        assertTrue(areGenerationNumbersValid(events, generator.getNumberOfSources()));
        assertTrue(isEventOrderValid(events));

        generator.reset();
    }

    /**
     * Run a generator through a gauntlet of sanity checks.
     */
    public void generatorSanityChecks(final GraphGenerator<?> generator) {
        validateReset(generator);
        validateCopyOfNewGenerator(generator);
        validateCopyOfActiveGenerator(generator);
        validateCleanCopyOfActiveGenerator(generator);
        validateParentDistribution(generator);
        validateOtherParentDistribution(generator);
        validateEventOrder(generator);
        validateMaxGeneration(generator);
        validateBirthRoundAdvancing(generator);
    }

    /**
     * Ensure that the birth round monotonically advances
     */
    private void validateBirthRoundAdvancing(@NonNull final GraphGenerator<?> generator) {
        final List<EventImpl> events = generator.generateEvents(100);
        final long firstEventBirthRound = events.get(0).getBirthRound();
        final long lastEventBirthRound = events.get(events.size() - 1).getBirthRound();
        long currentBirthRound = firstEventBirthRound;
        assertTrue(lastEventBirthRound > firstEventBirthRound);
        for (final EventImpl event : events) {
            final long eventBirthRound = event.getBirthRound();
            assertTrue(eventBirthRound >= currentBirthRound);
            currentBirthRound = eventBirthRound;
        }
        generator.reset();
    }

    /**
     * Assert that the max generation is updated correctly
     */
    public void validateMaxGeneration(final GraphGenerator<?> generator) {
        final List<EventImpl> events = generator.generateEvents(100);
        final EventImpl lastEvent = events.get(events.size() - 1);
        // validate only the last event to keep the validation simple
        assertEquals(
                lastEvent.getGeneration(),
                generator.getMaxGeneration(lastEvent.getCreatorId()),
                "last event should have the max generation");
        generator.reset();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @Tag(TestComponentTags.PLATFORM)
    @Tag(TestComponentTags.CONSENSUS)
    @DisplayName("Test Standard Generator")
    public void testStandardGenerator(final boolean birthRoundAsAncientThreshold) {
        final PlatformContext platformContext =
                birthRoundAsAncientThreshold ? BIRTH_ROUND_PLATFORM_CONTEXT : DEFAULT_PLATFORM_CONTEXT;
        final StandardGraphGenerator generator = new StandardGraphGenerator(
                platformContext,
                0,
                new StandardEventSource(),
                new StandardEventSource(),
                new StandardEventSource(),
                new StandardEventSource());

        generatorSanityChecks(generator);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @Tag(TestComponentTags.PLATFORM)
    @Tag(TestComponentTags.CONSENSUS)
    @DisplayName("Forking Source Test")
    public void forkingSourceTest(final boolean birthRoundAsAncientThreshold) {
        final int numberOfEvents = 1000;

        final PlatformContext platformContext =
                birthRoundAsAncientThreshold ? BIRTH_ROUND_PLATFORM_CONTEXT : DEFAULT_PLATFORM_CONTEXT;
        final StandardGraphGenerator generator = new StandardGraphGenerator(
                platformContext,
                0,
                new StandardEventSource(),
                new StandardEventSource(),
                new StandardEventSource(),
                new ForkingEventSource().setForkProbability(0.1).setMaximumBranchCount(2));

        final List<EventImpl> events = generator.generateEvents(numberOfEvents);

        assertFalse(areGenerationNumbersValid(events, 4));
    }

    @Test
    @Tag(TestComponentTags.PLATFORM)
    @Tag(TestComponentTags.CONSENSUS)
    @DisplayName("Dynamic Value Tests")
    public void dynamicValueTests() {

        final DynamicValue<Double> dynamicValue = (Random random, long eventIndex, Double previousValue) -> {
            if (eventIndex == 0) {
                assertNull(previousValue);
            } else {
                assertEquals(eventIndex - 1, previousValue);
            }
            return (double) eventIndex;
        };

        final DynamicValueGenerator<Double> dynamicValueGenerator = new DynamicValueGenerator<>(dynamicValue);

        // Generate a bunch of values in sequence
        for (int i = 0; i < 1000; i++) {
            assertEquals(dynamicValueGenerator.get(null, i), i);
        }

        // Going "backwards" should return expected values
        assertEquals(1000, dynamicValueGenerator.get(null, 1000));
        assertEquals(1000, dynamicValueGenerator.get(null, 1000));
        assertEquals(1000, dynamicValueGenerator.get(null, 1000));
        assertEquals(500, dynamicValueGenerator.get(null, 500));
        assertEquals(50, dynamicValueGenerator.get(null, 50));
        assertEquals(5, dynamicValueGenerator.get(null, 5));
        assertEquals(0, dynamicValueGenerator.get(null, 0));

        // Generate sequence of values again
        for (int i = 0; i < 1000; i++) {
            assertEquals(dynamicValueGenerator.get(null, i), i);
        }

        // Skipping index values should return expected values
        for (int i = 0; i < 1000; i += 10) {
            assertEquals(dynamicValueGenerator.get(null, i), i);
        }
    }

    /**
     * Utility method. Check that the age ratio is within expected bounds.
     *
     * @param eventAges
     * 		a map containing event ages
     * @param age
     * 		the age to check
     * @param expectedRatio
     * 		the expected ratio of the specified age
     * @param tolerance
     * 		the amount that the expected ratio may differ from the actual ratio. Given as a fraction of 1.0.
     * 		A tolerance of 0.05 allows the actual ratio to be between the bounds (0.95 * expected) and
     * 		(1.05 * expected).
     */
    private void assertAgeRatio(
            final Map<Integer, Integer> eventAges, final int age, final double expectedRatio, final double tolerance) {

        int totalEventCount = 0;
        for (final Map.Entry<Integer, Integer> entries : eventAges.entrySet()) {
            totalEventCount += entries.getValue();
        }

        final double ageCount = eventAges.getOrDefault(age, 0);
        double ratio = ageCount / totalEventCount;

        if (ratio < 0.0001) {
            ratio = 0;
        }

        if (totalEventCount * expectedRatio < 1 && ageCount == 0) {
            return;
        }

        final double lowerBound = expectedRatio * (1 - tolerance);
        final double upperBound = expectedRatio * (1 + tolerance);
        assertTrue(ratio >= lowerBound);
        assertTrue(ratio <= upperBound);
    }

    /**
     * Sometimes an other parent is chosen not to be the most recent event from a node. These tests
     * verify that behavior.
     */
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @Tag(TestComponentTags.PLATFORM)
    @Tag(TestComponentTags.CONSENSUS)
    @DisplayName("Other Parent Age Tests")
    @Disabled("This test needs to be investigated")
    public void otherParentAgeTests(final boolean birthRoundAsAncientThreshold) {

        final int numberOfEvents = 100_000;

        // A default generator uses a power distribution with alpha = 0.95
        final PlatformContext platformContext =
                birthRoundAsAncientThreshold ? BIRTH_ROUND_PLATFORM_CONTEXT : DEFAULT_PLATFORM_CONTEXT;
        StandardGraphGenerator generator = new StandardGraphGenerator(
                platformContext,
                0,
                new StandardEventSource(),
                new StandardEventSource(),
                new StandardEventSource(),
                new StandardEventSource());
        List<EventImpl> events = generator.generateEvents(numberOfEvents);
        Map<Integer, Integer> eventAges = gatherOtherParentAges(events, null);
        assertAgeRatio(eventAges, 0, 0.95, 0.05);
        assertAgeRatio(eventAges, 1, 0.05 * 0.95, 0.05);
        assertAgeRatio(eventAges, 2, 0.05 * 0.05 * 0.95, 0.2);

        // Completely disable old other parents
        generator = new StandardGraphGenerator(
                platformContext,
                0,
                new StandardEventSource().setRequestedOtherParentAgeDistribution(staticDynamicValue(0)),
                new StandardEventSource().setRequestedOtherParentAgeDistribution(staticDynamicValue(0)),
                new StandardEventSource().setRequestedOtherParentAgeDistribution(staticDynamicValue(0)),
                new StandardEventSource().setRequestedOtherParentAgeDistribution(staticDynamicValue(0)));
        events = generator.generateEvents(numberOfEvents);
        eventAges = gatherOtherParentAges(events, null);

        assertAgeRatio(eventAges, 0, 1.0, 0.0);
        assertAgeRatio(eventAges, 1, 0.0, 0.0);
        assertAgeRatio(eventAges, 2, 0.0, 0.0);

        // One node is much more likely to create events with old other parents
        generator = new StandardGraphGenerator(
                platformContext,
                0,
                new StandardEventSource(),
                new StandardEventSource(),
                new StandardEventSource(),
                new StandardEventSource().setRequestedOtherParentAgeDistribution(integerPowerDistribution(0.5)));
        events = generator.generateEvents(numberOfEvents);

        final HashSet<NodeId> excludedNodes = new HashSet<>();
        excludedNodes.add(NodeId.of(3L));
        eventAges = gatherOtherParentAges(events, excludedNodes);
        assertAgeRatio(eventAges, 0, 0.95, 0.05);
        assertAgeRatio(eventAges, 1, 0.05 * 0.95, 0.05);
        assertAgeRatio(eventAges, 2, 0.05 * 0.05 * 0.95, 0.2);

        excludedNodes.clear();
        excludedNodes.add(NodeId.of(0L));
        excludedNodes.add(NodeId.of(1L));
        excludedNodes.add(NodeId.of(2L));
        eventAges = gatherOtherParentAges(events, excludedNodes);
        assertAgeRatio(eventAges, 0, 0.5, 0.05);
        assertAgeRatio(eventAges, 1, 0.5 * 0.5, 0.05);
        assertAgeRatio(eventAges, 2, 0.5 * 0.5 * 0.5, 0.05);
        assertAgeRatio(eventAges, 3, 0.5 * 0.5 * 0.5 * 0.5, 0.05);
        assertAgeRatio(eventAges, 4, 0.5 * 0.5 * 0.5 * 0.5 * 0.5, 0.06);
        assertAgeRatio(eventAges, 5, 0.5 * 0.5 * 0.5 * 0.5 * 0.5 * 0.5, 0.2);

        // One node likes to consistently provide old other parents, all others always provide most recent parent
        generator = new StandardGraphGenerator(
                platformContext,
                0,
                new StandardEventSource().setRequestedOtherParentAgeDistribution(staticDynamicValue(0)),
                new StandardEventSource().setRequestedOtherParentAgeDistribution(staticDynamicValue(0)),
                new StandardEventSource().setRequestedOtherParentAgeDistribution(staticDynamicValue(0)),
                new StandardEventSource()
                        .setRequestedOtherParentAgeDistribution(staticDynamicValue(0))
                        .setProvidedOtherParentAgeDistribution(staticDynamicValue(3)));
        events = generator.generateEvents(numberOfEvents);

        excludedNodes.clear();
        excludedNodes.add(NodeId.of(3L));
        eventAges = gatherOtherParentAges(events, excludedNodes);
        assertAgeRatio(eventAges, 0, 0.666666, 0.05);
        assertAgeRatio(eventAges, 1, 0.0, 0.0);
        assertAgeRatio(eventAges, 2, 0.0, 0.0);
        assertAgeRatio(eventAges, 3, 0.333333, 0.05);

        excludedNodes.clear();
        excludedNodes.add(NodeId.of(0L));
        excludedNodes.add(NodeId.of(1L));
        excludedNodes.add(NodeId.of(2L));
        eventAges = gatherOtherParentAges(events, excludedNodes);
        assertAgeRatio(eventAges, 0, 1.0, 0.0);
        assertAgeRatio(eventAges, 1, 0.0, 0.0);
        assertAgeRatio(eventAges, 2, 0.0, 0.0);
    }

    /**
     * Sanity checks on creation timestamps for events, make sure fraction of events with repeating timestamps
     * matches expected value.
     */
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @Tag(TestComponentTags.PLATFORM)
    @Tag(TestComponentTags.CONSENSUS)
    @DisplayName("Repeated Timestamp Tests")
    void repeatedTimestampTests(final boolean birthRoundAsAncientThreshold) {

        final int numberOfEvents = 100_000;

        // A default generator uses a power distribution with alpha = 0.95
        final PlatformContext platformContext =
                birthRoundAsAncientThreshold ? BIRTH_ROUND_PLATFORM_CONTEXT : DEFAULT_PLATFORM_CONTEXT;
        final StandardGraphGenerator generator = new StandardGraphGenerator(
                platformContext,
                0,
                new StandardEventSource(),
                new StandardEventSource(),
                new StandardEventSource(),
                new StandardEventSource());
        generator.setSimultaneousEventFraction(0.1);
        final List<EventImpl> events = generator.generateEvents(numberOfEvents);

        int repeatCount = 0;
        Instant previousTimestamp = null;

        for (final EventImpl event : events) {
            final Instant timestamp = event.getTimeCreated();
            if (previousTimestamp != null && previousTimestamp.equals(timestamp)) {
                repeatCount++;
            }
            previousTimestamp = timestamp;
        }

        final double repeatRatio = ((double) repeatCount) / numberOfEvents;

        // Multiply expected ratio by 1/2 since we never repeat timestamp if either of the parents of the event was
        // the most recently emitted event
        final double expectedRepeatRatio = generator.getSimultaneousEventFraction() * 1 / 2;
        final double deviation = Math.abs(repeatRatio - expectedRepeatRatio);

        assertTrue(deviation < 0.01, "OOB");
    }
}
