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
package com.hedera.node.app.service.token.impl.handlers;

import static com.hedera.hapi.node.base.ResponseCodeEnum.ALIAS_IS_IMMUTABLE;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TRANSFER_ACCOUNT_ID;

import com.hedera.hapi.node.base.AccountAmount;
import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.base.NftTransfer;
import com.hedera.hapi.node.token.CryptoTransferTransactionBody;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.token.impl.ReadableAccountStore;
import com.hedera.node.app.service.token.impl.ReadableTokenStore;
import com.hedera.node.app.spi.AccountKeyLookup;
import com.hedera.node.app.spi.KeyOrLookupFailureReason;
import com.hedera.node.app.spi.meta.SigTransactionMetadataBuilder;
import com.hedera.node.app.spi.meta.TransactionMetadata;
import com.hedera.node.app.spi.workflows.TransactionHandler;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;

/**
 * This class contains all workflow-related functionality regarding {@link
 * HederaFunctionality#CRYPTO_TRANSFER}.
 */
public class CryptoTransferHandler implements TransactionHandler {

    /**
     * Pre-handles a {@link HederaFunctionality#CRYPTO_TRANSFER}
     * transaction, returning the metadata required to, at minimum, validate the signatures of all
     * required signing keys.
     *
     * @param txn the {@link TransactionBody} with the transaction data
     * @param payer the {@link AccountID} of the payer
     * @param accountStore the {@link AccountKeyLookup} to use to resolve keys
     * @param tokenStore the {@link ReadableTokenStore} to use to resolve token metadata
     * @return the {@link TransactionMetadata} with all information that needs to be passed to
     *     {@link #handle(TransactionMetadata)}
     * @throws NullPointerException if one of the arguments is {@code null}
     */
    public TransactionMetadata preHandle(
            @NonNull final TransactionBody txn,
            @NonNull final AccountID payer,
            @NonNull final ReadableAccountStore accountStore,
            @NonNull final ReadableTokenStore tokenStore) {
        final var op = txn.cryptoTransfer().orElseThrow();
        final var meta =
                new SigTransactionMetadataBuilder(accountStore).payerKeyFor(payer).txnBody(txn);
        for (final var transfers : op.tokenTransfers()) {
            final var tokenMeta = tokenStore.getTokenMeta(transfers.token());
            if (!tokenMeta.failed()) {
                handleTokenTransfers(transfers.transfers(), meta, accountStore);
                handleNftTransfers(transfers.nftTransfers(), meta, tokenMeta, op, accountStore);
            } else {
                meta.status(tokenMeta.failureReason());
            }
        }
        handleHbarTransfers(op, meta, accountStore);
        return meta.build();
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

    private void handleTokenTransfers(
            final List<AccountAmount> transfers,
            final SigTransactionMetadataBuilder meta,
            final ReadableAccountStore accountStore) {
        for (AccountAmount accountAmount : transfers) {
            final var keyOrFailure = accountStore.getKey(accountAmount.accountID());
            if (!keyOrFailure.failed()) {
                final var isUnapprovedDebit =
                        accountAmount.amount() < 0 && !accountAmount.isApproval();
                if (isUnapprovedDebit) {
                    meta.addNonPayerKey(accountAmount.accountID());
                } else {
                    meta.addNonPayerKeyIfReceiverSigRequired(
                            accountAmount.accountID(), INVALID_TRANSFER_ACCOUNT_ID);
                }
            } else {
                final var isCredit = accountAmount.amount() > 0L;
                final var isMissingAcc =
                        isCredit
                                && INVALID_ACCOUNT_ID.equals(keyOrFailure.failureReason())
                                && accountStore.isAlias(accountAmount.accountID());
                if (!isMissingAcc) {
                    meta.status(keyOrFailure.failureReason());
                }
            }
        }
    }

    private void handleNftTransfers(
            final List<NftTransfer> nftTransfersList,
            final SigTransactionMetadataBuilder meta,
            final ReadableTokenStore.TokenMetaOrLookupFailureReason tokenMeta,
            final CryptoTransferTransactionBody op,
            final ReadableAccountStore accountStore) {
        for (final var nftTransfer : nftTransfersList) {
            final var senderKeyOrFailure = accountStore.getKey(nftTransfer.senderAccountID());
            if (!senderKeyOrFailure.failed()) {
                if (!nftTransfer.isApproval()) {
                    meta.addNonPayerKey(nftTransfer.senderAccountID());
                }
            } else {
                meta.status(senderKeyOrFailure.failureReason());
            }

            final var receiverKeyOrFailure =
                    accountStore.getKeyIfReceiverSigRequired(nftTransfer.receiverAccountID());
            if (!receiverKeyOrFailure.failed()) {
                if (!receiverKeyOrFailure.equals(
                        KeyOrLookupFailureReason.PRESENT_BUT_NOT_REQUIRED)) {
                    meta.addNonPayerKeyIfReceiverSigRequired(
                            nftTransfer.receiverAccountID(), INVALID_TRANSFER_ACCOUNT_ID);
                } else if (tokenMeta.metadata().hasRoyaltyWithFallback()
                        && !receivesFungibleValue(nftTransfer.senderAccountID(), op)) {
                    // Fallback situation; but we still need to check if the treasury is
                    // the sender or receiver, since in neither case will the fallback
                    // fee actually be charged
                    final var treasury = tokenMeta.metadata().treasury().toGrpcAccountId();
                    if (!treasury.equals(nftTransfer.senderAccountID())
                            && !treasury.equals(nftTransfer.receiverAccountID())) {
                        meta.addNonPayerKey(nftTransfer.receiverAccountID());
                    }
                }
            } else {
                final var isMissingAcc =
                        INVALID_ACCOUNT_ID.equals(receiverKeyOrFailure.failureReason())
                                && accountStore.isAlias(nftTransfer.receiverAccountID());
                if (!isMissingAcc) {
                    meta.status(receiverKeyOrFailure.failureReason());
                }
            }
        }
    }

    private void handleHbarTransfers(
            final CryptoTransferTransactionBody op,
            final SigTransactionMetadataBuilder meta,
            final ReadableAccountStore accountStore) {
        for (AccountAmount accountAmount : op.transfers().accountAmounts()) {
            final var keyOrFailure = accountStore.getKey(accountAmount.accountID());

            if (!keyOrFailure.failed()) {
                final var isUnapprovedDebit =
                        accountAmount.amount() < 0 && !accountAmount.isApproval();
                if (isUnapprovedDebit) {
                    meta.addNonPayerKey(accountAmount.accountID());
                } else {
                    meta.addNonPayerKeyIfReceiverSigRequired(
                            accountAmount.accountID(), INVALID_TRANSFER_ACCOUNT_ID);
                }
            } else {
                final var isCredit = accountAmount.amount() > 0L;
                final var isImmutableAcc =
                        isCredit && ALIAS_IS_IMMUTABLE.equals(keyOrFailure.failureReason());
                final var isMissingAcc =
                        isCredit
                                && INVALID_ACCOUNT_ID.equals(keyOrFailure.failureReason())
                                && accountStore.isAlias(accountAmount.accountID());
                if (!isImmutableAcc && !isMissingAcc) {
                    meta.status(keyOrFailure.failureReason());
                }
            }
        }
    }

    private boolean receivesFungibleValue(
            final AccountID target, final CryptoTransferTransactionBody op) {
        for (var adjust : op.transfers().accountAmounts()) {
            if (adjust.amount() > 0 && adjust.accountID().equals(target)) {
                return true;
            }
        }
        for (var transfers : op.tokenTransfers()) {
            for (var adjust : transfers.transfers()) {
                if (adjust.amount() > 0 && adjust.accountID().equals(target)) {
                    return true;
                }
            }
        }
        return false;
    }
}
