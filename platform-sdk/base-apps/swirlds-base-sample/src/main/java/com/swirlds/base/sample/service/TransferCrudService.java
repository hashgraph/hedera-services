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
import com.swirlds.base.sample.domain.Transfer;
import com.swirlds.base.sample.metrics.ApplicationMetrics;
import com.swirlds.base.sample.persistence.BalanceDao;
import com.swirlds.base.sample.persistence.BalanceMovementDao;
import com.swirlds.base.sample.persistence.TransferDao;
import com.swirlds.base.sample.persistence.TransferDao.Criteria;
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
public class TransferCrudService extends CrudService<Transfer> {
    private static final Logger log = LogManager.getLogger(TransferCrudService.class);
    private final @NonNull TransferDao transferDao;
    private final @NonNull BalanceDao balanceDao;
    private final @NonNull BalanceMovementDao balanceMovementDao;
    private final @NonNull PlatformContext context;

    public TransferCrudService(@NonNull final PlatformContext context) {
        super(Transfer.class);
        this.context = Objects.requireNonNull(context, "transaction cannot be null");
        this.transferDao = TransferDao.getInstance();
        this.balanceDao = BalanceDao.getInstance();
        this.balanceMovementDao = BalanceMovementDao.getInstance();
    }

    @NonNull
    @Override
    public Transfer create(@NonNull final Transfer transfer) {
        final long timestamp = System.currentTimeMillis();
        final long startNano = System.nanoTime();
        Objects.requireNonNull(transfer, "transaction cannot be null");
        Preconditions.checkArgument(transfer.from() != null, "transfer#from cannot be null");
        Preconditions.checkArgument(transfer.to() != null, "transfer#to cannot be null");
        Preconditions.checkArgument(transfer.amount() != null, "transfer#amount cannot be null");

        final Balance fromBalance = balanceDao.findWithBlockForUpdate(transfer.from());
        try {
            Preconditions.checkArgument(fromBalance != null, "origin wallet not found");
            Preconditions.checkArgument(
                    fromBalance.amount().compareTo(transfer.amount()) >= 0,
                    "Not enough balance in originating account");
        } catch (IllegalArgumentException e) {
            balanceDao.release(transfer.from());
            context.getMetrics()
                    .getOrCreate(ApplicationMetrics.TRANSFER_TIME)
                    .set(Duration.of(System.nanoTime() - startNano, ChronoUnit.NANOS));
            throw e;
        }

        final Balance toBalance = balanceDao.findWithBlockForUpdate(transfer.to());
        try {
            Preconditions.checkArgument(toBalance != null, "destination wallet not found");
        } catch (IllegalArgumentException e) {
            balanceDao.release(transfer.from());
            balanceDao.release(transfer.to());
            context.getMetrics()
                    .getOrCreate(ApplicationMetrics.TRANSFER_TIME)
                    .set(Duration.of(System.nanoTime() - startNano, ChronoUnit.NANOS));
            throw e;
        }

        final String uuid = UUID.randomUUID().toString();

        try {
            balanceMovementDao.save(new BalanceMovement(
                    uuid, timestamp, fromBalance.wallet(), transfer.amount().negate()));
            balanceMovementDao.save(new BalanceMovement(uuid, timestamp, toBalance.wallet(), transfer.amount()));
            final Transfer save = transferDao.save(
                    new Transfer(uuid, transfer.from(), transfer.to(), transfer.amount(), timestamp));
            balanceDao.saveOrUpdate(new Balance(fromBalance.wallet(), fromBalance.amount().subtract(transfer.amount()) ));
            balanceDao.saveOrUpdate(new Balance(toBalance.wallet(), toBalance.amount().add(transfer.amount()) ));
            context.getMetrics()
                    .getOrCreate(ApplicationMetrics.TRANSFERS_COUNT)
                    .increment();
            return save;
        } catch (Exception e) {
            balanceMovementDao.deleteAllWith(transfer.from(), uuid, timestamp);
            balanceMovementDao.deleteAllWith(transfer.to(), uuid, timestamp);
            transferDao.delete(uuid);
            log.error("unexpected error applying transfer", e);
            throw new RuntimeException("unexpected error applying transfer");
        } finally {
            balanceDao.release(transfer.from());
            balanceDao.release(transfer.to());
            context.getMetrics()
                    .getOrCreate(ApplicationMetrics.TRANSFER_TIME)
                    .set(Duration.of(System.nanoTime() - startNano, ChronoUnit.NANOS));
        }
    }

    @NonNull
    @Override
    public List<Transfer> retrieveAll(@NonNull final Map<String, String> params) {
        Objects.requireNonNull(params, "params must not be null");
        final Criteria criteria;
        try {
            criteria = Criteria.fromMap(params);
        } catch (Exception e) {
            throw new IllegalArgumentException("Error parsing parameters for search" + params);
        }
        return transferDao.findByCriteria(criteria);
    }
}
