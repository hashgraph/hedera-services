/*
 * Copyright (C) 2016-2024 Hedera Hashgraph, LLC
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

package com.swirlds.platform.test.event.tipset;

import static com.swirlds.common.test.fixtures.RandomUtils.getRandomPrintSeed;
import static com.swirlds.common.test.fixtures.RandomUtils.randomHash;
import static com.swirlds.common.utility.Threshold.SUPER_MAJORITY;
import static com.swirlds.platform.event.creation.tipset.Tipset.merge;
import static com.swirlds.platform.event.creation.tipset.TipsetAdvancementWeight.ZERO_ADVANCEMENT_WEIGHT;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.hedera.hapi.platform.event.EventDescriptor;
import com.swirlds.base.time.Time;
import com.swirlds.common.Address;
import com.swirlds.common.AddressBook;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.platform.NodeId;
import com.swirlds.common.test.fixtures.platform.TestPlatformContextBuilder;
import com.swirlds.platform.consensus.EventWindow;
import com.swirlds.platform.event.AncientMode;
import com.swirlds.platform.event.creation.tipset.ChildlessEventTracker;
import com.swirlds.platform.event.creation.tipset.Tipset;
import com.swirlds.platform.event.creation.tipset.TipsetAdvancementWeight;
import com.swirlds.platform.event.creation.tipset.TipsetTracker;
import com.swirlds.platform.event.creation.tipset.TipsetWeightCalculator;
import com.swirlds.platform.system.events.EventConstants;
import com.swirlds.platform.system.events.EventDescriptorWrapper;
import com.swirlds.platform.test.fixtures.addressbook.RandomAddressBookBuilder;
import com.swirlds.platform.test.fixtures.addressbook.RandomAddressBookBuilder.WeightDistributionStrategy;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("TipsetWeightCalculator Tests")
class TipsetWeightCalculatorTests {

    /**
     * Create a new event descriptor with the given parameters and {@link EventConstants#BIRTH_ROUND_UNDEFINED} for the
     * birth round.
     *
     * @param hash       the hash of the event
     * @param creator    the creator of the event
     * @param generation the generation of the event
     * @return the event descriptor
     */
    private EventDescriptorWrapper newEventDescriptor(
            @NonNull final Hash hash, @NonNull final NodeId creator, final long generation) {
        return new EventDescriptorWrapper(
                new EventDescriptor(hash.getBytes(), creator.id(), EventConstants.BIRTH_ROUND_UNDEFINED, generation));
    }

    @Test
    @DisplayName("Basic Behavior Test")
    void basicBehaviorTest() {
        final Random random = getRandomPrintSeed();
        final int nodeCount = 5;

        final Map<NodeId, EventDescriptorWrapper> latestEvents = new HashMap<>();

        final AddressBook addressBook =
                RandomAddressBookBuilder.create(random).withSize(nodeCount).build();

        final Map<NodeId, Long> weightMap = new HashMap<>();
        long totalWeight = 0;
        for (final Address address : addressBook) {
            weightMap.put(address.getNodeId(), address.getWeight());
            totalWeight += address.getWeight();
        }

        final NodeId selfId = addressBook.getNodeId(random.nextInt(nodeCount));

        final PlatformContext platformContext =
                TestPlatformContextBuilder.create().build();

        // FUTURE WORK: Expand test to include birth round based ancient threshold.
        final TipsetTracker builder =
                new TipsetTracker(Time.getCurrent(), addressBook, AncientMode.GENERATION_THRESHOLD);
        final ChildlessEventTracker childlessEventTracker = new ChildlessEventTracker();
        final TipsetWeightCalculator calculator =
                new TipsetWeightCalculator(platformContext, addressBook, selfId, builder, childlessEventTracker);

        List<EventDescriptorWrapper> previousParents = List.of();
        TipsetAdvancementWeight runningAdvancementScore = ZERO_ADVANCEMENT_WEIGHT;
        Tipset previousSnapshot = calculator.getSnapshot();

        for (int eventIndex = 0; eventIndex < 1000; eventIndex++) {
            final NodeId creator = addressBook.getNodeId(random.nextInt(nodeCount));
            final long generation;
            if (latestEvents.containsKey(creator)) {
                generation = latestEvents.get(creator).eventDescriptor().generation() + 1;
            } else {
                generation = 1;
            }

            final EventDescriptorWrapper selfParent = latestEvents.get(creator);
            final EventDescriptorWrapper fingerprint = newEventDescriptor(randomHash(random), creator, generation);
            latestEvents.put(creator, fingerprint);

            // Select some nodes we'd like to be our parents.
            final Set<NodeId> desiredParents = new HashSet<>();
            final int maxParentCount = random.nextInt(nodeCount);
            for (int parentIndex = 0; parentIndex < maxParentCount; parentIndex++) {
                final NodeId parent = addressBook.getNodeId(random.nextInt(nodeCount));

                // We are only trying to generate a random number of parents, the exact count is unimportant.
                // So it doesn't matter if the actual number of parents is less than the number we requested.
                if (parent.equals(creator)) {
                    continue;
                }
                desiredParents.add(parent);
            }

            // Select the actual parents.
            final List<EventDescriptorWrapper> parentFingerprints = new ArrayList<>(desiredParents.size());
            if (selfParent != null) {
                parentFingerprints.add(selfParent);
            }
            for (final NodeId parent : desiredParents) {
                final EventDescriptorWrapper parentFingerprint = latestEvents.get(parent);
                if (parentFingerprint != null) {
                    parentFingerprints.add(parentFingerprint);
                }
            }

            builder.addEvent(fingerprint, parentFingerprints);

            if (creator != selfId) {
                // The following validation only needs to happen for events created by self

                // Only do previous parent validation if we create two or more events in a row.
                previousParents = List.of();

                continue;
            }

            // Manually calculate the advancement score.
            final List<Tipset> parentTipsets = new ArrayList<>(parentFingerprints.size());
            for (final EventDescriptorWrapper parentFingerprint : parentFingerprints) {
                parentTipsets.add(builder.getTipset(parentFingerprint));
            }

            final Tipset newTipset;
            if (parentTipsets.isEmpty()) {
                newTipset = new Tipset(addressBook).advance(creator, generation);
            } else {
                newTipset = merge(parentTipsets).advance(creator, generation);
            }

            final TipsetAdvancementWeight expectedAdvancementScoreChange =
                    previousSnapshot.getTipAdvancementWeight(selfId, newTipset).minus(runningAdvancementScore);

            // For events created by "this" node, check that the calculator is updated correctly.
            final TipsetAdvancementWeight advancementScoreChange =
                    calculator.addEventAndGetAdvancementWeight(fingerprint);

            assertEquals(expectedAdvancementScoreChange, advancementScoreChange);

            // Special case: if we create more than one event in a row and our current parents are a
            // subset of the previous parents, then we should expect an advancement score of zero.
            boolean subsetOfPreviousParents = true;
            for (final EventDescriptorWrapper parentFingerprint : parentFingerprints) {
                if (!previousParents.contains(parentFingerprint)) {
                    subsetOfPreviousParents = false;
                    break;
                }
            }
            if (subsetOfPreviousParents) {
                assertEquals(ZERO_ADVANCEMENT_WEIGHT, advancementScoreChange);
            }
            previousParents = parentFingerprints;

            // Validate that the snapshot advances correctly.
            runningAdvancementScore = runningAdvancementScore.plus(advancementScoreChange);
            if (SUPER_MAJORITY.isSatisfiedBy(
                    runningAdvancementScore.advancementWeight() + weightMap.get(selfId), totalWeight)) {
                // The snapshot should have been updated.
                assertNotSame(previousSnapshot, calculator.getSnapshot());
                previousSnapshot = calculator.getSnapshot();
                runningAdvancementScore = ZERO_ADVANCEMENT_WEIGHT;
            } else {
                // The snapshot should have not been updated.
                assertSame(previousSnapshot, calculator.getSnapshot());
            }
        }
    }

    @Test
    @DisplayName("Selfish Node Test")
    void selfishNodeTest() {
        final Random random = getRandomPrintSeed();
        final int nodeCount = 4;

        final AddressBook addressBook = RandomAddressBookBuilder.create(random)
                .withSize(nodeCount)
                .withAverageWeight(1)
                .withWeightDistributionStrategy(WeightDistributionStrategy.BALANCED)
                .build();

        // In this test, we simulate from the perspective of node A. All nodes have 1 weight.
        final NodeId nodeA = addressBook.getNodeId(0);
        final NodeId nodeB = addressBook.getNodeId(1);
        final NodeId nodeC = addressBook.getNodeId(2);
        final NodeId nodeD = addressBook.getNodeId(3);

        final PlatformContext platformContext =
                TestPlatformContextBuilder.create().build();

        // FUTURE WORK: Expand test to include birth round based ancient threshold.
        final TipsetTracker tracker =
                new TipsetTracker(Time.getCurrent(), addressBook, AncientMode.GENERATION_THRESHOLD);
        final ChildlessEventTracker childlessEventTracker = new ChildlessEventTracker();
        final TipsetWeightCalculator calculator =
                new TipsetWeightCalculator(platformContext, addressBook, nodeA, tracker, childlessEventTracker);

        final Tipset snapshot1 = calculator.getSnapshot();

        // Each node creates an event.
        final EventDescriptorWrapper eventA1 = newEventDescriptor(randomHash(random), nodeA, 1);
        tracker.addEvent(eventA1, List.of());
        childlessEventTracker.addEvent(eventA1, List.of());
        final EventDescriptorWrapper eventB1 = newEventDescriptor(randomHash(random), nodeB, 1);
        tracker.addEvent(eventB1, List.of());
        childlessEventTracker.addEvent(eventB1, List.of());
        final EventDescriptorWrapper eventC1 = newEventDescriptor(randomHash(random), nodeC, 1);
        tracker.addEvent(eventC1, List.of());
        childlessEventTracker.addEvent(eventC1, List.of());
        final EventDescriptorWrapper eventD1 = newEventDescriptor(randomHash(random), nodeD, 1);
        tracker.addEvent(eventD1, List.of());
        childlessEventTracker.addEvent(eventD1, List.of());

        assertEquals(ZERO_ADVANCEMENT_WEIGHT, calculator.getTheoreticalAdvancementWeight(List.of()));
        assertEquals(ZERO_ADVANCEMENT_WEIGHT, calculator.addEventAndGetAdvancementWeight(eventA1));
        assertSame(snapshot1, calculator.getSnapshot());

        // Each node creates another event. All nodes use all available other parents except the event from D.
        final EventDescriptorWrapper eventA2 = newEventDescriptor(randomHash(random), nodeA, 2);
        tracker.addEvent(eventA2, List.of(eventA1, eventB1, eventC1));
        childlessEventTracker.addEvent(eventA2, List.of(eventA1, eventB1, eventC1));
        final EventDescriptorWrapper eventB2 = newEventDescriptor(randomHash(random), nodeB, 2);
        tracker.addEvent(eventB2, List.of(eventA1, eventB1, eventC1));
        childlessEventTracker.addEvent(eventB2, List.of(eventA1, eventB1, eventC1));
        final EventDescriptorWrapper eventC2 = newEventDescriptor(randomHash(random), nodeC, 2);
        tracker.addEvent(eventC2, List.of(eventA1, eventB1, eventC1));
        childlessEventTracker.addEvent(eventC2, List.of(eventA1, eventB1, eventC1));
        final EventDescriptorWrapper eventD2 = newEventDescriptor(randomHash(random), nodeD, 2);
        tracker.addEvent(eventD2, List.of(eventA1, eventB1, eventC1, eventD1));
        childlessEventTracker.addEvent(eventD2, List.of(eventA1, eventB1, eventC1, eventD1));

        assertEquals(
                TipsetAdvancementWeight.of(2, 0),
                calculator.getTheoreticalAdvancementWeight(List.of(eventA1, eventB1, eventC1)));
        assertEquals(TipsetAdvancementWeight.of(2, 0), calculator.addEventAndGetAdvancementWeight(eventA2));

        // This should have been enough to advance the snapshot window by 1.
        final Tipset snapshot2 = calculator.getSnapshot();
        assertNotSame(snapshot1, snapshot2);

        // D should have a selfishness score of 1, all others a score of 0.
        assertEquals(0, calculator.getSelfishnessScoreForNode(nodeA));
        assertEquals(0, calculator.getSelfishnessScoreForNode(nodeB));
        assertEquals(0, calculator.getSelfishnessScoreForNode(nodeC));
        assertEquals(1, calculator.getSelfishnessScoreForNode(nodeD));
        assertEquals(1, calculator.getMaxSelfishnessScore());

        // Create another batch of events where D is bullied.
        final EventDescriptorWrapper eventA3 = newEventDescriptor(randomHash(random), nodeA, 3);
        tracker.addEvent(eventA3, List.of(eventA2, eventB2, eventC2));
        childlessEventTracker.addEvent(eventA3, List.of(eventA2, eventB2, eventC2));
        final EventDescriptorWrapper eventB3 = newEventDescriptor(randomHash(random), nodeB, 3);
        tracker.addEvent(eventB3, List.of(eventA2, eventB2, eventC2));
        childlessEventTracker.addEvent(eventB3, List.of(eventA2, eventB2, eventC2));
        final EventDescriptorWrapper eventC3 = newEventDescriptor(randomHash(random), nodeC, 3);
        tracker.addEvent(eventC3, List.of(eventA2, eventB2, eventC2));
        childlessEventTracker.addEvent(eventC3, List.of(eventA2, eventB2, eventC2));
        final EventDescriptorWrapper eventD3 = newEventDescriptor(randomHash(random), nodeD, 3);
        tracker.addEvent(eventD3, List.of(eventA2, eventB2, eventC2, eventD2));
        childlessEventTracker.addEvent(eventD3, List.of(eventA2, eventB2, eventC2, eventD2));

        assertEquals(
                TipsetAdvancementWeight.of(2, 0),
                calculator.getTheoreticalAdvancementWeight(List.of(eventA2, eventB2, eventC2)));
        assertEquals(TipsetAdvancementWeight.of(2, 0), calculator.addEventAndGetAdvancementWeight(eventA3));

        final Tipset snapshot3 = calculator.getSnapshot();
        assertNotSame(snapshot2, snapshot3);

        // D should have a selfishness score of 2, all others a score of 0.
        assertEquals(0, calculator.getSelfishnessScoreForNode(nodeA));
        assertEquals(0, calculator.getSelfishnessScoreForNode(nodeB));
        assertEquals(0, calculator.getSelfishnessScoreForNode(nodeC));
        assertEquals(2, calculator.getSelfishnessScoreForNode(nodeD));
        assertEquals(2, calculator.getMaxSelfishnessScore());

        // Create a bach of events that don't ignore D. Let's all ignore C, because C is a jerk.
        final EventDescriptorWrapper eventA4 = newEventDescriptor(randomHash(random), nodeA, 4);
        tracker.addEvent(eventA4, List.of(eventA3, eventB3, eventD3));
        childlessEventTracker.addEvent(eventA4, List.of(eventA3, eventB3, eventD3));
        final EventDescriptorWrapper eventB4 = newEventDescriptor(randomHash(random), nodeB, 4);
        tracker.addEvent(eventB4, List.of(eventA3, eventB3, eventD3));
        childlessEventTracker.addEvent(eventB4, List.of(eventA3, eventB3, eventD3));
        final EventDescriptorWrapper eventC4 = newEventDescriptor(randomHash(random), nodeC, 4);
        tracker.addEvent(eventC4, List.of(eventA3, eventB3, eventC3, eventD3));
        childlessEventTracker.addEvent(eventC4, List.of(eventA3, eventB3, eventC3, eventD3));
        final EventDescriptorWrapper eventD4 = newEventDescriptor(randomHash(random), nodeD, 4);
        tracker.addEvent(eventD4, List.of(eventA3, eventB3, eventD3));
        childlessEventTracker.addEvent(eventD4, List.of(eventA3, eventB3, eventD3));

        assertEquals(
                TipsetAdvancementWeight.of(2, 0),
                calculator.getTheoreticalAdvancementWeight(List.of(eventA3, eventB3, eventD3)));
        assertEquals(TipsetAdvancementWeight.of(2, 0), calculator.addEventAndGetAdvancementWeight(eventA4));

        final Tipset snapshot4 = calculator.getSnapshot();
        assertNotSame(snapshot3, snapshot4);

        // Now, all nodes should have a selfishness score of 0 except for C, which should have a score of 1.
        assertEquals(0, calculator.getSelfishnessScoreForNode(nodeA));
        assertEquals(0, calculator.getSelfishnessScoreForNode(nodeB));
        assertEquals(1, calculator.getSelfishnessScoreForNode(nodeC));
        assertEquals(0, calculator.getSelfishnessScoreForNode(nodeD));
        assertEquals(1, calculator.getMaxSelfishnessScore());

        // Stop ignoring C. D stops creating events.
        final EventDescriptorWrapper eventA5 = newEventDescriptor(randomHash(random), nodeA, 5);
        tracker.addEvent(eventA5, List.of(eventA4, eventB4, eventC4, eventD4));
        childlessEventTracker.addEvent(eventA5, List.of(eventA4, eventB4, eventC4, eventD4));
        final EventDescriptorWrapper eventB5 = newEventDescriptor(randomHash(random), nodeB, 5);
        tracker.addEvent(eventB5, List.of(eventA4, eventB4, eventC4, eventD4));
        childlessEventTracker.addEvent(eventB5, List.of(eventA4, eventB4, eventC4, eventD4));
        final EventDescriptorWrapper eventC5 = newEventDescriptor(randomHash(random), nodeC, 5);
        tracker.addEvent(eventC5, List.of(eventA4, eventB4, eventC4, eventD4));
        childlessEventTracker.addEvent(eventC5, List.of(eventA4, eventB4, eventC4, eventD4));

        assertEquals(
                TipsetAdvancementWeight.of(3, 0),
                calculator.getTheoreticalAdvancementWeight(List.of(eventA4, eventB4, eventC4, eventD4)));
        assertEquals(TipsetAdvancementWeight.of(3, 0), calculator.addEventAndGetAdvancementWeight(eventA5));

        final Tipset snapshot5 = calculator.getSnapshot();
        assertNotSame(snapshot4, snapshot5);

        assertEquals(0, calculator.getSelfishnessScoreForNode(nodeA));
        assertEquals(0, calculator.getSelfishnessScoreForNode(nodeB));
        assertEquals(0, calculator.getSelfishnessScoreForNode(nodeC));
        assertEquals(0, calculator.getSelfishnessScoreForNode(nodeD));
        assertEquals(0, calculator.getMaxSelfishnessScore());

        // D still is not creating events. Since there is no legal event from D to use as a parent, this doesn't
        // count as being selfish.
        final EventDescriptorWrapper eventA6 = newEventDescriptor(randomHash(random), nodeA, 6);
        tracker.addEvent(eventA6, List.of(eventA5, eventB5, eventC5));
        childlessEventTracker.addEvent(eventA6, List.of(eventA5, eventB5, eventC5));
        final EventDescriptorWrapper eventB6 = newEventDescriptor(randomHash(random), nodeB, 6);
        tracker.addEvent(eventB6, List.of(eventA5, eventB5, eventC5));
        childlessEventTracker.addEvent(eventB6, List.of(eventA5, eventB5, eventC5));
        final EventDescriptorWrapper eventC6 = newEventDescriptor(randomHash(random), nodeC, 6);
        tracker.addEvent(eventC6, List.of(eventA5, eventB5, eventC5));
        childlessEventTracker.addEvent(eventC6, List.of(eventA5, eventB5, eventC5));

        assertEquals(
                TipsetAdvancementWeight.of(2, 0),
                calculator.getTheoreticalAdvancementWeight(List.of(eventA5, eventB5, eventC5)));
        assertEquals(TipsetAdvancementWeight.of(2, 0), calculator.addEventAndGetAdvancementWeight(eventA6));

        final Tipset snapshot6 = calculator.getSnapshot();
        assertNotSame(snapshot5, snapshot6);

        assertEquals(0, calculator.getSelfishnessScoreForNode(nodeA));
        assertEquals(0, calculator.getSelfishnessScoreForNode(nodeB));
        assertEquals(0, calculator.getSelfishnessScoreForNode(nodeC));
        assertEquals(0, calculator.getSelfishnessScoreForNode(nodeD));
        assertEquals(0, calculator.getMaxSelfishnessScore());

        // Rinse and repeat.
        final EventDescriptorWrapper eventA7 = newEventDescriptor(randomHash(random), nodeA, 7);
        tracker.addEvent(eventA7, List.of(eventA6, eventB6, eventC6));
        childlessEventTracker.addEvent(eventA7, List.of(eventA6, eventB6, eventC6));
        final EventDescriptorWrapper eventB7 = newEventDescriptor(randomHash(random), nodeB, 7);
        tracker.addEvent(eventB7, List.of(eventA6, eventB6, eventC6));
        childlessEventTracker.addEvent(eventB7, List.of(eventA6, eventB6, eventC6));
        final EventDescriptorWrapper eventC7 = newEventDescriptor(randomHash(random), nodeC, 7);
        tracker.addEvent(eventC7, List.of(eventA6, eventB6, eventC6));
        childlessEventTracker.addEvent(eventC7, List.of(eventA6, eventB6, eventC6));

        assertEquals(
                TipsetAdvancementWeight.of(2, 0),
                calculator.getTheoreticalAdvancementWeight(List.of(eventA6, eventB6, eventC6)));
        assertEquals(TipsetAdvancementWeight.of(2, 0), calculator.addEventAndGetAdvancementWeight(eventA7));

        final Tipset snapshot7 = calculator.getSnapshot();
        assertNotSame(snapshot6, snapshot7);

        assertEquals(0, calculator.getSelfishnessScoreForNode(nodeA));
        assertEquals(0, calculator.getSelfishnessScoreForNode(nodeB));
        assertEquals(0, calculator.getSelfishnessScoreForNode(nodeC));
        assertEquals(0, calculator.getSelfishnessScoreForNode(nodeD));
        assertEquals(0, calculator.getMaxSelfishnessScore());
    }

    @Test
    @DisplayName("Zero Stake Node Test")
    void zeroWeightNodeTest() {
        final Random random = getRandomPrintSeed();
        final int nodeCount = 4;

        final AddressBook addressBook = RandomAddressBookBuilder.create(random)
                .withSize(nodeCount)
                .withAverageWeight(1)
                .withWeightDistributionStrategy(WeightDistributionStrategy.BALANCED)
                .build();

        // In this test, we simulate from the perspective of node A.
        // All nodes have 1 weight except for D, which has 0 weight.
        final NodeId nodeA = addressBook.getNodeId(0);
        final NodeId nodeB = addressBook.getNodeId(1);
        final NodeId nodeC = addressBook.getNodeId(2);
        final NodeId nodeD = addressBook.getNodeId(3);

        addressBook.add(addressBook.getAddress(nodeD).copySetWeight(0));

        final PlatformContext platformContext =
                TestPlatformContextBuilder.create().build();

        // FUTURE WORK: Expand test to include birth round based ancient threshold.
        final TipsetTracker builder =
                new TipsetTracker(Time.getCurrent(), addressBook, AncientMode.GENERATION_THRESHOLD);
        final ChildlessEventTracker childlessEventTracker = new ChildlessEventTracker();
        final TipsetWeightCalculator calculator =
                new TipsetWeightCalculator(platformContext, addressBook, nodeA, builder, childlessEventTracker);

        final Tipset snapshot1 = calculator.getSnapshot();

        // Each node creates an event.
        final EventDescriptorWrapper eventA1 = newEventDescriptor(randomHash(random), nodeA, 1);
        builder.addEvent(eventA1, List.of());
        final EventDescriptorWrapper eventB1 = newEventDescriptor(randomHash(random), nodeB, 1);
        builder.addEvent(eventB1, List.of());
        final EventDescriptorWrapper eventC1 = newEventDescriptor(randomHash(random), nodeC, 1);
        builder.addEvent(eventC1, List.of());
        final EventDescriptorWrapper eventD1 = newEventDescriptor(randomHash(random), nodeD, 1);
        builder.addEvent(eventD1, List.of());

        assertEquals(ZERO_ADVANCEMENT_WEIGHT, calculator.getTheoreticalAdvancementWeight(List.of()));
        assertEquals(ZERO_ADVANCEMENT_WEIGHT, calculator.addEventAndGetAdvancementWeight(eventA1));
        assertSame(snapshot1, calculator.getSnapshot());

        // Create a node "on top of" B1.
        final EventDescriptorWrapper eventA2 = newEventDescriptor(randomHash(random), nodeA, 2);
        builder.addEvent(eventA2, List.of(eventA1, eventB1));
        final TipsetAdvancementWeight advancement1 = calculator.addEventAndGetAdvancementWeight(eventA2);
        assertEquals(TipsetAdvancementWeight.of(1, 0), advancement1);

        // Snapshot should not have advanced.
        assertSame(snapshot1, calculator.getSnapshot());

        // If we get 1 more advancement point then the snapshot will advance. But building
        // on top of a zero stake node will not contribute to this and the snapshot will not
        // advance. Build on top of node D.
        final EventDescriptorWrapper eventA3 = newEventDescriptor(randomHash(random), nodeA, 3);
        builder.addEvent(eventA3, List.of(eventA2, eventD1));
        final TipsetAdvancementWeight advancement2 = calculator.addEventAndGetAdvancementWeight(eventA3);
        assertEquals(TipsetAdvancementWeight.of(0, 1), advancement2);

        // Snapshot should not have advanced.
        assertSame(snapshot1, calculator.getSnapshot());

        // Now, build on top of C. This should push us into the next snapshot.
        final EventDescriptorWrapper eventA4 = newEventDescriptor(randomHash(random), nodeA, 4);
        builder.addEvent(eventA4, List.of(eventA3, eventC1));
        final TipsetAdvancementWeight advancement3 = calculator.addEventAndGetAdvancementWeight(eventA4);
        assertEquals(TipsetAdvancementWeight.of(1, 0), advancement3);

        final Tipset snapshot2 = calculator.getSnapshot();
        assertNotEquals(snapshot1, snapshot2);
        assertEquals(snapshot2, builder.getTipset(eventA4));
    }

    @Test
    @DisplayName("Ancient Parent Test")
    void ancientParentTest() {
        final Random random = getRandomPrintSeed();
        final int nodeCount = 4;

        final AddressBook addressBook = RandomAddressBookBuilder.create(random)
                .withSize(nodeCount)
                .withAverageWeight(1)
                .withWeightDistributionStrategy(WeightDistributionStrategy.BALANCED)
                .build();

        final NodeId nodeA = addressBook.getNodeId(0);
        final NodeId nodeB = addressBook.getNodeId(1);
        final NodeId nodeC = addressBook.getNodeId(2);
        final NodeId nodeD = addressBook.getNodeId(3);

        final PlatformContext platformContext =
                TestPlatformContextBuilder.create().build();

        // FUTURE WORK: Expand test to include birth round based ancient threshold.
        final TipsetTracker builder =
                new TipsetTracker(Time.getCurrent(), addressBook, AncientMode.GENERATION_THRESHOLD);
        final ChildlessEventTracker childlessEventTracker = new ChildlessEventTracker();
        final TipsetWeightCalculator calculator =
                new TipsetWeightCalculator(platformContext, addressBook, nodeA, builder, childlessEventTracker);

        // Create generation 1 events.
        final EventDescriptorWrapper eventA1 = newEventDescriptor(randomHash(random), nodeA, 1);
        builder.addEvent(eventA1, List.of());
        final EventDescriptorWrapper eventB1 = newEventDescriptor(randomHash(random), nodeB, 1);
        builder.addEvent(eventB1, List.of());
        final EventDescriptorWrapper eventC1 = newEventDescriptor(randomHash(random), nodeC, 1);
        builder.addEvent(eventC1, List.of());
        final EventDescriptorWrapper eventD1 = newEventDescriptor(randomHash(random), nodeD, 1);
        builder.addEvent(eventD1, List.of());

        // Create some generation 2 events. A does not create an event yet.
        final EventDescriptorWrapper eventB2 = newEventDescriptor(randomHash(random), nodeB, 2);
        builder.addEvent(eventB2, List.of(eventA1, eventB1, eventC1, eventD1));
        final EventDescriptorWrapper eventC2 = newEventDescriptor(randomHash(random), nodeC, 2);
        builder.addEvent(eventC2, List.of(eventA1, eventB1, eventC1, eventD1));
        final EventDescriptorWrapper eventD2 = newEventDescriptor(randomHash(random), nodeD, 2);
        builder.addEvent(eventD2, List.of(eventA1, eventB1, eventC1, eventD1));

        // FUTURE WORK: Change the test to use birthRound instead of generation for ancient.
        // Mark generation 1 as ancient.
        final EventWindow eventWindow =
                new EventWindow(1, 2, 0 /* ignored in this context */, AncientMode.GENERATION_THRESHOLD);
        builder.setEventWindow(eventWindow);
        childlessEventTracker.pruneOldEvents(eventWindow);

        // We shouldn't be able to find tipsets for ancient events.
        assertNull(builder.getTipset(eventA1));
        assertNull(builder.getTipset(eventB1));
        assertNull(builder.getTipset(eventC1));
        assertNull(builder.getTipset(eventD1));

        // Including generation 1 events as parents shouldn't cause us to throw. (Angry log messages are ok).
        assertDoesNotThrow(() -> {
            calculator.getTheoreticalAdvancementWeight(List.of(eventA1, eventB2, eventC2, eventD1));
            final EventDescriptorWrapper eventA2 = newEventDescriptor(randomHash(random), nodeA, 2);
            builder.addEvent(eventA2, List.of(eventA1, eventB2, eventC2, eventD1));
            calculator.addEventAndGetAdvancementWeight(eventA2);
        });
    }
}
