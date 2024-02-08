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

import com.swirlds.base.sample.domain.Balance;
import com.swirlds.base.sample.domain.FullBalance;
import com.swirlds.base.sample.domain.FullBalance.Movement;
import com.swirlds.base.sample.domain.Wallet;
import com.swirlds.base.sample.persistence.BalanceDao;
import com.swirlds.base.sample.persistence.BalanceMovementDao;
import com.swirlds.base.sample.persistence.WalletDao;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Controls balance operations
 */
public class FullBalanceCrudService extends CrudService<FullBalance> {
    private final WalletDao walletDao;
    private final BalanceDao balanceDao;
    private final BalanceMovementDao balanceMovementDao;

    public FullBalanceCrudService() {
        super(FullBalance.class);
        this.walletDao = WalletDao.getInstance();
        this.balanceDao = BalanceDao.getInstance();
        this.balanceMovementDao = BalanceMovementDao.getInstance();
    }

    @Nullable
    @Override
    public FullBalance retrieve(@NonNull final String address) {
        final Wallet wallet = walletDao.findById(address);
        if (wallet == null) {
            return null;
        }
        final Balance balance = Objects.requireNonNull(
                balanceDao.findById(address), "There should be a balance for the existing wallet");

        final List<Movement> all = balanceMovementDao.getAll(address).stream()
                .map(v -> new Movement(new Date(v.timestamp()), v.amount(), v.transactionUUID()))
                .collect(Collectors.toList());
        return new FullBalance(wallet, balance.amount(), all);
    }
}
