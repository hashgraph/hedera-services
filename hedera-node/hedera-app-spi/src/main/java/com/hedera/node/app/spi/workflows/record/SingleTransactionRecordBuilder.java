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

package com.hedera.node.app.spi.workflows.record;

import static com.hedera.node.app.spi.workflows.HandleContext.TransactionCategory.CHILD;
import static com.hedera.node.app.spi.workflows.HandleContext.TransactionCategory.PRECEDING;
import static com.hedera.node.app.spi.workflows.HandleContext.TransactionCategory.SCHEDULED;
import static com.hedera.node.app.spi.workflows.HandleContext.TransactionCategory.USER;

import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.base.Transaction;
import com.hedera.hapi.node.transaction.SignedTransaction;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.spi.workflows.HandleContext;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * This interface contains methods to read general properties a transaction record builder.
 */
public interface SingleTransactionRecordBuilder {

    /**
     * Returns the status that is currently set in the record builder.
     *
     * @return the status of the transaction
     */
    @NonNull
    ResponseCodeEnum status();

    /**
     * Returns the parsed transaction body for this record.
     *
     * @return the transaction body
     */
    @NonNull
    TransactionBody transactionBody();

    /**
     * Returns the current transaction fee.
     *
     * @return the current transaction fee
     */
    long transactionFee();

    /**
     * Sets the receipt status.
     *
     * @param status the receipt status
     * @return the builder
     */
    SingleTransactionRecordBuilder status(@NonNull ResponseCodeEnum status);

    HandleContext.TransactionCategory category();

    /**
     * Returns true if this builder's transaction originated from inside another handler or workflow; and not
     * a user transaction (or scheduled user transaction).
     * @return true if this transaction is internal
     */
    default boolean isInternalDispatch() {
        return category() == CHILD || category() == PRECEDING;
    }

    /**
     * Returns true if this builder's transaction originated from a user transaction or scheduled user transaction; and
     * not from inside another handler or workflow.
     * @return true if this transaction is internal
     */
    default boolean isUserDispatch() {
        return category() == USER || category() == SCHEDULED;
    }

    /**
     * Convenience method to package as {@link TransactionBody} as a {@link Transaction} .
     *
     * @param body the transaction body
     * @return the transaction
     */
    static Transaction transactionWith(@NonNull TransactionBody body) {
        final var bodyBytes = TransactionBody.PROTOBUF.toBytes(body);
        final var signedTransaction =
                SignedTransaction.newBuilder().bodyBytes(bodyBytes).build();
        final var signedTransactionBytes = SignedTransaction.PROTOBUF.toBytes(signedTransaction);
        return Transaction.newBuilder()
                .signedTransactionBytes(signedTransactionBytes)
                .build();
    }
}
