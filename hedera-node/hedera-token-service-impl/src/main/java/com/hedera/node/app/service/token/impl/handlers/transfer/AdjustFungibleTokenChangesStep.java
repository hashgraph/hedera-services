/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

import static com.hedera.hapi.node.base.ResponseCodeEnum.ACCOUNT_AMOUNT_TRANSFERS_ONLY_ALLOWED_FOR_FUNGIBLE_COMMON;
import static com.hedera.hapi.node.base.ResponseCodeEnum.AMOUNT_EXCEEDS_ALLOWANCE;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.SPENDER_DOES_NOT_HAVE_ALLOWANCE;
import static com.hedera.hapi.node.base.ResponseCodeEnum.UNEXPECTED_TOKEN_DECIMALS;
import static com.hedera.node.app.service.token.impl.util.TokenHandlerHelper.getIfUsable;
import static com.hedera.node.app.spi.workflows.HandleException.validateTrue;
import static java.util.Collections.emptyList;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.TokenType;
import com.hedera.hapi.node.state.common.EntityIDPair;
import com.hedera.hapi.node.token.CryptoTransferTransactionBody;
import com.hedera.node.app.service.token.impl.WritableAccountStore;
import com.hedera.node.app.service.token.impl.WritableTokenRelationStore;
import com.hedera.node.app.service.token.impl.WritableTokenStore;
import com.hedera.node.app.service.token.impl.handlers.BaseTokenHandler;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Puts all fungible token changes from CryptoTransfer into state's modifications map.
 */
public class AdjustFungibleTokenChangesStep extends BaseTokenHandler implements TransferStep {
    // The CryptoTransferTransactionBody here is obtained by replacing aliases with their
    // corresponding accountIds.
    private final CryptoTransferTransactionBody op;
    private final AccountID topLevelPayer;

    public AdjustFungibleTokenChangesStep(
            @NonNull final CryptoTransferTransactionBody op, @NonNull final AccountID topLevelPayer) {
        requireNonNull(op);
        requireNonNull(topLevelPayer);

        this.op = op;
        this.topLevelPayer = topLevelPayer;
    }

    @Override
    public void doIn(@NonNull final TransferContext transferContext) {
        requireNonNull(transferContext);

        final var handleContext = transferContext.getHandleContext();
        final var tokenStore = handleContext.writableStore(WritableTokenStore.class);
        final var tokenRelStore = handleContext.writableStore(WritableTokenRelationStore.class);
        final var accountStore = handleContext.writableStore(WritableAccountStore.class);

        // two maps for aggregating the changes to the token balances and allowances.
        final Map<EntityIDPair, Long> aggregatedFungibleTokenChanges = new LinkedHashMap<>();
        final Map<EntityIDPair, Long> allowanceTransfers = new LinkedHashMap<>();

        // Look at all fungible token transfers and put into aggregatedFungibleTokenChanges map.
        // Also, put any transfers happening with allowances in allowanceTransfers map.
        for (final var transfers : op.tokenTransfersOrElse(emptyList())) {
            final var tokenId = transfers.tokenOrThrow();
            final var token = getIfUsable(tokenId, tokenStore);

            if (transfers.hasExpectedDecimals()) {
                validateTrue(token.decimals() == transfers.expectedDecimalsOrThrow(), UNEXPECTED_TOKEN_DECIMALS);
            }

            for (final var aa : transfers.transfersOrElse(emptyList())) {
                validateTrue(
                        token.tokenType() == TokenType.FUNGIBLE_COMMON,
                        ACCOUNT_AMOUNT_TRANSFERS_ONLY_ALLOWED_FOR_FUNGIBLE_COMMON);

                final var accountId = aa.accountIDOrThrow();
                getIfUsable(accountId, accountStore, handleContext.expiryValidator(), INVALID_ACCOUNT_ID);
                final var pair = new EntityIDPair(accountId, tokenId);

                // Validate freeze status and kyc granted
                final var accountID = aa.accountIDOrThrow();
                final var tokenRel = getIfUsable(accountID, tokenId, tokenRelStore);
                validateNotFrozenAndKycOnRelation(tokenRel);

                // Add the amount to the aggregatedFungibleTokenChanges map.
                // If the (accountId, tokenId) pair doesn't exist in the map, add it.
                // Else, update the aggregated transfer amount
                aggregatedFungibleTokenChanges.merge(pair, aa.amount(), Long::sum);

                // If the transfer is happening with an allowance,
                // add it to the allowanceTransfers map.
                // If the accountId tokenId pair doesn't exist in the map, add it.
                // Else, update the aggregated transfer amount
                if (aa.isApproval() && aa.amount() < 0) {
                    allowanceTransfers.merge(pair, aa.amount(), Long::sum);
                }
            }
        }

        modifyAggregatedAllowances(allowanceTransfers, accountStore, transferContext);
        modifyAggregatedTokenBalances(aggregatedFungibleTokenChanges, tokenRelStore, accountStore);
    }

    /**
     * Puts all the aggregated token allowances changes into the accountStore.
     * @param allowanceTransfers - map of aggregated token allowances to be modified
     * @param accountStore  - account store
     * @param transferContext - transfer context
     */
    private void modifyAggregatedAllowances(
            @NonNull final Map<EntityIDPair, Long> allowanceTransfers,
            @NonNull final WritableAccountStore accountStore,
            @NonNull final TransferContext transferContext) {
        // Look at all the allowanceTransfers and adjust the allowances in the accountStore.
        for (final var entry : allowanceTransfers.entrySet()) {
            final var atPair = entry.getKey();
            final var amount = entry.getValue();
            final var accountId = atPair.accountIdOrThrow();
            final var tokenId = atPair.tokenIdOrThrow();

            final var account = getIfUsable(
                    accountId, accountStore, transferContext.getHandleContext().expiryValidator(), INVALID_ACCOUNT_ID);
            final var accountCopy = account.copyBuilder();

            final var tokenAllowances = new ArrayList<>(account.tokenAllowancesOrElse(Collections.emptyList()));
            var haveExistingAllowance = false;
            for (int i = 0; i < tokenAllowances.size(); i++) {
                final var allowance = tokenAllowances.get(i);
                final var allowanceCopy = allowance.copyBuilder();
                // If isApproval flag is set then the spender account must have paid for the transaction.
                // The transfer list specifies the owner who granted allowance as sender
                // check if the allowances from the sender account has the payer account as spender
                if (topLevelPayer.equals(allowance.spenderId()) && tokenId.equals(allowance.tokenId())) {
                    haveExistingAllowance = true;
                    final var newAllowanceAmount = allowance.amount() + amount;
                    validateTrue(newAllowanceAmount >= 0, AMOUNT_EXCEEDS_ALLOWANCE);
                    allowanceCopy.amount(newAllowanceAmount);
                    if (newAllowanceAmount != 0) {
                        tokenAllowances.set(i, allowanceCopy.build());
                    } else {
                        tokenAllowances.remove(i);
                    }
                    break;
                }
            }
            validateTrue(haveExistingAllowance, SPENDER_DOES_NOT_HAVE_ALLOWANCE);
            accountCopy.tokenAllowances(tokenAllowances);
            accountStore.put(accountCopy.build());
        }
    }

    /**
     * Puts all the aggregated token balances changes into the tokenRelStore and accountStore.
     * @param aggregatedFungibleTokenChanges - map of aggregated token balances to be modified
     * @param tokenRelStore - token relation store
     * @param accountStore - account store
     */
    private void modifyAggregatedTokenBalances(
            @NonNull final Map<EntityIDPair, Long> aggregatedFungibleTokenChanges,
            @NonNull final WritableTokenRelationStore tokenRelStore,
            @NonNull final WritableAccountStore accountStore) {
        // Look at all the aggregatedFungibleTokenChanges and adjust the balances in the tokenRelStore.
        for (final var entry : aggregatedFungibleTokenChanges.entrySet()) {
            final var atPair = entry.getKey();
            final var amount = entry.getValue();
            final var rel = getIfUsable(atPair.accountIdOrThrow(), atPair.tokenIdOrThrow(), tokenRelStore);
            final var account = requireNonNull(accountStore.get(atPair.accountIdOrThrow()));
            adjustBalance(rel, account, amount, tokenRelStore, accountStore);
        }
    }
}
