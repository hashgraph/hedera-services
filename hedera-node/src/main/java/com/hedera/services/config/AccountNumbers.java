/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.config;

import static com.hedera.services.config.EntityNumbers.UNKNOWN_NUMBER;
import static com.hedera.services.context.properties.PropertyNames.ACCOUNTS_ADDRESS_BOOK_ADMIN;
import static com.hedera.services.context.properties.PropertyNames.ACCOUNTS_EXCHANGE_RATES_ADMIN;
import static com.hedera.services.context.properties.PropertyNames.ACCOUNTS_FEE_SCHEDULE_ADMIN;
import static com.hedera.services.context.properties.PropertyNames.ACCOUNTS_FREEZE_ADMIN;
import static com.hedera.services.context.properties.PropertyNames.ACCOUNTS_NODE_REWARD_ACCOUNT;
import static com.hedera.services.context.properties.PropertyNames.ACCOUNTS_STAKING_REWARD_ACCOUNT;
import static com.hedera.services.context.properties.PropertyNames.ACCOUNTS_SYSTEM_ADMIN;
import static com.hedera.services.context.properties.PropertyNames.ACCOUNTS_SYSTEM_DELETE_ADMIN;
import static com.hedera.services.context.properties.PropertyNames.ACCOUNTS_SYSTEM_UNDELETE_ADMIN;
import static com.hedera.services.context.properties.PropertyNames.ACCOUNTS_TREASURY;

import com.hedera.services.context.annotations.CompositeProps;
import com.hedera.services.context.properties.PropertySource;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class AccountNumbers {
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

    public long treasury() {
        if (treasury == UNKNOWN_NUMBER) {
            treasury = properties.getLongProperty(ACCOUNTS_TREASURY);
        }
        return treasury;
    }

    public long freezeAdmin() {
        if (freezeAdmin == UNKNOWN_NUMBER) {
            freezeAdmin = properties.getLongProperty(ACCOUNTS_FREEZE_ADMIN);
        }
        return freezeAdmin;
    }

    public long systemAdmin() {
        if (systemAdmin == UNKNOWN_NUMBER) {
            systemAdmin = properties.getLongProperty(ACCOUNTS_SYSTEM_ADMIN);
        }
        return systemAdmin;
    }

    public long addressBookAdmin() {
        if (addressBookAdmin == UNKNOWN_NUMBER) {
            addressBookAdmin = properties.getLongProperty(ACCOUNTS_ADDRESS_BOOK_ADMIN);
        }
        return addressBookAdmin;
    }

    public long feeSchedulesAdmin() {
        if (feeSchedulesAdmin == UNKNOWN_NUMBER) {
            feeSchedulesAdmin = properties.getLongProperty(ACCOUNTS_FEE_SCHEDULE_ADMIN);
        }
        return feeSchedulesAdmin;
    }

    public long exchangeRatesAdmin() {
        if (exchangeRatesAdmin == UNKNOWN_NUMBER) {
            exchangeRatesAdmin = properties.getLongProperty(ACCOUNTS_EXCHANGE_RATES_ADMIN);
        }
        return exchangeRatesAdmin;
    }

    public long systemDeleteAdmin() {
        if (systemDeleteAdmin == UNKNOWN_NUMBER) {
            systemDeleteAdmin = properties.getLongProperty(ACCOUNTS_SYSTEM_DELETE_ADMIN);
        }
        return systemDeleteAdmin;
    }

    public long systemUndeleteAdmin() {
        if (systemUndeleteAdmin == UNKNOWN_NUMBER) {
            systemUndeleteAdmin = properties.getLongProperty(ACCOUNTS_SYSTEM_UNDELETE_ADMIN);
        }
        return systemUndeleteAdmin;
    }

    public long stakingRewardAccount() {
        if (stakingRewardAccount == UNKNOWN_NUMBER) {
            stakingRewardAccount = properties.getLongProperty(ACCOUNTS_STAKING_REWARD_ACCOUNT);
        }
        return stakingRewardAccount;
    }

    public long nodeRewardAccount() {
        if (nodeRewardAccount == UNKNOWN_NUMBER) {
            nodeRewardAccount = properties.getLongProperty(ACCOUNTS_NODE_REWARD_ACCOUNT);
        }
        return nodeRewardAccount;
    }

    public boolean isSuperuser(long num) {
        return num == treasury() || num == systemAdmin();
    }
}
