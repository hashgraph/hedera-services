/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

import com.swirlds.common.crypto.CryptographyHolder;
import com.swirlds.common.system.BasicSoftwareVersion;
import com.swirlds.common.system.NodeId;
import com.swirlds.common.system.events.BaseEventHashedData;
import com.swirlds.common.system.events.BaseEventUnhashedData;
import com.swirlds.common.system.transaction.internal.SystemTransaction;
import com.swirlds.common.test.fixtures.DummySystemTransaction;
import com.swirlds.platform.consensus.ConsensusSnapshot;
import com.swirlds.platform.consensus.GraphGenerations;
import com.swirlds.platform.internal.ConsensusRound;
import com.swirlds.platform.internal.EventImpl;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Utility functions for testing system transaction handling
 */
public final class TransactionHandlingTestUtils {
    /**
     * Generate a new bare-bones event, containing DummySystemTransactions
     *
     * @param transactionCount the number of transactions to include in the event
     * @return the new event
     */
    public static EventImpl newDummyEvent(final int transactionCount) {
        SystemTransaction[] transactions = new SystemTransaction[transactionCount];

        for (int index = 0; index < transactionCount; index++) {
            transactions[index] = new DummySystemTransaction();
        }

        return new EventImpl(
                new BaseEventHashedData(
                        new BasicSoftwareVersion(1),
                        new NodeId(0),
                        0L,
                        0L,
                        CryptographyHolder.get().getNullHash(),
                        CryptographyHolder.get().getNullHash(),
                        Instant.now(),
                        transactions),
                new BaseEventUnhashedData(new NodeId(0L), new byte[0]));
    }

    /**
     * Generates a new round, with specified number of events, containing DummySystemTransactions
     *
     * @param roundContents a list of integers, where each list element results in an event being added the output
     *                      round, and the element value specifies number of transactions to include in the event
     * @return a new round, with specified contents
     */
    public static ConsensusRound newDummyRound(final List<Integer> roundContents) {
        final List<EventImpl> events = new ArrayList<>();
        for (Integer transactionCount : roundContents) {
            events.add(newDummyEvent(transactionCount));
        }

        return new ConsensusRound(
                events, mock(EventImpl.class), mock(GraphGenerations.class), mock(ConsensusSnapshot.class));
    }
}
