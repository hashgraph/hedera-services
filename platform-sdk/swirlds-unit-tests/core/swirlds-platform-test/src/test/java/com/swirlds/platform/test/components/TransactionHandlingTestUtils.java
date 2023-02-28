package com.swirlds.platform.test.components;

import static org.mockito.Mockito.mock;

import com.swirlds.common.crypto.CryptographyHolder;
import com.swirlds.common.system.events.BaseEventHashedData;
import com.swirlds.common.system.events.BaseEventUnhashedData;
import com.swirlds.common.system.transaction.internal.SystemTransaction;
import com.swirlds.common.test.DummySystemTransaction;
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
                        0,
                        0L,
                        0L,
                        CryptographyHolder.get().getNullHash(),
                        CryptographyHolder.get().getNullHash(),
                        Instant.now(),
                        transactions),
                new BaseEventUnhashedData(0L, new byte[0]));
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

        return new ConsensusRound(events, mock(GraphGenerations.class));
    }
}
