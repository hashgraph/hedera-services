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

package com.hedera.node.app.service.token.impl.util;

import com.hedera.hapi.node.base.AccountAmount;
import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.NftTransfer;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.base.TokenTransferList;
import edu.umd.cs.findbugs.annotations.NonNull;

public class CryptoTransferHelper {

    private CryptoTransferHelper() {
        throw new UnsupportedOperationException("Utility class only");
    }

    public static TokenTransferList createFungibleTransfer(
            final TokenID tokenId, final AccountID fromAccount, final long amount, final AccountID toAccount) {
        return TokenTransferList.newBuilder()
                .token(tokenId)
                .transfers(debit(fromAccount, amount), credit(toAccount, amount))
                .build();
    }

    public static TokenTransferList createNftTransfer(
            final TokenID tokenId, final AccountID fromAccount, final AccountID toAccount, final long serialNumber) {
        return TokenTransferList.newBuilder()
                .token(tokenId)
                .nftTransfers(nftTransfer(fromAccount, toAccount, serialNumber))
                .build();
    }

    public static NftTransfer nftTransfer(
            @NonNull final AccountID from, @NonNull final AccountID to, final long serialNo) {
        return NftTransfer.newBuilder()
                .serialNumber(serialNo)
                .senderAccountID(from)
                .receiverAccountID(to)
                .build();
    }

    public static AccountAmount debit(@NonNull final AccountID account, final long amount) {
        return adjust(account, -amount);
    }

    public static AccountAmount credit(@NonNull final AccountID account, final long amount) {
        return adjust(account, amount);
    }

    private static AccountAmount adjust(@NonNull final AccountID account, final long amount) {
        return AccountAmount.newBuilder().accountID(account).amount(amount).build();
    }
}
