/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.token.impl.handlers.transfer;

import static com.hedera.hapi.node.base.ResponseCodeEnum.ACCOUNT_FROZEN_FOR_TOKEN;
import static com.hedera.hapi.node.base.ResponseCodeEnum.ACCOUNT_KYC_NOT_GRANTED_FOR_TOKEN;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_NFT_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.NO_REMAINING_AUTOMATIC_ASSOCIATIONS;
import static com.hedera.hapi.node.base.ResponseCodeEnum.SENDER_DOES_NOT_OWN_NFT_SERIAL_NO;
import static com.hedera.hapi.node.base.ResponseCodeEnum.TOKEN_NOT_ASSOCIATED_TO_ACCOUNT;
import static com.hedera.node.app.service.token.impl.handlers.transfer.NFTOwnersChangeStep.validateSpenderHasAllowance;
import static com.hedera.node.app.service.token.impl.util.TokenHandlerHelper.getIfUsable;
import static com.hedera.node.app.spi.workflows.HandleException.validateFalse;
import static com.hedera.node.app.spi.workflows.HandleException.validateTrue;
import static java.util.Collections.emptyList;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.TokenAssociation;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.state.token.Token;
import com.hedera.hapi.node.token.CryptoTransferTransactionBody;
import com.hedera.node.app.service.token.impl.WritableAccountStore;
import com.hedera.node.app.service.token.impl.WritableNftStore;
import com.hedera.node.app.service.token.impl.WritableTokenRelationStore;
import com.hedera.node.app.service.token.impl.WritableTokenStore;
import com.hedera.node.app.service.token.impl.handlers.BaseTokenHandler;
import com.hedera.node.app.service.token.records.CryptoTransferRecordBuilder;
import com.hedera.node.app.spi.workflows.HandleContext;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ArrayList;
import java.util.List;

/**
 * Associates the token with the sender and receiver accounts if they are not already associated.
 * They are auto-associated only if there are open auto-associations available on the account.
 */
public class AssociateTokenRecipientsStep extends BaseTokenHandler implements TransferStep {
    private final CryptoTransferTransactionBody op;

    public AssociateTokenRecipientsStep(@NonNull final CryptoTransferTransactionBody op) {
        this.op = requireNonNull(op);
    }

    @Override
    public void doIn(@NonNull final TransferContext transferContext) {
        requireNonNull(transferContext);
        final var handleContext = transferContext.getHandleContext();
        final var tokenStore = handleContext.writableStore(WritableTokenStore.class);
        final var tokenRelStore = handleContext.writableStore(WritableTokenRelationStore.class);
        final var accountStore = handleContext.writableStore(WritableAccountStore.class);
        final var recordBuilder = handleContext.recordBuilder(CryptoTransferRecordBuilder.class);
        final var nftStore = handleContext.writableStore(WritableNftStore.class);
        final var expiryValidator = handleContext.expiryValidator();
        final List<TokenAssociation> newAssociations = new ArrayList<>();
        final var payer = handleContext.payer();

        for (final var xfers : op.tokenTransfersOrElse(emptyList())) {
            final var tokenId = xfers.tokenOrThrow();
            final var token = getIfUsable(tokenId, tokenStore);

            for (final var aa : xfers.transfersOrElse(emptyList())) {
                final var accountId = aa.accountID();
                final TokenAssociation newAssociation = validateAndBuildAutoAssociation(
                        accountId, tokenId, token, accountStore, tokenRelStore, handleContext);
                if (newAssociation != null) {
                    newAssociations.add(newAssociation);
                }
            }

            for (final var nftTransfer : xfers.nftTransfersOrElse(emptyList())) {
                final var receiverId = nftTransfer.receiverAccountID();
                final var senderId = nftTransfer.senderAccountID();
                // sender should be associated already. If not throw exception
                validateTrue(tokenRelStore.get(senderId, tokenId) != null, TOKEN_NOT_ASSOCIATED_TO_ACCOUNT);

                final var nft = nftStore.get(tokenId, nftTransfer.serialNumber());
                validateTrue(nft != null, INVALID_NFT_ID);

                final var senderAccount = getIfUsable(senderId, accountStore, expiryValidator, INVALID_ACCOUNT_ID);
                if (nftTransfer.isApproval()) {
                    // If isApproval flag is set then the spender account must have paid for the transaction.
                    // The transfer list specifies the owner who granted allowance as sender
                    // check if the allowances from the sender account has the payer account as spender
                    validateSpenderHasAllowance(senderAccount, payer, tokenId, nft);
                }

                if (nft.hasOwnerId()) {
                    validateTrue(nft.ownerId().equals(senderId), SENDER_DOES_NOT_OWN_NFT_SERIAL_NO);
                } else {
                    validateTrue(token.treasuryAccountId().equals(senderId), SENDER_DOES_NOT_OWN_NFT_SERIAL_NO);
                }

                final TokenAssociation newAssociation = validateAndBuildAutoAssociation(
                        receiverId, tokenId, token, accountStore, tokenRelStore, handleContext);
                if (newAssociation != null) {
                    newAssociations.add(newAssociation);
                }
            }
        }

        for (TokenAssociation newAssociation : newAssociations) {
            recordBuilder.addAutomaticTokenAssociation(newAssociation);
        }
    }

    /**
     * Associates the token with the account if it is not already associated. It is auto-associated only if there are
     * open auto-associations available on the account.
     *
     * @param accountId         The account to associate the token with
     * @param tokenId           The tokenID of the token to associate with the account
     * @param token             The token to associate with the account
     * @param accountStore      The account store
     * @param tokenRelStore     The token relation store
     * @param handleContext     The context
     */
    private TokenAssociation validateAndBuildAutoAssociation(
            @NonNull final AccountID accountId,
            @NonNull final TokenID tokenId,
            @NonNull final Token token,
            @NonNull final WritableAccountStore accountStore,
            @NonNull final WritableTokenRelationStore tokenRelStore,
            @NonNull final HandleContext handleContext) {
        final var account = getIfUsable(accountId, accountStore, handleContext.expiryValidator(), INVALID_ACCOUNT_ID);
        final var tokenRel = tokenRelStore.get(accountId, tokenId);
        final var config = handleContext.configuration();

        if (tokenRel == null && account.maxAutoAssociations() > 0) {
            validateFalse(
                    account.usedAutoAssociations() >= account.maxAutoAssociations(),
                    NO_REMAINING_AUTOMATIC_ASSOCIATIONS);
            validateFalse(token.hasKycKey(), ACCOUNT_KYC_NOT_GRANTED_FOR_TOKEN);
            validateFalse(token.accountsFrozenByDefault(), ACCOUNT_FROZEN_FOR_TOKEN);
            final var newRelation = autoAssociate(account, token, accountStore, tokenRelStore, config);
            return asTokenAssociation(newRelation.tokenId(), newRelation.accountId());
        } else {
            validateTrue(tokenRel != null, TOKEN_NOT_ASSOCIATED_TO_ACCOUNT);
            validateFalse(tokenRel.frozen(), ACCOUNT_FROZEN_FOR_TOKEN);
            return null;
        }
    }
}
