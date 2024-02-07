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
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class BalanceDao {

    private static class InstanceHolder {
        private static final BalanceDao INSTANCE = new BalanceDao();
    }

    public static @NonNull BalanceDao getInstance() {
        return InstanceHolder.INSTANCE;
    }

    private static final Map<String, Balance> BALANCE_REPOSITORY = new ConcurrentHashMap<>();

    public Balance save(final @NonNull Balance balance) {
        BALANCE_REPOSITORY.computeIfPresent(balance.wallet().address(), (k, b) -> {
            final Version version = b.version().checkAgainst(balance.version());
            return new Balance(b.wallet(), balance.amount(), version);
        });
        BALANCE_REPOSITORY.putIfAbsent(balance.wallet().address(), balance);
        return balance;
    }

    public Balance findById(final @NonNull String id) {
        return BALANCE_REPOSITORY.get(id);
    }
}
