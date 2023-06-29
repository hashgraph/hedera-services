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
import static com.hedera.node.app.service.token.impl.util.TokenHandlerHelper.getIfUsable;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_AMOUNT_TRANSFERS_ONLY_ALLOWED_FOR_FUNGIBLE_COMMON;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.UNEXPECTED_TOKEN_DECIMALS;
import static java.util.Collections.emptyList;

import com.hedera.hapi.node.base.TokenType;
import com.hedera.hapi.node.token.CryptoTransferTransactionBody;
import com.hedera.node.app.service.mono.utils.EntityNumPair;
import com.hedera.node.app.service.token.impl.WritableAccountStore;
import com.hedera.node.app.service.token.impl.WritableTokenRelationStore;
import com.hedera.node.app.service.token.impl.WritableTokenStore;
import com.hedera.node.app.service.token.impl.handlers.BaseTokenHandler;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class ZeroSumFungibleTransfersStep extends BaseTokenHandler implements TransferStep {
    final CryptoTransferTransactionBody op;

    public ZeroSumFungibleTransfersStep(final CryptoTransferTransactionBody op) {
        this.op = op;
    }

    @Override
    public void doIn(final TransferContext transferContext) {
        final var handleContext = transferContext.getHandleContext();
        final var tokenStore = handleContext.writableStore(WritableTokenStore.class);
        final var tokenRelStore = handleContext.writableStore(WritableTokenRelationStore.class);
        final var accountStore = handleContext.writableStore(WritableAccountStore.class);
        final Map<EntityNumPair, Long> aggregatedFungibleTokenChanges = new HashMap<>();
        final Map<EntityNumPair, Long> allowanceTransfers = new HashMap<>();
        // Look at all fungible token transfers and put into agrragatedFungibleTokenChanges map.
        // Also, put any transfers happening with allowances in allowanceTransfers map.
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
                // If the transfer is happening with an allowance, add it to the allowanceTransfers map.
                if (aa.isApproval() && aa.amount() < 0) {
                    if (!allowanceTransfers.containsKey(pair)) {
                        allowanceTransfers.put(pair, amount);
                    } else {
                        var existingChange = allowanceTransfers.get(pair);
                        allowanceTransfers.put(pair, existingChange + amount);
                    }
                }
            }
        }

        // Look at all the aggregatedFungibleTokenChanges and adjust the balances in the tokenRelStore.
        for (final var atPair : aggregatedFungibleTokenChanges.keySet()) {
            final var rel = getIfUsable(
                    asAccount(atPair.getHiOrderAsLong()), asToken(atPair.getLowOrderAsLong()), tokenRelStore);
            final var account = accountStore.get(asAccount(atPair.getHiOrderAsLong()));
            final var amount = aggregatedFungibleTokenChanges.get(atPair);
            adjustBalance(rel, account, amount, tokenRelStore, accountStore);
        }

        // Look at all the allowanceTransfers and adjust the allowances in the accountStore.
        for (final var atPair : allowanceTransfers.keySet()) {
            final var accountId = asAccount(atPair.getHiOrderAsLong());
            final var tokenId = asToken(atPair.getLowOrderAsLong());

            final var account = getIfUsable(
                    accountId, accountStore, transferContext.getHandleContext().expiryValidator(), INVALID_ACCOUNT_ID);
            final var accountCopy = account.copyBuilder();

            final var tokenAllowances = account.tokenAllowancesOrElse(Collections.emptyList());
            for (int i = 0; i < tokenAllowances.size(); i++) {
                final var allowance = tokenAllowances.get(i);
                final var allowanceCopy = allowance.copyBuilder();
                if (allowance.spenderNum() == accountId.accountNum() && allowance.tokenNum() == tokenId.tokenNum()) {
                    final var newAllowance = allowance.amount() + allowanceTransfers.get(account);
                    allowanceCopy.amount(newAllowance);
                    if (newAllowance != 0) {
                        tokenAllowances.set(i, allowanceCopy.build());
                    } else {
                        tokenAllowances.remove(i);
                    }
                }
            }
            accountCopy.tokenAllowances(tokenAllowances);
            accountStore.put(accountCopy.build());
        }
    }
}
