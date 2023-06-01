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

import static com.hedera.hapi.node.base.ResponseCodeEnum.AMOUNT_EXCEEDS_TOKEN_MAX_SUPPLY;
import static com.hedera.hapi.node.base.ResponseCodeEnum.DELEGATING_SPENDER_CANNOT_GRANT_APPROVE_FOR_ALL;
import static com.hedera.hapi.node.base.ResponseCodeEnum.DELEGATING_SPENDER_DOES_NOT_HAVE_APPROVE_FOR_ALL;
import static com.hedera.hapi.node.base.ResponseCodeEnum.FUNGIBLE_TOKEN_IN_NFT_ALLOWANCES;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_ALLOWANCE_SPENDER_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.NEGATIVE_ALLOWANCE_AMOUNT;
import static com.hedera.hapi.node.base.ResponseCodeEnum.NFT_IN_FUNGIBLE_TOKEN_ALLOWANCES;
import static com.hedera.hapi.node.base.ResponseCodeEnum.NOT_SUPPORTED;
import static com.hedera.hapi.node.base.ResponseCodeEnum.SPENDER_ACCOUNT_SAME_AS_OWNER;
import static com.hedera.hapi.node.base.ResponseCodeEnum.TOKEN_NOT_ASSOCIATED_TO_ACCOUNT;
import static com.hedera.node.app.service.token.impl.handlers.ContextualRetriever.isFungibleCommon;
import static com.hedera.node.app.service.token.impl.helpers.AllowanceHelpers.aggregateNftAllowances;
import static com.hedera.node.app.service.token.impl.helpers.AllowanceHelpers.getEffectiveOwner;
import static com.hedera.node.app.spi.workflows.HandleException.validateFalse;
import static com.hedera.node.app.spi.workflows.HandleException.validateTrue;
import static java.util.Collections.emptyList;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.base.TokenSupplyType;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.hapi.node.state.token.AccountApprovalForAllAllowance;
import com.hedera.hapi.node.state.token.Token;
import com.hedera.hapi.node.token.CryptoAllowance;
import com.hedera.hapi.node.token.CryptoApproveAllowanceTransactionBody;
import com.hedera.hapi.node.token.NftAllowance;
import com.hedera.hapi.node.token.TokenAllowance;
import com.hedera.node.app.service.token.ReadableAccountStore;
import com.hedera.node.app.service.token.ReadableTokenRelationStore;
import com.hedera.node.app.service.token.ReadableTokenStore;
import com.hedera.node.app.service.token.ReadableUniqueTokenStore;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.config.ConfigProvider;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class ApproveAllowanceValidator extends BaseAllowanceValidator {

    @Inject
    public ApproveAllowanceValidator(ConfigProvider configProvider) {
        super(configProvider);
    }

    public void validate(
            @NonNull final HandleContext context, Account payerAccount, ReadableAccountStore accountStore) {
        // create stores and config from context
        final var tokenStore = context.readableStore(ReadableTokenStore.class);
        final var tokenRelStore = context.readableStore(ReadableTokenRelationStore.class);
        final var nftStore = context.readableStore(ReadableUniqueTokenStore.class);

        final var txn = context.body();
        final var op = txn.cryptoApproveAllowanceOrThrow();

        final var cryptoAllowances = op.cryptoAllowancesOrElse(emptyList());
        final var tokenAllowances = op.tokenAllowancesOrElse(emptyList());
        final var nftAllowances = op.nftAllowancesOrElse(emptyList());

        // feature flag for allowances. Will probably be moved to some other place in app in the future.
        validateTrue(isEnabled(), NOT_SUPPORTED);

        // validate total count of allowances
        validateAllowanceCount(cryptoAllowances, tokenAllowances, nftAllowances);

        validateCryptoAllowances(cryptoAllowances, payerAccount, accountStore);
        validateFungibleTokenAllowances(tokenAllowances, payerAccount, accountStore, tokenStore, tokenRelStore);
        validateNftAllowances(nftAllowances, payerAccount, accountStore, tokenStore, tokenRelStore, nftStore);
    }

    /**
     * Validates the CryptoAllowances given in {@link CryptoApproveAllowanceTransactionBody}
     * @param cryptoAllowances crypto allowances list
     * @param payerAccount payer account for the approveAllowance txn
     * @param accountStore readable account store
     */
    void validateCryptoAllowances(
            @NonNull final List<CryptoAllowance> cryptoAllowances,
            @NonNull final Account payerAccount,
            @NonNull final ReadableAccountStore accountStore) {
        for (final var allowance : cryptoAllowances) {
            final var owner = allowance.owner();
            final var spender = allowance.spender();

            // check if owner specified in allowances exists.
            // If not set, owner will be treated as payer for the transaction
            final var effectiveOwner = getEffectiveOwner(owner, payerAccount, accountStore);
            // validate spender account
            final var spenderAccount = accountStore.getAccountById(spender);
            validateTrue(spenderAccount != null, INVALID_ALLOWANCE_SPENDER_ID);
            validateTrue(allowance.amount() >= 0, NEGATIVE_ALLOWANCE_AMOUNT);
            validateFalse(effectiveOwner.accountNumber() == spender.accountNum(), SPENDER_ACCOUNT_SAME_AS_OWNER);
        }
    }

    private void validateFungibleTokenAllowances(
            final List<TokenAllowance> tokenAllowances,
            final Account payerAccount,
            final ReadableAccountStore accountStore,
            final ReadableTokenStore tokenStore,
            final ReadableTokenRelationStore tokenRelStore) {
        for (final var allowance : tokenAllowances) {
            final var owner = allowance.owner();
            final var spender = allowance.spender();
            final var token = tokenStore.get(allowance.tokenId());
            // check if token exists
            validateTrue(token != null, INVALID_TOKEN_ID);

            // check if owner specified in allowances exists.
            // If not set, owner will be treated as payer for the transaction
            final var effectiveOwner = getEffectiveOwner(owner, payerAccount, accountStore);
            // validate spender account
            final var spenderAccount = accountStore.getAccountById(spender);
            validateTrue(spenderAccount != null, INVALID_ALLOWANCE_SPENDER_ID);
            validateTrue(isFungibleCommon(token), NFT_IN_FUNGIBLE_TOKEN_ALLOWANCES);

            // validate token amount
            final var amount = allowance.amount();
            validateTrue(amount >= 0, NEGATIVE_ALLOWANCE_AMOUNT);
            validateFalse(
                    token.supplyType().equals(TokenSupplyType.FINITE) && amount > token.maxSupply(),
                    AMOUNT_EXCEEDS_TOKEN_MAX_SUPPLY);
            // validate
            validateTokenBasics(effectiveOwner, spender, token, tokenRelStore);
        }
    }

    /**
     * Validate nft allowances list in {@link com.hedera.hapi.node.token.CryptoApproveAllowanceTransactionBody}
     *
     * @param nftAllowancesList nft allowances
     * @param payerAccount payer for approveAllowance txn
     * @param accountStore account store
     * @param tokenStore token store
     * @return response code
     */
    /**
     * Validate nft allowances list in {@link CryptoApproveAllowanceTransactionBody}
     * @param nftAllowancesList nft allowances
     * @param payerAccount payer for approveAllowance txn
     * @param accountStore account store
     * @param tokenStore token store
     */
    private void validateNftAllowances(
            final List<NftAllowance> nftAllowancesList,
            final Account payerAccount,
            final ReadableAccountStore accountStore,
            final ReadableTokenStore tokenStore,
            final ReadableTokenRelationStore tokenRelStore,
            final ReadableUniqueTokenStore uniqueTokenStore) {
        for (final var allowance : nftAllowancesList) {
            final var owner = allowance.owner();
            final var spender = allowance.spender();
            final var delegatingSpender = allowance.delegatingSpender();
            final var tokenId = allowance.tokenId();
            final var serialNums = allowance.serialNumbers();
            final var token = tokenStore.get(tokenId);
            final var approvedForAll = allowance.approvedForAll().booleanValue();

            validateFalse(isFungibleCommon(token), FUNGIBLE_TOKEN_IN_NFT_ALLOWANCES);

            final var effectiveOwner = getEffectiveOwner(owner, payerAccount, accountStore);
            validateTokenBasics(effectiveOwner, spender, token, tokenRelStore);

            if (delegatingSpender.accountNum() != 0) {
                validateTrue(!approvedForAll, DELEGATING_SPENDER_CANNOT_GRANT_APPROVE_FOR_ALL);
                final var approveForAllKey = AccountApprovalForAllAllowance.newBuilder()
                        .tokenNum(tokenId.tokenNum())
                        .spenderNum(spender.accountNum())
                        .build();
                validateTrue(
                        effectiveOwner.approveForAllNftAllowances().contains(approveForAllKey),
                        DELEGATING_SPENDER_DOES_NOT_HAVE_APPROVE_FOR_ALL);
            }

            validateSerialNums(serialNums, tokenId, uniqueTokenStore);
        }
    }

    private void validateAllowanceCount(
            final List<CryptoAllowance> cryptoAllowances,
            final List<TokenAllowance> tokenAllowances,
            final List<NftAllowance> nftAllowances) {
        // each serial number of an NFT is considered as an allowance.
        // So for Nft allowances aggregated amount is considered for limit calculation.
        final var totalAllowances =
                cryptoAllowances.size() + tokenAllowances.size() + aggregateNftAllowances(nftAllowances);
        validateTotalAllowances(totalAllowances);
    }

    private void validateTokenBasics(
            final Account owner,
            final AccountID spender,
            final Token token,
            final ReadableTokenRelationStore tokenRelStore) {
        final var accountId =
                AccountID.newBuilder().accountNum(owner.accountNumber()).build();
        final var tokenId = TokenID.newBuilder().tokenNum(token.tokenNumber()).build();
        // ONLY reject self-approval for NFT's; else allow to match OZ ERC-20
        validateFalse(!isFungibleCommon(token) && owner.equals(spender), SPENDER_ACCOUNT_SAME_AS_OWNER);
        final var relation = tokenRelStore.get(accountId, tokenId);
        validateTrue(relation != null, TOKEN_NOT_ASSOCIATED_TO_ACCOUNT);
    }
}
