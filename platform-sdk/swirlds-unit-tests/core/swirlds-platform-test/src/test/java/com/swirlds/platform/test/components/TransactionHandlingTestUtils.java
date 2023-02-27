package com.swirlds.platform.test.components;

import com.swirlds.common.crypto.CryptographyHolder;
import com.swirlds.common.system.events.BaseEventHashedData;
import com.swirlds.common.system.events.BaseEventUnhashedData;
import com.swirlds.common.system.transaction.internal.SystemTransaction;
import com.swirlds.common.test.DummySystemTransaction;
import com.swirlds.platform.internal.EventImpl;
import java.time.Instant;

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
}
