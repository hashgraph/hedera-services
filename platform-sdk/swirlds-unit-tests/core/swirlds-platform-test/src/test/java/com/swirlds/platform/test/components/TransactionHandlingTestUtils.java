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

package com.swirlds.platform.test.components;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.swirlds.common.crypto.CryptographyHolder;
import com.swirlds.common.platform.NodeId;
import com.swirlds.common.test.fixtures.DummySystemTransaction;
import com.swirlds.platform.internal.ConsensusRound;
import com.swirlds.platform.internal.EventImpl;
import com.swirlds.platform.system.BasicSoftwareVersion;
import com.swirlds.platform.system.events.BaseEventHashedData;
import com.swirlds.platform.system.events.BaseEventUnhashedData;
import com.swirlds.platform.system.events.EventConstants;
import com.swirlds.platform.system.events.EventDescriptor;
import com.swirlds.platform.system.transaction.ConsensusTransactionImpl;
import com.swirlds.platform.test.fixtures.event.EventImplTestUtils;
import com.swirlds.platform.test.fixtures.event.TestingEventBuilder;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Utility functions for testing system transaction handling
 */
public final class TransactionHandlingTestUtils {
    private TransactionHandlingTestUtils() {}

    /**
     * Generate a new bare-bones consensus round, containing DummySystemTransactions
     *
     * @param eventCount           the number of events to include in the round
     * @param transactionsPerEvent the number of transactions to include in each event
     * @return a bare-bones consensus round
     */
    public static ConsensusRound newDummyRound(final Random random, final int eventCount, final int transactionsPerEvent) {
        final ConsensusRound round = mock(ConsensusRound.class);

        final List<EventImpl> events = new ArrayList<>();
        for (int index = 0; index < eventCount; index++) {
            events.add(EventImplTestUtils.createEventImpl(
                    new TestingEventBuilder(random)
                            .setSystemTransactionCount(transactionsPerEvent),
                            null, null)
                    );
        }

        when(round.getConsensusEvents()).thenReturn(events);

        return round;
    }
}
