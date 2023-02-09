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

import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_NODE_ACCOUNT;
import static com.hedera.hapi.node.base.ResponseCodeEnum.TRANSACTION_ID_FIELD_NOT_ALLOWED;
import static com.hedera.node.app.service.mono.state.submerkle.TxnId.USER_TRANSACTION_NONCE;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.base.SignatureMap;
import com.hedera.node.app.service.token.entity.Account;
import com.hedera.node.app.spi.workflows.InsufficientBalanceException;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.transaction.TransactionBody;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** The {@code IngestChecker} contains checks that are specific to the ingest workflow */
public class IngestChecker {

    private static final Logger LOG = LoggerFactory.getLogger(IngestChecker.class);

    private final AccountID nodeAccountID;

    /**
     * Constructor of the {@code IngestChecker}
     *
     * @param nodeAccountID the {@link AccountID} of the <em>node</em>
     * @throws NullPointerException if one of the arguments is {@code null}
     */
    public IngestChecker(@NonNull final AccountID nodeAccountID) {
        this.nodeAccountID = requireNonNull(nodeAccountID);
    }

    /**
     * Checks a transaction for semantic errors
     *
     * @param txBody the {@link TransactionBody}
     * @param functionality the {@link HederaFunctionality} of the transaction
     * @throws NullPointerException if one of the arguments is {@code null}
     * @throws PreCheckException if a semantic error was discovered. The contained {@code
     *     responseCode} provides the error reason.
     */
    public void checkTransactionSemantics(
            @NonNull final TransactionBody txBody, @NonNull final HederaFunctionality functionality)
            throws PreCheckException {
        requireNonNull(txBody);
        requireNonNull(functionality);

        if (!Objects.equals(nodeAccountID, txBody.nodeAccountID())) {
            throw new PreCheckException(INVALID_NODE_ACCOUNT);
        }

        var txnId = txBody.transactionID();
        if (txnId.scheduled() || txnId.nonce() != USER_TRANSACTION_NONCE) {
            throw new PreCheckException(TRANSACTION_ID_FIELD_NOT_ALLOWED);
        }
    }

    /**
     * Checks the signature of the payer. <em>Currently not implemented.</em>
     *
     * @param txBody the {@link TransactionBody}
     * @param signatureMap the {@link SignatureMap} contained in the transaction
     * @param payer the {@code Account} of the payer
     * @throws NullPointerException if one of the arguments is {@code null}
     * @throws PreCheckException if an error is found while checking the signature. The contained
     *     {@code responseCode} provides the error reason.
     */
    public void checkPayerSignature(
            @NonNull final TransactionBody txBody,
            @NonNull final SignatureMap signatureMap,
            @NonNull final Account payer)
            throws PreCheckException {
        LOG.warn("IngestChecker.checkPayerSignature() has not been implemented yet");
        // TODO: Implement once signature check is implemented
    }

    /**
     * Checks the solvency of the payer
     *
     * @param txBody the {@link TransactionBody}
     * @param functionality the {@link HederaFunctionality} of the transaction
     * @param payer the {@code Account} of the payer
     * @throws NullPointerException if one of the arguments is {@code null}
     * @throws InsufficientBalanceException if the balance is sufficient
     */
    public void checkSolvency(
            @NonNull final TransactionBody txBody,
            @NonNull final HederaFunctionality functionality,
            @NonNull final Account payer)
            throws InsufficientBalanceException {
        LOG.warn("IngestChecker.checkSolvency() has not been implemented yet");
        // TODO: Implement once fee calculation is implemented
    }
}
