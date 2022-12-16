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
package com.hedera.node.app.service.mono.token.impl;

import static com.hedera.node.app.service.mono.Utils.asHederaKey;
import static com.hedera.node.app.service.mono.utils.EntityIdUtils.isAlias;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.*;

import com.google.common.annotations.VisibleForTesting;
import com.hedera.node.app.service.token.CryptoPreTransactionHandler;
import com.hedera.node.app.spi.KeyOrLookupFailureReason;
import com.hedera.node.app.spi.PreHandleContext;
import com.hedera.node.app.spi.key.HederaKey;
import com.hedera.node.app.spi.meta.SigTransactionMetadataBuilder;
import com.hedera.node.app.spi.meta.TransactionMetadata;
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CryptoTransferTransactionBody;
import com.hederahashgraph.api.proto.java.NftTransfer;
import com.hederahashgraph.api.proto.java.TransactionBody;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.apache.commons.lang3.NotImplementedException;

/**
 * A {@code CryptoPreTransactionHandler} implementation that pre-computes the required signing keys
 * (but not the candidate signatures) for each crypto operation.
 */
public final class CryptoPreTransactionHandlerImpl implements CryptoPreTransactionHandler {
    private final TokenStore tokenStore;
    private final AccountStore accountStore;
    private CryptoSignatureWaiversImpl waivers;

    public CryptoPreTransactionHandlerImpl(
            @NonNull final TokenStore tokenStore,
            @NonNull final AccountStore accountStore,
            @NonNull final PreHandleContext ctx) {
        this.tokenStore = Objects.requireNonNull(tokenStore);
        this.accountStore = Objects.requireNonNull(accountStore);
        this.waivers = new CryptoSignatureWaiversImpl(Objects.requireNonNull(ctx).accountNumbers());
    }

    @Override
    /** {@inheritDoc} */
    public TransactionMetadata preHandleCryptoCreate(
            final TransactionBody tx, final AccountID payer) {
        final var op = tx.getCryptoCreateAccount();
        final var key = asHederaKey(op.getKey());
        final var receiverSigReq = op.getReceiverSigRequired();
        return createAccountSigningMetadata(tx, key, receiverSigReq, payer);
    }

    @Override
    /** {@inheritDoc} */
    public TransactionMetadata preHandleCryptoDelete(
            final TransactionBody txn, final AccountID payer) {
        final var op = txn.getCryptoDelete();
        final var deleteAccountId = op.getDeleteAccountID();
        final var transferAccountId = op.getTransferAccountID();
        final var meta =
                new SigTransactionMetadataBuilder(accountStore)
                        .payerKeyFor(payer)
                        .txnBody(txn)
                        .addNonPayerKey(deleteAccountId)
                        .addNonPayerKeyIfReceiverSigRequired(
                                transferAccountId, INVALID_TRANSFER_ACCOUNT_ID);
        return meta.build();
    }

    @Override
    /** {@inheritDoc} */
    public TransactionMetadata preHandleApproveAllowances(
            final TransactionBody txn, final AccountID payer) {
        final var op = txn.getCryptoApproveAllowance();
        final var meta =
                new SigTransactionMetadataBuilder(accountStore).payerKeyFor(payer).txnBody(txn);
        var failureStatus = INVALID_ALLOWANCE_OWNER_ID;

        for (final var allowance : op.getCryptoAllowancesList()) {
            meta.addNonPayerKey(allowance.getOwner(), failureStatus);
        }
        for (final var allowance : op.getTokenAllowancesList()) {
            meta.addNonPayerKey(allowance.getOwner(), failureStatus);
        }
        for (final var allowance : op.getNftAllowancesList()) {
            final var ownerId = allowance.getOwner();
            // If a spender who is granted approveForAll from owner and is granting
            // allowance for a serial to another spender, need signature from the approveForAll
            // spender
            var operatorId =
                    allowance.hasDelegatingSpender() ? allowance.getDelegatingSpender() : ownerId;
            // If approveForAll is set to true, need signature from owner
            // since only the owner can grant approveForAll
            if (allowance.getApprovedForAll().getValue()) {
                operatorId = ownerId;
            }
            if (operatorId != ownerId) {
                failureStatus = INVALID_DELEGATING_SPENDER;
            }
            meta.addNonPayerKey(operatorId, failureStatus);
        }
        return meta.build();
    }

    @Override
    /** {@inheritDoc} */
    public TransactionMetadata preHandleDeleteAllowances(
            final TransactionBody txn, AccountID payer) {
        final var op = txn.getCryptoDeleteAllowance();
        final var meta =
                new SigTransactionMetadataBuilder(accountStore).payerKeyFor(payer).txnBody(txn);
        // Every owner whose allowances are being removed should sign, if the owner is not payer
        for (final var allowance : op.getNftAllowancesList()) {
            meta.addNonPayerKey(allowance.getOwner(), INVALID_ALLOWANCE_OWNER_ID);
        }
        return meta.build();
    }

    @Override
    /** {@inheritDoc} */
    public TransactionMetadata preHandleUpdateAccount(final TransactionBody txn, AccountID payer) {
        final var op = txn.getCryptoUpdateAccount();
        final var updateAccountId = op.getAccountIDToUpdate();
        final var meta =
                new SigTransactionMetadataBuilder(accountStore).payerKeyFor(payer).txnBody(txn);

        final var newAccountKeyMustSign = !waivers.isNewKeySignatureWaived(txn, payer);
        final var targetAccountKeyMustSign = !waivers.isTargetAccountSignatureWaived(txn, payer);
        if (targetAccountKeyMustSign) {
            meta.addNonPayerKey(updateAccountId);
        }
        if (newAccountKeyMustSign && op.hasKey()) {
            final var candidate = asHederaKey(op.getKey());
            candidate.ifPresent(meta::addToReqNonPayerKeys);
        }
        return meta.build();
    }

    @Override
    /** {@inheritDoc} */
    public TransactionMetadata preHandleCryptoTransfer(
            final TransactionBody txn, final AccountID payer) {
        final var op = Objects.requireNonNull(txn).getCryptoTransfer();
        final var meta =
                new SigTransactionMetadataBuilder(accountStore).payerKeyFor(payer).txnBody(txn);
        for (final var transfers : op.getTokenTransfersList()) {
            final var tokenMeta = tokenStore.getTokenMeta(transfers.getToken());
            if (!tokenMeta.failed()) {
                handleTokenTransfers(transfers.getTransfersList(), meta);
                handleNftTransfers(transfers.getNftTransfersList(), meta, tokenMeta, op);
            } else {
                meta.status(tokenMeta.failureReason());
            }
        }
        handleHbarTransfers(op, meta);
        return meta.build();
    }

    @Override
    /** {@inheritDoc} */
    public TransactionMetadata preHandleAddLiveHash(
            final TransactionBody txn, final AccountID payer) {
        throw new NotImplementedException();
    }

    @Override
    /** {@inheritDoc} */
    public TransactionMetadata preHandleDeleteLiveHash(
            final TransactionBody txn, final AccountID payer) {
        throw new NotImplementedException();
    }

    /* --------------- Helper methods --------------- */

    /**
     * Returns metadata for {@code CryptoCreate} transaction needed to validate signatures needed
     * for signing the transaction
     *
     * @param txn given transaction body
     * @param key key provided in the transaction body
     * @param receiverSigReq flag for receiverSigReq on the given transaction body
     * @param payer payer for the transaction
     * @return transaction's metadata needed to validate signatures
     */
    private TransactionMetadata createAccountSigningMetadata(
            final TransactionBody txn,
            final Optional<HederaKey> key,
            final boolean receiverSigReq,
            final AccountID payer) {
        final var meta =
                new SigTransactionMetadataBuilder(accountStore).payerKeyFor(payer).txnBody(txn);
        if (receiverSigReq && key.isPresent()) {
            meta.addToReqNonPayerKeys(key.get());
        }
        return meta.build();
    }

    private void handleTokenTransfers(
            final List<AccountAmount> transfers, final SigTransactionMetadataBuilder meta) {
        for (AccountAmount accountAmount : transfers) {
            final var keyOrFailure = accountStore.getKey(accountAmount.getAccountID());
            if (!keyOrFailure.failed()) {
                final var isUnapprovedDebit =
                        accountAmount.getAmount() < 0 && !accountAmount.getIsApproval();
                if (isUnapprovedDebit) {
                    meta.addNonPayerKey(accountAmount.getAccountID());
                } else {
                    meta.addNonPayerKeyIfReceiverSigRequired(
                            accountAmount.getAccountID(), INVALID_TRANSFER_ACCOUNT_ID);
                }
            } else {
                final var isCredit = accountAmount.getAmount() > 0L;
                final var isMissingAcc =
                        isCredit
                                && keyOrFailure.failureReason().equals(INVALID_ACCOUNT_ID)
                                && isAlias(accountAmount.getAccountID());
                if (!isMissingAcc) {
                    meta.status(keyOrFailure.failureReason());
                }
            }
        }
    }

    private void handleNftTransfers(
            final List<NftTransfer> nftTransfersList,
            final SigTransactionMetadataBuilder meta,
            final TokenStore.TokenMetaOrLookupFailureReason tokenMeta,
            final CryptoTransferTransactionBody op) {
        for (final var nftTransfer : nftTransfersList) {
            if (nftTransfer.hasSenderAccountID()) {
                final var senderKeyOrFailure =
                        accountStore.getKey(nftTransfer.getSenderAccountID());
                if (!senderKeyOrFailure.failed()) {
                    if (!nftTransfer.getIsApproval()) {
                        meta.addNonPayerKey(nftTransfer.getSenderAccountID());
                    }
                } else {
                    meta.status(senderKeyOrFailure.failureReason());
                }

                final var receiverKeyOrFailure =
                        accountStore.getKeyIfReceiverSigRequired(
                                nftTransfer.getReceiverAccountID());
                if (!receiverKeyOrFailure.failed()) {
                    if (!receiverKeyOrFailure.equals(
                            KeyOrLookupFailureReason.PRESENT_BUT_NOT_REQUIRED)) {
                        meta.addNonPayerKeyIfReceiverSigRequired(
                                nftTransfer.getReceiverAccountID(), INVALID_TRANSFER_ACCOUNT_ID);
                    } else if (tokenMeta.metadata().hasRoyaltyWithFallback()
                            && !receivesFungibleValue(nftTransfer.getSenderAccountID(), op)) {
                        // Fallback situation; but we still need to check if the treasury is
                        // the sender or receiver, since in neither case will the fallback
                        // fee actually be charged
                        final var treasury = tokenMeta.metadata().treasury().toGrpcAccountId();
                        if (!treasury.equals(nftTransfer.getSenderAccountID())
                                && !treasury.equals(nftTransfer.getReceiverAccountID())) {
                            meta.addNonPayerKey(nftTransfer.getReceiverAccountID());
                        }
                    }
                } else {
                    final var isMissingAcc =
                            INVALID_ACCOUNT_ID.equals(receiverKeyOrFailure.failureReason())
                                    && isAlias(nftTransfer.getReceiverAccountID());
                    if (!isMissingAcc) {
                        meta.status(receiverKeyOrFailure.failureReason());
                    }
                }
            } else {
                meta.status(INVALID_ACCOUNT_ID);
            }
        }
    }

    private void handleHbarTransfers(
            final CryptoTransferTransactionBody op, final SigTransactionMetadataBuilder meta) {
        for (AccountAmount accountAmount : op.getTransfers().getAccountAmountsList()) {
            final var keyOrFailure = accountStore.getKey(accountAmount.getAccountID());

            if (!keyOrFailure.failed()) {
                final var isUnapprovedDebit =
                        accountAmount.getAmount() < 0 && !accountAmount.getIsApproval();
                if (isUnapprovedDebit) {
                    meta.addNonPayerKey(accountAmount.getAccountID());
                } else {
                    meta.addNonPayerKeyIfReceiverSigRequired(
                            accountAmount.getAccountID(), INVALID_TRANSFER_ACCOUNT_ID);
                }
            } else {
                final var isCredit = accountAmount.getAmount() > 0L;
                final var isImmutableAcc =
                        isCredit && keyOrFailure.failureReason().equals(ALIAS_IS_IMMUTABLE);
                final var isMissingAcc =
                        isCredit
                                && keyOrFailure.failureReason().equals(INVALID_ACCOUNT_ID)
                                && isAlias(accountAmount.getAccountID());
                if (!isImmutableAcc && !isMissingAcc) {
                    meta.status(keyOrFailure.failureReason());
                }
            }
        }
    }

    private boolean receivesFungibleValue(AccountID target, CryptoTransferTransactionBody op) {
        for (var adjust : op.getTransfers().getAccountAmountsList()) {
            if (adjust.getAmount() > 0 && adjust.getAccountID().equals(target)) {
                return true;
            }
        }
        for (var transfers : op.getTokenTransfersList()) {
            for (var adjust : transfers.getTransfersList()) {
                if (adjust.getAmount() > 0 && adjust.getAccountID().equals(target)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * @param waivers signature waivers for crypto service
     * @deprecated This method is needed for testing until {@link CryptoSignatureWaiversImpl} is
     *     implemented. FUTURE: This method should be removed once {@link
     *     CryptoSignatureWaiversImpl} is implemented.
     */
    @Deprecated(forRemoval = true)
    @VisibleForTesting
    void setWaivers(final CryptoSignatureWaiversImpl waivers) {
        this.waivers = waivers;
    }
}
