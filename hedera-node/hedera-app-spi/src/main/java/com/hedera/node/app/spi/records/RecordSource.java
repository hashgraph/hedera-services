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

package com.hedera.node.app.spi.records;

import static com.hedera.hapi.node.base.ResponseCodeEnum.UNKNOWN;

import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.transaction.TransactionReceipt;
import com.hedera.hapi.node.transaction.TransactionRecord;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * A source of queryable {@link TransactionReceipt} and {@link TransactionRecord} records for one or more
 * {@link TransactionID}'s.
 */
public interface RecordSource {
    /**
     * This receipt is returned whenever we know there is a transaction pending (i.e. we have a history for a
     * transaction ID), but we do not yet have a record for it.
     */
    TransactionReceipt PENDING_RECEIPT =
            TransactionReceipt.newBuilder().status(UNKNOWN).build();

    /**
     * The transaction id of the user transaction that was used to assign consensus timestamps to the records
     * in this source.
     * @return the top-level user transaction id
     */
    @NonNull
    TransactionID userTxnId();

    /**
     * Perform the given action on each transaction record known to this source.
     * @param action the action to perform
     */
    void forEachTxnRecord(@NonNull Consumer<TransactionRecord> action);

    /**
     * Perform the given action on each transaction id and corresponding status known to this source.
     * @param action the action to perform
     */
    void forEachTxnOutcome(@NonNull BiConsumer<TransactionID, ResponseCodeEnum> action);

    /**
     * Gets the user transaction record for the given transaction id, if known.
     * @return the record, or {@code null} if the id is unknown
     */
    @Nullable
    TransactionRecord recordFor(@NonNull TransactionID txnId);
}
