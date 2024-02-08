/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.swirlds.base.sample.persistence;

import com.google.common.base.Predicates;
import com.swirlds.base.sample.domain.Transaction;
import com.swirlds.base.sample.internal.DataTransferUtils;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import org.apache.logging.log4j.util.Strings;

/**
 * in-memory simple data layer for Transactions
 */
public class TransactionDao {

    private static class InstanceHolder {
        private static final TransactionDao INSTANCE = new TransactionDao();
    }

    public static @NonNull TransactionDao getInstance() {
        return InstanceHolder.INSTANCE;
    }

    private static final Map<String, Transaction> TRANSACTION_REPOSITORY = new ConcurrentHashMap<>();

    public @NonNull Transaction save(final @NonNull Transaction transaction) {

        if (TRANSACTION_REPOSITORY.containsKey(transaction.uuid())) {
            throw new IllegalArgumentException("Non modifiable resource");
        }
        TRANSACTION_REPOSITORY.put(transaction.uuid(), transaction);
        return transaction;
    }

    /**
     * Search using a criteria
     */
    public @NonNull List<Transaction> findByCriteria(final @NonNull Criteria criteria) {
        return TRANSACTION_REPOSITORY.values().stream()
                .filter(criteria.toSearchPredicate())
                .toList();
    }

    /**
     * Delete the transaction
     */
    public void delete(final String uuid) {
        TRANSACTION_REPOSITORY.remove(uuid);
    }

    /**
     * Searching criteria
     */
    public record Criteria(
            String uuid,
            String addressFrom,
            String addressTo,
            Date from,
            Date to,
            BigDecimal amountFrom,
            BigDecimal amountTo) {

        /**
         * Create a searching criteria based on a map containing values
         */
        public static @NonNull Criteria fromMap(final @NonNull Map<String, String> parameters) {
            String uuid = parameters.get("uuid");
            String addressFrom = parameters.get("addressFrom");
            String addressTo = parameters.get("addressTo");
            Date from = parameters.get("from") != null ? DataTransferUtils.parseDate(parameters.get("from")) : null;
            Date to = parameters.get("to") != null ? DataTransferUtils.parseDate(parameters.get("to")) : null;
            BigDecimal amountFrom =
                    parameters.get("amountFrom") != null ? new BigDecimal(parameters.get("amountFrom")) : null;
            BigDecimal amountTo =
                    parameters.get("amountTo") != null ? new BigDecimal(parameters.get("amountTo")) : null;

            return new Criteria(uuid, addressFrom, addressTo, from, to, amountFrom, amountTo);
        }

        /**
         * @return a predicate to perform the search
         */
        private Predicate<Transaction> toSearchPredicate() {
            if (Strings.isNotBlank(uuid)) {
                return (t) -> t.uuid().equals(uuid);
            }

            Predicate<Transaction> result = Predicates.alwaysTrue();
            if (Strings.isNotBlank(addressFrom)) {
                result = result.and(t -> t.from().equals(addressFrom));
            }
            if (Strings.isNotBlank(addressTo)) {
                result = result.and(t -> t.to().equals(addressTo));
            }
            if (Objects.nonNull(from)) {
                result = result.and(t -> new Date(t.timestamp()).after(from));
            }
            if (Objects.nonNull(to)) {
                result = result.and(t -> new Date(t.timestamp()).before(to));
            }
            if (Objects.nonNull(amountFrom)) {
                result = result.and(t -> amountFrom.compareTo(t.amount()) <= 0);
            }
            if (Objects.nonNull(amountTo)) {
                result = result.and(t -> amountTo.compareTo(t.amount()) >= 0);
            }

            return result;
        }
    }
}
