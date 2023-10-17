/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.swirlds.common.system.NodeId;
import com.swirlds.common.system.events.EventDescriptor;
import com.swirlds.platform.event.creation.tipset.ChildlessEventTracker;
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

@DisplayName("ChildlessEventTracker Tests")
class ChildlessEventTrackerTests {

    /**
     * The childless event tracker doesn't track branches in the hashgraph, it only stores whatever branch has produced
     * the event with the highest generation. Return a set that contains only the event from the latest branch from each
     * creator.
     */
    @NonNull
    private Set<EventDescriptor> removeBranches(@NonNull List<EventDescriptor> events) {
        final Map<NodeId, EventDescriptor> uniqueEvents = new HashMap<>();

        for (final EventDescriptor event : events) {
            final EventDescriptor existingEvent = uniqueEvents.get(event.getCreator());
            if (existingEvent == null || existingEvent.getGeneration() < event.getGeneration()) {
                uniqueEvents.put(event.getCreator(), event);
            }
        }

        return new HashSet<>(uniqueEvents.values());
    }

    @Test
    @DisplayName("Basic Behavior Test")
    void basicBehaviorTest() {
        final Random random = getRandomPrintSeed();

        final ChildlessEventTracker tracker = new ChildlessEventTracker();

        // Adding some event with no parents
        final List<EventDescriptor> batch1 = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            final EventDescriptor descriptor = new EventDescriptor(randomHash(random), new NodeId(i), 0);
            tracker.addEvent(descriptor, List.of());
            batch1.add(descriptor);
        }

        assertEquals(new HashSet<>(batch1), new HashSet<>(tracker.getChildlessEvents()));

        // Increase generation. All new events will either have parents with odd node
        // IDs or parents that haven't been registered yet. When this is completed,
        // the new events should be tracked, and all registered parents should not be.

        final List<EventDescriptor> batch2 = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            final NodeId nonExistentParentId = new NodeId(i + 100);
            final EventDescriptor nonExistentParent = new EventDescriptor(randomHash(random), nonExistentParentId, 0);
            final int oddParentId = (i * 2 + 1) % 10;
            final EventDescriptor oddParent = batch1.get(oddParentId);

            final EventDescriptor descriptor = new EventDescriptor(randomHash(), new NodeId(i), 1);
            tracker.addEvent(descriptor, List.of(nonExistentParent, oddParent));
            batch2.add(descriptor);
        }

        final List<EventDescriptor> expectedEvents = new ArrayList<>(batch2);
        for (final EventDescriptor descriptor : batch1) {
            if (descriptor.getCreator().id() % 2 == 0) {
                expectedEvents.add(descriptor);
            }
        }

        assertEquals(removeBranches(expectedEvents), new HashSet<>(tracker.getChildlessEvents()));

        // Increase the minimum generation non-ancient to 1, all events from batch1 should be removed
        tracker.pruneOldEvents(1);

        assertEquals(removeBranches(batch2), new HashSet<>(tracker.getChildlessEvents()));
    }

    @Test
    @DisplayName("Branching Test")
    void branchingTest() {
        final Random random = getRandomPrintSeed();

        final ChildlessEventTracker tracker = new ChildlessEventTracker();

        final EventDescriptor e0 = new EventDescriptor(randomHash(random), new NodeId(0), 0);
        final EventDescriptor e1 = new EventDescriptor(randomHash(random), new NodeId(0), 1);
        final EventDescriptor e2 = new EventDescriptor(randomHash(random), new NodeId(0), 2);

        tracker.addEvent(e0, List.of());
        tracker.addEvent(e1, List.of(e0));
        tracker.addEvent(e2, List.of(e1));

        final List<EventDescriptor> batch1 = tracker.getChildlessEvents();
        assertEquals(1, batch1.size());
        assertEquals(e2, batch1.get(0));

        final EventDescriptor e3 = new EventDescriptor(randomHash(random), new NodeId(0), 3);
        final EventDescriptor e3Branch = new EventDescriptor(randomHash(random), new NodeId(0), 3);

        // Branch with the same generation, existing event should not be discarded.
        tracker.addEvent(e3, List.of(e2));
        tracker.addEvent(e3Branch, List.of(e2));

        final List<EventDescriptor> batch2 = tracker.getChildlessEvents();
        assertEquals(1, batch2.size());
        assertEquals(e3, batch2.get(0));

        // Branch with a lower generation, existing event should not be discarded.
        final EventDescriptor e2Branch = new EventDescriptor(randomHash(random), new NodeId(0), 2);
        tracker.addEvent(e2Branch, List.of(e1));

        assertEquals(1, batch2.size());
        assertEquals(e3, batch2.get(0));

        // Branch with a higher generation, existing event should be discarded.
        final EventDescriptor e99Branch = new EventDescriptor(randomHash(random), new NodeId(0), 99);
        tracker.addEvent(e99Branch, List.of());

        final List<EventDescriptor> batch3 = tracker.getChildlessEvents();
        assertEquals(1, batch3.size());
        assertEquals(e99Branch, batch3.get(0));
    }
}
