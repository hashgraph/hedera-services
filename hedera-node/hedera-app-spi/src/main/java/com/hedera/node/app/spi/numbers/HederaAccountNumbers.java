/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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
package com.hedera.node.app.spi.numbers;

/**
 * Represents different types of special accounts used in the ledger. FUTURE : Implementations of
 * these classes will be moved to this module in future PRs
 */
public interface HederaAccountNumbers {
    /**
     * Account number representing treasury
     *
     * @return treasury account number
     */
    long treasury();
    /**
     * Account number representing freeze admin
     *
     * @return freeze admin account number
     */
    long freezeAdmin();
    /**
     * Account number representing system admin
     *
     * @return system admin account number
     */
    long systemAdmin();
    /**
     * Account number representing address book admin
     *
     * @return address book admin account
     */
    long addressBookAdmin();
    /**
     * Account number representing fee schedule admin
     *
     * @return fee schedule admin account number
     */
    long feeSchedulesAdmin();
    /**
     * Account number representing exchange rate admin
     *
     * @return exchange rate admin account number
     */
    long exchangeRatesAdmin();
    /**
     * Account number representing system delete admin
     *
     * @return system delete admin account number
     */
    long systemDeleteAdmin();
    /**
     * Account number representing system undelete admin
     *
     * @return system undelete admin account number
     */
    long systemUndeleteAdmin();
    /**
     * Account number representing staking reward account number
     *
     * @return staking reward account number
     */
    long stakingRewardAccount();
    /**
     * Account number representing node reward account number
     *
     * @return node reward account number
     */
    long nodeRewardAccount();
    /**
     * Checks if the account number provided is superuser
     *
     * @return true if superuser account, false otherwise
     */
    default boolean isSuperuser(long num) {
        return num == treasury() || num == systemAdmin();
    }
}
