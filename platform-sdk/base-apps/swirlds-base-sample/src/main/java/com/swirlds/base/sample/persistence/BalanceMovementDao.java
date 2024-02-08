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

import com.swirlds.base.sample.domain.BalanceMovement;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * in-memory simple data layer for BalanceUpdates
 */
public class BalanceMovementDao {

    private static class InstanceHolder {
        private static final BalanceMovementDao INSTANCE = new BalanceMovementDao();
    }

    public static @NonNull BalanceMovementDao getInstance() {
        return InstanceHolder.INSTANCE;
    }

    private static final Map<String, ConcurrentLinkedQueue<BalanceMovement>> BALANCE_MOVEMENTS_REPOSITORY =
            new ConcurrentHashMap<>();

    public @NonNull BalanceMovement save(final @NonNull BalanceMovement operation) {
        BALANCE_MOVEMENTS_REPOSITORY.computeIfPresent(operation.wallet().address(), (k, s) -> {
            s.add(operation);
            return s;
        });
        BALANCE_MOVEMENTS_REPOSITORY.putIfAbsent(
                operation.wallet().address(), new ConcurrentLinkedQueue<>(List.of(operation)));
        return operation;
    }

    public void deleteAllWith(
            final @NonNull String address, final @NonNull String transactionUUID, final long timestamp) {
        BALANCE_MOVEMENTS_REPOSITORY
                .get(address)
                .removeIf(bu ->
                        bu.timestamp().equals(timestamp) && bu.transactionUUID().equals(transactionUUID));
    }

    public @NonNull List<BalanceMovement> getAll(final @NonNull String address) {
        final ConcurrentLinkedQueue<BalanceMovement> movements = BALANCE_MOVEMENTS_REPOSITORY.get(address);
        if (movements == null) {
            return List.of();
        }
        return movements.stream()
                .sorted(Comparator.comparing(BalanceMovement::timestamp))
                .toList();
    }
}
