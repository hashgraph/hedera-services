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

import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.TOKEN_NOT_ASSOCIATED_TO_ACCOUNT;
import static com.hedera.node.app.service.token.impl.util.TokenHandlerHelper.getIfUsable;
import static com.hedera.node.app.spi.workflows.HandleException.validateTrue;
import static java.util.Collections.emptyList;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.state.token.Token;
import com.hedera.hapi.node.token.CryptoTransferTransactionBody;
import com.hedera.node.app.service.token.impl.WritableAccountStore;
import com.hedera.node.app.service.token.impl.WritableTokenRelationStore;
import com.hedera.node.app.service.token.impl.WritableTokenStore;
import com.hedera.node.app.service.token.impl.handlers.BaseTokenHandler;
import com.hedera.node.app.service.token.records.CryptoTransferRecordBuilder;
import com.hedera.node.app.spi.workflows.HandleContext;
import edu.umd.cs.findbugs.annotations.NonNull;

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

        for (final var xfers : op.tokenTransfersOrElse(emptyList())) {
            final var tokenId = xfers.tokenOrThrow();
            final var token = getIfUsable(tokenId, tokenStore);

            for (final var aa : xfers.transfersOrElse(emptyList())) {
                final var accountId = aa.accountID();
                validateAndAutoAssociate(
                        accountId, tokenId, token, accountStore, tokenRelStore, handleContext, recordBuilder);
            }

            for (final var aa : xfers.nftTransfersOrElse(emptyList())) {
                final var receiverId = aa.receiverAccountID();
                final var senderId = aa.senderAccountID();
                // sender should be associated already. If not throw exception
                validateTrue(tokenRelStore.get(senderId, tokenId) != null, TOKEN_NOT_ASSOCIATED_TO_ACCOUNT);
                validateAndAutoAssociate(
                        receiverId, tokenId, token, accountStore, tokenRelStore, handleContext, recordBuilder);
            }
        }
    }

    /**
     * Associates the token with the account if it is not already associated. It is auto-associated only if there are
     * open auto-associations available on the account.
     *
     * @param accountId     The account to associate the token with
     * @param tokenId       The tokenID of the token to associate with the account
     * @param token         The token to associate with the account
     * @param accountStore  The account store
     * @param tokenRelStore The token relation store
     * @param handleContext The context
     * @param recordBuilder The record builder
     */
    private void validateAndAutoAssociate(
            @NonNull final AccountID accountId,
            @NonNull final TokenID tokenId,
            @NonNull final Token token,
            @NonNull final WritableAccountStore accountStore,
            @NonNull final WritableTokenRelationStore tokenRelStore,
            @NonNull final HandleContext handleContext,
            @NonNull final CryptoTransferRecordBuilder recordBuilder) {
        final var account = getIfUsable(accountId, accountStore, handleContext.expiryValidator(), INVALID_ACCOUNT_ID);
        final var tokenRel = tokenRelStore.get(accountId, tokenId);

        if (tokenRel == null && account.maxAutoAssociations() > 0) {
            final var newRelation = autoAssociate(account, token, accountStore, tokenRelStore, handleContext);
            recordBuilder.addAutomaticTokenAssociation(
                    asTokenAssociation(newRelation.tokenId(), newRelation.accountId()));
        } else {
            validateTrue(tokenRel != null, TOKEN_NOT_ASSOCIATED_TO_ACCOUNT);
        }
    }
}
