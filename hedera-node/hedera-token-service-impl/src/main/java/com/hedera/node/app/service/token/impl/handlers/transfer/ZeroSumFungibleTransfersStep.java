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
import static com.hedera.node.app.service.evm.utils.ValidationUtils.validateTrue;
import static com.hedera.node.app.service.token.impl.handlers.BaseCryptoHandler.asAccount;
import static com.hedera.node.app.service.token.impl.handlers.BaseTokenHandler.asToken;
import static com.hedera.node.app.service.token.impl.util.TokenHandlerHelper.getIfUsable;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_AMOUNT_TRANSFERS_ONLY_ALLOWED_FOR_FUNGIBLE_COMMON;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.UNEXPECTED_TOKEN_DECIMALS;
import static java.util.Collections.emptyList;

import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.TokenType;
import com.hedera.hapi.node.token.CryptoTransferTransactionBody;
import com.hedera.node.app.service.mono.utils.EntityNumPair;
import com.hedera.node.app.service.token.impl.WritableAccountStore;
import com.hedera.node.app.service.token.impl.WritableTokenRelationStore;
import com.hedera.node.app.service.token.impl.WritableTokenStore;
import com.hedera.node.app.service.token.impl.handlers.BaseTokenHandler;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public class ZeroSumFungibleTransfersStep extends BaseTokenHandler implements TransferStep {
    final CryptoTransferTransactionBody op;

    public ZeroSumFungibleTransfersStep(final CryptoTransferTransactionBody op) {
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
        final Map<EntityNumPair, Long> aggregatedFungibleTokenChanges = new LinkedHashMap<>();
        for (var xfers : op.tokenTransfers()) {
            final var tokenId = xfers.token();
            final var token = getIfUsable(tokenId, tokenStore);
            validateTrue(
                    token.tokenType().equals(TokenType.FUNGIBLE_COMMON),
                    ACCOUNT_AMOUNT_TRANSFERS_ONLY_ALLOWED_FOR_FUNGIBLE_COMMON);

            if (xfers.hasExpectedDecimals()) {
                validateTrue(token.decimals() == xfers.expectedDecimals().intValue(), UNEXPECTED_TOKEN_DECIMALS);
            }

            for (var aa : xfers.transfersOrElse(emptyList())) {
                final var accountId = aa.accountID();
                getIfUsable(accountId, accountStore, handleContext.expiryValidator(), INVALID_ACCOUNT_ID);

                final var amount = aa.amount();
                final var pair = EntityNumPair.fromLongs(accountId.accountNum(), tokenId.tokenNum());
                if (!aggregatedFungibleTokenChanges.containsKey(pair)) {
                    aggregatedFungibleTokenChanges.put(pair, amount);
                } else {
                    var existingChange = aggregatedFungibleTokenChanges.get(pair);
                    aggregatedFungibleTokenChanges.put(pair, existingChange + amount);
                }
            }
        }
        for (final var atPair : aggregatedFungibleTokenChanges.keySet()) {
            final var rel = getIfUsable(
                    asAccount(atPair.getHiOrderAsLong()), asToken(atPair.getLowOrderAsLong()), tokenRelStore);
            final var account = accountStore.get(asAccount(atPair.getHiOrderAsLong()));
            final var amount = aggregatedFungibleTokenChanges.get(atPair);
            adjustBalance(rel, account, amount, tokenRelStore, accountStore);
        }
    }
}
