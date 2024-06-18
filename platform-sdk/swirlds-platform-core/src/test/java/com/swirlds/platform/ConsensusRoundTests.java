/*
 * Copyright (C) 2022-2024 Hedera Hashgraph, LLC
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
import com.swirlds.common.test.fixtures.Randotron;
import com.swirlds.platform.consensus.ConsensusSnapshot;
import com.swirlds.platform.consensus.EventWindow;
import com.swirlds.platform.consensus.GraphGenerations;
import com.swirlds.platform.internal.ConsensusRound;
import com.swirlds.platform.internal.EventImpl;
import com.swirlds.platform.system.address.AddressBook;
import com.swirlds.platform.test.fixtures.event.EventImplTestUtils;
import com.swirlds.platform.test.fixtures.event.TestingEventBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class ConsensusRoundTests {

    @Test
    void testConstructor() {
        final Randotron r = Randotron.create();
        final GraphGenerations g = mock(GraphGenerations.class);
        final ConsensusSnapshot snapshot = mock(ConsensusSnapshot.class);

        when(snapshot.round()).thenReturn(1L);

        final List<EventImpl> events = List.of(
                EventImplTestUtils.createEventImpl(new TestingEventBuilder(r), null, null),
                EventImplTestUtils.createEventImpl(new TestingEventBuilder(r), null, null),
                EventImplTestUtils.createEventImpl(new TestingEventBuilder(r), null, null));

        final ConsensusRound round = new ConsensusRound(
                mock(AddressBook.class), events, mock(EventImpl.class), g, mock(EventWindow.class), snapshot, false);

        assertEquals(events, round.getConsensusEvents(), "consensus event list does not match the provided list.");
        assertEquals(events.size(), round.getNumEvents(), "numEvents does not match the events provided.");
        assertEquals(1, round.getRoundNum(), "roundNum does not match the events provided.");
        assertSame(g, round.getGenerations(), "getGenerations should match the supplied generations");
    }

    @Test
    void testNumApplicationTransactions() {
        final Random r = RandomUtils.initRandom(null);

        int numActualTransactions = 0;
        final List<EventImpl> events = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            final int numTransactions = r.nextInt(200);
            numActualTransactions += numTransactions;
            final EventImpl event = EventImplTestUtils.createEventImpl(
                    new TestingEventBuilder(r)
                            .setAppTransactionCount(numTransactions)
                            .setSystemTransactionCount(0),
                    null,
                    null);
            events.add(event);
        }

        final ConsensusRound round = new ConsensusRound(
                mock(AddressBook.class),
                events,
                mock(EventImpl.class),
                mock(GraphGenerations.class),
                mock(EventWindow.class),
                mock(ConsensusSnapshot.class),
                false);

        assertEquals(
                numActualTransactions, round.getNumAppTransactions(), "Incorrect number of application transactions.");
    }
}
