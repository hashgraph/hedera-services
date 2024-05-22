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
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_OWNER_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TOKEN_NFT_SERIAL_NUMBER;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TRANSACTION_BODY;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TREASURY_ACCOUNT_FOR_TOKEN;
import static com.hedera.hapi.node.base.ResponseCodeEnum.NOT_SUPPORTED;
import static com.hedera.hapi.node.base.ResponseCodeEnum.TOKEN_REFERENCE_REPEATED;
import static com.hedera.hapi.node.base.SubType.TOKEN_FUNGIBLE_COMMON;
import static com.hedera.hapi.node.base.SubType.TOKEN_NON_FUNGIBLE_UNIQUE;
import static com.hedera.node.app.hapi.fees.usage.SingletonUsageProperties.USAGE_PROPERTIES;
import static com.hedera.node.app.hapi.fees.usage.crypto.CryptoOpsUsage.LONG_ACCOUNT_AMOUNT_BYTES;
import static com.hedera.node.app.hapi.fees.usage.token.TokenOpsUsage.LONG_BASIC_ENTITY_ID_SIZE;
import static com.hedera.node.app.hapi.fees.usage.token.entities.TokenEntitySizes.TOKEN_ENTITY_SIZES;
import static com.hedera.node.app.service.token.impl.util.CryptoTransferHelper.createFungibleTransfer;
import static com.hedera.node.app.service.token.impl.util.CryptoTransferHelper.createNftTransfer;
import static com.hedera.node.app.service.token.impl.util.CryptoTransferHelper.nftTransfer;
import static com.hedera.node.app.service.token.impl.util.TokenHandlerHelper.TokenValidations.PERMIT_PAUSED;
import static com.hedera.node.app.service.token.impl.util.TokenHandlerHelper.getIfUsable;
import static com.hedera.node.app.service.token.impl.util.TokenHandlerHelper.getIfUsableForAliasedId;
import static com.hedera.node.app.spi.key.KeyUtils.isValid;
import static com.hedera.node.app.spi.validation.Validations.validateAccountID;
import static com.hedera.node.app.spi.workflows.HandleException.validateTrue;
import static com.hedera.node.app.spi.workflows.PreCheckException.validateFalsePreCheck;
import static com.hedera.node.app.spi.workflows.PreCheckException.validateTruePreCheck;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.base.NftID;
import com.hedera.hapi.node.base.SubType;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.base.TokenTransferList;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.hapi.node.state.token.AccountFungibleTokenAllowance;
import com.hedera.hapi.node.state.token.Nft;
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
import com.hedera.node.config.data.FeesConfig;
import com.hedera.node.config.data.HederaConfig;
import com.hedera.node.config.data.LedgerConfig;
import com.hedera.node.config.data.TokensConfig;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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
            verifyOwnerAndRequireKey(op.ownerOrThrow(), context, accountStore);
        }
    }

    /**
     * Verifies that the owner account exists and ensures the account's key is valid and required for the transaction.
     *
     * @param ownerId The AccountID of the owner whose key needs to be validated.
     * @param context The PreHandleContext providing transaction context.
     * @param accountStore The store to access readable account information.
     * @throws PreCheckException If the sender's account is immutable or the sender's account ID is invalid.
     */
    private void verifyOwnerAndRequireKey(
            @NonNull final AccountID ownerId,
            @NonNull final PreHandleContext context,
            @NonNull final ReadableAccountStore accountStore)
            throws PreCheckException {

        final var ownerAccount = accountStore.getAliasedAccountById(ownerId);
        validateTruePreCheck(ownerAccount != null, INVALID_OWNER_ID);

        // If the sender account is immutable, then we throw an exception.
        final var key = ownerAccount.key();
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

        final var uniqueTokenReferences = new HashSet<TokenReference>();
        for (final var rejection : op.rejections()) {
            if (!uniqueTokenReferences.add(rejection)) {
                throw new PreCheckException(TOKEN_REFERENCE_REPEATED);
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
        final var tokensConfig = context.configuration().getConfigData(TokensConfig.class);
        validateTrue(tokensConfig.tokenRejectEnabled(), NOT_SUPPORTED);
        validateTrue(rejections.size() <= ledgerConfig.tokenRejectsMaxLen(), INVALID_TRANSACTION_BODY);

        final var accountStore = context.writableStore(WritableAccountStore.class);
        final var rejectingAccount = getIfUsableForAliasedId(
                rejectingAccountID, accountStore, context.expiryValidator(), INVALID_ACCOUNT_ID);

        final var processedRejections =
                processRejectionsForTransferAndAllowancesRemoval(rejections, context, rejectingAccount);
        final var body = CryptoTransferTransactionBody.newBuilder()
                .tokenTransfers(processedRejections.tokenTransferLists)
                .build();

        // Apply all changes to the handleContext's states by performing the transfer to the treasuries
        final var transferContext = new TransferContextImpl(context, false, true);
        final var steps = decomposeTransferIntoSteps(body, context.payer());
        steps.forEach(step -> step.doIn(transferContext));

        // Update the token allowances
        if (hederaConfig.allowancesIsEnabled()) {
            final var updatedAccount = rejectingAccount
                    .copyBuilder()
                    .tokenAllowances(processedRejections.updatedFungibleTokenAllowances)
                    .build();
            accountStore.put(updatedAccount);

            // Update the NFT allowances
            final var nftStore = context.writableStore(WritableNftStore.class);
            processedRejections.processedNFTs.forEach(nft -> {
                if (nft.hasSpenderId()) {
                    nftStore.put(nft.copyBuilder().spenderId((AccountID) null).build());
                }
            });
        }
    }

    /**
     * Processes the rejections specified in the transaction to prepare for the transfers and the removal of allowances.
     *
     * @param rejections The list of TokenReferences representing the rejections to process.
     * @param context The HandleContext providing the current handling context.
     * @param rejectingAccount The Account rejecting its tokens.
     * @return ProcessedRejections containing the token transfer lists, updated fungible token allowances, and processed NFTs.
     */
    private ProcessedRejections processRejectionsForTransferAndAllowancesRemoval(
            @NonNull final List<TokenReference> rejections,
            @NonNull final HandleContext context,
            @NonNull final Account rejectingAccount) {

        // Initialize stores and get the rejecting account
        final var tokenStore = context.writableStore(WritableTokenStore.class);
        final var nftStore = context.writableStore(WritableNftStore.class);
        final var relStore = context.writableStore(WritableTokenRelationStore.class);
        final var accountID = rejectingAccount.accountIdOrThrow();

        // Prepare collections for processing
        final var tokenTransferListMap = new HashMap<TokenID, TokenTransferList>();
        final var updatedFungibleTokenAllowances = new ArrayList<>(rejectingAccount.tokenAllowances());
        final var processedNFTs = new ArrayList<Nft>();

        for (final var rejection : rejections) {
            if (rejection.hasFungibleToken()) {
                processFungibleTokenRejection(
                        rejection,
                        accountID,
                        tokenTransferListMap,
                        tokenStore,
                        relStore,
                        updatedFungibleTokenAllowances);
            } else if (rejection.hasNft()) {
                processNftRejectionAndRemoveSpenderAllowance(
                        rejection.nftOrThrow(), accountID, tokenTransferListMap, processedNFTs, nftStore, tokenStore);
            }
        }

        return new ProcessedRejections(
                tokenTransferListMap.values().stream().toList(), updatedFungibleTokenAllowances, processedNFTs);
    }

    /**
     * Processes a single fungible token rejection by performing validations and adds the transfer
     * for the rejected token.
     *
     * @param rejection The token reference detailing the rejection.
     * @param rejectingAccountID The AccountID of the account rejecting the token.
     * @param tokenTransferListMap A map to accumulate the token transfer lists.
     * @param tokenStore Access to writable token data.
     * @param relStore Access to writable token relations.
     * @param tokenAllowancesForRejectingAccount List to be updated with any changes to fungible token allowances.
     */
    private void processFungibleTokenRejection(
            @NonNull final TokenReference rejection,
            @NonNull final AccountID rejectingAccountID,
            @NonNull final Map<TokenID, TokenTransferList> tokenTransferListMap,
            @NonNull final WritableTokenStore tokenStore,
            @NonNull final WritableTokenRelationStore relStore,
            @NonNull final List<AccountFungibleTokenAllowance> tokenAllowancesForRejectingAccount) {
        final var token = getIfUsable(rejection.fungibleTokenOrThrow(), tokenStore, PERMIT_PAUSED);
        final var tokenId = token.tokenIdOrThrow();

        final var tokenRelation = getIfUsable(rejectingAccountID, tokenId, relStore, TokenRelValidations.PERMIT_FROZEN);
        validateTrue(token.treasuryAccountId() != null, INVALID_TREASURY_ACCOUNT_FOR_TOKEN);
        validateTrue(tokenRelation.balance() > 0, INSUFFICIENT_TOKEN_BALANCE);

        tokenTransferListMap.put(
                tokenId,
                createFungibleTransfer(
                        tokenId, rejectingAccountID, tokenRelation.balance(), token.treasuryAccountId()));

        tokenAllowancesForRejectingAccount.removeIf(allowance -> tokenId.equals(allowance.tokenId()));
    }

    /**
     * Processes a single NFT rejection by performing validations and adds the transfer for the rejected NFT.
     *
     * @param nftID The NftID of the NFT being rejected.
     * @param rejectingAccountID The AccountID of the owner rejecting the NFT.
     * @param tokenTransferListMap A map to accumulate the token transfer lists.
     * @param processedNFTs A list to accumulate the processed NFTs.
     * @param nftStore Access to writable NFT data.
     * @param tokenStore Access to writable token data.
     */
    private void processNftRejectionAndRemoveSpenderAllowance(
            @NonNull final NftID nftID,
            @NonNull final AccountID rejectingAccountID,
            @NonNull final Map<TokenID, TokenTransferList> tokenTransferListMap,
            @NonNull final List<Nft> processedNFTs,
            @NonNull final WritableNftStore nftStore,
            @NonNull final WritableTokenStore tokenStore) {
        final var nft = getIfUsable(nftID, nftStore);
        validateTrue(nft.hasOwnerId() && rejectingAccountID.equals(nft.ownerId()), INVALID_ACCOUNT_ID);

        final var token = getIfUsable(nftID.tokenIdOrThrow(), tokenStore, PERMIT_PAUSED);
        final var tokenId = token.tokenId();
        final var tokenTreasury = token.treasuryAccountIdOrThrow();

        final var newNftTransfer = nftTransfer(rejectingAccountID, tokenTreasury, nftID.serialNumber());
        if (tokenTransferListMap.containsKey(tokenId)) {
            final var nftTransfersWithSameTokenId =
                    new ArrayList<>(tokenTransferListMap.get(tokenId).nftTransfers());
            nftTransfersWithSameTokenId.add(newNftTransfer);
            tokenTransferListMap.put(tokenId, createNftTransfer(tokenId, nftTransfersWithSameTokenId));
        } else {
            tokenTransferListMap.put(tokenId, createNftTransfer(tokenId, newNftTransfer));
        }
        processedNFTs.add(nft);
    }

    /**
     * Decomposes the cryptoTransfer for the Reject operation into a list of steps.
     * Each step validates the preconditions needed from TransferContextImpl in order to perform the transfers.
     *
     * <p>Steps included:</p>
     * <ol>
     *     <li>Adjust fungible token balances.</li>
     *     <li>Change NFT ownerships.</li>
     * </ol>
     *
     * @param txn The transaction body containing the reject transfers.
     * @param topLevelPayer The account responsible for transaction fees.
     * @return A list of transfer steps to execute.
     */
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
        final var body = feeContext.body();
        final var op = body.tokenRejectOrThrow();
        final var config = feeContext.configuration();
        final var tokenMultiplier =
                requireNonNull(config).getConfigData(FeesConfig.class).tokenTransferUsageMultiplier();

        final int totalRejections = op.rejections().size();
        int numOfFungibleTokenRejections = 0;
        int numOfNFTRejections = 0;
        for (final var rejection : op.rejections()) {
            if (rejection.hasFungibleToken()) {
                numOfFungibleTokenRejections++;
            } else {
                numOfNFTRejections++;
            }
        }

        final int weightedTokensInvolved = tokenMultiplier * totalRejections;
        final int weightedFungibleTokens = tokenMultiplier * numOfFungibleTokenRejections;

        final long bpt =
                calculateBytesPerTransaction(weightedTokensInvolved, weightedFungibleTokens, numOfNFTRejections);
        final long rbs = calculateRamByteSeconds(weightedTokensInvolved, weightedFungibleTokens, numOfNFTRejections);

        return feeContext
                .feeCalculator(getSubType(numOfNFTRejections))
                .addBytesPerTransaction(bpt)
                .addRamByteSeconds(rbs)
                .calculate();
    }

    private SubType getSubType(final int numOfNFTRejections) {
        return numOfNFTRejections != 0 ? TOKEN_NON_FUNGIBLE_UNIQUE : TOKEN_FUNGIBLE_COMMON;
    }

    private long calculateBytesPerTransaction(
            final int weightedTokens, final int weightedFungibleTokens, final int numNFTRejections) {
        return weightedTokens * LONG_BASIC_ENTITY_ID_SIZE
                + weightedFungibleTokens * LONG_ACCOUNT_AMOUNT_BYTES
                + TOKEN_ENTITY_SIZES.bytesUsedForUniqueTokenTransfers(numNFTRejections);
    }

    private long calculateRamByteSeconds(
            final int weightedTokensInvolved, final int weightedFungibleTokens, final int numOfNFTRejections) {
        return USAGE_PROPERTIES.legacyReceiptStorageSecs()
                * TOKEN_ENTITY_SIZES.bytesUsedToRecordTokenTransfers(
                        weightedTokensInvolved, weightedFungibleTokens, numOfNFTRejections);
    }

    /**
     * A private record to encapsulate the processed rejections for the token transfers.
     *
     * @param tokenTransferLists The list of token transfer lists.
     * @param updatedFungibleTokenAllowances The updated fungible token allowances for the owner account.
     * @param processedNFTs The list of processed NFTs.
     */
    private record ProcessedRejections(
            List<TokenTransferList> tokenTransferLists,
            List<AccountFungibleTokenAllowance> updatedFungibleTokenAllowances,
            List<Nft> processedNFTs) {}
}
