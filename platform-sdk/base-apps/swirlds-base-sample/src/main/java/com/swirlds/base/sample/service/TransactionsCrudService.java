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

import com.google.common.base.Preconditions;
import com.swirlds.base.sample.domain.Balance;
import com.swirlds.base.sample.domain.BalanceMovement;
import com.swirlds.base.sample.domain.Transaction;
import com.swirlds.base.sample.metrics.ApplicationMetrics;
import com.swirlds.base.sample.persistence.BalanceDao;
import com.swirlds.base.sample.persistence.BalanceMovementDao;
import com.swirlds.base.sample.persistence.TransactionDao;
import com.swirlds.base.sample.persistence.TransactionDao.Criteria;
import com.swirlds.common.context.PlatformContext;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Controls transactions operations
 */
public class TransactionsCrudService extends CrudService<Transaction> {
    private static final Logger log = LogManager.getLogger(TransactionsCrudService.class);
    private final @NonNull TransactionDao transactionDao;
    private final @NonNull BalanceDao balanceDao;
    private final @NonNull BalanceMovementDao balanceMovementDao;
    private final @NonNull PlatformContext context;

    public TransactionsCrudService(@NonNull final PlatformContext context) {
        super(Transaction.class);
        this.context = Objects.requireNonNull(context, "transaction cannot be null");
        this.transactionDao = TransactionDao.getInstance();
        this.balanceDao = BalanceDao.getInstance();
        this.balanceMovementDao = BalanceMovementDao.getInstance();
    }

    @NonNull
    @Override
    public Transaction create(@NonNull final Transaction transaction) {
        final long timestamp = System.currentTimeMillis();
        final long startNano = System.nanoTime();
        Objects.requireNonNull(transaction, "transaction cannot be null");
        Preconditions.checkArgument(transaction.from() != null, "transaction#from cannot be null");
        Preconditions.checkArgument(transaction.to() != null, "transaction#to cannot be null");
        Preconditions.checkArgument(transaction.amount() != null, "transaction#amount cannot be null");

        final Balance fromBalance = balanceDao.findWithBlockForUpdate(transaction.from());
        try {
            Preconditions.checkArgument(fromBalance != null, "origin wallet not found");
            Preconditions.checkArgument(
                    fromBalance.amount().compareTo(transaction.amount()) >= 0,
                    "Not enough balance in originating account");
        } catch (IllegalArgumentException e) {
            balanceDao.release(transaction.from());
            context.getMetrics()
                    .getOrCreate(ApplicationMetrics.TRANSACTION_TIME)
                    .set(Duration.of(System.nanoTime() - startNano, ChronoUnit.NANOS));
            throw e;
        }

        final Balance toBalance = balanceDao.findWithBlockForUpdate(transaction.to());
        try {
            Preconditions.checkArgument(toBalance != null, "destination wallet not found");
        } catch (IllegalArgumentException e) {
            balanceDao.release(transaction.from());
            balanceDao.release(transaction.to());
            context.getMetrics()
                    .getOrCreate(ApplicationMetrics.TRANSACTION_TIME)
                    .set(Duration.of(System.nanoTime() - startNano, ChronoUnit.NANOS));
            throw e;
        }

        final String uuid = UUID.randomUUID().toString();

        try {
            balanceMovementDao.save(new BalanceMovement(
                    uuid, timestamp, fromBalance.wallet(), transaction.amount().negate()));
            balanceMovementDao.save(new BalanceMovement(uuid, timestamp, toBalance.wallet(), transaction.amount()));
            final Transaction save = transactionDao.save(
                    new Transaction(uuid, transaction.from(), transaction.to(), transaction.amount(), timestamp));
            balanceDao.saveOrUpdate(fromBalance);
            balanceDao.saveOrUpdate(toBalance);
            context.getMetrics()
                    .getOrCreate(ApplicationMetrics.TRANSACTION_COUNT)
                    .increment();
            return save;
        } catch (Exception e) {
            balanceMovementDao.deleteAllWith(transaction.from(), uuid, timestamp);
            balanceMovementDao.deleteAllWith(transaction.to(), uuid, timestamp);
            transactionDao.delete(uuid);
            log.error("unexpected error applying transaction", e);
            throw new RuntimeException("unexpected error applying transaction");
        } finally {
            balanceDao.release(transaction.from());
            balanceDao.release(transaction.to());
            context.getMetrics()
                    .getOrCreate(ApplicationMetrics.TRANSACTION_TIME)
                    .set(Duration.of(System.nanoTime() - startNano, ChronoUnit.NANOS));
        }
    }

    @NonNull
    @Override
    public List<Transaction> retrieveAll(@NonNull final Map<String, String> params) {
        Objects.requireNonNull(params, "params must not be null");
        final Criteria criteria;
        try {
            criteria = Criteria.fromMap(params);
        } catch (Exception e) {
            throw new IllegalArgumentException("Error parsing parameters for search" + params);
        }
        return transactionDao.findByCriteria(criteria);
    }
}
