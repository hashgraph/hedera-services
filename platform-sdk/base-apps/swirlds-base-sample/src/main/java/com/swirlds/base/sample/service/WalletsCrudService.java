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
import com.swirlds.base.sample.domain.Wallet;
import com.swirlds.base.sample.metrics.ApplicationMetrics;
import com.swirlds.base.sample.persistence.BalanceDao;
import com.swirlds.base.sample.persistence.Version;
import com.swirlds.base.sample.persistence.WalletDao;
import com.swirlds.common.context.PlatformContext;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Controls wallets operations
 */
public class WalletsCrudService extends CrudService<Wallet> {

    private final @NonNull WalletDao dao;
    private final @NonNull BalanceDao balanceDao;
    private final @NonNull PlatformContext context;

    public WalletsCrudService(final @NonNull PlatformContext context) {
        super(Wallet.class);
        this.context = Objects.requireNonNull(context, "transaction cannot be null");
        this.dao = WalletDao.getInstance();
        this.balanceDao = BalanceDao.getInstance();
    }

    @Override
    public void delete(@NonNull final String key) {
        dao.deleteById(key);
    }

    @NonNull
    @Override
    public Wallet create(@NonNull final Wallet body) {
        final Wallet save = dao.save(new Wallet(UUID.randomUUID().toString()));
        balanceDao.save(new Balance(save, BigDecimal.ZERO, new Version(0)));
        context.getMetrics().getOrCreate(ApplicationMetrics.WALLETS_COUNT).increment();
        return save;
    }

    @NonNull
    @Override
    public Wallet retrieve(@NonNull final String key) {
        return dao.findById(key);
    }

    @NonNull
    @Override
    public List<Wallet> retrieveAll(@NonNull final Map<String, String> params) {
        return dao.findAll();
    }
}
