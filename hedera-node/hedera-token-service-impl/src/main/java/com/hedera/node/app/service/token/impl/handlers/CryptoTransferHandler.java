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

import static com.hedera.hapi.node.base.ResponseCodeEnum.ACCOUNT_IS_IMMUTABLE;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TRANSFER_ACCOUNT_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TREASURY_ACCOUNT_FOR_TOKEN;
import static com.hedera.node.app.spi.validation.Validations.validateAccountID;
import static java.util.Collections.emptyList;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.AccountAmount;
import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.base.Key.KeyOneOfType;
import com.hedera.hapi.node.base.NftTransfer;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.base.TransferList;
import com.hedera.hapi.node.token.CryptoTransferTransactionBody;
import com.hedera.hapi.node.transaction.Query;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.token.impl.ReadableAccountStore;
import com.hedera.node.app.service.token.impl.ReadableTokenStore;
import com.hedera.node.app.service.token.impl.ReadableTokenStore.TokenMetadata;
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
        // FUTURE: Migrate validation from CryptoTransferTransitionLogic.validateSemantics()
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
            @NonNull final ReadableTokenStore tokenStore)
            throws PreCheckException {
        requireNonNull(accountStore);
        requireNonNull(tokenStore);
        final var op = context.body().cryptoTransferOrThrow();
        for (final var transfers : op.tokenTransfersOrElse(emptyList())) {
            final var tokenMeta = tokenStore.getTokenMeta(transfers.tokenOrElse(TokenID.DEFAULT));
            if (tokenMeta == null) throw new PreCheckException(INVALID_TOKEN_ID);
            checkFungibleTokenTransfers(transfers.transfersOrElse(emptyList()), context, accountStore, false);
            checkNftTransfers(transfers.nftTransfersOrElse(emptyList()), context, tokenMeta, op, accountStore);
        }

        final var hbarTransfers = op.transfersOrElse(TransferList.DEFAULT).accountAmountsOrElse(emptyList());
        checkFungibleTokenTransfers(hbarTransfers, context, accountStore, true);
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
        throw new UnsupportedOperationException("Not implemented");
    }

    /**
     * As part of pre-handle, checks that HBAR or fungible token transfers in the transfer list are plausible.
     *
     * @param transfers The transfers to check
     * @param ctx The context we gather signing keys into
     * @param accountStore The account store to use to look up accounts
     * @param hbarTransfer Whether this is a hbar transfer. When HIP-583 is implemented, we can remove this argument.
     * @throws PreCheckException If the transaction is invalid
     */
    private void checkFungibleTokenTransfers(
            @NonNull final List<AccountAmount> transfers,
            @NonNull final PreHandleContext ctx,
            @NonNull final AccountAccess accountStore,
            final boolean hbarTransfer)
            throws PreCheckException {
        // We're going to iterate over all the transfers in the transfer list. Each transfer is known as an
        // "account amount". Each of these represents the transfer of hbar INTO a single account or OUT of a
        // single account.
        for (final var accountAmount : transfers) {
            // Given an accountId, we need to look up the associated account.
            final var accountId = validateAccountID(accountAmount.accountIDOrElse(AccountID.DEFAULT));
            final var account = accountStore.getAccountById(accountId);
            final var isCredit = accountAmount.amount() > 0;
            final var isDebit = accountAmount.amount() < 0;
            if (account != null) {
                // This next code is not right, but we have it for compatibility until after we migrate
                // off the mono-service. Then we can fix this. In this logic, if the receiver account (the
                // one with the credit) doesn't have a key AND the value being sent is non-hbar fungible tokens,
                // then we fail with ACCOUNT_IS_IMMUTABLE. And if the account is being debited and has no key,
                // then we also fail with the same error. It should be that being credited value DOES NOT require
                // a key, unless `receiverSigRequired` is true.
                final var accountKey = account.getKey();
                if ((accountKey == null || accountKey.isEmpty()) && (isDebit || isCredit && !hbarTransfer)) {
                    throw new PreCheckException(ACCOUNT_IS_IMMUTABLE);
                }

                // We only need signing keys for accounts that are being debited OR those being credited
                // but with receiverSigRequired set to true. If the account is being debited but "isApproval"
                // is set on the transaction, then we defer to the token transfer logic to determine if all
                // signing requirements were met ("isApproval" is a way for the client to say "I don't need a key
                // because I'm approved which you will see when you handle this transaction").
                if (isDebit && !accountAmount.isApproval()) {
                    ctx.requireKeyOrThrow(account.getKey(), ACCOUNT_IS_IMMUTABLE);
                } else if (isCredit && account.isReceiverSigRequired()) {
                    ctx.requireKeyOrThrow(account.getKey(), INVALID_TRANSFER_ACCOUNT_ID);
                }
            } else if (hbarTransfer) {
                // It is possible for the transfer to be valid even if the account is not found. For example, we
                // allow auto-creation of "hollow accounts" if you transfer value into an account *by alias* that
                // didn't previously exist. If that is not the case, then we fail because we couldn't find the
                // destination account.
                if (!isCredit || !isAlias(accountId)) {
                    // Interestingly, this means that if the transfer amount is exactly 0 and the account has a
                    // non-existent alias, then we fail.
                    throw new PreCheckException(INVALID_ACCOUNT_ID);
                }
            } else if (isDebit) {
                // All debited accounts must be valid
                throw new PreCheckException(INVALID_ACCOUNT_ID);
            }
        }
    }

    private void checkNftTransfers(
            final List<NftTransfer> nftTransfersList,
            final PreHandleContext meta,
            final TokenMetadata tokenMeta,
            final CryptoTransferTransactionBody op,
            final ReadableAccountStore accountStore)
            throws PreCheckException {
        for (final var nftTransfer : nftTransfersList) {
            final var senderId = nftTransfer.senderAccountIDOrElse(AccountID.DEFAULT);
            validateAccountID(senderId);
            checkSender(senderId, nftTransfer, meta, accountStore);

            final var receiverId = nftTransfer.receiverAccountIDOrElse(AccountID.DEFAULT);
            validateAccountID(receiverId);
            checkReceiver(receiverId, senderId, nftTransfer, meta, tokenMeta, op, accountStore);
        }
    }

    private void checkReceiver(
            final AccountID receiverId,
            final AccountID senderId,
            final NftTransfer nftTransfer,
            final PreHandleContext meta,
            final TokenMetadata tokenMeta,
            final CryptoTransferTransactionBody op,
            final ReadableAccountStore accountStore)
            throws PreCheckException {

        // Lookup the receiver account and verify it.
        final var receiverAccount = accountStore.getAccountById(receiverId);
        if (receiverAccount == null) {
            // It may be that the receiver account does not yet exist. If it is being addressed by alias,
            // then this is OK, as we will automatically create the account. Otherwise, fail.
            if (!isAlias(receiverId)) {
                throw new PreCheckException(INVALID_ACCOUNT_ID);
            } else {
                return;
            }
        }

        final var receiverKey = receiverAccount.getKey();
        if (receiverKey == null || receiverKey.isEmpty()) {
            // If the receiver account has no key, then fail with ACCOUNT_IS_IMMUTABLE.
            throw new PreCheckException(ACCOUNT_IS_IMMUTABLE);
        } else if (receiverAccount.isReceiverSigRequired()) {
            // If receiverSigRequired is set, and if there is no key on the receiver's account, then fail with
            // INVALID_TRANSFER_ACCOUNT_ID. Otherwise, add the key.
            meta.requireKeyOrThrow(receiverKey, INVALID_TRANSFER_ACCOUNT_ID);
        } else if (tokenMeta.hasRoyaltyWithFallback()
                && !receivesFungibleValue(nftTransfer.senderAccountID(), op, accountStore)) {
            // It may be that this transfer has royalty fees associated with it. If it does, then we need
            // to check that the receiver signed the transaction, UNLESS the sender or receiver is
            // the treasury, in which case fallback fees will not be applied when the transaction is handled,
            // so the receiver key does not need to sign.
            final var treasury = tokenMeta.treasuryNum();
            if (treasury != senderId.accountNumOrThrow() && treasury != receiverId.accountNumOrThrow()) {
                meta.requireKeyOrThrow(receiverId, INVALID_TREASURY_ACCOUNT_FOR_TOKEN);
            }
        }
    }

    private void checkSender(
            final AccountID senderId,
            final NftTransfer nftTransfer,
            final PreHandleContext meta,
            final ReadableAccountStore accountStore)
            throws PreCheckException {

        // Lookup the sender account and verify it.
        final var senderAccount = accountStore.getAccountById(senderId);
        if (senderAccount == null) {
            throw new PreCheckException(INVALID_ACCOUNT_ID);
        }

        // If the sender account is immutable, then we throw an exception.
        final var key = senderAccount.key();
        if (key == null || key.key().kind().equals(KeyOneOfType.UNSET)) {
            // If the sender account has no key, then fail with ACCOUNT_IS_IMMUTABLE.
            throw new PreCheckException(ACCOUNT_IS_IMMUTABLE);
        } else if (!nftTransfer.isApproval()) {
            meta.requireKey(key);
        }
    }

    private boolean receivesFungibleValue(
            final AccountID target, final CryptoTransferTransactionBody op, final ReadableAccountStore accountStore) {
        for (final var adjust : op.transfersOrElse(TransferList.DEFAULT).accountAmountsOrElse(emptyList())) {
            final var unaliasedAccount = accountStore.getAccountById(adjust.accountIDOrElse(AccountID.DEFAULT));
            final var unaliasedTarget = accountStore.getAccountById(target);
            if (unaliasedAccount != null
                    && unaliasedTarget != null
                    && adjust.amount() > 0
                    && unaliasedAccount.equals(unaliasedTarget)) {
                return true;
            }
        }
        for (final var transfers : op.tokenTransfersOrElse(emptyList())) {
            for (final var adjust : transfers.transfersOrElse(emptyList())) {
                final var unaliasedAccount = accountStore.getAccountById(adjust.accountIDOrElse(AccountID.DEFAULT));
                final var unaliasedTarget = accountStore.getAccountById(target);
                if (unaliasedAccount != null
                        && unaliasedTarget != null
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
