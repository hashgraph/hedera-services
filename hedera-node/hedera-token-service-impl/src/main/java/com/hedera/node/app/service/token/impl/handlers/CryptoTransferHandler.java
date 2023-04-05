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
import static com.hedera.node.app.service.mono.pbj.PbjConverter.toPbj;
import static java.util.Collections.emptyList;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.AccountAmount;
import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.base.NftTransfer;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.base.TransferList;
import com.hedera.hapi.node.token.CryptoTransferTransactionBody;
import com.hedera.hapi.node.transaction.Query;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.token.impl.ReadableAccountStore;
import com.hedera.node.app.service.token.impl.ReadableTokenStore;
import com.hedera.node.app.service.token.impl.ReadableTokenStore.TokenMetaOrLookupFailureReason;
import com.hedera.node.app.spi.KeyOrLookupFailureReason;
import com.hedera.node.app.spi.accounts.AccountAccess;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.spi.workflows.PreHandleContext;
import com.hedera.node.app.spi.workflows.TransactionHandler;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * This class contains all workflow-related functionality regarding {@link
 * HederaFunctionality#CRYPTO_TRANSFER}.
 */
@Singleton
public class CryptoTransferHandler implements TransactionHandler {
    @Inject
    public CryptoTransferHandler() {
        // Exists for injection
    }

    /**
     * Validates a {@link HederaFunctionality#CRYPTO_TRANSFER} that is part of a {@link Query}.
     *
     * @param txn the {@link TransactionBody} of the {@code CryptoTransfer}
     * @throws PreCheckException if validation fails
     */
    public void validate(@NonNull final TransactionBody txn) throws PreCheckException {
        requireNonNull(txn);
        // FUTURE: Migrate validation from CryptoTransferTransistionLogic.validateSemantics()
        throw new UnsupportedOperationException("Not implemented");
    }

    /**
     * Pre-handles a {@link HederaFunctionality#CRYPTO_TRANSFER} transaction, returning the metadata
     * required to, at minimum, validate the signatures of all required signing keys.
     *
     * @param context the {@link PreHandleContext} which collects all information
     *
     * @param accountStore the {@link AccountAccess} to use to resolve keys
     * @param tokenStore the {@link ReadableTokenStore} to use to resolve token metadata
     * @throws NullPointerException if one of the arguments is {@code null}
     */
    public void preHandle(
            @NonNull final PreHandleContext context,
            @NonNull final ReadableAccountStore accountStore,
            @NonNull final ReadableTokenStore tokenStore) {
        requireNonNull(context);
        requireNonNull(accountStore);
        requireNonNull(tokenStore);
        final var op = context.getTxn().cryptoTransferOrThrow();
        for (final var transfers : op.tokenTransfersOrElse(emptyList())) {
            final var tokenMeta = tokenStore.getTokenMeta(transfers.tokenOrElse(TokenID.DEFAULT));
            if (!tokenMeta.failed()) {
                handleTokenTransfers(transfers.transfersOrElse(emptyList()), context, accountStore);
                handleNftTransfers(transfers.nftTransfersOrElse(emptyList()), context, tokenMeta, op, accountStore);
            } else {
                context.status(tokenMeta.failureReason());
            }
        }
        handleHbarTransfers(op, context, accountStore);
    }

    /**
     * This method is called during the handle workflow. It executes the actual transaction.
     *
     * <p>Please note: the method signature is just a placeholder which is most likely going to
     * change.
     *
     * @throws NullPointerException if one of the arguments is {@code null}
     */
    public void handle() {
        // TODO : Need to implement this method when we are ready to validate payments for query
        throw new UnsupportedOperationException("Not implemented");
    }

    private void handleTokenTransfers(
            final List<AccountAmount> transfers, final PreHandleContext meta, final ReadableAccountStore accountStore) {
        for (final AccountAmount accountAmount : transfers) {
            final var accountID = accountAmount.accountIDOrElse(AccountID.DEFAULT);
            final var keyOrFailure = accountStore.getKey(accountID);
            if (!keyOrFailure.failed()) {
                final var isUnapprovedDebit = accountAmount.amount() < 0 && !accountAmount.isApproval();
                if (isUnapprovedDebit) {
                    meta.addNonPayerKey(accountID);
                } else {
                    meta.addNonPayerKeyIfReceiverSigRequired(accountID, INVALID_TRANSFER_ACCOUNT_ID);
                }
            } else {
                final var failureReason = keyOrFailure.failureReason();
                final var isCredit = accountAmount.amount() > 0L;
                final var isMissingAcc = isCredit && INVALID_ACCOUNT_ID.equals(failureReason) && isAlias(accountID);
                if (!isMissingAcc && failureReason != null) {
                    // failureReason should not be null as we have already checked for keyOrFailure.failed()
                    // Only added this check to avoid the warning
                    meta.status(failureReason);
                }
            }
        }
    }

    private void handleNftTransfers(
            final List<NftTransfer> nftTransfersList,
            final PreHandleContext meta,
            final ReadableTokenStore.TokenMetaOrLookupFailureReason tokenMeta,
            final CryptoTransferTransactionBody op,
            final ReadableAccountStore accountStore) {
        for (final var nftTransfer : nftTransfersList) {
            final var senderId = nftTransfer.senderAccountIDOrElse(AccountID.DEFAULT);
            handleSender(senderId, nftTransfer, meta, accountStore);

            final var receiverId = nftTransfer.receiverAccountIDOrElse(AccountID.DEFAULT);
            handleReceiver(receiverId, senderId, nftTransfer, meta, tokenMeta, op, accountStore);
        }
    }

    private void handleReceiver(
            final AccountID receiverId,
            final AccountID senderId,
            final NftTransfer nftTransfer,
            final PreHandleContext meta,
            final TokenMetaOrLookupFailureReason tokenMeta,
            final CryptoTransferTransactionBody op,
            final ReadableAccountStore accountStore) {
        final var receiverKeyOrFailure = accountStore.getKeyIfReceiverSigRequired(receiverId);
        if (!receiverKeyOrFailure.failed()) {
            if (!receiverKeyOrFailure.equals(KeyOrLookupFailureReason.PRESENT_BUT_NOT_REQUIRED)) {
                meta.addNonPayerKeyIfReceiverSigRequired(receiverId, INVALID_TRANSFER_ACCOUNT_ID);
            } else if (tokenMeta.metadata().hasRoyaltyWithFallback()
                    && !receivesFungibleValue(nftTransfer.senderAccountID(), op, accountStore)) {
                // Fallback situation; but we still need to check if the treasury is
                // the sender or receiver, since in neither case will the fallback
                // fee actually be charged
                final var treasury = toPbj(tokenMeta.metadata().treasury().toGrpcAccountId());
                if (!treasury.equals(senderId) && !treasury.equals(receiverId)) {
                    meta.addNonPayerKey(receiverId);
                }
            }
        } else {
            final var failureReason = receiverKeyOrFailure.failureReason();
            final var isMissingAcc = INVALID_ACCOUNT_ID.equals(failureReason)
                    && isAlias(nftTransfer.receiverAccountIDOrElse(AccountID.DEFAULT));
            if (!isMissingAcc && failureReason != null) {
                // failureReason should not be null as we have already checked for receiverKeyOrFailure.failed()
                // Only added this check to avoid the warning
                meta.status(failureReason);
            }
        }
    }

    private void handleSender(
            final AccountID senderId,
            final NftTransfer nftTransfer,
            final PreHandleContext meta,
            final ReadableAccountStore accountStore) {
        final var senderKeyOrFailure = accountStore.getKey(senderId);
        if (!senderKeyOrFailure.failed()) {
            if (!nftTransfer.isApproval()) {
                meta.addNonPayerKey(senderId);
            }
        } else if (senderKeyOrFailure.failureReason() != null) {
            // failureReason should not be null as we have already checked for senderKeyOrFailure.failed()
            // Only added this check to avoid the warning
            meta.status(senderKeyOrFailure.failureReason());
        }
    }

    private void handleHbarTransfers(
            final CryptoTransferTransactionBody op, final PreHandleContext meta, final AccountAccess keyLookup) {
        for (final var accountAmount : op.transfersOrElse(TransferList.DEFAULT).accountAmountsOrElse(emptyList())) {
            final var accountId = accountAmount.accountIDOrElse(AccountID.DEFAULT);
            final var keyOrFailure = keyLookup.getKey(accountId);

            if (!keyOrFailure.failed()) {
                final var isUnapprovedDebit = accountAmount.amount() < 0 && !accountAmount.isApproval();
                if (isUnapprovedDebit) {
                    meta.addNonPayerKey(accountId);
                } else {
                    meta.addNonPayerKeyIfReceiverSigRequired(accountId, INVALID_TRANSFER_ACCOUNT_ID);
                }
            } else {
                final var failureReason = keyOrFailure.failureReason();
                final var isCredit = accountAmount.amount() > 0L;
                final var isImmutableAcc = isCredit && ALIAS_IS_IMMUTABLE.equals(failureReason);
                final var isMissingAcc = isCredit && INVALID_ACCOUNT_ID.equals(failureReason) && isAlias(accountId);
                if (!isImmutableAcc && !isMissingAcc && failureReason != null) {
                    // failureReason should not be null as we have already checked for keyOrFailure.failed()
                    // Only added this check to avoid the warning
                    meta.status(failureReason);
                }
            }
        }
    }

    private boolean receivesFungibleValue(
            final AccountID target, final CryptoTransferTransactionBody op, final ReadableAccountStore accountStore) {
        for (final var adjust : op.transfersOrElse(TransferList.DEFAULT).accountAmountsOrElse(emptyList())) {
            final var unaliasedAccount = accountStore.getAccountById(adjust.accountIDOrElse(AccountID.DEFAULT));
            final var unaliasedTarget = accountStore.getAccountById(target);
            if (unaliasedAccount.isPresent()
                    && unaliasedTarget.isPresent()
                    && adjust.amount() > 0
                    && unaliasedAccount.equals(unaliasedTarget)) {
                return true;
            }
        }
        for (final var transfers : op.tokenTransfersOrElse(emptyList())) {
            for (final var adjust : transfers.transfersOrElse(emptyList())) {
                final var unaliasedAccount = accountStore.getAccountById(adjust.accountIDOrElse(AccountID.DEFAULT));
                final var unaliasedTarget = accountStore.getAccountById(target);
                if (unaliasedAccount.isPresent()
                        && unaliasedTarget.isPresent()
                        && adjust.amount() > 0
                        && unaliasedAccount.equals(unaliasedTarget)) {
                    return true;
                }
            }
        }
        return false;
    }

    public static boolean isAlias(final AccountID idOrAlias) {
        return !idOrAlias.hasAccountNum() && idOrAlias.hasAlias();
    }
}
