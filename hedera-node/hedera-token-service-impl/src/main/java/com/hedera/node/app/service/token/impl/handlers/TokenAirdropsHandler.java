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

package com.hedera.node.app.service.token.impl.handlers;

import static com.hedera.hapi.node.base.ResponseCodeEnum.ACCOUNT_IS_IMMUTABLE;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TRANSACTION;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TRANSACTION_BODY;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TRANSFER_ACCOUNT_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.NOT_SUPPORTED;
import static com.hedera.hapi.util.HapiUtils.isHollow;
import static com.hedera.node.app.service.token.AliasUtils.isAlias;
import static com.hedera.node.app.service.token.impl.handlers.BaseCryptoHandler.isStakingAccount;
import static com.hedera.node.app.spi.key.KeyUtils.isValid;
import static com.hedera.node.app.spi.validation.Validations.validateAccountID;
import static com.hedera.node.app.spi.workflows.HandleException.validateTrue;
import static com.hedera.node.app.spi.workflows.PreCheckException.validateTruePreCheck;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.AccountAmount;
import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.base.NftTransfer;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.token.ReadableAccountStore;
import com.hedera.node.app.service.token.ReadableTokenStore;
import com.hedera.node.app.service.token.impl.validators.TokenAirdropValidator;
import com.hedera.node.app.spi.fees.FeeContext;
import com.hedera.node.app.spi.fees.Fees;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.spi.workflows.HandleException;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.spi.workflows.PreHandleContext;
import com.hedera.node.app.spi.workflows.TransactionHandler;
import com.hedera.node.config.data.TokensConfig;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * This class contains all workflow-related functionality regarding {@link
 * HederaFunctionality#TOKEN_AIRDROP}.
 */
@Singleton
public class TokenAirdropsHandler implements TransactionHandler {

    private final TokenAirdropValidator validator;

    /**
     * Default constructor for injection.
     */
    @Inject
    public TokenAirdropsHandler(@NonNull final TokenAirdropValidator validator) {
        this.validator = validator;
    }

    @Override
    public void preHandle(@NonNull final PreHandleContext context) throws PreCheckException {
        requireNonNull(context);
        pureChecks(context.body());

        final var op = context.body().tokenAirdropOrThrow();
        final var accountStore = context.createStore(ReadableAccountStore.class);
        final var tokenStore = context.createStore(ReadableTokenStore.class);

        //        final var topLevelPayer = context.payer();
        //        checkSender(topLevelPayer);

        for (final var transfers : op.tokenTransfers()) {
            final var tokenMeta = tokenStore.getTokenMeta(transfers.tokenOrElse(TokenID.DEFAULT));
            if (tokenMeta == null) throw new PreCheckException(INVALID_TOKEN_ID);
            checkFungibleTokenTransfers(transfers.transfers(), context, accountStore);
            checkNftTransfers(transfers.nftTransfers(), context, tokenMeta, accountStore);
        }
    }

    @Override
    public void pureChecks(@NonNull final TransactionBody txn) throws PreCheckException {
        requireNonNull(txn);
        final var op = txn.tokenAirdrop();
        validateTruePreCheck(op != null, INVALID_TRANSACTION_BODY);
        validator.pureChecks(op);
    }

    @Override
    public void handle(@NonNull final HandleContext context) throws HandleException {
        requireNonNull(context);
        final var txn = context.body();
        final var op = txn.tokenAirdrop();
        final var tokensConfig = context.configuration().getConfigData(TokensConfig.class);

        validateTrue(tokensConfig.airdropsEnabled(), NOT_SUPPORTED);
    }

    @NonNull
    @Override
    public Fees calculateFees(@NonNull final FeeContext feeContext) {
        // todo calculate fees
        return Fees.FREE;
    }

    /**
     * As part of pre-handle, token transfers in the transfer list are plausible.
     *
     * @param transfers The transfers to check
     * @param ctx The context we gather signing keys into
     * @param accountStore The account store to use to look up accounts
     * @throws PreCheckException If the transaction is invalid
     */
    private void checkFungibleTokenTransfers(
            @NonNull final List<AccountAmount> transfers,
            @NonNull final PreHandleContext ctx,
            @NonNull final ReadableAccountStore accountStore)
            throws PreCheckException {
        // We're going to iterate over all the transfers in the transfer list. Each transfer is known as an
        // "account amount". Each of these represents the transfer of fungible token INTO a single account or OUT of a
        // single account.
        for (final var accountAmount : transfers) {
            // Given an accountId, we need to look up the associated account.
            final var accountId = validateAccountID(accountAmount.accountIDOrElse(AccountID.DEFAULT), null);
            final var account = accountStore.getAliasedAccountById(accountId);
            final var isCredit = accountAmount.amount() > 0;
            final var isDebit = accountAmount.amount() < 0;
            if (account != null) {
                // This next code is not right, but we have it for compatibility until after we migrate
                // off the mono-service. Then we can fix this. In this logic, if the receiver account (the
                // one with the credit) doesn't have a key AND the value being sent is non-hbar fungible tokens,
                // then we fail with ACCOUNT_IS_IMMUTABLE. And if the account is being debited and has no key,
                // then we also fail with the same error. It should be that being credited value DOES NOT require
                // a key, unless `receiverSigRequired` is true.
                if (isStakingAccount(ctx.configuration(), account.accountId()) && (isDebit || isCredit)) {
                    // NOTE: should change to ACCOUNT_IS_IMMUTABLE after modularization
                    throw new PreCheckException(ACCOUNT_IS_IMMUTABLE);
                }

                // We only need signing keys for accounts that are being debited OR those being credited
                // but with receiverSigRequired set to true. If the account is being debited but "isApproval"
                // is set on the transaction, then we defer to the token transfer logic to determine if all
                // signing requirements were met ("isApproval" is a way for the client to say "I don't need a key
                // because I'm approved which you will see when you handle this transaction").
                if (isDebit && !accountAmount.isApproval()) {
                    // If the account is a hollow account, then we require a signature for it.
                    // It is possible that the hollow account has signed this transaction, in which case
                    // we need to finalize the hollow account by setting its key.
                    if (isHollow(account)) {
                        ctx.requireSignatureForHollowAccount(account);
                    } else {
                        ctx.requireKeyOrThrow(account.key(), INVALID_ACCOUNT_ID);
                    }

                } else if (isCredit && account.receiverSigRequired()) {
                    ctx.requireKeyOrThrow(account.key(), INVALID_TRANSFER_ACCOUNT_ID);
                }
            } else if (isDebit) {
                // All debited accounts must be valid
                throw new PreCheckException(INVALID_ACCOUNT_ID);
            }
        }
    }

    private void checkNftTransfers(
            final List<NftTransfer> nftTransfersList,
            final PreHandleContext context,
            final ReadableTokenStore.TokenMetadata tokenMeta,
            final ReadableAccountStore accountStore)
            throws PreCheckException {
        for (final var nftTransfer : nftTransfersList) {
            final var senderId = nftTransfer.senderAccountIDOrElse(AccountID.DEFAULT);
            validateAccountID(senderId, null);
            checkSender(senderId, nftTransfer, context, accountStore);

            final var receiverId = nftTransfer.receiverAccountIDOrElse(AccountID.DEFAULT);
            validateAccountID(receiverId, null);
            checkReceiver(receiverId, context, tokenMeta, accountStore);
        }
    }

    private void checkSender(
            final AccountID senderId,
            final NftTransfer nftTransfer,
            final PreHandleContext meta,
            final ReadableAccountStore accountStore)
            throws PreCheckException {

        // Lookup the sender account and verify it.
        final var senderAccount = accountStore.getAliasedAccountById(senderId);
        if (senderAccount == null) {
            throw new PreCheckException(INVALID_ACCOUNT_ID);
        }

        // If the sender account is immutable, then we throw an exception.
        final var key = senderAccount.key();
        if (key == null || !isValid(key)) {
            // If the sender account has no key, then fail with ACCOUNT_IS_IMMUTABLE.
            throw new PreCheckException(ACCOUNT_IS_IMMUTABLE);
        } else if (!nftTransfer.isApproval()) {
            meta.requireKey(key);
        }
    }

    private void checkReceiver(
            final AccountID receiverId,
            final PreHandleContext meta,
            final ReadableTokenStore.TokenMetadata tokenMeta,
            final ReadableAccountStore accountStore)
            throws PreCheckException {

        // Lookup the receiver account and verify it.
        final var receiverAccount = accountStore.getAliasedAccountById(receiverId);
        if (receiverAccount == null) {
            // It may be that the receiver account does not yet exist. If it is being addressed by alias,
            // then this is OK, as we will automatically create the account. Otherwise, fail.
            if (!isAlias(receiverId)) {
                throw new PreCheckException(INVALID_ACCOUNT_ID);
            } else {
                return;
            }
        }
        final var receiverKey = receiverAccount.key();
        if (isStakingAccount(meta.configuration(), receiverAccount.accountId())) {
            // If the receiver account has no key, then fail with ACCOUNT_IS_IMMUTABLE.
            throw new PreCheckException(ACCOUNT_IS_IMMUTABLE);
        } else if (receiverAccount.receiverSigRequired()) {
            // If receiverSigRequired is set, and if there is no key on the receiver's account, then fail with
            // INVALID_TRANSFER_ACCOUNT_ID. Otherwise, add the key.
            meta.requireKeyOrThrow(receiverKey, INVALID_TRANSFER_ACCOUNT_ID);
        } else if (tokenMeta.hasRoyaltyWithFallback()) {
            // It may be that this transfer has royalty fees associated with it. If it does, we throw an error as
            // Token Airdrops does not support royalty with fallback fees
            throw new PreCheckException(INVALID_TRANSACTION);
        }
    }
}
