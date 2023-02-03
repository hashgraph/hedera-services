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
package com.hedera.node.app.service.contract.impl.handlers;

import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_AUTORENEW_ACCOUNT;
import static com.hedera.node.app.service.mono.Utils.asHederaKey;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.contract.ContractUpdateTransactionBody;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.spi.AccountKeyLookup;
import com.hedera.node.app.spi.meta.SigTransactionMetadataBuilder;
import com.hedera.node.app.spi.meta.TransactionMetadata;
import com.hedera.node.app.spi.workflows.TransactionHandler;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * This class contains all workflow-related functionality regarding {@link
 * HederaFunctionality#CONTRACT_UPDATE}.
 */
public class ContractUpdateHandler implements TransactionHandler {

    /**
     * This method is called during the pre-handle workflow.
     *
     * <p>Typically, this method validates the {@link TransactionBody} semantically, gathers all
     * required keys, warms the cache, and creates the {@link TransactionMetadata} that is used in
     * the handle stage.
     *
     * <p>Please note: the method signature is just a placeholder which is most likely going to
     * change.
     *
     * @param txBody the {@link TransactionBody} with the transaction data
     * @param payer the {@link AccountID} of the payer
     * @return the {@link TransactionMetadata} with all information that needs to be passed to
     *     {@link #handle(TransactionMetadata)}
     * @throws NullPointerException if one of the arguments is {@code null}
     */
    public TransactionMetadata preHandle(
            @NonNull final TransactionBody txBody,
            @NonNull final AccountID payer,
            @NonNull final AccountKeyLookup keyLookup) {
        final var op = txBody.contractUpdateInstance().orElseThrow();
        final var meta =
                new SigTransactionMetadataBuilder(keyLookup).txnBody(txBody).payerKeyFor(payer);

        if (isAdminSigRequired(op)) {
            meta.addNonPayerKey(op.contractID());
        }
//        if (hasCryptoAdminKey(op)) {
//            final var key = asHederaKey(op.adminKey());
//            key.ifPresent(meta::addToReqNonPayerKeys);
//        }
//        if (op.autoRenewAccountId()
//                && !op.autoRenewAccountId().equals(AccountID.getDefaultInstance())) {
//            meta.addNonPayerKey(op.autoRenewAccountId(), INVALID_AUTORENEW_ACCOUNT);
//        }
        return meta.build();
    }

    private boolean isAdminSigRequired(final ContractUpdateTransactionBody op) {
        return op.expirationTime() == null
                || hasCryptoAdminKey(op)
                || (op.proxyAccountID() != null)
                || (op.autoRenewPeriod() != null)
                || (op.fileID() != null)
                || op.memo().isPresent() && op.memo().get().length() > 0;
    }

    private boolean hasCryptoAdminKey(final ContractUpdateTransactionBody op) {
        final var adminKey = op.adminKey();
        return adminKey != null && adminKey.contractID().isEmpty();
    }

    /**
     * This method is called during the handle workflow. It executes the actual transaction.
     *
     * <p>Please note: the method signature is just a placeholder which is most likely going to
     * change.
     *
     * @param metadata the {@link TransactionMetadata} that was generated during pre-handle.
     * @throws NullPointerException if one of the arguments is {@code null}
     */
    public void handle(@NonNull final TransactionMetadata metadata) {
        throw new UnsupportedOperationException("Not implemented");
    }
}
