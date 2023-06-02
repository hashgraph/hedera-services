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

import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_ALLOWANCE_OWNER_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_DELEGATING_SPENDER;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_PAYER_ACCOUNT_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.SENDER_DOES_NOT_OWN_NFT_SERIAL_NO;
import static com.hedera.node.app.service.token.impl.helpers.AllowanceHelpers.getEffectiveOwner;
import static com.hedera.node.app.service.token.impl.helpers.AllowanceHelpers.validateAllowanceLimit;
import static com.hedera.node.app.service.token.impl.helpers.AllowanceHelpers.validateOwner;
import static com.hedera.node.app.spi.workflows.HandleException.validateTrue;
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
import com.hedera.node.app.service.token.ReadableAccountStore;
import com.hedera.node.app.service.token.impl.WritableAccountStore;
import com.hedera.node.app.service.token.impl.WritableTokenStore;
import com.hedera.node.app.service.token.impl.WritableUniqueTokenStore;
import com.hedera.node.app.service.token.impl.validators.ApproveAllowanceValidator;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.spi.workflows.HandleException;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.spi.workflows.PreHandleContext;
import com.hedera.node.app.spi.workflows.TransactionHandler;
import com.hedera.node.config.data.HederaConfig;
import edu.umd.cs.findbugs.annotations.NonNull;
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

    @Override
    public void preHandle(@NonNull final PreHandleContext context) throws PreCheckException {
        requireNonNull(context);
        final var op = context.body().cryptoApproveAllowanceOrThrow();
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
        validateSemantics(context, payerAccount, accountStore);
        // Apply all changes to the state modifications
        approveAllowance(context, payerAccount, accountStore);
    }

    private void validateSemantics(
            @NonNull final HandleContext context,
            @NonNull final Account payerAccount,
            @NonNull final ReadableAccountStore accountStore) {
        requireNonNull(context);
        requireNonNull(payerAccount);
        requireNonNull(accountStore);

        allowanceValidator.validate(context, payerAccount, accountStore);
    }

    private void approveAllowance(
            @NonNull final HandleContext context,
            @NonNull final Account payerAccount,
            @NonNull final WritableAccountStore accountStore)
            throws HandleException {
        requireNonNull(context);
        requireNonNull(payerAccount);
        requireNonNull(accountStore);

        final var op = context.body().cryptoApproveAllowanceOrThrow();
        final var cryptoAllowances = op.cryptoAllowancesOrElse(emptyList());
        final var tokenAllowances = op.tokenAllowancesOrElse(emptyList());
        final var nftAllowances = op.nftAllowancesOrElse(emptyList());

        final var hederaConfig = context.configuration().getConfigData(HederaConfig.class);
        final var tokenStore = context.writableStore(WritableTokenStore.class);
        final var uniqueTokenStore = context.writableStore(WritableUniqueTokenStore.class);

        /* --- Do the business logic --- */
        applyCryptoAllowances(cryptoAllowances, payerAccount, accountStore, hederaConfig);
        applyFungibleTokenAllowances(tokenAllowances, payerAccount, accountStore, hederaConfig);
        applyNftAllowances(nftAllowances, payerAccount, accountStore, tokenStore, uniqueTokenStore, hederaConfig);
    }

    /**
     * Applies all changes needed for Crypto allowances from the transaction. If the spender already has an allowance,
     * the allowance value will be replaced with values from transaction
     */
    private void applyCryptoAllowances(
            @NonNull final List<CryptoAllowance> cryptoAllowances,
            @NonNull final Account payerAccount,
            @NonNull final WritableAccountStore accountStore,
            @NonNull final HederaConfig config) {
        requireNonNull(cryptoAllowances);
        requireNonNull(payerAccount);
        requireNonNull(accountStore);
        requireNonNull(config);

        for (final var allowance : cryptoAllowances) {
            final var owner = allowance.owner();
            final var spender = allowance.spender();
            final var effectiveOwner = getEffectiveOwner(owner, payerAccount, accountStore);
            final var ownerCryptoAllowances = effectiveOwner.cryptoAllowances();

            final var amount = allowance.amount();
            final var allowanceBuilder = AccountCryptoAllowance.newBuilder().spenderNum(spender.accountNum());
            final var zeroAllowance = allowanceBuilder.amount(0).build();
//            for(int i = 0 ; i < ownerCryptoAllowances.size(); i++) {
//                final var existingAllowance = ownerCryptoAllowances.get(i);
//                if (existingAllowance.spenderNum() == spender.accountNumOrThrow()) {
//                    if(amount == 0) {
//                        // remove will keep the changes in the modifications and doesn't persist to state.
//                        // When we need modify the same account again in the same transaction, we can use the
//                        // modified account from the modifications
//                        ownerCryptoAllowances.remove(i);
//                        break;
//                    } else {
//                        ownerCryptoAllowances.set(i, allowanceBuilder.amount(amount).build());
//                        break;
//                    }
//                } else {
//                    ownerCryptoAllowances.add(allowanceBuilder.amount(amount).build());
//                    break;
//                }
//            }
            if (ownerCryptoAllowances.contains(zeroAllowance)) {
                removeCryptoAllowance(zeroAllowance, effectiveOwner, accountStore);
            }
            if (amount > 0) {
                final var nonZeroAllowance = allowanceBuilder.amount(amount).build();
                addCryptoAllowance(nonZeroAllowance, effectiveOwner, accountStore, config);
            }
        }
    }

    /**
     * Applies all changes needed for fungible token allowances from the transaction.If the key
     * {token, spender} already has an allowance, the allowance value will be replaced with values
     * from transaction
     */
    private void applyFungibleTokenAllowances(
            @NonNull final List<TokenAllowance> tokenAllowances,
            @NonNull final Account payerAccount,
            @NonNull final WritableAccountStore accountStore,
            @NonNull final HederaConfig config) {
        for (final var allowance : tokenAllowances) {
            final var owner = allowance.owner();
            final var amount = allowance.amount();
            final var tokenId = allowance.tokenId();
            final var spender = allowance.spender();

            final var effectiveOwner = getEffectiveOwner(owner, payerAccount, accountStore);
            final var ownerTokenAllowances = effectiveOwner.tokenAllowances();

            final var allowanceBuilder = AccountFungibleTokenAllowance.newBuilder()
                    .spenderNum(spender.accountNum())
                    .tokenNum(tokenId.tokenNum());
            final var zeroAllowanceKey = allowanceBuilder.amount(0).build();
            if (ownerTokenAllowances.contains(zeroAllowanceKey)) {
                removeTokenAllowance(zeroAllowanceKey, effectiveOwner, accountStore);
            }
            if (amount > 0) {
                final var nonZeroAllowance = allowanceBuilder.amount(amount).build();
                addTokenAllowance(nonZeroAllowance, effectiveOwner, accountStore, config);
            }
//
//            for (int i = 0 ; i < ownerTokenAllowances.size(); i++) {
//                final var tokenAllowance = ownerTokenAllowances.get(i);
//                if(tokenAllowance.tokenNum() == tokenId.tokenNum() && tokenAllowance.spenderNum() == spender.accountNum()){
//                    if (amount == 0) {
//                        ownerTokenAllowances.remove(i);
//                        break;
//                    } else {
//                        ownerTokenAllowances.set(i, allowanceBuilder.amount(amount).build());
//                    }
//                } else {
//                    ownerTokenAllowances.add(allowanceBuilder.amount(amount).build());
//                }
//            }
        }
    }

    /**
     * Applies all changes needed for NFT allowances from the transaction. If the key{tokenNum,
     * spenderNum} doesn't exist in the map the allowance will be inserted. If the key exists,
     * existing allowance values will be replaced with new allowances given in operation
     */
    protected void applyNftAllowances(
            final List<NftAllowance> nftAllowances,
            @NonNull final Account payerAccount,
            @NonNull final WritableAccountStore accountStore,
            @NonNull final WritableTokenStore tokenStore,
            @NonNull final WritableUniqueTokenStore uniqueTokenStore,
            @NonNull final HederaConfig config) {
        for (final var allowance : nftAllowances) {
            final var owner = allowance.owner();
            final var tokenId = allowance.tokenId();
            final var spender = allowance.spender();

            final var effectiveOwner = getEffectiveOwner(owner, payerAccount, accountStore);

            if (allowance.hasApprovedForAll()) {
                final var approveForAllAllowance = AccountApprovalForAllAllowance.newBuilder()
                        .tokenNum(tokenId.tokenNum())
                        .spenderNum(spender.accountNum())
                        .build();

                if (allowance.approvedForAll().booleanValue()) {
                    addApproveForAllNftAllowance(approveForAllAllowance, effectiveOwner, accountStore, config);
                } else {
                    removeApproveForAllNftAllowance(approveForAllAllowance, effectiveOwner, accountStore, config);
                }
            }
            // Update spender on all these Nfts to the new spender
            updateSpender(tokenStore, uniqueTokenStore, effectiveOwner, spender, tokenId, allowance.serialNumbers());
        }
    }

    private void addApproveForAllNftAllowance(
            @NonNull final AccountApprovalForAllAllowance approveForAllAllowance,
            @NonNull final Account effectiveOwner,
            @NonNull final WritableAccountStore accountStore,
            @NonNull final HederaConfig config) {
        final var ownerApproveForAllAllowances = effectiveOwner.approveForAllNftAllowances();
        final var approveForAllSet = new HashSet<>(ownerApproveForAllAllowances);
        approveForAllSet.add(approveForAllAllowance);

        final var copy = effectiveOwner
                .copyBuilder()
                .approveForAllNftAllowances(List.copyOf(approveForAllSet))
                .build();
        validateAllowanceLimit(copy, config);
        accountStore.put(copy);
    }

    private void removeApproveForAllNftAllowance(
            @NonNull final AccountApprovalForAllAllowance approveForAllAllowance,
            @NonNull final Account effectiveOwner,
            @NonNull final WritableAccountStore accountStore,
            @NonNull final HederaConfig config) {
        final var ownerApproveForAllAllowances = effectiveOwner.approveForAllNftAllowances();
        final var approveForAllSet = new HashSet(ownerApproveForAllAllowances);
        approveForAllSet.remove(approveForAllAllowance);

        final var copy = effectiveOwner
                .copyBuilder()
                .approveForAllNftAllowances(new ArrayList<>(approveForAllSet))
                .build();
        validateAllowanceLimit(copy, config);
        accountStore.put(copy);
    }

    private void removeCryptoAllowance(
            @NonNull final AccountCryptoAllowance zeroAllowance,
            @NonNull final Account effectiveOwner,
            @NonNull final WritableAccountStore accountStore) {
        final var ownerCryptoAllowances = effectiveOwner.cryptoAllowances();
        ownerCryptoAllowances.remove(zeroAllowance);
        final var copy = effectiveOwner
                .copyBuilder()
                .cryptoAllowances(ownerCryptoAllowances)
                .build();
        accountStore.put(copy);
    }

    private void addCryptoAllowance(
            @NonNull final AccountCryptoAllowance nonZeroAllowance,
            @NonNull final Account effectiveOwner,
            @NonNull final WritableAccountStore accountStore,
            @NonNull final HederaConfig config) {
        final var ownerCryptoAllowances = new ArrayList<>(effectiveOwner.cryptoAllowances());
        final var index = lookupSpender(ownerCryptoAllowances, nonZeroAllowance.spenderNum());

        if(index != -1) {
            ownerCryptoAllowances.set(index, nonZeroAllowance);
        } else {
            ownerCryptoAllowances.add(nonZeroAllowance);
        }

        final var copy = effectiveOwner
                .copyBuilder()
                .cryptoAllowances(ownerCryptoAllowances)
                .build();
        validateAllowanceLimit(copy, config);
        accountStore.put(copy);
    }

    private void removeTokenAllowance(
            @NonNull final AccountFungibleTokenAllowance zeroAllowance,
            @NonNull final Account effectiveOwner,
            @NonNull final WritableAccountStore accountStore) {
        final var ownerTokenAllowances = effectiveOwner.tokenAllowances();
        ownerTokenAllowances.remove(zeroAllowance);
        final var copy = effectiveOwner
                .copyBuilder()
                .tokenAllowances(ownerTokenAllowances)
                .build();
        accountStore.put(copy);
    }

    private void addTokenAllowance(
            @NonNull final AccountFungibleTokenAllowance nonZeroAllowance,
            @NonNull final Account effectiveOwner,
            @NonNull final WritableAccountStore accountStore,
            @NonNull final HederaConfig config) {

        final var ownerTokenAllowances = new ArrayList<>(effectiveOwner.tokenAllowances());
        final var index = lookupSpenderAndToken(ownerTokenAllowances, nonZeroAllowance.spenderNum(), nonZeroAllowance.tokenNum());
        if(index != -1) {
            ownerTokenAllowances.set(index, nonZeroAllowance);
        } else {
            ownerTokenAllowances.add(nonZeroAllowance);
        }

        ownerTokenAllowances.add(nonZeroAllowance);
        final var copy = effectiveOwner
                .copyBuilder()
                .tokenAllowances(ownerTokenAllowances)
                .build();
        validateAllowanceLimit(copy, config);
        accountStore.put(copy);
    }

    /**
     * Updates the Spender of each NFT serial
     *
     */
    public void updateSpender(
            @NonNull final WritableTokenStore tokenStore,
            @NonNull final WritableUniqueTokenStore uniqueTokenStore,
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
            validateTrue(validateOwner(nft, owner.accountNumber(), token), SENDER_DOES_NOT_OWN_NFT_SERIAL_NO);
            final var copy =
                    nft.copyBuilder().spenderNumber(spenderId.accountNum()).build();
            uniqueTokenStore.put(copy);
        }
    }

    private int lookupSpender(final List<AccountCryptoAllowance> ownerCryptoAllowances, final long spenderNum){
        for (int i = 0; i < ownerCryptoAllowances.size(); i++) {
            final var allowance = ownerCryptoAllowances.get(i);
            if (allowance.spenderNum() == spenderNum) {
                return i;
            }
        }
        return -1;
    }

    private int lookupSpenderAndToken(final List<AccountFungibleTokenAllowance> ownerCryptoAllowances,
                                      final long spenderNum, final long tokenNum){
        for (int i = 0; i < ownerCryptoAllowances.size(); i++) {
            final var allowance = ownerCryptoAllowances.get(i);
            if (allowance.spenderNum() == spenderNum && allowance.tokenNum() == tokenNum) {
                return i;
            }
        }
        return -1;
    }
}
