/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.swirlds.platform.event.branching;

import static com.swirlds.platform.event.AncientMode.BIRTH_ROUND_THRESHOLD;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.swirlds.common.AddressBook;
import com.swirlds.common.platform.NodeId;
import com.swirlds.common.test.fixtures.Randotron;
import com.swirlds.platform.consensus.EventWindow;
import com.swirlds.platform.event.PlatformEvent;
import com.swirlds.platform.test.fixtures.addressbook.RandomAddressBookBuilder;
import com.swirlds.platform.test.fixtures.event.TestingEventBuilder;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.Test;

class BranchDetectorTests {

    /**
     * Generate a simple sequence of events. Events do not have other parents since it's simpler to not generate other
     * parents. Branch detection ignores other parents, so there is no need to provide them in this test. For
     * simplicity, each event has a birth round one higher than its parent.
     *
     * @return a list of events
     */
    @NonNull
    static List<PlatformEvent> generateSimpleSequenceOfEvents(
            @NonNull final Randotron randotron,
            @NonNull final NodeId creatorId,
            final int initialBirthRound,
            final int count) {

        final List<PlatformEvent> events = new ArrayList<>(count);

        for (int i = 0; i < count; i++) {
            final PlatformEvent selfParent = i == 0 ? null : events.get(i - 1);

            final PlatformEvent event = new TestingEventBuilder(randotron)
                    .setCreatorId(creatorId)
                    .setBirthRound(initialBirthRound + i)
                    .setSelfParent(selfParent)
                    .build();
            events.add(event);
        }

        return events;
    }

    @Test
    void requiresEventWindow() {
        final Randotron randotron = Randotron.create();
        final AddressBook addressBook =
                RandomAddressBookBuilder.create(randotron).withSize(8).build();

        final PlatformEvent event = new TestingEventBuilder(randotron)
                .setCreatorId(addressBook.getNodeId(0))
                .setBirthRound(1)
                .build();

        final BranchDetector branchDetector = new DefaultBranchDetector(addressBook);

        // We expect this to throw if we haven't yet specified the event window.
        assertThrows(IllegalStateException.class, () -> branchDetector.checkForBranches(event));
    }

    @Test
    void noBranchesTest() {
        final Randotron randotron = Randotron.create();

        final int nodeCount = 8;

        final AddressBook addressBook =
                RandomAddressBookBuilder.create(randotron).withSize(nodeCount).build();

        final int initialBirthRound = randotron.nextInt(1, 1000);

        final List<PlatformEvent> events = new ArrayList<>();
        for (final NodeId nodeId : addressBook.getNodeIdSet()) {
            events.addAll(generateSimpleSequenceOfEvents(randotron, nodeId, initialBirthRound, 512));
        }

        // Create a random topological ordering
        Collections.shuffle(events, randotron);
        events.sort(Comparator.comparingLong(x -> x.getBirthRound()));

        long ancientThreshold = initialBirthRound;

        final BranchDetector branchDetector = new DefaultBranchDetector(addressBook);
        branchDetector.updateEventWindow(
                new EventWindow(1 /* ignored */, ancientThreshold, 1 /* ignored */, BIRTH_ROUND_THRESHOLD));

        for (final PlatformEvent event : events) {
            if (event.getBirthRound() > ancientThreshold + 5 && randotron.nextBoolean(0.1)) {
                // Randomly advance the ancient threshold, but don't let it advance past where we are adding events.

                ancientThreshold++;
                branchDetector.updateEventWindow(
                        new EventWindow(1 /* ignored */, ancientThreshold, 1 /* ignored */, BIRTH_ROUND_THRESHOLD));
            }
            assertFalse(event.getBirthRound() < ancientThreshold);

            final PlatformEvent branchingEvent = branchDetector.checkForBranches(event);
            assertNull(branchingEvent);

            if (randotron.nextBoolean(0.01)) {
                branchDetector.clear();
                branchDetector.updateEventWindow(
                        new EventWindow(1 /* ignored */, ancientThreshold, 1 /* ignored */, BIRTH_ROUND_THRESHOLD));
            }
        }
    }

    /**
     * A single node branches by creating two children of the same parent.
     */
    @Test
    void branchingTest() {
        final Randotron randotron = Randotron.create();

        final int nodeCount = 8;

        final AddressBook addressBook =
                RandomAddressBookBuilder.create(randotron).withSize(nodeCount).build();

        final int initialBirthRound = randotron.nextInt(1, 1000);

        final NodeId branchingNode = addressBook.getNodeId(randotron.nextInt(0, addressBook.getSize()));
        PlatformEvent branchingEvent = null;
        PlatformEvent siblingEvent = null;

        final List<PlatformEvent> events = new ArrayList<>();
        for (final NodeId nodeId : addressBook.getNodeIdSet()) {
            final List<PlatformEvent> nodeEvents =
                    generateSimpleSequenceOfEvents(randotron, nodeId, initialBirthRound, 64);

            if (nodeId.equals(branchingNode)) {
                final int indexOfParent = randotron.nextInt(0, nodeEvents.size() - 1);
                final PlatformEvent parent = nodeEvents.get(indexOfParent);
                siblingEvent = nodeEvents.get(indexOfParent + 1);

                branchingEvent = new TestingEventBuilder(randotron)
                        .setCreatorId(branchingNode)
                        .setBirthRound(siblingEvent.getBirthRound())
                        .setSelfParent(parent)
                        .build();
            }

            events.addAll(nodeEvents);
        }

        // Create a random topological ordering
        Collections.shuffle(events, randotron);
        events.sort(Comparator.comparingLong(x -> x.getBirthRound()));

        long ancientThreshold = initialBirthRound;

        final BranchDetector branchDetector = new DefaultBranchDetector(addressBook);
        branchDetector.updateEventWindow(
                new EventWindow(1 /* ignored */, ancientThreshold, 1 /* ignored */, BIRTH_ROUND_THRESHOLD));

        boolean branchingEventAdded = false;

        for (final PlatformEvent event : events) {
            if (event.getBirthRound() > ancientThreshold + 5 && randotron.nextBoolean(0.1)) {
                // Randomly advance the ancient threshold, but don't let it advance past where we are adding events.
                ancientThreshold++;
                branchDetector.updateEventWindow(
                        new EventWindow(1 /* ignored */, ancientThreshold, 1 /* ignored */, BIRTH_ROUND_THRESHOLD));
            }
            assertFalse(event.getBirthRound() < ancientThreshold);

            final PlatformEvent result = branchDetector.checkForBranches(event);
            if (!branchingEventAdded) {
                // There shouldn't have been any observed branches yet
                assertNull(result);
            } else {
                if (event.getCreatorId().equals(branchingNode)) {
                    if (event.getBirthRound() == branchingEvent.getBirthRound() + 1) {
                        // The event that comes right after the branch should also be flagged as a branching event
                        assertSame(event, result);
                    } else {
                        // Following events from the brancher should not be flagged as branches
                        // since that node is now behaving normally
                        assertNull(result);
                    }
                } else {
                    // The branching node isn't branching any more, so we should no longer observe branches
                    assertNull(result);
                }
            }

            if (event == siblingEvent) {
                assertFalse(branchingEvent.getBirthRound() < ancientThreshold);
                // Intentionally add the branching event
                final PlatformEvent branchingResult = branchDetector.checkForBranches(branchingEvent);
                assertSame(branchingEvent, branchingResult);
                branchingEventAdded = true;
            }
        }
    }

    /**
     * A single node branches by creating an event with a null parent, even though there is a legal parent that is
     * non-ancient.
     */
    @Test
    void singleNodeBranchesWithNullParentTest() {
        final Randotron randotron = Randotron.create();

        final int nodeCount = 8;

        final AddressBook addressBook =
                RandomAddressBookBuilder.create(randotron).withSize(nodeCount).build();

        final int initialBirthRound = randotron.nextInt(1, 1000);

        final NodeId branchingNode = addressBook.getNodeId(randotron.nextInt(0, addressBook.getSize()));
        PlatformEvent branchingEvent = null;

        final List<PlatformEvent> events = new ArrayList<>();
        for (final NodeId nodeId : addressBook.getNodeIdSet()) {
            final List<PlatformEvent> nodeEvents =
                    generateSimpleSequenceOfEvents(randotron, nodeId, initialBirthRound, 64);

            if (nodeId.equals(branchingNode)) {
                final PlatformEvent lastNonBranchingEvent = nodeEvents.getLast();
                branchingEvent = new TestingEventBuilder(randotron)
                        .setCreatorId(branchingNode)
                        .setBirthRound(lastNonBranchingEvent.getBirthRound() + 1)
                        .setSelfParent(null)
                        .build();
                events.add(branchingEvent);
            }

            events.addAll(nodeEvents);
        }

        // Create a random topological ordering
        Collections.shuffle(events, randotron);
        events.sort(Comparator.comparingLong(x -> x.getBirthRound()));

        long ancientThreshold = initialBirthRound;

        final BranchDetector branchDetector = new DefaultBranchDetector(addressBook);
        branchDetector.updateEventWindow(
                new EventWindow(1 /* ignored */, ancientThreshold, 1 /* ignored */, BIRTH_ROUND_THRESHOLD));

        for (final PlatformEvent event : events) {
            if (event.getBirthRound() > ancientThreshold + 5 && randotron.nextBoolean(0.1)) {
                // Randomly advance the ancient threshold, but don't let it advance past where we are adding events.
                ancientThreshold++;
                branchDetector.updateEventWindow(
                        new EventWindow(1 /* ignored */, ancientThreshold, 1 /* ignored */, BIRTH_ROUND_THRESHOLD));
            }
            assertFalse(event.getBirthRound() < ancientThreshold);

            final PlatformEvent result = branchDetector.checkForBranches(event);
            if (event.getCreatorId().equals(branchingNode)) {
                if (event == branchingEvent) {
                    // This event has a null parent when it shouldn't
                    assertSame(event, result);
                } else {
                    // Not a branch (yet)
                    assertNull(result);
                }
            } else {
                // Not a branch
                assertNull(result);
            }
        }
    }

    /**
     * An event that is ancient before it is added should be ignored.
     */
    @Test
    void ancientEventIgnored() {
        final Randotron randotron = Randotron.create();

        final int nodeCount = 8;

        final AddressBook addressBook =
                RandomAddressBookBuilder.create(randotron).withSize(nodeCount).build();

        final int initialBirthRound = randotron.nextInt(1, 1000);

        final NodeId branchingNode = addressBook.getNodeId(randotron.nextInt(0, addressBook.getSize()));
        PlatformEvent branchingEvent = null;

        final List<PlatformEvent> events = new ArrayList<>();
        for (final NodeId nodeId : addressBook.getNodeIdSet()) {
            final List<PlatformEvent> nodeEvents =
                    generateSimpleSequenceOfEvents(randotron, nodeId, initialBirthRound, 64);

            if (nodeId.equals(branchingNode)) {
                final int indexOfParent = randotron.nextInt(0, nodeEvents.size() - 1);
                final PlatformEvent parent = nodeEvents.get(indexOfParent);
                final PlatformEvent siblingEvent = nodeEvents.get(indexOfParent + 1);

                branchingEvent = new TestingEventBuilder(randotron)
                        .setCreatorId(branchingNode)
                        .setBirthRound(siblingEvent.getBirthRound())
                        .setSelfParent(parent)
                        .build();
            }

            events.addAll(nodeEvents);
        }

        // Create a random topological ordering
        Collections.shuffle(events, randotron);
        events.sort(Comparator.comparingLong(x -> x.getBirthRound()));

        long ancientThreshold = initialBirthRound;

        final BranchDetector branchDetector = new DefaultBranchDetector(addressBook);
        branchDetector.updateEventWindow(
                new EventWindow(1 /* ignored */, ancientThreshold, 1 /* ignored */, BIRTH_ROUND_THRESHOLD));

        for (final PlatformEvent event : events) {
            if (event.getBirthRound() > ancientThreshold + 5 && randotron.nextBoolean(0.1)) {
                // Randomly advance the ancient threshold, but don't let it advance past where we are adding events.
                ancientThreshold++;
                branchDetector.updateEventWindow(
                        new EventWindow(1 /* ignored */, ancientThreshold, 1 /* ignored */, BIRTH_ROUND_THRESHOLD));
            }
            assertFalse(event.getBirthRound() < ancientThreshold);

            final PlatformEvent result = branchDetector.checkForBranches(event);
            assertNull(result);
        }

        // Ensure that the branching event is ancient when we add it.
        if (ancientThreshold <= branchingEvent.getBirthRound()) {
            ancientThreshold = branchingEvent.getBirthRound() + 1;
            branchDetector.updateEventWindow(
                    new EventWindow(1 /* ignored */, ancientThreshold, 1 /* ignored */, BIRTH_ROUND_THRESHOLD));
        }

        assertNull(branchDetector.checkForBranches(branchingEvent));
    }

    /**
     * If an event has a null self parent but no there are no eligible non-ancient parents, then it's not a branch.
     */
    @Test
    void nullParentNoNonAncientParents() {
        final Randotron randotron = Randotron.create();

        final int nodeCount = 8;

        final AddressBook addressBook =
                RandomAddressBookBuilder.create(randotron).withSize(nodeCount).build();

        final int initialBirthRound = randotron.nextInt(1, 1000);

        final NodeId branchingNode = addressBook.getNodeId(randotron.nextInt(0, addressBook.getSize()));
        PlatformEvent branchingEvent = null;

        final List<PlatformEvent> events = new ArrayList<>();
        for (final NodeId nodeId : addressBook.getNodeIdSet()) {
            final List<PlatformEvent> nodeEvents =
                    generateSimpleSequenceOfEvents(randotron, nodeId, initialBirthRound, 64);

            if (nodeId.equals(branchingNode)) {
                final PlatformEvent lastNonBranchingEvent = nodeEvents.getLast();
                branchingEvent = new TestingEventBuilder(randotron)
                        .setCreatorId(branchingNode)
                        .setBirthRound(lastNonBranchingEvent.getBirthRound() + 1)
                        .setSelfParent(null)
                        .build();
            }

            events.addAll(nodeEvents);
        }

        // Create a random topological ordering
        Collections.shuffle(events, randotron);
        events.sort(Comparator.comparingLong(x -> x.getBirthRound()));

        long ancientThreshold = initialBirthRound;

        final BranchDetector branchDetector = new DefaultBranchDetector(addressBook);
        branchDetector.updateEventWindow(
                new EventWindow(1 /* ignored */, ancientThreshold, 1 /* ignored */, BIRTH_ROUND_THRESHOLD));

        for (final PlatformEvent event : events) {
            if (event.getBirthRound() > ancientThreshold + 5 && randotron.nextBoolean(0.1)) {
                // Randomly advance the ancient threshold, but don't let it advance past where we are adding events.
                ancientThreshold++;
                branchDetector.updateEventWindow(
                        new EventWindow(1 /* ignored */, ancientThreshold, 1 /* ignored */, BIRTH_ROUND_THRESHOLD));
            }
            assertFalse(event.getBirthRound() < ancientThreshold);

            final PlatformEvent result = branchDetector.checkForBranches(event);
            assertNull(result);
        }

        // Ensure that all ancestors of the branching event are ancient.
        ancientThreshold = branchingEvent.getBirthRound();
        branchDetector.updateEventWindow(
                new EventWindow(1 /* ignored */, ancientThreshold, 1 /* ignored */, BIRTH_ROUND_THRESHOLD));

        assertNull(branchDetector.checkForBranches(branchingEvent));
    }

    /**
     * We create a real branch, but all of the "evidence" is ancient.
     */
    @Test
    void ancientBranchTest() {
        final Randotron randotron = Randotron.create();

        final int nodeCount = 8;

        final AddressBook addressBook =
                RandomAddressBookBuilder.create(randotron).withSize(nodeCount).build();

        final int initialBirthRound = randotron.nextInt(1, 1000);

        final NodeId branchingNode = addressBook.getNodeId(randotron.nextInt(0, addressBook.getSize()));
        PlatformEvent branchingEvent = null;

        final List<PlatformEvent> events = new ArrayList<>();
        for (final NodeId nodeId : addressBook.getNodeIdSet()) {
            final List<PlatformEvent> nodeEvents =
                    generateSimpleSequenceOfEvents(randotron, nodeId, initialBirthRound, 64);

            if (nodeId.equals(branchingNode)) {
                final int indexOfParent = randotron.nextInt(0, nodeEvents.size() - 1);
                final PlatformEvent parent = nodeEvents.get(indexOfParent);
                final PlatformEvent siblingEvent = nodeEvents.get(indexOfParent + 1);

                // Intentionally choose a birth round that is far after the last regular event
                branchingEvent = new TestingEventBuilder(randotron)
                        .setCreatorId(branchingNode)
                        .setBirthRound(initialBirthRound + 100)
                        .setSelfParent(parent)
                        .build();
            }

            events.addAll(nodeEvents);
        }

        // Create a random topological ordering
        Collections.shuffle(events, randotron);
        events.sort(Comparator.comparingLong(x -> x.getBirthRound()));

        long ancientThreshold = initialBirthRound;

        final BranchDetector branchDetector = new DefaultBranchDetector(addressBook);
        branchDetector.updateEventWindow(
                new EventWindow(1 /* ignored */, ancientThreshold, 1 /* ignored */, BIRTH_ROUND_THRESHOLD));

        for (final PlatformEvent event : events) {
            if (event.getBirthRound() > ancientThreshold + 5 && randotron.nextBoolean(0.1)) {
                // Randomly advance the ancient threshold, but don't let it advance past where we are adding events.
                ancientThreshold++;
                branchDetector.updateEventWindow(
                        new EventWindow(1 /* ignored */, ancientThreshold, 1 /* ignored */, BIRTH_ROUND_THRESHOLD));
            }
            assertFalse(event.getBirthRound() < ancientThreshold);

            final PlatformEvent result = branchDetector.checkForBranches(event);
            assertNull(result);
        }

        // Advance the ancient threshold to "hide the evidence"
        ancientThreshold = branchingEvent.getBirthRound();
        branchDetector.updateEventWindow(
                new EventWindow(1 /* ignored */, ancientThreshold, 1 /* ignored */, BIRTH_ROUND_THRESHOLD));

        assertNull(branchDetector.checkForBranches(branchingEvent));
    }
}
