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

import com.google.common.collect.ImmutableMap;
import com.swirlds.base.sample.domain.Transaction;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class TransactionDao {

    private static class InstanceHolder {
        private static final TransactionDao INSTANCE = new TransactionDao();
    }

    public static @NonNull TransactionDao getInstance() {
        return InstanceHolder.INSTANCE;
    }

    private static final Map<String, Map<String, Transaction>> TRANSACTION_REPOSITORY = new ConcurrentHashMap<>();

    public Transaction save(final @NonNull Transaction transaction) {
        TRANSACTION_REPOSITORY.computeIfPresent(transaction.from(), (a, b) -> {
            if (b.containsKey(transaction.id())) {
                throw new IllegalArgumentException("Non modifiable resource");
            }

            b.put(transaction.id(), transaction);
            return b;
        });

        TRANSACTION_REPOSITORY.putIfAbsent(
                transaction.from(), new ConcurrentHashMap<>(ImmutableMap.of(transaction.id(), transaction)));
        return transaction;
    }

    public List<Transaction> findByWalletId(final @NonNull String id) {
        return TRANSACTION_REPOSITORY.getOrDefault(id, ImmutableMap.of()).values().stream()
                .toList();
    }

    public void delete(final @NonNull Transaction transaction) {
        if (!TRANSACTION_REPOSITORY.containsKey(transaction.from())) {
            throw new IllegalArgumentException("Resource does not exist");
        }

        TRANSACTION_REPOSITORY.computeIfPresent(transaction.from(), (a, b) -> {
            if (!b.containsKey(transaction.id())) {
                throw new IllegalArgumentException("Resource does not exist");
            }
            b.remove(transaction.id());
            return b;
        });
    }
}
