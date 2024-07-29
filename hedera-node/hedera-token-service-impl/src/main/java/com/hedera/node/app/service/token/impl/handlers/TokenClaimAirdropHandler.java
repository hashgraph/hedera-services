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

import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TRANSACTION_BODY;
import static com.hedera.node.app.spi.workflows.HandleException.validateTrue;

import com.hedera.hapi.node.base.AccountAmount;
import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.base.NftTransfer;
import com.hedera.hapi.node.base.TokenTransferList;
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
        // todo implement record builder
        var recordBuilder = context.savepointStack().getBaseBuilder(TokenAirdropRecordBuilder.class);

        // todo validations for custom fees
        // validate if pending airdrop is existing in state
        op.pendingAirdrops().forEach(pendingAirdropId -> {
            validateTrue(pendingAirdropStore.exists(pendingAirdropId), INVALID_TRANSACTION_BODY);
        });

        List<TokenTransferList> transfers = new ArrayList<>();
        List<Token> tokensToAssociate = new ArrayList<>();
        AccountID receiverId = op.pendingAirdrops().getFirst().receiverId();

        for (var airdrop : op.pendingAirdrops()) {
            final var tokenId = airdrop.hasFungibleTokenType()
                    ? airdrop.fungibleTokenTypeOrThrow()
                    : airdrop.nonFungibleTokenOrThrow().tokenId();
            final var senderId = airdrop.senderIdOrThrow();

            if (airdrop.hasFungibleTokenType()) {
                // process fungible tokens
                var accountPendingAirdrop = pendingAirdropStore.get(airdrop);
                var senderAccountAmount = AccountAmount.newBuilder()
                        .amount(-accountPendingAirdrop.pendingAirdropValue().amount())
                        .accountID(senderId)
                        .build();
                var receiverAccountAmount = AccountAmount.newBuilder()
                        .amount(accountPendingAirdrop.pendingAirdropValue().amount())
                        .accountID(receiverId)
                        .build();
                transfers.add(TokenTransferList.newBuilder()
                        .token(tokenId)
                        .transfers(senderAccountAmount, receiverAccountAmount)
                        .build());
            } else {
                // process non-fungible tokens
                var nftTransfer = NftTransfer.newBuilder()
                        // todo check if it is approval
                        //                        .isApproval()
                        .senderAccountID(senderId)
                        .receiverAccountID(receiverId)
                        .serialNumber(airdrop.nonFungibleToken().serialNumber())
                        .build();
                transfers.add(TokenTransferList.newBuilder()
                        .token(tokenId)
                        .nftTransfers(nftTransfer)
                        .build());
            }

            var accountPendingAirdrop = pendingAirdropStore.get(airdrop);
            pendingAirdropStore.remove(airdrop);
            var senderAccount = accountStore.getAccountById(senderId);

            // todo update pending airdrop state and account head (similar to TokenRelListCalculator)
            if (!accountPendingAirdrop.hasPreviousAirdrop() && airdrop.equals(senderAccount.headPendingAirdropId())) {
                // update the nex, if exists
                // update accout's head
            }

            // check if we need new association
            if (tokenRelStore.get(receiverId, tokenId) == null) {
                tokensToAssociate.add(tokenStore.get(tokenId));
            }
        }

        // associate for free
        createAndLinkTokenRels(accountStore.getAccountById(receiverId), tokensToAssociate, accountStore, tokenRelStore);
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

    @Override
    public Fees calculateFees(@NonNull FeeContext feeContext) {
        return Fees.FREE;
    }
}
