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

import static com.hedera.hapi.node.base.ResponseCodeEnum.EMPTY_PENDING_AIRDROP_ID_LIST;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_PENDING_AIRDROP_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.PENDING_AIRDROP_ID_LIST_TOO_LONG;
import static com.hedera.hapi.node.base.ResponseCodeEnum.PENDING_AIRDROP_ID_REPEATED;
import static com.hedera.hapi.node.base.ResponseCodeEnum.TOKEN_AIRDROP_WITH_FALLBACK_ROYALTY;
import static com.hedera.node.app.service.token.impl.util.TokenHandlerHelper.getIfUsable;
import static com.hedera.node.app.spi.workflows.HandleException.validateTrue;
import static com.hedera.node.app.spi.workflows.PreCheckException.validateTruePreCheck;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.AccountAmount;
import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.base.NftTransfer;
import com.hedera.hapi.node.base.PendingAirdropId;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.base.SubType;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.base.TokenTransferList;
import com.hedera.hapi.node.state.token.Token;
import com.hedera.hapi.node.token.CryptoTransferTransactionBody;
import com.hedera.hapi.node.token.TokenClaimAirdropTransactionBody;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.token.ReadableAccountStore;
import com.hedera.node.app.service.token.ReadableAirdropStore;
import com.hedera.node.app.service.token.ReadableTokenStore;
import com.hedera.node.app.service.token.impl.WritableAccountStore;
import com.hedera.node.app.service.token.impl.WritableAirdropStore;
import com.hedera.node.app.service.token.impl.WritableTokenRelationStore;
import com.hedera.node.app.service.token.impl.handlers.transfer.TransferContextImpl;
import com.hedera.node.app.service.token.impl.handlers.transfer.TransferExecutor;
import com.hedera.node.app.service.token.impl.util.PendingAirdropUpdater;
import com.hedera.node.app.service.token.impl.validators.CryptoTransferValidator;
import com.hedera.node.app.service.token.impl.validators.TokenAirdropValidator;
import com.hedera.node.app.service.token.records.CryptoTransferStreamBuilder;
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
import java.util.List;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * This class contains all workflow-related functionality regarding {@link
 * HederaFunctionality#TOKEN_CLAIM_AIRDROP}.
 */
@Singleton
public class TokenClaimAirdropHandler extends TransferExecutor implements TransactionHandler {
    private final TokenAirdropValidator validator;
    private final PendingAirdropUpdater pendingAirdropUpdater;

    @Inject
    public TokenClaimAirdropHandler(
            @NonNull final TokenAirdropValidator validator,
            @NonNull final CryptoTransferValidator cryptoTransferValidator,
            @NonNull final PendingAirdropUpdater pendingAirdropUpdater) {
        super(cryptoTransferValidator);
        this.validator = validator;
        this.pendingAirdropUpdater = pendingAirdropUpdater;
    }

    @Override
    public void preHandle(@NonNull PreHandleContext context) throws PreCheckException {
        requireNonNull(context);
        final var op = requireNonNull(context.body().tokenClaimAirdrop());
        final var pendingAirdrops = op.pendingAirdrops();

        for (final var pendingAirdrop : pendingAirdrops) {
            final var receiverId = pendingAirdrop.receiverIdOrThrow();
            context.requireAliasedKeyOrThrow(receiverId, INVALID_ACCOUNT_ID);
        }
    }

    @Override
    public void pureChecks(@NonNull TransactionBody txn) throws PreCheckException {
        requireNonNull(txn);

        final var op = txn.tokenClaimAirdrop();
        requireNonNull(op);

        final var pendingAirdrops = op.pendingAirdrops();
        validateTruePreCheck(!pendingAirdrops.isEmpty(), EMPTY_PENDING_AIRDROP_ID_LIST);

        final var uniqueAirdrops = Set.copyOf(pendingAirdrops);
        validateTruePreCheck(pendingAirdrops.size() == uniqueAirdrops.size(), PENDING_AIRDROP_ID_REPEATED);
    }

    @Override
    public void handle(@NonNull HandleContext context) throws HandleException {
        final var op = context.body().tokenClaimAirdropOrThrow();
        validateSemantics(context, op);

        final var pendingAirdropStore = context.storeFactory().writableStore(WritableAirdropStore.class);
        final var accountStore = context.storeFactory().writableStore(WritableAccountStore.class);
        final var tokenStore = context.storeFactory().readableStore(ReadableTokenStore.class);
        final var tokenRelStore = context.storeFactory().writableStore(WritableTokenRelationStore.class);
        final var recordBuilder = context.savepointStack().getBaseBuilder(CryptoTransferStreamBuilder.class);

        final var transfers = new ArrayList<TokenTransferList>();
        final var tokensToAssociate = new ArrayList<Token>();
        final var receiverId = op.pendingAirdrops().getFirst().receiverId();

        // 1. validate pending airdrops and create transfer lists
        for (var airdrop : op.pendingAirdrops()) {
            final var tokenId = airdrop.hasFungibleTokenType()
                    ? airdrop.fungibleTokenTypeOrThrow()
                    : airdrop.nonFungibleTokenOrThrow().tokenId();
            // build transfer lists
            final var senderId = airdrop.senderIdOrThrow();
            transfers.add(createTokenTransferList(airdrop, pendingAirdropStore, tokenId, senderId, receiverId));

            // check if we need new association
            if (tokenRelStore.get(receiverId, tokenId) == null) {
                tokensToAssociate.add(tokenStore.get(tokenId));
            }
        }
        // associate tokens
        associateForFree(tokensToAssociate, receiverId, accountStore, tokenRelStore);
        // do the crypto transfer
        transferForFree(transfers, context, recordBuilder);
        // Update state
        pendingAirdropUpdater.removePendingAirdrops(op.pendingAirdrops(), pendingAirdropStore, accountStore);
    }

    /**
     * Validates the semantics of the token claim airdrop transaction.
     * @param context the handle context
     * @param op the token claim airdrop transaction body
     * @throws HandleException if the transaction is invalid
     */
    private void validateSemantics(@NonNull HandleContext context, @NonNull TokenClaimAirdropTransactionBody op)
            throws HandleException {
        final var tokensConfig = context.configuration().getConfigData(TokensConfig.class);
        validateTrue(
                op.pendingAirdrops().size() <= tokensConfig.maxAllowedPendingAirdropsToClaim(),
                PENDING_AIRDROP_ID_LIST_TOO_LONG);

        final var pendingAirdrops = op.pendingAirdrops();
        final var accountStore = context.storeFactory().readableStore(ReadableAccountStore.class);
        final var tokenStore = context.storeFactory().readableStore(ReadableTokenStore.class);
        final var pendingAirdropStore = context.storeFactory().readableStore(ReadableAirdropStore.class);

        for (final var airdrop : pendingAirdrops) {
            final var tokenId = airdrop.hasFungibleTokenType()
                    ? airdrop.fungibleTokenTypeOrThrow()
                    : airdrop.nonFungibleTokenOrThrow().tokenId();
            getIfUsable(tokenId, tokenStore);
            validateTrue(pendingAirdropStore.exists(airdrop), INVALID_PENDING_AIRDROP_ID);
            validateTrue(
                    validator.tokenHasNoRoyaltyWithFallbackFee(tokenId, tokenStore),
                    TOKEN_AIRDROP_WITH_FALLBACK_ROYALTY);
        }
    }

    @Override
    public Fees calculateFees(@NonNull FeeContext feeContext) {
        var tokensConfig = feeContext.configuration().getConfigData(TokensConfig.class);
        validateTrue(tokensConfig.airdropsClaimEnabled(), ResponseCodeEnum.NOT_SUPPORTED);
        final var feeCalculator = feeContext.feeCalculatorFactory().feeCalculator(SubType.DEFAULT);
        feeCalculator.resetUsage();

        return feeCalculator
                .addVerificationsPerTransaction(Math.max(0, feeContext.numTxnSignatures() - 1))
                .calculate();
    }

    private TokenTransferList createTokenTransferList(
            @NonNull final PendingAirdropId airdrop,
            @NonNull final WritableAirdropStore airdropStore,
            @NonNull final TokenID tokenId,
            @NonNull final AccountID senderId,
            @NonNull final AccountID receiverId) {
        final var accountPendingAirdrop = airdropStore.get(airdrop);
        if (airdrop.hasFungibleTokenType()) {
            // process fungible tokens
            final var senderAccountAmount = asAccountAmount(
                    senderId, -accountPendingAirdrop.pendingAirdropValue().amount());
            final var receiverAccountAmount = asAccountAmount(
                    receiverId, accountPendingAirdrop.pendingAirdropValue().amount());
            return TokenTransferList.newBuilder()
                    .token(tokenId)
                    .transfers(senderAccountAmount, receiverAccountAmount)
                    .build();
        } else {
            // process non-fungible tokens
            final var nftTransfer = NftTransfer.newBuilder()
                    .senderAccountID(senderId)
                    .receiverAccountID(receiverId)
                    .serialNumber(airdrop.nonFungibleToken().serialNumber())
                    .build();
            return TokenTransferList.newBuilder()
                    .token(tokenId)
                    .nftTransfers(nftTransfer)
                    .build();
        }
    }

    private void associateForFree(
            @NonNull final List<Token> tokensToAssociate,
            @NonNull final AccountID receiverId,
            @NonNull final WritableAccountStore accountStore,
            @NonNull final WritableTokenRelationStore tokenRelStore) {
        createAndLinkTokenRels(accountStore.getAccountById(receiverId), tokensToAssociate, accountStore, tokenRelStore);
    }

    private void transferForFree(
            @NonNull final List<TokenTransferList> transfers,
            @NonNull final HandleContext context,
            @NonNull final CryptoTransferStreamBuilder recordBuilder) {
        final var cryptoTransferBody = CryptoTransferTransactionBody.newBuilder()
                .tokenTransfers(transfers)
                .build();
        final var syntheticCryptoTransferTxn =
                TransactionBody.newBuilder().cryptoTransfer(cryptoTransferBody).build();
        final var transferContext = new TransferContextImpl(context, cryptoTransferBody, true);
        // We should skip custom fee steps here, because they must be already prepaid
        executeCryptoTransferWithoutCustomFee(syntheticCryptoTransferTxn, transferContext, context, recordBuilder);
    }

    public static AccountAmount asAccountAmount(final AccountID account, final long amount) {
        return AccountAmount.newBuilder().accountID(account).amount(amount).build();
    }
}
