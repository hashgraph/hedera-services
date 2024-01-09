/*
 * Copyright (C) 2020-2024 Hedera Hashgraph, LLC
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

package com.hedera.services.cli.sign;

/**
 * Contains properties related to Account Balance file type;
 * Its constructor is private. Users need to use the singleton to denote this type
 */
public final class AccountBalanceType {
    /**
     * description of the streamType, used for logging
     */
    private static final String ACCOUNT_BALANCE_DESCRIPTION = "account balance";
    /**
     * file name extension
     */
    private static final String ACCOUNT_BALANCE_EXTENSION = "pb";
    /**
     * file name extension of signature file
     */
    private static final String ACCOUNT_BALANCE_SIG_EXTENSION = "pb_sig";
    /**
     * a singleton denotes AccountBalanceType
     */
    private static final AccountBalanceType INSTANCE = new AccountBalanceType();

    private AccountBalanceType() {}

    public static AccountBalanceType getInstance() {
        return INSTANCE;
    }

    public String getDescription() {
        return ACCOUNT_BALANCE_DESCRIPTION;
    }

    public String getExtension() {
        return ACCOUNT_BALANCE_EXTENSION;
    }

    public String getSigExtension() {
        return ACCOUNT_BALANCE_SIG_EXTENSION;
    }

    public boolean isCorrectFile(final String fileName) {
        return fileName.endsWith(ACCOUNT_BALANCE_EXTENSION);
    }
}
