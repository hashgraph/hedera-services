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

package com.swirlds.platform.test.event.linking;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.swirlds.common.crypto.Hash;
import com.swirlds.common.system.events.EventDescriptor;
import com.swirlds.platform.consensus.GraphGenerations;
import com.swirlds.platform.event.GossipEvent;
import com.swirlds.platform.event.linking.EventLinker;
import com.swirlds.platform.event.linking.OrphanBufferingLinker;
import com.swirlds.platform.event.linking.ParentFinder;
import com.swirlds.platform.gossip.shadowgraph.Generations;
import com.swirlds.platform.internal.EventImpl;
import com.swirlds.platform.state.signed.SignedState;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.junit.jupiter.api.Assertions;

public class OrphanBufferTester implements EventLinker {
    final Map<Hash, EventImpl> linkedEvents = new HashMap<>();
    final ParentFinder parentFinder = new ParentFinder(linkedEvents::get);
    final OrphanBufferingLinker eventLinker;

    public OrphanBufferTester(final Function<ParentFinder, OrphanBufferingLinker> linkerConstructor) {
        eventLinker = linkerConstructor.apply(parentFinder);
    }

    @Override
    public void linkEvent(final GossipEvent event) {
        eventLinker.linkEvent(event);
    }

    public void linkEvent(final List<GossipEvent> graph, final int index) {
        eventLinker.linkEvent(graph.get(index));
    }

    @Override
    public void updateGenerations(final GraphGenerations generations) {
        eventLinker.updateGenerations(generations);
    }

    public void newAncientGeneration(final long ancientGeneration) {
        updateGenerations(new Generations(
                GraphGenerations.FIRST_GENERATION, ancientGeneration + 1, Long.MAX_VALUE // not relevant ATM
                ));
    }

    public void loadFromSignedState(final SignedState signedState) {
        eventLinker.loadFromSignedState(signedState);
    }

    @Override
    public boolean hasLinkedEvents() {
        return eventLinker.hasLinkedEvents();
    }

    @Override
    public EventImpl pollLinkedEvent() {
        final EventImpl linkedEvent = eventLinker.pollLinkedEvent();
        linkedEvents.put(linkedEvent.getBaseHash(), linkedEvent);
        return linkedEvent;
    }

    public void assertNoLinkedEvents() {}

    public void assertNumOrphans(final int expected) {
        Assertions.assertEquals(expected, eventLinker.getNumOrphans(), "expected number of orphans does not match");
    }

    public void assertOrphan(final GossipEvent orphan) {
        Assertions.assertTrue(
                eventLinker.isOrphan(orphan.getDescriptor()),
                String.format("the event %s is expected to be an orphan", orphan));
    }

    public void assertOrphans(final List<GossipEvent> orphans) {
        orphans.forEach(this::assertOrphan);
    }

    public void assertGeneration(final List<GossipEvent> graph, final int... expectedIndexes) {
        final List<GossipEvent> expectedEvents = new ArrayList<>();
        for (final int i : expectedIndexes) {
            expectedEvents.add(graph.get(i));
        }
        assertGeneration(expectedEvents);
    }

    public void assertGeneration(final GossipEvent... expectedEvents) {
        assertGeneration(Arrays.stream(expectedEvents).toList());
    }

    public void assertGeneration(final List<GossipEvent> expectedEvents) {
        if (expectedEvents.isEmpty()) {
            assertFalse(hasLinkedEvents(), "we are not expecting any linked events");
            return;
        }
        assertTrue(hasLinkedEvents(), "we are expecting linked events");
        final Map<EventDescriptor, GossipEvent> map = new HashMap<>();
        for (int i = 0; i < expectedEvents.size(); i++) {
            final EventImpl linkedEvent = pollLinkedEvent();
            assertNotNull(linkedEvent, "expected more events in generation");
            map.put(linkedEvent.getBaseEvent().getDescriptor(), linkedEvent.getBaseEvent());
        }
        for (final GossipEvent expectedEvent : expectedEvents) {
            final GossipEvent actualEvent = map.get(expectedEvent.getDescriptor());
            assertSame(expectedEvent, actualEvent, "expected event not found");
        }
    }
}
