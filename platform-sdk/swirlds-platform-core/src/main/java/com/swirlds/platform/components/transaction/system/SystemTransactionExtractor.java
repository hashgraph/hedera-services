package com.swirlds.platform.components.transaction.system;

import com.swirlds.platform.event.GossipEvent;
import com.swirlds.platform.system.transaction.StateSignatureTransaction;
import com.swirlds.platform.system.transaction.SystemTransaction;
import com.swirlds.platform.system.transaction.Transaction;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * A simple utility for extracting and filtering system transactions from events.
 */
public final class SystemTransactionExtractor {

    private SystemTransactionExtractor() {
    }

    /**
     * Extract all system transactions from the given event.
     *
     * @param event the event to extract system transactions from
     * @return the system transactions contained within the event
     */
    @NonNull
    public static List<SystemTransaction> getSystemTransactions(@NonNull final GossipEvent event) {
        final List<SystemTransaction> transactions = new ArrayList<>();

        for (final Transaction transaction : event.getHashedData().getTransactions()) {
            if (transaction instanceof final SystemTransaction systemTransaction) {
                transactions.add(systemTransaction);
            }
        }

        return transactions;
    }

    /**
     * Filter system transactions for state signature transactions.
     *
     * @param transaction the transaction to filter
     * @return the state signature transaction, or null if the transaction is not a state signature transaction
     */
    @Nullable
    public static StateSignatureTransaction stateSignatureTransactionFilter(
            @NonNull final SystemTransaction transaction) {
        if (transaction instanceof StateSignatureTransaction) {
            return (StateSignatureTransaction) transaction;
        }
        return null;
    }
}
