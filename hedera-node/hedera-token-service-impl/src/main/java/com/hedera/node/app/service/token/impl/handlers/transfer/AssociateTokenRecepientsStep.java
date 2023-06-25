/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.hedera.node.app.service.token.impl.handlers.transfer;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.state.token.Token;
import com.hedera.hapi.node.token.CryptoTransferTransactionBody;
import com.hedera.node.app.service.token.impl.WritableAccountStore;
import com.hedera.node.app.service.token.impl.WritableTokenRelationStore;
import com.hedera.node.app.service.token.impl.WritableTokenStore;
import com.hedera.node.app.service.token.impl.handlers.BaseTokenHandler;
import com.hedera.node.app.spi.workflows.HandleContext;

import java.util.Set;

import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.TOKEN_NOT_ASSOCIATED_TO_ACCOUNT;
import static com.hedera.node.app.service.token.impl.util.TokenHandlerHelper.getIfUsable;
import static com.hedera.node.app.spi.workflows.HandleException.validateTrue;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_ID;

public class AssociateTokenRecepientsStep extends BaseTokenHandler implements TransferStep {
    private final CryptoTransferTransactionBody op;
    public AssociateTokenRecepientsStep(final CryptoTransferTransactionBody op) {
        this.op = op;
    }

    @Override
    public Set<Key> authorizingKeysIn(final TransferContext transferContext) {
        return null;
    }

    @Override
    public void doIn(final TransferContext transferContext) {
        final var handleContext = transferContext.getHandleContext();
        final var tokenStore = handleContext.writableStore(WritableTokenStore.class);
        final var tokenRelStore = handleContext.writableStore(WritableTokenRelationStore.class);
        final var accountStore = handleContext.writableStore(WritableAccountStore.class);

        for (var xfers : op.tokenTransfers()) {
            final var tokenId = xfers.token();
            final var token = getIfUsable(tokenId, tokenStore);
            for(final var aa : xfers.transfers()){
                final var accountId = aa.accountID();
                validateAndAutoAssociate(accountId, tokenId, token, accountStore, tokenRelStore, handleContext);
            }

            for(final var aa : xfers.nftTransfers()){
                final var receiverId = aa.receiverAccountID();
                final var senderId = aa.senderAccountID();
                validateAndAutoAssociate(senderId, tokenId, token, accountStore, tokenRelStore, handleContext);
                validateAndAutoAssociate(receiverId, tokenId, token, accountStore, tokenRelStore, handleContext);
            }
        }
    }

    private void validateAndAutoAssociate(final AccountID accountId,
                                          final TokenID tokenId,
                                          final Token token,
                                          final WritableAccountStore accountStore,
                                          final WritableTokenRelationStore tokenRelStore,
                                          final HandleContext handleContext) {
        final var account = getIfUsable(accountId, accountStore, handleContext.expiryValidator(), INVALID_ACCOUNT_ID);
        final var tokenRel = tokenRelStore.get(accountId, tokenId);

        if(tokenRel == null && account.maxAutoAssociations() > 0){
            autoAssociate(account, token, accountStore, tokenRelStore, handleContext);
        } else {
            validateTrue(tokenRel != null, TOKEN_NOT_ASSOCIATED_TO_ACCOUNT);
        }
    }
}
