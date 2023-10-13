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

import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TRANSACTION_BODY;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TRANSFER_ACCOUNT_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TREASURY_ACCOUNT_FOR_TOKEN;
import static com.hedera.hapi.node.base.ResponseCodeEnum.NOT_SUPPORTED;
import static com.hedera.hapi.node.base.SubType.DEFAULT;
import static com.hedera.hapi.node.base.SubType.TOKEN_FUNGIBLE_COMMON;
import static com.hedera.hapi.node.base.SubType.TOKEN_NON_FUNGIBLE_UNIQUE;
import static com.hedera.hapi.node.base.SubType.TOKEN_NON_FUNGIBLE_UNIQUE_WITH_CUSTOM_FEES;
import static com.hedera.node.app.hapi.fees.usage.SingletonUsageProperties.USAGE_PROPERTIES;
import static com.hedera.node.app.hapi.fees.usage.crypto.CryptoOpsUsage.LONG_ACCOUNT_AMOUNT_BYTES;
import static com.hedera.node.app.hapi.fees.usage.token.TokenOpsUsage.LONG_BASIC_ENTITY_ID_SIZE;
import static com.hedera.node.app.hapi.fees.usage.token.entities.TokenEntitySizes.TOKEN_ENTITY_SIZES;
import static com.hedera.node.app.service.token.impl.handlers.transfer.AliasUtils.isAlias;
import static com.hedera.node.app.spi.key.KeyUtils.isEmpty;
import static com.hedera.node.app.spi.key.KeyUtils.isValid;
import static com.hedera.node.app.spi.validation.Validations.validateAccountID;
import static com.hedera.node.app.spi.workflows.HandleException.validateTrue;
import static com.hedera.node.app.spi.workflows.PreCheckException.validateTruePreCheck;
import static java.util.Collections.emptyList;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.AccountAmount;
import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.base.NftTransfer;
import com.hedera.hapi.node.base.SubType;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.base.TransferList;
import com.hedera.hapi.node.token.CryptoTransferTransactionBody;
import com.hedera.hapi.node.transaction.AssessedCustomFee;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.token.ReadableAccountStore;
import com.hedera.node.app.service.token.ReadableTokenStore;
import com.hedera.node.app.service.token.ReadableTokenStore.TokenMetadata;
import com.hedera.node.app.service.token.impl.handlers.transfer.AdjustFungibleTokenChangesStep;
import com.hedera.node.app.service.token.impl.handlers.transfer.AdjustHbarChangesStep;
import com.hedera.node.app.service.token.impl.handlers.transfer.AssociateTokenRecipientsStep;
import com.hedera.node.app.service.token.impl.handlers.transfer.CustomFeeAssessmentStep;
import com.hedera.node.app.service.token.impl.handlers.transfer.EnsureAliasesStep;
import com.hedera.node.app.service.token.impl.handlers.transfer.NFTOwnersChangeStep;
import com.hedera.node.app.service.token.impl.handlers.transfer.ReplaceAliasesWithIDsInOp;
import com.hedera.node.app.service.token.impl.handlers.transfer.TransferContextImpl;
import com.hedera.node.app.service.token.impl.handlers.transfer.TransferStep;
import com.hedera.node.app.service.token.impl.validators.CryptoTransferValidator;
import com.hedera.node.app.spi.fees.FeeContext;
import com.hedera.node.app.spi.fees.Fees;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.spi.workflows.HandleException;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.spi.workflows.PreHandleContext;
import com.hedera.node.app.spi.workflows.TransactionHandler;
import com.hedera.node.config.data.FeesConfig;
import com.hedera.node.config.data.HederaConfig;
import com.hedera.node.config.data.LazyCreationConfig;
import com.hedera.node.config.data.LedgerConfig;
import com.hedera.node.config.data.TokensConfig;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * This class contains all workflow-related functionality regarding {@link
 * HederaFunctionality#CRYPTO_TRANSFER}.
 */
@Singleton
public class CryptoTransferHandler implements TransactionHandler {
    private final CryptoTransferValidator validator;

    @Inject
    public CryptoTransferHandler(@NonNull final CryptoTransferValidator validator) {
        this.validator = validator;
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
        validator.pureChecks(op);
    }

    @Override
    public void handle(@NonNull final HandleContext context) throws HandleException {
        requireNonNull(context);
        final var txn = context.body();
        final var op = txn.cryptoTransferOrThrow();
        final var topLevelPayer = context.payer();

        final var ledgerConfig = context.configuration().getConfigData(LedgerConfig.class);
        final var hederaConfig = context.configuration().getConfigData(HederaConfig.class);
        final var tokensConfig = context.configuration().getConfigData(TokensConfig.class);

        validator.validateSemantics(op, ledgerConfig, hederaConfig, tokensConfig);

        // create a new transfer context that is specific only for this transaction
        final var transferContext = new TransferContextImpl(context);

        // Replace all aliases in the transaction body with its account ids
        final var replacedOp = ensureAndReplaceAliasesInOp(txn, transferContext, context);
        // Use the op with replaced aliases in further steps
        final var steps = decomposeIntoSteps(replacedOp, topLevelPayer, transferContext);
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

        // ensure all aliases exist, if not create then if receivers
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
     *
     * @param op              The crypto transfer transaction body
     * @param topLevelPayer   The payer of the transaction
     * @param transferContext
     * @return A list of steps to execute
     */
    private List<TransferStep> decomposeIntoSteps(
            final CryptoTransferTransactionBody op,
            final AccountID topLevelPayer,
            final TransferContextImpl transferContext) {
        final List<TransferStep> steps = new ArrayList<>();
        // Step 1: associate any token recipients that are not already associated and have
        // auto association slots open
        steps.add(new AssociateTokenRecipientsStep(op));
        // Step 2: Charge custom fees for token transfers. yet to be implemented
        final var customFeeStep = new CustomFeeAssessmentStep(op);
        // The below steps should be doe for both custom fee assessed transaction in addition to
        // original transaction
        final var customFeeAssessedOps = customFeeStep.assessCustomFees(transferContext);

        for (final var txn : customFeeAssessedOps) {
            steps.add(new AssociateTokenRecipientsStep(txn));
            // Step 3: Charge hbar transfers and also ones with isApproval. Modify the allowances map on account
            final var assessHbarTransfers = new AdjustHbarChangesStep(txn, topLevelPayer);
            steps.add(assessHbarTransfers);

            // Step 4: Charge token transfers with an approval. Modify the allowances map on account
            final var assessFungibleTokenTransfers = new AdjustFungibleTokenChangesStep(txn, topLevelPayer);
            steps.add(assessFungibleTokenTransfers);

            // Step 5: Change NFT owners and also ones with isApproval. Clear the spender on NFT.
            // Will be a no-op for every txn except possibly the first (i.e., the top-level txn).
            // This is because assessed custom fees never change NFT owners
            final var changeNftOwners = new NFTOwnersChangeStep(txn, topLevelPayer);
            steps.add(changeNftOwners);
        }

        return steps;
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
                    // NOTE: should change to ACCOUNT_IS_IMMUTABLE after modularization
                    throw new PreCheckException(INVALID_ACCOUNT_ID);
                }

                // We only need signing keys for accounts that are being debited OR those being credited
                // but with receiverSigRequired set to true. If the account is being debited but "isApproval"
                // is set on the transaction, then we defer to the token transfer logic to determine if all
                // signing requirements were met ("isApproval" is a way for the client to say "I don't need a key
                // because I'm approved which you will see when you handle this transaction").
                if (isDebit && !accountAmount.isApproval()) {
                    // NOTE: should change to ACCOUNT_IS_IMMUTABLE after modularization
                    ctx.requireKeyOrThrow(account.key(), INVALID_ACCOUNT_ID);
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
            // If the receiver account has no key, then fail with INVALID_ACCOUNT_ID.
            // NOTE: should change to ACCOUNT_IS_IMMUTABLE after modularization
            throw new PreCheckException(INVALID_ACCOUNT_ID);
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
            final var treasuryId = tokenMeta.treasuryAccountId();
            if (!treasuryId.equals(senderId) && !treasuryId.equals(receiverId)) {
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
            // If the sender account has no key, then fail with INVALID_ACCOUNT_ID.
            // NOTE: should change to ACCOUNT_IS_IMMUTABLE
            throw new PreCheckException(INVALID_ACCOUNT_ID);
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

    @NonNull
    @Override
    public Fees calculateFees(@NonNull final FeeContext feeContext) {
        final var body = feeContext.body();
        final var op = body.cryptoTransferOrThrow();
        final var config = feeContext.configuration();
        final var tokenMultiplier = config.getConfigData(FeesConfig.class).tokenTransferUsageMultiplier();

        /* BPT calculations shouldn't include any custom fee payment usage */
        int totalXfers = op.transfersOrElse(TransferList.DEFAULT)
                .accountAmountsOrElse(emptyList())
                .size();

        var totalTokensInvolved = 0;
        var totalTokenTransfers = 0;
        var numNftOwnershipChanges = 0;
        for (final var tokenTransfers : op.tokenTransfersOrElse(emptyList())) {
            totalTokensInvolved++;
            totalTokenTransfers += tokenTransfers.transfersOrElse(emptyList()).size();
            numNftOwnershipChanges +=
                    tokenTransfers.nftTransfersOrElse(emptyList()).size();
        }

        int weightedTokensInvolved = tokenMultiplier * totalTokensInvolved;
        int weightedTokenXfers = tokenMultiplier * totalTokenTransfers;
        final var bpt = weightedTokensInvolved * LONG_BASIC_ENTITY_ID_SIZE
                + (weightedTokenXfers + totalXfers) * LONG_ACCOUNT_AMOUNT_BYTES
                + TOKEN_ENTITY_SIZES.bytesUsedForUniqueTokenTransfers(numNftOwnershipChanges);

        /* Include custom fee payment usage in RBS calculations */
        var customFeeHbarTransfers = 0;
        var customFeeTokenTransfers = 0;
        final var involvedTokens = new ArrayList<TokenID>();
        final var customFeeAssessor = new CustomFeeAssessmentStep(op);
        List<AssessedCustomFee> assessedCustomFees;
        try {
            assessedCustomFees = customFeeAssessor.assessNumberOfCustomFees(feeContext);
        } catch (HandleException ignore) {
            assessedCustomFees = new ArrayList<>();
        }
        totalXfers += assessedCustomFees.size();
        for (final var fee : assessedCustomFees) {
            if (!fee.hasTokenId()) {
                customFeeHbarTransfers++;
            } else {
                customFeeTokenTransfers++;
                involvedTokens.add(fee.tokenId());
            }
        }
        weightedTokenXfers += tokenMultiplier * customFeeTokenTransfers;
        weightedTokensInvolved += tokenMultiplier * involvedTokens.size();
        long rbs = (totalXfers * LONG_ACCOUNT_AMOUNT_BYTES)
                + TOKEN_ENTITY_SIZES.bytesUsedToRecordTokenTransfers(
                        weightedTokensInvolved, weightedTokenXfers, numNftOwnershipChanges);

        /* Get subType based on the above information */
        final var subType = getSubType(
                numNftOwnershipChanges, totalTokenTransfers, customFeeHbarTransfers, customFeeTokenTransfers);
        return feeContext
                .feeCalculator(subType)
                .addBytesPerTransaction(bpt)
                .addRamByteSeconds(rbs * USAGE_PROPERTIES.legacyReceiptStorageSecs())
                .calculate();
    }

    private SubType getSubType(
            final int numNftOwnershipChanges,
            final int numFungibleTokenTransfers,
            final int customFeeHbarTransfers,
            final int customFeeTokenTransfers) {
        if (numNftOwnershipChanges != 0) {
            if (customFeeHbarTransfers > 0 || customFeeTokenTransfers > 0) {
                return TOKEN_NON_FUNGIBLE_UNIQUE_WITH_CUSTOM_FEES;
            }
            return TOKEN_NON_FUNGIBLE_UNIQUE;
        }
        if (numFungibleTokenTransfers != 0) {
            if (customFeeHbarTransfers > 0 || customFeeTokenTransfers > 0) {
                return SubType.TOKEN_FUNGIBLE_COMMON_WITH_CUSTOM_FEES;
            }
            return TOKEN_FUNGIBLE_COMMON;
        }
        return DEFAULT;
    }
}
