/*
 * Copyright (C) 2021-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.utils.forensics;

import com.hedera.services.utils.accessors.TxnAccessor;
import com.hederahashgraph.api.proto.java.*;
import java.time.Instant;

/**
 * Represents a single {@code (Transaction, TransactionRecord)} entry from a record stream,
 * including the consensus time as a {@link Instant} for convenience.
 *
 * @param accessor the transaction as an accessor for convenience
 * @param txnRecord the resolved record the transaction
 * @param consensusTime the consensus time
 */
public record RecordStreamEntry(
        TxnAccessor accessor, TransactionRecord txnRecord, Instant consensusTime) {

    public Transaction submittedTransaction() {
        return accessor.getSignedTxnWrapper();
    }

    public TransactionBody body() {
        return accessor.getTxn();
    }

    public ResponseCodeEnum finalStatus() {
        return txnRecord.getReceipt().getStatus();
    }

    public HederaFunctionality function() {
        return accessor.getFunction();
    }
}
