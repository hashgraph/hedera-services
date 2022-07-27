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

import static com.hedera.services.txns.crypto.helpers.AllowanceHelpers.aggregateNftAllowances;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.AMOUNT_EXCEEDS_TOKEN_MAX_SUPPLY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.DELEGATING_SPENDER_CANNOT_GRANT_APPROVE_FOR_ALL;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.DELEGATING_SPENDER_DOES_NOT_HAVE_APPROVE_FOR_ALL;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FUNGIBLE_TOKEN_IN_NFT_ALLOWANCES;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NEGATIVE_ALLOWANCE_AMOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NFT_IN_FUNGIBLE_TOKEN_ALLOWANCES;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NOT_SUPPORTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SPENDER_ACCOUNT_SAME_AS_OWNER;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_NOT_ASSOCIATED_TO_ACCOUNT;

import com.hedera.services.context.primitives.StateView;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.state.enums.TokenSupplyType;
import com.hedera.services.state.submerkle.FcTokenAllowanceId;
import com.hedera.services.store.AccountStore;
import com.hedera.services.store.ReadOnlyTokenStore;
import com.hedera.services.store.models.Account;
import com.hedera.services.store.models.Id;
import com.hedera.services.store.models.Token;
import com.hedera.services.txns.validation.OptionValidator;
import com.hedera.services.utils.EntityNum;
import com.hederahashgraph.api.proto.java.CryptoAllowance;
import com.hederahashgraph.api.proto.java.NftAllowance;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenAllowance;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class ApproveAllowanceChecks extends AllowanceChecks {
    private final OptionValidator validator;

    @Inject
    public ApproveAllowanceChecks(
            final GlobalDynamicProperties dynamicProperties, final OptionValidator validator) {
        super(dynamicProperties);
        this.validator = validator;
    }

    /**
     * Validate all allowances in {@link com.hederahashgraph.api.proto.java.CryptoApproveAllowance}
     * transactions
     *
     * @param cryptoAllowances crypto allowances list
     * @param tokenAllowances fungible token allowances list
     * @param nftAllowances nft allowances list
     * @param payerAccount Account of the payer for the allowance approve/adjust txn.
     * @param view working view
     * @return response code after validation
     */
    public ResponseCodeEnum allowancesValidation(
            final List<CryptoAllowance> cryptoAllowances,
            final List<TokenAllowance> tokenAllowances,
            final List<NftAllowance> nftAllowances,
            final Account payerAccount,
            final StateView view) {
        // feature flag for allowances
        if (!isEnabled()) {
            return NOT_SUPPORTED;
        }

        var validity = validateAllowanceCount(cryptoAllowances, tokenAllowances, nftAllowances);
        if (validity != OK) {
            return validity;
        }

        final var accountStore = new AccountStore(validator, view.asReadOnlyAccountStore());
        validity = validateCryptoAllowances(cryptoAllowances, payerAccount, accountStore);
        if (validity != OK) {
            return validity;
        }

        final var tokenStore =
                new ReadOnlyTokenStore(
                        accountStore,
                        view.asReadOnlyTokenStore(),
                        view.asReadOnlyNftStore(),
                        view.asReadOnlyAssociationStore());
        validity =
                validateFungibleTokenAllowances(
                        tokenAllowances, payerAccount, accountStore, tokenStore);
        if (validity != OK) {
            return validity;
        }

        validity = validateNftAllowances(nftAllowances, payerAccount, accountStore, tokenStore);
        if (validity != OK) {
            return validity;
        }

        return OK;
    }

    /**
     * Validates the CryptoAllowances given in {@link
     * com.hederahashgraph.api.proto.java.CryptoApproveAllowance} transaction
     *
     * @param cryptoAllowances crypto allowances list
     * @param payerAccount Account of the payer for the approveAllowance txn
     * @param accountStore account store
     * @return response code after validation
     */
    ResponseCodeEnum validateCryptoAllowances(
            final List<CryptoAllowance> cryptoAllowances,
            final Account payerAccount,
            final AccountStore accountStore) {
        if (cryptoAllowances.isEmpty()) {
            return OK;
        }

        for (final var allowance : cryptoAllowances) {
            final var owner = Id.fromGrpcAccount(allowance.getOwner());
            final var spender = Id.fromGrpcAccount(allowance.getSpender());

            final var fetchResult = fetchOwnerAccount(owner, payerAccount, accountStore);
            if (fetchResult.getRight() != OK) {
                return fetchResult.getRight();
            }
            final var ownerAccount = fetchResult.getLeft();

            var validity = validateAmount(allowance.getAmount());
            if (validity != OK) {
                return validity;
            }
            validity = validateSpender(ownerAccount.getId(), spender);
            if (validity != OK) {
                return validity;
            }
        }
        return OK;
    }

    /**
     * Validate fungible token allowances list {@link
     * com.hederahashgraph.api.proto.java.CryptoApproveAllowance} transaction
     *
     * @param tokenAllowances token allowances list
     * @param payerAccount Account of the payer for the approveAllowance txn
     * @param accountStore account store
     * @param tokenStore read only token store
     * @return
     */
    ResponseCodeEnum validateFungibleTokenAllowances(
            final List<TokenAllowance> tokenAllowances,
            final Account payerAccount,
            final AccountStore accountStore,
            final ReadOnlyTokenStore tokenStore) {
        if (tokenAllowances.isEmpty()) {
            return OK;
        }

        for (final var allowance : tokenAllowances) {
            final var owner = Id.fromGrpcAccount(allowance.getOwner());
            final var spender = Id.fromGrpcAccount(allowance.getSpender());
            final var token =
                    tokenStore.loadPossiblyPausedToken(Id.fromGrpcToken(allowance.getTokenId()));

            final var fetchResult = fetchOwnerAccount(owner, payerAccount, accountStore);
            if (fetchResult.getRight() != OK) {
                return fetchResult.getRight();
            }
            final var ownerAccount = fetchResult.getLeft();
            if (!token.isFungibleCommon()) {
                return NFT_IN_FUNGIBLE_TOKEN_ALLOWANCES;
            }

            var validity = validateTokenAmount(allowance.getAmount(), token);
            if (validity != OK) {
                return validity;
            }

            validity = validateTokenBasics(ownerAccount, spender, token, tokenStore);
            if (validity != OK) {
                return validity;
            }
        }
        return OK;
    }

    /**
     * Validate nft allowances list {@link
     * com.hederahashgraph.api.proto.java.CryptoApproveAllowance} transaction
     *
     * @param nftAllowancesList nft allowances
     * @param payerAccount payer for approveAllowance txn
     * @param accountStore account store
     * @param tokenStore token store
     * @return
     */
    ResponseCodeEnum validateNftAllowances(
            final List<NftAllowance> nftAllowancesList,
            final Account payerAccount,
            final AccountStore accountStore,
            final ReadOnlyTokenStore tokenStore) {
        if (nftAllowancesList.isEmpty()) {
            return OK;
        }

        for (final var allowance : nftAllowancesList) {
            final var owner = Id.fromGrpcAccount(allowance.getOwner());
            final var spender = Id.fromGrpcAccount(allowance.getSpender());
            final var delegatingSpender = Id.fromGrpcAccount(allowance.getDelegatingSpender());
            final var tokenId = allowance.getTokenId();
            final var serialNums = allowance.getSerialNumbersList();
            final var token = tokenStore.loadPossiblyPausedToken(Id.fromGrpcToken(tokenId));
            final var approvedForAll = allowance.getApprovedForAll().getValue();

            if (token.isFungibleCommon()) {
                return FUNGIBLE_TOKEN_IN_NFT_ALLOWANCES;
            }

            final var fetchResult = fetchOwnerAccount(owner, payerAccount, accountStore);
            if (fetchResult.getRight() != OK) {
                return fetchResult.getRight();
            }
            final var ownerAccount = fetchResult.getLeft();

            var validity = validateTokenBasics(ownerAccount, spender, token, tokenStore);
            if (validity != OK) {
                return validity;
            }

            if (approvedForAll && !delegatingSpender.equals(Id.MISSING_ID)) {
                return DELEGATING_SPENDER_CANNOT_GRANT_APPROVE_FOR_ALL;
            } else if (!delegatingSpender.equals(Id.MISSING_ID)) {
                final var allowanceKey =
                        FcTokenAllowanceId.from(
                                EntityNum.fromTokenId(tokenId), delegatingSpender.asEntityNum());
                if (!ownerAccount.getApprovedForAllNftsAllowances().contains(allowanceKey)) {
                    return DELEGATING_SPENDER_DOES_NOT_HAVE_APPROVE_FOR_ALL;
                }
            }

            validity = validateSerialNums(serialNums, token, tokenStore);
            if (validity != OK) {
                return validity;
            }
        }
        return OK;
    }

    public ResponseCodeEnum validateTokenAmount(final long amount, final Token token) {
        if (amount < 0) {
            return NEGATIVE_ALLOWANCE_AMOUNT;
        }

        if (token.getSupplyType().equals(TokenSupplyType.FINITE) && amount > token.getMaxSupply()) {
            return AMOUNT_EXCEEDS_TOKEN_MAX_SUPPLY;
        }
        return OK;
    }

    public ResponseCodeEnum validateAmount(final long amount) {
        if (amount < 0) {
            return NEGATIVE_ALLOWANCE_AMOUNT;
        }
        return OK;
    }

    ResponseCodeEnum validateAllowanceCount(
            final List<CryptoAllowance> cryptoAllowances,
            final List<TokenAllowance> tokenAllowances,
            final List<NftAllowance> nftAllowances) {
        // each serial number of an NFT is considered as an allowance.
        // So for Nft allowances aggregated amount is considered for limit calculation.
        final var totalAllowances =
                cryptoAllowances.size()
                        + tokenAllowances.size()
                        + aggregateNftAllowances(nftAllowances);
        return validateTotalAllowances(totalAllowances);
    }

    private ResponseCodeEnum validateTokenBasics(
            final Account ownerAccount,
            final Id spenderId,
            final Token token,
            final ReadOnlyTokenStore tokenStore) {
        if (ownerAccount.getId().equals(spenderId)) {
            return SPENDER_ACCOUNT_SAME_AS_OWNER;
        }
        if (!tokenStore.hasAssociation(token, ownerAccount)) {
            return TOKEN_NOT_ASSOCIATED_TO_ACCOUNT;
        }
        return OK;
    }

    private ResponseCodeEnum validateSpender(final Id ownerId, final Id spender) {
        if (ownerId.equals(spender)) {
            return SPENDER_ACCOUNT_SAME_AS_OWNER;
        }
        return OK;
    }
}
