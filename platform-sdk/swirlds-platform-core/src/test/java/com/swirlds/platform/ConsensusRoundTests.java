/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.swirlds.common.test.RandomUtils;
import com.swirlds.platform.consensus.GraphGenerations;
import com.swirlds.platform.internal.ConsensusRound;
import com.swirlds.platform.internal.EventImpl;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class ConsensusRoundTests {

    @Test
    void testConstructor() {
        final EventImpl e1 = mock(EventImpl.class);
        final EventImpl e2 = mock(EventImpl.class);
        final EventImpl e3 = mock(EventImpl.class);
        final GraphGenerations g = mock(GraphGenerations.class);

        when(e3.isLastInRoundReceived()).thenReturn(true);

        when(e1.getRoundReceived()).thenReturn(1L);
        when(e2.getRoundReceived()).thenReturn(1L);
        when(e3.getRoundReceived()).thenReturn(1L);

        final List<EventImpl> events = List.of(e1, e2, e3);

        final ConsensusRound round = new ConsensusRound(events, g);

        assertEquals(events, round.getConsensusEvents(), "consensus event list does not match the provided list.");
        assertEquals(events.size(), round.getNumEvents(), "numEvents does not match the events provided.");
        assertEquals(e3, round.getLastEvent(), "Last event does not match the event provided.");
        assertEquals(1, round.getRoundNum(), "roundNum does not match the events provided.");
        assertFalse(round.hasShutdownEvent(), "hasShutdownEvent does match the values of the events provided.");
        assertTrue(round.isComplete(), "isComplete does not match the values of the vents provided.");
        assertSame(g, round.getGenerations(), "getGenerations should match the supplied generations");
    }

    @Test
    void testShutdownEvent() {
        final EventImpl e1 = mock(EventImpl.class);
        final EventImpl e2 = mock(EventImpl.class);
        final EventImpl e3 = mock(EventImpl.class);

        when(e3.isLastOneBeforeShutdown()).thenReturn(true);

        when(e1.getRoundReceived()).thenReturn(1L);
        when(e2.getRoundReceived()).thenReturn(1L);
        when(e3.getRoundReceived()).thenReturn(1L);

        final List<EventImpl> events = List.of(e1, e2, e3);

        final ConsensusRound round = new ConsensusRound(events, mock(GraphGenerations.class));
        assertTrue(round.hasShutdownEvent(), "hasShutdownEvent does match the values of the events provided.");
    }

    @Test
    void testIsComplete() {
        final EventImpl e1 = mock(EventImpl.class);
        final EventImpl e2 = mock(EventImpl.class);
        final EventImpl e3 = mock(EventImpl.class);

        when(e1.getRoundReceived()).thenReturn(1L);
        when(e2.getRoundReceived()).thenReturn(1L);
        when(e3.getRoundReceived()).thenReturn(1L);

        final List<EventImpl> events = List.of(e1, e2, e3);

        final ConsensusRound round = new ConsensusRound(events, mock(GraphGenerations.class));
        assertFalse(round.isComplete(), "isComplete does not match the values of the vents provided.");
        assertNull(round.getLastEvent(), "Last event should be null in an incomplete round.");
    }

    @Test
    void testNumApplicationTransactions() {
        final Random r = RandomUtils.initRandom(null);

        int numActualTransactions = 0;
        final List<EventImpl> events = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            final EventImpl event = mock(EventImpl.class);
            final int numTransactions = r.nextInt(200);
            numActualTransactions += numTransactions;
            when(event.getNumAppTransactions()).thenReturn(numTransactions);
            events.add(event);
        }

        final ConsensusRound round = new ConsensusRound(events, mock(GraphGenerations.class));

        assertEquals(
                numActualTransactions, round.getNumAppTransactions(), "Incorrect number of application transactions.");
    }
}
