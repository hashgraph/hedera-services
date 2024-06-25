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

package com.hedera.node.app.workflows.handle.flow.dispatch.user.logic;

import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.Transaction;
import com.hedera.node.app.fees.ExchangeRateManager;
import com.hedera.node.app.workflows.TransactionInfo;
import com.hedera.node.app.workflows.handle.record.SingleTransactionRecordBuilderImpl;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Initializes the user record with all the necessary information.
 */
@Singleton
public class UserRecordInitializer {
    private final ExchangeRateManager exchangeRateManager;

    /**
     * Creates a user record initializer with the given exchange rate manager.
     * @param exchangeRateManager the exchange rate manager
     */
    @Inject
    public UserRecordInitializer(@NonNull final ExchangeRateManager exchangeRateManager) {
        this.exchangeRateManager = requireNonNull(exchangeRateManager);
    }

    /**
     * Initializes the user record with the transaction information. The record builder list is initialized with the
     * transaction, transaction bytes, transaction ID, exchange rate, and memo.
     * @param recordBuilder the record builder
     * @param txnInfo the transaction info
     */
    // TODO: Guarantee that this never throws an exception
    public SingleTransactionRecordBuilderImpl initializeUserRecord(
            @NonNull final SingleTransactionRecordBuilderImpl recordBuilder, @NonNull final TransactionInfo txnInfo) {
        requireNonNull(txnInfo);
        requireNonNull(recordBuilder);
        final var transaction = txnInfo.transaction();
        // If the transaction uses the legacy body bytes field instead of explicitly setting
        // its signed bytes, the record will have the hash of its bytes as serialized by PBJ
        final Bytes transactionBytes;
        if (transaction.signedTransactionBytes().length() > 0) {
            transactionBytes = transaction.signedTransactionBytes();
        } else {
            transactionBytes = Transaction.PROTOBUF.toBytes(transaction);
        }
        return recordBuilder
                .transaction(txnInfo.transaction())
                .transactionBytes(transactionBytes)
                .transactionID(txnInfo.txBody().transactionIDOrThrow())
                .exchangeRate(exchangeRateManager.exchangeRates())
                .memo(txnInfo.txBody().memo());
    }
}
