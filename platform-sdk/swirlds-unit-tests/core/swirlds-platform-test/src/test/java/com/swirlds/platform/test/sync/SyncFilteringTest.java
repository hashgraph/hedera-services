/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

import static com.swirlds.common.test.fixtures.RandomUtils.getRandomPrintSeed;
import static com.swirlds.common.test.fixtures.RandomUtils.randomHash;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.swirlds.base.test.fixtures.time.FakeTime;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.platform.NodeId;
import com.swirlds.common.utility.CompareTo;
import com.swirlds.platform.event.GossipEvent;
import com.swirlds.platform.gossip.shadowgraph.ShadowEvent;
import com.swirlds.platform.gossip.shadowgraph.ShadowGraph;
import com.swirlds.platform.gossip.shadowgraph.SyncUtils;
import com.swirlds.platform.internal.EventImpl;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SyncFilteringTest {

    @Test
    void filterLikelyDuplicatesTest() {
        final Random random = getRandomPrintSeed();

        final NodeId selfId = new NodeId(0);

        final FakeTime clock = new FakeTime();

        // Step 1: create a bunch of fake data

        final List<EventImpl> selfEvents = new ArrayList<>();
        final List<EventImpl> ancestors = new ArrayList<>();
        final List<EventImpl> nonAncestors = new ArrayList<>();

        final Map<NodeId, EventImpl> tipMap = new HashMap<>();
        Instant lastTime = null;
        for (int i = 0; i < 1000; i++) {
            final EventImpl event = mock(EventImpl.class);
            final GossipEvent gossipEvent = mock(GossipEvent.class);
            when(event.getBaseEvent()).thenReturn(gossipEvent);
            lastTime = clock.now();
            when(gossipEvent.getTimeReceived()).thenReturn(lastTime);
            final Hash hash = randomHash(random);
            when(event.getBaseHash()).thenReturn(hash);

            if (i % 10 == 0) {
                // create self event
                when(event.getCreatorId()).thenReturn(selfId);
                selfEvents.add(event);
            } else {
                // create an other event

                when(event.getCreatorId()).thenReturn(new NodeId(random.nextInt(1, 10)));

                if (i % 10 == 1) {
                    // create non-ancestor
                    nonAncestors.add(event);
                } else {
                    // create ancestor (these are likely to be the most common type during a large sync)
                    ancestors.add(event);
                }
            }

            tipMap.put(event.getCreatorId(), event);
            clock.tick(Duration.ofMillis(random.nextInt(50, 100)));
        }

        final List<EventImpl> allEvents = new ArrayList<>();
        allEvents.addAll(selfEvents);
        allEvents.addAll(ancestors);
        allEvents.addAll(nonAncestors);

        // Step 2: create mock shadowgraph that returns the fake data generated in step 1

        final ShadowGraph shadowGraph = mock(ShadowGraph.class);

        final List<ShadowEvent> tips = new ArrayList<>();
        for (final EventImpl event : tipMap.values()) {
            final ShadowEvent shadowEvent = new ShadowEvent(event, null, null);
            tips.add(shadowEvent);
        }
        when(shadowGraph.getTips()).thenReturn(tips);

        final Set<ShadowEvent> ancestorShadowEvents = new HashSet<>();
        for (final EventImpl event : ancestors) {
            final ShadowEvent shadowEvent = new ShadowEvent(event, null, null);
            ancestorShadowEvents.add(shadowEvent);
        }
        for (final EventImpl event : selfEvents) {
            final ShadowEvent shadowEvent = new ShadowEvent(event, null, null);
            ancestorShadowEvents.add(shadowEvent);
        }
        when(shadowGraph.findAncestors(any(), any())).thenReturn(ancestorShadowEvents);

        // Step 3: see what gets filtered out depending on the current time

        final Duration nonAncestorThreshold = Duration.ofSeconds(3);

        clock.reset();
        while (CompareTo.isLessThan(clock.now().plus(nonAncestorThreshold), clock.now())) {

            final Set<EventImpl> expectedEvents = new HashSet<>();

            // we always expect all self events and ancestors of self events
            expectedEvents.addAll(selfEvents);
            expectedEvents.addAll(ancestors);

            // We only expect non-ancestor events if we've had them for longer than the non-ancestor threshold
            for (final EventImpl event : nonAncestors) {
                final Duration eventAge = Duration.between(event.getBaseEvent().getTimeReceived(), clock.now());
                if (CompareTo.isGreaterThan(eventAge, nonAncestorThreshold)) {
                    expectedEvents.add(event);
                }
            }

            final List<EventImpl> filteredEvents =
                    SyncUtils.filterLikelyDuplicates(selfId, nonAncestorThreshold, clock.now(), allEvents, null);

            assertEquals(expectedEvents.size(), filteredEvents.size());
            for (final EventImpl event : filteredEvents) {
                assertTrue(expectedEvents.contains(event));
            }

            clock.tick(Duration.ofMillis(random.nextInt(25, 100)));
        }
    }
}
