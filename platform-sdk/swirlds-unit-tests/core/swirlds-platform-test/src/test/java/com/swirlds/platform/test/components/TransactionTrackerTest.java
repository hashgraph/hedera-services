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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.swirlds.platform.components.transaction.TransactionTracker;
import com.swirlds.platform.internal.ConsensusRound;
import com.swirlds.platform.internal.EventImpl;
import com.swirlds.test.framework.TestComponentTags;
import com.swirlds.test.framework.TestTypeTags;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

class TransactionTrackerTest {

    @Test
    @Tag(TestTypeTags.FUNCTIONAL)
    @Tag(TestComponentTags.PLATFORM)
    @DisplayName("tests transaction tracker")
    void test() {
        final TransactionTracker transactionTracker = new TransactionTracker();

        final EventImpl event = mock(EventImpl.class);
        final ConsensusRound consRound = mock(ConsensusRound.class);
        when(consRound.getConsensusEvents()).thenReturn(List.of(event));
        when(consRound.getNumEvents()).thenReturn(1);

        final AtomicLong round = new AtomicLong(1);
        final AtomicBoolean hasUserTransactions = new AtomicBoolean(false);
        when(event.getRoundReceived()).thenAnswer((v) -> round.get());
        when(event.hasUserTransactions()).thenAnswer((v) -> hasUserTransactions.get());

        assertEquals(0, transactionTracker.getNumUserTransEvents(), "a new tracker should be set to 0");
        transactionTracker.eventAdded(event);
        assertEquals(
                0,
                transactionTracker.getNumUserTransEvents(),
                "an event without user transactions should not have an effect");

        hasUserTransactions.set(true);

        transactionTracker.eventAdded(event);
        transactionTracker.eventAdded(event);
        assertEquals(2, transactionTracker.getNumUserTransEvents(), "2 events were added");
        assertEquals(
                -1,
                transactionTracker.getLastRoundReceivedAllTransCons(),
                "there was no round received at the end of which there were no non-consensus transactions");

        transactionTracker.consensusRound(consRound);
        transactionTracker.consensusRound(consRound);
        assertEquals(0, transactionTracker.getNumUserTransEvents(), "all events reached consensus");
        assertEquals(
                round.get(),
                transactionTracker.getLastRoundReceivedAllTransCons(),
                "there were no non-consensus transactions left at the end of the round");

        round.set(2);
        transactionTracker.eventAdded(event);
        transactionTracker.eventAdded(event);
        assertEquals(2, transactionTracker.getNumUserTransEvents(), "2 events were added");

        transactionTracker.consensusRound(consRound);
        transactionTracker.staleEvent(event);
        assertEquals(0, transactionTracker.getNumUserTransEvents(), "all events were stale");
        assertEquals(
                round.get(),
                transactionTracker.getLastRoundReceivedAllTransCons(),
                "there were no non-consensus transactions left at the end of the round");

        transactionTracker.eventAdded(event);
        transactionTracker.reset();
        assertEquals(0, transactionTracker.getNumUserTransEvents(), "reset should revert back to 0");
    }
}
