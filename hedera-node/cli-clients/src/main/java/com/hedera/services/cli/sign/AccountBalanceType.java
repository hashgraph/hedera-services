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

package com.hedera.services.cli.sign;

import com.swirlds.common.stream.StreamType;

/**
 * Contains properties related to Account Balance file type;
 * Its constructor is private. Users need to use the singleton to denote this type
 */
public final class AccountBalanceType implements StreamType {
    /**
     * description of the streamType, used for logging
     */
    public static final String ACCOUNT_BALANCE_DESCRIPTION = "account balance";
    /**
     * file name extension
     */
    public static final String ACCOUNT_BALANCE_EXTENSION = "pb";
    /**
     * file name extension of signature file
     */
    public static final String ACCOUNT_BALANCE_SIG_EXTENSION = "pb_sig";
    /**
     * a singleton denotes AccountBalanceType
     */
    private static final AccountBalanceType INSTANCE = new AccountBalanceType();

    private AccountBalanceType() {}

    public static AccountBalanceType getInstance() {
        return INSTANCE;
    }

    @Override
    public String getDescription() {
        return ACCOUNT_BALANCE_DESCRIPTION;
    }

    @Override
    public String getExtension() {
        return ACCOUNT_BALANCE_EXTENSION;
    }

    @Override
    public String getSigExtension() {
        return ACCOUNT_BALANCE_SIG_EXTENSION;
    }

    @Override
    // not used in BalanceFile
    public int[] getFileHeader() {
        return new int[0];
    }

    @Override
    // not used in BalanceFile
    public byte[] getSigFileHeader() {
        return new byte[0];
    }
}
