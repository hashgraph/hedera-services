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

    private SystemTransactionExtractor() {}

    /**
     * Extract all system transactions from the given event.
     *
     * @param event the event to extract system transactions from
     * @return the system transactions contained within the event
     */
    @Nullable
    public static List<ScopedSystemTransaction<?>> getScopedSystemTransactions(@NonNull final GossipEvent event) {
        final var transactions = event.getHashedData().getTransactions();
        if (transactions == null) {
            return null;
        }

        final List<ScopedSystemTransaction<?>> scopedTransactions = new ArrayList<>();

        for (final Transaction transaction : event.getHashedData().getTransactions()) {
            if (transaction instanceof final SystemTransaction systemTransaction) {
                scopedTransactions.add(
                        new ScopedSystemTransaction<>(event.getHashedData().getCreatorId(), systemTransaction));
            }
        }

        return scopedTransactions;
    }

    /**
     * Filter system transactions for state signature transactions.
     *
     * @param scopedTransaction the transaction to filter and cast
     * @return the state signature transaction, or null if the transaction is not a state signature transaction
     */
    @SuppressWarnings("unchecked")
    @Nullable
    public static ScopedSystemTransaction<StateSignatureTransaction> stateSignatureTransactionFilter(
            @NonNull final ScopedSystemTransaction<?> scopedTransaction) {
        if (scopedTransaction.transaction() instanceof StateSignatureTransaction) {
            return (ScopedSystemTransaction<StateSignatureTransaction>) scopedTransaction;
        }
        return null;
    }
}
