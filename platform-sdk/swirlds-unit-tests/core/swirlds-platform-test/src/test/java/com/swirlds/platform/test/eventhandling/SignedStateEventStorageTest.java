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

package com.swirlds.platform.test.eventhandling;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.swirlds.platform.eventhandling.SignedStateEventStorage;
import com.swirlds.platform.internal.EventImpl;
import com.swirlds.test.framework.TestComponentTags;
import com.swirlds.test.framework.TestTypeTags;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

class SignedStateEventStorageTest {
    @Test
    @Tag(TestTypeTags.FUNCTIONAL)
    @Tag(TestComponentTags.PLATFORM)
    @DisplayName("Test SignedStateEventFilter")
    void test() {
        final int numNodes = 5;
        final var events = new SignedStateEventStorage();
        int currentRoundGen;

        assertEquals(0, events.getQueueSize(), "new object should be empty");
        assertArrayEquals(new EventImpl[0], events.getEventsForLatestRound(), "new object should be empty");

        // round 1, all nodes add 1 event
        currentRoundGen = 1;
        for (int node = 0; node < numNodes; node++) {
            events.add(event(currentRoundGen, node));
        }
        assertEquals(numNodes, events.getEventsForLatestRound().length, "There should be 1 event for each node");
        currentRoundGen = 2;

        // rounds 2-4, all but the last node add events
        for (int round = 0; round < 3; round++, currentRoundGen++) {
            for (int node = 0; node < numNodes - 1; node++) {
                events.add(event(currentRoundGen, node));
            }
        }

        assertEquals(
                numNodes + (numNodes - 1) * 3,
                events.getEventsForLatestRound().length,
                "All events added should be saved since we didnt expire any");

        // we expire everything added in the first round which is the only round where the last node created any events
        long expiredRoundGen = 2;
        events.expireEvents(expiredRoundGen);

        assertEquals(
                (numNodes - 1) * 3,
                events.getEventsForLatestRound().length,
                "Expected 3 rounds of events without the last node");

        var array = events.getEventsForLatestRound();

        var reconnected = new SignedStateEventStorage();

        reconnected.loadDataFromSignedState(array, expiredRoundGen);

        assertEquals(events, reconnected, "after a reconnect, the new instance should be the same as the original");

        assertEqual(array, reconnected.getEventsForLatestRound());

        // round 5, add same events to both
        for (int node = 0; node < numNodes; node++) {
            final EventImpl event = event(currentRoundGen, node);
            events.add(event);
            reconnected.add(event);
        }

        assertEquals(events, reconnected, "after a reconnect, the new instance should be the same as the original");

        // expire both
        expiredRoundGen = 4;
        events.expireEvents(expiredRoundGen);
        reconnected.expireEvents(expiredRoundGen);

        // both should be equal
        assertEquals(events, reconnected, "after a reconnect, the new instance should be the same as the original");
    }

    private static void assertEqual(EventImpl[] e1, EventImpl[] e2) {
        assertEquals(e1.length, e2.length, "length should be the same");
        for (int i = 0; i < e1.length; i++) {
            assertEquals(e1[i], e2[i], "events should be the same same");
        }
    }

    private EventImpl event(int roundCreated, int creator) {
        final EventImpl event = mock(EventImpl.class);

        when(event.getRoundCreated()).thenReturn((long) roundCreated);
        when(event.getGeneration()).thenReturn((long) roundCreated);
        when(event.getRoundReceived()).thenReturn((long) roundCreated + 1);
        when(event.getCreatorId()).thenReturn((long) creator);

        return event;
    }
}
