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
import static com.hedera.hapi.node.base.ResponseCodeEnum.AMOUNT_EXCEEDS_ALLOWANCE;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_ACCOUNT_AMOUNTS;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_NFT_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TRANSACTION;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TRANSACTION_BODY;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TRANSFER_ACCOUNT_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.NOT_SUPPORTED;
import static com.hedera.hapi.node.base.ResponseCodeEnum.SENDER_DOES_NOT_OWN_NFT_SERIAL_NO;
import static com.hedera.hapi.node.base.ResponseCodeEnum.SPENDER_DOES_NOT_HAVE_ALLOWANCE;
import static com.hedera.hapi.node.base.ResponseCodeEnum.TOKEN_NOT_ASSOCIATED_TO_ACCOUNT;
import static com.hedera.hapi.util.HapiUtils.isHollow;
import static com.hedera.node.app.service.token.impl.handlers.BaseCryptoHandler.isStakingAccount;
import static com.hedera.node.app.service.token.impl.handlers.transfer.customfees.CustomFeeMeta.customFeeMetaFrom;
import static com.hedera.node.app.service.token.impl.util.AirdropHandlerHelper.createAccountAirdrop;
import static com.hedera.node.app.service.token.impl.util.AirdropHandlerHelper.createFungibleTokenPendingAirdropId;
import static com.hedera.node.app.service.token.impl.util.AirdropHandlerHelper.createNftPendingAirdropId;
import static com.hedera.node.app.service.token.impl.util.AirdropHandlerHelper.createPendingAirdropRecord;
import static com.hedera.node.app.service.token.impl.util.AirdropHandlerHelper.separateFungibleTransfers;
import static com.hedera.node.app.service.token.impl.util.AirdropHandlerHelper.separateNftTransfers;
import static com.hedera.node.app.service.token.impl.util.CryptoTransferHelper.createAccountAmount;
import static com.hedera.node.app.service.token.impl.util.CryptoTransferValidationHelper.checkPayer;
import static com.hedera.node.app.service.token.impl.util.CryptoTransferValidationHelper.checkReceiver;
import static com.hedera.node.app.service.token.impl.util.CryptoTransferValidationHelper.checkSender;
import static com.hedera.node.app.service.token.impl.util.TokenHandlerHelper.TokenValidations.PERMIT_PAUSED;
import static com.hedera.node.app.service.token.impl.util.TokenHandlerHelper.getIfUsable;
import static com.hedera.node.app.spi.validation.Validations.validateAccountID;
import static com.hedera.node.app.spi.workflows.HandleException.validateTrue;
import static com.hedera.node.app.spi.workflows.PreCheckException.validateTruePreCheck;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.AccountAmount;
import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.base.NftTransfer;
import com.hedera.hapi.node.base.PendingAirdropId;
import com.hedera.hapi.node.base.PendingAirdropValue;
import com.hedera.hapi.node.base.SubType;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.base.TokenTransferList;
import com.hedera.hapi.node.base.TokenType;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.hapi.node.state.token.AccountAirdrop;
import com.hedera.hapi.node.state.token.AccountApprovalForAllAllowance;
import com.hedera.hapi.node.state.token.Nft;
import com.hedera.hapi.node.token.CryptoTransferTransactionBody;
import com.hedera.hapi.node.token.TokenAirdropTransactionBody;
import com.hedera.hapi.node.token.TokenAssociateTransactionBody;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.token.ReadableAccountStore;
import com.hedera.node.app.service.token.ReadableNftStore;
import com.hedera.node.app.service.token.ReadableTokenRelationStore;
import com.hedera.node.app.service.token.ReadableTokenStore;
import com.hedera.node.app.service.token.impl.WritableAccountStore;
import com.hedera.node.app.service.token.impl.WritableAirdropStore;
import com.hedera.node.app.service.token.impl.handlers.transfer.CryptoTransferExecutor;
import com.hedera.node.app.service.token.impl.handlers.transfer.TransferContextImpl;
import com.hedera.node.app.service.token.impl.util.CryptoTransferFeeCalculator;
import com.hedera.node.app.service.token.impl.util.TokenAssociateToAccountFeeCalculator;
import com.hedera.node.app.service.token.impl.validators.CryptoTransferValidator;
import com.hedera.node.app.service.token.records.CryptoTransferRecordBuilder;
import com.hedera.node.app.service.token.records.TokenAirdropRecordBuilder;
import com.hedera.node.app.spi.fees.FeeContext;
import com.hedera.node.app.spi.fees.Fees;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.spi.workflows.HandleException;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.spi.workflows.PreHandleContext;
import com.hedera.node.app.spi.workflows.TransactionHandler;
import com.hedera.node.config.data.TokensConfig;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * This class contains all workflow-related functionality regarding {@link
 * HederaFunctionality#TOKEN_AIRDROP}.
 */
@Singleton
public class TokenAirdropHandler implements TransactionHandler {

    private final CryptoTransferValidator validator;

    /**
     * Default constructor for injection.
     */
    @Inject
    public TokenAirdropHandler(@NonNull final CryptoTransferValidator validator) {
        this.validator = validator;
    }

    @Override
    public void preHandle(@NonNull final PreHandleContext context) throws PreCheckException {
        requireNonNull(context);
        pureChecks(context.body());

        final var op = context.body().tokenAirdropOrThrow();
        final var accountStore = context.createStore(ReadableAccountStore.class);
        final var tokenStore = context.createStore(ReadableTokenStore.class);

        for (final var transfers : op.tokenTransfers()) {
            final var tokenID = transfers.tokenOrThrow();
            final var tokenMeta = tokenStore.getTokenMeta(transfers.tokenOrElse(TokenID.DEFAULT));
            validateTruePreCheck(tokenMeta != null, INVALID_TOKEN_ID);
            checkFungibleTokenTransfers(tokenID, transfers.transfers(), context, accountStore);
            checkNftTransfers(tokenID, transfers.nftTransfers(), context, tokenMeta, accountStore);
        }
    }

    @Override
    public void pureChecks(@NonNull final TransactionBody txn) throws PreCheckException {
        requireNonNull(txn);
        final var op = txn.tokenAirdropOrThrow();
        validator.airdropsPureChecks(op);
    }

    @Override
    public void handle(@NonNull final HandleContext context) throws HandleException {
        requireNonNull(context);
        final var txn = context.body();
        final var op = txn.tokenAirdropOrThrow();
        final var pendingStore = context.storeFactory().writableStore(WritableAirdropStore.class);
        final var accountStore = context.storeFactory().writableStore(WritableAccountStore.class);
        var recordBuilder = context.recordBuilders().getOrCreate(TokenAirdropRecordBuilder.class);
        List<TokenTransferList> tokenTransferListList = new ArrayList<>();

        // charge custom fees in advance
        var convertedOp = CryptoTransferTransactionBody.newBuilder()
                .tokenTransfers(op.tokenTransfers())
                .build();
        assessCustomFee(context, convertedOp);

        for (final var xfers : op.tokenTransfers()) {
            final var tokenId = xfers.tokenOrThrow();

            boolean shouldExecuteCryptoTransfer = false;
            var transferListBuilder = TokenTransferList.newBuilder().token(tokenId);

            // process fungible token transfers if any
            if (!xfers.transfers().isEmpty()) {
                // 1. separate transfers in to two lists
                // - one list for executing the transfer and one list for adding to pending state
                var fungibleLists = separateFungibleTransfers(context, tokenId, xfers.transfers());
                var senderOptionalAmount = xfers.transfers().stream()
                        .filter(item -> item.amount() < 0)
                        .findFirst();
                var senderAccount = accountStore.getForModify(
                        senderOptionalAmount.orElseThrow().accountIDOrThrow());
                validateTrue(senderAccount != null, INVALID_ACCOUNT_ID);

                // 2. create and save pending airdrops in to state
                fungibleLists.pendingFungibleAmounts().forEach(accountAmount -> {
                    var pendingId = createFungibleTokenPendingAirdropId(
                            tokenId, senderOptionalAmount.orElseThrow().accountID(), accountAmount.accountID());
                    var pendingValue = PendingAirdropValue.newBuilder()
                            .amount(accountAmount.amount())
                            .build();
                    AccountAirdrop newAccountAirdrop = getNewAccountAirdropAndUpdateStores(
                            senderAccount, pendingId, pendingValue, accountStore, pendingStore);
                    var record = createPendingAirdropRecord(pendingId, newAccountAirdrop);
                    pendingStore.put(pendingId, newAccountAirdrop);
                    recordBuilder.addPendingAirdrop(record);
                });

                // 3. create account amounts and add them to the transfer list
                if (!fungibleLists.transferFungibleAmounts().isEmpty()) {
                    shouldExecuteCryptoTransfer = true;
                    List<AccountAmount> amounts = new LinkedList<>();
                    var receiversAmountList = fungibleLists.transferFungibleAmounts().stream()
                            .filter(item -> item.amount() > 0)
                            .toList();
                    var senderAmount = receiversAmountList.stream()
                            .mapToLong(AccountAmount::amount)
                            .sum();
                    var senderAccountAmount = createAccountAmount(
                            senderOptionalAmount.orElseThrow().accountIDOrThrow(),
                            -senderAmount,
                            senderOptionalAmount.get().isApproval());
                    amounts.add(senderAccountAmount);
                    amounts.addAll(receiversAmountList);

                    transferListBuilder.transfers(amounts);
                }
            }

            // process non-fungible tokens transfers if any
            if (!xfers.nftTransfers().isEmpty()) {
                // 1. separate NFT transfers in to two lists
                // - one list for executing the transfer and one list for adding to pending state
                var nftLists = separateNftTransfers(context, tokenId, xfers.nftTransfers());

                // 2. create and save NFT pending airdrops in to state
                nftLists.pendingNftList().forEach(item -> {
                    var optionalAccountId = nftLists.pendingNftList().stream().findFirst();
                    var senderAccount =
                            accountStore.get(optionalAccountId.orElseThrow().senderAccountIDOrThrow());
                    validateTrue(senderAccount != null, INVALID_ACCOUNT_ID);
                    var pendingId = createNftPendingAirdropId(
                            tokenId, item.serialNumber(), item.senderAccountID(), item.receiverAccountID());
                    AccountAirdrop newAccountAirdrop = getNewAccountAirdropAndUpdateStores(
                            senderAccount, pendingId, null, accountStore, pendingStore);
                    pendingStore.put(pendingId, newAccountAirdrop);
                    var record = createPendingAirdropRecord(pendingId, newAccountAirdrop);
                    recordBuilder.addPendingAirdrop(record);
                });

                // 3. add to transfer list
                if (!nftLists.transferNftList().isEmpty()) {
                    shouldExecuteCryptoTransfer = true;
                    transferListBuilder.nftTransfers(nftLists.transferNftList());
                }
            }

            // build transfer list and add it to tokenTransferListList
            if (shouldExecuteCryptoTransfer) {
                tokenTransferListList.add(transferListBuilder.build());
            }
        }

        // transfer tokens, that are not in pending state, if any...
        if (!tokenTransferListList.isEmpty()) {
            executeCryptoTransfer(context, tokenTransferListList, recordBuilder);
        }
    }

    private void executeCryptoTransfer(
            @NonNull final HandleContext context,
            List<TokenTransferList> tokenTransferList,
            CryptoTransferRecordBuilder recordBuilder) {
        var cryptoTransferBody = CryptoTransferTransactionBody.newBuilder()
                .tokenTransfers(tokenTransferList)
                .build();

        final var syntheticCryptoTransferTxn =
                TransactionBody.newBuilder().cryptoTransfer(cryptoTransferBody).build();

        final var transferContext = new TransferContextImpl(context, cryptoTransferBody, true);

        // We should skip custom fee steps here, because they must be already prepaid
        CryptoTransferExecutor.executeCryptoTransferWithoutCustomFee(
                syntheticCryptoTransferTxn, transferContext, context, validator, recordBuilder);
    }

    private void assessCustomFee(@NonNull final HandleContext context, CryptoTransferTransactionBody body) {

        final var syntheticCryptoTransferTxn =
                TransactionBody.newBuilder().cryptoTransfer(body).build();
        final var transferContext = new TransferContextImpl(context, body, true);
        CryptoTransferExecutor.chargeCustomFee(syntheticCryptoTransferTxn, transferContext);
    }

    /**
     *  Create new {@link AccountAirdrop} and if the sender has already existing pending airdrops
     *  link them together and update the account store and the pending airdrop store with the new values
     */
    private AccountAirdrop getNewAccountAirdropAndUpdateStores(
            Account senderAccount,
            PendingAirdropId pendingId,
            PendingAirdropValue pendingValue,
            WritableAccountStore accountStore,
            WritableAirdropStore pendingStore) {

        AccountAirdrop newAccountAirdrop;
        if (senderAccount.hasHeadPendingAirdropId()) {
            // Get the previous head pending airdrop and update the previous airdrop ID
            var headAirdropId = senderAccount.headPendingAirdropIdOrThrow();
            var headAccountAirdrop = pendingStore.getForModify(headAirdropId);
            validateTrue(headAccountAirdrop != null, INVALID_TOKEN_ID);
            var updatedAirdrop =
                    headAccountAirdrop.copyBuilder().previousAirdrop(pendingId).build();
            pendingStore.put(headAirdropId, updatedAirdrop);

            // Create new account airdrop with next airdrop ID the previous head airdrop
            newAccountAirdrop = createAccountAirdrop(pendingId, pendingValue, headAirdropId);
        } else {
            newAccountAirdrop = createAccountAirdrop(pendingId, pendingValue);
        }
        // Update the sender account with new head pending airdrop
        var updatedSenderAccount =
                senderAccount.copyBuilder().headPendingAirdropId(pendingId).build();
        accountStore.put(updatedSenderAccount);
        return newAccountAirdrop;
    }

    /**
     *  Calculate the fees for the token airdrop transaction. The fees are calculated by combining the
     *  default airdrop fees, the crypto transfer fees and the token association fees.
     */
    @NonNull
    @Override
    public Fees calculateFees(@NonNull final FeeContext feeContext) {
        final var op = feeContext.body().tokenAirdropOrThrow();
        final var tokensConfig = feeContext.configuration().getConfigData(TokensConfig.class);
        validateTrue(tokensConfig.airdropsEnabled(), NOT_SUPPORTED);

        final var defaultAirdropFees =
                feeContext.feeCalculatorFactory().feeCalculator(SubType.DEFAULT).calculate();
        final var cryptoTransferFees = CryptoTransferFeeCalculator.calculate(feeContext, null, op.tokenTransfers());
        final var tokenAssociationFees = calculateTokenAssociationFees(feeContext, op);
        return combineFees(List.of(defaultAirdropFees, cryptoTransferFees, tokenAssociationFees));
    }

    /**
     * Calculate the fees for the token associations that need to be created as part of the airdrop. It gathers all the
     * token associations that need to be created and calculates the fees for each token association.
     */
    private Fees calculateTokenAssociationFees(FeeContext feeContext, TokenAirdropTransactionBody op) {
        // Gather all the token associations that need to be created
        var tokenAssociationsMap = new HashMap<AccountID, Set<TokenID>>();
        final var tokenRelStore = feeContext.readableStore(ReadableTokenRelationStore.class);
        for (var transferList : op.tokenTransfers()) {
            final var tokenToTransfer = transferList.token();
            for (var transfer : transferList.transfers()) {
                if (tokenRelStore.get(transfer.accountID(), tokenToTransfer) == null) {
                    var list = tokenAssociationsMap.getOrDefault(transfer.accountID(), new HashSet<>());
                    list.add(tokenToTransfer);
                    tokenAssociationsMap.put(transfer.accountID(), list);
                }
            }
            for (var nftTransfer : transferList.nftTransfers()) {
                if (tokenRelStore.get(nftTransfer.receiverAccountID(), tokenToTransfer) == null) {
                    var list = tokenAssociationsMap.getOrDefault(nftTransfer.receiverAccountID(), new HashSet<>());
                    list.add(tokenToTransfer);
                    tokenAssociationsMap.put(nftTransfer.receiverAccountID(), list);
                }
            }
        }

        // Calculate the fees for each token association
        var feeList = new ArrayList<Fees>();
        for (var entry : tokenAssociationsMap.entrySet()) {
            final var tokenAssociateBody = TokenAssociateTransactionBody.newBuilder()
                    .account(entry.getKey())
                    .tokens(new ArrayList<>(entry.getValue()))
                    .build();

            final var syntheticTxn = TransactionBody.newBuilder()
                    .tokenAssociate(tokenAssociateBody)
                    .transactionID(feeContext.body().transactionID())
                    .build();

            feeList.add(TokenAssociateToAccountFeeCalculator.calculate(syntheticTxn, feeContext));
        }

        return combineFees(feeList);
    }

    private Fees combineFees(List<Fees> fees) {
        long networkFee = 0, nodeFee = 0, serviceFee = 0;
        for (var fee : fees) {
            networkFee += fee.networkFee();
            nodeFee += fee.nodeFee();
            serviceFee += fee.serviceFee();
        }
        return new Fees(nodeFee, networkFee, serviceFee);
    }

    /**
     * As part of pre-handle, token transfers in the transfer list are plausible.
     *
     * @param tokenID      The ID of the token we are transferring
     * @param transfers    The transfers to check
     * @throws PreCheckException If the transaction is invalid
     */
    private void checkFungibleTokenTransfers(
            @NonNull final TokenID tokenID,
            @NonNull final List<AccountAmount> transfers,
            @NonNull final PreHandleContext ctx,
            @NonNull final ReadableAccountStore accountStore)
            throws PreCheckException {
        final var tokenStore = ctx.createStore(ReadableTokenStore.class);
        final var tokenRelStore = ctx.createStore(ReadableTokenRelationStore.class);
        // Fail if we have custom fees attached to the token
        validateTruePreCheck(tokenHasNoCustomFeesPaidByReceiver(tokenID, tokenStore), INVALID_TRANSACTION);
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
                if (isStakingAccount(ctx.configuration(), account.accountId()) && (isDebit || isCredit)) {
                    throw new PreCheckException(ACCOUNT_IS_IMMUTABLE);
                }

                if (isDebit) {
                    final var tokenRel = tokenRelStore.get(accountId, tokenID);
                    validateTruePreCheck(tokenRel != null, TOKEN_NOT_ASSOCIATED_TO_ACCOUNT);
                    if (accountAmount.isApproval()) {
                        final var topLevelPayer = ctx.payer();
                        final var tokenAllowances = new ArrayList<>(account.tokenAllowances());
                        var haveExistingAllowance = false;
                        for (final var allowance : tokenAllowances) {
                            if (topLevelPayer.equals(allowance.spenderId()) && tokenID.equals(allowance.tokenId())) {
                                haveExistingAllowance = true;
                                final var newAllowanceAmount = allowance.amount() + accountAmount.amount();
                                validateTruePreCheck(newAllowanceAmount >= 0, AMOUNT_EXCEEDS_ALLOWANCE);
                            }
                        }
                        validateTruePreCheck(haveExistingAllowance, SPENDER_DOES_NOT_HAVE_ALLOWANCE);
                    } else {
                        validateTruePreCheck(
                                tokenRel.balance() >= Math.abs(accountAmount.amount()), INVALID_ACCOUNT_AMOUNTS);
                        // If the account is a hollow account, then we require a signature for it.
                        // It is possible that the hollow account has signed this transaction, in which case
                        // we need to finalize the hollow account by setting its key.
                        if (isHollow(account)) {
                            ctx.requireSignatureForHollowAccount(account);
                        } else {
                            ctx.requireKeyOrThrow(account.key(), INVALID_ACCOUNT_ID);
                        }
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

    /**
     * As part of pre-handle, nft transfers in the transfer list are plausible.
     *
     * @param tokenID          The ID of the token we are transferring
     * @param nftTransfersList The nft transfers to check
     * @param context          The context we gather signing keys into
     * @param accountStore     The account store to use to look up accounts
     * @throws PreCheckException If the transaction is invalid
     */
    private void checkNftTransfers(
            final TokenID tokenID,
            final List<NftTransfer> nftTransfersList,
            final PreHandleContext context,
            final ReadableTokenStore.TokenMetadata tokenMeta,
            final ReadableAccountStore accountStore)
            throws PreCheckException {

        final var nftStore = context.createStore(ReadableNftStore.class);
        final var tokenStore = context.createStore(ReadableTokenStore.class);
        final var tokenRelStore = context.createStore(ReadableTokenRelationStore.class);
        final var token = getIfUsable(tokenID, tokenStore);

        validateTruePreCheck(tokenHasNoCustomFeesPaidByReceiver(tokenID, tokenStore), INVALID_TRANSACTION);

        for (final var nftTransfer : nftTransfersList) {
            // Validate accounts
            final var senderId = nftTransfer.senderAccountIDOrElse(AccountID.DEFAULT);
            validateAccountID(senderId, null);
            checkSender(senderId, nftTransfer, context, accountStore);
            checkPayer(senderId, context);
            final var senderAccount = accountStore.getAliasedAccountById(senderId);
            final var tokenRel = tokenRelStore.get(senderId, tokenID);
            validateTruePreCheck(tokenRel != null, TOKEN_NOT_ASSOCIATED_TO_ACCOUNT);

            final var receiverId = nftTransfer.receiverAccountIDOrElse(AccountID.DEFAULT);
            validateAccountID(receiverId, null);
            checkReceiver(receiverId, senderId, nftTransfer, context, tokenMeta, null, accountStore);
            final var receiverAccount = accountStore.getAliasedAccountById(receiverId);

            if (senderAccount == null || receiverAccount == null) {
                throw new PreCheckException(INVALID_TRANSACTION_BODY);
            }

            final var nft = nftStore.get(tokenID, nftTransfer.serialNumber());
            validateTrue(nft != null, INVALID_NFT_ID);

            if (nftTransfer.isApproval()) {
                // If isApproval flag is set then the spender account must have paid for the transaction.
                // The transfer list specifies the owner who granted allowance as sender
                // check if the allowances from the sender account has the payer account as spender
                validateSpenderHasAllowance(senderAccount, context.payer(), tokenID, nft);
            }

            // owner of nft should match the sender in transfer list
            if (nft.hasOwnerId()) {
                validateTrue(nft.ownerId() != null, INVALID_NFT_ID);
                validateTrue(nft.ownerId().equals(senderId), SENDER_DOES_NOT_OWN_NFT_SERIAL_NO);
            } else {
                final var treasuryId = token.treasuryAccountId();
                validateTrue(treasuryId != null, INVALID_ACCOUNT_ID);
                validateTrue(treasuryId.equals(senderId), SENDER_DOES_NOT_OWN_NFT_SERIAL_NO);
            }
        }
    }

    private void validateSpenderHasAllowance(
            final Account owner, final AccountID spender, final TokenID tokenId, final Nft nft) {
        final var approveForAllAllowances = owner.approveForAllNftAllowances();
        final var allowance = AccountApprovalForAllAllowance.newBuilder()
                .spenderId(spender)
                .tokenId(tokenId)
                .build();
        if (!approveForAllAllowances.contains(allowance)) {
            final var approvedSpender = nft.spenderId();
            validateTrue(approvedSpender != null && approvedSpender.equals(spender), SPENDER_DOES_NOT_HAVE_ALLOWANCE);
        }
    }

    private boolean tokenHasNoCustomFeesPaidByReceiver(TokenID tokenId, ReadableTokenStore tokenStore) {
        final var token = getIfUsable(tokenId, tokenStore, PERMIT_PAUSED);
        final var feeMeta = customFeeMetaFrom(token);
        if (feeMeta.tokenType().equals(TokenType.FUNGIBLE_COMMON)) {
            for (var fee : feeMeta.customFees()) {
                if (fee.hasFractionalFee()
                        && !requireNonNull(fee.fractionalFee()).netOfTransfers()) {
                    return false;
                }
            }
        } else if (feeMeta.tokenType().equals(TokenType.NON_FUNGIBLE_UNIQUE)) {
            for (var fee : feeMeta.customFees()) {
                if (fee.hasRoyaltyFee()) {
                    return false;
                }
            }
        }
        return true;
    }
}
