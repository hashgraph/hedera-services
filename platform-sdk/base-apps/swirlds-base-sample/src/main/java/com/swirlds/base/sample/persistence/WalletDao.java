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

import com.swirlds.base.sample.domain.Wallet;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * in-memory simple data layer for Transactions
 */
public class WalletDao {

    private static class InstanceHolder {
        private static final WalletDao INSTANCE = new WalletDao();
    }

    public static @NonNull WalletDao getInstance() {
        return InstanceHolder.INSTANCE;
    }

    private static final Map<String, Wallet> WALLET_REPOSITORY = new ConcurrentHashMap<>();

    public Wallet save(Wallet wallet) {
        if (WALLET_REPOSITORY.containsKey(wallet.address())) {
            throw new IllegalArgumentException("Resource already exist");
        }
        WALLET_REPOSITORY.put(wallet.address(), wallet);
        return wallet;
    }

    public Wallet findById(String id) {
        return WALLET_REPOSITORY.get(id);
    }

    public void deleteById(String id) {
        if (!WALLET_REPOSITORY.containsKey(id)) {
            throw new IllegalArgumentException("Resource does not exist");
        }
        WALLET_REPOSITORY.remove(id);
    }

    public List<Wallet> findAll() {
        return WALLET_REPOSITORY.values().stream().toList();
    }
}
