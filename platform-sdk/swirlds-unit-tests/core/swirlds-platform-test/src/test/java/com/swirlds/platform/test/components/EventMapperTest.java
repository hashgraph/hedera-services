/*
 * Copyright (C) 2021-2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform.test.components;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.swirlds.common.system.NodeId;
import com.swirlds.platform.components.EventMapper;
import com.swirlds.platform.internal.EventImpl;
import com.swirlds.platform.test.event.EventMocks;
import com.swirlds.test.framework.TestComponentTags;
import com.swirlds.test.framework.TestTypeTags;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@DisplayName("Event Mapper Test")
class EventMapperTest {

    /**
     * Inserting data into the mapper should result in that data coming back out of the map.
     */
    @Test
    @Tag(TestTypeTags.FUNCTIONAL)
    @Tag(TestComponentTags.PLATFORM)
    @DisplayName("DataInDataOutTest")
    void dataInDataOutTest() {

        final EventMapper mapper = new EventMapper(NodeId.createMain(0));

        final long nodeId1 = 1;
        final long nodeId2 = 2;

        // Check default values when no data has been added
        assertNull(mapper.getMostRecentEvent(nodeId1), "no data in mapper yet");
        assertEquals(-1, mapper.getHighestGenerationNumber(nodeId1), "no data in mapper yet");

        // Add a simple event
        final EventImpl e1 = EventMocks.createMockEvent(nodeId1, 1234, null);
        mapper.eventAdded(e1);

        assertSame(e1, mapper.getMostRecentEvent(nodeId1), "node returned should be same object");
        assertEquals(1234, mapper.getHighestGenerationNumber(nodeId1), "generation number should match");

        // Add an event with an other parent
        final EventImpl e2 = EventMocks.createMockEvent(nodeId1, 6789, e1);
        mapper.eventAdded(e2);

        assertSame(e2, mapper.getMostRecentEvent(nodeId1), "node returned should be same object");
        assertEquals(6789, mapper.getHighestGenerationNumber(nodeId1), "generation number should match");

        // Adding an event with a different node ID shouldn't change anything for the first ID,
        final EventImpl e3 = EventMocks.createMockEvent(nodeId2, 9999, null);
        mapper.eventAdded(e3);

        assertSame(e2, mapper.getMostRecentEvent(nodeId1), "node returned should be same object");
        assertEquals(6789, mapper.getHighestGenerationNumber(nodeId1), "generation number should match");

        // Clearing the map should clear all data
        mapper.clear();
        assertNull(mapper.getMostRecentEvent(nodeId1), "no data in mapper yet");
        assertEquals(-1, mapper.getHighestGenerationNumber(nodeId1), "no data in mapper yet");
    }

    @Test
    @Tag(TestTypeTags.FUNCTIONAL)
    @Tag(TestComponentTags.PLATFORM)
    @DisplayName("Orphaned Event Test")
    void orphanedEventTest() {
        final EventMapper mapper = new EventMapper(NodeId.createMain(0));

        // With no events in the mapper there will be no descendants
        for (int i = 0; i < 4; i++) {
            assertFalse(mapper.doesMostRecentEventHaveDescendants(i), "no events yet, should not have descendants");
        }

        // The first event added for each node will not have descendants until they are used as other parents
        for (int i = 0; i < 4; i++) {
            final EventImpl event = EventMocks.createMockEvent(i, 0, null);
            mapper.eventAdded(event);
            assertFalse(mapper.doesMostRecentEventHaveDescendants(i), "should not have descendants");
        }
        final EventImpl firstNode3Event = mapper.getMostRecentEvent(3);

        // Using an event as an other parent should mark it as having descendants
        for (int i = 0; i < 4; i++) {
            final long otherParentId = (i + 1) % 4;
            final EventImpl event = EventMocks.createMockEvent(i, 1, mapper.getMostRecentEvent(otherParentId));
            mapper.eventAdded(event);
            assertTrue(mapper.doesMostRecentEventHaveDescendants(otherParentId), "descendant was just created");
            // The node we just added will not have any descendants
            assertFalse(mapper.doesMostRecentEventHaveDescendants(i), "should not have descendants");
        }

        // In the previous operation node 3 got the last event, and so that event has no descendants. Using an old event
        // from node 3 won't cause the most recent event from 3 to have any descendants.
        assertFalse(mapper.doesMostRecentEventHaveDescendants(3), "event is most recent one created");
        final EventImpl e = EventMocks.createMockEvent(1, 2, firstNode3Event);
        mapper.eventAdded(e);
        assertFalse(mapper.doesMostRecentEventHaveDescendants(3), "event should not have descendants");
    }
}
