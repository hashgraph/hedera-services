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

import com.hedera.node.app.spi.numbers.HederaAccountNumbers;
import com.hedera.node.app.spi.numbers.HederaFileNumbers;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.FileID;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class EntityNumbers {
    public static final long UNKNOWN_NUMBER = Long.MIN_VALUE;

    private final HederaFileNumbers fileNumbers;
    private final HederaNumbers hederaNumbers;
    private final HederaAccountNumbers accountNumbers;

    @Inject
    public EntityNumbers(
            final HederaFileNumbers fileNumbers,
            final HederaNumbers hederaNumbers,
            final HederaAccountNumbers accountNumbers) {
        this.fileNumbers = fileNumbers;
        this.hederaNumbers = hederaNumbers;
        this.accountNumbers = accountNumbers;
    }

    public boolean isSystemFile(FileID id) {
        var num = id.getFileNum();
        return 1 <= num && num <= hederaNumbers.numReservedSystemEntities();
    }

    public boolean isSystemAccount(AccountID id) {
        var num = id.getAccountNum();
        return 1 <= num && num <= hederaNumbers.numReservedSystemEntities();
    }

    public boolean isSystemContract(ContractID id) {
        var num = id.getContractNum();
        return 1 <= num && num <= hederaNumbers.numReservedSystemEntities();
    }

    public HederaNumbers all() {
        return hederaNumbers;
    }

    public HederaFileNumbers files() {
        return fileNumbers;
    }

    public HederaAccountNumbers accounts() {
        return accountNumbers;
    }
}
