// SPDX-License-Identifier: Apache-2.0
package com.hedera.node.app.workflows;

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
        @NonNull TransactionID transactionID,
        @NonNull AccountID payerID,
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
}
