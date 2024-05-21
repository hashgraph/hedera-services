/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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
import static com.hedera.hapi.node.base.ResponseCodeEnum.INSUFFICIENT_TOKEN_BALANCE;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_NFT_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TOKEN_NFT_SERIAL_NUMBER;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TRANSACTION_BODY;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TREASURY_ACCOUNT_FOR_TOKEN;
import static com.hedera.hapi.node.base.ResponseCodeEnum.TOKEN_ID_REPEATED_IN_TOKEN_LIST;
import static com.hedera.node.app.service.token.impl.util.CryptoTransferHelper.createFungibleTransfer;
import static com.hedera.node.app.service.token.impl.util.CryptoTransferHelper.createNftTransfer;
import static com.hedera.node.app.service.token.impl.util.TokenHandlerHelper.TokenValidations.PERMIT_PAUSED;
import static com.hedera.node.app.service.token.impl.util.TokenHandlerHelper.getIfUsable;
import static com.hedera.node.app.spi.key.KeyUtils.isValid;
import static com.hedera.node.app.spi.validation.Validations.validateAccountID;
import static com.hedera.node.app.spi.workflows.HandleException.validateTrue;
import static com.hedera.node.app.spi.workflows.PreCheckException.validateFalsePreCheck;
import static com.hedera.node.app.spi.workflows.PreCheckException.validateTruePreCheck;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.base.NftID;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.base.TokenTransferList;
import com.hedera.hapi.node.state.token.AccountFungibleTokenAllowance;
import com.hedera.hapi.node.token.CryptoTransferTransactionBody;
import com.hedera.hapi.node.token.TokenReference;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.token.ReadableAccountStore;
import com.hedera.node.app.service.token.impl.WritableAccountStore;
import com.hedera.node.app.service.token.impl.WritableNftStore;
import com.hedera.node.app.service.token.impl.WritableTokenRelationStore;
import com.hedera.node.app.service.token.impl.WritableTokenStore;
import com.hedera.node.app.service.token.impl.handlers.transfer.AdjustFungibleTokenChangesStep;
import com.hedera.node.app.service.token.impl.handlers.transfer.NFTOwnersChangeStep;
import com.hedera.node.app.service.token.impl.handlers.transfer.TransferContextImpl;
import com.hedera.node.app.service.token.impl.handlers.transfer.TransferStep;
import com.hedera.node.app.service.token.impl.util.TokenHandlerHelper.TokenRelValidations;
import com.hedera.node.app.spi.fees.FeeContext;
import com.hedera.node.app.spi.fees.Fees;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.spi.workflows.HandleException;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.spi.workflows.PreHandleContext;
import com.hedera.node.app.spi.workflows.TransactionHandler;
import com.hedera.node.config.data.HederaConfig;
import com.hedera.node.config.data.LedgerConfig;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * This class contains all workflow-related functionality regarding {@link HederaFunctionality#TOKEN_REJECT}.
 * This transaction type is used to reject tokens from an account and send them back to the treasury.
 */
@Singleton
public class TokenRejectHandler extends BaseTokenHandler implements TransactionHandler {

    @Inject
    public TokenRejectHandler() {}

    @Override
    public void preHandle(@NonNull final PreHandleContext context) throws PreCheckException {
        requireNonNull(context);
        final var txn = context.body();
        final var op = txn.tokenRejectOrThrow();

        // If owner account is specified, verify that it's valid and require that it signs the transaction.
        // If not specified, payer account is considered as the one rejecting the tokens.
        if (op.hasOwner()) {
            final var accountStore = context.createStore(ReadableAccountStore.class);
            verifySenderAndRequireKey(op.owner(), context, accountStore);
        }
    }

    private void verifySenderAndRequireKey(
            final AccountID senderId, final PreHandleContext context, final ReadableAccountStore accountStore)
            throws PreCheckException {

        final var senderAccount = accountStore.getAliasedAccountById(senderId);
        validateTruePreCheck(senderAccount != null, INVALID_ACCOUNT_ID);

        // If the sender account is immutable, then we throw an exception.
        final var key = senderAccount.key();
        if (key == null || !isValid(key)) {
            throw new PreCheckException(ACCOUNT_IS_IMMUTABLE);
        }
        context.requireKey(key);
    }

    @SuppressWarnings("java:S2259")
    @Override
    public void pureChecks(@NonNull final TransactionBody txn) throws PreCheckException {
        requireNonNull(txn, "Transaction body cannot be null");
        final var op = txn.tokenRejectOrThrow();

        validateFalsePreCheck(op.rejections().isEmpty(), INVALID_TRANSACTION_BODY);
        if (op.hasOwner()) {
            validateAccountID(op.owner(), null);
        }

        var uniqueTokenReferences = new HashSet<TokenReference>();
        for (final var rejection : op.rejections()) {
            if (!uniqueTokenReferences.add(rejection)) {
                throw new PreCheckException(TOKEN_ID_REPEATED_IN_TOKEN_LIST);
            }
            // Ensure one token type per single rejection reference.
            validateFalsePreCheck(rejection.hasFungibleToken() && rejection.hasNft(), INVALID_TRANSACTION_BODY);

            if (rejection.hasFungibleToken()) {
                final var tokenID = rejection.fungibleToken();
                validateTruePreCheck(tokenID != null && !tokenID.equals(TokenID.DEFAULT), INVALID_TOKEN_ID);
            }
            if (rejection.hasNft()) {
                final var nftID = rejection.nft();
                validateTruePreCheck(nftID != null && nftID.tokenId() != null, INVALID_NFT_ID);
                validateTruePreCheck(nftID.serialNumber() > 0, INVALID_TOKEN_NFT_SERIAL_NUMBER);
            }
        }
    }

    @Override
    public void handle(@NonNull final HandleContext context) throws HandleException {
        requireNonNull(context);
        final var op = context.body().tokenRejectOrThrow();
        final var rejectingAccountID = op.ownerOrElse(context.payer());
        final var rejections = op.rejections();

        final var hederaConfig = context.configuration().getConfigData(HederaConfig.class);
        final var ledgerConfig = context.configuration().getConfigData(LedgerConfig.class);
        validateTrue(rejections.size() <= ledgerConfig.tokenRejectsMaxLen(), INVALID_TRANSACTION_BODY);

        final var processedRejectionTransfers =
                processRejectionsForTransferAndRemoveAllowances(rejections, context, rejectingAccountID, hederaConfig);

        final var body = CryptoTransferTransactionBody.newBuilder()
                .tokenTransfers(processedRejectionTransfers)
                .build();

        // Apply all changes to the handleContext's States
        final var transferContext = new TransferContextImpl(context, false, true);
        final var steps = decomposeTransferIntoSteps(body, context.payer());
        steps.forEach(step -> step.doIn(transferContext));
    }

    private List<TokenTransferList> processRejectionsForTransferAndRemoveAllowances(
            @NonNull final List<TokenReference> rejections,
            @NonNull final HandleContext context,
            @NonNull final AccountID rejectingAccountID,
            @NonNull final HederaConfig hederaConfig) {

        // Initialize stores and get the rejecting account
        final var tokenStore = context.writableStore(WritableTokenStore.class);
        final var accountStore = context.writableStore(WritableAccountStore.class);
        final var nftStore = context.writableStore(WritableNftStore.class);
        final var relStore = context.writableStore(WritableTokenRelationStore.class);

        final var rejectingAccount =
                getIfUsable(rejectingAccountID, accountStore, context.expiryValidator(), INVALID_ACCOUNT_ID);

        // Prepare collections for processing
        final var tokenTransferLists = new ArrayList<TokenTransferList>();
        final var updatedFungibleTokenAllowances = new ArrayList<>(rejectingAccount.tokenAllowances());

        final var allowancesIsEnabled = hederaConfig.allowancesIsEnabled();
        for (final var rejection : rejections) {
            if (rejection.hasFungibleToken()) {
                processFungibleTokenRejection(
                        rejection,
                        rejectingAccountID,
                        tokenTransferLists,
                        tokenStore,
                        relStore,
                        updatedFungibleTokenAllowances,
                        allowancesIsEnabled);
            } else if (rejection.hasNft()) {
                processNftRejectionAndRemoveSpenderAllowance(
                        requireNonNull(rejection.nft()),
                        rejectingAccountID,
                        tokenTransferLists,
                        nftStore,
                        tokenStore,
                        allowancesIsEnabled);
            }
        }

        // Update the account with the new fungible token allowances
        if (allowancesIsEnabled) {
            final var updatedAccount = rejectingAccount
                    .copyBuilder()
                    .tokenAllowances(updatedFungibleTokenAllowances)
                    .build();
            accountStore.put(updatedAccount);
        }
        return tokenTransferLists;
    }

    private void processFungibleTokenRejection(
            @NonNull final TokenReference rejection,
            @NonNull final AccountID rejectingAccountID,
            @NonNull final List<TokenTransferList> tokenTransferLists,
            @NonNull final WritableTokenStore tokenStore,
            @NonNull final WritableTokenRelationStore relStore,
            @NonNull final List<AccountFungibleTokenAllowance> tokenAllowancesForRejectingAccount,
            final boolean allowancesEnabled) {
        final var token = getIfUsable(requireNonNull(rejection.fungibleToken()), tokenStore, PERMIT_PAUSED);
        final var tokenId = requireNonNull(token.tokenId());

        final var tokenRelation = getIfUsable(rejectingAccountID, tokenId, relStore, TokenRelValidations.PERMIT_FROZEN);
        validateTrue(token.treasuryAccountId() != null, INVALID_TREASURY_ACCOUNT_FOR_TOKEN);
        validateTrue(tokenRelation.balance() > 0, INSUFFICIENT_TOKEN_BALANCE);

        tokenTransferLists.add(createFungibleTransfer(
                tokenId, rejectingAccountID, tokenRelation.balance(), token.treasuryAccountId()));

        if (allowancesEnabled) {
            tokenAllowancesForRejectingAccount.removeIf(allowance -> tokenId.equals(allowance.tokenId()));
        }
    }

    private void processNftRejectionAndRemoveSpenderAllowance(
            @NonNull final NftID nftID,
            @NonNull final AccountID ownerId,
            @NonNull final List<TokenTransferList> tokenTransferLists,
            @NonNull final WritableNftStore nftStore,
            @NonNull final WritableTokenStore tokenStore,
            final boolean allowancesEnabled) {
        final var nft = getIfUsable(nftID, nftStore);
        validateTrue(nft.hasOwnerId() && ownerId.equals(nft.ownerId()), INVALID_ACCOUNT_ID);

        final var token = getIfUsable(requireNonNull(nftID.tokenId()), tokenStore, PERMIT_PAUSED);
        final var tokenId = token.tokenId();
        final var tokenTreasury = token.treasuryAccountId();
        tokenTransferLists.add(createNftTransfer(tokenId, ownerId, tokenTreasury, nftID.serialNumber()));

        // Remove allowance for the NFT
        if (nft.hasSpenderId() && allowancesEnabled) {
            nftStore.put(nft.copyBuilder().spenderId((AccountID) null).build());
        }
    }

    private List<TransferStep> decomposeTransferIntoSteps(
            final CryptoTransferTransactionBody txn, final AccountID topLevelPayer) {
        final var steps = new ArrayList<TransferStep>();

        final var assessFungibleTokenTransfers = new AdjustFungibleTokenChangesStep(txn, topLevelPayer);
        steps.add(assessFungibleTokenTransfers);

        final var changeNftOwners = new NFTOwnersChangeStep(txn, topLevelPayer);
        steps.add(changeNftOwners);

        return steps;
    }

    @NonNull
    @Override
    public Fees calculateFees(final FeeContext feeContext) {
        // Todo: Implement the logic for calculating fees
        return Fees.FREE;
    }
}
