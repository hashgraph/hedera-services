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
 * Contains utility methods for extracting a particular type of system transaction from an event or a round.
 */
public class SystemTransactionExtractionUtils {
    /**
     * Hidden constructor.
     */
    private SystemTransactionExtractionUtils() {}

    /**
     * Extracts system transactions of a given type from a round.
     *
     * @param round                      the round to extract from
     * @param systemTransactionTypeClass the class of system transaction to extract
     * @param <T>                        the type of system transaction to extract
     * @return the extracted system transactions, or {@code null} if there are none
     */
    public static @Nullable <T extends SystemTransaction> List<ScopedSystemTransaction<T>> extractFromRound(
            @NonNull final ConsensusRound round, @NonNull final Class<T> systemTransactionTypeClass) {

        return round.getConsensusEvents().stream()
                .map(event -> extractFromEvent(event, systemTransactionTypeClass))
                .filter(Objects::nonNull)
                .flatMap(List::stream)
                .collect(collectingAndThen(toList(), list -> list.isEmpty() ? null : list));
    }

    /**
     * Extracts system transactions of a given type from an event.
     *
     * @param event                      the event to extract from
     * @param systemTransactionTypeClass the class of system transaction to extract
     * @param <T>                        the type of system transaction to extract
     * @return the extracted system transactions, or {@code null} if there are none
     */
    @SuppressWarnings("unchecked")
    public static @Nullable <T extends SystemTransaction> List<ScopedSystemTransaction<T>> extractFromEvent(
            @NonNull final BaseEvent event, @NonNull final Class<T> systemTransactionTypeClass) {

        final var transactions = event.getHashedData().getTransactions();
        if (transactions == null) {
            return null;
        }

        final List<ScopedSystemTransaction<T>> scopedTransactions = new ArrayList<>();

        for (final Transaction transaction : event.getHashedData().getTransactions()) {
            if (systemTransactionTypeClass.isInstance(transaction)) {
                scopedTransactions.add(new ScopedSystemTransaction<>(
                        event.getHashedData().getCreatorId(),
                        event.getHashedData().getSoftwareVersion(),
                        (T) transaction));
            }
        }
        return scopedTransactions.isEmpty() ? null : scopedTransactions;
    }
}
