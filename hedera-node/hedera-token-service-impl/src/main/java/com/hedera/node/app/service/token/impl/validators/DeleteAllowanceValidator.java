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

package com.hedera.node.app.service.token.impl.validators;

import static com.hedera.hapi.node.base.ResponseCodeEnum.*;
import static com.hedera.node.app.service.token.impl.handlers.TokenHandlerHelper.getIfUsable;
import static com.hedera.node.app.spi.workflows.HandleException.validateFalse;
import static com.hedera.node.app.spi.workflows.HandleException.validateTrue;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.base.TokenType;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.hapi.node.state.token.Token;
import com.hedera.hapi.node.token.CryptoDeleteAllowanceTransactionBody;
import com.hedera.hapi.node.token.NftRemoveAllowance;
import com.hedera.node.app.service.token.ReadableAccountStore;
import com.hedera.node.app.service.token.ReadableNftStore;
import com.hedera.node.app.service.token.ReadableTokenRelationStore;
import com.hedera.node.app.service.token.ReadableTokenStore;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.config.ConfigProvider;
import java.util.HashSet;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class DeleteAllowanceValidator extends AllowanceValidator {
    @Inject
    public DeleteAllowanceValidator(final ConfigProvider configProvider) {
        super(configProvider);
    }

    /**
     * Validates all allowances provided in {@link CryptoDeleteAllowanceTransactionBody}
     *
     * @param nftAllowances given nft serials allowances to remove
     * @param payerAccount payer for the transaction
     * @param accountStore account store
     */
    public void validate(
            final HandleContext handleContext,
            final List<NftRemoveAllowance> nftAllowances,
            final Account payerAccount,
            final ReadableAccountStore accountStore) {
        final var tokenStore = handleContext.readableStore(ReadableTokenStore.class);
        final var tokenRelStore = handleContext.readableStore(ReadableTokenRelationStore.class);
        final var nftStore = handleContext.readableStore(ReadableNftStore.class);

        // feature flag for allowances. Will probably be moved to some other place in app in the future.
        validateTrue(isEnabled(), NOT_SUPPORTED);

        validateAllowancesCount(nftAllowances);

        validateNftDeleteAllowances(nftAllowances, payerAccount, accountStore, tokenStore, tokenRelStore, nftStore);
    }

    /**
     * Validates all the {@link NftRemoveAllowance}s in the {@link
     * com.hederahashgraph.api.proto.java.CryptoDeleteAllowance} transaction
     *
     * @param nftAllowances nft remove allowances
     * @param payerAccount payer for the txn
     * @param accountStore account store
     * @param tokenStore read only token store
     */
    private void validateNftDeleteAllowances(
            final List<NftRemoveAllowance> nftAllowances,
            final Account payerAccount,
            final ReadableAccountStore accountStore,
            final ReadableTokenStore tokenStore,
            final ReadableTokenRelationStore tokenRelStore,
            final ReadableNftStore nftStore) {
        if (nftAllowances.isEmpty()) {
            return;
        }
        for (final var allowance : nftAllowances) {
            final var ownerId = allowance.ownerOrElse(AccountID.DEFAULT);
            final var tokenId = allowance.tokenIdOrElse(TokenID.DEFAULT);
            final var serialNums = allowance.serialNumbers();

            final Token token = getIfUsable(allowance.tokenIdOrElse(TokenID.DEFAULT), tokenStore);
            validateFalse(token.tokenType().equals(TokenType.FUNGIBLE_COMMON), FUNGIBLE_TOKEN_IN_NFT_ALLOWANCES);

            final var effectiveOwner = getEffectiveOwner(ownerId, payerAccount, accountStore);

            final var relation = tokenRelStore.get(
                    AccountID.newBuilder()
                            .accountNum(effectiveOwner.accountNumber())
                            .build(),
                    TokenID.newBuilder().tokenNum(token.tokenNumber()).build());
            validateTrue(relation.isPresent(), TOKEN_NOT_ASSOCIATED_TO_ACCOUNT);

            validateDeleteSerialNums(serialNums, tokenId, nftStore);
        }
    }

    private void validateDeleteSerialNums(
            final List<Long> serialNums, final TokenID tokenId, final ReadableNftStore nftStore) {
        validateFalse(serialNums.isEmpty(), EMPTY_ALLOWANCES);
        validateSerialNums(serialNums, tokenId, nftStore);
    }

    private void validateAllowancesCount(final List<NftRemoveAllowance> nftAllowances) {
        // each serial number of an NFT is considered as an allowance.
        // So for Nft allowances aggregated amount is considered for transaction limit calculation.
        // Number of serials will not be counted for allowance on account.
        validateTotalAllowancesPerTxn(aggregateNftDeleteAllowances(nftAllowances));
    }

    /**
     * Gets sum of number of serials in the nft allowances. Considers duplicate serial numbers as
     * well.
     *
     * @param nftAllowances give nft allowances
     * @return number of serials
     */
    public static int aggregateNftDeleteAllowances(final List<NftRemoveAllowance> nftAllowances) {
        var count = 0;
        final var serialsSet = new HashSet<Long>();
        for (final var allowance : nftAllowances) {
            serialsSet.addAll(allowance.serialNumbers());
            count += serialsSet.size();
            serialsSet.clear();
        }
        return count;
    }
}
