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

import static com.swirlds.common.test.RandomUtils.getRandomPrintSeed;
import static com.swirlds.common.test.RandomUtils.randomHash;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.swirlds.common.system.NodeId;
import com.swirlds.platform.event.EventDescriptor;
import com.swirlds.platform.event.tipset.ChildlessEventTracker;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ChildlessEventTracker Tests")
class ChildlessEventTrackerTests {

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

        final Set<EventDescriptor> expectedEvents = new HashSet<>(batch2);
        for (final EventDescriptor descriptor : batch1) {
            if (descriptor.getCreator().id() % 2 == 0) {
                expectedEvents.add(descriptor);
            }
        }

        assertEquals(expectedEvents, new HashSet<>(tracker.getChildlessEvents()));

        // Increase the minimum generation non-ancient to 1, all events from batch1 should be removed
        tracker.pruneOldEvents(1);

        assertEquals(new HashSet<>(batch2), new HashSet<>(tracker.getChildlessEvents()));
    }
}
