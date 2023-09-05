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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.swirlds.common.test.fixtures.RandomUtils;
import com.swirlds.platform.consensus.ConsensusSnapshot;
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
        final ConsensusSnapshot snapshot = mock(ConsensusSnapshot.class);

        when(e3.isLastInRoundReceived()).thenReturn(true);

        when(snapshot.round()).thenReturn(1L);

        final List<EventImpl> events = List.of(e1, e2, e3);

        final ConsensusRound round = new ConsensusRound(events, mock(EventImpl.class), g, snapshot);

        assertEquals(events, round.getConsensusEvents(), "consensus event list does not match the provided list.");
        assertEquals(events.size(), round.getNumEvents(), "numEvents does not match the events provided.");
        assertEquals(e3, round.getLastEvent(), "Last event does not match the event provided.");
        assertEquals(1, round.getRoundNum(), "roundNum does not match the events provided.");
        assertSame(g, round.getGenerations(), "getGenerations should match the supplied generations");
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

        final ConsensusRound round = new ConsensusRound(
                events, mock(EventImpl.class), mock(GraphGenerations.class), mock(ConsensusSnapshot.class));

        assertEquals(
                numActualTransactions, round.getNumAppTransactions(), "Incorrect number of application transactions.");
    }
}
