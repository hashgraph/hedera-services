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

import static com.swirlds.platform.test.observers.ObservationType.ADDED;
import static com.swirlds.platform.test.observers.ObservationType.PRE_CONSENSUS;
import static com.swirlds.platform.test.observers.ObservationType.STALE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.mock;

import com.swirlds.platform.consensus.ConsensusSnapshot;
import com.swirlds.platform.gossip.shadowgraph.Generations;
import com.swirlds.platform.internal.ConsensusRound;
import com.swirlds.platform.internal.EventImpl;
import com.swirlds.platform.observers.EventObserverDispatcher;
import com.swirlds.platform.test.event.SimpleEvent;
import com.swirlds.platform.test.observers.AddedAndStale;
import com.swirlds.platform.test.observers.AddedObserver;
import com.swirlds.platform.test.observers.ConsRound;
import com.swirlds.platform.test.observers.ObservationType;
import com.swirlds.platform.test.observers.PreConsensusObserver;
import com.swirlds.platform.test.observers.SimpleEventTracker;
import com.swirlds.platform.test.observers.StaleObserver;
import com.swirlds.test.framework.TestComponentTags;
import com.swirlds.test.framework.TestTypeTags;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

class EventObserverDispatcherTests {

    PreConsensusObserver preCons = new PreConsensusObserver();
    AddedObserver added = new AddedObserver();
    StaleObserver stale = new StaleObserver();
    AddedAndStale addedStale = new AddedAndStale();
    ConsRound consRound = new ConsRound();

    List<SimpleEventTracker> allEventTrackers = List.of(preCons, added, stale, addedStale);

    EventObserverDispatcher dispatcher = new EventObserverDispatcher(preCons, added, stale, consRound, addedStale);

    SimpleEvent e1 = new SimpleEvent(1);
    SimpleEvent e2 = new SimpleEvent(2);

    @Test
    @Tag(TestTypeTags.FUNCTIONAL)
    @Tag(TestComponentTags.PLATFORM)
    @DisplayName("Verify Observations")
    void test() {
        dispatchAndCheck(PRE_CONSENSUS, e1, preCons);

        dispatchAndCheck(PRE_CONSENSUS, e2, preCons);

        dispatchAndCheck(ADDED, e1, added, addedStale);

        dispatchAndCheck(ADDED, e2, added, addedStale);

        dispatchAndCheck(STALE, e1, stale, addedStale);

        e2.setLastInRoundReceived(true);

        dispatchAndCheckConsensus(new ConsensusRound(
                List.of(e1, e2),
                mock(EventImpl.class),
                Generations.GENESIS_GENERATIONS,
                mock(ConsensusSnapshot.class)));
    }

    private void dispatchAndCheckConsensus(final ConsensusRound consensusRound, final SimpleEventTracker... yes) {
        dispatcher.consensusRound(consensusRound);
    }

    private void dispatchAndCheck(
            final ObservationType type, final SimpleEvent event, final SimpleEventTracker... yes) {
        switch (type) {
            case PRE_CONSENSUS:
                dispatcher.preConsensusEvent(event);
                break;
            case ADDED:
                dispatcher.eventAdded(event);
                break;
            case STALE:
                dispatcher.staleEvent(event);
                break;
            case CONSENSUS:
                fail("Invalid unit test: Consensus observers receive rounds, not events.");
        }

        final List<SimpleEventTracker> list = List.of(yes);
        for (final SimpleEventTracker t : allEventTrackers) {
            final boolean assertBool = list.contains(t);
            assertEquals(
                    assertBool,
                    t.isObserved(type, event),
                    String.format(
                            "%s %s have observed the %s event %d",
                            t.getClass().getName(),
                            assertBool ? "should" : "should not",
                            type.toString(),
                            event.getId()));
        }
    }
}
