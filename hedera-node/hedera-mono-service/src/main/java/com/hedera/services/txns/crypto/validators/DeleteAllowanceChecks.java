/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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
package com.hedera.services.txns.crypto.validators;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.EMPTY_ALLOWANCES;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FUNGIBLE_TOKEN_IN_NFT_ALLOWANCES;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NOT_SUPPORTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_NOT_ASSOCIATED_TO_ACCOUNT;

import com.hedera.services.context.primitives.StateView;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.exceptions.InvalidTransactionException;
import com.hedera.services.store.AccountStore;
import com.hedera.services.store.ReadOnlyTokenStore;
import com.hedera.services.store.models.Account;
import com.hedera.services.store.models.Id;
import com.hedera.services.store.models.Token;
import com.hedera.services.txns.validation.OptionValidator;
import com.hederahashgraph.api.proto.java.NftRemoveAllowance;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Semantic check validation for {@link com.hederahashgraph.api.proto.java.CryptoDeleteAllowance}
 * transaction
 */
@Singleton
public class DeleteAllowanceChecks extends AllowanceChecks {
    private final OptionValidator validator;

    @Inject
    public DeleteAllowanceChecks(
            final GlobalDynamicProperties dynamicProperties, final OptionValidator validator) {
        super(dynamicProperties);
        this.validator = validator;
    }

    /**
     * Validates all allowances provided in {@link
     * com.hederahashgraph.api.proto.java.CryptoDeleteAllowanceTransactionBody}
     *
     * @param nftAllowances given nft serials allowances to remove
     * @param payerAccount payer for the transaction
     * @param view working view
     * @return validation response
     */
    public ResponseCodeEnum deleteAllowancesValidation(
            final List<NftRemoveAllowance> nftAllowances,
            final Account payerAccount,
            final StateView view) {
        // feature flag for allowances
        if (!isEnabled()) {
            return NOT_SUPPORTED;
        }

        var validity = validateAllowancesCount(nftAllowances);
        if (validity != OK) {
            return validity;
        }
        final var accountStore = new AccountStore(validator, view.asReadOnlyAccountStore());
        final var tokenStore =
                new ReadOnlyTokenStore(
                        accountStore,
                        view.asReadOnlyTokenStore(),
                        view.asReadOnlyNftStore(),
                        view.asReadOnlyAssociationStore());
        return validateNftDeleteAllowances(nftAllowances, payerAccount, accountStore, tokenStore);
    }

    /**
     * Validates all the {@link NftRemoveAllowance}s in the {@link
     * com.hederahashgraph.api.proto.java.CryptoDeleteAllowance} transaction
     *
     * @param nftAllowances nft remove allowances
     * @param payerAccount payer for the txn
     * @param accountStore account store
     * @param tokenStore read only token store
     * @return
     */
    public ResponseCodeEnum validateNftDeleteAllowances(
            final List<NftRemoveAllowance> nftAllowances,
            final Account payerAccount,
            final AccountStore accountStore,
            final ReadOnlyTokenStore tokenStore) {
        if (nftAllowances.isEmpty()) {
            return OK;
        }
        for (final var allowance : nftAllowances) {
            final var owner = Id.fromGrpcAccount(allowance.getOwner());
            final var serialNums = allowance.getSerialNumbersList();
            final Token token;
            try {
                token =
                        tokenStore.loadPossiblyPausedToken(
                                Id.fromGrpcToken(allowance.getTokenId()));
            } catch (InvalidTransactionException e) {
                return e.getResponseCode();
            }
            if (token.isFungibleCommon()) {
                return FUNGIBLE_TOKEN_IN_NFT_ALLOWANCES;
            }
            final var fetchResult = fetchOwnerAccount(owner, payerAccount, accountStore);
            if (fetchResult.getRight() != OK) {
                return fetchResult.getRight();
            }
            final var ownerAccount = fetchResult.getLeft();
            if (!tokenStore.hasAssociation(token, ownerAccount)) {
                return TOKEN_NOT_ASSOCIATED_TO_ACCOUNT;
            }
            final var validity = validateDeleteSerialNums(serialNums, token, tokenStore);
            if (validity != OK) {
                return validity;
            }
        }
        return OK;
    }

    ResponseCodeEnum validateDeleteSerialNums(
            final List<Long> serialNums, final Token token, final ReadOnlyTokenStore tokenStore) {
        if (serialNums.isEmpty()) {
            return EMPTY_ALLOWANCES;
        }
        return validateSerialNums(serialNums, token, tokenStore);
    }

    ResponseCodeEnum validateAllowancesCount(final List<NftRemoveAllowance> nftAllowances) {
        // each serial number of an NFT is considered as an allowance.
        // So for Nft allowances aggregated amount is considered for transaction limit calculation.
        // Number of serials will not be counted for allowance on account.
        return validateTotalAllowances(aggregateNftDeleteAllowances(nftAllowances));
    }

    /**
     * Gets sum of number of serials in the nft allowances. Considers duplicate serial numbers as
     * well.
     *
     * @param nftAllowances give nft allowances
     * @return number of serials
     */
    int aggregateNftDeleteAllowances(List<NftRemoveAllowance> nftAllowances) {
        int count = 0;
        for (var allowance : nftAllowances) {
            count += allowance.getSerialNumbersCount();
        }
        return count;
    }
}
