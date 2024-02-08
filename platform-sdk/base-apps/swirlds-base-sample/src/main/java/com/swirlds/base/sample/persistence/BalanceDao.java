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

import com.swirlds.base.sample.domain.Balance;
import com.swirlds.base.sample.domain.Wallet;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * in-memory simple data layer for Balance
 */
public class BalanceDao {

    private static class InstanceHolder {
        private static final BalanceDao INSTANCE = new BalanceDao();
    }

    public static @NonNull BalanceDao getInstance() {
        return InstanceHolder.INSTANCE;
    }

    private static final Map<String, InternalBalance> BALANCE_REPOSITORY = new ConcurrentHashMap<>();

    /**
     * Saves or updates the object
     */
    public @NonNull Balance saveOrUpdate(final @NonNull Balance balance) {
        BALANCE_REPOSITORY.computeIfPresent(balance.wallet().address(), (s, b) -> {
            b.lock.writeLock().lock();
            if (b.value.compareAndSet(b.value.get(), balance.amount())) {
                b.version.incrementAndGet();
            }
            b.lock.writeLock().unlock();
            return b;
        });
        BALANCE_REPOSITORY.putIfAbsent(
                balance.wallet().address(),
                new InternalBalance(
                        new AtomicReference<>(balance.amount()),
                        new AtomicLong(0),
                        balance.wallet(),
                        new ReentrantReadWriteLock()));
        return balance;
    }

    public @Nullable Balance findById(final @NonNull String address) {
        final InternalBalance internalBalance = BALANCE_REPOSITORY.get(address);
        return internalBalance == null ? null : new Balance(internalBalance.wallet, internalBalance.value.get());
    }

    /**
     * Retrieves the object with a lock for update. No other write operations can be performed against the object until
     * released.
     */
    public @Nullable Balance findWithBlockForUpdate(final @NonNull String address) {
        final InternalBalance iBalance = BALANCE_REPOSITORY.get(address);
        if (iBalance == null) {
            return null;
        }
        iBalance.lock.writeLock().lock();
        return new Balance(iBalance.wallet, iBalance.value.get());
    }

    /**
     * Release the write lock. Must be hold by the thread.
     */
    public void release(final @NonNull String address) {
        final InternalBalance iBalance = BALANCE_REPOSITORY.get(address);
        if (iBalance != null) {
            iBalance.lock.writeLock().unlock();
        }
    }

    private record InternalBalance(
            AtomicReference<BigDecimal> value, AtomicLong version, Wallet wallet, ReentrantReadWriteLock lock) {}
}
