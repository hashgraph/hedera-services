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
import static com.hedera.node.app.service.token.impl.handlers.transfer.customfees.CustomFeeMeta.customFeeMetaFrom;
import static com.hedera.node.app.service.token.impl.util.PendingAirdropUpdater.removePendingAirdrops;
import static com.hedera.node.app.service.token.impl.util.TokenHandlerHelper.getIfUsable;
import static com.hedera.node.app.spi.workflows.HandleException.validateTrue;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.AccountAmount;
import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.base.NftTransfer;
import com.hedera.hapi.node.base.PendingAirdropId;
import com.hedera.hapi.node.base.TokenAssociation;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.base.TokenTransferList;
import com.hedera.hapi.node.base.TokenType;
import com.hedera.hapi.node.state.token.Token;
import com.hedera.hapi.node.token.CryptoTransferTransactionBody;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.token.ReadableTokenStore;
import com.hedera.node.app.service.token.impl.WritableAccountStore;
import com.hedera.node.app.service.token.impl.WritableAirdropStore;
import com.hedera.node.app.service.token.impl.WritableTokenRelationStore;
import com.hedera.node.app.service.token.impl.handlers.transfer.CryptoTransferExecutor;
import com.hedera.node.app.service.token.impl.handlers.transfer.TransferContextImpl;
import com.hedera.node.app.service.token.impl.validators.TokenAirdropValidator;
import com.hedera.node.app.service.token.records.TokenAirdropRecordBuilder;
import com.hedera.node.app.spi.fees.FeeContext;
import com.hedera.node.app.spi.fees.Fees;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.spi.workflows.HandleException;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.spi.workflows.PreHandleContext;
import com.hedera.node.app.spi.workflows.TransactionHandler;
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
public class TokenClaimAirdropHandler extends BaseTokenHandler implements TransactionHandler {

    private final CryptoTransferExecutor executor;
    private final TokenAirdropValidator validator;

    @Inject
    public TokenClaimAirdropHandler(@NonNull CryptoTransferExecutor executor) {
        this.executor = executor;
        this.validator = new TokenAirdropValidator();
    }

    @Override
    public void preHandle(@NonNull PreHandleContext context) throws PreCheckException {}

    @Override
    public void pureChecks(@NonNull TransactionBody txn) throws PreCheckException {}

    @Override
    public void handle(@NonNull HandleContext context) throws HandleException {
        var pendingAirdropStore = context.storeFactory().writableStore(WritableAirdropStore.class);
        var accountStore = context.storeFactory().writableStore(WritableAccountStore.class);
        var tokenStore = context.storeFactory().readableStore(ReadableTokenStore.class);
        var tokenRelStore = context.storeFactory().writableStore(WritableTokenRelationStore.class);
        var op = context.body().tokenClaimAirdropOrThrow();
        var recordBuilder = context.savepointStack().getBaseBuilder(TokenAirdropRecordBuilder.class);

        List<TokenTransferList> transfers = new ArrayList<>();
        List<Token> tokensToAssociate = new ArrayList<>();
        AccountID receiverId = op.pendingAirdrops().getFirst().receiverId();

        // 1. validate pending airdrops and create transfer lists
        for (var airdrop : op.pendingAirdrops()) {
            final var tokenId = airdrop.hasFungibleTokenType()
                    ? airdrop.fungibleTokenTypeOrThrow()
                    : airdrop.nonFungibleTokenOrThrow().tokenId();
            // validate existence and custom fees
            validateTrue(pendingAirdropStore.exists(airdrop), INVALID_TRANSACTION_BODY);
            validateTrue(tokenHasNoCustomFeesPaidByReceiver(tokenId, tokenStore), INVALID_TRANSACTION);

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
        removePendingAirdrops(op.pendingAirdrops(), pendingAirdropStore, accountStore);
    }

    @Override
    public Fees calculateFees(@NonNull FeeContext feeContext) {
        return Fees.FREE;
    }

    private TokenTransferList createTokenTransferList(
            @NonNull PendingAirdropId airdrop,
            @NonNull WritableAirdropStore airdropStore,
            @NonNull TokenID tokenId,
            @NonNull AccountID senderId,
            @NonNull AccountID receiverId) {
        var accountPendingAirdrop = airdropStore.get(airdrop);
        if (airdrop.hasFungibleTokenType()) {
            // process fungible tokens
            var senderAccountAmount = AccountAmount.newBuilder()
                    .amount(-accountPendingAirdrop.pendingAirdropValue().amount())
                    .accountID(senderId)
                    .build();
            var receiverAccountAmount = AccountAmount.newBuilder()
                    .amount(accountPendingAirdrop.pendingAirdropValue().amount())
                    .accountID(receiverId)
                    .build();
            return TokenTransferList.newBuilder()
                    .token(tokenId)
                    .transfers(senderAccountAmount, receiverAccountAmount)
                    .build();
        } else {
            // process non-fungible tokens
            var nftTransfer = NftTransfer.newBuilder()
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
            @NonNull List<Token> tokensToAssociate,
            @NonNull AccountID receiverId,
            @NonNull WritableAccountStore accountStore,
            @NonNull WritableTokenRelationStore tokenRelStore,
            @NonNull TokenAirdropRecordBuilder recordBuilder) {
        createAndLinkTokenRels(accountStore.getAccountById(receiverId), tokensToAssociate, accountStore, tokenRelStore);
        tokensToAssociate.forEach(token -> recordBuilder.addAutomaticTokenAssociation(TokenAssociation.newBuilder()
                .tokenId(token.tokenId())
                .accountId(receiverId)
                .build()));
    }

    private void transferForFree(
            @NonNull List<TokenTransferList> transfers,
            @NonNull HandleContext context,
            @NonNull TokenAirdropRecordBuilder recordBuilder) {
        var cryptoTransferBody = CryptoTransferTransactionBody.newBuilder()
                .tokenTransfers(transfers)
                .build();
        var syntheticCryptoTransferTxn =
                TransactionBody.newBuilder().cryptoTransfer(cryptoTransferBody).build();
        final var transferContext = new TransferContextImpl(context, cryptoTransferBody, true);
        // We should skip custom fee steps here, because they must be already prepaid
        executor.executeCryptoTransferWithoutCustomFee(
                syntheticCryptoTransferTxn, transferContext, context, validator, recordBuilder);
    }

    // todo: same validation is used in TokenAirdropHandler, so when it is merged we can reuse it here too
    private boolean tokenHasNoCustomFeesPaidByReceiver(TokenID tokenId, ReadableTokenStore tokenStore) {
        final var token = getIfUsable(tokenId, tokenStore);
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
