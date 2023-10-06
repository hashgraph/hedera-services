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
import static com.hedera.node.app.spi.workflows.HandleException.validateFalse;
import static com.hedera.node.app.spi.workflows.HandleException.validateTrue;
import static java.util.Collections.emptyList;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.TokenSupplyType;
import com.hedera.hapi.node.base.TokenType;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.hapi.node.state.token.AccountApprovalForAllAllowance;
import com.hedera.hapi.node.state.token.Token;
import com.hedera.hapi.node.token.CryptoAllowance;
import com.hedera.hapi.node.token.CryptoApproveAllowanceTransactionBody;
import com.hedera.hapi.node.token.NftAllowance;
import com.hedera.hapi.node.token.TokenAllowance;
import com.hedera.node.app.service.token.ReadableAccountStore;
import com.hedera.node.app.service.token.ReadableNftStore;
import com.hedera.node.app.service.token.ReadableTokenRelationStore;
import com.hedera.node.app.service.token.ReadableTokenStore;
import com.hedera.node.app.spi.validation.ExpiryValidator;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.config.data.HederaConfig;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * This class validates the {@link CryptoApproveAllowanceTransactionBody} transaction
 */
@Singleton
public class ApproveAllowanceValidator extends AllowanceValidator {

    @Inject
    public ApproveAllowanceValidator() {
        // Dagger
    }

    public void validate(
            @NonNull final HandleContext context, final Account payerAccount, final ReadableAccountStore accountStore) {
        // create stores and config from context
        final var tokenStore = context.readableStore(ReadableTokenStore.class);
        final var tokenRelStore = context.readableStore(ReadableTokenRelationStore.class);
        final var nftStore = context.readableStore(ReadableNftStore.class);
        final var hederaConfig = context.configuration().getConfigData(HederaConfig.class);

        final var txn = context.body();
        final var op = txn.cryptoApproveAllowanceOrThrow();

        final var cryptoAllowances = op.cryptoAllowancesOrElse(emptyList());
        final var tokenAllowances = op.tokenAllowancesOrElse(emptyList());
        final var nftAllowances = op.nftAllowancesOrElse(emptyList());

        // feature flag for allowances. Will probably be moved to some other place in app in the future.
        validateTrue(hederaConfig.allowancesIsEnabled(), NOT_SUPPORTED);

        // validate total count of allowances
        validateAllowanceCount(cryptoAllowances, tokenAllowances, nftAllowances, hederaConfig);
        // validate all allowances
        final var expiryValidator = context.expiryValidator();
        validateCryptoAllowances(cryptoAllowances, payerAccount, accountStore, expiryValidator);
        validateFungibleTokenAllowances(
                tokenAllowances, payerAccount, accountStore, tokenStore, tokenRelStore, expiryValidator);
        validateNftAllowances(
                nftAllowances, payerAccount, accountStore, tokenStore, tokenRelStore, nftStore, expiryValidator);
    }

    /**
     * Validates the CryptoAllowances given in {@link CryptoApproveAllowanceTransactionBody}
     * @param cryptoAllowances crypto allowances list
     * @param payer payer account for the approveAllowance txn
     * @param accountStore readable account store
     */
    void validateCryptoAllowances(
            @NonNull final List<CryptoAllowance> cryptoAllowances,
            @NonNull final Account payer,
            @NonNull final ReadableAccountStore accountStore,
            @NonNull final ExpiryValidator expiryValidator) {
        for (final var allowance : cryptoAllowances) {
            final var owner = allowance.ownerOrElse(AccountID.DEFAULT);
            final var spender = allowance.spenderOrElse(AccountID.DEFAULT);

            // check if owner specified in allowances exists.
            // If not set, owner will be treated as payer for the transaction
            final var effectiveOwner = getEffectiveOwner(owner, payer, accountStore, expiryValidator);
            // validate spender account
            final var spenderAccount = accountStore.getAccountById(spender);

            validateSpender(allowance.amount(), spenderAccount);
            validateTrue(allowance.amount() >= 0, NEGATIVE_ALLOWANCE_AMOUNT);
            validateFalse(spender.equals(effectiveOwner.accountId()), SPENDER_ACCOUNT_SAME_AS_OWNER);
        }
    }

    private void validateFungibleTokenAllowances(
            final List<TokenAllowance> tokenAllowances,
            @NonNull final Account payer,
            final ReadableAccountStore accountStore,
            final ReadableTokenStore tokenStore,
            final ReadableTokenRelationStore tokenRelStore,
            @NonNull final ExpiryValidator expiryValidator) {
        for (final var allowance : tokenAllowances) {
            final var owner = allowance.owner();
            final var spender = allowance.spenderOrThrow();
            final var token = tokenStore.get(allowance.tokenIdOrThrow());
            // check if token exists
            validateTrue(token != null, INVALID_TOKEN_ID);

            // check if owner specified in allowances exists.
            // If not set, owner will be treated as payer for the transaction
            final var effectiveOwner = getEffectiveOwner(owner, payer, accountStore, expiryValidator);
            // validate spender account
            final var spenderAccount = accountStore.getAccountById(spender);
            validateTrue(TokenType.FUNGIBLE_COMMON.equals(token.tokenType()), NFT_IN_FUNGIBLE_TOKEN_ALLOWANCES);

            // validate token amount
            final var amount = allowance.amount();
            validateSpender(amount, spenderAccount);
            validateTrue(amount >= 0, NEGATIVE_ALLOWANCE_AMOUNT);
            validateFalse(
                    TokenSupplyType.FINITE.equals(token.supplyType()) && amount > token.maxSupply(),
                    AMOUNT_EXCEEDS_TOKEN_MAX_SUPPLY);
            // validate
            validateTokenBasics(effectiveOwner, spender, token, tokenRelStore);
        }
    }

    /**
     * Validate nft allowances list in {@link CryptoApproveAllowanceTransactionBody}
     * @param nftAllowancesList nft allowances
     * @param payer payer for approveAllowance txn
     * @param accountStore account store
     * @param tokenStore token store
     */
    private void validateNftAllowances(
            final List<NftAllowance> nftAllowancesList,
            @NonNull final Account payer,
            final ReadableAccountStore accountStore,
            final ReadableTokenStore tokenStore,
            final ReadableTokenRelationStore tokenRelStore,
            final ReadableNftStore uniqueTokenStore,
            @NonNull final ExpiryValidator expiryValidator) {
        for (final var allowance : nftAllowancesList) {
            final var owner = allowance.owner();
            final var spender = allowance.spenderOrThrow();
            final var tokenId = allowance.tokenIdOrThrow();
            final var serialNums = allowance.serialNumbers();

            final var token = tokenStore.get(tokenId);
            validateTrue(token != null, INVALID_TOKEN_ID);
            validateFalse(TokenType.FUNGIBLE_COMMON.equals(token.tokenType()), FUNGIBLE_TOKEN_IN_NFT_ALLOWANCES);

            final var spenderAccount = accountStore.getAccountById(spender);
            validateNFTSpender(serialNums, spenderAccount);

            final var effectiveOwner = getEffectiveOwner(owner, payer, accountStore, expiryValidator);
            validateTokenBasics(effectiveOwner, spender, token, tokenRelStore);

            // If a spender has been given approveForAll privileges, then it has the same privileges as owner of NFT.
            // But, the spender is not allowed to grant approveForAll privileges to anyone else.
            if (allowance.hasDelegatingSpender()
                    && allowance.delegatingSpenderOrThrow().accountNumOrThrow() != 0) {
                if (allowance.hasApprovedForAll()) {
                    validateFalse(
                            allowance.approvedForAll().booleanValue(), DELEGATING_SPENDER_CANNOT_GRANT_APPROVE_FOR_ALL);
                }
                final var approveForAllKey = AccountApprovalForAllAllowance.newBuilder()
                        .tokenId(tokenId)
                        .spenderId(allowance.delegatingSpender())
                        .build();
                validateTrue(
                        effectiveOwner
                                .approveForAllNftAllowancesOrElse(emptyList())
                                .contains(approveForAllKey),
                        DELEGATING_SPENDER_DOES_NOT_HAVE_APPROVE_FOR_ALL);
            }

            validateSerialNums(serialNums, tokenId, uniqueTokenStore);
        }
    }

    private void validateAllowanceCount(
            @NonNull final List<CryptoAllowance> cryptoAllowances,
            @NonNull final List<TokenAllowance> tokenAllowances,
            @NonNull final List<NftAllowance> nftAllowances,
            @NonNull final HederaConfig hederaConfig) {
        // each serial number of an NFT is considered as an allowance.
        // So for Nft allowances aggregated amount is considered for limit calculation.
        final var totalAllowances =
                cryptoAllowances.size() + tokenAllowances.size() + aggregateApproveNftAllowances(nftAllowances);
        validateTotalAllowancesPerTxn(totalAllowances, hederaConfig);
    }

    private void validateTokenBasics(
            final Account owner,
            final AccountID spender,
            final Token token,
            final ReadableTokenRelationStore tokenRelStore) {
        final var ownerId = owner.accountId();
        final var tokenId = token.tokenId();
        // ONLY reject self-approval for NFT's; else allow to match OZ ERC-20
        validateFalse(
                token.tokenType() != TokenType.FUNGIBLE_COMMON
                        && owner.accountIdOrThrow().equals(spender),
                SPENDER_ACCOUNT_SAME_AS_OWNER);
        final var relation = tokenRelStore.get(ownerId, tokenId);
        validateTrue(relation != null, TOKEN_NOT_ASSOCIATED_TO_ACCOUNT);
    }

    private void validateSpender(long amount, Account spenderAccount) {
        validateTrue(
                amount == 0 || (spenderAccount != null && !spenderAccount.deleted()), INVALID_ALLOWANCE_SPENDER_ID);
    }

    private void validateNFTSpender(List<Long> serialNumbers, Account spenderAccount) {
        validateTrue(
                serialNumbers.isEmpty() || (spenderAccount != null && !spenderAccount.deleted()),
                INVALID_ALLOWANCE_SPENDER_ID);
    }
}
