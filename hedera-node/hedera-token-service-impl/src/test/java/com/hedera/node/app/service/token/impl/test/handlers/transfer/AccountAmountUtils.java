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

package com.hedera.node.app.service.token.impl.test.handlers.transfer;

import com.hedera.hapi.node.base.AccountAmount;
import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.NftTransfer;
import com.hedera.pbj.runtime.io.buffer.Bytes;

public final class AccountAmountUtils {
    private AccountAmountUtils() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static AccountAmount aaWith(AccountID account, long amount) {
        return AccountAmount.newBuilder().accountID(account).amount(amount).build();
    }

    public static AccountAmount aaWithAllowance(AccountID account, long amount) {
        return AccountAmount.newBuilder()
                .accountID(account)
                .amount(amount)
                .isApproval(true)
                .build();
    }

    public static AccountID asAccountWithAlias(Bytes alias) {
        return AccountID.newBuilder().alias(alias).build();
    }

    public static NftTransfer nftTransferWith(AccountID from, AccountID to, long serialNo) {
        return NftTransfer.newBuilder()
                .senderAccountID(from)
                .receiverAccountID(to)
                .serialNumber(serialNo)
                .build();
    }

    public static NftTransfer nftTransferWithAllowance(AccountID from, AccountID to, long serialNo) {
        return NftTransfer.newBuilder()
                .senderAccountID(from)
                .receiverAccountID(to)
                .serialNumber(serialNo)
                .isApproval(true)
                .build();
    }

    public static AccountAmount aaAlias(final Bytes alias, final long amount) {
        return AccountAmount.newBuilder()
                .amount(amount)
                .accountID(AccountID.newBuilder().alias(alias).build())
                .build();
    }
}
