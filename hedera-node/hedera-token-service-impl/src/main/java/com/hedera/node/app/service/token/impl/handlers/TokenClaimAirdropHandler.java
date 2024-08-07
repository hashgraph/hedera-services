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

import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TRANSACTION;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TRANSACTION_BODY;
import static com.hedera.hapi.node.base.ResponseCodeEnum.MAX_PENDING_AIRDROP_ID_EXCEEDED;
import static com.hedera.node.app.spi.workflows.HandleException.validateTrue;

import com.hedera.hapi.node.base.AccountAmount;
import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.base.NftTransfer;
import com.hedera.hapi.node.base.PendingAirdropId;
import com.hedera.hapi.node.base.TokenAssociation;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.base.TokenTransferList;
import com.hedera.hapi.node.state.token.Token;
import com.hedera.hapi.node.token.CryptoTransferTransactionBody;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.token.ReadableTokenStore;
import com.hedera.node.app.service.token.impl.WritableAccountStore;
import com.hedera.node.app.service.token.impl.WritableAirdropStore;
import com.hedera.node.app.service.token.impl.WritableTokenRelationStore;
import com.hedera.node.app.service.token.impl.handlers.transfer.TransferContextImpl;
import com.hedera.node.app.service.token.impl.handlers.transfer.TransferExecutor;
import com.hedera.node.app.service.token.impl.util.PendingAirdropUpdater;
import com.hedera.node.app.service.token.impl.validators.CryptoTransferValidator;
import com.hedera.node.app.service.token.impl.validators.TokenAirdropValidator;
import com.hedera.node.app.service.token.records.TokenAirdropStreamBuilder;
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
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * This class contains all workflow-related functionality regarding {@link
 * HederaFunctionality#TOKEN_CLAIM_AIRDROP}.
 */
@Singleton
public class TokenClaimAirdropHandler extends TransferExecutor implements TransactionHandler {
    private final TokenAirdropValidator validator;

    @Inject
    public TokenClaimAirdropHandler(
            @NonNull final TokenAirdropValidator validator,
            @NonNull final CryptoTransferValidator cryptoTransferValidator) {
        super(cryptoTransferValidator);
        this.validator = validator;
    }

    @Override
    public void preHandle(@NonNull PreHandleContext context) throws PreCheckException {}

    @Override
    public void pureChecks(@NonNull TransactionBody txn) throws PreCheckException {}

    @Override
    public void handle(@NonNull HandleContext context) throws HandleException {
        final var op = context.body().tokenClaimAirdropOrThrow();
        final var tokensConfig = context.configuration().getConfigData(TokensConfig.class);
        validateTrue(
                op.pendingAirdrops().size() < tokensConfig.maxAllowedPendingAirdropsToClaim(),
                MAX_PENDING_AIRDROP_ID_EXCEEDED);

        final var pendingAirdropStore = context.storeFactory().writableStore(WritableAirdropStore.class);
        final var accountStore = context.storeFactory().writableStore(WritableAccountStore.class);
        final var tokenStore = context.storeFactory().readableStore(ReadableTokenStore.class);
        final var tokenRelStore = context.storeFactory().writableStore(WritableTokenRelationStore.class);
        final var recordBuilder = context.savepointStack().getBaseBuilder(TokenAirdropStreamBuilder.class);

        final List<TokenTransferList> transfers = new ArrayList<>();
        final List<Token> tokensToAssociate = new ArrayList<>();
        final AccountID receiverId = op.pendingAirdrops().getFirst().receiverId();

        // 1. validate pending airdrops and create transfer lists
        for (var airdrop : op.pendingAirdrops()) {
            final var tokenId = airdrop.hasFungibleTokenType()
                    ? airdrop.fungibleTokenTypeOrThrow()
                    : airdrop.nonFungibleTokenOrThrow().tokenId();
            // validate existence and custom fees
            validateTrue(pendingAirdropStore.exists(airdrop), INVALID_TRANSACTION_BODY);
            validateTrue(validator.tokenHasNoCustomFeesPaidByReceiver(tokenId, tokenStore), INVALID_TRANSACTION);

            // build transfer lists
            final var senderId = airdrop.senderIdOrThrow();
            transfers.add(createTokenTransferList(airdrop, pendingAirdropStore, tokenId, senderId, receiverId));

            // check if we need new association
            if (tokenRelStore.get(receiverId, tokenId) == null) {
                tokensToAssociate.add(tokenStore.get(tokenId));
            }
        }

        // associate tokens
        associateForFree(tokensToAssociate, receiverId, accountStore, tokenRelStore, recordBuilder);

        // do the crypto transfer
        transferForFree(transfers, context, recordBuilder);

        // Update state
        final var pendingAirdropsUpdater = new PendingAirdropUpdater(pendingAirdropStore, accountStore);
        pendingAirdropsUpdater.removePendingAirdrops(op.pendingAirdrops());
    }

    @Override
    public Fees calculateFees(@NonNull FeeContext feeContext) {
        return Fees.FREE;
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
            final var senderAccountAmount = AccountAmount.newBuilder()
                    .amount(-accountPendingAirdrop.pendingAirdropValue().amount())
                    .accountID(senderId)
                    .build();
            final var receiverAccountAmount = AccountAmount.newBuilder()
                    .amount(accountPendingAirdrop.pendingAirdropValue().amount())
                    .accountID(receiverId)
                    .build();
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
            @NonNull final WritableTokenRelationStore tokenRelStore,
            @NonNull final TokenAirdropStreamBuilder recordBuilder) {
        createAndLinkTokenRels(accountStore.getAccountById(receiverId), tokensToAssociate, accountStore, tokenRelStore);
        tokensToAssociate.forEach(token -> recordBuilder.addAutomaticTokenAssociation(TokenAssociation.newBuilder()
                .tokenId(token.tokenId())
                .accountId(receiverId)
                .build()));
    }

    private void transferForFree(
            @NonNull final List<TokenTransferList> transfers,
            @NonNull final HandleContext context,
            @NonNull final TokenAirdropStreamBuilder recordBuilder) {
        final var cryptoTransferBody = CryptoTransferTransactionBody.newBuilder()
                .tokenTransfers(transfers)
                .build();
        final var syntheticCryptoTransferTxn =
                TransactionBody.newBuilder().cryptoTransfer(cryptoTransferBody).build();
        final var transferContext = new TransferContextImpl(context, cryptoTransferBody, true);
        // We should skip custom fee steps here, because they must be already prepaid
        executeCryptoTransferWithoutCustomFee(syntheticCryptoTransferTxn, transferContext, context, recordBuilder);
    }
}
