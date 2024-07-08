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

import static com.hedera.node.app.spi.workflows.HandleContext.TransactionCategory.USER;

import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.base.Transaction;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.transaction.SignedTransaction;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.spi.workflows.HandleContext;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Instant;

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

    /**
     * The transaction category of the transaction that created this record
     * @return the transaction category
     */
    HandleContext.TransactionCategory category();

    /**
     * The behavior of the record when the parent transaction fails
     * @return the behavior
     */
    ReversingBehavior reversingBehavior();

    /**
     * Removes all the side effects on the record when the parent transaction fails
     */
    void nullOutSideEffectFields();

    /**
     * Sets the transactionID of the record based on the user transaction record.
     * @return the builder
     */
    SingleTransactionRecordBuilder syncBodyIdFromRecordId();

    /**
     * Sets the consensus timestamp of the record.
     * @param now the consensus timestamp
     * @return the builder
     */
    SingleTransactionRecordBuilder consensusTimestamp(@NonNull final Instant now);

    /**
     * Returns the transaction ID of the record.
     * @return the transaction ID
     */
    TransactionID transactionID();

    /**
     * Sets the transaction ID of the record.
     * @param transactionID the transaction ID
     * @return the builder
     */
    SingleTransactionRecordBuilder transactionID(@NonNull final TransactionID transactionID);

    /**
     * Sets the parent consensus timestamp of the record.
     * @param parentConsensus the parent consensus timestamp
     * @return the builder
     */
    SingleTransactionRecordBuilder parentConsensus(@NonNull final Instant parentConsensus);

    /**
     * Returns whether the record is a base record builder. A base record builder is a record builder
     * that is created when new stack is created.
     * @return true if the record is a base record builder; otherwise false
     */
    boolean isBaseRecordBuilder();

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

    /**
     * Returns whether the transaction is a preceding transaction.
     * @return {@code true} if the transaction is a preceding transaction; otherwise {@code false}
     */
    default boolean isPreceding() {
        return category().equals(HandleContext.TransactionCategory.PRECEDING);
    }

    /**
     * Returns whether the transaction is an internal dispatch.
     * @return true if the transaction is an internal dispatch; otherwise false
     */
    default boolean isInternalDispatch() {
        return !category().equals(USER);
    }

    /**
     * Possible behavior of a SingleTransactionRecord when a parent transaction fails,
     * and it is asked to be reverted
     */
    enum ReversingBehavior {
        /**
         * Changes are not committed. The record is kept in the record stream,
         * but the status is set to {@link ResponseCodeEnum#REVERTED_SUCCESS}
         */
        REVERSIBLE,

        /**
         * Changes are not committed and the record is removed from the record stream.
         */
        REMOVABLE,

        /**
         * Changes are committed independent of the user and parent transactions.
         */
        IRREVERSIBLE
    }
}
