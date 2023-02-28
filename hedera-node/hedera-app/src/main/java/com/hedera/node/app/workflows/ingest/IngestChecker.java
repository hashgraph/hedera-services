/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.workflows.ingest;

import static com.hedera.node.app.service.mono.state.submerkle.TxnId.USER_TRANSACTION_NONCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_NODE_ACCOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TRANSACTION_ID_FIELD_NOT_ALLOWED;
import static java.util.Objects.requireNonNull;

import com.hedera.node.app.service.mono.txns.submission.SolvencyPrecheck;
import com.hedera.node.app.service.mono.utils.EntityNum;
import com.hedera.node.app.service.mono.utils.accessors.SignedTxnAccessor;
import com.hedera.node.app.signature.SignaturePreparer;
import com.hedera.node.app.spi.accounts.Account;
import com.hedera.node.app.spi.workflows.InsufficientBalanceException;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.state.HederaState;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.SignatureMap;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.nio.ByteBuffer;
import java.util.Objects;
import javax.inject.Inject;

/**
 * The {@code IngestChecker} contains checks that are specific to the ingest workflow
 */
public class IngestChecker {

    private final AccountID nodeAccountID;
    private final SolvencyPrecheck solvencyPrecheck;
    private final SignaturePreparer signaturePreparer;

    /**
     * Constructor of the {@code IngestChecker}
     *
     * @param nodeAccountID     the {@link AccountID} of the <em>node</em>
     * @param solvencyPrecheck  the {@link SolvencyPrecheck} that checks payer balance
     * @param signaturePreparer the {@link SignaturePreparer} that prepares signature data
     * @throws NullPointerException if one of the arguments is {@code null}
     */
    @Inject
    public IngestChecker(
            @NonNull final AccountID nodeAccountID,
            @NonNull final SolvencyPrecheck solvencyPrecheck,
            @NonNull final SignaturePreparer signaturePreparer) {
        this.nodeAccountID = requireNonNull(nodeAccountID);
        this.solvencyPrecheck = solvencyPrecheck;
        this.signaturePreparer = requireNonNull(signaturePreparer);
    }

    /**
     * Checks a transaction for semantic errors
     *
     * @param txBody        the {@link TransactionBody}
     * @param functionality the {@link HederaFunctionality} of the transaction
     * @throws NullPointerException if one of the arguments is {@code null}
     * @throws PreCheckException    if a semantic error was discovered. The contained {@code responseCode} provides the
     *                              error reason.
     */
    public void checkTransactionSemantics(
            @NonNull final TransactionBody txBody, @NonNull final HederaFunctionality functionality)
            throws PreCheckException {
        requireNonNull(txBody);
        requireNonNull(functionality);

        if (!Objects.equals(nodeAccountID, txBody.getNodeAccountID())) {
            throw new PreCheckException(INVALID_NODE_ACCOUNT);
        }

        var txnId = txBody.getTransactionID();
        if (txnId.getScheduled() || txnId.getNonce() != USER_TRANSACTION_NONCE) {
            throw new PreCheckException(TRANSACTION_ID_FIELD_NOT_ALLOWED);
        }
    }

    /**
     * Checks the signature of the payer. <em>Currently not implemented.</em>
     *
     * @param state         the {@link HederaState} that should be used to read state
     * @param transaction   the relevant {@link Transaction}
     * @param signatureMap  the {@link SignatureMap} contained in the transaction
     * @param payerID       the {@link AccountID} of the payer
     * @throws NullPointerException if one of the arguments is {@code null}
     * @throws PreCheckException    if an error is found while checking the signature. The contained {@code responseCode}
     *                              provides the error reason.
     */
    public void checkPayerSignature(
            @NonNull final HederaState state,
            @NonNull final Transaction transaction,
            @NonNull final SignatureMap signatureMap,
            @NonNull final AccountID payerID)
            throws PreCheckException {
        // TODO - replace with a refactored version of the keys and signatures API
        final var payerSigStatus = signaturePreparer.syncGetPayerSigStatus(transaction);

        if (payerSigStatus != OK) {
            throw new PreCheckException(payerSigStatus);
        }
    }

    /**
     * Checks the solvency of the payer account for the given transaction.
     * <p>
     * TODO - replace with a refactored version of the mono solvency check
     *
     * @param transaction the {@link Transaction} in question
     * @throws NullPointerException         if any argument is {@code null}
     * @throws InsufficientBalanceException if the payer balance is not sufficient
     */
    public void checkSolvency(@NonNull final Transaction transaction) throws PreCheckException {
        final var accessor = SignedTxnAccessor.uncheckedFrom(transaction);
        final var payerNum = EntityNum.fromAccountId(accessor.getPayer());
        final var payerStatus = solvencyPrecheck.payerAccountStatus(payerNum);
        if (payerStatus != OK) {
            throw new PreCheckException(payerStatus);
        }
        final var solvencySummary = solvencyPrecheck.solvencyOfVerifiedPayer(accessor, false);
        if (solvencySummary.getValidity() != OK) {
            throw new InsufficientBalanceException(solvencySummary.getValidity(), solvencySummary.getRequiredFee());
        }
    }

    /**
     * Extracts a {@code byte[]} from a {@link ByteBuffer}. The {@code byte[]} may contain a copy or point to the data
     * of the {@code ByteBuffer} directly, i.e. the content should not be modified.
     *
     * @param buffer the {@link ByteBuffer} from which to extract the data
     * @return the {@code byte[]} with the data
     */
    public byte[] extractByteArray(@NonNull final ByteBuffer buffer) {
        requireNonNull(buffer);
        if (buffer.hasArray()) {
            return buffer.array();
        } else {
            final var byteArray = new byte[buffer.limit()];
            buffer.get(byteArray);
            return byteArray;
        }
    }
}
