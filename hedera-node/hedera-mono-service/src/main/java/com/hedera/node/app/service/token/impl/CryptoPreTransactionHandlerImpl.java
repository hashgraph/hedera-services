/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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
package com.hedera.node.app.service.token.impl;

import static com.hedera.node.app.Utils.asHederaKey;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ALLOWANCE_OWNER_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_DELEGATING_SPENDER;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TRANSFER_ACCOUNT_ID;

import com.hedera.node.app.SigTransactionMetadata;
import com.hedera.node.app.service.token.CryptoPreTransactionHandler;
import com.hedera.node.app.spi.key.HederaKey;
import com.hedera.node.app.spi.meta.TransactionMetadata;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.util.Objects;
import java.util.Optional;
import javax.annotation.Nonnull;
import org.apache.commons.lang3.NotImplementedException;

/**
 * A {@code CryptoPreTransactionHandler} implementation that pre-computes the required signing keys
 * (but not the candidate signatures) for each crypto operation.
 */
public final class CryptoPreTransactionHandlerImpl implements CryptoPreTransactionHandler {
    private final AccountStore accountStore;

    public CryptoPreTransactionHandlerImpl(@Nonnull final AccountStore accountStore) {
        this.accountStore = Objects.requireNonNull(accountStore);
    }

    @Override
    /** {@inheritDoc} */
    public TransactionMetadata preHandleCryptoCreate(final TransactionBody tx) {
        final var op = tx.getCryptoCreateAccount();
        final var key = asHederaKey(op.getKey());
        final var receiverSigReq = op.getReceiverSigRequired();
        final var payer = tx.getTransactionID().getAccountID();
        return createAccountSigningMetadata(tx, key, receiverSigReq, payer);
    }

    @Override
    /** {@inheritDoc} */
    public TransactionMetadata preHandleCryptoDelete(TransactionBody txn) {
        final var op = txn.getCryptoDelete();
        final var payer = txn.getTransactionID().getAccountID();
        final var deleteAccountId = op.getDeleteAccountID();
        final var transferAccountId = op.getTransferAccountID();
        final var meta = new SigTransactionMetadata(accountStore, txn, payer);
        addIfNotPayer(deleteAccountId, payer, meta);
        addIfNotPayerAndReceiverSigRequired(
                transferAccountId, payer, meta, INVALID_TRANSFER_ACCOUNT_ID);
        return meta;
    }

    @Override
    /** {@inheritDoc} */
    public TransactionMetadata preHandleApproveAllowances(TransactionBody txn) {
        final var op = txn.getCryptoApproveAllowance();
        final var payer = txn.getTransactionID().getAccountID();
        final var meta = new SigTransactionMetadata(accountStore, txn, payer);
        var failureStatus = INVALID_ALLOWANCE_OWNER_ID;

        for (final var allowance : op.getCryptoAllowancesList()) {
            addIfNotPayer(allowance.getOwner(), payer, meta, failureStatus);
        }
        for (final var allowance : op.getTokenAllowancesList()) {
            addIfNotPayer(allowance.getOwner(), payer, meta, failureStatus);
        }
        for (final var allowance : op.getNftAllowancesList()) {
            final var ownerId = allowance.getOwner();
            var operatorId =
                    allowance.hasDelegatingSpender() ? allowance.getDelegatingSpender() : ownerId;
            // Since only the owner can grant approveForAll
            if (allowance.getApprovedForAll().getValue()) {
                operatorId = ownerId;
            }
            if (operatorId != ownerId) {
                failureStatus = INVALID_DELEGATING_SPENDER;
            }
            addIfNotPayer(operatorId, payer, meta, failureStatus);
        }
        return meta;
    }

    @Override
    /** {@inheritDoc} */
    public TransactionMetadata preHandleDeleteAllowances(TransactionBody txn) {
        throw new NotImplementedException();
    }

    @Override
    /** {@inheritDoc} */
    public TransactionMetadata preHandleUpdateAccount(TransactionBody txn) {
        throw new NotImplementedException();
    }

    @Override
    /** {@inheritDoc} */
    public TransactionMetadata preHandleCryptoTransfer(TransactionBody txn) {
        throw new NotImplementedException();
    }

    @Override
    /** {@inheritDoc} */
    public TransactionMetadata preHandleAddLiveHash(TransactionBody txn) {
        throw new NotImplementedException();
    }

    @Override
    /** {@inheritDoc} */
    public TransactionMetadata preHandleDeleteLiveHash(TransactionBody txn) {
        throw new NotImplementedException();
    }

    /* --------------- Helper methods --------------- */
    /**
     * Returns metadata for {@code CryptoCreate} transaction needed to validate signatures needed
     * for signing the transaction
     *
     * @param tx given transaction body
     * @param key key provided in the transaction body
     * @param receiverSigReq flag for receiverSigReq on the given transaction body
     * @param payer payer for the transaction
     * @return transaction's metadata needed to validate signatures
     */
    private TransactionMetadata createAccountSigningMetadata(
            final TransactionBody tx,
            final Optional<HederaKey> key,
            final boolean receiverSigReq,
            final AccountID payer) {
        final var meta = new SigTransactionMetadata(accountStore, tx, payer);
        if (receiverSigReq && key.isPresent()) {
            meta.addToReqKeys(key.get());
        }
        return meta;
    }

    /**
     * Fetches given accountId's key and add it to the metadata only if the accountId is not same as
     * payer. If the accountId could not be fetched successfully, sets the failure status on the
     * metadata.
     *
     * @param accountId given accountId
     * @param payer payer accountId
     * @param meta metadata to which accountId's key will be added, if success
     * @param failureStatus explicit failure status to be set on metadata
     */
    private void addIfNotPayer(
            final AccountID accountId,
            final AccountID payer,
            final SigTransactionMetadata meta,
            final ResponseCodeEnum failureStatus) {
        if (isFailed(accountId, payer, meta)) {
            return;
        }
        var result = accountStore.getKey(accountId);
        if (result.failed()) {
            meta.setStatus(failureStatus != null ? failureStatus : result.failureReason());
        } else {
            meta.addToReqKeys(result.key());
        }
    }

    /**
     * Convenience method for having a default failure status set on metadata if there is a failure.
     *
     * @param accountId given accountId
     * @param payer payer accountId
     * @param meta metadata to which accountId's key will be added to the requiredKeys, if success
     */
    private void addIfNotPayer(
            final AccountID accountId, final AccountID payer, final SigTransactionMetadata meta) {
        addIfNotPayer(accountId, payer, meta, null);
    }

    /**
     * Fetches given accountId's key and add it to the metadata requiredKeys list only if the
     * accountId is not same as payer and the account has receiverSigRequired flag set to true. If
     * the accountId have receiverSigRequired flag set false, the key will not be added metadata. If
     * the accountId could not be fetched successfully, sets the failure status on the metadata.
     *
     * @param accountId given accountId
     * @param payer payer accountId
     * @param meta metadata to which accountId's key will be added to the requiredKeys, if success
     * @param failureStatus explicit failure status to be set on metadata
     */
    private void addIfNotPayerAndReceiverSigRequired(
            final AccountID accountId,
            final AccountID payer,
            final SigTransactionMetadata meta,
            final ResponseCodeEnum failureStatus) {
        if (isFailed(accountId, payer, meta)) {
            return;
        }
        var result = accountStore.getKeyIfReceiverSigRequired(accountId);
        if (result.failed()) {
            meta.setStatus(failureStatus != null ? failureStatus : result.failureReason());
        } else if (result.key() != null) {
            meta.addToReqKeys(result.key());
        }
    }

    /**
     * Convenience method for having a default failure status set on metadata if there is a failure.
     *
     * @param accountId given accountId
     * @param payer payer accountId
     * @param meta metadata to which accountId's key will be added to the requiredKeys, if success
     */
    private void addIfNotPayerAndReceiverSigRequired(
            final AccountID accountId, final AccountID payer, final SigTransactionMetadata meta) {
        addIfNotPayerAndReceiverSigRequired(accountId, payer, meta, null);
    }

    private boolean isFailed(
            final AccountID accountId, final AccountID payer, final SigTransactionMetadata meta) {
        return meta.failed()
                || payer.equals(accountId)
                || accountId.equals(AccountID.getDefaultInstance());
    }
}
