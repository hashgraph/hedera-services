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

package com.swirlds.platform.components.transaction.system;

import static java.util.stream.Collectors.collectingAndThen;
import static java.util.stream.Collectors.toList;

import com.swirlds.platform.internal.ConsensusRound;
import com.swirlds.platform.system.events.BaseEvent;
import com.swirlds.platform.system.transaction.SystemTransaction;
import com.swirlds.platform.system.transaction.Transaction;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Extracts a particular type of system transaction from an event or a round.
 */
public class SystemTransactionExtractor<T extends SystemTransaction> {
    /** the system transaction type to extract */
    private final Class<T> systemTransactionType;

    /**
     * Constructs a new extractor for the given system transaction type.
     *
     * @param systemTransactionType
     * 		the system transaction type to extract
     */
    public SystemTransactionExtractor(@NonNull final Class<T> systemTransactionType) {
        this.systemTransactionType = Objects.requireNonNull(systemTransactionType);
    }

    /**
     * Extracts the system transactions from the given round.
     *
     * @param round
     * 		the round to extract from
     * @return the extracted system transactions, or {@code null} if there are none
     */
    public @Nullable List<ScopedSystemTransaction<T>> handleRound(@NonNull final ConsensusRound round) {
        return round.getConsensusEvents().stream()
                .map(this::handleEvent)
                .filter(Objects::nonNull)
                .flatMap(List::stream)
                .collect(collectingAndThen(toList(), l -> l.isEmpty() ? null : l));
    }

    /**
     * Extracts the system transactions from the given event.
     *
     * @param event
     * 		the event to extract from
     * @return the extracted system transactions, or {@code null} if there are none
     */
    @SuppressWarnings("unchecked")
    public @Nullable List<ScopedSystemTransaction<T>> handleEvent(@NonNull final BaseEvent event) {
        // no transactions to transform
        final var transactions = event.getHashedData().getTransactions();
        if (transactions == null) {
            return null;
        }

        final List<ScopedSystemTransaction<T>> scopedTransactions = new ArrayList<>();

        for (final Transaction transaction : event.getHashedData().getTransactions()) {
            if (systemTransactionType.isInstance(transaction)) {
                scopedTransactions.add(new ScopedSystemTransaction<>(
                        event.getHashedData().getCreatorId(),
                        event.getHashedData().getSoftwareVersion(),
                        (T) transaction));
            }
        }
        return scopedTransactions.isEmpty() ? null : scopedTransactions;
    }
}
