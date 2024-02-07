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
import com.swirlds.base.sample.persistence.BalanceDao;
import edu.umd.cs.findbugs.annotations.NonNull;

public class BalanceService extends Service<Balance> {
    private final BalanceDao dao;

    public BalanceService() {
        super(Balance.class);
        this.dao = BalanceDao.getInstance();
    }

    @NonNull
    @Override
    public Balance retrieve(@NonNull final String key) {
        return dao.findById(key);
    }
}
