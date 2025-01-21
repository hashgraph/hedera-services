/*
 * Copyright (C) 2023-2025 Hedera Hashgraph, LLC
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

import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.hapi.platform.event.StateSignatureTransaction;
import com.hedera.pbj.runtime.ParseException;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.platform.event.PlatformEvent;
import com.swirlds.platform.internal.ConsensusRound;
import com.swirlds.platform.system.transaction.Transaction;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Contains utility methods for extracting a particular type of system transaction from an event or a round.
 */
public class SystemTransactionExtractionUtils {
    private static final Logger logger = LogManager.getLogger(SystemTransactionExtractionUtils.class);

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
    public static @Nullable <T> List<ScopedSystemTransaction<T>> extractFromRound(
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
    public static @Nullable <T> List<ScopedSystemTransaction<T>> extractFromEvent(
            @NonNull final PlatformEvent event, @NonNull final Class<T> systemTransactionTypeClass) {
        final List<ScopedSystemTransaction<T>> scopedTransactions = new ArrayList<>();

        final Iterator<Transaction> transactionIterator = event.transactionIterator();
        while (transactionIterator.hasNext()) {
            final List<Bytes> transactions = event.getGossipEvent().transactions();
            final boolean isNewFormat = !transactions.isEmpty();
            final Transaction transaction = transactionIterator.next();
            if (isNewFormat) {
                final Optional<StateSignatureTransaction> maybeStateSignatureTransaction =
                        extractStateSignatureTransactionFromTransaction(transaction);
                maybeStateSignatureTransaction.ifPresent(stateSignatureTransaction ->
                        consumeSystemTransaction(event, scopedTransactions, (T) stateSignatureTransaction));
            } else if (systemTransactionTypeClass.isInstance(
                    transaction.getTransaction().transaction().value())) {
                consumeSystemTransaction(event, scopedTransactions, (T)
                        transaction.getTransaction().transaction().value());
            }
        }

        return scopedTransactions.isEmpty() ? null : scopedTransactions;
    }

    private static Optional<StateSignatureTransaction> extractStateSignatureTransactionFromTransaction(
            final Transaction transaction) {
        try {
            final com.hedera.hapi.node.base.Transaction parsedTransaction =
                    com.hedera.hapi.node.base.Transaction.PROTOBUF.parse(transaction.getTransactionBytes());
            final Bytes bodyBytes = parsedTransaction.bodyBytes();
            final TransactionBody transactionBody = TransactionBody.PROTOBUF.parseStrict(bodyBytes);
            return Optional.ofNullable(transactionBody.stateSignatureTransaction());
        } catch (final ParseException e) {
            logger.error("Failed to parse StateSignatureTransaction from event", e);
            return Optional.empty();
        }
    }

    private static <T> void consumeSystemTransaction(
            @NonNull final PlatformEvent event,
            @NonNull final List<ScopedSystemTransaction<T>> scopedTransactions,
            final T stateSignatureTransaction) {
        scopedTransactions.add(new ScopedSystemTransaction<>(
                event.getCreatorId(), event.getSoftwareVersion(), stateSignatureTransaction));
    }
}
