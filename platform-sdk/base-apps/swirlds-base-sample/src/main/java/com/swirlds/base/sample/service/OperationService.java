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

package com.swirlds.base.sample.service;

import static java.util.Objects.isNull;

import com.google.common.base.Preconditions;
import com.swirlds.base.sample.domain.Inventory;
import com.swirlds.base.sample.domain.Operation;
import com.swirlds.base.sample.domain.Operation.OperationDetail;
import com.swirlds.base.sample.domain.OperationType;
import com.swirlds.base.sample.domain.Stock;
import com.swirlds.base.sample.domain.StockHandlingMode;
import com.swirlds.base.sample.metrics.ApplicationMetrics;
import com.swirlds.base.sample.persistence.InventoryDao;
import com.swirlds.base.sample.persistence.OperationDao;
import com.swirlds.base.sample.persistence.OperationDao.Criteria;
import com.swirlds.base.sample.persistence.StockDao;
import com.swirlds.common.context.PlatformContext;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Creates Operations
 */
public class OperationService extends CrudService<Operation> {
    private static final Logger log = LogManager.getLogger(OperationService.class);
    private final @NonNull OperationDao operationDao;
    private final @NonNull InventoryDao inventoryDao;
    private final @NonNull StockDao stockDao;
    private final @NonNull PlatformContext context;

    public OperationService(@NonNull final PlatformContext context) {
        super(Operation.class);
        this.context = Objects.requireNonNull(context, "context cannot be null");
        this.operationDao = OperationDao.getInstance();
        this.stockDao = StockDao.getInstance();
        this.inventoryDao = InventoryDao.getInstance();
    }

    @NonNull
    @Override
    public Operation create(@NonNull final Operation operation) {
        final long startNano = System.nanoTime();
        Objects.requireNonNull(operation, "operation cannot be null");
        Preconditions.checkArgument(operation.type() != null, "Operation#type cannot be null");
        Preconditions.checkArgument(operation.details() != null, "Operation#details cannot be null");
        Preconditions.checkArgument(!operation.details().isEmpty(), "Operation#details cannot be empty");

        operation.details().forEach(d -> {
            Preconditions.checkArgument(d.itemId() != null, "OperationDetails#itemId cannot be null");
            Preconditions.checkArgument(d.amount() != null, "OperationDetails#amount cannot be null");
            Preconditions.checkArgument(d.amount() >= 0, "OperationDetails#amount cannot be less than or equals to 0");
        });

        Map<String, Inventory> existences = new HashMap<>(operation.details().size());
        Map<String, Integer> operationAmount = operation.details().stream()
                .collect(Collectors.toMap(OperationDetail::itemId, OperationDetail::amount));
        for (OperationDetail od : operation.details()) {
            existences.put(od.itemId(), inventoryDao.findWithBlockForUpdate(od.itemId())); // Locks the stock for update
        }

        final String notFoundItem = existences.entrySet().stream()
                .filter(e -> e.getValue() == null)
                .map(Entry::getKey)
                .findFirst()
                .orElse(null);

        final Map<String, Integer> updatedAmounts = existences.entrySet().stream()
                .filter(e -> e.getValue() != null)
                .collect(Collectors.toMap(
                        Entry::getKey,
                        e -> operation.type().apply(e.getValue().amount(), operationAmount.get(e.getKey()))));

        final String notEnoughStockItem = updatedAmounts.entrySet().stream()
                .filter(e -> e.getValue() < 0)
                .map(Entry::getKey)
                .findFirst()
                .orElse(null);

        if (Objects.nonNull(notFoundItem)) {
            existences.entrySet().stream()
                    .filter(e -> e.getValue() != null)
                    .map(Entry::getKey)
                    .forEach(inventoryDao::release);
            log.error("item not found:{}", notFoundItem);
            throw new IllegalArgumentException("Item " + notFoundItem + " not found in the system");
        }
        if (Objects.nonNull(notEnoughStockItem)) {
            existences.entrySet().stream()
                    .filter(e -> e.getValue() != null)
                    .map(Entry::getKey)
                    .forEach(inventoryDao::release);
            log.error("item with not enough stock: {}", notEnoughStockItem);
            throw new IllegalArgumentException("Item " + notEnoughStockItem + " has not enough stock");
        }

        Operation saved = operationDao.save(operation);
        updatedAmounts.forEach((k, v) -> inventoryDao.saveOrUpdate(new Inventory(k, v)));

        if (saved.type() == OperationType.ADDITION) {
            operation.details().forEach(d -> {
                stockDao.save(
                        new Stock(d.itemId(), d.unitaryPrice(), saved.timestamp(), new AtomicInteger(d.amount())));
            });
        } else if (saved.type() == OperationType.DEDUCTION) {
            operation.details().forEach(d -> {
                int amount = d.amount();
                for (Stock s : stockDao.getAllWithRemaining(d.itemId()).stream()
                        .sorted(getComparator(d))
                        .toList()) {

                    final int value = s.remaining().get() - d.amount();
                    if (value >= 0) {
                        s.remaining().getAndSet(value);
                        amount -= value;
                    } else {
                        amount -= s.remaining().getAndSet(0);
                    }

                    if (amount <= 0) {
                        break;
                    }
                }
            });
        }
        existences.keySet().forEach(inventoryDao::release);

        final Duration duration = Duration.of(System.nanoTime() - startNano, ChronoUnit.NANOS);
        context.getMetrics().getOrCreate(ApplicationMetrics.OPERATION_TIME).set(duration);
        context.getMetrics().getOrCreate(ApplicationMetrics.OPERATION_TOTAL).increment();
        log.debug("Executed operation {} in {}", saved, duration);
        return saved;
    }

    private @NonNull Comparator<? super Stock> getComparator(final @Nullable OperationDetail detail) {
        if (isNull(detail) || StockHandlingMode.FIFO == detail.mode()) {
            return Comparator.comparing(Stock::timestamp);
        } else {
            return Comparator.comparing(Stock::timestamp).reversed();
        }
    }

    @NonNull
    @Override
    public List<Operation> retrieveAll(@NonNull final Map<String, String> params) {
        Objects.requireNonNull(params, "params must not be null");
        final Criteria criteria;
        try {
            criteria = Criteria.fromMap(params);
        } catch (Exception e) {
            throw new IllegalArgumentException("Error parsing parameters for search" + params);
        }
        return operationDao.findByCriteria(criteria);
    }
}
