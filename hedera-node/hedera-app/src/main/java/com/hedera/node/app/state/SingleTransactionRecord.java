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

package com.hedera.node.app.state;

import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.TokenType;
import com.hedera.hapi.node.base.Transaction;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.hapi.node.transaction.TransactionRecord;
import com.hedera.hapi.streams.TransactionSidecarRecord;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.List;

/**
 * A record of a single transaction, including the transaction, record and the sidecar records.
 */
public record SingleTransactionRecord(
        @NonNull Transaction transaction,
        @NonNull TransactionRecord transactionRecord,
        @NonNull List<TransactionSidecarRecord> transactionSidecarRecords,
        @NonNull TransactionOutputs transactionOutputs) {
    public SingleTransactionRecord {
        requireNonNull(transaction, "transaction must not be null");
        requireNonNull(transactionRecord, "record must not be null");
        requireNonNull(transactionSidecarRecords, "transactionSidecarRecords must not be null");
        requireNonNull(transactionOutputs, "transactionOutputs must not be null");
    }

    // This is used by BlockStream, and is not serialized.
    public record TransactionOutputs(
            @Nullable TokenType tokenType, @NonNull TransactionBody.DataOneOfType transactionBodyType) {}
}
