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

package com.swirlds.base.sample.internal;

import com.swirlds.base.sample.domain.Balance;
import com.swirlds.base.sample.domain.Wallet;
import com.swirlds.base.sample.persistence.BalanceDao;
import com.swirlds.base.sample.persistence.WalletDao;
import java.math.BigDecimal;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class InitialData {

    private static final Logger log = LogManager.getLogger(InitialData.class);

    /**
     * Populates seed data
     */
    public static void populate() {
        WalletDao.getInstance().save(new Wallet("0"));
        BalanceDao.getInstance().saveOrUpdate(new Balance(new Wallet("0"), BigDecimal.valueOf(Long.MAX_VALUE)));

        WalletDao.getInstance().save(new Wallet("X"));
        BalanceDao.getInstance().saveOrUpdate(new Balance(new Wallet("X"), BigDecimal.ZERO));

        log.debug("Seed data added");
    }
}
