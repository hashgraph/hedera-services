/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
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
package com.hedera.node.app.service.mono.config;

import static com.hedera.node.app.service.mono.config.EntityNumbers.UNKNOWN_NUMBER;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.ACCOUNTS_ADDRESS_BOOK_ADMIN;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.ACCOUNTS_EXCHANGE_RATES_ADMIN;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.ACCOUNTS_FEE_SCHEDULE_ADMIN;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.ACCOUNTS_FREEZE_ADMIN;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.ACCOUNTS_NODE_REWARD_ACCOUNT;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.ACCOUNTS_STAKING_REWARD_ACCOUNT;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.ACCOUNTS_SYSTEM_ADMIN;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.ACCOUNTS_SYSTEM_DELETE_ADMIN;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.ACCOUNTS_SYSTEM_UNDELETE_ADMIN;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.ACCOUNTS_TREASURY;

import com.hedera.node.app.service.mono.context.annotations.CompositeProps;
import com.hedera.node.app.service.mono.context.properties.PropertySource;
import com.hedera.node.app.spi.numbers.HederaAccountNumbers;
import javax.inject.Inject;
import javax.inject.Singleton;

/** FUTURE: This class will be moved to hedera-app-spi module in future PRs */
@Singleton
public class AccountNumbers implements HederaAccountNumbers {
    private final PropertySource properties;

    private long treasury = UNKNOWN_NUMBER;
    private long systemAdmin = UNKNOWN_NUMBER;
    private long freezeAdmin = UNKNOWN_NUMBER;
    private long addressBookAdmin = UNKNOWN_NUMBER;
    private long systemDeleteAdmin = UNKNOWN_NUMBER;
    private long feeSchedulesAdmin = UNKNOWN_NUMBER;
    private long exchangeRatesAdmin = UNKNOWN_NUMBER;
    private long systemUndeleteAdmin = UNKNOWN_NUMBER;
    private long stakingRewardAccount = UNKNOWN_NUMBER;
    private long nodeRewardAccount = UNKNOWN_NUMBER;

    @Inject
    public AccountNumbers(@CompositeProps PropertySource properties) {
        this.properties = properties;
    }

    @Override
    public long treasury() {
        if (treasury == UNKNOWN_NUMBER) {
            treasury = properties.getLongProperty(ACCOUNTS_TREASURY);
        }
        return treasury;
    }

    @Override
    public long freezeAdmin() {
        if (freezeAdmin == UNKNOWN_NUMBER) {
            freezeAdmin = properties.getLongProperty(ACCOUNTS_FREEZE_ADMIN);
        }
        return freezeAdmin;
    }

    @Override
    public long systemAdmin() {
        if (systemAdmin == UNKNOWN_NUMBER) {
            systemAdmin = properties.getLongProperty(ACCOUNTS_SYSTEM_ADMIN);
        }
        return systemAdmin;
    }

    @Override
    public long addressBookAdmin() {
        if (addressBookAdmin == UNKNOWN_NUMBER) {
            addressBookAdmin = properties.getLongProperty(ACCOUNTS_ADDRESS_BOOK_ADMIN);
        }
        return addressBookAdmin;
    }

    @Override
    public long feeSchedulesAdmin() {
        if (feeSchedulesAdmin == UNKNOWN_NUMBER) {
            feeSchedulesAdmin = properties.getLongProperty(ACCOUNTS_FEE_SCHEDULE_ADMIN);
        }
        return feeSchedulesAdmin;
    }

    @Override
    public long exchangeRatesAdmin() {
        if (exchangeRatesAdmin == UNKNOWN_NUMBER) {
            exchangeRatesAdmin = properties.getLongProperty(ACCOUNTS_EXCHANGE_RATES_ADMIN);
        }
        return exchangeRatesAdmin;
    }

    @Override
    public long systemDeleteAdmin() {
        if (systemDeleteAdmin == UNKNOWN_NUMBER) {
            systemDeleteAdmin = properties.getLongProperty(ACCOUNTS_SYSTEM_DELETE_ADMIN);
        }
        return systemDeleteAdmin;
    }

    @Override
    public long systemUndeleteAdmin() {
        if (systemUndeleteAdmin == UNKNOWN_NUMBER) {
            systemUndeleteAdmin = properties.getLongProperty(ACCOUNTS_SYSTEM_UNDELETE_ADMIN);
        }
        return systemUndeleteAdmin;
    }

    @Override
    public long stakingRewardAccount() {
        if (stakingRewardAccount == UNKNOWN_NUMBER) {
            stakingRewardAccount = properties.getLongProperty(ACCOUNTS_STAKING_REWARD_ACCOUNT);
        }
        return stakingRewardAccount;
    }

    @Override
    public long nodeRewardAccount() {
        if (nodeRewardAccount == UNKNOWN_NUMBER) {
            nodeRewardAccount = properties.getLongProperty(ACCOUNTS_NODE_REWARD_ACCOUNT);
        }
        return nodeRewardAccount;
    }

    @Override
    public boolean isSuperuser(long num) {
        return num == treasury() || num == systemAdmin();
    }
}
