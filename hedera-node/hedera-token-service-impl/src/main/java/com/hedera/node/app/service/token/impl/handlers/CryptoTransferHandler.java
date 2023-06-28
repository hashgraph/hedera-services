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
import static com.hedera.hapi.node.base.ResponseCodeEnum.ACCOUNT_REPEATED_IN_ACCOUNT_AMOUNTS;
import static com.hedera.hapi.node.base.ResponseCodeEnum.BATCH_SIZE_LIMIT_EXCEEDED;
import static com.hedera.hapi.node.base.ResponseCodeEnum.EMPTY_TOKEN_TRANSFER_ACCOUNT_AMOUNTS;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_ACCOUNT_AMOUNTS;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TOKEN_NFT_SERIAL_NUMBER;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TRANSACTION_BODY;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TRANSFER_ACCOUNT_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TREASURY_ACCOUNT_FOR_TOKEN;
import static com.hedera.hapi.node.base.ResponseCodeEnum.NOT_SUPPORTED;
import static com.hedera.hapi.node.base.ResponseCodeEnum.TOKEN_ID_REPEATED_IN_TOKEN_LIST;
import static com.hedera.hapi.node.base.ResponseCodeEnum.TOKEN_TRANSFER_LIST_SIZE_LIMIT_EXCEEDED;
import static com.hedera.hapi.node.base.ResponseCodeEnum.TRANSFERS_NOT_ZERO_SUM_FOR_TOKEN;
import static com.hedera.hapi.node.base.ResponseCodeEnum.TRANSFER_LIST_SIZE_LIMIT_EXCEEDED;
import static com.hedera.node.app.spi.key.KeyUtils.isEmpty;
import static com.hedera.node.app.spi.key.KeyUtils.isValid;
import static com.hedera.node.app.spi.validation.Validations.validateAccountID;
import static com.hedera.node.app.spi.workflows.HandleException.validateTrue;
import static com.hedera.node.app.spi.workflows.PreCheckException.validateFalsePreCheck;
import static com.hedera.node.app.spi.workflows.PreCheckException.validateTruePreCheck;
import static java.util.Collections.emptyList;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.AccountAmount;
import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.base.NftTransfer;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.base.TokenTransferList;
import com.hedera.hapi.node.base.TransferList;
import com.hedera.hapi.node.token.CryptoTransferTransactionBody;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.token.ReadableAccountStore;
import com.hedera.node.app.service.token.ReadableTokenStore;
import com.hedera.node.app.service.token.ReadableTokenStore.TokenMetadata;
import com.hedera.node.app.service.token.impl.handlers.transfer.EnsureAliasesStep;
import com.hedera.node.app.service.token.impl.handlers.transfer.ReplaceAliasesWithIDsInOp;
import com.hedera.node.app.service.token.impl.handlers.transfer.TransferContextImpl;
import com.hedera.node.app.service.token.impl.handlers.transfer.TransferStep;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.spi.workflows.HandleException;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.spi.workflows.PreHandleContext;
import com.hedera.node.app.spi.workflows.TransactionHandler;
import com.hedera.node.config.data.HederaConfig;
import com.hedera.node.config.data.LazyCreationConfig;
import com.hedera.node.config.data.LedgerConfig;
import com.hedera.node.config.data.TokensConfig;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ArrayList;
import java.util.HashSet;
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

    @Override
    public void preHandle(@NonNull final PreHandleContext context) throws PreCheckException {
        requireNonNull(context);
        pureChecks(context.body());

        final var op = context.body().cryptoTransferOrThrow();
        final var accountStore = context.createStore(ReadableAccountStore.class);
        final var tokenStore = context.createStore(ReadableTokenStore.class);
        for (final var transfers : op.tokenTransfersOrElse(emptyList())) {
            final var tokenMeta = tokenStore.getTokenMeta(transfers.tokenOrElse(TokenID.DEFAULT));
            if (tokenMeta == null) throw new PreCheckException(INVALID_TOKEN_ID);
            checkFungibleTokenTransfers(transfers.transfersOrElse(emptyList()), context, accountStore, false);
            checkNftTransfers(transfers.nftTransfersOrElse(emptyList()), context, tokenMeta, op, accountStore);
        }

        final var hbarTransfers = op.transfersOrElse(TransferList.DEFAULT).accountAmountsOrElse(emptyList());
        checkFungibleTokenTransfers(hbarTransfers, context, accountStore, true);
    }

    @Override
    public void pureChecks(@NonNull final TransactionBody txn) throws PreCheckException {
        requireNonNull(txn);
        final var op = txn.cryptoTransfer();
        validateTruePreCheck(op != null, INVALID_TRANSACTION_BODY);

        final var acctAmounts = op.transfersOrElse(TransferList.DEFAULT).accountAmountsOrElse(emptyList());
        final var uniqueAcctIds = new HashSet<AccountID>();
        long netBalance = 0;
        for (final AccountAmount acctAmount : acctAmounts) {
            validateTruePreCheck(acctAmount.hasAccountID(), INVALID_ACCOUNT_ID);
            final var acctId = validateAccountID(acctAmount.accountIDOrThrow());
            uniqueAcctIds.add(acctId);
            netBalance += acctAmount.amount();
        }
        validateTruePreCheck(netBalance == 0, INVALID_ACCOUNT_AMOUNTS);
        validateFalsePreCheck(uniqueAcctIds.size() < acctAmounts.size(), ACCOUNT_REPEATED_IN_ACCOUNT_AMOUNTS);

        final var tokenTransfers = op.tokenTransfersOrElse(emptyList());
        for (final TokenTransferList tokenTransfer : tokenTransfers) {
            final var tokenID = tokenTransfer.token();
            validateTruePreCheck(tokenID != null && !tokenID.equals(TokenID.DEFAULT), INVALID_TOKEN_ID);

            // Validate the fungible transfers
            final var uniqueTokenAcctIds = new HashSet<AccountID>();
            final var fungibleTransfers = tokenTransfer.transfersOrElse(emptyList());
            long netTokenBalance = 0;
            boolean nonZeroFungibleValueFound = false;
            for (final AccountAmount acctAmount : fungibleTransfers) {
                validateTruePreCheck(acctAmount.hasAccountID(), INVALID_TRANSFER_ACCOUNT_ID);
                uniqueTokenAcctIds.add(acctAmount.accountIDOrThrow());
                netTokenBalance += acctAmount.amount();
                if (!nonZeroFungibleValueFound && acctAmount.amount() != 0) {
                    nonZeroFungibleValueFound = true;
                }
            }
            validateFalsePreCheck(
                    uniqueTokenAcctIds.size() < fungibleTransfers.size(), ACCOUNT_REPEATED_IN_ACCOUNT_AMOUNTS);
            validateTruePreCheck(netTokenBalance == 0, TRANSFERS_NOT_ZERO_SUM_FOR_TOKEN);

            // Validate the nft transfers
            final var nftTransfers = tokenTransfer.nftTransfersOrElse(emptyList());
            final var nftIds = new HashSet<Long>();
            for (final NftTransfer nftTransfer : nftTransfers) {
                validateTruePreCheck(nftTransfer.serialNumber() > 0, INVALID_TOKEN_NFT_SERIAL_NUMBER);
                validateTruePreCheck(nftTransfer.hasSenderAccountID(), INVALID_TRANSFER_ACCOUNT_ID);
                validateTruePreCheck(nftTransfer.hasReceiverAccountID(), INVALID_TRANSFER_ACCOUNT_ID);

                nftIds.add(nftTransfer.serialNumber());
            }
            validateFalsePreCheck(nftIds.size() < nftTransfers.size(), TOKEN_ID_REPEATED_IN_TOKEN_LIST);

            // Verify that one and only one of the two types of transfers (fungible or non-fungible) is present
            validateFalsePreCheck(!nonZeroFungibleValueFound && nftIds.isEmpty(), EMPTY_TOKEN_TRANSFER_ACCOUNT_AMOUNTS);
            validateFalsePreCheck(nonZeroFungibleValueFound && !nftIds.isEmpty(), INVALID_ACCOUNT_AMOUNTS);
        }
    }

    @Override
    public void handle(@NonNull final HandleContext context) throws HandleException {
        requireNonNull(context);
        final var txn = context.body();
        final var op = txn.cryptoTransferOrThrow();

        final var ledgerConfig = context.configuration().getConfigData(LedgerConfig.class);
        final var hederaConfig = context.configuration().getConfigData(HederaConfig.class);
        final var tokensConfig = context.configuration().getConfigData(TokensConfig.class);

        validateSemantics(op, ledgerConfig, hederaConfig, tokensConfig);

        final var transferContext = new TransferContextImpl(context);
        final var replacedOp = ensureAndReplaceAliasesInOp(txn, transferContext, context);

        final var steps = decomposeIntoSteps(replacedOp);
        for (final var step : steps) {
            // Apply all changes to the handleContext's States
            step.doIn(transferContext);
        }
    }

    /**
     * Ensures all aliases specified in the transfer exist. If the aliases are in receiver section, and don't exist
     * they will be auto-created. This step populates resolved aliases and number of auto creations in the
     * transferContext, which is used by subsequent steps and throttling.
     * It will also replace all aliases in the {@link CryptoTransferTransactionBody} with its account ids, so it will
     * be easier to process in next steps.
     * @param txn the given transaction body
     * @param transferContext the given transfer context
     * @param context the given handle context
     * @return the replaced transaction body with all aliases replaced with its account ids
     * @throws HandleException if any error occurs during the process
     */
    private CryptoTransferTransactionBody ensureAndReplaceAliasesInOp(
            final TransactionBody txn, final TransferContextImpl transferContext, final HandleContext context)
            throws HandleException {
        final var op = txn.cryptoTransferOrThrow();
        // Ensures all aliases specified in the transfer exist
        // If the aliases are in receiver section, and don't exist they will be auto-created
        // This step populates resolved aliases and number of auto creations in the transferContext,
        // which is used by subsequent steps and throttling
        ensureExistenceOfAliasesOrCreate(op, transferContext);
        if (transferContext.numOfLazyCreations() > 0) {
            final var config = context.configuration().getConfigData(LazyCreationConfig.class);
            validateTrue(config.enabled(), NOT_SUPPORTED);
        }

        // replace all aliases with its account ids, so it will be easier to process in next steps
        final var replacedOp = new ReplaceAliasesWithIDsInOp().replaceAliasesWithIds(op, transferContext);
        // re-run pure checks on this op to see if there are no duplicates
        try {
            final var txnBody = txn.copyBuilder().cryptoTransfer(replacedOp).build();
            pureChecks(txnBody);
        } catch (PreCheckException e) {
            throw new HandleException(e.responseCode());
        }
        return replacedOp;
    }

    private void ensureExistenceOfAliasesOrCreate(
            @NonNull final CryptoTransferTransactionBody op, @NonNull final TransferContextImpl transferContext) {
        final var ensureAliasExistence = new EnsureAliasesStep(op);
        ensureAliasExistence.doIn(transferContext);
    }

    /**
     * Decomposes a crypto transfer into a sequence of steps that can be executed in order.
     * Each step validates the preconditions needed from TransferContextImpl in order to perform its action.
     * Steps are as follows:
     * <ol>
     *     <li>(c,o)Ensure existence of alias-referenced accounts</li>
     *     <li>(+,c)Charge custom fees for token transfers</li>
     *     <li>(o)Ensure associations of token recipients</li>
     *     <li>(+)Do zero-sum hbar balance changes</li>
     *     <li>(+)Do zero-sum fungible token transfers</li>
     *     <li>(+)Change NFT owners</li>
     *     <li>(+,c)Pay staking rewards, possibly to previously unmentioned stakee accounts</li>
     * </ol>
     * LEGEND: '+' = creates new BalanceChange(s) from either the transaction body, custom fee schedule, or staking reward situation
     *        'c' = updates an existing BalanceChange
     *        'o' = causes a side effect not represented as BalanceChange
     * @param op The crypto transfer transaction body
     * @return A list of steps to execute
     */
    private List<TransferStep> decomposeIntoSteps(final CryptoTransferTransactionBody op) {
        final List<TransferStep> steps = new ArrayList<>();
        // TODO: implement other steps

        return steps;
    }

    private void validateSemantics(
            @NonNull final CryptoTransferTransactionBody op,
            @NonNull final LedgerConfig ledgerConfig,
            @NonNull final HederaConfig hederaConfig,
            @NonNull final TokensConfig tokensConfig) {
        final var transfers = op.transfersOrElse(TransferList.DEFAULT);

        // Validate that there aren't too many hbar transfers
        final var hbarTransfers = transfers.accountAmountsOrElse(emptyList());
        validateTrue(hbarTransfers.size() < ledgerConfig.transfersMaxLen(), TRANSFER_LIST_SIZE_LIMIT_EXCEEDED);

        // Validate that allowances are enabled, or that no hbar transfers are an allowance transfer
        final var allowancesEnabled = hederaConfig.allowancesIsEnabled();
        validateTrue(allowancesEnabled || !hasAllowance(hbarTransfers), NOT_SUPPORTED);

        // The loop below will validate the counts for token transfers (both fungible and non-fungible)
        final var tokenTransfers = op.tokenTransfersOrElse(emptyList());
        var totalFungibleTransfers = 0;
        var totalNftTransfers = 0;
        final var nftsEnabled = tokensConfig.nftsAreEnabled();
        for (final TokenTransferList tokenTransfer : tokenTransfers) {
            // Validate the fungible token transfer(s) (if present)
            final var fungibleTransfers = tokenTransfer.transfersOrElse(emptyList());
            validateTrue(allowancesEnabled || !hasAllowance(fungibleTransfers), NOT_SUPPORTED);
            totalFungibleTransfers += fungibleTransfers.size();

            // Validate the nft transfer(s) (if present)
            final var nftTransfers = tokenTransfer.nftTransfersOrElse(emptyList());
            validateTrue(nftsEnabled || nftTransfers.isEmpty(), NOT_SUPPORTED);
            validateTrue(allowancesEnabled || !hasNftAllowance(nftTransfers), NOT_SUPPORTED);
            totalNftTransfers += nftTransfers.size();

            // Verify that the current total number of (counted) fungible transfers does not exceed the limit
            validateTrue(
                    totalFungibleTransfers < ledgerConfig.tokenTransfersMaxLen(),
                    TOKEN_TRANSFER_LIST_SIZE_LIMIT_EXCEEDED);
            // Verify that the current total number of (counted) nft transfers does not exceed the limit
            validateTrue(totalNftTransfers < ledgerConfig.nftTransfersMaxLen(), BATCH_SIZE_LIMIT_EXCEEDED);
        }
    }

    private boolean hasAllowance(@NonNull final List<AccountAmount> transfers) {
        for (final AccountAmount transfer : transfers) {
            if (transfer.isApproval()) {
                return true;
            }
        }

        return false;
    }

    private boolean hasNftAllowance(@NonNull final List<NftTransfer> nftTransfers) {
        for (final NftTransfer nftTransfer : nftTransfers) {
            if (nftTransfer.isApproval()) {
                return true;
            }
        }

        return false;
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
            @NonNull final ReadableAccountStore accountStore,
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
                final var accountKey = account.key();
                if ((isEmpty(accountKey)) && (isDebit || isCredit && !hbarTransfer)) {
                    throw new PreCheckException(ACCOUNT_IS_IMMUTABLE);
                }

                // We only need signing keys for accounts that are being debited OR those being credited
                // but with receiverSigRequired set to true. If the account is being debited but "isApproval"
                // is set on the transaction, then we defer to the token transfer logic to determine if all
                // signing requirements were met ("isApproval" is a way for the client to say "I don't need a key
                // because I'm approved which you will see when you handle this transaction").
                if (isDebit && !accountAmount.isApproval()) {
                    ctx.requireKeyOrThrow(account.key(), ACCOUNT_IS_IMMUTABLE);
                } else if (isCredit && account.receiverSigRequired()) {
                    ctx.requireKeyOrThrow(account.key(), INVALID_TRANSFER_ACCOUNT_ID);
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

        final var receiverKey = receiverAccount.key();
        if (isEmpty(receiverKey)) {
            // If the receiver account has no key, then fail with ACCOUNT_IS_IMMUTABLE.
            throw new PreCheckException(ACCOUNT_IS_IMMUTABLE);
        } else if (receiverAccount.receiverSigRequired()) {
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
        if (key == null || !isValid(key)) {
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
