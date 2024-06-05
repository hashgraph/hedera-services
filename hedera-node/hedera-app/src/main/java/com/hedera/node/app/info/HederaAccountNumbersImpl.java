/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.info;

import com.hedera.node.app.spi.numbers.HederaAccountNumbers;
import com.amh.config.ConfigProvider;
import com.hedera.node.config.data.AccountsConfig;
import edu.umd.cs.findbugs.annotations.NonNull;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class HederaAccountNumbersImpl implements HederaAccountNumbers {

    private AccountsConfig accountsConfig;

    @Inject
    public HederaAccountNumbersImpl(@NonNull final ConfigProvider configProvider) {
        final var config = configProvider.getConfiguration();
        this.accountsConfig = config.getConfigData(AccountsConfig.class);
    }

    @Override
    public long treasury() {
        return accountsConfig.treasury();
    }

    @Override
    public long freezeAdmin() {
        return accountsConfig.freezeAdmin();
    }

    @Override
    public long systemAdmin() {
        return accountsConfig.systemAdmin();
    }

    @Override
    public long addressBookAdmin() {
        return accountsConfig.addressBookAdmin();
    }

    @Override
    public long feeSchedulesAdmin() {
        return accountsConfig.feeSchedulesAdmin();
    }

    @Override
    public long exchangeRatesAdmin() {
        return accountsConfig.exchangeRatesAdmin();
    }

    @Override
    public long systemDeleteAdmin() {
        return accountsConfig.systemDeleteAdmin();
    }

    @Override
    public long systemUndeleteAdmin() {
        return accountsConfig.systemUndeleteAdmin();
    }

    @Override
    public long stakingRewardAccount() {
        return accountsConfig.stakingRewardAccount();
    }

    @Override
    public long nodeRewardAccount() {
        return accountsConfig.nodeRewardAccount();
    }
}
