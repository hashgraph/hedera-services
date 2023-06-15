/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.token.impl.handlers;

import static com.hedera.hapi.node.base.ResponseCodeEnum.*;
import static com.hedera.node.app.service.token.impl.validators.AllowanceValidator.isValidOwner;
import static com.hedera.node.app.service.token.impl.validators.AllowanceValidator.validateAllowanceLimit;
import static com.hedera.node.app.spi.workflows.HandleException.validateTrue;
import static com.hedera.node.app.spi.workflows.PreCheckException.validateTruePreCheck;
import static java.util.Collections.emptyList;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.hapi.node.state.token.AccountApprovalForAllAllowance;
import com.hedera.hapi.node.state.token.AccountCryptoAllowance;
import com.hedera.hapi.node.state.token.AccountFungibleTokenAllowance;
import com.hedera.hapi.node.token.CryptoAllowance;
import com.hedera.hapi.node.token.NftAllowance;
import com.hedera.hapi.node.token.TokenAllowance;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.token.ReadableAccountStore;
import com.hedera.node.app.service.token.impl.WritableAccountStore;
import com.hedera.node.app.service.token.impl.WritableNftStore;
import com.hedera.node.app.service.token.impl.WritableTokenStore;
import com.hedera.node.app.service.token.impl.validators.ApproveAllowanceValidator;
import com.hedera.node.app.spi.workflows.*;
import com.hedera.node.config.data.HederaConfig;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * This class contains all workflow-related functionality regarding {@link
 * HederaFunctionality#CRYPTO_APPROVE_ALLOWANCE}.
 */
@Singleton
public class CryptoApproveAllowanceHandler implements TransactionHandler {
    private final ApproveAllowanceValidator allowanceValidator;

    @Inject
    public CryptoApproveAllowanceHandler(@NonNull final ApproveAllowanceValidator allowanceValidator) {
        this.allowanceValidator = allowanceValidator;
    }

    /**
     * @param txn the transaction body
     * @throws PreCheckException if the transaction is invalid for any reason
     */
    @Override
    public void pureChecks(@NonNull final TransactionBody txn) throws PreCheckException {
        requireNonNull(txn);
        final var op = txn.cryptoApproveAllowanceOrThrow();

        final var cryptoAllowancesSize =
                op.hasCryptoAllowances() ? op.cryptoAllowances().size() : 0;
        final var tokenAllowancesSize =
                op.hasTokenAllowances() ? op.tokenAllowances().size() : 0;
        final var nftAllowancesSize = op.hasNftAllowances() ? op.nftAllowances().size() : 0;
        final var totalAllowancesSize = cryptoAllowancesSize + tokenAllowancesSize + nftAllowancesSize;
        validateTruePreCheck(totalAllowancesSize != 0, EMPTY_ALLOWANCES);
    }

    @Override
    public void preHandle(@NonNull final PreHandleContext context) throws PreCheckException {
        requireNonNull(context);
        final var txn = context.body();
        pureChecks(txn);
        final var op = txn.cryptoApproveAllowanceOrThrow();
        var failureStatus = INVALID_ALLOWANCE_OWNER_ID;

        for (final var allowance : op.cryptoAllowancesOrElse(emptyList())) {
            context.requireKeyOrThrow(allowance.ownerOrElse(AccountID.DEFAULT), failureStatus);
        }
        for (final var allowance : op.tokenAllowancesOrElse(emptyList())) {
            context.requireKeyOrThrow(allowance.ownerOrElse(AccountID.DEFAULT), failureStatus);
        }
        for (final var allowance : op.nftAllowancesOrElse(emptyList())) {
            final var ownerId = allowance.ownerOrElse(AccountID.DEFAULT);
            // If a spender who is granted approveForAll from owner and is granting
            // allowance for a serial to another spender, need signature from the approveForAll
            // spender
            var operatorId = allowance.delegatingSpenderOrElse(ownerId);
            // If approveForAll is set to true, need signature from owner
            // since only the owner can grant approveForAll
            if (allowance.hasApprovedForAll() && allowance.approvedForAllOrThrow()) {
                operatorId = ownerId;
            }
            if (operatorId != ownerId) {
                failureStatus = INVALID_DELEGATING_SPENDER;
            }
            context.requireKeyOrThrow(operatorId, failureStatus);
        }
    }

    @Override
    public void handle(@NonNull final HandleContext context) throws HandleException {
        final var txn = context.body();
        final var payer = txn.transactionIDOrThrow().accountIDOrThrow();

        final var accountStore = context.writableStore(WritableAccountStore.class);

        // validate payer account exists
        final var payerAccount = accountStore.getAccountById(payer);
        validateTrue(payerAccount != null, INVALID_PAYER_ACCOUNT_ID);
        // validate the transaction body fields that include state or configuration
        // We can use payerAccount for validations since it's not mutated in validateSemantics
        validateSemantics(context, payerAccount, accountStore);

        // Apply all changes to the state modifications
        // We need to look up payer for each modification, since payer would have been modified
        // by a previous allowance change
        approveAllowance(context, payer, accountStore);
    }
    /**
     * Validate the transaction body fields that include state or configuration
     * @param context the handle context
     * @param payerAccount the payer account
     * @param accountStore the account store
     */
    private void validateSemantics(
            @NonNull final HandleContext context,
            @NonNull final Account payerAccount,
            @NonNull final ReadableAccountStore accountStore) {
        requireNonNull(context);
        requireNonNull(payerAccount);
        requireNonNull(accountStore);

        allowanceValidator.validate(context, payerAccount, accountStore);
    }

    /**
     * Apply all changes to the state modifications for crypto, token, and nft allowances
     * @param context the handle context
     * @param payerId the payer account id
     * @param accountStore the account store
     * @throws HandleException if there is an error applying the changes
     */
    private void approveAllowance(
            @NonNull final HandleContext context,
            @NonNull final AccountID payerId,
            @NonNull final WritableAccountStore accountStore)
            throws HandleException {
        requireNonNull(context);
        requireNonNull(payerId);
        requireNonNull(accountStore);

        final var op = context.body().cryptoApproveAllowanceOrThrow();
        final var cryptoAllowances = op.cryptoAllowancesOrElse(emptyList());
        final var tokenAllowances = op.tokenAllowancesOrElse(emptyList());
        final var nftAllowances = op.nftAllowancesOrElse(emptyList());

        final var hederaConfig = context.configuration().getConfigData(HederaConfig.class);
        final var tokenStore = context.writableStore(WritableTokenStore.class);
        final var uniqueTokenStore = context.writableStore(WritableNftStore.class);

        /* --- Apply changes to state --- */
        final var allowanceMaxAccountLimit = hederaConfig.allowancesMaxAccountLimit();
        applyCryptoAllowances(cryptoAllowances, payerId, accountStore, allowanceMaxAccountLimit);
        applyFungibleTokenAllowances(tokenAllowances, payerId, accountStore, allowanceMaxAccountLimit);
        applyNftAllowances(
                nftAllowances, payerId, accountStore, tokenStore, uniqueTokenStore, allowanceMaxAccountLimit);
    }

    /**
     * Applies all changes needed for Crypto allowances from the transaction.
     * If the spender already has an allowance, the allowance value will be replaced with values
     * from transaction. If the amount specified is 0, the allowance will be removed.
     * @param cryptoAllowances the list of crypto allowances
     * @param payerId the payer account id
     * @param accountStore the account store
     * @param allowanceMaxAccountLimit the {@link HederaConfig}
     */
    private void applyCryptoAllowances(
            @NonNull final List<CryptoAllowance> cryptoAllowances,
            @NonNull final AccountID payerId,
            @NonNull final WritableAccountStore accountStore,
            final int allowanceMaxAccountLimit) {
        requireNonNull(cryptoAllowances);
        requireNonNull(payerId);
        requireNonNull(accountStore);

        for (final var allowance : cryptoAllowances) {
            final var owner = allowance.owner();
            final var spender = allowance.spenderOrThrow();
            final var effectiveOwner = getEffectiveOwnerAccount(owner, payerId, accountStore);
            final var mutableAllowances = new ArrayList<>(effectiveOwner.cryptoAllowancesOrElse(emptyList()));

            final var amount = allowance.amount();

            updateCryptoAllowance(mutableAllowances, amount, spender.accountNumOrThrow());
            final var copy = effectiveOwner
                    .copyBuilder()
                    .cryptoAllowances(mutableAllowances)
                    .build();
            // Only when amount is greater than 0 we add or modify existing allowances.
            // When removing existing allowances no need to check this.
            if (amount > 0) {
                validateAllowanceLimit(copy, allowanceMaxAccountLimit);
            }
            accountStore.put(copy);
        }
    }

    /**
     * Updates the crypto allowance amount if the allowance exists, otherwise adds a new allowance
     * If the amount is zero removes the allowance if it exists in the list
     * @param mutableAllowances the list of mutable allowances of owner
     * @param amount the amount
     * @param spenderNum the spender number
     */
    private void updateCryptoAllowance(
            final ArrayList<AccountCryptoAllowance> mutableAllowances, final long amount, final long spenderNum) {
        final var newAllowanceBuilder = AccountCryptoAllowance.newBuilder().spenderNum(spenderNum);
        // get the index of the allowance with same spender in existing list
        final var index = lookupSpender(mutableAllowances, spenderNum);
        // If given amount is zero, if the element exists remove it, otherwise do nothing
        if (amount == 0) {
            if (index != -1) {
                // If amount is 0, remove the allowance
                mutableAllowances.remove(index);
            }
            return;
        }
        if (index != -1) {
            mutableAllowances.set(index, newAllowanceBuilder.amount(amount).build());
        } else {
            mutableAllowances.add(newAllowanceBuilder.amount(amount).build());
        }
    }

    /**
     * Applies all changes needed for fungible token allowances from the transaction.If the key
     * {token, spender} already has an allowance, the allowance value will be replaced with values
     * from transaction
     * @param tokenAllowances the list of token allowances
     * @param payerId the payer account id
     * @param accountStore the account store
     * @param allowanceMaxAccountLimit the {@link HederaConfig} allowance max account limit
     */
    private void applyFungibleTokenAllowances(
            @NonNull final List<TokenAllowance> tokenAllowances,
            @NonNull final AccountID payerId,
            @NonNull final WritableAccountStore accountStore,
            final int allowanceMaxAccountLimit) {
        for (final var allowance : tokenAllowances) {
            final var owner = allowance.owner();
            final var amount = allowance.amount();
            final var tokenId = allowance.tokenIdOrThrow();
            final var spender = allowance.spenderOrThrow();

            final var effectiveOwner = getEffectiveOwnerAccount(owner, payerId, accountStore);
            final var mutableTokenAllowances = new ArrayList<>(effectiveOwner.tokenAllowancesOrElse(emptyList()));

            updateTokenAllowance(mutableTokenAllowances, amount, spender.accountNumOrThrow(), tokenId.tokenNum());
            final var copy = effectiveOwner
                    .copyBuilder()
                    .tokenAllowances(mutableTokenAllowances)
                    .build();
            // Only when amount is greater than 0 we add or modify existing allowances.
            // When removing existing allowances no need to check this.
            if (amount > 0) {
                validateAllowanceLimit(copy, allowanceMaxAccountLimit);
            }
            accountStore.put(copy);
        }
    }

    /**
     * Updates the token allowance amount if the allowance for given tokenNuma dn spenderNum exists,
     * otherwise adds a new allowance.
     * If the amount is zero removes the allowance if it exists in the list
     * @param mutableAllowances the list of mutable allowances of owner
     * @param amount the amount
     * @param spenderNum the spender number
     * @param tokenNum the token number
     */
    private void updateTokenAllowance(
            final ArrayList<AccountFungibleTokenAllowance> mutableAllowances,
            final long amount,
            final long spenderNum,
            final long tokenNum) {
        final var newAllowanceBuilder = AccountFungibleTokenAllowance.newBuilder()
                .spenderNum(spenderNum)
                .tokenNum(tokenNum);
        final var index = lookupSpenderAndToken(mutableAllowances, spenderNum, tokenNum);
        // If given amount is zero, if the element exists remove it
        if (amount == 0) {
            if (index != -1) {
                mutableAllowances.remove(index);
            }
            return;
        }
        if (index != -1) {
            mutableAllowances.set(index, newAllowanceBuilder.amount(amount).build());
        } else {
            mutableAllowances.add(newAllowanceBuilder.amount(amount).build());
        }
    }

    /**
     * Applies all changes needed for NFT allowances from the transaction. If the key{tokenNum,
     * spenderNum} doesn't exist in the map the allowance will be inserted. If the key exists,
     * existing allowance values will be replaced with new allowances given in operation
     *
     * @param nftAllowances the list of nft allowances
     * @param payerId the payer account id
     * @param accountStore the account store
     * @param tokenStore the token store
     * @param uniqueTokenStore the unique token store
     * @param allowanceMaxAccountLimit the {@link HederaConfig} config allowance max account limit
     */
    protected void applyNftAllowances(
            final List<NftAllowance> nftAllowances,
            @NonNull final AccountID payerId,
            @NonNull final WritableAccountStore accountStore,
            @NonNull final WritableTokenStore tokenStore,
            @NonNull final WritableNftStore uniqueTokenStore,
            final int allowanceMaxAccountLimit) {
        for (final var allowance : nftAllowances) {
            final var owner = allowance.owner();
            final var tokenId = allowance.tokenIdOrThrow();
            final var spender = allowance.spenderOrThrow();

            final var effectiveOwner = getEffectiveOwnerAccount(owner, payerId, accountStore);
            final var mutableNftAllowances =
                    new ArrayList<>(effectiveOwner.approveForAllNftAllowancesOrElse(emptyList()));

            if (allowance.hasApprovedForAll()) {
                final var approveForAllAllowance = AccountApprovalForAllAllowance.newBuilder()
                        .tokenNum(tokenId.tokenNum())
                        .spenderNum(spender.accountNumOrThrow())
                        .build();

                if (Boolean.TRUE.equals(allowance.approvedForAll())) {
                    if (!mutableNftAllowances.contains(approveForAllAllowance)) {
                        mutableNftAllowances.add(approveForAllAllowance);
                    }
                } else {
                    mutableNftAllowances.remove(approveForAllAllowance);
                }
                final var copy = effectiveOwner
                        .copyBuilder()
                        .approveForAllNftAllowances(mutableNftAllowances)
                        .build();
                validateAllowanceLimit(copy, allowanceMaxAccountLimit);
                accountStore.put(copy);
            }
            // Update spender on all these Nfts to the new spender
            updateSpender(tokenStore, uniqueTokenStore, effectiveOwner, spender, tokenId, allowance.serialNumbers());
        }
    }

    /**
     * Updates the spender of the given NFTs to the new spender
     * @param tokenStore the token store
     * @param uniqueTokenStore the unique token store
     * @param owner the owner account
     * @param spenderId the spender account id
     * @param tokenId the token id
     * @param serialNums the serial numbers
     */
    public void updateSpender(
            @NonNull final WritableTokenStore tokenStore,
            @NonNull final WritableNftStore uniqueTokenStore,
            @NonNull final Account owner,
            @NonNull final AccountID spenderId,
            @NonNull final TokenID tokenId,
            @NonNull final List<Long> serialNums) {
        if (serialNums.isEmpty()) {
            return;
        }

        final var serialsSet = new HashSet<>(serialNums);
        for (final var serialNum : serialsSet) {
            final var nft = uniqueTokenStore.get(tokenId, serialNum);
            final var token = tokenStore.get(tokenId);

            validateTrue(isValidOwner(nft, owner.accountNumber(), token), SENDER_DOES_NOT_OWN_NFT_SERIAL_NO);
            final var copy = nft.copyBuilder()
                    .spenderNumber(spenderId.accountNumOrThrow())
                    .build();
            uniqueTokenStore.put(copy);
        }
    }

    /**
     * Returns the index of the allowance with the given spender in the list if it exists,
     * otherwise returns -1
     * @param ownerAllowances list of allowances
     * @param spenderNum spender account number
     * @return index of the allowance if it exists, otherwise -1
     */
    private int lookupSpender(final List<AccountCryptoAllowance> ownerAllowances, final long spenderNum) {
        for (int i = 0; i < ownerAllowances.size(); i++) {
            final var allowance = ownerAllowances.get(i);
            if (allowance.spenderNum() == spenderNum) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Returns the index of the allowance  with the given spender and token in the list if it exists,
     * otherwise returns -1
     * @param ownerAllowances list of allowances
     * @param spenderNum spender account number
     * @param tokenNum token number
     * @return index of the allowance if it exists, otherwise -1
     */
    private int lookupSpenderAndToken(
            final List<AccountFungibleTokenAllowance> ownerAllowances, final long spenderNum, final long tokenNum) {
        for (int i = 0; i < ownerAllowances.size(); i++) {
            final var allowance = ownerAllowances.get(i);
            if (allowance.spenderNum() == spenderNum && allowance.tokenNum() == tokenNum) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Returns the effective owner account. If the owner is not present or owner is same as payer.
     * Since we are modifying the payer account in the same transaction for each allowance if owner is not specified,
     * we need to get the payer account each time from the modifications map.
     * @param owner owner of the allowance
     * @param payerId payer of the transaction
     * @param accountStore account store
     * @return effective owner account
     */
    private static Account getEffectiveOwnerAccount(
            @Nullable final AccountID owner,
            @NonNull final AccountID payerId,
            @NonNull final ReadableAccountStore accountStore) {
        final var ownerNum = owner != null ? owner.accountNumOrElse(0L) : 0L;
        if (ownerNum == 0 || ownerNum == payerId.accountNumOrThrow()) {
            // The payer would have been modified in the same transaction for previous allowances
            // So, get it from modifications map.
            return accountStore.getAccountById(payerId);
        } else {
            // If owner is in modifications get the modified account from state
            final var ownerAccount = accountStore.getAccountById(owner);
            validateTrue(ownerAccount != null, INVALID_ALLOWANCE_OWNER_ID);
            return ownerAccount;
        }
    }
}
