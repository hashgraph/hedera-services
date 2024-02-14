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

import com.swirlds.base.sample.domain.Stock;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * in-memory simple data layer for Stock
 */
public class StockDao {

    private static class InstanceHolder {
        private static final StockDao INSTANCE = new StockDao();
    }

    public static @NonNull StockDao getInstance() {
        return InstanceHolder.INSTANCE;
    }

    private static final Map<String, ConcurrentLinkedQueue<Stock>> STOCKS = new ConcurrentHashMap<>();

    public @NonNull Stock save(final @NonNull Stock operation) {
        STOCKS.computeIfPresent(operation.itemId(), (k, s) -> {
            s.add(operation);
            return s;
        });
        STOCKS.putIfAbsent(operation.itemId(), new ConcurrentLinkedQueue<>(List.of(operation)));
        return operation;
    }

    public @NonNull List<Stock> getAll(final @NonNull String itemId) {
        final ConcurrentLinkedQueue<Stock> stocks = STOCKS.get(itemId);
        if (stocks == null) {
            return List.of();
        }
        return stocks.stream().sorted(Comparator.comparing(Stock::timestamp)).toList();
    }

    public @NonNull List<Stock> getAllWithRemaining(final @NonNull String itemId) {
        final ConcurrentLinkedQueue<Stock> stocks = STOCKS.get(itemId);
        if (stocks == null) {
            return List.of();
        }
        return stocks.stream()
                .filter(s -> s.remaining().get() > 0)
                .sorted(Comparator.comparing(Stock::timestamp))
                .toList();
    }

    public void deleteByItemId(final String itemId) {
        STOCKS.remove(itemId);
    }
}
