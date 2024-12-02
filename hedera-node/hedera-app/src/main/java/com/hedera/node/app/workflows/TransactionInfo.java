/*
 * Copyright (C) 2022-2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.workflows;

import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.base.SignatureMap;
import com.hedera.hapi.node.base.Transaction;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.transaction.SignedTransaction;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;

/**
 * Contains information related to a parsed transaction.
 *
 * <p>In HAPI, a {@link Transaction} has either a {@link TransactionBody} or a {@link SignedTransaction}, and
 * these are just represented as bytes and must be parsed. Then a {@link SignedTransaction} itself has its own
 * {@link TransactionBody} that also has to be parsed.
 *
 * <p>All this parsing and checking is done by {@link TransactionChecker}. It saves all that work in an instance
 * of this class, so we don't have to parse it all again later.</p>
 *
 * @param transaction   The transaction itself
 * @param txBody        the deserialized {@link TransactionBody} (either from the {@link Transaction#body()} or
 *                      from the {@link Transaction#signedTransactionBytes()}).
 * @param transactionID the validated {@link TransactionID} extracted from {@link #txBody}
 * @param payerID       the validated {@link AccountID} of the payer extracted from {@link #transactionID}
 * @param signatureMap  the {@link SignatureMap} (either from {@link Transaction#sigMap()} or
 *                      from {@link SignedTransaction#sigMap()}). Not all transactions require a signature map....
 * @param signedBytes   the bytes to use for signature verification
 * @param functionality the {@link HederaFunctionality} representing the transaction.
 */
public record TransactionInfo(
        @NonNull Transaction transaction,
        @NonNull TransactionBody txBody,
        @Nullable TransactionID transactionID,
        @Nullable AccountID payerID,
        @NonNull SignatureMap signatureMap,
        @NonNull Bytes signedBytes,
        @NonNull HederaFunctionality functionality,
        @Nullable Bytes serializedTransaction) {

    public TransactionInfo(
            @NonNull Transaction transaction,
            @NonNull TransactionBody txBody,
            @NonNull SignatureMap signatureMap,
            @NonNull Bytes signedBytes,
            @NonNull HederaFunctionality functionality,
            @Nullable Bytes serializedTransaction) {
        this(
                transaction,
                txBody,
                txBody.transactionIDOrThrow(),
                txBody.transactionIDOrThrow().accountIDOrThrow(),
                signatureMap,
                signedBytes,
                functionality,
                serializedTransaction);
    }

    public static TransactionInfo from(
            @NonNull Transaction transaction,
            @NonNull TransactionBody txBody,
            @NonNull SignatureMap signatureMap,
            @NonNull Bytes signedBytes,
            @NonNull HederaFunctionality functionality) {
        TransactionID transactionId = null;
        AccountID payerId = null;
        if (txBody.transactionID() != null) {
            transactionId = txBody.transactionID();
            if (transactionId.accountID() != null) {
                payerId = txBody.transactionID().accountID();
            }
        }
        return new TransactionInfo(
                transaction, txBody, transactionId, payerId, signatureMap, signedBytes, functionality, null);
    }

    /**
     * Returns the {@link TransactionID} of the transaction.
     * @return the transaction ID
     * @throws NullPointerException if the transaction ID is null
     */
    public TransactionID txnIdOrThrow() {
        return requireNonNull(transactionID);
    }
}
