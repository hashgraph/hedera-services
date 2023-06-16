/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.spi.records;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.transaction.TransactionReceipt;
import com.hedera.hapi.node.transaction.TransactionRecord;
import com.hedera.node.app.spi.Service;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.List;

/**
 * Supplies {@link Service}s access to records and receipts.
 *
 * <p>A receipt is added when this node ingests a new transaction, or when this node pre-handles a transaction ingested
 * on another node. A receipt in this state will have a status of
 * {@link com.hedera.hapi.node.base.ResponseCodeEnum#UNKNOWN}.
 *
 * <p>Later, during {@code handle}, a final record and receipt is added based on the result of handling that
 * transaction. The receipt will no longer be "UNKNOWN" unless an unhandled exception occurred.
 */
public interface RecordCache {
    /**
     * Gets the "primary" record for the given {@link TransactionID}. A user may submit multiple transactions with the
     * same {@link TransactionID} to the network by submitting to multiple nodes. In that case, there will be multiple
     * records and receipts. However, only the first of these will be "primary" and all others will be duplicates.
     *
     * @param transactionID The transaction ID to lookup
     * @return The {@link TransactionRecord}, if any, or else {@code null}.
     */
    @Nullable
    default TransactionRecord getRecord(@NonNull TransactionID transactionID) {
        final var records = getRecords(transactionID);
        return records.isEmpty() ? null : records.get(0);
    }

    /**
     * Gets a list of all records for the given {@link TransactionID}. A user may submit multiple transactions with the
     * same {@link TransactionID} to the network by submitting to multiple nodes. In that case, there will be multiple
     * records and receipts.
     *
     * @param transactionID The transaction ID to lookup
     * @return The {@link TransactionRecord}s, if any, or else an empty list.
     */
    @NonNull
    List<TransactionRecord> getRecords(@NonNull TransactionID transactionID);

    /**
     * Gets a list of all records for the given {@link AccountID}. The {@link AccountID} is the account of the Payer of
     * the transaction.
     *
     * @param accountID The accountID of the Payer of the transactions
     * @return The {@link TransactionRecord}s, if any, or else an empty list.
     */
    @NonNull
    List<TransactionRecord> getRecords(@NonNull AccountID accountID);

    /**
     * Gets the "primary" receipt for the given {@link TransactionID}. A user may submit multiple transactions with the
     * same {@link TransactionID} to the network by submitting to multiple nodes. In that case, there will be multiple
     * receipts. However, only the first of these will be "primary" and all others will be duplicates.
     *
     * @param transactionID The transaction ID to lookup
     * @return The {@link TransactionReceipt}, if any, or else {@code null}. If the transaction is known to exist but
     * has not yet been handled, the receipt will have a status of
     * {@link com.hedera.hapi.node.base.ResponseCodeEnum#UNKNOWN}.
     */
    @Nullable
    default TransactionReceipt getReceipt(@NonNull TransactionID transactionID) {
        final var receipts = getReceipts(transactionID);
        return receipts.isEmpty() ? null : receipts.get(0);
    }

    /**
     * Gets a list of all receipts for the given {@link TransactionID}. A user may submit multiple transactions with the
     * same {@link TransactionID} to the network by submitting to multiple nodes. In that case, there will be multiple
     * receipts.
     *
     * @param transactionID The transaction ID to lookup
     * @return The {@link TransactionReceipt}s. If any transaction is known to exist but has not yet been handled, the
     * receipt will have a status of {@link com.hedera.hapi.node.base.ResponseCodeEnum#UNKNOWN}.
     */
    @NonNull
    List<TransactionReceipt> getReceipts(@NonNull TransactionID transactionID);

    /**
     * Gets a list of all receipts for the given {@link AccountID}. The {@link AccountID} is the account of the Payer of
     * the transaction.
     *
     * @param accountID The accountID of the Payer of the transactions
     * @return The {@link TransactionReceipt}s. If any transaction is known to exist but has not yet been handled, the
     * receipt will have a status of {@link com.hedera.hapi.node.base.ResponseCodeEnum#UNKNOWN}.
     */
    @NonNull
    default List<TransactionReceipt> getReceipts(@NonNull AccountID accountID) {
        final var records = getRecords(accountID);
        return records.stream().map(TransactionRecord::receipt).toList();
    }
}
