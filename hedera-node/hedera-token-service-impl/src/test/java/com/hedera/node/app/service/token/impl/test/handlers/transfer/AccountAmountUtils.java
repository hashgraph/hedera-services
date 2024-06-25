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

/**
 * Utility class for creating {@link AccountAmount} and {@link NftTransfer} objects.
 */
public final class AccountAmountUtils {
    private AccountAmountUtils() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Create an {@link AccountAmount} object with the given account and amount.
     * @param account the account
     * @param amount the amount
     * @return the {@link AccountAmount} object
     */
    public static AccountAmount aaWith(AccountID account, long amount) {
        return AccountAmount.newBuilder().accountID(account).amount(amount).build();
    }

    /**
     * Create an {@link AccountAmount} object with the given account and amount, and set the approval flag.
     * @param account the account
     * @param amount the amount
     * @return the {@link AccountAmount} object
     */
    public static AccountAmount aaWithAllowance(AccountID account, long amount) {
        return AccountAmount.newBuilder()
                .accountID(account)
                .amount(amount)
                .isApproval(true)
                .build();
    }

    /**
     * Create an {@link AccountID} object with the given alias.
     * @param alias the alias
     * @return the {@link AccountID} object
     */
    public static AccountID asAccountWithAlias(Bytes alias) {
        return AccountID.newBuilder().alias(alias).build();
    }

    /**
     * Create an {@link NftTransfer} object with the given sender, receiver, and serial number.
     * @param from the sender
     * @param to the receiver
     * @param serialNo the serial number
     * @return the {@link NftTransfer} object
     */
    public static NftTransfer nftTransferWith(AccountID from, AccountID to, long serialNo) {
        return NftTransfer.newBuilder()
                .senderAccountID(from)
                .receiverAccountID(to)
                .serialNumber(serialNo)
                .build();
    }

    /**
     * Create an {@link NftTransfer} object with the given sender, receiver, and serial number, and set the approval flag to true.
     * @param from the sender
     * @param to the receiver
     * @param serialNo the serial number
     * @return the {@link NftTransfer} object
     */
    public static NftTransfer nftTransferWithAllowance(AccountID from, AccountID to, long serialNo) {
        return NftTransfer.newBuilder()
                .senderAccountID(from)
                .receiverAccountID(to)
                .serialNumber(serialNo)
                .isApproval(true)
                .build();
    }

    /**
     * Create an {@link AccountAmount} object with the given aliased accountId with given alias and amount.
     * @param alias the alias
     * @param amount the amount
     * @return the {@link AccountAmount} object
     */
    public static AccountAmount aaAlias(final Bytes alias, final long amount) {
        return AccountAmount.newBuilder()
                .amount(amount)
                .accountID(AccountID.newBuilder().alias(alias).build())
                .build();
    }
}
