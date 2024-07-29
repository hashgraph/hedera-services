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

import static com.hedera.hapi.node.base.ResponseCodeEnum.AMOUNT_EXCEEDS_ALLOWANCE;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_ACCOUNT_AMOUNTS;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_NFT_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TRANSACTION;
import static com.hedera.hapi.node.base.ResponseCodeEnum.NOT_SUPPORTED;
import static com.hedera.hapi.node.base.ResponseCodeEnum.PENDING_NFT_AIRDROP_ALREADY_EXISTS;
import static com.hedera.hapi.node.base.ResponseCodeEnum.SENDER_DOES_NOT_OWN_NFT_SERIAL_NO;
import static com.hedera.hapi.node.base.ResponseCodeEnum.SPENDER_DOES_NOT_HAVE_ALLOWANCE;
import static com.hedera.hapi.node.base.ResponseCodeEnum.TOKEN_NOT_ASSOCIATED_TO_ACCOUNT;
import static com.hedera.node.app.service.token.impl.handlers.transfer.customfees.CustomFeeMeta.customFeeMetaFrom;
import static com.hedera.node.app.service.token.impl.util.AirdropHandlerHelper.createAccountPendingAirdrop;
import static com.hedera.node.app.service.token.impl.util.AirdropHandlerHelper.createFungibleTokenPendingAirdropId;
import static com.hedera.node.app.service.token.impl.util.AirdropHandlerHelper.createNftPendingAirdropId;
import static com.hedera.node.app.service.token.impl.util.AirdropHandlerHelper.createPendingAirdropRecord;
import static com.hedera.node.app.service.token.impl.util.AirdropHandlerHelper.separateFungibleTransfers;
import static com.hedera.node.app.service.token.impl.util.AirdropHandlerHelper.separateNftTransfers;
import static com.hedera.node.app.service.token.impl.util.CryptoTransferHelper.createAccountAmount;
import static com.hedera.node.app.service.token.impl.util.TokenHandlerHelper.getIfUsable;
import static com.hedera.node.app.spi.workflows.HandleException.validateTrue;
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
import com.hedera.hapi.node.state.token.AccountApprovalForAllAllowance;
import com.hedera.hapi.node.state.token.AccountPendingAirdrop;
import com.hedera.hapi.node.state.token.Nft;
import com.hedera.hapi.node.state.token.Token;
import com.hedera.hapi.node.token.CryptoTransferTransactionBody;
import com.hedera.hapi.node.token.TokenAirdropTransactionBody;
import com.hedera.hapi.node.token.TokenAssociateTransactionBody;
import com.hedera.hapi.node.transaction.CustomFee;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.token.ReadableNftStore;
import com.hedera.node.app.service.token.ReadableTokenRelationStore;
import com.hedera.node.app.service.token.ReadableTokenStore;
import com.hedera.node.app.service.token.impl.WritableAccountStore;
import com.hedera.node.app.service.token.impl.WritableAirdropStore;
import com.hedera.node.app.service.token.impl.handlers.transfer.CryptoTransferExecutor;
import com.hedera.node.app.service.token.impl.handlers.transfer.TransferContextImpl;
import com.hedera.node.app.service.token.impl.handlers.transfer.customfees.CustomFeeExemptions;
import com.hedera.node.app.service.token.impl.util.AirdropHandlerHelper;
import com.hedera.node.app.service.token.impl.validators.TokenAirdropValidator;
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
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * This class contains all workflow-related functionality regarding {@link
 * HederaFunctionality#TOKEN_AIRDROP}.
 */
@Singleton
public class TokenAirdropHandler implements TransactionHandler {

    private final TokenAirdropValidator validator;
    private final CryptoTransferExecutor executor;

    /**
     * Default constructor for injection.
     */
    @Inject
    public TokenAirdropHandler(
            @NonNull final TokenAirdropValidator validator, @NonNull CryptoTransferExecutor executor) {
        this.validator = validator;
        this.executor = executor;
    }

    @Override
    public void preHandle(@NonNull final PreHandleContext context) throws PreCheckException {
        requireNonNull(context);
        final var op = context.body().tokenAirdropOrThrow();
        var convertedOp = CryptoTransferTransactionBody.newBuilder()
                .tokenTransfers(op.tokenTransfers())
                .build();
        executor.preHandle(context, convertedOp);
    }

    @Override
    public void pureChecks(@NonNull final TransactionBody txn) throws PreCheckException {
        requireNonNull(txn);
        final var op = txn.tokenAirdropOrThrow();
        validator.pureChecks(op);
    }

    @Override
    public void handle(@NonNull final HandleContext context) throws HandleException {
        requireNonNull(context);
        final var txn = context.body();
        final var op = txn.tokenAirdropOrThrow();
        final var pendingStore = context.storeFactory().writableStore(WritableAirdropStore.class);
        final var accountStore = context.storeFactory().writableStore(WritableAccountStore.class);
        final var nftStore = context.storeFactory().readableStore(ReadableNftStore.class);
        final var tokenStore = context.storeFactory().readableStore(ReadableTokenStore.class);
        final var tokenRelStore = context.storeFactory().readableStore(ReadableTokenRelationStore.class);
        var recordBuilder = context.savepointStack().getBaseBuilder(TokenAirdropRecordBuilder.class);
        List<TokenTransferList> tokenTransferList = new ArrayList<>();

        // charge custom fees in advance
        var convertedOp = CryptoTransferTransactionBody.newBuilder()
                .tokenTransfers(op.tokenTransfers())
                .build();
        assessCustomFee(context, convertedOp);

        for (final var xfers : op.tokenTransfers()) {
            final var tokenId = xfers.tokenOrThrow();
            final var token = getIfUsable(tokenId, tokenStore);
            boolean shouldExecuteCryptoTransfer = false;
            final var transferListBuilder = TokenTransferList.newBuilder().token(tokenId);

            // process fungible token transfers if any
            if (!xfers.transfers().isEmpty()) {
                for (var transfer : xfers.transfers()) {
                    // We want to validate only the receivers. If it's a sender we skip the check.
                    boolean isSender = transfer.amount() < 0;
                    if (isSender) {
                        continue;
                    }
                    final var receiver = transfer.accountID();
                    if (!skipCustomFeeValidation(token, receiver)) {
                        validateTrue(tokenHasNoCustomFeesPaidByReceiver(token), INVALID_TRANSACTION);
                    }
                }

                final var senderOptionalAmount = xfers.transfers().stream()
                        .filter(item -> item.amount() < 0)
                        .findFirst();
                final var senderId = senderOptionalAmount.orElseThrow().accountIDOrThrow();
                final var senderAccount = accountStore.getForModify(senderId);
                validateTrue(senderAccount != null, INVALID_ACCOUNT_ID);

                // 1. Validate allowances and token associations
                validateFungibleTransfers(
                        context.payer(), senderAccount, tokenId, senderOptionalAmount.get(), tokenRelStore);

                // 2. separate transfers in to two lists
                // - one list for executing the transfer and one list for adding to pending state
                final var fungibleLists = separateFungibleTransfers(context, tokenId, xfers.transfers());

                // 3. create and save pending airdrops in to state
                fungibleLists.pendingFungibleAmounts().forEach(accountAmount -> {
                    final var receiver = accountAmount.accountID();
                    final var pendingId = createFungibleTokenPendingAirdropId(
                            tokenId, senderOptionalAmount.orElseThrow().accountID(), receiver);
                    final var pendingValue = PendingAirdropValue.newBuilder()
                            .amount(accountAmount.amount())
                            .build();
                    final AccountPendingAirdrop newAccountPendingAirdrop = getNewAccountPendingAirdropAndUpdateStores(
                            senderAccount, pendingId, pendingValue, accountStore, pendingStore);
                    final var record =
                            createPendingAirdropRecord(pendingId, newAccountPendingAirdrop.pendingAirdropValue());
                    pendingStore.put(pendingId, newAccountPendingAirdrop);
                    recordBuilder.addPendingAirdrop(record);
                });

                // 4. create account amounts and add them to the transfer list
                if (!fungibleLists.transferFungibleAmounts().isEmpty()) {
                    shouldExecuteCryptoTransfer = true;
                    List<AccountAmount> amounts = new LinkedList<>();
                    final var receiversAmountList = fungibleLists.transferFungibleAmounts().stream()
                            .filter(item -> item.amount() > 0)
                            .toList();
                    var senderAmount = receiversAmountList.stream()
                            .mapToLong(AccountAmount::amount)
                            .sum();
                    var newSenderAccountAmount = createAccountAmount(
                            senderOptionalAmount.orElseThrow().accountIDOrThrow(),
                            -senderAmount,
                            senderOptionalAmount.get().isApproval());
                    amounts.add(newSenderAccountAmount);
                    amounts.addAll(receiversAmountList);

                    transferListBuilder.transfers(amounts);
                }
            }

            // process non-fungible tokens transfers if any
            if (!xfers.nftTransfers().isEmpty()) {
                for (var transfer : xfers.nftTransfers()) {
                    final var receiver = transfer.receiverAccountID();
                    if (!skipCustomFeeValidation(token, receiver)) {
                        validateTrue(tokenHasNoCustomFeesPaidByReceiver(token), INVALID_TRANSACTION);
                    }
                }

                // 1. validate NFT transfers
                final var nftTransfer = xfers.nftTransfers().stream().findFirst();
                final var senderId = nftTransfer.orElseThrow().senderAccountIDOrThrow();
                final var senderAccount = accountStore.get(senderId);
                validateTrue(senderAccount != null, INVALID_ACCOUNT_ID);
                validateNftTransfers(
                        context.payer(), senderAccount, tokenId, xfers.nftTransfers(), tokenRelStore, token, nftStore);
                // 2. separate NFT transfers in to two lists
                // - one list for executing the transfer and one list for adding to pending state
                final var nftLists = separateNftTransfers(context, tokenId, xfers.nftTransfers());

                // 3. create and save NFT pending airdrops in to state
                nftLists.pendingNftList().forEach(item -> {
                    final var pendingId = createNftPendingAirdropId(
                            tokenId, item.serialNumber(), item.senderAccountID(), item.receiverAccountID());
                    AccountPendingAirdrop newAccountPendingAirdrop = getNewAccountPendingAirdropAndUpdateStores(
                            senderAccount, pendingId, null, accountStore, pendingStore);
                    // check for existence
                    validateTrue(!pendingStore.exists(pendingId), PENDING_NFT_AIRDROP_ALREADY_EXISTS);
                    pendingStore.put(pendingId, newAccountPendingAirdrop);
                    final var record =
                            createPendingAirdropRecord(pendingId, newAccountPendingAirdrop.pendingAirdropValue());
                    recordBuilder.addPendingAirdrop(record);
                });

                // 3. add to transfer list
                if (!nftLists.transferNftList().isEmpty()) {
                    shouldExecuteCryptoTransfer = true;
                    transferListBuilder.nftTransfers(nftLists.transferNftList());
                }
            }

            // build transfer list and add it to tokenTransferList
            if (shouldExecuteCryptoTransfer) {
                tokenTransferList.add(transferListBuilder.build());
            }
        }

        // transfer tokens, that are not in pending state, if any...
        if (!tokenTransferList.isEmpty()) {
            executeCryptoTransfer(context, tokenTransferList, recordBuilder);
        }
    }

    /**
     * When we do an airdrop we need to check if there are custom fees that needs to be paid by the receiver.
     * If there are, an error is returned.
     * However, there is an exception to this rule - if the receiver is the fee collector or the treasury account
     * they are exempt from paying the custom fees thus we don't need to check if there are custom fees.
     * This method returns if the receiver is the fee collector or the treasury account.
     */
    private static boolean skipCustomFeeValidation(Token token, AccountID receiverId) {
        for (var customFee : token.customFees()) {
            if (CustomFeeExemptions.isPayerExempt(customFeeMetaFrom(token), customFee, receiverId)) {
                return true;
            }
        }
        return false;
    }

    private void validateNftTransfers(
            AccountID payer,
            Account senderAccount,
            TokenID tokenId,
            List<NftTransfer> nftTransfers,
            ReadableTokenRelationStore tokenRelStore,
            Token token,
            ReadableNftStore nftStore) {
        final var tokenRel = tokenRelStore.get(senderAccount.accountIdOrThrow(), tokenId);
        validateTrue(tokenRel != null, TOKEN_NOT_ASSOCIATED_TO_ACCOUNT);
        validateTrue(token != null, INVALID_TOKEN_ID);

        for (NftTransfer nftTransfer : nftTransfers) {
            // If isApproval flag is set then the spender account must have paid for the transaction.
            // The transfer list specifies the owner who granted allowance as sender
            // check if the allowances from the sender account has the payer account as spender
            final var nft = nftStore.get(tokenId, nftTransfer.serialNumber());
            validateTrue(nft != null, INVALID_NFT_ID);
            if (nftTransfer.isApproval()) {
                validateSpenderHasAllowance(senderAccount, payer, tokenId, nft);
            }
            // owner of nft should match the sender in transfer list
            if (nft.hasOwnerId()) {
                validateTrue(nft.ownerId() != null, INVALID_NFT_ID);
                validateTrue(nft.ownerId().equals(senderAccount.accountId()), SENDER_DOES_NOT_OWN_NFT_SERIAL_NO);
            } else {
                final var treasuryId = token.treasuryAccountId();
                validateTrue(treasuryId != null, INVALID_ACCOUNT_ID);
                validateTrue(treasuryId.equals(senderAccount.accountId()), SENDER_DOES_NOT_OWN_NFT_SERIAL_NO);
            }
        }
    }

    private static void validateFungibleTransfers(
            final AccountID payer,
            final Account senderAccount,
            final TokenID tokenId,
            final AccountAmount senderAmount,
            final ReadableTokenRelationStore tokenRelStore) {
        final var tokenRel = tokenRelStore.get(senderAccount.accountIdOrThrow(), tokenId);
        validateTrue(tokenRel != null, TOKEN_NOT_ASSOCIATED_TO_ACCOUNT);
        if (senderAmount.isApproval()) {
            final var tokenAllowances = senderAccount.tokenAllowances();
            var haveExistingAllowance = false;
            for (final var allowance : tokenAllowances) {
                if (payer.equals(allowance.spenderId()) && tokenId.equals(allowance.tokenId())) {
                    haveExistingAllowance = true;
                    final var newAllowanceAmount = allowance.amount() + senderAmount.amount();
                    validateTrue(newAllowanceAmount >= 0, AMOUNT_EXCEEDS_ALLOWANCE);
                    break;
                }
            }
            validateTrue(haveExistingAllowance, SPENDER_DOES_NOT_HAVE_ALLOWANCE);
        } else {
            validateTrue(tokenRel.balance() >= Math.abs(senderAmount.amount()), INVALID_ACCOUNT_AMOUNTS);
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
        executor.executeCryptoTransferWithoutCustomFee(
                syntheticCryptoTransferTxn, transferContext, context, validator, recordBuilder);
    }

    private void assessCustomFee(@NonNull final HandleContext context, CryptoTransferTransactionBody body) {

        final var syntheticCryptoTransferTxn =
                TransactionBody.newBuilder().cryptoTransfer(body).build();
        final var transferContext = new TransferContextImpl(context, body, true);
        executor.chargeCustomFee(syntheticCryptoTransferTxn, transferContext);
    }

    /**
     *  Create new {@link AccountPendingAirdrop} and if the sender has already existing pending airdrops
     *  link them together and update the account store and the pending airdrop store with the new values
     */
    private AccountPendingAirdrop getNewAccountPendingAirdropAndUpdateStores(
            Account senderAccount,
            PendingAirdropId pendingId,
            PendingAirdropValue pendingValue,
            WritableAccountStore accountStore,
            WritableAirdropStore pendingStore) {

        AccountPendingAirdrop newAccountPendingAirdrop;
        if (senderAccount.hasHeadPendingAirdropId()) {
            // Get the previous head pending airdrop and update the previous airdrop ID
            final var headAirdropId = senderAccount.headPendingAirdropIdOrThrow();
            final var headAccountPendingAirdrop = pendingStore.getForModify(headAirdropId);
            validateTrue(headAccountPendingAirdrop != null, INVALID_TOKEN_ID);
            final var updatedAirdrop = headAccountPendingAirdrop
                    .copyBuilder()
                    .previousAirdrop(pendingId)
                    .build();
            pendingStore.patch(headAirdropId, updatedAirdrop);

            // Create new account airdrop with next airdrop ID the previous head airdrop
            newAccountPendingAirdrop = createAccountPendingAirdrop(pendingValue, headAirdropId);
        } else {
            newAccountPendingAirdrop = AirdropHandlerHelper.createAccountPendingAirdrop(pendingValue);
        }
        // Update the sender account with new head pending airdrop
        final var updatedSenderAccount =
                senderAccount.copyBuilder().headPendingAirdropId(pendingId).build();
        accountStore.put(updatedSenderAccount);
        return newAccountPendingAirdrop;
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
        final var cryptoTransferFees = calculateCryptoTransferFees(feeContext, op.tokenTransfers());
        final var tokenAssociationFees = calculateTokenAssociationFees(feeContext, op);
        return combineFees(List.of(defaultAirdropFees, cryptoTransferFees, tokenAssociationFees));
    }

    private Fees calculateCryptoTransferFees(
            @NonNull FeeContext feeContext, @NonNull List<TokenTransferList> tokenTransfers) {
        final var cryptoTransferBody = CryptoTransferTransactionBody.newBuilder()
                .tokenTransfers(tokenTransfers)
                .build();

        final var syntheticCryptoTransferTxn = TransactionBody.newBuilder()
                .cryptoTransfer(cryptoTransferBody)
                .transactionID(feeContext.body().transactionID())
                .build();

        return feeContext.dispatchComputeFees(syntheticCryptoTransferTxn, feeContext.payer());
    }

    /**
     * Calculate the fees for the token associations that need to be created as part of the airdrop. It gathers all the
     * token associations that need to be created and calculates the fees for each token association.
     */
    private Fees calculateTokenAssociationFees(FeeContext feeContext, TokenAirdropTransactionBody op) {
        // Gather all the token associations that need to be created
        final var tokenAssociationsMap = new HashMap<AccountID, Set<TokenID>>();
        final var tokenRelStore = feeContext.readableStore(ReadableTokenRelationStore.class);
        for (var transferList : op.tokenTransfers()) {
            final var tokenToTransfer = transferList.token();
            for (var transfer : transferList.transfers()) {
                if (tokenRelStore.get(transfer.accountID(), tokenToTransfer) == null) {
                    tokenAssociationsMap
                            .computeIfAbsent(transfer.accountID(), ignore -> new HashSet<>())
                            .add(tokenToTransfer);
                }
            }
            for (var nftTransfer : transferList.nftTransfers()) {
                if (tokenRelStore.get(nftTransfer.receiverAccountID(), tokenToTransfer) == null) {
                    tokenAssociationsMap
                            .computeIfAbsent(nftTransfer.receiverAccountID(), ignore -> new HashSet<>())
                            .add(tokenToTransfer);
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

            feeList.add(feeContext.dispatchComputeFees(syntheticTxn, feeContext.payer()));
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

    private boolean tokenHasNoCustomFeesPaidByReceiver(Token token) {
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
